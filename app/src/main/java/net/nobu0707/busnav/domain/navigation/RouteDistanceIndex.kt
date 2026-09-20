package net.nobu0707.busnav.domain.navigation

import kotlin.math.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry

/** Built once per route. Distances, projection and turn locations share this axis. */
class RouteDistanceIndex(val geometry: RouteGeometry) {
    private val cumulative = DoubleArray(geometry.points.size).also { values ->
        for (i in 1 until values.size) {
            values[i] = values[i - 1] + distanceMeters(geometry.points[i - 1], geometry.points[i])
        }
    }
    val cumulativeMeters: DoubleArray get() = cumulative.copyOf()
    val totalMeters: Double get() = cumulative.last()
    fun distanceAtGeometryIndex(index: Int): Double {
        require(index in cumulative.indices) { "Geometry index out of range" }
        return cumulative[index]
    }
    fun distanceBetweenIndices(from: Int, to: Int): Double {
        require(to >= from) { "Distance interval is reversed" }
        return distanceAtGeometryIndex(to) - distanceAtGeometryIndex(from)
    }
}

internal const val EARTH_RADIUS_METERS = 6_371_008.8
internal fun longitudeDelta(degrees: Double): Double = ((degrees + 540.0) % 360.0) - 180.0
internal fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val lat = Math.toRadians(b.latitude - a.latitude)
    val lon = Math.toRadians(longitudeDelta(b.longitude - a.longitude))
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
        cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
