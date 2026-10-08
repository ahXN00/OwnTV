package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.theme.OwnTVTheme
import java.io.File
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTile
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * Phase 12 — Backup & Restore (Settings → Backup), with selective sections: the user picks what to
 * back up (profiles & sources / customizations / favorites / history / resume) and, on restore,
 * which of the file's sections to apply. Uses an in-app file picker (no SAF).
 */
@Composable
fun BackupScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** More › Backup & Restore: what OK / ▶ on the sheet focuses. Settings passes none and gets focus on open. */
    entry: FocusRequester? = null,
) {
    val vm: BackupViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    // A restore can bring a different icon colour than the launcher shows: offer the restart.
    val restoredIcon by vm.restoredIcon.collectAsStateWithLifecycle()
    val appliedIcon = tv.own.owntv.ui.components.rememberAppliedIcon()
    restoredIcon?.takeIf { it != appliedIcon }?.let { icon ->
        tv.own.owntv.ui.components.AppIconRestartDialog(icon, onDismiss = vm::clearRestoredIcon)
    }

    var browser by remember { mutableStateOf(BrowseMode.FOLDER) } // which picker
    var showBrowser by remember { mutableStateOf(false) }
    var showExportPicker by remember { mutableStateOf(false) }
    // Opening the folder browser in the SAME frame the section picker closes makes the browser's
    // initial focus grab race the picker's teardown — focus ends up trapped on the screen behind
    // the overlay. Defer the open by a beat instead.
    var pendingFolderBrowser by remember { mutableStateOf(false) }
    LaunchedEffect(pendingFolderBrowser) {
        if (pendingFolderBrowser) {
            kotlinx.coroutines.delay(120)
            browser = BrowseMode.FOLDER
            showBrowser = true
            pendingFolderBrowser = false
        }
    }
    var exportSections by remember { mutableStateOf(BackupManager.Section.entries.toSet()) }
    // Export step 0: which profiles ride in the file (backup is profile-based). PIN-locked profiles
    // other than the active one must be unlocked with their PIN to be ticked.
    var showProfilePicker by remember { mutableStateOf(false) }
    var exportProfiles by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val profileChoices by vm.profileChoices.collectAsStateWithLifecycle()
    // After the folder is picked, hold it here to ask about password protection before exporting.
    var exportFolder by remember { mutableStateOf<File?>(null) }
    val ownFocus = remember { FocusRequester() }
    val firstFocus = entry ?: ownFocus
    val restoreBtnFocus = remember { FocusRequester() }
    if (entry == null) LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { firstFocus.requestFocus() } }

    // Restore: first pick Remote (upload from another device) or Local (file picker). Remote opens a full-screen
    // companion panel; an uploaded file drops back into the same inspect → section-picker flow.
    var showRemoteRestore by remember { mutableStateOf(false) }
    val remoteState by vm.remoteState.collectAsStateWithLifecycle()

    // Export: Remote (serve the file for another device to download) or Local (save to a folder).
    var exportToRemote by remember { mutableStateOf(false) }
    var showRemoteExportPassword by remember { mutableStateOf(false) }
    var showRemoteExport by remember { mutableStateOf(false) }
    // If the remote export fails to prepare, drop the panel so the base screen shows the error.
    LaunchedEffect(state) {
        if (state is BackupViewModel.State.Error && showRemoteExport) {
            vm.stopRemoteExport(); showRemoteExport = false
        }
    }

    if (onBack != null) BackHandler { onBack() }

    // Dialog-close focus return: closing the section picker / file browser refocuses the button
    // that opened it. The restore crosses INTO this group from the dialog, so onEnter intercepts
    // it — it consults dialogReturn first (and clears it) instead of hijacking.
    // Deliberately NOT tv.own.owntv.ui.components.rememberDialogFocusRestore: the onEnter below also
    // reads and clears this, and the shared helper clears it right after its own restore. Which of the
    // two wins would come down to whether onEnter runs inside requestFocus(), and this screen's restore
    // is not worth betting on that ordering.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    val anyDialogOpen = showBrowser || showExportPicker || showProfilePicker || pendingFolderBrowser || exportFolder != null ||
        showRemoteRestore || showRemoteExportPassword || showRemoteExport ||
        state is BackupViewModel.State.ChooseRestore || state is BackupViewModel.State.NeedPassword
    LaunchedEffect(anyDialogOpen) {
        if (!anyDialogOpen) {
            dialogReturn?.let { btn ->
                kotlinx.coroutines.delay(80)
                runCatching { btn.requestFocus() }
            }
        }
    }

    // The counts the warning card names, from More's read-only view model.
    val counts: tv.own.owntv.features.more.MoreCountsViewModel = koinViewModel()
    val lastBackup by counts.lastBackup.collectAsStateWithLifecycle()
    val favorites by counts.favorites.collectAsStateWithLifecycle()
    val history by counts.history.collectAsStateWithLifecycle()
    val playlists by counts.playlistCount.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    val working = state == BackupViewModel.State.Working
    val phoneFocus = remember { FocusRequester() }
    val startExport: (Boolean, FocusRequester) -> Unit = { remote, from ->
        if (!working) { dialogReturn = from; exportToRemote = remote; vm.loadProfiles(); showProfilePicker = true }
    }

    // P8-05: title, the last-backup card (amber "Never backed up" until there is one), Back up now and
    // Restore, and what a backup holds.
    Column(
        modifier = modifier
            // onEnter fires for any entry from outside the group — including our own dialog-close
            // restores (the dialogs live outside it) — so it must prefer the pending return button.
            .focusProperties {
                onEnter = {
                    val target = dialogReturn ?: firstFocus
                    dialogReturn = null
                    runCatching { target.requestFocus() }
                }
            }
            .focusGroup(),
    ) {
        Text(stringResource(R.string.settings_backup_title), style = stageText(42, 800, (-1).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val last = lastBackup
        val cardShape = RoundedCornerShape(26.mpx)
        Row(
            Modifier
                .padding(top = 26.mpx)
                .fillMaxWidth()
                .then(
                    if (last == null) {
                        Modifier
                            .background(StageColors.Warn.copy(alpha = 0.1f), cardShape)
                            .border(1.5.mpx, StageColors.Warn.copy(alpha = 0.35f), cardShape)
                    } else Modifier.background(Color.White.copy(alpha = 0.05f), cardShape),
                )
                .padding(24.mpx),
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (last == null) {
                OwnTVIcon(OwnTVIcon.WARNING, StageColors.Warn, Modifier.size(40.mpx))
                Column {
                    Text(stringResource(R.string.more_backup_never_title), style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(
                            R.string.more_backup_never_body,
                            pluralStringResource(R.plurals.more_playlist_count, playlists, playlists),
                            pluralStringResource(R.plurals.more_count_favorites, favorites.total, favorites.total),
                            pluralStringResource(R.plurals.more_count_history, history.total, history.total),
                        ),
                        style = stageText(17, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 4.mpx),
                    )
                }
            } else {
                OwnTVIcon(OwnTVIcon.CHECK, stageAccent.accent, Modifier.size(40.mpx))
                Column {
                    Text(
                        stringResource(R.string.more_pane_last_backup) + sep + android.text.format.DateUtils.getRelativeTimeSpanString(
                            last.at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
                        ),
                        style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            tv.own.owntv.ui.format.formatBestDateTime(context, "dMMMyyyyjm", last.at),
                            android.text.format.Formatter.formatShortFileSize(context, last.bytes),
                            if (last.encrypted) stringResource(R.string.more_pane_encrypted) else null,
                            last.path.takeIf { it.isNotBlank() },
                        ).joinToString(sep),
                        style = stageText(17, 500), color = StageColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.mpx),
                    )
                }
            }
        }

        Row(
            Modifier.padding(top = 22.mpx).fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
        ) {
            // Back up now: the card lights up while either of its two buttons holds focus.
            var backupFocused by remember { mutableStateOf(false) }
            StageTile(
                modifier = Modifier.weight(1f).fillMaxHeight().onFocusChanged { backupFocused = it.hasFocus }.focusGroup(),
                focusedLook = backupFocused,
            ) { focused ->
                OwnTVIcon(OwnTVIcon.DOWNLOADS, if (focused) StageColors.Text else StageColors.Muted, Modifier.size(30.mpx))
                Text(stringResource(R.string.more_backup_now), style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx))
                Text(stringResource(R.string.more_backup_now_body), style = stageText(16, 500), color = if (focused) StageColors.Text.copy(alpha = 0.8f) else StageColors.Muted, modifier = Modifier.padding(top = 6.mpx))
                Row(Modifier.padding(top = 16.mpx), horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                    BackupTool(stringResource(R.string.more_backup_file_tv), OwnTVIcon.FOLDER, Modifier.focusRequester(firstFocus)) { startExport(false, firstFocus) }
                    BackupTool(stringResource(R.string.settings_backup_remote), OwnTVIcon.NETWORK, Modifier.focusRequester(phoneFocus)) { startExport(true, phoneFocus) }
                }
            }
            // Restore: the same two ways as Back up now, as buttons on the card (owner, P12) — no chooser popup.
            var restoreFocused by remember { mutableStateOf(false) }
            val restoreRemoteFocus = remember { FocusRequester() }
            StageTile(
                modifier = Modifier.weight(1f).fillMaxHeight().onFocusChanged { restoreFocused = it.hasFocus }.focusGroup(),
                focusedLook = restoreFocused,
            ) { focused ->
                OwnTVIcon(OwnTVIcon.REFRESH, if (focused) StageColors.Text else StageColors.Muted, Modifier.size(30.mpx))
                Text(stringResource(R.string.settings_backup_restore_action), style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx))
                Text(stringResource(R.string.more_restore_body), style = stageText(16, 500), color = if (focused) StageColors.Text.copy(alpha = 0.8f) else StageColors.Muted, modifier = Modifier.padding(top = 6.mpx))
                Row(Modifier.padding(top = 16.mpx), horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                    BackupTool(stringResource(R.string.more_backup_file_tv), OwnTVIcon.FOLDER, Modifier.focusRequester(restoreBtnFocus)) {
                        if (!working) { dialogReturn = restoreBtnFocus; browser = BrowseMode.FILE; showBrowser = true }
                    }
                    BackupTool(stringResource(R.string.settings_backup_remote), OwnTVIcon.NETWORK, Modifier.focusRequester(restoreRemoteFocus)) {
                        if (!working) { dialogReturn = restoreRemoteFocus; showRemoteRestore = true }
                    }
                }
            }
        }

        Text(
            stringResource(R.string.more_backup_included).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
            style = stageText(13, 800, 0.13.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 28.mpx, bottom = 12.mpx),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
            BackupManager.Section.entries.forEach { section ->
                Row(
                    Modifier.height(44.mpx).background(StageColors.ControlFill, RoundedCornerShape(15.mpx)).padding(horizontal = 16.mpx),
                    horizontalArrangement = Arrangement.spacedBy(9.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OwnTVIcon(OwnTVIcon.CHECK, stageAccent.accent, Modifier.size(18.mpx))
                    Text(stringResource(sectionLabelRes(section)), style = stageText(16, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // What the last action did — the same sentences as before, in Stage type.
        val status = @Composable { text: String, color: Color -> Text(text, style = stageText(17, 500), color = color, modifier = Modifier.padding(top = 20.mpx)) }
        when (val s = state) {
            BackupViewModel.State.Working -> Row(Modifier.padding(top = 20.mpx), verticalAlignment = Alignment.CenterVertically) {
                OwnTVSpinner(sizeDp = 22)
                Spacer(Modifier.width(12.mpx))
                Text(stringResource(R.string.settings_backup_working), style = stageText(17, 500), color = StageColors.Muted)
            }
            is BackupViewModel.State.Done -> when (s.kind) {
                DoneKind.EXPORTED -> status(
                    if (s.passwordsOmitted) stringResource(R.string.settings_backup_saved_to_without_passwords, s.path.orEmpty())
                    else stringResource(R.string.settings_backup_saved_to, s.path.orEmpty()),
                    stageAccent.accent,
                )
                DoneKind.RESTORED -> Column {
                    status(pluralStringResource(R.plurals.settings_backup_restored, s.items, s.items), stageAccent.accent)
                    Text(stringResource(R.string.settings_backup_restore_resync), style = stageText(16, 500), color = StageColors.Muted)
                    if (s.passwordsOmitted) Text(stringResource(R.string.settings_backup_restore_password_note), style = stageText(16, 500), color = StageColors.Muted)
                    if (s.skippedSources > 0) Text(pluralStringResource(R.plurals.settings_backup_skipped_sources, s.skippedSources, s.skippedSources), style = stageText(16, 500), color = StageColors.Muted)
                    if (s.invalidLocale) Text(stringResource(R.string.settings_backup_invalid_locale), style = stageText(16, 500), color = StageColors.Muted)
                }
            }
            is BackupViewModel.State.Error -> status(
                stringResource(
                    when (s.kind) {
                        BackupError.EXPORT -> R.string.settings_backup_export_error
                        BackupError.READ -> R.string.settings_backup_read_error
                        BackupError.IMPORT -> R.string.settings_backup_import_error
                    },
                ),
                StageColors.Danger,
            )
            else -> Unit
        }
    }

    // Export step 0: pick the profiles to include (all unticked — the user chooses; locked
    // non-active profiles need their PIN to be ticked).
    if (showProfilePicker) {
        profileChoices?.let { choices ->
            ProfilePickerDialog(
                profiles = choices.profiles,
                activeId = choices.activeId,
                verifyPin = vm::verifyPin,
                onConfirm = { picked ->
                    exportProfiles = picked
                    showProfilePicker = false
                    showExportPicker = true
                },
                onDismiss = { showProfilePicker = false },
            )
        }
    }

    // Export step 1: choose what to include, then pick the folder (local) or continue (remote).
    if (showExportPicker) {
        SectionPickerDialog(
            title = stringResource(R.string.settings_backup_what_backup),
            sections = BackupManager.Section.entries,
            initial = BackupManager.Section.entries.toSet(),
            confirmLabel = if (exportToRemote) stringResource(R.string.settings_backup_continue) else stringResource(R.string.settings_backup_choose_folder_action),
            onConfirm = { chosen ->
                exportSections = chosen
                showExportPicker = false
                if (exportToRemote) showRemoteExportPassword = true else pendingFolderBrowser = true
            },
            onDismiss = { showExportPicker = false },
        )
    }

    // Restore step 2: the picked file was inspected — choose which of its sections to apply.
    (state as? BackupViewModel.State.ChooseRestore)?.let { choose ->
        val deviceSettings = remember(choose.file) { mutableStateOf(false) }
        SectionPickerDialog(
            title = stringResource(R.string.settings_backup_what_restore),
            sections = BackupManager.Section.entries.filter { it in choose.available },
            initial = choose.available,
            confirmLabel = stringResource(R.string.settings_backup_restore_action),
            onConfirm = { chosen -> vm.beginImport(choose.file, chosen, choose.encrypted, choose.password, deviceSettings.value) },
            onDismiss = { vm.reset() },
            deviceSettings = deviceSettings.takeIf { choose.fromOtherDevice },
        )
    }

    if (showBrowser) {
        StorageBrowser(
            title = stringResource(if (browser == BrowseMode.FOLDER) R.string.settings_backup_choose_folder else R.string.settings_backup_pick_file),
            mode = browser,
            // `.own` is what we write now; `.json` stays so pre-4.2 backups keep restoring.
            fileExtensions = BackupManager.RESTORE_EXTENSIONS,
            onPick = { file -> showBrowser = false; if (browser == BrowseMode.FOLDER) exportFolder = file else vm.inspect(file) },
            onDismiss = { showBrowser = false },
        )
    }

    // Export step 3: ask whether to protect passwords with a backup passphrase (or export without them).
    exportFolder?.let { folder ->
        BackupPasswordDialog(
            title = stringResource(R.string.settings_backup_encrypt_title),
            message = stringResource(R.string.settings_backup_encrypt_message),
            confirmLabel = stringResource(R.string.settings_backup_encrypt_export),
            skipLabel = stringResource(R.string.settings_backup_export_unencrypted),
            onConfirm = { pass -> exportFolder = null; vm.export(folder, exportSections, pass, exportProfiles) },
            onSkip = { exportFolder = null; vm.export(folder, exportSections, null, exportProfiles) },
            onDismiss = { exportFolder = null },
        )
    }

    // Restore password prompt. Two shapes, see BackupViewModel.State.NeedPassword:
    //  - sealed .own  → asked FIRST, mandatory; unlocking then reveals the section picker.
    //  - field-encrypted → asked after the section picker, optional (skip = no saved passwords).
    (state as? BackupViewModel.State.NeedPassword)?.let { need ->
        BackupPasswordDialog(
            title = stringResource(if (need.retry) R.string.settings_backup_wrong_password else R.string.settings_backup_enter_password),
            message = when {
                need.retry && need.sealed -> stringResource(R.string.settings_backup_password_encrypted_mismatch)
                need.retry -> stringResource(R.string.settings_backup_password_mismatch)
                need.sealed -> stringResource(R.string.settings_backup_encrypted_description)
                else -> stringResource(R.string.settings_backup_saved_passwords_description)
            },
            confirmLabel = if (need.sealed) stringResource(R.string.settings_backup_unlock) else stringResource(R.string.settings_backup_restore_action),
            skipLabel = if (need.sealed) null else stringResource(R.string.settings_backup_skip_passwords),
            onConfirm = { pass ->
                val sections = need.sections
                if (sections == null) vm.unlock(need.file, pass) else vm.import(need.file, sections, pass, need.deviceSettings)
            },
            onSkip = { need.sections?.let { vm.import(need.file, it, null, need.deviceSettings) } },
            onDismiss = { vm.reset() },
        )
    }


    // Remote export step 2: password prompt, then export to cache + start serving the file.
    if (showRemoteExportPassword) {
        BackupPasswordDialog(
            title = stringResource(R.string.settings_backup_encrypt_title),
            message = stringResource(R.string.settings_backup_encrypt_message),
            confirmLabel = stringResource(R.string.settings_backup_encrypt_prepare),
            skipLabel = stringResource(R.string.settings_backup_prepare_unencrypted),
            onConfirm = { pass -> showRemoteExportPassword = false; showRemoteExport = true; vm.exportRemote(exportSections, pass, exportProfiles) },
            onSkip = { showRemoteExportPassword = false; showRemoteExport = true; vm.exportRemote(exportSections, null, exportProfiles) },
            onDismiss = { showRemoteExportPassword = false },
        )
    }

    // Remote export: full-screen panel with PIN + QR while the file is served for download.
    if (showRemoteExport) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            RemoteBackupExportScreen(
                state = remoteState,
                preparing = state == BackupViewModel.State.Working,
                onStop = { vm.stopRemoteExport() },
                onBack = { vm.stopRemoteExport(); showRemoteExport = false },
            )
        }
    }

    // Restore step 0: Remote (send the backup from another device) or Local (pick a file on this device).

    // Remote restore: full-screen companion panel (PIN + QR). An uploaded file feeds the normal
    // inspect → section-picker flow; the panel closes itself when a file arrives.
    if (showRemoteRestore) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            RemoteBackupRestoreScreen(
                state = remoteState,
                backups = vm.remoteBackups,
                onStart = { port -> vm.startRemoteRestore(port) },
                onStop = { vm.stopRemoteRestore() },
                onBackupReceived = { file -> showRemoteRestore = false; vm.inspect(file) },
                onBack = { vm.stopRemoteRestore(); showRemoteRestore = false },
            )
        }
    }
}

