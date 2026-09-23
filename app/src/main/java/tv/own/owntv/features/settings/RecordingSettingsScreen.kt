package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.features.recordings.RecordingsViewModel
import tv.own.owntv.ui.components.NumberInputDialog
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.restoreAfterDialogClose
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

private enum class RecordingDialog { NONE, PRE_ROLL, POST_ROLL }

/** This screen's rows as Settings search finds them, by their own titles. `SettingsSearchRowsTest` holds it to the rows below. */
internal val RECORDING_SEARCH_ROWS: List<Int> = listOf(
    R.string.settings_recording_reserve, R.string.settings_record_watching,
    R.string.settings_recording_pre_roll, R.string.settings_recording_post_roll,
)

/**
 * The three things recording can be told, and one thing it has to tell the user.
 *
 * There is deliberately **no** "what to do when space runs low": that behaviour is fixed — a
 * recording stops with 500 MB free, keeps what it captured, and never deletes anything to make room.
 * A setting implies a choice, and there isn't one.
 */
@Composable
fun RecordingSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
    recordingsVm: RecordingsViewModel = koinViewModel(),
) {
    val reserve by vm.recordingReserveConnection.collectAsStateWithLifecycle()
    val preRoll by vm.recordingPreRollMinutes.collectAsStateWithLifecycle()
    val postRoll by vm.recordingPostRollMinutes.collectAsStateWithLifecycle()
    val recordWatching by vm.recordWhatImWatching.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    val firstFocus = remember { FocusRequester() }
    val preRollFocus = remember { FocusRequester() }
    val postRollFocus = remember { FocusRequester() }
    var dialog by remember { mutableStateOf(RecordingDialog.NONE) }
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    var showWatchingWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    val scrollState = rememberScrollState()
    var savedScroll by remember { mutableIntStateOf(0) }
    LaunchedEffect(dialog) {
        if (dialog != RecordingDialog.NONE) {
            savedScroll = scrollState.value
            return@LaunchedEffect
        }
        restoreAfterDialogClose(dialogReturn, scrollState, savedScroll)
        dialogReturn = null
    }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .verticalScroll(scrollState)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Header(title = stringResource(R.string.recording_settings_group), onBack = onBack)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.recording_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )

        // Said here and nowhere else, because this is the screen where a user wonders why a
        // recording began at 20:03. Not a setting — the app cannot grant itself the permission —
        // but the one place the consequence belongs.
        if (!recordingsVm.timersAreExact) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_recording_timers_inexact),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFEF4444),
            )
        }
        Spacer(Modifier.height(16.dp))

        Row2(
            icon = OwnTVIcon.LIVE_TV,
            title = stringResource(R.string.settings_recording_reserve),
            desc = stringResource(R.string.settings_recording_reserve_description),
            chip = stringResource(if (reserve) R.string.common_on else R.string.common_off),
            primaryChip = reserve,
            onClick = { vm.setRecordingReserveConnection(!reserve) },
            modifier = Modifier.focusRequester(firstFocus),
        )
        // D3 — the player's record button does not exist until this is on, and turning it on is
        // where the one-connection trade-off is explained and accepted. Turning it OFF needs no
        // dialog: nothing is being traded away.
        Row2(
            icon = OwnTVIcon.PLAY,
            title = stringResource(R.string.settings_record_watching),
            desc = stringResource(R.string.settings_record_watching_description),
            chip = stringResource(if (recordWatching) R.string.common_on else R.string.common_off),
            primaryChip = recordWatching,
            onClick = {
                if (recordWatching) vm.setRecordWhatImWatching(false) else showWatchingWarning = true
            },
        )
        Row2(
            icon = OwnTVIcon.HISTORY,
            title = stringResource(R.string.settings_recording_pre_roll),
            desc = stringResource(R.string.settings_recording_pre_roll_description),
            chip = pluralStringResource(R.plurals.recording_minutes, preRoll, preRoll),
            chevron = true,
            onClick = { dialogReturn = preRollFocus; dialog = RecordingDialog.PRE_ROLL },
            modifier = Modifier.focusRequester(preRollFocus),
        )
        Row2(
            icon = OwnTVIcon.HISTORY,
            title = stringResource(R.string.settings_recording_post_roll),
            desc = stringResource(R.string.settings_recording_post_roll_description),
            chip = pluralStringResource(R.plurals.recording_minutes, postRoll, postRoll),
            chevron = true,
            onClick = { dialogReturn = postRollFocus; dialog = RecordingDialog.POST_ROLL },
            modifier = Modifier.focusRequester(postRollFocus),
        )
    }

    // The auto-frame-rate precedent, and Multiview's after it: name the trade-off, then offer both
    // answers with the safer one first.
    if (showWatchingWarning) {
        RecordWatchingWarningDialog(
            onKeepOff = { showWatchingWarning = false },
            onTurnOn = { vm.setRecordWhatImWatching(true); showWatchingWarning = false },
        )
    }

    when (dialog) {
        // min = 0 on both: "no padding at all" is a legitimate choice for a provider whose guide
        // times are exact, and the dialog's usual min of 1 would quietly refuse it.
        RecordingDialog.PRE_ROLL -> NumberInputDialog(
            title = stringResource(R.string.settings_recording_pre_roll),
            value = preRoll,
            min = 0,
            max = MAX_ROLL_MINUTES,
            fieldLabel = stringResource(R.string.common_minutes),
            // Persist only — never close here. [NumberInputDialog] fires onSet live on every − / +
            // press, so closing in it shuts the dialog on the first nudge; Save and Back are what
            // dismiss it, exactly as on the channel-navigation dialogs.
            onSet = vm::setRecordingPreRollMinutes,
            onReset = {
                vm.setRecordingPreRollMinutes(tv.own.owntv.core.recording.RecordingSchedule.DEFAULT_PRE_ROLL_MINUTES)
            },
            onDismiss = { dialog = RecordingDialog.NONE },
        )
        RecordingDialog.POST_ROLL -> NumberInputDialog(
            title = stringResource(R.string.settings_recording_post_roll),
            value = postRoll,
            min = 0,
            max = MAX_ROLL_MINUTES,
            fieldLabel = stringResource(R.string.common_minutes),
            onSet = vm::setRecordingPostRollMinutes,
            onReset = {
                vm.setRecordingPostRollMinutes(tv.own.owntv.core.recording.RecordingSchedule.DEFAULT_POST_ROLL_MINUTES)
            },
            onDismiss = { dialog = RecordingDialog.NONE },
        )
        RecordingDialog.NONE -> Unit
    }
}

/** Core's own cap, mirrored here so the picker cannot offer a value the store would clamp. */
private const val MAX_ROLL_MINUTES = tv.own.owntv.core.recording.RecordingSchedule.MAX_ROLL_MINUTES

/**
 * The trade-off behind "Record what I'm watching", said before it is switched on (D3).
 *
 * Shaped exactly like the Multiview and auto-frame-rate warnings before it: a title naming the
 * constraint, a paragraph explaining what is gained and what is given up, and two buttons with the
 * safer answer first and focused.
 */
@Composable
private fun RecordWatchingWarningDialog(onKeepOff: () -> Unit, onTurnOn: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onKeepOff() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onKeepOff) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .modalScrim()
            .trapAllFocusExit()
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 520.dp, padding = 28.dp)) {
            Text(
                stringResource(R.string.settings_record_watching_warning_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.settings_record_watching_warning_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OwnTVButton(
                    stringResource(R.string.settings_record_watching_keep_off),
                    onClick = onKeepOff,
                    modifier = Modifier.focusRequester(focus),
                )
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    stringResource(R.string.settings_record_watching_turn_on),
                    onClick = onTurnOn,
                    style = OwnTVButtonStyle.SECONDARY,
                )
            }
        }
    }
    }
}
