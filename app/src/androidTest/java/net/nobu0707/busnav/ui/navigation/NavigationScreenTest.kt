package net.nobu0707.busnav.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.data.route.createDevelopmentSampleRoute
import net.nobu0707.busnav.location.LocationState
import net.nobu0707.busnav.ui.theme.BusNavTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun portraitLayoutShowsAllPrimaryRegionsWithoutLocation() {
        composeRule.setContent {
            BusNavTheme {
                NavigationScreen(
                    uiState = NavigationUiState(locationPermissionState = LocationPermissionState.Denied),
                    onLayoutModeChanged = {},
                    onRequestPermission = {},
                    onCurrentLocation = {},
                    onRouteOverview = {},
                    modifier = Modifier.requiredSize(400.dp, 800.dp),
                    mapContent = { modifier -> Box(modifier) },
                )
            }
        }

        composeRule.onNodeWithTag(NavigationTestTags.NEXT_GUIDANCE).assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.MAP).assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.OPERATIONS).assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.AUXILIARY).assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.PERMISSION).assertIsDisplayed()
    }

    @Test
    fun landscapeLayoutShowsThreeColumns() {
        composeRule.setContent {
            BusNavTheme {
                NavigationScreen(
                    uiState = NavigationUiState(locationPermissionState = LocationPermissionState.Granted),
                    onLayoutModeChanged = {},
                    onRequestPermission = {},
                    onCurrentLocation = {},
                    onRouteOverview = {},
                    modifier = Modifier.requiredSize(900.dp, 400.dp),
                    mapContent = { modifier -> Box(modifier) },
                )
            }
        }

        composeRule.onNodeWithTag(NavigationTestTags.NEXT_GUIDANCE).assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.MAP).assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.AUXILIARY).assertIsDisplayed()
    }

    @Test
    fun currentLocationButtonInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            BusNavTheme {
                NavigationScreen(
                    uiState = NavigationUiState(
                        locationPermissionState = LocationPermissionState.Granted,
                        location = LocationState(
                            point = GeoPoint(35.6812, 139.7671),
                            accuracyMeters = 5f,
                            bearingDegrees = null,
                            speedMetersPerSecond = null,
                            timestampMillis = 1L,
                        ),
                    ),
                    onLayoutModeChanged = {},
                    onRequestPermission = {},
                    onCurrentLocation = { clicked = true },
                    onRouteOverview = {},
                    modifier = Modifier.requiredSize(400.dp, 800.dp),
                    mapContent = { modifier -> Box(modifier) },
                )
            }
        }

        composeRule.onNodeWithTag(NavigationTestTags.CURRENT_LOCATION).performClick()
        composeRule.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun routeNameAndOverviewActionAreAvailable() {
        var clicked = false
        val route = createDevelopmentSampleRoute()
        composeRule.setContent {
            BusNavTheme {
                NavigationScreen(
                    uiState = NavigationUiState(
                        locationPermissionState = LocationPermissionState.Granted,
                        activeRoute = route,
                        isRouteLoading = false,
                    ),
                    onLayoutModeChanged = {},
                    onRequestPermission = {},
                    onCurrentLocation = {},
                    onRouteOverview = { clicked = true },
                    modifier = Modifier.requiredSize(400.dp, 800.dp),
                    mapContent = { modifier -> Box(modifier) },
                )
            }
        }

        composeRule.onNodeWithText("所定経路：開発用サンプルルート", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(NavigationTestTags.ROUTE_OVERVIEW).performClick()
        composeRule.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun routeEditEntryInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            BusNavTheme {
                NavigationScreen(
                    uiState = NavigationUiState(locationPermissionState = LocationPermissionState.Granted),
                    onLayoutModeChanged = {},
                    onRequestPermission = {},
                    onCurrentLocation = {},
                    onRouteOverview = {},
                    onEditRoute = { clicked = true },
                    modifier = Modifier.requiredSize(400.dp, 800.dp),
                    mapContent = { Box(it) },
                )
            }
        }

        composeRule.onNodeWithTag(NavigationTestTags.ROUTE_EDIT).performClick()
        composeRule.runOnIdle { assertTrue(clicked) }
    }
}
