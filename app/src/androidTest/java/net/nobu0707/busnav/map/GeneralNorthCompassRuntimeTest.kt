package net.nobu0707.busnav.map

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import java.io.File
import kotlin.math.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.ui.routeplan.EditorCamera
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class GeneralNorthCompassRuntimeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var needle = Color.Unspecified

    // Check rendered pixels, not a duplicate semantics rotation or a screenshot golden.
    private fun assertPointer(bearing: Double, name: String) {
        val image = rule.onNodeWithTag("general_north_compass").captureToImage()
        val directory = File(rule.activity.getExternalFilesDir(null), "general-north-compass").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val pixels = image.toPixelMap()
        var dx = 0.0
        var dy = 0.0
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (abs(color.red - needle.red) < 0.03f &&
                abs(color.green - needle.green) < 0.03f &&
                abs(color.blue - needle.blue) < 0.03f) {
                dx += x + 0.5 - pixels.width / 2.0
                dy += y + 0.5 - pixels.height / 2.0
                count++
            }
        }
        assertTrue("North pointer pixels missing at $bearing", count > 10)
        val expected = Math.toRadians(northScreenRotation(bearing))
        assertTrue("North pointer points in wrong direction at $bearing",
            (dx * sin(expected) - dy * cos(expected)) / hypot(dx, dy) > 0.97)
    }

    @Test fun renderedCardinalsKeepUprightLabelAndFixedTouchTarget() {
        val bearing = mutableStateOf(0.0)
        val dark = mutableStateOf(false)
        var taps = 0
        rule.setContent {
            BusNavTheme(darkTheme = dark.value) {
                needle = MaterialTheme.colorScheme.error
                GeneralNorthCompass(bearing.value) { taps++ }
            }
        }
        val bounds = rule.onNodeWithTag("general_north_compass").fetchSemanticsNode().boundsInRoot
        val labelBounds = rule.onNodeWithText("N", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        for (night in listOf(false, true)) {
            rule.runOnIdle { dark.value = night }
            for (degrees in listOf(0.0, 90.0, 180.0, 270.0)) {
                rule.runOnIdle { bearing.value = degrees }
                assertPointer(degrees, "isolated-$night-${degrees.toInt()}")
                assertEquals(bounds, rule.onNodeWithTag("general_north_compass").fetchSemanticsNode().boundsInRoot)
                assertEquals(labelBounds, rule.onNodeWithText("N", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
            }
        }
        // Tap ripples can tint the needle for several frames on physical devices.
        // Finish every pixel assertion before exercising clicks; do not weaken color matching.
        repeat(8) { rule.onNodeWithTag("general_north_compass").performClick() }
        rule.runOnIdle { assertEquals(8, taps) }
    }

    @Test fun nativeCameraCardinalsResetAndNavigationExclusivity() {
        val navigation = mutableStateOf(NavigationCameraState(following = false))
        var ready = false
        lateinit var native: MapLibreMap
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        rule.setContent {
            BusNavTheme {
                needle = MaterialTheme.colorScheme.error
                MapScreen(null, false, 0, null, 0,
                    navigationCamera = navigation.value,
                    onToggleOrientation = {},
                    initialCamera = EditorCamera(GeoPoint(35.68, 139.76), 15.5, 0.0, 0.0),
                    basemapConfig = BasemapConfig(null, BasemapMode.FALLBACK),
                    onMapReady = { ready = true }, onMapGesture = {}, onMapError = {})
            }
        }
        rule.waitUntil(30_000) { ready }
        rule.runOnUiThread {
            requireNotNull(findMap(rule.activity.window.decorView)).getMapAsync { native = it }
        }
        fun awaitBearing(expected: Double) {
            rule.waitUntil(10_000) {
                var matches = false
                rule.runOnUiThread {
                    matches = abs(shortestHeadingDelta(native.cameraPosition.bearing, expected)) < 0.05
                }
                matches
            }
            rule.waitForIdle()
        }
        for (degrees in listOf(0.0, 90.0, 180.0, 270.0)) {
            rule.runOnUiThread {
                native.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(native.cameraPosition).bearing(degrees).build()))
            }
            awaitBearing(degrees)
            if (degrees == 0.0) {
                rule.onNodeWithTag("general_north_compass").assertDoesNotExist()
            } else {
                // The camera callback is deliberately coalesced by MapController.
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithTag("general_north_compass").fetchSemanticsNodes().isNotEmpty()
                }
                Thread.sleep(200)
                rule.waitForIdle()
                assertPointer(degrees, "map-${degrees.toInt()}")
                rule.runOnUiThread {
                    val target = requireNotNull(native.cameraPosition.target)
                    val center = native.projection.toScreenLocation(target)
                    val north = native.projection.toScreenLocation(LatLng(target.latitude + 0.001, target.longitude))
                    val projected = normalizeHeading(Math.toDegrees(atan2(
                        (north.x - center.x).toDouble(), (center.y - north.y).toDouble())))
                    assertEquals(0.0, shortestHeadingDelta(projected, northScreenRotation(degrees)), 0.5)
                }
                lateinit var before: CameraPosition
                rule.runOnUiThread { before = native.cameraPosition }
                rule.onNodeWithTag("general_north_compass").performClick()
                awaitBearing(0.0)
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithTag("general_north_compass").fetchSemanticsNodes().isEmpty()
                }
                rule.runOnUiThread {
                    val after = native.cameraPosition
                    assertEquals(before.target!!.latitude, after.target!!.latitude, 0.000001)
                    assertEquals(before.target!!.longitude, after.target!!.longitude, 0.000001)
                    assertEquals(before.zoom, after.zoom, 0.00001)
                    assertEquals(before.tilt, after.tilt, 0.00001)
                }
                rule.runOnIdle { assertFalse(navigation.value.active) }
            }
            rule.onNodeWithTag("navigation_compass").assertDoesNotExist()
        }
        rule.runOnUiThread {
            native.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(native.cameraPosition).bearing(90.0).tilt(30.0).build()))
        }
        awaitBearing(90.0)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("general_north_compass").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("general_north_compass").performClick()
        awaitBearing(0.0)
        rule.runOnUiThread { assertEquals(30.0, native.cameraPosition.tilt, 0.00001) }
        rule.runOnIdle { navigation.value = navigation.value.copy(active = true) }
        rule.onNodeWithTag("general_north_compass").assertDoesNotExist()
        rule.onNodeWithTag("navigation_compass").assertIsDisplayed()
        rule.runOnUiThread {
            native.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(native.cameraPosition).bearing(180.0).build()))
        }
        awaitBearing(180.0)
        rule.onNodeWithTag("general_north_compass").assertDoesNotExist()
        rule.onNodeWithTag("navigation_compass").assertIsDisplayed()
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
}
