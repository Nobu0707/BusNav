package net.nobu0707.busnav.ui.navigation

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import net.nobu0707.busnav.MainActivity
import net.nobu0707.busnav.data.route.createDevelopmentSampleRoute
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.ScheduledRoute
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class NavigationRotationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun activityRecreationRetainsAppliedGeometryAndGuidanceSnapshot() {
        val sample = createDevelopmentSampleRoute()
        val route = ScheduledRoute("rotation-guidance", "Rotation route", sample.geometry, sample.points,
            guidance = RouteGuidance(listOf(RouteManeuver(0,ManeuverType.RIGHT,"",0,1))))
        rule.runOnIdle { ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder.applyCalculatedRoute(route) }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.runOnIdle {
            val state = ViewModelProvider(rule.activity)[NavigationViewModel::class.java].stateHolder.uiState.value
            assertSame(route, state.activeRoute)
            assertSame(route.guidance, state.activeRoute!!.guidance)
        }
    }
}
