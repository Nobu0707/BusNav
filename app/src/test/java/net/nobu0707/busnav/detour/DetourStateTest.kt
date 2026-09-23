package net.nobu0707.busnav.detour

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import net.nobu0707.busnav.domain.detour.*
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.prescribed.NavigationMode
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.ui.detour.*
import net.nobu0707.busnav.ui.navigation.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetourStateTest {
    private class Provider : LocationProvider {
        val flow=MutableSharedFlow<LocationUpdate>(extraBufferCapacity=20)
        override fun updates()=flow
        override fun isLocationEnabled()=true
    }
    private class Harness(val test: TestScope, engine: RoutingEngine? = null) {
        val provider=Provider()
        val saved=detourFixture(13)
        val requests=mutableListOf<RoutingRequest>()
        val dispatcher=StandardTestDispatcher(test.testScheduler)
        val nav=NavigationStateHolder(provider,object:ScheduledRouteRepository { override suspend fun getActiveRoute():ScheduledRoute?=null },
            test.backgroundScope,dispatcher,{test.testScheduler.currentTime})
        val holder=DetourStateHolder(engine ?: RoutingEngine { requests+=it; resultFor(it) }, nav,
            test.backgroundScope,{test.testScheduler.currentTime},dispatcher=dispatcher)
        fun fix(meters:Double=100.0, offset:Double=0.0, speed:Float?=0f, accuracy:Float?=5f, dt:Long=1000) {
            test.advanceTimeBy(dt)
            provider.flow.tryEmit(LocationUpdate.Position(LocationState(point(meters,offset),accuracy,90f,speed,0,test.testScheduler.currentTime)))
            test.runCurrent()
        }
        fun ready() {
            nav.setPermission(LocationPermissionState.Granted); test.runCurrent()
            fix()
            nav.openPrescribedRoute(saved); assertTrue(nav.startNavigation()); test.runCurrent()
            fix()
            assertNotNull(nav.uiState.value.lastReliablePrescribedProgress)
        }
        fun begin() { assertTrue(holder.begin()); test.runCurrent() }
        fun select() { holder.selectTarget(holder.state.value.candidates.first().id) }
        fun preview() { begin(); select(); assertTrue(holder.calculate()); test.runCurrent(); assertEquals(DetourSessionState.PREVIEW,holder.state.value.stage) }
        fun active() { ready(); fix(offset=100.0); preview(); assertTrue(holder.activate()); test.runCurrent() }
    }

    @Test fun explicitPreviewActivationKeepsModeIdentityOriginalAndProfile()=runTest {
        val h=Harness(this); h.ready(); h.preview()
        assertSame(h.saved.route,h.nav.uiState.value.activeRoute)
        assertEquals(1,h.requests.size)
        assertTrue(h.holder.activate()); runCurrent()
        val state=h.nav.uiState.value
        assertEquals(NavigationMode.PRESCRIBED,state.navigationMode)
        assertEquals(h.saved.id,state.activePrescribedRouteId)
        assertSame(h.saved.route,state.prescribedRouteSnapshot)
        assertEquals(h.saved.vehicleProfile,state.prescribedVehicleProfile)
        assertSame(h.holder.state.value.candidate!!.route,state.activeRoute)
        assertEquals(1,h.requests.size)
    }
    @Test fun rawGpsSavedProfileAndOrderedViaShapingRequestWithoutMutatingPlan()=runTest {
        val h=Harness(this); h.ready(); h.fix(offset=100.0); h.begin()
        assertEquals(0,h.requests.size)
        h.holder.selectMapMode(DetourMapMode.REJOIN); h.holder.setCursor(point(1800.0,20.0))
        h.holder.selectMapMode(DetourMapMode.VIA); h.holder.setCursor(point(600.0,200.0))
        h.holder.selectMapMode(DetourMapMode.SHAPING); h.holder.setCursor(point(900.0,200.0))
        assertEquals(0,h.requests.size)
        assertTrue(h.holder.calculate()); runCurrent()
        val r=h.requests.single()
        assertEquals(h.nav.uiState.value.location!!.point,r.origin)
        assertNotEquals(h.saved.route.start.position,r.origin)
        assertEquals(listOf(RoutePlanPointType.START,RoutePlanPointType.VIA,RoutePlanPointType.SHAPING,RoutePlanPointType.DESTINATION),r.points.map{it.type})
        assertEquals(h.saved.vehicleProfile,r.vehicleProfile)
        assertEquals(h.holder.state.value.target!!.point,r.destination)
        assertEquals(2,h.saved.routePlan.points.size)
        assertTrue(h.holder.activate());runCurrent();assertEquals(1,h.requests.size)
    }
    @Test fun offRouteRetainsLastReliableAnchor()=runTest {
        val h=Harness(this);h.ready();val anchor=h.nav.uiState.value.lastReliablePrescribedProgress
        repeat(4){h.fix(offset=100.0,dt=1500)}
        assertEquals(RouteDeviationState.OFF_ROUTE,h.nav.uiState.value.deviationSnapshot.state)
        assertEquals(anchor,h.nav.uiState.value.lastReliablePrescribedProgress)
        h.begin();assertEquals(anchor!!.progressMeters,h.holder.state.value.anchorProgressMeters!!,0.0)
        assertTrue(h.requests.isEmpty())
    }
    @Test fun noHistoryAndStaleHistoryCannotStart()=runTest {
        val h=Harness(this);h.nav.setPermission(LocationPermissionState.Granted);runCurrent()
        h.nav.openPrescribedRoute(h.saved);h.nav.startNavigation();runCurrent();h.fix(offset=100.0)
        assertFalse(h.holder.begin())
        h.nav.clearRoute();h.ready()
        h.fix(offset=100.0,dt=301000)
        assertFalse(h.holder.begin())
    }
    @Test fun locationPermissionFreshnessAccuracyAndDisabledBlock()=runTest {
        val h=Harness(this);h.ready();h.begin();h.select()
        h.nav.setPermission(LocationPermissionState.Denied);assertFalse(h.holder.calculate())
        h.nav.setPermission(LocationPermissionState.Granted);runCurrent()
        h.fix(accuracy=100f);assertFalse(h.holder.calculate())
        h.fix();advanceTimeBy(11000);runCurrent();assertFalse(h.holder.calculate())
        h.fix();h.provider.flow.tryEmit(LocationUpdate.Disabled);runCurrent();assertFalse(h.holder.calculate())
        assertTrue(h.requests.isEmpty())
    }
    @Test fun speedLockIsEnforcedInsideHolderAndGuidanceContinues()=runTest {
        val h=Harness(this);h.ready();h.begin();h.fix(speed=5f)
        h.select();assertNull(h.holder.state.value.target)
        h.holder.selectMapMode(DetourMapMode.REJOIN);assertEquals(DetourMapMode.NONE,h.holder.state.value.mapMode)
        assertEquals(GuidanceStatus.RELIABLE,h.nav.uiState.value.guidance.status)
        h.fix(speed=null);h.select();assertNotNull(h.holder.state.value.target)
        h.holder.calculate();runCurrent();h.fix(speed=5f)
        assertFalse(h.holder.activate());assertSame(h.saved.route,h.nav.uiState.value.activeRoute)
        h.fix(speed=2f);assertTrue(h.holder.activate())
    }
    @Test fun freeCannotStartAndPrescribedSnapshotIsCleared()=runTest {
        val h=Harness(this);h.ready();h.nav.clearRoute()
        h.nav.previewFreeRoute(FreeNavigationPlan(point(2000.0)),h.saved.route);h.nav.startFreeNavigation();runCurrent()
        assertFalse(h.holder.begin());assertNull(h.nav.uiState.value.prescribedRouteSnapshot)
        assertNull(h.nav.uiState.value.prescribedVehicleProfile);assertNull(h.nav.uiState.value.activePrescribedRouteId)
    }
    @Test fun renameDoesNotInvalidateSession()=runTest {
        val h=Harness(this);h.ready();h.begin();h.nav.refreshPrescribedName(h.saved.id,"改名");runCurrent()
        h.select();assertTrue(h.holder.calculate());runCurrent();assertTrue(h.holder.activate())
        assertEquals("改名",h.nav.uiState.value.activePrescribedRouteName)
    }
    @Test fun changedSavedContentInvalidatesButRenameDoesNot()=runTest {
        val h=Harness(this);h.active()
        h.nav.refreshPrescribedRecord(h.saved.copy(name="改名",updatedAtEpochMillis=2000));runCurrent()
        assertNotNull(h.nav.uiState.value.activeDetour)
        h.nav.refreshPrescribedRecord(h.saved.copy(vehicleProfile=h.saved.vehicleProfile.copy(heightMeters=3.5)));runCurrent()
        assertNull(h.nav.uiState.value.activeRoute)
        assertEquals(DetourSessionState.IDLE,h.holder.state.value.stage)
    }
    @Test fun replacingSessionInSelectingPreviewAndActiveClearsDetour()=runTest {
        for(stage in listOf("selecting","preview","active")) {
            val h=Harness(this);h.ready()
            if(stage=="selecting")h.begin() else h.preview()
            if(stage=="active"){h.holder.activate();runCurrent()}
            h.nav.clearRoute();h.nav.openPrescribedRoute(h.saved.copy(id="other"));h.nav.startNavigation();runCurrent()
            assertEquals(DetourSessionState.IDLE,h.holder.state.value.stage)
            assertNull(h.nav.uiState.value.activeDetour)
            assertSame(h.saved.route,h.nav.uiState.value.activeRoute)
        }
    }
    @Test fun cancellationInsensitiveResultsCannotApplyAfterAnyEditOrSessionSwitch()=runTest {
        for(action in listOf("cancel","target","point","end","switch")) {
            val pending=CompletableDeferred<RoutingResult>();var req:RoutingRequest?=null
            val h=Harness(this,RoutingEngine {req=it;withContext(NonCancellable){pending.await()}})
            h.ready();h.begin();h.select();h.holder.calculate();runCurrent()
            when(action) {
                "cancel"->h.holder.cancelPlanning()
                "target"->h.holder.selectTarget(h.holder.state.value.candidates.last().id)
                "point"->{h.holder.selectMapMode(DetourMapMode.VIA);h.holder.setCursor(point(900.0,200.0))}
                "end"->h.nav.clearRoute()
                "switch"->{h.nav.clearRoute();h.nav.previewFreeRoute(FreeNavigationPlan(point(5000.0)),h.saved.route)}
            }
            pending.complete(resultFor(req!!));runCurrent()
            assertNull(h.holder.state.value.candidate);assertNull(h.nav.uiState.value.activeDetour)
        }
    }
    @Test fun failedCalculationKeepsPrescribedGuidance()=runTest {
        val h=Harness(this,RoutingEngine {RoutingResult.Failure(RoutingFailure.NO_ROUTE)})
        h.ready();h.begin();h.select();h.holder.calculate();runCurrent()
        assertEquals(DetourSessionState.FAILED,h.holder.state.value.stage)
        assertEquals("この復帰地点への大型車経路を見つけられませんでした",h.holder.state.value.error)
        assertSame(h.saved.route,h.nav.uiState.value.activeRoute)
    }
    @Test fun cancelPlanningAndActiveHaveDifferentEffects()=runTest {
        val h=Harness(this);h.active();val active=h.nav.uiState.value.activeRoute
        h.begin();h.select();h.holder.calculate();runCurrent();h.holder.cancelPlanning();runCurrent()
        assertSame(active,h.nav.uiState.value.activeRoute)
        h.holder.endDetour();runCurrent()
        assertSame(h.saved.route,h.nav.uiState.value.activeRoute)
        repeat(4){h.fix(offset=100.0,dt=1500)}
        assertEquals(RouteDeviationState.OFF_ROUTE,h.nav.uiState.value.deviationSnapshot.state)
        assertEquals(2,h.requests.size)
    }
    @Test fun manualReplanUsesOneMoreRequestAndRequiresExplicitActivation()=runTest {
        val h=Harness(this);h.active();val active=h.nav.uiState.value.activeRoute
        h.preview();assertSame(active,h.nav.uiState.value.activeRoute);assertEquals(2,h.requests.size)
        assertTrue(h.holder.activate());runCurrent();assertNotSame(active,h.nav.uiState.value.activeRoute)
        assertSame(h.saved.route,h.nav.uiState.value.prescribedRouteSnapshot)
    }
    @Test fun detourDeviationDoesNotReplanAndPrescribedRejoinWins()=runTest {
        val h=Harness(this);h.active()
        assertEquals(RouteDeviationState.ON_ROUTE,h.nav.uiState.value.deviationSnapshot.state)
        h.fix(offset=220.0,dt=1500)
        assertEquals(RouteDeviationState.SUSPECTED_OFF_ROUTE,h.nav.uiState.value.deviationSnapshot.state)
        repeat(3){h.fix(offset=220.0,dt=1500)}
        assertEquals(RouteDeviationState.OFF_ROUTE,h.nav.uiState.value.deviationSnapshot.state)
        assertTrue(h.nav.uiState.value.deviation.message!!.startsWith("迂回経路"))
        assertEquals(1,h.requests.size)
        // Past crossing does not restore even after many fixes.
        repeat(5){h.fix(meters=100.0)}
        assertNotNull(h.nav.uiState.value.activeDetour)
        repeat(5){h.fix(meters=900.0)}
        assertNull(h.nav.uiState.value.activeDetour)
        assertSame(h.saved.route,h.nav.uiState.value.activeRoute)
        assertEquals(RejoinState.CONFIRMED,h.nav.uiState.value.rejoin.state)
        assertEquals(1,h.requests.size)
        assertEquals(DetourSessionState.COMPLETED,h.holder.state.value.stage)
        assertEquals(GuidanceStatus.RELIABLE,h.nav.uiState.value.guidance.status)
        assertTrue(h.nav.uiState.value.lastReliablePrescribedProgress!!.progressMeters>800)
    }
    @Test fun rejoinPastTargetResumesActualNextManeuver()=runTest {
        val h=Harness(this);h.active()
        repeat(5){h.fix(meters=7000.0)}
        assertNull(h.nav.uiState.value.activeDetour)
        assertTrue(h.nav.uiState.value.lastReliablePrescribedProgress!!.progressMeters>6900)
        assertEquals(ManeuverType.DESTINATION,h.nav.uiState.value.guidance.maneuverType)
        assertEquals(GuidanceStatus.RELIABLE,h.nav.uiState.value.guidance.status)
        assertEquals(1,h.requests.size)
    }
    @Test fun reorderDeleteAndEditInvalidatePreviewWithoutNetwork()=runTest {
        val h=Harness(this);h.ready();h.begin();h.select()
        h.holder.selectMapMode(DetourMapMode.VIA);h.holder.setCursor(point(400.0,100.0))
        h.holder.selectMapMode(DetourMapMode.SHAPING);h.holder.setCursor(point(600.0,100.0))
        val first=h.holder.state.value.points.first()
        h.holder.movePoint(first.id,1)
        assertEquals(first,h.holder.state.value.points.last())
        h.holder.calculate();runCurrent();h.holder.edit()
        assertNull(h.holder.state.value.candidate)
        h.holder.removePoint(first.id);assertEquals(1,h.holder.state.value.points.size)
        assertEquals(1,h.requests.size)
    }
}
