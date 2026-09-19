package net.nobu0707.busnav.domain.routeplan

class RoutePlan(
    val id: String,
    val name: String? = null,
    points: List<RoutePlanPoint> = emptyList(),
) {
    val points: List<RoutePlanPoint> = points.toList()

    init {
        require(id.isNotBlank()) { "Route plan id must not be blank" }
    }

    fun copy(
        id: String = this.id,
        name: String? = this.name,
        points: List<RoutePlanPoint> = this.points,
    ): RoutePlan = RoutePlan(id = id, name = name, points = points)

    override fun equals(other: Any?): Boolean =
        other is RoutePlan && id == other.id && name == other.name && points == other.points

    override fun hashCode(): Int = arrayOf(id, name, points).contentHashCode()

    override fun toString(): String = "RoutePlan(id=$id, name=$name, points=$points)"
}
