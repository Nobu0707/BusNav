package net.nobu0707.busnav.ui.free

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.FreeNavigationPlan
import net.nobu0707.busnav.domain.prescribed.NavigationMode
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routing.RoutingEngine
import net.nobu0707.busnav.domain.routing.VehicleProfile
import net.nobu0707.busnav.ui.navigation.*
import net.nobu0707.busnav.ui.routeplan.EditorCamera
import net.nobu0707.busnav.ui.routeplan.EditorCameraRequest
import net.nobu0707.busnav.ui.routing.*
import java.util.UUID

enum class FreeNavigationStage { IDLE, SELECTING, CALCULATING, PREVIEW }
data class FreeNavigationUiState(
    val stage: FreeNavigationStage = FreeNavigationStage.IDLE,
    val plan: FreeNavigationPlan? = null,
    val calculation: RouteCalculationState = RouteCalculationState.Idle,
    val previewRoute: ScheduledRoute? = null,
    val isRecalculation: Boolean = false,
    val error: String? = null,
    val cameraRequest: EditorCameraRequest? = null,
    val vehicleProfile: VehicleProfile = VehicleProfile.DEVELOPMENT_LARGE_BUS,
)

/** One shared navigation location source; no location or library subscription of its own.
 * A recalculation candidate is separate from the active navigation route until explicit adoption.
 */
class FreeNavigationStateHolder(
    engine: RoutingEngine,
    private val navigation: NavigationStateHolder,
    scope: CoroutineScope,
    profile: VehicleProfile = VehicleProfile.DEVELOPMENT_LARGE_BUS,
) {
    private val calculation = RouteCalculationStateHolder(engine, scope, profile)
    private val _state = MutableStateFlow(FreeNavigationUiState(vehicleProfile = profile))
    val state = _state.asStateFlow()
    private var revision = 0L
    var camera: EditorCamera? = null
        private set

    init {
        scope.launch {
            calculation.state.collect { result ->
                val current = _state.value
                if (current.stage != FreeNavigationStage.CALCULATING) return@collect
                when (result) {
                    is RouteCalculationState.Success -> if (result.planRevision == revision) {
                        val route = result.route
                        if (!current.isRecalculation && !navigation.previewFreeRoute(requireNotNull(current.plan), route)) {
                            cancel()
                            return@collect
                        }
                        _state.value = current.copy(stage = FreeNavigationStage.PREVIEW, calculation = result,
                            previewRoute = route, cameraRequest = EditorCameraRequest(revision, route.geometry.points))
                    }
                    is RouteCalculationState.Failure -> if (result.planRevision == revision) {
                        _state.value = current.copy(stage = FreeNavigationStage.SELECTING, calculation = result, error = result.reason.userMessage())
                    }
                    else -> Unit
                }
            }
        }
    }

    fun beginSelection(): Boolean {
        if (!navigation.beginFreeSelection()) return false
        invalidate()
        _state.value = FreeNavigationUiState(stage = FreeNavigationStage.SELECTING, vehicleProfile = _state.value.vehicleProfile)
        return true
    }

    fun selectDestination(point: GeoPoint, name: String? = null) {
        if (_state.value.stage != FreeNavigationStage.SELECTING || _state.value.isRecalculation) return
        invalidate()
        _state.value = _state.value.copy(plan = FreeNavigationPlan(point, name), error = null,
            calculation = RouteCalculationState.Idle, previewRoute = null)
    }

    fun calculate(): Boolean {
        val current = _state.value
        if (current.stage != FreeNavigationStage.SELECTING) return false
        val plan = current.plan ?: return false
        val startFix = navigation.startLocationFix()
        val problem = navigation.startLocationProblem()
        if (problem != null || startFix == null) {
            _state.value = current.copy(error = problem ?: "現在地を更新中です")
            return false
        }
        invalidate()
        _state.value = current.copy(stage = FreeNavigationStage.CALCULATING, error = null,
            calculation = RouteCalculationState.Calculating(revision), previewRoute = null)
        // ONLY raw LocationState.point is read here. Matched projections cannot become START.
        val accepted = calculation.calculate(plan.toRoutePlan(startFix.point,
            "free-" + UUID.randomUUID()), revision, current.vehicleProfile)
        if (!accepted) _state.value = current.copy(error = "目的地を現在地から離れた地点に設定してください")
        return accepted
    }

    fun recalculate(): Boolean {
        val nav = navigation.uiState.value
        if (!nav.navigationActive || nav.navigationMode != NavigationMode.FREE || nav.freePlan == null ||
            _state.value.stage != FreeNavigationStage.IDLE) return false
        invalidate()
        _state.value = _state.value.copy(stage = FreeNavigationStage.SELECTING, plan = nav.freePlan,
            isRecalculation = true, previewRoute = null, error = null)
        return calculate()
    }

    fun start(): Boolean {
        val current = _state.value
        if (current.stage != FreeNavigationStage.PREVIEW) return false
        val route = current.previewRoute ?: return false
        val plan = current.plan ?: return false
        val adopted = if (current.isRecalculation) navigation.replaceFreeRoute(plan, route)
            else navigation.uiState.value.activeRoute === route && navigation.startFreeNavigation()
        if (adopted) {
            invalidate()
            _state.value = FreeNavigationUiState(vehicleProfile = current.vehicleProfile)
        }
        return adopted
    }

    fun changeDestination() {
        if (_state.value.isRecalculation) return
        invalidate()
        navigation.beginFreeSelection()
        _state.value = _state.value.copy(stage = FreeNavigationStage.SELECTING, previewRoute = null,
            calculation = RouteCalculationState.Idle, cameraRequest = null, error = null)
    }

    fun cancel() {
        val current = _state.value
        invalidate()
        if (!current.isRecalculation && !navigation.uiState.value.isNavigationStarted &&
            navigation.uiState.value.navigationMode == NavigationMode.FREE) navigation.clearRoute()
        _state.value = FreeNavigationUiState(vehicleProfile = current.vehicleProfile)
    }

    fun endNavigation() {
        cancel()
        navigation.clearRoute()
    }

    fun saveCamera(value: EditorCamera) { camera = value }
    fun cameraApplied(id: Long) {
        if (_state.value.cameraRequest?.id == id) _state.value = _state.value.copy(cameraRequest = null)
    }
    private fun invalidate() { revision++; calculation.cancel() }
}
