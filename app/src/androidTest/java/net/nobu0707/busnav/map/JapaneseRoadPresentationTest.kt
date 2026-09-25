package net.nobu0707.busnav.map

import android.graphics.RectF
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.traffic.TrafficValidity
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.test.*
import net.nobu0707.busnav.detour.detourFixture
import net.nobu0707.busnav.ui.routeplan.EditorCamera
import net.nobu0707.busnav.ui.traffic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.*
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.Request

class JapaneseRoadPresentationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var restoreContent: () -> Unit
    private lateinit var native: MapLibreMap
    private lateinit var view: MapView
    @Volatile private var fullyRendered = false
    private val shieldIds = arrayOf("route-shield-expressway", "route-shield-urban_expressway", "route-shield-national", "route-shield-prefectural")

    private fun start(): androidx.compose.runtime.MutableState<BasemapConfig> {
        effectiveTestConnections()
        LocalBasemapAssumptions.assumeAvailable()
        val config = mutableStateOf(BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL, BasemapRegion.KANTO, true).withTheme(false))
        val provider = DebugFixtureTrafficInformationProvider().apply { select(TrafficFixtureScenario.MIXED) }
        val events = runBlocking { provider.observeTraffic().first() }.events.filter { it.validity(System.currentTimeMillis()) == TrafficValidity.ACTIVE }
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        var ready = false
        restoreContent = { rule.activity.setContent {
            MapScreen(null, false, 0, detourFixture(13).route, 0, trafficEvents = events,
                basemapConfig = config.value, initialCamera = EditorCamera(GeoPoint(35.658,139.701),14.0,90.0,0.0),
                onMapReady = { ready = true }, onMapGesture = {}, onMapError = {})
        } }
        rule.runOnUiThread { restoreContent() }
        rule.waitUntil(30_000) { ready }
        rule.runOnUiThread {
            view = requireNotNull(findMap(rule.activity.window.decorView))
            view.addOnDidFinishRenderingFrameListener(MapView.OnDidFinishRenderingFrameListener { full, _, _ -> fullyRendered = full })
            view.getMapAsync { native = it }
        }
        return config
    }
    private fun awaitMap() {
        rule.waitUntil(60_000) { var loaded = false; rule.runOnUiThread { loaded = native.style?.isFullyLoaded == true && fullyRendered }; loaded }
        // Placement transitions and asynchronous glyph/image upload must settle before screenshot/query.
        Thread.sleep(1200)
    }
    private fun screenshot(name: String) {
        // Capture displayed pixels: Vulkan framebuffer readback can crash on the x86 emulator.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "road-style").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun liveKantoZoomThemeMatrixAndChubuRegression() {
        val config = start()
        val seen = mutableSetOf<String>()
        for (dark in listOf(false, true)) {
            rule.runOnIdle { config.value = config.value.withTheme(dark) }
            rule.waitUntil(30_000) { var ok = false; rule.runOnUiThread { ok = native.style?.uri == config.value.styleUrl }; ok }
            for (zoom in listOf(8.0, 10.0, 12.0, 14.0, 16.0)) {
                rule.runOnUiThread { fullyRendered = false; native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.658,139.701), zoom)) }
                awaitMap()
                rule.runOnUiThread {
                    val style = requireNotNull(native.style)
                    JapaneseRoadShields.ids.forEach { assertNotNull(style.getImage(it)) }
                    val ids = style.layers.map { it.id }
                    shieldIds.forEach { assertTrue(ids.indexOf(it) > ids.indexOf(OverlayLayerOrder.ACTIVE_ROUTE)) }
                    assertTrue(ids.indexOf(OverlayLayerOrder.ACTIVE_ROUTE) < ids.indexOf(OverlayLayerOrder.TRAFFIC_MARKER))
                    val features = native.queryRenderedFeatures(RectF(0f,0f,view.width.toFloat(),view.height.toFloat()), *shieldIds)
                    seen.addAll(features.map { it.getStringProperty("route_network") })
                    if (zoom <= 12) assertTrue("Overview must hide all shields", features.isEmpty())
                }
                screenshot("kanto-${if (dark) "dark" else "light"}-z${zoom.toInt()}")
            }
            rule.runOnUiThread { fullyRendered = false; native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.660,139.310), 14.0)) }
            awaitMap(); screenshot("suburban-${if (dark) "dark" else "light"}")
            rule.runOnUiThread { fullyRendered = false; native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.68,139.795), 13.0)) }
            awaitMap()
            rule.runOnUiThread {
                val screen = RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
                assertTrue(native.queryRenderedFeatures(screen, OverlayLayerOrder.ACTIVE_ROUTE).isNotEmpty())
                val trafficIcons = native.queryRenderedFeatures(screen, OverlayLayerOrder.TRAFFIC_MARKER)
                    .map { it.getStringProperty("icon") }.toSet()
                assertTrue("Missing rendered traffic symbols: $trafficIcons", trafficIcons.containsAll(listOf(
                    "busnav-traffic-ROAD_CLOSURE", "busnav-traffic-ACCIDENT", "busnav-traffic-ROADWORK", "busnav-traffic-CONGESTION")))
            }
            screenshot("route-traffic-${if (dark) "dark" else "light"}")
        }
        rule.runOnUiThread { fullyRendered = false; native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.678,139.742), 16.0)) }
        awaitMap()
        rule.runOnUiThread { seen.addAll(native.queryRenderedFeatures(RectF(0f,0f,view.width.toFloat(),view.height.toFloat()), *shieldIds)
            .map { it.getStringProperty("route_network") }) }
        assertTrue("Missing rendered road types: $seen", seen.containsAll(listOf("urban_expressway", "national")))
        for (dark in listOf(false, true)) {
            rule.runOnIdle { config.value = BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL, BasemapRegion.CHUBU, true).withTheme(dark) }
            rule.waitUntil(30_000) { var ok = false; rule.runOnUiThread { ok = native.style?.uri == config.value.styleUrl }; ok }
            rule.runOnUiThread { fullyRendered = false; native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.17,136.90),14.0)) }
            awaitMap(); screenshot("chubu-${if (dark) "dark" else "light"}")
        }
    }
    @Test fun syntheticDynamicNumbersUseAllOriginalBackgrounds() {
        start()
        val json = LocalBasemapAssumptions.client().newCall(Request.Builder().url(
            LocalBasemapAssumptions.BASE_URL + "/styles/busnav-light/style.json").build()).execute().use { JSONObject(it.body!!.string()) }
        val cases = listOf("national" to "1", "national" to "12", "national" to "246", "expressway" to "E1",
            "expressway" to "E20", "expressway" to "C4", "urban_expressway" to "C1", "urban_expressway" to "B", "urban_expressway" to "K1", "prefectural" to "12", "prefectural" to "34", "prefectural" to "300")
        val features = cases.mapIndexed { i, (kind, ref) ->
            val lat = 35.658 + (i / 3 - 1.5) * 0.001
            val lon = 139.701 + (i % 3 - 1) * 0.0013
            Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(lon - 0.0005,lat), Point.fromLngLat(lon + 0.0005,lat)))).apply {
                addStringProperty("route_source_network", "首都高速道路"); addStringProperty("route_network", kind); addStringProperty("route_ref", ref); addStringProperty("class", "primary")
            }
        }
        json.put("sources", JSONObject().put("openmaptiles", JSONObject().put("type", "geojson")
            .put("data", JSONObject(FeatureCollection.fromFeatures(features).toJson()))))
        val layers = json.getJSONArray("layers")
        val selected = JSONArray()
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            if (layer.getString("id") in (shieldIds.toList() + listOf("background", "roads-primary"))) {
                layer.remove("source-layer")
                if (layer.getString("id").startsWith("route-shield-")) layer.getJSONObject("layout").put("symbol-placement", "line-center")
                selected.put(layer)
            }
        }
        json.put("layers", selected)
        rule.runOnUiThread { fullyRendered = false; native.setStyle(Style.Builder().fromJson(json.toString()))
            native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.658,139.701), 16.0)) }
        awaitMap()
        rule.runOnUiThread {
            val rendered = native.queryRenderedFeatures(RectF(0f,0f,view.width.toFloat(),view.height.toFloat()), *shieldIds)
                .map { it.getStringProperty("route_network") to it.getStringProperty("route_ref") }.toSet()
            assertEquals(cases.toSet(), rendered)
        }
        rule.runOnUiThread {
            fullyRendered = false
            (native.style!!.getSource(RouteOverlayController.GEOMETRY_SOURCE_ID) as org.maplibre.android.style.sources.GeoJsonSource)
                .setGeoJson(FeatureCollection.fromFeatures(features))
        }
        awaitMap()
        rule.runOnUiThread {
            val ids = native.style!!.layers.map { it.id }
            shieldIds.forEach { assertTrue(ids.indexOf(OverlayLayerOrder.ACTIVE_ROUTE) < ids.indexOf(it)) }
            assertTrue(native.queryRenderedFeatures(RectF(0f,0f,view.width.toFloat(),view.height.toFloat()), OverlayLayerOrder.ACTIVE_ROUTE).isNotEmpty())
        }
        screenshot("synthetic-numbers-over-route")
    }
    @Test fun liveFacilitiesIntersectionsAndMeasuredSpan() {
        val config = start()
        val types = mutableSetOf<String>()
        val refs = mutableSetOf<String>()
        val labels = mutableSetOf<String>()
        var intersections = 0
        for (dark in listOf(false, true)) {
            rule.runOnIdle { config.value = config.value.withTheme(dark) }
            rule.waitUntil(30_000) { var ok = false; rule.runOnUiThread { ok = native.style?.uri == config.value.styleUrl }; ok }
            for ((site, point) in listOf("miyakezaka" to LatLng(35.678,139.742), "ohashi" to LatLng(35.651,139.689),
                "hakozaki" to LatLng(35.681,139.787), "bayshore" to LatLng(35.632,139.791),
                "kanagawa" to LatLng(35.469,139.629), "kasumigaseki" to LatLng(35.67208,139.745123),
                "iikura-toll" to LatLng(35.663241,139.738423), "shibuya-access" to LatLng(35.656189,139.697455), "c2-yamate" to LatLng(35.659,139.691))) {
                rule.runOnUiThread { fullyRendered = false; native.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 15.5)) }
                awaitMap()
                rule.runOnUiThread {
                    val rect = RectF(0f,0f,view.width.toFloat(),view.height.toFloat())
                    val facilities = native.queryRenderedFeatures(rect, "facility-junction-icon", "facility-access-icon", "facility-toll-icon")
                    types.addAll(facilities.map { it.getStringProperty("facility_type") })
                    labels.addAll(native.queryRenderedFeatures(rect, "facility-junction-label", "facility-access-label")
                        .map { it.getStringProperty("name") })
                    refs.addAll(native.queryRenderedFeatures(rect, "route-shield-urban_expressway").map { it.getStringProperty("route_ref") })
                    intersections += native.queryRenderedFeatures(rect, "intersection-major", "intersection-normal").size
                }
                screenshot("details-$site-${if (dark) "dark" else "light"}")
            }
        }
        assertTrue("Missing facilities: $types", types.containsAll(listOf("junction", "toll_gate")))
        assertTrue("No access icons: $types", types.any { it in listOf("interchange", "entrance", "exit") })
        assertTrue("No green facility labels", labels.isNotEmpty())
        assertTrue("No intersection labels", intersections > 0)
        assertTrue("Missing C1/C2: $refs", refs.containsAll(listOf("C1", "C2")))
        for (landscape in listOf(false, true)) {
            rule.runOnUiThread { rule.activity.requestedOrientation = if (landscape)
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == if (landscape)
                android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT }
            rule.runOnUiThread { if (findMap(rule.activity.window.decorView) == null) restoreContent() }
            rule.waitUntil(30_000) {
                var loaded = false
                rule.runOnUiThread {
                    findMap(rule.activity.window.decorView)?.let { currentView ->
                        if (view !== currentView) {
                            view = currentView
                            fullyRendered = false
                            view.addOnDidFinishRenderingFrameListener(MapView.OnDidFinishRenderingFrameListener { full, _, _ -> fullyRendered = full })
                        }
                        view.getMapAsync { native = it; loaded = it.style?.isFullyLoaded == true }
                    }
                }
                loaded && (view.width > view.height) == landscape
            }
            for (meters in listOf(3200.0, 2000.0, 2400.0, 3000.0)) {
                rule.runOnUiThread {
                    val a = native.projection.fromScreenLocation(android.graphics.PointF(view.width/2f, 0f))
                    val b = native.projection.fromScreenLocation(android.graphics.PointF(view.width/2f, view.height.toFloat()))
                    val current = VisibleMapSpanCalculator.distance(GeoPoint(a.latitude,a.longitude), GeoPoint(b.latitude,b.longitude))
                    val zoom = native.cameraPosition.zoom + kotlin.math.ln(current/meters)/kotlin.math.ln(2.0)
                    fullyRendered = false
                    native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.678,139.742), zoom))
                }
                awaitMap()
                rule.runOnUiThread {
                    val expected = if (meters <= 2400) "visible" else "none"
                    shieldIds.forEach { assertEquals("span=$meters landscape=$landscape", expected, native.style!!.getLayer(it)!!.visibility.value) }
                    if (meters >= 3000) assertTrue(native.queryRenderedFeatures(RectF(0f,0f,view.width.toFloat(),view.height.toFloat()), *shieldIds).isEmpty())
                }
                screenshot("span-${meters.toInt()}-${if (landscape) "landscape" else "portrait"}")
            }
        }
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
}
