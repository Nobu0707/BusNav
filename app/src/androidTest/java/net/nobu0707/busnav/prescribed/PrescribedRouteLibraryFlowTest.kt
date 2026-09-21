package net.nobu0707.busnav.prescribed

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.data.storage.prescribed.*
import net.nobu0707.busnav.domain.prescribed.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.map.basemap.BasemapConfig
import net.nobu0707.busnav.ui.navigation.*
import net.nobu0707.busnav.ui.routeplan.*
import net.nobu0707.busnav.ui.prescribed.PrescribedRouteLibraryViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import java.util.UUID

class PrescribedRouteLibraryFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun createSaveRecreateOfflineOpenEditCancelOverwriteRenameDuplicateDelete() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "isolated-ui-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, PrescribedRouteDatabase::class.java, name).build()
        val repo = RoomPrescribedRouteRepository(db)
        val fixture = prescribedFixture()
        var calls = 0
        var offline = false
        val engine = RoutingEngine { request ->
            calls++
            check(!offline) { "Server unavailable" }
            RoutingResult.Success(ScheduledRoute("result-$calls", "公開テスト経路", fixture.route.geometry,
                request.points.map { RoutePoint(it.id, RoutePointType.valueOf(it.type.name), it.position, it.name) },
                fixture.route.metadata, fixture.route.guidance), RoutingSummary(1234.56789,987.654321))
        }
        val provider = object : LocationProvider {
            override fun updates() = emptyFlow<LocationUpdate>()
            override fun isLocationEnabled() = false
        }
        fun attach() = rule.runOnUiThread {
            MapLibre.getInstance(rule.activity)
            rule.activity.setContent {
                NavigationRoute(provider, InMemoryScheduledRouteRepository(null), engine,
                    prescribedRouteRepository = repo, basemapConfig = BasemapConfig.fromBuildValue("", true))
            }
        }
        fun editor() = ViewModelProvider(rule.activity)[RoutePlanEditorViewModel::class.java].stateHolder
        fun nav() = ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder
        fun library() = ViewModelProvider(rule.activity)[PrescribedRouteLibraryViewModel::class.java].holder
        fun waitTag(tag: String) = rule.waitUntil(20000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        fun openLibrary() { waitTag(NavigationTestTags.OPERATIONS); rule.onNodeWithTag(NavigationTestTags.OPERATIONS).performClick(); waitTag("prescribed_library") }
        fun records() = runBlocking { repo.observeAll().first() }
        fun record(id: String) = runBlocking { (repo.getById(id) as PrescribedRouteLoad.Found).record }
        fun calculateApply() {
            rule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATE).performClick()
            waitTag(RoutePlanEditorTestTags.RESULT)
            rule.onNodeWithTag(RoutePlanEditorTestTags.POINT_LIST).performScrollToNode(hasTestTag(RoutePlanEditorTestTags.APPLY))
            rule.onNodeWithTag(RoutePlanEditorTestTags.APPLY).performClick()
        }
        try {
            attach()
            openLibrary()
            waitTag("library_empty")
            rule.onNodeWithTag("library_create").performClick()
            rule.runOnIdle { editor().replacePlan(fixture.routePlan) }
            calculateApply()
            openLibrary()
            rule.onNodeWithTag("library_save_current").performClick()
            rule.onNodeWithTag("library_name").performTextReplacement("公開テスト保存経路")
            rule.onNodeWithTag("library_save_confirm").performClick()
            waitTag(NavigationTestTags.MAP)
            val original = records().single()
            val saved = record(original.id)
            assertEquals(fixture.route.geometry, saved.route.geometry)
            assertEquals(1, calls)

            rule.activityRule.scenario.recreate()
            attach()
            openLibrary()
            rule.onNodeWithTag("library_route_" + original.id).assertExists()
            offline = true
            rule.onNodeWithText("開く").performClick()
            waitTag(NavigationTestTags.MAP)
            rule.waitUntil(20000) { nav().uiState.value.isMapReady }
            rule.runOnIdle {
                assertEquals(saved.route, nav().uiState.value.activeRoute)
                assertEquals(saved.id, nav().uiState.value.activePrescribedRouteId)
                assertEquals(saved.route.guidance, nav().uiState.value.activeRoute!!.guidance)
                assertFalse(nav().uiState.value.isNavigationStarted)
            }
            var lineInstalled = false
            rule.waitUntil(15000) {
                rule.runOnUiThread { findMap(rule.activity.window.decorView)?.getMapAsync { map ->
                    lineInstalled = map.style?.getLayer(net.nobu0707.busnav.map.RouteOverlayController.LINE_LAYER_ID) != null
                } }
                lineInstalled
            }
            assertEquals(1, calls)
            openLibrary()
            rule.onNodeWithText("編集", substring = false).performClick()
            waitTag(RoutePlanEditorTestTags.SCREEN)
            rule.runOnIdle { editor().removePoint("via") }
            rule.onNodeWithTag(RoutePlanEditorTestTags.BACK).performClick()
            assertEquals(saved, record(original.id))
            assertEquals(1, calls)

            openLibrary()
            rule.onNodeWithText("編集", substring = false).performClick()
            waitTag(RoutePlanEditorTestTags.SCREEN)
            rule.runOnIdle { editor().removePoint("via") }
            offline = false
            calculateApply()
            waitTag("library_save_confirm")
            rule.onNodeWithTag("library_save_confirm").performClick()
            waitTag(NavigationTestTags.MAP)
            val updated = record(original.id)
            assertEquals(3, updated.routePlan.points.size)
            assertEquals(saved.createdAtEpochMillis, updated.createdAtEpochMillis)
            assertEquals(2, calls)

            offline = true
            openLibrary()
            rule.onNodeWithText("その他").performClick()
            rule.onNodeWithText("名前変更").performClick()
            rule.onNodeWithTag("library_name").performTextReplacement("公開テスト改名")
            rule.onNodeWithTag("library_save_confirm").performClick()
            rule.waitUntil(10000) { library().state.value.routes.single().name == "公開テスト改名" }
            rule.onNodeWithText("その他").performClick()
            rule.onNodeWithText("複製").performClick()
            rule.waitUntil(10000) { library().state.value.routes.size == 2 }
            val duplicate = records().single { it.id != original.id }
            val card = rule.onNodeWithTag("library_route_" + duplicate.id)
            card.performScrollTo()
            rule.onNode(hasText("その他") and hasAnyAncestor(hasTestTag("library_route_" + duplicate.id))).performClick()
            rule.onNodeWithText("削除", substring = false).performClick()
            rule.onNodeWithTag("library_delete_confirm").performClick()
            rule.waitUntil(10000) { library().state.value.routes.size == 1 }
            assertEquals(updated.route, record(original.id).route)
            assertEquals(2, calls)
            rule.onNodeWithText("経路の使用を終了").performScrollTo().performClick()
            rule.onNodeWithText("その他").performClick()
            rule.onNodeWithText("削除", substring = false).performClick()
            rule.onNodeWithTag("library_delete_confirm").performClick()
            waitTag("library_empty")
        } finally { rule.runOnUiThread { rule.activity.finish() }; db.close(); context.deleteDatabase(name) }
    }
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
}