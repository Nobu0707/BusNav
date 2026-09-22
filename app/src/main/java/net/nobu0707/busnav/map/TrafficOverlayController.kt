package net.nobu0707.busnav.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import net.nobu0707.busnav.domain.traffic.*
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*

/** Original BusNav symbols; retained data reinstalls after every style load. */
internal class TrafficOverlayController {
    var events: List<TrafficEvent> = emptyList()
    fun install(style: Style) {
        listOf(LINE_SOURCE, AREA_SOURCE, MARKER_SOURCE).forEach { if (style.getSource(it) == null)
            style.addSource(GeoJsonSource(it, FeatureCollection.fromFeatures(emptyArray<Feature>()))) }
        if (style.getLayer(AREA_LAYER) == null) style.addLayer(FillLayer(AREA_LAYER, AREA_SOURCE).withProperties(fillColor("#FFB040"), fillOpacity(0.25f)))
        if (style.getLayer(LINE_LAYER) == null) style.addLayer(LineLayer(LINE_LAYER, LINE_SOURCE).withProperties(
            lineColor(get("color")), lineWidth(6f), lineDasharray(arrayOf(1.5f, 1f))))
        if (style.getLayer(MARKER_LAYER) == null) style.addLayer(SymbolLayer(MARKER_LAYER, MARKER_SOURCE).withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconSize(0.65f)))
        render(style)
    }
    fun render(style: Style) {
        val lines = mutableListOf<Feature>(); val areas = mutableListOf<Feature>(); val markers = mutableListOf<Feature>()
        for (event in events) {
            val closed = TrafficRouteImpactAnalyzer.level(event.kind) == TrafficImpactLevel.BLOCKING
            val label = if (closed) "×" else when (event.kind) { TrafficEventKind.ROADWORK -> "工"; TrafficEventKind.CONGESTION -> "≋"; else -> "!" }
            val color = if (closed) "#C62828" else if (event.kind == TrafficEventKind.CONGESTION) "#A24C00" else "#725000"
            val icon = "busnav-traffic-${event.kind}"
            if (style.getImage(icon) == null) {
                val bitmap = createBitmap(64, 64); val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = Color.WHITE; canvas.drawCircle(32f, 32f, 31f, paint)
                paint.color = Color.parseColor(color); canvas.drawCircle(32f, 32f, 27f, paint)
                paint.color = Color.WHITE; paint.textSize = 36f; paint.textAlign = Paint.Align.CENTER; paint.isFakeBoldText = true
                canvas.drawText(label, 32f, 44f, paint); style.addImage(icon, bitmap)
            }
            fun point(p: net.nobu0707.busnav.domain.model.GeoPoint) = Point.fromLngLat(p.longitude, p.latitude)
            val anchor = when (val geometry = event.geometry) {
                is TrafficGeometry.Point -> point(geometry.point)
                is TrafficGeometry.Polyline -> {
                    lines += Feature.fromGeometry(LineString.fromLngLats(geometry.points.map(::point))).apply { addStringProperty("color", color) }
                    point(geometry.points.first())
                }
                is TrafficGeometry.Polygon -> {
                    val ring = geometry.points.map(::point).let { if (it.first() == it.last()) it else it + it.first() }
                    areas += Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
                    point(geometry.points.first())
                }
            }
            markers += Feature.fromGeometry(anchor).apply { addStringProperty("icon", icon) }
        }
        listOf(LINE_SOURCE to lines, AREA_SOURCE to areas, MARKER_SOURCE to markers).forEach { (id, features) ->
            (style.getSource(id) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }
    companion object {
        const val LINE_SOURCE = "busnav-traffic-lines"
        const val AREA_SOURCE = "busnav-traffic-areas"
        const val MARKER_SOURCE = "busnav-traffic-markers"
        const val LINE_LAYER = net.nobu0707.busnav.map.basemap.OverlayLayerOrder.TRAFFIC_LINE
        const val AREA_LAYER = net.nobu0707.busnav.map.basemap.OverlayLayerOrder.TRAFFIC_AREA
        const val MARKER_LAYER = net.nobu0707.busnav.map.basemap.OverlayLayerOrder.TRAFFIC_MARKER
    }
}
