package net.nobu0707.busnav.ui.routeplan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanOperations
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routeplan.validateForRouting

class RoutePlanEditorStateHolder(
    initialState: RoutePlanUiState = RoutePlanUiState(),
    private val operations: RoutePlanOperations = RoutePlanOperations(),
) {
    private val _uiState = MutableStateFlow(initialState.normalized())
    val uiState: StateFlow<RoutePlanUiState> = _uiState.asStateFlow()

    fun selectAddMode(type: RoutePlanPointType) {
        update { copy(selectedAddMode = type, errorMessage = null) }
    }

    fun selectPoint(id: String?) {
        update { copy(selectedPointId = id?.takeIf { candidate -> currentPlan.points.any { it.id == candidate } }) }
    }

    fun addPoint(position: GeoPoint) {
        val addMode = _uiState.value.selectedAddMode
        updatePlan { plan ->
            when (addMode) {
                RoutePlanPointType.START -> operations.setStart(plan, position)
                RoutePlanPointType.DESTINATION -> operations.setDestination(plan, position)
                RoutePlanPointType.VIA -> operations.addVia(plan, position)
                RoutePlanPointType.SHAPING -> operations.addShaping(plan, position)
            }
        }
    }

    fun removePoint(id: String) {
        updatePlan { operations.removePoint(it, id) }
        update { if (selectedPointId == id) copy(selectedPointId = null) else this }
    }

    fun movePoint(id: String, delta: Int) {
        val intermediates = _uiState.value.currentPlan.points.filter {
            it.type == RoutePlanPointType.VIA || it.type == RoutePlanPointType.SHAPING
        }
        val currentIndex = intermediates.indexOfFirst { it.id == id }
        if (currentIndex < 0) return
        updatePlan { operations.movePoint(it, id, currentIndex + delta) }
    }

    fun toggleIntermediateType(id: String) {
        val point = _uiState.value.currentPlan.points.firstOrNull { it.id == id } ?: return
        val newType = when (point.type) {
            RoutePlanPointType.VIA -> RoutePlanPointType.SHAPING
            RoutePlanPointType.SHAPING -> RoutePlanPointType.VIA
            else -> return
        }
        updatePlan { operations.changePointType(it, id, newType) }
    }

    fun clearIntermediatePoints() = updatePlan(operations::clearIntermediatePoints)

    fun clearPlan() {
        updatePlan(operations::clearPlan)
        update { copy(selectedPointId = null) }
    }

    fun requestPlanOverview() {
        if (_uiState.value.currentPlan.points.isEmpty()) return
        update { copy(planOverviewRequestId = planOverviewRequestId + 1) }
    }

    fun completeEditing() = update { copy(hasUnsavedChanges = false, errorMessage = null) }

    private fun updatePlan(transform: (net.nobu0707.busnav.domain.routeplan.RoutePlan) -> net.nobu0707.busnav.domain.routeplan.RoutePlan) {
        update {
            val updated = transform(currentPlan)
            if (updated == currentPlan) this else copy(
                currentPlan = updated,
                validation = updated.validateForRouting(),
                hasUnsavedChanges = true,
                errorMessage = null,
                revision = revision + 1,
            )
        }
    }

    private inline fun update(transform: RoutePlanUiState.() -> RoutePlanUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun RoutePlanUiState.normalized(): RoutePlanUiState =
        copy(validation = currentPlan.validateForRouting())
}
