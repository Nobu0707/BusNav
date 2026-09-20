package net.nobu0707.busnav.data.routing.valhalla

internal class RecordingRoutingDiagnostics : RoutingDiagnostics {
    data class Entry(
        val event: String,
        val details: String,
        val throwable: Throwable?,
        val threadName: String,
    )

    val debugEntries = mutableListOf<Entry>()
    val errorEntries = mutableListOf<Entry>()

    override val isDebugEnabled = true

    override val isErrorEnabled = true

    override fun debug(event: String, details: () -> String) {
        debugEntries += Entry(event, details(), null, Thread.currentThread().name)
    }

    override fun error(event: String, throwable: Throwable?, details: () -> String) {
        errorEntries += Entry(event, details(), throwable, Thread.currentThread().name)
    }

    fun errorFor(event: String): Entry =
        errorEntries.single { it.event == event }
}
