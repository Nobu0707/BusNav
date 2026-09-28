package net.nobu0707.busnav.facility

import java.net.Inet6Address
import java.net.NetworkInterface
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.nobu0707.busnav.BuildConfig
import net.nobu0707.busnav.data.facility.HttpRouteFacilityProvider
import net.nobu0707.busnav.data.routing.valhalla.RoutingConfig
import net.nobu0707.busnav.data.routing.valhalla.ValhallaRoutingEngine
import net.nobu0707.busnav.domain.facility.RouteFacilityQueryOptions
import net.nobu0707.busnav.domain.facility.RouteFacilityQueryResult
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlan
import net.nobu0707.busnav.domain.routeplan.RoutePlanPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType
import net.nobu0707.busnav.domain.routeplan.RoutingRequestResult
import net.nobu0707.busnav.domain.routeplan.toRoutingRequest
import net.nobu0707.busnav.domain.routing.RoutingResult
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run on a physical device with global IPv6. Do not log route geometry or device identity. */
class RouteFacilityLiveSmokeTest {
    @Test fun tokyoNagoyaRouteReturnsProgressOrderedFacilityCandidates() = runBlocking {
        val globalIpv6 = NetworkInterface.getNetworkInterfaces().asSequence().any { network ->
            network.inetAddresses.asSequence().any { address ->
                address is Inet6Address && !address.isAnyLocalAddress && !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress && !address.isSiteLocalAddress
            }
        }
        assumeTrue("Production facility host requires global IPv6", globalIpv6)
        val plan = RoutePlan("facility-live-smoke", "Tokyo to Nagoya", listOf(
            RoutePlanPoint("start", RoutePlanPointType.START, GeoPoint(35.681236, 139.767125)),
            RoutePlanPoint("destination", RoutePlanPointType.DESTINATION, GeoPoint(35.170915, 136.881537)),
        ))
        val request = (plan.toRoutingRequest() as RoutingRequestResult.Ready).request
        val routing = withTimeout(120_000) {
            ValhallaRoutingEngine(RoutingConfig(BuildConfig.VALHALLA_BASE_URL)).calculateRoute(request)
        }
        assertTrue("Remote routing failed: $routing", routing is RoutingResult.Success)
        val route = (routing as RoutingResult.Success).route
        val result = withTimeout(45_000) {
            HttpRouteFacilityProvider().findFacilities(route.geometry, RouteFacilityQueryOptions())
        }
        assertTrue("Remote facility query failed: $result", result is RouteFacilityQueryResult.Success)
        val candidates = (result as RouteFacilityQueryResult.Success).candidates
        assertTrue("Expected Tokyo to Nagoya candidates", candidates.isNotEmpty())
        assertTrue(candidates.zipWithNext().all { (a, b) -> a.routeProgressMeters <= b.routeProgressMeters })
    }
}
