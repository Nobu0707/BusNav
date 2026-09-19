package net.nobu0707.busnav.domain.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleProfileTest {
    @Test
    fun `valid profile is immutable value`() {
        val profile = profile(axleLoad = null)
        assertEquals(12.0, profile.lengthMeters, 0.0)
        assertNull(profile.axleLoadMetricTons)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero length is rejected`() { profile(length = 0.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `negative width is rejected`() { profile(width = -1.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `zero height is rejected`() { profile(height = 0.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `negative weight is rejected`() { profile(weight = -1.0) }

    @Test(expected = IllegalArgumentException::class)
    fun `zero optional axle load is rejected`() { profile(axleLoad = 0.0) }

    private fun profile(
        length: Double = 12.0,
        width: Double = 2.5,
        height: Double = 3.5,
        weight: Double = 16.0,
        axleLoad: Double? = 10.0,
    ) = VehicleProfile("bus", "Bus", length, width, height, weight, axleLoad)
}
