package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.FOLLOW_GLOBAL_LATENCY_SECS
import tv.own.owntv.R
import tv.own.owntv.core.settings.SubtitleStyle
import tv.own.owntv.player.ZoomMode
import tv.own.owntv.player.alignment
import tv.own.owntv.ui.components.FocusableSurface
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.core.player.EnginePreference
import tv.own.owntv.features.shell.components.AutoFrameRateWarningDialog
import tv.own.owntv.features.shell.components.LivePreviewPanelHiddenDialog
import tv.own.owntv.features.shell.components.surroundModeLabel
import tv.own.owntv.core.player.SurroundMode
import tv.own.owntv.core.player.TrackLanguages
import tv.own.owntv.features.shell.components.LocalSettingsRowTone
import tv.own.owntv.features.shell.components.colors
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.glass
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.ui.theme.asComposeFamily

// The six sections of this screen, in spine order — the mockup's Video Player Settings model.
private const val SECTION_ENGINE = 0
private const val SECTION_LIVE = 1
internal const val SECTION_SOUND = 2
private const val SECTION_SUBTITLES = 3
private const val SECTION_EPISODES = 4
private const val SECTION_DIAGNOSTICS = 5

/**
 * One row of this screen as Quick can show it: which section it lives in, and what to draw for it.
 * Quick pins keys, and a key pinned from in here has no row on the Settings root to borrow a title
 * from — so the catalogue below is what lets the pinned copy appear there and jump back to the real
 * row. Keep an entry in step with its row: the key on the row's `quickKey`, the same icon and title.
 */
/**
 * Pass as `stringResource(id, *NO_ARGS)` for a string with a literal `%` and no placeholder. The i18n
 * validator requires every `%` to be written `%%`, and only the formatting overload turns it back into
 * one — the plain overload shows "100%%".
 */
internal val NO_ARGS: Array<Any> = emptyArray()

internal data class VideoQuickRef(
    val key: String,
    val section: Int,
    val icon: OwnTVIcon,
    val titleRes: Int,
    val descRes: Int? = null,
)

/** The Subtitle appearance popup's rows as Settings search finds them; they open that popup's row, `vp_sub_style`. */
internal val SUBTITLE_APPEARANCE_SEARCH_ROWS: List<Int> = listOf(
    R.string.settings_subtitle_size, R.string.settings_subtitle_font, R.string.settings_subtitle_color_short,
    R.string.settings_subtitle_position_short, R.string.settings_subtitle_background_transparency,
)

/** Every row of this screen that can be pinned to Quick, in the order the sections show them. */
internal val VIDEO_QUICK_ROWS: List<VideoQuickRef> = listOf(
    VideoQuickRef("vp_hw", SECTION_ENGINE, OwnTVIcon.VIDEO, R.string.settings_hardware_decoding, R.string.settings_hardware_decoding_description),
    VideoQuickRef("vp_hdr", SECTION_ENGINE, OwnTVIcon.VIDEO, R.string.settings_quick_hdr, R.string.settings_hdr_description),
    VideoQuickRef("vp_max_quality", SECTION_ENGINE, OwnTVIcon.VIDEO, R.string.settings_max_video_quality, R.string.settings_max_video_quality_description),
    VideoQuickRef("vp_tunneled", SECTION_ENGINE, OwnTVIcon.VIDEO, R.string.settings_tunneled_playback, R.string.settings_tunneled_playback_description),
    VideoQuickRef("vp_afr", SECTION_ENGINE, OwnTVIcon.VIDEO, R.string.settings_auto_frame_rate, R.string.settings_auto_frame_rate_description),
    VideoQuickRef("vp_afr_pause", SECTION_ENGINE, OwnTVIcon.PAUSE, R.string.settings_afr_pause, R.string.settings_afr_pause_description),
    VideoQuickRef("vp_afr_resolution", SECTION_ENGINE, OwnTVIcon.ASPECT, R.string.settings_afr_resolution, R.string.settings_afr_resolution_description),
    VideoQuickRef("vp_multiview", SECTION_ENGINE, OwnTVIcon.LIST_GRID, R.string.settings_multiview, R.string.settings_multiview_description),
    VideoQuickRef("vp_multiview_tiles", SECTION_ENGINE, OwnTVIcon.LIST_GRID, R.string.settings_multiview_tiles_max, R.string.settings_multiview_description),
    VideoQuickRef("vp_live_engine", SECTION_ENGINE, OwnTVIcon.PLAY, R.string.settings_live_tv_player, R.string.settings_live_player_description),
    VideoQuickRef("vp_live_engine_sources", SECTION_ENGINE, OwnTVIcon.PLAY, R.string.settings_live_engine_per_playlist, R.string.settings_live_engine_per_playlist_description),
    VideoQuickRef("vp_reset_live_pins", SECTION_ENGINE, OwnTVIcon.PLAY, R.string.settings_reset_live_player_choices, R.string.settings_reset_live_player_choices_description),
    VideoQuickRef("vp_vod_engine", SECTION_ENGINE, OwnTVIcon.PLAY, R.string.settings_movies_series_player, R.string.settings_movies_player_description),
    VideoQuickRef("vp_vod_engine_sources", SECTION_ENGINE, OwnTVIcon.PLAY, R.string.settings_vod_engine_per_playlist, R.string.settings_vod_engine_per_playlist_description),
    VideoQuickRef("vp_reset_pins", SECTION_ENGINE, OwnTVIcon.PLAY, R.string.settings_reset_player_choices, R.string.settings_reset_player_choices_description),
    VideoQuickRef("vp_forget_fixes", SECTION_ENGINE, OwnTVIcon.REFRESH, R.string.settings_forget_stream_fixes, R.string.settings_forget_stream_fixes_description),
    VideoQuickRef("vp_external", SECTION_ENGINE, OwnTVIcon.PLAY, R.string.settings_external_player, R.string.settings_external_player_row_description),
    VideoQuickRef("vp_zoom", SECTION_ENGINE, OwnTVIcon.ASPECT, R.string.settings_default_zoom, R.string.settings_default_zoom_description),
    VideoQuickRef("vp_reset_zoom", SECTION_ENGINE, OwnTVIcon.ASPECT, R.string.settings_reset_saved_zoom, R.string.settings_reset_saved_zoom_description),
    VideoQuickRef("vp_seek_step", SECTION_ENGINE, OwnTVIcon.FORWARD, R.string.settings_seek_step, R.string.settings_seek_step_description),
    VideoQuickRef("vp_rewind_step", SECTION_ENGINE, OwnTVIcon.REWIND, R.string.settings_live_rewind_step, R.string.settings_live_rewind_step_description),
    VideoQuickRef("vp_vod_buffer", SECTION_ENGINE, OwnTVIcon.DOWNLOADS, R.string.settings_vod_buffer, R.string.settings_vod_buffer_description),
    VideoQuickRef("vp_vod_timeout", SECTION_ENGINE, OwnTVIcon.NETWORK, R.string.settings_vod_network_timeout, R.string.settings_vod_network_timeout_description),
    VideoQuickRef("vp_vod_reconnects", SECTION_ENGINE, OwnTVIcon.REFRESH, R.string.settings_vod_reconnects, R.string.settings_vod_reconnects_description),
    VideoQuickRef("vp_live_preview", SECTION_LIVE, OwnTVIcon.LIVE_TV, R.string.settings_quick_live_preview, R.string.settings_live_preview_description),
    VideoQuickRef("vp_preview_audio", SECTION_LIVE, OwnTVIcon.AUDIO, R.string.settings_preview_audio, R.string.settings_preview_audio_description),
    VideoQuickRef("vp_live_latency", SECTION_LIVE, OwnTVIcon.LIVE_TV, R.string.settings_live_latency, R.string.settings_live_latency_description),
    VideoQuickRef("vp_latency_sources", SECTION_LIVE, OwnTVIcon.LIVE_TV, R.string.settings_live_latency_per_playlist, R.string.settings_live_latency_per_playlist_description),
    VideoQuickRef("vp_preroll", SECTION_LIVE, OwnTVIcon.LIVE_TV, R.string.settings_live_preroll, R.string.settings_live_preroll_description),
    VideoQuickRef("vp_tune_timeout", SECTION_LIVE, OwnTVIcon.LIVE_TV, R.string.settings_live_tune_timeout, R.string.settings_live_tune_timeout_description),
    VideoQuickRef("vp_tune_timeout_sources", SECTION_LIVE, OwnTVIcon.LIVE_TV, R.string.settings_live_tune_timeout_per_playlist, R.string.settings_live_tune_timeout_per_playlist_description),
    VideoQuickRef("vp_preroll_sources", SECTION_LIVE, OwnTVIcon.LIVE_TV, R.string.settings_live_preroll_per_playlist, R.string.settings_live_preroll_per_playlist_description),
    VideoQuickRef("vp_channel_numbers", SECTION_LIVE, OwnTVIcon.LIVE_TV, R.string.settings_channel_numbers, R.string.settings_channel_numbers_description),
    VideoQuickRef("vp_live_left_right", SECTION_LIVE, OwnTVIcon.SEEK_BACK, R.string.settings_live_left_right_rewinds, R.string.settings_live_left_right_rewinds_description),
    VideoQuickRef("vp_timeshift", SECTION_LIVE, OwnTVIcon.REWIND, R.string.settings_timeshift, R.string.settings_timeshift_description),
    VideoQuickRef("vp_timeshift_window", SECTION_LIVE, OwnTVIcon.REWIND, R.string.settings_timeshift_window, R.string.settings_timeshift_window_description),
    VideoQuickRef("vp_volume", SECTION_SOUND, OwnTVIcon.VOLUME_HIGH, R.string.settings_default_volume, R.string.settings_default_volume_description),
    VideoQuickRef("vp_reset_volume", SECTION_SOUND, OwnTVIcon.VOLUME_HIGH, R.string.settings_reset_saved_volume, R.string.settings_reset_saved_volume_description),
    VideoQuickRef("vp_audio_lang", SECTION_SOUND, OwnTVIcon.AUDIO, R.string.settings_preferred_audio_language, R.string.settings_preferred_audio_language_description),
    VideoQuickRef("vp_surround", SECTION_SOUND, OwnTVIcon.AUDIO, R.string.settings_surround_sound),
    VideoQuickRef("vp_passthrough", SECTION_SOUND, OwnTVIcon.AUDIO, R.string.settings_audio_passthrough, R.string.settings_audio_passthrough_description),
    VideoQuickRef("vp_night_mode", SECTION_SOUND, OwnTVIcon.VOLUME_HIGH, R.string.settings_night_mode, R.string.settings_night_mode_description),
    VideoQuickRef("vp_volume_leveling", SECTION_SOUND, OwnTVIcon.VOLUME_HIGH, R.string.settings_volume_leveling, R.string.settings_volume_leveling_description),
    VideoQuickRef("vp_audio_sync", SECTION_SOUND, OwnTVIcon.AUDIO, R.string.settings_audio_sync, R.string.settings_audio_sync_description),
    VideoQuickRef("vp_reset_audio_delay", SECTION_SOUND, OwnTVIcon.AUDIO, R.string.settings_reset_saved_audio_delay, R.string.settings_reset_saved_audio_delay_description),
    VideoQuickRef("vp_sub_style", SECTION_SUBTITLES, OwnTVIcon.SUBTITLE, R.string.settings_subtitle_appearance, R.string.settings_subtitle_appearance_description),
    VideoQuickRef("vp_sub_lang", SECTION_SUBTITLES, OwnTVIcon.SUBTITLE, R.string.settings_preferred_subtitle_language, R.string.settings_preferred_language_description),
    VideoQuickRef("vp_resume", SECTION_EPISODES, OwnTVIcon.PLAY, R.string.settings_resume_playback, R.string.settings_resume_playback_description),
    VideoQuickRef("vp_autoplay", SECTION_EPISODES, OwnTVIcon.AUTOPLAY_NEXT, R.string.settings_autoplay_next, R.string.settings_autoplay_next_description),
    VideoQuickRef("vp_mini", SECTION_EPISODES, OwnTVIcon.PIP, R.string.settings_mini_player_root, R.string.settings_mini_player_root_description),
    VideoQuickRef("vp_measured_stats", SECTION_DIAGNOSTICS, OwnTVIcon.VIDEO, R.string.settings_measured_stats, R.string.settings_measured_stats_description),
    VideoQuickRef("vp_logging", SECTION_DIAGNOSTICS, OwnTVIcon.INFO, R.string.settings_detailed_playback_logging, R.string.settings_detailed_playback_logging_description),
    // N19 — no row, so nothing to find or pin, on a TV whose decoders cannot tunnel.
).filter { it.key != "vp_tunneled" || tv.own.owntv.player.Tunneling.supported }

/**
 * What a Video player row pinned to Quick shows and does over there.
 *
 * A pin is meant to be a copy of the row, not a link to it: the chip carries the same value the real
 * row shows, and a row that is a plain toggle flips in place on the Settings root. Rows whose value
 * comes from a dialog — and the two toggles that must ask before turning ON — leave [onToggle] null,
 * so pressing them still opens Video player settings on the row, with that row's dialog already up.
 */
internal class VideoQuickBinding(
    val chip: String?,
    val primaryChip: Boolean,
    val onToggle: (() -> Unit)?,
)

