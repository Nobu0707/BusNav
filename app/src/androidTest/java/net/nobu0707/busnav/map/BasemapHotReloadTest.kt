package net.nobu0707.busnav.map

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
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
    @Test fun fallbackToDetailedStyleKeepsRouteAllPointTypesAndCamera() { exercise(null) }
    @Test fun kantoToChubuKeepsRouteAllPointTypesAndCamera() { exercise(BasemapRegion.KANTO) }
    @Test fun chubuToKantoKeepsRouteAllPointTypesAndCamera() { exercise(BasemapRegion.CHUBU) }

    @Test fun kantoDarkToLightKeepsAllOverlays() { exercise(BasemapRegion.KANTO, false) }
    @Test fun kantoLightToDarkKeepsAllOverlays() { exercise(BasemapRegion.KANTO, true) }
    @Test fun chubuDarkToLightKeepsAllOverlays() { exercise(BasemapRegion.CHUBU, false) }
    @Test fun chubuLightToDarkKeepsAllOverlays() { exercise(BasemapRegion.CHUBU, true) }

    private fun exercise(from: BasemapRegion?, targetDark: Boolean? = null) {
        LocalBasemapAssumptions.assumeAvailable()
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        val target = if (targetDark != null) requireNotNull(from) else if (from == BasemapRegion.KANTO) BasemapRegion.CHUBU else BasemapRegion.KANTO
        val targetConfig = BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL, target, true)
            .let { if (targetDark == null) it else it.withTheme(targetDark) }
        val targetUrl = targetConfig.styleUrl!!
        val config = mutableStateOf(if (from == null) BasemapConfig(null, BasemapMode.FALLBACK)
            else BasemapConfig.forRegion(LocalBasemapAssumptions.BASE_URL, from, true)
                .let { if (targetDark == null) it else it.withTheme(!targetDark) })
        val route = createDevelopmentSampleRoute()
        val plan = RoutePlan("reload", points = listOf(
            RoutePlanPoint("start", RoutePlanPointType.START, GeoPoint(35.18, 136.90)),
            RoutePlanPoint("via", RoutePlanPointType.VIA, GeoPoint(35.19, 136.91)),
            RoutePlanPoint("shaping", RoutePlanPointType.SHAPING, GeoPoint(35.20, 136.92)),
            RoutePlanPoint("dest", RoutePlanPointType.DESTINATION, GeoPoint(35.21, 136.93)),
        ))
        var ready = false
        rule.setContent {
            androidx.compose.foundation.layout.Column {
            net.nobu0707.busnav.ui.navigation.DeviationBanner(
                net.nobu0707.busnav.ui.navigation.DeviationUiState("所定経路から外れている可能性があります", true))
            MapScreen(location = net.nobu0707.busnav.location.LocationState(GeoPoint(35.18,136.90),5f,73f,0f,1L), isFollowingLocation = false, recenterRequestId = 0,
                activeRoute = route, routeOverviewRequestId = 0, routePlan = plan,
                basemapConfig = config.value, onMapReady = { ready = true },
                onMapGesture = {}, onMapError = {})
            }
        }
        rule.waitUntil(15_000) { ready }
        lateinit var nativeMap: MapLibreMap
        var camera = ""
        lateinit var originalView: MapView
        rule.runOnUiThread {
            originalView = requireNotNull(findMapView(rule.activity.window.decorView))
            originalView.getMapAsync { nativeMap = it; camera = it.cameraPosition.toString() }
            config.value = targetConfig
        }
        rule.waitUntil(30_000) {
            var complete = false
            rule.runOnUiThread {
                val style = nativeMap.style
                complete = style?.uri == targetUrl && style.isFullyLoaded &&
                    style.getSource(RoutePlanOverlayController.POINT_SOURCE_ID) != null
            }
            complete
        }
        rule.onNodeWithTag("deviation_banner").assertIsDisplayed()
        rule.runOnUiThread {
            assertSame(originalView, findMapView(rule.activity.window.decorView))
            val style = requireNotNull(nativeMap.style)
            listOf(RoutePlanOverlayController.START_LAYER_ID, RoutePlanOverlayController.VIA_LAYER_ID,
                RoutePlanOverlayController.SHAPING_LAYER_ID, RoutePlanOverlayController.DESTINATION_LAYER_ID,
                RouteOverlayController.LINE_LAYER_ID).forEach { assertNotNull(style.getLayer(it)) }
            assertNotNull(style.getSource(RouteOverlayController.GEOMETRY_SOURCE_ID))
            val vehicle=style.getLayer(OverlayLayerOrder.VEHICLE) as org.maplibre.android.style.layers.SymbolLayer
            assertEquals(2f,vehicle.iconSize.value!!,0f)
            assertEquals(73f,vehicle.iconRotate.value!!,0f)
            assertNotNull(style.getSource("busnav-vehicle-source"))
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
