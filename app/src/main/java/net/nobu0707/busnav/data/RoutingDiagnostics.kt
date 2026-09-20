package net.nobu0707.busnav.data.routing.valhalla

import android.util.Log

interface RoutingDiagnostics {
    fun debug(event: String, details: String)

    fun error(event: String, details: String, throwable: Throwable? = null)
}

object NoOpRoutingDiagnostics : RoutingDiagnostics {
    override fun debug(event: String, details: String) = Unit

    override fun error(event: String, details: String, throwable: Throwable?) = Unit
}

class AndroidLogRoutingDiagnostics(
    private val tag: String = DEFAULT_TAG,
) : RoutingDiagnostics {
    override fun debug(event: String, details: String) {
        Log.d(tag, "$event $details")
    }

    override fun error(event: String, details: String, throwable: Throwable?) {
        Log.e(tag, "$event $details", throwable)
    }

    private companion object {
        const val DEFAULT_TAG = "BusNavValhalla"
    }
}
