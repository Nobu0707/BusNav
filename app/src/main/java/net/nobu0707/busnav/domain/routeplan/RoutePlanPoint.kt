package net.nobu0707.busnav.domain.routeplan

import net.nobu0707.busnav.domain.model.GeoPoint

data class RoutePlanPoint(
    val id: String,
    val type: RoutePlanPointType,
    val position: GeoPoint,
    val name: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Route plan point id must not be blank" }
    }
}
