package tv.own.owntv.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.player.HeroPreviewEngine
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.livePreviewEngine
import tv.own.owntv.player.ownTVPlayer

/** App-wide libmpv player. */
val playerModule = module {
    // Tails own-process logcat for MediaCodec/AudioTrack errors the engines can't expose.
    single { tv.own.owntv.player.PlayerDiagnostics() }
    // Both engines are built by core's shared builders (named arguments, one copy for both apps).
    single { ownTVPlayer() }
    // ExoPlayer engine for the fast Live preview pane (mpv stays the full/fullscreen player).
    single { livePreviewEngine() }
    // Multiview's engines. The `single` above stays exactly what it was — the one long-lived engine
    // behind the Live preview pane and promoted fullscreen playback. The pool builds its own, one per
    // tile, and is the only thing that owns more than one at a time.
    single { tv.own.owntv.player.LiveEnginePool { livePreviewEngine() } }
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
    // Host failover's toast and "All N servers failed", shared by Live, films and episodes.
    single { tv.own.owntv.player.ServerNotices() }
    // One per film/series ViewModel: plays on the in-use server and reopens on the next one (AC5).
    factory {
        tv.own.owntv.player.VodServerFailover(
            player = get<tv.own.owntv.player.OwnTVPlayer>(),
            hosts = get<tv.own.owntv.core.network.HostSelector>(),
            gate = get<tv.own.owntv.player.ReleaseGate>(),
            xtream = get<tv.own.owntv.core.parser.XtreamClient>(),
            notices = get<tv.own.owntv.player.ServerNotices>(),
            connectivity = get<tv.own.owntv.core.network.ConnectivityObserver>(),
            settings = get<tv.own.owntv.core.settings.SettingsRepository>(),
            openStreams = get<tv.own.owntv.core.live.OpenStreamRegistry>(),
        )
    }
    // Host failover: stops every engine and anything else holding the account before another server
    // is opened (one connection at a time). Core's LiveTuneController and VodFailover resolve it lazily.
    single {
        tv.own.owntv.player.ReleaseGate(
            tv.own.owntv.player.PlaybackReleaseSteps(
                player = get(),
                livePreview = get(),
                pool = get(),
                heroPreview = get(),
                streaming = get(),
                openStreams = get(),
                downloads = get(),
                timeshift = getOrNull(),
                hosts = get(),
            ),
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
