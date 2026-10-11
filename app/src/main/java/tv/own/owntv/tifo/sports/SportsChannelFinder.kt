package tv.own.owntv.tifo.sports

import androidx.room.useReaderConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/** A channel that probably shows the event, and why we think so. */
data class SportsChannelMatch(
    val channel: ChannelEntity,
    val score: Int,
    /** The guide programme that matched, or null for a match on the channel's name alone. */
    val programmeTitle: String?,
    val programmeStartMs: Long?,
    val programmeStopMs: Long?,
)

/**
 * Finds the active playlist's channels that show an event: through the guide (a programme at the
 * event's time whose title names the teams or the event), through channels named after the game
 * itself ("NFL 03: Bears vs Packers"), through the game's own broadcaster (ranked first: ESPN's
 * listing for a US league, the home country's broadcaster otherwise), then through the
 * competition's usual broadcasters.
 *
 * Reads core's database and never writes to it. The two queries name core's tables and columns
 * directly, since core has no DAO call that searches programme titles.
 */
class SportsChannelFinder(
    private val db: OwnTVDatabase,
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
) {

    suspend fun find(event: SportsEvent, nowMs: Long): List<SportsChannelMatch> = withContext(Dispatchers.IO) {
        val sourceIds = activeProfileSources(settings, sourceDao).first().liveSourceIds
        if (sourceIds.isEmpty()) return@withContext emptyList()

        val best = HashMap<Long, SportsChannelMatch>()
        fun offer(match: SportsChannelMatch) {
            if (SportsMatching.isSeparator(match.channel.name)) return
            val old = best[match.channel.id]
            if (old == null) {
                best[match.channel.id] = match
            } else if (match.score > old.score) {
                // Keep the guide programme that matched, if this better match has none.
                best[match.channel.id] = if (match.programmeTitle == null && old.programmeTitle != null) {
                    match.copy(programmeTitle = old.programmeTitle, programmeStartMs = old.programmeStartMs, programmeStopMs = old.programmeStopMs)
                } else {
                    match
                }
            }
        }

        // 1. The guide.
        val (from, to) = window(event, nowMs)
        val programmes = programmesMatching(SportsMatching.searchTerms(event), from, to)
            .mapNotNull { p ->
                val score = SportsMatching.score(event, p.title, p.categories)
                if (score > 0) p to score else null
            }
        if (programmes.isNotEmpty()) {
            val byKey = programmes.groupBy { it.first.epgKey }
            for ((channelId, epgKey) in channelsForGuideKeys(byKey.keys, sourceIds)) {
                val channel = channelDao.getById(channelId) ?: continue
                val (programme, score) = byKey[epgKey]?.maxByOrNull { it.second } ?: continue
                offer(SportsChannelMatch(channel, score, programme.title, programme.startMs, programme.stopMs))
            }
        }

        // 2. Channels named after the game, which providers often leave without a guide.
        for ((channelId, name) in channelsNamed(SportsMatching.channelSearchTerms(event), sourceIds)) {
            val score = SportsMatching.channelScore(event, name)
            if (score <= 0) continue
            val channel = channelDao.getById(channelId) ?: continue
            offer(SportsChannelMatch(channel, score, null, null, null))
        }

        // 3. The game's own broadcaster, above everything else: the channel ESPN lists for a US
        // league, the home country's broadcaster for the others. A channel whose guide also shows
        // the game goes above the rest of that broadcaster's channels.
        val competition = event.competition
        val firstBroadcasters = if (competition.usesEspnBroadcasts) event.broadcasts else competition.homeBroadcasters
        for (name in firstBroadcasters.distinctBy { it.lowercase() }) {
            channelsNamed(SportsMatching.broadcasterSearchTerms(name), sourceIds)
                .filter { (_, channelName) ->
                    !SportsMatching.isSeparator(channelName) && SportsMatching.broadcasterMatches(channelName, name)
                }
                .sortedBy { (_, channelName) -> channelName.length }
                .take(LISTED_CHANNELS)
                .forEach { (channelId, _) ->
                    val confirmed = (best[channelId]?.score ?: 0) >= SportsMatching.BEST_MATCH
                    val score = if (confirmed) SportsMatching.LISTED_AND_SHOWING else SportsMatching.LISTED_BROADCASTER
                    channelDao.getById(channelId)?.let { offer(SportsChannelMatch(it, score, null, null, null)) }
                }
        }

        // 4. The competition's usual broadcasters, below anything else found.
        val broadcasterNames = event.competition.broadcasters.distinctBy { it.lowercase() }
        for (name in broadcasterNames) {
            runCatching { channelDao.searchList(name, sourceIds, BROADCASTER_CHANNELS) }.getOrDefault(emptyList())
                .forEach { offer(SportsChannelMatch(it, SportsMatching.BROADCASTER, null, null, null)) }
        }

        best.values
            .sortedWith(compareByDescending<SportsChannelMatch> { it.score }.thenBy { it.channel.name.lowercase() })
            .take(MAX_RESULTS)
    }

    /** The guide window in which a programme can show the event. */
    private fun window(event: SportsEvent, nowMs: Long): Pair<Long, Long> = when {
        // A tournament runs for days: whatever airs in the next twelve hours (or from its first day).
        event.competition.kind == SportKind.TOURNAMENT -> {
            val start = maxOf(nowMs, event.startMs)
            start to start + 12 * HOUR
        }
        // A programme that covers the start of the event, or the event so far if it is under way.
        else -> event.startMs + 10 * MINUTE to event.startMs + HOUR
    }

    private data class Programme(val epgKey: String, val title: String, val categories: String?, val startMs: Long, val stopMs: Long)

    /** Programmes airing over [from]..[to] whose title holds any of [terms]. */
    private suspend fun programmesMatching(terms: List<String>, from: Long, to: Long): List<Programme> {
        if (terms.isEmpty()) return emptyList()
        val likes = terms.joinToString(" OR ") { "title LIKE ?" }
        val sql = "SELECT epgChannelId, title, categories, startMs, stopMs FROM epg_programmes " +
            "WHERE startMs <= ? AND stopMs >= ? AND ($likes) LIMIT $MAX_PROGRAMMES"
        return runCatching {
            db.useReaderConnection { connection ->
                connection.usePrepared(sql) { st ->
                    st.bindLong(1, to)
                    st.bindLong(2, from)
                    terms.forEachIndexed { i, term -> st.bindText(i + 3, "%" + term + "%") }
                    buildList {
                        while (st.step()) {
                            add(
                                Programme(
                                    epgKey = st.getText(0).trim().lowercase(),
                                    title = st.getText(1),
                                    categories = if (st.isNull(2)) null else st.getText(2),
                                    startMs = st.getLong(3),
                                    stopMs = st.getLong(4),
                                ),
                            )
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Id and name of the playlist channels whose name holds any of [terms]. */
    private suspend fun channelsNamed(terms: List<String>, sourceIds: List<Long>): List<Pair<Long, String>> {
        if (terms.isEmpty()) return emptyList()
        val sql = "SELECT id, name FROM channels " +
            "WHERE sourceId IN (" + sourceIds.joinToString(",") { "?" } + ") " +
            "AND (" + terms.joinToString(" OR ") { "name LIKE ?" } + ") LIMIT $MAX_NAMED_CHANNELS"
        return runCatching {
            db.useReaderConnection { connection ->
                connection.usePrepared(sql) { st ->
                    sourceIds.forEachIndexed { i, id -> st.bindLong(i + 1, id) }
                    terms.forEachIndexed { i, term -> st.bindText(sourceIds.size + i + 1, "%" + term + "%") }
                    buildList { while (st.step()) add(st.getLong(0) to st.getText(1)) }
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Channel id to guide key, for the playlist channels whose guide id is one of [keys]. */
    private suspend fun channelsForGuideKeys(keys: Set<String>, sourceIds: List<Long>): List<Pair<Long, String>> {
        if (keys.isEmpty()) return emptyList()
        val keyList = keys.toList()
        val sql = "SELECT id, LOWER(TRIM(epgChannelId)) FROM channels " +
            "WHERE sourceId IN (" + sourceIds.joinToString(",") { "?" } + ") " +
            "AND LOWER(TRIM(epgChannelId)) IN (" + keyList.joinToString(",") { "?" } + ") LIMIT $MAX_RESULTS"
        return runCatching {
            db.useReaderConnection { connection ->
                connection.usePrepared(sql) { st ->
                    sourceIds.forEachIndexed { i, id -> st.bindLong(i + 1, id) }
                    keyList.forEachIndexed { i, key -> st.bindText(sourceIds.size + i + 1, key) }
                    buildList { while (st.step()) add(st.getLong(0) to st.getText(1)) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val MAX_PROGRAMMES = 400
        const val MAX_RESULTS = 40
        const val MAX_NAMED_CHANNELS = 400

        /** Per broadcaster, so "CBS" doesn't list every local affiliate. */
        const val LISTED_CHANNELS = 6
        const val BROADCASTER_CHANNELS = 8
    }
}
