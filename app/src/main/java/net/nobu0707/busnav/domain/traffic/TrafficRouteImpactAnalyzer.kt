package net.nobu0707.busnav.domain.traffic

import kotlin.math.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.ScheduledRoute

enum class TrafficImpactPosition { AHEAD, CURRENT, BEHIND, OFF_ROUTE, AMBIGUOUS }
enum class TrafficImpactLevel { NONE, INFORMATION, DELAY, RESTRICTION, BLOCKING }
enum class TrafficMatchConfidence { HIGH, MEDIUM, LOW, AMBIGUOUS }
data class TrafficRouteImpact(
    val event: TrafficEvent,
    val position: TrafficImpactPosition,
    val level: TrafficImpactLevel,
    val startProgressMeters: Double? = null,
    val endProgressMeters: Double? = null,
    val distanceAheadMeters: Double? = null,
    val matchingConfidence: TrafficMatchConfidence = TrafficMatchConfidence.LOW,
    val endKnown: Boolean = false,
    val highwayDecisionLabel: String? = null,
)

/** Prepared per immutable route. No GPS, network, UI, or provider-specific road-link assumptions. */
class TrafficRouteImpactAnalyzer(
    private val route: ScheduledRoute,
    private val config: TrafficConfig = TrafficConfig(),
    private val distances: RouteDistanceIndex = RouteDistanceIndex(route.geometry),
) {
    init { require(distances.geometry === route.geometry) }
    private val roadNamesBySegment = route.geometry.points.dropLast(1).indices.map { i ->
        route.guidance?.maneuvers.orEmpty().filter { i >= it.beginGeometryIndex && i < it.endGeometryIndex }
            .flatMap { it.streetNames }.map(::roadKey)
    }
    private data class Hit(val start: Double, val end: Double, val distance: Double, val segment: Int, val directed: Boolean, val identity: Boolean, val contradictory: Boolean)

    fun analyze(snapshot: TrafficSnapshot, now: Long, currentProgressMeters: Double?): List<TrafficRouteImpact> =
        snapshot.events.filter { it.validity(now) == TrafficValidity.ACTIVE }.map { match(it) }
            .map { position(it, currentProgressMeters) }.sortedWith(impactOrder)

    /** Reposition cached geometry impacts cheaply when the navigation matcher advances. */
    fun position(impact: TrafficRouteImpact, progress: Double?): TrafficRouteImpact {
        if (impact.position == TrafficImpactPosition.OFF_ROUTE || impact.matchingConfidence == TrafficMatchConfidence.AMBIGUOUS) return impact
        val start = impact.startProgressMeters ?: return impact
        val end = impact.endProgressMeters ?: start
        if (progress == null || !progress.isFinite() || progress !in 0.0..distances.totalMeters)
            return impact.copy(position = TrafficImpactPosition.AMBIGUOUS, distanceAheadMeters = null)
        val position = when {
            end < progress - config.progressToleranceMeters -> TrafficImpactPosition.BEHIND
            start > progress + config.progressToleranceMeters -> TrafficImpactPosition.AHEAD
            else -> TrafficImpactPosition.CURRENT
        }
        return impact.copy(position = position, distanceAheadMeters = if (position == TrafficImpactPosition.BEHIND) null else (start - progress).coerceAtLeast(0.0))
    }

    fun match(event: TrafficEvent): TrafficRouteImpact {
        val hits = mutableListOf<Hit>()
        val points = route.geometry.points
        val eventPoints = when (val geometry = event.geometry) {
            is TrafficGeometry.Point -> listOf(geometry.point)
            is TrafficGeometry.Polyline -> geometry.points
            is TrafficGeometry.Polygon -> geometry.points
        }
        val latMargin = config.corridorMeters / 110_000
        val minLat = eventPoints.minOf { it.latitude } - latMargin
        val maxLat = eventPoints.maxOf { it.latitude } + latMargin
        val lonMargin = latMargin / cos(Math.toRadians(max(abs(minLat), abs(maxLat)))).coerceAtLeast(0.001)
        val minLon = eventPoints.minOf { it.longitude } - lonMargin
        val maxLon = eventPoints.maxOf { it.longitude } + lonMargin
        for (i in 0 until points.lastIndex) {
            val a = points[i]; val b = points[i + 1]
            if (max(a.latitude, b.latitude) < minLat || min(a.latitude, b.latitude) > maxLat) continue
            if (maxLon - minLon < 180 && abs(a.longitude - b.longitude) < 180 &&
                (max(a.longitude, b.longitude) < minLon || min(a.longitude, b.longitude) > maxLon)) continue
            val names = roadNamesBySegment[i]
            val identity = listOfNotNull(event.roadReference, event.roadName).any { roadKey(it) in names }
            val contradictory = names.isNotEmpty() && listOfNotNull(event.roadName, event.roadReference).isNotEmpty() && !identity
            fun add(start: Double, end: Double, distance: Double, bearing: Double?) {
                if (distance > config.corridorMeters) return
                val expected = event.bearingDegrees ?: bearing
                val delta = expected?.let { angle(bearing(a, b), it + if (event.direction == TrafficDirection.REVERSE) 180.0 else 0.0) }
                if (event.direction in listOf(TrafficDirection.FORWARD, TrafficDirection.REVERSE) && delta != null && delta > 60) return
                val directed = event.direction == TrafficDirection.BOTH ||
                    event.direction != TrafficDirection.UNKNOWN && delta != null && delta <= 45
                hits += Hit(start, end, distance, i, directed, identity, contradictory)
            }
            when (val geometry = event.geometry) {
                is TrafficGeometry.Point -> {
                    val p = RouteProjector.projectSegment(geometry.point, distances, i)
                    add(p.distanceAlongRouteMeters, p.distanceAlongRouteMeters, p.distanceFromRouteMeters, null)
                }
                is TrafficGeometry.Polyline -> geometry.points.zipWithNext().forEach { (c, d) ->
                    // Project both ends; overlapping long segments do not depend on vertex density.
                    val cProjection = RouteProjector.projectSegment(c, distances, i)
                    val dProjection = RouteProjector.projectSegment(d, distances, i)
                    val eventIndex = RouteDistanceIndex(net.nobu0707.busnav.domain.route.RouteGeometry(listOf(c, d)))
                    val near = maxOf(
                        RouteProjector.projectSegment(cProjection.projectedPoint, eventIndex, 0).distanceFromRouteMeters,
                        RouteProjector.projectSegment(dProjection.projectedPoint, eventIndex, 0).distanceFromRouteMeters)
                    val alignment = angle(bearing(a, b), bearing(c, d)).let { min(it, 180 - it) }
                    if (alignment <= 35) add(min(cProjection.distanceAlongRouteMeters, dProjection.distanceAlongRouteMeters),
                        max(cProjection.distanceAlongRouteMeters, dProjection.distanceAlongRouteMeters), near, bearing(c, d))
                    else intersection(a, b, c, d)?.let { fraction ->
                        val progress = distances.distanceAtGeometryIndex(i) + fraction * distances.distanceBetweenIndices(i, i + 1)
                        // A geometric crossing alone cannot establish road identity.
                        hits += Hit(progress, progress, 0.0, i, false, identity, contradictory)
                    }
                }
                is TrafficGeometry.Polygon -> {
                    val ring = geometry.points
                    val cuts = mutableListOf(0.0, 1.0)
                    ring.indices.forEach { k -> intersection(a, b, ring[k], ring[(k + 1) % ring.size])?.let(cuts::add) }
                    cuts.distinct().sorted().zipWithNext().forEach { (from, to) ->
                        val mid = interpolate(a, b, (from + to) / 2)
                        if (inside(mid, ring)) add(distances.distanceAtGeometryIndex(i) + from * distances.distanceBetweenIndices(i, i + 1),
                            distances.distanceAtGeometryIndex(i) + to * distances.distanceBetweenIndices(i, i + 1), 0.0, null)
                    }
                }
            }
        }
        if (hits.isEmpty()) return TrafficRouteImpact(event, TrafficImpactPosition.OFF_ROUTE, TrafficImpactLevel.NONE)
        val sorted = hits.sortedBy { it.start }
        val groups = mutableListOf<MutableList<Hit>>()
        for (hit in sorted) {
            val last = groups.lastOrNull()
            if (last == null || hit.start - last.maxOf { it.end } > config.corridorMeters * 2) groups += mutableListOf(hit)
            else last += hit
        }
        val start = hits.minOf { it.start }; val end = hits.maxOf { it.end }
        val relevant = hits.filter { it.end > it.start || event.geometry is TrafficGeometry.Point }
        val strong = relevant.isNotEmpty() && relevant.all { hit ->
            hit.directed && !hit.contradictory &&
                (hit.identity || hit.distance <= config.strongGeometryMeters && event.geometry !is TrafficGeometry.Polygon) &&
                (event.geometry !is TrafficGeometry.Point || hit.identity || event.bearingDegrees != null && hit.distance <= 5)
        } && (event.geometry is TrafficGeometry.Point || end - start >= config.minimumOverlapMeters)
        val confidence = when {
            groups.size > 1 || hits.any { it.contradictory } -> TrafficMatchConfidence.AMBIGUOUS
            strong -> TrafficMatchConfidence.HIGH
            hits.any { it.identity } || event.geometry is TrafficGeometry.Polygon -> TrafficMatchConfidence.MEDIUM
            else -> TrafficMatchConfidence.AMBIGUOUS
        }
        val intended = level(event.kind)
        val level = if (intended == TrafficImpactLevel.BLOCKING && confidence != TrafficMatchConfidence.HIGH) TrafficImpactLevel.INFORMATION else intended
        val highway = if (confidence == TrafficMatchConfidence.HIGH && hits.any { it.identity } &&
            intended in listOf(TrafficImpactLevel.BLOCKING, TrafficImpactLevel.RESTRICTION)) {
            route.guidance?.maneuvers.orEmpty().firstOrNull {
                (it.type.name.startsWith("RAMP") || it.type.name.startsWith("EXIT") || it.type.name.startsWith("KEEP") || it.type == ManeuverType.MERGE) &&
                    distances.distanceAtGeometryIndex(it.beginGeometryIndex) in (start - 200)..(end + 200)
            }?.let { it.signs.firstOrNull()?.text ?: it.streetNames.firstOrNull() }
        } else null
        return TrafficRouteImpact(event, TrafficImpactPosition.AMBIGUOUS, level, start, end,
            matchingConfidence = confidence, endKnown = confidence == TrafficMatchConfidence.HIGH && event.geometry !is TrafficGeometry.Point,
            highwayDecisionLabel = highway)
    }

    companion object {
        val impactOrder = compareByDescending<TrafficRouteImpact> { it.level.ordinal }
            .thenBy { it.distanceAheadMeters ?: Double.MAX_VALUE }.thenByDescending { it.highwayDecisionLabel != null }
        fun level(kind: TrafficEventKind) = when (kind) {
            TrafficEventKind.ROAD_CLOSURE, TrafficEventKind.ENTRY_CLOSURE, TrafficEventKind.EXIT_CLOSURE, TrafficEventKind.WINTER_CLOSURE -> TrafficImpactLevel.BLOCKING
            TrafficEventKind.LANE_RESTRICTION, TrafficEventKind.SPEED_RESTRICTION, TrafficEventKind.ACCIDENT,
            TrafficEventKind.ROADWORK, TrafficEventKind.OBSTACLE, TrafficEventKind.EVENT_RESTRICTION -> TrafficImpactLevel.RESTRICTION
            TrafficEventKind.CONGESTION -> TrafficImpactLevel.DELAY
            else -> TrafficImpactLevel.INFORMATION
        }
        private fun roadKey(name: String) = name.lowercase().replace(Regex("[\\s　-]"), "")
        private fun bearing(a: GeoPoint, b: GeoPoint): Double = Math.toDegrees(atan2(
            longitudeDelta(b.longitude - a.longitude) * cos(Math.toRadians((a.latitude + b.latitude) / 2)), b.latitude - a.latitude))
        private fun angle(a: Double, b: Double) = abs(longitudeDelta(a - b))
        private fun interpolate(a: GeoPoint, b: GeoPoint, f: Double) = GeoPoint(a.latitude + (b.latitude - a.latitude) * f,
            longitudeDelta(a.longitude + longitudeDelta(b.longitude - a.longitude) * f))
        private fun intersection(a: GeoPoint, b: GeoPoint, c: GeoPoint, d: GeoPoint): Double? {
            val rx = longitudeDelta(b.longitude - a.longitude); val ry = b.latitude - a.latitude
            val sx = longitudeDelta(d.longitude - c.longitude); val sy = d.latitude - c.latitude
            val qx = longitudeDelta(c.longitude - a.longitude); val qy = c.latitude - a.latitude
            val denominator = rx * sy - ry * sx
            if (abs(denominator) < 1e-16) return null
            val t = (qx * sy - qy * sx) / denominator; val u = (qx * ry - qy * rx) / denominator
            return t.takeIf { it in 0.0..1.0 && u in 0.0..1.0 }
        }
        private fun inside(p: GeoPoint, polygon: List<GeoPoint>): Boolean {
            var contained = false
            polygon.indices.forEach { i ->
                val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
                if ((a.latitude > p.latitude) != (b.latitude > p.latitude) &&
                    longitudeDelta(p.longitude - a.longitude) < longitudeDelta(b.longitude - a.longitude) *
                    (p.latitude - a.latitude) / (b.latitude - a.latitude)) contained = !contained
            }
            return contained
        }
    }
}
