package net.nobu0707.busnav.map.basemap

import java.net.URI

enum class BasemapMode {
    LOCAL_DEV,
    REMOTE_STYLE,
    FALLBACK,
}

data class BasemapConfig(
    val styleUrl: String?,
    val mode: BasemapMode,
    val fallbackStyleUrl: String = FALLBACK_STYLE_URL,
) {
    init {
        require(mode == BasemapMode.FALLBACK || !styleUrl.isNullOrBlank()) {
            "A non-fallback basemap requires a style URL"
        }
    }

    /** Known BusNav endpoints have sibling light styles; custom URLs remain untouched. */
    fun withTheme(dark: Boolean): BasemapConfig {
        val themed = styleUrl?.replace(
            Regex("/styles/(busnav(?:-kanto|-chubu)?)(?:-light)?/style\\.json$"),
        ) { match -> "/styles/" + match.groupValues[1] + (if (dark) "" else "-light") + "/style.json" }
        return copy(styleUrl = themed, fallbackStyleUrl = if (dark) FALLBACK_STYLE_URL
            else "asset://basemap/fallback-light-style.json")
    }

    companion object {
        const val FALLBACK_STYLE_URL = "asset://basemap/fallback-style.json"

        fun regionStylePath(region: BasemapRegion) = "/styles/busnav-" + region.id + "/style.json"

        fun forRegion(baseUrl: String, region: BasemapRegion, isDebug: Boolean): BasemapConfig =
            fromBuildValue(if (baseUrl.isBlank()) "" else baseUrl.trimEnd('/') + regionStylePath(region), isDebug)

        fun fromBuildValue(styleUrl: String, isDebug: Boolean): BasemapConfig {
            val normalized = styleUrl.trim().takeIf(String::isNotEmpty)
                ?: return BasemapConfig(null, BasemapMode.FALLBACK)
            val uri = runCatching { URI(normalized) }.getOrNull()
                ?: return BasemapConfig(null, BasemapMode.FALLBACK)
            if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
                return BasemapConfig(null, BasemapMode.FALLBACK)
            }
            val scheme = uri.scheme?.lowercase()
            val localDevelopmentHost = uri.host in LOCAL_DEVELOPMENT_HOSTS

            if (localDevelopmentHost && !isDebug) {
                return BasemapConfig(null, BasemapMode.FALLBACK)
            }
            if (scheme == "http" && !isDebug) {
                return BasemapConfig(null, BasemapMode.FALLBACK)
            }
            if (scheme != "http" && scheme != "https") {
                return BasemapConfig(null, BasemapMode.FALLBACK)
            }

            return BasemapConfig(
                styleUrl = normalized,
                mode = if (localDevelopmentHost) BasemapMode.LOCAL_DEV else BasemapMode.REMOTE_STYLE,
            )
        }

        private val LOCAL_DEVELOPMENT_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")
    }
}
