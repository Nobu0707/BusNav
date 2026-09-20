package net.nobu0707.busnav.test

import java.util.concurrent.TimeUnit
import net.nobu0707.busnav.BuildConfig
import org.junit.Assume.assumeTrue
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request

object LocalBasemapAssumptions {
    val STYLE_URL = BuildConfig.BASEMAP_STYLE_URL
    val BASE_URL: String = URI(STYLE_URL).let { "${it.scheme}://${it.rawAuthority}" }

    fun assumeAvailable() {
        val available = runCatching {
            client().newCall(Request.Builder().url(STYLE_URL).build())
                .execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
        assumeTrue("Local TileServer is unavailable", available)
    }

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private const val PROBE_TIMEOUT_SECONDS = 3L
}
