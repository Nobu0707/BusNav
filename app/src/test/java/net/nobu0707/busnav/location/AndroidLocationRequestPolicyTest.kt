package net.nobu0707.busnav.location

import org.junit.Assert.*
import org.junit.Test

class AndroidLocationRequestPolicyTest {
    @Test fun preciseNavigationRequestsStationaryUpdates() {
        assertEquals(listOf("gps", "network"), AndroidLocationRequestPolicy.providers(true))
        assertEquals(1_000L, AndroidLocationRequestPolicy.updateIntervalMillis)
        assertEquals(0f, AndroidLocationRequestPolicy.minimumDistanceMeters)
    }
    @Test fun approximatePermissionDoesNotRequestGps() {
        assertEquals(listOf("network"), AndroidLocationRequestPolicy.providers(false))
    }
}
