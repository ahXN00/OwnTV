package tv.own.owntv.core.sync

import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.repository.EpgRepository

/** Per-type counts after a playlist import, for the success breakdown. */
data class SyncCounts(val channels: Int, val movies: Int, val series: Int, val epg: Int = 0) {
    /** e.g. "40K channels · 100K movies · 30K series · 30K EPG synced". */
    fun summary(includeEpg: Boolean): String {
        val parts = buildList {
            if (channels > 0) add("${human(channels)} channels")
            if (movies > 0) add("${human(movies)} movies")
            if (series > 0) add("${human(series)} series")
            if (includeEpg && epg > 0) add("${human(epg)} EPG")
        }
        return if (parts.isEmpty()) "Synced successfully" else parts.joinToString(" · ") + " synced"
    }

    /** Content breakdown without a trailing verb, for list rows: "40K channels · 100K movies · 30K series". */
    val breakdown: String
        get() = buildList {
            if (channels > 0) add("${human(channels)} channels")
            if (movies > 0) add("${human(movies)} movies")
            if (series > 0) add("${human(series)} series")
        }.joinToString(" · ")

    private fun human(n: Int): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).removeSuffix(".0M").let { if (it.endsWith("M")) it else "${it}M" }
        n >= 1_000 -> "%.1fK".format(n / 1_000.0).removeSuffix(".0K").let { if (it.endsWith("K")) it else "${it}K" }
        else -> n.toString()
    }
}

/**
 * Runs after a playlist syncs: auto-creates & syncs the playlist's own EPG (Xtream `xmltv.php` /
 * M3U `url-tvg`) so the guide "just works", de-duplicated by URL — then returns the per-type counts
 * for the success message.
 */
class ImportFinalizer(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val epgSourceStore: EpgSourceStore,
    private val epgRepository: EpgRepository,
) {
    suspend fun finalize(source: SourceEntity): SyncCounts {
        val epg = ensureEpg(source)
        return SyncCounts(
            channels = channelDao.countForSourceOnce(source.id),
            movies = movieDao.countForSourceOnce(source.id),
            series = seriesDao.countForSourceOnce(source.id),
            epg = epg,
        )
    }

    /** Current content counts for a source (no EPG) — for the Playlists list rows. */
    suspend fun contentCounts(sourceId: Long): SyncCounts = SyncCounts(
        channels = channelDao.countForSourceOnce(sourceId),
        movies = movieDao.countForSourceOnce(sourceId),
        series = seriesDao.countForSourceOnce(sourceId),
    )

    /** Ensure an EPG source exists for the playlist's guide URL and (re)sync it; returns programmes. */
    private suspend fun ensureEpg(source: SourceEntity): Int {
        val url = epgRepository.guideUrl(source) ?: return 0
        val existing = epgSourceStore.getAll().firstOrNull { it.url.equals(url, ignoreCase = true) }
        val epgSource = existing ?: epgSourceStore.add("${source.name} EPG", url, source.userAgent)
        val now = System.currentTimeMillis()
        return runCatching { epgRepository.refreshUrl(epgSource.id, epgSource.url, epgSource.userAgent) }
            .onSuccess { epgSourceStore.setSynced(epgSource.id, now, null) }
            .onFailure { epgSourceStore.setSynced(epgSource.id, now, it.message) }
            .getOrDefault(0)
    }
}
