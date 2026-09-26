package net.nobu0707.busnav.ui.navigation

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.SurfaceView
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import net.nobu0707.busnav.data.route.createDevelopmentSampleRoute
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.prescribed.NavigationMode
import net.nobu0707.busnav.location.LocationState
import net.nobu0707.busnav.map.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.ui.routeplan.EditorCamera
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import java.io.File
import kotlin.math.*

class NavigationConsolidationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun overlayStatesKeepMapBoundsAndModeAwareActions() {
        val state = mutableStateOf(NavigationUiState(locationPermissionState = LocationPermissionState.Granted,
            activeRoute = createDevelopmentSampleRoute(), isNavigationStarted = true,
            navigationMode = NavigationMode.FREE, isRouteLoading = false))
        var recalculated = 0
        var ended = 0
        rule.setContent { BusNavTheme {
            NavigationScreen(state.value, {}, {}, {}, {},
                onFreeRecalculate = { recalculated++ }, onEndNavigation = { ended++ },
                modifier = Modifier.requiredSize(400.dp, 800.dp), mapContent = { Box(it) })
        } }
        fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val map = bounds(NavigationTestTags.MAP)
        assertTrue(map.height > 500 * rule.density.density)
        assertTrue(map.contains(bounds("navigation_top_overlay").center))
        assertTrue(map.contains(bounds("free_map_actions").center))
        assertTrue(bounds("free_end").bottom <= bounds("free_recalculate").top)
        assertFalse(bounds("free_recalculate").overlaps(bounds(NavigationTestTags.CURRENT_LOCATION)))
        rule.onNodeWithTag("free_recalculate").performClick()
        rule.onNodeWithTag("free_end").performClick()
        assertEquals(1, recalculated); assertEquals(1, ended)
        rule.runOnIdle { state.value = state.value.copy(deviation = DeviationUiState("位置情報を確認中")) }
        rule.onNodeWithTag("deviation_banner").assertIsDisplayed()
        assertEquals(map, bounds(NavigationTestTags.MAP))
        rule.runOnIdle { state.value = state.value.copy(arrival = ArrivalSnapshot(ArrivalState.ARRIVED)) }
        rule.onNodeWithTag("free_recalculate").assertDoesNotExist()
        rule.onNodeWithTag("free_end").assertIsDisplayed()
        rule.onNodeWithTag("free_arrived").assertIsDisplayed()
        rule.runOnIdle { state.value = state.value.copy(navigationMode = NavigationMode.PRESCRIBED) }
        rule.onNodeWithTag("free_map_actions").assertDoesNotExist()
        assertEquals(map, bounds(NavigationTestTags.MAP))
        rule.onNodeWithTag(NavigationTestTags.OPERATIONS).assertDoesNotExist()
    }

    @Test fun nativeBoundsSurfaceProjectionAndFollowSurviveResizeAndRotation() {
        val point = GeoPoint(35.68, 139.76)
        val state = mutableStateOf(NavigationUiState(locationPermissionState = LocationPermissionState.Granted,
            activeRoute = createDevelopmentSampleRoute(), isNavigationStarted = true,
            navigationMode = NavigationMode.FREE, isRouteLoading = false,
            location = LocationState(point, 5f, 90f, 8f, 1, SystemClock.elapsedRealtime())))
        val reduced = mutableStateOf(false)
        var ready = false
        var gestures = 0
        var native: MapLibreMap? = null
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        fun content() = rule.activity.setContent { BusNavTheme {
            Box(Modifier.fillMaxSize().padding(bottom = if (reduced.value) 60.dp else 0.dp)) {
                NavigationScreen(state.value, {}, {}, {}, {}, mapContent = { modifier ->
                    MapScreen(state.value.location, true, 0, null, 0, modifier = modifier,
                        initialCamera = EditorCamera(point, 16.0, 90.0, 0.0),
                        navigationCamera = NavigationCameraState(true, true, NavigationMapOrientation.HEADING_UP, 90.0),
                        onToggleOrientation = {},
                        basemapConfig = BasemapConfig(null, BasemapMode.FALLBACK),
                        onMapReady = { ready = true }, onMapGesture = { gestures++ }, onMapError = {})
                })
            }
        } }
        fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
        fun verify(label: String) {
            rule.waitUntil(30_000) { ready }
            rule.waitForIdle()
            val container = rule.onNodeWithTag(BasemapTestTags.CONTAINER).fetchSemanticsNode().boundsInRoot
            lateinit var view: MapView
            rule.runOnUiThread {
                view = views(rule.activity.window.decorView).filterIsInstance<MapView>().single()
                view.getMapAsync { native = it }
            }
            rule.waitUntil(10_000) { native != null && view.width == container.width.roundToInt() && view.height == container.height.roundToInt() }
            // Allow the render thread to deliver the corresponding surface resize.
            rule.waitUntil(10_000) {
                var match = false
                rule.runOnUiThread {
                    val surface = views(view).firstOrNull { it is SurfaceView || it is TextureView }
                    match = surface != null && surface.width == view.width && surface.height == view.height
                    if (surface is SurfaceView) match = match && surface.holder.surfaceFrame.width() == view.width &&
                        surface.holder.surfaceFrame.height() == view.height
                }
                match
            }
            val right = rule.onNodeWithTag("navigation_right_actions").fetchSemanticsNode().boundsInRoot
            val left = rule.onAllNodesWithTag("free_map_actions").fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            val occlusion = maxOf(right.height, left?.height ?: 0f)
            val topBounds = rule.onNodeWithTag("navigation_top_overlay").fetchSemanticsNode().boundsInRoot
            rule.runOnUiThread {
                val map = requireNotNull(native)
                val center = map.cameraPosition.target!!
                val dlat = Math.toDegrees(100.0 / 6371008.8)
                val dlon = dlat / cos(Math.toRadians(center.latitude))
                val origin = map.projection.toScreenLocation(center)
                val north = map.projection.toScreenLocation(LatLng(center.latitude + dlat, center.longitude))
                val east = map.projection.toScreenLocation(LatLng(center.latitude, center.longitude + dlon))
                val ratio = hypot(north.x - origin.x, north.y - origin.y) / hypot(east.x - origin.x, east.y - origin.y)
                assertTrue("$label projection ratio=$ratio", ratio in 0.9f..1.1f)
                val camera = map.cameraPosition
                val padding = requireNotNull(camera.padding)

                val visibleBottom = view.height - occlusion
                val y = map.projection.toScreenLocation(LatLng(point.latitude, point.longitude)).y
                assertEquals("$label bottom margin", 33.9 * view.resources.displayMetrics.density, (visibleBottom - y).toDouble(), 3.0)
                val evidence = "$label orientation=HEADING_UP following=true active=true mapHeight=${view.height} " +
                    "bottomOcclusion=$occlusion topOverlay=$topBounds " +
                    "padding=${padding.toList()} pointY=$y visibleBottom=$visibleBottom bottomDistance=${visibleBottom-y} isotropy=$ratio"
                android.util.Log.i("NavigationOverlayEvidence", evidence)
                val evidenceDir = File(rule.activity.getExternalFilesDir(null), "phase0106f").apply { mkdirs() }
                File(evidenceDir, "projection.txt").appendText(evidence + "\n")
            }
            fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val mapBounds = bounds(NavigationTestTags.MAP)
            assertEquals(mapBounds, container)
            val overlay = bounds("navigation_top_overlay")
            val margin = 8 * rule.density.density
            assertEquals(mapBounds.left + margin, overlay.left, 1f)
            assertEquals(mapBounds.right - margin, overlay.right, 1f)
            val guidance = bounds(NavigationTestTags.NEXT_GUIDANCE)
            assertEquals(overlay.left, guidance.left, 1f)
            assertEquals(overlay.right, guidance.right, 1f)
            rule.onAllNodesWithTag("deviation_banner").fetchSemanticsNodes().firstOrNull()?.let {
                assertEquals(overlay.left, it.boundsInRoot.left, 1f)
                assertEquals(overlay.right, it.boundsInRoot.right, 1f)
            }
            for (tag in listOf("navigation_compass", "map_zoom_in", "map_zoom_out", "map_scale_ruler")) {
                val node = rule.onNodeWithTag(tag)
                if (container.height / rule.density.density < 420) node.performScrollTo()
                node.assertIsDisplayed()
                assertTrue("$label $tag below overlay", bounds(tag).top >= overlay.bottom + margin - 1f)
            }
            rule.onNodeWithTag(NavigationTestTags.OPERATIONS).assertDoesNotExist()
            val ruler = rule.onNodeWithTag("map_scale_ruler").fetchSemanticsNode().boundsInRoot
            assertTrue("$label ruler width=${ruler.width}, density=${view.resources.displayMetrics.density}", ruler.width <= 68 * view.resources.displayMetrics.density + 1f)
            val zoom = rule.onNodeWithTag("map_zoom_out").fetchSemanticsNode().boundsInRoot
            val current = rule.onNodeWithTag(NavigationTestTags.CURRENT_LOCATION).fetchSemanticsNode().boundsInRoot
            assertFalse("$label controls overlap", zoom.overlaps(current))
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            // Semantics can settle before the GL frame reaches the display.
            SystemClock.sleep(500)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val dir = File(rule.activity.getExternalFilesDir(null), "phase0106f").apply { mkdirs() }
            File(dir, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        try {
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                rule.runOnUiThread { rule.activity.requestedOrientation = orientation }
                val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) 1 else 2
                rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == expected }
                rule.runOnUiThread {
                    ready = false; native = null; reduced.value = false
                    state.value = state.value.copy(location = state.value.location!!.copy(elapsedRealtimeMillis = SystemClock.elapsedRealtime()))
                    content()
                }
                verify("orientation-$orientation")
                var before = org.maplibre.android.camera.CameraPosition.Builder().build()
                rule.runOnUiThread { before = native!!.cameraPosition }
                if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    rule.onNodeWithTag("map_zoom_in").performScrollTo()
                rule.onNodeWithTag("map_zoom_in").performClick()
                rule.runOnUiThread {
                    val after = native!!.cameraPosition
                    assertEquals(before.zoom + 1, after.zoom, 0.01)
                    assertEquals(before.target!!.latitude, after.target!!.latitude, 0.000001)
                    assertEquals(before.target!!.longitude, after.target!!.longitude, 0.000001)
                    assertEquals(before.bearing, after.bearing, 0.001)
                    assertArrayEquals(before.padding, after.padding, 0.01)
                }
                if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    rule.onNodeWithTag("map_zoom_out").performScrollTo()
                rule.onNodeWithTag("map_zoom_out").performClick()
                assertEquals(0, gestures)
                rule.runOnIdle {
                    state.value = state.value.copy(deviation = DeviationUiState("位置情報を確認中"),
                        guidance = state.value.guidance.copy(secondaryText = "位置が不確実です", nextNextInstruction = "その先を確認"),
                        location = state.value.location!!.copy(elapsedRealtimeMillis = SystemClock.elapsedRealtime()))
                    reduced.value = true
                }
                verify("resized-$orientation")
                rule.runOnIdle { state.value = state.value.copy(navigationMode = NavigationMode.PRESCRIBED,
                    deviation = DeviationUiState(), location = state.value.location!!.copy(elapsedRealtimeMillis = SystemClock.elapsedRealtime())) }
                verify("prescribed-$orientation")
            }
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }
}
