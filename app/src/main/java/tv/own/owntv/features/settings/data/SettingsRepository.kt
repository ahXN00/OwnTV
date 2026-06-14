package tv.own.owntv.features.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import tv.own.owntv.ui.theme.AccentColor
import tv.own.owntv.ui.theme.ThemeMode
import tv.own.owntv.ui.theme.UiZoom

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_settings")

/**
 * Persists app-level preferences. Phase 1 only needs the theme selection; this will grow to hold
 * UI zoom, custom user-agent, refresh-on-start, etc. in later phases.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val UI_ZOOM_PCT = intPreferencesKey("ui_zoom_percent")
        val ACCENT = stringPreferencesKey("accent_color")
        val ACCENT_CUSTOM = stringPreferencesKey("accent_custom")
        val AVATAR_ID = intPreferencesKey("avatar_id")
        val ACTIVE_PROFILE = longPreferencesKey("active_profile_id")
        val DEFAULT_SOURCE = longPreferencesKey("default_source_id")
        val DOWNLOAD_ROOT = stringPreferencesKey("download_root")
        val REFRESH_SOURCE_IDS = stringSetPreferencesKey("refresh_source_ids")
        val LIVE_PREVIEW = booleanPreferencesKey("live_preview")
        val LIVE_PREVIEW_AUDIO = booleanPreferencesKey("live_preview_audio")
        val HDR_ENABLED = booleanPreferencesKey("hdr_enabled")
        // Video Player Settings
        val HW_DECODING = booleanPreferencesKey("hw_decoding")
        val DEFAULT_ZOOM = stringPreferencesKey("default_zoom")
        val SUB_SCALE = floatPreferencesKey("sub_scale")
        val AUDIO_DELAY_MS = intPreferencesKey("audio_delay_ms")
        val PREF_AUDIO_LANG = stringPreferencesKey("pref_audio_lang")
        val PREF_SUB_LANG = stringPreferencesKey("pref_sub_lang")
        // Per-section list sorting ("PLAYLIST" or "ALPHA")
        val SORT_LIVE = stringPreferencesKey("sort_live")
        val SORT_MOVIES = stringPreferencesKey("sort_movies")
        val SORT_SERIES = stringPreferencesKey("sort_series")
        val RESUME_MODE = stringPreferencesKey("resume_mode")
        val UPDATE_CHECK_ON_START = booleanPreferencesKey("update_check_on_start")
    }

    /** Automatically check GitHub Releases for a newer version shortly after launch. */
    val updateCheckOnStart: Flow<Boolean> = context.dataStore.data.map { it[Keys.UPDATE_CHECK_ON_START] ?: true }

    suspend fun setUpdateCheckOnStart(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UPDATE_CHECK_ON_START] = enabled }
    }

    // --- Resume behavior for movies/episodes with a saved position ---

    enum class ResumeMode(val label: String) {
        AUTO("Always resume"), ASK("Ask to resume"), NEVER("Never resume")
    }

    val resumeMode: Flow<ResumeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.RESUME_MODE]?.let { runCatching { ResumeMode.valueOf(it) }.getOrNull() } ?: ResumeMode.ASK
    }

    suspend fun setResumeMode(mode: ResumeMode) {
        context.dataStore.edit { it[Keys.RESUME_MODE] = mode.name }
    }

    // --- List sorting (per browse section) ---

    /** How a browse section's lists are ordered. */
    enum class SortMode { PLAYLIST, ALPHA }

    /** Live TV defaults to the playlist's own order; Movies/Series default to A–Z. */
    val sortLive: Flow<SortMode> = context.dataStore.data.map { parseSort(it[Keys.SORT_LIVE], SortMode.PLAYLIST) }
    val sortMovies: Flow<SortMode> = context.dataStore.data.map { parseSort(it[Keys.SORT_MOVIES], SortMode.ALPHA) }
    val sortSeries: Flow<SortMode> = context.dataStore.data.map { parseSort(it[Keys.SORT_SERIES], SortMode.ALPHA) }

    suspend fun setSortLive(mode: SortMode) {
        context.dataStore.edit { it[Keys.SORT_LIVE] = mode.name }
    }

    suspend fun setSortMovies(mode: SortMode) {
        context.dataStore.edit { it[Keys.SORT_MOVIES] = mode.name }
    }

    suspend fun setSortSeries(mode: SortMode) {
        context.dataStore.edit { it[Keys.SORT_SERIES] = mode.name }
    }

    private fun parseSort(raw: String?, default: SortMode): SortMode =
        raw?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: default

    // --- Video Player Settings ---

    /** Hardware decoding (mpv hwdec auto-safe). Off = force software decoding for tricky streams. */
    val hwDecoding: Flow<Boolean> = context.dataStore.data.map { it[Keys.HW_DECODING] ?: true }

    suspend fun setHwDecoding(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HW_DECODING] = enabled }
    }

    /** Default zoom/aspect mode applied when playback starts (a [tv.own.owntv.player.ZoomMode] name). */
    val defaultZoom: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_ZOOM] ?: "FIT" }

    suspend fun setDefaultZoom(name: String) {
        context.dataStore.edit { it[Keys.DEFAULT_ZOOM] = name }
    }

    /** Subtitle scale multiplier (mpv sub-scale); 1.0 = normal. */
    val subtitleScale: Flow<Float> = context.dataStore.data.map { it[Keys.SUB_SCALE] ?: 1.0f }

    suspend fun setSubtitleScale(scale: Float) {
        context.dataStore.edit { it[Keys.SUB_SCALE] = scale }
    }

    /** Audio sync offset in milliseconds (mpv audio-delay); +ve delays audio. */
    val audioDelayMs: Flow<Int> = context.dataStore.data.map { it[Keys.AUDIO_DELAY_MS] ?: 0 }

    suspend fun setAudioDelayMs(ms: Int) {
        context.dataStore.edit { it[Keys.AUDIO_DELAY_MS] = ms }
    }

    /** Preferred audio language (ISO code, mpv alang); blank = no preference. */
    val preferredAudioLang: Flow<String> = context.dataStore.data.map { it[Keys.PREF_AUDIO_LANG] ?: "" }

    suspend fun setPreferredAudioLang(lang: String) {
        context.dataStore.edit { it[Keys.PREF_AUDIO_LANG] = lang }
    }

    /** Preferred subtitle language (ISO code, mpv slang); blank = no preference. */
    val preferredSubLang: Flow<String> = context.dataStore.data.map { it[Keys.PREF_SUB_LANG] ?: "" }

    suspend fun setPreferredSubLang(lang: String) {
        context.dataStore.edit { it[Keys.PREF_SUB_LANG] = lang }
    }

    /** Per-source "refresh on startup" — the set of source ids to re-sync when the app launches. */
    val refreshSourceIds: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        prefs[Keys.REFRESH_SOURCE_IDS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setSourceRefresh(sourceId: Long, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.REFRESH_SOURCE_IDS].orEmpty().toMutableSet()
            if (enabled) current.add(sourceId.toString()) else current.remove(sourceId.toString())
            prefs[Keys.REFRESH_SOURCE_IDS] = current
        }
    }

    /** Whether focusing a channel auto-plays it in the Live preview pane. */
    val livePreviewEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIVE_PREVIEW] ?: true }

    suspend fun setLivePreviewEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_PREVIEW] = enabled }
    }

    /** Whether the Live preview plays audio (off by default so browsing stays quiet). */
    val livePreviewAudio: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIVE_PREVIEW_AUDIO] ?: false }

    suspend fun setLivePreviewAudio(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_PREVIEW_AUDIO] = enabled }
    }

    /** Use HDR output when the video and display support it. */
    val hdrEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HDR_ENABLED] ?: true }

    suspend fun setHdrEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HDR_ENABLED] = enabled }
    }

    /** The source shown as "active" in the sidebar; -1 = none chosen (fall back to the first source). */
    val defaultSourceId: Flow<Long> = context.dataStore.data.map { it[Keys.DEFAULT_SOURCE] ?: -1L }

    suspend fun setDefaultSource(id: Long) {
        context.dataStore.edit { it[Keys.DEFAULT_SOURCE] = id }
    }

    /** User-chosen download base folder; blank = app-specific storage. */
    val downloadRoot: Flow<String> = context.dataStore.data.map { it[Keys.DOWNLOAD_ROOT] ?: "" }

    suspend fun setDownloadRoot(path: String) {
        context.dataStore.edit { it[Keys.DOWNLOAD_ROOT] = path }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.AMOLED_DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val uiZoomPercent: Flow<Int> = context.dataStore.data.map { prefs ->
        UiZoom.clamp(prefs[Keys.UI_ZOOM_PCT] ?: UiZoom.DEFAULT)
    }

    suspend fun setUiZoomPercent(percent: Int) {
        context.dataStore.edit { it[Keys.UI_ZOOM_PCT] = UiZoom.clamp(percent) }
    }

    val accent: Flow<AccentColor> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACCENT]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
            ?: AccentColor.TEAL
    }

    /** Picking a preset clears any custom accent so the preset takes effect. */
    suspend fun setAccent(accent: AccentColor) {
        context.dataStore.edit {
            it[Keys.ACCENT] = accent.name
            it[Keys.ACCENT_CUSTOM] = ""
        }
    }

    /** Custom accent as a hex string ("#52DBC8"); blank = use the [accent] preset. */
    val customAccent: Flow<String> = context.dataStore.data.map { it[Keys.ACCENT_CUSTOM] ?: "" }

    suspend fun setCustomAccent(hex: String) {
        context.dataStore.edit { it[Keys.ACCENT_CUSTOM] = hex.trim() }
    }

    /** Avatar for the current (placeholder) profile until real profiles arrive in the wizard. */
    val avatarId: Flow<Int> = context.dataStore.data.map { it[Keys.AVATAR_ID] ?: 0 }

    suspend fun setAvatarId(id: Int) {
        context.dataStore.edit { it[Keys.AVATAR_ID] = id }
    }

    /** Active profile id; -1 means first-run / setup not yet completed. */
    val activeProfileId: Flow<Long> = context.dataStore.data.map { it[Keys.ACTIVE_PROFILE] ?: -1L }

    suspend fun setActiveProfile(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE] = id }
    }

    // --- Backup / restore of pure UI/player preferences (device-agnostic) ---
    // Deliberately EXCLUDES the download folder (a device-specific path) and the profile/source-coupled
    // keys (active profile, default source, refresh-on-startup) — those ride with the sources backup.

    private val backupStringKeys = listOf(
        Keys.THEME_MODE, Keys.ACCENT, Keys.ACCENT_CUSTOM, Keys.DEFAULT_ZOOM,
        Keys.PREF_AUDIO_LANG, Keys.PREF_SUB_LANG, Keys.SORT_LIVE, Keys.SORT_MOVIES,
        Keys.SORT_SERIES, Keys.RESUME_MODE,
    )
    private val backupIntKeys = listOf(Keys.UI_ZOOM_PCT, Keys.AUDIO_DELAY_MS)
    private val backupBoolKeys = listOf(Keys.LIVE_PREVIEW, Keys.LIVE_PREVIEW_AUDIO, Keys.HDR_ENABLED, Keys.HW_DECODING, Keys.UPDATE_CHECK_ON_START)
    private val backupFloatKeys = listOf(Keys.SUB_SCALE)

    suspend fun exportSettings(): org.json.JSONObject {
        val p = context.dataStore.data.first()
        return org.json.JSONObject().apply {
            backupStringKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupIntKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupBoolKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupFloatKeys.forEach { k -> p[k]?.let { put(k.name, it.toDouble()) } }
        }
    }

    suspend fun importSettings(o: org.json.JSONObject) {
        context.dataStore.edit { prefs ->
            backupStringKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getString(k.name) }
            backupIntKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getInt(k.name) }
            backupBoolKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getBoolean(k.name) }
            backupFloatKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getDouble(k.name).toFloat() }
        }
    }
}