/** A single-secret prompt with a confirm (encrypt/restore), a skip (no passwords) and cancel. */
@Composable
private fun BackupPasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    /** Null hides the skip button entirely — a sealed `.own` has nothing to fall back to. */
    skipLabel: String?,
    onConfirm: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss, title = title, body = message,
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            if (skipLabel != null) tv.own.owntv.ui.stage.StageButton(skipLabel, onClick = onSkip, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(confirmLabel, onClick = { if (password.isNotBlank()) onConfirm(password) }, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        OwnTVTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.settings_backup_password),
            isPassword = true,
            focusRequester = firstFocus,
        )
    }
}

/**
 * Export step 0: choose which profiles the backup contains. Ticking the active profile or an
 * unlocked one is immediate; ticking another profile with a PIN prompts for it — wrong PIN shows
 * "PIN incorrect" and leaves it unticked.
 *
 * The ACTIVE profile starts ticked; every other profile still starts unticked and is the user's
 * explicit choice. Starting with nothing ticked meant a user who ticked every *section* — the
 * screen before this one, where everything is selected by default — could still walk away with a
 * backup containing no profile data at all, which is not what "back up everything" looked like.
 * The active profile needs no PIN to include, so pre-ticking it reveals nothing a locked profile
 * was protecting.
 */
