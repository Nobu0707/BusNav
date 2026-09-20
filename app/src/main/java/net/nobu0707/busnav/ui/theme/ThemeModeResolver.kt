package net.nobu0707.busnav.ui.theme

object ThemeModeResolver {
    fun isDark(navigationActive: Boolean, isNight: Boolean, isTunnel: Boolean): Boolean =
        navigationActive && (isNight || isTunnel)
}
