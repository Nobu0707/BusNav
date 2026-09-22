package net.nobu0707.busnav.traffic

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.nobu0707.busnav.domain.traffic.*
import net.nobu0707.busnav.ui.traffic.*
import org.junit.Assert.*
import org.junit.Test

class TrafficFixtureTest {
    @Test fun defaultNoneAndAllScenariosAreExplicitSyntheticSnapshots() = runTest {
        val provider=DebugFixtureTrafficInformationProvider { 1000 }
        assertEquals(TrafficProviderStatus.NOT_CONFIGURED,provider.observeTraffic().first().status)
        for(scenario in TrafficFixtureScenario.entries) {
            provider.select(scenario)
            val snapshot=provider.observeTraffic().first()
            assertEquals(TrafficProviderStatus.AVAILABLE,snapshot.status)
            assertFalse(snapshot.source.isLive);assertEquals("開発用交通情報",snapshot.source.displayName)
            assertEquals(scenario==TrafficFixtureScenario.CLEAR,snapshot.events.isEmpty())
        }
        provider.disable();assertEquals(TrafficProviderStatus.NOT_CONFIGURED,provider.refresh().status)
    }
}
