package net.nobu0707.busnav.location

import net.nobu0707.busnav.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocationStateTest {
    @Test
    fun `bearing is normalized for map icon rotation`() {
        val state = LocationState(
            point = GeoPoint(35.6812, 139.7671),
            accuracyMeters = 4f,
            bearingDegrees = -30f,
            speedMetersPerSecond = 12f,
            timestampMillis = 123L,
        )

        assertEquals(330f, state.normalizedBearingDegrees)
    }

    @Test
    fun `invalid coordinate is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoPoint(latitude = 91.0, longitude = 139.0)
        }
    }
}

