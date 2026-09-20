package net.nobu0707.busnav.domain.navigation

import kotlin.math.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry

data class RouteProjection(
    val segmentIndex: Int,
    val fraction: Double,
    val projectedPoint: GeoPoint,
    val distanceFromRouteMeters: Double,
    val distanceAlongRouteMeters: Double,
)

/** Nearest geometric projection, NOT map matching. Crossings/parallel roads are ambiguous. */
object RouteProjector {
    fun project(
        position: GeoPoint,
        geometry: RouteGeometry,
        distanceIndex: RouteDistanceIndex,
        hintSegmentIndex: Int? = null,
    ): RouteProjection {
        require(distanceIndex.geometry === geometry) { "Distance index belongs to another geometry" }
        fun at(i: Int): RouteProjection {
            val a = geometry.points[i]
            val b = geometry.points[i + 1]
            val scale = cos(Math.toRadians((a.latitude + b.latitude) / 2))
            val x = longitudeDelta(b.longitude - a.longitude) * scale
            val y = b.latitude - a.latitude
            val px = longitudeDelta(position.longitude - a.longitude) * scale
            val py = position.latitude - a.latitude
            val denominator = x * x + y * y
            val fraction = if (denominator == 0.0) 0.0 else ((px * x + py * y) / denominator).coerceIn(0.0, 1.0)
            val projected = GeoPoint(a.latitude + fraction * y,
                longitudeDelta(a.longitude + fraction * longitudeDelta(b.longitude - a.longitude)))
            return RouteProjection(i, fraction, projected, distanceMeters(position, projected),
                distanceIndex.distanceAtGeometryIndex(i) + fraction * distanceIndex.distanceBetweenIndices(i, i + 1))
        }
        val last = geometry.points.lastIndex - 1
        val hint = hintSegmentIndex?.takeIf { it in 0..last }
        var best = at(hint ?: 0)
        // Full scan guarantees nearest distance. Hint only resolves equally near crossing segments.
        for (i in 0..last) {
            val candidate = at(i)
            if (candidate.distanceFromRouteMeters < best.distanceFromRouteMeters - 0.001) best = candidate
        }
        return best
    }
}