@Composable
private fun ProfilePickerDialog(
    profiles: List<tv.own.owntv.core.database.entity.ProfileEntity>,
    activeId: Long,
    verifyPin: (tv.own.owntv.core.database.entity.ProfileEntity, String) -> Boolean,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var ticked by remember(activeId) {
        mutableStateOf(if (profiles.any { it.id == activeId }) setOf(activeId) else emptySet())
    }
    var pinFor by remember { mutableStateOf<tv.own.owntv.core.database.entity.ProfileEntity?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(pinFor == null) { if (pinFor == null) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_backup_which_profiles),
        body = stringResource(R.string.settings_backup_selected_profiles),
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_backup_continue), onClick = { if (ticked.isNotEmpty()) onConfirm(ticked) }, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        profiles.forEachIndexed { i, p ->
            val locked = p.pinHash != null && p.id != activeId
            CheckRow(
                label = if (p.id == activeId) stringResource(R.string.settings_backup_profile_current, p.name) else p.name,
                desc = when {
                    locked -> stringResource(R.string.settings_backup_pin_locked)
                    p.isKids -> stringResource(R.string.settings_backup_kids_profile)
                    else -> null
                },
                checked = p.id in ticked,
                onToggle = {
                    when {
                        p.id in ticked -> ticked = ticked - p.id
                        locked -> pinFor = p
                        else -> ticked = ticked + p.id
                    }
                },
                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
    pinFor?.let { profile ->
        ProfilePinDialog(
            profileName = profile.name,
            verify = { pin -> verifyPin(profile, pin) },
            onUnlocked = { ticked = ticked + profile.id; pinFor = null },
            onDismiss = { pinFor = null },
        )
    }
}

/** PIN prompt for including a locked, non-active profile in the backup. */
@Composable
private fun ProfilePinDialog(
    profileName: String,
    verify: (String) -> Boolean,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { fieldFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_backup_profile_locked, profileName),
        body = stringResource(R.string.settings_backup_profile_pin_description),
        width = 760.mpx,
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(
                stringResource(R.string.settings_backup_unlock),
                onClick = { if (pin.isNotBlank()) { if (verify(pin)) onUnlocked() else { wrong = true; pin = "" } } },
                height = 56.mpx, textSize = 19, tinted = true,
            )
        },
    ) {
        OwnTVTextField(
            value = pin,
            onValueChange = { pin = it; wrong = false },
            label = stringResource(R.string.settings_backup_profile_pin),
            isPassword = true,
            focusRequester = fieldFocus,
        )
        if (wrong) Text(stringResource(R.string.settings_backup_pin_incorrect), style = stageText(17, 600), color = StageColors.Danger, modifier = Modifier.padding(top = 10.mpx))
    }
}

