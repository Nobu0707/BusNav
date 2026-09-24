package net.nobu0707.busnav.ui.free

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.prescribed.NavigationMode
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.prescribed.prescribedFixture
import net.nobu0707.busnav.ui.navigation.*
import net.nobu0707.busnav.ui.routing.*
import net.nobu0707.busnav.ui.theme.ThemeModeResolver
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FreeNavigationTest {
    private val origin = GeoPoint(35.68, 139.76)
    private val destination = GeoPoint(35.68, 139.77)
    private fun fix(time: Long = 1000, point: GeoPoint = origin, accuracy: Float? = 5f) =
        LocationState(point, accuracy, 90f, 0f, Long.MAX_VALUE, time)

    private class Provider : LocationProvider {
        val flow = MutableSharedFlow<LocationUpdate>(extraBufferCapacity = 10)
        override fun updates() = flow
        override fun isLocationEnabled() = true
    }

    private inner class Harness(val scope: TestScope, profile: VehicleProfile = VehicleProfile.DEVELOPMENT_LARGE_BUS,
        engineOverride: RoutingEngine? = null) {
        val provider = Provider()
        val requests = mutableListOf<RoutingRequest>()
        var failure = false
        val nav = NavigationStateHolder(provider, object : ScheduledRouteRepository { override suspend fun getActiveRoute(): ScheduledRoute? = null },
            scope.backgroundScope, StandardTestDispatcher(scope.testScheduler), { scope.testScheduler.currentTime })
        val free = FreeNavigationStateHolder(engineOverride ?: RoutingEngine { request ->
            requests += request
            if (failure) RoutingResult.Failure(RoutingFailure.NETWORK) else {
                val geometry = RouteGeometry(listOf(request.origin, request.destination))
                RoutingResult.Success(ScheduledRoute(request.routePlanId, "公開テスト経路", geometry,
                    request.points.map { RoutePoint(it.id, RoutePointType.valueOf(it.type.name), it.position) },
                    guidance = RouteGuidance(listOf(RouteManeuver(0, ManeuverType.RIGHT, "", 1, 1)))),
                    RoutingSummary(900.0, 120.0))
            }
        }, nav, scope.backgroundScope, profile = profile)

        fun ready() {
            nav.setPermission(LocationPermissionState.Granted)
            scope.runCurrent()
            send()
        }
        fun send(point: GeoPoint = origin, accuracy: Float? = 5f, dt: Long = 1000) {
            scope.advanceTimeBy(dt)
            provider.flow.tryEmit(LocationUpdate.Position(fix(scope.testScheduler.currentTime, point, accuracy)))
            scope.runCurrent()
        }
        fun preview() {
            assertTrue(free.beginSelection())
            free.selectDestination(destination, "公共目的地")
            assertTrue(free.calculate())
            scope.runCurrent()
            assertEquals(FreeNavigationStage.PREVIEW, free.state.value.stage)
        }
        fun started() { ready(); preview(); assertTrue(free.start()); scope.runCurrent() }
    }

    @Test fun selectionAndCursorDestinationRemainSeparateFromEditor() = runTest {
        val h = Harness(this); h.ready()
        assertTrue(h.free.beginSelection())
        assertNull(h.free.state.value.plan)
        h.free.selectDestination(destination, "公共地点")
        assertEquals(destination, h.free.state.value.plan!!.destination)
        assertFalse(h.nav.uiState.value.isNavigationStarted)
        assertEquals(0, h.requests.size)
    }
    @Test fun previewDoesNotStartGuidanceOrNightTheme() = runTest {
        val h = Harness(this); h.ready(); h.preview()
        val state = h.nav.uiState.value
        assertEquals(NavigationMode.FREE, state.navigationMode)
        assertNull(state.activePrescribedRouteId)
        assertFalse(state.navigationActive)
        assertEquals(GuidanceStatus.NO_ROUTE, state.guidance.status)
        assertFalse(ThemeModeResolver.isDark(state.navigationActive, true, true))
        assertEquals(1, h.requests.size)
    }
    @Test fun explicitStartEnablesGuidanceAndNightThemeWithoutRequest() = runTest {
        val h = Harness(this); h.started()
        assertTrue(h.nav.uiState.value.navigationActive)
        assertTrue(ThemeModeResolver.isDark(h.nav.uiState.value.navigationActive, true, false))
        assertEquals(GuidanceStatus.RELIABLE, h.nav.uiState.value.guidance.status)
        assertEquals(1, h.requests.size)
    }
    @Test fun rawGpsStartAndVehicleProfileArePassedExactly() = runTest {
        val profile = prescribedFixture().vehicleProfile
        val h = Harness(this, profile); h.ready(); h.preview()
        assertEquals(origin, h.requests.single().origin)
        assertEquals(destination, h.requests.single().destination)
        assertEquals(profile, h.requests.single().vehicleProfile)
        assertTrue(h.requests.single().routePlanId.startsWith("free-"))
        assertEquals(listOf(RoutePlanPointType.START, RoutePlanPointType.DESTINATION), h.requests.single().points.map { it.type })
    }
    @Test fun noPermissionCannotRoute() = runTest {
        val h = Harness(this); runCurrent(); h.free.beginSelection(); h.free.selectDestination(destination)
        assertFalse(h.free.calculate())
        assertEquals("位置情報の利用を許可してください", h.free.state.value.error)
        assertTrue(h.requests.isEmpty())
    }
    @Test fun noLocationCannotRoute() = runTest {
        val h = Harness(this); h.nav.setPermission(LocationPermissionState.Granted); runCurrent()
        h.free.beginSelection(); h.free.selectDestination(destination)
        assertFalse(h.free.calculate()); assertEquals("現在地を取得中です", h.free.state.value.error)
    }
    @Test fun disabledLocationCannotRouteEvenWithCachedFix() = runTest {
        val h = Harness(this); h.ready()
        h.provider.flow.tryEmit(LocationUpdate.Disabled); runCurrent()
        h.free.beginSelection(); h.free.selectDestination(destination)
        assertFalse(h.free.calculate()); assertEquals("端末の位置情報を有効にしてください", h.free.state.value.error)
    }
    @Test fun staleLocationCannotRoute() = runTest {
        val h = Harness(this); h.ready(); advanceTimeBy(15001); runCurrent()
        h.free.beginSelection(); h.free.selectDestination(destination)
        assertFalse(h.free.calculate()); assertEquals("現在地を更新中です", h.free.state.value.error)
    }
    @Test fun poorAccuracyCannotRoute() = runTest {
        val h = Harness(this); h.ready(); h.send(accuracy = 160f, dt = 16000)
        h.free.beginSelection(); h.free.selectDestination(destination)
        assertFalse(h.free.calculate()); assertEquals("現在地の精度が不足しています", h.free.state.value.error)
    }
    @Test fun degradedFreeFixStartsAndGuidanceRecovers() = runTest {
        val h = Harness(this)
        h.nav.setPermission(LocationPermissionState.Granted); runCurrent()
        h.send(accuracy = 120f)
        assertEquals(LocationQuality.DEGRADED, h.nav.uiState.value.startLocationQuality)
        assertTrue(h.nav.uiState.value.startLocationAllowed)
        h.preview()
        assertTrue(h.free.start()); runCurrent()
        assertEquals(NavigationMode.FREE, h.nav.uiState.value.navigationMode)
        assertTrue(h.nav.uiState.value.isNavigationStarted)
        assertEquals(GuidanceStatus.UNCERTAIN, h.nav.uiState.value.guidance.status)
        assertEquals(RouteMatchQuality.UNRELIABLE, h.nav.uiState.value.deviationSnapshot.matchQuality)
        assertNotEquals(RouteDeviationState.OFF_ROUTE, h.nav.uiState.value.deviationSnapshot.state)
        h.send(accuracy = 20f)
        assertEquals(GuidanceStatus.RELIABLE, h.nav.uiState.value.guidance.status)
    }
    @Test fun prescribedUsesSameStartGateAndRecovers() = runTest {
        val h = Harness(this)
        val record = prescribedFixture()
        h.nav.setPermission(LocationPermissionState.Granted); runCurrent()
        assertTrue(h.nav.openPrescribedRoute(record))
        assertFalse(h.nav.startNavigation())
        h.send(point = record.route.geometry.first, accuracy = 120f)
        assertTrue(h.nav.startNavigation()); runCurrent()
        assertEquals(NavigationMode.PRESCRIBED, h.nav.uiState.value.navigationMode)
        assertEquals(GuidanceStatus.UNCERTAIN, h.nav.uiState.value.guidance.status)
        h.send(point = record.route.geometry.first, accuracy = 20f)
        assertEquals(GuidanceStatus.RELIABLE, h.nav.uiState.value.guidance.status)
    }
    @Test fun approximatePermissionExplainsWhyStartIsUnavailable() = runTest {
        val h = Harness(this)
        h.nav.setPermission(LocationPermissionState.Approximate); runCurrent()
        h.send(accuracy = 20f)
        h.free.beginSelection(); h.free.selectDestination(destination)
        assertFalse(h.free.calculate())
        assertEquals("正確な位置情報を許可してください", h.free.state.value.error)
        assertFalse(h.nav.uiState.value.startLocationAllowed)
    }
    @Test fun oneBadFixKeepsRecentRawStartButMarkerShowsNewest() = runTest {
        val h = Harness(this); h.ready()
        val badPoint = GeoPoint(35.681, 139.76)
        h.send(point = badPoint, accuracy = 180f)
        assertEquals(badPoint, h.nav.uiState.value.location!!.point)
        assertTrue(h.nav.uiState.value.startLocationAllowed)
        h.preview()
        assertEquals(origin, h.requests.single().origin)
    }
    @Test fun noGuidanceRouteCanStartWithExplicitNoGuidanceState() = runTest {
        val sample = prescribedFixture().route
        val route = ScheduledRoute("free-no-guidance", "案内なし", sample.geometry, sample.points)
        val h = Harness(this, engineOverride = RoutingEngine { RoutingResult.Success(route, RoutingSummary(1.0, 1.0)) })
        h.ready(); h.preview(); assertTrue(h.free.start()); runCurrent()
        h.send(route.geometry.first)
        assertEquals(GuidanceStatus.NO_GUIDANCE, h.nav.uiState.value.guidance.status)
        assertTrue(h.nav.uiState.value.navigationActive)
    }
    @Test fun arrivedSessionRetainsRouteUntilExplicitEnd() = runTest {
        val h = Harness(this); h.started(); val route = h.nav.uiState.value.activeRoute
        repeat(8) { h.send(destination) }
        assertEquals(ArrivalState.ARRIVED, h.nav.uiState.value.arrival.state)
        assertSame(route, h.nav.uiState.value.activeRoute)
        assertTrue(h.nav.uiState.value.isNavigationStarted)
        assertEquals(1, h.requests.size)
        h.free.endNavigation(); assertNull(h.nav.uiState.value.activeRoute)
    }

    @Test fun qualityUsesMonotonicTimeAndInclusiveBoundaries() {
        val config = FreeNavigationConfig()
        assertNull(config.locationProblem(fix(1000, accuracy = 50f), 11000))
        assertNotNull(config.locationProblem(fix(1000), 11001))
        assertNotNull(config.locationProblem(fix(12000), 11000))
        assertNotNull(config.locationProblem(fix(-1), 0))
        assertNotNull(config.locationProblem(fix().copy(elapsedRealtimeMillis = null), 1000))
        for (accuracy in listOf(null, Float.NaN, -1f, Float.POSITIVE_INFINITY, 50.1f))
            assertNotNull(config.locationProblem(fix(accuracy = accuracy), 1000))
    }
    @Test fun offRouteMakesZeroRequestsManualRecalculationMakesExactlyOne() = runTest {
        val h = Harness(this); h.started()
        repeat(4) { h.send(GeoPoint(35.681,139.76), dt = 1500) }
        assertEquals(RouteDeviationState.OFF_ROUTE, h.nav.uiState.value.deviationSnapshot.state)
        assertEquals(1, h.requests.size)
        val old = h.nav.uiState.value.activeRoute
        assertTrue(h.free.recalculate()); assertFalse(h.free.recalculate()); runCurrent()
        assertEquals(2, h.requests.size)
        assertSame(old, h.nav.uiState.value.activeRoute)
        assertEquals(h.nav.uiState.value.location!!.point, h.requests.last().origin)
        assertNotEquals(old!!.start.position, h.requests.last().origin)
        assertEquals(destination, h.requests.last().destination)
        assertTrue(h.free.start()); runCurrent()
        assertEquals(2, h.requests.size)
        assertNotSame(old, h.nav.uiState.value.activeRoute)
        assertNull(h.nav.uiState.value.activePrescribedRouteId)
    }
    @Test fun failedRecalculationKeepsOldNavigation() = runTest {
        val h = Harness(this); h.started(); val old = h.nav.uiState.value.activeRoute
        h.failure = true; assertTrue(h.free.recalculate()); runCurrent()
        assertSame(old, h.nav.uiState.value.activeRoute)
        assertTrue(h.nav.uiState.value.isNavigationStarted)
        assertNotNull(h.free.state.value.error)
        h.free.cancel(); assertSame(old, h.nav.uiState.value.activeRoute)
    }
    @Test fun cancelledRecalculationPreviewKeepsOldRoute() = runTest {
        val h = Harness(this); h.started(); val old = h.nav.uiState.value.activeRoute
        h.free.recalculate(); runCurrent(); h.free.cancel()
        assertSame(old, h.nav.uiState.value.activeRoute); assertTrue(h.nav.uiState.value.isNavigationStarted)
        assertEquals(2, h.requests.size)
    }
    @Test fun destinationChangeClearsPreviewAndDoesNotStart() = runTest {
        val h = Harness(this); h.ready(); h.preview(); h.free.changeDestination()
        assertNull(h.nav.uiState.value.activeRoute)
        h.free.selectDestination(GeoPoint(35.69,139.78))
        assertNull(h.free.state.value.previewRoute)
        assertFalse(h.free.start())
        assertEquals(1, h.requests.size)
    }
    @Test fun cancelPendingIgnoresCancellationInsensitiveResult() = runTest {
        val response = CompletableDeferred<RoutingResult>()
        val h = Harness(this, engineOverride = RoutingEngine { withContext(NonCancellable) { response.await() } })
        h.ready(); h.free.beginSelection(); h.free.selectDestination(destination); h.free.calculate(); runCurrent()
        h.free.cancel()
        response.complete(RoutingResult.Success(prescribedFixture().route, RoutingSummary(1.0, 1.0))); runCurrent()
        assertEquals(FreeNavigationStage.IDLE, h.free.state.value.stage)
        assertNull(h.nav.uiState.value.activeRoute)
    }
    @Test fun cancelAndRestartSameRevisionCannotPublishOldCalculation() = runTest {
        val pending = mutableListOf<CompletableDeferred<RoutingResult>>()
        val calc = RouteCalculationStateHolder(RoutingEngine {
            val result = CompletableDeferred<RoutingResult>(); pending += result
            withContext(NonCancellable) { result.await() }
        }, backgroundScope)
        val plan = FreeNavigationPlan(destination).toRoutePlan(origin, "test")
        calc.calculate(plan, 0); runCurrent(); calc.cancel(); calc.calculate(plan, 0); runCurrent()
        pending[0].complete(RoutingResult.Failure(RoutingFailure.NETWORK)); runCurrent()
        assertTrue(calc.state.value is RouteCalculationState.Calculating)
        pending[1].complete(RoutingResult.Success(prescribedFixture().route, RoutingSummary(1.0, 1.0))); runCurrent()
        assertTrue(calc.state.value is RouteCalculationState.Success)
    }
    @Test fun activeFreeCannotBeSilentlyReplacedByPrescribedOrNewSelection() = runTest {
        val h = Harness(this); h.started(); val old = h.nav.uiState.value.activeRoute
        assertFalse(h.nav.openPrescribedRoute(prescribedFixture()))
        assertFalse(h.free.beginSelection())
        assertSame(old, h.nav.uiState.value.activeRoute)
        h.free.endNavigation(); assertTrue(h.nav.openPrescribedRoute(prescribedFixture()))
        assertEquals(NavigationMode.PRESCRIBED, h.nav.uiState.value.navigationMode)
        assertFalse(h.nav.uiState.value.isNavigationStarted)
    }
    @Test fun activePrescribedRequiresEndBeforeFreeAndNeverResurrects() = runTest {
        val h = Harness(this); h.ready()
        h.nav.openPrescribedRoute(prescribedFixture()); h.nav.startNavigation()
        assertFalse(h.free.beginSelection())
        h.free.endNavigation(); h.preview(); h.free.start(); h.free.endNavigation()
        assertNull(h.nav.uiState.value.activeRoute); assertNull(h.nav.uiState.value.activePrescribedRouteId)
        assertNull(h.nav.uiState.value.freePlan); assertFalse(h.nav.uiState.value.isNavigationStarted)
        assertEquals(RouteDeviationState.UNKNOWN, h.nav.uiState.value.deviationSnapshot.state)
    }
    @Test fun freeAndPrescribedWordingUseMode() {
        for (mode in NavigationMode.entries) {
            val label = if (mode == NavigationMode.FREE) "案内経路" else "所定経路"
            for (state in listOf(RouteDeviationState.SUSPECTED_OFF_ROUTE, RouteDeviationState.OFF_ROUTE, RouteDeviationState.RECOVERING)) {
                val text = deviationUiState(RouteDeviationSnapshot(state = state), mode).message!!
                assertTrue(text.startsWith(label))
            }
            val nav = NavigationUiState(activeRoute = prescribedFixture().route, navigationMode = mode)
            assertTrue(operationsSummary(nav.copy(isRouteLoading = false)).startsWith(label))
        }
    }
    @Test fun endClearsMatcherDestinationAndArrivalAndDoesNotRoute() = runTest {
        val h = Harness(this); h.started(); h.free.endNavigation(); runCurrent()
        val state = h.nav.uiState.value
        assertNull(state.activeRoute); assertNull(state.freePlan); assertNull(state.highwayGuidance)
        assertEquals(GuidanceStatus.NO_ROUTE, state.guidance.status)
        assertEquals(ArrivalState.EN_ROUTE, state.arrival.state)
        assertEquals(1, h.requests.size)
    }
    @Test fun arrivalRequiresTwoDistinctReliableFixesAndRemainsUntilEnd() {
        val detector = ArrivalDetector()
        val first = detector.update(ArrivalSnapshot(), fix(1000, destination), destination, 20.0, true, 1000)
        assertEquals(ArrivalState.APPROACHING, first.state)
        assertEquals(first, detector.update(first, fix(1000, destination), destination, 0.0, true, 1000))
        val arrived = detector.update(first, fix(2000, destination), destination, 0.0, true, 2000)
        assertEquals(ArrivalState.ARRIVED, arrived.state)
        assertEquals(arrived, detector.update(arrived, fix(3000, origin), destination, 900.0, false, 3000))
    }
    @Test fun gpsSpikePoorAccuracyAndUnreliableProgressCannotArrive() {
        val detector = ArrivalDetector()
        var state = detector.update(ArrivalSnapshot(), fix(1000, destination), destination, 0.0, true, 1000)
        state = detector.update(state, fix(2000, origin), destination, 800.0, true, 2000)
        assertEquals(0, state.consecutiveFixes)
        state = detector.update(state, fix(3000, destination), destination, 0.0, true, 3000)
        assertNotEquals(ArrivalState.ARRIVED, state.state)
        for ((accuracy, reliable, remaining) in listOf(Triple(80f,true,0.0), Triple(5f,false,0.0), Triple(5f,true,500.0))) {
            val next = detector.update(state, fix(4000, destination, accuracy), destination, remaining, reliable, 4000)
            assertEquals(0, next.consecutiveFixes)
        }
    }
    @Test fun staleOrSeparatedArrivalEvidenceDoesNotAccumulate() {
        val detector = ArrivalDetector()
        val first = detector.update(ArrivalSnapshot(), fix(1000, destination), destination, 0.0, true, 1000)
        assertEquals(1, detector.update(first, fix(12000, destination), destination, 0.0, true, 12000).consecutiveFixes)
        assertEquals(0, detector.update(first, fix(2000, destination), destination, 0.0, true, 20000).consecutiveFixes)
    }
    @Test fun offRoadFreeRouteUsesOneRequestAndKeepsRawMarker() = runTest {
        var requests = 0
        val h = Harness(this, engineOverride = RoutingEngine { request ->
            requests++
            val snapped = GeoPoint(request.origin.latitude + 80.0 / 111_195.1, request.origin.longitude)
            RoutingResult.Success(ScheduledRoute("snapped", "snapped",
                RouteGeometry(listOf(snapped, request.destination)),
                listOf(RoutePoint("start", RoutePointType.START, request.origin),
                    RoutePoint("end", RoutePointType.DESTINATION, request.destination))),
                RoutingSummary(1000.0, 120.0))
        })
        h.ready(); h.preview()
        assertEquals(1, requests)
        assertEquals(origin, h.nav.uiState.value.location!!.point)
        val start = h.free.state.value.startPosition!!
        assertEquals(origin, start.rawLocation)
        assertEquals(NavigationStartSource.ROUTING_SNAPPED, start.source)
        assertEquals(80.0, start.snapDistanceMeters, 0.2)
        assertEquals(start, h.nav.uiState.value.freeStartPosition)
        assertTrue(h.free.start())
        assertEquals(origin, h.nav.uiState.value.location!!.point)
    }

    @Test fun distantFreeSnapIsBlockedWithRoadMessage() = runTest {
        var requests = 0
        val h = Harness(this, engineOverride = RoutingEngine { request ->
            requests++
            val distant = GeoPoint(request.origin.latitude + 350.0 / 111_195.1, request.origin.longitude)
            RoutingResult.Success(ScheduledRoute("distant", "distant",
                RouteGeometry(listOf(distant, request.destination)),
                listOf(RoutePoint("start", RoutePointType.START, request.origin),
                    RoutePoint("end", RoutePointType.DESTINATION, request.destination))),
                RoutingSummary(1000.0, 120.0))
        })
        h.ready(); h.free.beginSelection(); h.free.selectDestination(destination)
        assertTrue(h.free.calculate()); runCurrent()
        assertEquals(1, requests)
        assertEquals(FreeNavigationStage.SELECTING, h.free.state.value.stage)
        assertEquals("走行可能な道路を確認できません", h.free.state.value.error)
        assertNull(h.nav.uiState.value.activeRoute)
    }

    @Test fun prescribedStartsOffRouteWithoutInventingMatch() = runTest {
        val h = Harness(this)
        val record = prescribedFixture()
        h.nav.setPermission(LocationPermissionState.Granted); runCurrent()
        val offRoad = GeoPoint(record.route.geometry.first.latitude + 0.002,
            record.route.geometry.first.longitude)
        h.send(point = offRoad)
        assertTrue(h.nav.openPrescribedRoute(record))
        assertTrue(h.nav.startNavigation()); runCurrent()
        assertEquals(offRoad, h.nav.uiState.value.location!!.point)
        assertTrue(h.nav.uiState.value.navigationActive)
        assertNotEquals(RouteMatchQuality.MATCHED, h.nav.uiState.value.deviationSnapshot.matchQuality)
    }}
