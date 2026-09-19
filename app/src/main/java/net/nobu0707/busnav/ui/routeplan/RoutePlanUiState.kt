package net.nobu0707.busnav.ui.routeplan

import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.RoutePlanBounds
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routeplan.RoutePlanValidationResult
import net.nobu0707.busnav.domain.routeplan.boundsOrNull
import net.nobu0707.busnav.domain.routeplan.validateForRouting

data class RoutePlanUiState(
    val currentPlan: RoutePlan = RoutePlan(id = "current-route-plan", name = "編集中のルート"),
    val selectedPointId: String? = null,
    val selectedAddMode: RoutePlanPointType = RoutePlanPointType.VIA,
    val validation: RoutePlanValidationResult = currentPlan.validateForRouting(),
    val hasUnsavedChanges: Boolean = false,
    val planOverviewRequestId: Int = 0,
    val errorMessage: String? = null,
    val revision: Long = 0,
) {
    val planBounds: RoutePlanBounds? get() = currentPlan.boundsOrNull()
}
