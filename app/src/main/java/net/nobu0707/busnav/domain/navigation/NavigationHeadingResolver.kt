package net.nobu0707.busnav.domain.navigation

import kotlin.math.abs
import net.nobu0707.busnav.location.LocationState

fun normalizeHeading(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
fun shortestHeadingDelta(from: Double, to: Double): Double = normalizeHeading(to - from + 180.0) - 180.0
fun northScreenRotation(cameraBearing: Double): Double = normalizeHeading(-cameraBearing)

enum class HeadingSource { NONE, GPS, ROUTE, HELD }
data class NavigationHeading(
    val degrees: Double? = null,
    val source: HeadingSource = HeadingSource.NONE,
    val fixTime: Long? = null,
    val pendingDegrees: Double? = null,
    val pendingTime: Long? = null,
)
data class NavigationHeadingConfig(
    val minimumSpeedMetersPerSecond: Float = 2.5f,
    val minimumHeadingChangeDegrees: Double = 2.0,
    val maximumSingleUpdateDegrees: Double = 100.0,
    val confirmationToleranceDegrees: Double = 25.0,
    val confirmationTimeoutMillis: Long = 2500,
    val animationDurationMillis: Int = 350,
    val staleHeadingTimeoutMillis: Long = 10_000,
)
data class ReliableRouteHeading(val degrees: Double?, val quality: RouteMatchQuality, val projectionReliable: Boolean)

/** Pure fix-level filter: large jumps need confirmation; ordinary turns have no low-pass lag. */
class NavigationHeadingResolver(val config: NavigationHeadingConfig = NavigationHeadingConfig()) {
    fun isFresh(location: LocationState?, now: Long): Boolean = location?.elapsedRealtimeMillis?.let {
        now - it in 0 until config.staleHeadingTimeoutMillis
    } == true

    fun resolve(location: LocationState?, previous: NavigationHeading, now: Long,
        route: ReliableRouteHeading? = null): NavigationHeading {
        if (!isFresh(location, now)) return previous.copy(source = if (previous.degrees == null) HeadingSource.NONE else HeadingSource.HELD)
        val fix = requireNotNull(location)
        val time = requireNotNull(fix.elapsedRealtimeMillis)
        if (previous.fixTime != null && time < previous.fixTime) return previous
        val fallback = route?.takeIf { it.quality == RouteMatchQuality.MATCHED && it.projectionReliable }
            ?.degrees?.takeIf { it.isFinite() }?.let(::normalizeHeading)
        if (previous.fixTime == time) return if (previous.degrees == null && fallback != null)
            previous.copy(degrees = fallback, source = HeadingSource.ROUTE) else previous
        val candidate = fix.normalizedBearingDegrees?.toDouble()?.takeIf {
            fix.speedMetersPerSecond?.let { speed -> speed.isFinite() && speed >= config.minimumSpeedMetersPerSecond } == true
        }
        if (candidate == null) return NavigationHeading(previous.degrees ?: fallback,
            if (previous.degrees != null) HeadingSource.HELD else if (fallback != null) HeadingSource.ROUTE else HeadingSource.NONE, time)
        val last = previous.degrees ?: return NavigationHeading(candidate, HeadingSource.GPS, time)
        val delta = abs(shortestHeadingDelta(last, candidate))
        if (delta < config.minimumHeadingChangeDegrees) return NavigationHeading(last, HeadingSource.HELD, time)
        val confirmed = previous.pendingDegrees?.let {
            abs(shortestHeadingDelta(it, candidate)) <= config.confirmationToleranceDegrees &&
                previous.pendingTime?.let { pending -> time - pending in 1..config.confirmationTimeoutMillis } == true
        } == true
        if (delta > config.maximumSingleUpdateDegrees && !confirmed)
            return NavigationHeading(last, HeadingSource.HELD, time, candidate, time)
        return NavigationHeading(candidate, HeadingSource.GPS, time)
    }
}

data class NavigationCameraState(
    val active: Boolean = false,
    val following: Boolean = true,
    val orientation: NavigationMapOrientation = NavigationMapOrientation.HEADING_UP,
    val headingDegrees: Double? = null,
) {
    fun targetBearing(current: Double): Double = when {
        !active || !following -> current
        orientation == NavigationMapOrientation.NORTH_UP -> 0.0
        else -> headingDegrees ?: current
    }
    fun vehicleScreenRotation(cameraBearing: Double, rawHeading: Double?): Double =
        if (active && following && orientation == NavigationMapOrientation.HEADING_UP) 0.0
        else normalizeHeading((headingDegrees ?: rawHeading ?: cameraBearing) - cameraBearing)
}