/** The live value and behaviour of one pinned Video player row. Mirrors that row, key for key. */
@Composable
internal fun videoQuickBinding(key: String, vm: SettingsViewModel): VideoQuickBinding? {
    fun toggle(chip: String, on: Boolean, flip: () -> Unit) = VideoQuickBinding(chip, on, flip)
    fun link(chip: String?, primary: Boolean = false) = VideoQuickBinding(chip, primary, null)

    @Composable
    fun onOff(on: Boolean) = stringResource(if (on) R.string.common_on else R.string.common_off)

    @Composable
    fun overrides(count: Int) =
        if (count == 0) stringResource(R.string.common_off)
        else pluralStringResource(R.plurals.settings_live_preroll_overrides, count, count)

    @Composable
    fun saved(count: Int) =
        if (count == 0) stringResource(R.string.settings_reset_player_choices_none)
        else pluralStringResource(R.plurals.settings_reset_player_choices_count, count, count)

    return when (key) {
        "vp_hw" -> {
            val on by vm.hwDecoding.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setHwDecoding(!on) }
        }
        "vp_hdr" -> {
            val on by vm.hdrEnabled.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setHdrEnabled(!on) }
        }
        "vp_max_quality" -> {
            val height by vm.maxVideoHeight.collectAsStateWithLifecycle()
            link(videoQualityLabel(height), height > 0)
        }
        "vp_tunneled" -> {
            val on by vm.tunneledPlayback.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setTunneledPlayback(!on) }
        }
        "vp_multiview" -> {
            val on by vm.multiviewEnabled.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setMultiviewEnabled(!on) }
        }
        "vp_multiview_tiles" -> {
            // The ceiling is picked in a dialog, so this one links back to the row rather than
            // cycling the number in place — [dialogForQuickKey] already routes it there.
            val tiles by vm.multiviewTiles.collectAsStateWithLifecycle()
            link(
                stringResource(R.string.settings_multiview_tiles_max_value, tiles),
                tiles > tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES,
            )
        }
        "vp_afr" -> {
            val on by vm.autoFrameRate.collectAsStateWithLifecycle()
            // Below Android 12 turning it ON asks first, and that warning lives on the screen itself.
            val needsWarning = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
            if (!on && needsWarning) link(onOff(on)) else toggle(onOff(on), on) { vm.setAutoFrameRate(!on) }
        }
        "vp_live_engine" -> {
            val engine by vm.liveEnginePreference.collectAsStateWithLifecycle()
            link(engineLabel(engine), engine != EnginePreference.EXO_FIRST)
        }
        "vp_live_engine_sources" -> {
            val sources by vm.sources.collectAsStateWithLifecycle()
            val count = sources.count { it.liveEnginePreference != null }
            link(overrides(count), count > 0)
        }
        "vp_vod_engine" -> {
            val engine by vm.vodEnginePreference.collectAsStateWithLifecycle()
            link(engineLabel(engine), engine != EnginePreference.MPV_FIRST)
        }
        "vp_vod_engine_sources" -> {
            val sources by vm.sources.collectAsStateWithLifecycle()
            val count = sources.count { it.vodEnginePreference != null }
            link(overrides(count), count > 0)
        }
        "vp_reset_pins" -> {
            val pins by vm.vodEnginePinCount.collectAsStateWithLifecycle()
            link(saved(pins), pins > 0)
        }
        "vp_reset_live_pins" -> {
            val pins by vm.livePinCount.collectAsStateWithLifecycle()
            link(saved(pins), pins > 0)
        }
        "vp_forget_fixes" -> link(null)
        "vp_external" -> {
            val live by vm.externalPlayerLive.collectAsStateWithLifecycle()
            val movies by vm.externalPlayerMovies.collectAsStateWithLifecycle()
            val series by vm.externalPlayerSeries.collectAsStateWithLifecycle()
            link(externalPlayerChip(live, movies, series), live || movies || series)
        }
        "vp_zoom" -> {
            val zoom by vm.defaultZoom.collectAsStateWithLifecycle()
            link(stringResource(runCatching { ZoomMode.valueOf(zoom) }.getOrDefault(ZoomMode.FIT).labelRes))
        }
        "vp_reset_zoom" -> {
            val count by vm.savedZoomCount.collectAsStateWithLifecycle()
            link(saved(count), count > 0)
        }
        "vp_seek_step" -> {
            val secs by vm.seekStepSec.collectAsStateWithLifecycle()
            link(stringResource(R.string.settings_live_buffer_seconds, secs))
        }
        "vp_vod_buffer" -> {
            val secs by vm.vodBufferSecs.collectAsStateWithLifecycle()
            link(vodAutoOrSeconds(secs), secs > 0)
        }
        "vp_vod_timeout" -> {
            val secs by vm.vodNetworkTimeoutSecs.collectAsStateWithLifecycle()
            link(vodAutoOrSeconds(secs), secs > 0)
        }
        "vp_vod_reconnects" -> {
            val count by vm.vodReconnects.collectAsStateWithLifecycle()
            link(stringResource(R.string.settings_vod_reconnects_value, count), count > 1)
        }
        "vp_afr_pause" -> {
            val secs by vm.afrPauseSecs.collectAsStateWithLifecycle()
            link(afrPauseLabel(secs), secs > 0)
        }
        "vp_afr_resolution" -> {
            val on by vm.afrMatchResolution.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setAfrMatchResolution(!on) }
        }
        "vp_rewind_step" -> {
            val secs by vm.liveRewindStepSec.collectAsStateWithLifecycle()
            link(stringResource(R.string.settings_live_buffer_seconds, secs))
        }
        "vp_live_preview" -> {
            val on by vm.livePreviewEnabled.collectAsStateWithLifecycle()
            val panelActive by vm.livePreviewPanelActive.collectAsStateWithLifecycle()
            // Turning it ON with no room for the panel explains itself in a popup on the screen.
            if (!on && !panelActive) link(onOff(on)) else toggle(onOff(on), on) { vm.setLivePreviewEnabled(!on) }
        }
        "vp_preview_audio" -> {
            val on by vm.livePreviewAudio.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setLivePreviewAudio(!on) }
        }
        "vp_live_latency" -> {
            val mode by vm.liveLatencyMode.collectAsStateWithLifecycle()
            val custom by vm.liveLatencyCustomSecs.collectAsStateWithLifecycle()
            link(
                if (mode == tv.own.owntv.core.settings.LiveLatency.CUSTOM) stringResource(R.string.settings_live_buffer_seconds, custom)
                else stringResource(liveLatencyLabelRes(mode)),
            )
        }
        "vp_latency_sources" -> {
            val sources by vm.sources.collectAsStateWithLifecycle()
            val count = sources.count { it.liveLatencyMode != null }
            link(overrides(count), count > 0)
        }
        "vp_preroll" -> {
            val secs by vm.livePrerollSecs.collectAsStateWithLifecycle()
            link(if (secs <= 0) stringResource(R.string.common_off) else stringResource(R.string.settings_live_buffer_seconds, secs), secs > 0)
        }
        "vp_tune_timeout" -> {
            val secs by vm.liveTuneTimeoutSecs.collectAsStateWithLifecycle()
            link(if (secs <= 0) stringResource(R.string.common_never) else stringResource(R.string.settings_live_buffer_seconds, secs), secs > 0)
        }
        "vp_tune_timeout_sources" -> {
            val sources by vm.sources.collectAsStateWithLifecycle()
            val count = sources.count { it.liveTuneTimeoutSecs != null }
            link(overrides(count), count > 0)
        }
        "vp_preroll_sources" -> {
            val sources by vm.sources.collectAsStateWithLifecycle()
            val count = sources.count { it.livePrerollSecs >= 0 }
            link(overrides(count), count > 0)
        }
        "vp_channel_numbers" -> {
            val on by vm.directTune.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setDirectTune(!on) }
        }
        "vp_live_left_right" -> {
            val on by vm.liveLeftRightRewinds.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setLiveLeftRightRewinds(!on) }
        }
        "vp_timeshift" -> {
            val on by vm.timeshiftEnabled.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setTimeshiftEnabled(!on) }
        }
        "vp_timeshift_window" -> {
            val minutes by vm.timeshiftWindowMinutes.collectAsStateWithLifecycle()
            link(stringResource(R.string.player_duration_minutes, minutes))
        }
        "vp_volume" -> {
            val volume by vm.defaultVolume.collectAsStateWithLifecycle()
            link(stringResource(R.string.player_percent, volume))
        }
        "vp_reset_volume" -> {
            val count by vm.savedVolumeCount.collectAsStateWithLifecycle()
            link(saved(count), count > 0)
        }
        "vp_audio_lang" -> {
            val code by vm.preferredAudioLang.collectAsStateWithLifecycle()
            link(langName(code))
        }
        "vp_surround" -> {
            val mode by vm.surroundMode.collectAsStateWithLifecycle()
            toggle(surroundModeLabel(mode), mode != SurroundMode.STEREO) { vm.cycleSurroundMode() }
        }
        "vp_passthrough" -> {
            val on by vm.audioPassthrough.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setAudioPassthrough(!on) }
        }
        "vp_night_mode" -> {
            val on by vm.nightMode.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setNightMode(!on) }
        }
        "vp_volume_leveling" -> {
            val on by vm.volumeLevelling.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setVolumeLevelling(!on) }
        }
        "vp_audio_sync" -> {
            val delay by vm.audioDelayMs.collectAsStateWithLifecycle()
            link(stringResource(R.string.settings_audio_delay_value, delay))
        }
        "vp_reset_audio_delay" -> {
            val count by vm.savedAudioDelayCount.collectAsStateWithLifecycle()
            link(saved(count), count > 0)
        }
        "vp_sub_style" -> {
            val on by vm.subtitleStyleEnabled.collectAsStateWithLifecycle()
            link(onOff(on), on)
        }
        "vp_sub_lang" -> {
            val code by vm.preferredSubLang.collectAsStateWithLifecycle()
            link(langName(code))
        }
        "vp_resume" -> {
            val mode by vm.resumeMode.collectAsStateWithLifecycle()
            link(stringResource(resumeModeLabelRes(mode)))
        }
        "vp_autoplay" -> {
            val on by vm.autoPlayNext.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setAutoPlayNext(!on) }
        }
        "vp_measured_stats" -> {
            val on by vm.measuredStreamStats.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setMeasuredStreamStats(!on) }
        }
        "vp_logging" -> {
            val on by vm.detailedDiagnostics.collectAsStateWithLifecycle()
            toggle(onOff(on), on) { vm.setDetailedDiagnostics(!on) }
        }
        // vp_mini has no value of its own — the mini player row is a screen, nothing else.
        else -> null
    }
}

/**
 * What a [Row2] needs in order to be pinnable: the keys pinned right now, the handler for a held OK,
 * and which row a Quick shortcut arrived asking for. Screens that do not pin never provide it, so
 * their rows behave exactly as before.
 */
internal class QuickPinScope(
    val pinned: List<String>,
    val onHold: (String) -> Unit,
    val focusKey: String?,
    val focusRequester: FocusRequester,
)

internal val LocalQuickPin = androidx.compose.runtime.staticCompositionLocalOf<QuickPinScope?> { null }

/** Backdrop for both subtitle previews: a busy-ish frame, because a flat panel makes even a solid box look harmless. */
private val SUB_PREVIEW_BRUSH = androidx.compose.ui.graphics.Brush.linearGradient(
    listOf(Color(0xFF2E4A6B), Color(0xFF7A5C3E), Color(0xFF3B6B4A)),
)
private val SUB_SIZES = listOf(0.8f to R.string.settings_subtitle_small, 1.0f to R.string.settings_subtitle_normal, 1.3f to R.string.settings_subtitle_large, 1.6f to R.string.settings_subtitle_extra_large)

@Composable
private fun langName(code: String): String = when (code) {
    "" -> stringResource(R.string.settings_none_auto)
    TrackLanguages.ORIGINAL -> stringResource(R.string.settings_language_original)
    else -> TrackLanguages.displayName(code, LocalConfiguration.current.locales[0])
}

/** No preference first, then (audio only) the title's original language, then every language by name. */
@Composable
private fun languageOptions(withOriginal: Boolean): List<Pair<String, String>> {
    val display = LocalConfiguration.current.locales[0]
    val codes = listOf("") + listOfNotNull(TrackLanguages.ORIGINAL.takeIf { withOriginal }) +
        remember(display) { TrackLanguages.sortedFor(display) }
    return codes.map { it to langName(it) }
}

private fun nearestSubSize(scale: Float) = SUB_SIZES.minByOrNull { kotlin.math.abs(it.first - scale) } ?: SUB_SIZES[1]

/** The next size in the four-step table, wrapping Extra large back to Small — the size rows cycle. */
private fun nextSubSize(scale: Float): Float =
    SUB_SIZES[(SUB_SIZES.indexOf(nearestSubSize(scale)) + 1) % SUB_SIZES.size].first

@Composable
private fun subSizeName(scale: Float): String = stringResource(
    SUB_SIZES.minByOrNull { kotlin.math.abs(it.first - scale) }?.second ?: R.string.settings_subtitle_normal,
)

/** Chip for the one "Subtitle size" row now that there are two values: "Normal · Large", ExoPlayer first. */
@Composable
private fun subSizePairName(scaleExo: Float, scaleMpv: Float): String {
    val exo = subSizeName(scaleExo)
    val mpv = subSizeName(scaleMpv)
    return if (exo == mpv) exo else "$exo · $mpv"
}

private fun resumeModeLabelRes(mode: tv.own.owntv.core.settings.SettingsRepository.ResumeMode): Int = when (mode) {
    tv.own.owntv.core.settings.SettingsRepository.ResumeMode.AUTO -> R.string.settings_resume_always
    tv.own.owntv.core.settings.SettingsRepository.ResumeMode.ASK -> R.string.settings_resume_ask
    tv.own.owntv.core.settings.SettingsRepository.ResumeMode.NEVER -> R.string.settings_resume_never
}

private fun liveLatencyLabelRes(mode: tv.own.owntv.core.settings.LiveLatency): Int = when (mode) {
    tv.own.owntv.core.settings.LiveLatency.LOW -> R.string.settings_live_latency_low
    tv.own.owntv.core.settings.LiveLatency.BALANCED -> R.string.settings_live_latency_balanced
    tv.own.owntv.core.settings.LiveLatency.STABLE -> R.string.settings_live_latency_stable
    tv.own.owntv.core.settings.LiveLatency.CUSTOM -> R.string.settings_live_latency_custom
}

/**
 * Video Player settings — decoder, default aspect/zoom, subtitle size & language, audio sync. Each
 * value is persisted and applied to the shared mpv player (live where possible, otherwise next load).
 */
