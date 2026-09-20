package net.nobu0707.busnav.domain.navigation

enum class RouteDeviationState { UNKNOWN, ON_ROUTE, SUSPECTED_OFF_ROUTE, OFF_ROUTE, RECOVERING }

data class DeviationConfig(
    val onRouteDistanceMeters: Double = 25.0,
    val suspectDistanceMeters: Double = 35.0,
    val offRouteDistanceMeters: Double = 50.0,
    val offRouteConsecutiveFixes: Int = 3,
    val onRouteRecoveryFixes: Int = 3,
    val minimumOffRouteDurationMillis: Long = 3000,
    val minimumRecoveryDurationMillis: Long = 2000,
    val maximumEvidenceGapMillis: Long = 10_000,
) {
    init {
        require(listOf(onRouteDistanceMeters, suspectDistanceMeters, offRouteDistanceMeters).all { it.isFinite() && it >= 0 })
        require(onRouteDistanceMeters < suspectDistanceMeters && suspectDistanceMeters <= offRouteDistanceMeters)
        require(offRouteConsecutiveFixes >= 2 && onRouteRecoveryFixes >= 2)
        require(minimumOffRouteDurationMillis > 0 && minimumRecoveryDurationMillis > 0 && maximumEvidenceGapMillis > 0)
    }
}

data class RouteDeviationSnapshot(
    val state: RouteDeviationState = RouteDeviationState.UNKNOWN,
    val matchQuality: RouteMatchQuality = RouteMatchQuality.UNRELIABLE,
    val distanceFromRouteMeters: Double? = null,
    val matchedProgressMeters: Double? = null,
    val consecutiveOffRouteFixes: Int = 0,
    val consecutiveOnRouteFixes: Int = 0,
    val sinceTimestampMillis: Long? = null,
    val evidenceSinceMillis: Long? = null,
    val lastTimestampMillis: Long? = null,
    val recoveryRequired: Boolean = false,
    val confirmedOffRoute: Boolean = false,
) {
    val allowsGuidance: Boolean get() = state == RouteDeviationState.ON_ROUTE && matchQuality == RouteMatchQuality.MATCHED
}

/** Pure hysteresis. Accuracy-adjusted distance is a heuristic, not a statistical confidence interval. */
class RouteDeviationDetector(val config: DeviationConfig = DeviationConfig()) {
    fun uncertain(previous: RouteDeviationSnapshot, quality: RouteMatchQuality = RouteMatchQuality.UNRELIABLE): RouteDeviationSnapshot =
        previous.copy(state = if (previous.confirmedOffRoute)
            RouteDeviationState.OFF_ROUTE else RouteDeviationState.UNKNOWN,
            matchQuality = quality, consecutiveOffRouteFixes = 0, consecutiveOnRouteFixes = 0,
            evidenceSinceMillis = null, recoveryRequired = previous.recoveryRequired || previous.state != RouteDeviationState.UNKNOWN)

    fun update(previous: RouteDeviationSnapshot, match: RouteMatch?, accuracy: Float?, timestamp: Long?): RouteDeviationSnapshot {
        if (timestamp != null && previous.lastTimestampMillis != null && timestamp <= previous.lastTimestampMillis) return previous
        if (timestamp != null && previous.lastTimestampMillis != null && timestamp - previous.lastTimestampMillis >= config.maximumEvidenceGapMillis) {
            return update(uncertain(previous).copy(lastTimestampMillis = null), match, accuracy, timestamp)
        }
        if (match == null || match.quality != RouteMatchQuality.MATCHED || accuracy == null ||
            !accuracy.isFinite() || accuracy < 0 || timestamp == null) {
            return uncertain(previous, match?.quality ?: RouteMatchQuality.UNRELIABLE).copy(lastTimestampMillis = timestamp ?: previous.lastTimestampMillis)
        }
        val distance = match.projection.distanceFromRouteMeters
        val strongOff = distance - accuracy >= config.offRouteDistanceMeters
        val near = distance <= config.onRouteDistanceMeters
        val wasOff = previous.confirmedOffRoute
        val needsRecovery = previous.recoveryRequired || wasOff || previous.state == RouteDeviationState.RECOVERING
        val offCount = if (strongOff) previous.consecutiveOffRouteFixes + 1 else 0
        val onCount = if (near) previous.consecutiveOnRouteFixes + 1 else 0
        val since = when {
            strongOff -> if (previous.consecutiveOffRouteFixes > 0) previous.evidenceSinceMillis ?: timestamp else timestamp
            near && needsRecovery -> if (previous.consecutiveOnRouteFixes > 0) previous.evidenceSinceMillis ?: timestamp else timestamp
            else -> null
        }
        val state = when {
            near && needsRecovery -> if (onCount >= config.onRouteRecoveryFixes &&
                timestamp - requireNotNull(since) >= config.minimumRecoveryDurationMillis) RouteDeviationState.ON_ROUTE
                else RouteDeviationState.RECOVERING
            wasOff -> RouteDeviationState.OFF_ROUTE
            strongOff && offCount >= config.offRouteConsecutiveFixes &&
                timestamp - requireNotNull(since) >= config.minimumOffRouteDurationMillis -> RouteDeviationState.OFF_ROUTE
            distance >= config.suspectDistanceMeters -> RouteDeviationState.SUSPECTED_OFF_ROUTE
            near -> RouteDeviationState.ON_ROUTE
            else -> previous.state
        }
        return RouteDeviationSnapshot(state, match.quality, distance, match.projection.distanceAlongRouteMeters,
            offCount, onCount, if (state != previous.state) timestamp else previous.sinceTimestampMillis,
            since, timestamp, needsRecovery && state != RouteDeviationState.ON_ROUTE,
            state == RouteDeviationState.OFF_ROUTE || (wasOff && state != RouteDeviationState.ON_ROUTE))
    }
}
