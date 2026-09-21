package net.nobu0707.busnav.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import net.nobu0707.busnav.domain.detour.*
import net.nobu0707.busnav.domain.route.ScheduledRoute
import net.nobu0707.busnav.map.basemap.OverlayLayerOrder
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*

data class DetourOverlayData(val route: ScheduledRoute? = null, val targets: List<RejoinTarget> = emptyList(),
    val selected: RejoinTarget? = null, val points: List<DetourDraftPoint> = emptyList())

/** Installed after the original route and before the raw location, also on style reload. */
internal class DetourOverlayController {
    var data: DetourOverlayData = DetourOverlayData()
    fun install(style: Style) {
        for (id in listOf(LINE_SOURCE, MARKER_SOURCE)) if (style.getSource(id) == null)
            style.addSource(GeoJsonSource(id, FeatureCollection.fromFeatures(emptyArray<Feature>())))
        if (style.getLayer(OverlayLayerOrder.DETOUR_CASING) == null)
            style.addLayer(LineLayer(OverlayLayerOrder.DETOUR_CASING, LINE_SOURCE).withProperties(
                lineColor("#302440"), lineWidth(14f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        if (style.getLayer(OverlayLayerOrder.DETOUR_LINE) == null)
            style.addLayer(LineLayer(OverlayLayerOrder.DETOUR_LINE, LINE_SOURCE).withProperties(
                lineColor("#F4ABFF"), lineWidth(9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        if (style.getLayer(OverlayLayerOrder.DETOUR_MARKERS) == null)
            style.addLayer(SymbolLayer(OverlayLayerOrder.DETOUR_MARKERS, MARKER_SOURCE).withProperties(
                iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconSize(0.65f)))
        render(style)
    }
    fun render(style: Style) {
        val line = data.route?.let { Feature.fromGeometry(LineString.fromLngLats(it.geometry.points.map { p ->
            Point.fromLngLat(p.longitude, p.latitude)
        })) }
        (style.getSource(LINE_SOURCE) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(line)))
        val features = mutableListOf<Feature>()
        fun marker(point: net.nobu0707.busnav.domain.model.GeoPoint, label: String, selected: Boolean) {
            val id = "detour-marker-$label-$selected"
            if (style.getImage(id) == null) {
                val bitmap = createBitmap(64, 64)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = if (selected) Color.rgb(255, 195, 30) else Color.WHITE
                canvas.drawCircle(32f, 32f, 30f, paint)
                paint.color = Color.rgb(58, 30, 74)
                canvas.drawCircle(32f, 32f, if (selected) 23f else 26f, paint)
                paint.color = Color.WHITE; paint.textSize = 28f; paint.textAlign = Paint.Align.CENTER; paint.isFakeBoldText = true
                canvas.drawText(label, 32f, 42f, paint)
                style.addImage(id, bitmap)
            }
            features += Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply { addStringProperty("icon", id) }
        }
        // Symbol placement prioritizes earlier features. Do not cover a manual selection with an
        // automatic marker at the same location.
        data.selected?.takeIf { target -> data.targets.none { it.id == target.id } }?.let { marker(it.point, "R", true) }
        data.targets.withIndex().sortedByDescending { it.value == data.selected }.forEach { (i, target) ->
            val overlapsManual = data.selected?.let { it.source == RejoinTargetSource.MANUAL &&
                net.nobu0707.busnav.domain.navigation.distanceMeters(it.point, target.point) < 5.0 } == true
            if (!overlapsManual) marker(target.point, (i + 1).toString(), target == data.selected)
        }
        data.points.forEachIndexed { i, p -> marker(p.position, (if (p.type == DetourDraftPointType.VIA) "V" else "S") + (i + 1), false) }
        (style.getSource(MARKER_SOURCE) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
    companion object {
        const val LINE_SOURCE = "busnav-detour-line-source"
        const val MARKER_SOURCE = "busnav-detour-marker-source"
    }
}
