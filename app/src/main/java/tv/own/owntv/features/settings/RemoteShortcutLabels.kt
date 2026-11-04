package tv.own.owntv.features.settings

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.localizedInteger

@Composable
internal fun remoteButtonLabel(keyCode: Int): String = when {
    keyCode in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 ->
        localizedInteger(keyCode - AndroidKeyEvent.KEYCODE_0, grouping = false)
    keyCode in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 ->
        localizedInteger(keyCode - AndroidKeyEvent.KEYCODE_NUMPAD_0, grouping = false)
    else -> when (keyCode) {
    AndroidKeyEvent.KEYCODE_CHANNEL_UP -> stringResource(R.string.settings_remote_button_channel_up)
    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> stringResource(R.string.settings_remote_button_channel_down)
    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> stringResource(R.string.settings_remote_button_rewind)
    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> stringResource(R.string.settings_remote_button_fast_forward)
    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> stringResource(R.string.settings_remote_button_previous)
    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> stringResource(R.string.settings_remote_button_next)
    AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> stringResource(R.string.settings_remote_button_last_channel)
    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
    -> stringResource(R.string.settings_remote_action_play_pause)
    AndroidKeyEvent.KEYCODE_GUIDE -> stringResource(R.string.common_nav_guide)
    AndroidKeyEvent.KEYCODE_INFO -> stringResource(R.string.player_tool_info)
    AndroidKeyEvent.KEYCODE_MENU -> stringResource(R.string.settings_remote_button_menu)
    AndroidKeyEvent.KEYCODE_CAPTIONS -> stringResource(R.string.player_tool_subtitles)
    AndroidKeyEvent.KEYCODE_PROG_RED -> stringResource(R.string.settings_remote_button_red)
    AndroidKeyEvent.KEYCODE_PROG_GREEN -> stringResource(R.string.settings_remote_button_green)
    AndroidKeyEvent.KEYCODE_PROG_YELLOW -> stringResource(R.string.settings_remote_button_yellow)
    AndroidKeyEvent.KEYCODE_PROG_BLUE -> stringResource(R.string.settings_remote_button_blue)
        else -> stringResource(R.string.settings_remote_shortcuts_unknown_button, keyCode)
    }
}

internal fun remoteButtonIcon(keyCode: Int, fallbackAction: RemoteShortcutAction): OwnTVIcon = when {
    keyCode in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 ||
        keyCode in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> OwnTVIcon.MORE
    else -> when (keyCode) {
        AndroidKeyEvent.KEYCODE_CHANNEL_UP -> OwnTVIcon.REMOTE_CHANNEL_UP
        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> OwnTVIcon.REMOTE_CHANNEL_DOWN
        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> OwnTVIcon.REWIND
        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> OwnTVIcon.FORWARD
        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> OwnTVIcon.SKIP_PREVIOUS
        AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> OwnTVIcon.SKIP_NEXT
        AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> OwnTVIcon.HISTORY
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
        -> OwnTVIcon.PLAY
        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> OwnTVIcon.PAUSE
        AndroidKeyEvent.KEYCODE_GUIDE -> OwnTVIcon.EPG
        AndroidKeyEvent.KEYCODE_INFO -> OwnTVIcon.INFO
        AndroidKeyEvent.KEYCODE_MENU -> OwnTVIcon.MENU
        AndroidKeyEvent.KEYCODE_CAPTIONS -> OwnTVIcon.SUBTITLE
        AndroidKeyEvent.KEYCODE_PROG_RED,
        AndroidKeyEvent.KEYCODE_PROG_GREEN,
        AndroidKeyEvent.KEYCODE_PROG_YELLOW,
        AndroidKeyEvent.KEYCODE_PROG_BLUE,
        -> OwnTVIcon.LIVE_DOT
        else -> remoteActionIcon(fallbackAction)
    }
}

