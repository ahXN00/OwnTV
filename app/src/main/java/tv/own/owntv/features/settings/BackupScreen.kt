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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import java.io.File

/**
 * Phase 12 — Backup & Restore (Settings → Backup), with selective sections: the user picks what to
 * back up (profiles & sources / customizations / favorites / history / resume) and, on restore,
 * which of the file's sections to apply. Uses an in-app file picker (no SAF).
 */
@Composable
fun BackupScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: BackupViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

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
    val firstFocus = remember { FocusRequester() }
    val restoreBtnFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { firstFocus.requestFocus() } }

    // Restore: first pick Remote (upload from another device) or Local (file picker). Remote opens a full-screen
    // companion panel; an uploaded file drops back into the same inspect → section-picker flow.
    var showRestoreChooser by remember { mutableStateOf(false) }
    var showRemoteRestore by remember { mutableStateOf(false) }
    val remoteState by vm.remoteState.collectAsStateWithLifecycle()

    // Export: Remote (serve the file for another device to download) or Local (save to a folder).
    var showExportChooser by remember { mutableStateOf(false) }
    var exportToRemote by remember { mutableStateOf(false) }
    var showRemoteExportPassword by remember { mutableStateOf(false) }
    var showRemoteExport by remember { mutableStateOf(false) }
    // If the remote export fails to prepare, drop the panel so the base screen shows the error.
    LaunchedEffect(state) {
        if (state is BackupViewModel.State.Error && showRemoteExport) {
            vm.stopRemoteExport(); showRemoteExport = false
        }
    }

    BackHandler { onBack() }

    // Dialog-close focus return: closing the section picker / file browser refocuses the button
    // that opened it. The restore crosses INTO this group from the dialog, so onEnter intercepts
    // it — it consults dialogReturn first (and clears it) instead of hijacking.
    // Deliberately NOT tv.own.owntv.ui.components.rememberDialogFocusRestore: the onEnter below also
    // reads and clears this, and the shared helper clears it right after its own restore. Which of the
    // two wins would come down to whether onEnter runs inside requestFocus(), and this screen's restore
    // is not worth betting on that ordering.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    val anyDialogOpen = showBrowser || showExportPicker || showProfilePicker || pendingFolderBrowser || exportFolder != null ||
        showRestoreChooser || showRemoteRestore || showExportChooser || showRemoteExportPassword || showRemoteExport ||
        state is BackupViewModel.State.ChooseRestore || state is BackupViewModel.State.NeedPassword
    LaunchedEffect(anyDialogOpen) {
        if (!anyDialogOpen) {
            dialogReturn?.let { btn ->
                kotlinx.coroutines.delay(80)
                runCatching { btn.requestFocus() }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            // onEnter fires for any entry from outside the group — including our own dialog-close
            // restores (the dialogs live outside it) — so it must prefer the pending return button.
            .focusProperties {
                onEnter = {
                    val target = dialogReturn ?: firstFocus
                    dialogReturn = null
                    runCatching { target.requestFocus() }
                }
            }
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text(stringResource(R.string.settings_backup_title), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_backup_save_description),
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.widthIn(max = 680.dp),
        )
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton(stringResource(R.string.settings_backup_export_button), onClick = { dialogReturn = firstFocus; showExportChooser = true }, icon = OwnTVIcon.BACKUP, enabled = state != BackupViewModel.State.Working, modifier = Modifier.focusRequester(firstFocus))
            OwnTVButton(stringResource(R.string.settings_backup_restore_button), onClick = { dialogReturn = restoreBtnFocus; showRestoreChooser = true }, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.REFRESH, enabled = state != BackupViewModel.State.Working, modifier = Modifier.focusRequester(restoreBtnFocus))
        }
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            BackupViewModel.State.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                OwnTVSpinner(sizeDp = 22)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.settings_backup_working), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            is BackupViewModel.State.Done -> when (s.kind) {
                DoneKind.EXPORTED -> Text(
                    if (s.passwordsOmitted) {
                        stringResource(R.string.settings_backup_saved_to_without_passwords, s.path.orEmpty())
                    } else {
                        stringResource(R.string.settings_backup_saved_to, s.path.orEmpty())
                    },
                    style = MaterialTheme.typography.bodyLarge, color = colors.primary,
                )
                DoneKind.RESTORED -> Column {
                    Text(pluralStringResource(R.plurals.settings_backup_restored, s.items, s.items), style = MaterialTheme.typography.bodyLarge, color = colors.primary)
                    Text(stringResource(R.string.settings_backup_restore_resync), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    if (s.passwordsOmitted) {
                        Text(stringResource(R.string.settings_backup_restore_password_note), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    if (s.skippedSources > 0) {
                        Text(pluralStringResource(R.plurals.settings_backup_skipped_sources, s.skippedSources, s.skippedSources), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    if (s.invalidLocale) {
                        Text(stringResource(R.string.settings_backup_invalid_locale), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            is BackupViewModel.State.Error -> Text(
                stringResource(
                    when (s.kind) {
                        BackupError.EXPORT -> R.string.settings_backup_export_error
                        BackupError.READ -> R.string.settings_backup_read_error
                        BackupError.IMPORT -> R.string.settings_backup_import_error
                    },
                ),
                style = MaterialTheme.typography.bodyLarge, color = Color(0xFFEF4444),
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

    // Export step 0: Remote (serve for another device to download) or Local (save to a folder).
    if (showExportChooser) {
        RemoteLocalChooserDialog(
            title = stringResource(R.string.settings_backup_export_title),
            message = stringResource(R.string.settings_backup_export_message),
            onRemote = { showExportChooser = false; exportToRemote = true; vm.loadProfiles(); showProfilePicker = true },
            onLocal = { showExportChooser = false; exportToRemote = false; vm.loadProfiles(); showProfilePicker = true },
            onDismiss = { showExportChooser = false },
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
    if (showRestoreChooser) {
        RemoteLocalChooserDialog(
            title = stringResource(R.string.settings_backup_restore_title),
            message = stringResource(R.string.settings_backup_restore_message),
            onRemote = { showRestoreChooser = false; showRemoteRestore = true },
            onLocal = { showRestoreChooser = false; browser = BrowseMode.FILE; showBrowser = true },
            onDismiss = { showRestoreChooser = false },
        )
    }

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

/** Two-way chooser: send/receive over the LAN companion server, or use a local file. */
@Composable
private fun RemoteLocalChooserDialog(
    title: String,
    message: String,
    onRemote: () -> Unit,
    onLocal: () -> Unit,
    onDismiss: () -> Unit,
) {
    OwnTVPopup(onDismissRequest = onDismiss) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 560.dp, padding = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_backup_local_file), onClick = onLocal, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton(stringResource(R.string.settings_backup_remote), onClick = onRemote, modifier = Modifier.focusRequester(firstFocus))
            }
        }
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
    OwnTVPopup(onDismissRequest = onDismiss) {
    val colors = OwnTVTheme.colors
    var password by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 560.dp, padding = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            OwnTVTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.settings_backup_password),
                isPassword = true,
                focusRequester = firstFocus,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                if (skipLabel != null) OwnTVButton(skipLabel, onClick = onSkip, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton(confirmLabel, onClick = { onConfirm(password) }, enabled = password.isNotBlank())
            }
        }
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
    }
@Composable
private fun ProfilePickerDialog(
    profiles: List<tv.own.owntv.core.database.entity.ProfileEntity>,
    activeId: Long,
    verifyPin: (tv.own.owntv.core.database.entity.ProfileEntity, String) -> Boolean,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    OwnTVPopup(onDismissRequest = onDismiss) {
    val colors = OwnTVTheme.colors
    var ticked by remember(activeId) {
        mutableStateOf(if (profiles.any { it.id == activeId }) setOf(activeId) else emptySet())
    }
    var pinFor by remember { mutableStateOf<tv.own.owntv.core.database.entity.ProfileEntity?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { if (pinFor != null) pinFor = null else onDismiss() }

    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 560.dp, padding = 28.dp)) {
            Text(stringResource(R.string.settings_backup_which_profiles), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_backup_selected_profiles),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            profiles.forEachIndexed { i, p ->
                val locked = p.pinHash != null && p.id != activeId
                CheckRow(
                    label = if (p.id == activeId) {
                        stringResource(R.string.settings_backup_profile_current, p.name)
                    } else {
                        p.name
                    },
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
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_backup_continue), onClick = { onConfirm(ticked) }, enabled = ticked.isNotEmpty())
            }
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
    }
@Composable
private fun ProfilePinDialog(
    profileName: String,
    verify: (String) -> Boolean,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    OwnTVPopup(onDismissRequest = onDismiss) {
    val colors = OwnTVTheme.colors
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 480.dp, padding = 28.dp)) {
            Text(stringResource(R.string.settings_backup_profile_locked, profileName), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_backup_profile_pin_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OwnTVTextField(
                value = pin,
                onValueChange = { pin = it; wrong = false },
                label = stringResource(R.string.settings_backup_profile_pin),
                isPassword = true,
                focusRequester = fieldFocus,
            )
            if (wrong) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_backup_pin_incorrect), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    stringResource(R.string.settings_backup_unlock),
                    onClick = { if (verify(pin)) onUnlocked() else { wrong = true; pin = "" } },
                    enabled = pin.isNotBlank(),
                )
            }
        }
    }
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
) {
    OwnTVPopup(onDismissRequest = onDismiss) {
    val colors = OwnTVTheme.colors
    var selected by remember { mutableStateOf(initial) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 560.dp, padding = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(16.dp))

            CheckRow(
                label = stringResource(R.string.settings_backup_everything),
                desc = null,
                checked = selected.size == sections.size,
                onToggle = { selected = if (selected.size == sections.size) emptySet() else sections.toSet() },
                modifier = Modifier.focusRequester(firstFocus),
            )
            Spacer(Modifier.height(6.dp))
            sections.forEach { section ->
                CheckRow(
                    label = stringResource(sectionLabelRes(section)),
                    desc = stringResource(sectionDescriptionRes(section)),
                    checked = section in selected,
                    onToggle = { selected = if (section in selected) selected - section else selected + section },
                )
            }
            if (deviceSettings != null && BackupManager.Section.SETTINGS in selected) {
                Spacer(Modifier.height(6.dp))
                CheckRow(
                    label = stringResource(R.string.settings_backup_device_settings),
                    desc = stringResource(R.string.settings_backup_device_settings_desc),
                    checked = deviceSettings.value,
                    onToggle = { deviceSettings.value = !deviceSettings.value },
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(confirmLabel, onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty())
            }
        }
    }
}

    }
@Composable
private fun CheckRow(
    label: String,
    desc: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
        surface = GlassSurface.DIALOGS,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (checked) colors.primary else Color.Transparent)
                    .border(2.dp, if (checked) colors.primary else colors.outline, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(colors.onPrimary))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                if (desc != null) {
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
