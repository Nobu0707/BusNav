package net.nobu0707.busnav.data.routing.valhalla

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Polyline6DecoderTest {
    @Test
    fun `known precision six fixture decodes latitude then longitude`() {
        val points = Polyline6Decoder.decode("_izlhA~rlgdF_{geC~ywl@_kwzCn`{nI")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 0.000001)
        assertEquals(-120.2, points[0].longitude, 0.000001)
        assertEquals(40.7, points[1].latitude, 0.000001)
        assertEquals(-120.95, points[1].longitude, 0.000001)
        assertEquals(43.252, points[2].latitude, 0.000001)
        assertEquals(-126.453, points[2].longitude, 0.000001)
    }

    @Test
    fun `precision is six rather than five`() {
        val point = Polyline6Decoder.decode("_izlhA~rlgdF").single()
        assertEquals(38.5, point.latitude, 0.000001)
        assertEquals(-120.2, point.longitude, 0.000001)
    }

    @Test
    fun `actual twelve thousand character shape decodes deterministically`() {
        val body = requireNotNull(
            javaClass.getResource("/valhalla/route-88km-valhalla-3.9.0.json"),
        ).readText()
        val shape = Json { ignoreUnknownKeys = true }
            .decodeFromString<ValhallaRouteResponse>(body)
            .trip!!.legs.single().shape!!
        assertEquals(12_208, shape.length)

        val first = Polyline6Decoder.decode(shape)
        val second = Polyline6Decoder.decode(shape)

        assertEquals(3_075, first.size)
        assertEquals(first, second)
        assertTrue(first.all { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty shape is rejected`() { Polyline6Decoder.decode("") }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated shape is rejected`() { Polyline6Decoder.decode("_izlhA") }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid character is rejected`() { Polyline6Decoder.decode(" ") }
}
