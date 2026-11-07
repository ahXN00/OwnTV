package tv.own.owntv.features.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.backup.BackupManager.ProfileRestoreChoice
import tv.own.owntv.core.categorylock.PinAttempt
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.features.profiles.ProtectedProfilePinDialog
import tv.own.owntv.ui.components.restoreAfterDialogClose
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.theme.mpx

/**
 * A restore's profiles that have the name of one already here, asked about one at a time: Merge into it,
 * Replace it, or Keep both (the file's comes in as "<name> (restored)"). Merging or replacing a protected
 * profile other than the one in use asks one of its PINs first; Back on the PIN returns to the choice.
 * Back on a choice cancels the restore.
 */
@Composable
internal fun RestoreClashStep(
    ask: BackupViewModel.State.ProfileClashes,
    verify: suspend (ProfileEntity, String) -> PinAttempt,
    onDone: (Map<Long, ProfileRestoreChoice>) -> Unit,
    onCancel: () -> Unit,
) {
    var index by remember(ask) { mutableIntStateOf(0) }
    var choices by remember(ask) { mutableStateOf(emptyMap<Long, ProfileRestoreChoice>()) }
    var pinFor by remember(ask) { mutableStateOf<ProfileRestoreChoice?>(null) }
    val clash = ask.clashes.getOrNull(index)
    if (clash == null) {
        LaunchedEffect(ask) { onDone(choices) }
        return
    }
    val here = ask.profiles.firstOrNull { it.id == clash.deviceProfileId }
    val needsPin = here != null && clash.deviceProfileId != ask.activeId && clash.deviceProfileId in ask.protectedIds
    val keepName = stringResource(R.string.settings_backup_profile_restored_name, clash.name)
    val take = { choice: ProfileRestoreChoice ->
        choices = choices + (clash.fileProfileId to choice)
        pinFor = null
        index++
    }
    val pick = { choice: ProfileRestoreChoice -> if (needsPin && choice !is ProfileRestoreChoice.KeepBoth) pinFor = choice else take(choice) }
    key(clash.fileProfileId) {
        val pending = pinFor
        if (pending != null && here != null) {
            ProtectedProfilePinDialog(
                profileName = here.name,
                body = stringResource(R.string.profiles_protected_edit),
                verify = { pin -> verify(here, pin) },
                onUnlocked = { take(pending) },
                onDismiss = { pinFor = null },
            )
        } else {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { restoreAfterDialogClose(first) }
            StagePopup(
                onDismiss = onCancel,
                title = stringResource(R.string.settings_backup_profile_clash_title, clash.name),
                body = stringResource(R.string.settings_backup_profile_clash_body),
                width = 820.mpx,
            ) {
                Column(Modifier.focusGroup().focusRequester(first), verticalArrangement = Arrangement.spacedBy(2.mpx)) {
                    StagePopupOption(
                        title = stringResource(R.string.settings_backup_profile_merge),
                        subtitle = stringResource(R.string.settings_backup_profile_merge_description),
                        onClick = { pick(ProfileRestoreChoice.Merge) },
                    )
                    StagePopupOption(
                        title = stringResource(R.string.settings_backup_profile_replace),
                        subtitle = stringResource(R.string.settings_backup_profile_replace_description),
                        onClick = { pick(ProfileRestoreChoice.Replace) },
                    )
                    StagePopupOption(
                        title = stringResource(R.string.settings_backup_profile_keep_both),
                        subtitle = stringResource(R.string.settings_backup_profile_keep_both_description, keepName),
                        onClick = { pick(ProfileRestoreChoice.KeepBoth(keepName)) },
                    )
                }
            }
        }
    }
}
