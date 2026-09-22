package net.nobu0707.busnav.map

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.nobu0707.busnav.domain.traffic.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.test.*
import net.nobu0707.busnav.detour.*
import net.nobu0707.busnav.ui.traffic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.geojson.FeatureCollection

class TrafficOverlayTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun kantoLightDarkReloadKeepsPointLinePolygonAndVehicleOrder() {
        effectiveTestConnections();LocalBasemapAssumptions.assumeAvailable()
        val provider=DebugFixtureTrafficInformationProvider();provider.select(TrafficFixtureScenario.MIXED)
        val events=runBlocking { provider.observeTraffic().first() }.events.filter { it.validity(System.currentTimeMillis())==TrafficValidity.ACTIVE }
        val data=mutableStateOf(events)
        val config=mutableStateOf(BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL,BasemapRegion.KANTO,true).withTheme(false))
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        var ready=false
        rule.setContent {
            MapScreen(null,false,0,detourFixture(13).route,1,trafficEvents=data.value,
                basemapConfig=config.value,onMapReady={ready=true},onMapGesture={},onMapError={})
        }
        rule.waitUntil(20000){ready}
        lateinit var native:MapLibreMap
        rule.runOnUiThread {requireNotNull(findMap(rule.activity.window.decorView)).getMapAsync { native=it }}
        for(dark in listOf(true,false)) {
            rule.runOnIdle {config.value=config.value.withTheme(dark)}
            rule.waitUntil(30000){var loaded=false;rule.runOnUiThread {loaded=native.style?.let {it.isFullyLoaded && it.uri==config.value.styleUrl && it.getLayer(TrafficOverlayController.MARKER_LAYER)!=null}==true};loaded}
            rule.runOnUiThread {
                val style=requireNotNull(native.style)
                for(id in listOf(TrafficOverlayController.LINE_SOURCE,TrafficOverlayController.AREA_SOURCE,TrafficOverlayController.MARKER_SOURCE)) assertNotNull(style.getSource(id))
                val ids=style.layers.map {it.id}
                assertTrue(ids.indexOf(TrafficOverlayController.MARKER_LAYER)<ids.indexOf(OverlayLayerOrder.VEHICLE))
                assertNotNull(style.getImage("busnav-traffic-ROAD_CLOSURE"))
                assertNotNull(style.getImage("busnav-traffic-ROADWORK"))
                assertNotNull(style.getImage("busnav-traffic-ACCIDENT"))
            }
        }
        rule.runOnIdle {data.value=emptyList()}
        rule.waitForIdle()
    }
    private fun findMap(view:View):MapView? {
        if(view is MapView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)findMap(view.getChildAt(i))?.let{return it}
        return null
    }
}
