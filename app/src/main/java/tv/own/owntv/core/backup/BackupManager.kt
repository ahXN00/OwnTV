package tv.own.owntv.core.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.features.settings.data.SettingsRepository

/**
 * Phase 12 — backup & restore of the painful-to-re-enter setup: **profiles** (name/avatar/kids/PIN),
 * **sources** (URLs + credentials + per-source UA) and their profile links, plus per-profile
 * **customizations** (hidden/renamed/reordered categories & channels), favorites, watch history and
 * resume positions — as a JSON file. The user picks which [Section]s to include on export and which
 * to apply on restore. Content (channels/movies/series) is NOT backed up — it's large and re-syncs
 * from the sources after restore. Profile/source ids are preserved on restore, so customization keys
 * stay valid.
 */
class BackupManager(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val userData: UserDataResolver,
    private val epgSources: tv.own.owntv.core.epg.EpgSourceStore,
) {
    /** What a backup can contain; the user multi-selects these for export and restore. */
    enum class Section(val label: String, val desc: String) {
        SOURCES("Profiles & sources", "Viewers, PINs, playlists, EPG feeds and credentials"),
        CUSTOMIZE("Customizations", "Hidden/renamed/reordered categories, channels & EPG matches"),
        FAVORITES("Favorites", "Starred channels, movies and series"),
        HISTORY("Watch history", "Recently watched lists"),
        RESUME("Resume positions", "Where you stopped in movies & episodes"),
        SETTINGS("App settings", "Theme, accent, player & layout preferences"),
    }

    /** Writes the chosen [sections] into [folder] as owntv-backup.json; returns the file path. */
    suspend fun export(folder: File, sections: Set<Section> = Section.entries.toSet()): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject().apply {
                put("version", 5)
                put("sections", JSONArray().apply { sections.forEach { put(it.name) } })
                if (Section.SOURCES in sections) {
                    put("profiles", JSONArray().apply { profileDao.getAllOnce().forEach { put(profileJson(it)) } })
                    put("sources", JSONArray().apply { sourceDao.getAllOnce().forEach { put(sourceJson(it)) } })
                    put("links", JSONArray().apply { sourceDao.allLinks().forEach { put(JSONObject().put("profileId", it.profileId).put("sourceId", it.sourceId)) } })
                    put("epgSources", epgSources.exportJson()) // standalone EPG feeds ride with sources
                }
                if (Section.CUSTOMIZE in sections) {
                    put("customizations", JSONObject().apply { customize.exportAll().forEach { (k, v) -> put(k, v) } })
                }
                // Favorites / history / resume positions, exported with stable keys (see UserDataResolver).
                val kinds = kindsFor(sections)
                if (kinds.isNotEmpty()) put("userData", userData.exportAll(kinds))
                if (Section.SETTINGS in sections) put("settings", settings.exportSettings())
            }
            if (!folder.exists()) folder.mkdirs()
            val out = File(folder, "owntv-backup.json")
            out.writeText(root.toString(2))
            out.absolutePath
        }
    }

    /** Which sections a backup file actually contains (older files have no "sections" field). */
    suspend fun sectionsIn(file: File): Result<Set<Section>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(file.readText())
            val out = mutableSetOf<Section>()
            if (root.has("profiles") || root.has("sources")) out += Section.SOURCES
            if (root.optJSONObject("customizations")?.keys()?.hasNext() == true) out += Section.CUSTOMIZE
            if (root.optJSONObject("settings")?.keys()?.hasNext() == true) out += Section.SETTINGS
            root.optJSONArray("userData")?.let { arr ->
                for (i in 0 until arr.length()) {
                    when (arr.getJSONObject(i).optString("kind")) {
                        "fav" -> out += Section.FAVORITES
                        "his" -> out += Section.HISTORY
                        "prog" -> out += Section.RESUME
                    }
                }
            }
            if (out.isEmpty()) error("Not an OwnTV backup file")
            out
        }
    }

    /** Applies the chosen [sections] of the file (only those it actually contains). */
    suspend fun import(file: File, sections: Set<Section> = Section.entries.toSet()): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(file.readText())
            var count = 0

            if (Section.SOURCES in sections && (root.has("profiles") || root.has("sources"))) {
                val profiles = root.optJSONArray("profiles") ?: JSONArray()
                val sources = root.optJSONArray("sources") ?: JSONArray()
                val links = root.optJSONArray("links") ?: JSONArray()

                profileDao.deleteAll()       // cascades favorites/history/progress/profile_source
                sourceDao.deleteAllSources() // cascades content + profile_source

                for (i in 0 until profiles.length()) profileDao.insert(profileFrom(profiles.getJSONObject(i)))
                for (i in 0 until sources.length()) sourceDao.insert(sourceFrom(sources.getJSONObject(i)))
                for (i in 0 until links.length()) {
                    val l = links.getJSONObject(i)
                    sourceDao.link(ProfileSourceCrossRef(profileId = l.getLong("profileId"), sourceId = l.getLong("sourceId")))
                }
                epgSources.importJson(root.optString("epgSources").takeIf { it.isNotBlank() })
                profileDao.getAllOnce().firstOrNull()?.let { settings.setActiveProfile(it.id) }
                count += profiles.length() + sources.length()
            }

            if (Section.CUSTOMIZE in sections) {
                root.optJSONObject("customizations")?.let { o ->
                    val cust = HashMap<String, String>()
                    o.keys().forEach { k -> cust[k] = o.getString(k) }
                    customize.importAll(cust)
                    count += cust.size
                }
            }

            // Favorites/history/progress: stashed as pending records — they attach automatically as
            // the post-restore syncs repopulate the content tables (UserDataResolver.resolvePending).
            val kinds = kindsFor(sections)
            if (kinds.isNotEmpty()) {
                root.optJSONArray("userData")?.let { arr ->
                    val filtered = JSONArray()
                    for (i in 0 until arr.length()) {
                        val e = arr.getJSONObject(i)
                        if (e.optString("kind") in kinds) filtered.put(e)
                    }
                    userData.importAll(filtered)
                    count += filtered.length()
                }
            }

            if (Section.SETTINGS in sections) {
                root.optJSONObject("settings")?.let { settings.importSettings(it); count += it.length() }
            }
            count
        }
    }

    private fun kindsFor(sections: Set<Section>): Set<String> = buildSet {
        if (Section.FAVORITES in sections) add("fav")
        if (Section.HISTORY in sections) add("his")
        if (Section.RESUME in sections) add("prog")
    }

    // --- mapping ---
    private fun profileJson(p: ProfileEntity) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("avatarColor", p.avatarColor); put("avatarId", p.avatarId)
        put("isKids", p.isKids); put("pinHash", p.pinHash ?: JSONObject.NULL); put("createdAt", p.createdAt)
    }

    private fun profileFrom(o: JSONObject) = ProfileEntity(
        id = o.getLong("id"), name = o.getString("name"), avatarColor = o.getInt("avatarColor"),
        avatarId = o.optInt("avatarId", 0), isKids = o.optBoolean("isKids", false),
        pinHash = o.optStringOrNull("pinHash"), createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    private fun sourceJson(s: SourceEntity) = JSONObject().apply {
        put("id", s.id); put("name", s.name); put("type", s.type.name); put("url", s.url)
        put("username", s.username ?: JSONObject.NULL); put("password", s.password ?: JSONObject.NULL)
        put("userAgent", s.userAgent ?: JSONObject.NULL); put("epgUrl", s.epgUrl ?: JSONObject.NULL)
        put("createdAt", s.createdAt); put("lastSyncAt", s.lastSyncAt ?: JSONObject.NULL)
    }

    private fun sourceFrom(o: JSONObject) = SourceEntity(
        id = o.getLong("id"), name = o.getString("name"),
        type = runCatching { SourceType.valueOf(o.getString("type")) }.getOrDefault(SourceType.M3U),
        url = o.getString("url"), username = o.optStringOrNull("username"), password = o.optStringOrNull("password"),
        userAgent = o.optStringOrNull("userAgent"), epgUrl = o.optStringOrNull("epgUrl"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        lastSyncAt = if (o.isNull("lastSyncAt")) null else o.optLong("lastSyncAt"),
    )
}

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
