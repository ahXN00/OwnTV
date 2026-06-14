package tv.own.owntv.core.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.EpgChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.parser.XmltvParser
import tv.own.owntv.core.parser.XtreamClient

/**
 * Fetches and stores the bulk XMLTV guide for a source (Xtream `xmltv.php`, or a M3U playlist's
 * `url-tvg`). Only programmes overlapping a rolling window are kept, and finished rows are pruned, so
 * the guide stays bounded. The grid reads from [EpgDao]; per-channel now/next still uses Xtream's
 * short-EPG separately.
 */
class EpgRepository(
    private val epgDao: EpgDao,
    private val http: HttpClient,
    private val xtream: XtreamClient,
) {
    /** The guide URL for a source, or null if it has no EPG feed. A manual EPG URL always wins. */
    fun guideUrl(source: SourceEntity): String? = when (source.type) {
        SourceType.XTREAM -> source.epgUrl?.takeIf { it.isNotBlank() } ?: xtream.xmltvUrl(source)
        SourceType.M3U -> source.epgUrl?.takeIf { it.isNotBlank() }
        SourceType.LOCAL_BACKUP -> null
    }

    fun hasGuide(source: SourceEntity): Boolean = guideUrl(source) != null

    /**
     * Refresh one playlist source's guide (used by the one-time migration). Returns programmes stored.
     */
    suspend fun refresh(source: SourceEntity): Int {
        val url = guideUrl(source) ?: return 0
        return refreshUrl(source.id, url, source.userAgent)
    }

    /**
     * Download [url] (XMLTV, gzip-aware) into the guide tables keyed by [storeId], keeping only the
     * rolling window. Used for standalone EPG sources (negative ids). Throws on network/parse failure.
     */
    suspend fun refreshUrl(storeId: Long, url: String, userAgent: String?): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val from = now - WINDOW_BACK_MS
        val to = now + WINDOW_AHEAD_MS

        epgDao.clearSource(storeId) // clear-then-insert (no unique key on programmes to REPLACE on)

        val channels = LinkedHashMap<String, EpgChannelEntity>()
        val buffer = ArrayList<EpgProgrammeEntity>(CHUNK)
        var stored = 0

        http.get(url, userAgent) { input ->
            XmltvParser.parse(
                input,
                onChannel = { id, name ->
                    // Ids are stored normalized (trim+lowercase) so guide lookups can use the
                    // (epgChannelId, startMs) index directly — XMLTV ids often differ from the
                    // panel's epg_channel_id only in case.
                    val key = id.trim().lowercase()
                    channels.getOrPut(key) { EpgChannelEntity(sourceId = storeId, epgChannelId = key, displayName = name) }
                },
                onProgramme = { channelId, startMs, stopMs, title, desc ->
                    if (stopMs > from && startMs < to) {
                        buffer.add(
                            EpgProgrammeEntity(
                                sourceId = storeId, epgChannelId = channelId.trim().lowercase(),
                                startMs = startMs, stopMs = stopMs, title = title, description = desc,
                            ),
                        )
                        if (buffer.size >= CHUNK) {
                            runBlocking { epgDao.upsertProgrammes(buffer.toList()) }
                            stored += buffer.size
                            buffer.clear()
                        }
                    }
                },
            )
        }
        if (buffer.isNotEmpty()) { epgDao.upsertProgrammes(buffer.toList()); stored += buffer.size }
        if (channels.isNotEmpty()) epgDao.upsertChannels(channels.values.toList())
        epgDao.prune(now - WINDOW_BACK_MS)
        stored
    }

    /** Drop a removed EPG source's stored programmes. */
    suspend fun clear(storeId: Long) = withContext(Dispatchers.IO) { epgDao.clearSource(storeId) }

    companion object {
        private const val WINDOW_BACK_MS = 2L * 60 * 60 * 1000 // keep 2h of just-aired
        private const val WINDOW_AHEAD_MS = 48L * 60 * 60 * 1000 // and 48h ahead
        private const val CHUNK = 500
    }
}
