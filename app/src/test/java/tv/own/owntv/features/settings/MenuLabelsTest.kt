package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.SettingsRepository.NavSize
import tv.own.owntv.core.settings.SettingsRepository.NavStyle
import tv.own.owntv.features.settings.MenuLabels.Rail

class MenuLabelsTest {

    private val docked = NavStyle.DOCKED
    private val floating = NavStyle.FLOATING

    @Test
    fun `docked reads Size and Widen set by hand`() {
        assertEquals(MenuLabels.OFF, MenuLabels.of(docked, NavSize.COMPACT, null))
        assertEquals(MenuLabels.WHEN_FOCUSED, MenuLabels.of(docked, NavSize.COMPACT, NavSize.WIDE))
        NavSize.entries.filter { it != NavSize.COMPACT }.forEach { size ->
            assertEquals("$size", MenuLabels.ALWAYS, MenuLabels.of(docked, size, null))
            assertEquals("$size + widen", MenuLabels.ALWAYS, MenuLabels.of(docked, size, NavSize.NORMAL))
        }
    }

    @Test
    fun `floating is Off only at Compact, and never Always`() {
        assertEquals(MenuLabels.OFF, MenuLabels.of(floating, NavSize.COMPACT, NavSize.WIDE))
        assertEquals(MenuLabels.WHEN_FOCUSED, MenuLabels.of(floating, NavSize.NORMAL, null))
        assertEquals(MenuLabels.WHEN_FOCUSED, MenuLabels.of(floating, NavSize.EXTRA_WIDE, null))
        assertEquals(listOf(MenuLabels.OFF, MenuLabels.WHEN_FOCUSED), MenuLabels.choicesFor(floating))
        assertEquals(MenuLabels.entries, MenuLabels.choicesFor(docked))
    }

    @Test
    fun `docked choices write the matching Size and Widen`() {
        assertEquals(Rail(NavSize.COMPACT, null), MenuLabels.apply(MenuLabels.OFF, docked, NavSize.WIDE, NavSize.WIDE))
        assertEquals(Rail(NavSize.COMPACT, NavSize.NORMAL), MenuLabels.apply(MenuLabels.WHEN_FOCUSED, docked, NavSize.COMPACT, null))
        assertEquals(Rail(NavSize.NORMAL, null), MenuLabels.apply(MenuLabels.ALWAYS, docked, NavSize.COMPACT, null))
    }

    @Test
    fun `a width the user picked survives switching between the choices`() {
        val focused = MenuLabels.apply(MenuLabels.WHEN_FOCUSED, docked, NavSize.EXTRA_WIDE, null)
        assertEquals(Rail(NavSize.COMPACT, NavSize.EXTRA_WIDE), focused)
        val always = MenuLabels.apply(MenuLabels.ALWAYS, docked, focused.size, focused.widen)
        assertEquals(NavSize.EXTRA_WIDE, always.size)
        assertEquals(Rail(NavSize.EXTRA_WIDE, null), MenuLabels.apply(MenuLabels.ALWAYS, docked, NavSize.EXTRA_WIDE, null))
    }

    @Test
    fun `floating choices change Size only`() {
        assertEquals(Rail(NavSize.COMPACT, NavSize.WIDE), MenuLabels.apply(MenuLabels.OFF, floating, NavSize.NORMAL, NavSize.WIDE))
        assertEquals(Rail(NavSize.WIDE, NavSize.WIDE), MenuLabels.apply(MenuLabels.WHEN_FOCUSED, floating, NavSize.COMPACT, NavSize.WIDE))
        assertEquals(Rail(NavSize.NORMAL, null), MenuLabels.apply(MenuLabels.WHEN_FOCUSED, floating, NavSize.COMPACT, null))
    }

    @Test
    fun `every choice reads back as itself`() {
        for (style in NavStyle.entries) {
            for (size in NavSize.entries) {
                for (widen in listOf(null) + NavSize.entries.filter { it != NavSize.COMPACT }) {
                    for (choice in MenuLabels.choicesFor(style)) {
                        val rail = MenuLabels.apply(choice, style, size, widen)
                        assertEquals("$style $size $widen -> $choice", choice, MenuLabels.of(style, rail.size, rail.widen))
                    }
                }
            }
        }
    }

    @Test
    fun `OK steps through the style's choices and wraps`() {
        assertEquals(MenuLabels.WHEN_FOCUSED, MenuLabels.next(docked, MenuLabels.OFF))
        assertEquals(MenuLabels.ALWAYS, MenuLabels.next(docked, MenuLabels.WHEN_FOCUSED))
        assertEquals(MenuLabels.OFF, MenuLabels.next(docked, MenuLabels.ALWAYS))
        assertEquals(MenuLabels.OFF, MenuLabels.next(floating, MenuLabels.WHEN_FOCUSED))
    }

    @Test
    fun `settings search has its own Menu labels entry`() {
        val screen = java.io.File("src/main/java/tv/own/owntv/features/shell/components/SettingsScreen.kt").readText()
        val search = screen.substring(screen.indexOf("val searchResults"), screen.indexOf("val needle = searchQuery"))
        assertTrue("the Menu labels search entry left the search block", "menuLabelsSearchEntry(" in search)
        assertTrue("the Menu labels search entry lost its title", "R.string.settings_nav_menu_labels)" in screen)
    }
}
