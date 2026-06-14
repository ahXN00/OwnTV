package tv.own.owntv.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import tv.own.owntv.features.customize.CustomizeViewModel
import tv.own.owntv.features.downloads.DownloadsViewModel
import tv.own.owntv.features.epg.EpgViewModel
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.movies.MovieViewModel
import tv.own.owntv.features.profiles.ProfilesViewModel
import tv.own.owntv.features.search.SearchViewModel
import tv.own.owntv.features.series.SeriesViewModel
import tv.own.owntv.features.settings.BackupViewModel
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.settings.data.SettingsRepository
import tv.own.owntv.features.setup.SetupViewModel
import tv.own.owntv.features.shell.ShellViewModel

/**
 * Root Koin module. Each feature will contribute its own bindings as the app grows;
 * for now this wires settings persistence and the shell view model.
 */
val appModule = module {
    single { SettingsRepository(androidContext()) }
    viewModel { ShellViewModel(get(), get(), get(), get(), get()) }
    // profileDao, sourceDao, sourceRepository, backup, settings, connectivity, importFinalizer
    viewModel { SetupViewModel(get(), get(), get(), get(), get(), get(), get()) }
    // channelDao, categoryDao, favoriteDao, historyDao, sourceDao, settings, xtreamClient, customize, epgDao, epgSourceStore, player
    viewModel { LiveViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    // movieDao, categoryDao, favoriteDao, historyDao, progressDao, sourceDao, settings, customize, player, downloadManager
    viewModel { MovieViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    // seriesDao, categoryDao, favoriteDao, historyDao, progressDao, sourceDao, seriesRepository, settings, customize, player, downloadManager
    viewModel { SeriesViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    // channelDao, movieDao, seriesDao, historyDao, sourceDao, settings, customize, player
    viewModel { SearchViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    // profileDao, sourceDao, settings
    viewModel { ProfilesViewModel(get(), get(), get()) }
    // sourceDao, sourceRepository, settings, connectivity, epgDao, importFinalizer
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    // downloadDao, settings, downloadManager, player
    viewModel { DownloadsViewModel(get(), get(), get(), get()) }
    // settings, sourceRepository, channelDao, epgDao, epgRepository, epgSourceStore, connectivity, customize, historyDao, player
    viewModel { EpgViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    // settings, sourceDao, categoryDao, customizationStore
    viewModel { CustomizeViewModel(get(), get(), get(), get()) }
    // backupManager
    viewModel { BackupViewModel(get()) }
    // store, epgRepository, sourceRepository, settings, connectivity, epgDao
    viewModel { tv.own.owntv.features.settings.EpgSourcesViewModel(get(), get(), get(), get(), get(), get()) }
}
