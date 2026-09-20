package net.nobu0707.busnav.developer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface DeveloperConnectionRepository {
    val settings: Flow<DeveloperConnectionSettings>
    suspend fun update(settings: DeveloperConnectionSettings)
    suspend fun reset()
}

class DataStoreDeveloperConnectionRepository(
    private val store: DataStore<Preferences>,
    private val defaults: DeveloperConnectionSettings,
) : DeveloperConnectionRepository {
    override val settings = store.data.map { prefs ->
        DeveloperConnectionSettings(
            prefs[VALHALLA]?.let { runCatching { normalizeBaseUrl(it) }.getOrNull() } ?: defaults.valhallaBaseUrl,
            prefs[BASEMAP]?.let { runCatching { normalizeBaseUrl(it) }.getOrNull() } ?: defaults.basemapBaseUrl,
        )
    }
    override suspend fun update(settings: DeveloperConnectionSettings) {
        val valid = settings.normalized()
        store.edit { it[VALHALLA] = valid.valhallaBaseUrl; it[BASEMAP] = valid.basemapBaseUrl }
    }
    override suspend fun reset() { store.edit { it.remove(VALHALLA); it.remove(BASEMAP) } }
    private companion object {
        val VALHALLA = stringPreferencesKey("valhallaBaseUrl")
        val BASEMAP = stringPreferencesKey("basemapBaseUrl")
    }
}

/** Release never opens the development DataStore, including after a debug install. */
class FixedConnectionRepository(defaults: DeveloperConnectionSettings) : DeveloperConnectionRepository {
    override val settings = flowOf(defaults)
    override suspend fun update(settings: DeveloperConnectionSettings) = error("Development settings unavailable")
    override suspend fun reset() = Unit
}
