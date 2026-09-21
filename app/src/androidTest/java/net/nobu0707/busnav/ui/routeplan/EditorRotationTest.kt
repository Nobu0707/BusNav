package net.nobu0707.busnav.ui.routeplan

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import net.nobu0707.busnav.MainActivity
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.ui.navigation.NavigationTestTags
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.MapView
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng

class EditorRotationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun portraitLandscapeAndRecreationRetainEditorStateWithoutRefit() {
        rule.waitUntil(15000) { rule.onAllNodesWithTag(NavigationTestTags.ROUTE_EDIT).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(NavigationTestTags.ROUTE_EDIT).performClick()
        rule.onNodeWithText("経路編集").performClick()
        if (rule.onAllNodesWithTag("session_switch_confirm").fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithTag("session_switch_confirm").performClick()
        lateinit var holder: RoutePlanEditorStateHolder
        rule.runOnIdle { holder = ViewModelProvider(rule.activity)[RoutePlanEditorViewModel::class.java].stateHolder }
        awaitMap()
        rule.waitUntil(15000) { holder.uiState.value.cameraRequest == null }
        rule.runOnUiThread {
            find(rule.activity.window.decorView)!!.getMapAsync { it.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.687,139.756),17.0)) }
            holder.selectAddMode(RoutePlanPointType.SHAPING)
            holder.registerCursor(GeoPoint(35.687,139.756))
            holder.setSheetState(EditorSheetState.EXPANDED)
        }
        rule.waitUntil(5000) { holder.camera?.zoom == 17.0 }
        val saved = holder.camera!!
        val plan = holder.uiState.value.currentPlan
        try {
            rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            awaitMap()
            assertRetained(holder, saved, plan)
            rule.activityRule.scenario.recreate()
            awaitMap()
            assertRetained(holder, saved, plan)
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }
    private fun assertRetained(holder: RoutePlanEditorStateHolder, camera: EditorCamera, plan: net.nobu0707.busnav.domain.routeplan.RoutePlan) {
        rule.onNodeWithTag(RoutePlanEditorTestTags.COMPLETE).assertIsDisplayed()
        rule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATE).assertIsDisplayed()
        rule.runOnIdle {
            assertSame(holder, ViewModelProvider(rule.activity)[RoutePlanEditorViewModel::class.java].stateHolder)
            assertEquals(RoutePlanPointType.SHAPING, holder.uiState.value.selectedAddMode)
            assertEquals(EditorSheetState.EXPANDED, holder.uiState.value.sheetState)
            assertEquals(plan, holder.uiState.value.currentPlan)
            assertNull(holder.uiState.value.cameraRequest)
            find(rule.activity.window.decorView)!!.getMapAsync { map ->
                assertEquals(camera.zoom, map.cameraPosition.zoom, .00001)
                assertEquals(camera.center.latitude, map.cameraPosition.target!!.latitude, .000001)
                assertEquals(camera.center.longitude, map.cameraPosition.target!!.longitude, .000001)
            }
            assertEquals(camera.zoom, holder.camera!!.zoom, .00001)
            assertEquals(camera.center.latitude, holder.camera!!.center.latitude, .000001)
            assertEquals(camera.center.longitude, holder.camera!!.center.longitude, .000001)
        }
    }
    private fun awaitMap() {
        rule.waitUntil(30000) {
            var ready = false
            rule.runOnUiThread { find(rule.activity.window.decorView)?.getMapAsync { ready = it.style?.isFullyLoaded == true } }
            ready && rule.onAllNodesWithTag(RoutePlanEditorTestTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun find(v: View): MapView? {
        if (v is MapView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
        return null
    }
}
