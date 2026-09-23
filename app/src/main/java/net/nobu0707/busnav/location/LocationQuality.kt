package net.nobu0707.busnav.location

enum class LocationQuality { GOOD, USABLE, DEGRADED, UNUSABLE, STALE }
enum class LocationQualityReason { NONE, NO_FIX, INVALID_TIME, TOO_OLD, INVALID_ACCURACY, POOR_ACCURACY }

data class LocationQualityConfig(
    val goodAccuracyMeters: Float = 30f,
    val usableAccuracyMeters: Float = 100f,
    val degradedAccuracyMeters: Float = 150f,
    val maxStartAgeMillis: Long = 15_000,
    val preferredFixAgeMillis: Long = 10_000,
) {
    init {
        require(goodAccuracyMeters > 0 && goodAccuracyMeters.isFinite())
        require(usableAccuracyMeters >= goodAccuracyMeters && usableAccuracyMeters.isFinite())
        require(degradedAccuracyMeters >= usableAccuracyMeters && degradedAccuracyMeters.isFinite())
        require(maxStartAgeMillis > 0 && preferredFixAgeMillis in 1..maxStartAgeMillis)
    }
}

data class LocationQualityAssessment(
    val quality: LocationQuality,
    val reason: LocationQualityReason,
    val navigationStartAllowed: Boolean,
    val strongGuidanceAllowed: Boolean,
)

class LocationQualityPolicy(val config: LocationQualityConfig = LocationQualityConfig()) {
    fun assess(fix: LocationState?, now: Long): LocationQualityAssessment {
        if (fix == null) return result(LocationQuality.UNUSABLE, LocationQualityReason.NO_FIX)
        val time = fix.elapsedRealtimeMillis
        if (time == null || time < 0 || time > now) return result(LocationQuality.STALE, LocationQualityReason.INVALID_TIME)
        if (now - time > config.maxStartAgeMillis) return result(LocationQuality.STALE, LocationQualityReason.TOO_OLD)
        val accuracy = fix.accuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy < 0)
            return result(LocationQuality.UNUSABLE, LocationQualityReason.INVALID_ACCURACY)
        return when {
            accuracy <= config.goodAccuracyMeters -> result(LocationQuality.GOOD, LocationQualityReason.NONE)
            accuracy <= config.usableAccuracyMeters -> result(LocationQuality.USABLE, LocationQualityReason.NONE)
            accuracy <= config.degradedAccuracyMeters -> result(LocationQuality.DEGRADED, LocationQualityReason.NONE)
            else -> result(LocationQuality.UNUSABLE, LocationQualityReason.POOR_ACCURACY)
        }
    }

    private fun result(quality: LocationQuality, reason: LocationQualityReason) = LocationQualityAssessment(
        quality, reason, quality in setOf(LocationQuality.GOOD, LocationQuality.USABLE, LocationQuality.DEGRADED),
        quality in setOf(LocationQuality.GOOD, LocationQuality.USABLE),
    )
}

/** Keeps raw fixes only. The newest fix drives the marker; this selection is for starting a route. */
class RecentStartFixes(private val policy: LocationQualityPolicy = LocationQualityPolicy()) {
    private val fixes = ArrayDeque<LocationState>()
    fun add(fix: LocationState, now: Long) {
        val time = fix.elapsedRealtimeMillis ?: return
        if (time < 0 || time > now || (fixes.lastOrNull()?.elapsedRealtimeMillis ?: -1) >= time) return
        fixes.addLast(fix)
        prune(now)
    }
    fun best(now: Long): LocationState? {
        prune(now)
        val allowed = fixes.filter { policy.assess(it, now).navigationStartAllowed }
        val preferred = allowed.filter { now - requireNotNull(it.elapsedRealtimeMillis) <= policy.config.preferredFixAgeMillis }
        return (preferred.ifEmpty { allowed })
            .minWithOrNull(compareBy<LocationState> { it.accuracyMeters ?: Float.MAX_VALUE }
                .thenByDescending { it.elapsedRealtimeMillis ?: -1 })
    }
    fun clear() = fixes.clear()
    private fun prune(now: Long) {
        while (fixes.isNotEmpty() && policy.assess(fixes.first(), now).reason == LocationQualityReason.TOO_OLD)
            fixes.removeFirst()
    }
}
