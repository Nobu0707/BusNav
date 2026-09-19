package net.nobu0707.busnav.domain.route

import net.nobu0707.busnav.domain.model.GeoPoint

class RouteGeometry(points: List<GeoPoint>) {
    val points: List<GeoPoint> = points.toList()

    init {
        require(this.points.size >= 2) { "Route geometry must contain at least two points" }
    }

    val first: GeoPoint get() = points.first()
    val last: GeoPoint get() = points.last()

    val bounds: RouteBounds by lazy {
        RouteBounds(
            minLatitude = points.minOf(GeoPoint::latitude),
            maxLatitude = points.maxOf(GeoPoint::latitude),
            minLongitude = points.minOf(GeoPoint::longitude),
            maxLongitude = points.maxOf(GeoPoint::longitude),
        )
    }

    override fun equals(other: Any?): Boolean = other is RouteGeometry && points == other.points

    override fun hashCode(): Int = points.hashCode()

    override fun toString(): String = "RouteGeometry(points=$points)"
}
