package net.nobu0707.busnav.domain.search

import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.domain.routeplan.RoutePlanPointType

data class GeoBounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    init {
        require(west in -180.0..180.0 && east in -180.0..180.0)
        require(south in -90.0..90.0 && north in -90.0..90.0 && west < east && south < north)
    }
}

enum class SearchBiasMode { NONE, CURRENT_LOCATION, VISIBLE_MAP, VISIBLE_MAP_BOUNDED }
data class PlaceSearchContext(
    val currentLocation: GeoPoint? = null,
    val visibleMapBounds: GeoBounds? = null,
    val biasMode: SearchBiasMode = SearchBiasMode.NONE,
)

@JvmInline value class PlaceObjectId(val value: String) {
    init { require(Regex("[NWR][1-9][0-9]*").matches(value)) }
    companion object {
        fun from(osmType: String?, osmId: String?): PlaceObjectId? {
            val prefix = when (osmType?.lowercase()) {
                "node", "n" -> "N"
                "way", "w" -> "W"
                "relation", "r" -> "R"
                else -> return null
            }
            return runCatching { PlaceObjectId(prefix + osmId.orEmpty()) }.getOrNull()
        }
    }
}

enum class PlaceCategory {
    SERVICE_AREA, PARKING_AREA, STATION, ADDRESS, SHOP, RESTAURANT, AIRPORT, PORT,
    INTERCHANGE, JUNCTION, TOURISM, PUBLIC_FACILITY, PLACE, OTHER,
}

data class PlaceAddress(val summary: String, val fields: Map<String, String>)
data class PlaceSearchItem(
    val id: PlaceObjectId,
    val name: String,
    val displayName: String,
    val point: GeoPoint,
    val category: PlaceCategory,
    val sourceCategory: String?,
    val sourceType: String?,
    val address: PlaceAddress?,
    val importance: Double?,
    val namedetails: Map<String, String>,
    val extratags: Map<String, String>,
    val distanceFromCurrentMeters: Double?,
)

sealed interface PlaceSearchFailure {
    data object Network : PlaceSearchFailure
    data object Timeout : PlaceSearchFailure
    data class Http(val code: Int) : PlaceSearchFailure
    data object Parse : PlaceSearchFailure
    data object ServerUnavailable : PlaceSearchFailure
    data object RateLimited : PlaceSearchFailure
}

sealed interface PlaceSearchResult {
    data class Success(val items: List<PlaceSearchItem>) : PlaceSearchResult
    data class Failure(val reason: PlaceSearchFailure) : PlaceSearchResult
}
sealed interface ReverseGeocodeResult {
    data class Success(val item: PlaceSearchItem?) : ReverseGeocodeResult
    data class Failure(val reason: PlaceSearchFailure) : ReverseGeocodeResult
}
sealed interface PlaceLookupResult {
    data class Success(val items: List<PlaceSearchItem>) : PlaceLookupResult
    data class Failure(val reason: PlaceSearchFailure) : PlaceLookupResult
}

interface PlaceSearchProvider {
    suspend fun search(query: String, context: PlaceSearchContext): PlaceSearchResult
    suspend fun reverseGeocode(point: GeoPoint): ReverseGeocodeResult
    suspend fun lookup(ids: List<PlaceObjectId>): PlaceLookupResult
}

fun PlaceSearchItem.routePointName(): String = name.ifBlank { displayName }

fun PlaceSearchItem.categoryLabel(): String = when (category) {
    PlaceCategory.SERVICE_AREA -> "サービスエリア"
    PlaceCategory.PARKING_AREA -> "パーキングエリア"
    PlaceCategory.STATION -> "駅"
    PlaceCategory.ADDRESS -> "住所"
    PlaceCategory.SHOP -> "店舗・商業施設"
    PlaceCategory.RESTAURANT -> "飲食店"
    PlaceCategory.AIRPORT -> "空港"
    PlaceCategory.PORT -> "港・フェリー"
    PlaceCategory.INTERCHANGE -> "インターチェンジ"
    PlaceCategory.JUNCTION -> "ジャンクション"
    PlaceCategory.TOURISM -> "観光地"
    PlaceCategory.PUBLIC_FACILITY -> "公共施設"
    PlaceCategory.PLACE -> "地名"
    PlaceCategory.OTHER -> "その他"
}

fun PlaceSearchItem.routePointTypeLabel(type: RoutePlanPointType): String = when (type) {
    RoutePlanPointType.START -> "出発地"
    RoutePlanPointType.DESTINATION -> "目的地"
    RoutePlanPointType.VIA -> "経由地"
    RoutePlanPointType.SHAPING -> "通過指定"
}
