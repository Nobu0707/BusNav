package net.nobu0707.busnav.domain.routeplan

import net.nobu0707.busnav.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlanOperationsTest {
    private var id = 0
    private val operations = RoutePlanOperations(RoutePlanPointIdGenerator { "point-${++id}" })
    private val empty = RoutePlan(id = "plan")
    private val tokyo = GeoPoint(35.6812, 139.7671)
    private val yokohama = GeoPoint(35.4658, 139.6223)
    private val kawasaki = GeoPoint(35.5308, 139.7029)

    @Test fun `empty plan is valid editable state`() {
        assertTrue(empty.points.isEmpty())
        assertFalse(empty.validateForRouting().isRoutingReady)
    }

    @Test fun `set start adds start first`() {
        val plan = operations.setStart(empty, tokyo)
        assertEquals(RoutePlanPointType.START, plan.points.single().type)
    }

    @Test fun `setting start again replaces existing start`() {
        val plan = operations.setStart(operations.setStart(empty, tokyo), yokohama)
        assertEquals(1, plan.points.count { it.type == RoutePlanPointType.START })
        assertEquals(yokohama, plan.points.first().position)
    }

    @Test fun `setting destination again replaces existing destination`() {
        val first = operations.setDestination(empty, tokyo)
        val plan = operations.setDestination(first, yokohama)
        assertEquals(1, plan.points.count { it.type == RoutePlanPointType.DESTINATION })
        assertEquals(yokohama, plan.points.last().position)
    }

    @Test fun `via and shaping are inserted before destination`() {
        var plan = operations.setStart(empty, tokyo)
        plan = operations.setDestination(plan, yokohama)
        plan = operations.addVia(plan, kawasaki)
        plan = operations.addShaping(plan, GeoPoint(35.55, 139.68))
        assertEquals(
            listOf(RoutePlanPointType.START, RoutePlanPointType.VIA, RoutePlanPointType.SHAPING, RoutePlanPointType.DESTINATION),
            plan.points.map { it.type },
        )
    }

    @Test fun `removing intermediate leaves endpoints`() {
        var plan = readyPlan()
        plan = operations.addVia(plan, kawasaki)
        val via = plan.points.single { it.type == RoutePlanPointType.VIA }
        plan = operations.removePoint(plan, via.id)
        assertEquals(listOf(RoutePlanPointType.START, RoutePlanPointType.DESTINATION), plan.points.map { it.type })
    }

    @Test fun `via changes to shaping and back`() {
        var plan = operations.addVia(readyPlan(), kawasaki)
        val id = plan.points.single { it.type == RoutePlanPointType.VIA }.id
        plan = operations.changePointType(plan, id, RoutePlanPointType.SHAPING)
        assertEquals(RoutePlanPointType.SHAPING, plan.points.first { it.id == id }.type)
        plan = operations.changePointType(plan, id, RoutePlanPointType.VIA)
        assertEquals(RoutePlanPointType.VIA, plan.points.first { it.id == id }.type)
    }

    @Test fun `endpoint type change is rejected`() {
        val plan = readyPlan()
        val start = plan.points.first()
        assertEquals(plan, operations.changePointType(plan, start.id, RoutePlanPointType.VIA))
        val via = operations.addVia(plan, kawasaki)
        val viaId = via.points.single { it.type == RoutePlanPointType.VIA }.id
        assertEquals(via, operations.changePointType(via, viaId, RoutePlanPointType.START))
    }

    @Test fun `intermediate points reorder while endpoints stay fixed`() {
        var plan = operations.addVia(readyPlan(), kawasaki)
        plan = operations.addShaping(plan, GeoPoint(35.55, 139.68))
        val shaping = plan.points.single { it.type == RoutePlanPointType.SHAPING }
        plan = operations.movePoint(plan, shaping.id, 0)
        assertEquals(RoutePlanPointType.START, plan.points.first().type)
        assertEquals(RoutePlanPointType.SHAPING, plan.points[1].type)
        assertEquals(RoutePlanPointType.DESTINATION, plan.points.last().type)
    }

    @Test fun `moving endpoints is rejected`() {
        val plan = readyPlan()
        assertEquals(plan, operations.movePoint(plan, plan.points.first().id, 1))
        assertEquals(plan, operations.movePoint(plan, plan.points.last().id, 0))
    }

    @Test fun `clear intermediates preserves endpoints`() {
        val plan = operations.addShaping(operations.addVia(readyPlan(), kawasaki), GeoPoint(35.55, 139.68))
        assertEquals(2, operations.clearIntermediatePoints(plan).points.size)
    }

    @Test fun `clear plan preserves identity but removes all points`() {
        val cleared = operations.clearPlan(readyPlan())
        assertEquals("plan", cleared.id)
        assertTrue(cleared.points.isEmpty())
    }

    @Test fun `routing request is only created for ready plan`() {
        assertTrue(empty.toRoutingRequest() is RoutingRequestResult.Invalid)
        val ready = operations.addVia(readyPlan(), kawasaki).toRoutingRequest()
        assertTrue(ready is RoutingRequestResult.Ready)
        assertEquals(1, (ready as RoutingRequestResult.Ready).request.intermediatePoints.size)
    }

    private fun readyPlan(): RoutePlan =
        operations.setDestination(operations.setStart(empty, tokyo), yokohama)
}
