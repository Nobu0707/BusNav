package net.nobu0707.busnav.domain.navigation

import kotlin.math.*
import net.nobu0707.busnav.location.LocationState

enum class RouteMatchQuality { MATCHED, AMBIGUOUS, UNRELIABLE }

data class RouteMatcherConfig(
    val minimumAccuracyFloorMeters: Double = 8.0,
    val maximumAccuracyMeters: Double = 40.0,
    val minimumHeadingSpeedMetersPerSecond: Double = 2.5,
    val distanceWeight: Double = 1.0,
    val headingWeight: Double = 4.0,
    val continuityWeight: Double = 0.25,
    val jumpWeight: Double = 4.0,
    val continuityScaleMeters: Double = 50.0,
    val jumpMarginMeters: Double = 45.0,
    val fallbackSpeedMetersPerSecond: Double = 45.0,
    val speedAllowanceMultiplier: Double = 1.5,
    val backwardToleranceMeters: Double = NavigationProgressConfig().backwardToleranceMeters,
    val ambiguityScoreGap: Double = 0.35,
    val equivalentProgressMeters: Double = 20.0,
    val maximumMatchDistanceMeters: Double = 300.0,
    val staleAfterMillis: Long = 10_000,
    val reacquireAfterFixes: Int = 3,
    val searchRadiusMeters: Double = 350.0,
    val previousSegmentWindow: Int = 12,
    val gridDegrees: Double = 0.01,
    val maximumCellsPerSegment: Int = 256,
) {
    init {
        require(listOf(minimumAccuracyFloorMeters, maximumAccuracyMeters, continuityScaleMeters, jumpMarginMeters,
            fallbackSpeedMetersPerSecond, maximumMatchDistanceMeters, searchRadiusMeters, gridDegrees)
            .all { it.isFinite() && it > 0 })
        require(listOf(minimumHeadingSpeedMetersPerSecond, distanceWeight, headingWeight, continuityWeight,
            jumpWeight, backwardToleranceMeters, ambiguityScoreGap, equivalentProgressMeters)
            .all { it.isFinite() && it >= 0 })
        require(speedAllowanceMultiplier.isFinite() && speedAllowanceMultiplier >= 1)
        require(maximumAccuracyMeters >= minimumAccuracyFloorMeters && searchRadiusMeters >= maximumMatchDistanceMeters)
        require(staleAfterMillis > 0 && reacquireAfterFixes >= 2 && previousSegmentWindow >= 0 && maximumCellsPerSegment > 0)
        require(abs(360.0 / gridDegrees - round(360.0 / gridDegrees)) < 0.000001)
    }
}

data class RouteMatchCandidate(
    val projection: RouteProjection,
    val segmentBearingDegrees: Double?,
    val distanceScore: Double,
    val headingScore: Double?,
    val continuityScore: Double,
    val jumpPenalty: Double,
    val totalScore: Double,
    val headingDifferenceDegrees: Double?,
    val plausible: Boolean,
)

data class RouteMatch(
    val projection: RouteProjection,
    val quality: RouteMatchQuality,
    /** Relative heuristic score, NOT a calibrated probability. */
    val confidence: Double,
    val headingDifferenceDegrees: Double?,
    val candidateGap: Double?,
    val evaluatedCandidates: Int,
)

data class RouteMatcherState(
    val lastTimestampMillis: Long? = null,
    val anchor: RouteProjection? = null,
    val anchorTimestampMillis: Long? = null,
    val heldProgressMeters: Double? = null,
    val lostFixes: Int = 0,
)

data class RouteMatcherResult(val match: RouteMatch?, val state: RouteMatcherState, val accepted: Boolean)

fun headingDifferenceDegrees(a: Double, b: Double): Double = abs(longitudeDelta(a - b))

