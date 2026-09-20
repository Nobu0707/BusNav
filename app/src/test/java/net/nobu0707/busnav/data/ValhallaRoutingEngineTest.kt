package net.nobu0707.busnav.data.routing.valhalla

import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
    fun `next request reads saved endpoint without recreating engine`() = runBlocking {
        MockWebServer().use { second ->
            second.start()
            val defaults = net.nobu0707.busnav.developer.DeveloperConnectionSettings(server.url("/").toString(), server.url("/").toString())
            val file = java.io.File.createTempFile("connections", ".preferences_pb").also { it.delete() }
            val job = kotlinx.coroutines.SupervisorJob()
            val store = net.nobu0707.busnav.developer.testPreferenceStore(file,
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + job))
            val repository = net.nobu0707.busnav.developer.DataStoreDeveloperConnectionRepository(store, defaults)
            val engine = ValhallaRoutingEngine(RoutingConfig(defaults.valhallaBaseUrl),
                baseUrlProvider = { repository.settings.first().valhallaBaseUrl })
            try {
                server.enqueue(MockResponse().setBody(successBody()))
                assertTrue(engine.calculateRoute(request()) is RoutingResult.Success)
                assertEquals("/route", server.takeRequest().path)
                repository.update(defaults.copy(valhallaBaseUrl = second.url("/").toString()))
                second.enqueue(MockResponse().setBody(successBody()))
                assertTrue(engine.calculateRoute(request()) is RoutingResult.Success)
                assertEquals("/route", second.takeRequest().path)
                repository.reset()
                server.enqueue(MockResponse().setBody(successBody()))
                assertTrue(engine.calculateRoute(request()) is RoutingResult.Success)
                assertEquals("/route", server.takeRequest().path)
            } finally { job.cancel(); job.join(); file.delete() }
        }
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
        assertEquals("maneuvers", root.getValue("directions_type").jsonPrimitive.content)
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
        assertEquals(
            listOf(RoutePointType.START, RoutePointType.VIA, RoutePointType.SHAPING, RoutePointType.DESTINATION),
            success.route.points.map { it.type },
        )
        assertEquals("Plan", success.route.name)
        assertEquals(12_300.0, success.route.metadata.distanceMeters!!, 0.0)
        assertEquals("valhalla", success.route.metadata.routingSource)
    }

    @Test
    fun `actual long response records body fingerprint and succeeds`() = runBlocking {
        val diagnostics = RecordingRoutingDiagnostics()
        server.enqueue(MockResponse().setBody(longFixture()))
        val success = engine(diagnostics).calculateRoute(failingRouteRequest()) as RoutingResult.Success

        assertEquals(88_881.0, success.summary.distanceMeters, 0.001)
        assertEquals(3_075, success.route.geometry.points.size)
        val responseEntry = diagnostics.debugEntries.single { it.event == "response.received" }
        assertTrue(responseEntry.details.contains("status=200"))
        assertTrue(responseEntry.details.contains("bodyLength=12972"))
        assertTrue(responseEntry.details.contains("bodySha256="))
    }

    @Test
    fun `malformed responses return invalid response with diagnostic stage`() = runBlocking {
        val diagnostics = RecordingRoutingDiagnostics()
        val engine = engine(diagnostics)

        server.enqueue(MockResponse().setBody("""{"trip":{"summary":{"length":1,"time":2},"legs":[{"shape":"_"}]}}"""))
        assertFailure(RoutingFailure.INVALID_RESPONSE, engine.calculateRoute(request()))
        assertTrue(diagnostics.errorFor("response.parse.failed").details.contains("stage=POLYLINE"))

        diagnostics.errorEntries.clear()
        server.enqueue(MockResponse().setBody("not-json"))
        assertFailure(RoutingFailure.INVALID_RESPONSE, engine.calculateRoute(request()))
        assertTrue(diagnostics.errorFor("response.parse.failed").details.contains("stage=JSON_DECODE"))

        diagnostics.errorEntries.clear()
        server.enqueue(MockResponse().setBody(""))
        assertFailure(RoutingFailure.INVALID_RESPONSE, engine.calculateRoute(request()))
        assertTrue(diagnostics.errorFor("response.parse.failed").details.contains("stage=EMPTY_BODY"))
    }

    @Test
    fun `route invariant failure uses route construction event`() = runBlocking {
        val diagnostics = RecordingRoutingDiagnostics()
        val invalidRequest = request().copy(
            points = request().points.mapIndexed { index, point ->
                if (index == 0) point.copy(id = "") else point
            },
        )
        server.enqueue(MockResponse().setBody(successBody()))

        assertFailure(RoutingFailure.INVALID_RESPONSE, engine(diagnostics).calculateRoute(invalidRequest))

        assertTrue(
            diagnostics.errorFor("route.construction.failed").details
                .contains("stage=ROUTE_CONSTRUCTION"),
        )
    }

    @Test
    fun `response parsing runs on injected computation dispatcher`() = runBlocking {
        val ioDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "routing-io-test")
        }.asCoroutineDispatcher()
        val computationDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "routing-computation-test")
        }.asCoroutineDispatcher()
        try {
            val diagnostics = RecordingRoutingDiagnostics()
            server.enqueue(MockResponse().setBody(longFixture()))

            val result = engine(
                diagnostics = diagnostics,
                dispatchers = RoutingDispatchers(ioDispatcher, computationDispatcher),
            ).calculateRoute(failingRouteRequest())

            assertTrue(result is RoutingResult.Success)
            val parseEntries = diagnostics.debugEntries.filter { it.event.startsWith("parse.") }
            assertTrue(parseEntries.isNotEmpty())
            assertTrue(
                "parse threads=${parseEntries.map { it.threadName }}",
                parseEntries.all { it.threadName.startsWith("routing-computation-test") },
            )
        } finally {
            ioDispatcher.close()
            computationDispatcher.close()
        }
    }

    @Test
    fun `same engine handles twelve consecutive long responses`() = runBlocking {
        val engine = engine()
        repeat(12) { server.enqueue(MockResponse().setBody(longFixture())) }

        repeat(12) {
            val result = engine.calculateRoute(failingRouteRequest())
            assertTrue("request $it should succeed", result is RoutingResult.Success)
        }
        assertEquals(12, server.requestCount)
    }

    @Test
    fun `same engine handles alternating short and long responses`() = runBlocking {
        val bodies = listOf(successBody(), longFixture(), successBody(), longFixture(), successBody(), longFixture())
        bodies.forEach { server.enqueue(MockResponse().setBody(it)) }
        val engine = engine()

        bodies.indices.forEach { index ->
            val result = engine.calculateRoute(if (index % 2 == 0) request() else failingRouteRequest())
            assertTrue("request $index should succeed", result is RoutingResult.Success)
        }
        assertEquals(bodies.size, server.requestCount)
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
        val engine = ValhallaRoutingEngine(
            RoutingConfig(server.url("/").toString(), readTimeoutSeconds = 1),
        )
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

    private fun engine(
        diagnostics: RoutingDiagnostics = NoOpRoutingDiagnostics,
        dispatchers: RoutingDispatchers = RoutingDispatchers(),
    ) =
        ValhallaRoutingEngine(
            config = RoutingConfig(server.url("/").toString()),
            routeIdFactory = { "result" },
            diagnostics = diagnostics,
            dispatchers = dispatchers,
        )

    private fun request() = (plan().toRoutingRequest() as RoutingRequestResult.Ready).request

    private fun failingRouteRequest() = (RoutePlan(
        id = "failing-plan",
        name = "Failing route",
        points = listOf(
            RoutePlanPoint(
                "s",
                RoutePlanPointType.START,
                GeoPoint(35.52755965924169, 138.79653353327427),
            ),
            RoutePlanPoint(
                "d",
                RoutePlanPointType.DESTINATION,
                GeoPoint(35.609542457517534, 138.29084069799353),
            ),
        ),
    ).toRoutingRequest() as RoutingRequestResult.Ready).request

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

    private fun longFixture(): String =
        requireNotNull(javaClass.getResource("/valhalla/route-88km-valhalla-3.9.0.json")).readText()

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
