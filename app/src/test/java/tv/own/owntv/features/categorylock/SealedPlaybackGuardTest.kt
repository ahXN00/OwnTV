package tv.own.owntv.features.categorylock

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SealedPlaybackGuardTest {

    private val stopped = mutableListOf<String>()
    private var now = PlayingCategories(live = null, liveInPlayer = false, vod = null)
    private val guard = SealedPlaybackGuard(
        playing = { now },
        stopLive = { inPlayer, archive -> stopped += "live inPlayer=$inPlayer archive=$archive" },
        stopVod = { stopped += "vod" },
        stopTiles = { categories -> stopped += "tiles of ${categories.sorted()}" },
    )

    private fun check(sealed: Set<Long>) = runBlocking { guard.stopIfSealed(guard.snapshot()) { it in sealed } }

    @Test
    fun aSealedLiveChannelStopsWithWhetherThePlayerShowedIt() {
        now = PlayingCategories(live = 7, liveInPlayer = true, vod = null)
        check(setOf(7))
        assertEquals(listOf("live inPlayer=true archive=false"), stopped)
    }

    @Test
    fun aSealedFilmStopsAndTheOpenLiveChannelPlaysOn() {
        now = PlayingCategories(live = 3, liveInPlayer = false, vod = 9)
        check(setOf(9))
        assertEquals(listOf("vod"), stopped)
    }

    @Test
    fun nothingSealedStopsNothing() {
        now = PlayingCategories(live = 3, liveInPlayer = true, vod = 9)
        check(setOf(1, 2))
        assertEquals(emptyList<String>(), stopped)
    }

    @Test
    fun anItemWithNoCategoryIsNeverAsked() {
        var asked = false
        runBlocking { guard.stopIfSealed(PlayingCategories(null, true, null)) { asked = true; true } }
        assertEquals(false, asked)
        assertEquals(emptyList<String>(), stopped)
    }

    @Test
    fun aSealedChannelArchiveStopsAsTheArchive() {
        now = PlayingCategories(live = 7, liveInPlayer = false, vod = null, liveArchive = true)
        check(setOf(7))
        assertEquals(listOf("live inPlayer=false archive=true"), stopped)
    }

    @Test
    fun onlyTheMultiviewTilesOfASealedCategoryStop() {
        now = PlayingCategories(live = null, liveInPlayer = false, vod = null, tiles = listOf(5, null, 6, 5))
        val asked = mutableListOf<Long>()
        runBlocking { guard.stopIfSealed(guard.snapshot()) { asked += it; it == 5L } }
        assertEquals(listOf("tiles of [5]"), stopped)
        assertEquals(listOf(5L, 6L), asked)
    }

    @Test
    fun theTilesToEmptyAreTheOnesHoldingASealedCategoryLastFirst() {
        assertEquals(listOf(3, 0), sealedTileIndices(listOf(5, null, 6, 5), setOf(5)))
    }

    @Test
    fun aTileRetunedDuringTheCheckIsJudgedByWhatItHoldsNow() {
        // The relock saw [5, 6]; while the locks were read the two tiles swapped channels.
        assertEquals(listOf(1), sealedTileIndices(listOf(6, 5), setOf(5)))
        assertEquals(emptyList<Int>(), sealedTileIndices(listOf(6, null), setOf(5)))
    }

    @Test
    fun noSealedTileStopsNoTile() {
        now = PlayingCategories(live = null, liveInPlayer = false, vod = null, tiles = listOf(5, 6))
        check(setOf(1))
        assertEquals(emptyList<String>(), stopped)
    }
}
