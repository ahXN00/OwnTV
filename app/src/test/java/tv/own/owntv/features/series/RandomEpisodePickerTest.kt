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

    @Test
    fun shuffleLeavesOutSpecials() {
        val episodes = listOf(0 to "special", 1 to "s1e1", 2 to "s2e1")
        assertEquals(listOf(1 to "s1e1", 2 to "s2e1"), shuffleCandidates(episodes) { it.first })
    }

    @Test
    fun shuffleKeepsSpecialsWhenThereIsNothingElse() {
        val episodes = listOf(0 to "special a", 0 to "special b")
        assertEquals(episodes, shuffleCandidates(episodes) { it.first })
    }

    @Test
    fun shuffleOfNoEpisodesIsEmpty() {
        assertEquals(emptyList<Pair<Int, String>>(), shuffleCandidates(emptyList<Pair<Int, String>>()) { it.first })
    }
}
