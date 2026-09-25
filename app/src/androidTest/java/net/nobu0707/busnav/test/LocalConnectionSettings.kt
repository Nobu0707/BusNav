package net.nobu0707.busnav.test

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.nobu0707.busnav.developer.DeveloperConnectionSettings
import net.nobu0707.busnav.developer.RemoteTestEndpoints
import net.nobu0707.busnav.developer.createConnectionRepository

/** Live infrastructure checks always use the canonical remote profile. */
fun effectiveTestConnections(): DeveloperConnectionSettings = runBlocking {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val repository = createConnectionRepository(instrumentation.targetContext)
    val current = repository.settings.first()
    val effective = RemoteTestEndpoints.settings()
    if (current != effective) repository.update(effective)
    effective
}
