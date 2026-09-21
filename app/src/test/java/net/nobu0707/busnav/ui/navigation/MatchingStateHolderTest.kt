package net.nobu0707.busnav.ui.navigation

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.navigation.*
import net.nobu0707.busnav.domain.route.*
import net.nobu0707.busnav.domain.routeplan.*
import net.nobu0707.busnav.domain.routing.*
import net.nobu0707.busnav.location.*
import net.nobu0707.busnav.ui.routing.RouteCalculationStateHolder
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchingStateHolderTest {
    private fun route(id: String="test", highway: Boolean=false): ScheduledRoute {
        val g=RouteGeometry(listOf(GeoPoint(0.0,0.0),GeoPoint(0.0,.005),GeoPoint(0.0,.01)))
        return ScheduledRoute(id,id,g,listOf(RoutePoint("s",RoutePointType.START,g.first),RoutePoint("d",RoutePointType.DESTINATION,g.last)),
            guidance=RouteGuidance(listOf(RouteManeuver(0,if(highway) ManeuverType.KEEP_LEFT else ManeuverType.RIGHT,"",1,2))))
    }
    private class Provider : LocationProvider {
        val flow=MutableSharedFlow<LocationUpdate>(extraBufferCapacity=20)
        override fun updates()=flow
        override fun isLocationEnabled()=true
        fun send(time: Long, y: Double=0.0, accuracy: Float=5f, x: Double=.001) =
            flow.tryEmit(LocationUpdate.Position(LocationState(GeoPoint(y,x),accuracy,90f,0f,0,time)))
    }
    @Test fun offRouteSuppressesBothGuidanceTypesAndRecoveryRestoresThemWithoutRouting() = runTest {
        for(highway in listOf(false,true)) {
            val r=route(highway=highway);val p=Provider()
            val events=mutableListOf<String>()
            val h=NavigationStateHolder(p,object:ScheduledRouteRepository{override suspend fun getActiveRoute()=r},
                backgroundScope,StandardTestDispatcher(testScheduler),{testScheduler.currentTime},diagnostics=events::add)
            h.setPermission(LocationPermissionState.Granted);runCurrent();h.applyCalculatedRoute(r);runCurrent()
            var calls=0
            val calculation=RouteCalculationStateHolder(RoutingEngine { calls++;RoutingResult.Success(r,RoutingSummary(1000.0,60.0)) },backgroundScope)
            val plan=RoutePlan("plan",points=listOf(RoutePlanPoint("s",RoutePlanPointType.START,r.geometry.first),
                RoutePlanPoint("d",RoutePlanPointType.DESTINATION,r.geometry.last)))
            calculation.calculate(plan,0);runCurrent()
            h.applyCalculatedRoute(requireNotNull(calculation.currentCandidate(0)));runCurrent()
            fun send(y:Double=0.0,dt:Long=1000) { advanceTimeBy(dt);p.send(testScheduler.currentTime,y);runCurrent() }
            send()
            assertEquals(GuidanceStatus.RELIABLE,h.uiState.value.guidance.status)
            assertNull(h.uiState.value.deviation.message)
            if(highway) assertNotNull(h.uiState.value.highwayGuidance!!.schematic)
            send(.0009);send(.0009);send(.0009,2000)
            assertEquals(RouteDeviationState.OFF_ROUTE,h.uiState.value.deviationSnapshot.state)
            assertNull(h.uiState.value.guidance.distanceText)
            assertNull(h.uiState.value.highwayGuidance?.schematic)
            assertTrue(h.uiState.value.deviation.isProminent)
            assertEquals(1,calls);assertSame(r,h.uiState.value.activeRoute)
            assertEquals(.0009,h.uiState.value.location!!.point.latitude,0.0)
            send();assertEquals(RouteDeviationState.RECOVERING,h.uiState.value.deviationSnapshot.state)
            send();send()
            assertEquals(GuidanceStatus.RELIABLE,h.uiState.value.guidance.status)
            if(highway) assertNotNull(h.uiState.value.highwayGuidance!!.schematic)
            assertEquals(1,calls)
            calculation.calculate(plan,0);runCurrent();assertEquals(2,calls)
            assertTrue(events.all { it.startsWith("navigation.") && !it.contains("0.00") })
            assertTrue(events.zipWithNext().none { (a,b)->a==b })
        }
    }
    @Test fun noNewFixExpiresGuidanceAndRecoveryNeedsFreshEvidence() = runTest {
        val r=route();val p=Provider()
        val h=NavigationStateHolder(p,object:ScheduledRouteRepository{override suspend fun getActiveRoute()=r},
            backgroundScope,StandardTestDispatcher(testScheduler),{testScheduler.currentTime})
        h.setPermission(LocationPermissionState.Granted);runCurrent();h.applyCalculatedRoute(r);runCurrent()
        p.send(0);runCurrent();assertEquals(GuidanceStatus.RELIABLE,h.uiState.value.guidance.status)
        advanceTimeBy(10000);runCurrent()
        assertEquals(RouteMatchQuality.UNRELIABLE,h.uiState.value.deviationSnapshot.matchQuality)
        assertNull(h.uiState.value.guidance.distanceText)
        p.send(testScheduler.currentTime);runCurrent()
        assertEquals(RouteDeviationState.RECOVERING,h.uiState.value.deviationSnapshot.state)
        repeat(2){advanceTimeBy(1000);p.send(testScheduler.currentTime);runCurrent()}
        assertEquals(RouteDeviationState.ON_ROUTE,h.uiState.value.deviationSnapshot.state)
    }
    @Test fun duplicateAndOlderFixCannotRewindMarkerOrEvidence() = runTest {
        val r=route();val p=Provider()
        val h=NavigationStateHolder(p,object:ScheduledRouteRepository{override suspend fun getActiveRoute()=r},
            backgroundScope,StandardTestDispatcher(testScheduler),{testScheduler.currentTime})
        h.setPermission(LocationPermissionState.Granted);runCurrent();h.applyCalculatedRoute(r);runCurrent()
        advanceTimeBy(1000);p.send(1000);runCurrent()
        val before=h.uiState.value
        p.send(999,.001);p.send(1000,.001);runCurrent()
        assertEquals(before,h.uiState.value)
    }
    @Test fun routeReplacementRebuildsMatcherAndClearsDeviation() = runTest {
        val r=route();val p=Provider()
        val h=NavigationStateHolder(p,object:ScheduledRouteRepository{override suspend fun getActiveRoute()=r},
            backgroundScope,StandardTestDispatcher(testScheduler),{testScheduler.currentTime})
        h.setPermission(LocationPermissionState.Granted);runCurrent();h.applyCalculatedRoute(r);runCurrent()
        p.send(0,.001);runCurrent()
        advanceTimeBy(1500);p.send(1500,.001);runCurrent()
        advanceTimeBy(1500);p.send(3000,.001);runCurrent()
        assertEquals(RouteDeviationState.OFF_ROUTE,h.uiState.value.deviationSnapshot.state)
        val other=route("replacement");h.clearRoute();h.applyCalculatedRoute(other)
        assertEquals(RouteDeviationState.UNKNOWN,h.uiState.value.deviationSnapshot.state)
        runCurrent()
        assertSame(other,h.uiState.value.activeRoute)
        assertEquals(RouteDeviationState.SUSPECTED_OFF_ROUTE,h.uiState.value.deviationSnapshot.state)
        assertEquals(1,h.uiState.value.deviationSnapshot.consecutiveOffRouteFixes)
    }
    private class QueuedDispatcher:CoroutineDispatcher() {
        val tasks=mutableListOf<Runnable>()
        override fun dispatch(context:CoroutineContext,block:Runnable) { tasks.add(block) }
        fun runLast() { tasks.removeAt(tasks.lastIndex).run() }
        fun runAll() { while(tasks.isNotEmpty()) tasks.removeAt(0).run() }
    }
    @Test fun delayedOlderComputationCannotOverwriteNewestFixOrReplacement() = runTest {
        val r=route();val p=Provider();val worker=QueuedDispatcher()
        val h=NavigationStateHolder(p,object:ScheduledRouteRepository{override suspend fun getActiveRoute()=r},
            backgroundScope,worker,{testScheduler.currentTime})
        h.setPermission(LocationPermissionState.Granted);runCurrent();h.applyCalculatedRoute(r);runCurrent();worker.runAll();runCurrent()
        p.send(0,.001);runCurrent() // A is pending on the worker.
        advanceTimeBy(1000);p.send(1000);runCurrent() // B replaces A.
        worker.runLast();runCurrent()
        assertEquals(RouteDeviationState.ON_ROUTE,h.uiState.value.deviationSnapshot.state)
        val newest=h.uiState.value
        worker.runAll();runCurrent();assertEquals(newest,h.uiState.value)
        advanceTimeBy(1000);p.send(2000,.001);runCurrent()
        h.clearRoute();h.applyCalculatedRoute(route("new"));runCurrent()
        worker.runLast();runCurrent();worker.runAll();runCurrent()
        assertEquals("new",h.uiState.value.activeRoute!!.id)
        assertEquals(1,h.uiState.value.deviationSnapshot.consecutiveOffRouteFixes)
    }
    @Test fun routeWithoutManeuversStillDetectsDeviation() = runTest {
        val template=route()
        val r=ScheduledRoute("no-guidance","route",template.geometry,template.points)
        val p=Provider()
        val h=NavigationStateHolder(p,object:ScheduledRouteRepository{override suspend fun getActiveRoute()=r},
            backgroundScope,StandardTestDispatcher(testScheduler),{testScheduler.currentTime})
        h.setPermission(LocationPermissionState.Granted);runCurrent();h.applyCalculatedRoute(r);runCurrent()
        repeat(3){advanceTimeBy(1500);p.send(testScheduler.currentTime,.001);runCurrent()}
        assertEquals(RouteDeviationState.OFF_ROUTE,h.uiState.value.deviationSnapshot.state)
        assertEquals(GuidanceStatus.NO_GUIDANCE,h.uiState.value.guidance.status)
    }
    @Test fun layoutChangeDoesNotResetMatchingOrDeviation() = runTest {
        val r=route();val p=Provider()
        val h=NavigationStateHolder(p,object:ScheduledRouteRepository{override suspend fun getActiveRoute()=r},
            backgroundScope,StandardTestDispatcher(testScheduler),{testScheduler.currentTime})
        h.setPermission(LocationPermissionState.Granted);runCurrent();h.applyCalculatedRoute(r);runCurrent();p.send(0);runCurrent()
        val snapshot=h.uiState.value.deviationSnapshot
        h.setLayoutMode(NavigationLayoutMode.LandscapeThreeColumn);runCurrent()
        assertSame(snapshot,h.uiState.value.deviationSnapshot)
    }

    @Test fun sameRouteReapplicationWhilePreparingKeepsPendingIndex() = runTest {
        val r=route();val p=Provider();val worker=QueuedDispatcher()
        val h=NavigationStateHolder(p,object:ScheduledRouteRepository{override suspend fun getActiveRoute()=r},
            backgroundScope,worker,{testScheduler.currentTime})
        h.setPermission(LocationPermissionState.Granted);runCurrent();h.applyCalculatedRoute(r);runCurrent()
        h.applyCalculatedRoute(r)
        worker.runAll();runCurrent()
        p.send(0);runCurrent();worker.runAll();runCurrent()
        assertEquals(GuidanceStatus.RELIABLE,h.uiState.value.guidance.status)
    }
}
