package net.nobu0707.busnav.domain.navigation

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.RoutePlanPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.location.LocationState

/** Ephemeral destination intent. START is sampled from raw GPS for each request. */
data class FreeNavigationPlan(val destination: GeoPoint, val destinationName: String? = null) {
    fun toRoutePlan(rawStart: GeoPoint, requestId: String) = RoutePlan(requestId, destinationName ?: "目的地まで", listOf(
        RoutePlanPoint("start", RoutePlanPointType.START, rawStart),
        RoutePlanPoint("destination", RoutePlanPointType.DESTINATION, destination, destinationName),
    ))

    fun destinationOverlay() = RoutePlan("free-destination", points = listOf(
        RoutePlanPoint("destination", RoutePlanPointType.DESTINATION, destination, destinationName),
    ))
}

data class FreeNavigationConfig(val maxLocationAgeMillis: Long = 10_000, val maxStartAccuracyMeters: Float = 50f) {
    init { require(maxLocationAgeMillis > 0); require(maxStartAccuracyMeters.isFinite() && maxStartAccuracyMeters > 0) }

    fun locationProblem(location: LocationState?, now: Long): String? {
        if (location == null) return "現在地を取得中です"
        val timestamp = location.elapsedRealtimeMillis
        if (timestamp == null || timestamp < 0 || timestamp > now || now - timestamp > maxLocationAgeMillis)
            return "現在地が古いため、更新を待っています"
        val accuracy = location.accuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy < 0 || accuracy > maxStartAccuracyMeters)
            return "現在地の精度が十分ではありません"
        return null
    }
}
