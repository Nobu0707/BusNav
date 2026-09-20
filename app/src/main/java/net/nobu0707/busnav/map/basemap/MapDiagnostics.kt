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
            detail?.takeIf(String::isNotBlank)?.let { append(" detail=").append(it.take(MAX_DETAIL_LENGTH)) }
        }
        if (event == MapDiagnosticEvent.STYLE_LOAD_FAILED || event == MapDiagnosticEvent.SOURCE_ERROR) {
            Log.w(TAG, message)
        } else {
            Log.i(TAG, message)
        }
    }

    private companion object {
        const val TAG = "BusNavMap"
        const val MAX_DETAIL_LENGTH = 240
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
    return resource.substringBefore('?').substringBefore('#')
}
