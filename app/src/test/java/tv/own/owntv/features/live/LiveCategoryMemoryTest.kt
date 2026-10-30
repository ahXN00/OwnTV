package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.serialize
import tv.own.owntv.features.live.LiveCategoryMemory.Step

class LiveCategoryMemoryTest {

    private val fixed = listOf(LiveKey.Favorites, LiveKey.History, LiveKey.All)
    private val rail = fixed + listOf(LiveKey.Folder(12), LiveKey.Custom("custom:abc"))

    @Test
    fun `toggle off restores nothing`() {
        assertNull(LiveCategoryMemory.target(false, LiveKey.Folder(1), LiveKey.Folder(2).serialize()))
    }

    @Test
    fun `stored value is read in core's format`() {
        assertEquals(LiveKey.Folder(12), LiveCategoryMemory.target(true, null, "FOLDER:12"))
        assertEquals(LiveKey.Custom("custom:abc"), LiveCategoryMemory.target(true, null, "CUSTOM:custom:abc"))
        assertEquals(LiveKey.Favorites, LiveCategoryMemory.target(true, null, "FAV"))
    }

    @Test
    fun `empty or unreadable stored value restores nothing`() {
        assertNull(LiveCategoryMemory.target(true, null, ""))
        assertNull(LiveCategoryMemory.target(true, null, "FOLDER:x"))
    }

    @Test
    fun `a pick of this run wins over the stored value, which may not be saved yet`() {
        assertEquals(LiveKey.Folder(7), LiveCategoryMemory.target(true, LiveKey.Folder(7), "FOLDER:12"))
    }

    @Test
    fun `saved folder or custom category is applied once it is in the rail`() {
        assertEquals(Step.APPLY, LiveCategoryMemory.step(LiveKey.Folder(12), LiveKey.All, rail))
        assertEquals(Step.APPLY, LiveCategoryMemory.step(LiveKey.Custom("custom:abc"), LiveKey.All, rail))
    }

    @Test
    fun `a rail still loading keeps waiting on All without a timeout`() {
        assertEquals(Step.WAIT, LiveCategoryMemory.step(LiveKey.Folder(12), LiveKey.All, fixed))
    }

    @Test
    fun `fixed entries apply at once`() {
        assertEquals(Step.APPLY, LiveCategoryMemory.step(LiveKey.Favorites, LiveKey.All, fixed))
        assertEquals(Step.APPLY, LiveCategoryMemory.step(LiveKey.All, LiveKey.Folder(12), rail))
    }

    @Test
    fun `a folder of another profile or playlist falls back to All`() {
        assertEquals(Step.FALLBACK, LiveCategoryMemory.step(LiveKey.Folder(99), LiveKey.Folder(99), rail))
        assertEquals(Step.FALLBACK, LiveCategoryMemory.step(LiveKey.Folder(99), LiveKey.Custom("custom:gone"), rail))
    }

    @Test
    fun `a valid category on screen is left alone while waiting`() {
        assertEquals(Step.WAIT, LiveCategoryMemory.step(LiveKey.Folder(99), LiveKey.Folder(12), rail))
        assertEquals(Step.WAIT, LiveCategoryMemory.step(LiveKey.Folder(99), LiveKey.All, rail))
    }
}
