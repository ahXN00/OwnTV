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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import tv.own.owntv.features.home.HomeConfig
import tv.own.owntv.core.util.Pin
import tv.own.owntv.ui.theme.AccentColor
import tv.own.owntv.ui.theme.ThemeMode
import tv.own.owntv.ui.theme.UiZoom

/** Per-profile startup landing (Phase 3 / v4.0.0). LAST_CHANNEL also covers "auto-play my channel" since
 *  it's always the one you last watched. */
enum class StartupMode(val label: String) {
    HOME("Home"), LAST_CHANNEL("Last channel"), FAVORITES("Live · Favorites")
}

/**
 * Per-source playlist auto-refresh mode. The interval entries are **staleness thresholds**, not strict
 * timers: a source is refreshed when `now - lastSyncAt >= thresholdMs`. OFF disables auto-refresh;
 * STARTUP refreshes on cold app start only; interval modes are checked on cold start and on resume.
 * [thresholdMs] has a default of null so OFF/STARTUP can be declared without an explicit value.
 */
enum class PlaylistAutoRefresh(val label: String, val thresholdMs: Long? = null) {
    OFF("Off"),
    STARTUP("Refresh at startup"),
    HOURS_6("6 hours", 6 * 3600_000L),
    HOURS_12("12 hours", 12 * 3600_000L),
    HOURS_24("24 hours", 24 * 3600_000L),
    HOURS_48("48 hours", 48 * 3600_000L);

    /** Interval (staleness-threshold) mode — checked on cold start AND on resume when threshold is exceeded. */
    val isInterval: Boolean get() = thresholdMs != null && this != STARTUP
}

/** Per-EPG-source auto-refresh mode. Same staleness-threshold semantics as [PlaylistAutoRefresh]. */
enum class EpgAutoRefresh(val label: String, val thresholdMs: Long? = null) {
    OFF("Off"),
    STARTUP("Refresh at startup"),
    HOURS_1("1 hour", 1 * 3600_000L),
    HOURS_3("3 hours", 3 * 3600_000L),
    HOURS_6("6 hours", 6 * 3600_000L),
    HOURS_12("12 hours", 12 * 3600_000L),
    HOURS_24("24 hours", 24 * 3600_000L),
    HOURS_48("48 hours", 48 * 3600_000L);

    val isInterval: Boolean get() = thresholdMs != null && this != STARTUP
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_settings")

/** CH+- key paging limits. Top-level so any caller (VM, UI) can reference them via the class. */
object ChNavLimits {
    /** Hard cap for the CH+- skip counts — protects against typos (e.g. 999999) overloading slow TVs. */
    const val HARD_MAX = 1000
    /** Above this value the settings UI shows an advisory warning (high skips overshoot short lists). */
    const val WARN_THRESHOLD = 50
    /** Default per-direction skip (single CH+/- press moves this many items). */
    const val DEFAULT_SKIP = 10
}

/**
 * Persists app-level preferences. Phase 1 only needs the theme selection; this will grow to hold
 * UI zoom, custom user-agent, refresh-on-start, etc. in later phases.
 */
class SettingsRepository(private val context: Context) {

    /**
     * Every settings flow below is derived through this (audit ST2, step 2).
     *
     * DataStore emits the **whole** `Preferences` object to **every** collector on **every** write,
     * so without the `distinctUntilChanged` a single toggle re-ran ~100 `map { }` lambdas and pushed
     * ~100 "new" StateFlow values app-wide — each one a potential recomposition — even though only
     * one key had actually changed. One operator here stops that propagation for all of them at
     * once, which is why this is a helper rather than 74 hand-edited call sites.
     */
    private fun <T> prefsFlow(transform: (Preferences) -> T): Flow<T> =
        context.dataStore.data.map(transform).distinctUntilChanged()

    // Liquid Glass defaults: OFF (empty scope) — the glass look is strictly opt-in, the app looks
    // unchanged until the user enables it in Settings → Glass Effect. Alpha/blur defaults are the
    // "nice preset" applied once glass is turned on.
    private val GLASS_SCOPE_DEFAULT_BITS: Int = 0
    private val GLASS_ALPHA_DEFAULT_PCT: Int = 75
    private val GLASS_BLUR_DEFAULT_PCT: Int = 80

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
        // Per-source auto-refresh selections (JSON maps: { "<sourceId>": "<EnumName>" }). Replace the
        // binary refresh-on-startup set with Off/Startup + staleness thresholds. Migration-safe: the legacy
        // REFRESH_SOURCE_IDS set is read once (see migrateLegacyRefreshFlags) then ignored.
        val PLAYLIST_AUTO_REFRESH = stringPreferencesKey("playlist_auto_refresh")
        val EPG_AUTO_REFRESH = stringPreferencesKey("epg_auto_refresh")
        // Per-EPG-source: use that feed's own <icon src> channel logos instead of the playlist's.
        val EPG_USE_LOGOS = stringPreferencesKey("epg_use_logos")
        val REFRESH_MIGRATED = booleanPreferencesKey("refresh_migration_done")
        val EPG_REFILL_CHECKED = booleanPreferencesKey("epg_refill_checked")
        // Set while a backup restore is applying, cleared only when it completes (B2). A value that
        // survives to the next launch means the restore was interrupted and may be half-applied.
        val RESTORE_IN_PROGRESS = stringPreferencesKey("restore_in_progress")
        val LIVE_PREVIEW = booleanPreferencesKey("live_preview")
        val LIVE_PREVIEW_AUDIO = booleanPreferencesKey("live_preview_audio")
        // Docked mini-player: size (% of screen width) and screen corner/edge.
        val MINI_PLAYER_SIZE_PCT = intPreferencesKey("mini_player_size_pct")
        val MINI_PLAYER_POSITION = stringPreferencesKey("mini_player_position")
        // Live TV latency: preset name + the custom seconds used when the preset is CUSTOM.
        val LIVE_LATENCY_MODE = stringPreferencesKey("live_latency_mode")
        val LIVE_LATENCY_CUSTOM_SECS = intPreferencesKey("live_latency_custom_secs")
        val HDR_ENABLED = booleanPreferencesKey("hdr_enabled")
        val AUTO_FRAME_RATE = booleanPreferencesKey("auto_frame_rate")
        val ANDROID_TV_HOME = booleanPreferencesKey("android_tv_home")
        // Video Player Settings
        val HW_DECODING = booleanPreferencesKey("hw_decoding")
        val VOD_PREFER_EXO = booleanPreferencesKey("vod_prefer_exo")
        val MEASURED_STREAM_STATS = booleanPreferencesKey("measured_stream_stats")
        val DIRECT_TUNE = booleanPreferencesKey("direct_tune")
        val SURROUND_SOUND = booleanPreferencesKey("surround_sound")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        /** Legacy single external-player toggle (movies + series + downloads). Superseded by the three
         *  per-section keys below but still read as their default, so an existing setting survives. */
        val EXTERNAL_PLAYER = booleanPreferencesKey("external_player")
        val EXTERNAL_PLAYER_LIVE = booleanPreferencesKey("external_player_live")
        val EXTERNAL_PLAYER_MOVIES = booleanPreferencesKey("external_player_movies")
        val EXTERNAL_PLAYER_SERIES = booleanPreferencesKey("external_player_series")
        val DEFAULT_ZOOM = stringPreferencesKey("default_zoom")
        val SUB_SCALE = floatPreferencesKey("sub_scale")
        val AUDIO_DELAY_MS = intPreferencesKey("audio_delay_ms")
        val PREF_AUDIO_LANG = stringPreferencesKey("pref_audio_lang")
        val PREF_SUB_LANG = stringPreferencesKey("pref_sub_lang")
        // Per-section list sorting ("PLAYLIST" or "ALPHA")
        val SORT_LIVE = stringPreferencesKey("sort_live")
        val SORT_GUIDE = stringPreferencesKey("sort_guide")
        val SORT_MOVIES = stringPreferencesKey("sort_movies")
        val SORT_SERIES = stringPreferencesKey("sort_series")
        val RESUME_MODE = stringPreferencesKey("resume_mode")
        val UPDATE_CHECK_ON_START = booleanPreferencesKey("update_check_on_start")
        val CATCHUP_TZ = stringPreferencesKey("catchup_timezone")
        val CATCHUP_PLAYER = stringPreferencesKey("catchup_player")
        val CATCHUP_OFFSET_MIN = intPreferencesKey("catchup_offset_minutes")
        val ANIMATION_LEVEL = stringPreferencesKey("animation_level")
        val RESUME_LAST_CHANNEL = booleanPreferencesKey("resume_last_channel")
        val LAST_LIVE_CATEGORY = stringPreferencesKey("last_live_category")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val LAST_LIVE_CHANNEL = androidx.datastore.preferences.core.longPreferencesKey("last_live_channel")
        val VOD_VIEW_MODE = stringPreferencesKey("vod_view_mode")
        // Global proxy (Approach 1 — one app-wide HTTP proxy). HTTP only; no per-source override yet.
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_USER = stringPreferencesKey("proxy_user")
        val PROXY_PASS = stringPreferencesKey("proxy_pass")
        // Weather chip: show/hide + manual location override (blank = auto-detect from public IP).
        val WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        val WEATHER_LOCATION = stringPreferencesKey("weather_location")
        val WEATHER_FAHRENHEIT = booleanPreferencesKey("weather_fahrenheit")
        // TMDB metadata enrichment (see extras/future-plan/tmdb-metadata-plan.md). Master toggle + the two
        // advanced tiers (own key / self-host URL). Blank tier fields = use the default caching Worker.
        val METADATA_ENABLED = booleanPreferencesKey("metadata_enabled") // legacy; migrated to METADATA_MODE
        val METADATA_MODE = stringPreferencesKey("metadata_mode")
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val METADATA_SERVER_URL = stringPreferencesKey("metadata_server_url")
        // TMDB content language (ISO 639-1, optionally with region — e.g. "el", "pt-BR"). Blank = the
        // TMDB default (en-US), which is what every install used before this setting existed, so leaving
        // it blank keeps existing users' metadata exactly as it was. "auto" = follow the device locale.
        val METADATA_LANGUAGE = stringPreferencesKey("metadata_language")
        // Nav menu customization (v4.3.0): DYNAMIC auto-adapts the side icons to what the active playlist
        // offers; STATIC lets the user hide specific icons. NAV_HIDDEN holds MainSection.name values the
        // user has hidden (STATIC mode only — DYNAMIC ignores it).
        val NAV_MENU_MODE = stringPreferencesKey("nav_menu_mode")
        val NAV_MENU_HIDDEN = stringSetPreferencesKey("nav_menu_hidden")
        // CH+- key paging for browse panels (Live/Movies/Series: category rail + item list/grid).
        // Master toggle + a per-direction skip count (CH+ toward first, CH− toward last). Counts are
        // clamped to [1, CH_NAV_HARD_MAX] on write; the UI warns above CH_NAV_WARN_THRESHOLD.
        val CH_NAV_ENABLED = booleanPreferencesKey("ch_nav_enabled")
        val CH_NAV_UP_SKIP = intPreferencesKey("ch_nav_up_skip")
        val CH_NAV_DOWN_SKIP = intPreferencesKey("ch_nav_down_skip")
        // "Browsing & lists" — two independent per-section toggles (Live TV / Movies / Series).
        //
        // REMEMBER_LAST_*  = remember last ITEM. OFF (default) = switching category resets the browse list
        //                    to the top; ON = each category keeps its own scroll position. The Live one
        //                    also gates lastLiveChannelId (the focused-channel restore on re-entry).
        // REMEMBER_CAT_*   = remember last CATEGORY. ON (default) = reopening the section lands on the
        //                    category you left; OFF = always start on All. Live TV has always behaved this
        //                    way; Movies/Series gained the same persistence alongside the toggle.
        val REMEMBER_LAST_LIVE = booleanPreferencesKey("remember_last_live")
        val REMEMBER_LAST_MOVIES = booleanPreferencesKey("remember_last_movies")
        val REMEMBER_LAST_SERIES = booleanPreferencesKey("remember_last_series")
        val REMEMBER_CAT_LIVE = booleanPreferencesKey("remember_cat_live")
        val REMEMBER_CAT_MOVIES = booleanPreferencesKey("remember_cat_movies")
        val REMEMBER_CAT_SERIES = booleanPreferencesKey("remember_cat_series")
        val LAST_MOVIES_CATEGORY = stringPreferencesKey("last_movies_category")
        val LAST_SERIES_CATEGORY = stringPreferencesKey("last_series_category")
        // Background image (Liquid Glass). bg_image_path holds the absolute path of the image we
        // COPIED into app-private storage (so a USB unplug or source-folder delete never blanks it);
        // blank = no background (feature off, panels stay solid). glass_scope is the bitmask of which
        // surfaces go translucent (GlassConfig.fromBitmask); glass_alpha is the fill alpha in 0..100;
        // glass_blur is the backdrop frost strength in 0..100 (Phase 4 — real backdrop blur; 0 keeps
        // the older Tier-1 translucency-only look).
        val BG_IMAGE_PATH = stringPreferencesKey("bg_image_path")
        val GLASS_SCOPE = intPreferencesKey("glass_scope")
        val GLASS_ALPHA = intPreferencesKey("glass_alpha")
        val GLASS_BLUR = intPreferencesKey("glass_blur")
    }

