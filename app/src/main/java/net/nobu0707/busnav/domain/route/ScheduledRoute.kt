package net.nobu0707.busnav.domain.route

import net.nobu0707.busnav.domain.navigation.RouteGuidance

class ScheduledRoute(
    val id: String,
    val name: String,
    val geometry: RouteGeometry,
    points: List<RoutePoint>,
    val metadata: RouteMetadata = RouteMetadata(),
    val guidance: RouteGuidance? = null,
) {
    val points: List<RoutePoint> = points.toList()

    init {
        guidance?.maneuvers?.forEachIndexed { index, maneuver ->
            require(maneuver.index == index) { "Maneuver indices must be sequential" }
            require(maneuver.beginGeometryIndex in geometry.points.indices &&
                maneuver.endGeometryIndex in maneuver.beginGeometryIndex..geometry.points.lastIndex) {
                "Maneuver geometry indices are invalid"
            }
            if (index > 0) require(maneuver.beginGeometryIndex >= guidance.maneuvers[index - 1].beginGeometryIndex)
        }
        require(id.isNotBlank()) { "Route id must not be blank" }
        require(name.isNotBlank()) { "Route name must not be blank" }
        require(this.points.map(RoutePoint::id).distinct().size == this.points.size) {
            "Route point ids must be unique"
        }
        require(this.points.count { it.type == RoutePointType.START } == 1) {
            "A scheduled route must have exactly one START point"
        }
        require(this.points.count { it.type == RoutePointType.DESTINATION } == 1) {
            "A scheduled route must have exactly one DESTINATION point"
        }
    }

    val start: RoutePoint get() = points.single { it.type == RoutePointType.START }
    val destination: RoutePoint get() = points.single { it.type == RoutePointType.DESTINATION }

    override fun equals(other: Any?): Boolean =
        other is ScheduledRoute &&
            id == other.id &&
            name == other.name &&
            geometry == other.geometry &&
            points == other.points &&
            metadata == other.metadata && guidance == other.guidance

    override fun hashCode(): Int = arrayOf(id, name, geometry, points, metadata, guidance).contentHashCode()

    override fun toString(): String =
        "ScheduledRoute(id=$id, name=$name, geometry=$geometry, points=$points, metadata=$metadata)"
}
