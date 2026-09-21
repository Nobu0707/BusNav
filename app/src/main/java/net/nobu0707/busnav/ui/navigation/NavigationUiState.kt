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
    val activePrescribedRouteId: String? = null,
    val activePrescribedRouteName: String? = null,
    val navigationMode: net.nobu0707.busnav.domain.prescribed.NavigationMode = net.nobu0707.busnav.domain.prescribed.NavigationMode.PRESCRIBED,
    val isNavigationStarted: Boolean = false,
    val freePlan: net.nobu0707.busnav.domain.navigation.FreeNavigationPlan? = null,
    val arrival: net.nobu0707.busnav.domain.navigation.ArrivalSnapshot = net.nobu0707.busnav.domain.navigation.ArrivalSnapshot(),
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

/** Explicit start owns activation; stored-record identity is never a mode discriminator. */
val NavigationUiState.navigationActive: Boolean
    get() = isNavigationStarted && activeRoute != null

val NavigationUiState.routeLabel: String
    get() = if (navigationMode == net.nobu0707.busnav.domain.prescribed.NavigationMode.FREE) "案内経路" else "所定経路"
