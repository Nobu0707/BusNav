package net.nobu0707.busnav.map

import net.nobu0707.busnav.domain.search.PlaceSearchItem
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal class SearchResultOverlayController {
    var selected: PlaceSearchItem? = null

    fun install(style: Style) {
        if (style.getSource(SOURCE) == null)
            style.addSource(GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>())))
        if (style.getLayer(LAYER) == null) style.addLayer(CircleLayer(LAYER, SOURCE).withProperties(
            circleColor("#1677E8"), circleRadius(11f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
        render(style)
    }

    fun render(style: Style) {
        val features = selected?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.point.longitude, it.point.latitude))) }.orEmpty()
        (style.getSource(SOURCE) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    companion object {
        const val SOURCE = "busnav-search-selected-source"
        const val LAYER = "busnav-search-selected-layer"
    }
}
