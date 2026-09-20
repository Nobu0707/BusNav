package net.nobu0707.busnav.domain.navigation

import kotlin.math.abs
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.route.RouteGeometry
import net.nobu0707.busnav.location.LocationState
import org.junit.Assert.*
import org.junit.Test

/** All coordinates are synthetic geometry, never a recorded location history. */
class RouteMatcherTest {
    private fun p(x: Double, y: Double = 0.0) = GeoPoint(y / 111195.08, x / 111195.08)
    private fun matcher(vararg points: GeoPoint): RouteMatcher {
        val config = RouteMatcherConfig()
        return RouteMatcher(RouteMatchIndex(RouteDistanceIndex(RouteGeometry(points.toList())), config), config)
    }
    private fun straight() = matcher(p(0.0), p(5000.0))
    private fun fix(x: Double, y: Double = 0.0, t: Long = 1000, accuracy: Float? = 5f,
        bearing: Float? = 90f, speed: Float? = 10f) = LocationState(p(x,y),accuracy,bearing,speed,123456,t)
    private fun result(m: RouteMatcher, f: LocationState, state: RouteMatcherState = RouteMatcherState()) =
        m.match(f,state,requireNotNull(f.elapsedRealtimeMillis))
    private fun crossing() = matcher(p(-100.0),p(100.0),p(100.0,-100.0),p(0.0,-100.0),p(0.0,100.0))

