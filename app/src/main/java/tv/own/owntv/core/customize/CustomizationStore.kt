package tv.own.owntv.core.customize

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.model.MediaType

private val Context.customizeStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_customizations")

/**
 * Stable identity for a category/channel that survives re-sync (content rows are clear-then-insert,
 * so DB ids change every refresh). Xtream rows key on the provider's remote id; M3U rows (no stable
 * id) fall back to the name — so a provider-side rename can detach an M3U customization.
 */
object CustomizeKeys {
    fun category(c: CategoryEntity): String = "${c.sourceId}:${c.remoteId ?: c.name}"
    fun channel(ch: ChannelEntity): String = "${ch.sourceId}:${ch.remoteId ?: ch.name}"
}

/** One browse section's customizations (categories + items) for a profile. */
data class SectionCustomizations(
    val hiddenCategories: Set<String> = emptySet(),
    /** Hidden channels/items: stable key → display label (so the unhide list can show a name). */
    val hiddenItems: Map<String, String> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(),
    val itemNames: Map<String, String> = emptyMap(),
    /** Explicit category order (keys, first = top). Categories not listed follow in natural order. */
    val categoryOrder: List<String> = emptyList(),
    /** Manual EPG match: item key → the EPG channel id to use (overrides the channel's own epg id). */
    val epgMatches: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = hiddenCategories.isEmpty() && hiddenItems.isEmpty() && categoryNames.isEmpty() &&
            itemNames.isEmpty() && categoryOrder.isEmpty() && epgMatches.isEmpty()
}

/**
 * Per-profile content customizations (TiviMate-style hide / rename / reorder), stored as JSON in
 * DataStore — deliberately NOT a Room table, so no schema change (the DB uses destructive
 * migrations) and edits survive every re-sync via [CustomizeKeys].
 */
class CustomizationStore(private val context: Context) {

    private fun key(profileId: Long, type: MediaType) =
        stringPreferencesKey("cust_${profileId}_${type.name}")

    fun observe(profileId: Long, type: MediaType): Flow<SectionCustomizations> =
        context.customizeStore.data
            .map { prefs -> parse(prefs[key(profileId, type)]) }
            .distinctUntilChanged()

    suspend fun update(profileId: Long, type: MediaType, transform: (SectionCustomizations) -> SectionCustomizations) {
        context.customizeStore.edit { prefs ->
            val k = key(profileId, type)
            val next = transform(parse(prefs[k]))
            if (next.isEmpty) prefs.remove(k) else prefs[k] = serialize(next)
        }
    }

    // --- convenience mutations ---

    suspend fun setCategoryHidden(profileId: Long, type: MediaType, catKey: String, hidden: Boolean) =
        update(profileId, type) {
            it.copy(hiddenCategories = if (hidden) it.hiddenCategories + catKey else it.hiddenCategories - catKey)
        }

    /** Hide/show a whole span of categories in one atomic edit (range select in Customize). */
    suspend fun setCategoriesHidden(profileId: Long, type: MediaType, catKeys: Collection<String>, hidden: Boolean) =
        update(profileId, type) {
            it.copy(hiddenCategories = if (hidden) it.hiddenCategories + catKeys else it.hiddenCategories - catKeys)
        }

    suspend fun setItemHidden(profileId: Long, type: MediaType, itemKey: String, label: String, hidden: Boolean) =
        update(profileId, type) {
            it.copy(hiddenItems = if (hidden) it.hiddenItems + (itemKey to label) else it.hiddenItems - itemKey)
        }

    suspend fun renameCategory(profileId: Long, type: MediaType, catKey: String, name: String?) =
        update(profileId, type) {
            it.copy(categoryNames = if (name.isNullOrBlank()) it.categoryNames - catKey else it.categoryNames + (catKey to name.trim()))
        }

