package net.nobu0707.busnav.ui.free

import android.Manifest
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.data.routing.valhalla.*
import net.nobu0707.busnav.data.storage.prescribed.*
import net.nobu0707.busnav.developer.createConnectionRepository
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.prescribed.NavigationMode
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.map.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.prescribed.prescribedFixture
import net.nobu0707.busnav.test.*
import net.nobu0707.busnav.ui.navigation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

class FreeNavigationFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun generalGuidanceCursorRecreationRecalcArrivalEndAndLibraryIsolation() = flow(false, false)
    @Test fun highwayGuidanceCursorRecreationRecalcArrivalEndAndLibraryIsolation() = flow(true, false)
    @Test fun livePublicKantoCurrentLocationPreviewExplicitStart() {
        effectiveTestConnections()
        LocalValhallaAssumptions.assumeAvailable()
        LocalBasemapAssumptions.assumeAvailable()
        flow(false, true)
    }

    private fun flow(highway: Boolean, live: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, PrescribedRouteDatabase::class.java).build()
        val library = RoomPrescribedRouteRepository(db)
        val saved = prescribedFixture()
        runBlocking { library.save(saved) }
        val calls = AtomicInteger()
        var failRouting = false
        val start = GeoPoint(35.6812,139.7671)
        val destination = if (live) GeoPoint(35.7138,139.7773) else GeoPoint(35.6812,139.7771)
        val positions = MutableStateFlow<LocationUpdate>(LocationUpdate.Disabled)
        val provider = object : LocationProvider {
            override fun updates() = repeatingSyntheticLocations(positions)
            override fun isLocationEnabled() = true
        }
        val connections = createConnectionRepository(context)
        val delegate = ValhallaRoutingEngine(RoutingConfig(LocalValhallaAssumptions.BASE_URL),
            baseUrlProvider = { connections.settings.first().valhallaBaseUrl })
        val engine = RoutingEngine { request ->
            calls.incrementAndGet()
            assertEquals(VehicleProfile.DEVELOPMENT_LARGE_BUS, request.vehicleProfile)
            if (failRouting) RoutingResult.Failure(RoutingFailure.NETWORK)
            else if (live) delegate.calculateRoute(request)
            else {
                val geometry = RouteGeometry(listOf(request.origin, request.destination))
                RoutingResult.Success(ScheduledRoute(request.routePlanId, "公開試験経路", geometry,
                    request.points.map { RoutePoint(it.id, RoutePointType.valueOf(it.type.name), it.position) },
                    guidance = RouteGuidance(listOf(RouteManeuver(0,
                        if (highway) ManeuverType.KEEP_LEFT else ManeuverType.RIGHT, "", 1, 1)))),
                    RoutingSummary(900.0,180.0))
            }
        }
        for (permission in listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName, permission)
        fun attach() = rule.runOnUiThread {
            MapLibre.getInstance(rule.activity)
            rule.activity.setContent {
                NavigationRoute(provider, InMemoryScheduledRouteRepository(null), engine, library, connections,
                    presentationClock = Clock.fixed(Instant.parse("2026-06-21T14:00:00Z"), ZoneId.of("Asia/Tokyo")),
                    basemapConfig = if (live) BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL, BasemapRegion.KANTO, true)
                        else BasemapConfig.fromBuildValue("", true))
            }
        }
        fun nav() = ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder
        fun free() = ViewModelProvider(rule.activity)[FreeNavigationViewModel::class.java].holder
        fun waitTag(tag: String) = rule.waitUntil(30000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        fun position(point: GeoPoint) {
            positions.value = LocationUpdate.Position(LocationState(point,5f,90f,null,0,SystemClock.elapsedRealtime()))
        }
        fun screenshot(label: String) {
            rule.waitUntil(20000) {
                var rendered = false
                rule.runOnUiThread {
                    val view = findMap(rule.activity.window.decorView)
                    view?.getMapAsync { native ->
                        rendered = native.style?.isFullyLoaded == true &&
                            view.isAttachedToWindow && view.width > 0 &&
                            native.style?.getLayer(OverlayLayerOrder.VEHICLE) != null &&
                            (nav().uiState.value.activeRoute == null ||
                                native.style?.getLayer(RouteOverlayController.LINE_LAYER_ID) != null)
                    }
                }
                rendered
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(1000) // Wait for platform rotation and GPU rendering after checking installed overlays.
            if (label.startsWith("preview")) rule.runOnUiThread {
                val view = requireNotNull(findMap(rule.activity.window.decorView))
                val route = requireNotNull(free().state.value.previewRoute)
                view.getMapAsync { native ->
                    val points = route.geometry.points
                    val extremes = listOf(points.minBy { it.latitude }, points.maxBy { it.latitude },
                        points.minBy { it.longitude }, points.maxBy { it.longitude })
                    for (point in extremes) {
                        val pixel = native.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                        assertTrue("Preview geometry must fit the visible map", pixel.x in 0f..view.width.toFloat() &&
                            pixel.y in 0f..view.height.toFloat())
                    }
                }
            }
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val suffix = if (live) "live" else if (highway) "highway" else "general"
            val file = java.io.File(context.getExternalFilesDir(null), "free-$suffix-$label.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            instrumentation.uiAutomation.executeShellCommand("cp " + file.absolutePath + " /sdcard/Download/busnav-free-$suffix-$label.png").close()
        }
        fun count() = runBlocking { library.observeAll().first().size }
        fun recreate() { rule.activityRule.scenario.recreate(); attach() }
        fun checkTheme(dark: Boolean) {
            rule.waitUntil(20000) {
                var matches = false
                rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync { native ->
                    matches = native.style?.let { it.isFullyLoaded && (it.uri.contains("-light") != dark) } == true
                } }
                matches
            }
        }
        try {
            attach(); waitTag(NavigationTestTags.MAP); position(start)
            rule.waitUntil(15000) { nav().uiState.value.location != null }
            rule.onNodeWithTag(NavigationTestTags.ROUTE_EDIT).performClick()
            rule.onNodeWithTag("open_free").performClick(); waitTag("free_cursor")
            recreate(); waitTag("free_cursor")
            screenshot("selector")
            var centered = false
            rule.waitUntil(15000) {
                rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync { native ->
                    if (native.style?.isFullyLoaded == true) {
                        native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(destination.latitude,destination.longitude),14.0))
                        centered = true
                    }
                } }
                centered
            }
            rule.onNodeWithTag("free_set_destination").performClick()
            waitTag("free_calculate")
            val selected = free().state.value.plan!!
            assertEquals(destination.latitude, selected.destination.latitude, 0.00003)
            assertEquals(destination.longitude, selected.destination.longitude, 0.00003)
            recreate(); waitTag("free_calculate")
            assertEquals(selected, free().state.value.plan)
            rule.onNodeWithTag("free_calculate").assertIsDisplayed().performClick()
            waitTag("free_preview")
            val preview = free().state.value.previewRoute!!
            assertEquals(start, preview.start.position)
            assertEquals(1, calls.get()); assertFalse(nav().uiState.value.isNavigationStarted); assertEquals(1,count())
            checkTheme(false)
            screenshot("preview")
            if (!live && !highway) {
                rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                attach(); waitTag("free_preview")
                assertSame(preview, free().state.value.previewRoute)
                screenshot("preview-landscape")
                rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
                attach(); waitTag("free_preview")
            }
            recreate(); waitTag("free_preview")
            assertSame(preview, free().state.value.previewRoute)
            rule.onNodeWithTag("free_start").assertIsDisplayed().performClick()
            waitTag(NavigationTestTags.MAP)
            if (live) position(preview.geometry.first) // Public synthetic fix on the routed road, after asserting raw START.
            rule.waitUntil(15000) { nav().uiState.value.guidance.status == GuidanceStatus.RELIABLE }
            assertEquals(NavigationMode.FREE,nav().uiState.value.navigationMode)
            assertNull(nav().uiState.value.activePrescribedRouteId)
            if (highway) rule.waitUntil(15000) { nav().uiState.value.highwayGuidance?.schematic != null }
            checkTheme(true)
            screenshot("guidance")
            recreate(); waitTag("free_end")
            assertSame(preview, nav().uiState.value.activeRoute)
            assertTrue(nav().uiState.value.isNavigationStarted)
            // A UI switch needs confirmation, and dismissal leaves the active session intact.
            rule.onNodeWithTag(NavigationTestTags.OPERATIONS).performClick()
            waitTag("session_switch_confirm")
            rule.onNodeWithText("キャンセル").performClick()
            assertSame(preview, nav().uiState.value.activeRoute)
            if (!live) {
                position(GeoPoint(start.latitude + 0.0009, start.longitude))
                rule.waitUntil(20000) { nav().uiState.value.deviationSnapshot.state == RouteDeviationState.OFF_ROUTE }
                assertEquals(1,calls.get())
                assertTrue(nav().uiState.value.deviation.message!!.startsWith("案内経路"))
                assertEquals(start.latitude + 0.0009,nav().uiState.value.location!!.point.latitude,0.0)
                failRouting = true
                rule.onNodeWithTag("free_recalculate").performClick()
                waitTag("free_error")
                assertSame(preview,nav().uiState.value.activeRoute)
                rule.onNodeWithTag("free_cancel").performClick()
                failRouting = false
                rule.onNodeWithTag("free_recalculate").performClick()
                waitTag("free_preview")
                assertSame(preview,nav().uiState.value.activeRoute)
                assertEquals(3,calls.get())
                rule.onNodeWithTag("free_start").assertIsDisplayed().performClick()
                waitTag("free_end")
                assertNotSame(preview,nav().uiState.value.activeRoute)
                assertEquals(3,calls.get())
                position(selected.destination)
                rule.waitUntil(25000) { nav().uiState.value.arrival.state == ArrivalState.ARRIVED }
                rule.onNodeWithTag("free_arrived").assertIsDisplayed()
                screenshot("arrived")
                assertNotNull(nav().uiState.value.activeRoute)
            }
            rule.onNodeWithTag("free_end").performClick()
            rule.runOnIdle {
                assertNull(nav().uiState.value.activeRoute)
                assertNull(nav().uiState.value.freePlan)
                assertFalse(nav().uiState.value.isNavigationStarted)
            }
            assertEquals(1,count())
            assertEquals(saved, runBlocking { (library.getById(saved.id) as net.nobu0707.busnav.domain.prescribed.PrescribedRouteLoad.Found).record })
            // Existing library open/start works after ending FREE without another routing request.
            rule.onNodeWithTag(NavigationTestTags.OPERATIONS).performClick()
            waitTag("prescribed_library")
            rule.onNodeWithText("その他").performClick()
            rule.onNodeWithText("ナビに使用").performClick()
            waitTag(NavigationTestTags.MAP)
            assertEquals(NavigationMode.PRESCRIBED,nav().uiState.value.navigationMode)
            assertTrue(nav().uiState.value.isNavigationStarted)
            assertEquals(if (live) 1 else 3,calls.get())
            rule.onNodeWithTag(NavigationTestTags.ROUTE_EDIT).performClick()
            rule.onNodeWithTag("open_free").performClick()
            waitTag("session_switch_confirm")
            rule.onNodeWithTag("session_switch_confirm").performClick()
            waitTag("free_cursor")
            assertNull(nav().uiState.value.activeRoute)
            rule.onNodeWithTag("free_cancel").performClick()
            assertEquals(1,count())
        } finally { rule.runOnUiThread { rule.activity.finish() }; db.close() }
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        return null
    }
}
