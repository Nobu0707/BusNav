@file:Suppress("LogNotTimber")

package net.nobu0707.busnav.map.basemap

import android.util.Log
import java.net.URI

enum class MapDiagnosticEvent(val eventName: String) {
    STYLE_LOAD_START("basemap.style.load.start"),
    STYLE_LOAD_SUCCESS("basemap.style.load.success"),
    STYLE_LOAD_FAILED("basemap.style.load.failed"),
    SOURCE_ERROR("basemap.source.error"),
}

interface MapDiagnostics {
    fun record(event: MapDiagnosticEvent, resource: String? = null, detail: String? = null)
}

object NoOpMapDiagnostics : MapDiagnostics {
    override fun record(event: MapDiagnosticEvent, resource: String?, detail: String?) = Unit
}

class AndroidLogMapDiagnostics : MapDiagnostics {
    override fun record(event: MapDiagnosticEvent, resource: String?, detail: String?) {
        val message = buildString {
            append(event.eventName)
            resource?.let { append(" resource=").append(sanitizeMapResource(it)) }
            detail?.takeIf(String::isNotBlank)?.let { append(" detail=").append(sanitizeMapDetail(it)) }
        }
        if (event == MapDiagnosticEvent.STYLE_LOAD_FAILED || event == MapDiagnosticEvent.SOURCE_ERROR) {
            Log.w(TAG, message)
        } else {
            Log.i(TAG, message)
        }
    }

    private companion object {
        const val TAG = "BusNavMap"
    }
}

fun sanitizeMapResource(resource: String): String {
    val parsed = runCatching { URI(resource) }.getOrNull()
    if (parsed?.scheme != null && parsed.host != null) {
        return URI(
            parsed.scheme,
            null,
            parsed.host,
            parsed.port,
            parsed.path,
            null,
            null,
        ).toString()
    }
    return if (resource == BasemapConfig.FALLBACK_STYLE_URL) resource else "[invalid-resource]"
}


/** Redact first, then bound the log line. Never log raw parser failures. */
fun sanitizeMapDetail(detail: String): String {
    var safe = detail
    repeat(4) {
        safe = Regex("(?:%[0-9a-fA-F]{2})+").replace(safe) { match ->
            runCatching { java.net.URLDecoder.decode(match.value, "UTF-8") }.getOrDefault("[encoded]")
        }
    }
    safe = Regex("(?i)https?[^\\s<>\\\"]*").replace(safe) { match ->
        val uri = runCatching { URI(match.value) }.getOrNull()
        if (uri?.host != null) {
            uri.host + if (uri.port >= 0) ":${uri.port}" else ""
        } else "[redacted-url]"
    }
    safe = Regex("(?i)\\bbearer\\s+[^\\s,;\\\"]+").replace(safe, "Bearer [redacted]")
    safe = Regex("(?i)([\\\"']?(?:[a-z_]*token|api[-_]?key|authorization|password|passwd|secret|credential)[\\\"']?\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;]+)")
        .replace(safe) { it.groupValues[1] + "[redacted]" }
    safe = Regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b").replace(safe, "[redacted]")
    return safe.replace(Regex("[\\r\\n\\t]"), " ").take(240)
}
