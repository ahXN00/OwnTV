package tv.own.owntv.features.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.settings.PlayerHoldOkAction
import tv.own.owntv.core.settings.PlayerShortOkAction
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.restoreAfterDialogClose

private enum class PlayerButtonsDialog { NONE, HOLD_OK, SHORT_OK }

/** This group's rows as Settings search finds them, by their own titles. `SettingsSearchRowsTest` holds it to the rows below. */
internal val PLAYER_BUTTONS_SEARCH_ROWS: List<Int> = listOf(
    R.string.settings_player_hold_ok, R.string.settings_player_short_ok,
    R.string.settings_player_swap_channel_keys, R.string.settings_player_number_keys_tune,
)

@Composable
private fun holdOkLabel(action: PlayerHoldOkAction): String = stringResource(
    when (action) {
        PlayerHoldOkAction.OFF -> R.string.common_off
        PlayerHoldOkAction.CHANNEL_LIST -> R.string.settings_player_ok_channel_list
        PlayerHoldOkAction.GUIDE -> R.string.settings_player_ok_guide
        PlayerHoldOkAction.PROGRAMME_INFO -> R.string.settings_player_ok_programme_info
    },
)

@Composable
private fun shortOkLabel(action: PlayerShortOkAction): String = stringResource(
    when (action) {
        PlayerShortOkAction.SHOW_CONTROLS -> R.string.settings_player_ok_show_controls
        PlayerShortOkAction.CHANNEL_LIST -> R.string.settings_player_ok_channel_list
        PlayerShortOkAction.GUIDE -> R.string.settings_player_ok_guide
    },
)

/**
 * The PLAYER BUTTONS part of Settings › Remote shortcuts: what OK does while the player's controls
 * are hidden, which way the channel keys zap, and whether number keys tune. Every default is the
 * player's behaviour from before these options existed.
 */
@Composable
internal fun PlayerButtonsSettingsRows(scrollState: ScrollState, vm: PlayerButtonsSettingsViewModel = koinViewModel()) {
    val options by vm.options.collectAsStateWithLifecycle()
    val holdFocus = remember { FocusRequester() }
    val shortFocus = remember { FocusRequester() }
    var dialog by remember { mutableStateOf(PlayerButtonsDialog.NONE) }
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }

    var savedScroll by remember { mutableIntStateOf(0) }
    LaunchedEffect(dialog) {
        if (dialog != PlayerButtonsDialog.NONE) {
            savedScroll = scrollState.value
            return@LaunchedEffect
        }
        if (dialogReturn != null) restoreAfterDialogClose(dialogReturn, scrollState, savedScroll)
        dialogReturn = null
    }

    StageSettingsHeading(stringResource(R.string.settings_player_buttons), 4)
    KeyedSettingRow(
        icon = OwnTVIcon.CH_NAV,
        title = stringResource(R.string.settings_player_hold_ok),
        desc = stringResource(R.string.settings_player_hold_ok_description),
        value = SettingValue.Choice(holdOkLabel(options.holdOk)),
        helpKey = "pb_hold_ok",
        onClick = { dialogReturn = holdFocus; dialog = PlayerButtonsDialog.HOLD_OK },
        modifier = Modifier.focusRequester(holdFocus),
    )
    KeyedSettingRow(
        icon = OwnTVIcon.PLAY,
        title = stringResource(R.string.settings_player_short_ok),
        desc = stringResource(R.string.settings_player_short_ok_description),
        value = SettingValue.Choice(shortOkLabel(options.shortOk)),
        helpKey = "pb_short_ok",
        onClick = { dialogReturn = shortFocus; dialog = PlayerButtonsDialog.SHORT_OK },
        modifier = Modifier.focusRequester(shortFocus),
    )
    KeyedSettingRow(
        icon = OwnTVIcon.SWAP,
        title = stringResource(R.string.settings_player_swap_channel_keys),
        desc = stringResource(R.string.settings_player_swap_channel_keys_description),
        value = SettingValue.Switch(options.swapChannelKeys),
        helpKey = "pb_swap_ch",
        onClick = { vm.setSwapChannelKeys(!options.swapChannelKeys) },
    )
    KeyedSettingRow(
        icon = OwnTVIcon.MORE,
        title = stringResource(R.string.settings_player_number_keys_tune),
        desc = stringResource(R.string.settings_player_number_keys_tune_description),
        value = SettingValue.Switch(options.numberKeysTune),
        helpKey = "pb_number_keys",
        onClick = { vm.setNumberKeysTune(!options.numberKeysTune) },
    )

    val close = { dialog = PlayerButtonsDialog.NONE }
    when (dialog) {
        PlayerButtonsDialog.HOLD_OK -> PickerDialog(
            title = stringResource(R.string.settings_player_hold_ok),
            options = PlayerHoldOkAction.entries.map { it.name to holdOkLabel(it) },
            selected = options.holdOk.name,
            onSelect = { vm.setHoldOk(PlayerHoldOkAction.fromName(it)); close() },
            onDismiss = close,
        )
        PlayerButtonsDialog.SHORT_OK -> PickerDialog(
            title = stringResource(R.string.settings_player_short_ok),
            options = PlayerShortOkAction.entries.map { it.name to shortOkLabel(it) },
            selected = options.shortOk.name,
            onSelect = { vm.setShortOk(PlayerShortOkAction.fromName(it)); close() },
            onDismiss = close,
        )
        PlayerButtonsDialog.NONE -> Unit
    }
}
