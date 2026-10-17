package tv.own.owntv.features.categorylock

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A relock on a profile change checks what plays against the outgoing profile's locks too. */
class RelockProfileTest {

    @Test
    fun aChangeGivesTheOutgoingProfileAndTheFirstGivesItself() {
        val profile = RelockProfile()
        assertNull(profile.current)
        assertEquals(1L, profile.changeTo(1))
        assertEquals(1L, profile.changeTo(2))
        assertEquals(2L, profile.current)
    }

    @Test
    fun theOpenerAndTheActiveProfileAreEachCheckedOnce() {
        assertEquals(listOf(1L, 2L), relockedProfiles(opener = 1, active = 2))
        assertEquals(listOf(2L), relockedProfiles(opener = 2, active = 2))
        assertEquals(listOf(2L), relockedProfiles(opener = null, active = 2))
        assertEquals(listOf(1L), relockedProfiles(opener = 1, active = -1))
    }

    @Test
    fun deletingTheActiveProfileStopsWhatItsLockSealsThoughTheNextOneHasNoLock() {
        // Profile 1 locks category 7 and its PIN opened it; profile 2, which takes over without the picker, locks nothing.
        val locks = mapOf(1L to setOf(7L), 2L to emptySet())
        val stopped = mutableListOf<String>()
        val guard = SealedPlaybackGuard(
            playing = { PlayingCategories(live = 7, liveInPlayer = true, vod = null) },
            stopLive = { _, _ -> stopped += "live" },
            stopVod = { stopped += "vod" },
        )
        val profile = RelockProfile().apply { changeTo(1) }
        val opener = profile.changeTo(2)
        runBlocking {
            guard.stopIfSealed(guard.snapshot()) { category ->
                relockedProfiles(opener, active = 2).any { category in locks.getValue(it) }
            }
        }
        assertEquals(listOf("live"), stopped)
    }
}
