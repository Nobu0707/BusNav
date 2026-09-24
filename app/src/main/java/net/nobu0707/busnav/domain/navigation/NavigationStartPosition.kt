package net.nobu0707.busnav.domain.navigation

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.ScheduledRoute

enum class NavigationStartSource { RAW_GPS, ROUTING_SNAPPED }

data class NavigationStartPosition(
    val rawLocation: GeoPoint,
    val navigationStartPoint: GeoPoint,
    val source: NavigationStartSource,
    val snapDistanceMeters: Double,
)

data class NavigationStartSnapConfig(
    val preferredMaxSnapMeters: Double = 100.0,
    val absoluteMaxSnapMeters: Double = 300.0,
) {
    init {
        require(preferredMaxSnapMeters >= 0.0 && preferredMaxSnapMeters.isFinite())
        require(absoluteMaxSnapMeters >= preferredMaxSnapMeters && absoluteMaxSnapMeters.isFinite())
    }
}

/** The first Valhalla leg shape begins at the routed START edge. The route's
 * point list still contains the raw requested START; it is not used for this check.
 */
fun freeNavigationStartPosition(
    rawStart: GeoPoint,
    route: ScheduledRoute,
    config: NavigationStartSnapConfig = NavigationStartSnapConfig(),
): NavigationStartPosition? {
    val snapped = route.geometry.first
    val distance = distanceMeters(rawStart, snapped)
    if (!distance.isFinite() || distance > config.absoluteMaxSnapMeters) return null
    return NavigationStartPosition(rawStart, snapped,
        if (distance < 1.0) NavigationStartSource.RAW_GPS else NavigationStartSource.ROUTING_SNAPPED,
        distance)
}
