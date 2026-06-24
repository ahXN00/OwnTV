package tv.own.owntv.features.multiview

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.player.CornerEngine
import tv.own.owntv.player.MediaMeta

/** How the MultiView tiles are arranged on screen. */
enum class MultiLayout {
    /** Every tile the same size (1 / 2-up / 3-up / 2×2). */
    GRID,

    /** One dominant tile filling most of the screen, the rest as a small strip beside it. */
    DOMINANT,
}

/**
 * Drives **MultiView** — up to [MAX] live streams on screen at once (PR 2, building on the PiP engine).
 *
 * Architecture: OwnTV's live full-screen player is ExoPlayer, so MultiView is simply **N ExoPlayer tiles**.
 * Each tile is a [tv.own.owntv.player.SecondaryLivePlayer] — the same constrained second decoder hardened
 * for PiP (a resolution-cap hint, stereo, no subtitles, software-decoder fallback, no audio focus), which is
 * exactly what running several concurrent decoders on TV hardware needs. The cap is smaller than a PiP corner
 * (a quarter-screen tile never needs 720p); it picks a lower rendition for adaptive streams (no transcoding),
 * and for single-resolution streams the safeguard is the software-decoder fallback — so on a weak SoC four
 * full-res streams lean on CPU rather than failing. Concurrent-decoder limits are real; tile count may need
 * to be device-tiered (like [tv.own.owntv.player.PlayerBudget]) in a follow-up.
 *
 * Only the **active** tile is audible (audio follows D-pad focus — two soundtracks would be cacophony) and,
 * in [MultiLayout.DOMINANT], the active tile is the large one. The engine pool is created lazily and reused
 * across sessions; [exit] stops every decoder so MultiView never holds streams open in the background.
 *
 * Pure state + thin engine calls, so the whole thing is unit-testable with fake [CornerEngine]s — no real
 * decoder is ever spun up in a test.
 */
class MultiViewController(
    /** Hard ceiling for this device — [MAX] on capable hardware, fewer on low-RAM boxes (see [deviceMaxTiles]). */
    val maxTiles: Int,
    private val engineFactory: () -> CornerEngine,
) {

    private val pool = arrayOfNulls<CornerEngine>(MAX)

    private val _tiles = MutableStateFlow<List<ChannelEntity>>(emptyList())
    /** The channels currently on screen, one per tile (index = tile slot). */
    val tiles: StateFlow<List<ChannelEntity>> = _tiles.asStateFlow()

    private val _activeIndex = MutableStateFlow(0)
    /** The focused tile — the only audible one, and the dominant one in [MultiLayout.DOMINANT]. */
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    private val _layout = MutableStateFlow(MultiLayout.GRID)
    val layout: StateFlow<MultiLayout> = _layout.asStateFlow()

    private val _active = MutableStateFlow(false)
    /** True while the MultiView screen is up. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** The engine backing tile [index] (lazily created). The UI renders its surface. */
    fun engineAt(index: Int): CornerEngine = pool[index] ?: engineFactory().also { pool[index] = it }

    /** Enter MultiView with [channels] (clamped to [maxTiles]); the first tile starts active/audible. */
    fun enter(channels: List<ChannelEntity>) {
        val take = channels.take(maxTiles)
        if (take.isEmpty()) return
        _tiles.value = take
        _activeIndex.value = 0
        _active.value = true
        take.forEachIndexed { i, ch -> startTile(i, ch) }
        applyAudio()
    }

    /** True while another tile can be added on this device (room under [maxTiles]). */
    val canAddTile: Boolean get() = _tiles.value.size < maxTiles

    /** Add [channel] as a new tile if there's room (< [maxTiles]); it starts muted (the active tile keeps audio). */
    fun addTile(channel: ChannelEntity) {
        val current = _tiles.value
        if (current.size >= maxTiles) return
        val index = current.size
        _tiles.value = current + channel
        _active.value = true
        startTile(index, channel)
        applyAudio()
    }

    /** Remove tile [index], stopping its decoder. Exits MultiView if it was the last tile. */
    fun removeTile(index: Int) {
        val current = _tiles.value
        if (index !in current.indices) return
        // The engine pool is positional; free the now-unused tail slot, then rebuild the list.
        pool[current.lastIndex]?.release(); pool[current.lastIndex] = null
        // Reassign channels to slots 0..n-2 so engine[i] always matches tiles[i].
        val remaining = current.toMutableList().apply { removeAt(index) }
        if (remaining.isEmpty()) { exit(); return }
        _tiles.value = remaining
        remaining.forEachIndexed { i, ch -> startTile(i, ch) } // re-bind slots to the new order (idempotent)
        _activeIndex.value = _activeIndex.value.coerceIn(0, remaining.lastIndex)
        applyAudio()
    }

    /** Move focus to tile [index] — it becomes the only audible one (and the dominant one in DOMINANT mode). */
    fun setActive(index: Int) {
        if (index !in _tiles.value.indices) return
        _activeIndex.value = index
        applyAudio()
    }

    /** Make tile [index] the large/dominant tile (and switch to the dominant layout). */
    fun promoteToDominant(index: Int) {
        if (index !in _tiles.value.indices) return
        _activeIndex.value = index
        _layout.value = MultiLayout.DOMINANT
        applyAudio()
    }

    /** Toggle between the equal grid and the dominant-plus-small layout. */
    fun toggleLayout() {
        _layout.value = if (_layout.value == MultiLayout.GRID) MultiLayout.DOMINANT else MultiLayout.GRID
    }

    /** Leave MultiView, **releasing** every tile's player so MultiView holds no decoder or memory while
     *  closed — important on resource-constrained boxes. The (cheap) instances are rebuilt on next entry. */
    fun exit() {
        for (i in pool.indices) { pool[i]?.release(); pool[i] = null }
        _tiles.value = emptyList()
        _activeIndex.value = 0
        _layout.value = MultiLayout.GRID
        _active.value = false
    }

    private fun startTile(index: Int, channel: ChannelEntity) {
        val engine = engineAt(index)
        if (engine.currentUrl != channel.streamUrl) {
            engine.play(channel.streamUrl, meta = MediaMeta(title = channel.name, logoUrl = channel.logoUrl), muted = true)
        }
    }

    /** Exactly one tile is audible: the active one. Pure rule, also exercised in tests. */
    private fun applyAudio() {
        val active = _activeIndex.value
        _tiles.value.indices.forEach { i -> pool[i]?.setMuted(i != active) }
    }

    companion object {
        /** Absolute ceiling on simultaneous streams (capable hardware). */
        const val MAX = 4

        /** Per-tile resolution cap — a quarter-screen tile never needs more, and keeping it low is what lets
         *  several software-decoded streams coexist on a weak SoC (selection hint only; never transcodes). */
        const val TILE_MAX_HEIGHT = 480

        /**
         * How many tiles this device should allow. Low-RAM boxes (the `isLowRamDevice` flag, or < ~3 GB)
         * realistically can't run four concurrent decoders, so they cap at 2 — better two solid streams than
         * four stuttering ones. Mirrors the device-scaled philosophy of [tv.own.owntv.player.PlayerBudget].
         */
        fun deviceMaxTiles(context: android.content.Context): Int {
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val totalGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
            return if (am.isLowRamDevice || totalGb < 3.0) 2 else MAX
        }
    }
}
