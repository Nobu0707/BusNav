package net.nobu0707.busnav.ui.routeplan

import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.emptyFlow
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.map.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.ui.navigation.NavigationRoute
import net.nobu0707.busnav.ui.navigation.NavigationTestTags
import net.nobu0707.busnav.test.LocalBasemapAssumptions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng

class EditorMapRuntimeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var holder: RoutePlanEditorStateHolder
    private lateinit var native: MapLibreMap
    private lateinit var view: MapView
    @Volatile private var rendered = false
    private val tokyo = GeoPoint(35.6812, 139.7671)
    private val ueno = GeoPoint(35.7138, 139.7773)
    private val route = ScheduledRoute("kanto", "東京から上野", RouteGeometry(listOf(tokyo, ueno)), listOf(
        RoutePoint("start", RoutePointType.START, tokyo), RoutePoint("dest", RoutePointType.DESTINATION, ueno)))
    private val candidate = ScheduledRoute("candidate", "探索結果", RouteGeometry(listOf(tokyo, GeoPoint(35.73,139.8), ueno)), route.points)

    private fun open(active: ScheduledRoute?) {
        LocalBasemapAssumptions.assumeAvailable()
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        val provider = object : LocationProvider {
            override fun updates() = emptyFlow<LocationUpdate>()
            override fun isLocationEnabled() = false
        }
        val repository = InMemoryScheduledRouteRepository(active)
        val engine = RoutingEngine { RoutingResult.Success(candidate, RoutingSummary(12000.0, 2400.0)) }
        rule.setContent {
            NavigationRoute(provider, repository, engine,
                basemapConfig = BasemapConfig.fromBuildValue(LocalBasemapAssumptions.STYLE_URL, true))
        }
        rule.runOnIdle { holder = ViewModelProvider(rule.activity)[RoutePlanEditorViewModel::class.java].stateHolder }
        awaitMap()
        rule.runOnUiThread { native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.69, 139.76), 16.0)) }
        rule.waitUntil(5000) { holder.camera?.zoom == 16.0 }
    }
    private fun enter() {
        val previous = view
        rule.onNodeWithTag(NavigationTestTags.ROUTE_EDIT).performClick()
        rule.onNodeWithText("経路編集").performClick()
        if (rule.onAllNodesWithTag("session_switch_confirm").fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithTag("session_switch_confirm").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithTag(RoutePlanEditorTestTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(10000) {
            var changed = false
            rule.runOnUiThread { changed = findMap(rule.activity.window.decorView)?.let { it !== previous } == true }
            changed
        }
        awaitMap()
        rule.waitUntil(15000) { holder.uiState.value.cameraRequest == null }
    }
    private fun awaitMap() {
        rule.waitUntil(30000) {
            var ready = false
            rule.runOnUiThread { findMap(rule.activity.window.decorView)?.let { found ->
                found.getMapAsync { map -> if (map.style?.isFullyLoaded == true) {
                    if (!::view.isInitialized || view !== found) {
                        rendered = false
                        found.addOnDidFinishRenderingFrameListener { fully, _, _ -> rendered = fully }
                    }
                    view = found; native = map; ready = true
                } }
            } }
            ready
        }
    }
    private fun assertFit(points: List<GeoPoint>) {
        val sheetHeight = rule.onNodeWithTag(RoutePlanEditorTestTags.SHEET).fetchSemanticsNode().boundsInRoot.height
        rule.runOnUiThread {
            assertTrue(native.cameraPosition.zoom > 8.0)
            assertTrue(native.cameraPosition.padding?.all { it == 0.0 } != false)
            val bottom = view.height - sheetHeight
            points.forEach { point ->
                val pixel = native.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                assertTrue("Point outside visible map: " + pixel + " bottom=" + bottom, pixel.y >= -1 && pixel.y <= bottom + 2)
                assertTrue(pixel.x >= -1 && pixel.x <= view.width + 1)
            }
        }
    }
    @Test fun noRouteKeepsCameraAndCursorRegistersExactNativeCoordinate() {
        open(null)
        val saved = holder.camera!!
        enter()
        rule.runOnUiThread {
            assertEquals(saved.zoom, native.cameraPosition.zoom, .00001)
            assertEquals(saved.center.latitude, native.cameraPosition.target!!.latitude, .00000001)
        }
        var cursor: GeoPoint? = null
        val sheetHeight = rule.onNodeWithTag(RoutePlanEditorTestTags.SHEET).fetchSemanticsNode().boundsInRoot.height
        rule.runOnUiThread {
            val p = native.projection.fromScreenLocation(PointF(view.width / 2f, (view.height - sheetHeight) / 2f))
            cursor = GeoPoint(p.latitude, p.longitude)
        }
        rule.onNodeWithTag(RoutePlanEditorTestTags.REGISTER).performClick()
        rule.runOnIdle { assertEquals(cursor, holder.uiState.value.currentPlan.points.single().position) }
        rule.onNodeWithTag(RoutePlanEditorTestTags.COMPLETE).performClick()
        rule.onNodeWithTag(NavigationTestTags.ROUTE_EDIT).assertIsDisplayed()
        awaitMap()
        enter()
        rule.runOnUiThread {
            assertEquals(15.0, native.cameraPosition.zoom, .000001)
            val registered = holder.uiState.value.currentPlan.points.single().position
            assertEquals(cursor, registered)
        }
    }
    @Test fun kantoFitPanCursorLongListSheetCalculateAndFinish() {
        open(route)
        enter()
        assertFit(route.geometry.points)
        awaitRendered()
        capture("01-route-fit")
        val before = holder.camera
        // MapLibre GestureDetector uses the native event clock.
        val location = IntArray(2)
        rule.runOnUiThread { view.getLocationOnScreen(location) }
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val down = android.os.SystemClock.uptimeMillis()
        for (i in 0..20) {
            val action = when (i) { 0 -> android.view.MotionEvent.ACTION_DOWN; 20 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
            val event = android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action,
                location[0] + view.width * (.25f + i * .02f), location[1] + view.height * .2f, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
            android.os.SystemClock.sleep(20)
        }
        rule.waitUntil(5000) { holder.camera != before }
        for (i in 0..13) {
            rule.runOnUiThread {
                holder.selectAddMode(when (i) { 0 -> RoutePlanPointType.START; 13 -> RoutePlanPointType.DESTINATION
                    else -> if (i % 2 == 0) RoutePlanPointType.SHAPING else RoutePlanPointType.VIA })
                native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.6812 + i * .002, 139.7671 + i * .0005), 16.0))
            }
            rule.onNodeWithTag(RoutePlanEditorTestTags.REGISTER).performClick()
        }
        val camera = holder.camera
        rule.onNodeWithTag(RoutePlanEditorTestTags.HANDLE).performClick()
        val last = holder.uiState.value.currentPlan.points.last().id
        rule.onNodeWithTag(RoutePlanEditorTestTags.POINT_LIST).performScrollToNode(hasTestTag(RoutePlanEditorTestTags.point(last)))
        rule.onNodeWithTag(RoutePlanEditorTestTags.COMPLETE).assertIsDisplayed()
        capture("02-long-list-expanded")
        rule.onNodeWithTag(RoutePlanEditorTestTags.HANDLE).performTouchInput { swipeDown(endY = height + 160f) }
        rule.onNodeWithTag(RoutePlanEditorTestTags.HANDLE).performTouchInput { swipeDown(endY = height + 160f) }
        rule.runOnIdle { assertEquals(camera, holder.camera) }
        capture("03-sheet-peek")
        rule.onNodeWithTag(RoutePlanEditorTestTags.HANDLE).performTouchInput { swipeUp(endY = -160f) }
        rule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATE).performClick()
        rule.waitUntil(15000) { holder.uiState.value.cameraRequest == null && holder.camera != camera }
        assertFit(candidate.geometry.points)
        rule.onNodeWithTag(RoutePlanEditorTestTags.RESULT).assertIsDisplayed()
        rule.runOnUiThread {
            assertNotNull(native.style!!.getLayer(RouteOverlayController.LINE_LAYER_ID))
            assertNotNull(native.style!!.getSource(RoutePlanOverlayController.POINT_SOURCE_ID))
        }
        capture("04-candidate-fit")
        rule.onNodeWithTag(RoutePlanEditorTestTags.COMPLETE).performClick()
        rule.onNodeWithTag(NavigationTestTags.ROUTE_EDIT).assertIsDisplayed()
    }
    private fun awaitRendered() {
        rule.waitUntil(30000) {
            rendered
        }
    }
    private fun capture(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val directory = java.io.File(rule.activity.getExternalFilesDir(null), "phase0085b-smoke").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(directory, name + ".png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    private fun findMap(v: View): MapView? {
        if (v is MapView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findMap(v.getChildAt(i))?.let { return it }
        return null
    }
}
