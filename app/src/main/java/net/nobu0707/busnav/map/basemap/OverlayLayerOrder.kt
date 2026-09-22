package net.nobu0707.busnav.map.basemap

/**
 * Restore explicit ordering after every style load: route lines below shields,
 * then traffic, editable points and the live vehicle.
 */
object OverlayLayerOrder {
    const val ACTIVE_ROUTE_CASING = "busnav-scheduled-route-casing-layer"
    const val ACTIVE_ROUTE = "busnav-scheduled-route-line-layer"
    const val ACTIVE_START = "busnav-scheduled-route-start-layer"
    const val ACTIVE_STOP = "busnav-scheduled-route-stop-layer"
    const val ACTIVE_VIA = "busnav-scheduled-route-via-layer"
    const val ACTIVE_SHAPING = "busnav-scheduled-route-shaping-layer"
    const val ACTIVE_DESTINATION = "busnav-scheduled-route-destination-layer"
    const val PLAN_PREVIEW = "busnav-route-plan-preview-line-layer"
    const val START = "busnav-route-plan-start-layer"
    const val VIA = "busnav-route-plan-via-layer"
    const val SHAPING = "busnav-route-plan-shaping-layer"
    const val DESTINATION = "busnav-route-plan-destination-layer"
    const val DETOUR_CASING = "busnav-detour-casing"
    const val DETOUR_LINE = "busnav-detour-line"
    const val DETOUR_MARKERS = "busnav-detour-markers"
    const val TRAFFIC_AREA = "busnav-traffic-area"
    const val TRAFFIC_LINE = "busnav-traffic-line"
    const val TRAFFIC_MARKER = "busnav-traffic-marker"
    const val VEHICLE = "busnav-vehicle-layer"

    const val SHIELD_ANCHOR = "busnav-shield-anchor"
    val routeLineIds = listOf(ACTIVE_ROUTE_CASING, ACTIVE_ROUTE, DETOUR_CASING, DETOUR_LINE, PLAN_PREVIEW)

    /** Called after every reload. Fallback styles may not contain a shield anchor. */
    fun restore(style: org.maplibre.android.maps.Style) {
        val anchor = style.getLayer(SHIELD_ANCHOR)?.id
            ?: style.layers.firstOrNull { it.id.startsWith("route-shield-") }?.id
        if (anchor != null) routeLineIds.forEach { id ->
            style.getLayer(id)?.let { layer -> style.removeLayer(layer); style.addLayerBelow(layer, anchor) }
        }
        orderedLayerIds.filterNot { it in routeLineIds }.forEach { id ->
            style.getLayer(id)?.let { layer -> style.removeLayer(layer); style.addLayer(layer) }
        }
    }

    val orderedLayerIds = routeLineIds + listOf(
        DETOUR_MARKERS, TRAFFIC_AREA, TRAFFIC_LINE, TRAFFIC_MARKER,
        ACTIVE_START, ACTIVE_STOP, ACTIVE_VIA, ACTIVE_SHAPING, ACTIVE_DESTINATION,
        START, VIA, SHAPING, DESTINATION, VEHICLE,
    )
}
