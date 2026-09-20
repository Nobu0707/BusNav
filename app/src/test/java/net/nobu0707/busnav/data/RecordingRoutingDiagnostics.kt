package net.nobu0707.busnav.data.routing.valhalla

internal class RecordingRoutingDiagnostics : RoutingDiagnostics {
    data class Entry(
        val event: String,
        val details: String,
        val throwable: Throwable?,
    )

    val debugEntries = mutableListOf<Entry>()
    val errorEntries = mutableListOf<Entry>()

    override fun debug(event: String, details: String) {
        debugEntries += Entry(event, details, null)
    }

    override fun error(event: String, details: String, throwable: Throwable?) {
        errorEntries += Entry(event, details, throwable)
    }

    fun errorFor(event: String): Entry =
        errorEntries.single { it.event == event }
}
