package net.nobu0707.busnav.developer

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import net.nobu0707.busnav.map.basemap.BasemapConfig
import net.nobu0707.busnav.map.basemap.BasemapRegion

data class DeveloperConnectionSettings(
    val valhallaBaseUrl: String,
    val basemapBaseUrl: String,
    val basemapRegion: BasemapRegion = BasemapRegion.JAPAN,
    val selectedConnectionEnvironment: ConnectionEnvironment = ConnectionEnvironment.CUSTOM,
) {
    fun normalized(): DeveloperConnectionSettings {
        val valid = copy(valhallaBaseUrl = normalizeBaseUrl(valhallaBaseUrl),
            basemapBaseUrl = normalizeBaseUrl(basemapBaseUrl),
            basemapRegion = if (selectedConnectionEnvironment == ConnectionEnvironment.REMOTE_TEST)
                basemapRegion else BasemapRegion.JAPAN)
        if (selectedConnectionEnvironment == ConnectionEnvironment.REMOTE_TEST) {
            require(valid == RemoteTestEndpoints.settings()) { "REMOTE_TESTはHTTPSの全国サーバー固定です" }
        }
        return valid
    }
    fun basemapConfig(isDebug: Boolean) = if (selectedConnectionEnvironment == ConnectionEnvironment.REMOTE_TEST) {
        normalized()
        BasemapConfig.fromBuildValue(RemoteTestEndpoints.DARK_STYLE, isDebug)
            .copy(failureHint = RemoteTestEndpoints.IPV6_HINT)
    } else BasemapConfig.forRegion(basemapBaseUrl, BasemapRegion.JAPAN, isDebug)
    companion object {
        const val STYLE_PATH = "/styles/busnav/style.json"
        fun defaults(valhallaUrl: String, styleUrl: String): DeveloperConnectionSettings {
            val legacyPath = BasemapRegion.entries.firstOrNull {
                styleUrl.endsWith(BasemapConfig.regionStylePath(it))
            }?.let(BasemapConfig::regionStylePath) ?: STYLE_PATH
            val baseUrl = styleUrl.removeSuffix(legacyPath).trimEnd('/')
            return DeveloperConnectionSettings(valhallaUrl, baseUrl, BasemapRegion.JAPAN)
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
