package tv.own.owntv.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import okhttp3.OkHttpClient
import tv.own.owntv.core.network.HttpClient

/**
 * ExoPlayer (Media3) that drives the muted **in-pane Live preview**. ExoPlayer starts HLS far faster than
 * mpv (which full-probes ~5 s before the first frame), so scrolling the channel list feels responsive.
 *
 * The **full** player stays on mpv (4K/HDR direct path, broad IPTV/raw-TS compatibility) — going fullscreen
 * [stop]s this engine and hands the channel to mpv. Preview and fullscreen use separate SurfaceViews on
 * separate screens, so the two decoders never share a surface. A single long-lived instance (Koin single),
 * like [OwnTVPlayer]; it's [stop]ped (not released) whenever the preview isn't on screen.
 *
 * All calls must be on the main thread (ExoPlayer is single-threaded): the VM invokes [play]/[stop]/
 * [setMuted] from the UI thread and the Compose surface invokes [setSurface] from the holder callback.
 */
@UnstableApi
class LivePreviewEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val diagnostics: PlayerDiagnostics,
    settings: tv.own.owntv.features.settings.data.SettingsRepository,
) : PlaybackEngine {

    // Escape-hatch toggle (Settings → Video player → Diagnostics). When off, no live fps/bitrate
    // measuring runs on this engine — declared values only. Never affects the playback pipeline.
    @Volatile private var measuredStatsEnabled = true
    private val settingsFlow = settings.measuredStreamStats
    private val settingsScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
    // Live latency (#72): target live-edge offset in seconds; null = engine default (Balanced). Applied
    // as a MediaItem.LiveConfiguration on the next channel open.
    @Volatile private var liveBufferSecs: Int? = null
    enum class State { IDLE, LOADING, PLAYING, ERROR }

    init { LiveDiagnosticsLog.init(context) }

    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var muted: Boolean = true

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _videoHeight = MutableStateFlow<Int?>(null)
    val videoHeight: StateFlow<Int?> = _videoHeight.asStateFlow()
    // PAR-corrected display aspect (w/h) + native pixel (w, h), used by ExoPreviewSurface's zoom/letterbox
    // sizing (see Modifier.videoZoom). Mirrors OwnTVPlayer._videoAspect/_videoSize so live-on-ExoPlayer
    // zooms identically to live-on-mpv / VOD.
    private val _videoAspect = MutableStateFlow<Float?>(null)
    val videoAspect: StateFlow<Float?> = _videoAspect.asStateFlow()
    private val _videoSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoSize: StateFlow<Pair<Int, Int>?> = _videoSize.asStateFlow()
    // Up-to-4 mini stream chips for the preview pane / player top bar: aspect · resolution · fps · audio.
    private val _streamChips = MutableStateFlow<List<String>>(emptyList())
    override val streamChips: StateFlow<List<String>> = _streamChips.asStateFlow()
    // This engine IS ExoPlayer — static first chip for the fullscreen top bar.
    override val engineChip: StateFlow<String?> = MutableStateFlow("EXO")

    // --- PlaybackEngine: lets the full-screen HUD drive a promoted preview (play/pause, state, volume) ---
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _buffering = MutableStateFlow(false)
    override val buffering: StateFlow<Boolean> = _buffering.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()
    private val _errorInfo = MutableStateFlow<ErrorInfo?>(null)
    override val errorInfo: StateFlow<ErrorInfo?> = _errorInfo.asStateFlow()
    private val _videoRes = MutableStateFlow<String?>(null)
    override val videoRes: StateFlow<String?> = _videoRes.asStateFlow()
    private val _volume = MutableStateFlow(100)
    override val volume: StateFlow<Int> = _volume.asStateFlow()
    private val _zoomMode = MutableStateFlow(ZoomMode.FIT)
    override val zoomMode: StateFlow<ZoomMode> = _zoomMode.asStateFlow()
    private val _audioCount = MutableStateFlow(0)
    override val audioCount: StateFlow<Int> = _audioCount.asStateFlow()
    private val _subCount = MutableStateFlow(0)
    override val subCount: StateFlow<Int> = _subCount.asStateFlow()
    // Audio/text tracks enumerated from the active stream (multi-language live, or a VOD file added via M3U).
    private var audioTrackList: List<TrackOption> = emptyList()
    private var audioSelections: List<AudioSel> = emptyList()
    private var textTrackList: List<TrackOption> = emptyList()
    private var textSelections: List<TextSel> = emptyList()
    private data class AudioSel(val id: Int, val group: androidx.media3.common.TrackGroup, val trackIndex: Int)
    private data class TextSel(val id: Int, val group: androidx.media3.common.TrackGroup, val trackIndex: Int)
    // Subtitle cues + an "on" flag. The Compose surface mounts a SubtitleView ONLY while [subtitleOn] (else
    // any overlaid view knocks the SurfaceView off the hardware-overlay path and stutters 4K — same as VOD).
    private val _cues = MutableStateFlow<List<androidx.media3.common.text.Cue>>(emptyList())
    val cues: StateFlow<List<androidx.media3.common.text.Cue>> = _cues.asStateFlow()
    private val _subtitleOn = MutableStateFlow(false)
    val subtitleOn: StateFlow<Boolean> = _subtitleOn.asStateFlow()
    // True when the stream HAS audio but ExoPlayer can decode NONE of it (e.g. AC3/E-AC3/DTS on a device
    // without that decoder) — the VM hands such a stream to mpv (FFmpeg decodes everything) so it isn't silent.
    private val _audioUnsupported = MutableStateFlow(false)
    val audioUnsupported: StateFlow<Boolean> = _audioUnsupported.asStateFlow()
    // One-shot per load: audio/position is progressing normally and a video track exists, but ExoPlayer has
    // never rendered a single frame of it — the "audio plays, no picture" case the freeze/frame watchdogs
    // below can't see (they only catch a freeze AFTER frames were once seen, or a total position stall).
    // The VM observes this to try the existing mpv fallback once; legitimate audio-only streams never have
    // a video track, so they never set this.
    private val _noVideoDetected = MutableStateFlow(false)
    val noVideoDetected: StateFlow<Boolean> = _noVideoDetected.asStateFlow()
    private var noVideoTriggered = false
    private var readySinceMs = 0L
    // Set true once this tune is observed to be UHD (>1080p). Cheap panels (e.g. some Hisense) leak the 4K
    // hardware decoder if it's merely parked/reused (ExoPlayer's normal stop) instead of fully released —
    // every later channel then waits ~20 s for a decoder slot until the TV reboots. So when we LEAVE a UHD
    // channel via stop() (Back / exit fullscreen / background / leaving the list) we fully release+rebuild
    // the ExoPlayer so its MediaCodec is handed back cleanly. Deliberately NOT triggered from play(): that
    // path is also the preview-pane re-tune on every focus, and rebuilding there churns 4K previews and
    // pushes borderline streams into the mpv fallback. Scoped to UHD only — SD/HD keeps the fast reuse path.
    @Volatile private var sawUhd = false

    // Programmatic codec/audio errors (Reviewer: more reliable than logcat for ExoPlayer, and survives the
    // Android 14+ own-logcat lockdown). MediaCodec.CodecException.diagnosticInfo carries the exact code
    // (e.g. 0x80001000); AudioSink errors name the audio failure. Reset per load, preferred when present.
    @Volatile private var lastCodecError: String? = null
    @Volatile private var lastVideoDecoder: String? = null // e.g. "OMX.realtek.video.decoder", for the spec line
    private val throughputTracker = ThroughputTracker()
    private val fpsSample = FpsSample()
    private var dropsBaseline = 0

    init {
        // Keep the escape-hatch flag current; turning it off stops any in-flight measuring immediately.
        settingsFlow.onEach { measuredStatsEnabled = it; if (!it) throughputTracker.setEnabled(false) }
            .launchIn(settingsScope)
        settings.liveBufferSeconds.onEach { liveBufferSecs = it }.launchIn(settingsScope)
    }
    private val analytics = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
        override fun onVideoCodecError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, videoCodecError: Exception) {
            lastCodecError = codecDetail("video", videoCodecError)
        }
        override fun onAudioCodecError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, audioCodecError: Exception) {
            lastCodecError = codecDetail("audio", audioCodecError)
        }
        override fun onAudioSinkError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, audioSinkError: Exception) {
            lastCodecError = "audio: ${audioSinkError.message ?: audioSinkError.javaClass.simpleName}"
        }
        override fun onVideoDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            lastVideoDecoder = decoderName
            dropsBaseline = currentDroppedFrames(player) // a new decoder session may start its own counters
        }
    }

    /** "HEVC 1920x1080 • OMX.realtek.video.decoder" from the active stream, for the error screen's spec line. */
    private fun exoSpec(): String? {
        val f = player?.videoFormat
        val codec = f?.sampleMimeType?.substringAfterLast('/')?.let { mimeName(it) }
        val res = if (f != null && f.width > 0 && f.height > 0) "${f.width}x${f.height}" else null
        val head = listOfNotNull(codec, res).joinToString(" ").ifBlank { null }
        return listOfNotNull(head, lastVideoDecoder).joinToString(" • ").ifBlank { null }
    }
    private fun mimeName(m: String) = when (m.lowercase()) {
        "hevc" -> "HEVC"; "avc" -> "H.264"; "av01" -> "AV1"; "x-vnd.on2.vp9", "vp9" -> "VP9"
        "mp4v-es" -> "MPEG-4"; "mpeg2" -> "MPEG-2"; else -> m.uppercase()
    }
    private fun codecDetail(kind: String, e: Exception): String {
        (e as? android.media.MediaCodec.CodecException)?.let { return "$kind codec: ${it.diagnosticInfo}" }
        return "$kind codec: ${e.message ?: e.javaClass.simpleName}"
    }

    /** Technical readout for the stream-info overlay, from the active ExoPlayer formats. */
    override fun streamInfo(): List<Pair<String, String>> {
        val p = player ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        out += "Engine" to "ExoPlayer"
        p.videoFormat?.let { f ->
            val line = listOfNotNull(
                f.sampleMimeType?.substringAfterLast('/')?.let { mimeName(it) },
                if (f.width > 0 && f.height > 0) "${f.width}×${f.height}" else null,
                displayFps(f)?.let { "%.2f fps".format(it) },
            ).joinToString(" · ")
            if (line.isNotBlank()) out += "Video" to line
            when (f.colorInfo?.colorTransfer) {
                C.COLOR_TRANSFER_ST2084 -> "HDR10 (PQ)"; C.COLOR_TRANSFER_HLG -> "HLG"; else -> null
            }?.let { out += "HDR" to it }
            out += bitrateRow(f, throughputTracker)
        }
        out += "Decoder" to "ExoPlayer (hardware)"
        p.audioFormat?.let { f ->
            val line = listOfNotNull(
                f.sampleMimeType?.substringAfterLast('/')?.uppercase(),
                when (f.channelCount) { 1 -> "mono"; 2 -> "stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> null },
                if (f.sampleRate > 0) "%.0f kHz".format(f.sampleRate / 1000.0) else null,
            ).joinToString(" · ")
            if (line.isNotBlank()) out += "Audio" to line
        }
        bufferRow(p, dropsBaseline)?.let { out += it }
        currentUrl?.let { out += "Source" to HttpClient.redactUrl(it) }
        return out
    }
    /** Recompute the preview's mini chips (aspect · resolution · fps · audio · bitrate) from the active
     *  formats. Bitrate is the declared [Format.bitrate] only — measuring live throughput on every
     *  preview stream drags 4K playback, so the chip stays blank for raw MPEG-TS (the debug overlay
     *  still shows a measured value when opened). */
    private fun updateStreamChips() {
        val p = player ?: run { _streamChips.value = emptyList(); return }
        val chips = ArrayList<String>(5)
        p.videoFormat?.let { f ->
            if (f.width > 0 && f.height > 0) aspectLabel(f.width, f.height)?.let { chips += it }
            qualityLabel(f.width, f.height)?.let { chips += it }
            displayFps(f)?.let { chips += "${Math.round(it)} FPS" }
            f.bitrate.takeIf { it > 0 }?.let { chips += "%.1f Mbps".format(it / 1_000_000.0) }
        }
        p.audioFormat?.let { f ->
            (when (f.channelCount) { 1 -> "MONO"; 2 -> "STEREO"; 6 -> "5.1"; 8 -> "7.1"; else -> null })?.let { chips += it }
        }
        _streamChips.value = chips
    }

    private fun displayFps(f: Format) = f.frameRate.takeIf { it > 0 } ?: fpsSample.lastFps

    override fun refreshStreamChips() = ensureFpsMeasurement()
    override fun setBitrateTrackingEnabled(enabled: Boolean) = throughputTracker.setEnabled(enabled && measuredStatsEnabled)

    private fun ensureFpsMeasurement() {
        if (!measuredStatsEnabled) return // escape hatch: no live fps measuring at all
        if ((player?.videoFormat?.frameRate ?: 0f) <= 0f) restartFpsMeasurement()
    }

    private fun aspectLabel(w: Int, h: Int): String? {
        if (w <= 0 || h <= 0) return null
        val r = w.toFloat() / h
        return when {
            r in 1.72f..1.82f -> "16:9"
            r in 1.28f..1.40f -> "4:3"
            r >= 2.15f -> "21:9"
            r in 1.55f..1.66f -> "16:10"
            else -> "%.2f:1".format(r)
        }
    }
    private fun qualityLabel(w: Int, h: Int): String? {
        if (h <= 0) return null
        if (h < 480) return "${h}p"
        if (w <= 0) return null
        val area = w.toLong() * h.toLong()
        return standards.minByOrNull { (_, stdArea) ->
            abs(area - stdArea).toDouble() / stdArea.toDouble()
        }?.label
    }

    private data class ResStd(val label: String, val pixelArea: Long)

    private val standards = listOf(
        ResStd("4K",     8_294_400),     // 3840×2160
        ResStd("1440p",  3_686_400),     // 2560×1440
        ResStd("1080p",  2_073_600),     // 1920×1080
        ResStd("720p",   921_600),       // 1280×720
        ResStd("480p",   409_920),       // 854×480
    )

    private val _currentMeta = MutableStateFlow(MediaMeta())
    override val currentMeta: StateFlow<MediaMeta> = _currentMeta.asStateFlow()
    override val isLiveContent: Boolean = true

    /** URL the preview is currently on (null when stopped) — lets the VM skip a redundant reload. */
    var currentUrl: String? = null
        private set

    /**
     * Reconnect URL provider — set ONLY when the current item's URL is short-lived and must be
     * re-minted on reconnect (Stalker live, plan §5.4.1). Before a reconnect/HUD-retry reload, the
     * engine awaits this; a null provider or null result → replay [currentUrl] as-is (M3U/Xtream,
     * whose URLs are stable). Installed/cleared by `LiveViewModel` on each tune.
     */
    @Volatile var reconnectUrlProvider: tv.own.owntv.core.stalker.ReconnectUrlProvider? = null


    // Live auto-reconnect: a channel that DID play and then errors/stalls (provider hiccup / Wi-Fi blip)
    // re-fetches from the live edge instead of dead-ending. A channel that NEVER opened keeps the old
    // ERROR (so the VM falls back to mpv). retryCount resets whenever playback goes healthy again.
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    /** Scope for the reconnect URL-provider (awaiting its suspend freshUrl() off-main, then reloading on main). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hasPlayed = false
    private var retryCount = 0
    // Set just before our own stop()/release() touches the player, so the STATE_IDLE that follows is
    // recognized as a clean, self-caused cancellation rather than an unexpected mid-live drop.
    private var stoppingIntentionally = false
    // A single failed prepare() fires both onPlayerError AND the STATE_IDLE that follows it — without this
    // guard each one called reconnect() independently and burned two retryCount slots for one real failure.
    // Set true while a reconnect's delayed re-prepare is scheduled/running; cleared right before that
    // prepare() call so the NEXT genuine failure (from that prepare) is free to trigger its own reconnect.
    private var reconnectPending = false
    // Set true once retryCount is exhausted and we've surfaced the terminal error; stops the stallWatchdog
    // from re-arming and stops error/IDLE from calling reconnect() again until a fresh play()/retry().
    private var gaveUp = false
    private val stallWatchdog = Runnable { reconnect("buffering stalled") }

    // Silent-freeze watchdog. A live HLS feed can keep ExoPlayer in STATE_READY with the playback CLOCK
    // still advancing — no buffering event, no onPlayerError — while the video renderer has stopped
    // producing frames (a provider encoder/codec hiccup, a stale/empty segment, a mid-stream codec change).
    // That looks exactly like a frozen channel with "nothing happening", and a position-only watchdog misses
    // it because currentPosition keeps marching with the timeline. So we also tick a counter on every frame
    // actually rendered to the surface (VideoFrameMetadataListener, wired in build()); if frames stop while
    // ExoPlayer insists it's playing, the feed is dead → reconnect. Position-stall (a fully dead feed where
    // even the clock stopped) is kept as a second trigger; audio-only channels have no video frames, so they
    // rely on that position trigger alone.
    private val frameCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private var lastFrameCount = 0
    // Latched true once we've seen ANY rendered frame this load. Frame-based freeze detection only fires
    // AFTER this — so if the per-frame hook silently failed to register (or a stream renders no video at
    // all), we never false-trigger a reconnect on healthy playback; we fall back to the position check.
    private var everRendered = false
    private var videoRenderer: Renderer? = null
    private val frameListener = VideoFrameMetadataListener { _, _, _, _ -> frameCounter.incrementAndGet() }
    private var fpsAttempts = 0
    private val fpsFastRefresh: Runnable = Runnable {
        val fresh = player?.let { fpsSample.peek(it) }
        fpsAttempts++
        // Retry until a reading matches a standard rate, capped so a genuinely unusual one doesn't retry forever.
        val done = fpsAttempts >= FPS_MAX_ATTEMPTS || (fpsAttempts >= 2 && fpsSample.confident)
        if (done) {
            fresh?.let { fpsSample.publish(it) }
            updateStreamChips()
        } else {
            mainHandler.postDelayed(fpsFastRefresh, FPS_TICK_MS)
        }
    }
    private fun restartFpsMeasurement() {
        mainHandler.removeCallbacks(fpsFastRefresh)
        fpsSample.resetWindow() // keeps the old reading visible until replaced
        fpsAttempts = 0
        mainHandler.postDelayed(fpsFastRefresh, FPS_BASELINE_MS)
    }
    private var lastProgressPos = -1L
    private var lastProgressWallMs = 0L // SystemClock.elapsedRealtime() of the last forward position move
    private var frozenChecks = 0
    private val progressWatchdog = object : Runnable {
        override fun run() {
            val p = player
            // Gate on INTENT to play (playWhenReady && STATE_READY), NOT isPlaying. isPlaying drops to false
            // during transient playback suppression and brief internal stalls WITHOUT entering STATE_BUFFERING
            // or STATE_ERROR — and the old gate then reset the freeze counter every poll, so a real frozen-
            // but-"ready" channel was never caught (no spinner / reconnect / error). playWhenReady stays true
            // through those flickers, which is exactly the "should be advancing but isn't" condition we want.
            if (p != null && hasPlayed && p.playWhenReady && p.playbackState == Player.STATE_READY) {
                val now = android.os.SystemClock.elapsedRealtime()
                val frames = frameCounter.get()
                val hasVideo = p.videoFormat != null
                if (frames > 0) everRendered = true
                val pos = p.currentPosition
                val posAdvanced = pos > 0 && pos != lastProgressPos
                if (posAdvanced) { lastProgressPos = pos; lastProgressWallMs = now }
                else if (lastProgressWallMs == 0L) lastProgressWallMs = now // seed on the first ready poll
                // Audio-plays-no-video: a video track exists but has never rendered a single frame, even
                // though we're not in the total-freeze case above (position/audio clock IS advancing). Only
                // fires once per load so the VM's one-shot mpv fallback isn't retriggered after it acts.
                if (!_audioOnly.value && !noVideoTriggered && hasVideo && !everRendered && now - readySinceMs >= NO_VIDEO_TIMEOUT_MS) {
                    noVideoTriggered = true
                    LiveDiagnosticsLog.event("progressWatchdog: no video frame after ${now - readySinceMs}ms (pos=$pos advancing, video track present)")
                    _noVideoDetected.value = true
                }
                // Backstop: zero forward progress for the whole window while we intend to play == a dead feed.
                // Wall-clock based, so it CAN'T be missed by isPlaying flicker or a non-functional frame hook.
                val noProgressMs = now - lastProgressWallMs
                if (noProgressMs >= FREEZE_TIMEOUT_MS) {
                    LiveDiagnosticsLog.event("progressWatchdog: no-progress detected for ${noProgressMs}ms (pos=$pos, state=READY, frameHook=$everRendered)")
                    frozenChecks = 0
                    reconnect("stream frozen — no progress ${noProgressMs}ms"); return
                }
                // Picture frozen but the live clock still advances (position moving) — only the rendered-frame
                // count can see this. Guarded by everRendered so a non-functional frame hook can't false-fire.
                // In Audio Mode the surface is intentionally detached, so no frames render and the count
                // sits still — that's expected, not a frozen picture. Skip the frame-based freeze check;
                // the position/no-progress backstop above still catches a genuinely dead feed.
                val framesStuck = !_audioOnly.value && everRendered && hasVideo && frames == lastFrameCount
                lastFrameCount = frames
                if (framesStuck) {
                    if (++frozenChecks >= FROZEN_LIMIT) {
                        LiveDiagnosticsLog.event("progressWatchdog: picture frozen, frames stuck at $frames for $frozenChecks polls (pos still advancing)")
                        frozenChecks = 0
                        reconnect("picture frozen"); return
                    }
                } else {
                    frozenChecks = 0
                }
            } else {
                frozenChecks = 0; lastProgressPos = -1L; lastProgressWallMs = 0L
            }
            mainHandler.postDelayed(this, PROGRESS_CHECK_MS)
        }
    }

    /** One diagnostic line per ExoPlayer state transition — never includes the stream URL. */
    private fun logStateChange(playbackState: Int) {
        val name = when (playbackState) {
            Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"; Player.STATE_ENDED -> "ENDED"; else -> "UNKNOWN($playbackState)"
        }
        val p = player
        LiveDiagnosticsLog.event(
            "state_changed state=$name playWhenReady=${p?.playWhenReady} isPlaying=${p?.isPlaying} " +
                "pos=${p?.currentPosition} buffered=${p?.bufferedPosition} hasPlayed=$hasPlayed " +
                "isLiveContent=$isLiveContent buffering=${_buffering.value} reconnect=$retryCount/$MAX_RECONNECTS"
        )
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            logStateChange(playbackState)
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.value = State.LOADING; _buffering.value = true
                    // After it has played, a long buffer == a dropped feed → reconnect (live streams don't
                    // resume on their own here). Before first play, leave initial load alone.
                    if (hasPlayed && !gaveUp) {
                        LiveDiagnosticsLog.event("stallWatchdog armed (${STALL_MS}ms)")
                        mainHandler.removeCallbacks(stallWatchdog); mainHandler.postDelayed(stallWatchdog, STALL_MS)
                    }
                }
                Player.STATE_READY -> {
                    val resumed = hasPlayed // a READY after first play == recovered from a buffer/stall
                    _state.value = State.PLAYING; _buffering.value = false
                    hasPlayed = true; retryCount = 0; mainHandler.removeCallbacks(stallWatchdog)
                    if (resumed) LiveDiagnosticsLog.event("playing — READY, spinner cleared, stallWatchdog cancelled")
                    // (re)start the silent-freeze poll now that we're actually playing. Reset the frame
                    // baseline so the freeze window is measured from this READY (a healthy stream renders its
                    // first frame well within the grace window; one that never does trips the watchdog).
                    frameCounter.set(0); lastFrameCount = 0; everRendered = false; lastProgressPos = -1L; lastProgressWallMs = 0L; frozenChecks = 0
                    readySinceMs = android.os.SystemClock.elapsedRealtime(); noVideoTriggered = false
                    mainHandler.removeCallbacks(progressWatchdog); mainHandler.postDelayed(progressWatchdog, PROGRESS_CHECK_MS)
                    ensureFpsMeasurement()
                }
                Player.STATE_ENDED -> {
                    // A live HLS feed shouldn't legitimately "end" — this is a stall/hiccup (e.g. a stray
                    // EXT-X-ENDLIST from a provider glitch, or a momentarily empty playlist), not a real
                    // terminal state. Only react once we've actually been playing; before that, leave it
                    // alone (mirrors the pre-fix behavior so a channel that never opens still falls through
                    // to onPlayerError / the VM's mpv fallback instead of looping reconnects forever).
                    mainHandler.removeCallbacks(stallWatchdog)
                    when {
                        !hasPlayed -> {
                            LiveDiagnosticsLog.event("STATE_ENDED before first play — no action")
                            _buffering.value = false
                        }
                        reconnectPending || gaveUp -> _buffering.value = true
                        else -> {
                            LiveDiagnosticsLog.event("STATE_ENDED mid-live — treating as stall, reconnecting")
                            _buffering.value = true
                            reconnect("ended mid-live")
                        }
                    }
                }
                Player.STATE_IDLE -> {
                    mainHandler.removeCallbacks(stallWatchdog)
                    when {
                        stoppingIntentionally -> {
                            LiveDiagnosticsLog.event("STATE_IDLE — clean cancellation (stop/release/back)")
                            stoppingIntentionally = false
                            _buffering.value = false
                        }
                        reconnectPending || gaveUp -> _buffering.value = true
                        hasPlayed -> {
                            // Unexpected IDLE while we still intend to be on a live channel — same
                            // dead-end this fix targets, just via STATE_IDLE instead of STATE_ENDED.
                            LiveDiagnosticsLog.event("STATE_IDLE unexpected mid-live — treating as stall, reconnecting")
                            _buffering.value = true
                            reconnect("idle mid-live")
                        }
                        else -> {
                            LiveDiagnosticsLog.event("STATE_IDLE before first play — no action")
                            _buffering.value = false
                        }
                    }
                }
                else -> { _buffering.value = false; mainHandler.removeCallbacks(stallWatchdog) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.height > 0) {
                _videoHeight.value = videoSize.height
                _videoRes.value = "${videoSize.height}p"
                if (videoSize.height > 1080) sawUhd = true // mark UHD → full decoder release on leave
            }
            if (videoSize.width > 0 && videoSize.height > 0) {
                // Aspect for zoom/letterbox sizing (PAR-corrected), + native pixel size for Original (1:1).
                _videoAspect.value =
                    (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                _videoSize.value = videoSize.width to videoSize.height
            }
            updateStreamChips()
            ensureFpsMeasurement()
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            rebuildTracks(tracks); updateStreamChips(); ensureFpsMeasurement()
        }
        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) { _cues.value = cueGroup.cues }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.w(LiveDiagnosticsLog.TAG, "ExoPlayer error: ${error.errorCodeName}", error)
            LiveDiagnosticsLog.event("player_error code=${error.errorCodeName} hasPlayed=$hasPlayed")
            // mid-stream drop → reconnect, unless a reconnect from the SAME failed prepare is already
            // in flight (ExoPlayer often fires this alongside a STATE_IDLE for one physical failure) or
            // we've already exhausted retries and are waiting on the user/a fresh play().
            if (hasPlayed && !reconnectPending && !gaveUp) { reconnect("error ${error.errorCodeName}"); return }
            if (hasPlayed) return
            // Never opened → a stream ExoPlayer can't handle; the VM falls back to mpv on this ERROR.
            _state.value = State.ERROR
            _isPlaying.value = false
            _buffering.value = false
            _error.value = "Couldn't play this channel."
            val raw = lastCodecError ?: diagnostics.recentError()
                ?: error.errorCodeName + ((error.cause?.message ?: error.message)?.let { ": $it" } ?: "")
            _errorInfo.value = ErrorInfo(PlayerErrors.reasonFor(raw), exoSpec(), raw)
        }
    }

    /** Attach the preview SurfaceView's surface, or null when it's destroyed. */
    fun setSurface(s: Surface?) {
        surface = s
        if (s != null) player?.setVideoSurface(s) else player?.clearVideoSurface()
    }

    /** Start (or switch to) [url] as a muted/unmuted preview. Never throws — a stream ExoPlayer can't set
     *  up just falls back to the channel logo (the full mpv player can still play it). [meta] populates the
     *  full-screen HUD title when this preview is promoted. [userAgent] is the per-source custom UA. */
    /** Fully release the ExoPlayer instance (and its MediaCodec) — used when leaving a UHD channel so the
     *  4K hardware decoder is handed back cleanly instead of parked/reused. Keeps [surface] so the next
     *  [play] rebuilds a fresh player and re-attaches. The next [play] lazily rebuilds via `player ?: build()`. */
    fun releaseDecoderForUhd() {
        if (!sawUhd) return // only pay the rebuild when leaving a genuine UHD stream
        sawUhd = false
        if (player == null) return
        LiveDiagnosticsLog.event("UHD channel left — full decoder release+rebuild")
        player?.run { removeListener(listener); release() }
        player = null
        videoRenderer = null
    }

    fun play(url: String, muted: Boolean, meta: MediaMeta = MediaMeta(), userAgent: String? = null) {
        LiveDiagnosticsLog.event("play() url=${HttpClient.redactUrl(url)} muted=$muted")
        stoppingIntentionally = false
        currentUa = userAgent?.takeIf { it.isNotBlank() } ?: HttpClient.DEFAULT_USER_AGENT
        diagnostics.start(); diagnostics.markLoad()
        lastCodecError = null; lastVideoDecoder = null
        this.muted = muted
        currentUrl = url
        hasPlayed = false; retryCount = 0; reconnectPending = false; gaveUp = false
        mainHandler.removeCallbacks(stallWatchdog); mainHandler.removeCallbacks(progressWatchdog); mainHandler.removeCallbacks(fpsFastRefresh)
        audioTrackList = emptyList(); audioSelections = emptyList(); _audioCount.value = 0
        textTrackList = emptyList(); textSelections = emptyList(); _subCount.value = 0
        _subtitleOn.value = false; _cues.value = emptyList(); _audioUnsupported.value = false
        _noVideoDetected.value = false; noVideoTriggered = false; readySinceMs = 0L
        _videoHeight.value = null; _videoAspect.value = null; _videoSize.value = null; _streamChips.value = emptyList()
        _videoRes.value = null
        _error.value = null
        _errorInfo.value = null
        frameCounter.set(0); lastFrameCount = 0; everRendered = false; lastProgressPos = -1L; lastProgressWallMs = 0L; frozenChecks = 0
        throughputTracker.reset(); fpsSample.resetAll(); dropsBaseline = currentDroppedFrames(player)
        _currentMeta.value = meta
        _volume.value = if (muted) 0 else 100
        _state.value = State.LOADING
        _buffering.value = true
        runCatching {
            val p = player ?: build().also { player = it }
            surface?.let { p.setVideoSurface(it) }
            p.volume = if (muted) 0f else 1f
            p.setMediaSource(mediaSourceFor(url))
            p.prepare()
            p.playWhenReady = true
        }.onFailure {
            android.util.Log.w(LiveDiagnosticsLog.TAG, "preview play() failed for ${HttpClient.redactUrl(url)}", it)
            LiveDiagnosticsLog.event("play() failed: ${it.message}")
            _state.value = State.ERROR
            _error.value = "Couldn't play this channel."
            val raw = lastCodecError ?: diagnostics.recentError() ?: it.message
            _errorInfo.value = raw?.let { r -> ErrorInfo(PlayerErrors.reasonFor(r), exoSpec(), r) }
        }
    }

    fun setMuted(m: Boolean) {
        muted = m
        player?.volume = if (m) 0f else 1f
        _volume.value = if (m) 0 else 100
    }

    // Snapshot of the live channel taken when the app backgrounds (screensaver / Home), so it can be restored
    // on return — otherwise onStop frees the stream and a paused live channel never resumes (even on Play).
    private data class LiveRestore(val url: String, val muted: Boolean, val meta: MediaMeta)
    @Volatile private var backgroundRestore: LiveRestore? = null

    /** Backgrounded (screensaver / Home): remember what's playing, then free the stream. Paired with
     *  [onAppForegrounded]. */
    fun onAppBackgrounded() {
        currentUrl?.let { backgroundRestore = LiveRestore(it, muted, _currentMeta.value) }
        stop()
    }

    /** Foregrounded: re-tune the live channel that was freed while backgrounded (at the live edge), so it
     *  resumes instead of sitting on a dead/empty stream. No-op if something is already playing. */
    fun onAppForegrounded() {
        val r = backgroundRestore ?: return
        backgroundRestore = null
        if (currentUrl != null) return
        play(r.url, muted = r.muted, meta = r.meta)
    }

    /** Drop any pending restore (e.g. on profile switch — don't bring back the previous user's channel). */
    fun discardBackgroundRestore() { backgroundRestore = null }

    /** Stop playback and free the decoder/connection (e.g. before mpv takes over for fullscreen). Keeps the
     *  ExoPlayer instance alive for the next preview. */
    fun stop() {
        LiveDiagnosticsLog.event("stop() — intentional")
        stoppingIntentionally = true
        currentUrl = null
        hasPlayed = false; retryCount = 0; reconnectPending = false; gaveUp = false
        mainHandler.removeCallbacks(stallWatchdog); mainHandler.removeCallbacks(progressWatchdog); mainHandler.removeCallbacks(fpsFastRefresh)
        frameCounter.set(0); lastFrameCount = 0; everRendered = false; lastProgressPos = -1L; frozenChecks = 0
        audioTrackList = emptyList(); audioSelections = emptyList(); _audioCount.value = 0
        textTrackList = emptyList(); textSelections = emptyList(); _subCount.value = 0
        _subtitleOn.value = false; _cues.value = emptyList(); _audioUnsupported.value = false
        _noVideoDetected.value = false; noVideoTriggered = false; readySinceMs = 0L
        _videoHeight.value = null; _videoAspect.value = null; _videoSize.value = null; _streamChips.value = emptyList()
        _state.value = State.IDLE
        player?.run { stop(); clearMediaItems() }
        // Leaving a UHD channel (back / exit fullscreen / background): fully release the 4K decoder.
        releaseDecoderForUhd()
    }

    fun release() {
        LiveDiagnosticsLog.event("release() — intentional")
        stoppingIntentionally = true
        mainHandler.removeCallbacks(stallWatchdog)
        mainHandler.removeCallbacks(progressWatchdog)
        player?.run { removeListener(listener); release() }
        player = null
        videoRenderer = null
        surface = null
        currentUrl = null
        sawUhd = false
        _state.value = State.IDLE
    }

    /** Live auto-reconnect: re-fetch [currentUrl] from the live edge after a mid-stream error/stall. Backs
     *  off and gives up after [MAX_RECONNECTS] consecutive failures (then the HUD's Retry button takes over).
     *  retryCount is reset to 0 as soon as playback goes healthy again (STATE_READY).
     *
     *  For an expiring-URL source (Stalker, plan §5.4.1) the reconnect must NOT replay the now-dead
     *  resolved URL — a [reconnectUrlProvider] mints a fresh one first (null/absent → replay as-is,
     *  which is correct for M3U/Xtream and direct-URL Stalker portals). */
    private fun reconnect(reason: String) {
        mainHandler.removeCallbacks(stallWatchdog); mainHandler.removeCallbacks(progressWatchdog); mainHandler.removeCallbacks(fpsFastRefresh)
        val p = player
        val url = currentUrl
        if (p == null || url == null || retryCount >= MAX_RECONNECTS) {
            LiveDiagnosticsLog.event("reconnect exhausted ($reason) at $retryCount/$MAX_RECONNECTS — giving up")
            gaveUp = true
            _state.value = State.ERROR; _isPlaying.value = false; _buffering.value = false
            _error.value = "Lost connection to this channel."
            val raw = lastCodecError ?: diagnostics.recentError() ?: reason
            _errorInfo.value = ErrorInfo(PlayerErrors.reasonFor(raw), exoSpec(), raw)
            return
        }
        retryCount++
        reconnectPending = true
        _error.value = null; _errorInfo.value = null; _state.value = State.LOADING; _buffering.value = true
        LiveDiagnosticsLog.event("reconnect attempt $retryCount/$MAX_RECONNECTS reason=$reason")
        val delayMs = (1500L * retryCount).coerceAtMost(4000L)
        // Resolve a fresh URL off-main (Stalker create_link is a network call) before the delayed reload.
        val provider = reconnectUrlProvider
        scope.launch {
            val fresh = if (provider != null) {
                withContext(Dispatchers.IO) {
                    runCatching { provider.freshUrl() }
                        .onFailure { LiveDiagnosticsLog.event("reconnect fresh-url failed: ${it.message}") }
                        .getOrNull()
                }
            } else null
            // Coalesce the backoff delay with the resolve: whichever is later wins, but the resolve must
            // complete before we reload. Post the reload so it lands on the main thread's Looper after delay.
            mainHandler.postDelayed({
                if (currentUrl != url) { reconnectPending = false; return@postDelayed } // superseded (zapped / stopped)
                reconnectPending = false
                val loadUrl = fresh ?: url // null provider/result → replay the (still-valid) stored URL
                if (fresh != null && fresh != url) {
                    currentUrl = fresh // adopt the refreshed URL so a later reconnect compares against it
                    LiveDiagnosticsLog.event("reconnect re-resolved expiring URL (${HttpClient.redactUrl(fresh)})")
                }
                runCatching {
                    p.setMediaItem(MediaItem.fromUri(loadUrl)) // fresh fetch (live edge)
                    p.prepare()
                    p.playWhenReady = true
                }.onFailure { _state.value = State.ERROR; _error.value = "Lost connection to this channel." }
            }, delayMs)
        }
    }

    // --- Audio Mode (Audio Mode plan §5): keep audio playing, release the video surface ---
    private val _audioOnly = MutableStateFlow(false)
    override val audioOnly: StateFlow<Boolean> = _audioOnly.asStateFlow()
    override fun enterAudioOnly() {
        if (_audioOnly.value) return
        _audioOnly.value = true
        player?.clearVideoSurface() // audio keeps playing without a surface; [surface] kept for return
    }
    override fun exitAudioOnly() {
        if (!_audioOnly.value) return
        _audioOnly.value = false
        surface?.let { player?.setVideoSurface(it) }
    }

    // --- PlaybackEngine controls (full-screen HUD) ---
    override fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    override fun setZoomMode(mode: ZoomMode) { _zoomMode.value = mode } // ExoPreviewSurface observes this + videoAspect/Size and sizes the surface (see Modifier.videoZoom)

    override fun adjustVolume(delta: Int) {
        // Live engine (ExoPlayer) caps at 100% — boost above 100 is mpv-only (Exo can't amplify past unity
        // and a gain audio-processor broke the audio sink). VOD/series/compat-live play on mpv and do boost.
        val v = (_volume.value + delta).coerceIn(0, 100)
        _volume.value = v
        muted = v == 0
        player?.volume = v / 100f
    }

    override fun toggleMute() = setMuted(!muted)
    override fun retry() {
        val url = currentUrl ?: return
        val provider = reconnectUrlProvider
        if (provider == null) { play(url, muted, _currentMeta.value); return }
        // Expiring-URL source (Stalker): re-resolve before retrying, then reload on the main thread.
        scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                runCatching { provider.freshUrl() }
                    .onFailure { LiveDiagnosticsLog.event("retry fresh-url failed: ${it.message}") }
                    .getOrNull()
            }
            play(fresh ?: url, muted, _currentMeta.value)
        }
    }
    override fun selectAudio(id: Int) {
        val p = player ?: return
        val sel = audioSelections.firstOrNull { it.id == id } ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(sel.group, listOf(sel.trackIndex)))
            .build()
        audioTrackList = audioTrackList.map { it.copy(selected = it.mpvId == id) }
    }

    override fun selectSubtitle(id: Int) {
        val p = player ?: return
        val sel = textSelections.firstOrNull { it.id == id } ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(sel.group, listOf(sel.trackIndex)))
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            .build()
        _subtitleOn.value = true // mount the SubtitleView overlay
        textTrackList = textTrackList.map { it.copy(selected = it.mpvId == id) }
    }

    override fun disableSubtitles() {
        player?.let {
            it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true).build()
        }
        _subtitleOn.value = false
        _cues.value = emptyList()
        textTrackList = textTrackList.map { it.copy(selected = false) }
    }

    override fun audioTracks(): List<TrackOption> = audioTrackList
    override fun textTracks(): List<TrackOption> = textTrackList

    /** Build the audio + subtitle track lists from the active stream so the HUD menus can switch language /
     *  subtitles (multi-track live channels, or a VOD file imported via M3U). Mirrors [ExoSubtitleEngine]. */
    private fun rebuildTracks(tracks: androidx.media3.common.Tracks) {
        val audio = ArrayList<TrackOption>(); val aSel = ArrayList<AudioSel>(); var aId = 0
        val text = ArrayList<TrackOption>(); val tSel = ArrayList<TextSel>(); var tId = 0
        for (group in tracks.groups) {
            when (group.type) {
                androidx.media3.common.C.TRACK_TYPE_AUDIO -> for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }
                    audio.add(TrackOption(f.label ?: lang?.uppercase() ?: "Audio ${aId + 1}", aId, group.isTrackSelected(i), lang = lang))
                    aSel.add(AudioSel(aId, group.mediaTrackGroup, i)); aId++
                }
                androidx.media3.common.C.TRACK_TYPE_TEXT -> for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }
                    text.add(TrackOption(f.label ?: lang?.uppercase() ?: "Subtitle ${tId + 1}", tId, _subtitleOn.value && group.isTrackSelected(i), lang = lang))
                    tSel.add(TextSel(tId, group.mediaTrackGroup, i)); tId++
                }
            }
        }
        audioTrackList = audio; audioSelections = aSel; _audioCount.value = audio.size
        textTrackList = text; textSelections = tSel; _subCount.value = text.size
        if (tv.own.owntv.BuildConfig.DEBUG) {
            LiveDiagnosticsLog.event(
                "tracks: audio=${audio.size} text=${text.size}" +
                    text.joinToString(prefix = " [", postfix = "]") { it.label },
            )
        }
        // Audio exists but ExoPlayer can decode none of it → the VM will route this stream to mpv.
        val anySupportedAudio = tracks.groups.any { g ->
            g.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && (0 until g.length).any { g.isTrackSupported(it) }
        }
        _audioUnsupported.value = audio.isNotEmpty() && !anySupportedAudio
    }

    // Effective User-Agent for the current stream; updated per play() call.
    // null = no source UA configured, use DEFAULT_USER_AGENT.
    private var currentUa: String = HttpClient.DEFAULT_USER_AGENT
    private var dataSourceForUa: String = ""
    private var cachedHttpDataSource: OkHttpDataSource.Factory? = null
    private var cachedDefaultFactory: DefaultMediaSourceFactory? = null
    private var cachedHlsCcFactory: HlsMediaSource.Factory? = null
    private fun httpDataSourceFor(ua: String): OkHttpDataSource.Factory {
        if (ua != dataSourceForUa || cachedHttpDataSource == null) {
            cachedHttpDataSource = OkHttpDataSource.Factory(okHttpClient).setUserAgent(ua)
                .setTransferListener(throughputTracker)
            // Raw MPEG-TS (typical Xtream live ".ts"): providers rarely declare caption descriptors in
            // the PMT, so the stock TS extractor never exposes the embedded CEA-608 track (#57).
            // FLAG_OVERRIDE_CAPTION_DESCRIPTORS makes it expose the standard CC1 track regardless; the
            // flag only affects TS — every other format sniffs exactly as before. Passed into the same
            // DefaultMediaSourceFactory that has always handled non-HLS live, so routing is unchanged.
            // The flag alone is NOT enough: DefaultExtractorsFactory passes an empty subtitle-format
            // list to DefaultTsPayloadReaderFactory, and with the override flag that empty list is
            // returned verbatim (= zero CC tracks, even declared ones). The CEA-608 CC1 format must be
            // supplied explicitly via setTsSubtitleFormats.
            val cc1 = androidx.media3.common.Format.Builder()
                .setSampleMimeType(androidx.media3.common.MimeTypes.APPLICATION_CEA608)
                .setAccessibilityChannel(1) // CC1 — the standard primary caption channel
                .build()
            cachedDefaultFactory = DefaultMediaSourceFactory(
                cachedHttpDataSource!!,
                androidx.media3.extractor.DefaultExtractorsFactory()
                    .setTsExtractorFlags(
                        androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS,
                    )
                    .setTsSubtitleFormats(listOf(cc1)),
            )
            cachedHlsCcFactory = HlsMediaSource.Factory(cachedHttpDataSource!!).setExtractorFactory(DefaultHlsExtractorFactory(0, true))
            dataSourceForUa = ua
        }
        return cachedHttpDataSource!!
    }

    /** HLS → caption-aware factory; everything else (raw MPEG-TS, etc.) → default. */
    private fun mediaSourceFor(url: String): MediaSource {
        httpDataSourceFor(currentUa) // ensure factories match current UA
        // Live latency (#72): a target live-edge offset for live streams (HLS/DASH). Ignored by
        // progressive/raw-TS sources, so it can only help where it applies.
        val item = MediaItem.Builder().setUri(url).apply {
            liveBufferSecs?.let {
                setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(it * 1000L).build())
            }
        }.build()
        val uri = item.localConfiguration?.uri ?: return cachedDefaultFactory!!.createMediaSource(item)
        return if (Util.inferContentType(uri) == C.CONTENT_TYPE_HLS) cachedHlsCcFactory!!.createMediaSource(item)
        else cachedDefaultFactory!!.createMediaSource(item)
    }

    private fun build(): ExoPlayer {
        // Shallow buffers — a preview only needs to start quickly, not buffer deep.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 8_000, 1_000, 2_000)
            .build()
        // forceDisableMediaCodecAsynchronousQueueing(): Media3 runs MediaCodec asynchronously by default on
        // API 31+, which corrupts (macroblocks) some UHD-HEVC streams on Realtek/Amlogic VPUs — the
        // synchronous path is what players like TiviMate use to avoid it. Channels it still can't decode
        // cleanly are handed to mpv via the per-channel "force mpv" routing.
        val renderers = DefaultRenderersFactory(context).forceDisableMediaCodecAsynchronousQueueing()
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFor(currentUa)))
            .setLoadControl(loadControl)
            .build()
            .apply {
                addListener(listener); addAnalyticsListener(analytics)
                // Wire the per-frame tick to the video renderer so the health watchdog can tell a frozen
                // PICTURE (clock still running — invisible to a position-only check) from real playback.
                // Best-effort: if the renderer isn't found / doesn't accept the message, the position and
                // error/stall watchdogs still cover total freezes, so this can't make things worse.
                videoRenderer = (0 until rendererCount).map { getRenderer(it) }
                    .firstOrNull { it.trackType == C.TRACK_TYPE_VIDEO }
                if (videoRenderer == null) {
                    android.util.Log.w(LiveDiagnosticsLog.TAG, "frame hook NOT wired — no video renderer found; picture-freeze detection falls back to the no-progress backstop")
                }
                videoRenderer?.let { r ->
                    runCatching {
                        createMessage(r)
                            .setType(MediaCodecVideoRenderer.MSG_SET_VIDEO_FRAME_METADATA_LISTENER)
                            .setPayload(frameListener)
                            .send()
                    }.onFailure {
                        android.util.Log.w(LiveDiagnosticsLog.TAG, "frame hook send FAILED (${it.message}) — picture-freeze detection falls back to the no-progress backstop")
                    }
                }
            }
    }

    companion object {
        private const val MAX_RECONNECTS = 8        // ~consecutive failures before giving up (HUD Retry then)
        private const val STALL_MS = 12_000L        // buffering this long after playing == a dropped feed
        private const val PROGRESS_CHECK_MS = 2_500L // poll interval for the silent-freeze watchdog
        private const val FROZEN_LIMIT = 3          // picture frozen this many polls (~7.5s) == a dropped feed
        private const val FREEZE_TIMEOUT_MS = 8_000L // zero forward progress this long while READY == dead feed
        private const val NO_VIDEO_TIMEOUT_MS = 8_000L // video track present, zero frames rendered this long == "audio plays, no picture"
        private const val FPS_BASELINE_MS = 500L
        private const val FPS_TICK_MS = 1_000L
        private const val FPS_MAX_ATTEMPTS = 5
    }
}
