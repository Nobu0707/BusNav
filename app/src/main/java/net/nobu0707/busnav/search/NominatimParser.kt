package net.nobu0707.busnav.search

import kotlin.math.*
import kotlinx.serialization.json.*
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.search.*

/** JSONv2 stays outside UI. A malformed row never discards its valid neighbours. */
class NominatimParser(private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun parseList(body: String, currentLocation: GeoPoint? = null): List<PlaceSearchItem> =
        json.parseToJsonElement(body).jsonArray.mapNotNull { parseItem(it, currentLocation) }

    fun parseSingle(body: String): PlaceSearchItem? = parseItem(json.parseToJsonElement(body), null)

    fun parseItem(element: JsonElement, currentLocation: GeoPoint? = null): PlaceSearchItem? {
        val obj = element as? JsonObject ?: return null
        val id = PlaceObjectId.from(obj.string("osm_type"), obj.string("osm_id")) ?: return null
        val latitude = obj.string("lat")?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val longitude = obj.string("lon")?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        val point = GeoPoint(latitude, longitude)
        val names = obj.map("namedetails")
        val tags = obj.map("extratags")
        val sourceCategory = obj.string("category") ?: obj.string("class")
        val sourceType = obj.string("type")
        val displayName = obj.string("display_name").orEmpty()
        val name = names["name:ja"].nonBlank() ?: obj.string("name").nonBlank()
            ?: displayName.substringBefore(',').trim().nonBlank() ?: "${latitude}, ${longitude}"
        val fields = obj.map("address")
        val summary = listOf("road", "suburb", "city", "town", "village", "state", "postcode")
            .mapNotNull(fields::get).distinct().joinToString("、").ifBlank { displayName.substringAfter(',', "").trim() }
        return PlaceSearchItem(id, name, displayName, point, classify(sourceCategory, sourceType, tags),
            sourceCategory, sourceType, summary.takeIf { it.isNotBlank() }?.let { PlaceAddress(it, fields) },
            obj.string("importance")?.toDoubleOrNull(), names, tags,
            currentLocation?.let { distanceMeters(it, point) })
    }

    private fun classify(category: String?, type: String?, tags: Map<String, String>): PlaceCategory {
        fun tag(key: String, value: String) = (category == key && type == value) || tags[key] == value
        return when {
            tag("highway", "services") -> PlaceCategory.SERVICE_AREA
            tag("highway", "rest_area") -> PlaceCategory.PARKING_AREA
            tag("railway", "station") || tag("public_transport", "station") -> PlaceCategory.STATION
            tag("highway", "motorway_junction") -> PlaceCategory.INTERCHANGE
            category == "shop" || tags.containsKey("shop") -> PlaceCategory.SHOP
            tag("amenity", "restaurant") || tag("amenity", "cafe") || tag("amenity", "fast_food") -> PlaceCategory.RESTAURANT
            (category == "aeroway" && type in setOf("aerodrome", "terminal", "airport")) || tags["aeroway"] in setOf("aerodrome", "terminal") -> PlaceCategory.AIRPORT
            tag("amenity", "ferry_terminal") || tag("harbour", "yes") -> PlaceCategory.PORT
            category == "tourism" || tags.containsKey("tourism") -> PlaceCategory.TOURISM
            category == "amenity" || tags.containsKey("amenity") -> PlaceCategory.PUBLIC_FACILITY
            category == "place" -> PlaceCategory.PLACE
            category == "boundary" || category == "building" || type in setOf("house", "residential", "postcode") -> PlaceCategory.ADDRESS
            else -> PlaceCategory.OTHER
        }
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 6371000.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.map(key: String): Map<String, String> = (get(key) as? JsonObject)?.mapNotNull { (k, v) ->
        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
    }?.toMap().orEmpty()
    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
}
