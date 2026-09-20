package net.nobu0707.busnav.map

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import net.nobu0707.busnav.data.route.createDevelopmentSampleRoute
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.test.LocalBasemapAssumptions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource

class BasemapHotReloadTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun fallbackToDetailedStyleKeepsRouteAllPointTypesAndCamera() {
        LocalBasemapAssumptions.assumeAvailable()
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        lateinit var view: MapView
        lateinit var nativeMap: MapLibreMap
        lateinit var controller: MapController
        var loaded = false
        val route = createDevelopmentSampleRoute()
        val plan = RoutePlan("reload", points = listOf(
            RoutePlanPoint("start", RoutePlanPointType.START, GeoPoint(35.18, 136.90)),
            RoutePlanPoint("via", RoutePlanPointType.VIA, GeoPoint(35.19, 136.91)),
            RoutePlanPoint("shaping", RoutePlanPointType.SHAPING, GeoPoint(35.20, 136.92)),
            RoutePlanPoint("dest", RoutePlanPointType.DESTINATION, GeoPoint(35.21, 136.93)),
        ))
        rule.runOnUiThread {
            view = MapView(rule.activity).also { it.onCreate(null); it.onStart(); it.onResume() }
            controller = MapController(onReady = { loaded = true }, onGesture = {}, onError = {},
                onLongPress = {}, routePaddingPx = 40,
                basemapConfig = BasemapConfig(null, BasemapMode.FALLBACK),
                mapDiagnostics = NoOpMapDiagnostics, onBasemapStateChanged = {})
            controller.updateRoute(route, 0); controller.updateRoutePlan(plan, 0)
            controller.attach(view)
            view.getMapAsync { nativeMap = it }
        }
        rule.setContent {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
            DisposableEffect(Unit) { onDispose { controller.detach(); view.onPause(); view.onStop(); view.onDestroy() } }
        }
        rule.waitUntil(15_000) { loaded }
        var before = ""
        rule.runOnUiThread {
            before = nativeMap.cameraPosition.toString()
            controller.updateBasemap(BasemapConfig.fromBuildValue(LocalBasemapAssumptions.STYLE_URL, true))
        }
        rule.waitUntil(30_000) {
            var complete = false
            rule.runOnUiThread {
                val style = nativeMap.style
                val source = style?.getSource(RoutePlanOverlayController.POINT_SOURCE_ID) as? GeoJsonSource
                complete = style?.isFullyLoaded == true && source != null
            }
            complete
        }
        rule.runOnUiThread {
            val style = requireNotNull(nativeMap.style)
            listOf(RoutePlanOverlayController.START_LAYER_ID, RoutePlanOverlayController.VIA_LAYER_ID,
                RoutePlanOverlayController.SHAPING_LAYER_ID, RoutePlanOverlayController.DESTINATION_LAYER_ID,
                RouteOverlayController.LINE_LAYER_ID).forEach { assertNotNull(style.getLayer(it)) }
            val geometry = style.getSource(RouteOverlayController.GEOMETRY_SOURCE_ID) as GeoJsonSource
            assertNotNull(geometry)
            assertEquals(before, nativeMap.cameraPosition.toString())
        }
    }
}
