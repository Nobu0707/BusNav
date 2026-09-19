package net.nobu0707.busnav.ui.routing

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry
import net.nobu0707.busnav.domain.route.RoutePoint
import net.nobu0707.busnav.domain.route.RoutePointType
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.RoutePlanPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routing.RoutingEngine
import net.nobu0707.busnav.domain.routing.RoutingFailure
import net.nobu0707.busnav.domain.routing.RoutingResult
import net.nobu0707.busnav.domain.routing.RoutingSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCalculationStateHolderTest {
    @Test
    fun `invalid plan does not call engine`() = runTest {
        var calls = 0
        val holder = RouteCalculationStateHolder(RoutingEngine { calls++; success() }, this)
        assertFalse(holder.calculate(RoutePlan("empty"), 0))
        assertEquals(0, calls)
        assertEquals(RouteCalculationState.Idle, holder.state.value)
    }

    @Test
    fun `valid plan progresses through calculating and success`() = runTest {
        val holder = RouteCalculationStateHolder(RoutingEngine { success() }, this)
        assertTrue(holder.calculate(plan(), 3))
        assertEquals(RouteCalculationState.Calculating(3), holder.state.value)
        advanceUntilIdle()
        val state = holder.state.value as RouteCalculationState.Success
        assertEquals(3, state.planRevision)
        assertEquals(route(), state.route)
        assertEquals(route(), holder.currentCandidate(3))
        assertNull(holder.currentCandidate(4))
    }

    @Test
    fun `failure can be retried`() = runTest {
        var calls = 0
        val holder = RouteCalculationStateHolder(
            RoutingEngine {
                calls++
                if (calls == 1) RoutingResult.Failure(RoutingFailure.NO_ROUTE) else success()
            },
            this,
        )
        holder.calculate(plan(), 1)
        advanceUntilIdle()
        assertEquals(RoutingFailure.NO_ROUTE, (holder.state.value as RouteCalculationState.Failure).reason)
        holder.calculate(plan(), 1)
        advanceUntilIdle()
        assertTrue(holder.state.value is RouteCalculationState.Success)
    }

    @Test
    fun `new request cancels old calculation`() = runTest {
        var starts = 0
        var cancellations = 0
        val holder = RouteCalculationStateHolder(
            RoutingEngine {
                starts++
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { cancellations++ }
                }
            },
            this,
        )
        holder.calculate(plan(), 1)
        runCurrent()
        holder.calculate(plan(), 1)
        runCurrent()
        assertEquals(2, starts)
        assertEquals(1, cancellations)
        holder.cancel()
    }

    @Test
    fun `plan revision change cancels calculating and makes success stale`() = runTest {
        val holder = RouteCalculationStateHolder(RoutingEngine { success() }, this)
        holder.calculate(plan(), 5)
        advanceUntilIdle()
        holder.onPlanChanged(6)
        assertNull(holder.currentCandidate(6))
        assertTrue(holder.state.value is RouteCalculationState.Success)
    }

    private fun success() = RoutingResult.Success(route(), RoutingSummary(1_000.0, 60.0))

    private fun plan() = RoutePlan(
        id = "plan",
        points = listOf(
            RoutePlanPoint("s", RoutePlanPointType.START, GeoPoint(35.0, 139.0)),
            RoutePlanPoint("d", RoutePlanPointType.DESTINATION, GeoPoint(35.1, 139.1)),
        ),
    )

    private fun route() = ScheduledRoute(
        id = "route",
        name = "Route",
        geometry = RouteGeometry(listOf(GeoPoint(35.0, 139.0), GeoPoint(35.1, 139.1))),
        points = listOf(
            RoutePoint("s", RoutePointType.START, GeoPoint(35.0, 139.0)),
            RoutePoint("d", RoutePointType.DESTINATION, GeoPoint(35.1, 139.1)),
        ),
    )
}
