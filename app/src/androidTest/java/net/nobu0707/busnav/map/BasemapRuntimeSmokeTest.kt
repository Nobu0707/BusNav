package net.nobu0707.busnav.map

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.nobu0707.busnav.MainActivity
import net.nobu0707.busnav.test.LocalBasemapAssumptions
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasemapRuntimeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun localStyleVectorTileAndJapaneseGlyphLoadWhenServerIsAvailable() {
        if (!LocalBasemapAssumptions.assumeAvailable()) return
        val client = LocalBasemapAssumptions.client()

        assertResource(client, "/styles/busnav/style.json", minimumBytes = 1_000)
        assertResource(client, "/data/chubu/10/906/404.pbf", minimumBytes = 100)
        assertResource(
            client,
            "/fonts/Klokantech%20Noto%20Sans%20CJK%20Regular/12288-12543.pbf",
            minimumBytes = 100,
        )

        composeRule.waitUntil(timeoutMillis = 30_000) {
            val node = composeRule.onNodeWithTag(BasemapTestTags.CONTAINER)
                .fetchSemanticsNode()
            runCatching { node.config[SemanticsProperties.StateDescription] }.getOrNull() == "AVAILABLE"
        }
    }

    private fun assertResource(
        client: okhttp3.OkHttpClient,
        path: String,
        minimumBytes: Int,
    ) {
        val request = Request.Builder().url(LocalBasemapAssumptions.BASE_URL + path).build()
        client.newCall(request).execute().use { response ->
            assertTrue("$path returned HTTP ${response.code}", response.isSuccessful)
            val bytes = response.body?.bytes() ?: ByteArray(0)
            assertTrue("$path returned only ${bytes.size} bytes", bytes.size >= minimumBytes)
        }
    }
}
