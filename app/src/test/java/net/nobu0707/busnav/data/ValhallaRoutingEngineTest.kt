package net.nobu0707.busnav.data.routing.valhalla

import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RoutePointType
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.RoutePlanPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routeplan.RoutingRequestResult
import net.nobu0707.busnav.domain.routeplan.toRoutingRequest
import net.nobu0707.busnav.domain.routing.RoutingFailure
import net.nobu0707.busnav.domain.routing.RoutingResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValhallaRoutingEngineTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `posts ordered mapped locations and conservative truck options`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody()))
        val result = engine().calculateRoute(request())
        assertTrue(result is RoutingResult.Success)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/route", recorded.path)
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))
        val payload = recorded.body.readUtf8()
        val root = Json.parseToJsonElement(payload).jsonObject
        assertEquals("truck", root.getValue("costing").jsonPrimitive.content)
        assertEquals("kilometers", root.getValue("units").jsonPrimitive.content)
        assertEquals("polyline6", root.getValue("shape_format").jsonPrimitive.content)
        assertEquals("none", root.getValue("directions_type").jsonPrimitive.content)
        assertEquals(
            listOf("break", "via", "through", "break"),
            root.getValue("locations").jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content },
        )
        val truck = root.getValue("costing_options").jsonObject.getValue("truck").jsonObject
        assertEquals(3.5, truck.getValue("height").jsonPrimitive.double, 0.0)
        assertEquals(2.5, truck.getValue("width").jsonPrimitive.double, 0.0)
        assertEquals(12.0, truck.getValue("length").jsonPrimitive.double, 0.0)
        assertEquals(16.0, truck.getValue("weight").jsonPrimitive.double, 0.0)
        assertEquals(10.0, truck.getValue("axle_load").jsonPrimitive.double, 0.0)
        assertEquals(0.8, truck.getValue("use_highways").jsonPrimitive.double, 0.0)
        assertEquals(0.1, truck.getValue("use_living_streets").jsonPrimitive.double, 0.0)
        assertEquals(0.0, truck.getValue("use_tracks").jsonPrimitive.double, 0.0)
        assertTrue(truck.getValue("exclude_unpaved").jsonPrimitive.boolean)
        assertFalse(payload.contains("ignore_restrictions"))
        assertFalse(payload.contains("ignore_access"))
        assertFalse(payload.contains("ignore_oneways"))
    }

    @Test
    fun `maps multi leg response to scheduled route and removes duplicate boundary`() = runBlocking {
        server.enqueue(MockResponse().setBody(successBody()))
        val success = engine().calculateRoute(request()) as RoutingResult.Success
        assertEquals(12_300.0, success.summary.distanceMeters, 0.0)
        assertEquals(901.0, success.summary.durationSeconds, 0.0)
        assertEquals(3, success.route.geometry.points.size)
        assertEquals(listOf(RoutePointType.START, RoutePointType.VIA, RoutePointType.SHAPING, RoutePointType.DESTINATION), success.route.points.map { it.type })
        assertEquals("Plan", success.route.name)
        assertEquals(12_300.0, success.route.metadata.distanceMeters!!, 0.0)
        assertEquals("valhalla", success.route.metadata.routingSource)
    }

    @Test
    fun `malformed shape and malformed json are invalid response`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"trip":{"summary":{"length":1,"time":2},"legs":[{"shape":"_"}]}}"""))
        assertFailure(RoutingFailure.INVALID_RESPONSE, engine().calculateRoute(request()))
        server.enqueue(MockResponse().setBody("not-json"))
        assertFailure(RoutingFailure.INVALID_RESPONSE, engine().calculateRoute(request()))
    }

    @Test
    fun `http errors are classified without exposing body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"No path could be found","error_code":442}"""))
        assertFailure(RoutingFailure.NO_ROUTE, engine().calculateRoute(request()))
        server.enqueue(MockResponse().setResponseCode(429).setBody("busy"))
        assertFailure(RoutingFailure.RATE_LIMITED, engine().calculateRoute(request()))
        server.enqueue(MockResponse().setResponseCode(500).setBody("secret detail"))
        assertFailure(RoutingFailure.SERVER_ERROR, engine().calculateRoute(request()))
    }

    @Test
    fun `read timeout is classified`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val engine = ValhallaRoutingEngine(RoutingConfig(server.url("/").toString(), readTimeoutSeconds = 1))
        assertFailure(RoutingFailure.TIMEOUT, engine.calculateRoute(request()))
    }

    @Test
    fun `coroutine cancellation cancels call and propagates`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val task = async { engine().calculateRoute(request()) }
        server.takeRequest(2, TimeUnit.SECONDS)
        task.cancel()
        delay(10)
        assertTrue(task.isCancelled)
        try {
            task.await()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            Unit
        }
    }

    private fun engine() = ValhallaRoutingEngine(
        config = RoutingConfig(server.url("/").toString()),
        routeIdFactory = { "result" },
    )

    private fun request() = (plan().toRoutingRequest() as RoutingRequestResult.Ready).request

    private fun plan() = RoutePlan(
        id = "plan",
        name = "Plan",
        points = listOf(
            RoutePlanPoint("s", RoutePlanPointType.START, GeoPoint(38.5, -120.2), "Start"),
            RoutePlanPoint("v", RoutePlanPointType.VIA, GeoPoint(40.7, -120.95)),
            RoutePlanPoint("h", RoutePlanPointType.SHAPING, GeoPoint(41.0, -121.0)),
            RoutePlanPoint("d", RoutePlanPointType.DESTINATION, GeoPoint(43.252, -126.453), "End"),
        ),
    )

    private fun successBody() = """
        {"trip":{"summary":{"length":12.3,"time":901},"legs":[
          {"shape":"_izlhA~rlgdF_{geC~ywl@"},
          {"shape":"_ecslA~meueF_kwzCn`{nI"}
        ]}}
    """.trimIndent()

    private fun assertFailure(expected: RoutingFailure, actual: RoutingResult) {
        assertEquals(expected, (actual as RoutingResult.Failure).reason)
    }
}
