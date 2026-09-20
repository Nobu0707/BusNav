package net.nobu0707.busnav.ui.navigation

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.setContent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.map.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.*
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString

/** Real loaded road attributes + stationary synthetic GPS; no device wall-clock changes. */
class ThemeRuntimeSmokeTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun inactiveNightActiveDayNightTunnelExitAndEditor() {
        effectiveTestConnections()
        LocalBasemapAssumptions.assumeAvailable()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val night=Clock.fixed(Instant.parse("2026-06-21T14:00:00Z"),ZoneId.of("Asia/Tokyo"))
        val day=Clock.fixed(Instant.parse("2026-06-21T03:00:00Z"),ZoneId.of("Asia/Tokyo"))
        val clock=mutableStateOf(night)
        val positions=MutableStateFlow<LocationUpdate>(LocationUpdate.Disabled)
        val provider=object:LocationProvider {
            override fun updates()=repeatingSyntheticLocations(positions)
            override fun isLocationEnabled()=true
        }
        val repository=InMemoryScheduledRouteRepository()
        val engine=RoutingEngine { error("Presentation must not issue routing requests") }
        listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName,it)
        }
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        fun content() { rule.activity.setContent { NavigationRoute(provider,repository,engine,presentationClock=clock.value,
            basemapConfig=BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL,BasemapRegion.KANTO,true)) } }
        rule.runOnUiThread { content() }
        fun holder()=ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder
        rule.waitUntil(15000) { rule.onAllNodesWithTag(NavigationTestTags.MAP).fetchSemanticsNodes().isNotEmpty() }
        val point=GeoPoint(35.6812,139.7671)
        fun position(p:GeoPoint) {
            positions.value=LocationUpdate.Position(LocationState(p,5f,73f,0f,System.currentTimeMillis(),SystemClock.elapsedRealtime()))
        }
        rule.runOnIdle { holder().startLocationUpdates() }
        position(point)
        rule.waitUntil(15000) { holder().uiState.value.location!=null && holder().uiState.value.isMapReady }
        val originalActivity=rule.activity
        lateinit var native:MapLibreMap
        lateinit var view:MapView
        rule.runOnUiThread { view=requireNotNull(findMap(rule.activity.window.decorView));view.getMapAsync { native=it } }
        fun awaitTheme(dark:Boolean) {
            val url=BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL,BasemapRegion.KANTO,true).withTheme(dark).styleUrl
            rule.waitUntil(30000) {
                var done=false
                rule.runOnUiThread { done=native.style?.let { it.uri==url && it.isFullyLoaded && it.getLayer(OverlayLayerOrder.VEHICLE)!=null }==true }
                done
            }
            rule.runOnIdle { assertSame(originalActivity,rule.activity);assertSame(view,findMap(rule.activity.window.decorView)) }
        }
        fun screenshot(label:String) {
            instrumentation.waitForIdleSync()
            Thread.sleep(800)
            val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val file=File(instrumentation.targetContext.getExternalFilesDir(null),"presentation-"+label+".png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
            instrumentation.uiAutomation.executeShellCommand("cp "+file.absolutePath+" /sdcard/Download/busnav-presentation-"+label+".png").close()
        }
        awaitTheme(false)
        screenshot("inactive-night-light")
        val geometry=RouteGeometry(listOf(point,GeoPoint(35.69,139.77),GeoPoint(35.70,139.78)))
        val route=ScheduledRoute("presentation","東京・表示検証",geometry,listOf(
            RoutePoint("s",RoutePointType.START,geometry.first),RoutePoint("d",RoutePointType.DESTINATION,geometry.last)),
            guidance=RouteGuidance(listOf(RouteManeuver(0,ManeuverType.RIGHT,"右折",1,2))))
        rule.runOnIdle { holder().applyCalculatedRoute(route) }
        awaitTheme(true);screenshot("active-night-dark")
        rule.runOnIdle { clock.value=day }
        awaitTheme(false);screenshot("active-day-light")
        rule.runOnUiThread { native.moveCamera(CameraUpdateFactory.zoomTo(14.0)) }
        var tunnel:GeoPoint?=null
        var surface:GeoPoint?=null
        rule.waitUntil(30000) {
            rule.runOnUiThread {
                val style=native.style
                val source=style?.getSource("openmaptiles") as? VectorSource
                val detector=TransportationTunnelProvider { style }
                if(style?.isFullyLoaded==true && source!=null) {
                    val features=source.querySourceFeatures(arrayOf("transportation"),null)
                    for(feature in features) {
                        if(feature.getStringProperty("class") !in setOf("motorway","trunk","primary","secondary","tertiary","minor","service")) continue
                        val lines=when(val g=feature.geometry()) {
                            is LineString -> listOf(g.coordinates())
                            is MultiLineString -> g.coordinates()
                            else -> emptyList()
                        }
                        for(line in lines) for((a,b) in line.zipWithNext()) {
                            val p=GeoPoint((a.latitude()+b.latitude())/2,(a.longitude()+b.longitude())/2)
                            if(kotlin.math.abs(p.latitude-point.latitude)>.012 || kotlin.math.abs(p.longitude-point.longitude)>.012) continue
                            val tagged=feature.hasProperty("brunnel") && feature.getStringProperty("brunnel")=="tunnel"
                            if(tagged && tunnel==null && detector.observe(p)==TunnelObservation.TUNNEL) tunnel=p
                            if(!tagged && surface==null && detector.observe(p)==TunnelObservation.SURFACE) surface=p
                            if(tunnel!=null && surface!=null) break
                        }
                        if(tunnel!=null && surface!=null) break
                    }
                }
            }
            tunnel!=null && surface!=null
        }
        position(requireNotNull(tunnel))
        awaitTheme(true);screenshot("active-tunnel-dark")
        position(requireNotNull(surface))
        awaitTheme(false);screenshot("tunnel-exit-light")
        rule.runOnUiThread { native.moveCamera(CameraUpdateFactory.zoomTo(14.0)) }
        screenshot("zoom14-light")
        rule.runOnUiThread { native.moveCamera(CameraUpdateFactory.zoomTo(18.0)) }
        screenshot("zoom18-light")
        rule.runOnUiThread { native.moveCamera(CameraUpdateFactory.zoomTo(14.0)) }

        rule.runOnIdle { clock.value=night }
        awaitTheme(true)
        rule.onNodeWithTag(NavigationTestTags.ROUTE_EDIT).performClick()
        rule.onNodeWithTag(net.nobu0707.busnav.ui.routeplan.RoutePlanEditorTestTags.SCREEN).assertIsDisplayed()
        rule.waitUntil(30000) {
            var light=false
            rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync {
                light=it.style?.let { s -> s.isFullyLoaded && s.uri.contains("-light/") }==true
            } }
            light
        }
        screenshot("editor-night-light")
        rule.onNodeWithTag(net.nobu0707.busnav.ui.routeplan.RoutePlanEditorTestTags.BACK).performClick()
        rule.runOnUiThread { rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15000) { rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE }
        rule.runOnUiThread { content() }
        rule.waitUntil(30000) {
            var dark=false
            rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync {
                dark=it.style?.let { s -> s.isFullyLoaded && !s.uri.contains("-light/") }==true
            } }
            dark
        }
        BottomLabelLayout.labels.forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        screenshot("landscape-night-dark")
        rule.runOnIdle { clock.value=day }
        rule.waitUntil(30000) {
            var light=false
            rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync {
                light=it.style?.let { s -> s.isFullyLoaded && s.uri.contains("-light/") }==true
            } }
            light
        }
        screenshot("landscape-day-light")
    }
    private fun findMap(v:View):MapView? {
        if(v is MapView) return v
        if(v is ViewGroup) for(i in 0 until v.childCount) findMap(v.getChildAt(i))?.let { return it }
        return null
    }
}
