package net.nobu0707.busnav.map.basemap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapDiagnosticsTest {
    @Test
    fun sanitizerRemovesQueryFragmentAndCredentials() {
        val sanitized = sanitizeMapResource(
            "https://user:password@tiles.example.test/style.json?token=secret#fragment",
        )

        assertEquals("https://tiles.example.test/style.json", sanitized)
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("password"))
    }
}
