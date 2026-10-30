package tv.own.owntv.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import tv.own.owntv.core.i18n.SupportedLocales
import tv.own.owntv.core.profile.ProfileManager
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.features.automation.ApiRouteSet
import tv.own.owntv.features.automation.AutomationAppSettings
import tv.own.owntv.features.automation.AutomationController
import tv.own.owntv.features.automation.AutomationProfile
import tv.own.owntv.features.automation.AutomationProfiles
import tv.own.owntv.features.automation.AutomationSetup
import tv.own.owntv.features.automation.AutomationSetupSession
import tv.own.owntv.features.automation.AutomationSetupViewModel
import tv.own.owntv.features.automation.AutomationStore
import tv.own.owntv.features.automation.PairingWindow
import tv.own.owntv.features.automation.PlayerStop
import tv.own.owntv.features.automation.PlayingEngine
import tv.own.owntv.features.automation.PlaylistTarget
import tv.own.owntv.features.automation.ProfileChoice
import tv.own.owntv.features.automation.ProfileSwitch
import tv.own.owntv.features.automation.SetupProfile
import tv.own.owntv.features.automation.profileRoutes
import tv.own.owntv.features.automation.setupRoutes
import tv.own.owntv.player.SleepTimer

/**
 * Automation API: first-run setup. The wizard's "Set up by Automation" page and its visit, which the setup
 * and profile routes ask whether a device paired from it ([AutomationSetupSession]), and the profile the
 * playlist routes act for while setup runs ([PlaylistTarget]).
 * "Who's watching?" takes the profile the API opened from [ProfileChoice].
 */
val automationSetupModule = module {
    viewModelOf(::AutomationSetupViewModel)
    single {
        val store = get<AutomationStore>()
        val pairing = get<PairingWindow>()
        AutomationSetupSession(
            enabled = { store.settings.value.enabled },
            setEnabled = get<AutomationController>()::setEnabled,
            openPairing = { pairing.open() },
            closePairing = pairing::close,
            deviceIds = { store.settings.value.devices.mapTo(mutableSetOf()) { it.id } },
            unpair = store::removeDevice,
            offBefore = { store.settings.value.offBeforeSetup },
            setOffBefore = store::setOffBeforeSetup,
            scope = get(),
        )
    }
    single {
        SetupProfile(activeProfileId = get<SettingsRepository>().activeProfileId, profileDao = get(), session = get())
    }
    single { ProfileChoice() }
    single {
        val open = get<AutomationProfile>()
        PlaylistTarget(openId = open::id, openPlaylists = open::playlists, setup = get())
    }
    single(named("setup")) {
        val backend = AutomationSetup(
            setup = get(),
            session = get(),
            profileDao = get(),
            finishFirstRun = { profileId -> get<SourceImporter>().apply { useProfile(profileId) }.finish() },
            appSettings = AutomationAppSettings(get()),
            choice = get(),
            nav = get(),
        )
        val languages = SupportedLocales.pickerRows.mapTo(mutableSetOf(SupportedLocales.SYSTEM_DEFAULT_TAG)) { it.languageTag }
        ApiRouteSet { setupRoutes(backend, languages) }
    }
    single(named("profiles")) {
        val stop = PlayerStop(get<SleepTimer>(), PlayingEngine(get(), get()))
        val manager = get<ProfileManager>()
        val backend = AutomationProfiles(
            profileDao = get(),
            // No PIN to pass on: one is only ever set on the TV.
            create = { name, avatar, kids -> manager.create(name, avatar, kids, pin = null, fallbackName = name) },
            target = get(),
            setup = get(),
            sensitive = get(),
            switchTo = ProfileSwitch(stop, manager, choice = get())::switchTo,
        )
        ApiRouteSet { profileRoutes(backend) }
    }
}
