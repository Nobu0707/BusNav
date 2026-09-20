package net.nobu0707.busnav.data.routing.valhalla

import kotlinx.serialization.json.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.VehicleProfile
import org.junit.Assert.*
import org.junit.Test

class ValhallaGuidanceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val request = RoutingRequest("test", "test", listOf(
        RoutingRequestPoint("s", GeoPoint(0.0, 0.0), RoutePlanPointType.START, null),
        RoutingRequestPoint("d", GeoPoint(0.0, 0.004), RoutePlanPointType.DESTINATION, null)), VehicleProfile.DEVELOPMENT_LARGE_BUS)
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/valhalla/maneuvers-" + name + "-3.9.0.json")).readText()
    private fun parse(body: String) = ValhallaRouteResponseParser().parse(request, body).route
    private fun leg(shape: String, indices: List<Pair<Int, Int>>) = buildJsonObject {
        put("shape", shape)
        putJsonArray("maneuvers") { indices.forEach { (begin, end) -> add(buildJsonObject {
            put("type", 10); put("begin_shape_index", begin); put("end_shape_index", end)
        }) } }
    }
    private fun body(legs: List<JsonObject>) = buildJsonObject { putJsonObject("trip") {
        putJsonObject("summary") { put("length", 1); put("time", 10) }
        put("legs", JsonArray(legs))
    } }.toString()
    // Polyline6 for (0,0)-(0,.001)-(0,.002), then shared or disjoint next leg.
    private val first = "???o}@?o}@"
    private val shared = "?_|B?o}@?o}@"
    private val disjoint = "?ozD?o}@?o}@"

    @Test fun realLocalResponseDeserializesAndIgnoresUnknownFields() {
        val raw = json.decodeFromString<ValhallaRouteResponse>(fixture("local"))
        assertEquals(8, raw.trip!!.legs.flatMap { it.maneuvers }.size)
        val route = parse(fixture("local"))
        assertEquals(ManeuverType.START, route.guidance!!.maneuvers.first().type)
        assertEquals(ManeuverType.DESTINATION, route.guidance.maneuvers.last().type)
        assertTrue(route.guidance.maneuvers.all { it.beginGeometryIndex in route.geometry.points.indices && it.endGeometryIndex in route.geometry.points.indices })
    }
    @Test fun realHighwayResponseParses() {
        val route = parse(fixture("highway"))
        assertEquals(16, route.guidance!!.maneuvers.size)
        assertTrue(route.guidance.maneuvers.any { it.type == ManeuverType.RAMP_RIGHT })
        assertTrue(route.guidance.maneuvers.any { it.type == ManeuverType.EXIT_LEFT })
        assertEquals(4, route.guidance.maneuvers.count { it.signs.isNotEmpty() })
    }
    @Test fun representativeRawTypesMapWithoutLeakingIntegers() {
        val expected = listOf(ManeuverType.SLIGHT_RIGHT, ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT,
            ManeuverType.U_TURN_RIGHT, ManeuverType.U_TURN_LEFT, ManeuverType.SHARP_LEFT, ManeuverType.LEFT,
            ManeuverType.SLIGHT_LEFT, ManeuverType.RAMP_STRAIGHT, ManeuverType.RAMP_RIGHT, ManeuverType.RAMP_LEFT,
            ManeuverType.EXIT_RIGHT, ManeuverType.EXIT_LEFT, ManeuverType.KEEP_STRAIGHT, ManeuverType.KEEP_RIGHT,
            ManeuverType.KEEP_LEFT, ManeuverType.MERGE, ManeuverType.ROUNDABOUT, ManeuverType.ROUNDABOUT,
            ManeuverType.FERRY, ManeuverType.FERRY)
        expected.forEachIndexed { i, type -> assertEquals(type, mapManeuverType(i + 9)) }
        listOf(1,2,3).forEach { assertEquals(ManeuverType.START, mapManeuverType(it)) }
        listOf(4,5,6).forEach { assertEquals(ManeuverType.DESTINATION, mapManeuverType(it)) }
        listOf(7,8).forEach { assertEquals(ManeuverType.CONTINUE, mapManeuverType(it)) }
        listOf(37,38).forEach { assertEquals(ManeuverType.MERGE, mapManeuverType(it)) }
        assertEquals(ManeuverType.UNKNOWN, mapManeuverType(999))
    }
    @Test fun unknownTypeDoesNotInvalidateResponse() {
        val input = body(listOf(leg(first, listOf(0 to 2)))).replace(":10", ":999")
        assertEquals(ManeuverType.UNKNOWN, parse(input).guidance!!.maneuvers.single().type)
    }
    @Test fun singleLegIndicesStayLocal() {
        val m = parse(body(listOf(leg(first, listOf(1 to 2))))).guidance!!.maneuvers.single()
        assertEquals(1, m.beginGeometryIndex); assertEquals(2, m.endGeometryIndex)
    }
    @Test fun duplicateBoundaryMapsBothBeginAndEndToMergedAxis() {
        val route = parse(body(listOf(leg(first, listOf(0 to 2)), leg(shared, listOf(0 to 1, 1 to 2, 2 to 2)))))
        assertEquals(5, route.geometry.points.size)
        assertEquals(listOf(2,3,4), route.guidance!!.maneuvers.drop(1).map { it.beginGeometryIndex })
        assertEquals(listOf(3,4,4), route.guidance.maneuvers.drop(1).map { it.endGeometryIndex })
    }
    @Test fun nonDuplicateBoundaryUsesEntirePreviousPointCount() {
        val route = parse(body(listOf(leg(first, listOf(0 to 2)), leg(disjoint, listOf(0 to 2)))))
        assertEquals(6, route.geometry.points.size)
        assertEquals(3, route.guidance!!.maneuvers.last().beginGeometryIndex)
        assertEquals(5, route.guidance.maneuvers.last().endGeometryIndex)
    }
    @Test fun outOfRangeAndReversedIndicesAreRejectedWithoutClamp() {
        for (indices in listOf(-1 to 1, 0 to 3, 2 to 1)) {
            val e = assertThrows(ValhallaResponseException::class.java) { parse(body(listOf(leg(first, listOf(indices))))) }
            assertEquals(ValhallaResponseStage.MANEUVER_INDEX, e.stage)
        }
    }
    @Test fun syntheticSignFixtureKeepsTypesCountsAndStableDeduplication() {
        val text = requireNotNull(javaClass.getResource("/valhalla/maneuvers-signs-synthetic.json")).readText()
        val route = parse(text)
        val m = route.guidance!!.maneuvers.single()
        assertEquals(listOf(HighwaySignType.EXIT_NUMBER,HighwaySignType.EXIT_BRANCH,HighwaySignType.EXIT_TOWARD,HighwaySignType.EXIT_NAME),m.signs.map { it.type })
        assertEquals(2, m.signs.first().consecutiveCount)
        assertEquals("before", m.verbalPreTransitionInstruction)
        assertEquals("after", m.verbalPostTransitionInstruction)
        assertEquals(50.0, m.distanceMeters!!, 0.0)
    }
}