/** Pure transition: canceled computations cannot mutate the committed tracker state. No I/O. */
class RouteMatcher(val index: RouteMatchIndex, val config: RouteMatcherConfig = RouteMatcherConfig()) {
    fun match(fix: LocationState, previous: RouteMatcherState, nowElapsedMillis: Long): RouteMatcherResult {
        val timestamp = fix.elapsedRealtimeMillis
        if (timestamp == null || timestamp < 0 || timestamp > nowElapsedMillis ||
            nowElapsedMillis - timestamp >= config.staleAfterMillis ||
            (previous.lastTimestampMillis != null && timestamp <= previous.lastTimestampMillis)) {
            return RouteMatcherResult(null, previous, false)
        }
        val anchor = previous.anchor.takeUnless { previous.lostFixes >= config.reacquireAfterFixes }
        val ids = index.candidates(fix.point, anchor?.segmentIndex)
        var candidates = ids.map { score(fix, it, anchor, previous.anchorTimestampMillis) }.sortedBy { it.totalScore }
        // A local window must never prevent reacquisition of the original route elsewhere.
        if (candidates.first().projection.distanceFromRouteMeters > config.searchRadiusMeters && ids.size < index.segments.size) {
            candidates = index.segments.indices.map { score(fix, it, anchor, previous.anchorTimestampMillis) }.sortedBy { it.totalScore }
        }
        val best = candidates.first()
        // Adjacent projections at the same route position are one hypothesis, not a false ambiguity.
        val second = candidates.drop(1).firstOrNull {
            abs(it.projection.distanceAlongRouteMeters - best.projection.distanceAlongRouteMeters) > config.equivalentProgressMeters
        }
        val gap = second?.let { it.totalScore - best.totalScore }
        val accuracy = fix.accuracyMeters
        val quality = when {
            accuracy == null || !accuracy.isFinite() || accuracy < 0 || accuracy > config.maximumAccuracyMeters ||
                !best.plausible || best.projection.distanceFromRouteMeters > config.maximumMatchDistanceMeters -> RouteMatchQuality.UNRELIABLE
            gap != null && gap < config.ambiguityScoreGap -> RouteMatchQuality.AMBIGUOUS
            else -> RouteMatchQuality.MATCHED
        }
        val matched = quality == RouteMatchQuality.MATCHED
        val close = matched && best.projection.distanceFromRouteMeters <= NavigationProgressConfig().reliableDistanceMeters
        val rawProgress = best.projection.distanceAlongRouteMeters
        val held = previous.heldProgressMeters
        val progress = if (close && held != null && held - rawProgress in 0.0..config.backwardToleranceMeters) held else rawProgress
        val next = previous.copy(lastTimestampMillis = timestamp,
            anchor = if (close) best.projection else previous.anchor,
            anchorTimestampMillis = if (close) timestamp else previous.anchorTimestampMillis,
            heldProgressMeters = if (close) progress else held,
            lostFixes = if (close) 0 else previous.lostFixes + 1)
        val confidence = if (quality == RouteMatchQuality.UNRELIABLE) 0.0 else
            (1.0 / (1.0 + best.totalScore)) * (gap?.div(config.ambiguityScoreGap.coerceAtLeast(0.001))?.coerceIn(0.0, 1.0) ?: 1.0)
        return RouteMatcherResult(RouteMatch(best.projection, quality, confidence,
            best.headingDifferenceDegrees, gap, candidates.size), next, true)
    }

    fun score(fix: LocationState, segment: Int, anchor: RouteProjection? = null, anchorTime: Long? = null): RouteMatchCandidate {
        val projection = RouteProjector.projectSegment(fix.point, index.distances, segment)
        val accuracy = fix.accuracyMeters?.toDouble()?.takeIf { it.isFinite() && it >= 0 } ?: config.maximumAccuracyMeters
        val distance = projection.distanceFromRouteMeters / max(accuracy, config.minimumAccuracyFloorMeters)
        val speed = fix.speedMetersPerSecond?.toDouble()?.takeIf { it.isFinite() && it >= 0 }
        val bearing = index.segments[segment].bearingDegrees
        val difference = if (speed != null && speed >= config.minimumHeadingSpeedMetersPerSecond &&
            bearing != null) fix.normalizedBearingDegrees?.let { headingDifferenceDegrees(it.toDouble(), bearing) } else null
        val heading = difference?.div(180.0)
        val dt = if (anchorTime != null && fix.elapsedRealtimeMillis != null)
            ((fix.elapsedRealtimeMillis - anchorTime).coerceAtLeast(0) / 1000.0) else 0.0
        val delta = anchor?.let { projection.distanceAlongRouteMeters - it.distanceAlongRouteMeters } ?: 0.0
        val travel = (abs(delta) - config.backwardToleranceMeters).coerceAtLeast(0.0)
        val allowance = (speed ?: config.fallbackSpeedMetersPerSecond) * dt * config.speedAllowanceMultiplier + config.jumpMarginMeters
        val jump = ((travel - allowance) / config.jumpMarginMeters).coerceAtLeast(0.0)
        val continuity = if (anchor == null) 0.0 else travel / max(config.continuityScaleMeters, allowance)
        return RouteMatchCandidate(projection, bearing, distance, heading, continuity, jump,
            config.distanceWeight * distance + config.headingWeight * (heading ?: 0.0) +
                config.continuityWeight * continuity + config.jumpWeight * jump, difference, jump == 0.0)
    }
}
