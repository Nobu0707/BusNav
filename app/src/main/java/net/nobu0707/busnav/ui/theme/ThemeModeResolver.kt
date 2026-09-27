package net.nobu0707.busnav.ui.theme

object ThemeModeResolver {
    fun isDark(currentScreenIsNavigation: Boolean, isNavigationStarted: Boolean,
        hasActiveRoute: Boolean, isNight: Boolean, isTunnel: Boolean): Boolean =
        currentScreenIsNavigation && isNavigationStarted && hasActiveRoute && (isNight || isTunnel)

    fun isDark(navigationActive: Boolean, isNight: Boolean, isTunnel: Boolean): Boolean =
        navigationActive && (isNight || isTunnel)
}
