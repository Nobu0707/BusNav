package net.nobu0707.busnav.developer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import net.nobu0707.busnav.map.basemap.BasemapRegion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

interface DeveloperConnectionRepository {
    val settings: Flow<DeveloperConnectionSettings>
    suspend fun update(settings: DeveloperConnectionSettings)
    suspend fun reset()
    suspend fun profile(environment: ConnectionEnvironment): DeveloperConnectionSettings =
        settings.first().forEnvironment(environment)
}

suspend fun DeveloperConnectionRepository.updateWhenIdle(settings: DeveloperConnectionSettings, navigationActive: Boolean) {
    ConnectionSwitchPolicy.requireEditable(navigationActive)
    update(settings)
}

suspend fun DeveloperConnectionRepository.resetWhenIdle(navigationActive: Boolean) {
    ConnectionSwitchPolicy.requireEditable(navigationActive)
    reset()
}

class DataStoreDeveloperConnectionRepository(
    private val store: DataStore<Preferences>,
    private val defaults: DeveloperConnectionSettings,
) : DeveloperConnectionRepository {
    private fun decode(prefs: Preferences): DeveloperConnectionSettings {
        val environment = ConnectionEnvironment.fromId(prefs[ENVIRONMENT])
        // Repair an inconsistent remote record as one canonical snapshot, never a regional fallback.
        if (environment == ConnectionEnvironment.REMOTE_TEST) return RemoteTestEndpoints.settings()
        return DeveloperConnectionSettings(
            prefs[VALHALLA]?.let { runCatching { normalizeBaseUrl(it) }.getOrNull() } ?: defaults.valhallaBaseUrl,
            prefs[BASEMAP]?.let { runCatching { normalizeBaseUrl(it) }.getOrNull() } ?: defaults.basemapBaseUrl,
            prefs[REGION]?.let(BasemapRegion::fromId) ?: defaults.basemapRegion,
            environment,
        )
    }
    override val settings = store.data.map(::decode)

    override suspend fun profile(environment: ConnectionEnvironment): DeveloperConnectionSettings {
        val prefs = store.data.first()
        val current = decode(prefs)
        if (environment == ConnectionEnvironment.REMOTE_TEST) return RemoteTestEndpoints.settings()
        val saved = readProfile(prefs, environment)
            ?: readProfile(prefs, ConnectionEnvironment.CUSTOM)
            ?: current.takeUnless { it.selectedConnectionEnvironment == ConnectionEnvironment.REMOTE_TEST }
            ?: defaults
        return saved.forEnvironment(environment)
    }

    override suspend fun update(settings: DeveloperConnectionSettings) {
        val valid = settings.normalized()
        store.edit {
            saveProfile(it, decode(it))
            it[VALHALLA] = valid.valhallaBaseUrl
            it[BASEMAP] = valid.basemapBaseUrl
            it[REGION] = valid.basemapRegion.id
            it[ENVIRONMENT] = valid.selectedConnectionEnvironment.id
            saveProfile(it, valid)
        }
    }
    override suspend fun reset() {
        store.edit { prefs ->
            prefs.remove(VALHALLA); prefs.remove(BASEMAP); prefs.remove(REGION); prefs.remove(ENVIRONMENT)
            ConnectionEnvironment.entries.forEach { environment ->
                listOf("routing", "map", "region").forEach { prefs.remove(profileKey(environment, it)) }
            }
        }
    }

    private fun saveProfile(prefs: androidx.datastore.preferences.core.MutablePreferences, value: DeveloperConnectionSettings) {
        val environment = value.selectedConnectionEnvironment
        if (environment == ConnectionEnvironment.REMOTE_TEST) return
        prefs[profileKey(environment, "routing")] = value.valhallaBaseUrl
        prefs[profileKey(environment, "map")] = value.basemapBaseUrl
        prefs[profileKey(environment, "region")] = value.basemapRegion.id
    }

    private fun readProfile(prefs: Preferences, environment: ConnectionEnvironment): DeveloperConnectionSettings? {
        val routing = prefs[profileKey(environment, "routing")] ?: return null
        val map = prefs[profileKey(environment, "map")] ?: return null
        return runCatching {
            DeveloperConnectionSettings(routing, map, BasemapRegion.fromId(prefs[profileKey(environment, "region")]), environment).normalized()
        }.getOrNull()
    }

    private companion object {
        val VALHALLA = stringPreferencesKey("valhallaBaseUrl")
        val BASEMAP = stringPreferencesKey("basemapBaseUrl")
        val REGION = stringPreferencesKey("basemapRegion")
        val ENVIRONMENT = stringPreferencesKey("selectedConnectionEnvironment")
        fun profileKey(environment: ConnectionEnvironment, field: String) =
            stringPreferencesKey("connectionProfile.${environment.id}.$field")
    }
}

/** Release never opens the development DataStore, including after a debug install. */
class FixedConnectionRepository(defaults: DeveloperConnectionSettings) : DeveloperConnectionRepository {
    override val settings = flowOf(defaults)
    override suspend fun update(settings: DeveloperConnectionSettings) = error("Development settings unavailable")
    override suspend fun reset() = Unit
}
