package net.nobu0707.busnav.ui.navigation

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.data.routing.valhalla.*
import net.nobu0707.busnav.developer.createConnectionRepository
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.map.RouteOverlayController
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.test.*
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import java.io.File
import kotlin.math.*

/** Public-road geometry plus synthetic offsets only. Does not consume actual device GPS. */
class MatchingRuntimeSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun deterministicDeviationRecoveryPortraitLandscapeAndRotation() = exercise(false)
    @Test fun kantoLiveHighwayMatcherDeviationRecoveryAndRawMarker() = exercise(true)

    private fun exercise(live: Boolean) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val connections=createConnectionRepository(instrumentation.targetContext)
        if(live) {
            effectiveTestConnections()
            runBlocking { connections.update(connections.settings.first().copy(basemapRegion=BasemapRegion.KANTO)) }
            LocalValhallaAssumptions.assumeAvailable();LocalBasemapAssumptions.assumeAvailable()
        }
        val settingsBefore=runBlocking { connections.settings.first() }
        var calls=0
        val engine=RoutingEngine { request ->
            calls++
            ValhallaRoutingEngine(RoutingConfig(LocalValhallaAssumptions.BASE_URL)).calculateRoute(request)
        }
        val route=if(live) {
            val plan=RoutePlan("kanto-matcher-public-road",points=listOf(
                RoutePlanPoint("s",RoutePlanPointType.START,GeoPoint(35.6812,139.7671)),
                RoutePlanPoint("d",RoutePlanPointType.DESTINATION,GeoPoint(35.9062,139.6237))))
            val result=runBlocking { engine.calculateRoute((plan.toRoutingRequest() as RoutingRequestResult.Ready).request) }
            assertTrue(result is RoutingResult.Success)
            (result as RoutingResult.Success).route
        } else {
            val g=RouteGeometry(listOf(GeoPoint(35.0,139.0),GeoPoint(35.0,139.01),GeoPoint(35.0,139.02)))
            ScheduledRoute("synthetic","Synthetic route",g,listOf(RoutePoint("s",RoutePointType.START,g.first),
                RoutePoint("d",RoutePointType.DESTINATION,g.last)),
                guidance=RouteGuidance(listOf(RouteManeuver(0,ManeuverType.RIGHT,"",1,2))))
        }
        val calculator=NavigationProgressCalculator(route)
        val config=RouteMatcherConfig()
        val matcher=RouteMatcher(RouteMatchIndex(calculator.distanceIndex,config),config)
        fun sample(p:GeoPoint,accuracy:Float=5f)=LocationState(p,accuracy,null,0f,System.currentTimeMillis(),SystemClock.elapsedRealtime())
        val pair=if(!live) GeoPoint(35.0,139.004) to GeoPoint(35.001,139.004) else {
            assertTrue(calculator.highwayCalculator.decisions.isNotEmpty())
            val firstRamp = calculator.highwayCalculator.decisions.first { it.type in setOf(HighwayDecisionType.RAMP_LEFT, HighwayDecisionType.RAMP_RIGHT, HighwayDecisionType.RAMP_STRAIGHT) }.distanceAlongRouteMeters
            matcher.index.segments.indices.asSequence().filter { matcher.index.segments[it].lengthMeters>40 && matcher.index.segments[it].startMeters>firstRamp+100 }.mapNotNull { i ->
                val s=matcher.index.segments[i]
                val on=GeoPoint((s.start.latitude+s.end.latitude)/2,(s.start.longitude+s.end.longitude)/2)
                val bearing=Math.toRadians(s.bearingDegrees?:return@mapNotNull null)
                val off=GeoPoint(on.latitude + 100*cos(bearing+Math.PI/2)/111195.0,
                    on.longitude+100*sin(bearing+Math.PI/2)/(111195.0*cos(Math.toRadians(on.latitude))))
                val a=matcher.match(sample(on),RouteMatcherState(),SystemClock.elapsedRealtime()).match?:return@mapNotNull null
                val b=matcher.match(sample(off),RouteMatcherState(),SystemClock.elapsedRealtime()).match?:return@mapNotNull null
                val highway=calculator.highwayCalculator.calculate(a.projection.distanceAlongRouteMeters,ProjectionReliability.RELIABLE)
                if(a.quality==RouteMatchQuality.MATCHED && b.quality==RouteMatchQuality.MATCHED &&
                    b.projection.distanceFromRouteMeters>=75 && highway.phase in
                    setOf(HighwayGuidancePhase.APPROACHING_DECISION,HighwayGuidancePhase.IMMINENT_DECISION)) on to off else null
            }.firstOrNull()?:error("No unambiguous highway fixture with a 100 m synthetic offset")
        }
        val positions=MutableStateFlow<LocationUpdate>(LocationUpdate.Disabled)
        val provider=object:LocationProvider {
            override fun updates()=repeatingSyntheticLocations(positions)
            override fun isLocationEnabled()=true
        }
        listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName,it)
        }
        rule.runOnUiThread {
            MapLibre.getInstance(rule.activity)
            rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        rule.waitUntil(10000) { rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT }
        fun content() { rule.activity.setContent { BusNavTheme { NavigationRoute(provider,InMemoryScheduledRouteRepository(),engine,
            basemapConfig=if(live) BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL,BasemapRegion.KANTO,true)
                else BasemapConfig(null,BasemapMode.FALLBACK)) } } }
        fun holder()=ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder
        fun await(state:RouteDeviationState) = rule.waitUntil(15000) { holder().uiState.value.deviationSnapshot.state==state }
        fun position(p:GeoPoint,accuracy:Float=5f) { positions.value=LocationUpdate.Position(sample(p,accuracy)) }
        fun screenshot(label:String) {
            if(!live) return
            rule.waitUntil(15000) {
                var loaded = false
                rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync { loaded = it.style?.isFullyLoaded == true } }
                loaded
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(1200) // Platform rotation and MapLibre render completion for visual QA.
            val bitmap=instrumentation.uiAutomation.takeScreenshot()?:error("Screenshot unavailable")
            val file=File(instrumentation.targetContext.getExternalFilesDir(null),"matching-$label.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/busnav-matching-$label.png").close()
        }
        rule.runOnUiThread { content() }
        rule.waitUntil(10000) { rule.onAllNodesWithTag(NavigationTestTags.MAP).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle {
            holder().openPrescribedRoute(net.nobu0707.busnav.domain.prescribed.PrescribedRouteRecord(
                "matching-saved", route.name, null,
                net.nobu0707.busnav.domain.routeplan.RoutePlan("matching-plan", points = route.points.map {
                    net.nobu0707.busnav.domain.routeplan.RoutePlanPoint(it.id,
                        net.nobu0707.busnav.domain.routeplan.RoutePlanPointType.valueOf(it.type.name), it.position, it.name)
                }), route, VehicleProfile.DEVELOPMENT_LARGE_BUS, 0, 0))
            holder().startNavigation()
        }
        position(pair.first);await(RouteDeviationState.ON_ROUTE)
        rule.onNodeWithTag("deviation_banner").assertDoesNotExist()
        rule.runOnIdle { assertEquals(GuidanceStatus.RELIABLE,holder().uiState.value.guidance.status) }
        if(live) rule.onNodeWithTag("highway_schematic",useUnmergedTree=true).assertIsDisplayed()
        rule.waitUntil(20000) { holder().uiState.value.isMapReady }
        position(pair.second);await(RouteDeviationState.SUSPECTED_OFF_ROUTE)
        rule.onNodeWithText("所定経路との位置関係を確認中").assertIsDisplayed()
        await(RouteDeviationState.OFF_ROUTE)
        rule.onNodeWithText("所定経路から外れている可能性があります").assertIsDisplayed()
        rule.onNodeWithTag("highway_schematic",useUnmergedTree=true).assertDoesNotExist()
        rule.onNodeWithTag("guidance_distance").assertDoesNotExist()
        rule.runOnIdle {
            assertSame(route,holder().uiState.value.activeRoute)
            assertEquals(pair.second,holder().uiState.value.location!!.point)
            assertEquals(if(live) 1 else 0,calls)
            findMap(rule.activity.window.decorView)!!.getMapAsync {
                assertNotNull(it.style!!.getSource(RouteOverlayController.GEOMETRY_SOURCE_ID))
                assertNotNull(it.style!!.getLayer(RouteOverlayController.LINE_LAYER_ID))
            }
        }
        screenshot("portrait-off-route")
        val before=holder()
        rule.activityRule.scenario.recreate();rule.runOnUiThread { content() }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("deviation_banner").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle { assertSame(before,holder());assertEquals(RouteDeviationState.OFF_ROUTE,holder().uiState.value.deviationSnapshot.state) }
        rule.runOnUiThread { rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(10000) { rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE }
        rule.runOnUiThread { content() }
        rule.onNodeWithText("所定経路から外れている可能性があります").assertIsDisplayed()
        screenshot("landscape-off-route")
        position(pair.first,100f)
        rule.waitUntil(10000) { holder().uiState.value.deviationSnapshot.matchQuality==RouteMatchQuality.UNRELIABLE }
        rule.onNodeWithTag("highway_schematic",useUnmergedTree=true).assertDoesNotExist()
        position(pair.first);await(RouteDeviationState.RECOVERING)
        rule.onNodeWithText("所定経路への復帰を確認中").assertIsDisplayed()
        await(RouteDeviationState.ON_ROUTE)
        rule.onNodeWithTag("deviation_banner").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(GuidanceStatus.RELIABLE,holder().uiState.value.guidance.status)
            assertEquals(if(live) 1 else 0,calls)
            assertEquals(settingsBefore,runBlocking { connections.settings.first() })
        }
        if(live) rule.onNodeWithTag("highway_schematic",useUnmergedTree=true).assertIsDisplayed()
        screenshot("landscape-recovered")
    }

    private fun findMap(view:View):MapView? {
        if(view is MapView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
}
