package net.nobu0707.busnav.developer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.nobu0707.busnav.BuildConfig
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeveloperConnectionRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val defaults = DeveloperConnectionSettings.defaults(BuildConfig.VALHALLA_BASE_URL, BuildConfig.BASEMAP_STYLE_URL)

    @Test fun defaultsSaveReloadResetAndReleaseIsolation() = runBlocking {
        val file = temporary.root.resolve("settings.preferences_pb")
        var job = SupervisorJob()
        fun repo() = DataStoreDeveloperConnectionRepository(testPreferenceStore(file, CoroutineScope(Dispatchers.IO + job)), defaults)
        var repository = repo()
        try {
            assertEquals(defaults, repository.settings.first())
            repository.update(DeveloperConnectionSettings(" http://192.168.1.100:8002/ ", "https://dev.example.com/"))
            val saved = DeveloperConnectionSettings("http://192.168.1.100:8002", "https://dev.example.com")
            assertEquals(saved, repository.settings.first())
            assertEquals("https://dev.example.com/styles/busnav/style.json", saved.basemapConfig(true).styleUrl)
            job.cancelAndJoin()
            job = SupervisorJob()
            repository = repo()
            assertEquals(saved, repository.settings.first())
            val release = FixedConnectionRepository(DeveloperConnectionSettings("", ""))
            assertEquals(DeveloperConnectionSettings("", ""), release.settings.first())
            assertTrue(runCatching { release.update(saved) }.isFailure)
            assertTrue(runCatching { repository.update(saved.copy(valhallaBaseUrl = "http://user:pass@host")) }.isFailure)
            assertEquals(saved, repository.settings.first())
            repository.reset()
            assertEquals(defaults, repository.settings.first())
        } finally { job.cancelAndJoin() }
    }

    @Test fun acceptsAndNormalizesDevelopmentEndpoints() {
        listOf("http://10.0.2.2:8002", "http://192.168.1.100:8080", "https://dev.example.com", "http://[::1]:8080").forEach {
            assertEquals(it, normalizeBaseUrl("  $it/  "))
        }
    }

    @Test fun rejectsMalformedOrCredentialBearingUrls() {
        listOf("", "host:8002", "ftp://host", "http:///path", "http://host/path", "http://host/../",
            "http://host/?", "http://host/?token=secret", "http://host/#", "http://user:pass@host",
            "http://@host", "http://host:", "http://host:0", "http://host:65536", "http://ho st", "http://host/%2f").forEach {
            assertTrue("must reject $it", runCatching { normalizeBaseUrl(it) }.isFailure)
        }
    }
}
