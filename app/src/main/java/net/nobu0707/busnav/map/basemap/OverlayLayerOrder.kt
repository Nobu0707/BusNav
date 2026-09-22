package net.nobu0707.busnav.map.basemap

/**
 * MapLibre appends these layers after every basemap style load. Their order is
 * intentionally stable: routes, editable points, then the live vehicle.
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

    val orderedLayerIds = listOf(
        ACTIVE_ROUTE_CASING,
        ACTIVE_ROUTE,
        ACTIVE_START,
        ACTIVE_STOP,
        ACTIVE_VIA,
        ACTIVE_SHAPING,
        ACTIVE_DESTINATION,
        DETOUR_CASING,
        DETOUR_LINE,
        DETOUR_MARKERS,
        TRAFFIC_AREA,
        TRAFFIC_LINE,
        TRAFFIC_MARKER,
        PLAN_PREVIEW,
        START,
        VIA,
        SHAPING,
        DESTINATION,
        VEHICLE,
    )
}
