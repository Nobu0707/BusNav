package net.nobu0707.busnav.developer

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import net.nobu0707.busnav.map.basemap.BasemapConfig
import net.nobu0707.busnav.map.basemap.BasemapRegion

data class DeveloperConnectionSettings(
    val valhallaBaseUrl: String,
    val basemapBaseUrl: String,
    val basemapRegion: BasemapRegion = BasemapRegion.KANTO,
) {
    fun normalized() = DeveloperConnectionSettings(
        normalizeBaseUrl(valhallaBaseUrl), normalizeBaseUrl(basemapBaseUrl), basemapRegion,
    )
    fun basemapConfig(isDebug: Boolean) = BasemapConfig.forRegion(basemapBaseUrl, basemapRegion, isDebug)
    companion object {
        const val STYLE_PATH = "/styles/busnav/style.json"
        fun defaults(valhallaUrl: String, styleUrl: String): DeveloperConnectionSettings {
            val region = BasemapRegion.entries.firstOrNull { styleUrl.endsWith(BasemapConfig.regionStylePath(it)) }
                ?: BasemapRegion.KANTO
            val baseUrl = styleUrl.removeSuffix(BasemapConfig.regionStylePath(region)).removeSuffix(STYLE_PATH).trimEnd('/')
            return DeveloperConnectionSettings(valhallaUrl, baseUrl, region)
        }
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
