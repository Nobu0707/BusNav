package net.nobu0707.busnav.test

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.nobu0707.busnav.developer.DeveloperConnectionSettings
import net.nobu0707.busnav.developer.createConnectionRepository

/** Test APK installation can clear app data. Endpoints are supplied at runtime, never committed. */
fun effectiveTestConnections(): DeveloperConnectionSettings = runBlocking {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val args = InstrumentationRegistry.getArguments()
    val repository = createConnectionRepository(instrumentation.targetContext)
    val current = repository.settings.first()
    val valhalla = args.getString("testValhallaBaseUrl")
    val basemap = args.getString("testBasemapBaseUrl")
    if (valhalla == null && basemap == null) return@runBlocking current
    val effective = current.copy(
        valhallaBaseUrl = valhalla ?: current.valhallaBaseUrl,
        basemapBaseUrl = basemap ?: current.basemapBaseUrl,
    ).normalized()
    if (current != effective) repository.update(effective)
    effective
}