@Composable
internal fun remoteActionLabel(action: RemoteShortcutAction): String = stringResource(
    when (action) {
        RemoteShortcutAction.OPEN_HOME -> R.string.common_nav_home
        RemoteShortcutAction.OPEN_LIVE_TV -> R.string.common_nav_live_tv
        RemoteShortcutAction.OPEN_MOVIES -> R.string.common_nav_movies
        RemoteShortcutAction.OPEN_SERIES -> R.string.common_nav_series
        RemoteShortcutAction.OPEN_DOWNLOADS -> R.string.common_nav_downloads
        RemoteShortcutAction.OPEN_GUIDE -> R.string.common_nav_guide
        RemoteShortcutAction.OPEN_SEARCH -> R.string.common_nav_search
        RemoteShortcutAction.OPEN_SETTINGS -> R.string.common_nav_settings
        RemoteShortcutAction.OPEN_PROFILE_SWITCHER -> R.string.profiles_title
        RemoteShortcutAction.OPEN_PLAYLIST_SWITCHER -> R.string.settings_playlists
        RemoteShortcutAction.CONTINUE_LAST_WATCHED -> R.string.settings_remote_action_continue
        RemoteShortcutAction.FOCUS_NOW_PLAYING -> R.string.settings_remote_action_focus_now_playing
        RemoteShortcutAction.EXPAND_NOW_PLAYING -> R.string.settings_remote_action_expand_now_playing
        RemoteShortcutAction.ENTER_MINI_PLAYER -> R.string.settings_remote_action_mini_player
        RemoteShortcutAction.ENTER_AUDIO_MODE -> R.string.player_tool_audio_only
        RemoteShortcutAction.PLAY_PAUSE -> R.string.settings_remote_action_play_pause
        RemoteShortcutAction.PAGE_TOWARD_FIRST -> R.string.settings_remote_action_page_first
        RemoteShortcutAction.PAGE_TOWARD_LAST -> R.string.settings_remote_action_page_last
        RemoteShortcutAction.JUMP_TO_FIRST -> R.string.settings_remote_action_jump_first
        RemoteShortcutAction.JUMP_TO_LAST -> R.string.settings_remote_action_jump_last
        RemoteShortcutAction.RETURN_TO_LIVE -> R.string.player_go_live
        RemoteShortcutAction.PREVIOUS_CHANNEL -> R.string.player_previous_channel
        RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS -> R.string.player_tool_subtitles
        RemoteShortcutAction.OPEN_AUDIO_CONTROLS -> R.string.player_tool_audio
        RemoteShortcutAction.OPEN_ASPECT_CONTROLS -> R.string.player_tool_aspect
        RemoteShortcutAction.TOGGLE_PLAYBACK_INFO -> R.string.player_tool_info
    },
)

internal fun remoteActionIcon(action: RemoteShortcutAction): OwnTVIcon = when (action) {
    RemoteShortcutAction.OPEN_HOME -> OwnTVIcon.HOME
    RemoteShortcutAction.OPEN_LIVE_TV, RemoteShortcutAction.RETURN_TO_LIVE -> OwnTVIcon.LIVE_TV
    RemoteShortcutAction.OPEN_MOVIES -> OwnTVIcon.MOVIES
    RemoteShortcutAction.OPEN_SERIES -> OwnTVIcon.SERIES
    RemoteShortcutAction.OPEN_DOWNLOADS -> OwnTVIcon.DOWNLOADS
    RemoteShortcutAction.OPEN_GUIDE -> OwnTVIcon.EPG
    RemoteShortcutAction.OPEN_SEARCH -> OwnTVIcon.SEARCH
    RemoteShortcutAction.OPEN_SETTINGS -> OwnTVIcon.SETTINGS
    RemoteShortcutAction.OPEN_PROFILE_SWITCHER -> OwnTVIcon.PERSON
    RemoteShortcutAction.OPEN_PLAYLIST_SWITCHER -> OwnTVIcon.PLAYLIST
    RemoteShortcutAction.CONTINUE_LAST_WATCHED, RemoteShortcutAction.PLAY_PAUSE -> OwnTVIcon.PLAY
    RemoteShortcutAction.FOCUS_NOW_PLAYING -> OwnTVIcon.FOCUS_HIGHLIGHT
    RemoteShortcutAction.EXPAND_NOW_PLAYING -> OwnTVIcon.EXPAND
    RemoteShortcutAction.ENTER_MINI_PLAYER -> OwnTVIcon.PIP
    RemoteShortcutAction.ENTER_AUDIO_MODE, RemoteShortcutAction.OPEN_AUDIO_CONTROLS -> OwnTVIcon.AUDIO
    RemoteShortcutAction.PAGE_TOWARD_FIRST, RemoteShortcutAction.JUMP_TO_FIRST -> OwnTVIcon.SKIP_PREVIOUS
    RemoteShortcutAction.PAGE_TOWARD_LAST, RemoteShortcutAction.JUMP_TO_LAST -> OwnTVIcon.SKIP_NEXT
    RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS -> OwnTVIcon.SUBTITLE
    RemoteShortcutAction.OPEN_ASPECT_CONTROLS -> OwnTVIcon.ASPECT
    RemoteShortcutAction.TOGGLE_PLAYBACK_INFO -> OwnTVIcon.INFO
    RemoteShortcutAction.PREVIOUS_CHANNEL -> OwnTVIcon.HISTORY
}
