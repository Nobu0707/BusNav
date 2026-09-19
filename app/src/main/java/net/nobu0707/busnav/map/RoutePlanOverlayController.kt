package net.nobu0707.busnav.map

import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal class RoutePlanOverlayController {
    private var routePlan: RoutePlan? = null

    fun setRoutePlan(plan: RoutePlan?) {
        routePlan = plan
    }

    fun install(style: Style) {
        ensureSources(style)
        ensureLayers(style)
        render(style)
    }

    fun render(style: Style) {
        val points = routePlan?.points.orEmpty()
        val lineFeature = if (points.size >= 2) {
            Feature.fromGeometry(
                LineString.fromLngLats(points.map { Point.fromLngLat(it.position.longitude, it.position.latitude) }),
            )
        } else {
            null
        }
        val previewSource = style.getSource(PREVIEW_SOURCE_ID) as? GeoJsonSource
        if (lineFeature == null) {
            previewSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
        } else {
            previewSource?.setGeoJson(lineFeature)
        }
        val pointFeatures = points.map { point ->
            Feature.fromGeometry(Point.fromLngLat(point.position.longitude, point.position.latitude)).apply {
                addStringProperty(POINT_TYPE_PROPERTY, point.type.name)
                addStringProperty(POINT_ID_PROPERTY, point.id)
            }
        }
        (style.getSource(POINT_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(pointFeatures),
        )
    }

    private fun ensureSources(style: Style) {
        if (style.getSource(PREVIEW_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(PREVIEW_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray<Feature>())))
        }
        if (style.getSource(POINT_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(POINT_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray<Feature>())))
        }
    }

    private fun ensureLayers(style: Style) {
        if (style.getLayer(PREVIEW_LINE_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(PREVIEW_LINE_LAYER_ID, PREVIEW_SOURCE_ID).withProperties(
                    lineColor("#FF9F43"),
                    lineWidth(3f),
                    lineOpacity(0.72f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
        addPointLayer(style, START_LAYER_ID, RoutePlanPointType.START, "#1B9E77", 9f, 3f)
        addPointLayer(style, VIA_LAYER_ID, RoutePlanPointType.VIA, "#FF9F43", 7f, 2f)
        addPointLayer(style, SHAPING_LAYER_ID, RoutePlanPointType.SHAPING, "#9B6DFF", 5f, 2f)
        addPointLayer(style, DESTINATION_LAYER_ID, RoutePlanPointType.DESTINATION, "#D94B64", 10f, 3f)
    }

    private fun addPointLayer(
        style: Style,
        layerId: String,
        type: RoutePlanPointType,
        color: String,
        radius: Float,
        strokeWidth: Float,
    ) {
        if (style.getLayer(layerId) != null) return
        style.addLayer(
            CircleLayer(layerId, POINT_SOURCE_ID)
                .withFilter(eq(get(POINT_TYPE_PROPERTY), literal(type.name)))
                .withProperties(
                    circleColor(color),
                    circleRadius(radius),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(strokeWidth),
                ),
        )
    }

    companion object {
        const val PREVIEW_SOURCE_ID = "busnav-route-plan-preview-source"
        const val PREVIEW_LINE_LAYER_ID = "busnav-route-plan-preview-line-layer"
        const val POINT_SOURCE_ID = "busnav-route-plan-point-source"
        const val START_LAYER_ID = "busnav-route-plan-start-layer"
        const val VIA_LAYER_ID = "busnav-route-plan-via-layer"
        const val SHAPING_LAYER_ID = "busnav-route-plan-shaping-layer"
        const val DESTINATION_LAYER_ID = "busnav-route-plan-destination-layer"
        const val POINT_TYPE_PROPERTY = "routePlanPointType"
        const val POINT_ID_PROPERTY = "routePlanPointId"
    }
}
