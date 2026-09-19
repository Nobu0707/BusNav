package net.nobu0707.busnav.ui.routeplan

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanOperations
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointIdGenerator
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlanEditorStateHolderTest {
    private var id = 0
    private val holder = RoutePlanEditorStateHolder(
        operations = RoutePlanOperations(RoutePlanPointIdGenerator { "id-${++id}" }),
    )

    @Test fun `selected mode controls point added by map`() {
        holder.selectAddMode(RoutePlanPointType.START)
        holder.addPoint(GeoPoint(35.0, 139.0))
        assertEquals(RoutePlanPointType.START, holder.uiState.value.currentPlan.points.single().type)
        assertTrue(holder.uiState.value.hasUnsavedChanges)
    }

    @Test fun `remove clears selected point`() {
        holder.addPoint(GeoPoint(35.0, 139.0))
        val id = holder.uiState.value.currentPlan.points.single().id
        holder.selectPoint(id)
        holder.removePoint(id)
        assertTrue(holder.uiState.value.currentPlan.points.isEmpty())
        assertEquals(null, holder.uiState.value.selectedPointId)
    }

    @Test fun `reorder and toggle update intermediate points`() {
        holder.addPoint(GeoPoint(35.0, 139.0))
        holder.addPoint(GeoPoint(35.1, 139.1))
        val second = holder.uiState.value.currentPlan.points.last().id
        holder.movePoint(second, -1)
        assertEquals(second, holder.uiState.value.currentPlan.points.first().id)
        holder.toggleIntermediateType(second)
        assertEquals(RoutePlanPointType.SHAPING, holder.uiState.value.currentPlan.points.first().type)
    }

    @Test fun `bounds cover all points`() {
        holder.addPoint(GeoPoint(35.0, 140.0))
        holder.addPoint(GeoPoint(36.0, 139.0))
        val bounds = holder.uiState.value.planBounds
        assertNotNull(bounds)
        assertEquals(35.0, bounds!!.minLatitude, 0.0)
        assertEquals(140.0, bounds.maxLongitude, 0.0)
    }

    @Test fun `overview requires points and edit complete clears dirty flag`() {
        holder.requestPlanOverview()
        assertEquals(0, holder.uiState.value.planOverviewRequestId)
        holder.addPoint(GeoPoint(35.0, 139.0))
        holder.requestPlanOverview()
        assertEquals(1, holder.uiState.value.planOverviewRequestId)
        holder.completeEditing()
        assertFalse(holder.uiState.value.hasUnsavedChanges)
    }

    @Test fun `plan edits increment revision but selection does not`() {
        assertEquals(0, holder.uiState.value.revision)
        holder.selectAddMode(RoutePlanPointType.START)
        holder.selectPoint(null)
        assertEquals(0, holder.uiState.value.revision)
        holder.addPoint(GeoPoint(35.0, 139.0))
        assertEquals(1, holder.uiState.value.revision)
        holder.removePoint(holder.uiState.value.currentPlan.points.single().id)
        assertEquals(2, holder.uiState.value.revision)
    }
}
