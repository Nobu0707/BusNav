package net.nobu0707.busnav.traffic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import net.nobu0707.busnav.detour.*
import net.nobu0707.busnav.domain.detour.*
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.domain.traffic.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.ui.detour.*
import net.nobu0707.busnav.ui.navigation.*
import net.nobu0707.busnav.ui.traffic.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficIntegrationTest {
    private class Harness(val test: TestScope) {
        val source = TrafficSourceInfo("test", "開発用交通情報", false)
        val snapshot = MutableStateFlow(TrafficSnapshot(source, TrafficProviderStatus.AVAILABLE, receivedAtEpochMillis = 0))
        val fixes = MutableSharedFlow<LocationUpdate>(extraBufferCapacity = 8)
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        val nav = NavigationStateHolder(object : LocationProvider {
            override fun updates() = fixes
            override fun isLocationEnabled() = true
        }, object : ScheduledRouteRepository { override suspend fun getActiveRoute(): ScheduledRoute? = null },
            test.backgroundScope, dispatcher, { test.testScheduler.currentTime })
        val traffic = TrafficStateHolder(object : TrafficInformationProvider {
            override val source = this@Harness.source
            override fun observeTraffic() = snapshot
            override suspend fun refresh() = error("No automatic refresh")
        }, nav, test.backgroundScope, { test.testScheduler.currentTime }, dispatcher = dispatcher)
        var calls = 0
        val detour = DetourStateHolder(RoutingEngine { calls++; resultFor(it) }, nav, test.backgroundScope,
            { test.testScheduler.currentTime }, dispatcher = dispatcher, trafficSnapshot = traffic::currentSnapshot,
            epochMillis = { test.testScheduler.currentTime }, trafficUpdates = traffic.state)
        val saved = detourFixture(13)
        fun ready(free: Boolean = false) {
            nav.setPermission(LocationPermissionState.Granted); test.runCurrent()
            fix(100.0)
            if (free) { nav.previewFreeRoute(FreeNavigationPlan(saved.route.geometry.last), saved.route); nav.startFreeNavigation() }
            else { nav.openPrescribedRoute(saved); nav.startNavigation() }
            traffic.start(); test.runCurrent(); fix(100.0)
        }
        fun fix(meters: Double, offset: Double = 0.0) {
            test.advanceTimeBy(1000)
            fixes.tryEmit(LocationUpdate.Position(LocationState(point(meters, offset), 5f, 90f, 0f, 0, test.testScheduler.currentTime)))
            test.runCurrent()
        }
        fun event() = TrafficEvent("closure", TrafficEventKind.ROAD_CLOSURE, TrafficSeverity.CRITICAL, "× 合成通行止め",
            TrafficGeometry.Polyline(listOf(point(1500.0),point(2400.0))), source, direction = TrafficDirection.FORWARD)
        fun push(events: List<TrafficEvent> = listOf(event()), status: TrafficProviderStatus = TrafficProviderStatus.AVAILABLE) {
            snapshot.value = TrafficSnapshot(source, status, events, test.testScheduler.currentTime); test.runCurrent()
        }
        fun begin() { val impact = traffic.state.value.alert!!; assertTrue(detour.begin(impact.event.detourReason(), impact.detourContext())); test.runCurrent() }
        fun calculate() { detour.selectTarget(detour.state.value.candidates.first().id); assertTrue(detour.calculate()); test.runCurrent() }
    }
    @Test fun arrivalAlertPanelReadAndConsiderAreZeroCallsCalculateIsOnePreviewActivateZero() = runTest {
        val h = Harness(this); h.ready(); h.push()
        assertEquals(TrafficImpactPosition.AHEAD,h.traffic.state.value.alert!!.position)
        assertEquals(DetourSessionState.IDLE,h.detour.state.value.stage)
        assertEquals(0,h.calls)
        h.traffic.state.value.activeEvents; h.begin()
        assertTrue(h.detour.state.value.candidates.all { it.progressMeters > 2600 })
        h.detour.selectTarget(h.detour.state.value.candidates.first().id)
        assertEquals(0,h.calls)
        assertTrue(h.detour.calculate()); runCurrent(); assertEquals(1,h.calls)
        assertEquals(TrafficDetourConflict.BLOCKING_CONFLICT,h.detour.state.value.trafficValidation!!.conflict)
        assertFalse(h.detour.activate()); assertEquals(1,h.calls)
        assertSame(h.saved.route,h.nav.uiState.value.activeRoute)
    }
    @Test fun manualViaAllowsExplicitActivationThenRejoinAfterFloor() = runTest {
        val h=Harness(this);h.ready();h.push();h.begin();h.calculate()
        assertFalse(h.detour.activate())
        h.detour.edit()
        for (p in listOf(point(1000.0,200.0),point(2500.0,200.0))) {
            h.detour.selectMapMode(DetourMapMode.VIA);h.detour.setCursor(p)
        }
        assertTrue(h.detour.calculate());runCurrent()
        assertEquals(TrafficDetourConflict.NONE,h.detour.state.value.trafficValidation!!.conflict)
        assertEquals(2,h.calls);assertNull(h.nav.uiState.value.activeDetour)
        assertTrue(h.detour.activate());runCurrent()
        repeat(5){h.fix(2200.0)}
        assertNotNull(h.nav.uiState.value.activeDetour)
        repeat(5){h.fix(2900.0)}
        assertNull(h.nav.uiState.value.activeDetour);assertSame(h.saved.route,h.nav.uiState.value.activeRoute)
        assertEquals(2,h.calls)
    }
    @Test fun newClosureAfterPreviewIsCheckedAtActivationAndRemovalNeverAutoActivates() = runTest {
        val h=Harness(this);h.ready();assertTrue(h.detour.begin());runCurrent()
        h.calculate();assertEquals(1,h.calls)
        h.push(listOf(h.event().copy(geometry=TrafficGeometry.Polyline(listOf(point(300.0),point(600.0))))))
        assertFalse(h.detour.activate());assertNull(h.nav.uiState.value.activeDetour)
        h.push(emptyList());assertNull(h.nav.uiState.value.activeDetour)
        assertEquals(DetourSessionState.PREVIEW,h.detour.state.value.stage)
        assertTrue(h.detour.activate());runCurrent();assertEquals(1,h.calls)
    }
    @Test fun freeModeShowsImpactAndOverlayDataWithoutAutomaticRouting() = runTest {
        val h=Harness(this);h.ready(free=true);h.push()
        assertNotNull(h.traffic.state.value.alert);assertEquals(1,h.traffic.state.value.activeEvents.size)
        assertFalse(h.detour.begin());assertEquals(0,h.calls)
        assertSame(h.saved.route,h.nav.uiState.value.activeRoute)
    }
    @Test fun localClockExpiresAndPromotesEventsWithoutRefreshOrGps() = runTest {
        val h=Harness(this);h.ready()
        h.push(listOf(h.event().copy(validUntilEpochMillis=2500),h.event().copy(id="future",validFromEpochMillis=2500)))
        assertEquals(1,h.traffic.state.value.activeEvents.size);assertEquals(1,h.traffic.state.value.futureEvents.size)
        advanceTimeBy(1500);runCurrent()
        assertEquals("future",h.traffic.state.value.activeEvents.single().id)
        assertTrue(h.traffic.state.value.futureEvents.isEmpty());assertEquals(0,h.calls)
    }
    @Test fun failedSourceRetainsEventsAndForegroundResumeReevaluatesAge() = runTest {
        val h=Harness(this);h.ready();h.push();h.push(emptyList(),TrafficProviderStatus.ERROR)
        assertEquals(1,h.traffic.state.value.activeEvents.size)
        assertEquals(TrafficProviderStatus.ERROR,h.traffic.state.value.status)
        h.traffic.stop();h.push(emptyList());assertEquals(1,h.traffic.state.value.activeEvents.size)
        h.traffic.start();runCurrent();assertTrue(h.traffic.state.value.activeEvents.isEmpty())
        advanceTimeBy(301000);runCurrent();assertEquals(TrafficProviderStatus.STALE,h.traffic.state.value.status)
    }
    @Test fun trafficRemovalDoesNotCancelActiveDetour() = runTest {
        val h=Harness(this);h.ready();h.push(emptyList());assertTrue(h.detour.begin());runCurrent();h.calculate()
        assertTrue(h.detour.activate());runCurrent();val active=h.nav.uiState.value.activeDetour
        h.push(listOf(h.event()));h.push(emptyList());assertSame(active,h.nav.uiState.value.activeDetour);assertEquals(1,h.calls)
    }
}
