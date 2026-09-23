package net.nobu0707.busnav.map.basemap

enum class BasemapRegion(val id: String, val label: String) {
    JAPAN("japan", "全国 / Japan"),
    KANTO("kanto", "関東 / Kanto"),
    CHUBU("chubu", "中部 / Chubu");

    companion object {
        fun fromId(id: String?): BasemapRegion = entries.firstOrNull { it.id == id } ?: KANTO
    }
}
