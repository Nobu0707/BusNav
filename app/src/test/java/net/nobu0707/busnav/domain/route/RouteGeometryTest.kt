package net.nobu0707.busnav.domain.route

import net.nobu0707.busnav.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouteGeometryTest {
    @Test
    fun `two points create valid geometry`() {
        val geometry = RouteGeometry(listOf(GeoPoint(35.0, 139.0), GeoPoint(36.0, 140.0)))

        assertEquals(2, geometry.points.size)
    }

    @Test
    fun `fewer than two points are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteGeometry(listOf(GeoPoint(35.0, 139.0)))
        }
    }

    @Test
    fun `bounds contain all coordinates`() {
        val geometry = RouteGeometry(
            listOf(
                GeoPoint(35.5, 140.0),
                GeoPoint(34.2, 141.3),
                GeoPoint(36.8, 138.7),
            ),
        )

        assertEquals(34.2, geometry.bounds.minLatitude, 0.0)
        assertEquals(36.8, geometry.bounds.maxLatitude, 0.0)
        assertEquals(138.7, geometry.bounds.minLongitude, 0.0)
        assertEquals(141.3, geometry.bounds.maxLongitude, 0.0)
    }

    @Test
    fun `duplicate coordinates remain valid`() {
        val point = GeoPoint(35.0, 139.0)
        val geometry = RouteGeometry(listOf(point, point))

        assertEquals(point.latitude, geometry.bounds.minLatitude, 0.0)
        assertEquals(point.longitude, geometry.bounds.maxLongitude, 0.0)
    }
}
