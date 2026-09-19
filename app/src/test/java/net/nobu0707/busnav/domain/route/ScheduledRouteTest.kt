package net.nobu0707.busnav.domain.route

import net.nobu0707.busnav.data.route.createDevelopmentSampleRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledRouteTest {
    @Test
    fun `development sample has expected identity and endpoints`() {
        val route = createDevelopmentSampleRoute()

        assertEquals("development-sample-route", route.id)
        assertEquals("開発用サンプルルート", route.name)
        assertEquals(RoutePointType.START, route.start.type)
        assertEquals(RoutePointType.DESTINATION, route.destination.type)
        assertEquals(route.geometry.first, route.start.position)
        assertEquals(route.geometry.last, route.destination.position)
        assertTrue(route.points.any { it.type == RoutePointType.STOP })
    }
}
