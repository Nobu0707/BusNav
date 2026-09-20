package net.nobu0707.busnav.domain.navigation

import kotlin.math.*
import net.nobu0707.busnav.domain.model.GeoPoint

/** Built once on Default. Wrapped longitude grid indexes segment bounding regions, not just vertices. */
class RouteMatchIndex(val distances: RouteDistanceIndex, private val config: RouteMatcherConfig) {
    data class Segment(
        val start: GeoPoint, val end: GeoPoint, val startMeters: Double, val lengthMeters: Double,
        val bearingDegrees: Double?, val south: Double, val north: Double,
        val westUnwrapped: Double, val eastUnwrapped: Double,
    )
    val segments = distances.geometry.points.zipWithNext().mapIndexed { i, (a, b) ->
        val endLon = a.longitude + longitudeDelta(b.longitude - a.longitude)
        val length = distances.distanceBetweenIndices(i, i + 1)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(longitudeDelta(b.longitude - a.longitude))
        val bearing = if (length == 0.0) null else
            (Math.toDegrees(atan2(sin(dLon) * cos(lat2),
                cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))) + 360.0) % 360.0
        Segment(a, b, distances.distanceAtGeometryIndex(i), length, bearing,
            min(a.latitude, b.latitude), max(a.latitude, b.latitude),
            min(a.longitude, endLon), max(a.longitude, endLon))
    }
    private val longitudeCells = ceil(360.0 / config.gridDegrees).toInt()
    private val cells = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
    private val largeSegments = mutableListOf<Int>()

    init {
        segments.forEachIndexed { i, s ->
            val south = cell(s.south)
            val north = cell(s.north)
            val west = cell(s.westUnwrapped)
            val east = cell(s.eastUnwrapped)
            if ((north.toLong() - south + 1) * (east.toLong() - west + 1) > config.maximumCellsPerSegment) {
                largeSegments.add(i)
            } else for (lat in south..north) for (lon in west..east) {
                cells.getOrPut(lat to wrap(lon)) { mutableListOf() }.add(i)
            }
        }
    }

    private fun cell(degrees: Double) = floor(degrees / config.gridDegrees).toInt()
    private fun wrap(cell: Int) = Math.floorMod(cell, longitudeCells)

    fun candidates(position: GeoPoint, previousSegment: Int?): Set<Int> {
        val latRadius = Math.toDegrees(config.searchRadiusMeters / EARTH_RADIUS_METERS)
        val lonRadius = latRadius / cos(Math.toRadians(abs(position.latitude) + latRadius)).coerceAtLeast(0.001)
        // Polar/very broad searches deliberately use the correctness fallback.
        if (lonRadius >= 180.0) return segments.indices.toSet()
        val result = largeSegments.toMutableSet()
        for (lat in cell(position.latitude - latRadius)..cell(position.latitude + latRadius)) {
            for (lon in cell(position.longitude - lonRadius)..cell(position.longitude + lonRadius)) {
                cells[lat to wrap(lon)]?.let(result::addAll)
            }
        }
        previousSegment?.let {
            result.addAll(max(0, it - config.previousSegmentWindow)..min(segments.lastIndex, it + config.previousSegmentWindow))
        }
        return result.ifEmpty { segments.indices.toMutableSet() }
    }
}
