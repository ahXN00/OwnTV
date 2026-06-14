package tv.own.owntv.player

import android.content.Context
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.features.settings.data.SettingsRepository
import java.util.Locale

/** A selectable audio/subtitle track ([mpvId] is the mpv track id used for `aid`/`sid`). */
data class TrackOption(val label: String, val mpvId: Int, val selected: Boolean)

/** Metadata shown in the player HUD (breadcrumb path, year, channel logo). */
data class MediaMeta(
    val title: String? = null,
    val subtitle: String? = null,
    val year: String? = null,
    val logoUrl: String? = null,
)

/** An item in a play queue (e.g. a season's episodes), for prev/next. */
data class PlaylistItem(val url: String, val meta: MediaMeta = MediaMeta())

/** Whether prev/next are available in the current queue. */
data class NavState(val hasPrev: Boolean, val hasNext: Boolean)

/** Video scaling modes exposed in the player's zoom menu. */
enum class ZoomMode(val label: String) {
    FIT("Fit Screen"), FILL("Fill / Crop"), STRETCH("Stretch"),
    ORIGINAL("Original (1:1)"), FORCE_16_9("Force 16:9"), FORCE_4_3("Force 4:3"),
}

/**
 * App-wide single libmpv player. mpv (FFmpeg) decodes virtually any codec/container and exposes every
 * audio/subtitle track — the right engine for IPTV (ExoPlayer only surfaced device-decodable tracks).
 * Also gives caching, playback speed, etc. State is published as StateFlows for the Compose HUD.
 */
