package net.nobu0707.busnav.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLayoutResolverTest {
    @Test
    fun `portrait phone uses map-first layout`() {
        assertEquals(
            NavigationLayoutMode.PortraitMap,
            resolveNavigationLayout(widthDp = 412f, heightDp = 915f),
        )
    }

    @Test
    fun `wide landscape uses three-column layout`() {
        assertEquals(
            NavigationLayoutMode.LandscapeThreeColumn,
            resolveNavigationLayout(widthDp = 915f, heightDp = 412f),
        )
    }

    @Test
    fun `small landscape remains map-first to avoid cramped columns`() {
        assertEquals(
            NavigationLayoutMode.PortraitMap,
            resolveNavigationLayout(widthDp = 599f, heightDp = 360f),
        )
    }
}
