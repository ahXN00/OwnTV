package tv.own.owntv.features.series

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RandomEpisodePickerTest {
    @Test
    fun emptyListReturnsNull() {
        assertNull(pickRandomEpisode(emptyList<Int>()))
    }

    @Test
    fun singleElementIsReturned() {
        assertEquals(7, pickRandomEpisode(listOf(7)))
    }

    @Test
    fun picksByTheInjectedRandomSource() {
        val items = listOf("a", "b", "c")
        assertEquals("a", pickRandomEpisode(items) { 0.0 })
        assertEquals("b", pickRandomEpisode(items) { 0.5 })
        assertEquals("c", pickRandomEpisode(items) { 0.99 })
    }

    @Test
    fun clampsWhenRandomReturnsOne() {
        val items = listOf("a", "b")
        assertEquals("b", pickRandomEpisode(items) { 1.0 })
    }
}
