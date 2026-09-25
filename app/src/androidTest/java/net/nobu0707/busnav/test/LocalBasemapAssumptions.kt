package net.nobu0707.busnav.test

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import net.nobu0707.busnav.BuildConfig
import org.junit.Assume.assumeTrue
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request

object LocalBasemapAssumptions {
    val STYLE_URL: String get() = effectiveTestConnections().basemapConfig(true).styleUrl!!
    val BASE_URL: String get() = URI(STYLE_URL).let { "${it.scheme}://${it.rawAuthority}" }

    fun assumeAvailable() {
        val reachable = runCatching {
            client().newCall(Request.Builder().url(BASE_URL + "/fonts.json").build()).execute().use { true }
        }.getOrDefault(false)
        assumeTrue("Remote Japan map is unreachable", reachable)
        client().newCall(Request.Builder().url(STYLE_URL).build()).execute().use {
            org.junit.Assert.assertTrue("Remote Japan style is unavailable: HTTP " + it.code, it.isSuccessful)
        }
    }

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private const val PROBE_TIMEOUT_SECONDS = 3L
}
