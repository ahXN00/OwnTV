package tv.own.owntv.di

import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.backup.UserDataResolver
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.parser.M3uParser
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.repository.SeriesRepository
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.core.sync.SyncManager
import java.util.concurrent.TimeUnit

/** Networking, parsers, sync engine, and repositories (Phase 5). */
val dataModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Default a player-style UA for any request that didn't set one (e.g. Coil image loads),
            // since some IPTV panels reject the stock OkHttp UA. Per-source UAs still override this.
            .addInterceptor { chain ->
                val req = chain.request()
                val out = if (req.header("User-Agent").isNullOrBlank()) {
                    req.newBuilder().header("User-Agent", HttpClient.DEFAULT_USER_AGENT).build()
                } else {
                    req
                }
                chain.proceed(out)
            }
            .build()
    }
    single { HttpClient(get()) }
    single { ConnectivityObserver(androidContext()) }
    single { CustomizationStore(androidContext()) }
    single { tv.own.owntv.core.epg.EpgSourceStore(androidContext()) }
    // store, sourceDao, epgRepository
    single { tv.own.owntv.core.epg.EpgMigration(get(), get(), get()) }
    // channelDao, movieDao, seriesDao, epgSourceStore, epgRepository
    single { tv.own.owntv.core.sync.ImportFinalizer(get(), get(), get(), get(), get()) }
    single { M3uParser() }
    single { XtreamClient(get()) }
    single { SyncManager(get(), get(), get(), get(), get(), get(), get(), get()) }
    // context, channelDao, movieDao, seriesDao, favoriteDao, historyDao, progressDao
    single { UserDataResolver(androidContext(), get(), get(), get(), get(), get(), get()) }
    // sourceDao, syncManager, userDataResolver
    single { SourceRepository(get(), get(), get()) }
    // epgDao, httpClient, xtreamClient
    single { EpgRepository(get(), get(), get()) }
    // seriesDao, sourceDao, xtreamClient, userDataResolver
    single { SeriesRepository(get(), get(), get(), get()) }
    // context, downloadDao, okHttpClient, settings
    single { DownloadManager(androidContext(), get(), get(), get()) }
    // profileDao, sourceDao, settings, customizationStore, userDataResolver, epgSourceStore
    single { BackupManager(get(), get(), get(), get(), get(), get()) }
    // context, okHttpClient — in-app updates from GitHub Releases
    single { UpdateManager(androidContext(), get()) }
}
