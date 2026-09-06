package tv.own.owntv.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tv.own.owntv.features.customize.CustomizeItemsViewModel
import tv.own.owntv.features.customize.CustomizeViewModel
import tv.own.owntv.features.downloads.DownloadsViewModel
import tv.own.owntv.features.epg.EpgViewModel
import tv.own.owntv.core.content.SearchReader
import tv.own.owntv.core.home.HomeFeedReader
import tv.own.owntv.core.live.GuideReader
import tv.own.owntv.features.home.HomeViewModel
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.movies.MovieViewModel
import tv.own.owntv.features.profiles.ProfileGateSessionViewModel
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.features.search.SearchViewModel
import tv.own.owntv.features.series.SeriesViewModel
import tv.own.owntv.features.settings.LocalSyncViewModel
import tv.own.owntv.features.settings.BackupViewModel
import tv.own.owntv.features.settings.DeleteSubtitlesViewModel
import tv.own.owntv.features.settings.EpgSourcesViewModel
import tv.own.owntv.features.settings.HomeSettingsViewModel
import tv.own.owntv.features.settings.LanguageSettingsViewModel
import tv.own.owntv.features.settings.OpenSubtitlesViewModel
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.setup.SetupViewModel
import tv.own.owntv.features.shell.ShellViewModel
import tv.own.owntv.features.subtitles.SubtitleSearchViewModel

/**
 * Root Koin module. Each feature will contribute its own bindings as the app grows;
 * for now this wires settings persistence and the shell view model.
 *
 * ViewModels are bound with `viewModelOf(::X)` constructor references, never positional `get()`
 * lists: with 15-23 parameters, a positional list only has to *count* right, so inserting or
 * reordering two same-typed parameters would silently swap two dependencies at runtime with no
 * compile error and no Koin error. A constructor reference binds by the declared constructor, so
 * reordering is safe and a missing binding fails immediately, naming the type.
 */
val appModule = module {
    viewModelOf(::ShellViewModel)
    // Home's rails are core's, shared with the phone app; the view model only decorates them.
    singleOf(::GuideReader)
    singleOf(::SearchReader)
    singleOf(::HomeFeedReader)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SetupViewModel)
    // Takes a Context first; Koin resolves it from androidContext().
    viewModelOf(::LiveViewModel)
    viewModelOf(::MovieViewModel)
    viewModelOf(::SeriesViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::ProfilesViewModel)
    // Activity-scoped session state for the profile gate (configuration-only retention, no saved
    // state — see ProfileGateSessionViewModel).
    viewModelOf(::ProfileGateSessionViewModel)
    // 24 constructor parameters — past the highest arity Koin's `*Of` DSL generates, so this one
    // binding stays explicit. Named arguments give it the same guarantee viewModelOf gives the rest:
    // reordering the constructor is safe, and adding a parameter is a compile error here, not a
    // silently mis-wired dependency at runtime.
    viewModel {
        SettingsViewModel(
            profileDao = get(),
            sourceDao = get(),
            sourceRepository = get(),
            settings = get(),
            connectivity = get(),
            epgDao = get(),
            importFinalizer = get(),
            channelDao = get(),
            categoryDao = get(),
            customizationStore = get(),
            navVisibility = get(),
            userDataWriter = get(),
            epgRepository = get(),
            epgSourceStore = get(),
            launcherIntegrationRepository = get(),
            catalogSyncScheduler = get(),
            okHttpClient = get(),
            metadataProvider = get(),
            metadataRepository = get(),
            metadataBudget = get(),
            stalkerAuth = get(),
            stalkerClient = get(),
            xtreamClient = get(),
            sourceTester = get(),
            companion = get(),
            vodEngineStore = get(),
            playbackPrefs = get(),
        )
    }
    viewModelOf(::LocalSyncViewModel)
    viewModelOf(::HomeSettingsViewModel)
    viewModelOf(::LanguageSettingsViewModel)
    viewModelOf(::OpenSubtitlesViewModel)
    viewModelOf(::DeleteSubtitlesViewModel)
    viewModelOf(::SubtitleSearchViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::EpgViewModel)
    viewModelOf(::CustomizeViewModel)
    viewModelOf(::CustomizeItemsViewModel)
    viewModelOf(::BackupViewModel)
    viewModelOf(::EpgSourcesViewModel)
}
