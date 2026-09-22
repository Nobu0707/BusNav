package net.nobu0707.busnav.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.nobu0707.busnav.domain.navigation.NavigationMapOrientation

interface NavigationMapPreferenceRepository {
    val orientation: Flow<NavigationMapOrientation>
    suspend fun setOrientation(orientation: NavigationMapOrientation)
    suspend fun toggleOrientation()
}

class DataStoreNavigationMapPreferenceRepository(private val store: DataStore<Preferences>) : NavigationMapPreferenceRepository {
    override val orientation = store.data.map { NavigationMapOrientation.fromStored(it[ORIENTATION]) }
    override suspend fun setOrientation(orientation: NavigationMapOrientation) {
        store.edit { it[ORIENTATION] = orientation.storedValue }
    }
    override suspend fun toggleOrientation() {
        store.edit { it[ORIENTATION] = NavigationMapOrientation.fromStored(it[ORIENTATION]).toggled().storedValue }
    }
    companion object { val ORIENTATION = stringPreferencesKey("navigation_map_orientation") }
}

private val Context.navigationMapPreferences by preferencesDataStore(name = "navigation_map_preferences")
fun navigationMapPreferenceRepository(context: Context): NavigationMapPreferenceRepository =
    DataStoreNavigationMapPreferenceRepository(context.applicationContext.navigationMapPreferences)
