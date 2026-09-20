package net.nobu0707.busnav.developer

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import net.nobu0707.busnav.map.basemap.BasemapConfig

data class DeveloperConnectionSettings(val valhallaBaseUrl: String, val basemapBaseUrl: String) {
    fun normalized() = DeveloperConnectionSettings(
        normalizeBaseUrl(valhallaBaseUrl), normalizeBaseUrl(basemapBaseUrl),
    )
    fun basemapConfig(isDebug: Boolean) = BasemapConfig.fromBuildValue(
        if (basemapBaseUrl.isBlank()) "" else basemapBaseUrl + STYLE_PATH, isDebug,
    )
    companion object {
        const val STYLE_PATH = "/styles/busnav/style.json"
        fun defaults(valhallaUrl: String, styleUrl: String) = DeveloperConnectionSettings(
            valhallaUrl, styleUrl.removeSuffix(STYLE_PATH).trimEnd('/'),
        )
    }
}

/** Validate before OkHttp's intentionally permissive parsing/normalization. */
fun normalizeBaseUrl(input: String): String {
    val value = input.trim()
    val uri = runCatching { URI(value) }.getOrNull()
    require(uri != null && uri.scheme?.lowercase() in setOf("http", "https") &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null &&
        uri.rawFragment == null && uri.rawPath in setOf("", "/") &&
        (uri.port == -1 || uri.port in 1..65535) && uri.rawAuthority?.endsWith(":") == false && !value.contains('\\')) {
        "http(s)://ホスト:ポートを入力してください（認証情報・query・fragment・path は不可）"
    }
    val url = value.toHttpUrlOrNull()
    require(url != null) { "URLの形式が正しくありません" }
    return url.toString().removeSuffix("/")
}
