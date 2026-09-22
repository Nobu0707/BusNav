package net.nobu0707.busnav.map

import net.nobu0707.busnav.map.JapaneseRoadNetwork.Kind.*
import org.junit.Assert.*
import org.junit.Test

class JapaneseRoadNetworkTest {
    @Test fun normalizesOnlyUnambiguousRefs() {
        listOf("E 1" to "E1", "E20" to "E20", "C 4" to "C4", "E1A" to "E1A").forEach { (input, output) ->
            assertEquals(output, JapaneseRoadNetwork.normalize(input, EXPRESSWAY))
        }
        listOf("1", "20", "246").forEach { assertEquals(it, JapaneseRoadNetwork.normalize("国道${it}号", NATIONAL_ROUTE)) }
        listOf("12", "34", "300").forEach { assertEquals(it, JapaneseRoadNetwork.normalize("県道${it}号", PREFECTURAL_ROUTE)) }
        listOf("4号新宿線", "Route 12 maybe", "246号線", "0", "1234", "１２", "").forEach {
            assertNull(JapaneseRoadNetwork.normalize(it, NATIONAL_ROUTE))
            assertNull(JapaneseRoadNetwork.normalize(it, EXPRESSWAY))
        }
    }
    @Test fun networkMembershipWinsOverProviderAndHighwayClass() {
        val national = JapaneseRoadNetwork.fromNetwork("JP:national", "246")
        val pref = JapaneseRoadNetwork.fromNetwork("JP:prefectural:tokyo", "12")
        val route = JapaneseRoadNetwork.classify(listOf(pref, national), null, "expressway", "primary", "12")
        assertEquals(NATIONAL_ROUTE, route.kind)
        assertEquals("246", route.ref)
        assertEquals(PREFECTURAL_ROUTE, JapaneseRoadNetwork.classify(listOf(pref), null, null, "primary", "12").kind)
        assertEquals(EXPRESSWAY, JapaneseRoadNetwork.fromNetwork("首都高速道路", "3").kind)
        assertNull(JapaneseRoadNetwork.fromNetwork("首都高速道路", "4号新宿線").ref)
    }
    @Test fun noNationalOrPrefecturalGuessFromBareNumbersOrRoadClass() {
        listOf("motorway", "trunk", "primary", "secondary", "tertiary", "minor").forEach {
            assertEquals(OTHER, JapaneseRoadNetwork.classify(emptyList(), "road", null, it, "246").kind)
        }
        assertEquals(OTHER, JapaneseRoadNetwork.fromNetwork("JP:unknown", "12").kind)
        assertEquals(OTHER, JapaneseRoadNetwork.classify(emptyList(), null, null, "primary", "E1").kind)
        assertEquals(EXPRESSWAY, JapaneseRoadNetwork.classify(emptyList(), "road", null, "motorway", "E20").kind)
        assertEquals(NATIONAL_ROUTE, JapaneseRoadNetwork.classify(emptyList(), null, "national", "primary", "20").kind)
    }
    @Test fun multiRefUsesFirstCanonicalAndDoesNotSkipInvalidToken() {
        assertEquals("1", JapaneseRoadNetwork.normalize("1;246", NATIONAL_ROUTE))
        assertEquals("E1", JapaneseRoadNetwork.normalize("E 1;E20", EXPRESSWAY))
        assertNull(JapaneseRoadNetwork.normalize("unknown;246", NATIONAL_ROUTE))
        assertNull(JapaneseRoadNetwork.normalize(";246", NATIONAL_ROUTE))
        val a = JapaneseRoadNetwork.fromNetwork("JP:prefectural", null)
        val b = JapaneseRoadNetwork.fromNetwork("JP:prefectural:tokyo", "305")
        assertEquals("305", JapaneseRoadNetwork.classify(listOf(a, b), null, null, "primary", "305").ref)
    }
}
