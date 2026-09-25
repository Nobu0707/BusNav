package net.nobu0707.busnav.map

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.map.basemap.BasemapConfig
import net.nobu0707.busnav.map.basemap.BasemapMode
import net.nobu0707.busnav.ui.routeplan.EditorCamera
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import kotlin.math.abs

class MapControlsViewportRuntimeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun compassPresetRulerAndLiveCursorProjection() {
        val bottom = mutableIntStateOf(200)
        var reader: (() -> GeoPoint?)? = null
        var ready = false
        rule.runOnUiThread { MapLibre.getInstance(rule.activity) }
        rule.setContent {
            BusNavTheme {
                Box(Modifier.fillMaxSize()) {
                    MapScreen(null, false, 0, null, 0,
                        modifier = Modifier.fillMaxSize(),
                        initialCamera = EditorCamera(GeoPoint(35.68, 139.76), 15.0, 30.0, 0.0),
                        editorBottomPadding = bottom.intValue,
                        selectionMode = MapSelectionMode.ROUTE_POINT,
                        onCursorReader = { reader = it },
                        basemapConfig = BasemapConfig(null, BasemapMode.FALLBACK),
                        onMapReady = { ready = true }, onMapGesture = {}, onMapError = {})
                    MapSelectionCursor(MapSelectionMode.ROUTE_POINT, bottomInsetPx = bottom.intValue)
                }
            }
        }
        rule.waitUntil(30_000) { ready && reader != null }
        rule.onNodeWithTag("general_north_compass").assertExists().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("general_north_compass").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("map_scale_ruler").assertExists()
        rule.onNodeWithTag("map_scale_preset").performClick()
        rule.onNodeWithText("標準").assertExists()
        val view = findMap(rule.activity.window.decorView)!!
        fun cursorY(): Float {
            var y = Float.NaN
            rule.runOnUiThread {
                view.getMapAsync { native ->
                    val geo = requireNotNull(reader?.invoke())
                    y = native.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(
                        geo.latitude, geo.longitude)).y
                }
            }
            rule.waitUntil(5_000) { y.isFinite() }
            return y
        }
        assertEquals((view.height - 200) / 2f, cursorY(), 2f)
        rule.runOnIdle { bottom.intValue = 400 }
        rule.waitForIdle()
        assertEquals((view.height - 400) / 2f, cursorY(), 2f)
    }

    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
}
