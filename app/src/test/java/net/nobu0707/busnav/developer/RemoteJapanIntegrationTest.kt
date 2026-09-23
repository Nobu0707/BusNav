package net.nobu0707.busnav.developer

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.nobu0707.busnav.data.route.createDevelopmentSampleRoute
import net.nobu0707.busnav.domain.route.ScheduledRouteRepository
import net.nobu0707.busnav.location.LocationProvider
import net.nobu0707.busnav.location.LocationUpdate
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.ui.navigation.NavigationStateHolder
import net.nobu0707.busnav.ui.navigation.navigationActive
import net.nobu0707.busnav.ui.settings.developer.message
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteJapanIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val local = DeveloperConnectionSettings("http://10.0.2.2:8002", "http://10.0.2.2:8080")
    private val lan = DeveloperConnectionSettings("http://192.168.1.100:8002", "http://192.168.1.100:8080", BasemapRegion.CHUBU)

    @Test fun japanAndLegacyRegionDecode() {
        assertEquals(BasemapRegion.JAPAN, BasemapRegion.fromId("japan"))
        assertEquals(BasemapRegion.KANTO, BasemapRegion.fromId("kanto"))
        assertEquals(BasemapRegion.CHUBU, BasemapRegion.fromId("chubu"))
        assertEquals(BasemapRegion.KANTO, BasemapRegion.fromId("future"))
        assertEquals(BasemapRegion.KANTO, BasemapRegion.fromId(null))
    }

    @Test fun environmentDecodeNeverOptsLegacyIntoRemote() {
        ConnectionEnvironment.entries.forEach { assertEquals(it, ConnectionEnvironment.fromId(it.id)) }
        assertEquals(ConnectionEnvironment.CUSTOM, ConnectionEnvironment.fromId(null))
        assertEquals(ConnectionEnvironment.CUSTOM, ConnectionEnvironment.fromId("future"))
    }

    @Test fun remoteHasExactCanonicalEndpointsAndNationwideRegion() {
        val remote = lan.forEnvironment(ConnectionEnvironment.REMOTE_TEST).normalized()
        assertEquals(ConnectionEnvironment.REMOTE_TEST, remote.selectedConnectionEnvironment)
        assertEquals("https://routing-busnav.nobu0707.net", remote.valhallaBaseUrl)
        assertEquals("https://maps-busnav.nobu0707.net", remote.basemapBaseUrl)
        assertEquals(BasemapRegion.JAPAN, remote.basemapRegion)
        for (debug in listOf(true, false)) {
            val config = remote.basemapConfig(debug)
            assertEquals("https://maps-busnav.nobu0707.net/styles/busnav-light/style.json", config.withTheme(false).styleUrl)
            assertEquals("https://maps-busnav.nobu0707.net/styles/busnav/style.json", config.withTheme(true).styleUrl)
            assertEquals(config, config.withTheme(false).withTheme(true))
        }
    }

    @Test fun remoteRejectsHttpCustomHostsAndRegionalOverrides() {
        val remote = RemoteTestEndpoints.settings()
        listOf(remote.copy(valhallaBaseUrl = "http://routing-busnav.nobu0707.net"),
            remote.copy(basemapBaseUrl = "http://maps-busnav.nobu0707.net"),
            remote.copy(basemapBaseUrl = lan.basemapBaseUrl),
            remote.copy(basemapRegion = BasemapRegion.KANTO), remote.copy(basemapRegion = BasemapRegion.CHUBU)
        ).forEach {
            assertTrue(runCatching { it.normalized() }.isFailure)
            assertTrue(runCatching { it.basemapConfig(true) }.isFailure)
        }
    }

    @Test fun localAndCustomUrlsAndRegionsRemainCompatible() {
        val emulator = lan.forEnvironment(ConnectionEnvironment.LOCAL_EMULATOR)
        assertEquals(local.valhallaBaseUrl, emulator.valhallaBaseUrl)
        assertEquals(local.basemapBaseUrl, emulator.basemapBaseUrl)
        assertEquals(BasemapRegion.CHUBU, emulator.basemapRegion)
        for (environment in listOf(ConnectionEnvironment.CUSTOM, ConnectionEnvironment.LOCAL_LAN)) {
            assertEquals(lan.copy(selectedConnectionEnvironment = environment), lan.forEnvironment(environment))
        }
        assertEquals("http://10.0.2.2:8080/styles/busnav-kanto/style.json", local.basemapConfig(true).styleUrl)
        assertEquals("http://192.168.1.100:8080/styles/busnav-chubu-light/style.json", lan.basemapConfig(true).withTheme(false).styleUrl)
        // The legacy unqualified build-time style keeps its original local region default.
        assertEquals(BasemapRegion.KANTO, DeveloperConnectionSettings.defaults(local.valhallaBaseUrl,
            local.basemapBaseUrl + "/styles/busnav/style.json").basemapRegion)
    }

    @Test fun remotePersistenceReopenAndSavedLocalProfiles() = runBlocking {
        val file = temporary.root.resolve("profiles.preferences_pb")
        var job = SupervisorJob()
        fun repository() = DataStoreDeveloperConnectionRepository(testPreferenceStore(file, CoroutineScope(Dispatchers.IO + job)), local)
        var repo = repository()
        try {
            repo.update(lan)
            val savedLan = lan.copy(selectedConnectionEnvironment = ConnectionEnvironment.LOCAL_LAN)
            repo.update(savedLan)
            repo.update(repo.profile(ConnectionEnvironment.REMOTE_TEST))
            job.cancelAndJoin(); job = SupervisorJob(); repo = repository()
            assertEquals(RemoteTestEndpoints.settings(), repo.settings.first())
            assertEquals(savedLan, repo.profile(ConnectionEnvironment.LOCAL_LAN))
            assertEquals(lan, repo.profile(ConnectionEnvironment.CUSTOM))
            assertEquals(BasemapRegion.CHUBU, repo.profile(ConnectionEnvironment.LOCAL_EMULATOR).basemapRegion)
            repo.update(repo.profile(ConnectionEnvironment.LOCAL_LAN))
            assertEquals(savedLan, repo.settings.first())
            repo.reset()
            assertEquals(local, repo.settings.first())
            assertEquals(local.copy(selectedConnectionEnvironment = ConnectionEnvironment.LOCAL_LAN), repo.profile(ConnectionEnvironment.LOCAL_LAN))
        } finally { job.cancelAndJoin() }
    }

    @Test fun legacyLanRecordWithoutEnvironmentPreservesEveryValue() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = testPreferenceStore(temporary.root.resolve("legacy.preferences_pb"), CoroutineScope(Dispatchers.IO + job))
            val legacy = lan.copy(basemapRegion = BasemapRegion.KANTO)
            store.edit {
                it[stringPreferencesKey("valhallaBaseUrl")] = legacy.valhallaBaseUrl
                it[stringPreferencesKey("basemapBaseUrl")] = legacy.basemapBaseUrl
                it[stringPreferencesKey("basemapRegion")] = "kanto"
            }
            val repo = DataStoreDeveloperConnectionRepository(store, local)
            assertEquals(legacy, repo.settings.first())
            assertNull(store.data.first()[stringPreferencesKey("selectedConnectionEnvironment")])
            repo.update(repo.profile(ConnectionEnvironment.REMOTE_TEST))
            assertEquals(legacy.copy(selectedConnectionEnvironment = ConnectionEnvironment.LOCAL_LAN), repo.profile(ConnectionEnvironment.LOCAL_LAN))
            assertEquals(legacy, repo.profile(ConnectionEnvironment.CUSTOM))
        } finally { job.cancelAndJoin() }
    }

    @Test fun remoteRecordCannotDecodeAsLocalEvenWithCorruptRegionAndUrls() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = testPreferenceStore(temporary.root.resolve("repair.preferences_pb"), CoroutineScope(Dispatchers.IO + job))
            store.edit {
                it[stringPreferencesKey("selectedConnectionEnvironment")] = "remote_test"
                it[stringPreferencesKey("basemapRegion")] = "unknown"
                it[stringPreferencesKey("basemapBaseUrl")] = lan.basemapBaseUrl
                it[stringPreferencesKey("valhallaBaseUrl")] = lan.valhallaBaseUrl
            }
            assertEquals(RemoteTestEndpoints.settings(), DataStoreDeveloperConnectionRepository(store, local).settings.first())
        } finally { job.cancelAndJoin() }
    }

    @Test fun dataStoreEmitsOnlyWholeProfiles() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = testPreferenceStore(temporary.root.resolve("atomic.preferences_pb"), CoroutineScope(Dispatchers.IO + job))
            val repo = DataStoreDeveloperConnectionRepository(store, local)
            val emissions = mutableListOf<DeveloperConnectionSettings>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { repo.settings.collect { emissions += it } }
            repo.settings.first()
            repo.update(RemoteTestEndpoints.settings())
            repo.settings.first { it.selectedConnectionEnvironment == ConnectionEnvironment.REMOTE_TEST }
            yield()
            collector.cancelAndJoin()
            assertTrue(emissions.isNotEmpty())
            assertTrue(emissions.contains(RemoteTestEndpoints.settings()))
            assertTrue(emissions.all { it == local || it == RemoteTestEndpoints.settings() })
            val prefs = store.data.first()
            assertEquals("remote_test", prefs[stringPreferencesKey("selectedConnectionEnvironment")])
            assertEquals("japan", prefs[stringPreferencesKey("basemapRegion")])
        } finally { job.cancelAndJoin() }
    }

    @Test fun navigationGuardBlocksApplyAndResetWithoutMutatingRepository() = runBlocking {
        val repo = FakeConnections(local)
        assertFalse(ConnectionSwitchPolicy.canEdit(true))
        assertTrue(runCatching { repo.updateWhenIdle(RemoteTestEndpoints.settings(), true) }.isFailure)
        assertTrue(runCatching { repo.resetWhenIdle(true) }.isFailure)
        assertEquals(local, repo.settings.value)
        assertEquals(0, repo.writes)
        repo.updateWhenIdle(RemoteTestEndpoints.settings(), false)
        assertEquals(RemoteTestEndpoints.settings(), repo.settings.value)
        repo.resetWhenIdle(false)
        assertEquals(local, repo.settings.value)
    }

    @Test fun profileSwitchRequestsStyleReloadAndPreservesNavigationRouteState() = runBlocking {
        val job = SupervisorJob()
        try {
            val route = createDevelopmentSampleRoute()
            val navigation = NavigationStateHolder(object : LocationProvider {
                override fun updates(): Flow<LocationUpdate> = emptyFlow()
                override fun isLocationEnabled() = true
            }, object : ScheduledRouteRepository { override suspend fun getActiveRoute() = route },
                CoroutineScope(job + Dispatchers.Unconfined), Dispatchers.Unconfined)
            navigation.onMapReady()
            val before = navigation.uiState.value
            val repo = FakeConnections(local)
            val controller = BasemapController(local.basemapConfig(true), NoOpMapDiagnostics) {}
            controller.onStyleLoaded()
            repo.updateWhenIdle(repo.profile(ConnectionEnvironment.REMOTE_TEST), before.navigationActive)
            val remote = repo.settings.value
            assertEquals(RemoteTestEndpoints.ROUTING, remote.valhallaBaseUrl)
            assertEquals(RemoteTestEndpoints.MAP, remote.basemapBaseUrl)
            assertEquals(BasemapRegion.JAPAN, remote.basemapRegion)
            assertEquals(RemoteTestEndpoints.LIGHT_STYLE, controller.updateConfig(remote.basemapConfig(true).withTheme(false)))
            assertEquals(BasemapState.LOADING, controller.state)
            controller.onStyleLoaded(); navigation.onMapReady()
            assertEquals(before, navigation.uiState.value)
            assertSame(route, navigation.uiState.value.activeRoute)
            assertEquals(RemoteTestEndpoints.DARK_STYLE, controller.updateConfig(remote.basemapConfig(true).withTheme(true)))
            controller.onStyleLoaded()
            assertEquals(before, navigation.uiState.value)
        } finally { job.cancelAndJoin() }
    }

    @Test fun nationwideConfigNeverReloadsOnPanOrFallsBackToRegionalSource() {
        val config = RemoteTestEndpoints.settings().basemapConfig(true).withTheme(false)
        val controller = BasemapController(config, NoOpMapDiagnostics) {}
        assertEquals(RemoteTestEndpoints.LIGHT_STYLE, controller.initialStyle())
        controller.onStyleLoaded()
        // Position is deliberately not an input of the config/resolver (Sapporo through Naha).
        repeat(10) { assertNull(controller.updateConfig(config)) }
        assertNull(controller.onMapLoadFailed("nationwide tile request failed"))
        assertEquals(BasemapState.UNAVAILABLE, controller.state)
        val failed = BasemapController(config, NoOpMapDiagnostics) {}
        assertEquals("asset://basemap/fallback-light-style.json", failed.onMapLoadFailed("style failed"))
        failed.onStyleLoaded()
        assertEquals(BasemapState.UNAVAILABLE, failed.state)
    }

    @Test fun remoteFailureHintDoesNotClaimDnsDiagnosis() {
        for (status in listOf(ConnectionStatus.TIMEOUT, ConnectionStatus.HOST_ERROR, ConnectionStatus.HTTP_ERROR)) {
            val result = ConnectionResult(status, 503)
            assertEquals("現在のリモートテストサーバーはIPv6接続が必要です", result.failureHint(ConnectionEnvironment.REMOTE_TEST))
            assertTrue(result.message(ConnectionEnvironment.REMOTE_TEST).contains(RemoteTestEndpoints.IPV6_HINT))
            assertNull(result.failureHint(ConnectionEnvironment.CUSTOM))
        }
        assertNull(ConnectionResult(ConnectionStatus.SUCCESS).failureHint(ConnectionEnvironment.REMOTE_TEST))
        assertEquals(RemoteTestEndpoints.IPV6_HINT, RemoteTestEndpoints.settings().basemapConfig(true).withTheme(false).failureHint)
        assertNull(local.basemapConfig(true).failureHint)
    }

    @Test fun releaseCleartextPolicyAndDevelopmentStoreIsolationRemainIntact() {
        assertEquals(BasemapMode.FALLBACK, local.basemapConfig(false).mode)
        assertEquals(BasemapMode.FALLBACK, lan.basemapConfig(false).mode)
        assertEquals(BasemapMode.REMOTE_STYLE, RemoteTestEndpoints.settings().basemapConfig(false).mode)
        val releaseManifest = File("src/release/AndroidManifest.xml").readText()
        assertTrue(releaseManifest.contains("android:usesCleartextTraffic=\"false\""))
        assertFalse(File("src/main/AndroidManifest.xml").readText().contains("usesCleartextTraffic=\"true\""))
        runBlocking {
            val fixed = FixedConnectionRepository(DeveloperConnectionSettings("", ""))
            assertTrue(runCatching { fixed.update(RemoteTestEndpoints.settings()) }.isFailure)
            assertEquals(DeveloperConnectionSettings("", ""), fixed.settings.first())
        }
    }

    private class FakeConnections(private val initial: DeveloperConnectionSettings) : DeveloperConnectionRepository {
        override val settings = MutableStateFlow(initial)
        var writes = 0
        override suspend fun update(settings: DeveloperConnectionSettings) { this.settings.value = settings.normalized(); writes++ }
        override suspend fun reset() { settings.value = initial; writes++ }
    }
}
