package tv.own.owntv

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import tv.own.owntv.core.util.Perf
import tv.own.owntv.core.di.coreModule
import tv.own.owntv.core.di.databaseModule
import tv.own.owntv.core.di.dataModule
import tv.own.owntv.di.appModule
import tv.own.owntv.di.automationModule
import tv.own.owntv.di.automationSetupModule
import tv.own.owntv.di.automationUiModule
import tv.own.owntv.di.automationVodModule
import tv.own.owntv.di.playerModule
import tv.own.owntv.ui.theme.subtitleFontResource

class OwnTVApp : Application(), SingletonImageLoader.Factory, androidx.work.Configuration.Provider {

    companion object {
        /** Upper bound for the image disk cache — the value it was once fixed at. */
        private const val MAX_IMAGE_CACHE_BYTES = 250L * 1024 * 1024

        /** Lower bound: below this the cache thrashes and stops saving any downloads. */
        private const val MIN_IMAGE_CACHE_BYTES = 32L * 1024 * 1024

        /** Sports' Auto rebuild waits this long after launch, so it never competes with the first screen. */
        private const val SPORTS_START_DELAY_MS = 15_000L
    }

    /** Application-lifetime scope for small fire-and-forget IO that must not touch the launch path. */
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(tv.own.owntv.core.sync.work.KoinWorkerFactory())
            .build()

    /**
     * Wrap the Application base with the selected locale and apply it to the process locale
     * defaults BEFORE the rest of the process starts. The selected tag is read synchronously from
     * the SharedPreferences-backed LocaleStore (DataStore is async and cannot be read here).
     *
     * Application wrapping is retained on a verified platform ground (see docs/internationalization.md
     * 0b): Android's ConfigurationController resets Locale.getDefault() to the device locale on every
     * system configuration change unless the Application context carries our locale. Koin singletons
     * and Workers no longer resolve strings here under v2, but java.text / java.time / String.format /
     * every Locale.getDefault() reader still depend on it.
     */
    override fun attachBaseContext(base: Context) {
        val tag = tv.own.owntv.core.i18n.LocaleStore.from(base).readBlocking()
        tv.own.owntv.core.i18n.AppLocale.applyGlobally(tag)
        super.attachBaseContext(tv.own.owntv.core.i18n.AppLocale.wrap(base, tag))
    }

