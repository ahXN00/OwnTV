package tv.own.owntv.features.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.settings.StageFieldRow
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.displayText

/**
 * Restore a backup: from a phone over Wi-Fi, or from a file on this device. Private for the automation
 * API ([SensitiveScreenMarker]): its keys must not start a restore, which brings profiles with their
 * Kids flag and PIN.
 */
@Composable
internal fun BackupChooserStep(wizardStep: WizardStep, onBack: () -> Unit, onPhone: () -> Unit, onLocalFile: () -> Unit) {
    SensitiveScreenMarker()
    val first = rememberFirstFocus()
    WizardFrame(
        step = wizardStep,
        title = stringResource(R.string.setup_restore_a_backup),
        sub = stringResource(R.string.setup_restore_choice_description),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = null,
    ) {
        WizardCards(
            listOf(
                WizardCard(OwnTVIcon.PHONE, stringResource(R.string.setup_from_phone), stringResource(R.string.setup_upload_from_wifi_device), onPhone),
                WizardCard(OwnTVIcon.FOLDER, stringResource(R.string.setup_local_file), stringResource(R.string.setup_pick_backup_local), onLocalFile),
            ),
            first,
        )
    }
}

/**
 * Restoring a backup: the file picker, then progress, the password question or the failure, in the frame.
 * Private for the automation API ([SensitiveScreenMarker]), and so is the section picker shown over it: a
 * restore merges profiles (Kids flag, PIN) and the prompt takes the backup password.
 */
@Composable
internal fun ImportBackupStep(
    state: SourceImporter.ImportState,
    wizardStep: WizardStep,
    onPick: (java.io.File) -> Unit,
    /** A file is picked: the browser closes, so it is not left behind the restore dialog. */
    fileChosen: Boolean,
    onPassword: (java.io.File, String?) -> Unit,
    onBack: () -> Unit,
) {
    SensitiveScreenMarker()
    when (state) {
        SourceImporter.ImportState.Running -> WizardFrame(
            step = wizardStep,
            title = stringResource(R.string.setup_restoring),
            sub = null,
            back = null,
            next = null,
        )
        is SourceImporter.ImportState.NeedPassword -> {
            var password by remember { mutableStateOf("") }
            val first = rememberFirstFocus()
            val label = stringResource(R.string.setup_backup_password)
            WizardFrame(
                step = wizardStep,
                title = stringResource(if (state.retry) R.string.setup_wrong_backup_password else R.string.setup_enter_backup_password),
                sub = when {
                    state.retry && state.sealed -> stringResource(R.string.setup_password_mismatch_sealed)
                    state.retry -> stringResource(R.string.setup_password_mismatch)
                    state.sealed -> stringResource(R.string.setup_backup_encrypted_prompt)
                    else -> stringResource(R.string.setup_backup_passwords_encrypted_prompt)
                },
                back = WizardAction(stringResource(R.string.common_back), onBack),
                next = WizardAction(stringResource(R.string.setup_restore), { if (password.isNotBlank()) onPassword(state.file, password) }, enabled = password.isNotBlank()),
                // No "Skip" for a sealed container: without the password there is nothing to restore.
                extraLeft = if (state.sealed) emptyList() else listOf(WizardAction(stringResource(R.string.setup_skip_no_passwords), { onPassword(state.file, null) })),
            ) {
                StageFieldRow(
                    OwnTVIcon.PENCIL, label, password, { password = it }, SettingHelp(label, ""),
                    password = true, modifier = Modifier.focusRequester(first),
                )
            }
        }
        is SourceImporter.ImportState.Failed -> WizardFrame(
            step = wizardStep,
            title = stringResource(R.string.setup_restore_failed),
            sub = state.failure.displayText(),
            back = null,
            next = WizardAction(stringResource(R.string.common_back), onBack, rememberFirstFocus()),
            onBack = onBack,
        )
        else -> if (!fileChosen) StorageBrowser(
            title = stringResource(R.string.setup_pick_backup_file),
            mode = BrowseMode.FILE,
            // `.own` containers plus pre-4.2 `.json` backups.
            fileExtensions = tv.own.owntv.core.backup.BackupManager.RESTORE_EXTENSIONS,
            onPick = onPick,
            onDismiss = onBack,
        )
    }
}
