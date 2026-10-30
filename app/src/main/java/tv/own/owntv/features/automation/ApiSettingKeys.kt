package tv.own.owntv.features.automation

import org.json.JSONObject

/**
 * The settings a paired device may read and change, by their backup names. This is its own list on
 * purpose, not "whatever a backup carries": a paired device is trusted to run the TV, not to decide
 * where the app sends its traffic. So the keys that point the app at another machine or file are
 * left out (proxy, DNS, the metadata and subtitle servers, the download folder, the background
 * image path), and a key that backups gain later stays out until someone adds it here.
 */
object ApiSettingKeys {
    val allowed: Set<String> = setOf(
        // Look and layout
        "theme_mode", "accent_color", "accent_custom", "focus_highlight_color", "focus_highlight_width",
        "main_font_family", "popup_font_family", "font_size_percent", "popup_font_size_percent",
        "popup_size_percent", "ui_zoom_percent", "animation_level", "app_icon", "ui_language",
        "glass_preset", "glass_scope", "glass_alpha", "glass_blur", "glass_highlight",
        "glass_allow_full_transparency", "glass_depth_effects", "glass_glint",
        "bg_style", "bg_look", "bg_dim", "bg_blur", "bg_accent_light", "brand_accent_triangle",
        "ambient_glow_enabled", "ambient_glow_pulse", "clock_time_color", "clock_date_color", "clock_weather_color",
        "sheet_counts", "settings_quick_pinned",
        // Navigation and menus
        "nav_menu_mode", "nav_style", "nav_size", "nav_length", "nav_widen", "nav_menu_hidden", "nav_hide_after_ms",
        "phone_nav_labels", "phone_nav_hide_on_scroll", "remote_shortcut_bindings", "gesture_sensitivity_pct",
        "settings_menu_order_live", "settings_menu_order_movie", "settings_menu_order_series", "settings_menu_order_episode",
        // Live TV and guide
        "live_layout", "live_view", "live_preview", "live_preview_audio", "hero_preview", "guide_view", "guide_preview",
        "guide_show_empty", "guide_days_to_keep", "guide_density_pct", "guide_width_on", "guide_width_channels",
        "guide_width_epg", "sort_live", "sort_guide", "epg_offset_minutes", "direct_tune", "ch_nav_enabled",
        "ch_nav_up_skip", "ch_nav_down_skip", "live_left_right_rewinds", "live_rewind_step_sec",
        "resume_last_channel", "remember_last_live", "remember_cat_live", "last_live_category",
        "panel_w_live_on", "panel_w_live_cat", "panel_w_live_list", "panel_w_live_preview",
        "live_stage_sheet", "live_stage_list", "live_stage_preview", "reminder_mode", "reminder_lead_min",
        "multiview_enabled", "multiview_tiles", "multiview_warning_accepted",
        // Movies and series
        "vod_view_mode", "vod_layout", "vod_grid_columns", "episode_view_mode", "sort_movies", "sort_series",
        "remember_last_movies", "remember_last_series", "remember_cat_movies", "remember_cat_series",
        "last_movies_category", "last_series_category", "panel_w_movies_on", "panel_w_movies_cat",
        "panel_w_movies_list", "panel_w_movies_preview", "panel_w_series_on", "panel_w_series_cat",
        "panel_w_series_list", "panel_w_series_preview", "cinematic_details_movies", "cinematic_details_series",
        "cinematic_sheet_movies", "cinematic_sheet_series", "metadata_enabled", "metadata_mode", "metadata_language",
        "recent_searches",
        // Playback
        "default_zoom", "live_engine", "vod_engine", "vod_prefer_exo", "hw_decoding", "software_decode_above_1080p",
        "tunneled_playback", "hdr_enabled", "auto_frame_rate", "afr_match_resolution", "afr_pause_secs",
        "auto_frame_rate_prompted", "max_video_height", "mobile_data_max_video_height", "data_saver",
        "live_latency_mode", "live_latency_custom_secs", "live_preroll_secs", "live_tune_timeout_secs",
        "vod_buffer_secs", "vod_network_timeout_secs", "vod_reconnects", "seek_step_sec", "resume_mode",
        "auto_play_next", "timeshift_enabled", "timeshift_window_minutes", "timeshift_resume_mode",
        "catchup_timezone", "catchup_player", "catchup_offset_minutes", "measured_stream_stats", "detailed_diagnostics",
        "external_player", "external_player_live", "external_player_movies", "external_player_series",
        "background_playback", "mini_player_position", "mini_player_size_pct", "mini_player_style",
        "pip_enabled", "pip_size", "pip_on_back", "pip_snap",
        // Audio
        "default_volume", "audio_delay_ms", "pref_audio_lang", "surround_mode", "surround_sound", "audio_passthrough",
        "night_mode", "volume_levelling", "audio_per_channel", "audio_on_screen_off", "audio_on_mobile_data",
        // Subtitles
        "pref_sub_lang", "sub_search_langs", "sub_search_filter", "sub_style_enabled", "sub_color", "sub_font",
        "sub_position", "sub_bg_opacity", "sub_scale", "sub_scale_mpv", "sub_scale_exo",
        // Recording and downloads
        "recording_pre_roll_minutes", "recording_post_roll_minutes", "recording_reserve_connection",
        "record_what_im_watching", "recording_over_mobile_data", "download_watch_choice", "downloads_wifi_only",
        // Home screen, weather and updates
        "android_tv_home", "weather_enabled", "weather_fahrenheit", "weather_location", "update_check_on_start",
    )

    /** The keys of [changes] the API does not take. */
    fun rejected(changes: JSONObject): List<String> = ApiValues.unknownKeys(changes, allowed)

    /** [all] narrowed to the keys the API takes, so reading never shows what writing would refuse. */
    fun visible(all: JSONObject): JSONObject = JSONObject().apply {
        all.keys().forEach { key -> if (key in allowed) put(key, all.get(key)) }
    }
}
