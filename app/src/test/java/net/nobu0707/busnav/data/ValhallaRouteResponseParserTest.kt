package net.nobu0707.busnav.data.routing.valhalla

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.freeNavigationStartPosition
import net.nobu0707.busnav.domain.route.RoutePointType
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routeplan.RoutingRequest
import net.nobu0707.busnav.domain.routeplan.RoutingRequestPoint
import net.nobu0707.busnav.domain.routing.VehicleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ValhallaRouteResponseParserTest {
    @Test
    fun `actual Valhalla 3 9 response maps to scheduled route`() {
        val diagnostics = RecordingRoutingDiagnostics()
        val success = parser(diagnostics).parse(validRequest(), longFixture())

        assertEquals(88_881.0, success.summary.distanceMeters, 0.001)
        assertEquals(7_188.89, success.summary.durationSeconds, 0.001)
        assertEquals(3_075, success.route.geometry.points.size)
        assertTrue(success.route.geometry.points.all { point ->
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
        })
        assertEquals(1, success.route.points.count { it.type == RoutePointType.START })
        assertEquals(1, success.route.points.count { it.type == RoutePointType.DESTINATION })
        val rawStart = validRequest().points.first().position
        assertEquals(rawStart, success.route.start.position)
        val routedStart = freeNavigationStartPosition(rawStart, success.route)
        assertNotNull(routedStart)
        assertEquals(success.route.geometry.first, routedStart!!.navigationStartPoint)
        assertTrue(routedStart.snapDistanceMeters in 100.0..300.0)
        assertTrue(diagnostics.debugEntries.any {
            it.event == "parse.shape" && it.details.contains("encodedLength=12208")
        })
        assertTrue(diagnostics.debugEntries.any {
            it.event == "parse.geometry" && it.details == "pointCount=3075"
        })
    }

    @Test
    fun `empty body reports empty body stage`() {
        assertStage(ValhallaResponseStage.EMPTY_BODY, "")
    }

    @Test
    fun `malformed json and missing trip report their stages`() {
        assertStage(ValhallaResponseStage.JSON_DECODE, "not-json")
        assertStage(ValhallaResponseStage.TRIP, "{}")
    }

    @Test
    fun `invalid summary empty legs and missing shape report their stages`() {
        assertStage(
            ValhallaResponseStage.SUMMARY,
            """{"trip":{"summary":{"length":-1,"time":2},"legs":[{"shape":"_izlhA~rlgdF"}]}}""",
        )
        assertStage(
            ValhallaResponseStage.LEGS,
            """{"trip":{"summary":{"length":1,"time":2},"legs":[]}}""",
        )
        assertStage(
            ValhallaResponseStage.SHAPE,
            """{"trip":{"summary":{"length":1,"time":2},"legs":[{}]}}""",
        )
    }

    @Test
    fun `malformed polyline reports polyline stage`() {
        assertStage(
            ValhallaResponseStage.POLYLINE,
            """{"trip":{"summary":{"length":1,"time":2},"legs":[{"shape":"_"}]}}""",
        )
    }

    @Test
    fun `scheduled route invariant failure reports route construction stage`() {
        val request = validRequest().copy(
            points = listOf(
                RoutingRequestPoint(
                    id = "",
                    position = GeoPoint(35.0, 138.0),
                    type = RoutePlanPointType.START,
                    name = null,
                ),
                RoutingRequestPoint(
                    id = "destination",
                    position = GeoPoint(35.1, 138.1),
                    type = RoutePlanPointType.DESTINATION,
                    name = null,
                ),
            ),
        )
        assertStage(
            expected = ValhallaResponseStage.ROUTE_CONSTRUCTION,
            body = shortFixture(),
            request = request,
        )
    }

    private fun assertStage(
        expected: ValhallaResponseStage,
        body: String,
        request: RoutingRequest = validRequest(),
    ) {
        try {
            parser().parse(request, body)
            fail("Expected ValhallaResponseException")
        } catch (error: ValhallaResponseException) {
            assertEquals(expected, error.stage)
            assertTrue(error.message.orEmpty().isNotBlank())
        }
    }

    private fun parser(diagnostics: RoutingDiagnostics = NoOpRoutingDiagnostics) =
        ValhallaRouteResponseParser(
            routeIdFactory = { "result" },
            diagnostics = diagnostics,
        )

    private fun validRequest() = RoutingRequest(
        routePlanId = "failing-route",
        routeName = "Valhalla 3.9 regression",
        points = listOf(
            RoutingRequestPoint(
                id = "start",
                position = GeoPoint(35.52755965924169, 138.79653353327427),
                type = RoutePlanPointType.START,
                name = null,
            ),
            RoutingRequestPoint(
                id = "destination",
                position = GeoPoint(35.609542457517534, 138.29084069799353),
                type = RoutePlanPointType.DESTINATION,
                name = null,
            ),
        ),
        vehicleProfile = VehicleProfile.DEVELOPMENT_LARGE_BUS,
    )

    private fun longFixture(): String =
        requireNotNull(javaClass.getResource("/valhalla/route-88km-valhalla-3.9.0.json")).readText()

    private fun shortFixture() =
        """{"trip":{"summary":{"length":1,"time":2},"legs":[{"shape":"_izlhA~rlgdF_{geC~ywl@"}]}}"""
}
