package net.nobu0707.busnav.map.basemap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BasemapConfigTest {
    @Test
    fun debugEmulatorUrlSelectsLocalDevelopmentMode() {
        val config = BasemapConfig.fromBuildValue(
            "http://10.0.2.2:8080/styles/busnav/style.json",
            isDebug = true,
        )

        assertEquals(BasemapMode.LOCAL_DEV, config.mode)
        assertEquals("http://10.0.2.2:8080/styles/busnav/style.json", config.styleUrl)
    }

    @Test
    fun releaseNeverUsesLocalDevelopmentUrl() {
        val config = BasemapConfig.fromBuildValue(
            "http://10.0.2.2:8080/styles/busnav/style.json",
            isDebug = false,
        )

        assertEquals(BasemapMode.FALLBACK, config.mode)
        assertNull(config.styleUrl)
    }

    @Test
    fun emptyReleaseValueUsesFallback() {
        val config = BasemapConfig.fromBuildValue("", isDebug = false)

        assertEquals(BasemapMode.FALLBACK, config.mode)
        assertEquals(BasemapConfig.FALLBACK_STYLE_URL, config.fallbackStyleUrl)
    }

    @Test
    fun secureRemoteStyleCanBeSelected() {
        val config = BasemapConfig.fromBuildValue(
            "https://tiles.example.test/styles/busnav/style.json",
            isDebug = false,
        )

        assertEquals(BasemapMode.REMOTE_STYLE, config.mode)
    }
}
