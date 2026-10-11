package tv.own.owntv.features.sport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Resolves an ESPN fixture to channels from the user's own playlists (Lume's EPG-based channel
 * matching): broadcast-name containment, team-name containment, and — strongest — an EPG
 * programme overlapping the fixture whose title names a team.
 */
class SportsMatcher(
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
) {

    suspend fun match(fixture: Fixture, limit: Int = 6): List<ChannelMatch> =        withContext(Dispatchers.IO) {
            val sourceIds = liveSourceIds()
            if (sourceIds.isEmpty()) return@withContext emptyList()
            val home = fixture.home
            val away = fixture.away
            val queries = linkedSetOf(
                home.abbreviation, away.abbreviation,
                home.name, away.name,
                home.name.firstWord(), away.name.firstWord(),
            ).filter { it.length >= 3 }
            val candidates = LinkedHashMap<Long, ChannelEntity>()
            for (query in queries) {
                runCatching { channelDao.searchList(query, sourceIds, MatchSearchLimit) }
                    .getOrNull()?.forEach { candidates.putIfAbsent(it.id, it) }
                if (candidates.size >= CandidateCap) break
            }
            for (broadcast in fixture.broadcasts) {
                val token = broadcast.longestWord(minLength = 4) ?: continue
                runCatching { channelDao.searchList(token, sourceIds, MatchSearchLimit) }
                    .getOrNull()?.forEach { candidates.putIfAbsent(it.id, it) }
                if (candidates.size >= CandidateCap) break
            }
            if (candidates.isEmpty()) return@withContext emptyList()
            val from = fixture.dateMs - EpgBeforeMs
            val to = fixture.dateMs + EpgAfterMs
            val scored = candidates.values.map { channel ->
                var score = 0
                var epgTitle: String? = null
                val name = channel.name.lowercase()
                if (name.contains(home.abbreviation.lowercase()) || name.contains(away.abbreviation.lowercase())) score += 2
                if (name.contains(home.name.lowercase()) || name.contains(away.name.lowercase())) score += 2
                val broadcastHit = fixture.broadcasts.any { broadcast ->
                    broadcast.words().any { word -> word.length >= 4 && name.contains(word.lowercase()) }
                }
                if (broadcastHit) score += 3
                val epgKey = channel.epgChannelId?.trim()?.lowercase()
                if (!epgKey.isNullOrBlank()) {
                    val programmes = runCatching { epgDao.programmesForChannel(epgKey, from, to) }.getOrNull().orEmpty()
                    val hit = programmes.firstOrNull { programme ->
                        val title = programme.title.lowercase()
                        (title.contains(home.name.lowercase()) || title.contains(away.name.lowercase()) ||
                            title.contains(home.abbreviation.lowercase()) || title.contains(away.abbreviation.lowercase())) &&
                            programme.startMs <= fixture.dateMs + EpgAfterMs &&
                            programme.stopMs >= fixture.dateMs - EpgBeforeMs
                    }
                    if (hit != null) {
                        score += 5
                        epgTitle = hit.title
                    }
                }
                ChannelMatch(channel.id, channel.name, channel.logoUrl, epgTitle, score) to score
            }.filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
            scored
        }

    /** Entities for matched ids, in the matches' order — the zap context for playback. */
    suspend fun channels(ids: List<Long>): List<ChannelEntity> =
        withContext(Dispatchers.IO) {
            ids.mapNotNull { runCatching { channelDao.getById(it) }.getOrNull() }
        }

    private suspend fun liveSourceIds(): List<Long> {        val pid = settings.activeProfileId.first()
        if (pid < 0) return emptyList()
        val sources = runCatching { sourceRepository.observeSources(pid).first() }.getOrNull().orEmpty()
        val defaultId = runCatching { settings.defaultSourceId.first() }.getOrDefault(-1L)
        val scoped = if (defaultId > 0) sources.filter { it.id == defaultId } else sources
        return scoped.filter { it.syncLive }.map { it.id }
    }

    private fun String.firstWord(): String = trim().split(WordSplit).firstOrNull().orEmpty()

    private fun String.words(): List<String> = trim().split(WordSplit).filter { it.isNotBlank() }

    private fun String.longestWord(minLength: Int): String? =
        words().maxByOrNull { it.length }?.takeIf { it.length >= minLength }

    companion object {
        private const val MatchSearchLimit = 20
        private const val CandidateCap = 60
        private const val EpgBeforeMs = 2L * 60L * 60L * 1000L
        private const val EpgAfterMs = 4L * 60L * 60L * 1000L
        private val WordSplit = Regex("\\s+")
    }
}
