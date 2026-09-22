package net.nobu0707.busnav.map

import android.graphics.RectF
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
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
    private lateinit var native: MapLibreMap
    private lateinit var view: MapView
    @Volatile private var fullyRendered = false
    private val shieldIds = arrayOf("route-shield-expressway", "route-shield-national", "route-shield-prefectural")

    private fun start(): androidx.compose.runtime.MutableState<BasemapConfig> {
        effectiveTestConnections()
        LocalBasemapAssumptions.assumeAvailable()
        val config = mutableStateOf(BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL, BasemapRegion.KANTO, true).withTheme(false))
        val provider = DebugFixtureTrafficInformationProvider().apply { select(TrafficFixtureScenario.MIXED) }
        val events = runBlocking { provider.observeTraffic().first() }.events.filter { it.validity(System.currentTimeMillis()) == TrafficValidity.ACTIVE }
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        var ready = false
        rule.setContent {
            MapScreen(null, false, 0, detourFixture(13).route, 0, trafficEvents = events,
                basemapConfig = config.value, initialCamera = EditorCamera(GeoPoint(35.658,139.701),14.0,0.0,0.0),
                onMapReady = { ready = true }, onMapGesture = {}, onMapError = {})
        }
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
        val done = AtomicBoolean(false)
        rule.runOnUiThread {
            native.snapshot { bitmap ->
                val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "road-style").apply { mkdirs() }
                File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                done.set(true)
            }
        }
        rule.waitUntil(10_000) { done.get() }
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
                    shieldIds.forEach { assertTrue(ids.indexOf(it) < ids.indexOf(OverlayLayerOrder.ACTIVE_ROUTE)) }
                    assertTrue(ids.indexOf(OverlayLayerOrder.ACTIVE_ROUTE) < ids.indexOf(OverlayLayerOrder.TRAFFIC_MARKER))
                    val features = native.queryRenderedFeatures(RectF(0f,0f,view.width.toFloat(),view.height.toFloat()), *shieldIds)
                    seen.addAll(features.map { it.getStringProperty("route_network") })
                    if (zoom < 13) assertTrue(features.none { it.getStringProperty("route_network") == "prefectural" })
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
                assertTrue(native.queryRenderedFeatures(screen, OverlayLayerOrder.TRAFFIC_MARKER).size >= 4)
            }
            screenshot("route-traffic-${if (dark) "dark" else "light"}")
        }
        assertTrue("Missing rendered road types: $seen", seen.containsAll(listOf("expressway", "national", "prefectural")))
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
            LocalBasemapAssumptions.BASE_URL + "/styles/busnav-kanto-light/style.json").build()).execute().use { JSONObject(it.body!!.string()) }
        val cases = listOf("national" to "1", "national" to "12", "national" to "246", "expressway" to "E1",
            "expressway" to "E20", "expressway" to "C4", "prefectural" to "12", "prefectural" to "34", "prefectural" to "300")
        val features = cases.mapIndexed { i, (kind, ref) ->
            val lat = 35.658 + (i - 4) * 0.00055
            Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(139.696,lat), Point.fromLngLat(139.706,lat)))).apply {
                addStringProperty("route_network", kind); addStringProperty("route_ref", ref); addStringProperty("class", "primary")
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
        screenshot("synthetic-numbers")
    }
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
}
