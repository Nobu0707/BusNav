package net.nobu0707.busnav.data.facility

import java.security.MessageDigest
import net.nobu0707.busnav.domain.facility.RouteFacilityQueryOptions
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry
import net.nobu0707.busnav.data.routing.valhalla.Polyline6Decoder

object RouteFacilityPolyline {
    fun joinLegs(shapes: List<String>): RouteGeometry {
        val points = mutableListOf<GeoPoint>()
        for (shape in shapes) {
            val decoded = Polyline6Decoder.decode(shape)
            points += if (points.isNotEmpty() && decoded.firstOrNull() == points.last()) decoded.drop(1) else decoded
        }
        return RouteGeometry(points)
    }
    fun encode(route: RouteGeometry): String {
        val points = route.points.distinctConsecutive()
        require(points.size in 2..50_000) { "Invalid route point count" }
        val result = StringBuilder()
        var latitude = 0L
        var longitude = 0L
        for (point in points) {
            val nextLatitude = kotlin.math.round(point.latitude * 1_000_000).toLong()
            val nextLongitude = kotlin.math.round(point.longitude * 1_000_000).toLong()
            appendValue(result, nextLatitude - latitude)
            appendValue(result, nextLongitude - longitude)
            latitude = nextLatitude
            longitude = nextLongitude
        }
        return result.toString()
    }

    fun fingerprint(polyline: String, options: RouteFacilityQueryOptions): String {
        val canonical = "$polyline|${options.corridorMeters}|${options.types.map { it.name }.sorted().joinToString(",")}"
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun List<GeoPoint>.distinctConsecutive(): List<GeoPoint> = filterIndexed { index, point -> index == 0 || point != this[index - 1] }
    private fun appendValue(result: StringBuilder, delta: Long) {
        var value = if (delta < 0) (delta shl 1).inv() else delta shl 1
        while (value >= 0x20) {
            result.append(((0x20 or (value and 0x1f).toInt()) + 63).toChar())
            value = value shr 5
        }
        result.append((value.toInt() + 63).toChar())
    }
}
