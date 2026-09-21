package net.nobu0707.busnav.domain.detour

import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.location.LocationState

data class RejoinConfig(val minimumForwardMeters: Double = 500.0, val maximumDistanceMeters: Double = 25.0,
    val requiredFixes: Int = 3, val minimumDurationMillis: Long = 2000,
    val maximumAccuracyMeters: Float = 30f, val maximumHeadingDifferenceDegrees: Double = 45.0,
    val maximumEvidenceGapMillis: Long = 10_000) {
    init {
        require(listOf(minimumForwardMeters, maximumDistanceMeters, maximumHeadingDifferenceDegrees).all { it.isFinite() && it > 0 })
        require(requiredFixes >= 2 && minimumDurationMillis > 0 && maximumEvidenceGapMillis > 0)
        require(maximumAccuracyMeters.isFinite() && maximumAccuracyMeters > 0)
    }
}
enum class RejoinState { SEARCHING, CANDIDATE, CONFIRMED }
data class RejoinSnapshot(val state: RejoinState = RejoinState.SEARCHING, val consecutiveFixes: Int = 0,
    val sinceMillis: Long? = null, val lastTimestampMillis: Long? = null, val actualProgressMeters: Double? = null)

/** Evidence is consecutive, fresh, distinct and forward. The planned target is intentionally irrelevant. */
class RejoinDetector(val config: RejoinConfig = RejoinConfig()) {
    fun update(previous: RejoinSnapshot, match: RouteMatch?, fix: LocationState, floor: Double, now: Long): RejoinSnapshot {
        val time = fix.elapsedRealtimeMillis
        if (time != null && previous.lastTimestampMillis != null && time <= previous.lastTimestampMillis) return previous
        val accuracy = fix.accuracyMeters
        val valid = time != null && time in 0..now && now - time < config.maximumEvidenceGapMillis &&
            accuracy != null && accuracy.isFinite() && accuracy in 0f..config.maximumAccuracyMeters &&
            match?.quality == RouteMatchQuality.MATCHED && match.projection.distanceFromRouteMeters <= config.maximumDistanceMeters &&
            match.projection.distanceAlongRouteMeters >= floor &&
            (match.headingDifferenceDegrees == null || match.headingDifferenceDegrees <= config.maximumHeadingDifferenceDegrees)
        if (!valid) return RejoinSnapshot(lastTimestampMillis = time)
        val connected = previous.lastTimestampMillis?.let { time - it < config.maximumEvidenceGapMillis } == true
        val count = if (connected) previous.consecutiveFixes + 1 else 1
        val since = if (connected) previous.sinceMillis ?: time else time
        val confirmed = count >= config.requiredFixes && time - since >= config.minimumDurationMillis
        return RejoinSnapshot(if (confirmed) RejoinState.CONFIRMED else RejoinState.CANDIDATE,
            count, since, time, match.projection.distanceAlongRouteMeters)
    }
}
