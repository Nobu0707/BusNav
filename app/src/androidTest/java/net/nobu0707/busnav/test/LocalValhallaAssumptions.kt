package net.nobu0707.busnav.test

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue

object LocalValhallaAssumptions {
    // Follow the application's Developer Connections on physical devices as well as emulators.
    val BASE_URL: String get() = kotlinx.coroutines.runBlocking {
        net.nobu0707.busnav.developer.createConnectionRepository(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext).settings.first().valhallaBaseUrl
    }

    fun assumeAvailable() {
        val client = OkHttpClient.Builder()
            .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val available = runCatching {
            client.newCall(
                Request.Builder()
                    .url("$BASE_URL/status")
                    .build(),
            ).execute().use { response -> response.isSuccessful }
        }.getOrDefault(false)

        assumeTrue("Local Valhalla is not available at $BASE_URL", available)
    }

    private const val PROBE_TIMEOUT_SECONDS = 2L
}