/** Widened for More's Backup pane, which lists what a backup carries. One mapping, not two. */
internal fun sectionLabelRes(section: BackupManager.Section): Int = when (section) {
    BackupManager.Section.SOURCES -> R.string.settings_backup_section_sources
    BackupManager.Section.CUSTOMIZE -> R.string.settings_backup_section_customize
    BackupManager.Section.FAVORITES -> R.string.settings_backup_section_favorites
    BackupManager.Section.HISTORY -> R.string.settings_backup_section_history
    BackupManager.Section.RESUME -> R.string.settings_backup_section_resume
    BackupManager.Section.MANUAL_REORDER -> R.string.settings_backup_section_reorder
    BackupManager.Section.SETTINGS -> R.string.settings_backup_section_settings
}

private fun sectionDescriptionRes(section: BackupManager.Section): Int = when (section) {
    BackupManager.Section.SOURCES -> R.string.settings_backup_section_sources_desc
    BackupManager.Section.CUSTOMIZE -> R.string.settings_backup_section_customize_desc
    BackupManager.Section.FAVORITES -> R.string.settings_backup_section_favorites_desc
    BackupManager.Section.HISTORY -> R.string.settings_backup_section_history_desc
    BackupManager.Section.RESUME -> R.string.settings_backup_section_resume_desc
    BackupManager.Section.MANUAL_REORDER -> R.string.settings_backup_section_reorder_desc
    BackupManager.Section.SETTINGS -> R.string.settings_backup_section_settings_desc
}

