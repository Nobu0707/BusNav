package net.nobu0707.busnav.map

import kotlin.math.*
import net.nobu0707.busnav.domain.model.GeoPoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point

/** Only loaded OpenMapTiles roads and explicit brunnel=tunnel are tunnel evidence. */
class TransportationTunnelProvider(private val style: () -> Style?) : TunnelStateProvider {
    override fun observe(point: GeoPoint): TunnelObservation {
        val loaded = style()?.takeIf { it.isFullyLoaded } ?: return TunnelObservation.UNKNOWN
        val source = loaded.getSource("openmaptiles") as? VectorSource ?: return TunnelObservation.UNKNOWN
        return runCatching {
            val roads = source.querySourceFeatures(arrayOf("transportation"), null).mapNotNull { feature ->
                if (feature.getStringProperty("class") !in ROAD_CLASSES) return@mapNotNull null
                val lines = when (val geometry = feature.geometry()) {
                    is LineString -> listOf(geometry.coordinates())
                    is MultiLineString -> geometry.coordinates()
                    else -> emptyList()
                }
                val distance = lines.flatMap { it.zipWithNext() }.minOfOrNull { (a, b) ->
                    segmentDistance(point, a, b)
                } ?: return@mapNotNull null
                distance to (feature.hasProperty("brunnel") && feature.getStringProperty("brunnel") == "tunnel")
            }.sortedBy { it.first }
            val nearest = roads.firstOrNull()?.takeIf { it.first <= 15.0 }
                ?: return@runCatching TunnelObservation.UNKNOWN
            if (roads.any { it.second != nearest.second && it.first <= nearest.first + 5.0 }) {
                TunnelObservation.UNKNOWN
            } else if (nearest.second) TunnelObservation.TUNNEL else TunnelObservation.SURFACE
        }.getOrDefault(TunnelObservation.UNKNOWN)
    }

    private fun segmentDistance(p: GeoPoint, a: Point, b: Point): Double {
        val scale = cos(Math.toRadians(p.latitude))
        val ax = Math.toRadians(a.longitude() - p.longitude) * 6_371_000 * scale
        val ay = Math.toRadians(a.latitude() - p.latitude) * 6_371_000
        val bx = Math.toRadians(b.longitude() - p.longitude) * 6_371_000 * scale
        val by = Math.toRadians(b.latitude() - p.latitude) * 6_371_000
        val dx = bx - ax
        val dy = by - ay
        val length = dx * dx + dy * dy
        val t = if (length == 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    private companion object {
        val ROAD_CLASSES = setOf("motorway", "trunk", "primary", "secondary", "tertiary", "minor", "service")
    }
}
