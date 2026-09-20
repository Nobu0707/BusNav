package net.nobu0707.busnav.data.routing.valhalla

import android.util.Log

interface RoutingDiagnostics {
    val isDebugEnabled: Boolean

    val isErrorEnabled: Boolean

    fun debug(event: String, details: () -> String)

    fun error(event: String, throwable: Throwable? = null, details: () -> String)
}

object NoOpRoutingDiagnostics : RoutingDiagnostics {
    override val isDebugEnabled = false

    override val isErrorEnabled = false

    override fun debug(event: String, details: () -> String) = Unit

    override fun error(event: String, throwable: Throwable?, details: () -> String) = Unit
}

class AndroidLogRoutingDiagnostics(
    private val tag: String = DEFAULT_TAG,
) : RoutingDiagnostics {
    override val isDebugEnabled: Boolean
        get() = Log.isLoggable(tag, Log.DEBUG)

    override val isErrorEnabled: Boolean
        get() = Log.isLoggable(tag, Log.ERROR)

    override fun debug(event: String, details: () -> String) {
        if (isDebugEnabled) Log.d(tag, "$event ${details()}")
    }

    override fun error(event: String, throwable: Throwable?, details: () -> String) {
        if (isErrorEnabled) Log.e(tag, "$event ${details()}", throwable)
    }

    private companion object {
        const val DEFAULT_TAG = "BusNavValhalla"
    }
}
