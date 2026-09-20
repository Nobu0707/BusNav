package net.nobu0707.busnav.ui.navigation

import net.nobu0707.busnav.domain.navigation.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import net.nobu0707.busnav.domain.route.ScheduledRouteRepository
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.location.LocationProvider
import net.nobu0707.busnav.location.LocationUpdate

class NavigationStateHolder(
    private val locationProvider: LocationProvider,
    private val routeRepository: ScheduledRouteRepository,
    private val scope: CoroutineScope,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var preparationJob: Job? = null
    private var guidanceJob: Job? = null
    private var calculator: NavigationProgressCalculator? = null
    private var previousProgress: NavigationProgress? = null

    init {
        loadActiveRoute()
    }

    private fun loadActiveRoute() {
        scope.launch {
            try {
                val route = routeRepository.getActiveRoute()
                update { copy(activeRoute = route, isRouteLoading = false, routeError = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update {
                    copy(
                        activeRoute = null,
                        isRouteLoading = false,
                        routeError = error.message ?: "所定経路を読み込めませんでした",
                    )
                }
            }
        }
    }

    fun setLayoutMode(layoutMode: NavigationLayoutMode) = update { copy(layoutMode = layoutMode) }

    fun setPermission(permissionState: LocationPermissionState) {
        update { copy(locationPermissionState = permissionState, isLoading = false) }
        if (permissionState == LocationPermissionState.Granted) startLocationUpdates() else stopLocationUpdates()
    }

    fun startLocationUpdates() {
        if (_uiState.value.locationPermissionState != LocationPermissionState.Granted) return
        if (locationJob?.isActive == true) return
        if (!locationProvider.isLocationEnabled()) {
            update { copy(locationError = "端末の位置情報を有効にしてください", isLoading = false) }
        }
        locationJob = scope.launch {
            locationProvider.updates()
                .catch { error ->
                    if (error is CancellationException) throw error
                    update { copy(locationError = error.message ?: "位置情報を取得できませんでした", isLoading = false) }
                }
                .collect { locationUpdate ->
                    when (locationUpdate) {
                        is LocationUpdate.Position -> update {
                            copy(location = locationUpdate.location, locationError = null, isLoading = false)
                        }
                        is LocationUpdate.Error -> update {
                            copy(locationError = locationUpdate.message, isLoading = false)
                        }
                        LocationUpdate.Disabled -> update {
                            copy(locationError = "端末の位置情報を有効にしてください", isLoading = false)
                        }
                    }
                }
        }
    }

    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    fun onMapReady() = update { copy(isMapReady = true) }

    fun onMapError(message: String) = update { copy(isMapReady = false, locationError = message, isLoading = false) }

    fun onManualMapGesture() = update { copy(isFollowingLocation = false) }

    fun onCurrentLocationRequested() = update {
        copy(isFollowingLocation = true, recenterRequestId = recenterRequestId + 1)
    }

    fun onRouteOverviewRequested() {
        if (_uiState.value.activeRoute == null) return
        update {
            copy(
                isFollowingLocation = false,
                routeOverviewRequestId = routeOverviewRequestId + 1,
            )
        }
    }

    fun applyCalculatedRoute(route: ScheduledRoute) {
        update {
            copy(
                activeRoute = route,
                isRouteLoading = false,
                routeError = null,
                routeOverviewRequestId = routeOverviewRequestId + 1,
            )
        }
    }

    private fun refreshGuidance() {
        guidanceJob?.cancel()
        val state = _uiState.value
        val route = state.activeRoute
        val location = state.location
        val prepared = calculator
        val pending = when {
            route == null -> GuidanceUiState()
            route.guidance?.maneuvers.isNullOrEmpty() -> GuidanceUiState(GuidanceStatus.NO_GUIDANCE, "この経路に案内情報はありません")
            location == null || state.locationPermissionState != LocationPermissionState.Granted || state.locationError != null ->
                GuidanceUiState(GuidanceStatus.WAITING_LOCATION, "位置情報待ち")
            prepared == null -> GuidanceUiState(GuidanceStatus.LOCATING, "案内を準備中")
            else -> null
        }
        if (pending != null) {
            _uiState.value = state.copy(guidance = pending)
            return
        }
        val last = previousProgress
        guidanceJob = scope.launch {
            val progress = withContext(computationDispatcher) {
                val raw = requireNotNull(prepared).calculate(requireNotNull(location).point, last?.projection?.segmentIndex)
                if (raw.isProjectionReliable && last != null &&
                    last.distanceAlongRouteMeters - raw.distanceAlongRouteMeters in 0.0..prepared.config.backwardToleranceMeters) {
                    prepared.calculate(raw.projection, last.distanceAlongRouteMeters)
                } else raw
            }
            if (_uiState.value.activeRoute === route && _uiState.value.location == location) {
                val accuracy = location?.accuracyMeters
                val display = if (accuracy != null && (!accuracy.isFinite() || accuracy > requireNotNull(prepared).config.reliableDistanceMeters)) {
                    GuidanceUiState(GuidanceStatus.UNCERTAIN, "経路付近の位置を確認中", "位置精度が低下しています")
                } else guidanceUiState(progress)
                if (display.status == GuidanceStatus.RELIABLE) previousProgress = progress
                _uiState.value = _uiState.value.copy(guidance = display)
            }
        }
    }

    private fun update(transform: NavigationUiState.() -> NavigationUiState) {
        val before = _uiState.value
        val after = before.transform()
        _uiState.value = after
        if (before.activeRoute !== after.activeRoute) {
            preparationJob?.cancel()
            calculator = null
            previousProgress = null
            after.activeRoute?.takeIf { !it.guidance?.maneuvers.isNullOrEmpty() }?.let { route ->
                preparationJob = scope.launch {
                    val prepared = withContext(computationDispatcher) { NavigationProgressCalculator(route) }
                    if (_uiState.value.activeRoute === route) {
                        calculator = prepared
                        refreshGuidance()
                    }
                }
            }
        }
        if (before.activeRoute !== after.activeRoute || before.location != after.location ||
            before.locationPermissionState != after.locationPermissionState || before.locationError != after.locationError) {
            refreshGuidance()
        }
    }
}
