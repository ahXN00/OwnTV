package tv.own.owntv.features.settings

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.settings.ChNavLimits
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.owntv.core.settings.RemoteShortcutBinding
import tv.own.owntv.core.settings.RemoteShortcutBindings
import tv.own.owntv.core.settings.RemoteShortcutPress
import tv.own.owntv.ui.components.NumberInputDialog
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.restoreAfterDialogClose
import tv.own.owntv.ui.format.localizedInteger
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

private enum class ChNavDialog { NONE, ENABLED, UP_SKIP, DOWN_SKIP, CAPTURE, ACTION, RESET }

@Composable
fun ChNavSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val enabled by vm.chNavEnabled.collectAsStateWithLifecycle()
    val upSkip by vm.chNavUpSkip.collectAsStateWithLifecycle()
    val downSkip by vm.chNavDownSkip.collectAsStateWithLifecycle()
    val bindings by vm.remoteShortcutBindings.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    val upSkipFocus = remember { FocusRequester() }
    val downSkipFocus = remember { FocusRequester() }
    val bindingFocus = remember { mutableMapOf<Pair<Int, RemoteShortcutPress>, FocusRequester>() }
    var dialog by remember { mutableStateOf(ChNavDialog.NONE) }
    var pendingBinding by remember { mutableStateOf<RemoteShortcutBinding?>(null) }
    var editingExisting by remember { mutableStateOf(false) }
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    val scrollState = rememberScrollState()
    var savedScroll by remember { mutableIntStateOf(0) }
    LaunchedEffect(dialog) {
        if (dialog != ChNavDialog.NONE) {
            savedScroll = scrollState.value
            return@LaunchedEffect
        }
        if (dialogReturn != null) restoreAfterDialogClose(dialogReturn, scrollState, savedScroll)
        dialogReturn = null
    }

    // The switch, the button assignments as rows, the two skip counts; how it works in the panel.
    val back = stringResource(R.string.common_back)
    val layout = stringResource(R.string.settings_group_layout)
    val pageHints = listOf(stringResource(R.string.common_ok) to stringResource(R.string.settings_key_change), back to layout)
    val about = stringResource(R.string.settings_remote_shortcuts_description)
    StageFullPage(
        parents = listOf(layout),
        title = stringResource(R.string.settings_remote_shortcuts),
        count = "",
        onBack = onBack,
        modifier = modifier,
        scroll = scrollState,
        rowsFocus = firstFocus,
    ) {
        val onTitle = stringResource(R.string.settings_remote_shortcuts_enabled)
        val onValue = SettingValue.Switch(enabled)
        val howItWorks = stringResource(R.string.settings_remote_shortcuts_help)
        StageSettingRow(
            icon = OwnTVIcon.CH_NAV,
            title = onTitle,
            desc = stringResource(R.string.settings_line_remote_shortcuts_on),
            value = onValue,
            onClick = { vm.setChNavEnabled(!enabled) },
            help = settingHelp(null, onTitle, stringResource(R.string.settings_remote_shortcuts_enabled_description), onValue, pinnable = false).copy(
                extra = {
                    Text(howItWorks, style = stageText(15, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 14.mpx))
                },
            ),
        )

        val sorted = bindings.sortedWith(compareBy<RemoteShortcutBinding> { it.keyCode }.thenBy { it.press.ordinal })
        StageSettingsHeading(stringResource(R.string.settings_remote_shortcuts_assignments), sorted.size)
        val separator = dotSeparator()
        sorted.forEach { binding ->
            val focus = remember(binding.keyCode, binding.press) { bindingFocus.getOrPut(binding.keyCode to binding.press) { FocusRequester() } }
            val pressLabel = stringResource(
                if (binding.press == RemoteShortcutPress.SHORT) R.string.settings_remote_shortcuts_short_press
                else R.string.settings_remote_shortcuts_long_press,
            )
            val title = remoteButtonLabel(binding.keyCode) + separator + pressLabel
            val value = SettingValue.Opens(null)
            StageSettingRow(
                icon = remoteButtonIcon(binding.keyCode, binding.action),
                title = title,
                desc = remoteActionLabel(binding.action),
                value = value,
                onClick = {
                    dialogReturn = focus
                    pendingBinding = binding
                    editingExisting = true
                    dialog = ChNavDialog.ACTION
                },
                modifier = Modifier.focusRequester(focus),
                help = SettingHelp(title, about, hints = pageHints),
            )
        }
        val addTitle = stringResource(R.string.settings_remote_shortcuts_add)
        val addDesc = stringResource(R.string.settings_remote_shortcuts_add_description)
        StageSettingRow(
            icon = OwnTVIcon.ADD,
            title = addTitle,
            desc = addDesc,
            value = SettingValue.Opens(null),
            onClick = { editingExisting = false; dialog = ChNavDialog.CAPTURE },
            help = SettingHelp(addTitle, addDesc, hints = settingHints(SettingValue.Opens(null), pinnable = false)),
        )
        val resetTitle = stringResource(R.string.settings_remote_shortcuts_reset)
        val resetDesc = stringResource(R.string.settings_remote_shortcuts_reset_description)
        StageSettingRow(
            icon = OwnTVIcon.REFRESH,
            title = resetTitle,
            desc = resetDesc,
            value = null,
            onClick = { dialog = ChNavDialog.RESET },
            help = SettingHelp(resetTitle, resetDesc, hints = settingHints(null, pinnable = false)),
        )

        StageSettingsHeading(stringResource(R.string.settings_skip_counts), 2)
        val upTitle = stringResource(R.string.settings_ch_nav_up)
        val upDesc = stringResource(R.string.settings_ch_nav_up_description)
        val upValue = SettingValue.Opens(localizedInteger(upSkip, grouping = false))
        StageSettingRow(
            icon = OwnTVIcon.PAGE_TOWARD_FIRST,
            title = upTitle,
            desc = upDesc,
            value = upValue,
            onClick = { dialogReturn = upSkipFocus; dialog = ChNavDialog.UP_SKIP },
            modifier = Modifier.focusRequester(upSkipFocus),
            help = settingHelp(null, upTitle, upDesc, upValue, pinnable = false),
        )
        val downTitle = stringResource(R.string.settings_ch_nav_down)
        val downDesc = stringResource(R.string.settings_ch_nav_down_description)
        val downValue = SettingValue.Opens(localizedInteger(downSkip, grouping = false))
        StageSettingRow(
            icon = OwnTVIcon.PAGE_TOWARD_LAST,
            title = downTitle,
            desc = downDesc,
            value = downValue,
            onClick = { dialogReturn = downSkipFocus; dialog = ChNavDialog.DOWN_SKIP },
            modifier = Modifier.focusRequester(downSkipFocus),
            help = settingHelp(null, downTitle, downDesc, downValue, pinnable = false),
        )
        PlayerButtonsSettingsRows(scrollState)
    }

    val warnText = stringResource(R.string.settings_large_skips_warning)
    when (dialog) {
        ChNavDialog.ENABLED -> PickerDialog(
            title = stringResource(R.string.settings_remote_shortcuts_enabled),
            options = listOf("true" to stringResource(R.string.common_on), "false" to stringResource(R.string.common_off)),
            selected = enabled.toString(),
            onSelect = { value -> vm.setChNavEnabled(value.toBoolean()); dialog = ChNavDialog.NONE },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.UP_SKIP -> NumberInputDialog(
            title = stringResource(R.string.settings_ch_nav_up),
            value = upSkip,
            min = 1,
            max = ChNavLimits.HARD_MAX,
            step = 5,
            warnAbove = ChNavLimits.WARN_THRESHOLD,
            warningText = warnText,
            onSet = vm::setChNavUpSkip,
            onReset = { vm.setChNavUpSkip(ChNavLimits.DEFAULT_SKIP) },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.DOWN_SKIP -> NumberInputDialog(
            title = stringResource(R.string.settings_ch_nav_down),
            value = downSkip,
            min = 1,
            max = ChNavLimits.HARD_MAX,
            step = 5,
            warnAbove = ChNavLimits.WARN_THRESHOLD,
            warningText = warnText,
            onSet = vm::setChNavDownSkip,
            onReset = { vm.setChNavDownSkip(ChNavLimits.DEFAULT_SKIP) },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.CAPTURE -> RemoteButtonCapturePopup(
            onCaptured = { keyCode, press ->
                pendingBinding = RemoteShortcutBinding(keyCode, press, RemoteShortcutAction.OPEN_HOME)
                dialog = ChNavDialog.ACTION
            },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.ACTION -> pendingBinding?.let { binding ->
            val actionOptions = RemoteShortcutAction.entries.map { it.name to remoteActionLabel(it) }
            PickerDialog(
                title = stringResource(R.string.settings_remote_shortcuts_choose_action),
                options = if (editingExisting) listOf(String() to stringResource(R.string.common_delete)) + actionOptions else actionOptions,
                selected = binding.action.name,
                searchable = true,
                leadingIcons = buildMap {
                    if (editingExisting) put(String(), OwnTVIcon.CLOSE)
                    RemoteShortcutAction.entries.forEach { put(it.name, remoteActionIcon(it)) }
                },
                onSelect = { value ->
                    if (value.isEmpty()) vm.removeRemoteShortcutBinding(binding.keyCode, binding.press)
                    else vm.setRemoteShortcutBinding(binding.copy(action = RemoteShortcutAction.valueOf(value)))
                    pendingBinding = null
                    dialog = ChNavDialog.NONE
                },
                onDismiss = { pendingBinding = null; dialog = ChNavDialog.NONE },
            )
        }
        ChNavDialog.RESET -> PickerDialog(
            title = stringResource(R.string.settings_remote_shortcuts_reset),
            options = listOf(
                "true" to stringResource(R.string.common_reset),
                "false" to stringResource(R.string.common_cancel),
            ),
            selected = "false",
            onSelect = { value ->
                if (value.toBoolean()) vm.resetRemoteShortcutBindings()
                dialog = ChNavDialog.NONE
            },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.NONE -> Unit
    }
}

@Composable
private fun RemoteButtonCapturePopup(
    onCaptured: (Int, RemoteShortcutPress) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var activeKey by remember { mutableIntStateOf(AndroidKeyEvent.KEYCODE_UNKNOWN) }
    var pressedAt by remember { mutableStateOf(0L) }
    BackHandler { onDismiss() }

    // A Stage popup that takes the next key press; the panel catches it before the focused button does.
    LaunchedEffect(focus) {
        withFrameNanos { }
        kotlinx.coroutines.delay(60)
        runCatching { focus.requestFocus() }
    }
    val captureKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
        val keyCode = event.nativeKeyEvent.keyCode
        if (RemoteShortcutBindings.isProtectedKey(keyCode)) {
            false
        } else {
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (activeKey == AndroidKeyEvent.KEYCODE_UNKNOWN) {
                        activeKey = keyCode
                        pressedAt = System.currentTimeMillis()
                    }
                    activeKey == keyCode
                }
                KeyEventType.KeyUp -> {
                    if (activeKey != keyCode) false
                    else {
                        val press = if (System.currentTimeMillis() - pressedAt >= CAPTURE_LONG_PRESS_MS) {
                            RemoteShortcutPress.LONG
                        } else RemoteShortcutPress.SHORT
                        activeKey = AndroidKeyEvent.KEYCODE_UNKNOWN
                        onCaptured(keyCode, press)
                        true
                    }
                }
                else -> activeKey == keyCode
            }
        }
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_remote_shortcuts_capture_title),
        body = stringResource(R.string.settings_remote_shortcuts_capture_description),
        width = 760.mpx,
        modifier = Modifier.onPreviewKeyEvent(captureKeyEvent),
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(focus))
        },
    )
}

private const val CAPTURE_LONG_PRESS_MS = 600L
