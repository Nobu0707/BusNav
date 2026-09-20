package net.nobu0707.busnav.ui.navigation

import net.nobu0707.busnav.domain.navigation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.nobu0707.busnav.domain.route.ScheduledRouteRepository
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.location.LocationProvider
import net.nobu0707.busnav.location.LocationUpdate

class NavigationStateHolder(
    private val locationProvider: LocationProvider,
    private val routeRepository: ScheduledRouteRepository,
    private val scope: CoroutineScope,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val matcherConfig: RouteMatcherConfig = RouteMatcherConfig(),
    private val diagnostics: (String) -> Unit = {},
) {
    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()
    private var locationJob: Job? = null
    private var preparationJob: Job? = null
    private var guidanceJob: Job? = null
    private var freshnessJob: Job? = null
    private var calculator: NavigationProgressCalculator? = null
    private var matcher: RouteMatcher? = null
    private var matcherState = RouteMatcherState()
    private val detector = RouteDeviationDetector()
    private var deviation = RouteDeviationSnapshot()
    private var previousHighway: HighwayGuidanceSnapshot? = null
    private var generation = 0L
    private var routeGeneration = 0L
    private var latestFixTime: Long? = null
    private var lastDiagnosticQuality: RouteMatchQuality? = null
    private var lastDiagnosticState: RouteDeviationState? = null

    init { loadActiveRoute() }

    private fun loadActiveRoute() {
        val revision = routeGeneration
        scope.launch {
            try {
                val route = routeRepository.getActiveRoute()
                if (revision == routeGeneration) update { copy(activeRoute = route, isRouteLoading = false, routeError = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (revision == routeGeneration) update {
                    copy(activeRoute = null, isRouteLoading = false, routeError = error.message ?: "所定経路を読み込めませんでした")
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
        if (_uiState.value.locationPermissionState != LocationPermissionState.Granted || locationJob?.isActive == true) return
        if (!locationProvider.isLocationEnabled()) update {
            copy(locationError = "端末の位置情報を有効にしてください", isLoading = false)
        }
        locationJob = scope.launch {
            locationProvider.updates().catch { error ->
                if (error is CancellationException) throw error
                update { copy(locationError = error.message ?: "位置情報を取得できませんでした", isLoading = false) }
            }.collect { incoming ->
                when (incoming) {
                    is LocationUpdate.Position -> {
                        val time = incoming.location.elapsedRealtimeMillis
                        // Reject old/duplicate fixes before changing either the raw marker or navigation state.
                        if (time != null && latestFixTime != null && time <= requireNotNull(latestFixTime)) return@collect
                        if (time != null && time in 0..elapsedMillis()) latestFixTime = time
                        update { copy(location = incoming.location, locationError = null, isLoading = false) }
                        freshnessJob?.cancel()
                        freshnessJob = scope.launch {
                            val remaining = time?.let { matcherConfig.staleAfterMillis - (elapsedMillis() - it) } ?: 0
                            delay(remaining.coerceIn(0, matcherConfig.staleAfterMillis))
                            if (_uiState.value.location === incoming.location) markUnavailable()
                        }
                    }
                    is LocationUpdate.Error -> update { copy(locationError = incoming.message, isLoading = false) }
                    LocationUpdate.Disabled -> update { copy(locationError = "端末の位置情報を有効にしてください", isLoading = false) }
                }
            }
        }
    }

    fun stopLocationUpdates() { locationJob?.cancel(); locationJob = null }
    fun onMapReady() = update { copy(isMapReady = true) }
    fun onMapError(message: String) = update { copy(isMapReady = false, locationError = message, isLoading = false) }
    fun onManualMapGesture() = update { copy(isFollowingLocation = false) }
    fun onCurrentLocationRequested() = update { copy(isFollowingLocation = true, recenterRequestId = recenterRequestId + 1) }
    fun onRouteOverviewRequested() {
        if (_uiState.value.activeRoute == null) return
        update { copy(isFollowingLocation = false, routeOverviewRequestId = routeOverviewRequestId + 1) }
    }

    fun applyCalculatedRoute(route: ScheduledRoute) {
        update { copy(activeRoute = route, isRouteLoading = false, routeError = null, routeOverviewRequestId = routeOverviewRequestId + 1) }
    }

    private fun emitTransitions() {
        if (lastDiagnosticQuality != deviation.matchQuality) {
            diagnostics("navigation.match." + deviation.matchQuality.name.lowercase())
            lastDiagnosticQuality = deviation.matchQuality
        }
        if (lastDiagnosticState != deviation.state) {
            diagnostics("navigation.deviation." + deviation.state.name.lowercase())
            lastDiagnosticState = deviation.state
        }
    }

    private fun markUnavailable() {
        generation++
        guidanceJob?.cancel()
        deviation = detector.uncertain(deviation)
        emitTransitions()
        val state = _uiState.value
        _uiState.value = state.copy(
            guidance = if (state.activeRoute == null) GuidanceUiState() else GuidanceUiState(GuidanceStatus.WAITING_LOCATION, "位置情報を確認中"),
            highwayGuidance = null,
            deviation = if (state.activeRoute == null) DeviationUiState() else deviationUiState(deviation),
            deviationSnapshot = deviation,
        )
    }

    private fun refreshGuidance() {
        val revision = ++generation
        guidanceJob?.cancel()
        val state = _uiState.value
        val route = state.activeRoute
        val location = state.location
        val prepared = calculator
        val matching = matcher
        if (route == null || location == null || state.locationPermissionState != LocationPermissionState.Granted ||
            state.locationError != null || prepared == null || matching == null) {
            markUnavailable()
            return
        }
        val previous = matcherState
        val priorDeviation = deviation
        val highwayBefore = previousHighway
        guidanceJob = scope.launch {
            val result = withContext(computationDispatcher) {
                matching.match(location, previous, elapsedMillis())
            }
            // Both generation and route identity protect against cancellation-insensitive calculations and ABA changes.
            if (generation != revision || _uiState.value.activeRoute !== route || _uiState.value.location !== location) return@launch
            val timestamp = location.elapsedRealtimeMillis
            if (!result.accepted || timestamp == null || elapsedMillis() - timestamp >= matcherConfig.staleAfterMillis) {
                markUnavailable()
                return@launch
            }
            matcherState = result.state
            deviation = detector.update(priorDeviation, result.match, location.accuracyMeters, timestamp)
            val match = requireNotNull(result.match)
            val reliability = when {
                match.quality == RouteMatchQuality.UNRELIABLE -> ProjectionReliability.UNRELIABLE
                !deviation.allowsGuidance -> ProjectionReliability.UNCERTAIN
                else -> ProjectionReliability.RELIABLE
            }
            val progress = prepared.calculate(match.projection,
                matcherState.heldProgressMeters ?: match.projection.distanceAlongRouteMeters).copy(reliability = reliability)
            val display = if (route.guidance?.maneuvers.isNullOrEmpty()) GuidanceUiState(GuidanceStatus.NO_GUIDANCE, "この経路に案内情報はありません")
                else guidanceUiState(progress)
            val highway = prepared.highwayCalculator.calculate(progress.distanceAlongRouteMeters, reliability, highwayBefore)
            previousHighway = highway
            emitTransitions()
            _uiState.value = _uiState.value.copy(guidance = display, highwayGuidance = HighwayInstructionFormatter.format(highway),
                deviation = deviationUiState(deviation), deviationSnapshot = deviation)
        }
    }

    private fun update(transform: NavigationUiState.() -> NavigationUiState) {
        val before = _uiState.value
        val after = before.transform()
        _uiState.value = after
        if (before.activeRoute !== after.activeRoute) {
            routeGeneration++
            preparationJob?.cancel()
            calculator = null
            matcher = null
            matcherState = RouteMatcherState()
            deviation = RouteDeviationSnapshot()
            previousHighway = null
            markUnavailable()
            after.activeRoute?.let { route ->
                val revision = routeGeneration
                preparationJob = scope.launch {
                    val prepared = withContext(computationDispatcher) {
                        val progress = NavigationProgressCalculator(route)
                        progress to RouteMatcher(RouteMatchIndex(progress.distanceIndex, matcherConfig), matcherConfig)
                    }
                    if (revision == routeGeneration && _uiState.value.activeRoute === route) {
                        calculator = prepared.first
                        matcher = prepared.second
                        refreshGuidance()
                    }
                }
            }
        }
        if (before.activeRoute !== after.activeRoute) return
        if (before.location != after.location ||
            before.locationPermissionState != after.locationPermissionState || before.locationError != after.locationError) refreshGuidance()
    }
}
