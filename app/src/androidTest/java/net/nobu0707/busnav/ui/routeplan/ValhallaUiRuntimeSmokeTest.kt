package net.nobu0707.busnav.ui.routeplan

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.ComponentActivity
import net.nobu0707.busnav.ui.theme.BusNavTheme
import net.nobu0707.busnav.ui.navigation.NavigationRoute
import net.nobu0707.busnav.location.AndroidLocationProvider
import net.nobu0707.busnav.data.route.InMemoryScheduledRouteRepository
import net.nobu0707.busnav.data.routing.valhalla.RoutingConfig
import net.nobu0707.busnav.data.routing.valhalla.ValhallaRoutingEngine
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.test.LocalValhallaAssumptions
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValhallaUiRuntimeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var planHolder: RoutePlanEditorStateHolder

    @Before
    fun openRouteEditor() {
        LocalValhallaAssumptions.assumeAvailable()
        composeRule.runOnUiThread { org.maplibre.android.MapLibre.getInstance(composeRule.activity) }
        val provider = AndroidLocationProvider(composeRule.activity.applicationContext)
        val repository = InMemoryScheduledRouteRepository()
        val engine = ValhallaRoutingEngine(RoutingConfig(LocalValhallaAssumptions.BASE_URL))
        composeRule.setContent {
            BusNavTheme { NavigationRoute(provider, repository, engine) }
        }
        composeRule.runOnUiThread {
            planHolder = ViewModelProvider(composeRule.activity)[RoutePlanEditorViewModel::class.java]
                .stateHolder
        }
        composeRule.onNodeWithContentDescription("ルート編集画面を開く").performClick()
        composeRule.onNodeWithText("ルート編集").assertIsDisplayed()
    }

    @Test
    fun shortAndLongRoutesSucceedSevenTimesThroughActivityUi() {
        setEndpoints(SHORT_START, SHORT_DESTINATION)
        val shortDistance = calculateAndAssert(SHORT_ROUTE)
        calculateAndAssert(SHORT_ROUTE)

        setEndpoints(LONG_START, LONG_DESTINATION)
        repeat(3) {
            calculateAndAssert(LONG_ROUTE)
        }

        setEndpoints(SHORT_START, SHORT_DESTINATION)
        calculateAndAssert(SHORT_ROUTE)

        setEndpoints(LONG_START, LONG_DESTINATION)
        val longDistance = calculateAndAssert(LONG_ROUTE)

        assertTrue("short route should be shorter than long route", shortDistance < longDistance)
    }

    private fun setEndpoints(start: GeoPoint, destination: GeoPoint) {
        composeRule.runOnUiThread {
            planHolder.selectAddMode(RoutePlanPointType.START)
            planHolder.addPoint(start)
            planHolder.selectAddMode(RoutePlanPointType.DESTINATION)
            planHolder.addPoint(destination)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("出発地・到着地を設定済み").assertIsDisplayed()
    }

    private fun calculateAndAssert(expected: RouteExpectation): Double {
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATE)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.waitUntil(timeoutMillis = ROUTE_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(RoutePlanEditorTestTags.RESULT)
                .fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.RESULT)
            .assertIsDisplayed()
        assertTrue(
            "invalid-response message must not be displayed",
            composeRule.onAllNodesWithText(INVALID_RESPONSE_MESSAGE).fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithText("探索結果（道路沿いルート）").assertIsDisplayed()

        val summary = composeRule.onAllNodes(hasText(SUMMARY_PREFIX, substring = true))
            .fetchSemanticsNodes()
            .single()
            .config[SemanticsProperties.Text]
            .single()
            .text
        val match = requireNotNull(SUMMARY_PATTERN.matchEntire(summary)) {
            "Unexpected route summary: $summary"
        }
        val distanceKm = match.groupValues[1].toDouble()
        val durationMinutes = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt()
            ?: (match.groupValues[3].toInt() * 60 + match.groupValues[4].toInt())
        assertTrue(
            "${expected.name} distance $distanceKm km was outside ${expected.minDistanceKm}..${expected.maxDistanceKm}",
            distanceKm in expected.minDistanceKm..expected.maxDistanceKm,
        )
        assertTrue("${expected.name} duration must be positive", durationMinutes > 0)
        return distanceKm
    }

    private companion object {
        const val ROUTE_TIMEOUT_MILLIS = 120_000L
        const val INVALID_RESPONSE_MESSAGE = "経路探索結果を読み取れませんでした"
        const val SUMMARY_PREFIX = "探索距離"
        val SUMMARY_PATTERN = Regex(
            "探索距離 ([0-9]+(?:\\.[0-9]+)?) km・推定所要時間 (?:(\\d+)分|(\\d+)時間(\\d+)分)",
        )
        val SHORT_ROUTE = RouteExpectation("short route", 10.0, 60.0)
        val LONG_ROUTE = RouteExpectation("long route", 50.0, 150.0)
        val SHORT_START = GeoPoint(35.24010, 138.61081)
        val SHORT_DESTINATION = GeoPoint(35.25416, 138.83650)
        val LONG_START = GeoPoint(35.52755965924169, 138.79653353327427)
        val LONG_DESTINATION = GeoPoint(35.609542457517534, 138.29084069799353)
    }

    private data class RouteExpectation(
        val name: String,
        val minDistanceKm: Double,
        val maxDistanceKm: Double,
    )
}