/**
 * Multi-select dialog over backup sections, with an "Everything" toggle on top.
 *
 * Internal rather than private because the first-run wizard asks the same question with the same
 * words — see `SetupWizard.kt`. One dialog, so the two places can never drift apart.
 */
@Composable
internal fun SectionPickerDialog(
    title: String,
    sections: List<BackupManager.Section>,
    initial: Set<BackupManager.Section>,
    confirmLabel: String,
    onConfirm: (Set<BackupManager.Section>) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Restore only: offer to take the *other* device's hardware settings too (engine, decoder, frame
     * rate, HDR, surround). Null hides the row — export, or a backup this device wrote itself, whose
     * hardware settings core restores anyway. Unticked unless the user ticks it; not part of
     * "Everything", which is about what the file holds, not about this device.
     */
    deviceSettings: MutableState<Boolean>? = null,
    /** First-run restore: say so when Sources is unticked, because the device then has no playlists. */
    warnWithoutSources: Boolean = false,
) {
    var selected by remember { mutableStateOf(initial) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = title,
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(confirmLabel, onClick = { if (selected.isNotEmpty()) onConfirm(selected) }, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        CheckRow(
            label = stringResource(R.string.settings_backup_everything),
            desc = null,
            checked = selected.size == sections.size,
            onToggle = { selected = if (selected.size == sections.size) emptySet() else sections.toSet() },
            modifier = Modifier.focusRequester(firstFocus),
        )
        tv.own.owntv.ui.stage.StagePopupDivider()
        sections.forEach { section ->
            CheckRow(
                label = stringResource(sectionLabelRes(section)),
                desc = stringResource(sectionDescriptionRes(section)),
                checked = section in selected,
                onToggle = { selected = if (section in selected) selected - section else selected + section },
            )
        }
        if (deviceSettings != null && BackupManager.Section.SETTINGS in selected) {
            tv.own.owntv.ui.stage.StagePopupDivider()
            CheckRow(
                label = stringResource(R.string.settings_backup_device_settings),
                desc = stringResource(R.string.settings_backup_device_settings_desc),
                checked = deviceSettings.value,
                onToggle = { deviceSettings.value = !deviceSettings.value },
            )
        }
        if (warnWithoutSources && selected.isNotEmpty() && BackupManager.Section.SOURCES !in selected) {
            Text(
                stringResource(R.string.settings_backup_no_sources_warning),
                style = stageText(17, 600),
                color = StageColors.Warn,
                modifier = Modifier.padding(top = 10.mpx),
            )
        }
    }
}

/** A Stage check row: the box (accent with a tick when on), the label over an optional line. */
@Composable
private fun CheckRow(
    label: String,
    desc: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    tv.own.owntv.ui.stage.StagePopupOption(
        title = label, subtitle = desc, onClick = onToggle, modifier = modifier,
        leading = { focused -> tv.own.owntv.ui.stage.StagePopupCheck(checked, focused) },
    )
}

/** A button inside a More card ("File on this TV"): 42 high, 16/700, white 6%; focused = FILLED. */
@Composable
private fun BackupTool(text: String, icon: OwnTVIcon, modifier: Modifier = Modifier, onClick: () -> Unit) {
    StageSurface(
        onClick = onClick, radius = 15.mpx, modifier = modifier.height(42.mpx),
        idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(15.mpx)),
    ) { focused ->
        val c = if (focused) stageAccent.onAccent else StageColors.Text
        Row(Modifier.padding(horizontal = 16.mpx), horizontalArrangement = Arrangement.spacedBy(9.mpx), verticalAlignment = Alignment.CenterVertically) {
            OwnTVIcon(icon, c, Modifier.size(18.mpx))
            Text(text, style = stageText(16, 700), color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
