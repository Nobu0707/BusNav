package net.nobu0707.busnav.developer

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.nobu0707.busnav.data.DataStoreNavigationMapPreferenceRepository
import net.nobu0707.busnav.domain.navigation.NavigationMapOrientation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NavigationMapPreferenceRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun saveCloseReopenAndInvalidFallback() = runBlocking {
        val file = temporary.root.resolve("navigation.preferences_pb")
        var job = SupervisorJob()
        var store = testPreferenceStore(file, CoroutineScope(Dispatchers.IO + job))
        try {
            var repository = DataStoreNavigationMapPreferenceRepository(store)
            assertEquals(HEADING_UP, repository.orientation.first())
            repository.setOrientation(NORTH_UP)
            job.cancelAndJoin()
            job = SupervisorJob()
            store = testPreferenceStore(file, CoroutineScope(Dispatchers.IO + job))
            repository = DataStoreNavigationMapPreferenceRepository(store)
            assertEquals(NORTH_UP, repository.orientation.first())
            store.edit { it[DataStoreNavigationMapPreferenceRepository.ORIENTATION] = "corrupt" }
            assertEquals(HEADING_UP, repository.orientation.first())
        } finally { job.cancelAndJoin() }
    }
}
