package net.nobu0707.busnav.ui.routeplan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.nobu0707.busnav.MainActivity
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValhallaUiRuntimeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var planHolder: RoutePlanEditorStateHolder

    @Before
    fun openRouteEditor() {
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
        calculateAndAssert("探索距離 29.6 km・推定所要時間 35分")
        calculateAndAssert("探索距離 29.6 km・推定所要時間 35分")

        setEndpoints(LONG_START, LONG_DESTINATION)
        repeat(3) {
            calculateAndAssert("探索距離 88.9 km・推定所要時間 1時間59分")
        }

        setEndpoints(SHORT_START, SHORT_DESTINATION)
        calculateAndAssert("探索距離 29.6 km・推定所要時間 35分")

        setEndpoints(LONG_START, LONG_DESTINATION)
        calculateAndAssert("探索距離 88.9 km・推定所要時間 1時間59分")
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

    private fun calculateAndAssert(expectedSummary: String) {
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
        composeRule.onNodeWithText(expectedSummary).assertIsDisplayed()
        composeRule.onNodeWithText("探索結果（道路沿いルート）").assertIsDisplayed()
    }

    private companion object {
        const val ROUTE_TIMEOUT_MILLIS = 120_000L
        val SHORT_START = GeoPoint(35.24010, 138.61081)
        val SHORT_DESTINATION = GeoPoint(35.25416, 138.83650)
        val LONG_START = GeoPoint(35.52755965924169, 138.79653353327427)
        val LONG_DESTINATION = GeoPoint(35.609542457517534, 138.29084069799353)
    }
}