@Composable
fun VideoPlayerSettingsScreen(
    onBack: () -> Unit,
    /** Open the Mini player popup straight away — the settings search lists that setting by name. */
    openMiniPlayer: Boolean = false,
    /** Arriving from a Quick shortcut: the section to show and the row to put the cursor on. */
    openSection: Int? = null,
    focusRowKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val vm: SettingsViewModel = koinViewModel()
    val hw by vm.hwDecoding.collectAsStateWithLifecycle()
    val vodEngine by vm.vodEnginePreference.collectAsStateWithLifecycle()
    val liveEngine by vm.liveEnginePreference.collectAsStateWithLifecycle()
    val enginePins by vm.vodEnginePinCount.collectAsStateWithLifecycle()
    val livePins by vm.livePinCount.collectAsStateWithLifecycle()
    val defaultVolume by vm.defaultVolume.collectAsStateWithLifecycle()
    val savedZoom by vm.savedZoomCount.collectAsStateWithLifecycle()
    val savedVolume by vm.savedVolumeCount.collectAsStateWithLifecycle()
    val savedAudioDelay by vm.savedAudioDelayCount.collectAsStateWithLifecycle()
    val seekStep by vm.seekStepSec.collectAsStateWithLifecycle()
    val liveRewindStep by vm.liveRewindStepSec.collectAsStateWithLifecycle()
    val measuredStats by vm.measuredStreamStats.collectAsStateWithLifecycle()
    val detailedDiagnostics by vm.detailedDiagnostics.collectAsStateWithLifecycle()
    val directTune by vm.directTune.collectAsStateWithLifecycle()
    val liveLeftRightRewinds by vm.liveLeftRightRewinds.collectAsStateWithLifecycle()
    val timeshiftEnabled by vm.timeshiftEnabled.collectAsStateWithLifecycle()
    val timeshiftWindowMinutes by vm.timeshiftWindowMinutes.collectAsStateWithLifecycle()
    val afrPauseSecs by vm.afrPauseSecs.collectAsStateWithLifecycle()
    val vodBufferSecs by vm.vodBufferSecs.collectAsStateWithLifecycle()
    val vodNetworkTimeoutSecs by vm.vodNetworkTimeoutSecs.collectAsStateWithLifecycle()
    val vodReconnects by vm.vodReconnects.collectAsStateWithLifecycle()
    val afrMatchResolution by vm.afrMatchResolution.collectAsStateWithLifecycle()
    val externalLive by vm.externalPlayerLive.collectAsStateWithLifecycle()
    val externalMovies by vm.externalPlayerMovies.collectAsStateWithLifecycle()
    val externalSeries by vm.externalPlayerSeries.collectAsStateWithLifecycle()
    val zoom by vm.defaultZoom.collectAsStateWithLifecycle()
    val subStyleOn by vm.subtitleStyleEnabled.collectAsStateWithLifecycle()
    val subScaleExo by vm.subtitleScaleExo.collectAsStateWithLifecycle()
    val subScaleMpv by vm.subtitleScaleMpv.collectAsStateWithLifecycle()
    val subFont by vm.subtitleFont.collectAsStateWithLifecycle()
    val subColor by vm.subtitleColor.collectAsStateWithLifecycle()
    val subPosition by vm.subtitlePosition.collectAsStateWithLifecycle()
    val subBgOpacity by vm.subtitleBgOpacity.collectAsStateWithLifecycle()
    val audioDelay by vm.audioDelayMs.collectAsStateWithLifecycle()
    val audioLang by vm.preferredAudioLang.collectAsStateWithLifecycle()
    val subLang by vm.preferredSubLang.collectAsStateWithLifecycle()
    val resumeMode by vm.resumeMode.collectAsStateWithLifecycle()
    val liveLatency by vm.liveLatencyMode.collectAsStateWithLifecycle()
    val liveCustomSecs by vm.liveLatencyCustomSecs.collectAsStateWithLifecycle()
    val livePreroll by vm.livePrerollSecs.collectAsStateWithLifecycle()
    val liveTuneTimeout by vm.liveTuneTimeoutSecs.collectAsStateWithLifecycle()
    // The playback settings that also appear on the Settings root. They live here too so that Video
    // player is the one complete list; the root rows are shortcuts to the same values (item 14).
    val hdr by vm.hdrEnabled.collectAsStateWithLifecycle()
    val autoFrameRate by vm.autoFrameRate.collectAsStateWithLifecycle()
    val multiviewEnabled by vm.multiviewEnabled.collectAsStateWithLifecycle()
    val multiviewTiles by vm.multiviewTiles.collectAsStateWithLifecycle()
    val multiviewWarningAccepted by vm.multiviewWarningAccepted.collectAsStateWithLifecycle()
    val surroundMode by vm.surroundMode.collectAsStateWithLifecycle()
    val audioPassthrough by vm.audioPassthrough.collectAsStateWithLifecycle()
    val nightMode by vm.nightMode.collectAsStateWithLifecycle()
    val volumeLevelling by vm.volumeLevelling.collectAsStateWithLifecycle()
    val maxVideoHeight by vm.maxVideoHeight.collectAsStateWithLifecycle()
    val tunneledPlayback by vm.tunneledPlayback.collectAsStateWithLifecycle()
    val tunnelingFailed by vm.tunnelingFailed.collectAsStateWithLifecycle()
    val autoPlayNext by vm.autoPlayNext.collectAsStateWithLifecycle()
    val livePreview by vm.livePreviewEnabled.collectAsStateWithLifecycle()
    val previewAudio by vm.livePreviewAudio.collectAsStateWithLifecycle()
    val livePreviewPanelActive by vm.livePreviewPanelActive.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    // The playlist whose per-playlist "Pre-buffer" override is being edited.
    var prerollSource by remember { mutableStateOf<tv.own.owntv.core.database.entity.SourceEntity?>(null) }
    // Same, for the per-playlist Live TV engine and Live latency overrides.
    var engineSource by remember { mutableStateOf<tv.own.owntv.core.database.entity.SourceEntity?>(null) }
    var vodEngineSource by remember { mutableStateOf<tv.own.owntv.core.database.entity.SourceEntity?>(null) }
    var tuneTimeoutSource by remember { mutableStateOf<tv.own.owntv.core.database.entity.SourceEntity?>(null) }
    var latencySource by remember { mutableStateOf<tv.own.owntv.core.database.entity.SourceEntity?>(null) }
    // Low-latency acknowledgement popup (shown for "Low latency" and below-Balanced custom values).
    // First lambda runs on "I understand", second on "Cancel".
    var lowWarning by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }

    // OpenSubtitles account lives as an in-place sub-screen of this tab (plan §15). These three
    // are declared before the early return so they survive while the sub-screen is shown — that's
    // what lets Back land focus on the row that opened it instead of the top of the list.
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    /** Whether the Custom-latency stepper actually set a value this time round — the mode switch to
     *  Custom, and the low-latency acknowledgement, both hang off that rather than off merely opening it. */
    var customCommitted by remember { mutableStateOf(false) }
    val selectedSectionFocus = remember { FocusRequester() }
    /** Which of the six sections the spine has selected, and whose rows the sheet is showing. */
    var section by rememberSaveable { mutableIntStateOf(openSection ?: 0) }
    // Rows here can be pinned to Quick just like the ones on the Settings root: hold OK for the menu.
    val quickPinned by vm.quickPinnedKeys.collectAsStateWithLifecycle()
    /** The row whose hold-OK menu is open, then the row that menu owes its focus back to. */
    var menuKey by remember { mutableStateOf<String?>(null) }
    var menuReturnKey by remember { mutableStateOf<String?>(null) }
    val rowReturnFocus = remember { FocusRequester() }
    LaunchedEffect(menuReturnKey) {
        if (menuReturnKey == null) return@LaunchedEffect
        kotlinx.coroutines.delay(60)
        if (runCatching { rowReturnFocus.requestFocus() }.isFailure) {
            runCatching { selectedSectionFocus.requestFocus() }
        }
    }
    // A Quick shortcut names the row it was pinned from — land the cursor on it, not on the sections.
    LaunchedEffect(focusRowKey) {
        if (focusRowKey == null) return@LaunchedEffect
        // A pinned row whose value lives in a dialog opens that dialog straight away: the pin should
        // land the user where pressing the real row would, not one press short of it.
        dialogForQuickKey(focusRowKey)?.let { dialog = it }
        repeat(10) {
            kotlinx.coroutines.delay(50)
            if (runCatching { rowReturnFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    // Kick focus into the group; the group's onEnter (below) decides the actual target.
    //
    // NOT when returning from the Mini player sub-screen. Requesting focus here would win the race
    // against the scroll-restore effect below, which then snapped the list back to the top one frame
    // later and left the highlight on a row that had scrolled off screen. That effect already does
    // the two steps in the right order — scroll first, then focus the pending return row — so on this
    // one entry it is left to do the whole job.
    LaunchedEffect(Unit) {
        if (openMiniPlayer) {
            // Arriving straight on the Mini player popup: show the section that owns that row, so
            // closing the popup has somewhere to put the cursor back.
            section = SECTION_EPISODES
            dialog = Dialog.MINI_PLAYER
        } else if (focusRowKey == null) {
            runCatching { selectedSectionFocus.requestFocus() }
        }
    }
    BackHandler { onBack() }

    // Dialog-close focus return: closing a picker refocuses the row that opened it. The restore
    // request crosses INTO this screen's focus group from the dialog, so the group's onEnter
    // intercepts it — it consults dialogReturn first (and clears it) instead of hijacking.
    val dialogRowFocus = remember { Dialog.entries.associateWith { FocusRequester() } }
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    // Hoisted scroll state: snapshot at click time, restore on dialog close, so the list doesn't
    // visibly jump/scroll-animate when a scrim picker opens or closes over it (same fix as the
    // Settings root list — Compose resets the scrollable's offset when a scrim dialog tears down).
    val scrollState = rememberScrollState()
    var savedScroll by remember { mutableIntStateOf(0) }
    // The tile count the warning is being asked about, so "Use 4 anyway" knows which number it meant.
    var pendingTiles by remember { mutableIntStateOf(tv.own.owntv.core.live.MAX_MULTIVIEW_TILES) }
    val anyDialogOpen = dialog != Dialog.NONE || lowWarning != null
    LaunchedEffect(dialog, lowWarning) {
        if (dialog != Dialog.NONE) {
            // The custom-seconds dialog has no row of its own — it belongs to the Live latency row.
            val returnRow = when (dialog) {
                Dialog.LIVE_CUSTOM -> Dialog.LIVE_LATENCY
                // The per-playlist value picker belongs to the playlist row that opened it.
                Dialog.LIVE_PREROLL_SOURCE -> Dialog.LIVE_PREROLL_SOURCES
                Dialog.LIVE_ENGINE_SOURCE -> Dialog.LIVE_ENGINE_SOURCES
                Dialog.VOD_ENGINE_SOURCE -> Dialog.VOD_ENGINE_SOURCES
                Dialog.LIVE_TUNE_TIMEOUT_SOURCE -> Dialog.LIVE_TUNE_TIMEOUT_SOURCES
                Dialog.LIVE_LATENCY_SOURCE, Dialog.LIVE_LATENCY_CUSTOM_SOURCE -> Dialog.LIVE_LATENCY_SOURCES
                else -> dialog
            }
            dialogReturn = dialogRowFocus.getValue(returnRow)
        } else if (lowWarning != null) {
            // The warning popup has no row of its own — it always returns to the Live latency row.
            // Re-assert this here because the picker→popup transition lets focus dip back into the
            // list, firing onEnter and clearing dialogReturn before the popup grabs focus.
            dialogReturn = dialogRowFocus.getValue(Dialog.LIVE_LATENCY)
        } else {
            // Don't steal focus back to the row while the low-latency warning popup is up — it keeps
            // focus itself. Restore only once it (and every dialog) is closed, holding the scroll
            // offset still while focus lands — see [restoreAfterDialogClose].
            tv.own.owntv.ui.components.restoreAfterDialogClose(dialogReturn, scrollState, savedScroll)
        }
    }

    // Auto frame rate below Android 12 asks before turning ON: there is no way to query the display
    // for the refresh rates it can reach without blanking it. Turning it off stays immediate. This is
    // the same rule the root row follows, and both go through the same warning popup.
    val afrNeedsWarning = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
    val toggleAutoFrameRate = {
        if (!autoFrameRate && afrNeedsWarning) {
            savedScroll = scrollState.value; dialog = Dialog.AFR_WARNING
        } else {
            vm.setAutoFrameRate(!autoFrameRate)
        }
    }
    // Turning Live preview ON while the layout leaves no room for the preview panel would do nothing
    // visible, so say so rather than silently accepting it. Turning it off is always allowed.
    val toggleLivePreview = {
        if (livePreview) {
            vm.setLivePreviewEnabled(false)
        } else if (!livePreviewPanelActive) {
            savedScroll = scrollState.value; dialog = Dialog.LIVE_PREVIEW_PANEL
        } else {
            vm.setLivePreviewEnabled(true)
        }
    }

    val zoomMode = runCatching { ZoomMode.valueOf(zoom) }.getOrDefault(ZoomMode.FIT)

    // --- The spine stack. This screen keeps the two-column shape of the Settings root: the six
    // sections on the left, the selected section's rows on the right, and a back row at the head of
    // the spine saying where the screen sits. Focus selects, Right enters the rows, Left comes back.
    val perPlaylist = sources.isNotEmpty()
    val sectionNames = listOf(
        stringResource(R.string.settings_vp_section_engine),
        stringResource(R.string.settings_live_tv),
        stringResource(R.string.settings_vp_section_sound),
        stringResource(R.string.settings_subtitles),
        stringResource(R.string.settings_vp_section_episodes),
        stringResource(R.string.settings_diagnostics),
    )
    val sectionSummaries = listOf(
        stringResource(R.string.settings_vp_section_engine_summary),
        stringResource(R.string.settings_vp_section_live_summary),
        stringResource(R.string.settings_vp_section_sound_summary),
        stringResource(R.string.settings_vp_section_subtitles_summary),
        stringResource(R.string.settings_vp_section_episodes_summary),
        stringResource(R.string.settings_vp_section_diagnostics_summary),
    )
    val sectionIcons = listOf(
        OwnTVIcon.VIDEO, OwnTVIcon.LIVE_TV, OwnTVIcon.AUDIO,
        OwnTVIcon.SUBTITLE, OwnTVIcon.SKIP_NEXT, OwnTVIcon.INFO,
    )
    val sectionCounts = listOf(
        // + Maximum video quality, and Tunneled playback where a decoder supports it (P15).
        14 + (if (vm.tunnelingSupported) 1 else 0) + (if (perPlaylist) 2 else 0),
        // + Pause and rewind live TV, and its length while it is on (N4).
        6 + (if (timeshiftEnabled) 1 else 0) + (if (livePreview) 1 else 0) + (if (perPlaylist) 3 else 0),
        9, 2, 3, 2, // Sound: + passthrough, night mode, volume leveling (P14)
    )
    var sheetFocused by remember { mutableStateOf(false) }
    val spineScroll = rememberScrollState()

    // Back leaves the rows for the sections first, and only then the screen — the same step Left
    // makes, so whichever the user reaches for does the same thing.
    BackHandler(enabled = sheetFocused) { runCatching { selectedSectionFocus.requestFocus() } }
    // A new section starts at its first row. The scroll offset is shared by all six, so without this
    // a short section inherits a long one's offset and opens looking empty.
    LaunchedEffect(section) { scrollState.scrollTo(0) }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalQuickPin provides QuickPinScope(
            pinned = quickPinned,
            onHold = { menuKey = it },
            focusKey = menuReturnKey ?: focusRowKey,
            focusRequester = rowReturnFocus,
        ),
    ) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            // onEnter fires for any entry from outside the group — including our own dialog-close
            // restores (the dialogs live outside it) and the return from the OpenSubtitles sub-screen —
            // so it must prefer the pending return row over the section column.
            .focusProperties {
                onEnter = {
                    if (lowWarning != null) {
                        // The warning popup is opening and will grab focus itself. Route the
                        // transitional dip to the Live latency row WITHOUT clearing dialogReturn,
                        // so the popup-close restore still has a target to return to.
                        runCatching { dialogRowFocus.getValue(Dialog.LIVE_LATENCY).requestFocus() }
                    } else {
                        val target = dialogReturn
                        dialogReturn = null
                        runCatching { (target ?: selectedSectionFocus).requestFocus() }
                    }
                }
            }
            .focusGroup()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val paneShape = tv.own.owntv.features.shell.components.SettingsSkin.PaneShape
        Column(
            modifier = Modifier
                .width(tv.own.owntv.features.shell.components.SettingsSkin.SpineWidth)
                .fillMaxHeight()
                .clip(paneShape)
                .glass(surface = GlassSurface.CARDS, baseFill = colors.surfaceContainerLow, shape = paneShape)
                .border(1.dp, colors.outlineVariant, paneShape)
                .verticalScroll(spineScroll)
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)
                .focusProperties { onEnter = { runCatching { selectedSectionFocus.requestFocus() } } }
                .focusGroup(),
        ) {
            tv.own.owntv.features.shell.components.SpineBackRow(
                from = stringResource(R.string.settings_playback_group),
                title = stringResource(R.string.settings_video_player_title),
                onBack = onBack,
            )
            sectionNames.forEachIndexed { i, name ->
                tv.own.owntv.features.shell.components.SpineItem(
                    label = name,
                    summary = sectionSummaries[i],
                    icon = sectionIcons[i],
                    count = sectionCounts[i],
                    selected = i == section,
                    active = false,
                    onFocused = { section = i },
                    modifier = if (i == section) Modifier.focusRequester(selectedSectionFocus) else Modifier,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(paneShape)
                .glass(surface = GlassSurface.CARDS, baseFill = colors.surfaceContainerLow, shape = paneShape)
                .border(1.dp, colors.outlineVariant, paneShape),
        ) {
            tv.own.owntv.features.shell.components.SheetHeader(
                title = sectionNames[section],
                summary = sectionSummaries[section],
                tag = pluralStringResource(R.plurals.settings_setting_count, sectionCounts[section], sectionCounts[section]),
                tagHot = sheetFocused,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onFocusChanged { sheetFocused = it.hasFocus }
                    // Left is the way back to the sections. A full-width row has no neighbour to its
                    // left inside the sheet's own plate, so the sheet says it explicitly.
                    .onPreviewKeyEvent { e ->
                        if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                            e.key == androidx.compose.ui.input.key.Key.DirectionLeft
                        ) {
                            runCatching { selectedSectionFocus.requestFocus() }.isSuccess
                        } else {
                            false
                        }
                    }
                    .focusGroup()
                    .verticalScroll(scrollState)
                    .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when (section) {
                    SECTION_ENGINE -> {
        Row2(
            quickKey = "vp_hw",
            icon = OwnTVIcon.VIDEO, title = stringResource(R.string.settings_hardware_decoding),
            desc = stringResource(R.string.settings_hardware_decoding_description),
            chip = if (hw) stringResource(R.string.common_on) else stringResource(R.string.common_off), primaryChip = hw,
            onClick = { vm.setHwDecoding(!hw) },
        )
        Row2(
            quickKey = "vp_hdr",
            icon = OwnTVIcon.VIDEO, title = stringResource(R.string.settings_quick_hdr),
            desc = stringResource(R.string.settings_hdr_description),
            chip = stringResource(if (hdr) R.string.common_on else R.string.common_off), primaryChip = hdr,
            onClick = { vm.setHdrEnabled(!hdr) },
        )
        // N11 — the Settings limit; the player's Quality button picks within a stream.
        Row2(
            quickKey = "vp_max_quality",
            icon = OwnTVIcon.VIDEO, title = stringResource(R.string.settings_max_video_quality),
            desc = stringResource(R.string.settings_max_video_quality_description),
            chip = videoQualityLabel(maxVideoHeight), primaryChip = maxVideoHeight > 0, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.MAX_QUALITY)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.MAX_QUALITY },
        )
        // N19 — only where a decoder can tunnel; after a failure it says why it is off.
        if (vm.tunnelingSupported) {
            Row2(
                quickKey = "vp_tunneled",
                icon = OwnTVIcon.VIDEO, title = stringResource(R.string.settings_tunneled_playback),
                desc = stringResource(R.string.settings_tunneled_playback_description) +
                    if (tunnelingFailed && !tunneledPlayback) " " + stringResource(R.string.settings_tunneled_playback_failed) else "",
                chip = stringResource(if (tunneledPlayback) R.string.common_on else R.string.common_off), primaryChip = tunneledPlayback,
                onClick = { vm.setTunneledPlayback(!tunneledPlayback) },
            )
        }
        Row2(
            quickKey = "vp_afr",
            icon = OwnTVIcon.VIDEO, title = stringResource(R.string.settings_auto_frame_rate),
            desc = stringResource(R.string.settings_auto_frame_rate_description) +
                if (afrNeedsWarning) " " + stringResource(R.string.settings_auto_frame_rate_warning_suffix) else "",
            chip = stringResource(if (autoFrameRate) R.string.common_on else R.string.common_off), primaryChip = autoFrameRate,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.AFR_WARNING)),
            onClick = toggleAutoFrameRate,
        )
        // N7 — both only act for a film with Auto frame rate on; shown always, like every row here.
        Row2(
            quickKey = "vp_afr_pause",
            icon = OwnTVIcon.PAUSE, title = stringResource(R.string.settings_afr_pause),
            desc = stringResource(R.string.settings_afr_pause_description),
            chip = afrPauseLabel(afrPauseSecs), primaryChip = afrPauseSecs > 0, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.AFR_PAUSE)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.AFR_PAUSE },
        )
        Row2(
            quickKey = "vp_afr_resolution",
            icon = OwnTVIcon.ASPECT, title = stringResource(R.string.settings_afr_resolution),
            desc = stringResource(R.string.settings_afr_resolution_description),
            chip = stringResource(if (afrMatchResolution) R.string.common_on else R.string.common_off), primaryChip = afrMatchResolution,
            onClick = { vm.setAfrMatchResolution(!afrMatchResolution) },
        )
        Row2(
            quickKey = "vp_multiview",
            icon = OwnTVIcon.LIST_GRID, title = stringResource(R.string.settings_multiview),
            desc = stringResource(R.string.settings_multiview_description),
            chip = stringResource(if (multiviewEnabled) R.string.common_on else R.string.common_off),
            primaryChip = multiviewEnabled,
            onClick = { vm.setMultiviewEnabled(!multiviewEnabled) },
        )
        if (multiviewEnabled) {
            Row2(
                quickKey = "vp_multiview_tiles",
                icon = OwnTVIcon.LIST_GRID, title = stringResource(R.string.settings_multiview_tiles_max),
                desc = stringResource(R.string.settings_multiview_description),
                // "Max 4", not "4": the number is the ceiling, and the grid opens with two and grows
                // only when the user asks. A bare number read as "every grid is this big", which is
                // what it used to be and what made watching two channels impossible.
                chip = stringResource(R.string.settings_multiview_tiles_max_value, multiviewTiles),
                chevron = true,
                primaryChip = multiviewTiles > tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES,
                modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.MULTIVIEW_TILES)),
                onClick = { savedScroll = scrollState.value; dialog = Dialog.MULTIVIEW_TILES },
            )
        }
        Row2(
            quickKey = "vp_live_engine",
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_live_tv_player),
            desc = stringResource(R.string.settings_live_player_description),
            chip = engineLabel(liveEngine), chevron = true,
            primaryChip = liveEngine != EnginePreference.EXO_FIRST,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_ENGINE)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_ENGINE },
        )
        if (sources.isNotEmpty()) {
            Row2(
                quickKey = "vp_live_engine_sources",
                icon = OwnTVIcon.PLAY,
                title = stringResource(R.string.settings_live_engine_per_playlist),
                desc = stringResource(R.string.settings_live_engine_per_playlist_description),
                chip = sources.count { it.liveEnginePreference != null }.let { count ->
                    if (count == 0) stringResource(R.string.common_off)
                    else pluralStringResource(R.plurals.settings_live_preroll_overrides, count, count)
                },
                primaryChip = sources.any { it.liveEnginePreference != null },
                chevron = true,
                modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_ENGINE_SOURCES)),
                onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_ENGINE_SOURCES },
            )
        }
        Row2(
            quickKey = "vp_reset_live_pins",
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_reset_live_player_choices),
            desc = stringResource(R.string.settings_reset_live_player_choices_description),
            chip = if (livePins == 0) stringResource(R.string.settings_reset_player_choices_none)
            else pluralStringResource(R.plurals.settings_reset_player_choices_count, livePins, livePins),
            primaryChip = livePins > 0,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESET_LIVE_PINS)),
            // Opens the confirmation even with nothing pinned, as the films row does: a row that
            // swallows OK is a dead end on a remote.
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESET_LIVE_PINS },
        )
        Row2(
            quickKey = "vp_vod_engine",
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_movies_series_player),
            desc = stringResource(R.string.settings_movies_player_description),
            chip = engineLabel(vodEngine), chevron = true,
            primaryChip = vodEngine != EnginePreference.MPV_FIRST,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.VOD_ENGINE)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.VOD_ENGINE },
        )
        if (sources.isNotEmpty()) {
            Row2(
                quickKey = "vp_vod_engine_sources",
                icon = OwnTVIcon.PLAY,
                title = stringResource(R.string.settings_vod_engine_per_playlist),
                desc = stringResource(R.string.settings_vod_engine_per_playlist_description),
                chip = sources.count { it.vodEnginePreference != null }.let { count ->
                    if (count == 0) stringResource(R.string.common_off)
                    else pluralStringResource(R.plurals.settings_live_preroll_overrides, count, count)
                },
                primaryChip = sources.any { it.vodEnginePreference != null },
                chevron = true,
                modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.VOD_ENGINE_SOURCES)),
                onClick = { savedScroll = scrollState.value; dialog = Dialog.VOD_ENGINE_SOURCES },
            )
        }
        Row2(
            quickKey = "vp_reset_pins",
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_reset_player_choices),
            desc = stringResource(R.string.settings_reset_player_choices_description),
            chip = if (enginePins == 0) stringResource(R.string.settings_reset_player_choices_none)
            else pluralStringResource(R.plurals.settings_reset_player_choices_count, enginePins, enginePins),
            primaryChip = enginePins > 0,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESET_PINS)),
            // Opens the confirmation even with nothing pinned: a settings row that swallows the OK
            // press is a dead end on a remote, and the chip already says whether there is anything
            // to reset.
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESET_PINS },
        )
        Row2(
            quickKey = "vp_forget_fixes",
            icon = OwnTVIcon.REFRESH, title = stringResource(R.string.settings_forget_stream_fixes),
            desc = stringResource(R.string.settings_forget_stream_fixes_description),
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.FORGET_FIXES)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.FORGET_FIXES },
        )
        Row2(
            quickKey = "vp_external",
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_external_player),
            desc = stringResource(R.string.settings_external_player_row_description),
            chip = externalPlayerChip(externalLive, externalMovies, externalSeries), chevron = true,
            primaryChip = externalLive || externalMovies || externalSeries,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.EXTERNAL_PLAYER)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.EXTERNAL_PLAYER },
        )
        Row2(
            quickKey = "vp_zoom",
            icon = OwnTVIcon.ASPECT, title = stringResource(R.string.settings_default_zoom),
            desc = stringResource(R.string.settings_default_zoom_description),
            chip = stringResource(zoomMode.labelRes), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.ZOOM)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.ZOOM },
        )
        Row2(
            quickKey = "vp_reset_zoom",
            icon = OwnTVIcon.ASPECT, title = stringResource(R.string.settings_reset_saved_zoom),
            desc = stringResource(R.string.settings_reset_saved_zoom_description),
            chip = if (savedZoom == 0) stringResource(R.string.settings_reset_player_choices_none)
            else pluralStringResource(R.plurals.settings_reset_player_choices_count, savedZoom, savedZoom),
            primaryChip = savedZoom > 0,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESET_SAVED_ZOOM)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESET_SAVED_ZOOM },
        )
        Row2(
            quickKey = "vp_seek_step",
            icon = OwnTVIcon.FORWARD, title = stringResource(R.string.settings_seek_step),
            desc = stringResource(R.string.settings_seek_step_description),
            chip = stringResource(R.string.settings_live_buffer_seconds, seekStep), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.SEEK_STEP)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.SEEK_STEP },
        )
        Row2(
            quickKey = "vp_rewind_step",
            icon = OwnTVIcon.REWIND, title = stringResource(R.string.settings_live_rewind_step),
            desc = stringResource(R.string.settings_live_rewind_step_description),
            chip = stringResource(R.string.settings_live_buffer_seconds, liveRewindStep), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_REWIND_STEP)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_REWIND_STEP },
        )
        // N18 — films, episodes and catch-up only; live keeps its own latency and give-up settings.
        Row2(
            quickKey = "vp_vod_buffer",
            icon = OwnTVIcon.DOWNLOADS, title = stringResource(R.string.settings_vod_buffer),
            desc = stringResource(R.string.settings_vod_buffer_description),
            chip = vodAutoOrSeconds(vodBufferSecs), primaryChip = vodBufferSecs > 0, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.VOD_BUFFER)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.VOD_BUFFER },
        )
        Row2(
            quickKey = "vp_vod_timeout",
            icon = OwnTVIcon.NETWORK, title = stringResource(R.string.settings_vod_network_timeout),
            desc = stringResource(R.string.settings_vod_network_timeout_description),
            chip = vodAutoOrSeconds(vodNetworkTimeoutSecs), primaryChip = vodNetworkTimeoutSecs > 0, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.VOD_TIMEOUT)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.VOD_TIMEOUT },
        )
        Row2(
            quickKey = "vp_vod_reconnects",
            icon = OwnTVIcon.REFRESH, title = stringResource(R.string.settings_vod_reconnects),
            desc = stringResource(R.string.settings_vod_reconnects_description),
            chip = stringResource(R.string.settings_vod_reconnects_value, vodReconnects), primaryChip = vodReconnects > 1, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.VOD_RECONNECTS)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.VOD_RECONNECTS },
        )
                    }
                    SECTION_SOUND -> {
        Row2(
            quickKey = "vp_volume",
            icon = OwnTVIcon.VOLUME_HIGH, title = stringResource(R.string.settings_default_volume),
            desc = stringResource(R.string.settings_default_volume_description, *NO_ARGS),
            chip = stringResource(R.string.player_percent, defaultVolume), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.VOLUME)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.VOLUME },
        )
        Row2(
            quickKey = "vp_reset_volume",
            icon = OwnTVIcon.VOLUME_HIGH, title = stringResource(R.string.settings_reset_saved_volume),
            desc = stringResource(R.string.settings_reset_saved_volume_description),
            chip = if (savedVolume == 0) stringResource(R.string.settings_reset_player_choices_none)
            else pluralStringResource(R.plurals.settings_reset_player_choices_count, savedVolume, savedVolume),
            primaryChip = savedVolume > 0,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESET_SAVED_VOLUME)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESET_SAVED_VOLUME },
        )
        Row2(
            quickKey = "vp_audio_lang",
            icon = OwnTVIcon.AUDIO, title = stringResource(R.string.settings_preferred_audio_language),
            desc = stringResource(R.string.settings_preferred_audio_language_description),
            chip = langName(audioLang), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.AUDIO_LANG)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.AUDIO_LANG },
        )
        Row2(
            quickKey = "vp_surround",
            icon = OwnTVIcon.AUDIO, title = stringResource(R.string.settings_surround_sound),
            desc = when (surroundMode) {
                SurroundMode.AUTO -> stringResource(R.string.settings_surround_auto_description)
                SurroundMode.STEREO -> stringResource(R.string.settings_surround_stereo_description)
                SurroundMode.SURROUND -> stringResource(R.string.settings_surround_forced_description)
            },
            chip = surroundModeLabel(surroundMode), primaryChip = surroundMode != SurroundMode.STEREO,
            onClick = { vm.cycleSurroundMode() },
        )
        Row2(
            quickKey = "vp_passthrough",
            icon = OwnTVIcon.AUDIO, title = stringResource(R.string.settings_audio_passthrough),
            desc = stringResource(R.string.settings_audio_passthrough_description),
            chip = stringResource(if (audioPassthrough) R.string.common_on else R.string.common_off), primaryChip = audioPassthrough,
            onClick = { vm.setAudioPassthrough(!audioPassthrough) },
        )
        Row2(
            quickKey = "vp_night_mode",
            icon = OwnTVIcon.VOLUME_HIGH, title = stringResource(R.string.settings_night_mode),
            desc = stringResource(R.string.settings_night_mode_description),
            chip = stringResource(if (nightMode) R.string.common_on else R.string.common_off), primaryChip = nightMode,
            onClick = { vm.setNightMode(!nightMode) },
        )
        Row2(
            quickKey = "vp_volume_leveling",
            icon = OwnTVIcon.VOLUME_HIGH, title = stringResource(R.string.settings_volume_leveling),
            desc = stringResource(R.string.settings_volume_leveling_description),
            chip = stringResource(if (volumeLevelling) R.string.common_on else R.string.common_off), primaryChip = volumeLevelling,
            onClick = { vm.setVolumeLevelling(!volumeLevelling) },
        )
        Row2(
            quickKey = "vp_audio_sync",
            icon = OwnTVIcon.AUDIO, title = stringResource(R.string.settings_audio_sync),
            desc = stringResource(R.string.settings_audio_sync_description),
            chip = stringResource(R.string.settings_audio_delay_value, audioDelay), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.AUDIO_SYNC)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.AUDIO_SYNC },
        )
        Row2(
            quickKey = "vp_reset_audio_delay",
            icon = OwnTVIcon.AUDIO, title = stringResource(R.string.settings_reset_saved_audio_delay),
            desc = stringResource(R.string.settings_reset_saved_audio_delay_description),
            chip = if (savedAudioDelay == 0) stringResource(R.string.settings_reset_player_choices_none)
            else pluralStringResource(R.plurals.settings_reset_player_choices_count, savedAudioDelay, savedAudioDelay),
            primaryChip = savedAudioDelay > 0,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESET_SAVED_AUDIO_DELAY)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESET_SAVED_AUDIO_DELAY },
        )
                    }
                    SECTION_EPISODES -> {
        Row2(
            quickKey = "vp_resume",
            icon = OwnTVIcon.PLAY, title = stringResource(R.string.settings_resume_playback),
            desc = stringResource(R.string.settings_resume_playback_description),
            chip = stringResource(resumeModeLabelRes(resumeMode)), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESUME)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.RESUME },
        )
        Row2(
            quickKey = "vp_autoplay",
            icon = OwnTVIcon.AUTOPLAY_NEXT, title = stringResource(R.string.settings_autoplay_next),
            desc = stringResource(R.string.settings_autoplay_next_description),
            chip = stringResource(if (autoPlayNext) R.string.common_on else R.string.common_off), primaryChip = autoPlayNext,
            onClick = { vm.setAutoPlayNext(!autoPlayNext) },
        )
        Row2(
            quickKey = "vp_mini",
            icon = OwnTVIcon.PIP, title = stringResource(R.string.settings_mini_player_root),
            desc = stringResource(R.string.settings_mini_player_root_description),
            chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.MINI_PLAYER)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.MINI_PLAYER },
        )

                    }
                    SECTION_SUBTITLES -> {
        Row2(
            quickKey = "vp_sub_style",
            icon = OwnTVIcon.SUBTITLE, title = stringResource(R.string.settings_subtitle_appearance),
            desc = stringResource(R.string.settings_subtitle_appearance_description),
            chip = stringResource(if (subStyleOn) R.string.common_on else R.string.common_off), primaryChip = subStyleOn, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.SUB_STYLE)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.SUB_STYLE },
        )
        Row2(
            quickKey = "vp_sub_lang",
            icon = OwnTVIcon.SUBTITLE, title = stringResource(R.string.settings_preferred_subtitle_language),
            desc = stringResource(R.string.settings_preferred_language_description),
            chip = langName(subLang), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.SUB_LANG)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.SUB_LANG },
        )

                    }
                    SECTION_LIVE -> {
        Row2(
            quickKey = "vp_live_preview",
            icon = OwnTVIcon.LIVE_TV, title = stringResource(R.string.settings_quick_live_preview),
            desc = stringResource(R.string.settings_live_preview_description),
            chip = stringResource(if (livePreview) R.string.common_on else R.string.common_off), primaryChip = livePreview,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_PREVIEW_PANEL)),
            onClick = toggleLivePreview,
        )
        if (livePreview) {
            Row2(
                quickKey = "vp_preview_audio",
                icon = OwnTVIcon.AUDIO, title = stringResource(R.string.settings_preview_audio),
                desc = stringResource(R.string.settings_preview_audio_description),
                chip = stringResource(if (previewAudio) R.string.common_on else R.string.common_off), primaryChip = previewAudio,
                onClick = { vm.setLivePreviewAudio(!previewAudio) },
            )
        }
        Row2(
            quickKey = "vp_live_latency",
            icon = OwnTVIcon.LIVE_TV, title = stringResource(R.string.settings_live_latency),
            // The requested depth is a time, but the buffer is also capped in BYTES
            // (`LiveBuffer.targetBufferBytes`), and on a 4K feed that cap is what binds. The code has
            // always handled it; the user was never told, so a 60 s setting that behaved like far less
            // looked like a bug rather than a memory limit.
            desc = stringResource(R.string.settings_live_latency_description) + " " +
                stringResource(R.string.settings_live_latency_bitrate_note),
            chip = if (liveLatency == tv.own.owntv.core.settings.LiveLatency.CUSTOM) stringResource(R.string.settings_live_buffer_seconds, liveCustomSecs) else stringResource(liveLatencyLabelRes(liveLatency)),
            chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_LATENCY)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_LATENCY },
        )
        if (sources.isNotEmpty()) {
            Row2(
                quickKey = "vp_latency_sources",
                icon = OwnTVIcon.LIVE_TV,
                title = stringResource(R.string.settings_live_latency_per_playlist),
                desc = stringResource(R.string.settings_live_latency_per_playlist_description),
                chip = sources.count { it.liveLatencyMode != null }.let { count ->
                    if (count == 0) stringResource(R.string.common_off)
                    else pluralStringResource(R.plurals.settings_live_preroll_overrides, count, count)
                },
                primaryChip = sources.any { it.liveLatencyMode != null },
                chevron = true,
                modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_LATENCY_SOURCES)),
                onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_LATENCY_SOURCES },
            )
        }
        Row2(
            quickKey = "vp_preroll",
            icon = OwnTVIcon.LIVE_TV,
            title = stringResource(R.string.settings_live_preroll),
            desc = stringResource(R.string.settings_live_preroll_description),
            chip = if (livePreroll <= 0) {
                stringResource(R.string.common_off)
            } else {
                stringResource(R.string.settings_live_buffer_seconds, livePreroll)
            },
            primaryChip = livePreroll > 0,
            chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_PREROLL)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_PREROLL },
        )
        Row2(
            quickKey = "vp_tune_timeout",
            icon = OwnTVIcon.LIVE_TV,
            title = stringResource(R.string.settings_live_tune_timeout),
            desc = stringResource(R.string.settings_live_tune_timeout_description),
            chip = if (liveTuneTimeout <= 0) {
                stringResource(R.string.common_never)
            } else {
                stringResource(R.string.settings_live_buffer_seconds, liveTuneTimeout)
            },
            primaryChip = liveTuneTimeout > 0,
            chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_TUNE_TIMEOUT)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_TUNE_TIMEOUT },
        )
        if (sources.isNotEmpty()) {
            Row2(
                quickKey = "vp_tune_timeout_sources",
                icon = OwnTVIcon.LIVE_TV,
                title = stringResource(R.string.settings_live_tune_timeout_per_playlist),
                desc = stringResource(R.string.settings_live_tune_timeout_per_playlist_description),
                chip = sources.count { it.liveTuneTimeoutSecs != null }.let { count ->
                    if (count == 0) stringResource(R.string.common_off)
                    else pluralStringResource(R.plurals.settings_live_preroll_overrides, count, count)
                },
                primaryChip = sources.any { it.liveTuneTimeoutSecs != null },
                chevron = true,
                modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_TUNE_TIMEOUT_SOURCES)),
                onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_TUNE_TIMEOUT_SOURCES },
            )
        }
        if (sources.isNotEmpty()) {
            Row2(
                quickKey = "vp_preroll_sources",
                icon = OwnTVIcon.LIVE_TV,
                title = stringResource(R.string.settings_live_preroll_per_playlist),
                desc = stringResource(R.string.settings_live_preroll_per_playlist_description),
                chip = sources.count { it.livePrerollSecs >= 0 }.let { count ->
                    if (count == 0) stringResource(R.string.common_off)
                    else pluralStringResource(R.plurals.settings_live_preroll_overrides, count, count)
                },
                primaryChip = sources.any { it.livePrerollSecs >= 0 },
                chevron = true,
                modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.LIVE_PREROLL_SOURCES)),
                onClick = { savedScroll = scrollState.value; dialog = Dialog.LIVE_PREROLL_SOURCES },
            )
        }
        Row2(
            quickKey = "vp_channel_numbers",
            icon = OwnTVIcon.LIVE_TV, title = stringResource(R.string.settings_channel_numbers),
            desc = stringResource(R.string.settings_channel_numbers_description),
            chip = if (directTune) stringResource(R.string.common_on) else stringResource(R.string.common_off), primaryChip = directTune,
            onClick = { vm.setDirectTune(!directTune) },
        )
        Row2(
            quickKey = "vp_live_left_right",
            icon = OwnTVIcon.SEEK_BACK, title = stringResource(R.string.settings_live_left_right_rewinds),
            desc = stringResource(R.string.settings_live_left_right_rewinds_description),
            chip = if (liveLeftRightRewinds) stringResource(R.string.common_on) else stringResource(R.string.common_off), primaryChip = liveLeftRightRewinds,
            onClick = { vm.setLiveLeftRightRewinds(!liveLeftRightRewinds) },
        )
        Row2(
            quickKey = "vp_timeshift",
            icon = OwnTVIcon.REWIND, title = stringResource(R.string.settings_timeshift),
            desc = stringResource(R.string.settings_timeshift_description),
            chip = if (timeshiftEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off), primaryChip = timeshiftEnabled,
            onClick = { vm.setTimeshiftEnabled(!timeshiftEnabled) },
        )
        // The length means nothing while saving is off, so it appears with the switch.
        if (timeshiftEnabled) Row2(
            quickKey = "vp_timeshift_window",
            icon = OwnTVIcon.REWIND, title = stringResource(R.string.settings_timeshift_window),
            desc = stringResource(R.string.settings_timeshift_window_description),
            chip = stringResource(R.string.player_duration_minutes, timeshiftWindowMinutes), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.TIMESHIFT_WINDOW)),
            onClick = { savedScroll = scrollState.value; dialog = Dialog.TIMESHIFT_WINDOW },
        )

                    }
                    else -> {
        Row2(
            quickKey = "vp_measured_stats",
            icon = OwnTVIcon.VIDEO, title = stringResource(R.string.settings_measured_stats),
            desc = stringResource(R.string.settings_measured_stats_description),
            chip = if (measuredStats) stringResource(R.string.common_on) else stringResource(R.string.common_off), primaryChip = measuredStats,
            onClick = { vm.setMeasuredStreamStats(!measuredStats) },
        )
        Row2(
            quickKey = "vp_logging",
            icon = OwnTVIcon.INFO, title = stringResource(R.string.settings_detailed_playback_logging),
            desc = stringResource(R.string.settings_detailed_playback_logging_description),
            chip = stringResource(if (detailedDiagnostics) R.string.common_on else R.string.common_off), primaryChip = detailedDiagnostics,
            onClick = { vm.setDetailedDiagnostics(!detailedDiagnostics) },
        )
                    }
                }
            }
        }
    }
    }

    menuKey?.let { key ->
        val ref = VIDEO_QUICK_ROWS.first { it.key == key }
        val at = quickPinned.indexOf(key)
        // Order belongs to the Quick list, which is not on screen here — so this menu only says
        // whether the row is pinned.
        tv.own.owntv.features.shell.components.SettingsRowMenu(
            title = stringResource(ref.titleRes),
            pinned = at >= 0,
            canMoveUp = false,
            canMoveDown = false,
            onPinToggle = {
                vm.setQuickPinnedKeys(if (at >= 0) quickPinned - key else quickPinned + key)
            },
            onMoveUp = {},
            onMoveDown = {},
            onDismiss = { menuReturnKey = key; menuKey = null },
        )
    }

    when (dialog) {
        Dialog.LIVE_ENGINE -> PickerDialog(
            title = stringResource(R.string.settings_live_tv_player),
            options = engineOptions(default = EnginePreference.EXO_FIRST),
            selected = liveEngine.name,
            onSelect = { vm.setLiveEnginePreference(EnginePreference.valueOf(it)); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.VOD_ENGINE -> PickerDialog(
            title = stringResource(R.string.settings_movies_series_player),
            options = engineOptions(default = EnginePreference.MPV_FIRST),
            selected = vodEngine.name,
            onSelect = { vm.setVodEnginePreference(EnginePreference.valueOf(it)); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.ZOOM -> PickerDialog(
            title = stringResource(R.string.settings_default_zoom),
            options = ZoomMode.entries.map { it.name to stringResource(it.labelRes) },
            selected = zoomMode.name,
            onSelect = { vm.setDefaultZoom(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.RESUME -> PickerDialog(
            title = stringResource(R.string.settings_resume_playback),
            options = tv.own.owntv.core.settings.SettingsRepository.ResumeMode.entries.map { it.name to stringResource(resumeModeLabelRes(it)) },
            selected = resumeMode.name,
            onSelect = { vm.setResumeMode(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SUB_STYLE -> SubtitleAppearanceDialog(
                enabled = subStyleOn,
                scaleExo = subScaleExo,
                scaleMpv = subScaleMpv,
                font = subFont,
                color = subColor,
            position = subPosition,
            bgOpacity = subBgOpacity,
                onToggle = { vm.setSubtitleStyleEnabled(it) },
                onScaleExo = { vm.setSubtitleScaleExo(it) },
                onScaleMpv = { vm.setSubtitleScaleMpv(it) },
                onFont = { vm.setSubtitleFont(it) },
                onColor = { vm.setSubtitleColor(it) },
            onPosition = { vm.setSubtitlePosition(it) },
            onBgOpacity = { vm.setSubtitleBgOpacity(it) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SUB_LANG -> PickerDialog(
            title = stringResource(R.string.settings_preferred_subtitle_language),
            options = languageOptions(withOriginal = false),
            selected = subLang,
            onSelect = { vm.setPreferredSubLang(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AUDIO_LANG -> PickerDialog(
            title = stringResource(R.string.settings_preferred_audio_language),
            options = languageOptions(withOriginal = true),
            selected = audioLang,
            onSelect = { vm.setPreferredAudioLang(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AUDIO_SYNC -> StepperDialog(
            title = stringResource(R.string.settings_audio_sync),
            // ±5s, matching what the player itself accepts. The narrower ±2s here meant a delay set in the
            // HUD could not be reproduced — or corrected — from Settings.
            // 25 ms steps: the offset being corrected here is the TV's own picture-processing delay, which
            // lands in the tens of milliseconds — a 50 ms step could only bracket it, never hit it.
            value = audioDelay, step = 25, min = -5000, max = 5000,
            format = { stringResource(R.string.settings_audio_delay, it) },
            onSet = { vm.setAudioDelayMs(it) },
            onReset = { vm.setAudioDelayMs(0) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_LATENCY -> PickerDialog(
            title = stringResource(R.string.settings_live_latency),
            options = tv.own.owntv.core.settings.LiveLatency.entries.map { it.name to stringResource(liveLatencyLabelRes(it)) },
            selected = liveLatency.name,
            onSelect = { name ->
                val mode = tv.own.owntv.core.settings.LiveLatency.fromName(name)
                dialog = Dialog.NONE
                when (mode) {
                    // "Low latency" — warn before applying; Cancel leaves the current choice untouched.
                    tv.own.owntv.core.settings.LiveLatency.LOW ->
                        lowWarning = Pair({ vm.setLiveLatencyMode(mode) }, {})
                    // "Custom" — enter the seconds first. The mode is committed by the stepper itself, not
                    // here: switching on open meant backing out of the number dialog still left the user on
                    // Custom, with a value they never chose.
                    tv.own.owntv.core.settings.LiveLatency.CUSTOM -> {
                        customCommitted = false
                        dialog = Dialog.LIVE_CUSTOM
                    }
                    else -> vm.setLiveLatencyMode(mode)
                }
            },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_CUSTOM -> StepperDialog(
            title = stringResource(R.string.settings_custom_live_buffer),
            value = liveCustomSecs,
            step = 1,
            min = tv.own.owntv.core.settings.LiveBuffer.CUSTOM_MIN,
            max = tv.own.owntv.core.settings.LiveBuffer.CUSTOM_MAX,
            format = { stringResource(R.string.settings_live_buffer_seconds, it) },
            onSet = {
                vm.setLiveLatencyCustomSecs(it)
                vm.setLiveLatencyMode(tv.own.owntv.core.settings.LiveLatency.CUSTOM)
                customCommitted = true
            },
            onReset = {
                vm.setLiveLatencyCustomSecs(tv.own.owntv.core.settings.LiveBuffer.CUSTOM_DEFAULT)
                vm.setLiveLatencyMode(tv.own.owntv.core.settings.LiveLatency.CUSTOM)
                customCommitted = true
            },
            onDismiss = {
                dialog = Dialog.NONE
                // A below-Balanced custom value gets the same acknowledgement; Cancel reverts to Balanced.
                if (customCommitted && tv.own.owntv.core.settings.LiveBuffer.isLowLatency(liveCustomSecs)) {
                    lowWarning = Pair({}, { vm.setLiveLatencyMode(tv.own.owntv.core.settings.LiveLatency.BALANCED) })
                }
            },
        )
        Dialog.LIVE_PREROLL -> PickerDialog(
            title = stringResource(R.string.settings_live_preroll),
            options = tv.own.owntv.core.settings.LiveBuffer.PREROLL_CHOICES.map {
                it.toString() to if (it <= 0) stringResource(R.string.common_off) else stringResource(R.string.settings_video_seconds, it)
            },
            selected = livePreroll.toString(),
            onSelect = { vm.setLivePrerollSecs(it.toIntOrNull() ?: 0); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_TUNE_TIMEOUT -> PickerDialog(
            title = stringResource(R.string.settings_live_tune_timeout),
            options = tv.own.owntv.player.LiveLadder.BUDGET_CHOICES_SECS.map {
                it.toString() to if (it <= 0) stringResource(R.string.common_never) else stringResource(R.string.settings_live_buffer_seconds, it)
            },
            selected = liveTuneTimeout.toString(),
            onSelect = { vm.setLiveTuneTimeoutSecs(it.toIntOrNull() ?: 0); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_PREROLL_SOURCES -> PickerDialog(
            title = stringResource(R.string.settings_live_preroll_playlist_picker),
            options = sources.map { src ->
                val value = if (src.livePrerollSecs >= 0) {
                    stringResource(R.string.settings_video_seconds, src.livePrerollSecs)
                } else {
                    stringResource(R.string.settings_live_preroll_follow)
                }
                src.id.toString() to "${src.name}  ·  $value"
            },
            selected = prerollSource?.id?.toString() ?: "",
            onSelect = { id ->
                prerollSource = sources.firstOrNull { it.id.toString() == id }
                dialog = if (prerollSource != null) Dialog.LIVE_PREROLL_SOURCE else Dialog.NONE
            },
            onDismiss = { prerollSource = null; dialog = Dialog.NONE },
        )
        // --- per-playlist Live TV engine: pick the playlist, then its value ---
        Dialog.LIVE_ENGINE_SOURCES -> PickerDialog(
            title = stringResource(R.string.settings_live_preroll_playlist_picker),
            options = sources.map { src ->
                val value = src.liveEnginePreference
                    ?.let { name -> EnginePreference.entries.firstOrNull { it.name == name } }
                    ?.let { engineLabel(it) }
                    ?: stringResource(R.string.settings_live_preroll_follow)
                src.id.toString() to "${src.name}  ·  $value"
            },
            selected = engineSource?.id?.toString() ?: "",
            onSelect = { id ->
                engineSource = sources.firstOrNull { it.id.toString() == id }
                dialog = if (engineSource != null) Dialog.LIVE_ENGINE_SOURCE else Dialog.NONE
            },
            onDismiss = { engineSource = null; dialog = Dialog.NONE },
        )
        Dialog.LIVE_ENGINE_SOURCE -> PickerDialog(
            title = engineSource?.name ?: stringResource(R.string.settings_live_tv_player),
            options = listOf(FOLLOW_GLOBAL to stringResource(R.string.settings_live_preroll_follow)) +
                EnginePreference.entries.map { it.name to engineLabel(it) },
            selected = engineSource?.liveEnginePreference ?: FOLLOW_GLOBAL,
            onSelect = { value ->
                engineSource?.let { vm.setSourceLiveEngine(it.id, value.takeIf { v -> v != FOLLOW_GLOBAL }) }
                dialog = Dialog.LIVE_ENGINE_SOURCES
            },
            // Back goes back ONE level, to the playlist list — which is also where the value just set is
            // shown. Closing both levels stranded the user on the row two steps above what they opened,
            // and made configuring a second playlist a fresh trip from the top.
            onDismiss = { dialog = Dialog.LIVE_ENGINE_SOURCES },
        )
        // --- per-playlist Movies & Series engine: pick the playlist, then its value ---
        Dialog.VOD_ENGINE_SOURCES -> PickerDialog(
            title = stringResource(R.string.settings_live_preroll_playlist_picker),
            options = sources.map { src ->
                val value = src.vodEnginePreference
                    ?.let { name -> EnginePreference.entries.firstOrNull { it.name == name } }
                    ?.let { engineLabel(it) }
                    ?: stringResource(R.string.settings_live_preroll_follow)
                src.id.toString() to "${src.name}  ·  $value"
            },
            selected = vodEngineSource?.id?.toString() ?: "",
            onSelect = { id ->
                vodEngineSource = sources.firstOrNull { it.id.toString() == id }
                dialog = if (vodEngineSource != null) Dialog.VOD_ENGINE_SOURCE else Dialog.NONE
            },
            onDismiss = { vodEngineSource = null; dialog = Dialog.NONE },
        )
        Dialog.VOD_ENGINE_SOURCE -> PickerDialog(
            title = vodEngineSource?.name ?: stringResource(R.string.settings_movies_series_player),
            options = listOf(FOLLOW_GLOBAL to stringResource(R.string.settings_live_preroll_follow)) +
                EnginePreference.entries.map { it.name to engineLabel(it) },
            selected = vodEngineSource?.vodEnginePreference ?: FOLLOW_GLOBAL,
            onSelect = { value ->
                vodEngineSource?.let { vm.setSourceVodEngine(it.id, value.takeIf { v -> v != FOLLOW_GLOBAL }) }
                dialog = Dialog.VOD_ENGINE_SOURCES
            },
            onDismiss = { dialog = Dialog.VOD_ENGINE_SOURCES },
        )
        // --- per-playlist "Give up after": pick the playlist, then its value ---
        Dialog.LIVE_TUNE_TIMEOUT_SOURCES -> PickerDialog(
            title = stringResource(R.string.settings_live_preroll_playlist_picker),
            options = sources.map { src ->
                val value = when (val secs = src.liveTuneTimeoutSecs) {
                    null -> stringResource(R.string.settings_live_preroll_follow)
                    0 -> stringResource(R.string.common_never)
                    else -> stringResource(R.string.settings_live_buffer_seconds, secs)
                }
                src.id.toString() to "${src.name}  ·  $value"
            },
            selected = tuneTimeoutSource?.id?.toString() ?: "",
            onSelect = { id ->
                tuneTimeoutSource = sources.firstOrNull { it.id.toString() == id }
                dialog = if (tuneTimeoutSource != null) Dialog.LIVE_TUNE_TIMEOUT_SOURCE else Dialog.NONE
            },
            onDismiss = { tuneTimeoutSource = null; dialog = Dialog.NONE },
        )
        Dialog.LIVE_TUNE_TIMEOUT_SOURCE -> PickerDialog(
            title = tuneTimeoutSource?.name ?: stringResource(R.string.settings_live_tune_timeout),
            options = listOf(FOLLOW_GLOBAL to stringResource(R.string.settings_live_preroll_follow)) +
                tv.own.owntv.player.LiveLadder.BUDGET_CHOICES_SECS.map {
                    it.toString() to if (it <= 0) stringResource(R.string.common_never) else stringResource(R.string.settings_live_buffer_seconds, it)
                },
            selected = tuneTimeoutSource?.liveTuneTimeoutSecs?.toString() ?: FOLLOW_GLOBAL,
            onSelect = { value ->
                tuneTimeoutSource?.let { vm.setSourceTuneTimeout(it.id, value.toIntOrNull()) }
                dialog = Dialog.LIVE_TUNE_TIMEOUT_SOURCES
            },
            onDismiss = { dialog = Dialog.LIVE_TUNE_TIMEOUT_SOURCES },
        )
        // --- per-playlist Live latency: pick the playlist, then its value (Custom opens the stepper) ---
        Dialog.LIVE_LATENCY_SOURCES -> PickerDialog(
            title = stringResource(R.string.settings_live_preroll_playlist_picker),
            options = sources.map { src ->
                val mode = src.liveLatencyMode?.let { tv.own.owntv.core.settings.LiveLatency.fromName(it) }
                val value = when {
                    mode == null -> stringResource(R.string.settings_live_preroll_follow)
                    mode == tv.own.owntv.core.settings.LiveLatency.CUSTOM ->
                        stringResource(R.string.settings_live_buffer_seconds, sourceCustomSecs(src))
                    else -> stringResource(liveLatencyLabelRes(mode))
                }
                src.id.toString() to "${src.name}  ·  $value"
            },
            selected = latencySource?.id?.toString() ?: "",
            onSelect = { id ->
                latencySource = sources.firstOrNull { it.id.toString() == id }
                dialog = if (latencySource != null) Dialog.LIVE_LATENCY_SOURCE else Dialog.NONE
            },
            onDismiss = { latencySource = null; dialog = Dialog.NONE },
        )
        Dialog.LIVE_LATENCY_SOURCE -> PickerDialog(
            title = latencySource?.name ?: stringResource(R.string.settings_live_latency),
            options = listOf(FOLLOW_GLOBAL to stringResource(R.string.settings_live_preroll_follow)) +
                tv.own.owntv.core.settings.LiveLatency.entries.map { it.name to stringResource(liveLatencyLabelRes(it)) },
            selected = latencySource?.liveLatencyMode ?: FOLLOW_GLOBAL,
            onSelect = { name ->
                val src = latencySource
                dialog = Dialog.NONE
                when {
                    name == FOLLOW_GLOBAL -> {
                        src?.let { vm.setSourceLiveLatency(it.id, null, FOLLOW_GLOBAL_LATENCY_SECS) }
                        dialog = Dialog.LIVE_LATENCY_SOURCES
                    }
                    // Same rules as the global picker: Low is acknowledged first, Custom asks for the
                    // seconds and is committed by the stepper rather than on opening it.
                    name == tv.own.owntv.core.settings.LiveLatency.LOW.name ->
                        lowWarning = Pair(
                            { src?.let { vm.setSourceLiveLatency(it.id, name, FOLLOW_GLOBAL_LATENCY_SECS) }; latencySource = null },
                            { latencySource = null },
                        )
                    name == tv.own.owntv.core.settings.LiveLatency.CUSTOM.name ->
                        dialog = Dialog.LIVE_LATENCY_CUSTOM_SOURCE
                    else -> {
                        src?.let { vm.setSourceLiveLatency(it.id, name, FOLLOW_GLOBAL_LATENCY_SECS) }
                        dialog = Dialog.LIVE_LATENCY_SOURCES
                    }
                }
            },
            // Back to the playlist list, one level. The Low/Custom branches above still close the chain:
            // both hand over to another popup (the acknowledgement, the seconds stepper) that owns what
            // happens next, and re-opening the list underneath them would fight for focus.
            onDismiss = { dialog = Dialog.LIVE_LATENCY_SOURCES },
        )
        Dialog.LIVE_LATENCY_CUSTOM_SOURCE -> StepperDialog(
            title = stringResource(R.string.settings_custom_live_buffer),
            value = latencySource?.let { sourceCustomSecs(it) } ?: tv.own.owntv.core.settings.LiveBuffer.CUSTOM_DEFAULT,
            step = 1,
            min = tv.own.owntv.core.settings.LiveBuffer.CUSTOM_MIN,
            max = tv.own.owntv.core.settings.LiveBuffer.CUSTOM_MAX,
            format = { stringResource(R.string.settings_live_buffer_seconds, it) },
            onSet = { secs ->
                val src = latencySource
                val commit: () -> Unit = {
                    src?.let {
                        vm.setSourceLiveLatency(it.id, tv.own.owntv.core.settings.LiveLatency.CUSTOM.name, secs)
                    }
                }
                // A below-Balanced number gets the same acknowledgement the global setting asks for.
                if (tv.own.owntv.core.settings.LiveBuffer.isLowLatency(secs)) {
                    lowWarning = Pair(commit, { latencySource = null })
                } else {
                    commit()
                }
            },
            onReset = {
                latencySource?.let { vm.setSourceLiveLatency(it.id, null, FOLLOW_GLOBAL_LATENCY_SECS) }
            },
            onDismiss = { dialog = Dialog.LIVE_LATENCY_SOURCE }, // back one level, to the mode picker
        )
        Dialog.LIVE_PREROLL_SOURCE -> PickerDialog(
            title = prerollSource?.name ?: stringResource(R.string.settings_sort_playlist),
            options = listOf("-1" to stringResource(R.string.settings_live_preroll_follow)) +
                tv.own.owntv.core.settings.LiveBuffer.PREROLL_CHOICES.map {
                    it.toString() to if (it <= 0) stringResource(R.string.common_off) else stringResource(R.string.settings_video_seconds, it)
                },
            selected = (prerollSource?.livePrerollSecs ?: -1).toString(),
            onSelect = { value ->
                prerollSource?.let { vm.setSourcePreroll(it.id, value.toIntOrNull() ?: -1) }
                dialog = Dialog.LIVE_PREROLL_SOURCES
            },
            onDismiss = { dialog = Dialog.LIVE_PREROLL_SOURCES }, // back one level, to the playlist list
        )
        Dialog.MINI_PLAYER -> MiniPlayerSettingsDialog(onDismiss = { dialog = Dialog.NONE })
        Dialog.EXTERNAL_PLAYER -> ExternalPlayerDialog(
            live = externalLive, movies = externalMovies, series = externalSeries,
            onToggle = { section, enabled -> vm.setExternalPlayer(section, enabled) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.RESET_PINS -> ConfirmResetDialog(
            title = stringResource(R.string.settings_reset_player_choices_confirm),
            description = stringResource(R.string.settings_reset_player_choices_confirm_description),
            onConfirm = { vm.clearVodEnginePins(); dialog = Dialog.NONE },
            onCancel = { dialog = Dialog.NONE },
        )
        Dialog.RESET_LIVE_PINS -> ConfirmResetDialog(
            title = stringResource(R.string.settings_reset_live_player_choices_confirm),
            description = stringResource(R.string.settings_reset_live_player_choices_confirm_description),
            onConfirm = { vm.clearLivePins(); dialog = Dialog.NONE },
            onCancel = { dialog = Dialog.NONE },
        )
        Dialog.FORGET_FIXES -> ConfirmResetDialog(
            title = stringResource(R.string.settings_forget_stream_fixes_confirm),
            description = stringResource(R.string.settings_forget_stream_fixes_confirm_description),
            onConfirm = { vm.forgetStreamFixes(); dialog = Dialog.NONE },
            onCancel = { dialog = Dialog.NONE },
        )
        Dialog.VOLUME -> StepperDialog(
            title = stringResource(R.string.settings_default_volume),
            // The same 0–150 range and 5% step the player's own volume dialog uses, so a level found
            // there can be set as the default here without landing between two values.
            value = defaultVolume, step = 5, min = 0, max = 150,
            format = { stringResource(R.string.player_percent, it) },
            onSet = { vm.setDefaultVolume(it) },
            onReset = { vm.setDefaultVolume(100) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SEEK_STEP -> PickerDialog(
            title = stringResource(R.string.settings_seek_step),
            options = tv.own.owntv.core.settings.SeekSteps.SEEK_CHOICES.map {
                it.toString() to stringResource(R.string.settings_live_buffer_seconds, it)
            },
            selected = seekStep.toString(),
            onSelect = { vm.setSeekStepSec(it.toIntOrNull() ?: tv.own.owntv.core.settings.SeekSteps.DEFAULT_SEEK_STEP_SEC); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.MAX_QUALITY -> PickerDialog(
            title = stringResource(R.string.settings_max_video_quality),
            options = vm.maxVideoHeightChoices.map { it.toString() to videoQualityLabel(it) },
            selected = maxVideoHeight.toString(),
            onSelect = { vm.setMaxVideoHeight(it.toIntOrNull() ?: 0); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.VOD_BUFFER -> PickerDialog(
            title = stringResource(R.string.settings_vod_buffer),
            options = vm.vodBufferChoicesSecs.map { it.toString() to vodAutoOrSeconds(it) },
            selected = vodBufferSecs.toString(),
            onSelect = { vm.setVodBufferSecs(it.toIntOrNull() ?: 0); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.VOD_TIMEOUT -> PickerDialog(
            title = stringResource(R.string.settings_vod_network_timeout),
            options = vm.vodNetworkTimeoutChoicesSecs.map { it.toString() to vodAutoOrSeconds(it) },
            selected = vodNetworkTimeoutSecs.toString(),
            onSelect = { vm.setVodNetworkTimeoutSecs(it.toIntOrNull() ?: 0); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.TIMESHIFT_WINDOW -> PickerDialog(
            title = stringResource(R.string.settings_timeshift_window),
            options = tv.own.owntv.core.timeshift.TimeshiftRules.WINDOW_CHOICES_MINUTES.map {
                it.toString() to stringResource(R.string.player_duration_minutes, it)
            },
            selected = timeshiftWindowMinutes.toString(),
            onSelect = { choice -> choice.toIntOrNull()?.let { vm.setTimeshiftWindowMinutes(it) }; dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.VOD_RECONNECTS -> PickerDialog(
            title = stringResource(R.string.settings_vod_reconnects),
            options = vm.vodReconnectChoices.map { it.toString() to stringResource(R.string.settings_vod_reconnects_value, it) },
            selected = vodReconnects.toString(),
            onSelect = { vm.setVodReconnects(it.toIntOrNull() ?: 1); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AFR_PAUSE -> PickerDialog(
            title = stringResource(R.string.settings_afr_pause),
            options = (0..vm.afrPauseMaxSecs).map { it.toString() to afrPauseLabel(it) },
            selected = afrPauseSecs.toString(),
            onSelect = { vm.setAfrPauseSecs(it.toIntOrNull() ?: 0); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIVE_REWIND_STEP -> PickerDialog(
            title = stringResource(R.string.settings_live_rewind_step),
            options = tv.own.owntv.core.settings.SeekSteps.LIVE_REWIND_CHOICES.map {
                it.toString() to stringResource(R.string.settings_live_buffer_seconds, it)
            },
            selected = liveRewindStep.toString(),
            onSelect = { vm.setLiveRewindStepSec(it.toIntOrNull() ?: tv.own.owntv.core.settings.SeekSteps.DEFAULT_LIVE_REWIND_STEP_SEC); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.RESET_SAVED_ZOOM -> ConfirmResetDialog(
            title = stringResource(R.string.settings_reset_saved_zoom_confirm),
            description = stringResource(R.string.settings_reset_saved_zoom_confirm_description),
            onConfirm = { vm.clearSavedZoom(); dialog = Dialog.NONE },
            onCancel = { dialog = Dialog.NONE },
        )
        Dialog.RESET_SAVED_VOLUME -> ConfirmResetDialog(
            title = stringResource(R.string.settings_reset_saved_volume_confirm),
            description = stringResource(R.string.settings_reset_saved_volume_confirm_description),
            onConfirm = { vm.clearSavedVolume(); dialog = Dialog.NONE },
            onCancel = { dialog = Dialog.NONE },
        )
        Dialog.RESET_SAVED_AUDIO_DELAY -> ConfirmResetDialog(
            title = stringResource(R.string.settings_reset_saved_audio_delay_confirm),
            description = stringResource(R.string.settings_reset_saved_audio_delay_confirm_description),
            onConfirm = { vm.clearSavedAudioDelay(); dialog = Dialog.NONE },
            onCancel = { dialog = Dialog.NONE },
        )
        Dialog.AFR_WARNING -> tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { dialog = Dialog.NONE }) {
            AutoFrameRateWarningDialog(
                onEnable = { vm.setAutoFrameRate(true); dialog = Dialog.NONE },
                onDismiss = { dialog = Dialog.NONE },
            )
        }
        Dialog.MULTIVIEW_TILES -> tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { dialog = Dialog.NONE }) {
            MultiviewTilesDialog(
                current = multiviewTiles,
                onPick = { tiles ->
                    // Above two tiles the warning is asked once, here and nowhere else (D12): a tile
                    // that then fails already explains itself, so the user is not asked twice.
                    if (tiles > tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES && !multiviewWarningAccepted) {
                        pendingTiles = tiles
                        dialog = Dialog.MULTIVIEW_WARNING
                    } else {
                        vm.setMultiviewTiles(tiles)
                        dialog = Dialog.NONE
                    }
                },
                onDismiss = { dialog = Dialog.NONE },
            )
        }
        Dialog.MULTIVIEW_WARNING -> tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { dialog = Dialog.NONE }) {
            MultiviewWarningDialog(
                onUseAnyway = { vm.setMultiviewTiles(pendingTiles, acceptWarning = true); dialog = Dialog.NONE },
                onKeepTwo = {
                    vm.setMultiviewTiles(tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES)
                    dialog = Dialog.NONE
                },
            )
        }
        Dialog.LIVE_PREVIEW_PANEL -> tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { dialog = Dialog.NONE }) {
            LivePreviewPanelHiddenDialog(onDismiss = { dialog = Dialog.NONE })
        }
        Dialog.NONE -> Unit
    }

    lowWarning?.let { (onConfirm, onCancel) ->
        LiveLatencyWarningDialog(
            onConfirm = { lowWarning = null; onConfirm() },
            onCancel = { lowWarning = null; onCancel() },
        )
    }
}

/** Acknowledgement popup when picking a below-Balanced live buffer (Low latency, or a low custom value). */
@Composable
private fun LiveLatencyWarningDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onCancel() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onCancel) {
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.dialogPanel(width = 500.dp, padding = 28.dp)) {
            Text(stringResource(R.string.settings_low_latency_warning), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_low_latency_warning_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onCancel, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_low_latency_understand), onClick = onConfirm, modifier = Modifier.focusRequester(firstFocus))
            }
        }
    }
    }
}

/** Confirmation before forgetting a whole set of remembered per-item choices (engine pins, zoom and
 *  volume). Focus starts on Cancel — the row that opens this is one press away from an ordinary
 *  setting, so a mis-press must not wipe choices the user made deliberately. */
@Composable
private fun ConfirmResetDialog(title: String, description: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onCancel() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onCancel) {
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.dialogPanel(width = 500.dp, padding = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(
                    stringResource(R.string.common_cancel), onClick = onCancel,
                    style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(firstFocus),
                )
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.common_reset), onClick = onConfirm)
            }
        }
    }
    }
}

/** The dialog a pinned row opens when Quick jumps into this screen. Null for rows that toggle. */
private fun dialogForQuickKey(key: String): Dialog? = when (key) {
    "vp_afr" -> Dialog.AFR_WARNING
    "vp_multiview_tiles" -> Dialog.MULTIVIEW_TILES
    "vp_live_engine" -> Dialog.LIVE_ENGINE
    "vp_live_engine_sources" -> Dialog.LIVE_ENGINE_SOURCES
    "vp_vod_engine" -> Dialog.VOD_ENGINE
    "vp_vod_engine_sources" -> Dialog.VOD_ENGINE_SOURCES
    "vp_reset_pins" -> Dialog.RESET_PINS
    "vp_reset_live_pins" -> Dialog.RESET_LIVE_PINS
    "vp_forget_fixes" -> Dialog.FORGET_FIXES
    "vp_external" -> Dialog.EXTERNAL_PLAYER
    "vp_zoom" -> Dialog.ZOOM
    "vp_reset_zoom" -> Dialog.RESET_SAVED_ZOOM
    "vp_seek_step" -> Dialog.SEEK_STEP
    "vp_afr_pause" -> Dialog.AFR_PAUSE
    "vp_vod_buffer" -> Dialog.VOD_BUFFER
    "vp_max_quality" -> Dialog.MAX_QUALITY
    "vp_vod_timeout" -> Dialog.VOD_TIMEOUT
    "vp_vod_reconnects" -> Dialog.VOD_RECONNECTS
    "vp_rewind_step" -> Dialog.LIVE_REWIND_STEP
    "vp_live_preview" -> Dialog.LIVE_PREVIEW_PANEL
    "vp_live_latency" -> Dialog.LIVE_LATENCY
    "vp_latency_sources" -> Dialog.LIVE_LATENCY_SOURCES
    "vp_preroll" -> Dialog.LIVE_PREROLL
    "vp_tune_timeout" -> Dialog.LIVE_TUNE_TIMEOUT
    "vp_tune_timeout_sources" -> Dialog.LIVE_TUNE_TIMEOUT_SOURCES
    "vp_preroll_sources" -> Dialog.LIVE_PREROLL_SOURCES
    "vp_volume" -> Dialog.VOLUME
    "vp_reset_volume" -> Dialog.RESET_SAVED_VOLUME
    "vp_audio_lang" -> Dialog.AUDIO_LANG
    "vp_audio_sync" -> Dialog.AUDIO_SYNC
    "vp_reset_audio_delay" -> Dialog.RESET_SAVED_AUDIO_DELAY
    "vp_sub_style" -> Dialog.SUB_STYLE
    "vp_sub_lang" -> Dialog.SUB_LANG
    "vp_resume" -> Dialog.RESUME
    "vp_mini" -> Dialog.MINI_PLAYER
    else -> null
}

private enum class Dialog { NONE, LIVE_ENGINE, LIVE_ENGINE_SOURCES, LIVE_ENGINE_SOURCE, LIVE_LATENCY_SOURCES, LIVE_LATENCY_SOURCE, LIVE_LATENCY_CUSTOM_SOURCE, VOD_ENGINE, VOD_ENGINE_SOURCES, VOD_ENGINE_SOURCE, ZOOM, VOLUME, RESET_SAVED_ZOOM, RESET_SAVED_VOLUME, RESET_SAVED_AUDIO_DELAY, SEEK_STEP, LIVE_REWIND_STEP, SUB_STYLE, SUB_LANG, AUDIO_LANG, AUDIO_SYNC, RESUME, LIVE_LATENCY, LIVE_CUSTOM, LIVE_PREROLL, LIVE_TUNE_TIMEOUT, LIVE_TUNE_TIMEOUT_SOURCES, LIVE_TUNE_TIMEOUT_SOURCE, LIVE_PREROLL_SOURCES, LIVE_PREROLL_SOURCE, EXTERNAL_PLAYER, RESET_PINS, RESET_LIVE_PINS, FORGET_FIXES, AFR_WARNING, AFR_PAUSE, VOD_BUFFER, VOD_TIMEOUT, VOD_RECONNECTS, TIMESHIFT_WINDOW, MAX_QUALITY, LIVE_PREVIEW_PANEL, MINI_PLAYER, MULTIVIEW_TILES, MULTIVIEW_WARNING }

/** "Auto" for 0, else seconds ("60s") — the film buffer and network timeout choices (N18). */
@Composable
private fun vodAutoOrSeconds(secs: Int): String =
    if (secs <= 0) stringResource(R.string.settings_vod_network_auto) else stringResource(R.string.settings_live_buffer_seconds, secs)

/** "Auto" for 0, else the picture height ("1080p") — Maximum video quality (N11). */
@Composable
internal fun videoQualityLabel(height: Int): String =
    if (height <= 0) stringResource(R.string.settings_auto) else stringResource(R.string.settings_video_quality_lines, height)

/** "Off", or the hold in seconds ("2s"), for the Auto frame rate pause (N7). */
@Composable
private fun afrPauseLabel(secs: Int): String =
    if (secs <= 0) stringResource(R.string.common_off) else stringResource(R.string.settings_live_buffer_seconds, secs)

/**
 * Label for one engine preference — "ExoPlayer, then mpv", "mpv only", and so on.
 *
 * The engine names themselves are brands and never translated (`settings_player_*` are
 * `translatable="false"`), so only the two sentence frames around them are, which is also why the same
 * four labels serve both sections.
 */
@Composable
internal fun engineLabel(preference: EnginePreference): String {
    val exo = stringResource(R.string.settings_player_exoplayer)
    val mpv = stringResource(R.string.settings_player_mpv)
    return when (preference) {
        EnginePreference.EXO_FIRST -> stringResource(R.string.settings_engine_order, exo, mpv)
        EnginePreference.MPV_FIRST -> stringResource(R.string.settings_engine_order, mpv, exo)
        EnginePreference.EXO_ONLY -> stringResource(R.string.settings_engine_only, exo)
        EnginePreference.MPV_ONLY -> stringResource(R.string.settings_engine_only, mpv)
    }
}

/** Picker key for "follow the global setting" — the state a null column reads as. */
private const val FOLLOW_GLOBAL = ""

/** A playlist's stored custom-latency seconds, falling back to the global default when it has none
 *  yet (the `-1` sentinel), so the stepper always opens on a sensible number. */
private fun sourceCustomSecs(src: tv.own.owntv.core.database.entity.SourceEntity): Int =
    src.liveLatencyCustomSecs.takeIf { it >= tv.own.owntv.core.settings.LiveBuffer.CUSTOM_MIN }
        ?: tv.own.owntv.core.settings.LiveBuffer.CUSTOM_DEFAULT

/** The four options for an engine picker, with [default] marked — Live TV and Movies & Series have
 *  different defaults, so which line carries the mark depends on the section, not on the option. */
@Composable
private fun engineOptions(default: EnginePreference): List<Pair<String, String>> =
    EnginePreference.entries.map { preference ->
        val label = engineLabel(preference)
        preference.name to if (preference == default) {
            stringResource(R.string.settings_engine_default, label)
        } else {
            label
        }
    }

/** Row chip for the External player row: "Off", "On" (all three), or the sections that are on. */
@Composable
private fun externalPlayerChip(live: Boolean, movies: Boolean, series: Boolean): String {
    val on = buildList {
        if (live) add(stringResource(R.string.common_nav_live_tv))
        if (movies) add(stringResource(R.string.common_nav_movies))
        if (series) add(stringResource(R.string.common_nav_series))
    }
    return when (on.size) {
        0 -> stringResource(R.string.common_off)
        3 -> stringResource(R.string.common_on)
        else -> on.joinToString(", ")
    }
}

// --- Shared building blocks (kept local to the settings sub-screens) ---

@Composable
internal fun Header(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusableSurface(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            surface = GlassSurface.CARDS,
            contentAlignment = Alignment.Center,
        ) { _ -> OwnTVIcon(OwnTVIcon.BACK, tint = OwnTVTheme.colors.onSurface, modifier = Modifier.size(20.dp)) }
        Column {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnTVTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OwnTVTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
internal fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(1.dp)
            .background(OwnTVTheme.colors.outlineVariant),
    )
}

/** A settings row with an icon tile, title/description and a trailing value chip (+ optional chevron). */
@Composable
internal fun Row2(
    icon: OwnTVIcon,
    title: String,
    desc: String? = null,
    chip: String? = null,
    primaryChip: Boolean = true,
    chevron: Boolean = false,
    iconTint: Color? = null,
    /** Tile fill override. With [iconTint] and [titleTint], marks a row as destructive. */
    iconBackground: Color? = null,
    titleTint: Color? = null,
    iconBadge: String? = null,
    accentIconBadge: Boolean = false,
    keycapColor: Color? = null,
    keycapLabel: String? = null,
    /** Its key in [VIDEO_QUICK_ROWS]. Present = a held OK offers to pin this row to Quick. */
    quickKey: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val pin = if (quickKey != null) LocalQuickPin.current else null
    // A held OK raises the long press while the key is still down and a plain click when it is finally
    // released — so without this the menu would open and the row would fire underneath it.
    var longAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    FocusableSurface(
        onClick = { if (android.os.SystemClock.uptimeMillis() - longAt > 800) onClick() },
        onLongClick = pin?.let { p -> { longAt = android.os.SystemClock.uptimeMillis(); p.onHold(quickKey!!) } },
        modifier = modifier
            .fillMaxWidth()
            .then(if (pin != null && pin.focusKey == quickKey) Modifier.focusRequester(pin.focusRequester) else Modifier),
        shape = RoundedCornerShape(16.dp),
        surface = GlassSurface.CARDS,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Tile tone comes from the sub-screen, not the row, so these match the root row that
            // opened the screen instead of always being the accent.
            val (tileBg, tileOn) = LocalSettingsRowTone.current.colors()
            Box {
                Box(
                    modifier = Modifier.size(Dimens.IconTileSize).clip(RoundedCornerShape(Dimens.IconTileCorner)).background(iconBackground ?: tileBg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (keycapColor != null) {
                        val keycapShape = RoundedCornerShape(6.dp)
                        Box(
                            modifier = Modifier
                                .width(30.dp)
                                .height(18.dp)
                                .clip(keycapShape)
                                .background(keycapColor)
                                .border(1.5.dp, Color.White.copy(alpha = .78f), keycapShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            keycapLabel?.let {
                                Text(
                                    it,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 8.sp),
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    } else {
                        OwnTVIcon(icon = icon, tint = iconTint ?: tileOn, modifier = Modifier.size(22.dp))
                    }
                }
                iconBadge?.let {
                    val badgeBackground = if (accentIconBadge) colors.primary else colors.onSurface
                    val badgeForeground = if (accentIconBadge) colors.onPrimary else colors.surface
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 5.dp, y = 5.dp)
                            .size(19.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(badgeBackground)
                            .border(2.dp, colors.surface, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            it,
                            color = badgeForeground,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp),
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleTint ?: colors.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // The same dot the root rows carry: this one is also sitting in Quick.
                    if (pin != null && quickKey in pin.pinned) {
                        Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.primary))
                    }
                }
                if (desc != null) Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (chip != null) {
                val bg = if (primaryChip) colors.primaryContainer else colors.secondaryContainer
                val on = if (primaryChip) colors.onPrimaryContainer else colors.onSecondaryContainer
                Text(
                    chip, style = MaterialTheme.typography.labelMedium, color = on, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (chevron) OwnTVIcon(OwnTVIcon.CHEVRON, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** A single-select list dialog (value → label). */
@Composable
internal fun PickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    searchable: Boolean = false,
    trailingLabels: Map<String, String> = emptyMap(),
    leadingIcons: Map<String, OwnTVIcon> = emptyMap(),
    subtitle: String? = null,
    descriptions: Map<String, String> = emptyMap(),
    /** Drawn under an option's description — the layout chooser's little bar preview. */
    optionPreview: (@Composable (String) -> Unit)? = null,
) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    val searchFr = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    // When searchable, filter the option labels live (e.g. finding a category among hundreds).
    val shown = if (searchable && query.isNotBlank()) {
        options.filter { it.second.contains(query.trim(), ignoreCase = true) }
    } else {
        options
    }
    val selIndex = shown.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    LaunchedEffect(shown, selected, searchable) {
        // Nested pickers attach in the same frame their opener loses focus. Wait until this popup's
        // focus window exists, otherwise focus remains on the Add/Remove or Prefix/Suffix button.
        kotlinx.coroutines.delay(80)
        runCatching { (if (searchable) searchFr else fr).requestFocus() }
    }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        tv.own.owntv.ui.theme.PopupFontTheme {
            Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
                Column(
                    // Descriptions need room to breathe; a 280dp column would wrap them to five lines.
                    modifier = Modifier.dialogPanel(width = if (descriptions.isEmpty()) 280.dp else 420.dp, corner = 16.dp, padding = 14.dp, scroll = false),
                ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            if (searchable) {
                tv.own.owntv.ui.components.SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.common_search_hint),
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFr),
                    surface = GlassSurface.DIALOGS,
                )
                Spacer(Modifier.height(12.dp))
            }
            // Cap the list to the screen (minus dialog chrome) so Close stays reachable on small screens.
            val listMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 220.dp).coerceIn(140.dp, 240.dp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMax), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(shown, key = { _, o -> o.first }) { index, (value, label) ->
                    val isSel = value == selected
                    FocusableSurface(
                        onClick = { onSelect(value) },
                        modifier = if (index == selIndex) Modifier.fillMaxWidth().focusRequester(fr) else Modifier.fillMaxWidth(),
                        selected = isSel,
                        shape = RoundedCornerShape(12.dp),
                        selectedContainerColor = colors.primaryContainer,
                        contentAlignment = Alignment.CenterStart,
                        surface = GlassSurface.DIALOGS,
                    ) { _ ->
                        Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        leadingIcons[value]?.let { icon ->
                            OwnTVIcon(
                                icon,
                                tint = if (isSel) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp).size(18.dp),
                            )
                        }
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isSel) colors.onPrimaryContainer else colors.onSurface, modifier = Modifier.weight(1f))
                        trailingLabels[value]?.let {
                            tv.own.owntv.ui.components.ProviderChip(
                                name = it,
                                maxWidth = 92.dp,
                                compact = true,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        if (isSel) OwnTVIcon(OwnTVIcon.STAR, tint = colors.onPrimaryContainer, filled = true, modifier = Modifier.size(14.dp))
                        }
                        descriptions[value]?.let { desc ->
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSel) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                            )
                        }
                        optionPreview?.let { preview ->
                            Box(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) { preview(value) }
                        }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OwnTVButton(stringResource(R.string.content_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
            }
                }
            }
        }
    }
}

/**
 * External player defaults, one independent toggle per section. Unlike [PickerDialog] these aren't
 * mutually exclusive, so the dialog stays open as rows are flipped and closes only on Close/Back.
 * Same chrome as every other settings popup — `dialogPanel` + `GlassSurface.DIALOGS`, so it follows
 * the Glass effect setting instead of hard-coding a solid panel.
 */
@Composable
private fun ExternalPlayerDialog(
    live: Boolean,
    movies: Boolean,
    series: Boolean,
    onToggle: (tv.own.owntv.core.settings.SettingsRepository.ExternalPlayerSection, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onDismiss() }
    val rows = listOf(
        Triple(tv.own.owntv.core.settings.SettingsRepository.ExternalPlayerSection.LIVE_TV, stringResource(R.string.common_nav_live_tv), live),
        Triple(tv.own.owntv.core.settings.SettingsRepository.ExternalPlayerSection.MOVIES, stringResource(R.string.common_nav_movies), movies),
        Triple(tv.own.owntv.core.settings.SettingsRepository.ExternalPlayerSection.SERIES, stringResource(R.string.common_nav_series), series),
    )
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.dialogPanel(width = 300.dp, corner = 16.dp, padding = 14.dp, scroll = false)) {
                Text(stringResource(R.string.settings_external_player), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_external_player_description),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                rows.forEachIndexed { index, (section, label, enabled) ->
                    if (index > 0) Spacer(Modifier.height(4.dp))
                    FocusableSurface(
                        onClick = { onToggle(section, !enabled) },
                        modifier = if (index == 0) Modifier.fillMaxWidth().focusRequester(fr) else Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentAlignment = Alignment.CenterStart,
                        surface = GlassSurface.DIALOGS,
                    ) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                            Text(
                                if (enabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (enabled) colors.primary else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OwnTVButton(stringResource(R.string.content_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                }
            }
        }
    }
}

/** A +/- stepper dialog for an integer value. */
@Composable
internal fun StepperDialog(
    title: String,
    value: Int,
    step: Int,
    min: Int,
    max: Int,
    format: @Composable (Int) -> String,
    onSet: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val plusEnabled = value < max
    val minusEnabled = value > min
    val steppers = tv.own.owntv.ui.components.rememberStepperFocus(plusEnabled, minusEnabled)
    BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.dialogPanel(width = 360.dp, corner = 16.dp, padding = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepBtn("–", enabled = minusEnabled, modifier = Modifier.focusRequester(steppers.minus)) { onSet((value - step).coerceAtLeast(min)) }
                Text(
                    format(value),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                StepBtn("+", enabled = plusEnabled, modifier = Modifier.focusRequester(steppers.plus)) { onSet((value + step).coerceAtMost(max)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnTVButton(stringResource(R.string.common_reset), onClick = onReset, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
            }
        }
    }
    }
}

/** The quick text-color presets offered above the full picker (label → "#RRGGBB"). */
private val SUB_COLOR_PRESETS: List<Pair<Int, String>> = listOf(
    R.string.settings_subtitle_color_white to "#FFFFFF",
    R.string.settings_subtitle_color_yellow to "#FFEB3B",
    R.string.settings_subtitle_color_cyan to "#4FC3F7",
    R.string.settings_subtitle_color_green to "#8BC34A",
    R.string.settings_subtitle_color_grey to "#BDBDBD",
)

@Composable
private fun subOpacityLabel(pct: Int): String = when {
    !SubtitleStyle.hasOpacity(pct) -> stringResource(R.string.settings_subtitle_default)
    pct == SubtitleStyle.OPACITY_MIN -> stringResource(R.string.settings_subtitle_background_none)
    pct == SubtitleStyle.OPACITY_MAX -> stringResource(R.string.settings_subtitle_background_solid)
    else -> stringResource(R.string.common_percent, pct)
}

@Composable
private fun subColorLabel(hex: String): String = if (SubtitleStyle.hasColor(hex)) {
    hex.uppercase()
} else {
    stringResource(R.string.settings_subtitle_default)
}

@Composable
private fun subtitlePositionName(position: SubtitleStyle.Position): String = stringResource(
    when (position) {
        SubtitleStyle.Position.DEFAULT -> R.string.settings_subtitle_default
        SubtitleStyle.Position.TOP_LEFT -> R.string.player_mini_top_left
        SubtitleStyle.Position.TOP_CENTER -> R.string.player_mini_top_center
        SubtitleStyle.Position.TOP_RIGHT -> R.string.player_mini_top_right
        SubtitleStyle.Position.BOTTOM_LEFT -> R.string.player_mini_bottom_left
        SubtitleStyle.Position.BOTTOM_CENTER -> R.string.player_mini_bottom_center
        SubtitleStyle.Position.BOTTOM_RIGHT -> R.string.player_mini_bottom_right
    },
)

/**
 * Subtitle appearance (#96) — the menu for the whole custom look: a master toggle, then size, text
 * color, screen position and background transparency, each opening its own popup, with a live
 * preview above them all.
 *
 * Two levels of opt-in, and both matter. The master toggle gates everything: while it's off none of
 * these values reach any renderer, so subtitles keep their stock look — most importantly the styling
 * broadcasters embed in Live TV (CEA-608/teletext) cues, which can only be overridden by discarding
 * embedded styles wholesale. Each option then carries its own "Default", so turning the toggle on
 * still changes nothing until something is actually picked.
 *
 * Every control writes through immediately, so a change is visible on a paused stream behind the
 * dialog rather than only on the next channel change.
 */
@Composable
private fun SubtitleAppearanceDialog(
    enabled: Boolean,
    scaleExo: Float,
    scaleMpv: Float,
    font: AppFontFamily?,
    color: String,
    position: SubtitleStyle.Position,
    bgOpacity: Int,
    onToggle: (Boolean) -> Unit,
    onScaleExo: (Float) -> Unit,
    onScaleMpv: (Float) -> Unit,
    onFont: (AppFontFamily?) -> Unit,
    onColor: (String) -> Unit,
    onPosition: (SubtitleStyle.Position) -> Unit,
    onBgOpacity: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var child by remember { mutableStateOf(SubDialog.NONE) }
    // Which row opened the popup that is closing: closing a child returns focus to it, the same
    // contract the settings list itself follows.
    var lastChild by remember { mutableStateOf(SubDialog.NONE) }
    val toggleFocus = remember { FocusRequester() }
    val rowFocus = remember { SubDialog.entries.associateWith { FocusRequester() } }
    LaunchedEffect(child) {
        if (child == SubDialog.NONE) {
            withFrameNanos { }
            kotlinx.coroutines.delay(60)
            runCatching {
                (if (lastChild == SubDialog.NONE) toggleFocus else rowFocus.getValue(lastChild)).requestFocus()
            }
        }
    }

    // A child popup replaces this panel rather than stacking over it: focus stays unambiguous on a
    // D-pad, and the popups that need one carry their own preview, so nothing is lost by hiding this.
    if (child != SubDialog.NONE) {
        val close = { child = SubDialog.NONE }
            when (child) {
                SubDialog.SIZE -> SubtitleSizeDialog(
                    scaleExo = scaleExo, scaleMpv = scaleMpv, font = font, color = color,
                    bgOpacity = bgOpacity, onScaleExo = onScaleExo, onScaleMpv = onScaleMpv, onDismiss = close,
                )
                SubDialog.FONT -> PickerDialog(
                    title = stringResource(R.string.settings_subtitle_font),
                    options = listOf("" to stringResource(R.string.settings_subtitle_default)) +
                        AppFontFamily.entries.map { it.name to subtitleFontFamilyLabel(it) },
                    selected = font?.name.orEmpty(),
                    onSelect = { selected ->
                        onFont(AppFontFamily.entries.firstOrNull { it.name == selected })
                        close()
                    },
                    onDismiss = close,
                )
                SubDialog.COLOR -> SubtitleColorDialog(color = color, onColor = onColor, onDismiss = close)
            SubDialog.POSITION -> SubtitlePositionDialog(position = position, onSelect = onPosition, onDismiss = close)
                SubDialog.TRANSPARENCY -> SubtitleTransparencyDialog(
                    scale = scaleExo, font = font, color = color, position = position,
                bgOpacity = bgOpacity, onSet = onBgOpacity, onDismiss = close,
            )
            SubDialog.NONE -> Unit
        }
        return
    }

    BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().modalScrim()
                .trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 640.dp, padding = 28.dp)) {
                Text(stringResource(R.string.settings_subtitle_appearance), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_subtitle_customize_description),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                // The overview sits above every row, including the master toggle, so the effect of a
                // change is judged against a picture instead of guessed from a chip.
        SubtitlePreview(enabled = enabled, scale = scaleExo, font = font, color = color, position = position, bgOpacity = bgOpacity)
                Spacer(Modifier.height(16.dp))

                Row2(
                    icon = OwnTVIcon.SUBTITLE,
                    title = stringResource(R.string.settings_subtitle_customize),
                    desc = stringResource(R.string.settings_subtitle_customize_off),
                    chip = stringResource(if (enabled) R.string.common_on else R.string.common_off),
                    primaryChip = enabled,
                    modifier = Modifier.focusRequester(toggleFocus),
                    onClick = { onToggle(!enabled) },
                )

                if (enabled) {
                    val open = { target: SubDialog -> lastChild = target; child = target }
                    Spacer(Modifier.height(2.dp))
            Row2(
                icon = OwnTVIcon.SUBTITLE,
                title = stringResource(R.string.settings_subtitle_size),
                        desc = stringResource(R.string.settings_subtitle_size_description),
                        chip = subSizePairName(scaleExo, scaleMpv),
                        primaryChip = SubtitleStyle.hasScale(scaleExo) || SubtitleStyle.hasScale(scaleMpv),
                        chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.SIZE)),
                onClick = { open(SubDialog.SIZE) },
            )
            Row2(
                icon = OwnTVIcon.SUBTITLE,
                title = stringResource(R.string.settings_subtitle_font),
                desc = stringResource(R.string.settings_choose_font),
                chip = font?.let { subtitleFontFamilyLabel(it) } ?: stringResource(R.string.settings_subtitle_default),
                primaryChip = font != null,
                chevron = true,
                modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.FONT)),
                onClick = { open(SubDialog.FONT) },
            )
            Row2(
                        icon = OwnTVIcon.SUBTITLE,
                        title = stringResource(R.string.settings_subtitle_color_short),
                        desc = stringResource(R.string.settings_subtitle_color_description),
                        chip = subColorLabel(color), primaryChip = SubtitleStyle.hasColor(color), chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.COLOR)),
                        onClick = { open(SubDialog.COLOR) },
                    )
                    Row2(
                        icon = OwnTVIcon.SUBTITLE,
                        title = stringResource(R.string.settings_subtitle_position_short),
                        desc = stringResource(R.string.settings_subtitle_position_description),
                        chip = subtitlePositionName(position), primaryChip = position != SubtitleStyle.Position.DEFAULT, chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.POSITION)),
                        onClick = { open(SubDialog.POSITION) },
                    )
                    Row2(
                        icon = OwnTVIcon.SUBTITLE,
                        title = stringResource(R.string.settings_subtitle_background_transparency),
                        desc = stringResource(R.string.settings_subtitle_background_description),
                        chip = subOpacityLabel(bgOpacity), primaryChip = SubtitleStyle.hasOpacity(bgOpacity), chevron = true,
                        modifier = Modifier.focusRequester(rowFocus.getValue(SubDialog.TRANSPARENCY)),
                        onClick = { open(SubDialog.TRANSPARENCY) },
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                    Spacer(Modifier.weight(1f))
                    if (enabled) {
                        OwnTVButton(stringResource(R.string.settings_subtitle_reset_all), style = OwnTVButtonStyle.SECONDARY, onClick = {
                        onScaleExo(SubtitleStyle.SCALE_DEFAULT)
                        onScaleMpv(SubtitleStyle.SCALE_DEFAULT)
                        onFont(null)
                        onColor(SubtitleStyle.COLOR_DEFAULT)
                            onPosition(SubtitleStyle.Position.DEFAULT)
                            onBgOpacity(SubtitleStyle.OPACITY_DEFAULT)
                        })
                    }
                }
            }
        }
    }
}

/** The four options of [SubtitleAppearanceDialog], each opening its own popup. */
private enum class SubDialog { NONE, SIZE, FONT, COLOR, POSITION, TRANSPARENCY }

@Composable
private fun subtitleFontFamilyLabel(family: AppFontFamily): String = stringResource(
    when (family) {
        AppFontFamily.LORA -> R.string.settings_font_lora
        AppFontFamily.SYSTEM_SANS -> R.string.settings_font_system_sans
        AppFontFamily.MONOSPACE -> R.string.settings_font_monospace
        AppFontFamily.PLAYFAIR_DISPLAY -> R.string.settings_font_playfair_display
        AppFontFamily.DANCING_SCRIPT -> R.string.settings_font_dancing_script
        AppFontFamily.POPPINS -> R.string.settings_font_poppins
    },
)

/**
 * Subtitle text color — the same D-pad-tuned picker the accent color uses (shared controls live in
 * `ui.components`), plus a "Use default" escape that hands the color back to the stream and player.
 */
@Composable
private fun SubtitleColorDialog(color: String, onColor: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    // Seeded once from the stored color; the picker writes straight through to settings, so this is
    // only the working position of the hue bar / square between key presses.
    val hsv = remember {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(SubtitleStyle.colorArgb(color.ifBlank { "#FFFFFF" }), it)
        }
    }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    var hexInput by remember { mutableStateOf(color.removePrefix("#")) }
    var hexError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    fun applyPicked(hex: String) {
        hexInput = hex.removePrefix("#")
        hexError = false
        onColor(hex)
    }

    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().modalScrim()
                .imePadding().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 440.dp, corner = 16.dp, padding = 18.dp)) {
                Text(stringResource(R.string.settings_subtitle_color), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_subtitle_color_default_description),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SUB_COLOR_PRESETS.forEachIndexed { index, (_, hex) ->
                        tv.own.owntv.ui.components.ColorSwatch(
                            color = Color(SubtitleStyle.colorArgb(hex)),
                            selected = color.equals(hex, ignoreCase = true),
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = {
                                android.graphics.Color.colorToHSV(SubtitleStyle.colorArgb(hex), hsv)
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                applyPicked(hex)
                            },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                // Hex field above the picker: the on-screen keyboard covers the lower half of the
                // screen, so it has to stay high enough to remain visible while typing.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("#", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                    tv.own.owntv.ui.components.OwnTVTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it.take(6); hexError = false },
                        label = stringResource(R.string.settings_subtitle_hex),
                        placeholder = "FFFFFF",
                        modifier = Modifier.width(170.dp),
                    )
                    OwnTVButton(stringResource(R.string.settings_apply), onClick = {
                        val hex = "#" + hexInput.trim().removePrefix("#").uppercase()
                        if (tv.own.owntv.ui.theme.parseAccentHex(hex) != null) {
                            android.graphics.Color.colorToHSV(SubtitleStyle.colorArgb(hex), hsv)
                            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                            applyPicked(hex)
                        } else {
                            hexError = true
                        }
                    })
                }
                if (hexError) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_subtitle_color_hex_hint), style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                }

                Spacer(Modifier.height(14.dp))
                tv.own.owntv.ui.components.HueBar(hue = hue) { h ->
                    hue = h
                    applyPicked(tv.own.owntv.ui.components.hsvToHex(hue, sat, value))
                }
                Spacer(Modifier.height(12.dp))
                tv.own.owntv.ui.components.SatValSquare(hue = hue, sat = sat, value = value) { s, v ->
                    sat = s; value = v
                    applyPicked(tv.own.owntv.ui.components.hsvToHex(hue, sat, value))
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVButton(stringResource(R.string.settings_subtitle_use_default), style = OwnTVButtonStyle.SECONDARY, onClick = {
                        hexInput = ""
                        hexError = false
                        onColor(SubtitleStyle.COLOR_DEFAULT)
                    })
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * Subtitle position — Default plus the six fixed anchors, drawn as miniature screens so the choice
 * is made by looking rather than by reading a label.
 */
@Composable
private fun SubtitlePositionDialog(
    position: SubtitleStyle.Position,
    onSelect: (SubtitleStyle.Position) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().modalScrim()
                .trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 430.dp, corner = 16.dp, padding = 18.dp, scroll = false)) {
                Text(stringResource(R.string.settings_subtitle_position), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_subtitle_position_default_description),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                PositionCell(
                    position = SubtitleStyle.Position.DEFAULT,
                    selected = position == SubtitleStyle.Position.DEFAULT,
                    modifier = Modifier.fillMaxWidth().let {
                        if (position == SubtitleStyle.Position.DEFAULT) it.focusRequester(selectedFocus) else it
                    },
                    onClick = { onSelect(SubtitleStyle.Position.DEFAULT) },
                )
                SubtitleStyle.Position.ANCHORS.chunked(3).forEach { anchorRow ->
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        anchorRow.forEach { anchor ->
                            PositionCell(
                                position = anchor,
                                selected = position == anchor,
                                modifier = Modifier.weight(1f).let {
                                    if (position == anchor) it.focusRequester(selectedFocus) else it
                                },
                                onClick = { onSelect(anchor) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
                }
            }
        }
    }
}

/** One cell of the position picker: a miniature screen with the subtitle bar where it will land. */
@Composable
private fun PositionCell(
    position: SubtitleStyle.Position,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val isDefault = position == SubtitleStyle.Position.DEFAULT
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.height(if (isDefault) 40.dp else 64.dp),
        selected = selected,
        shape = RoundedCornerShape(12.dp),
        selectedContainerColor = colors.primaryContainer,
        surface = GlassSurface.DIALOGS,
        contentAlignment = Alignment.Center,
    ) { _ ->
        val labelColor = if (selected) colors.onPrimaryContainer else colors.onSurface
        if (isDefault) {
            Text(
                subtitlePositionName(position), style = MaterialTheme.typography.labelMedium, color = labelColor,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = position.alignment(),
                ) {
                    Box(
                        Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (selected) colors.onPrimaryContainer else colors.outline),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitlePositionName(position), style = MaterialTheme.typography.labelSmall, color = labelColor,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Subtitle size — one row per engine, because mpv and Media3's SubtitleView draw the same multiplier
 * at visibly different sizes. There is no conversion between them worth guessing at, so the user gets
 * a control for each and sets them once against a preview that shows both at the same time.
 *
 * OK cycles a row through the four sizes in place rather than opening a picker: a third popup on top
 * of a popup on top of a dialog is two extra Back presses, and with a live preview a four-value
 * control reads better as a cycle. Same pattern as the transparency popup below.
 */
@Composable
private fun SubtitleSizeDialog(
    scaleExo: Float,
    scaleMpv: Float,
    font: AppFontFamily?,
    color: String,
    bgOpacity: Int,
    onScaleExo: (Float) -> Unit,
    onScaleMpv: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().modalScrim()
                .trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.dialogPanel(width = 520.dp, corner = 16.dp, padding = 18.dp, scroll = false)) {
                Text(stringResource(R.string.settings_subtitle_size), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_subtitle_size_description),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SubtitleSizePreview(
                    scaleExo = scaleExo, scaleMpv = scaleMpv, font = font, color = color, bgOpacity = bgOpacity,
                )
                Spacer(Modifier.height(14.dp))
                Row2(
                    icon = OwnTVIcon.SUBTITLE,
                    title = stringResource(R.string.settings_player_exoplayer),
                    chip = subSizeName(scaleExo), primaryChip = SubtitleStyle.hasScale(scaleExo),
                    modifier = Modifier.focusRequester(firstRow),
                    onClick = { onScaleExo(nextSubSize(scaleExo)) },
                )
                Row2(
                    icon = OwnTVIcon.SUBTITLE,
                    title = stringResource(R.string.settings_player_mpv),
                    chip = subSizeName(scaleMpv), primaryChip = SubtitleStyle.hasScale(scaleMpv),
                    onClick = { onScaleMpv(nextSubSize(scaleMpv)) },
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
                }
            }
        }
    }
}

/** Both engines' sizes on one stand-in frame — the whole point of the setting is seeing the difference. */
@Composable
private fun SubtitleSizePreview(
    scaleExo: Float,
    scaleMpv: Float,
    font: AppFontFamily?,
    color: String,
    bgOpacity: Int,
) {
    val textColor = if (SubtitleStyle.hasColor(color)) Color(SubtitleStyle.colorArgb(color)) else Color.White
    val boxColor = if (SubtitleStyle.hasOpacity(bgOpacity)) {
        Color(SubtitleStyle.backgroundArgb(bgOpacity))
    } else {
        Color.Black.copy(alpha = 0.45f)
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SUB_PREVIEW_BRUSH)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            stringResource(R.string.settings_player_exoplayer) to scaleExo,
            stringResource(R.string.settings_player_mpv) to scaleMpv,
        ).forEach { (engine, scale) ->
            Text(engine, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
            Text(
                stringResource(R.string.settings_subtitle_preview_sample),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale,
                    fontFamily = font?.asComposeFamily() ?: MaterialTheme.typography.bodyLarge.fontFamily,
                ),
                color = textColor,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(boxColor)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Background transparency — a ±10% stepper. "Default" is its own state rather than a value in the
 * range: it means the box is left to the renderer (and, on Live TV, to the broadcaster).
 */
@Composable
private fun SubtitleTransparencyDialog(
    scale: Float,
    font: AppFontFamily?,
    color: String,
    position: SubtitleStyle.Position,
    bgOpacity: Int,
    onSet: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val isDefault = !SubtitleStyle.hasOpacity(bgOpacity)
    // From "Default" either button adopts the mid value first, so neither is ever a dead end.
    val effective = if (isDefault) SubtitleStyle.OPACITY_START else bgOpacity
    val minusEnabled = isDefault || effective > SubtitleStyle.OPACITY_MIN
    val plusEnabled = isDefault || effective < SubtitleStyle.OPACITY_MAX
    val steppers = tv.own.owntv.ui.components.rememberStepperFocus(plusEnabled, minusEnabled)
    BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().modalScrim()
                .trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.dialogPanel(width = 380.dp, corner = 16.dp, padding = 18.dp, scroll = false),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.settings_subtitle_background_transparency), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_subtitle_background_description),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
        SubtitlePreview(
            enabled = true, scale = scale, font = font, color = color, position = position,
                    bgOpacity = bgOpacity, height = 92.dp,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepBtn("–", enabled = minusEnabled, modifier = Modifier.focusRequester(steppers.minus)) {
                        onSet(
                            if (isDefault) SubtitleStyle.OPACITY_START
                            else (effective - SubtitleStyle.OPACITY_STEP).coerceAtLeast(SubtitleStyle.OPACITY_MIN),
                        )
                    }
                    Text(
                        subOpacityLabel(bgOpacity), style = MaterialTheme.typography.titleMedium,
                        color = colors.primary, modifier = Modifier.width(100.dp), textAlign = TextAlign.Center,
                    )
                    StepBtn("+", enabled = plusEnabled, modifier = Modifier.focusRequester(steppers.plus)) {
                        onSet(
                            if (isDefault) SubtitleStyle.OPACITY_START
                            else (effective + SubtitleStyle.OPACITY_STEP).coerceAtMost(SubtitleStyle.OPACITY_MAX),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVButton(stringResource(R.string.settings_subtitle_use_default), style = OwnTVButtonStyle.SECONDARY, onClick = { onSet(SubtitleStyle.OPACITY_DEFAULT) })
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * A stand-in video frame with a sample subtitle drawn the way the renderers will draw it — same
 * color, background alpha, text scale and anchor. Anything on "Default" (or everything, while the
 * master toggle is off) falls back to the stock look.
 */
@Composable
private fun SubtitlePreview(
    enabled: Boolean,
    scale: Float,
    font: AppFontFamily? = null,
    color: String,
    position: SubtitleStyle.Position,
    bgOpacity: Int,
    height: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val colors = OwnTVTheme.colors
    val textColor = if (enabled && SubtitleStyle.hasColor(color)) Color(SubtitleStyle.colorArgb(color)) else Color.White
    val boxColor = if (enabled && SubtitleStyle.hasOpacity(bgOpacity)) {
        Color(SubtitleStyle.backgroundArgb(bgOpacity))
    } else {
        Color.Black.copy(alpha = 0.45f)
    }
    val anchor = if (enabled) position else SubtitleStyle.Position.DEFAULT
    val textScale = if (enabled) scale else SubtitleStyle.SCALE_DEFAULT
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(SUB_PREVIEW_BRUSH),
        contentAlignment = anchor.alignment(),
    ) {
        Text(
            stringResource(R.string.settings_subtitle_preview_sample),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale,
                fontFamily = if (enabled && font != null) font.asComposeFamily()
                    else MaterialTheme.typography.bodyLarge.fontFamily,
            ),
            color = textColor,
            modifier = Modifier
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(boxColor)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        if (!enabled) {
            Text(
                stringResource(R.string.settings_subtitle_preview_stock),
                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
    }
}

@Composable
internal fun StepBtn(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.Center,
        surface = GlassSurface.DIALOGS,
    ) { _ -> Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) colors.onSurface else colors.outline) }
}
