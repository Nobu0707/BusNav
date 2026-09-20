package net.nobu0707.busnav.ui.theme

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.*
import net.nobu0707.busnav.domain.model.GeoPoint

data class SolarDay(val sunrise: Instant?, val sunset: Instant?, val polarNight: Boolean = false) {
    fun isNight(now: Instant): Boolean = if (sunrise == null || sunset == null) polarNight
        else now < sunrise || now >= sunset
}

/** NOAA general solar equations, zenith 90.833 degrees. No network/Android dependencies.
 * https://gml.noaa.gov/grad/solcalc/solareqns.PDF
 * Approximate sea-level horizon; terrain and weather are not modelled.
 */
class SolarCalculator {
    fun calculate(point: GeoPoint, date: LocalDate, zone: ZoneId): SolarDay {
        val gamma = 2 * PI / date.lengthOfYear() * (date.dayOfYear - 1)
        val equation = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val latitude = Math.toRadians(point.latitude)
        val cosine = cos(Math.toRadians(90.833)) / (cos(latitude) * cos(declination)) -
            tan(latitude) * tan(declination)
        if (cosine > 1) return SolarDay(null, null, polarNight = true)
        if (cosine < -1) return SolarDay(null, null)
        val hourAngle = Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
        val noonMinutes = 720 - 4 * point.longitude - equation
        var midnightUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val noon = midnightUtc.plusSeconds((noonMinutes * 60).roundToLong())
        val dayShift = date.toEpochDay() - noon.atZone(zone).toLocalDate().toEpochDay()
        midnightUtc = midnightUtc.plusSeconds(dayShift * 86400)
        return SolarDay(
            midnightUtc.plusSeconds(((noonMinutes - 4 * hourAngle) * 60).roundToLong()),
            midnightUtc.plusSeconds(((noonMinutes + 4 * hourAngle) * 60).roundToLong()),
        )
    }
}

/** Cache astronomy only; time is evaluated on every tick. */
class SolarDayCache(private val calculator: SolarCalculator = SolarCalculator()) {
    private var key: Triple<LocalDate, ZoneId, GeoPoint>? = null
    private var value: SolarDay? = null
    fun isNight(point: GeoPoint?, now: Instant, zone: ZoneId): Boolean {
        if (point == null) return false
        val date = now.atZone(zone).toLocalDate()
        val previous = key
        if (previous == null || previous.first != date || previous.second != zone ||
            distanceMeters(previous.third, point) >= 5_000) {
            key = Triple(date, zone, point)
            value = calculator.calculate(point, date, zone)
        }
        return value?.isNight(now) == true
    }
}

internal fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val lat = Math.toRadians(b.latitude - a.latitude)
    val lon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
        cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
    return 12_742_000 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
