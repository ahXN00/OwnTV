package tv.own.owntv.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import tv.own.owntv.features.automation.ApiRouteSet
import tv.own.owntv.features.automation.AppLauncher
import tv.own.owntv.features.automation.AutomationNav
import tv.own.owntv.features.automation.AutomationProfile
import tv.own.owntv.features.automation.AutomationUi
import tv.own.owntv.features.automation.AutomationUiState
import tv.own.owntv.features.automation.NavCheck
import tv.own.owntv.features.automation.NavGate
import tv.own.owntv.features.automation.ProfileCatalog
import tv.own.owntv.features.automation.RoomCatalogReads
import tv.own.owntv.features.automation.UnlockedProfile
import tv.own.owntv.features.automation.uiRoutes
import tv.own.owntv.features.shell.StaleShellItemsViewModel

/**
 * Automation API: the ui area. Its routes are an ApiRouteSet the automation module picks up. The shell
 * publishes into [UnlockedProfile] and [AutomationUiState] (less the last profile's items,
 * [StaleShellItemsViewModel]), and applies [AutomationNav] once [NavCheck]
 * agrees; the channel and VOD areas share the profile, what it may see ([ProfileCatalog]) and the gate.
 */
val automationUiModule = module {
    single { AutomationNav() }
    single { AutomationUiState() }
    viewModelOf(::StaleShellItemsViewModel)
    single { UnlockedProfile() }
    single {
        AutomationProfile(
            sourceActions = get(),
            sourceRepository = get(),
            settings = get(),
            profileDao = get(),
            unlocked = get(),
        )
    }
    single {
        val reads = RoomCatalogReads(
            profile = get(),
            profileDao = get(),
            customize = get(),
            categoryDao = get(),
            channelDao = get(),
            movieDao = get(),
            seriesDao = get(),
            reader = get(),
        )
        ProfileCatalog(reads)
    }
    single {
        val launcher: AppLauncher = get()
        NavGate(nav = get(), bringForward = { notice -> launcher.open(null, notice) })
    }
    single {
        val profile: AutomationProfile = get()
        val catalog: ProfileCatalog = get()
        NavCheck(nav = get(), activeProfile = { profile.id() }, showVisible = { pid, id -> catalog.show(pid, id) != null })
    }
    single(named("ui")) {
        val backend = AutomationUi(
            launcher = get(),
            foreground = get(),
            sensitive = get(),
            uiState = get(),
            profile = get(),
            gate = get(),
        )
        ApiRouteSet { uiRoutes(backend) }
    }
}
