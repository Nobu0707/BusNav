package net.nobu0707.busnav.ui.detour

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.nobu0707.busnav.domain.detour.*
import net.nobu0707.busnav.domain.traffic.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.RouteDeviationState
import net.nobu0707.busnav.domain.prescribed.NavigationMode
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.ui.navigation.*
import net.nobu0707.busnav.ui.routeplan.EditorCamera
import net.nobu0707.busnav.ui.routeplan.EditorCameraRequest
import net.nobu0707.busnav.ui.routing.*
import java.util.UUID

enum class DetourMapMode { NONE, REJOIN, VIA, SHAPING }
data class DetourUiState(
    val trafficContext: TrafficDetourContext? = null,
    val trafficValidation: TrafficDetourValidation? = null,
    val stage: DetourSessionState = DetourSessionState.IDLE,
    val candidates: List<RejoinTarget> = emptyList(),
    val target: RejoinTarget? = null,
    val points: List<DetourDraftPoint> = emptyList(),
    val candidate: DetourCandidate? = null,
    val anchorProgressMeters: Double? = null,
    val vehicleProfile: VehicleProfile? = null,
    val locked: Boolean = false,
    val preparing: Boolean = false,
    val mapMode: DetourMapMode = DetourMapMode.NONE,
    val error: String? = null,
    val cameraRequest: EditorCameraRequest? = null,
)

/** Ephemeral planning state. Navigation owns active guidance and both matching streams.
 * Every mutation is guarded here as well as in the UI; only calculate invokes the engine.
 */
