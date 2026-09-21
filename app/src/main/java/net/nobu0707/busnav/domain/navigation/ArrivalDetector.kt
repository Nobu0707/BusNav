package net.nobu0707.busnav.domain.navigation

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.location.LocationState
import kotlin.math.*

enum class ArrivalState { EN_ROUTE, APPROACHING, ARRIVED }
data class ArrivalSnapshot(val state: ArrivalState = ArrivalState.EN_ROUTE, val consecutiveFixes: Int = 0, val lastFixMillis: Long? = null)
data class ArrivalConfig(
    val remainingMeters: Double = 50.0,
    val destinationRadiusMeters: Double = 75.0,
    val approachMeters: Double = 250.0,
    val maxAccuracyMeters: Float = 50f,
    val requiredFixes: Int = 2,
    val maxFixGapMillis: Long = 10_000,
) {
    init {
        require(remainingMeters.isFinite() && remainingMeters >= 0)
        require(destinationRadiusMeters.isFinite() && destinationRadiusMeters > 0)
        require(approachMeters.isFinite() && approachMeters >= remainingMeters)
        require(maxAccuracyMeters.isFinite() && maxAccuracyMeters > 0)
        require(requiredFixes >= 2 && maxFixGapMillis > 0)
    }
}

/** Requires reliable route progress AND raw proximity, on distinct consecutive monotonic fixes. */
class ArrivalDetector(private val config: ArrivalConfig = ArrivalConfig()) {
    fun update(previous: ArrivalSnapshot, location: LocationState, destination: GeoPoint,
        remainingRouteMeters: Double, reliable: Boolean, now: Long): ArrivalSnapshot {
        if (previous.state == ArrivalState.ARRIVED) return previous
        val time = location.elapsedRealtimeMillis
        if (time != null && previous.lastFixMillis != null && time <= previous.lastFixMillis) return previous
        val accurate = FreeNavigationConfig(config.maxFixGapMillis, config.maxAccuracyMeters).locationProblem(location, now) == null
        val close = distance(location.point, destination) <= config.destinationRadiusMeters
        val nearEnd = remainingRouteMeters.isFinite() && remainingRouteMeters >= 0 && remainingRouteMeters <= config.remainingMeters
        val continuous = time != null && previous.lastFixMillis != null && time - previous.lastFixMillis <= config.maxFixGapMillis
        val count = if (accurate && reliable && close && nearEnd) (if (continuous) previous.consecutiveFixes else 0) + 1 else 0
        val state = when {
            count >= config.requiredFixes -> ArrivalState.ARRIVED
            accurate && reliable && remainingRouteMeters in 0.0..config.approachMeters -> ArrivalState.APPROACHING
            else -> ArrivalState.EN_ROUTE
        }
        return ArrivalSnapshot(state, count, time)
    }

    private fun distance(a: GeoPoint, b: GeoPoint): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
