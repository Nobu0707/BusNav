package net.nobu0707.busnav.map

/** Logical dp: bitmap center, location anchor and arrow tip are identical. */
data class VehicleMarkerGeometry(val size: Float = 56f) {
    init { require(size.isFinite() && size > 0) }
    data class Point(val x: Float, val y: Float)
    val center = Point(size / 2, size / 2)
    val radius = size * 0.40f
    val arrow = listOf(center, Point(size * 0.70f, size * 0.82f),
        Point(size * 0.50f, size * 0.75f), Point(size * 0.30f, size * 0.82f))
}
