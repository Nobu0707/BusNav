package net.nobu0707.busnav.domain.navigation

enum class NavigationMapOrientation(val storedValue: String) {
    HEADING_UP("heading_up"), NORTH_UP("north_up");

    fun toggled() = if (this == HEADING_UP) NORTH_UP else HEADING_UP

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.storedValue == value } ?: HEADING_UP
    }
}
