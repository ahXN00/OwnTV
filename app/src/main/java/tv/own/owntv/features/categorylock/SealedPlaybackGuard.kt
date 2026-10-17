package tv.own.owntv.features.categorylock

/**
 * The categories of what is on screen when a relock happens. [live] is the Live channel in the preview
 * pane or the player: [liveInPlayer] when the player shows it live, [liveArchive] when the player is on
 * that channel's archive (a catch-up programme or a rewind). [vod] is the film or episode the player is
 * on, and [tiles] the channel of each Multiview tile by tile index. Null: nothing there, or an item
 * with no category.
 */
internal data class PlayingCategories(
    val live: Long?,
    val liveInPlayer: Boolean,
    val vod: Long?,
    val liveArchive: Boolean = false,
    val tiles: List<Long?> = emptyList(),
)

/**
 * Stops what a relock (leaving the app, a profile change) has just sealed, so nothing from a locked
 * category plays on, resumes on return, or stays in the preview pane. The shell sets one up while it is
 * on screen. [playing] is read before the relock, while the engines still hold their streams; going to
 * the background frees them, and the player no longer knows which item it had.
 */
internal class SealedPlaybackGuard(
    private val playing: () -> PlayingCategories,
    private val stopLive: (inPlayer: Boolean, archive: Boolean) -> Unit,
    private val stopVod: () -> Unit,
    /** Gets the sealed categories the tiles held, to empty each tile still holding one of them. */
    private val stopTiles: (categories: Set<Long>) -> Unit = {},
) {
    fun snapshot(): PlayingCategories = playing()

    /** Stops each part of [now] whose category [seals] says is sealed. */
    suspend fun stopIfSealed(now: PlayingCategories, seals: suspend (categoryId: Long) -> Boolean) {
        if (now.live != null && seals(now.live)) stopLive(now.liveInPlayer, now.liveArchive)
        if (now.vod != null && seals(now.vod)) stopVod()
        val sealedTileCategories = now.tiles.filterNotNull().toSet().filterTo(mutableSetOf()) { seals(it) }
        if (sealedTileCategories.isNotEmpty()) stopTiles(sealedTileCategories)
    }
}

/**
 * The tiles to empty for [sealed] categories, by what each tile in [tiles] holds now rather than when
 * the relock looked: a tile can be retuned or move while the locks are checked. Last first, so emptying
 * one never moves the index of one still to empty.
 */
internal fun sealedTileIndices(tiles: List<Long?>, sealed: Set<Long>): List<Int> =
    tiles.indices.filter { tiles[it] in sealed }.sortedDescending()
