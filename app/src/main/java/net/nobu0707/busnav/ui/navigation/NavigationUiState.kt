package net.nobu0707.busnav.ui.navigation

import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.location.LocationState

enum class LocationPermissionState {
    Unknown,
    Requestable,
    Granted,
    Denied,
}

enum class NavigationLayoutMode {
    PortraitMap,
    LandscapeThreeColumn,
}

data class NavigationUiState(
    val deviation: DeviationUiState = DeviationUiState(),
    val deviationSnapshot: net.nobu0707.busnav.domain.navigation.RouteDeviationSnapshot = net.nobu0707.busnav.domain.navigation.RouteDeviationSnapshot(),
    val highwayGuidance: HighwayGuidanceUiState? = null,
    val guidance: GuidanceUiState = GuidanceUiState(),
    val isMapReady: Boolean = false,
    val locationPermissionState: LocationPermissionState = LocationPermissionState.Unknown,
    val location: LocationState? = null,
    val isFollowingLocation: Boolean = true,
    val locationError: String? = null,
    val layoutMode: NavigationLayoutMode = NavigationLayoutMode.PortraitMap,
    val isLoading: Boolean = true,
    val recenterRequestId: Int = 0,
    val activeRoute: ScheduledRoute? = null,
    val isRouteLoading: Boolean = true,
    val routeError: String? = null,
    val routeOverviewRequestId: Int = 0,
)

fun resolveNavigationLayout(widthDp: Float, heightDp: Float): NavigationLayoutMode =
    if (widthDp >= 600f && widthDp > heightDp) {
        NavigationLayoutMode.LandscapeThreeColumn
    } else {
        NavigationLayoutMode.PortraitMap
    }
