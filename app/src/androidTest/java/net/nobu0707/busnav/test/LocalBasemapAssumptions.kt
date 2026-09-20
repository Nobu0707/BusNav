package net.nobu0707.busnav.test

import java.util.concurrent.TimeUnit
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request

object LocalBasemapAssumptions {
    const val BASE_URL = "http://10.0.2.2:8080"
    const val STYLE_URL = "$BASE_URL/styles/busnav/style.json"

    fun assumeAvailable(): Boolean {
        val available = runCatching {
            client().newCall(Request.Builder().url(STYLE_URL).build())
                .execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
        if (!available) {
            Log.i(TAG, "SKIP live basemap smoke: TileServer GL is unavailable at $STYLE_URL")
        }
        return available
    }

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private const val PROBE_TIMEOUT_SECONDS = 3L
    private const val TAG = "BusNavBasemapTest"
}
