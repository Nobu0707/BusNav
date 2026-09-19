package net.nobu0707.busnav.ui.navigation

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
) {
    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null

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

    private inline fun update(transform: NavigationUiState.() -> NavigationUiState) {
        _uiState.value = _uiState.value.transform()
    }
}
