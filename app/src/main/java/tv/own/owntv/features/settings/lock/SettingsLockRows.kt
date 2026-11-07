package tv.own.owntv.features.settings.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.core.categorylock.PinDoor
import tv.own.owntv.features.profiles.PinDialog
import tv.own.owntv.features.profiles.PinPrompt
import tv.own.owntv.features.shell.components.RootRow
import tv.own.owntv.features.shell.components.SettingsSearchEntry
import tv.own.owntv.features.shell.components.TileTone
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.mpx

/** Where Settings › App › Settings lock's popup is. */
internal sealed interface LockStep {
    /** The lock is on: Change PIN, Turn off. */
    data object Choose : LockStep
    /** The current Settings PIN, before [then]. */
    data class Check(val then: LockStep) : LockStep
    data object TurnOff : LockStep
    data object NewPin : LockStep
    data class Confirm(val first: String, val mismatch: Boolean = false) : LockStep
}

/** The Settings lock rows' popup state, and the row its focus returns to. */
@Stable
internal class SettingsLockState {
    val rowFocus = FocusRequester()
    internal var step by mutableStateOf<LockStep?>(null)
    val open: Boolean get() = step != null

    fun start(on: Boolean) {
        step = if (on) LockStep.Choose else LockStep.NewPin
    }
}

@Composable
internal fun rememberSettingsLockState(): SettingsLockState = remember { SettingsLockState() }

/**
 * Settings › App › Settings lock, and — only while it is on — its "Also lock long-press menu changes"
 * switch. [onOpen] is the caller's bookkeeping before the popup opens (scroll, focus return).
 */
@Composable
internal fun settingsLockRootRows(state: SettingsLockState, vm: SettingsPinViewModel, onOpen: () -> Unit): List<RootRow> {
    val lock by vm.lock.collectAsStateWithLifecycle()
    return listOfNotNull(
        RootRow(
            "settings_lock", TileTone.SECONDARY, OwnTVIcon.LOCK,
            title = stringResource(R.string.settings_lock), desc = stringResource(R.string.settings_lock_description),
            chip = stringResource(if (lock.on) R.string.common_on else R.string.common_off),
            chipTone = if (lock.on) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = state.rowFocus,
            onClick = { onOpen(); state.start(lock.on) },
        ),
        RootRow(
            "settings_lock_menus", TileTone.SECONDARY, OwnTVIcon.LOCK,
            title = stringResource(R.string.settings_lock_menus), desc = stringResource(R.string.settings_lock_menus_description),
            chip = stringResource(if (lock.lockMenus) R.string.common_on else R.string.common_off),
            chipTone = if (lock.lockMenus) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { vm.setLockMenus(!lock.lockMenus) },
        ).takeIf { lock.on },
    )
}

/** The same rows in Settings search. */
@Composable
internal fun settingsLockSearchEntries(state: SettingsLockState, vm: SettingsPinViewModel, onOpen: () -> Unit): List<SettingsSearchEntry> {
    val lock by vm.lock.collectAsStateWithLifecycle()
    val group = stringResource(R.string.settings_group_app)
    val keywords = stringResource(R.string.settings_search_keywords_settings_lock)
    return listOfNotNull(
        SettingsSearchEntry(
            group, stringResource(R.string.settings_lock), keywords, OwnTVIcon.LOCK, TileTone.SECONDARY,
            chip = stringResource(if (lock.on) R.string.common_on else R.string.common_off),
            chipTone = if (lock.on) TileTone.PRIMARY else TileTone.SECONDARY,
        ) { onOpen(); state.start(lock.on) },
        SettingsSearchEntry(
            group, stringResource(R.string.settings_lock_menus), keywords, OwnTVIcon.LOCK, TileTone.SECONDARY,
            chip = stringResource(if (lock.lockMenus) R.string.common_on else R.string.common_off),
            chipTone = if (lock.lockMenus) TileTone.PRIMARY else TileTone.SECONDARY,
            showChevron = false,
        ) { vm.setLockMenus(!lock.lockMenus) }.takeIf { lock.on },
    )
}

/**
 * The popups behind the row. Off: a new PIN, twice, turns it on. On: Change PIN or Turn off, each
 * asking the current Settings PIN first — wrong entries count only on the Settings door.
 */
@Composable
internal fun SettingsLockDialogHost(state: SettingsLockState, vm: SettingsPinViewModel) {
    val step = state.step ?: return
    val lock by vm.lock.collectAsStateWithLifecycle()
    val close = { state.step = null }
    when (step) {
        LockStep.Choose -> tv.own.owntv.ui.stage.StagePopup(
            onDismiss = close,
            title = stringResource(R.string.settings_lock),
            body = stringResource(R.string.settings_lock_description),
            width = 760.mpx,
            buttons = {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = close, height = 56.mpx, textSize = 19)
                tv.own.owntv.ui.stage.StageButton(
                    stringResource(R.string.settings_lock_turn_off),
                    onClick = { state.step = LockStep.Check(LockStep.TurnOff) },
                    height = 56.mpx, textSize = 19,
                )
                tv.own.owntv.ui.stage.StageButton(
                    stringResource(R.string.settings_lock_change_pin),
                    onClick = { state.step = LockStep.Check(LockStep.NewPin) },
                    height = 56.mpx, textSize = 19, tinted = true,
                )
            },
        )
        is LockStep.Check -> {
            val pin = lock.pin
            if (pin == null) {
                LaunchedEffect(Unit) { close() }
            } else {
                PinPrompt(
                    pin = pin,
                    door = PinDoor.SETTINGS,
                    onUnlock = {
                        if (step.then == LockStep.TurnOff) {
                            vm.setPin(null)
                            close()
                        } else {
                            state.step = step.then
                        }
                    },
                    onDismiss = close,
                )
            }
        }
        LockStep.TurnOff -> LaunchedEffect(Unit) { close() }
        LockStep.NewPin -> key("first") {
            PinDialog(
                title = stringResource(R.string.settings_customize_new_pin),
                onSubmit = { entered -> state.step = LockStep.Confirm(entered) },
                onDismiss = close,
                compact = true,
            )
        }
        is LockStep.Confirm -> key("confirm", step.mismatch) {
            PinDialog(
                title = stringResource(if (step.mismatch) R.string.settings_customize_pin_mismatch else R.string.settings_customize_confirm_pin),
                onSubmit = { entered ->
                    if (entered == step.first) {
                        vm.setPin(entered)
                        close()
                    } else {
                        state.step = step.copy(mismatch = true)
                    }
                },
                onDismiss = close,
                compact = true,
            )
        }
    }
}
