package net.nobu0707.busnav.map

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

class BasemapHotReloadTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun fallbackToDetailedStyleKeepsRouteAllPointTypesAndCamera() {
        LocalBasemapAssumptions.assumeAvailable()
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        val config = mutableStateOf(BasemapConfig(null, BasemapMode.FALLBACK))
        val route = createDevelopmentSampleRoute()
        val plan = RoutePlan("reload", points = listOf(
            RoutePlanPoint("start", RoutePlanPointType.START, GeoPoint(35.18, 136.90)),
            RoutePlanPoint("via", RoutePlanPointType.VIA, GeoPoint(35.19, 136.91)),
            RoutePlanPoint("shaping", RoutePlanPointType.SHAPING, GeoPoint(35.20, 136.92)),
            RoutePlanPoint("dest", RoutePlanPointType.DESTINATION, GeoPoint(35.21, 136.93)),
        ))
        var ready = false
        rule.setContent {
            MapScreen(location = null, isFollowingLocation = false, recenterRequestId = 0,
                activeRoute = route, routeOverviewRequestId = 0, routePlan = plan,
                basemapConfig = config.value, onMapReady = { ready = true },
                onMapGesture = {}, onMapError = {})
        }
        rule.waitUntil(15_000) { ready }
        lateinit var nativeMap: MapLibreMap
        var camera = ""
        lateinit var originalView: MapView
        rule.runOnUiThread {
            originalView = requireNotNull(findMapView(rule.activity.window.decorView))
            originalView.getMapAsync { nativeMap = it; camera = it.cameraPosition.toString() }
            config.value = BasemapConfig.fromBuildValue(LocalBasemapAssumptions.STYLE_URL, true)
        }
        rule.waitUntil(30_000) {
            var complete = false
            rule.runOnUiThread {
                val style = nativeMap.style
                complete = style?.uri == LocalBasemapAssumptions.STYLE_URL && style.isFullyLoaded &&
                    style.getSource(RoutePlanOverlayController.POINT_SOURCE_ID) != null
            }
            complete
        }
        rule.runOnUiThread {
            assertSame(originalView, findMapView(rule.activity.window.decorView))
            val style = requireNotNull(nativeMap.style)
            listOf(RoutePlanOverlayController.START_LAYER_ID, RoutePlanOverlayController.VIA_LAYER_ID,
                RoutePlanOverlayController.SHAPING_LAYER_ID, RoutePlanOverlayController.DESTINATION_LAYER_ID,
                RouteOverlayController.LINE_LAYER_ID).forEach { assertNotNull(style.getLayer(it)) }
            assertNotNull(style.getSource(RouteOverlayController.GEOMETRY_SOURCE_ID))
            assertEquals(camera, nativeMap.cameraPosition.toString())
        }
    }

    private fun findMapView(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findMapView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
