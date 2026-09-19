package net.nobu0707.busnav.domain.routeplan

data class RoutePlanBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
)

fun RoutePlan.boundsOrNull(): RoutePlanBounds? {
    if (points.isEmpty()) return null
    return RoutePlanBounds(
        minLatitude = points.minOf { it.position.latitude },
        maxLatitude = points.maxOf { it.position.latitude },
        minLongitude = points.minOf { it.position.longitude },
        maxLongitude = points.maxOf { it.position.longitude },
    )
}
