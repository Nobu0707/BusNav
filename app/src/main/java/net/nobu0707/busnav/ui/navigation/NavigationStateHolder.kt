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
    private val arrivalDetector = ArrivalDetector()
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
                if (revision == routeGeneration) update { copy(activeRoute = route,
                    freePlan = route?.let { FreeNavigationPlan(it.destination.position, it.name) },
                    navigationMode = if (route != null) net.nobu0707.busnav.domain.prescribed.NavigationMode.FREE else navigationMode,
                    isRouteLoading = false, routeError = null) }
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

    fun previewEditorCandidate(route: ScheduledRoute) =
        previewFreeRoute(FreeNavigationPlan(route.destination.position, route.name), route)

    fun applyCalculatedRoute(route: ScheduledRoute) {
        // Compatibility for explicitly applied unsaved editor candidates.
        if (_uiState.value.isNavigationStarted) return
        val following = _uiState.value.isFollowingLocation
        previewEditorCandidate(route)
        startFreeNavigation()
        update { copy(isFollowingLocation = following) }
    }

    /** Cross-session replacements require an explicit end of the active session. */
    fun beginFreeSelection(): Boolean {
        if (_uiState.value.isNavigationStarted) return false
        clearRoute()
        update { copy(navigationMode = net.nobu0707.busnav.domain.prescribed.NavigationMode.FREE) }
        return true
    }

    fun previewFreeRoute(plan: FreeNavigationPlan, route: ScheduledRoute): Boolean {
        if (_uiState.value.isNavigationStarted) return false
        update { copy(activeRoute = route, freePlan = plan, activePrescribedRouteId = null,
            activePrescribedRouteName = null, navigationMode = net.nobu0707.busnav.domain.prescribed.NavigationMode.FREE,
            isNavigationStarted = false, isFollowingLocation = false, isRouteLoading = false, routeError = null,
            routeOverviewRequestId = routeOverviewRequestId + 1) }
        return true
    }

    fun startFreeNavigation(): Boolean {
        if (_uiState.value.navigationMode != net.nobu0707.busnav.domain.prescribed.NavigationMode.FREE ||
            _uiState.value.freePlan == null || _uiState.value.activeRoute == null) return false
        update { copy(isNavigationStarted = true, isFollowingLocation = true) }
        return true
    }

    fun replaceFreeRoute(plan: FreeNavigationPlan, route: ScheduledRoute): Boolean {
        if (!_uiState.value.navigationActive || _uiState.value.navigationMode != net.nobu0707.busnav.domain.prescribed.NavigationMode.FREE ||
            _uiState.value.freePlan != plan) return false
        update { copy(activeRoute = route, isFollowingLocation = true, routeOverviewRequestId = routeOverviewRequestId + 1) }
        return true
    }

    fun openPrescribedRoute(record: net.nobu0707.busnav.domain.prescribed.PrescribedRouteRecord): Boolean {
        if (_uiState.value.isNavigationStarted) return false
        update { copy(activeRoute = record.route, freePlan = null, activePrescribedRouteId = record.id, activePrescribedRouteName = record.name,
            navigationMode = net.nobu0707.busnav.domain.prescribed.NavigationMode.PRESCRIBED,
            isNavigationStarted = false, isFollowingLocation = false, isRouteLoading = false,
            routeError = null, routeOverviewRequestId = routeOverviewRequestId + 1) }
        return true
    }
    fun refreshPrescribedName(id: String, name: String) {
        if (_uiState.value.activePrescribedRouteId == id) update { copy(activePrescribedRouteName = name) }
    }
    fun startNavigation() {
        if (_uiState.value.navigationMode == net.nobu0707.busnav.domain.prescribed.NavigationMode.FREE) startFreeNavigation()
        else if (_uiState.value.activeRoute != null && _uiState.value.activePrescribedRouteId != null)
            update { copy(isNavigationStarted = true) }
    }
    fun clearRoute() {
        routeGeneration++ // Invalidate even a pending initial load when activeRoute is already null.
        update { copy(activeRoute = null, freePlan = null, arrival = ArrivalSnapshot(), activePrescribedRouteId = null,
            activePrescribedRouteName = null, isNavigationStarted = false, isRouteLoading = false) }
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
            guidance = if (!state.navigationActive) GuidanceUiState() else GuidanceUiState(GuidanceStatus.WAITING_LOCATION, "位置情報を確認中"),
            highwayGuidance = null,
            deviation = if (!state.navigationActive) DeviationUiState() else deviationUiState(deviation, state.navigationMode),
            deviationSnapshot = deviation,
            arrival = if (state.arrival.state == ArrivalState.ARRIVED) state.arrival else ArrivalSnapshot(),
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
        if (!state.navigationActive || route == null || location == null || state.locationPermissionState != LocationPermissionState.Granted ||
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
            val arrival = state.freePlan?.let { plan ->
                arrivalDetector.update(_uiState.value.arrival, location, plan.destination, progress.remainingRouteMeters,
                    progress.isProjectionReliable, elapsedMillis())
            } ?: ArrivalSnapshot()
            emitTransitions()
            _uiState.value = _uiState.value.copy(guidance = display, highwayGuidance = HighwayInstructionFormatter.format(highway),
                deviation = deviationUiState(deviation, state.navigationMode), deviationSnapshot = deviation, arrival = arrival)
        }
    }

    private fun update(transform: NavigationUiState.() -> NavigationUiState) {
        val before = _uiState.value
        val after = before.transform()
        _uiState.value = after
        if (before.activeRoute !== after.activeRoute) {
            _uiState.value = _uiState.value.copy(arrival = ArrivalSnapshot())
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
        if (before.location != after.location || before.isNavigationStarted != after.isNavigationStarted ||
            before.locationPermissionState != after.locationPermissionState || before.locationError != after.locationError) refreshGuidance()
    }
}