class DetourStateHolder(
    engine: RoutingEngine,
    private val navigation: NavigationStateHolder,
    private val scope: CoroutineScope,
    private val elapsedMillis: () -> Long,
    val config: DetourConfig = DetourConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val diagnostics: (String) -> Unit = {},
    private val trafficSnapshot: () -> TrafficSnapshot = { TrafficSnapshot(NoOpTrafficInformationProvider().source, TrafficProviderStatus.NOT_CONFIGURED) },
    private val epochMillis: () -> Long = System::currentTimeMillis,
    private val trafficUpdates: StateFlow<net.nobu0707.busnav.ui.traffic.TrafficUiState>? = null,
) {
    private data class Session(val token: Long, val id: String, val route: ScheduledRoute,
        val profile: VehicleProfile, val anchor: Double, val reason: DetourReason, val trafficContext: TrafficDetourContext?)
    private val _state = MutableStateFlow(DetourUiState())
    val state = _state.asStateFlow()
    private val calculation = RouteCalculationStateHolder(engine, scope)
    private var session: Session? = null
    private var generator: RejoinCandidateGenerator? = null
    private var revision = 0L
    private var prepareJob: Job? = null
    private var draft: DetourDraft? = null
    private var completionId = navigation.uiState.value.rejoinCompletedId
    var camera: EditorCamera? = null
        private set

    init {
        scope.launch {
            trafficUpdates?.collect {
                val candidate = _state.value.candidate
                if (candidate != null && _state.value.stage == DetourSessionState.PREVIEW) {
                    val validation = withContext(dispatcher) { TrafficDetourValidator().validate(candidate.route, trafficSnapshot(), epochMillis()) }
                    if (_state.value.candidate === candidate) {
                        if (_state.value.trafficValidation?.conflict != validation.conflict && validation.conflict != TrafficDetourConflict.NONE)
                            diagnostics("traffic.detour.conflict")
                        _state.value = _state.value.copy(trafficValidation = validation)
                    }
                }
            }
        }
        scope.launch {
            navigation.uiState.collect { nav ->
                if (session != null && !sessionCurrent()) {
                    reset()
                    return@collect
                }
                if (nav.rejoinCompletedId != completionId) {
                    completionId = nav.rejoinCompletedId
                    invalidate()
                    session = null
                    generator = null
                    _state.value = DetourUiState(stage = DetourSessionState.COMPLETED)
                } else {
                    _state.value = _state.value.copy(locked = config.editingLocked(nav.location?.speedMetersPerSecond))
                }
            }
        }
        scope.launch {
            calculation.state.collect { result ->
                if (_state.value.stage != DetourSessionState.CALCULATING || !sessionCurrent()) return@collect
                when (result) {
                    is RouteCalculationState.Success -> if (result.planRevision == revision) {
                        val candidate = DetourCandidate(UUID.randomUUID().toString(), requireNotNull(draft), result.route, result.summary)
                        val validation = withContext(dispatcher) { TrafficDetourValidator().validate(candidate.route, trafficSnapshot(), epochMillis()) }
                        if (result.planRevision != revision || !sessionCurrent()) return@collect
                        _state.value = _state.value.copy(stage = DetourSessionState.PREVIEW, candidate = candidate,
                            trafficValidation = validation,
                            cameraRequest = EditorCameraRequest(revision, result.route.geometry.points))
                        if (validation.conflict != TrafficDetourConflict.NONE) diagnostics("traffic.detour.conflict")
                        diagnostics("detour.preview.ready")
                    }
                    is RouteCalculationState.Failure -> if (result.planRevision == revision) {
                        _state.value = _state.value.copy(stage = DetourSessionState.FAILED,
                            error = if (result.reason == RoutingFailure.NO_ROUTE) "この復帰地点への大型車経路を見つけられませんでした"
                            else result.reason.userMessage())
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun sessionCurrent(): Boolean {
        val s = session ?: return false
        val nav = navigation.uiState.value
        return nav.navigationActive && nav.navigationMode == NavigationMode.PRESCRIBED &&
            nav.prescribedSessionToken == s.token && nav.activePrescribedRouteId == s.id &&
            nav.prescribedRouteSnapshot === s.route && nav.prescribedVehicleProfile == s.profile
    }

    private fun locationProblem(): String? {
        val nav = navigation.uiState.value
        return when {
            nav.locationPermissionState != LocationPermissionState.Granted -> "位置情報の利用を許可してください"
            nav.locationError != null -> nav.locationError
            else -> config.locationQuality.locationProblem(nav.location, elapsedMillis())
        }
    }

    private fun editable(): Boolean {
        if (!sessionCurrent()) { reset(); return false }
        if (config.editingLocked(navigation.uiState.value.location?.speedMetersPerSecond)) {
            _state.value = _state.value.copy(locked = true, error = SAFETY_MESSAGE)
            return false
        }
        return true
    }

    fun begin(reason: DetourReason? = null, trafficContext: TrafficDetourContext? = null): Boolean {
        val nav = navigation.uiState.value
        if (!nav.navigationActive || nav.navigationMode != NavigationMode.PRESCRIBED ||
            nav.activePrescribedRouteId == null || nav.prescribedRouteSnapshot == null || nav.prescribedVehicleProfile == null) return false
        val anchor = nav.lastReliablePrescribedProgress
        val problem = locationProblem() ?: when {
            config.editingLocked(nav.location?.speedMetersPerSecond) -> SAFETY_MESSAGE
            anchor == null -> "所定経路上の位置を確認してから迂回を設定してください"
            elapsedMillis() - anchor.timestampMillis !in 0..config.maxAnchorAgeMillis -> "所定経路上の最終位置が古いため迂回を開始できません"
            else -> null
        }
        if (problem != null) { _state.value = _state.value.copy(error = problem); return false }
        invalidate()
        val current = Session(nav.prescribedSessionToken, nav.activePrescribedRouteId, nav.prescribedRouteSnapshot,
            nav.prescribedVehicleProfile, requireNotNull(anchor).progressMeters,
            reason ?: if (nav.deviationSnapshot.state == RouteDeviationState.OFF_ROUTE) DetourReason.OFF_ROUTE_RECOVERY else DetourReason.MANUAL, trafficContext)
        session = current
        generator = null
        val request = revision
        _state.value = DetourUiState(stage = DetourSessionState.SELECTING_REJOIN, preparing = true,
            trafficContext = trafficContext,
            anchorProgressMeters = current.anchor, vehicleProfile = current.profile)
        prepareJob = scope.launch {
            val prepared = withContext(dispatcher) { RejoinCandidateGenerator(current.route, current.anchor, config,
                minimumSafeRejoinProgress = trafficContext?.minimumSafeRejoinProgress) }
            if (revision != request || !sessionCurrent()) return@launch
            generator = prepared
            val candidates = prepared.generate()
            _state.value = _state.value.copy(preparing = false, candidates = candidates,
                cameraRequest = EditorCameraRequest(request, candidates.map { it.point } + requireNotNull(nav.location).point))
        }
        diagnostics("detour.session.started")
        return true
    }

    fun selectTarget(id: String) {
        if (!editable()) return
        val target = _state.value.candidates.firstOrNull { it.id == id } ?: return
        select(target)
    }
    private fun select(target: RejoinTarget) {
        invalidate()
        _state.value = _state.value.copy(stage = DetourSessionState.EDITING, target = target,
            candidate = null, mapMode = DetourMapMode.NONE, error = null)
        diagnostics("detour.target.selected")
    }
    fun selectMapMode(mode: DetourMapMode) {
        if (!editable() || generator == null) return
        if (mode != DetourMapMode.REJOIN && _state.value.target == null) return
        invalidate()
        _state.value = _state.value.copy(stage = if (mode == DetourMapMode.REJOIN) DetourSessionState.SELECTING_REJOIN else DetourSessionState.EDITING,
            mapMode = mode, candidate = null, cameraRequest = null, error = null)
    }
    fun setCursor(point: GeoPoint) {
        if (!editable()) return
        when (_state.value.mapMode) {
            DetourMapMode.REJOIN -> {
                val result = generator?.manual(point) ?: return
                if (result.target != null) select(result.target)
                else _state.value = _state.value.copy(error = result.error)
            }
            DetourMapMode.VIA, DetourMapMode.SHAPING -> {
                val type = if (_state.value.mapMode == DetourMapMode.VIA) DetourDraftPointType.VIA else DetourDraftPointType.SHAPING
                invalidate()
                _state.value = _state.value.copy(points = _state.value.points + DetourDraftPoint(UUID.randomUUID().toString(), type, point),
                    candidate = null, mapMode = DetourMapMode.NONE, error = null)
            }
            DetourMapMode.NONE -> Unit
        }
    }
    fun removePoint(id: String) {
        if (!editable()) return
        invalidate()
        _state.value = _state.value.copy(points = _state.value.points.filterNot { it.id == id },
            stage = DetourSessionState.EDITING, candidate = null, error = null)
    }
    fun movePoint(id: String, delta: Int) {
        if (!editable()) return
        val points = _state.value.points.toMutableList()
        val index = points.indexOfFirst { it.id == id }
        if (index < 0 || index + delta !in points.indices) return
        points.add(index + delta, points.removeAt(index))
        invalidate()
        _state.value = _state.value.copy(points = points, stage = DetourSessionState.EDITING, candidate = null)
    }
    fun edit() {
        if (!editable()) return
        invalidate()
        _state.value = _state.value.copy(stage = DetourSessionState.EDITING, candidate = null, error = null)
    }
    fun calculate(): Boolean {
        if (_state.value.stage !in listOf(DetourSessionState.EDITING, DetourSessionState.FAILED) || !editable()) return false
        val target = _state.value.target ?: return false
        val s = session ?: return false
        val problem = locationProblem()
        if (problem != null) { _state.value = _state.value.copy(error = problem); return false }
        invalidate()
        // Raw GPS is sampled at the explicit calculate action, never taken from a projection.
        draft = DetourDraft(s.id, s.reason, requireNotNull(navigation.uiState.value.location).point, s.anchor, target, _state.value.points.toList(), s.trafficContext)
        _state.value = _state.value.copy(stage = DetourSessionState.CALCULATING, candidate = null, error = null, mapMode = DetourMapMode.NONE)
        val accepted = calculation.calculate(requireNotNull(draft).toRoutePlan("detour-" + UUID.randomUUID()), revision, s.profile)
        if (!accepted) _state.value = _state.value.copy(stage = DetourSessionState.FAILED, error = "復帰地点と経由地を確認してください")
        else diagnostics("detour.calculation.started")
        return accepted
    }
    fun activate(): Boolean {
        if (_state.value.stage != DetourSessionState.PREVIEW || !editable()) return false
        locationProblem()?.let { _state.value = _state.value.copy(error = it); return false }
        val candidate = _state.value.candidate ?: return false
        val validation = TrafficDetourValidator().validate(candidate.route, trafficSnapshot(), epochMillis())
        _state.value = _state.value.copy(trafficValidation = validation)
        if (!validation.activationAllowed) {
            diagnostics("traffic.detour.conflict")
            _state.value = _state.value.copy(error = validation.message)
            return false
        }
        if (!navigation.activateDetour(candidate, requireNotNull(session).token, config.rejoin)) return false
        invalidate()
        _state.value = _state.value.copy(stage = DetourSessionState.ACTIVE, mapMode = DetourMapMode.NONE, cameraRequest = null)
        return true
    }
    /** Closing a replan leaves the previous active detour intact. Ending it is a separate action. */
    fun cancelPlanning() {
        invalidate()
        if (navigation.uiState.value.activeDetour != null && sessionCurrent()) {
            _state.value = _state.value.copy(stage = DetourSessionState.ACTIVE,
                candidate = navigation.uiState.value.activeDetour!!.candidate, mapMode = DetourMapMode.NONE, error = null)
        } else reset()
        diagnostics("detour.cancelled")
    }
    fun endDetour() { navigation.cancelActiveDetour(); reset() }
    fun clearCompleted() { if (_state.value.stage == DetourSessionState.COMPLETED) reset() }
    fun saveCamera(value: EditorCamera) { camera = value }
    fun cameraApplied(id: Long) { if (_state.value.cameraRequest?.id == id) _state.value = _state.value.copy(cameraRequest = null) }
    private fun invalidate() { revision++; prepareJob?.cancel(); calculation.cancel(); draft = null }
    private fun reset() { invalidate(); session = null; generator = null; _state.value = DetourUiState() }
    companion object { const val SAFETY_MESSAGE = "安全な場所に停車して迂回経路を設定してください" }
}
