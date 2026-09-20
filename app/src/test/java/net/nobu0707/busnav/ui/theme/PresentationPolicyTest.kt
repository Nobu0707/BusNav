package net.nobu0707.busnav.ui.theme

import java.time.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.map.*
import net.nobu0707.busnav.map.basemap.*
import net.nobu0707.busnav.ui.navigation.BottomLabelLayout
import org.junit.Assert.*
import org.junit.Test

class PresentationPolicyTest {
    private val tokyo = GeoPoint(35.6812, 139.7671)
    private val zone = ZoneId.of("Asia/Tokyo")
    @Test fun allThemeCombinations() {
        for (active in listOf(false, true)) for (night in listOf(false, true)) for (tunnel in listOf(false, true)) {
            assertEquals(active && (night || tunnel), ThemeModeResolver.isDark(active, night, tunnel))
        }
    }
    @Test fun tokyoSeasonalSolarRangesAndExactBoundaries() {
        // Approximate public Tokyo times, minutes after midnight JST; tolerance 15 minutes.
        listOf(Triple("2026-03-20", 346, 1073), Triple("2026-06-21", 265, 1140),
            Triple("2026-12-22", 407, 992)).forEach { (date, rise, set) ->
            val day = SolarCalculator().calculate(tokyo, LocalDate.parse(date), zone)
            val sunrise = requireNotNull(day.sunrise)
            val sunset = requireNotNull(day.sunset)
            fun minutes(i: Instant) = i.atZone(zone).let { it.hour * 60 + it.minute }
            assertTrue(date+" sunrise "+minutes(sunrise), minutes(sunrise) in rise-15..rise+15)
            assertTrue(date+" sunset "+minutes(sunset), minutes(sunset) in set-15..set+15)
            assertTrue(day.isNight(sunrise.minusNanos(1)))
            assertFalse(day.isNight(sunrise))
            assertFalse(day.isNight(sunset.minusNanos(1)))
            assertTrue(day.isNight(sunset))
        }
    }
    @Test fun polarDaysLeapYearAndDateLineZones() {
        val c=SolarCalculator()
        assertFalse(c.calculate(GeoPoint(80.0, 0.0), LocalDate.parse("2024-06-21"), ZoneOffset.UTC).polarNight)
        assertTrue(c.calculate(GeoPoint(80.0, 0.0), LocalDate.parse("2024-12-21"), ZoneOffset.UTC).polarNight)
        val date=LocalDate.parse("2024-02-29")
        val nz=ZoneId.of("Pacific/Auckland")
        val result=c.calculate(GeoPoint(-36.85,174.76),date,nz)
        assertEquals(date,result.sunrise!!.atZone(nz).toLocalDate())
        assertEquals(date,result.sunset!!.atZone(nz).toLocalDate())
        val ny=ZoneId.of("America/New_York")
        val dst=c.calculate(GeoPoint(40.71,-74.01),LocalDate.parse("2026-03-08"),ny)
        assertTrue(dst.sunrise!!.atZone(ny).hour in 6..8)
    }
    @Test fun missingPositionAndCacheDateZoneMovementChanges() {
        val cache=SolarDayCache()
        val instant=Instant.parse("2026-06-21T20:00:00Z")
        assertFalse(cache.isNight(null,instant,zone))
        assertFalse(cache.isNight(tokyo,instant,zone))
        val west=GeoPoint(33.59,130.40)
        assertEquals(SolarCalculator().calculate(west,instant.atZone(zone).toLocalDate(),zone).isNight(instant),
            cache.isNight(west,instant,zone))
        val tomorrow=instant.plusSeconds(86400)
        assertEquals(SolarCalculator().calculate(tokyo,tomorrow.atZone(zone).toLocalDate(),zone).isNight(tomorrow),
            cache.isNight(tokyo,tomorrow,zone))
        assertEquals(SolarCalculator().calculate(tokyo,tomorrow.atZone(ZoneOffset.UTC).toLocalDate(),ZoneOffset.UTC).isNight(tomorrow),
            cache.isNight(tokyo,tomorrow,ZoneOffset.UTC))
    }
    @Test fun tunnelDebouncesJitterExitAndUnknownExpiry() {
        val h=TunnelHysteresis()
        assertFalse(h.update(TunnelObservation.UNKNOWN,0))
        assertFalse(h.update(TunnelObservation.TUNNEL,100))
        assertFalse(h.update(TunnelObservation.SURFACE,1000))
        assertFalse(h.update(TunnelObservation.TUNNEL,1100))
        assertFalse(h.update(TunnelObservation.TUNNEL,3099))
        assertTrue(h.update(TunnelObservation.TUNNEL,3100))
        assertTrue(h.update(TunnelObservation.SURFACE,4000))
        assertTrue(h.update(TunnelObservation.TUNNEL,5000))
        assertTrue(h.update(TunnelObservation.SURFACE,6000))
        assertTrue(h.update(TunnelObservation.SURFACE,9999))
        assertFalse(h.update(TunnelObservation.SURFACE,10000))
        h.update(TunnelObservation.TUNNEL,11000)
        assertTrue(h.update(TunnelObservation.TUNNEL,13000))
        assertTrue(h.update(TunnelObservation.UNKNOWN,20999))
        assertFalse(h.update(TunnelObservation.UNKNOWN,21000))
        assertFalse(h.update(TunnelObservation.TUNNEL,22000))
        assertTrue(h.update(TunnelObservation.TUNNEL,24000))
        assertFalse(h.update(TunnelObservation.TUNNEL,10))
    }
    @Test fun regionalThemesRoundTripAndFallback() {
        BasemapRegion.entries.forEach {
            val base=BasemapConfig.forRegion("http://localhost:8080",it,true)
            val light=base.withTheme(false)
            assertTrue(light.styleUrl!!.contains(it.id+"-light"))
            assertEquals(base,light.withTheme(true))
            assertEquals(light,light.withTheme(false))
        }
        assertTrue(BasemapConfig(null,BasemapMode.FALLBACK).withTheme(false).fallbackStyleUrl.contains("light"))
        assertEquals(listOf("ルート","迂回","規制","音声","表示"),BottomLabelLayout.labels)
        assertEquals(1,BottomLabelLayout.maxLines)
    }
}