    suspend fun renameItem(profileId: Long, type: MediaType, itemKey: String, name: String?) =
        update(profileId, type) {
            it.copy(itemNames = if (name.isNullOrBlank()) it.itemNames - itemKey else it.itemNames + (itemKey to name.trim()))
        }

    suspend fun setCategoryOrder(profileId: Long, type: MediaType, orderedKeys: List<String>) =
        update(profileId, type) { it.copy(categoryOrder = orderedKeys) }

    /** Manually map an item to an EPG channel id (null/blank clears the override → auto-match). */
    suspend fun setEpgMatch(profileId: Long, type: MediaType, itemKey: String, epgChannelId: String?) =
        update(profileId, type) {
            val key = epgChannelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            it.copy(epgMatches = if (key == null) it.epgMatches - itemKey else it.epgMatches + (itemKey to key))
        }

    // --- backup & restore (profile/source ids are preserved by BackupManager, so keys stay valid) ---

    /** All raw customization entries (preference key → JSON) for embedding into a backup file. */
    suspend fun exportAll(): Map<String, String> =
        context.customizeStore.data.first().asMap()
            .mapNotNull { (k, v) -> (v as? String)?.let { k.name to it } }
            .toMap()

    /** Replaces all customizations with the backup's entries (empty map = clear everything). */
    suspend fun importAll(entries: Map<String, String>) {
        context.customizeStore.edit { prefs ->
            prefs.clear()
            entries.forEach { (k, v) -> if (k.startsWith("cust_")) prefs[stringPreferencesKey(k)] = v }
        }
    }

    // --- JSON (org.json, matching BackupManager's style) ---

    private fun parse(raw: String?): SectionCustomizations {
        if (raw.isNullOrBlank()) return SectionCustomizations()
        return runCatching {
            val o = JSONObject(raw)
            SectionCustomizations(
                hiddenCategories = o.optJSONArray("hiddenCats").toStringSet(),
                hiddenItems = o.optJSONObject("hiddenItems").toStringMap(),
                categoryNames = o.optJSONObject("catNames").toStringMap(),
                itemNames = o.optJSONObject("itemNames").toStringMap(),
                categoryOrder = o.optJSONArray("catOrder").toStringList(),
                epgMatches = o.optJSONObject("epgMatch").toStringMap(),
            )
        }.getOrDefault(SectionCustomizations())
    }

    private fun serialize(c: SectionCustomizations): String = JSONObject().apply {
        put("hiddenCats", JSONArray(c.hiddenCategories.toList()))
        put("hiddenItems", JSONObject(c.hiddenItems as Map<*, *>))
        put("catNames", JSONObject(c.categoryNames as Map<*, *>))
        put("itemNames", JSONObject(c.itemNames as Map<*, *>))
        put("catOrder", JSONArray(c.categoryOrder))
        put("epgMatch", JSONObject(c.epgMatches as Map<*, *>))
    }.toString()

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun JSONArray?.toStringSet(): Set<String> = toStringList().toSet()

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val out = HashMap<String, String>()
        keys().forEach { k -> out[k] = getString(k) }
        return out
    }
}

/**
 * Applies category customizations: hidden categories drop out, custom names replace the originals,
 * and reordered keys come first (in their stored order) with the rest keeping natural order.
 */
fun List<CategoryEntity>.applyCustomizations(c: SectionCustomizations): List<Pair<CategoryEntity, String>> {
    if (c.isEmpty) return map { it to it.name }
    val visible = filter { CustomizeKeys.category(it) !in c.hiddenCategories }
    val orderIndex = c.categoryOrder.withIndex().associate { (i, k) -> k to i }
    val (pinned, rest) = visible.partition { CustomizeKeys.category(it) in orderIndex }
    val sorted = pinned.sortedBy { orderIndex.getValue(CustomizeKeys.category(it)) } + rest
    return sorted.map { cat -> cat to (c.categoryNames[CustomizeKeys.category(cat)] ?: cat.name) }
}