    // --- Live TV: remember the last focused channel so reopening lands focus back on it ---
    val lastLiveChannelId: Flow<Long> = prefsFlow { it[Keys.LAST_LIVE_CHANNEL] ?: -1L }
    suspend fun setLastLiveChannelId(id: Long) {
        context.dataStore.edit { it[Keys.LAST_LIVE_CHANNEL] = id }
    }

    // --- Startup: per-profile landing (v4.0.0). Falls back to the legacy global resume toggle for existing
    //     users (so "Resume last channel = On" keeps working until they pick a per-profile mode). ---
    fun startupMode(profileId: Long): Flow<StartupMode> = prefsFlow { prefs ->
        prefs[stringPreferencesKey("startup_mode_$profileId")]?.let { runCatching { StartupMode.valueOf(it) }.getOrNull() }
            ?: if (prefs[Keys.RESUME_LAST_CHANNEL] == true) StartupMode.LAST_CHANNEL else StartupMode.HOME
    }
    suspend fun setStartupMode(profileId: Long, mode: StartupMode) {
        context.dataStore.edit { it[stringPreferencesKey("startup_mode_$profileId")] = mode.name }
    }

    // --- Customize Categories & Items: optional per-profile PIN lock on the screen (so hidden items can't
    //     be unhidden by someone else). Exported/imported in backups as a salted SHA-256 hash (see
    //     exportCustomizePins / importCustomizePins → BackupManager `customizePins`), so the PIN value
    //     itself never travels in a readable form. ---
    fun customizePin(profileId: Long): Flow<String?> = prefsFlow { prefs ->
        prefs[stringPreferencesKey("customize_pin_$profileId")]?.takeIf { it.isNotBlank() }
    }

    /** null/blank clears the lock. */
    suspend fun setCustomizePin(profileId: Long, pin: String?) {
        context.dataStore.edit { prefs ->
            val k = stringPreferencesKey("customize_pin_$profileId")
            if (pin.isNullOrBlank()) prefs.remove(k) else prefs[k] = Pin.hash(pin.trim())
        }
    }

    /** Whether a category the provider adds on a later resync is hidden automatically. Same across
     *  Live/Movies/Series for a profile — there's no reason to want it to differ by section. */
    fun hideNewCategoriesDefault(profileId: Long): Flow<Boolean> = prefsFlow { prefs ->
        prefs[booleanPreferencesKey("hide_new_categories_$profileId")] ?: false
    }

