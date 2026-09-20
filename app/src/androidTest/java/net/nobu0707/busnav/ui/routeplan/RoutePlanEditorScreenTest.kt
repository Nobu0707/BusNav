package net.nobu0707.busnav.ui.routeplan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.RoutePlanPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.ui.theme.BusNavTheme
import net.nobu0707.busnav.data.route.createDevelopmentSampleRoute
import net.nobu0707.busnav.domain.routing.RoutingFailure
import net.nobu0707.busnav.domain.routing.RoutingSummary
import net.nobu0707.busnav.ui.routing.RouteCalculationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RoutePlanEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun portraitEmptyPlanShowsMainRegions() {
        setEditor(RoutePlanUiState(), Modifier.requiredSize(400.dp, 800.dp))

        composeRule.onNodeWithTag(RoutePlanEditorTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.MAP).assertIsDisplayed()
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.EMPTY).fetchSemanticsNode()
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.COMPLETE).assertIsDisplayed()
    }

    @Test
    fun landscapePlanShowsEditorAndMap() {
        setEditor(RoutePlanUiState(currentPlan = samplePlan()), Modifier.requiredSize(1000.dp, 450.dp))

        composeRule.onNodeWithTag(RoutePlanEditorTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.MAP).assertIsDisplayed()
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.POINT_LIST).fetchSemanticsNode()
        composeRule.onNodeWithText("仮ルート（経路探索前プレビュー）").assertIsDisplayed()
    }

    @Test
    fun pointActionsInvokeDeleteMoveAndToggleCallbacks() {
        var deleted = ""
        var moved = ""
        var delta = 0
        var toggled = ""
        val plan = samplePlan()
        val via = plan.points.first { it.type == RoutePlanPointType.VIA }
        composeRule.setContent {
            BusNavTheme {
                RoutePlanEditorScreen(
                    uiState = RoutePlanUiState(currentPlan = plan),
                    onBack = {},
                    onSelectAddMode = {},
                    onSelectPoint = {},
                    onRemovePoint = { deleted = it },
                    onMovePoint = { id, amount -> moved = id; delta = amount },
                    onTogglePointType = { toggled = it },
                    onPlanOverview = {},
                    onComplete = {},
                    modifier = Modifier.requiredSize(400.dp, 1100.dp),
                    mapContent = { Box(it) },
                )
            }
        }

        composeRule.onNodeWithTag(RoutePlanEditorTestTags.toggle(via.id)).performScrollTo().performClick()
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.moveDown(via.id)).performScrollTo().performClick()
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.delete(via.id)).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(via.id, toggled)
            assertEquals(via.id, moved)
            assertEquals(1, delta)
            assertEquals(via.id, deleted)
        }
    }

    @Test
    fun editCompleteInvokesCallback() {
        var completed = false
        setEditor(
            state = RoutePlanUiState(),
            modifier = Modifier.requiredSize(400.dp, 800.dp),
            onComplete = { completed = true },
        )
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.COMPLETE).performClick()
        composeRule.runOnIdle { assertTrue(completed) }
    }

    @Test
    fun invalidPlanDisablesCalculateButton() {
        setEditor(RoutePlanUiState(), Modifier.requiredSize(400.dp, 900.dp))
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATE).assertIsNotEnabled()
    }

    @Test
    fun calculatingStateShowsProgressAndDisablesDuplicateTap() {
        setEditor(
            RoutePlanUiState(currentPlan = samplePlan()),
            Modifier.requiredSize(400.dp, 900.dp),
            calculationState = RouteCalculationState.Calculating(0),
        )
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATING).assertIsDisplayed()
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.CALCULATE).assertIsNotEnabled()
    }

    @Test
    fun successStateShowsSummaryAndApplyAction() {
        val plan = samplePlan()
        setEditor(
            RoutePlanUiState(currentPlan = plan, revision = 2),
            Modifier.requiredSize(400.dp, 1100.dp),
            calculationState = RouteCalculationState.Success(2, createDevelopmentSampleRoute(), RoutingSummary(12_300.0, 3_900.0)),
        )
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.RESULT).assertIsDisplayed()
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.APPLY).assertIsEnabled()
    }

    @Test
    fun failureStateShowsUserFacingMessage() {
        val plan = samplePlan()
        setEditor(
            RoutePlanUiState(currentPlan = plan),
            Modifier.requiredSize(400.dp, 1000.dp),
            calculationState = RouteCalculationState.Failure(0, RoutingFailure.NO_ROUTE),
        )
        composeRule.onNodeWithTag(RoutePlanEditorTestTags.FAILURE).assertIsDisplayed()
    }

    private fun setEditor(
        state: RoutePlanUiState,
        modifier: Modifier,
        onComplete: () -> Unit = {},
        calculationState: RouteCalculationState = RouteCalculationState.Idle,
    ) {
        composeRule.setContent {
            BusNavTheme {
                RoutePlanEditorScreen(
                    uiState = state,
                    onBack = {},
                    onSelectAddMode = {},
                    onSelectPoint = {},
                    onRemovePoint = {},
                    onMovePoint = { _, _ -> },
                    onTogglePointType = {},
                    onPlanOverview = {},
                    onComplete = onComplete,
                    calculationState = calculationState,
                    modifier = modifier,
                    mapContent = { Box(it) },
                )
            }
        }
    }

    private fun samplePlan() = RoutePlan(
        id = "plan",
        points = listOf(
            RoutePlanPoint("start", RoutePlanPointType.START, GeoPoint(35.0, 139.0)),
            RoutePlanPoint("via", RoutePlanPointType.VIA, GeoPoint(35.2, 139.2)),
            RoutePlanPoint("shaping", RoutePlanPointType.SHAPING, GeoPoint(35.4, 139.4)),
            RoutePlanPoint("destination", RoutePlanPointType.DESTINATION, GeoPoint(35.6, 139.6)),
        ),
    )
}
