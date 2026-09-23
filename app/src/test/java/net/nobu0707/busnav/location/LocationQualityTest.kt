package net.nobu0707.busnav.location

import net.nobu0707.busnav.domain.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class LocationQualityTest {
    private val policy = LocationQualityPolicy()
    private fun fix(time: Long = 1_000, accuracy: Float? = 5f) =
        LocationState(GeoPoint(35.0, 139.0), accuracy, null, null, 0, time)

    @Test fun accuracyBoundariesSeparateStartFromStrongGuidance() {
        for ((accuracy, quality) in listOf(5f to LocationQuality.GOOD, 30f to LocationQuality.GOOD,
            50f to LocationQuality.USABLE, 80f to LocationQuality.USABLE, 100f to LocationQuality.USABLE,
            120f to LocationQuality.DEGRADED, 150f to LocationQuality.DEGRADED)) {
            val result = policy.assess(fix(accuracy = accuracy), 1_000)
            assertEquals(quality, result.quality)
            assertTrue(result.navigationStartAllowed)
            assertEquals(accuracy <= 100f, result.strongGuidanceAllowed)
        }
        assertFalse(policy.assess(fix(accuracy = 150.1f), 1_000).navigationStartAllowed)
    }

    @Test fun invalidAccuracyAndTimeNeverStart() {
        for (accuracy in listOf(null, Float.NaN, -1f, Float.POSITIVE_INFINITY))
            assertFalse(policy.assess(fix(accuracy = accuracy), 1_000).navigationStartAllowed)
        assertFalse(policy.assess(null, 1_000).navigationStartAllowed)
        assertEquals(LocationQuality.STALE, policy.assess(fix(-1), 1_000).quality)
        assertEquals(LocationQuality.STALE, policy.assess(fix(2_000), 1_000).quality)
        assertEquals(LocationQuality.STALE, policy.assess(fix(), 16_001).quality)
        assertTrue(policy.assess(fix(), 16_000).navigationStartAllowed)
        assertTrue(policy.assess(fix(), 11_000).navigationStartAllowed)
        assertTrue(policy.assess(fix(), 6_000).navigationStartAllowed)
    }

    @Test fun recentBestRejectsOutOfOrderAndOneBadFix() {
        val recent = RecentStartFixes(policy)
        recent.add(fix(1_000, 20f), 1_000)
        recent.add(fix(2_000, 180f), 2_000)
        recent.add(fix(1_500, 5f), 2_000)
        assertEquals(1_000L, recent.best(2_000)?.elapsedRealtimeMillis)
        recent.add(fix(3_000, 120f), 3_000)
        assertEquals(1_000L, recent.best(3_000)?.elapsedRealtimeMillis)
        // An old precise fix must not override a recent usable one indefinitely.
        recent.add(fix(12_000, 80f), 12_000)
        assertEquals(12_000L, recent.best(12_000)?.elapsedRealtimeMillis)
        assertNull(recent.best(28_001))
    }
}
