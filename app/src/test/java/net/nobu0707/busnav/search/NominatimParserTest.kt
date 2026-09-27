package net.nobu0707.busnav.search

import net.nobu0707.busnav.domain.search.PlaceCategory
import org.junit.Assert.*
import org.junit.Test

class NominatimParserTest {
    private val parser = NominatimParser()

    @Test fun classificationsUseTagsAndKeepAmbiguousFeatures() {
        val rows = listOf(
            row("東京駅", "railway", "station", "N1"),
            row("海老名SA", "highway", "services", "W2"),
            row("刈谷PA", "highway", "rest_area", "N3"),
            row("イオンモール", "shop", "mall", "W4"),
            row("食堂", "amenity", "restaurant", "N5"),
            row("羽田空港", "aeroway", "aerodrome", "W6"),
            row("フェリー", "amenity", "ferry_terminal", "N7"),
            row("足寄IC", "highway", "motorway_junction", "N8"),
            row("東京都庁", "amenity", "townhall", "W9"),
            row("東京駅SA", "railway", "platform", "N10"),
            row("東京都千代田区", "boundary", "administrative", "R11"),
        )
        val items = parser.parseList(rows.joinToString(",", "[", "]"))
        assertEquals(listOf(PlaceCategory.STATION, PlaceCategory.SERVICE_AREA, PlaceCategory.PARKING_AREA,
            PlaceCategory.SHOP, PlaceCategory.RESTAURANT, PlaceCategory.AIRPORT, PlaceCategory.PORT,
            PlaceCategory.INTERCHANGE, PlaceCategory.PUBLIC_FACILITY, PlaceCategory.OTHER,
            PlaceCategory.ADDRESS), items.map { it.category })
        assertEquals("N1", items.first().id.value)
    }

    @Test fun malformedRowsAreSkippedWithoutDiscardingValidRows() {
        val body = "[${row("東京駅", "railway", "station", "N1")}," +
            "{\"osm_type\":\"node\",\"osm_id\":12,\"lat\":\"NaN\",\"lon\":\"139\"}," +
            "{\"lat\":\"35\",\"lon\":\"139\"}]"
        assertEquals(1, parser.parseList(body).size)
    }

    @Test fun japaneseNameAddressAndExtrasAreRetained() {
        val body = """{"osm_type":"way","osm_id":42,"lat":"35.0","lon":"139.0",
            "name":"Tokyo","display_name":"Tokyo, Japan","category":"shop","type":"mall",
            "namedetails":{"name:ja":"イオンモール幕張新都心"},
            "extratags":{"shop":"mall"},"address":{"city":"千葉市","state":"千葉県"},"unknown":"ignored"}"""
        val item = requireNotNull(parser.parseSingle(body))
        assertEquals("イオンモール幕張新都心", item.name)
        assertEquals("千葉市、千葉県", item.address?.summary)
        assertEquals("mall", item.extratags["shop"])
        assertEquals("W42", item.id.value)
    }

    private fun row(name: String, category: String, type: String, id: String): String =
        """{"osm_type":"${when (id.first()) { 'N' -> "node"; 'W' -> "way"; else -> "relation" }}",
            "osm_id":${id.drop(1)},"lat":"35.0","lon":"139.0","name":"$name",
            "category":"$category","type":"$type"}"""
}
