package net.nobu0707.busnav.facility

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.nobu0707.busnav.data.facility.RouteFacilityPolyline
import net.nobu0707.busnav.data.facility.RouteFacilityResponseParser
import net.nobu0707.busnav.data.routing.valhalla.Polyline6Decoder
import net.nobu0707.busnav.domain.facility.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry
import net.nobu0707.busnav.ui.facility.RouteFacilityStateHolder
import org.junit.Assert.*
import org.junit.Test
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class RouteFacilityTest {
    private val route = RouteGeometry(listOf(GeoPoint(35.0, 139.0), GeoPoint(35.1, 139.1)))
    private val candidate = RouteFacilityCandidate(RouteFacilityId('N', 12), "海老名SA", RouteFacilityType.SERVICE_AREA,
        GeoPoint(35.05, 139.05), 8000.0, 240.0, DirectionConfidence.GEOMETRIC_CANDIDATE)

    @Test fun parserAcceptsValidRowsAndDropsMalformedOnes() {
        val rows = """{"version":1,"facilities":[
            {"osm_type":"N","osm_id":12,"name":"海老名SA","type":"SERVICE_AREA","lat":35.05,"lon":139.05,"route_progress_m":8000,"corridor_distance_m":240,"direction_confidence":"GEOMETRIC_CANDIDATE"},
            {"osm_type":"W","osm_id":13,"name":"PA","type":"PARKING_AREA","lat":35.06,"lon":139.06,"route_progress_m":9000,"corridor_distance_m":200,"direction_confidence":"GEOMETRIC_CANDIDATE"},
            {"osm_type":"N","osm_id":14,"type":"UNKNOWN","lat":35.0,"lon":139.0,"route_progress_m":100,"corridor_distance_m":100,"direction_confidence":"GEOMETRIC_CANDIDATE"},
            {"osm_type":"N","osm_id":15,"type":"SERVICE_AREA","lat":91,"lon":139.0,"route_progress_m":100,"corridor_distance_m":100,"direction_confidence":"GEOMETRIC_CANDIDATE"},
            {"osm_type":"N","osm_id":16,"type":"SERVICE_AREA","lat":35.0,"lon":139.0,"route_progress_m":-1,"corridor_distance_m":100,"direction_confidence":"GEOMETRIC_CANDIDATE"},
            {"osm_type":"N","osm_id":17,"type":"SERVICE_AREA","lat":"NaN","lon":139.0,"route_progress_m":100,"corridor_distance_m":100,"direction_confidence":"GEOMETRIC_CANDIDATE"}
        ]}"""
        assertEquals(listOf(RouteFacilityType.SERVICE_AREA, RouteFacilityType.PARKING_AREA),
            RouteFacilityResponseParser.parse(rows).map { it.type })
        assertTrue(RouteFacilityResponseParser.parse("""{"version":1,"facilities":[]}""").isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            RouteFacilityResponseParser.parse("""{"version":99,"facilities":[]}""")
        }
    }

    @Test fun polylineRoundtripBoundaryDedupAndFingerprint() {
        val a = RouteGeometry(listOf(GeoPoint(35.0, 139.0), GeoPoint(35.1, 139.1)))
        val b = RouteGeometry(listOf(GeoPoint(35.1, 139.1), GeoPoint(35.2, 139.2)))
        val joined = RouteFacilityPolyline.joinLegs(listOf(RouteFacilityPolyline.encode(a), RouteFacilityPolyline.encode(b)))
        assertEquals(3, joined.points.size)
        val encoded = RouteFacilityPolyline.encode(joined)
        assertEquals(joined.points, Polyline6Decoder.decode(encoded))
        assertEquals(RouteFacilityPolyline.fingerprint(encoded, RouteFacilityQueryOptions()),
            RouteFacilityPolyline.fingerprint(RouteFacilityPolyline.encode(joined), RouteFacilityQueryOptions()))
        assertNotEquals(RouteFacilityPolyline.fingerprint(encoded, RouteFacilityQueryOptions()),
            RouteFacilityPolyline.fingerprint(encoded, RouteFacilityQueryOptions(corridorMeters = 1300)))
    }

    @Test fun routeChangesQueryOnceAndGpsOnlyUpdatesLocalDistance() = runTest {
        var calls = 0
        val provider = object : RouteFacilityProvider {
            override suspend fun findFacilities(route: RouteGeometry, options: RouteFacilityQueryOptions): RouteFacilityQueryResult {
                calls++
                return RouteFacilityQueryResult.Success(listOf(candidate))
            }
        }
        val holder = RouteFacilityStateHolder(provider, backgroundScope)
        holder.activate(route); runCurrent()
        assertEquals(1, calls)
        repeat(100) { holder.updateProgress(1000.0 + it, true) }
        holder.activate(RouteGeometry(route.points)); runCurrent()
        assertEquals(1, calls)
        assertEquals(6901.0, holder.state.value.distances[candidate.id]?.distanceAheadMeters ?: 0.0, 0.01)
        holder.activate(RouteGeometry(route.points + GeoPoint(35.2, 139.2))); runCurrent()
        assertEquals(2, calls)
        holder.activate(route); runCurrent()
        assertEquals(2, calls) // Session cache
    }

    @Test fun staleRouteResponseIsIgnoredAndMovingEditsAreLocked() = runTest {
        val first = CompletableDeferred<RouteFacilityQueryResult>()
        var calls = 0
        val provider = object : RouteFacilityProvider {
            override suspend fun findFacilities(route: RouteGeometry, options: RouteFacilityQueryOptions): RouteFacilityQueryResult {
                calls++
                return if (calls == 1) first.await() else RouteFacilityQueryResult.Success(listOf(candidate))
            }
        }
        val holder = RouteFacilityStateHolder(provider, backgroundScope)
        holder.activate(route); runCurrent()
        holder.activate(RouteGeometry(route.points + GeoPoint(35.2, 139.2))); runCurrent()
        first.complete(RouteFacilityQueryResult.Success(emptyList())); runCurrent()
        assertEquals(1, holder.state.value.candidates.size)
        assertFalse(holder.addPlannedStop(candidate.id, moving = true))
        assertTrue(holder.addPlannedStop(candidate.id, moving = false))
        holder.activate(route); runCurrent()
        assertEquals(candidate.id, holder.state.value.plannedStops.single().facilityId)
        assertFalse(holder.addPlannedStop(candidate.id, moving = false))
        assertFalse(holder.removePlannedStop(candidate.id, moving = true))
        assertTrue(holder.removePlannedStop(candidate.id, moving = false))
    }

    @Test fun passageHysteresisAndNearNoticeOnlyOnce() {
        val progress = RouteFacilityProgress()
        val stop = PlannedRestStop(candidate.id, candidate.name, candidate.type, candidate.point, 8000.0)
        progress.update(5000.0, true)
        assertTrue(progress.notifyNear(stop))
        assertFalse(progress.notifyNear(stop))
        progress.update(8600.0, true)
        assertTrue(progress.progress(stop).passed)
        progress.update(7900.0, true)
        assertTrue(progress.progress(stop).passed)
        progress.update(0.0, false)
        assertEquals(7900.0, 8000.0 - (progress.progress(stop).distanceAheadMeters ?: 0.0), 0.01)
    }

    @Test fun httpStatusAndParseFailuresStayInFacilityDomain() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val provider = net.nobu0707.busnav.data.facility.HttpRouteFacilityProvider(
                url = server.url("/busnav/v1/route-facilities").toString())
            val expected = listOf(400 to RouteFacilityFailure.Http400, 413 to RouteFacilityFailure.Http413,
                429 to RouteFacilityFailure.Http429, 503 to RouteFacilityFailure.ServerUnavailable)
            for ((status, failure) in expected) {
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(RouteFacilityQueryResult.Failure(failure),
                    provider.findFacilities(route, RouteFacilityQueryOptions()))
            }
            server.enqueue(MockResponse().setResponseCode(200).setBody("{bad"))
            assertEquals(RouteFacilityQueryResult.Failure(RouteFacilityFailure.Parse),
                provider.findFacilities(route, RouteFacilityQueryOptions()))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"version":1,"facilities":[]}"""))
            assertEquals(RouteFacilityQueryResult.Success(emptyList()),
                provider.findFacilities(route, RouteFacilityQueryOptions()))
        } finally { server.shutdown() }
    }

    @Test fun missingPlannedCandidateIsRetainedForReview() {
        val stop = PlannedRestStop(candidate.id, candidate.name, candidate.type, candidate.point, 8000.0)
        assertEquals(PlannedStopStatus.NOT_ON_CURRENT_ROUTE_CANDIDATES,
            reconcilePlannedStops(listOf(stop), emptyList()).single().status)
        assertEquals(PlannedStopStatus.UPCOMING,
            reconcilePlannedStops(listOf(stop.copy(status = PlannedStopStatus.PASSED)), listOf(candidate)).single().status)
    }}
