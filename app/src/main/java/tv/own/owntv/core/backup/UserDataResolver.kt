package tv.own.owntv.core.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.PlaybackProgressEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType

private val Context.pendingStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_pending_userdata")
private val PENDING_KEY = stringPreferencesKey("entries")

/**
 * Backs up and restores the per-profile user data that lives on volatile content ids: favorites,
 * watch history, and resume positions. Content rows are clear-then-insert on every sync, so ids
 * can't be exported directly — instead each record is exported with a stable identity
 * (sourceId + provider remoteId, falling back to the name) and re-resolved against the content
 * tables AFTER the post-restore sync repopulates them. Unresolvable records stay pending and are
 * retried after every sync (and after a show's episodes load), so they heal as content appears.
 */
class UserDataResolver(
    private val context: Context,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
) {

    /** Exports the chosen kinds ("fav" / "his" / "prog") as stable-key records for the backup file. */
    suspend fun exportAll(kinds: Set<String> = setOf("fav", "his", "prog")): JSONArray {
        val out = JSONArray()
        if ("fav" in kinds) favoriteDao.getAllOnce().forEach { f ->
            describe(f.mediaType, f.itemId)?.let { out.put(it.put("p", f.profileId).put("kind", "fav").put("at", f.addedAt)) }
        }
        if ("his" in kinds) historyDao.getAllOnce().forEach { h ->
            describe(h.mediaType, h.itemId)?.let { out.put(it.put("p", h.profileId).put("kind", "his").put("at", h.watchedAt)) }
        }
        if ("prog" in kinds) progressDao.getAllOnce().forEach { pr ->
            describe(pr.mediaType, pr.itemId)?.let {
                out.put(it.put("p", pr.profileId).put("kind", "prog").put("at", pr.updatedAt).put("pos", pr.positionMs).put("dur", pr.durationMs))
            }
        }
        return out
    }

    /**
     * Heals favorites/history/resume across a source re-sync. Content rows are clear-then-insert, so
     * their ids change every refresh and the user-data rows (keyed on the old ids) orphan — the count
     * badge still showed them, but the join returned nothing. Capture [exportAll] BEFORE the sync (ids
     * still valid → stable keys), then call this AFTER it: re-resolve each record to the new ids, purge
     * the now-orphaned rows so counts and lists agree, and keep anything still unresolvable (e.g.
     * not-yet-loaded episodes) pending for a later sync / show-open.
     */
    suspend fun relinkAfterSync(snapshot: JSONArray) {
        val unresolved = JSONArray()
        for (i in 0 until snapshot.length()) {
            val e = snapshot.getJSONObject(i)
            val ok = runCatching { resolveAndInsert(e) }.getOrDefault(false)
            if (!ok) unresolved.put(e)
        }
        favoriteDao.purgeOrphans()
        historyDao.purgeOrphans()
        progressDao.purgeOrphans()
        if (unresolved.length() > 0) addPending(unresolved)
        resolvePending() // also retries any in-flight backup restore
    }

    /** Appends records to the pending set (de-duplicated by content), so they heal on a later resolve. */
    private suspend fun addPending(extra: JSONArray) {
        context.pendingStore.edit { prefs ->
            val existing = prefs[PENDING_KEY]?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
            val seen = HashSet<String>()
            for (i in 0 until existing.length()) seen.add(existing.getJSONObject(i).toString())
            for (i in 0 until extra.length()) {
                val s = extra.getJSONObject(i).toString()
                if (seen.add(s)) existing.put(extra.getJSONObject(i))
            }
            prefs[PENDING_KEY] = existing.toString()
        }
    }

    /** Replaces the pending set with a backup's records (empty/absent clears), then tries resolving. */
    suspend fun importAll(entries: JSONArray?) {
        context.pendingStore.edit { prefs ->
            if (entries == null || entries.length() == 0) prefs.remove(PENDING_KEY)
            else prefs[PENDING_KEY] = entries.toString()
        }
        resolvePending()
    }

    /**
     * Tries to attach pending records to current content rows. Called after every successful source
     * sync and after a show's episodes load; resolved records are inserted (idempotently — the user
     * data tables have unique (profile, type, item) indices) and removed from the pending set.
     */
    suspend fun resolvePending() {
        val raw = context.pendingStore.data.first()[PENDING_KEY] ?: return
        val entries = runCatching { JSONArray(raw) }.getOrNull() ?: return
        if (entries.length() == 0) return

        val remaining = JSONArray()
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val resolved = runCatching { resolveAndInsert(e) }.getOrDefault(false)
            if (!resolved) remaining.put(e)
        }
        context.pendingStore.edit { prefs ->
            if (remaining.length() == 0) prefs.remove(PENDING_KEY) else prefs[PENDING_KEY] = remaining.toString()
        }
    }

    // --- export side: content row → stable identity ---

    private suspend fun describe(type: MediaType, itemId: Long): JSONObject? = when (type) {
        MediaType.LIVE -> channelDao.getById(itemId)?.let {
            JSONObject().put("t", type.name).put("src", it.sourceId).putOpt("rid", it.remoteId).put("name", it.name)
        }
        MediaType.MOVIE -> movieDao.getById(itemId)?.let {
            JSONObject().put("t", type.name).put("src", it.sourceId).putOpt("rid", it.remoteId).put("name", it.name)
        }
        MediaType.SERIES -> seriesDao.getSeriesById(itemId)?.let {
            JSONObject().put("t", type.name).put("src", it.sourceId).putOpt("rid", it.remoteId).put("name", it.name)
        }
        MediaType.EPISODE -> {
            val ep = seriesDao.getEpisodeById(itemId) ?: return null
            val show = seriesDao.getSeriesById(ep.seriesId) ?: return null
            JSONObject().put("t", type.name).put("src", show.sourceId)
                .putOpt("srid", show.remoteId).put("sname", show.name)
                .putOpt("rid", ep.remoteId).put("season", ep.seasonNumber).put("ep", ep.episodeNumber)
        }
    }

    // --- restore side: stable identity → current content row ---

    private suspend fun resolveAndInsert(e: JSONObject): Boolean {
        val type = runCatching { MediaType.valueOf(e.getString("t")) }.getOrNull() ?: return true // drop garbage
        val src = e.getLong("src")
        val rid = e.optStringOrNull("rid")
        val itemId: Long = when (type) {
            MediaType.LIVE -> (rid?.let { channelDao.findByRemote(src, it) } ?: channelDao.findByName(src, e.getString("name")))?.id
            MediaType.MOVIE -> (rid?.let { movieDao.findByRemote(src, it) } ?: movieDao.findByName(src, e.getString("name")))?.id
            MediaType.SERIES -> (rid?.let { seriesDao.findSeriesByRemote(src, it) } ?: seriesDao.findSeriesByName(src, e.getString("name")))?.id
            MediaType.EPISODE -> {
                val srid = e.optStringOrNull("srid")
                val show = (srid?.let { seriesDao.findSeriesByRemote(src, it) } ?: seriesDao.findSeriesByName(src, e.getString("sname")))
                    ?: return false
                (rid?.let { seriesDao.findEpisodeByRemote(show.id, it) }
                    ?: seriesDao.findEpisodeByNumber(show.id, e.getInt("season"), e.getInt("ep")))?.id
            }
        } ?: return false

        val pid = e.getLong("p")
        val at = e.optLong("at", System.currentTimeMillis())
        when (e.getString("kind")) {
            "fav" -> favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = type, itemId = itemId, addedAt = at))
            "his" -> historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = type, itemId = itemId, watchedAt = at))
            "prog" -> progressDao.save(
                PlaybackProgressEntity(
                    profileId = pid, mediaType = type, itemId = itemId,
                    positionMs = e.optLong("pos", 0), durationMs = e.optLong("dur", 0), updatedAt = at,
                ),
            )
        }
        return true
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
