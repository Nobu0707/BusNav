package net.nobu0707.busnav.domain.routeplan

import net.nobu0707.busnav.domain.model.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlanValidationTest {
    private val start = RoutePlanPoint("start", RoutePlanPointType.START, GeoPoint(35.0, 139.0))
    private val destination = RoutePlanPoint("destination", RoutePlanPointType.DESTINATION, GeoPoint(36.0, 140.0))
    private val via = RoutePlanPoint("via", RoutePlanPointType.VIA, GeoPoint(35.4, 139.4))
    private val shaping = RoutePlanPoint("shaping", RoutePlanPointType.SHAPING, GeoPoint(35.6, 139.6))

    @Test fun `empty is not ready`() = assertFalse(plan().validateForRouting().isRoutingReady)
    @Test fun `start only is not ready`() = assertFalse(plan(start).validateForRouting().isRoutingReady)
    @Test fun `destination only is not ready`() = assertFalse(plan(destination).validateForRouting().isRoutingReady)
    @Test fun `start and destination are ready`() = assertTrue(plan(start, destination).validateForRouting().isRoutingReady)
    @Test fun `via between endpoints is ready`() = assertTrue(plan(start, via, destination).validateForRouting().isRoutingReady)
    @Test fun `shaping between endpoints is ready`() = assertTrue(plan(start, shaping, destination).validateForRouting().isRoutingReady)
    @Test fun `destination before start is not ready`() = assertFalse(plan(destination, start).validateForRouting().isRoutingReady)
    @Test fun `intermediate outside endpoints is not ready`() = assertFalse(plan(via, start, destination).validateForRouting().isRoutingReady)
    @Test fun `duplicate ids are not ready`() = assertFalse(plan(start, destination.copy(id = "start")).validateForRouting().isRoutingReady)

    private fun plan(vararg points: RoutePlanPoint) = RoutePlan("plan", points = points.toList())
}
