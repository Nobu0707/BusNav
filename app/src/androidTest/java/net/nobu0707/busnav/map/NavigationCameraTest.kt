package net.nobu0707.busnav.map

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.location.LocationState
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.test.*
import net.nobu0707.busnav.ui.routeplan.EditorCamera
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.SymbolLayer

class NavigationCameraTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var native: MapLibreMap
    private lateinit var view: MapView
    private val resolver = NavigationHeadingResolver()
    private var heading = NavigationHeading()
    private var sequence = 0L
    private val camera = mutableStateOf(NavigationCameraState(active = true))
    private val location = mutableStateOf<LocationState?>(null)
    private val recenter = mutableStateOf(0)
    private val config = mutableStateOf(BasemapConfig(null, BasemapMode.FALLBACK).withTheme(false))
    private var ready = false

    private fun start(live: Boolean = false) {
        if (live) {
            effectiveTestConnections()
            LocalBasemapAssumptions.assumeAvailable()
            config.value = BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL, BasemapRegion.KANTO, true).withTheme(false)
        }
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        rule.setContent {
            BusNavTheme {
                MapScreen(location.value, camera.value.following, recenter.value, null, 0,
                    navigationCamera = camera.value, basemapConfig = config.value,
                    initialCamera = EditorCamera(GeoPoint(35.678, 139.742), 16.0, 0.0, 0.0),
                    onToggleOrientation = { camera.value = camera.value.copy(orientation = camera.value.orientation.toggled()) },
                    onMapReady = { ready = true },
                    onMapGesture = { camera.value = camera.value.copy(following = false) }, onMapError = {})
            }
        }
        rule.waitUntil(30_000) { ready }
        rule.runOnUiThread {
            view = requireNotNull(findMap(rule.activity.window.decorView))
            view.getMapAsync { native = it }
        }
        send(0f)
    }

    private fun send(degrees: Float?, speed: Float = 8f) {
        rule.runOnIdle {
            val now = SystemClock.elapsedRealtime()
            val fix = LocationState(GeoPoint(35.678, 139.742), 5f, degrees, speed, ++sequence, now)
            heading = resolver.resolve(fix, heading, now)
            location.value = fix
            camera.value = camera.value.copy(headingDegrees = heading.degrees)
        }
    }
    private fun awaitBearing(expected: Double) {
        rule.waitForIdle()
        rule.waitUntil(10_000) {
            var result = false
            rule.runOnUiThread { result = abs(shortestHeadingDelta(native.cameraPosition.bearing, expected)) < 0.5 }
            result
        }
        Thread.sleep(420)
    }
    private fun verifyNorthProjection() {
        rule.runOnUiThread {
            val target = requireNotNull(native.cameraPosition.target)
            val center = native.projection.toScreenLocation(target)
            val north = native.projection.toScreenLocation(LatLng(target.latitude + 0.001, target.longitude))
            val actual = normalizeHeading(Math.toDegrees(atan2((north.x - center.x).toDouble(), (center.y - north.y).toDouble())))
            assertEquals(0.0, shortestHeadingDelta(actual, northScreenRotation(native.cameraPosition.bearing)), 0.5)
        }
    }
    private fun snapshot(name: String): Bitmap {
        val result = AtomicReference<Bitmap>()
        rule.runOnUiThread { native.snapshot { result.set(it) } }
        rule.waitUntil(10_000) { result.get() != null }
        val bitmap = result.get()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "navigation-camera").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
    /** Checks actual rendered arrow pixels, independent of iconRotate properties. Ring pixels are excluded. */
    private fun verifyArrowPixels(bitmap: Bitmap, expectedHeading: Double) {
        var cx = 0f; var cy = 0f; var radius = 0f
        rule.runOnUiThread {
            val center = native.projection.toScreenLocation(LatLng(35.678, 139.742))
            cx = center.x; cy = center.y
            radius = 56f * view.resources.displayMetrics.density * 0.34f
        }
        var dx = 0.0; var dy = 0.0; var count = 0
        for (y in (cy - radius).toInt()..(cy + radius).toInt()) for (x in (cx - radius).toInt()..(cx + radius).toInt()) {
            if (x !in 0 until bitmap.width || y !in 0 until bitmap.height || hypot(x - cx, y - cy) > radius) continue
            val color = bitmap.getPixel(x, y)
            if (abs(android.graphics.Color.red(color) - 190) < 12 &&
                abs(android.graphics.Color.green(color) - 25) < 12 && abs(android.graphics.Color.blue(color) - 58) < 12) {
                dx += x - cx; dy += y - cy; count++
            }
        }
        assertTrue("Rendered arrow pixels missing", count > 20)
        val length = hypot(dx, dy)
        val theta = Math.toRadians(expectedHeading)
        assertTrue("Rendered arrow tail points in wrong direction", (dx * -sin(theta) + dy * cos(theta)) / length > 0.95)
    }

    @Test fun cardinalCameraCompassAndRenderedMarkerBothModes() {
        start()
        for (mode in NavigationMapOrientation.entries) {
            rule.runOnIdle { camera.value = camera.value.copy(orientation = mode) }
            for (degrees in listOf(0f, 90f, 180f, 270f)) {
                send(degrees)
                awaitBearing(if (mode == NavigationMapOrientation.HEADING_UP) degrees.toDouble() else 0.0)
                verifyNorthProjection()
                rule.runOnUiThread {
                    val layer = native.style!!.getLayer(OverlayLayerOrder.VEHICLE) as SymbolLayer
                    assertEquals("viewport", layer.iconRotationAlignment.value)
                    assertEquals(if (mode == NavigationMapOrientation.HEADING_UP) 0f else degrees, layer.iconRotate.value!!, 0.5f)
                    assertEquals(16.0, native.cameraPosition.zoom, 0.001)
                }
                verifyArrowPixels(snapshot("$mode-${degrees.toInt()}"), if (mode == NavigationMapOrientation.HEADING_UP) 0.0 else degrees.toDouble())
            }
        }
    }

    @Test fun turnsSpikeStopGestureToggleRecenterAndStaleLocation() {
        start()
        for (degrees in listOf(0f, 20f, 45f, 80f, 90f, 120f, 180f)) { send(degrees); awaitBearing(degrees.toDouble()) }
        // Reset only the synthetic resolver history, as a new navigation trace.
        heading = NavigationHeading()
        send(10f); awaitBearing(10.0)
        send(220f); awaitBearing(10.0)
        send(12f); awaitBearing(12.0)
        send(250f, 0f); awaitBearing(12.0)
        val down = SystemClock.uptimeMillis()
        for (i in 0..20) {
            rule.runOnUiThread {
                val action = when (i) { 0 -> android.view.MotionEvent.ACTION_DOWN; 20 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
                val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    view.width * 0.5f + i * 8f, view.height * 0.5f + i * 4f, 0)
                view.dispatchTouchEvent(event)
                event.recycle()
            }
            Thread.sleep(20)
        }
        rule.waitUntil(5000) { !camera.value.following }
        var before = 0.0
        rule.runOnUiThread { before = native.cameraPosition.bearing }
        send(90f); awaitBearing(before)
        rule.runOnIdle { camera.value = camera.value.copy(following = true); recenter.value++ }
        awaitBearing(90.0)
        rule.onNodeWithTag("navigation_compass").performClick()
        awaitBearing(0.0)
        rule.runOnUiThread { assertEquals(16.0, native.cameraPosition.zoom, 0.001) }
        rule.onNodeWithTag("navigation_compass").performClick()
        awaitBearing(90.0)
        rule.runOnIdle {
            location.value = location.value!!.copy(elapsedRealtimeMillis = SystemClock.elapsedRealtime() - 20_000)
            camera.value = camera.value.copy(headingDegrees = 180.0)
        }
        awaitBearing(90.0)
        rule.runOnIdle { location.value = null; camera.value = camera.value.copy(headingDegrees = 270.0) }
        awaitBearing(90.0)
        rule.runOnIdle { camera.value = camera.value.copy(active = false) }
        rule.onNodeWithTag("navigation_compass").assertDoesNotExist()
    }

    @Test fun liveRotatedStylesRegionsLabelsAndSpan() {
        start(live = true)
        send(90f); awaitBearing(90.0)
        for (mode in NavigationMapOrientation.entries) {
            rule.runOnIdle { camera.value = camera.value.copy(orientation = mode) }
            send(90f); awaitBearing(if (mode == NavigationMapOrientation.HEADING_UP) 90.0 else 0.0)
            for (region in BasemapRegion.entries) for (dark in listOf(false, true, false)) {
                rule.runOnIdle { config.value = BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL, region, true).withTheme(dark) }
                rule.waitUntil(30_000) {
                    var loaded = false
                    rule.runOnUiThread { loaded = native.style?.uri == config.value.styleUrl && native.style?.isFullyLoaded == true &&
                        native.style?.getLayer(OverlayLayerOrder.VEHICLE) != null }
                    loaded
                }
                awaitBearing(if (mode == NavigationMapOrientation.HEADING_UP) 90.0 else 0.0)
                verifyNorthProjection()
                rule.runOnUiThread {
                    val style = requireNotNull(native.style)
                    for (id in listOf("route-shield-urban_expressway", "route-shield-expressway", "facility-junction-label", "intersection-major")) {
                        val layer = style.getLayer(id) as? SymbolLayer
                        if (layer != null) assertEquals("viewport", layer.textRotationAlignment.value)
                    }
                    val span = VisibleMapSpanCalculator.measure(VisibleMapSpanCalculator.Rect(0f, 0f, view.width.toFloat(), view.height.toFloat())) {
                        val point = native.projection.fromScreenLocation(android.graphics.PointF(it.x, it.y))
                        GeoPoint(point.latitude, point.longitude)
                    }
                    assertTrue(span > 0 && span < 2200)
                    assertEquals(16.0, native.cameraPosition.zoom, 0.001)
                }
                snapshot("live-${region.name}-$mode-$dark")
            }
        }
    }
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
}
