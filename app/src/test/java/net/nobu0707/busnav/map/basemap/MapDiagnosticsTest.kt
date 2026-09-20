package net.nobu0707.busnav.map.basemap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapDiagnosticsTest {
    @Test fun detailRedactsUrlsCredentialsEncodedUrlsAndMalformedText() {
        listOf(
            "Error loading https://host/style.json?token=secret",
            "http://user:secret@host/path",
            "https%3A%2F%2Fuser%3Asecret%40host%2Fpath%3Ftoken%3Dsecret",
            "http://user:secret@[broken/path?token=secret",
            "Authorization: Bearer secret",
            "token=secret api_key=secret password=secret",
            "{\"access_token\":\"secret\"}",
        ).forEach { assertFalse(sanitizeMapDetail(it), sanitizeMapDetail(it).contains("secret")) }
    }
    @Test fun normalTextIsPreservedAndTruncationFollowsRedaction() {
        assertEquals("normal error text", sanitizeMapDetail("normal error text"))
        val value = "x".repeat(225) + " token=" + "secret".repeat(90)
        val result = sanitizeMapDetail(value)
        assertFalse(result.contains("secret"))
        assertEquals(240, result.length)
    }

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
