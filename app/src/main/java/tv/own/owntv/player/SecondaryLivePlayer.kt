package tv.own.owntv.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import tv.own.owntv.core.network.HttpClient

/**
 * A **second**, independent ExoPlayer that drives the picture-in-picture corner window — the half of
 * "true PiP" that lets you watch a different stream alongside the main one. It is deliberately separate
 * from [LivePreviewEngine] (which is busy being the in-pane preview / promoted-fullscreen live engine):
 * the two never share a player, a surface, or audio focus, so both can decode at once.
 *
 * ExoPlayer (not mpv) for the corner because a second mpv context would be heavy and invasive, whereas
 * ExoPlayer instances are light and independently constructable — the same reasoning that put the live
 * preview on ExoPlayer. mpv stays the single full-screen player, untouched.
 *
 * Scope: **live channels only** for now (no VOD seek/resume state). Like the other engines, all calls
 * must be on the main thread (ExoPlayer is single-threaded); the corner surface attaches/detaches its
 * [Surface] from the holder callback and the controller calls [play]/[stop]/[setMuted] from the UI.
 *
 * Memory note: a second decoder competes for the device's player budget (see [PlayerBudget] — TV-class
 * boxes get OOM-killed at a few hundred MB PSS). Buffers here are intentionally shallow, and the corner
 * is a single extra stream (not four), which real Android TV hardware comfortably handles in practice.
 */
@UnstableApi
class SecondaryLivePlayer(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val diagnostics: PlayerDiagnostics,
) : CornerEngine {

    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var muted: Boolean = true

    private val _state = MutableStateFlow(CornerState.IDLE)
    override val state: StateFlow<CornerState> = _state.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()
    private val _videoHeight = MutableStateFlow<Int?>(null)
    val videoHeight: StateFlow<Int?> = _videoHeight.asStateFlow()
    private val _meta = MutableStateFlow(MediaMeta())
    override val meta: StateFlow<MediaMeta> = _meta.asStateFlow()

    /** URL the corner is currently on (null when stopped) — lets the controller skip a redundant reload. */
    override var currentUrl: String? = null
        private set

    // Live auto-reconnect — a channel that played and then dropped (provider hiccup / Wi-Fi blip) re-fetches
    // from the live edge instead of dead-ending, mirroring LivePreviewEngine but simpler (no track menus).
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var hasPlayed = false
    private var retryCount = 0
    private val stallWatchdog = Runnable { reconnect("buffering stalled") }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.value = CornerState.LOADING; _buffering.value = true
                    if (hasPlayed) { mainHandler.removeCallbacks(stallWatchdog); mainHandler.postDelayed(stallWatchdog, STALL_MS) }
                }
                Player.STATE_READY -> {
                    _state.value = CornerState.PLAYING; _buffering.value = false
                    hasPlayed = true; retryCount = 0; mainHandler.removeCallbacks(stallWatchdog)
                }
                else -> { _buffering.value = false; mainHandler.removeCallbacks(stallWatchdog) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.height > 0) _videoHeight.value = videoSize.height
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.w(TAG, "corner ExoPlayer error: ${error.errorCodeName}", error)
            if (hasPlayed) { reconnect("error ${error.errorCodeName}"); return } // mid-stream drop → reconnect
            _state.value = CornerState.ERROR; _isPlaying.value = false; _buffering.value = false
        }
    }

    /** Attach the corner SurfaceView's surface, or null when it's destroyed. */
    override fun setSurface(s: Surface?) {
        surface = s
        if (s != null) player?.setVideoSurface(s) else player?.clearVideoSurface()
    }

    /** Start (or switch to) [url] in the corner. Starts muted by default — the main stream keeps the audio
     *  until the user explicitly hands sound to the corner. Never throws: a stream ExoPlayer can't open just
     *  shows the error state (the corner is best-effort; the main player is unaffected either way). */
    override fun play(url: String, meta: MediaMeta, muted: Boolean) {
        diagnostics.start(); diagnostics.markLoad()
        this.muted = muted
        currentUrl = url
        hasPlayed = false; retryCount = 0; mainHandler.removeCallbacks(stallWatchdog)
        _videoHeight.value = null
        _meta.value = meta
        _state.value = CornerState.LOADING
        _buffering.value = true
        runCatching {
            val p = player ?: build().also { player = it }
            surface?.let { p.setVideoSurface(it) }
            p.volume = if (muted) 0f else 1f
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.playWhenReady = true
        }.onFailure {
            android.util.Log.w(TAG, "corner play() failed for $url", it)
            _state.value = CornerState.ERROR
        }
    }

    override fun setMuted(m: Boolean) {
        muted = m
        player?.volume = if (m) 0f else 1f
    }

    /** Stop playback and free the decoder/connection, keeping the instance for the next corner channel. */
    override fun stop() {
        currentUrl = null
        hasPlayed = false; retryCount = 0; mainHandler.removeCallbacks(stallWatchdog)
        _videoHeight.value = null
        _isPlaying.value = false
        _buffering.value = false
        _state.value = CornerState.IDLE
        player?.run { stop(); clearMediaItems() }
    }

    fun release() {
        mainHandler.removeCallbacks(stallWatchdog)
        player?.run { removeListener(listener); release() }
        player = null
        surface = null
        currentUrl = null
        _state.value = CornerState.IDLE
    }

    private fun reconnect(reason: String) {
        mainHandler.removeCallbacks(stallWatchdog)
        val p = player
        val url = currentUrl
        if (p == null || url == null || retryCount >= MAX_RECONNECTS) {
            _state.value = CornerState.ERROR; _isPlaying.value = false; _buffering.value = false
            return
        }
        retryCount++
        _state.value = CornerState.LOADING; _buffering.value = true
        android.util.Log.w(TAG, "corner reconnect ($reason) — attempt $retryCount/$MAX_RECONNECTS")
        mainHandler.postDelayed({
            if (currentUrl != url) return@postDelayed // superseded (channel changed / closed)
            runCatching {
                p.setMediaItem(MediaItem.fromUri(url)) // fresh fetch (live edge)
                p.prepare()
                p.playWhenReady = true
            }.onFailure { _state.value = CornerState.ERROR }
        }, (1500L * retryCount).coerceAtMost(4000L))
    }

    private fun build(): ExoPlayer {
        val dataSource = OkHttpDataSource.Factory(okHttpClient).setUserAgent(HttpClient.DEFAULT_USER_AGENT)
        // Shallow buffers — the corner only needs to start quickly, not buffer deep (keeps memory down).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 8_000, 1_000, 2_000)
            .build()
        return ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .build()
            .apply { addListener(listener) }
    }

    companion object {
        private const val TAG = "SecondaryLivePlayer"
        private const val MAX_RECONNECTS = 6
        private const val STALL_MS = 12_000L
    }
}