class OwnTVPlayer(
    private val context: Context,
    private val settings: SettingsRepository,
) : MPVLib.EventObserver {

    private companion object {
        const val TAG = "OwnTVPlayer"
        const val MAX_AUTO_RETRIES = 3 // silent retries (backoff) before showing the error UI
    }

    // The app renders with mpv's direct decoder-to-surface output (vo=mediacodec_embed) — the same
    // zero-copy pipeline YouTube/Netflix use, and the right one for TV hardware. mpv's GL renderer is
    // kept ONLY as the automatic software-decode rescue (hwdec=no): the direct surface can't display
    // software-decoded frames, so those go through vo=gpu. The Android emulator's *translated* GL is
    // broken and hard-crashes the process, so on emulators we never attempt the GL rescue — we show a
    // clean "can't decode" error instead. Real TVs (incl. Fire TV) run the GL rescue fine.
    private val glUnsupported: Boolean by lazy { isProbablyEmulator() }
    private fun isProbablyEmulator(): Boolean {
        val fp = android.os.Build.FINGERPRINT
        val model = android.os.Build.MODEL
        return fp.startsWith("generic") || fp.startsWith("unknown") ||
            fp.contains("emulator", true) || fp.contains("/sdk_") ||
            model.contains("google_sdk") || model.contains("Emulator") ||
            model.contains("Android SDK built for") ||
            android.os.Build.MANUFACTURER.contains("Genymotion") ||
            android.os.Build.HARDWARE.contains("goldfish") || android.os.Build.HARDWARE.contains("ranchu") ||
            android.os.Build.PRODUCT.contains("sdk") || android.os.Build.PRODUCT.contains("emulator") ||
            android.os.Build.PRODUCT.contains("simulator")
    }

    private var mpv: MPVLib? = null
    private var initialized = false
    private var pendingSeekMs = 0L
    private var currentUrl: String? = null
    private var expectingPlayback = false
    private var playlist: List<PlaylistItem> = emptyList()
    private var playlistIndex = 0
    // mpv's android video output needs a surface at loadfile time, or it deselects video (audio-only).
    // So when no surface is attached yet we defer the load until attachSurface().
    private var surfaceAttached = false
    private var pendingUrl: String? = null
    private var hdrHint = true
    private var playerBudget: PlayerBudget? = null

    // Decode watchdog state: if a >1080p video ends up on the software decoder, playback is aborted
    // with a friendly error — CPU-decoding 4K/8K on a TV chip stutters, overheats, and OOM-kills the
    // app (observed on a TCL G10). Both values arrive on mpv's event thread per loaded file.
    @Volatile private var currentHwdec: String? = null
    @Volatile private var currentHeightPx = 0
    @Volatile private var currentWidthPx = 0
    @Volatile private var decodeGuardTripped = false

    // --- Render path -------------------------------------------------------------------------
    // Always direct (vo=mediacodec_embed + hwdec=mediacodec): zero CPU copies, no GL shader work, the
    // panel's own silicon renders HDR — the same pipeline YouTube/Netflix use, and the only one weak TV
    // SoCs play 4K smoothly on. The GL renderer (vo=gpu) is used ONLY when hardware decoding is off (the
    // user setting) or the per-item software rescue kicks in — the direct surface can't show SW frames.
    // Silent auto-retry budget for a load that fails to start (transient: cold-boot decoder-busy,
    // a provider 5xx, the surface-timing race). Reset per genuinely-new item; counts up across
    // retries with backoff, then the error UI + manual Retry takes over.
    @Volatile private var autoRetries = 0
    // A stream the hardware decoder can't start (weak TV decoders — e.g. a Fire TV Stick 3rd gen reject
    // some otherwise-fine channels/VOD with "unsupported format") is retried ONCE in pure software,
    // per item, before the error shows — so the user no longer has to flip the global hardware-decoding
    // setting off. Per-item only; never changes the user's setting. Reset on each genuinely-new item.
    @Volatile private var forceSoftwareThisLoad = false
    private val _directRender = MutableStateFlow(false)
    /** True while the direct (decoder-to-surface) output is in use — HUD hides zoom, app draws subs. */
    val directRender: StateFlow<Boolean> = _directRender.asStateFlow()

    /** Hardware decoding effectively in use right now — the global setting, minus a per-item override
     *  forced on after the hardware decoder failed to start a stream. */
    private fun hwDecodingActive(): Boolean = hwDecoding && !forceSoftwareThisLoad

    /** Direct decoder-to-surface output. The only non-direct case is software decoding (hwdec off or
     *  the per-item rescue), which the direct surface can't display, so it uses the GL renderer. */
    private fun useDirect(): Boolean = hwDecodingActive()

    /** Apply vo/hwdec for the current render path (also safe live — mpv reinits decoder/output).
     *  Direct → hardware decode to surface; otherwise software decode through the GL renderer. */
    private fun MPVLib.applyRenderConfig() {
        val direct = useDirect()
        setPropertyString("hwdec", if (direct) "mediacodec" else "no")
        if (surfaceAttached) setPropertyString("vo", if (direct) "mediacodec_embed" else "gpu")
        _directRender.value = direct
    }

    // Video Player Settings — cached so ensureInit can apply them as mpv options, and the observers
    // below apply changes live to a running player.
    private var hwDecoding = true
    private var subScale = 1.0
    private var audioDelaySec = 0.0
    private var prefAudioLang = ""
    private var prefSubLang = ""
    private var defaultZoom = ZoomMode.FIT

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * All mpv commands/property writes run on this single worker thread, never on the UI thread.
     * libmpv calls are synchronous and can block for seconds while the core is stuck in a stalling
     * network read (flaky live streams) — issuing them from the main thread caused ANRs ("Input
     * dispatching timed out"). A single thread keeps the original call order.
     */
    private val mpvExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "mpv-cmd").apply { isDaemon = true }
    }

    private fun mpvAsync(block: MPVLib.() -> Unit) {
        val m = mpv ?: return
        mpvExecutor.execute { runCatching { m.block() } }
    }

    init {
        // Track the HDR setting; apply it live and re-apply on each load via ensureInit.
        settings.hdrEnabled.onEach { enabled ->
            hdrHint = enabled
            if (initialized) mpvAsync { setPropertyString("target-colorspace-hint", if (enabled) "yes" else "no") }
        }.launchIn(scope)
        settings.hwDecoding.onEach { on ->
            hwDecoding = on
            if (initialized) mpvAsync { applyRenderConfig() }
        }.launchIn(scope)
        settings.subtitleScale.onEach { s ->
            subScale = s.toDouble()
            if (initialized) mpvAsync { setPropertyDouble("sub-scale", subScale) }
        }.launchIn(scope)
        settings.audioDelayMs.onEach { ms ->
            audioDelaySec = ms / 1000.0
            if (initialized) mpvAsync { setPropertyDouble("audio-delay", audioDelaySec) }
        }.launchIn(scope)
        settings.preferredAudioLang.onEach { lang ->
            prefAudioLang = lang
            if (initialized && lang.isNotBlank()) mpvAsync { setPropertyString("alang", lang) }
        }.launchIn(scope)
        settings.preferredSubLang.onEach { lang ->
            prefSubLang = lang
            if (initialized && lang.isNotBlank()) mpvAsync { setPropertyString("slang", lang) }
        }.launchIn(scope)
        settings.defaultZoom.onEach { name ->
            defaultZoom = runCatching { ZoomMode.valueOf(name) }.getOrDefault(ZoomMode.FIT)
        }.launchIn(scope)
        // Subtitle overlay is fed by OBSERVING "sub-text" (see eventProperty) — not polling. The old
        // 250 ms getPropertyString poll logged a "property unavailable" error 4×/sec whenever no line
        // was on screen, flooding logcat and burning a cross-thread call the whole time.
    }
    // Bumped on every load/stop so stale work can tell it's been superseded: the end-of-file error
    // check, and queued loadfile commands (fast preview scrolling queues a burst — only the newest
    // may run, or a slow provider makes the worker grind through dead loads). Volatile: written on
    // the main thread, read on the mpv-cmd worker.
    @Volatile private var loadGeneration = 0
    private var errorCheckJob: Job? = null

    private val _nav = MutableStateFlow(NavState(false, false))
    val nav: StateFlow<NavState> = _nav.asStateFlow()

    var currentTitle: String? = null
        private set
    var currentSubtitle: String? = null
        private set
    var currentYear: String? = null
        private set
    var currentLogoUrl: String? = null
        private set
    var isLiveContent: Boolean = false
        private set

    private var preMuteVolume = 100

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()
    private val _videoRes = MutableStateFlow<String?>(null)
    val videoRes: StateFlow<String?> = _videoRes.asStateFlow()

    /** Video aspect ratio (w/h) — the surface view sizes itself with this in direct mode. */
    private val _videoAspect = MutableStateFlow<Float?>(null)
    val videoAspect: StateFlow<Float?> = _videoAspect.asStateFlow()

    /** Native video pixel size (w, h) — the surface view uses it for the Original (1:1) zoom mode. */
    private val _videoSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoSize: StateFlow<Pair<Int, Int>?> = _videoSize.asStateFlow()

    /** Current subtitle line(s) for the Compose overlay (direct mode only; null = nothing showing). */
    private val _subText = MutableStateFlow<String?>(null)
    val subText: StateFlow<String?> = _subText.asStateFlow()
    private val _audioCount = MutableStateFlow(0)
    val audioCount: StateFlow<Int> = _audioCount.asStateFlow()
    private val _subCount = MutableStateFlow(0)
    val subCount: StateFlow<Int> = _subCount.asStateFlow()
    private val _zoomMode = MutableStateFlow(ZoomMode.FIT)
    val zoomMode: StateFlow<ZoomMode> = _zoomMode.asStateFlow()
    private val _speed = MutableStateFlow(1.0)
    val speed: StateFlow<Double> = _speed.asStateFlow()

    val currentMediaUrl: String? get() = currentUrl

    private fun ensureInit() {
        if (initialized) return
        val budget = PlayerBudget.of(context)
        playerBudget = budget
        android.util.Log.i(TAG, "PlayerBudget: $budget")
        mpv = MPVLib.create(context)?.apply {
            setOptionString("vo", if (useDirect()) "mediacodec_embed" else "gpu")
            setOptionString("gpu-context", "android")
            setOptionString("hwdec", if (useDirect()) "mediacodec" else "no")
            setOptionString("ao", "audiotrack")
            setOptionString("force-window", "no")
            setOptionString("idle", "yes")
            setOptionString("ytdl", "no") // IPTV URLs are direct; skip the youtube-dl hook
            // Quiet logcat in release; debug builds keep decoder/video-out logs for diagnosing
            // hwdec behavior on real TVs (which decoder engaged, why fallbacks happened).
            setOptionString("msg-level", if (tv.own.owntv.BuildConfig.DEBUG) "all=warn,vd=v,vo=v" else "all=warn")
            // Demuxer cache sized to the device (a fixed 256MiB OOM-killed real TVs — see PlayerBudget).
            setOptionString("cache", "yes")
            setOptionString("demuxer-max-bytes", budget.demuxerMaxBytes)
            setOptionString("demuxer-max-back-bytes", budget.demuxerBackBytes)
            setOptionString("demuxer-readahead-secs", budget.readaheadSecs)
            setOptionString("cache-secs", budget.cacheSecs)
            if (budget.lowSpec) {
                // GL diet for TV-class GPUs (e.g. PowerVR BXE on budget 4K panels): mpv's default
                // render path tone-maps 4K HDR in rgba16f with quality scalers — that alone drops
                // a TCL G10 to half-speed video. "fast" = bilinear scalers, no dither/deband.
                setOptionString("profile", "fast")
                setOptionString("fbo-format", "rgba8") // 4K rgba16f intermediates are ~64MB each
                setOptionString("tone-mapping", "clip") // cheapest HDR→SDR
            }
            setOptionString("network-timeout", "60")
            // Strict IPTV panels briefly answer 5xx (e.g. 509 connection-limit right after a channel
            // switch, while the old session still counts). Let FFmpeg retry those itself instead of
            // EOF-ing the stream — the demuxer cache rides over the gap with no visible interruption.
            setOptionString("stream-lavf-o", "reconnect=1,reconnect_streamed=1,reconnect_delay_max=8,reconnect_on_http_error=5xx")
            setOptionString("user-agent", HttpClient.DEFAULT_USER_AGENT)
            setOptionString("sub-scale-with-window", "yes")
            setOptionString("sub-scale", subScale.toString())
            setOptionString("audio-delay", audioDelaySec.toString())
            if (prefAudioLang.isNotBlank()) setOptionString("alang", prefAudioLang)
            if (prefSubLang.isNotBlank()) setOptionString("slang", prefSubLang)
            // HDR passthrough: signal the source colorspace (incl. HDR10/HLG) to the display surface.
            setOptionString("target-colorspace-hint", if (hdrHint) "yes" else "no")
            init()
            // Read back what mpv actually accepted — setOptionString failures are silent, and this
            // line also identifies the running build in logcat captures.
            _directRender.value = useDirect()
            android.util.Log.i(
                TAG,
                "mpv ready: lowSpec=${budget.lowSpec} direct=${useDirect()} hwdec=${getPropertyString("hwdec")} " +
                    "fbo=${getPropertyString("fbo-format")} cache=${getPropertyString("demuxer-max-bytes")}",
            )
            observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            observeProperty("width", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            observeProperty("height", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            observeProperty("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            // Decode watchdog input: which decoder is actually active ("mediacodec[-copy]" or "no").
            observeProperty("hwdec-current", MPVLib.MpvFormat.MPV_FORMAT_STRING)
            // Current subtitle line for the app-drawn overlay (direct mode); fires only on change.
            observeProperty("sub-text", MPVLib.MpvFormat.MPV_FORMAT_STRING)
            addObserver(this@OwnTVPlayer)
        }
        initialized = mpv != null
    }

    /** Play a single item (movie / live channel) — clears any queue. [muted] is used by the live preview. */
    fun play(
        url: String,
        title: String? = null,
        subtitle: String? = null,
        year: String? = null,
        logoUrl: String? = null,
        isLive: Boolean = false,
        startPositionMs: Long = 0,
        muted: Boolean = false,
    ) {
        playlist = emptyList()
        playlistIndex = 0
        updateNav()
        _zoomMode.value = defaultZoom // start new content at the user's default zoom
        loadUrl(url, MediaMeta(title, subtitle, year, logoUrl), isLive, startPositionMs, muted)
    }

    /** Play a queue (a season's episodes) starting at [startIndex] — enables prev/next. */
    fun playEpisodes(items: List<PlaylistItem>, startIndex: Int, startPositionMs: Long = 0) {
        playlist = items
        playlistIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val item = items.getOrNull(playlistIndex) ?: return
        _zoomMode.value = defaultZoom
        loadUrl(item.url, item.meta, isLive = false, startPositionMs)
        updateNav()
    }

    fun next() {
        if (playlistIndex < playlist.size - 1) {
            playlistIndex++
            playCurrent()
        }
    }

    fun previous() {
        if (playlistIndex > 0) {
            playlistIndex--
            playCurrent()
        }
    }

    private fun playCurrent() {
        val item = playlist.getOrNull(playlistIndex) ?: return
        loadUrl(item.url, item.meta, isLive = false, 0)
        updateNav()
    }

    private fun updateNav() {
        _nav.value = NavState(playlistIndex > 0, playlistIndex < playlist.size - 1)
    }

    private fun loadUrl(
        url: String,
        meta: MediaMeta,
        isLive: Boolean,
        startPositionMs: Long,
        muted: Boolean = false,
        resetRetries: Boolean = true,
    ) {
        ensureInit()
        currentTitle = meta.title
        currentSubtitle = meta.subtitle
        currentYear = meta.year
        currentLogoUrl = meta.logoUrl
        isLiveContent = isLive
        currentUrl = url
        loadGeneration++
        errorCheckJob?.cancel()
        _error.value = null
        _videoRes.value = null
        expectingPlayback = true
        pendingSeekMs = startPositionMs
        // A genuinely new item resets the failure budget; an auto-retry / software-fallback reload of
        // the SAME item passes resetRetries=false to keep that state.
        if (resetRetries) {
            autoRetries = 0
            // A new item re-arms hardware decoding (the software override is per-item) — restore the
            // direct render path so one bad stream doesn't keep the rest in software.
            if (forceSoftwareThisLoad) {
                forceSoftwareThisLoad = false
                mpvAsync { applyRenderConfig() }
            }
        }
        // Reset the decode watchdog + per-file video state.
        currentHwdec = null
        currentHeightPx = 0
        currentWidthPx = 0
        decodeGuardTripped = false
        _videoAspect.value = null
        _videoSize.value = null
        _subText.value = null
        // Mute is a global mpv property, so set it now (applies whenever the file actually loads). The
        // live preview mutes; everything else plays with sound.
        mpvAsync { setPropertyBoolean("mute", muted) }
        // Defer the actual loadfile until a surface exists, otherwise mpv inits video output with no
        // surface and falls back to audio-only. attachSurface() flushes the pending load.
        if (surfaceAttached) startLoad(url) else pendingUrl = url
    }

    private fun startLoad(url: String) {
        pendingUrl = null
        val gen = loadGeneration
        mpvAsync {
            // Superseded by a newer load or a stop while waiting in the queue? Skip the dead load —
            // this keeps fast preview-scrolling from grinding through every channel it passed.
            if (gen != loadGeneration) return@mpvAsync
            command(arrayOf("loadfile", url))
            setPropertyBoolean("pause", false)
        }
        // mpv only fires the "pause" observer on a *change*; at startup pause is already false, so seed
        // the playing state here, otherwise the HUD shows PLAY while the stream is actually running.
        _isPlaying.value = true
    }

    /** Mute/unmute without reloading — lets preview → fullscreen reuse the same stream connection. */
    fun setMuted(muted: Boolean) {
        if (initialized) mpvAsync { setPropertyBoolean("mute", muted) }
    }

    fun togglePlayPause() {
        if (initialized) mpvAsync { command(arrayOf("cycle", "pause")) }
    }

    fun seekBy(deltaMs: Long) {
        if (initialized) mpvAsync { command(arrayOf("seek", (deltaMs / 1000).toString(), "relative")) }
    }

    fun setSpeed(speed: Double) {
        if (initialized) mpvAsync { setPropertyDouble("speed", speed) }
        _speed.value = speed
    }

    // --- Volume (mpv software volume, independent of the system/hardware volume) ---
    fun setVolume(percent: Int) {
        val v = percent.coerceIn(0, 150)
        if (initialized) mpvAsync { setPropertyDouble("volume", v.toDouble()) }
        _volume.value = v
        if (v > 0) preMuteVolume = v
    }

    fun adjustVolume(delta: Int) = setVolume(_volume.value + delta)

    fun toggleMute() {
        if (_volume.value > 0) { preMuteVolume = _volume.value; setVolume(0) } else setVolume(preMuteVolume.coerceAtLeast(10))
    }

    // --- Zoom / aspect ---
    fun setZoomMode(mode: ZoomMode) {
        _zoomMode.value = mode
        // Direct mode: the decoder owns the surface, so mpv's GL scaling properties don't apply — the
        // surface VIEW resizes/crops itself per mode instead (see MpvVideoSurface, which observes
        // zoomMode). Nothing to do on mpv here. GL mode (software rescue) still uses the props below.
        if (_directRender.value) return
        mpvAsync {
            // Reset, then apply the chosen mode's overrides.
            setPropertyString("video-unscaled", "no")
            setPropertyDouble("panscan", 0.0)
            when (mode) {
                ZoomMode.FIT -> { setPropertyString("keepaspect", "yes"); setPropertyString("video-aspect-override", "no") }
                ZoomMode.FILL -> { setPropertyString("keepaspect", "yes"); setPropertyString("video-aspect-override", "no"); setPropertyDouble("panscan", 1.0) }
                ZoomMode.STRETCH -> { setPropertyString("keepaspect", "no"); setPropertyString("video-aspect-override", "no") }
                ZoomMode.ORIGINAL -> { setPropertyString("keepaspect", "yes"); setPropertyString("video-aspect-override", "no"); setPropertyString("video-unscaled", "yes") }
                ZoomMode.FORCE_16_9 -> { setPropertyString("keepaspect", "yes"); setPropertyString("video-aspect-override", "16:9") }
                ZoomMode.FORCE_4_3 -> { setPropertyString("keepaspect", "yes"); setPropertyString("video-aspect-override", "4:3") }
            }
        }
    }

    fun retry() {
        val url = currentUrl ?: return
        loadUrl(url, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, 0)
    }

    fun stop() {
        loadGeneration++ // cancels any queued-but-not-yet-executed load
        if (initialized) mpvAsync { command(arrayOf("stop")) }
        currentUrl = null
        pendingUrl = null
        _isPlaying.value = false
        _buffering.value = false
    }

    /**
     * The app moved to the background (Home / another app). An IPTV player has no background
     * playback — stop the stream so the demuxer cache and decoder buffers are freed immediately.
     * Holding them got the process LMK-killed at 490–620 MB PSS while invisible ("empty" state).
     */
    fun onAppBackgrounded() {
        if (currentUrl != null || pendingUrl != null) stop()
    }

    /**
     * The OS signaled serious memory pressure while we're alive: yield before the kernel takes.
     * Shrinks the demuxer cache live (it prunes already-buffered data too).
     */
    fun onTrimMemory() {
        if (!initialized) return
        mpvAsync {
            setPropertyString("demuxer-max-bytes", PlayerBudget.TRIM_DEMUXER_BYTES)
            setPropertyString("demuxer-max-back-bytes", "8MiB")
        }
    }

    fun release() {
        errorCheckJob?.cancel()
        scope.cancel()
        if (initialized) {
            val m = mpv
            mpv = null
            initialized = false
            // Destroy on the command thread so queued commands drain first (and never block the UI).
            mpvExecutor.execute {
                runCatching {
                    m?.removeObserver(this)
                    m?.destroy()
                }
            }
        }
        mpvExecutor.shutdown()
    }

    // --- Surface (driven by the MpvVideoSurface view) ---
    fun attachSurface(surface: Surface) {
        ensureInit()
        mpv?.attachSurface(surface)
        mpv?.setOptionString("force-window", "yes")
        mpv?.setOptionString("vo", if (useDirect()) "mediacodec_embed" else "gpu")
        surfaceAttached = true
        // Flush a load that was waiting for the surface (so video output inits correctly the first time).
        pendingUrl?.let { startLoad(it) }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        if (initialized) mpvAsync { setPropertyString("android-surface-size", "${width}x$height") }
    }

    fun detachSurface() {
        surfaceAttached = false
        if (!initialized) return
        mpv?.setPropertyString("vo", "null")
        mpv?.setOptionString("force-window", "no")
        mpv?.detachSurface()
    }

    // --- Tracks ---
    // Track lists are queried once per loaded file (on mpv's event thread) and cached, so the HUD
    // never issues synchronous mpv reads from the UI thread (those block during network stalls → ANR).
    private val _audioTrackList = MutableStateFlow<List<TrackOption>>(emptyList())
    private val _subTrackList = MutableStateFlow<List<TrackOption>>(emptyList())

    fun audioTracks(): List<TrackOption> = _audioTrackList.value
    fun textTracks(): List<TrackOption> = _subTrackList.value

    /** Synchronous mpv read — only call off the main thread (mpv event thread / mpv-cmd worker). */
    private fun queryTracks(type: String): List<TrackOption> {
        if (!initialized) return emptyList()
        val m = mpv ?: return emptyList()
        val count = m.getPropertyInt("track-list/count") ?: 0
        val out = ArrayList<TrackOption>()
        for (i in 0 until count) {
            if (m.getPropertyString("track-list/$i/type") != type) continue
            val id = m.getPropertyInt("track-list/$i/id") ?: continue
            val title = m.getPropertyString("track-list/$i/title")
            val lang = m.getPropertyString("track-list/$i/lang")
            val selected = m.getPropertyBoolean("track-list/$i/selected") ?: false
            out.add(TrackOption(label(title, lang, id), id, selected))
        }
        return out
    }

    fun selectAudio(mpvId: Int) {
        if (initialized) mpvAsync { setPropertyInt("aid", mpvId) }
        _audioTrackList.value = _audioTrackList.value.map { it.copy(selected = it.mpvId == mpvId) }
    }

    fun selectSubtitle(mpvId: Int) {
        if (initialized) mpvAsync { setPropertyInt("sid", mpvId) }
        _subTrackList.value = _subTrackList.value.map { it.copy(selected = it.mpvId == mpvId) }
    }

    fun disableSubtitles() {
        if (initialized) mpvAsync { setPropertyString("sid", "no") }
        _subTrackList.value = _subTrackList.value.map { it.copy(selected = false) }
    }

    private fun label(title: String?, lang: String?, id: Int): String {
        val l = lang?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { runCatching { Locale(it).displayLanguage }.getOrNull()?.ifBlank { it } ?: it }
        return listOfNotNull(title?.takeIf { it.isNotBlank() }, l).joinToString(" · ").ifBlank { "Track $id" }
    }

    // --- mpv event callbacks (called off the main thread) ---
    override fun eventProperty(property: String) {
        // A string property went unavailable/null. For sub-text that means "no line on screen now".
        if (property == "sub-text") _subText.value = null
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "time-pos" -> {
                _position.value = value * 1000
                if (value > 0) expectingPlayback = false // playback actually started
            }
            "duration" -> _duration.value = value * 1000
            "width" -> {
                currentWidthPx = value.toInt()
                updateAspect()
            }
            "height" -> {
                _videoRes.value = resolutionLabel(value.toInt())
                currentHeightPx = value.toInt()
                updateAspect()
                enforceDecodeGuard()
            }
        }
    }

    private fun updateAspect() {
        val w = currentWidthPx
        val h = currentHeightPx
        _videoAspect.value = if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
        _videoSize.value = if (w > 0 && h > 0) w to h else null
    }

    /**
     * Abort playback when a >1080p video lands on the SOFTWARE decoder (hwdec-current == "no"):
     * TV CPUs can't sustain it — it stutters for a few seconds, then the memory/thermal pressure
     * gets the whole app killed. ≤1080p software decoding stays allowed (viable, and the rescue
     * path for streams the hardware decoder mangles).
     */
    private fun enforceDecodeGuard() {
        if (decodeGuardTripped) return
        val hw = currentHwdec ?: return
        val h = currentHeightPx
        if (h <= 1080 || (hw != "no" && hw.isNotEmpty())) return
        android.util.Log.w(TAG, "Decode guard TRIPPED: ${h}px on software decoder")
        decodeGuardTripped = true
        val res = resolutionLabel(h) ?: "${h}p"
        val msg = if (hwDecoding) {
            "This TV's hardware decoder doesn't support this $res video, and software decoding " +
                "above 1080p would overload the TV."
        } else {
            "Hardware decoding is turned off — software decoding can't handle $res video. " +
                "Enable it in Settings → Video Player."
        }
        // Halt decoding but KEEP currentUrl so the HUD's Retry works (e.g. after the user flips the
        // hardware-decoding setting, which applies live).
        loadGeneration++
        expectingPlayback = false
        errorCheckJob?.cancel()
        pendingUrl = null
        mpvAsync { command(arrayOf("stop")) }
        scope.launch {
            _isPlaying.value = false
            _buffering.value = false
            _error.value = msg
        }
    }

    private fun resolutionLabel(height: Int): String? = when {
        height <= 0 -> null
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        else -> "${height}p"
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> _isPlaying.value = !value
            "paused-for-cache" -> _buffering.value = value
        }
    }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "hwdec-current" -> {
                android.util.Log.i(TAG, "hwdec-current='$value' (height=${currentHeightPx}px, setting=${if (hwDecoding) "on" else "off"})")
                currentHwdec = value
                enforceDecodeGuard()
            }
            // Active subtitle line for the app-drawn overlay (direct mode only; GL mode draws its own).
            "sub-text" -> {
                val line = value.trim().takeIf { it.isNotEmpty() }
                _subText.value = if (_directRender.value) line else null
            }
        }
    }
    override fun eventProperty(property: String, value: Double) {
        if (property == "speed") _speed.value = value
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                // The new file opened successfully — cancel any pending error and clear a stale one.
                // This callback runs on mpv's event thread (not main), so sync reads are safe here.
                expectingPlayback = false
                errorCheckJob?.cancel()
                _error.value = null
                _buffering.value = false // a reconnect's spinner ends when the new file loads
                _audioTrackList.value = queryTracks("audio")
                _subTrackList.value = queryTracks("sub")
                _audioCount.value = _audioTrackList.value.size
                _subCount.value = _subTrackList.value.size
                mpv?.getPropertyBoolean("pause")?.let { _isPlaying.value = !it }
                mpv?.getPropertyInt("height")?.let { _videoRes.value = resolutionLabel(it) }
                setZoomMode(_zoomMode.value) // re-apply zoom on the new track
                if (pendingSeekMs > 0) {
                    val seekMs = pendingSeekMs
                    pendingSeekMs = 0
                    mpvAsync { command(arrayOf("seek", (seekMs / 1000).toString(), "absolute")) }
                }
                // Decode watchdog, polled: the decoder is chosen a few seconds AFTER the file loads,
                // so read it directly once it has settled (the observed event also runs enforceDecodeGuard).
                val gen = loadGeneration
                scope.launch {
                    delay(4_000)
                    if (gen != loadGeneration) return@launch
                    mpvAsync {
                        val hw = getPropertyString("hwdec-current") ?: ""
                        val h = getPropertyInt("height") ?: 0
                        android.util.Log.i(TAG, "decode check: hwdec-current='$hw' height=${h}px direct=${_directRender.value}")
                        // The direct surface can only display hardware frames. If the direct decoder
                        // didn't engage (cold-boot decoder-busy, etc.), retry direct a few times (it
                        // usually frees within seconds), then fall back to software decode, then error.
                        if (_directRender.value && (hw.isEmpty() || hw == "no")) {
                            val pos = if (isLiveContent) 0L else _position.value
                            if (autoRetries < MAX_AUTO_RETRIES) {
                                autoRetries++
                                android.util.Log.w(TAG, "direct failed — retry $autoRetries/$MAX_AUTO_RETRIES")
                                _buffering.value = true
                                scope.launch {
                                    delay(800L * autoRetries)
                                    if (gen == loadGeneration) loadUrl(currentUrl ?: return@launch, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, pos, resetRetries = false)
                                }
                            } else if (hwDecodingActive() && !glUnsupported) {
                                // Direct decoder never engaged after retries — fall back to software decode
                                // (GL) for this item (weak decoders that mangle the stream) before erroring.
                                // Skipped on emulators, where the translated GL would crash.
                                android.util.Log.w(TAG, "direct failed — falling back to software decode for this item")
                                forceSoftwareThisLoad = true
                                applyRenderConfig()
                                scope.launch { loadUrl(currentUrl ?: return@launch, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, pos, resetRetries = false) }
                            } else {
                                android.util.Log.w(TAG, "direct failed — retries exhausted, showing error")
                                scope.launch { _buffering.value = false; _error.value = "This TV's video decoder is busy. Try again in a moment." }
                            }
                            return@mpvAsync
                        }
                        currentHwdec = hw.ifEmpty { null }
                        if (h > 0) currentHeightPx = h
                        enforceDecodeGuard()
                    }
                }
            }
            // A file ended. This also fires for the *previous* item when we load a new one (next/prev),
            // so don't error immediately — wait briefly; if the new file loads, FILE_LOADED cancels this.
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                if (expectingPlayback) {
                    val gen = loadGeneration
                    errorCheckJob?.cancel()
                    errorCheckJob = scope.launch {
                        delay(1500)
                        if (!expectingPlayback || gen != loadGeneration) return@launch
                        // The stream didn't start. Silently retry a few times with backoff before
                        // surfacing the error — handles transient failures (cold-boot decoder-busy,
                        // a provider 5xx, the first-play surface race) so the user rarely sees an error.
                        if (autoRetries < MAX_AUTO_RETRIES && currentUrl != null) {
                            autoRetries++
                            android.util.Log.w(TAG, "playback didn't start — auto-retry $autoRetries/$MAX_AUTO_RETRIES")
                            _buffering.value = true
                            delay(700L * autoRetries)
                            if (gen == loadGeneration && currentUrl != null) {
                                loadUrl(
                                    currentUrl!!, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl),
                                    isLiveContent, if (isLiveContent) 0L else _position.value, resetRetries = false,
                                )
                            }
                        } else if (hwDecodingActive() && !glUnsupported && currentUrl != null) {
                            // Hardware decoding never got it going — some weak TV decoders reject streams
                            // that software decoding plays fine. Try once in pure software before erroring.
                            // Skipped on emulators, where the translated GL would crash.
                            android.util.Log.w(TAG, "playback didn't start on hardware — falling back to software decode")
                            forceSoftwareThisLoad = true
                            _buffering.value = true
                            mpvAsync { applyRenderConfig() }
                            delay(200)
                            if (gen == loadGeneration && currentUrl != null) {
                                loadUrl(
                                    currentUrl!!, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl),
                                    isLiveContent, if (isLiveContent) 0L else _position.value, resetRetries = false,
                                )
                            }
                        } else {
                            _buffering.value = false
                            _error.value = "Couldn't play this stream. The source may be offline or use an unsupported format."
                        }
                    }
                } else if (isLiveContent && currentUrl != null) {
                    // A live stream died mid-play (provider hiccup / connection limit → HTTP 509):
                    // mpv goes idle and the screen would just stay blank. Show the buffering spinner
                    // and reconnect after a short pause; if that load also fails, the expectingPlayback
                    // path above shows the error UI with its Retry button. A user stop()/new load bumps
                    // loadGeneration and cancels.
                    _buffering.value = true
                    val gen = loadGeneration
                    scope.launch {
                        delay(1200)
                        if (gen == loadGeneration && currentUrl != null) retry() else _buffering.value = false
                    }
                }
            }
        }
    }
}
