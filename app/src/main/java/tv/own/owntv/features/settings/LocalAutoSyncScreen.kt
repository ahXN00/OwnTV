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
 * One paired device's auto-sync settings: what travels to it, and when it travels by itself.
 *
 * The shape is the one the phone app uses — a list of questions, and a Save button that is the only
 * thing that makes anything real. Nothing here changes the saved settings as it is touched: the
 * screen edits a draft, and only Save writes it down. A television that saved as it ticked would
 * leave a half-finished schedule behind every time the user pressed Back.
 *
 * The questions are the ones a backup asks — which parts of the data, whose profiles, and (a thing
 * a backup does not have) how often — so the words on the rows are the ones the rest of the app
 * already uses for them. The device is not one of the questions: [device] is the answer to it, and
 * the row that opened this sheet already named it.
 *
 * The profile list is only drawn when there is a choice to make: a household with one viewer has
 * already answered that question, and a row that can only be ticked again is noise.
 */
@Composable
fun LocalAutoSyncSheet(device: PairedDevice, onDismiss: () -> Unit) {
    val vm: LocalAutoSyncViewModel = koinViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    // The draft. Null means "not touched yet", so the sheet shows what is saved until the user
    // changes something — the settings arrive from disk a moment after the sheet opens.
    var timing by remember(device.id) { mutableStateOf<LocalAutoSyncTiming?>(null) }
    var sections by remember(device.id) { mutableStateOf<Set<BackupManager.Section>?>(null) }
    var profileDraft by remember(device.id) { mutableStateOf<Set<Long>?>(null) }
    val config = settings[device.id]

    // An empty set in the config means "everything", so a device that was never asked shows every
    // row ticked. Ids that no longer exist cannot be shown, and drop out of the intersection.
    val allProfiles = profiles.map { it.id }.toSet()
    val chosenTiming = timing ?: config.timing
    val chosenSections = sections ?: config.sections
    // With one profile there is no row to tick, so the hidden list means "everything" — including
    // when the saved answer names a profile that has since been deleted, which would otherwise
    // leave Save disabled with no row to press.
    val chosenProfiles = profileDraft
        ?: if (profiles.size <= 1) {
            allProfiles
        } else {
            config.profileIds.takeIf { it.isNotEmpty() }?.intersect(allProfiles) ?: allProfiles
        }

    val panel = remember { FocusRequester() }
    // The popup's window does not own focus for the first frame or two, so the request is retried
    // per frame until it lands.
    LaunchedEffect(Unit) { restoreAfterDialogClose(panel) }
    val locale = LocalConfiguration.current.locales[0]

    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.local_auto_sync_settings_title),
        eyebrow = stringResource(R.string.local_auto_sync_title).uppercase(locale),
        body = stringResource(R.string.local_auto_sync_for_device, device.name),
        buttons = {
            StagePopupButton(
                stringResource(R.string.local_auto_sync_save),
                onClick = {
                    vm.save(
                        deviceId = device.id,
                        timing = chosenTiming,
                        sections = chosenSections,
                        // Everything ticked is saved as "all", not as a list of today's ids: a
                        // viewer added next week belongs in a schedule that says "all of them".
                        profileIds = if (chosenProfiles.size == allProfiles.size) emptySet() else chosenProfiles,
                    )
                    onDismiss()
                },
                // Nothing ticked means nothing to sync. The button says so rather than saving a
                // schedule that would go and fetch a whole backup to do nothing with it.
                enabled = chosenSections.isNotEmpty() &&
                    (allProfiles.isEmpty() || chosenProfiles.isNotEmpty()),
            )
        },
    ) {
        Column(
            Modifier.focusGroup().focusRequester(panel),
            verticalArrangement = Arrangement.spacedBy(2.mpx),
        ) {
            StagePopupLabel(stringResource(R.string.local_auto_sync_what_to_sync).uppercase(locale))
            BackupManager.Section.entries.forEach { section ->
                StagePopupOption(
                    title = stringResource(section.labelRes()),
                    onClick = {
                        val current = chosenSections
                        sections = if (section in current) current - section else current + section
                    },
                    leading = { focused -> StagePopupCheck(section in chosenSections, focused) },
                )
            }
            if (profiles.size > 1) {
                StagePopupDivider()
                StagePopupLabel(stringResource(R.string.local_auto_sync_what_profiles).uppercase(locale))
                profiles.forEach { profile ->
                    StagePopupOption(
                        title = profile.name,
                        onClick = {
                            profileDraft = if (profile.id in chosenProfiles) {
                                chosenProfiles - profile.id
                            } else {
                                chosenProfiles + profile.id
                            }
                        },
                        leading = { focused -> StagePopupCheck(profile.id in chosenProfiles, focused) },
                    )
                }
            }
            StagePopupDivider()
            StagePopupLabel(stringResource(R.string.local_auto_sync_when).uppercase(locale))
            LocalAutoSyncTiming.entries.forEach { option ->
                StagePopupOption(
                    title = stringResource(option.labelRes()),
                    onClick = { timing = option },
                    leading = { focused -> StagePopupRadio(option == chosenTiming, focused) },
                )
            }
            // What the last run with this device did, when there was one. A setting that says
            // "daily" and a device that has not run in a week are both true at once, and only one
            // of them is on screen here — so the other one has to be.
            if (config.lastRunAt > 0L) {
                Text(
                    stringResource(
                        R.string.local_auto_sync_last_run,
                        android.text.format.DateUtils.getRelativeTimeSpanString(
                            config.lastRunAt,
                            System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS,
                        ).toString(),
                    ),
                    style = stageText(18, 500),
                    color = StageColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.mpx),
                )
            }
            Text(
                stringResource(R.string.local_auto_sync_note),
                style = stageText(18, 400),
                color = StageColors.Muted,
                modifier = Modifier.padding(top = 6.mpx),
            )
        }
    }
}

/** The word on the "When" rows, and on the paired device's row when it has a schedule. */
internal fun LocalAutoSyncTiming.labelRes(): Int = when (this) {
    LocalAutoSyncTiming.OFF -> R.string.local_auto_sync_timing_off
    LocalAutoSyncTiming.STARTUP -> R.string.local_auto_sync_timing_immediately
    LocalAutoSyncTiming.HOURS_6 -> R.string.local_auto_sync_timing_6h
    LocalAutoSyncTiming.HOURS_24 -> R.string.local_auto_sync_timing_daily
    LocalAutoSyncTiming.DAYS_7 -> R.string.local_auto_sync_timing_weekly
}
