package tv.own.owntv.features.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.sync.local.AutoSyncAgreement
import tv.own.owntv.core.sync.local.LocalAutoSyncTiming
import tv.own.owntv.core.sync.local.PairedDevice
import tv.own.owntv.ui.components.restoreAfterDialogClose
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupButton
import tv.own.owntv.ui.stage.StagePopupCheck
import tv.own.owntv.ui.stage.StagePopupDivider
import tv.own.owntv.ui.stage.StagePopupLabel
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.stage.StagePopupRadio
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * One paired device's auto-sync: the switch, what this device lets travel, whose profiles, how often.
 *
 * Turning the switch on asks the other device (its Local sync page open), whose screen shows the
 * question; this side's terms are written only on its yes, with the choices below as they are then —
 * so they can be set first. Turning it off is local and immediate, and the other device is told.
 * Once on, the choices are a draft until Save.
 */
@Composable
fun LocalAutoSyncSheet(device: PairedDevice, onDismiss: () -> Unit) {
    val vm: LocalAutoSyncViewModel = koinViewModel()
    val agreements by vm.agreements.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val enableState by vm.enableState.collectAsStateWithLifecycle()
    val agreement = agreements[device.id]
    val enabled = agreement != null
    var timing by remember(device.id) { mutableStateOf<LocalAutoSyncTiming?>(null) }
    var sections by remember(device.id) { mutableStateOf<Set<BackupManager.Section>?>(null) }
    val asking = enableState is LocalAutoSyncViewModel.EnableState.Asking

    val chosenTiming = timing ?: agreement?.timing ?: LocalAutoSyncTiming.DEFAULT
    val chosenSections = sections ?: agreement?.sections ?: AutoSyncAgreement.DEFAULT_SECTIONS
    val choicesValid = chosenSections.isNotEmpty()

    val panel = remember { FocusRequester() }
    LaunchedEffect(Unit) { restoreAfterDialogClose(panel) }
    val locale = LocalConfiguration.current.locales[0]
    val dismiss = {
        vm.cancelEnable()
        onDismiss()
    }

    StagePopup(
        onDismiss = dismiss,
        title = device.name,
        eyebrow = stringResource(R.string.local_auto_sync_title).uppercase(locale),
        body = stringResource(R.string.local_auto_sync_for_device, device.name),
        buttons = {
            StagePopupButton(
                stringResource(R.string.common_save),
                onClick = {
                    vm.save(device.id, chosenSections, chosenTiming)
                    onDismiss()
                },
                enabled = enabled && !asking && choicesValid,
            )
        },
    ) {
        Column(Modifier.focusGroup().focusRequester(panel), verticalArrangement = Arrangement.spacedBy(2.mpx)) {
            StagePopupOption(
                title = stringResource(R.string.local_auto_sync_enable),
                subtitle = when {
                    asking -> stringResource(R.string.local_auto_sync_waiting, device.name)
                    enabled -> null
                    else -> stringResource(R.string.local_auto_sync_enable_hint)
                },
                onClick = {
                    if (enabled) vm.disable(device.id) else vm.enable(device, chosenSections, chosenTiming)
                },
                enabled = !asking && (enabled || choicesValid),
                leading = { focused -> StagePopupCheck(enabled, focused) },
            )
            when (val state = enableState) {
                is LocalAutoSyncViewModel.EnableState.Declined -> StatusText(stringResource(R.string.local_auto_sync_declined, state.deviceName), danger = true)
                is LocalAutoSyncViewModel.EnableState.Failed -> StatusText(stringResource(state.reason.messageRes()), danger = true)
                else -> Unit
            }
            // Which profiles this is between: fixed when it was set up, whatever is on screen later.
            val activeId by vm.activeProfileId.collectAsStateWithLifecycle()
            val hereName = profiles.firstOrNull { it.id == (agreement?.localProfileId ?: activeId) }?.name.orEmpty()
            StatusText(
                if (agreement != null) stringResource(R.string.local_auto_sync_profiles, hereName, agreement.theirProfileName)
                else stringResource(R.string.local_auto_sync_profile_hint, hereName, device.name),
            )
            agreement?.theirSections?.takeIf { it.isNotEmpty() }?.let { theirs ->
                StatusText(
                    stringResource(
                        R.string.local_auto_sync_their_sections,
                        device.name,
                        BackupManager.Section.entries.filter { it in theirs }.map { stringResource(it.labelRes()) }
                            .joinToString(stringResource(R.string.content_epg_bits_separator)),
                    ),
                )
            }
            StagePopupDivider()
            StagePopupLabel(stringResource(R.string.local_auto_sync_what_to_sync).uppercase(locale))
            BackupManager.Section.entries.forEach { section ->
                StagePopupOption(
                    title = stringResource(section.labelRes()),
                    onClick = { sections = if (section in chosenSections) chosenSections - section else chosenSections + section },
                    enabled = !asking,
                    leading = { focused -> StagePopupCheck(section in chosenSections, focused) },
                )
            }
            StagePopupDivider()
            StagePopupLabel(stringResource(R.string.local_auto_sync_when).uppercase(locale))
            LocalAutoSyncTiming.entries.forEach { option ->
                StagePopupOption(
                    title = stringResource(option.labelRes()),
                    onClick = { timing = option },
                    enabled = !asking,
                    leading = { focused -> StagePopupRadio(option == chosenTiming, focused) },
                )
            }
            Text(
                stringResource(R.string.local_auto_sync_note),
                style = stageText(18, 400),
                color = StageColors.Muted,
                modifier = Modifier.padding(top = 10.mpx),
            )
        }
    }
}

@Composable
private fun StatusText(text: String, danger: Boolean = false) {
    Text(
        text,
        style = stageText(18, 500),
        color = if (danger) StageColors.Danger else StageColors.Muted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.mpx, start = 22.mpx),
    )
}

/** The words on the "How often" rows and on the paired device's row. */
internal fun LocalAutoSyncTiming.labelRes(): Int = when (this) {
    LocalAutoSyncTiming.IMMEDIATELY -> R.string.local_auto_sync_timing_immediately
    LocalAutoSyncTiming.HOURS_6 -> R.string.settings_epg_refresh_6h
    LocalAutoSyncTiming.HOURS_24 -> R.string.local_auto_sync_timing_daily
    LocalAutoSyncTiming.DAYS_7 -> R.string.local_auto_sync_timing_weekly
}
