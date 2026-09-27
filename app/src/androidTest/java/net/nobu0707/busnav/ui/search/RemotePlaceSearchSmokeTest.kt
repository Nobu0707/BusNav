package net.nobu0707.busnav.ui.search

import java.net.Inet6Address
import java.net.NetworkInterface
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.nobu0707.busnav.domain.search.PlaceSearchContext
import net.nobu0707.busnav.domain.search.PlaceSearchResult
import net.nobu0707.busnav.search.NominatimPlaceSearchProvider
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Production host only. Devices without global IPv6 cannot run this smoke. */
class RemotePlaceSearchSmokeTest {
    @Test fun japaneseStationQueryReachesProductionSearch() = runBlocking {
        val globalIpv6 = NetworkInterface.getNetworkInterfaces().asSequence().any { network ->
            network.inetAddresses.asSequence().any { address ->
                address is Inet6Address && !address.isAnyLocalAddress && !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress && !address.isSiteLocalAddress
            }
        }
        assumeTrue("Production search host requires global IPv6", globalIpv6)
        val result = withTimeout(30_000) {
            NominatimPlaceSearchProvider().search("東京駅", PlaceSearchContext())
        }
        assertTrue("Production search did not return a candidate",
            result is PlaceSearchResult.Success && result.items.isNotEmpty())
    }
}