    @Test fun distanceScoreUsesAccuracyFloorAndReportedAccuracy() {
        val m=straight()
        val a=m.score(fix(100.0,30.0),0); val b=m.score(fix(100.0,30.0,accuracy=30f),0)
        assertEquals(30.0/8.0,a.distanceScore,.001); assertEquals(1.0,b.distanceScore,.001)
        assertTrue(a.totalScore>b.totalScore)
    }
    @Test fun headingWrapAndInvalidHeading() {
        assertEquals(2.0,headingDifferenceDegrees(359.0,1.0),0.0)
        assertEquals(20.0,headingDifferenceDegrees(350.0,10.0),0.0)
        assertNull(straight().score(fix(100.0,bearing=Float.NaN),0).headingScore)
        assertNull(straight().score(fix(100.0,bearing=null),0).headingScore)
    }
    @Test fun stationaryAndLowSpeedIgnoreHeading() {
        listOf(0f,2.49f,Float.NaN,-1f).forEach { assertNull(straight().score(fix(100.0,bearing=270f,speed=it),0).headingScore) }
        assertNotNull(straight().score(fix(100.0,bearing=270f,speed=2.5f),0).headingScore)
    }
    @Test fun initialSearchCanStartFarAlongRoute() {
        val r=result(straight(),fix(4500.0)).match!!
        assertEquals(RouteMatchQuality.MATCHED,r.quality); assertEquals(4500.0,r.projection.distanceAlongRouteMeters,.01)
    }
    @Test fun selfCrossingUsesHeadingAndContinuity() {
        val m=crossing()
        val first=result(m,fix(-20.0))
        val next=result(m,fix(0.0,t=3000,bearing=null),first.state)
        assertEquals(0,next.match!!.projection.segmentIndex)
        assertEquals(RouteMatchQuality.MATCHED,next.match.quality)
        assertEquals(3,result(m,fix(0.0,bearing=0f)).match!!.projection.segmentIndex)
    }
    @Test fun tiedCrossingWithoutHistoryIsAmbiguous() {
        val match=result(crossing(),fix(0.0,bearing=null)).match!!
        assertEquals(RouteMatchQuality.AMBIGUOUS,match.quality)
        assertEquals(0.0,match.candidateGap!!,.0001)
    }
    @Test fun oppositeCarriagewayHeadingOverridesCloserWrongDirection() {
        val m=matcher(p(0.0),p(1000.0),p(1000.0,15.0),p(0.0,15.0))
        assertEquals(0,result(m,fix(400.0,12.0)).match!!.projection.segmentIndex)
        assertEquals(2,result(m,fix(400.0,12.0,bearing=270f)).match!!.projection.segmentIndex)
        val first=result(m,fix(400.0))
        assertEquals(0,result(m,fix(400.0,12.0,t=2000,speed=0f,bearing=270f),first.state).match!!.projection.segmentIndex)
    }
    @Test fun plausibleAdvanceUsesTimeAndSpeed() {
        val m=straight(); val a=result(m,fix(100.0))
        val jump=result(m,fix(3000.0,t=2000),a.state)
        assertEquals(RouteMatchQuality.UNRELIABLE,jump.match!!.quality)
        assertEquals(a.state.heldProgressMeters,jump.state.heldProgressMeters)
        assertTrue(m.score(fix(3000.0,t=2000),0,a.state.anchor,a.state.anchorTimestampMillis).jumpPenalty>0)
        val longGap=result(m,fix(3000.0,t=301000),a.state)
        assertEquals(RouteMatchQuality.MATCHED,longGap.match!!.quality)
    }
    @Test fun missingSpeedHasConservativeTimeAllowance() {
        val m=straight();val a=result(m,fix(100.0))
        assertEquals(RouteMatchQuality.MATCHED,result(m,fix(250.0,t=6000,speed=null),a.state).match!!.quality)
    }
    @Test fun singleLongitudinalSpikeDoesNotMoveAnchor() {
        val m=straight(); val a=result(m,fix(100.0)); val b=result(m,fix(3000.0,t=2000),a.state)
        val c=result(m,fix(120.0,t=3000),b.state)
        assertEquals(120.0,c.state.heldProgressMeters!!,.01)
        assertEquals(RouteMatchQuality.MATCHED,c.match!!.quality)
    }
    @Test fun sustainedLostFixesReacquireOutsideOldWindow() {
        val points=(0..4000).map { p(it*2.0) }; val m=matcher(*points.toTypedArray())
        var state=result(m,fix(100.0)).state
        repeat(4) { state=result(m,fix(6000.0,t=(it+2)*1000L),state).state }
        assertEquals(6000.0,state.heldProgressMeters!!,.01)
    }
    @Test fun backwardJitterHeldButRealReverseMovementAllowed() {
        val m=straight(); val a=result(m,fix(100.0))
        val jitter=result(m,fix(95.0,t=2000,speed=0f),a.state)
        assertEquals(100.0,jitter.state.heldProgressMeters!!,.01)
        val reverse=result(m,fix(60.0,t=6000,bearing=270f),jitter.state)
        assertEquals(60.0,reverse.state.heldProgressMeters!!,.01)
    }
    @Test fun invalidAccuracyNeverEstablishesReliableMatch() {
        listOf(null,Float.NaN,Float.POSITIVE_INFINITY,-1f,80f,100f).forEach {
            assertEquals("accuracy=$it",RouteMatchQuality.UNRELIABLE,result(straight(),fix(100.0,accuracy=it)).match!!.quality)
        }
    }
    @Test fun staleFutureMissingAndOutOfOrderTimesDoNotMutateTracker() {
        val m=straight();val f=fix(100.0);val a=result(m,f)
        listOf(f.copy(elapsedRealtimeMillis=null),f.copy(elapsedRealtimeMillis=-1),
            f.copy(elapsedRealtimeMillis=999), f.copy(elapsedRealtimeMillis=1001),f).forEach {
            val r=m.match(it,a.state,1000);assertFalse(r.accepted);assertSame(a.state,r.state)
        }
        assertFalse(m.match(f,RouteMatcherState(),11000).accepted)
    }
    @Test fun wallClockChangesDoNotAffectMatching() {
        val m=straight(); val a=result(m,fix(100.0))
        val f=fix(110.0,t=2000)
        assertEquals(result(m,f,a.state),result(m,f.copy(timestampMillis=Long.MIN_VALUE),a.state))
    }
    @Test fun duplicateVerticesAndZeroLengthAreSafe() {
        val m=matcher(p(0.0),p(0.0))
        assertNull(m.index.segments[0].bearingDegrees)
        assertEquals(0.0,result(m,fix(0.0)).match!!.projection.distanceAlongRouteMeters,0.0)
        assertEquals(0.0,RouteProjector.project(p(0.0),m.index.distances.geometry,m.index.distances).distanceFromRouteMeters,0.0)
    }
    @Test fun datelineUsesWrappedLongitude() {
        val m=matcher(GeoPoint(0.0,179.99),GeoPoint(0.0,-179.99))
        val f=fix(0.0).copy(point=GeoPoint(0.0,180.0))
        val match=result(m,f).match!!
        assertEquals(.5,match.projection.fraction,.00001)
        assertEquals(m.index.distances.totalMeters/2,match.projection.distanceAlongRouteMeters,.001)
    }
    @Test fun adjacentVerticesAreEquivalentHypotheses() {
        val m=matcher(p(0.0),p(100.0),p(200.0))
        assertEquals(RouteMatchQuality.MATCHED,result(m,fix(100.0)).match!!.quality)
    }
    @Test fun curveTraceRetainsCommonDistanceAxis() {
        val m=matcher(p(0.0),p(100.0),p(100.0,100.0),p(200.0,100.0))
        var state=RouteMatcherState()
        val samples=listOf(fix(50.0,t=1000),fix(100.0,50.0,11000,bearing=0f),fix(150.0,100.0,21000))
        samples.forEachIndexed { i,f ->
            val r=result(m,f,state); state=r.state
            assertEquals("fix $i",RouteMatchQuality.MATCHED,r.match!!.quality)
            assertEquals("fix $i",50.0+100*i,r.match.projection.distanceAlongRouteMeters,.01)
        }
    }
    @Test fun largeRouteUsesBoundedNearbyCandidatesAndRecordsTiming() {
        val m=matcher(*(0..4000).map { p(it*5.0) }.toTypedArray())
        var state=result(m,fix(5000.0)).state
        val start=System.nanoTime()
        repeat(1000) { val r=result(m,fix(5000.0+it,t=2000L+it*1000),state); state=r.state
            assertTrue(r.match!!.evaluatedCandidates<600)
            assertEquals(RouteMatchQuality.MATCHED,r.match.quality)
        }
        println("Phase008 4001-point route: " + ((System.nanoTime()-start)/1e6/1000) + " ms/update, JVM warm sequence")
    }
    @Test fun invalidConfigsRejected() {
        assertThrows(IllegalArgumentException::class.java) { RouteMatcherConfig(staleAfterMillis=0) }
        assertThrows(IllegalArgumentException::class.java) { RouteMatcherConfig(minimumAccuracyFloorMeters=Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { DeviationConfig(offRouteConsecutiveFixes=1) }
    }
    private data class Expected(val y: Double, val t: Long, val state: RouteDeviationState, val accuracy: Float=5f)
    private fun replay(samples: List<Expected>) {
        val m=straight();val d=RouteDeviationDetector();var tracker=RouteMatcherState();var state=RouteDeviationSnapshot()
        samples.forEachIndexed { i, expected ->
            val f=fix(100.0,expected.y,expected.t,accuracy=expected.accuracy)
            val r=result(m,f,tracker);tracker=r.state
            state=d.update(state,r.match,f.accuracyMeters,f.elapsedRealtimeMillis)
            assertEquals("fix $i ($expected)",expected.state,state.state)
            assertEquals("segment fix $i",0,r.match!!.projection.segmentIndex)
            assertEquals("progress fix $i",100.0,r.match.projection.distanceAlongRouteMeters,.01)
            assertEquals("quality fix $i",if(expected.accuracy>40) RouteMatchQuality.UNRELIABLE else RouteMatchQuality.MATCHED,r.match.quality)
        }
    }
    @Test fun crossTrackSpikeAndSmallNoiseDoNotConfirmDeviation() = replay(listOf(
        Expected(2.0,1000,RouteDeviationState.ON_ROUTE),Expected(-3.0,2000,RouteDeviationState.ON_ROUTE),
        Expected(100.0,3000,RouteDeviationState.SUSPECTED_OFF_ROUTE),Expected(0.0,4000,RouteDeviationState.ON_ROUTE)))
    @Test fun parallelRoadSameHeadingSustainedDeviationAndRecoverySequence() = replay(listOf(
        Expected(0.0,1000,RouteDeviationState.ON_ROUTE),
        Expected(70.0,2000,RouteDeviationState.SUSPECTED_OFF_ROUTE),
        Expected(70.0,3000,RouteDeviationState.SUSPECTED_OFF_ROUTE),
        Expected(70.0,5000,RouteDeviationState.OFF_ROUTE),
        Expected(0.0,6000,RouteDeviationState.RECOVERING),
        Expected(0.0,7000,RouteDeviationState.RECOVERING),
        Expected(0.0,8000,RouteDeviationState.ON_ROUTE)))
    @Test fun poorAccuracyDoesNotConfirmDeviationAndInterruptsEvidence() = replay(listOf(
        Expected(0.0,1000,RouteDeviationState.ON_ROUTE),
        Expected(100.0,2000,RouteDeviationState.SUSPECTED_OFF_ROUTE),
        Expected(100.0,3000,RouteDeviationState.UNKNOWN,100f),
        Expected(100.0,4000,RouteDeviationState.SUSPECTED_OFF_ROUTE),
        Expected(100.0,5000,RouteDeviationState.SUSPECTED_OFF_ROUTE)))
    @Test fun offRoutePoorAccuracyRetainsWarningButResetsEvidence() = replay(listOf(
        Expected(100.0,1000,RouteDeviationState.SUSPECTED_OFF_ROUTE),
        Expected(100.0,2000,RouteDeviationState.SUSPECTED_OFF_ROUTE),
        Expected(100.0,4000,RouteDeviationState.OFF_ROUTE),
        Expected(100.0,5000,RouteDeviationState.OFF_ROUTE,100f),
        Expected(0.0,6000,RouteDeviationState.RECOVERING)))
    @Test fun closeParallelRoadWithinAccuracyCannotBeDistinguishedFromNoise() = replay(listOf(
        Expected(15.0,1000,RouteDeviationState.ON_ROUTE),Expected(15.0,3000,RouteDeviationState.ON_ROUTE),
        Expected(15.0,5000,RouteDeviationState.ON_ROUTE)))
    @Test fun countAndDurationAreBothRequired() = replay(listOf(
        Expected(100.0,1000,RouteDeviationState.SUSPECTED_OFF_ROUTE),
        Expected(100.0,1001,RouteDeviationState.SUSPECTED_OFF_ROUTE),
        Expected(100.0,1002,RouteDeviationState.SUSPECTED_OFF_ROUTE),
        Expected(100.0,4000,RouteDeviationState.OFF_ROUTE)))
    @Test fun deviationRejectsDuplicateTimesWithoutCountingEvidence() {
        val d=RouteDeviationDetector();val r=result(straight(),fix(100.0,100.0)).match
        val a=d.update(RouteDeviationSnapshot(),r,5f,1000)
        assertEquals(a,d.update(a,r,5f,1000));assertEquals(a,d.update(a,r,5f,999))
    }

    @Test fun ambiguityDoesNotAdvanceAnchorOrConfirmDeviation() {
        val m=crossing();val r=result(m,fix(0.0,bearing=null))
        assertNull(r.state.anchor);assertNull(r.state.heldProgressMeters)
        val d=RouteDeviationDetector()
        assertEquals(RouteDeviationState.UNKNOWN,d.update(RouteDeviationSnapshot(),r.match,5f,1000).state)
    }
    @Test fun interruptedRecoveryDoesNotInventConfirmedOffRoute() {
        val m=straight();val d=RouteDeviationDetector()
        fun match(y:Double,t:Long)=result(m,fix(100.0,y,t)).match
        var s=d.update(RouteDeviationSnapshot(),match(0.0,1000),5f,1000)
        s=d.uncertain(s)
        s=d.update(s,match(0.0,2000),5f,2000)
        assertEquals(RouteDeviationState.RECOVERING,s.state)
        s=d.update(s,match(100.0,3000),5f,3000)
        assertEquals(RouteDeviationState.SUSPECTED_OFF_ROUTE,s.state)
        assertFalse(s.confirmedOffRoute)
    }
    @Test fun staleGapCannotSatisfySustainedDeviationDuration() {
        val m=straight();val d=RouteDeviationDetector()
        val r=result(m,fix(100.0,100.0)).match
        var s=d.update(RouteDeviationSnapshot(),r,5f,1000)
        s=d.update(s,r,5f,2000)
        s=d.update(s,r,5f,20000)
        assertEquals(RouteDeviationState.SUSPECTED_OFF_ROUTE,s.state)
        assertEquals(1,s.consecutiveOffRouteFixes)
    }
}
