package net.nobu0707.busnav.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import net.nobu0707.busnav.domain.facility.*
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal class RouteFacilityOverlayController {
    var candidates: List<RouteFacilityCandidate> = emptyList()
    var planned: Set<RouteFacilityId> = emptySet()
    var selected: RouteFacilityId? = null
    var expanded: Boolean = false

    fun install(style: Style) {
        if (style.getSource(SOURCE) == null)
            style.addSource(GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>())))
        if (style.getLayer(LAYER) == null) style.addLayer(SymbolLayer(LAYER, SOURCE).withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconSize(0.65f)))
        render(style)
    }

    fun render(style: Style) {
        val features = candidates.filter { expanded || it.id == selected || it.id in planned }.map { candidate ->
            val prefix = if (candidate.type == RouteFacilityType.SERVICE_AREA) "SA" else "PA"
            val state = when {
                candidate.id == selected -> "selected"
                candidate.id in planned -> "planned"
                else -> "normal"
            }
            val icon = "busnav-facility-$prefix-$state"
            if (style.getImage(icon) == null) style.addImage(icon, badge(prefix, state))
            Feature.fromGeometry(Point.fromLngLat(candidate.point.longitude, candidate.point.latitude)).apply {
                addStringProperty("icon", icon)
            }
        }
        (style.getSource(SOURCE) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun badge(label: String, state: String) = createBitmap(80, 80).also { bitmap ->
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(40f, 40f, 39f, paint)
        paint.color = Color.parseColor(when (state) {
            "selected" -> "#C62828"
            "planned" -> "#6A1B9A"
            else -> "#175DAD"
        })
        canvas.drawCircle(40f, 40f, 35f, paint)
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText(label, 40f, 51f, paint)
    }

    companion object {
        const val SOURCE = "busnav-route-facilities"
        const val LAYER = "busnav-route-facility-markers"
    }
}
