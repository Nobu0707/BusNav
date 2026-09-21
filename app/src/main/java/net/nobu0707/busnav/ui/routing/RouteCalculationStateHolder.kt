package net.nobu0707.busnav.ui.routing

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.RoutingRequestResult
import net.nobu0707.busnav.domain.routeplan.toRoutingRequest
import net.nobu0707.busnav.domain.routing.RoutingEngine
import net.nobu0707.busnav.domain.routing.RoutingFailure
import net.nobu0707.busnav.domain.routing.RoutingResult
import net.nobu0707.busnav.domain.routing.VehicleProfile

class RouteCalculationStateHolder(
    private val routingEngine: RoutingEngine,
    private val scope: CoroutineScope,
    private val vehicleProfile: VehicleProfile = VehicleProfile.DEVELOPMENT_LARGE_BUS,
) {
    private val _state = MutableStateFlow<RouteCalculationState>(RouteCalculationState.Idle)
    val state: StateFlow<RouteCalculationState> = _state.asStateFlow()
    private var calculationJob: Job? = null

    fun calculate(plan: RoutePlan, planRevision: Long, profile: VehicleProfile = vehicleProfile): Boolean {
        val request = when (val result = plan.toRoutingRequest(profile)) {
            is RoutingRequestResult.Invalid -> return false
            is RoutingRequestResult.Ready -> result.request
        }
        calculationJob?.cancel()
        _state.value = RouteCalculationState.Calculating(planRevision)
        calculationJob = scope.launch {
            try {
                when (val result = routingEngine.calculateRoute(request)) {
                    is RoutingResult.Success -> if (isCurrentCalculation(planRevision)) {
                        _state.value = RouteCalculationState.Success(planRevision, result.route, result.summary)
                    }
                    is RoutingResult.Failure -> if (isCurrentCalculation(planRevision)) {
                        _state.value = RouteCalculationState.Failure(planRevision, result.reason)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentCalculation(planRevision)) {
                    _state.value = RouteCalculationState.Failure(planRevision, RoutingFailure.NETWORK)
                }
            }
        }
        return true
    }

    fun onPlanChanged(currentRevision: Long) {
        val current = _state.value
        when {
            current is RouteCalculationState.Calculating && current.planRevision != currentRevision -> {
                calculationJob?.cancel()
                calculationJob = null
                _state.value = RouteCalculationState.Idle
            }
            current is RouteCalculationState.Failure && current.planRevision != currentRevision -> {
                _state.value = RouteCalculationState.Idle
            }
        }
    }

    fun currentCandidate(currentRevision: Long): ScheduledRoute? =
        (_state.value as? RouteCalculationState.Success)
            ?.takeIf { it.planRevision == currentRevision }
            ?.route

    fun cancel() {
        calculationJob?.cancel()
        calculationJob = null
        if (_state.value is RouteCalculationState.Calculating) _state.value = RouteCalculationState.Idle
    }

    private fun isCurrentCalculation(revision: Long): Boolean =
        (_state.value as? RouteCalculationState.Calculating)?.planRevision == revision
}
