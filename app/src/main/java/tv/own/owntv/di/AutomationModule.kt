package tv.own.owntv.di

import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tv.own.owntv.features.automation.ApiRouteSet
import tv.own.owntv.features.automation.AppLauncher
import tv.own.owntv.features.automation.AutomationAppSettings
import tv.own.owntv.features.automation.AutomationCache
import tv.own.owntv.features.automation.AutomationChannels
import tv.own.owntv.features.automation.AutomationController
import tv.own.owntv.features.automation.AutomationForeground
import tv.own.owntv.features.automation.AutomationGate
import tv.own.owntv.features.automation.AutomationGuide
import tv.own.owntv.features.automation.AutomationLogs
import tv.own.owntv.features.automation.AutomationNotices
import tv.own.owntv.features.automation.AutomationPlayer
import tv.own.owntv.features.automation.PlayerStop
import tv.own.owntv.features.automation.PlayingEngine
import tv.own.owntv.features.automation.AutomationPlaylists
import tv.own.owntv.features.automation.AutomationRoutes
import tv.own.owntv.features.automation.AutomationServer
import tv.own.owntv.features.automation.AutomationStore
import tv.own.owntv.features.automation.AutomationViewModel
import tv.own.owntv.features.automation.KeyGate
import tv.own.owntv.features.automation.KeySender
import tv.own.owntv.features.automation.PairingWindow
import tv.own.owntv.features.automation.ScreenWaker
import tv.own.owntv.features.automation.SensitiveScreens
import tv.own.owntv.features.automation.WakeNonce
import tv.own.owntv.features.automation.cacheRoutes
import tv.own.owntv.features.automation.channelRoutes
import tv.own.owntv.features.automation.guideRoutes
import tv.own.owntv.features.automation.logRoutes
import tv.own.owntv.features.automation.playerRoutes
import tv.own.owntv.features.automation.playlistRoutes
import tv.own.owntv.features.automation.settingsRoutes
import tv.own.owntv.player.SleepTimer

/** The automation API: one store, gate, server and controller for the whole process, and a backend per area. */
val automationModule = module {
    single { AutomationStore(androidContext()) }
    single { PairingWindow() }
    single { SensitiveScreens() }
    // Created with Koin, before the first screen resumes, so it always knows which screen is in front.
    single(createdAtStart = true) { AutomationForeground(androidApplication()) }
    single { AutomationNotices() }
    single {
        val foreground: AutomationForeground = get()
        KeyGate(inFront = { foreground.activity() != null }, screens = get(), sender = KeySender.forThisDevice())
    }
    single { WakeNonce() }
    single { ScreenWaker(androidContext()) }
    single { AppLauncher(androidContext(), get(), get(), get(), get(), get()) }
    single {
        AutomationPlaylists(
            newImporter = { get() },
            sourceActions = get(),
            profile = get(),
            settings = get(),
            importFinalizer = get(),
            epgRepository = get(),
            epgSourceStore = get(),
            catalogSyncScheduler = get(),
            sourceTester = get(),
            scope = get(),
        )
    }
    single {
        AutomationGuide(
            store = get(),
            epgRepository = get(),
            epgDao = get(),
            settings = get(),
            scheduler = get(),
            scope = get(),
        )
    }
    single {
        AutomationChannels(
            catalog = get(),
            profile = get(),
            launcher = get(),
        )
    }
    single {
        val store: AutomationStore = get()
        AutomationGate(settings = { store.settings.value }, addDevice = store::addDevice, pairing = get())
    }
    single {
        val engines = PlayingEngine(get(), get())
        val routes = playlistRoutes(get<AutomationPlaylists>()) +
            guideRoutes(get<AutomationGuide>()) +
            channelRoutes(get<AutomationChannels>()) +
            playerRoutes(AutomationPlayer(engines, PlayerStop(get<SleepTimer>(), engines), get(), get())) +
            settingsRoutes(AutomationAppSettings(get())) +
            cacheRoutes(AutomationCache(androidContext(), get())) +
            logRoutes(AutomationLogs(androidContext())) +
            getAll<ApiRouteSet>().flatMap { it.routes() }.sortedBy { it.pattern }
        val store: AutomationStore = get()
        AutomationServer(AutomationRoutes(get(), routes), get(), allowTailscale = { store.settings.value.allowTailscale })
    }
    single { AutomationController(androidContext(), get(), get()) }
    viewModelOf(::AutomationViewModel)
}
