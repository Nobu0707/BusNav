package net.nobu0707.busnav.traffic

import android.Manifest
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.*
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.domain.detour.*
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.domain.traffic.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.test.*
import net.nobu0707.busnav.detour.*
import net.nobu0707.busnav.ui.detour.*
import net.nobu0707.busnav.ui.navigation.*
import net.nobu0707.busnav.ui.traffic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import java.util.concurrent.atomic.AtomicInteger

class TrafficFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun prescribedClosureConflictManualViaExplicitActivationAndRejoin() = exercise(false)
    @Test fun freeClosureAlertsWithoutAutomaticRouteCalculation() = exercise(true)

    private fun exercise(free: Boolean) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        effectiveTestConnections();LocalBasemapAssumptions.assumeAvailable()
        val saved=detourFixture(13)
        val calls=AtomicInteger()
        val engine=RoutingEngine { calls.incrementAndGet();resultFor(it) }
        val positions=MutableStateFlow<LocationUpdate>(LocationUpdate.Disabled)
        val provider=object:LocationProvider {
            override fun updates()=repeatingSyntheticLocations(positions)
            override fun isLocationEnabled()=true
        }
        for(permission in listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
            instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName,permission)
        fun attach()=rule.runOnUiThread {
            MapLibre.getInstance(rule.activity)
            rule.activity.setContent {
                NavigationRoute(provider,InMemoryScheduledRouteRepository(null),engine,
                    basemapConfig=BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL,BasemapRegion.KANTO,true))
            }
        }
        fun nav()=ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder
        fun traffic()=ViewModelProvider(rule.activity)[TrafficViewModel::class.java].holder
        fun detour()=ViewModelProvider(rule.activity)[DetourViewModel::class.java].holder
        fun tag(value:String)=rule.waitUntil(20000){rule.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty()}
        fun position(m:Double) {positions.value=LocationUpdate.Position(LocationState(point(m),5f,90f,0f,0,SystemClock.elapsedRealtime()))}
        attach();tag(NavigationTestTags.MAP)
        rule.onNodeWithTag("bottom_規制").assertIsEnabled().performClick();tag("traffic_panel")
        rule.onNodeWithTag("traffic_status").assertTextEquals("交通情報サービス未接続")
        rule.onNodeWithTag("traffic_source_fixture").performScrollTo().performClick()
        rule.onNodeWithText("受信情報に現在有効な規制はありません").assertExists()
        rule.onNodeWithText("閉じる").performClick()
        rule.runOnIdle {
            if(free){nav().previewFreeRoute(FreeNavigationPlan(saved.route.geometry.last),saved.route);nav().startFreeNavigation()}
            else {nav().openPrescribedRoute(saved);nav().startNavigation()}
        }
        position(100.0)
        rule.waitUntil(20000){nav().uiState.value.trafficProgressMeters!=null}
        rule.onNodeWithTag("bottom_規制").performClick();tag("traffic_panel")
        rule.onNodeWithTag("traffic_scenario").performScrollTo().performClick()
        rule.onNodeWithTag("traffic_fixture_CLOSURE").performClick()
        rule.waitUntil(20000){traffic().state.value.alert?.level==TrafficImpactLevel.BLOCKING}
        rule.onNodeWithText("閉じる").performClick();tag("traffic_alert")
        assertEquals(0,calls.get())
        rule.activityRule.scenario.recreate();attach();tag("traffic_alert")
        assertEquals("開発用交通情報",traffic().state.value.snapshot.source.displayName)
        if(free){
            rule.onNodeWithTag("traffic_consider_detour").assertDoesNotExist()
            assertEquals(0,calls.get());assertSame(saved.route,nav().uiState.value.activeRoute)
            capture("free-warning");return
        }
        capture("closure-warning")
        rule.onNodeWithTag("traffic_consider_detour").performClick();tag("detour_screen")
        rule.waitUntil(20000){!detour().state.value.preparing}
        assertTrue(detour().state.value.candidates.all { it.progressMeters>2600 })
        rule.onNodeWithTag("detour_target_0").performScrollTo().performClick()
        assertEquals(0,calls.get())
        rule.onNodeWithTag("detour_calculate").performClick()
        rule.waitUntil(20000){detour().state.value.stage==DetourSessionState.PREVIEW}
        rule.onNodeWithTag("detour_activate").assertIsNotEnabled();assertEquals(1,calls.get())
        capture("blocked-preview")
        rule.onNodeWithText("編集に戻る").performScrollTo().performClick()
        // Same manual VIA/SHAPING actions as the screen's map cursor callbacks, with deterministic coordinates.
        rule.runOnIdle {
            detour().selectMapMode(DetourMapMode.VIA);detour().setCursor(point(1000.0,200.0))
            detour().selectMapMode(DetourMapMode.SHAPING);detour().setCursor(point(2500.0,200.0))
        }
        rule.onNodeWithTag("detour_calculate").performClick()
        rule.waitUntil(20000){detour().state.value.stage==DetourSessionState.PREVIEW}
        rule.onNodeWithTag("detour_activate").assertIsEnabled()
        assertNull(nav().uiState.value.activeDetour);assertEquals(2,calls.get())
        rule.onNodeWithTag("detour_activate").performClick();tag("detour_active")
        position(2200.0)
        SystemClock.sleep(4500)
        assertNotNull(nav().uiState.value.activeDetour)
        position(2900.0)
        rule.waitUntil(20000){nav().uiState.value.activeDetour==null}
        assertSame(saved.route,nav().uiState.value.activeRoute);assertEquals(2,calls.get())
    }
    private fun findMap(view:View):MapView? {
        if(view is MapView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)findMap(view.getChildAt(i))?.let{return it}
        return null
    }
    private fun capture(label:String) {
        rule.waitForIdle()
        rule.waitUntil(30000) {
            var loaded=false
            rule.runOnUiThread {
                findMap(rule.activity.window.decorView)?.getMapAsync { native ->
                    loaded=native.style?.let { it.isFullyLoaded && OverlayLayerOrder.orderedLayerIds.all { id -> it.getLayer(id)!=null } }==true
                }
            }
            loaded
        }
        SystemClock.sleep(900)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"traffic-$label.png")
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/busnav-traffic-$label.png").close()
    }
}