    suspend fun setHideNewCategoriesDefault(profileId: Long, hidden: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("hide_new_categories_$profileId")] = hidden }
    }

    // --- Home: per-profile row order / visibility / hero filters. ---
    private fun homeConfigKey(profileId: Long) = stringPreferencesKey("home_config_$profileId")

    fun homeConfig(profileId: Long): Flow<HomeConfig> = prefsFlow { prefs ->
        HomeConfig.fromJson(prefs[homeConfigKey(profileId)])
    }

    suspend fun updateHomeConfig(profileId: Long, transform: (HomeConfig) -> HomeConfig) {
        context.dataStore.edit { prefs ->
            val key = homeConfigKey(profileId)
            val next = transform(HomeConfig.fromJson(prefs[key]))
            if (next == HomeConfig()) prefs.remove(key) else prefs[key] = next.toJson().toString()
        }
    }

    // --- Startup: auto-open the last-watched live channel (default OFF) — legacy, now migrated to startupMode ---
    val resumeLastChannel: Flow<Boolean> = prefsFlow { it[Keys.RESUME_LAST_CHANNEL] ?: false }
    suspend fun setResumeLastChannel(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESUME_LAST_CHANNEL] = enabled }
    }

    // --- Remember the last selected category so reopening a section lands where you left off.
    //     Written by each section's view model (debounced), read once on restore. ---
    val lastLiveCategory: Flow<String> = prefsFlow { it[Keys.LAST_LIVE_CATEGORY] ?: "" }
    suspend fun setLastLiveCategory(key: String) {
        context.dataStore.edit { it[Keys.LAST_LIVE_CATEGORY] = key }
    }
    val lastMoviesCategory: Flow<String> = prefsFlow { it[Keys.LAST_MOVIES_CATEGORY] ?: "" }
    suspend fun setLastMoviesCategory(key: String) {
        context.dataStore.edit { it[Keys.LAST_MOVIES_CATEGORY] = key }
    }
    val lastSeriesCategory: Flow<String> = prefsFlow { it[Keys.LAST_SERIES_CATEGORY] ?: "" }
    suspend fun setLastSeriesCategory(key: String) {
        context.dataStore.edit { it[Keys.LAST_SERIES_CATEGORY] = key }
    }

    // --- Per-section "remember last CATEGORY" (default ON each — Live TV's long-standing behaviour,
    //     now also available for Movies/Series). OFF = the section always opens on All. ---
    val rememberCategoryLive: Flow<Boolean> = prefsFlow { it[Keys.REMEMBER_CAT_LIVE] ?: true }
    suspend fun setRememberCategoryLive(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_CAT_LIVE] = enabled }
    }
    val rememberCategoryMovies: Flow<Boolean> = prefsFlow { it[Keys.REMEMBER_CAT_MOVIES] ?: true }
    suspend fun setRememberCategoryMovies(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_CAT_MOVIES] = enabled }
    }
    val rememberCategorySeries: Flow<Boolean> = prefsFlow { it[Keys.REMEMBER_CAT_SERIES] ?: true }
    suspend fun setRememberCategorySeries(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_CAT_SERIES] = enabled }
    }

    // --- Per-section "remember last ITEM per category" (default OFF each).
    //     OFF = switching category resets the browse list to the top; ON = each category keeps its own
    //     scroll position. The Live toggle additionally gates the lastLiveChannelId restore on re-entry. ---
    val rememberLastLive: Flow<Boolean> = prefsFlow { it[Keys.REMEMBER_LAST_LIVE] ?: false }
    suspend fun setRememberLastLive(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_LAST_LIVE] = enabled }
    }
    val rememberLastMovies: Flow<Boolean> = prefsFlow { it[Keys.REMEMBER_LAST_MOVIES] ?: false }
    suspend fun setRememberLastMovies(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_LAST_MOVIES] = enabled }
    }
    val rememberLastSeries: Flow<Boolean> = prefsFlow { it[Keys.REMEMBER_LAST_SERIES] ?: false }
    suspend fun setRememberLastSeries(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_LAST_SERIES] = enabled }
    }

    // --- Search: recent search terms (most-recent first, capped). Stored as one newline-joined string
    //     so no schema/table is needed; blank entries are ignored on read. ---
    val recentSearches: Flow<List<String>> = prefsFlow { prefs ->
        prefs[Keys.RECENT_SEARCHES]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    /** Push a query to the top of the recents (case-insensitive dedup), capped at 12 entries. */
    suspend fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_SEARCHES]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val next = (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(12)
            prefs[Keys.RECENT_SEARCHES] = next.joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { it.remove(Keys.RECENT_SEARCHES) }
    }

    // --- Appearance: animation level (perf control for low-end boxes) ---
    val animationLevel: Flow<tv.own.owntv.ui.theme.AnimationLevel> = prefsFlow { prefs ->
        prefs[Keys.ANIMATION_LEVEL]?.let { runCatching { tv.own.owntv.ui.theme.AnimationLevel.valueOf(it) }.getOrNull() }
            ?: tv.own.owntv.ui.theme.AnimationLevel.FULL
    }

    suspend fun setAnimationLevel(level: tv.own.owntv.ui.theme.AnimationLevel) {
        context.dataStore.edit { it[Keys.ANIMATION_LEVEL] = level.name }
    }

    // --- Weather chip (top bar): show/hide + manual location override for VPN users ---

    /** Show the weather chip in the top bar (default ON). */
    val weatherEnabled: Flow<Boolean> = prefsFlow { it[Keys.WEATHER_ENABLED] ?: true }

    suspend fun setWeatherEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WEATHER_ENABLED] = enabled }
    }

    /**
     * Manual weather location. Blank (default) = auto-detect from public IP. Otherwise a city name
     * (geocoded via Open-Meteo) or a raw "lat,lon" pair. Lets users fix the wrong-city behaviour
     * they see behind a VPN, where IP geolocation resolves to the VPN server's city.
     */
    val weatherLocation: Flow<String> = prefsFlow { it[Keys.WEATHER_LOCATION] ?: "" }

    suspend fun setWeatherLocation(location: String) {
        context.dataStore.edit { it[Keys.WEATHER_LOCATION] = location.trim() }
    }

    /** Show the weather temperature in Fahrenheit instead of Celsius (default °C). */
    val weatherFahrenheit: Flow<Boolean> = prefsFlow { it[Keys.WEATHER_FAHRENHEIT] ?: false }

    suspend fun setWeatherFahrenheit(fahrenheit: Boolean) {
        context.dataStore.edit { it[Keys.WEATHER_FAHRENHEIT] = fahrenheit }
    }

    // --- TMDB metadata enrichment (plan §4) ---
    // One provider, three configs. Enrichment is opt-outable via the master toggle; the two advanced
    // fields (own key / self-host URL) override the default caching Worker when set.

    /** Metadata source mode (plan §4.1). Defaults to Provider+TMDB; back-compat: an old boolean master
     *  toggle maps false→Provider, true→Provider+TMDB when no explicit mode is stored yet. */
    val metadataMode: Flow<tv.own.owntv.core.metadata.MetadataMode> = prefsFlow { p ->
        parseMetadataMode(p)
    }

    private fun parseMetadataMode(p: Preferences): tv.own.owntv.core.metadata.MetadataMode {
        p[Keys.METADATA_MODE]?.let { raw ->
            runCatching { tv.own.owntv.core.metadata.MetadataMode.valueOf(raw) }.getOrNull()?.let { return it }
        }
        // No explicit mode yet — derive from the legacy boolean toggle.
        return if (p[Keys.METADATA_ENABLED] == false) tv.own.owntv.core.metadata.MetadataMode.PROVIDER
        else tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB
    }

    suspend fun setMetadataMode(mode: tv.own.owntv.core.metadata.MetadataMode) {
        context.dataStore.edit {
            it[Keys.METADATA_MODE] = mode.name
            it[Keys.METADATA_ENABLED] = mode.enrich // keep legacy key coherent for older readers
        }
    }

    /** Tier 2 — the user's own TMDB v3 API key; blank = don't call TMDB directly. */
    val tmdbApiKey: Flow<String> = prefsFlow { it[Keys.TMDB_API_KEY] ?: "" }

    suspend fun setTmdbApiKey(key: String) {
        context.dataStore.edit { it[Keys.TMDB_API_KEY] = key.trim() }
    }

    /** Tier 3 — a custom TMDB-shaped metadata server base URL; blank = don't self-host. */
    val metadataServerUrl: Flow<String> = prefsFlow { it[Keys.METADATA_SERVER_URL] ?: "" }

    suspend fun setMetadataServerUrl(url: String) {
        context.dataStore.edit { it[Keys.METADATA_SERVER_URL] = url.trim() }
    }

    /**
     * TMDB content language. Blank = TMDB's own default (en-US) — the pre-existing behaviour, so an
     * upgrade never silently changes anyone's metadata. "auto" = follow the device locale, resolved at
     * call time by [tv.own.owntv.core.metadata.MetadataConfig.resolvedLanguage].
     */
    val metadataLanguage: Flow<String> = prefsFlow { it[Keys.METADATA_LANGUAGE] ?: "" }

    suspend fun setMetadataLanguage(code: String) {
        context.dataStore.edit { it[Keys.METADATA_LANGUAGE] = code.trim() }
    }

    /** Live snapshot of the metadata settings as one object (consumed by TmdbProvider). */
    val metadataConfigFlow: Flow<tv.own.owntv.core.metadata.MetadataConfig> = prefsFlow { p ->
        tv.own.owntv.core.metadata.MetadataConfig(
            mode = parseMetadataMode(p),
            tmdbApiKey = p[Keys.TMDB_API_KEY] ?: "",
            customServerUrl = p[Keys.METADATA_SERVER_URL] ?: "",
            language = p[Keys.METADATA_LANGUAGE] ?: "",
        )
    }

    /** One-shot read of the current metadata config (used by TmdbProvider per call). */
    suspend fun metadataConfig(): tv.own.owntv.core.metadata.MetadataConfig = metadataConfigFlow.first()

    // --- Catch-up (archive) playback ---

    /** Which timezone to format Xtream timeshift URLs in. Most panels run on the server's local time, which
     *  usually matches the user's region — so **Device** is the default; a manual UTC offset is the fallback. */
    enum class CatchupTimezone { DEVICE, MANUAL }

    /** Manual UTC offset bounds (whole hours), in minutes. */
    val catchupOffsetRangeMinutes: IntRange = -12 * 60..14 * 60

    val catchupTimezone: Flow<CatchupTimezone> = prefsFlow { prefs ->
        prefs[Keys.CATCHUP_TZ]?.let { runCatching { CatchupTimezone.valueOf(it) }.getOrNull() } ?: CatchupTimezone.DEVICE
    }

    /** Manual mode's offset from UTC, in minutes (0 = UTC, the previous default). */
    val catchupOffsetMinutes: Flow<Int> = prefsFlow { it[Keys.CATCHUP_OFFSET_MIN] ?: 0 }

    suspend fun setCatchupTimezone(mode: CatchupTimezone) {
        context.dataStore.edit { it[Keys.CATCHUP_TZ] = mode.name }
    }

    /** Which player takes a catch-up archive programme. Archives are mid-GOP MPEG-TS, the hardest thing
     *  the in-app engines have to swallow, so handing them to VLC/MX is a genuine escape hatch — but the
     *  in-app player keeps the HUD, resume and engine toggle, so **INTERNAL stays the default**. */
    enum class CatchupPlayer { ASK, INTERNAL, EXTERNAL }

    val catchupPlayer: Flow<CatchupPlayer> = prefsFlow { prefs ->
        prefs[Keys.CATCHUP_PLAYER]?.let { runCatching { CatchupPlayer.valueOf(it) }.getOrNull() } ?: CatchupPlayer.INTERNAL
    }

    suspend fun setCatchupPlayer(mode: CatchupPlayer) {
        context.dataStore.edit { it[Keys.CATCHUP_PLAYER] = mode.name }
    }

    suspend fun setCatchupOffsetMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.CATCHUP_OFFSET_MIN] = minutes.coerceIn(catchupOffsetRangeMinutes) }
    }

    /** The timezone catch-up/timeshift URLs are formatted in — device tz, or a manual UTC offset. */
    suspend fun resolveCatchupTimeZone(): java.util.TimeZone = when (catchupTimezone.first()) {
        CatchupTimezone.DEVICE -> java.util.TimeZone.getDefault()
        CatchupTimezone.MANUAL -> java.util.SimpleTimeZone(catchupOffsetMinutes.first() * 60_000, "catchup")
    }

    /** Automatically check GitHub Releases for a newer version shortly after launch. */
    val updateCheckOnStart: Flow<Boolean> = prefsFlow { it[Keys.UPDATE_CHECK_ON_START] ?: true }

    suspend fun setUpdateCheckOnStart(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UPDATE_CHECK_ON_START] = enabled }
    }

    // --- Resume behavior for movies/episodes with a saved position ---

    enum class ResumeMode(val label: String) {
        AUTO("Always resume"), ASK("Ask to resume"), NEVER("Never resume")
    }

    val resumeMode: Flow<ResumeMode> = prefsFlow { prefs ->
        prefs[Keys.RESUME_MODE]?.let { runCatching { ResumeMode.valueOf(it) }.getOrNull() } ?: ResumeMode.ASK
    }

    suspend fun setResumeMode(mode: ResumeMode) {
        context.dataStore.edit { it[Keys.RESUME_MODE] = mode.name }
    }

    // --- Nav menu customization (v4.3.0) ---
    // DYNAMIC: the side icons adapt to what the active playlist actually contains (Home & Settings
    // always show; Live/Guide show when there are channels; Movies/Series show when their content
    // exists; Downloads shows when Movies OR Series exist since Live has no download). STATIC: the
    // user picks exactly which icons to hide. Default STATIC (all visible) → existing users see no
    // change until they opt into Dynamic.

    enum class NavMenuMode(val label: String) { DYNAMIC("Dynamic"), STATIC("Static") }

    val navMenuMode: Flow<NavMenuMode> = prefsFlow { prefs ->
        prefs[Keys.NAV_MENU_MODE]?.let { runCatching { NavMenuMode.valueOf(it) }.getOrNull() } ?: NavMenuMode.STATIC
    }

    suspend fun setNavMenuMode(mode: NavMenuMode) {
        context.dataStore.edit { it[Keys.NAV_MENU_MODE] = mode.name }
    }

    /** Names of the [tv.own.owntv.features.shell.MainSection] browse items the user has hidden (STATIC mode). */
    val navMenuHidden: Flow<Set<String>> = prefsFlow { it[Keys.NAV_MENU_HIDDEN] ?: emptySet() }

    /** Replace the whole hidden set. Empty = all visible. */
    suspend fun setNavMenuHidden(hidden: Set<String>) {
        context.dataStore.edit { prefs ->
            if (hidden.isEmpty()) prefs.remove(Keys.NAV_MENU_HIDDEN) else prefs[Keys.NAV_MENU_HIDDEN] = hidden
        }
    }

    // --- List sorting (per browse section) ---

    /** How a browse section's lists are ordered. RATING (highest provider rating first) applies to
     *  Movies/Series only; Live/EPG never select it. */
    enum class SortMode { PLAYLIST, ALPHA, RATING, DATE_ADDED }

    /** All three browse sections (Live/Movies/Series) default to the playlist/provider's own order — the
     *  natural grouping a user expects right after a sync. A–Z is one tap away (toggleSort). */
    val sortLive: Flow<SortMode> = prefsFlow { parseSort(it[Keys.SORT_LIVE], SortMode.PLAYLIST) }
    val sortMovies: Flow<SortMode> = prefsFlow { parseSort(it[Keys.SORT_MOVIES], SortMode.PLAYLIST) }
    val sortSeries: Flow<SortMode> = prefsFlow { parseSort(it[Keys.SORT_SERIES], SortMode.PLAYLIST) }

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

    /** The TV Guide's own ordering. LIVE_TV mirrors the Live TV sort; CATCHUP floats archive channels up. */
    enum class GuideSort(val label: String) { ALPHA("A–Z"), PROVIDER("Provider"), LIVE_TV("Live TV"), CATCHUP("Catch-up"), FAVORITES("Favorites") }

    /** How Movies & Series browse: the poster wall, or a compact list (more titles at once). */
    enum class VodViewMode(val label: String) { GRID("Grid"), LIST("List") }
    val vodViewMode: Flow<VodViewMode> = prefsFlow { prefs ->
        prefs[Keys.VOD_VIEW_MODE]?.let { runCatching { VodViewMode.valueOf(it) }.getOrNull() } ?: VodViewMode.GRID
    }
    suspend fun setVodViewMode(mode: VodViewMode) {
        context.dataStore.edit { it[Keys.VOD_VIEW_MODE] = mode.name }
    }

    val sortGuide: Flow<GuideSort> = prefsFlow { prefs ->
        prefs[Keys.SORT_GUIDE]?.let { runCatching { GuideSort.valueOf(it) }.getOrNull() } ?: GuideSort.LIVE_TV
    }

    suspend fun setSortGuide(mode: GuideSort) {
        context.dataStore.edit { it[Keys.SORT_GUIDE] = mode.name }
    }

    // --- Video Player Settings ---

    /** Hardware decoding (mpv hwdec auto-safe). Off = force software decoding for tricky streams. */
    val hwDecoding: Flow<Boolean> = prefsFlow { it[Keys.HW_DECODING] ?: true }

    suspend fun setHwDecoding(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HW_DECODING] = enabled }
    }

    /** Preferred engine for Movies & Series (VOD). Off (default) = mpv first with an automatic ExoPlayer
     *  fallback; on = ExoPlayer first with an automatic mpv fallback. mpv is the default because it has
     *  the wider codec support (DTS/TrueHD audio, odd containers) and the A/V-sync nudge; ExoPlayer-first
     *  is for devices/providers where mpv's path can't open streams that ExoPlayer plays fine. */
    val vodPreferExo: Flow<Boolean> = prefsFlow { it[Keys.VOD_PREFER_EXO] ?: false }

    suspend fun setVodPreferExo(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOD_PREFER_EXO] = enabled }
    }

    /** Measure live fps / bitrate / dropped frames for the stream-info overlay. On (default) = the
     *  overlay shows measured values that ExoPlayer doesn't declare for raw MPEG-TS. Off = a hard
     *  escape hatch: no live measuring runs at all (declared values only), for any low-end TV where
     *  the measuring is ever suspected of causing stutter. Never affects the actual playback pipeline. */
    val measuredStreamStats: Flow<Boolean> = prefsFlow { it[Keys.MEASURED_STREAM_STATS] ?: true }

    suspend fun setMeasuredStreamStats(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MEASURED_STREAM_STATS] = enabled }
    }

    /** Type a provider channel number on the remote during full-screen live playback to jump straight
     *  to that channel. On (default). Off = number keys are ignored during playback, for anyone whose
     *  remote sends digits accidentally or who doesn't want the keys captured. */
    val directTune: Flow<Boolean> = prefsFlow { it[Keys.DIRECT_TUNE] ?: true }

    suspend fun setDirectTune(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DIRECT_TUNE] = enabled }
    }

    /** Which section a stream belongs to when deciding whether it goes to an external player. */
    enum class ExternalPlayerSection { LIVE_TV, MOVIES, SERIES }

    /** Hand this section's streams to an external player (VLC, MX Player) instead of the in-app engine.
     *  Off by default everywhere.
     *
     *  Movies and Series fall back to [Keys.EXTERNAL_PLAYER], the single global toggle these three keys
     *  replaced — that's the upgrade path, so a user who had it on keeps external playback for exactly
     *  the sections it used to cover. Live TV has no such fallback: the old toggle never routed live
     *  streams out, so inheriting it would silently start sending channels to VLC after an update. */
    val externalPlayerMovies: Flow<Boolean> = prefsFlow { it[Keys.EXTERNAL_PLAYER_MOVIES] ?: it[Keys.EXTERNAL_PLAYER] ?: false }
    val externalPlayerSeries: Flow<Boolean> = prefsFlow { it[Keys.EXTERNAL_PLAYER_SERIES] ?: it[Keys.EXTERNAL_PLAYER] ?: false }
    val externalPlayerLive: Flow<Boolean> = prefsFlow { it[Keys.EXTERNAL_PLAYER_LIVE] ?: false }

    fun externalPlayer(section: ExternalPlayerSection): Flow<Boolean> = when (section) {
        ExternalPlayerSection.LIVE_TV -> externalPlayerLive
        ExternalPlayerSection.MOVIES -> externalPlayerMovies
        ExternalPlayerSection.SERIES -> externalPlayerSeries
    }

    /** A download is a movie or an episode, so it follows that section's setting. */
    fun externalPlayerFor(mediaType: tv.own.owntv.core.model.MediaType): Flow<Boolean> =
        if (mediaType == tv.own.owntv.core.model.MediaType.SERIES) externalPlayerSeries else externalPlayerMovies

    suspend fun setExternalPlayer(section: ExternalPlayerSection, enabled: Boolean) {
        val key = when (section) {
            ExternalPlayerSection.LIVE_TV -> Keys.EXTERNAL_PLAYER_LIVE
            ExternalPlayerSection.MOVIES -> Keys.EXTERNAL_PLAYER_MOVIES
            ExternalPlayerSection.SERIES -> Keys.EXTERNAL_PLAYER_SERIES
        }
        context.dataStore.edit { it[key] = enabled }
    }

    /** Surround sound (**off by default — opt-in**). Most users are on TV speakers / 2.0 soundbars, and
     *  forcing a multichannel-LPCM path exposes flaky vendor audio HALs / lying HDMI-ARC chips that claim
     *  5.1 then mis-play it (drained 2× → "fast video, no sound", #25). So default stereo for stability;
     *  users with a real 5.1/7.1 receiver turn this on. On: mpv decodes Dolby/DTS to multichannel LPCM (the
     *  sink picks the layout), with a runaway-detector that auto-falls-back to stereo on a broken output. We
     *  never bitstream/passthrough (its AudioTrack reports no clock and stutters video to a slideshow).
     *  Second, subtler failure mode (confirmed in the field): even when multichannel LPCM plays correctly,
     *  the wider HDMI/ARC buffer adds latency the TV/soundbar doesn't report back, so audio lags video
     *  (lip-sync drift) on VODs. Stereo's small, well-reported buffer stays locked. Hence: default OFF. */
    val surroundSound: Flow<Boolean> = prefsFlow { it[Keys.SURROUND_SOUND] ?: false }

    suspend fun setSurroundSound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SURROUND_SOUND] = enabled }
    }

    /** Auto-play the next episode (and roll into the next season) when one finishes. On by default. */
    val autoPlayNext: Flow<Boolean> = prefsFlow { it[Keys.AUTO_PLAY_NEXT] ?: true }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }
    }

    /** Default zoom/aspect mode applied when playback starts (a [tv.own.owntv.player.ZoomMode] name). */
    val defaultZoom: Flow<String> = prefsFlow { it[Keys.DEFAULT_ZOOM] ?: "FIT" }

    suspend fun setDefaultZoom(name: String) {
        context.dataStore.edit { it[Keys.DEFAULT_ZOOM] = name }
    }

    /** Subtitle scale multiplier (mpv sub-scale); 1.0 = normal. */
    val subtitleScale: Flow<Float> = prefsFlow { it[Keys.SUB_SCALE] ?: 1.0f }

    suspend fun setSubtitleScale(scale: Float) {
        context.dataStore.edit { it[Keys.SUB_SCALE] = scale }
    }

    /** Audio sync offset in milliseconds (mpv audio-delay); +ve delays audio. */
    val audioDelayMs: Flow<Int> = prefsFlow { it[Keys.AUDIO_DELAY_MS] ?: 0 }

    suspend fun setAudioDelayMs(ms: Int) {
        context.dataStore.edit { it[Keys.AUDIO_DELAY_MS] = ms }
    }

    // --- CH+- key paging (browse panels): master toggle + per-direction skip counts ---
    // Clamped to [1, ChNavLimits.HARD_MAX] on write so an accidental huge value can never persist.
    val chNavEnabled: Flow<Boolean> = prefsFlow { it[Keys.CH_NAV_ENABLED] ?: true }
    suspend fun setChNavEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CH_NAV_ENABLED] = enabled }
    }
    /** CH+ skip count (jumps this many items toward the first item). */
    val chNavUpSkip: Flow<Int> = prefsFlow {
        (it[Keys.CH_NAV_UP_SKIP] ?: ChNavLimits.DEFAULT_SKIP).coerceIn(1, ChNavLimits.HARD_MAX)
    }
    suspend fun setChNavUpSkip(n: Int) {
        context.dataStore.edit { it[Keys.CH_NAV_UP_SKIP] = n.coerceIn(1, ChNavLimits.HARD_MAX) }
    }
    /** CH− skip count (jumps this many items toward the last item). */
    val chNavDownSkip: Flow<Int> = prefsFlow {
        (it[Keys.CH_NAV_DOWN_SKIP] ?: ChNavLimits.DEFAULT_SKIP).coerceIn(1, ChNavLimits.HARD_MAX)
    }
    suspend fun setChNavDownSkip(n: Int) {
        context.dataStore.edit { it[Keys.CH_NAV_DOWN_SKIP] = n.coerceIn(1, ChNavLimits.HARD_MAX) }
    }

    /** Preferred audio language (ISO code, mpv alang); blank = no preference. */
    val preferredAudioLang: Flow<String> = prefsFlow { it[Keys.PREF_AUDIO_LANG] ?: "" }

    suspend fun setPreferredAudioLang(lang: String) {
        context.dataStore.edit { it[Keys.PREF_AUDIO_LANG] = lang }
    }

    /** Preferred subtitle language (ISO code, mpv slang); blank = no preference. */
    val preferredSubLang: Flow<String> = prefsFlow { it[Keys.PREF_SUB_LANG] ?: "" }

    suspend fun setPreferredSubLang(lang: String) {
        context.dataStore.edit { it[Keys.PREF_SUB_LANG] = lang }
    }

    // --- Per-source auto-refresh (Off / Startup / staleness threshold) ---
    // Stored as a JSON map { "<sourceId>": "<EnumName>" } in the owntv_settings DataStore — migration-safe
    // (Room uses destructive migrations, so anything that must survive a schema bump lives here). Reuses the
    // existing lastSyncAt columns (SourceEntity.lastSyncAt for playlists, EpgSource.lastSyncAt for EPG) as the
    // "last successful sync" timestamp; nothing new is stored for that.

    /** Per-source playlist auto-refresh selection. Missing ids default to [PlaylistAutoRefresh.OFF]. */
    val playlistAutoRefresh: Flow<Map<Long, PlaylistAutoRefresh>> =
        prefsFlow { prefs -> parseRefreshMap(prefs[Keys.PLAYLIST_AUTO_REFRESH]) { PlaylistAutoRefresh.valueOf(it) } }

    /** Per-source EPG auto-refresh selection. Missing ids default to [EpgAutoRefresh.OFF]. */
    val epgAutoRefresh: Flow<Map<Long, EpgAutoRefresh>> =
        prefsFlow { prefs -> parseRefreshMap(prefs[Keys.EPG_AUTO_REFRESH]) { EpgAutoRefresh.valueOf(it) } }

    /**
     * EPG sources whose own `<icon src>` channel logos should replace the playlist's logos. Per source,
     * so one feed can supply logos while another only supplies programmes. Missing ids default to off.
     */
    val epgUseLogos: Flow<Set<Long>> = prefsFlow { prefs ->
        parseRefreshMap(prefs[Keys.EPG_USE_LOGOS])
            .filterValues { it.toBoolean() }
            .keys.mapNotNullTo(LinkedHashSet()) { it.toLongOrNull() }
    }

    suspend fun setEpgUseLogos(sourceId: Long, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EPG_USE_LOGOS] = writeRefreshMap(readRefreshMap(prefs[Keys.EPG_USE_LOGOS]), sourceId, enabled.toString())
        }
    }

    suspend fun setPlaylistAutoRefresh(sourceId: Long, mode: PlaylistAutoRefresh) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAYLIST_AUTO_REFRESH] = writeRefreshMap(readRefreshMap(prefs[Keys.PLAYLIST_AUTO_REFRESH]), sourceId, mode.name)
        }
    }

    suspend fun setEpgAutoRefresh(sourceId: Long, mode: EpgAutoRefresh) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EPG_AUTO_REFRESH] = writeRefreshMap(readRefreshMap(prefs[Keys.EPG_AUTO_REFRESH]), sourceId, mode.name)
        }
    }

    /**
     * One-time migration of the legacy binary `refresh_source_ids` set → per-source `STARTUP` entries.
     * Idempotent (guarded by [Keys.REFRESH_MIGRATED]) and non-overwriting: if the new
     * [playlistAutoRefresh] map is already non-empty (user picked a mode in the new UI, or a prior migration
     * ran), we only flip the flag and return — never clobbering existing selections.
     */
    suspend fun migrateLegacyRefreshFlags() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.REFRESH_MIGRATED] == true) return@edit
            val existing = readRefreshMap(prefs[Keys.PLAYLIST_AUTO_REFRESH])
            val legacyIds = prefs[Keys.REFRESH_SOURCE_IDS].orEmpty()
            // Only migrate into an empty map — never overwrite selections already made in the new UI.
            if (existing.isEmpty() && legacyIds.isNotEmpty()) {
                val migrated = legacyIds.associate { it to PlaylistAutoRefresh.STARTUP.name }
                prefs[Keys.PLAYLIST_AUTO_REFRESH] =
                    org.json.JSONObject(migrated).toString()
            }
            prefs[Keys.REFRESH_MIGRATED] = true
        }
    }

    /**
     * One-shot guard for the post-migration EPG refill (audit D4).
     *
     * `MIGRATION_8_9` deletes every row in `epg_programmes` (it adds `contentHash` and a natural-key
     * unique index, which the old rows can't satisfy) and nothing schedules a re-fetch — so an
     * upgrading user was left with an empty guide until they happened to re-sync EPG by hand. This
     * runs the detection exactly once per install, which also covers users who passed through 8→9
     * long ago and are still sitting on an empty guide.
     */
    val epgRefillChecked: Flow<Boolean> = prefsFlow { it[Keys.EPG_REFILL_CHECKED] == true }

    suspend fun markEpgRefillChecked() {
        context.dataStore.edit { prefs -> prefs[Keys.EPG_REFILL_CHECKED] = true }
    }

    /**
     * Interrupted-restore marker (B2). A restore writes to the database *and* to several DataStore
     * files; only the row writes can share a transaction, so a crash or a pulled plug part-way
     * through leaves a half-applied merge that nothing would otherwise notice. The marker is written
     * before the first write and removed after the last one, so a value still present at the next
     * launch means "that restore didn't finish". The value is the backup file name plus the sections
     * that were being applied — enough to tell the user what to re-run, and never a secret.
     */
    val restoreInProgress: Flow<String?> = prefsFlow { it[Keys.RESTORE_IN_PROGRESS] }

    suspend fun markRestoreStarted(description: String) {
        context.dataStore.edit { prefs -> prefs[Keys.RESTORE_IN_PROGRESS] = description }
    }

    suspend fun clearRestoreMarker() {
        context.dataStore.edit { prefs -> prefs.remove(Keys.RESTORE_IN_PROGRESS) }
    }

    private inline fun <reified E : Enum<E>> parseRefreshMap(raw: String?, valueOf: (String) -> E): Map<Long, E> {
        if (raw.isNullOrBlank()) return emptyMap()
        val obj = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<Long, E>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val id = key.toLongOrNull() ?: continue
            val name = obj.optString(key)
            val mode = runCatching { valueOf(name) }.getOrNull() ?: continue
            out[id] = mode
        }
        return out
    }

    private fun readRefreshMap(raw: String?): MutableMap<String, String> {
        if (raw.isNullOrBlank()) return LinkedHashMap()
        val obj = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return LinkedHashMap()
        val out = LinkedHashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = obj.optString(key)
        }
        return out
    }

    private fun writeRefreshMap(map: MutableMap<String, String>, sourceId: Long, value: String): String {
        map[sourceId.toString()] = value
        return org.json.JSONObject(map.toMap()).toString()
    }

    /** Whether focusing a channel auto-plays it in the Live preview pane. */
    val livePreviewEnabled: Flow<Boolean> = prefsFlow { it[Keys.LIVE_PREVIEW] ?: true }

    suspend fun setLivePreviewEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_PREVIEW] = enabled }
    }

    /** Whether the Live preview plays audio (off by default so browsing stays quiet). */
    val livePreviewAudio: Flow<Boolean> = prefsFlow { it[Keys.LIVE_PREVIEW_AUDIO] ?: false }

    suspend fun setLivePreviewAudio(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_PREVIEW_AUDIO] = enabled }
    }

    /** Use HDR output when the video and display support it. */
    val hdrEnabled: Flow<Boolean> = prefsFlow { it[Keys.HDR_ENABLED] ?: true }

    suspend fun setHdrEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HDR_ENABLED] = enabled }
    }

    /**
     * Switch the display's refresh rate to match the video frame rate (24/25/30/50/60 fps) during
     * full-screen playback, and restore it on exit. Applies to both engines and to Live TV as well as
     * VOD. Default on; turn off if a TV/AV receiver re-handshakes HDMI noisily on every channel change.
     */
    val autoFrameRate: Flow<Boolean> = prefsFlow { it[Keys.AUTO_FRAME_RATE] ?: true }

    suspend fun setAutoFrameRate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_FRAME_RATE] = enabled }
    }

    /** Mirror continue-watching rows into Android TV home surfaces. */
    val androidTvHomeEnabled: Flow<Boolean> = prefsFlow { it[Keys.ANDROID_TV_HOME] ?: true }

    suspend fun setAndroidTvHomeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANDROID_TV_HOME] = enabled }
    }

    /** The source shown as "active" in the sidebar; -1 = none chosen (fall back to the first source). */
    val defaultSourceId: Flow<Long> = prefsFlow { it[Keys.DEFAULT_SOURCE] ?: -1L }

    suspend fun setDefaultSource(id: Long) {
        context.dataStore.edit { it[Keys.DEFAULT_SOURCE] = id }
    }

    /** User-chosen download base folder; blank = app-specific storage. */
    val downloadRoot: Flow<String> = prefsFlow { it[Keys.DOWNLOAD_ROOT] ?: "" }

    suspend fun setDownloadRoot(path: String) {
        context.dataStore.edit { it[Keys.DOWNLOAD_ROOT] = path }
    }

    val themeMode: Flow<ThemeMode> = prefsFlow { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val uiZoomPercent: Flow<Int> = prefsFlow { prefs ->
        UiZoom.clamp(prefs[Keys.UI_ZOOM_PCT] ?: UiZoom.DEFAULT)
    }

    suspend fun setUiZoomPercent(percent: Int) {
        context.dataStore.edit { it[Keys.UI_ZOOM_PCT] = UiZoom.clamp(percent) }
    }

    /** Docked mini-player size as a percentage of screen width (clamped to the allowed range). */
    val miniPlayerSizePct: Flow<Int> = prefsFlow { prefs ->
        tv.own.owntv.player.MiniPlayerSize.clamp(prefs[Keys.MINI_PLAYER_SIZE_PCT] ?: tv.own.owntv.player.MiniPlayerSize.DEFAULT)
    }

    suspend fun setMiniPlayerSizePct(percent: Int) {
        context.dataStore.edit { it[Keys.MINI_PLAYER_SIZE_PCT] = tv.own.owntv.player.MiniPlayerSize.clamp(percent) }
    }

    /** Docked mini-player screen position (a [tv.own.owntv.player.MiniPlayerPosition] name). */
    val miniPlayerPosition: Flow<String> = prefsFlow { prefs ->
        prefs[Keys.MINI_PLAYER_POSITION] ?: tv.own.owntv.player.MiniPlayerPosition.DEFAULT.name
    }

    suspend fun setMiniPlayerPosition(name: String) {
        context.dataStore.edit { it[Keys.MINI_PLAYER_POSITION] = name }
    }

    /** Live TV latency preset (a [LiveLatency] name). */
    val liveLatencyMode: Flow<String> = prefsFlow { prefs ->
        prefs[Keys.LIVE_LATENCY_MODE] ?: LiveLatency.DEFAULT.name
    }

    suspend fun setLiveLatencyMode(name: String) {
        context.dataStore.edit { it[Keys.LIVE_LATENCY_MODE] = name }
    }

    /** Custom live buffer seconds, used when the preset is [LiveLatency.CUSTOM]. */
    val liveLatencyCustomSecs: Flow<Int> = prefsFlow { prefs ->
        LiveBuffer.clampCustom(prefs[Keys.LIVE_LATENCY_CUSTOM_SECS] ?: LiveBuffer.CUSTOM_DEFAULT)
    }

    suspend fun setLiveLatencyCustomSecs(secs: Int) {
        context.dataStore.edit { it[Keys.LIVE_LATENCY_CUSTOM_SECS] = LiveBuffer.clampCustom(secs) }
    }

    /** Effective live buffer in seconds the engines apply (null = keep engine defaults, i.e. Balanced). */
    val liveBufferSeconds: Flow<Int?> = combine(liveLatencyMode, liveLatencyCustomSecs) { mode, custom ->
        LiveBuffer.effectiveSeconds(LiveLatency.fromName(mode), custom)
    }

    val accent: Flow<AccentColor> = prefsFlow { prefs ->
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
    val customAccent: Flow<String> = prefsFlow { it[Keys.ACCENT_CUSTOM] ?: "" }

    suspend fun setCustomAccent(hex: String) {
        context.dataStore.edit { it[Keys.ACCENT_CUSTOM] = hex.trim() }
    }

    // --- Liquid Glass: background image + which surfaces go translucent + how translucent ---
    /** Absolute path to the user's background image (copied into app-private storage); blank = off. */
    val bgImagePath: Flow<String> = prefsFlow { it[Keys.BG_IMAGE_PATH] ?: "" }

    /** Glass scope as a [GlassConfig] bitfield. Empty scope = feature off. */
    val glassConfig: Flow<tv.own.owntv.ui.theme.GlassConfig> = prefsFlow { p ->
        val bits = p[Keys.GLASS_SCOPE] ?: GLASS_SCOPE_DEFAULT_BITS
        val alphaPct = p[Keys.GLASS_ALPHA] ?: GLASS_ALPHA_DEFAULT_PCT
        val blurPct = p[Keys.GLASS_BLUR] ?: GLASS_BLUR_DEFAULT_PCT
        tv.own.owntv.ui.theme.GlassConfig.fromBitmask(bits, alpha = alphaPct / 100f, blurStrength = blurPct / 100f)
    }

    /** Persist the background image path. Pass "" to clear (turn glass off). */
    suspend fun setBgImagePath(path: String) {
        context.dataStore.edit { it[Keys.BG_IMAGE_PATH] = path.trim() }
    }

    /** Persist the glass scope bitfield (see [tv.own.owntv.ui.theme.GlassConfig.toBitmask]). */
    suspend fun setGlassScopeBitmask(bits: Int) {
        context.dataStore.edit { it[Keys.GLASS_SCOPE] = bits }
    }

    /** Persist glass alpha as an integer 0..100. */
    suspend fun setGlassAlphaPercent(pct: Int) {
        context.dataStore.edit { it[Keys.GLASS_ALPHA] = pct.coerceIn(0, 100) }
    }

    /** Persist the backdrop blur ("frost") strength as an integer 0..100. 0 = Tier-1 translucency only. */
    suspend fun setGlassBlurPercent(pct: Int) {
        context.dataStore.edit { it[Keys.GLASS_BLUR] = pct.coerceIn(0, 100) }
    }

    /** Avatar for the current (placeholder) profile until real profiles arrive in the wizard. */
    val avatarId: Flow<Int> = prefsFlow { it[Keys.AVATAR_ID] ?: 0 }

    suspend fun setAvatarId(id: Int) {
        context.dataStore.edit { it[Keys.AVATAR_ID] = id }
    }

    /** Active profile id; -1 means first-run / setup not yet completed. */
    val activeProfileId: Flow<Long> = prefsFlow { it[Keys.ACTIVE_PROFILE] ?: -1L }

    suspend fun setActiveProfile(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE] = id }
    }

    // --- Global proxy (Approach 1 — one app-wide HTTP proxy) ---
    // Covers all OkHttp traffic (playlist/API/EPG/images/downloads/updates/weather + ExoPlayer) and mpv
    // playback via its http-proxy option. Per-source overrides and SOCKS are future work; the proxy
    // password is intentionally NOT part of settings backup/export — see extras/PROXY_SUPPORT_PLAN.md.

    /** Live snapshot of the proxy settings as a single object (consumed by ProxyConfigHolder). */
    val proxyConfig: Flow<tv.own.owntv.core.network.ProxyConfig> = prefsFlow { p ->
        tv.own.owntv.core.network.ProxyConfig(
            enabled = p[Keys.PROXY_ENABLED] ?: false,
            host = p[Keys.PROXY_HOST] ?: "",
            port = p[Keys.PROXY_PORT] ?: 0,
            username = p[Keys.PROXY_USER] ?: "",
            password = p[Keys.PROXY_PASS] ?: "",
        )
    }

    suspend fun setProxyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROXY_ENABLED] = enabled }
    }

    /** Persist the proxy form in one write (enabled + host/port/user/pass). Blank user/pass = no auth.
     *  Port is clamped to a valid range; 0 means "unset". */
    suspend fun saveProxy(enabled: Boolean, host: String, port: Int, username: String, password: String) {
        context.dataStore.edit {
            it[Keys.PROXY_ENABLED] = enabled
            it[Keys.PROXY_HOST] = host.trim()
            it[Keys.PROXY_PORT] = port.coerceIn(0, 65535)
            it[Keys.PROXY_USER] = username.trim()
            it[Keys.PROXY_PASS] = password
        }
    }

    // --- Backup / restore of pure UI/player preferences (device-agnostic) ---
    // Deliberately EXCLUDES the download folder (a device-specific path) and the profile/source-coupled
    // keys (active profile, default source, refresh-on-startup) — those ride with the sources backup.

    private val backupStringKeys = listOf(
        Keys.THEME_MODE, Keys.ACCENT, Keys.ACCENT_CUSTOM, Keys.DEFAULT_ZOOM,
        Keys.PREF_AUDIO_LANG, Keys.PREF_SUB_LANG, Keys.SORT_LIVE, Keys.SORT_GUIDE, Keys.SORT_MOVIES,
        Keys.SORT_SERIES, Keys.RESUME_MODE, Keys.CATCHUP_TZ, Keys.CATCHUP_PLAYER, Keys.ANIMATION_LEVEL, Keys.VOD_VIEW_MODE,
        Keys.WEATHER_LOCATION, Keys.RECENT_SEARCHES,
        // Global proxy — non-secret fields only. The proxy password (Keys.PROXY_PASS) is NEVER part of
        // this whitelist; it is handled separately by BackupManager (encrypted or omitted).
        Keys.PROXY_HOST, Keys.PROXY_USER,
        // TMDB metadata: source mode + self-host URL. The user's own TMDB API key (Keys.TMDB_API_KEY) is a
        // secret and is deliberately NOT backed up in plaintext (same policy as the proxy password).
        Keys.METADATA_SERVER_URL, Keys.METADATA_MODE, Keys.METADATA_LANGUAGE,
        // Download folder. Backed up so a same-device reinstall keeps the chosen folder; on a different
        // device a path that no longer exists is harmless — StorageAccess.resolveRoot falls back to app
        // storage, so a stale restore never breaks downloads.
        Keys.DOWNLOAD_ROOT,
        // Nav menu mode rides with settings backup so a reinstall keeps the user's DYNAMIC/STATIC choice.
        Keys.NAV_MENU_MODE,
        // Docked mini-player position rides with settings backup (size is an int key, see backupIntKeys).
        Keys.MINI_PLAYER_POSITION,
        // Live TV latency preset (custom seconds is an int key, see backupIntKeys).
        Keys.LIVE_LATENCY_MODE,
        // Liquid Glass: the background image path + scope/alpha so a reinstall keeps the look.
        // NOTE: only the path string travels — the image bytes live in app-private storage which is
        // wiped on uninstall, so on a new device a stale path is ignored gracefully (falls back to none).
        Keys.BG_IMAGE_PATH,
    )
    private val backupStringSetKeys = listOf(
        // The STATIC-mode hidden set rides with backup so a reinstall keeps the user's hidden icons.
        Keys.NAV_MENU_HIDDEN,
    )
    private val backupIntKeys = listOf(Keys.UI_ZOOM_PCT, Keys.AUDIO_DELAY_MS, Keys.CATCHUP_OFFSET_MIN, Keys.PROXY_PORT, Keys.CH_NAV_UP_SKIP, Keys.CH_NAV_DOWN_SKIP, Keys.MINI_PLAYER_SIZE_PCT, Keys.LIVE_LATENCY_CUSTOM_SECS, Keys.GLASS_SCOPE, Keys.GLASS_ALPHA, Keys.GLASS_BLUR)
    private val backupBoolKeys = listOf(
        Keys.LIVE_PREVIEW, Keys.LIVE_PREVIEW_AUDIO, Keys.HDR_ENABLED, Keys.AUTO_FRAME_RATE, Keys.ANDROID_TV_HOME, Keys.HW_DECODING,
        Keys.VOD_PREFER_EXO, Keys.MEASURED_STREAM_STATS, Keys.DIRECT_TUNE, Keys.EXTERNAL_PLAYER,
        Keys.EXTERNAL_PLAYER_LIVE, Keys.EXTERNAL_PLAYER_MOVIES, Keys.EXTERNAL_PLAYER_SERIES, Keys.UPDATE_CHECK_ON_START, Keys.SURROUND_SOUND, Keys.AUTO_PLAY_NEXT, Keys.PROXY_ENABLED,
        Keys.WEATHER_ENABLED, Keys.WEATHER_FAHRENHEIT, Keys.RESUME_LAST_CHANNEL, Keys.METADATA_ENABLED, Keys.CH_NAV_ENABLED,
        Keys.REMEMBER_LAST_LIVE, Keys.REMEMBER_LAST_MOVIES, Keys.REMEMBER_LAST_SERIES,
        Keys.REMEMBER_CAT_LIVE, Keys.REMEMBER_CAT_MOVIES, Keys.REMEMBER_CAT_SERIES,
    )
    private val backupFloatKeys = listOf(Keys.SUB_SCALE)

    suspend fun exportSettings(): org.json.JSONObject {
        val p = context.dataStore.data.first()
        return org.json.JSONObject().apply {
            backupStringKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupStringSetKeys.forEach { k -> p[k]?.let { put(k.name, org.json.JSONArray(it)) } }
            backupIntKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupBoolKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupFloatKeys.forEach { k -> p[k]?.let { put(k.name, it.toDouble()) } }
        }
    }

    suspend fun importSettings(o: org.json.JSONObject) {
        context.dataStore.edit { prefs ->
            backupStringKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getString(k.name) }
            backupStringSetKeys.forEach { k ->
                if (o.has(k.name)) prefs[k] = o.getJSONArray(k.name).let { arr -> buildSet { for (i in 0 until arr.length()) add(arr.getString(i)) } }
            }
            backupIntKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getInt(k.name) }
            backupBoolKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getBoolean(k.name) }
            backupFloatKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getDouble(k.name).toFloat() }
        }
    }

    // --- Backup: per-profile Customize PIN lock (dynamic "customize_pin_<id>" keys) ---

    /** Exports all per-profile Customize PINs as { "<profileId>": "<pin>" }. */
    suspend fun exportCustomizePins(): org.json.JSONObject {
        val prefix = "customize_pin_"
        val out = org.json.JSONObject()
        context.dataStore.data.first().asMap().forEach { (k, v) ->
            if (k.name.startsWith(prefix) && v is String && v.isNotBlank()) {
                out.put(k.name.removePrefix(prefix), v)
            }
        }
        return out
    }

    /** Restores Customize PINs only for profile ids in [existingProfileIds] (others are dropped safely). */
    suspend fun importCustomizePins(o: org.json.JSONObject, existingProfileIds: Set<Long>) {
        context.dataStore.edit { prefs ->
            o.keys().forEach { key ->
                val pid = key.toLongOrNull() ?: return@forEach
                if (pid !in existingProfileIds) return@forEach
                val pin = o.optString(key).takeIf { it.isNotEmpty() } ?: return@forEach
                prefs[stringPreferencesKey("customize_pin_$pid")] = normalizeCustomizePin(pin)
            }
        }
    }

    private fun normalizeCustomizePin(value: String): String {
        val trimmed = value.trim()
        return if (CUSTOMIZE_PIN_HASH_REGEX.matches(trimmed)) trimmed else Pin.hash(trimmed)
    }

    private companion object {
        val CUSTOMIZE_PIN_HASH_REGEX = Regex("^[0-9a-fA-F]{16}:[0-9a-fA-F]{64}$")
    }

    // --- Backup: per-profile startup landing (dynamic "startup_mode_<id>" keys) ---

    /** Exports all per-profile startup-mode keys as { "<profileId>": "<MODE>" }. */
    suspend fun exportStartupModes(): org.json.JSONObject {
        val prefix = "startup_mode_"
        val out = org.json.JSONObject()
        context.dataStore.data.first().asMap().forEach { (k, v) ->
            if (k.name.startsWith(prefix) && v is String) {
                out.put(k.name.removePrefix(prefix), v)
            }
        }
        return out
    }

    /** Exports all per-profile Home config blobs as { "<profileId>": { ... } }. */
    suspend fun exportHomeConfigs(): org.json.JSONObject {
        val prefix = "home_config_"
        val out = org.json.JSONObject()
        context.dataStore.data.first().asMap().forEach { (k, v) ->
            if (k.name.startsWith(prefix) && v is String) {
                val blob = runCatching { org.json.JSONObject(v) }.getOrNull() ?: return@forEach
                out.put(k.name.removePrefix(prefix), blob)
            }
        }
        return out
    }

    /** Restores startup modes only for profile ids in [existingProfileIds] (others are dropped safely). */
    suspend fun importStartupModes(o: org.json.JSONObject, existingProfileIds: Set<Long>) {
        context.dataStore.edit { prefs ->
            o.keys().forEach { key ->
                val pid = key.toLongOrNull() ?: return@forEach
                if (pid !in existingProfileIds) return@forEach
                val mode = o.optString(key).takeIf { it.isNotEmpty() } ?: return@forEach
                if (runCatching { StartupMode.valueOf(mode) }.isSuccess) {
                    prefs[stringPreferencesKey("startup_mode_$pid")] = mode
                }
            }
        }
    }

    /** Restores Home configs only for profile ids in [existingProfileIds] (others are dropped safely). */
    suspend fun importHomeConfigs(o: org.json.JSONObject, existingProfileIds: Set<Long>) {
        context.dataStore.edit { prefs ->
            o.keys().forEach { key ->
                val pid = key.toLongOrNull() ?: return@forEach
                if (pid !in existingProfileIds) return@forEach
                val blob = o.optJSONObject(key) ?: return@forEach
                prefs[homeConfigKey(pid)] = blob.toString()
            }
        }
    }

    // --- Backup: per-profile "hide new categories" preference (dynamic "hide_new_categories_<id>" keys) ---

    /** Exports all per-profile "hide new categories" preferences as { "<profileId>": true/false }. */
    suspend fun exportHideNewCategories(): org.json.JSONObject {
        val prefix = "hide_new_categories_"
        val out = org.json.JSONObject()
        context.dataStore.data.first().asMap().forEach { (k, v) ->
            if (k.name.startsWith(prefix) && v is Boolean) {
                out.put(k.name.removePrefix(prefix), v)
            }
        }
        return out
    }

    /** Restores the preference only for profile ids in [existingProfileIds] (others are dropped safely). */
    suspend fun importHideNewCategories(o: org.json.JSONObject, existingProfileIds: Set<Long>) {
        context.dataStore.edit { prefs ->
            o.keys().forEach { key ->
                val pid = key.toLongOrNull() ?: return@forEach
                if (pid !in existingProfileIds) return@forEach
                prefs[booleanPreferencesKey("hide_new_categories_$pid")] = o.getBoolean(key)
            }
        }
    }

    // --- Backup: per-source auto-refresh maps (ride with the SOURCES section, since source/EPG ids
    //     are preserved on restore). Exported as the raw { "<id>": "<EnumName>" } JSON maps. ---

    /** Exports the per-source playlist auto-refresh map as { "<sourceId>": "<mode>" }. */
    suspend fun exportPlaylistAutoRefresh(): org.json.JSONObject =
        context.dataStore.data.first()[Keys.PLAYLIST_AUTO_REFRESH]
            ?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: org.json.JSONObject()

    /** Exports the per-EPG-source auto-refresh map as { "<epgSourceId>": "<mode>" }. */
    suspend fun exportEpgAutoRefresh(): org.json.JSONObject =
        context.dataStore.data.first()[Keys.EPG_AUTO_REFRESH]
            ?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: org.json.JSONObject()

    /**
     * Restores the playlist auto-refresh map. Ids not in [existingSourceIds] are dropped; unknown
     * enum values fall back to OFF. Replaces the whole map (SOURCES restore wipes+recreates sources,
     * so pre-restore selections refer to deleted ids). Also marks the legacy refresh migration done
     * so it can never clobber the restored selections.
     */
    suspend fun importPlaylistAutoRefresh(o: org.json.JSONObject, existingSourceIds: Set<Long>) {
        val cleaned = sanitizeRefreshMap(o, existingSourceIds) { runCatching { PlaylistAutoRefresh.valueOf(it) }.getOrDefault(PlaylistAutoRefresh.OFF).name }
        context.dataStore.edit { prefs ->
            // Merge-restore: keep the device's existing per-source choices, backup entries win per key.
            val merged = parseRefreshMap(prefs[Keys.PLAYLIST_AUTO_REFRESH]) + cleaned
            prefs[Keys.PLAYLIST_AUTO_REFRESH] = org.json.JSONObject(merged as Map<*, *>).toString()
            prefs[Keys.REFRESH_MIGRATED] = true
        }
    }

    /** Restores the EPG auto-refresh map; same semantics as [importPlaylistAutoRefresh]. */
    suspend fun importEpgAutoRefresh(o: org.json.JSONObject, existingEpgSourceIds: Set<Long>) {
        val cleaned = sanitizeRefreshMap(o, existingEpgSourceIds) { runCatching { EpgAutoRefresh.valueOf(it) }.getOrDefault(EpgAutoRefresh.OFF).name }
        context.dataStore.edit { prefs ->
            val merged = parseRefreshMap(prefs[Keys.EPG_AUTO_REFRESH]) + cleaned
            prefs[Keys.EPG_AUTO_REFRESH] = org.json.JSONObject(merged as Map<*, *>).toString()
        }
    }

    /** Exports the per-EPG-source "use this feed's logos" map as { "<epgSourceId>": "true" }. */
    suspend fun exportEpgUseLogos(): org.json.JSONObject =
        context.dataStore.data.first()[Keys.EPG_USE_LOGOS]
            ?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: org.json.JSONObject()

    /** Restores the EPG logo-preference map; same merge semantics as [importEpgAutoRefresh]. */
    suspend fun importEpgUseLogos(o: org.json.JSONObject, existingEpgSourceIds: Set<Long>) {
        val cleaned = sanitizeRefreshMap(o, existingEpgSourceIds) { it.toBoolean().toString() }
        context.dataStore.edit { prefs ->
            val merged = parseRefreshMap(prefs[Keys.EPG_USE_LOGOS]) + cleaned
            prefs[Keys.EPG_USE_LOGOS] = org.json.JSONObject(merged as Map<*, *>).toString()
        }
    }

    private fun parseRefreshMap(raw: String?): Map<String, String> {
        val o = raw?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        o.keys().forEach { k -> out[k] = o.optString(k) }
        return out
    }

    private inline fun sanitizeRefreshMap(
        o: org.json.JSONObject,
        existingIds: Set<Long>,
        sanitize: (String) -> String,
    ): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        o.keys().forEach { key ->
            val id = key.toLongOrNull() ?: return@forEach
            if (id !in existingIds) return@forEach
            out[key] = sanitize(o.optString(key))
        }
        return out
    }

    // --- Backup: default source (SOURCES section — ids are preserved on restore) ---

    /** Currently selected default source id, or null when none chosen. */
    suspend fun currentDefaultSourceId(): Long? =
        context.dataStore.data.first()[Keys.DEFAULT_SOURCE]?.takeIf { it > 0 }

    /** Restores the default source only when that id survived the restore. */
    suspend fun importDefaultSource(id: Long, existingSourceIds: Set<Long>) {
        if (id in existingSourceIds) setDefaultSource(id)
    }

    // --- Backup: proxy password (handled out-of-band by BackupManager: encrypted or omitted) ---

    /** Current proxy password, for the backup layer to encrypt. Never logged. */
    suspend fun currentProxyPassword(): String = context.dataStore.data.first()[Keys.PROXY_PASS] ?: ""

    /** The user's own TMDB API key — a secret; BackupManager exports it encrypted-only (like the proxy password). */
    suspend fun currentTmdbApiKey(): String = context.dataStore.data.first()[Keys.TMDB_API_KEY] ?: ""

    /** Sets only the proxy password (used on restore once decrypted). Blank clears it. */
    suspend fun setProxyPassword(password: String) {
        context.dataStore.edit { it[Keys.PROXY_PASS] = password }
    }
}
