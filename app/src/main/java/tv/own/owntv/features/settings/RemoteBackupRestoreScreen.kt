package tv.own.owntv.features.settings

import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.StageColors
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.companion.CompanionServerState
import java.io.File

/**
 * Remote restore: opens the LAN companion server in backup-upload mode and shows the PIN, a QR of the
 * URL, and the URL text so another device on the same Wi-Fi can send an OwnTV backup file to the TV. When an
 * upload arrives, [onBackupReceived] hands the saved file off to the normal restore flow (section
 * picker, password prompt). The listener stops automatically when this screen leaves composition.
 *
 * Shared by More → Backup & Restore and the first-run/add-profile setup wizard.
 */
@Composable
fun RemoteBackupRestoreScreen(
    state: CompanionServerState,
    backups: Flow<File>,
    onStart: (port: Int) -> Unit,
    onStop: () -> Unit,
    onBackupReceived: (File) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { actionFocus.requestFocus() } }

    // Open a fresh backup-upload session on entry; stop it when the screen leaves.
    LaunchedEffect(Unit) { onStart(CompanionLink.DEFAULT_PORT) }
    LaunchedEffect(backups) { backups.collect(onBackupReceived) }
    DisposableEffect(Unit) { onDispose { onStop() } }

    val restart = { onStart(CompanionLink.DEFAULT_PORT) }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onBack,
        title = stringResource(R.string.settings_restore_remote_title),
        body = stringResource(R.string.settings_restore_remote_description),
        width = 820.mpx,
        modifier = modifier,
        buttons = {
            when (state) {
                is CompanionServerState.Failed ->
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_try_again), onClick = restart, height = 56.mpx, textSize = 19)
                // Restarting mints a fresh PIN, so this is the recovery path — not a retry of a failure.
                CompanionServerState.Locked ->
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_new_pin), onClick = restart, height = 56.mpx, textSize = 19)
                else -> Unit
            }
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_back), onClick = onBack, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(actionFocus))
        },
    ) {
        tv.own.owntv.ui.components.StageCompanionStatus(state)
    }
}

/**
 * Remote export: the TV has written the backup to a cache file and is serving it over the companion
 * server. Shows the PIN, a QR of the URL, and the URL text so another device on the same Wi-Fi can
 * open it, enter the PIN and download the file. The server is started by the ViewModel (after the
 * export finishes) and stopped when this screen leaves composition.
 */
@Composable
fun RemoteBackupExportScreen(
    state: CompanionServerState,
    preparing: Boolean,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { actionFocus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { onStop() } }

    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onBack,
        title = stringResource(R.string.settings_download_remote_title),
        body = stringResource(R.string.settings_download_remote_description),
        width = 820.mpx,
        modifier = modifier,
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = onBack, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(actionFocus))
        },
    ) {
        if (preparing) {
            Text(stringResource(R.string.settings_preparing_backup), style = stageText(18, 500), color = StageColors.Muted)
        } else {
            tv.own.owntv.ui.components.StageCompanionStatus(state)
        }
    }
}
