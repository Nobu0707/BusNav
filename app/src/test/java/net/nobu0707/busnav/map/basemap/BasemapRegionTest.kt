package net.nobu0707.busnav.map.basemap

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.nobu0707.busnav.developer.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BasemapRegionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun defaultAndRegionalUrlsAreIndependentOfRouting() {
        val settings = DeveloperConnectionSettings("http://localhost:8002", "http://localhost:8080")
        assertEquals(BasemapRegion.JAPAN, settings.basemapRegion)
        val chubu = settings.copy(basemapRegion = BasemapRegion.CHUBU)
        assertEquals("http://localhost:8080/styles/busnav/style.json", settings.basemapConfig(true).styleUrl)
        assertEquals(settings.basemapConfig(true), chubu.basemapConfig(true))
        assertEquals(settings.valhallaBaseUrl, chubu.valhallaBaseUrl)
        assertEquals(BasemapMode.FALLBACK, chubu.basemapConfig(false).mode)
        assertEquals(BasemapRegion.JAPAN, BasemapRegion.fromId("unknown"))
        assertEquals(BasemapRegion.JAPAN, BasemapRegion.fromId(null))
    }

    @Test fun invalidStoredRegionFallsBackAndResetRemovesRegion() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = testPreferenceStore(temporary.root.resolve("region.preferences_pb"), CoroutineScope(Dispatchers.IO + job))
            val defaults = DeveloperConnectionSettings("http://localhost:8002", "http://localhost:8080")
            val repo = DataStoreDeveloperConnectionRepository(store, defaults)
            val key = stringPreferencesKey("basemapRegion")
            store.edit { it[key] = "unknown" }
            assertEquals(BasemapRegion.JAPAN, repo.settings.first().basemapRegion)
            repo.update(defaults.copy(basemapRegion = BasemapRegion.CHUBU))
            assertEquals("japan", store.data.first()[key])
            repo.reset()
            assertNull(store.data.first()[key])
            assertEquals(defaults, repo.settings.first())
        } finally { job.cancelAndJoin() }
    }
}
