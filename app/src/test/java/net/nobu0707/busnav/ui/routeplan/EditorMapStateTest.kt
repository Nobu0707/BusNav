package net.nobu0707.busnav.ui.routeplan

import net.nobu0707.busnav.data.route.createDevelopmentSampleRoute
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routeplan.*
import org.junit.Assert.*
import org.junit.Test

class EditorMapStateTest {
    private val sample = createDevelopmentSampleRoute()
    private val point = GeoPoint(35.681234567, 139.767123456)
    private val plan = RoutePlan("plan", points = listOf(RoutePlanPoint("one", RoutePlanPointType.VIA, point)))

    @Test fun entryPrefersActiveGeometryThenCandidateThenPlan() {
        val candidate = ScheduledRoute("candidate", "candidate", RouteGeometry(listOf(point, GeoPoint(35.8,139.8))), sample.points)
        assertEquals(sample.geometry.points, editorEntryPoints(sample, candidate, plan))
        assertEquals(candidate.geometry.points, editorEntryPoints(null, candidate, plan))
        assertEquals(listOf(point), editorEntryPoints(null, null, plan))
        assertTrue(editorEntryPoints(null, null, RoutePlan("empty")).isEmpty())
    }
    @Test fun emptyEntryPreservesCameraAndDoesNotRequestReset() {
        val holder = RoutePlanEditorStateHolder()
        val camera = EditorCamera(point, 17.4, 43.0, 20.0)
        holder.saveCamera(camera)
        holder.enterEditor(null, null)
        assertSame(camera, holder.camera)
        assertNull(holder.uiState.value.cameraRequest)
    }
    @Test fun cursorRegistrationKeepsExactCoordinatesAndSelectedType() {
        RoutePlanPointType.entries.forEach { type ->
            val holder = RoutePlanEditorStateHolder()
            holder.selectAddMode(type)
            holder.registerCursor(point)
            assertEquals(point, holder.uiState.value.currentPlan.points.single().position)
            assertEquals(type, holder.uiState.value.currentPlan.points.single().type)
            assertNull(holder.uiState.value.cameraRequest)
        }
    }
    @Test fun sheetAndSelectionDoNotIssueCameraRequestsOrEditPlan() {
        val holder = RoutePlanEditorStateHolder(RoutePlanUiState(currentPlan = plan))
        holder.enterEditor(sample, null)
        val request = holder.uiState.value.cameraRequest!!
        holder.cameraApplied(request.id)
        EditorSheetState.entries.forEach { level ->
            holder.setSheetState(level)
            assertEquals(level, holder.uiState.value.sheetState)
            assertNull(holder.uiState.value.cameraRequest)
            assertEquals(plan, holder.uiState.value.currentPlan)
            assertEquals(0L, holder.uiState.value.revision)
        }
    }
    @Test fun requestsAreAcknowledgedOnceAndCandidateIsNotRefittedOnRecreation() {
        val holder = RoutePlanEditorStateHolder()
        holder.onCandidateCalculated(sample)
        val first = holder.uiState.value.cameraRequest!!
        holder.cameraApplied(first.id + 1)
        assertEquals(first, holder.uiState.value.cameraRequest)
        holder.cameraApplied(first.id)
        holder.onCandidateCalculated(sample)
        assertNull(holder.uiState.value.cameraRequest)
        holder.onCandidateCalculated(null)
        holder.onCandidateCalculated(sample)
        assertTrue(holder.uiState.value.cameraRequest!!.id > first.id)
    }
    @Test fun labelsAreUserFacingJapanese() {
        assertEquals(listOf("出発地", "目的地", "経由地", "通過指定"),
            listOf(RoutePlanPointType.START, RoutePlanPointType.DESTINATION, RoutePlanPointType.VIA, RoutePlanPointType.SHAPING).map { it.displayName })
        assertEquals("この付近を通るよう経路を調整", SHAPING_HELPER)
    }
}
