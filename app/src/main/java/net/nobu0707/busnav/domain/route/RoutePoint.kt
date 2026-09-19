package net.nobu0707.busnav.domain.route

import net.nobu0707.busnav.domain.model.GeoPoint

data class RoutePoint(
    val id: String,
    val type: RoutePointType,
    val position: GeoPoint,
    val name: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Route point id must not be blank" }
    }
}
