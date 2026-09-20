package net.nobu0707.busnav.map

import net.nobu0707.busnav.domain.model.GeoPoint

enum class TunnelObservation { TUNNEL, SURFACE, UNKNOWN }

fun interface TunnelStateProvider {
    fun observe(point: GeoPoint): TunnelObservation
}

/** Monotonic debounce: enter 2 s, exit 4 s, unknown expires after 8 s. */
class TunnelHysteresis {
    var isTunnel: Boolean = false
        private set
    private var pending = TunnelObservation.UNKNOWN
    private var pendingSince = 0L
    private var lastKnown = Long.MIN_VALUE
    private var lastTime = Long.MIN_VALUE

    fun update(observation: TunnelObservation, nowMillis: Long): Boolean {
        if (nowMillis < lastTime) reset()
        lastTime = nowMillis
        if (observation == TunnelObservation.UNKNOWN) {
            pending = observation
            if (lastKnown == Long.MIN_VALUE || nowMillis - lastKnown >= 8_000) isTunnel = false
            return isTunnel
        }
        lastKnown = nowMillis
        if (pending != observation) {
            pending = observation
            pendingSince = nowMillis
        }
        val desired = observation == TunnelObservation.TUNNEL
        if (desired != isTunnel && nowMillis - pendingSince >= (if (desired) 2_000 else 4_000)) {
            isTunnel = desired
        }
        return isTunnel
    }

    fun reset() {
        isTunnel = false
        pending = TunnelObservation.UNKNOWN
        lastKnown = Long.MIN_VALUE
        lastTime = Long.MIN_VALUE
    }
}
