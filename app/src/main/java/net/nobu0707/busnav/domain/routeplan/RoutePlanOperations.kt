package net.nobu0707.busnav.domain.routeplan

import java.util.UUID
import net.nobu0707.busnav.domain.model.GeoPoint

fun interface RoutePlanPointIdGenerator {
    fun nextId(): String
}

class RoutePlanOperations(
    private val idGenerator: RoutePlanPointIdGenerator = RoutePlanPointIdGenerator {
        UUID.randomUUID().toString()
    },
) {
    fun setStart(plan: RoutePlan, position: GeoPoint, name: String? = null): RoutePlan {
        val point = newPoint(RoutePlanPointType.START, position, name)
        return plan.copy(points = listOf(point) + plan.points.filterNot { it.type == RoutePlanPointType.START })
            .withEndpointsAtEdges()
    }

    fun setDestination(plan: RoutePlan, position: GeoPoint, name: String? = null): RoutePlan {
        val point = newPoint(RoutePlanPointType.DESTINATION, position, name)
        return plan.copy(points = plan.points.filterNot { it.type == RoutePlanPointType.DESTINATION } + point)
            .withEndpointsAtEdges()
    }

    fun addVia(plan: RoutePlan, position: GeoPoint, name: String? = null): RoutePlan =
        addIntermediate(plan, newPoint(RoutePlanPointType.VIA, position, name))

    fun addShaping(plan: RoutePlan, position: GeoPoint, name: String? = null): RoutePlan =
        addIntermediate(plan, newPoint(RoutePlanPointType.SHAPING, position, name))

    fun removePoint(plan: RoutePlan, id: String): RoutePlan =
        if (plan.points.none { it.id == id }) plan else plan.copy(points = plan.points.filterNot { it.id == id })

    /** Moves an intermediate point to a zero-based index among intermediate points. */
    fun movePoint(plan: RoutePlan, id: String, newIndex: Int): RoutePlan {
        val moving = plan.points.firstOrNull { it.id == id } ?: return plan
        if (moving.type !in INTERMEDIATE_TYPES) return plan
        val intermediates = plan.points.filter { it.type in INTERMEDIATE_TYPES }.toMutableList()
        intermediates.removeAll { it.id == id }
        intermediates.add(newIndex.coerceIn(0, intermediates.size), moving)
        return plan.copy(points = buildOrderedPoints(plan, intermediates))
    }

    fun changePointType(plan: RoutePlan, id: String, newType: RoutePlanPointType): RoutePlan {
        if (newType !in INTERMEDIATE_TYPES) return plan
        val point = plan.points.firstOrNull { it.id == id } ?: return plan
        if (point.type !in INTERMEDIATE_TYPES || point.type == newType) return plan
        return plan.copy(points = plan.points.map { if (it.id == id) it.copy(type = newType) else it })
    }

    fun clearIntermediatePoints(plan: RoutePlan): RoutePlan =
        plan.copy(points = plan.points.filterNot { it.type in INTERMEDIATE_TYPES })

    fun clearPlan(plan: RoutePlan): RoutePlan = plan.copy(points = emptyList())

    private fun addIntermediate(plan: RoutePlan, point: RoutePlanPoint): RoutePlan {
        val destinationIndex = plan.points.indexOfFirst { it.type == RoutePlanPointType.DESTINATION }
        val insertionIndex = if (destinationIndex >= 0) destinationIndex else plan.points.size
        return plan.copy(points = plan.points.toMutableList().apply { add(insertionIndex, point) })
            .withEndpointsAtEdges()
    }

    private fun newPoint(type: RoutePlanPointType, position: GeoPoint, name: String?): RoutePlanPoint =
        RoutePlanPoint(id = idGenerator.nextId(), type = type, position = position, name = name)

    private fun RoutePlan.withEndpointsAtEdges(): RoutePlan {
        val intermediates = points.filter { it.type in INTERMEDIATE_TYPES }
        return copy(points = buildOrderedPoints(this, intermediates))
    }

    private fun buildOrderedPoints(
        plan: RoutePlan,
        intermediates: List<RoutePlanPoint>,
    ): List<RoutePlanPoint> = buildList {
        plan.points.firstOrNull { it.type == RoutePlanPointType.START }?.let(::add)
        addAll(intermediates)
        plan.points.firstOrNull { it.type == RoutePlanPointType.DESTINATION }?.let(::add)
    }
}
