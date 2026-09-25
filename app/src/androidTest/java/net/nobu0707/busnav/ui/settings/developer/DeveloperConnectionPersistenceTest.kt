package net.nobu0707.busnav.ui.settings.developer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.nobu0707.busnav.developer.*
import org.junit.Assert.assertEquals
import org.junit.Test

class DeveloperConnectionPersistenceTest {
    @Test fun androidStorageSurvivesRecreationAndReset() = runBlocking {
        val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test-connections.preferences_pb")
        file.delete()
        var job = SupervisorJob()
        val defaults = DeveloperConnectionSettings("http://10.0.2.2:8002", "http://10.0.2.2:8080")
        fun repo() = DataStoreDeveloperConnectionRepository(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }), defaults)
        var repository = repo()
        val lan = DeveloperConnectionSettings("http://192.168.1.100:8002", "http://192.168.1.100:8080", net.nobu0707.busnav.map.basemap.BasemapRegion.CHUBU)
        try {
            repository.update(lan)
            job.cancelAndJoin(); job = SupervisorJob(); repository = repo()
            assertEquals(lan.copy(basemapRegion = net.nobu0707.busnav.map.basemap.BasemapRegion.JAPAN), repository.settings.first())
            repository.reset()
            assertEquals(defaults, repository.settings.first())
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
