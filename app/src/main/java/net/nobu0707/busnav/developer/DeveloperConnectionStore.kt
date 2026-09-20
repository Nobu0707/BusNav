package net.nobu0707.busnav.developer

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import net.nobu0707.busnav.BuildConfig

private val Context.developerConnections by preferencesDataStore(
    name = "developer_connections",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

fun createConnectionRepository(context: Context): DeveloperConnectionRepository {
    val defaults = DeveloperConnectionSettings.defaults(BuildConfig.VALHALLA_BASE_URL, BuildConfig.BASEMAP_STYLE_URL)
    return if (BuildConfig.DEBUG) DataStoreDeveloperConnectionRepository(context.applicationContext.developerConnections, defaults)
    else FixedConnectionRepository(defaults)
}
