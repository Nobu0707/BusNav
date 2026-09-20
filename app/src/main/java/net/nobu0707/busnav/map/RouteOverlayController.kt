package net.nobu0707.busnav.map

import net.nobu0707.busnav.map.basemap.OverlayLayerOrder
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
import net.nobu0707.busnav.domain.route.RoutePointType
import net.nobu0707.busnav.domain.route.ScheduledRoute

internal class RouteOverlayController {
    private var activeRoute: ScheduledRoute? = null

    fun setRoute(route: ScheduledRoute?) {
        activeRoute = route
    }

    fun install(style: Style) {
        ensureGeometrySource(style)
        ensurePointSource(style)
        ensureLineLayers(style)
        ensurePointLayers(style)
        render(style)
    }

    fun render(style: Style) {
        val route = activeRoute
        val line = route?.let {
            LineString.fromLngLats(it.geometry.points.map { point ->
                Point.fromLngLat(point.longitude, point.latitude)
            })
        }
        val geometrySource = style.getSource(GEOMETRY_SOURCE_ID) as? GeoJsonSource
        if (line == null) {
            geometrySource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
        } else {
            geometrySource?.setGeoJson(Feature.fromGeometry(line))
        }

        val pointFeatures = route?.points.orEmpty().map { routePoint ->
            Feature.fromGeometry(
                Point.fromLngLat(routePoint.position.longitude, routePoint.position.latitude),
            ).apply {
                addStringProperty(POINT_TYPE_PROPERTY, routePoint.type.name)
                routePoint.name?.let { addStringProperty(POINT_NAME_PROPERTY, it) }
            }
        }
        (style.getSource(POINT_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(pointFeatures),
        )
    }

    private fun ensureGeometrySource(style: Style) {
        if (style.getSource(GEOMETRY_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(GEOMETRY_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray<Feature>())),
            )
        }
    }

    private fun ensurePointSource(style: Style) {
        if (style.getSource(POINT_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(POINT_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray<Feature>())),
            )
        }
    }

    private fun ensureLineLayers(style: Style) {
        if (style.getLayer(CASING_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(CASING_LAYER_ID, GEOMETRY_SOURCE_ID).withProperties(
                    lineColor("#14252E"),
                    lineWidth(9f),
                    lineOpacity(0.9f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
        if (style.getLayer(LINE_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(LINE_LAYER_ID, GEOMETRY_SOURCE_ID).withProperties(
                    lineColor("#5BD6FF"),
                    lineWidth(5f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
    }

    private fun ensurePointLayers(style: Style) {
        addPointLayer(style, START_LAYER_ID, RoutePointType.START, "#35D07F", 8f)
        addPointLayer(style, STOP_LAYER_ID, RoutePointType.STOP, "#FFD166", 7f)
        addPointLayer(style, VIA_LAYER_ID, RoutePointType.VIA, "#FF9F43", 7f)
        addPointLayer(style, SHAPING_LAYER_ID, RoutePointType.SHAPING, "#9B6DFF", 5f)
        addPointLayer(style, DESTINATION_LAYER_ID, RoutePointType.DESTINATION, "#FF6B6B", 9f)
    }

    private fun addPointLayer(
        style: Style,
        layerId: String,
        type: RoutePointType,
        color: String,
        radius: Float,
    ) {
        if (style.getLayer(layerId) != null) return
        style.addLayer(
            CircleLayer(layerId, POINT_SOURCE_ID)
                .withFilter(eq(get(POINT_TYPE_PROPERTY), literal(type.name)))
                .withProperties(
                    circleColor(color),
                    circleRadius(radius),
                    circleStrokeColor("#F7FBFF"),
                    circleStrokeWidth(2.5f),
                ),
        )
    }

    companion object {
        const val GEOMETRY_SOURCE_ID = "busnav-scheduled-route-source"
        const val POINT_SOURCE_ID = "busnav-scheduled-route-points-source"
        const val CASING_LAYER_ID = OverlayLayerOrder.ACTIVE_ROUTE_CASING
        const val LINE_LAYER_ID = OverlayLayerOrder.ACTIVE_ROUTE
        const val START_LAYER_ID = OverlayLayerOrder.ACTIVE_START
        const val STOP_LAYER_ID = OverlayLayerOrder.ACTIVE_STOP
        const val VIA_LAYER_ID = OverlayLayerOrder.ACTIVE_VIA
        const val SHAPING_LAYER_ID = OverlayLayerOrder.ACTIVE_SHAPING
        const val DESTINATION_LAYER_ID = OverlayLayerOrder.ACTIVE_DESTINATION
        const val POINT_TYPE_PROPERTY = "routePointType"
        const val POINT_NAME_PROPERTY = "routePointName"
    }
}
