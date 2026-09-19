package net.nobu0707.busnav.data.routing.valhalla

import net.nobu0707.busnav.domain.model.GeoPoint

object Polyline6Decoder {
    fun decode(encoded: String): List<GeoPoint> {
        require(encoded.isNotEmpty()) { "Encoded shape must not be empty" }
        val result = mutableListOf<GeoPoint>()
        var index = 0
        var latitude = 0L
        var longitude = 0L
        while (index < encoded.length) {
            val latitudeValue = decodeValue(encoded, index)
            index = latitudeValue.nextIndex
            val longitudeValue = decodeValue(encoded, index)
            index = longitudeValue.nextIndex
            latitude += latitudeValue.delta
            longitude += longitudeValue.delta
            result += GeoPoint(latitude / PRECISION, longitude / PRECISION)
        }
        return result
    }

    private fun decodeValue(encoded: String, startIndex: Int): DecodedValue {
        var index = startIndex
        var value = 0L
        var shift = 0
        while (true) {
            require(index < encoded.length) { "Truncated encoded polyline" }
            val code = encoded[index++].code - 63
            require(code in 0..63) { "Invalid encoded polyline character" }
            require(shift <= 60) { "Encoded polyline value is too large" }
            value = value or ((code and 0x1f).toLong() shl shift)
            if (code < 0x20) break
            shift += 5
        }
        val delta = if ((value and 1L) != 0L) (value shr 1).inv() else value shr 1
        return DecodedValue(delta, index)
    }

    private data class DecodedValue(val delta: Long, val nextIndex: Int)

    private const val PRECISION = 1_000_000.0
}
