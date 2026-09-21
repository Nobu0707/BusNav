package net.nobu0707.busnav.prescribed

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import net.nobu0707.busnav.data.storage.prescribed.*
import net.nobu0707.busnav.domain.prescribed.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.domain.route.ScheduledRouteRepository
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.ui.navigation.*
import net.nobu0707.busnav.ui.prescribed.*
import net.nobu0707.busnav.ui.routing.RouteCalculationStateHolder
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrescribedRouteTest {
    @Test fun freeCalculationStartAndEndNeverWriteSavedLibrary() = runTest {
        val record = prescribedFixture()
        val repository = FakeRepository(record)
        val positions = MutableStateFlow<LocationUpdate>(LocationUpdate.Position(LocationState(
            record.route.start.position, 5f, null, null, 0, 0)))
        val provider = object : LocationProvider {
            override fun updates() = positions
            override fun isLocationEnabled() = true
        }
        val nav = NavigationStateHolder(provider, object : ScheduledRouteRepository {
            override suspend fun getActiveRoute() = null
        }, backgroundScope, StandardTestDispatcher(testScheduler), { testScheduler.currentTime })
        val library = PrescribedRouteLibraryStateHolder(repository, backgroundScope, { nav.uiState.value.activePrescribedRouteId })
        val free = net.nobu0707.busnav.ui.free.FreeNavigationStateHolder(RoutingEngine {
            RoutingResult.Success(record.route, RoutingSummary(1000.0, 120.0))
        }, nav, backgroundScope, { testScheduler.currentTime })
        nav.setPermission(LocationPermissionState.Granted); runCurrent()
        val before = repository.records.toMap()
        free.beginSelection(); free.selectDestination(record.route.destination.position); free.calculate(); runCurrent()
        assertEquals(0, repository.writes)
        assertTrue(free.start()); runCurrent()
        free.endNavigation(); runCurrent()
        assertEquals(before, repository.records)
        assertEquals(0, repository.writes)
        assertEquals(1, library.state.value.routes.size)
    }

    private fun roundtrip(r: PrescribedRouteRecord) = PrescribedRouteCodec.record(r.id, r.name, r.description,
        r.createdAtEpochMillis, r.updatedAtEpochMillis, PrescribedRouteCodec.decode(PrescribedRouteCodec.encode(r)))
    @Test fun largePayloadRoundtripPreservesEveryFieldExactly() {
        val source = prescribedFixture()
        val restored = roundtrip(source)
        assertEquals(source, restored)
        assertEquals(4001, restored.route.geometry.points.size)
        assertEquals(100, restored.route.guidance!!.maneuvers.size)
        assertEquals(source.vehicleProfile, restored.vehicleProfile)
        assertEquals(source.route.guidance, restored.route.guidance)
    }
    @Test fun nullableGuidanceMetadataAndAxleLoadRoundtrip() {
        val r = prescribedFixture(size = 2)
        val route = r.route.let { net.nobu0707.busnav.domain.route.ScheduledRoute(it.id, it.name, it.geometry, it.points) }
        val source = r.copy(route = route, description = null, vehicleProfile = r.vehicleProfile.copy(axleLoadMetricTons = null))
        assertEquals(source, roundtrip(source))
    }
    @Test fun canonicalRecordNameDoesNotAlterPayload() {
        val r = prescribedFixture()
        val renamed = r.copy(name = "別の表示名")
        assertEquals(PrescribedRouteCodec.encode(r), PrescribedRouteCodec.encode(renamed))
        assertEquals(renamed, roundtrip(renamed))
    }
    @Test fun invalidSaveRejectsBlankName() { assertThrows(IllegalArgumentException::class.java) { prescribedFixture().copy(name = " ").validate() } }
    @Test fun invalidSaveRejectsChangedPlan() {
        val r = prescribedFixture()
        assertThrows(IllegalArgumentException::class.java) {
            r.copy(routePlan = RoutePlanOperations().removePoint(r.routePlan, "via")).validate()
        }
    }
    @Test fun invalidSaveRejectsMissingDestination() {
        val r = prescribedFixture()
        assertThrows(IllegalArgumentException::class.java) { r.copy(routePlan = r.routePlan.copy(points = r.routePlan.points.dropLast(1))).validate() }
    }
    @Test fun invalidSaveRejectsInfiniteVehicleDimension() {
        assertThrows(IllegalArgumentException::class.java) {
            val r = prescribedFixture()
            r.copy(vehicleProfile = r.vehicleProfile.copy(heightMeters = Double.POSITIVE_INFINITY)).validate()
        }
    }
    @Test fun malformedJsonRejected() { assertThrows(IllegalArgumentException::class.java) { PrescribedRouteCodec.decode("{bad") } }
    @Test fun unknownPayloadSchemaRejected() {
        val text = PrescribedRouteCodec.encode(prescribedFixture()).replace("\"schemaVersion\":1", "\"schemaVersion\":999")
        assertThrows(IllegalArgumentException::class.java) { PrescribedRouteCodec.decode(text) }
    }
    @Test fun unknownEnumRejectedDuringMapping() {
        val r = prescribedFixture()
        val p = PrescribedRouteCodec.payload(r)
        assertThrows(IllegalArgumentException::class.java) {
            PrescribedRouteCodec.record(r.id,r.name,null,1000,2000,p.copy(plan = p.plan.copy(points =
                p.plan.points.map { it.copy(type = "FUTURE_TYPE") })))
        }
    }
    @Test fun openRenameDuplicateDeleteAndEditDoNotCallRoutingEngine() = runTest {
        val r = prescribedFixture()
        val repo = FakeRepository(r)
        var calls = 0
        val calculation = RouteCalculationStateHolder(RoutingEngine { request ->
            calls++
            assertEquals(r.vehicleProfile, request.vehicleProfile)
            RoutingResult.Success(r.route, RoutingSummary(1.0,1.0))
        }, backgroundScope)
        val h = PrescribedRouteLibraryStateHolder(repo, backgroundScope, { null }, newId = { "copy" })
        h.open(r.id) { assertEquals(r, it) }; runCurrent()
        h.rename(r.id, "renamed"); runCurrent()
        h.duplicate(r.id); runCurrent()
        h.delete("copy"); runCurrent()
        h.edit(r.id) {}; runCurrent()
        assertEquals(0, calls)
        calculation.calculate(r.routePlan, 1, r.vehicleProfile); runCurrent()
        assertEquals(1, calls)
    }
    @Test fun draftCancelDoesNotWriteOriginal() = runTest {
        val r = prescribedFixture(); val repo = FakeRepository(r)
        val h = PrescribedRouteLibraryStateHolder(repo, backgroundScope, { r.id })
        h.edit(r.id) {}; runCurrent()
        val plan = RoutePlanOperations().removePoint(r.routePlan, "via")
        assertFalse(h.canSaveDraft(plan))
        h.cancelDraft()
        assertNull(h.state.value.draft)
        assertEquals(r, repo.records[r.id])
        assertEquals(0, repo.writes)
    }
    @Test fun mismatchedCandidateDoesNotReplaceCurrentSnapshot() = runTest {
        val r = prescribedFixture()
        val h = PrescribedRouteLibraryStateHolder(FakeRepository(r), backgroundScope, { r.id })
        h.open(r.id) {}; runCurrent()
        assertFalse(h.acceptCandidate(r.routePlan.copy(points = r.routePlan.points.drop(1)), r.route, r.vehicleProfile))
        assertEquals(r, h.state.value.current)
        assertNotNull(h.state.value.error)
    }
    @Test fun changedDraftCannotSaveOldGeometry() = runTest {
        val r = prescribedFixture(); val repo = FakeRepository(r)
        val h = PrescribedRouteLibraryStateHolder(repo, backgroundScope, { r.id })
        h.edit(r.id) {}; runCurrent()
        h.save("new", null, false, r.routePlan.copy(points = r.routePlan.points.drop(1))) { fail() }; runCurrent()
        assertNotNull(h.state.value.error)
        assertEquals(r, repo.records[r.id])
    }
    @Test fun draftOverwriteRetainsIdentityAndCreationTime() = runTest {
        val r = prescribedFixture(); val repo = FakeRepository(r)
        val h = PrescribedRouteLibraryStateHolder(repo, backgroundScope, { r.id }, { 3000 })
        h.edit(r.id) {}; runCurrent()
        h.acceptCandidate(r.routePlan, r.route, r.vehicleProfile)
        h.save("edited", "new desc", false, r.routePlan) {}; runCurrent()
        val updated = repo.records.getValue(r.id)
        assertEquals(r.createdAtEpochMillis, updated.createdAtEpochMillis)
        assertEquals(3000, updated.updatedAtEpochMillis)
        assertEquals("edited", updated.name)
        assertNull(h.state.value.draft)
    }
    @Test fun saveAsAndDuplicateHaveNewIdentityAndPreserveOriginal() = runTest {
        val r = prescribedFixture(); val repo = FakeRepository(r)
        var next = 0
        val h = PrescribedRouteLibraryStateHolder(repo, backgroundScope, { r.id }, newId = { "new-${next++}" })
        h.edit(r.id) {}; runCurrent()
        h.save(r.name, r.description, true, r.routePlan) {}; runCurrent()
        h.duplicate(r.id); runCurrent()
        assertEquals(3, repo.records.size)
        assertEquals(r, repo.records[r.id])
        assertTrue(repo.records.values.all { it.route == r.route })
    }
    @Test fun activeRouteCannotBeDeleted() = runTest {
        val r = prescribedFixture(); val repo = FakeRepository(r)
        val h = PrescribedRouteLibraryStateHolder(repo, backgroundScope, { r.id })
        h.delete(r.id); runCurrent()
        assertNotNull(h.state.value.error)
        assertEquals(r, repo.records[r.id])
    }
    @Test fun deletedDraftIsNotResurrectedByOverwrite() = runTest {
        val r = prescribedFixture(); val repo = FakeRepository(r)
        val h = PrescribedRouteLibraryStateHolder(repo, backgroundScope, { null })
        h.edit(r.id) {}; runCurrent()
        repo.delete(r.id); runCurrent()
        h.save("edited", null, false, r.routePlan) { fail() }; runCurrent()
        assertNotNull(h.state.value.error)
        assertTrue(repo.records.isEmpty())
    }
    @Test fun missingAndCorruptAndUnsupportedLoadsAreUserErrors() = runTest {
        val repo = FakeRepository()
        val h = PrescribedRouteLibraryStateHolder(repo, backgroundScope, { null })
        for (result in listOf(PrescribedRouteLoad.Missing, PrescribedRouteLoad.Corrupt, PrescribedRouteLoad.Unsupported)) {
            repo.loadResult = result
            h.open("missing") { fail() }; runCurrent()
            assertNotNull(h.state.value.error)
        }
    }
    @Test fun switchingSavedRoutesResetsDeviationAndPreservesStableId() = runTest {
        val provider = object : LocationProvider {
            override fun updates(): Flow<LocationUpdate> = emptyFlow()
            override fun isLocationEnabled() = true
        }
        val nav = NavigationStateHolder(provider, object : ScheduledRouteRepository {
            override suspend fun getActiveRoute() = null
        }, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        val a = prescribedFixture("a")
        val b = prescribedFixture("b", size = 200)
        nav.openPrescribedRoute(a); runCurrent()
        nav.startNavigation()
        assertFalse(nav.openPrescribedRoute(b))
        assertEquals("a", nav.uiState.value.activePrescribedRouteId)
        nav.clearRoute()
        nav.openPrescribedRoute(b); runCurrent()
        assertEquals("b", nav.uiState.value.activePrescribedRouteId)
        assertEquals(NavigationMode.PRESCRIBED, nav.uiState.value.navigationMode)
        assertEquals(b.route, nav.uiState.value.activeRoute)
        assertFalse(nav.uiState.value.isNavigationStarted)
        assertNull(nav.uiState.value.highwayGuidance)
        assertEquals(net.nobu0707.busnav.domain.navigation.RouteMatchQuality.UNRELIABLE, nav.uiState.value.deviationSnapshot.matchQuality)
        nav.clearRoute()
        assertNull(nav.uiState.value.activePrescribedRouteId)
    }
}

private class FakeRepository(vararg initial: PrescribedRouteRecord) : PrescribedRouteRepository {
    val records = initial.associateBy { it.id }.toMutableMap()
    var writes = 0
    var loadResult: PrescribedRouteLoad? = null
    private val flow = MutableStateFlow<List<PrescribedRouteSummary>>(emptyList())
    init { emit() }
    private fun emit() { flow.value = records.values.sortedByDescending { it.updatedAtEpochMillis }.map {
        PrescribedRouteSummary(it.id,it.name,it.description,it.route.metadata.distanceMeters,it.route.start.name,it.route.destination.name,it.updatedAtEpochMillis,1)
    } }
    override fun observeAll() = flow
    override suspend fun getById(id: String) = loadResult ?: records[id]?.let { PrescribedRouteLoad.Found(it) } ?: PrescribedRouteLoad.Missing
    override suspend fun save(record: PrescribedRouteRecord, existingOnly: Boolean) {
        record.validate()
        check(!existingOnly || records.containsKey(record.id))
        writes++; records[record.id] = record; emit()
    }
    override suspend fun rename(id: String, name: String) { records[id]?.let { records[id] = it.copy(name = name) }; emit() }
    override suspend fun delete(id: String) { records.remove(id); emit() }
}