    /**
     * Re-apply the selected locale after the framework's process-level reset. "System default" (`""`)
     * resolves the *new* device locale list, so a device-locale change while following system applies on
     * the next callback rather than freezing the startup locale.
     */
    /** Core opens external players and system screens from this context: each one is a hand-off. */
    override fun startActivity(intent: Intent) {
        tv.own.owntv.features.pip.startAsHandOff(intent) { super.startActivity(it) }
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        tv.own.owntv.features.pip.startAsHandOff(intent) { super.startActivity(it, options) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val tag = tv.own.owntv.core.i18n.LocaleStore.from(this).readBlocking()
        tv.own.owntv.core.i18n.AppLocale.applyGlobally(tag)
    }

    override fun onCreate() {
        Perf.begin() // zero-point for the OwnTVPerf startup timeline (adb logcat -s OwnTVPerf)
        super.onCreate()
        // "Restart now" after an icon change runs a few milliseconds in a process of its own; nothing
        // below may start there (core's AppRestartActivity).
        if (tv.own.owntv.core.brand.AppIconSwitcher.isRestartProcess(this)) return
        // Core has its own BuildConfig, which carries none of this: a library gets no version at all,
        // and the edge key and the maintainer switch are the app's build inputs. Hand them over before
        // the first reader — CrashRecorder, two lines down (see CoreBuildInfo).
        tv.own.owntv.core.CoreBuildInfo.versionName = BuildConfig.VERSION_NAME
        tv.own.owntv.core.CoreBuildInfo.versionCode = BuildConfig.VERSION_CODE
        tv.own.owntv.core.CoreBuildInfo.edgeKey = BuildConfig.TMDB_EDGE_KEY
        tv.own.owntv.core.CoreBuildInfo.devTools = BuildConfig.DEV_TOOLS
        tv.own.owntv.core.CoreBuildInfo.debug = BuildConfig.DEBUG
        tv.own.owntv.core.CoreBuildInfo.diagnosticBuild = BuildConfig.DIAGNOSTIC_BUILD
        // This app's own releases, which the in-app updater asks about. Core defaults to exactly
        // this value, so the line changes nothing today — it is here so the television names its own
        // repository instead of relying on core to guess it, the same as the phone app does. A
        // default that happens to be right for one app is a trap for every other one.
        tv.own.owntv.core.CoreBuildInfo.releaseRepo = "ahXN00/OwnTV"
        // Fixed for this process before any player exists: the setting applies only after a restart.
        tv.own.owntv.core.player.FfmpegAudioSetting.load(this)
        // First thing after the context exists: a crash from here on leaves a trace on disk that the
        // user can export from Settings, instead of being lost with the process.
        tv.own.owntv.core.util.CrashRecorder.diagnostics = { tv.own.owntv.player.LiveDiagnosticsLog.snapshot() }
        tv.own.owntv.core.util.CrashRecorder.install(this)
        // Core learns a panel's session limit while syncing; the engine is what acts on it. Registered
        // before Koin so the very first source flow already reaches the player (see LiveSessionLimit).
        tv.own.owntv.core.player.LiveSessionLimit.report = tv.own.owntv.player.LiveStreamQuirks::rememberSessionLimit
        // Four of the five subtitle faces are this app's own res/font files, so the engine asks us for
        // the id rather than owning the assets (see SubtitleFontAssets).
        tv.own.owntv.player.SubtitleFontAssets.resourceOf = { it.subtitleFontResource }
        // The six icon colours are MainActivity + a suffix (see MainActivityColours.kt); core switches them.
        tv.own.owntv.core.brand.AppIconSwitcher.mainActivityClass = MainActivity::class.java.name
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@OwnTVApp)
            modules(coreModule, appModule, automationModule, automationUiModule, automationVodModule, automationSetupModule, databaseModule, dataModule, playerModule)
        }
        Perf.stamp("koin-started")
        // Automation API: back on after a process restart when the user left it on.
        GlobalContext.get().get<tv.own.owntv.features.automation.AutomationController>().startIfEnabled()
        // A chosen icon colour ("Later", the first-run pick, a restore) reaches the launcher when the app
        // is next in the background, never while it is on screen.
        tv.own.owntv.core.brand.AppIconSwitcher.start(this, GlobalContext.get().get())
        // Diagnostics switch and the one-shot settings migrations — core's, shared with the phone, which
        // used to run none of them.
        tv.own.owntv.player.PlaybackStartup.start(
            context = this,
            scope = appScope,
            settings = GlobalContext.get().get(),
        )
        // The Stage UI's own defaults, once per install: Docked · Compact rail, glass
        // on at 50%, the Stage background; Movies & Series keep an update's layout.
        appScope.launch {
            runCatching {
                GlobalContext.get().get<tv.own.owntv.core.settings.SettingsRepository>().applyStageDefaults()
            }
        }
        // Sports: core decides from Auto rebuild whether the stored board is due ("on every app start",
        // or older than the chosen hours). It reads one small file and touches no database.
        appScope.launch {
            kotlinx.coroutines.delay(SPORTS_START_DELAY_MS)
            runCatching { GlobalContext.get().get<tv.own.owntv.core.sports.SportsRepository>().appStarted() }
        }
        // NOTE: cold start does ZERO heavy DB work. Index + ANALYZE maintenance is piggy-backed onto the
        // operation that actually changes the data — ImportFinalizer.finalize() for normal re-syncs, the
        // deferred content-index worker after a fresh import, the EpgRepository refresh after every EPG
        // sync, and the v5 migration on upgrade — never onto app launch.
        // A previous build ran `ANALYZE movies`/`ANALYZE series` (a full scan of 170k+50k rows) here at EVERY
        // cold start. On a low-end TV box's eMMC + CPU that saturated storage and the single DB connection at
        // the precise moment the Movies/Series grids need their first read — which is exactly why the grids
        // took seconds to appear. It was also redundant: content only ever changes via a sync, and every sync
        // already re-runs ANALYZE in finalize(), so the launch-time re-analyze was pure recurring tax. With it
        // off the launch path, the grids' first indexed query returns in well under a second.
    }

    /**
     * App-wide Coil loader that fetches images through the app's OkHttpClient — so posters/logos get
     * the same player-style User-Agent and connection pooling our IPTV requests use (some panels reject
     * default UAs). Crossfade (120 ms, off on low-RAM) smooths the grid as images stream in. The memory
     * cache stays well below Coil's default ~25% — on a 4K TV every megabyte belongs to the video
     * pipeline, not to channel-logo bitmaps: 15% of the heap up to 64 MB, and 10% on low-RAM devices.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val lowRam = context.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice == true
        // The shared client deliberately disables connection retries so catalog/EPG sync owns its retry
        // policy. That setting is harmful for CDN images, though: hosts such as image.tmdb.org publish
        // both IPv6 and IPv4, and some Android TV boxes resolve IPv6 first even when their IPv6 route is
        // unusable. OkHttp then needs retryOnConnectionFailure to try the next resolved address (the TV's
        // browser already does this fallback). Derive one long-lived client so Coil keeps the app-wide
        // proxy, DNS, UA, TLS and connection-pool configuration while regaining alternate-route fallback.
        // The shared client is also pinned to HTTP/1.1, because IPTV panels answer an HTTP/2 handshake
        // with RST_STREAM(PROTOCOL_ERROR). Image CDNs are the opposite case: image.tmdb.org negotiates
        // h2, and a poster grid opens dozens of requests to that one host at once. Over HTTP/1.1 those
        // queue behind OkHttp's per-host connection limit; over h2 they multiplex on a single connection.
        // Restore protocol negotiation for images only — the panel-facing client keeps its 1.1 pin.
        val imageHttpClient = GlobalContext.get().get<OkHttpClient>()
            .newBuilder()
            .retryOnConnectionFailure(true)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient })) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(imageMemoryCacheBytes(Runtime.getRuntime().maxMemory(), lowRam))
                    .build()
            }
            // Explicit bounded disk cache: posters/logos survive across sessions instead of
            // re-downloading, capped so a 220k-item catalog can't eat the box's storage.
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(imageDiskCacheBytes())
                    .build()
            }
            // Opaque poster art doesn't need an alpha channel; RGB_565 halves bitmap memory
            // under the deliberately small memory cap.
            .allowRgb565(true)
            .crossfade(if (lowRam) 0 else 120)
            .build()
    }

    /**
     * Size the poster/logo disk cache against the storage the box actually has: 5% of free
     * space, capped at the previous fixed 250 MB and floored at 32 MB (below that the cache stops
     * being worth having). A 220k-item catalog *will* reach whatever cap it is given, so on a nearly
     * full 8 GB box the fixed number was competing with downloads and app updates for the last
     * gigabyte. One `statfs` call at Application construction, never re-evaluated at runtime — the
     * launch path stays free of directory walks.
     */
    private fun imageDiskCacheBytes(): Long {
        val free = runCatching { cacheDir.usableSpace }.getOrDefault(0L).coerceAtLeast(0L)
        return minOf(MAX_IMAGE_CACHE_BYTES, (free * 0.05).toLong()).coerceAtLeast(MIN_IMAGE_CACHE_BYTES)
    }

    /**
     * Memory-pressure airbag: when the OS warns we're a kill candidate, drop the image cache and
     * shrink the player's stream cache live instead of waiting for the low-memory killer.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // TRIM_MEMORY_RUNNING_LOW is deprecated on API 34+ but still the only signal on TV devices
        // running older Android, so keep honouring it.
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
        }
        // The engines judge the level themselves: UI_HIDDEN (every Home press) is not pressure for them,
        // and used to leave mpv on a trimmed cache for the rest of the session.
        runCatching { GlobalContext.getOrNull()?.getOrNull<tv.own.owntv.player.PlaybackEngines>()?.onTrimMemory(level) }
    }
}

/** 15% of the heap, capped at 64 MB; low-RAM devices keep 10%. */
internal fun imageMemoryCacheBytes(heapBytes: Long, lowRam: Boolean): Long {
    if (lowRam) return (heapBytes * 0.10).toLong()
    return minOf((heapBytes * 0.15).toLong(), 64L * 1024 * 1024)
}
