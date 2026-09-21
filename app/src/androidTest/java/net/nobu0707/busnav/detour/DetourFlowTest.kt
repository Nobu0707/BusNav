package net.nobu0707.busnav.detour

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
import net.nobu0707.busnav.domain.detour.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.prescribed.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.map.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.test.*
import net.nobu0707.busnav.ui.detour.*
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

class DetourFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun offRouteManualPointsPreviewRotationActivationDeviationRejoin() = flow(false, false)
    @Test fun proactiveDetourExplicitCancelAndRejoin() = flow(false, true)
    @Test fun liveKantoOffRouteSavedProfileDetourRejoin() = flow(true, false)
    @Test fun liveKantoProactiveViaDetourRejoin() = flow(true, true)

    private fun flow(live: Boolean, proactive: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        effectiveTestConnections()
        if (live) { LocalValhallaAssumptions.assumeAvailable(); LocalBasemapAssumptions.assumeAvailable() }
        val db = Room.inMemoryDatabaseBuilder(context, PrescribedRouteDatabase::class.java).build()
        val library = RoomPrescribedRouteRepository(db)
        val connections = createConnectionRepository(context)
        val delegate = ValhallaRoutingEngine(RoutingConfig(LocalValhallaAssumptions.BASE_URL),
            baseUrlProvider = { connections.settings.first().valhallaBaseUrl })
        val saved = if (live) runBlocking {
            val plan = RoutePlan("public-kanto", "関東公開試験経路", listOf(
                RoutePlanPoint("s", RoutePlanPointType.START, GeoPoint(35.6812,139.7671)),
                RoutePlanPoint("d", RoutePlanPointType.DESTINATION, GeoPoint(35.7138,139.7773))))
            val profile = detourFixture().vehicleProfile
            val result = delegate.calculateRoute((plan.toRoutingRequest(profile) as RoutingRequestResult.Ready).request)
            assertTrue("Live prescribed route must succeed",result is RoutingResult.Success)
            PrescribedRouteRecord("public-saved","関東公開試験経路",null,plan,(result as RoutingResult.Success).route,profile,1000,1000)
        } else detourFixture(13)
        runBlocking { library.save(saved) }
        val calls = AtomicInteger()
        val requests = mutableListOf<RoutingRequest>()
        val engine = RoutingEngine { request ->
            calls.incrementAndGet(); requests += request
            assertEquals(saved.vehicleProfile, request.vehicleProfile)
            if (live) delegate.calculateRoute(request) else resultFor(request)
        }
        val positions = MutableStateFlow<LocationUpdate>(LocationUpdate.Disabled)
        val provider = object : LocationProvider {
            override fun updates() = repeatingSyntheticLocations(positions)
            override fun isLocationEnabled() = true
        }
        for (permission in listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
            instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName,permission)
        fun attach() = rule.runOnUiThread {
            MapLibre.getInstance(rule.activity)
            rule.activity.setContent {
                NavigationRoute(provider, InMemoryScheduledRouteRepository(null), engine, library, connections,
                    presentationClock = Clock.fixed(Instant.parse("2026-06-21T14:00:00Z"),ZoneId.of("Asia/Tokyo")),
                    basemapConfig = if (live) BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL,BasemapRegion.KANTO,true)
                        else BasemapConfig.fromBuildValue("",true))
            }
        }
        fun nav() = ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder
        fun detour() = ViewModelProvider(rule.activity)[DetourViewModel::class.java].holder
        fun waitTag(tag:String) = rule.waitUntil(30000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        fun position(p:GeoPoint,speed:Float?=0f) { positions.value=LocationUpdate.Position(LocationState(p,5f,null,speed,0,SystemClock.elapsedRealtime())) }
        fun recreate() { rule.activityRule.scenario.recreate();attach() }
        fun center(p:GeoPoint) {
            rule.waitUntil(20000) {
                var ready=false
                rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync { native ->
                    if (native.style?.isFullyLoaded==true) {
                        native.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude,p.longitude),15.0))
                        ready=true
                    }
                } }
                ready
            }
        }
        fun screenshot(label:String) {
            rule.waitUntil(20000) {
                var ready=false
                rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync { native ->
                    ready=native.style?.isFullyLoaded==true && OverlayLayerOrder.orderedLayerIds.all { native.style?.getLayer(it)!=null }
                } }
                ready
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(700)
            val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val name="detour-${if(live) "live" else "fake"}-${if(proactive) "proactive" else "off"}-$label.png"
            val file=java.io.File(context.getExternalFilesDir(null),name)
            file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            instrumentation.uiAutomation.executeShellCommand("cp "+file.absolutePath+" /sdcard/Download/busnav-"+name).close()
        }
        fun checkTheme(dark:Boolean) = rule.waitUntil(20000) {
            var matches=false
            rule.runOnUiThread {findMap(rule.activity.window.decorView)?.getMapAsync {native->
                matches=native.style?.let {it.isFullyLoaded && (it.uri.contains("-light")!=dark)}==true
            }}
            matches
        }
        try {
            attach();waitTag(NavigationTestTags.MAP)
            val original=(runBlocking{library.getById(saved.id)} as PrescribedRouteLoad.Found).record
            rule.runOnIdle {assertTrue(nav().openPrescribedRoute(original));nav().startNavigation()}
            val start=if(live) original.route.geometry.first else point(100.0)
            position(start)
            rule.waitUntil(20000) {nav().uiState.value.lastReliablePrescribedProgress!=null}
            val rawStart=if(proactive) start else GeoPoint(start.latitude+0.0009,start.longitude)
            position(rawStart)
            if (!proactive && !live) {
                rule.waitUntil(20000){nav().uiState.value.deviationSnapshot.state==RouteDeviationState.OFF_ROUTE}
                rule.onNodeWithTag("detour_off_route").performClick()
            } else rule.onNodeWithTag("bottom_迂回").performClick()
            waitTag("detour_screen")
            rule.waitUntil(20000){!detour().state.value.preparing}
            assertEquals(0,calls.get())
            assertTrue("Public route must offer a safe forward candidate",detour().state.value.candidates.isNotEmpty())
            val target=detour().state.value.candidates.first()
            screenshot("candidates")
            if(!live) {recreate();waitTag("detour_screen");assertEquals(target,detour().state.value.candidates.first())}
            rule.onNodeWithTag("detour_target_0").performScrollTo().performClick()
            if(!live) {
                rule.onNodeWithText("復帰地点を変更").performScrollTo().performClick()
                waitTag("detour_cursor");center(target.point)
                rule.onNodeWithTag("detour_set_cursor").performClick()
                assertEquals(RejoinTargetSource.MANUAL,detour().state.value.target!!.source)
            }
            rule.onNodeWithTag("detour_via").performScrollTo().performClick()
            val via=if(live) GeoPoint(35.6870,139.7635) else point(500.0,200.0)
            center(via);rule.onNodeWithTag("detour_set_cursor").performClick()
            if(!live) {
                rule.onNodeWithTag("detour_shaping").performScrollTo().performClick()
                center(point(750.0,200.0));rule.onNodeWithTag("detour_set_cursor").performClick()
                recreate();waitTag("detour_calculate");assertEquals(2,detour().state.value.points.size)
            }
            assertEquals(0,calls.get())
            rule.onNodeWithTag("detour_calculate").performClick()
            rule.waitUntil(60000){detour().state.value.stage==DetourSessionState.PREVIEW || detour().state.value.stage==DetourSessionState.FAILED}
            assertEquals(detour().state.value.error,DetourSessionState.PREVIEW,detour().state.value.stage)
            assertEquals(rawStart,requests.single().origin)
            assertEquals(target.point.latitude,requests.single().destination.latitude,0.00003)
            assertSame(original.route,nav().uiState.value.activeRoute)
            assertEquals(1,calls.get())
            checkTheme(false);screenshot("preview")
            val preview=detour().state.value.candidate!!
            if (live && proactive) {
                val originalDistances=RouteDistanceIndex(original.route.geometry)
                assertTrue("VIA must take the detour away from the original road",
                    preview.route.geometry.points.any {
                        RouteProjector.project(it,original.route.geometry,originalDistances).distanceFromRouteMeters > 30.0
                    })
            }
            if(!live) {
                rule.runOnUiThread {rule.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
                rule.waitUntil(15000){rule.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
                attach();waitTag("detour_activate");assertSame(preview,detour().state.value.candidate);screenshot("preview-landscape")
                rule.runOnUiThread {rule.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
                rule.waitUntil(15000){rule.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT}
                attach();waitTag("detour_activate")
            }
            rule.onNodeWithTag("detour_activate").assertIsDisplayed().performClick();waitTag("detour_active")
            assertEquals(NavigationMode.PRESCRIBED,nav().uiState.value.navigationMode)
            assertEquals(saved.id,nav().uiState.value.activePrescribedRouteId)
            assertSame(original.route,nav().uiState.value.prescribedRouteSnapshot)
            position(preview.route.geometry.first)
            checkTheme(true);screenshot("active")
            if(!live) {
                recreate();waitTag("detour_active");assertSame(preview.route,nav().uiState.value.activeRoute)
                position(point(100.0,250.0))
                rule.waitUntil(20000){nav().uiState.value.deviationSnapshot.state==RouteDeviationState.OFF_ROUTE}
                assertEquals(1,calls.get())
                rule.onNodeWithTag("detour_replan").performClick();waitTag("detour_screen")
                rule.onNodeWithTag("detour_cancel").performClick();waitTag("detour_active")
                assertSame(preview.route,nav().uiState.value.activeRoute)
            }
            position(target.point)
            rule.waitUntil(25000){nav().uiState.value.activeDetour==null}
            assertSame(original.route,nav().uiState.value.activeRoute)
            assertEquals(RejoinState.CONFIRMED,nav().uiState.value.rejoin.state)
            rule.waitUntil(15000){nav().uiState.value.guidance.status==GuidanceStatus.RELIABLE}
            assertEquals(1,calls.get());screenshot("rejoined")
            assertEquals(saved,runBlocking{(library.getById(saved.id) as PrescribedRouteLoad.Found).record})
            if (!live && proactive) {
                position(start)
                rule.waitUntil(20000){nav().uiState.value.lastReliablePrescribedProgress!!.progressMeters < 500}
                rule.onNodeWithTag("bottom_迂回").performClick();waitTag("detour_screen")
                rule.waitUntil(15000){!detour().state.value.preparing}
                rule.onNodeWithTag("detour_target_0").performScrollTo().performClick()
                rule.onNodeWithTag("detour_calculate").performClick();waitTag("detour_activate")
                rule.onNodeWithTag("detour_activate").performClick();waitTag("detour_active")
                rule.onNodeWithTag("detour_end").performClick()
                assertNull(nav().uiState.value.activeDetour);assertSame(original.route,nav().uiState.value.activeRoute)
                assertEquals(2,calls.get())
            }
        } finally {rule.runOnUiThread{rule.activity.finish()};db.close()}
    }
    private fun findMap(view:View):MapView? {
        if(view is MapView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)findMap(view.getChildAt(i))?.let{return it}
        return null
    }
}
