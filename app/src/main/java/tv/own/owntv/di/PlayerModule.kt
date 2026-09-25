package tv.own.owntv.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.player.HeroPreviewEngine
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.player.OwnTVPlayer

/** App-wide libmpv player. */
val playerModule = module {
    // Tails own-process logcat for MediaCodec/AudioTrack errors the engines can't expose.
    single { tv.own.owntv.player.PlayerDiagnostics() }
    // Named, because nine consecutive get() calls silently depend on parameter ORDER: reorder the
    // constructor and Koin still resolves by type, so two same-typed dependencies would swap unnoticed.
    single {
        OwnTVPlayer(
            context = androidContext(),
            settings = get(),
            connectivity = get(),
            streamingHttp = get(), // ExoPlayer image-sub handoff
            diagnostics = get(),
            proxyHolder = get(),
            vodEngineStore = get(),
            localeStore = get(),
            playbackPrefs = get(), // per-item zoom/volume
            originalLanguage = get(), // "Original language" audio
        )
    }
    // ExoPlayer engine for the fast Live preview pane (mpv stays the full/fullscreen player).
    // context, streamingHttp, diagnostics, settings, connectivity (auto-resume when the network
    // returns), playbackPrefs (per-channel zoom/volume)
    single { LivePreviewEngine(androidContext(), get(), get(), get(), get(), get()) }
    // Multiview's engines. The `single` above stays exactly what it was — the one long-lived engine
    // behind the Live preview pane and promoted fullscreen playback. The pool builds its own, one per
    // tile, and is the only thing that owns more than one at a time.
    single {
        tv.own.owntv.player.LiveEnginePool {
            LivePreviewEngine(androidContext(), get(), get(), get(), get(), get())
        }
    }
    // Muted ExoPlayer engine for the Home hero preview. The last argument lets it ask whether mpv is
    // already streaming, so a one-session provider isn't locked out by the hero preview (F19d).
    // Resolved lazily inside the lambda to keep this free of a construction-order dependency.
    single { HeroPreviewEngine(androidContext(), get(), get(), streamInUse = { get<OwnTVPlayer>().hasActiveStream }) }
    // Every engine above, so Home / the screensaver / memory pressure reach all of them in one call —
    // the Multiview pool included, which the hand-kept list in MainActivity used to miss.
    single {
        tv.own.owntv.player.PlaybackEngines(
            player = get(),
            livePreview = get(),
            pool = get(),
            heroPreview = get(),
        )
    }
    // Audio focus (duck-don't-pause) + the system MediaSession, driven by whichever engine is playing.
    single { tv.own.owntv.player.PlaybackSession(androidContext()) }
    // N17 — ends itself when the session detaches; what "stop" means is assigned by the shell.
    single { tv.own.owntv.player.ScreenOff(androidContext()) }
    single { tv.own.owntv.player.SleepTimer(active = get<tv.own.owntv.player.PlaybackSession>().active, screenOff = get(), itemEnd = get<OwnTVPlayer>()) }
    // Bridges the playing item to the OpenSubtitles search. Bound here rather than with the rest of
    // the subtitle stack because it takes the player; it follows the engine to :player-core.
    single { tv.own.owntv.core.subtitles.SubtitleController(get(), get(), get(), get()) }
}
