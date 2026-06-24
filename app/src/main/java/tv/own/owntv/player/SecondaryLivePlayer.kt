package tv.own.owntv.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import tv.own.owntv.core.network.HttpClient

/**
 * A **second**, independent ExoPlayer that drives the picture-in-picture corner window — the half of
 * "true PiP" that lets you watch a different stream alongside the main one. It is deliberately separate
 * from [LivePreviewEngine] (the in-pane preview / promoted-fullscreen live engine): the two never share a
 * player, a surface, or audio focus, so both can decode at once.
 *
 * Architecture: OwnTV's **live** full-screen player is ExoPlayer ([LivePreviewEngine] promoted; mpv is the
 * fallback and the **VOD** full-screen player). So live PiP runs **two ExoPlayer instances** at once — the
 * main one plus this corner. ExoPlayer (not a second mpv context) because instances are light and
 * independently constructable, and it matches whatever the live main is already doing.
 *
 * Coexistence: two ExoPlayers competing for the device's scarce hardware decoders / single audio-passthrough
 * path is the real risk on TV hardware, so [build] constrains this corner — a 720p resolution-cap selection
 * hint (picks a lower rendition when the stream is adaptive; a no-op, never a transcode, when it isn't),
 * stereo/AAC audio (no surround passthrough), subtitles disabled, **software-decoder fallback** (the real
 * safeguard when no second hardware decoder is free), and it never takes audio focus. That keeps the surround
 * output — and, for adaptive streams, the 4K/HDR hardware decoder — with the main stream.
 *
 * Scope: **live channels only** for now (no VOD seek/resume state). Like the other engines, all calls must
 * be on the main thread (ExoPlayer is single-threaded); the corner surface attaches/detaches its [Surface]
 * from the holder callback and the controller calls [play]/[stop]/[setMuted] from the UI.
 *
 * Memory note: a second decoder competes for the device's player budget (see [PlayerBudget] — TV-class
 * boxes get OOM-killed at a few hundred MB PSS). Buffers here are intentionally shallow, and the corner is
 * a single capped-resolution extra stream, which real Android TV hardware handles in practice.
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
        // The corner is a SECOND ExoPlayer running alongside the main one (which, for live, is also ExoPlayer).
        // It's deliberately constrained so the two instances coexist on real TV hardware, where concurrent
        // hardware decoders / audio passthrough are scarce:
        //  • Cap the corner to 720p30. This is a track-SELECTION hint, NOT transcoding: for an ADAPTIVE stream
        //    (multi-variant HLS/DASH, common in IPTV) ExoPlayer picks a lower rendition the provider already
        //    sends — a real saving (smaller decoder, less bandwidth). For a single-resolution stream there's
        //    nothing lower to pick, so it decodes the full stream (the cap is a harmless no-op); the safeguard
        //    there is decoder fallback below, plus the small surface (cheap HW downscale for display).
        //  • Downmix to stereo + prefer AAC — the corner must not grab the single audio-passthrough path
        //    (AC3/E-AC3/DTS surround), so the main keeps its surround output intact.
        //  • No subtitles in the corner (it's a thumbnail) — avoids a second image-subtitle render path.
        val trackSelector = DefaultTrackSelector(context)
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setMaxVideoSize(1280, 720)
            .setMaxVideoFrameRate(30)
            .setMaxAudioChannelCount(2)
            .setPreferredAudioMimeType(MimeTypes.AUDIO_AAC)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        // The real coexistence safeguard: if no second HARDWARE decoder is free (TV SoCs typically allow only
        // 1–2 concurrent), fall back to a SOFTWARE codec instead of failing the corner. SW decode costs CPU —
        // the honest price of a second full-resolution stream when the provider offers no smaller rendition.
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .build()
            .apply {
                // Never take audio focus — the main player owns it. The corner is muted unless the user hands
                // it the sound, and even then it shares the session without yanking focus from the main stream.
                setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, /* handleAudioFocus = */ false)
                addListener(listener)
            }
    }

    companion object {
        private const val TAG = "SecondaryLivePlayer"
        private const val MAX_RECONNECTS = 6
        private const val STALL_MS = 12_000L
    }
}
