package net.nobu0707.busnav.data.routing.valhalla

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.RoutePlanPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routeplan.RoutingRequestResult
import net.nobu0707.busnav.domain.routeplan.toRoutingRequest
import net.nobu0707.busnav.domain.routing.RoutingResult
import net.nobu0707.busnav.test.LocalValhallaAssumptions
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValhallaRuntimeSmokeTest {
    @Test
    fun failingRouteSucceedsThreeTimesAgainstLocalValhalla() = runBlocking {
        LocalValhallaAssumptions.assumeAvailable()
        val client = OkHttpClient()
        val engine = ValhallaRoutingEngine(
            config = RoutingConfig(LocalValhallaAssumptions.BASE_URL),
            client = client,
            diagnostics = AndroidLogRoutingDiagnostics(),
        )
        val plan = RoutePlan(
            id = "android-runtime-smoke",
            name = "Android runtime smoke",
            points = listOf(
                RoutePlanPoint(
                    id = "start",
                    type = RoutePlanPointType.START,
                    position = GeoPoint(35.52755965924169, 138.79653353327427),
                ),
                RoutePlanPoint(
                    id = "destination",
                    type = RoutePlanPointType.DESTINATION,
                    position = GeoPoint(35.609542457517534, 138.29084069799353),
                ),
            ),
        )
        val request = (plan.toRoutingRequest() as RoutingRequestResult.Ready).request

        repeat(3) { attempt ->
            val result = engine.calculateRoute(request)
            assertTrue("attempt $attempt returned $result", result is RoutingResult.Success)
            val success = result as RoutingResult.Success
            assertTrue(success.summary.distanceMeters in 50_000.0..150_000.0)
            assertTrue(success.summary.durationSeconds > 0.0)
            assertTrue(success.route.geometry.points.size > 1)
        }
    }
}
