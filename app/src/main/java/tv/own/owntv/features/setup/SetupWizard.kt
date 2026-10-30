package tv.own.owntv.features.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.features.settings.EpgSyncDialog
import tv.own.owntv.features.settings.RemoteBackupRestoreScreen
import tv.own.owntv.features.settings.SectionPickerDialog
import tv.own.owntv.features.settings.SetupLocalSyncScreen
import tv.own.owntv.features.shell.PendingShellRequest
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.stageBackground
import tv.own.owntv.ui.theme.stageAccent

private enum class Step { WELCOME, DISPLAY_SIZE, DISCLAIMER, AUTOMATION, SETUP_CHOICE, SYNC_DEVICE, CREATE_PROFILE, ADD_CONTENT, ADD_SOURCE_REMOTE, ADD_SOURCE, IMPORTING, EXISTING, IMPORT_BACKUP_CHOOSER, IMPORT_BACKUP_REMOTE, IMPORT_BACKUP }

/**
 * Onboarding for one profile. [firstRun] shows language/welcome/disclaimer; otherwise it
 * starts at profile creation (used by "Add profile"). [onDone] receives the newly active profile id and
 * enters the app; [onCancel] backs out (to the gate). Every step is a [WizardFrame]; adding a playlist
 * reuses the Settings › Add a source pages.
 */
@Composable
fun Onboarding(firstRun: Boolean, onDone: (Long?) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SetupViewModel = koinViewModel()
    val defaultProfileName = stringResource(R.string.setup_default_profile)
    val defaultIptvName = stringResource(R.string.setup_default_iptv)
    val defaultPlaylistName = stringResource(R.string.setup_name_default_playlist)
    val defaultPortalName = stringResource(R.string.setup_default_portal)
    var step by rememberSaveable(firstRun) { mutableStateOf(if (firstRun) Step.WELCOME else Step.CREATE_PROFILE) }
    // Set up by Automation: the notice still comes first, then the pairing page instead of the choice.
    var viaAutomation by rememberSaveable { mutableStateOf(false) }
    val importState by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val epgSync by vm.epgSync.collectAsStateWithLifecycle()
    var existing by remember { mutableStateOf<List<SourceEntity>>(emptyList()) }
    // Where "Try Again" returns to when an import fails (new source vs. linking existing).
    var importOrigin by remember { mutableStateOf(Step.ADD_SOURCE) }
    // The playlist being imported, for "Importing <name>"; null when linking existing playlists.
    var importName by remember { mutableStateOf<String?>(null) }
    // Where Back from the backup-restore picker returns to (first-run choice vs. add-content step).
    var backupOrigin by remember { mutableStateOf(Step.ADD_CONTENT) }
    // The chosen backup file and what the user wants out of it. A file with no choice yet is what
    // raises the section dialog below; picking a file no longer starts the restore by itself.
    var restoreFile by remember { mutableStateOf<java.io.File?>(null) }
    var restoreSections by remember { mutableStateOf<Set<tv.own.owntv.core.backup.BackupManager.Section>?>(null) }
    // "Hardware settings from the other device" — asked with the sections, carried like them.
    val restoreDeviceSettings = remember { mutableStateOf(false) }

    // Refresh the "existing playlists" availability whenever we land on the add-content step.
    LaunchedEffect(step) { if (step == Step.ADD_CONTENT) existing = runCatching { vm.availableExistingSources() }.getOrDefault(emptyList()) }

    val accent = stageAccent.accent
    Box(modifier = modifier.fillMaxSize().stageBackground(accent)) {
        when (step) {
            Step.WELCOME -> WelcomeStep(onNext = { viaAutomation = false; step = Step.DISPLAY_SIZE }, onAutomation = { viaAutomation = true; step = Step.DISCLAIMER })
            // Before the disclaimer, which is the first screen with a paragraph of real text on it:
            // if the interface is too small to read, that is the screen it first hurts on.
            Step.DISPLAY_SIZE -> DisplaySizeStep(onNext = { step = Step.DISCLAIMER }, onBack = { step = Step.WELCOME })
            Step.DISCLAIMER -> DisclaimerStep(
                onBack = { step = if (viaAutomation) Step.WELCOME else Step.DISPLAY_SIZE },
                onAccept = { step = if (viaAutomation) Step.AUTOMATION else Step.SETUP_CHOICE },
            )
            Step.AUTOMATION -> SetupAutomationStep(
                onHere = { viaAutomation = false; step = Step.SETUP_CHOICE },
                onCancel = { viaAutomation = false; step = Step.WELCOME },
            )
            Step.SETUP_CHOICE -> SetupChoiceStep(
                onBack = { step = Step.DISCLAIMER },
                onNewProfile = { step = Step.CREATE_PROFILE },
                onRestore = { backupOrigin = Step.SETUP_CHOICE; step = Step.IMPORT_BACKUP_CHOOSER },
                onSync = { step = Step.SYNC_DEVICE },
            )
            // A sync brings whole profiles with it, exactly as a restored backup does, so it finishes
            // the wizard the same way: hand over with no profile chosen and let MainActivity ask for
            // the PIN of whichever one the user picks.
            Step.SYNC_DEVICE -> SetupLocalSyncScreen(
                onRestored = { onDone(null) },
                onBack = { step = Step.SETUP_CHOICE },
            )
            Step.CREATE_PROFILE -> ProfileStep(
                onCreate = { name, avatar, kids, pin, picture -> vm.createProfile(name.ifBlank { defaultProfileName }, avatar, kids, pin, picture) { step = Step.ADD_CONTENT } },
                onBack = { if (firstRun) step = Step.SETUP_CHOICE else onCancel() },
            )
            Step.ADD_CONTENT -> AddContentStep(
                hasExisting = existing.isNotEmpty(),
                onPhone = { step = Step.ADD_SOURCE_REMOTE },
                onType = { step = Step.ADD_SOURCE },
                onExisting = { step = Step.EXISTING },
                onImport = { backupOrigin = Step.ADD_CONTENT; step = Step.IMPORT_BACKUP_CHOOSER },
                onSkip = { vm.finish(onDone) },
            )
            Step.ADD_SOURCE_REMOTE -> RemoteSetupScreen(
                state = vm.remoteState.collectAsStateWithLifecycle().value,
                payloads = vm.remotePayloads,
                onStartListener = { port -> vm.startRemoteListener(port) },
                onStopListener = { vm.stopRemoteListener() },
                // A remote submission hands off to the pre-filled form (the user presses Start Import).
                onPayloadReceived = { step = Step.ADD_SOURCE },
                onBack = { vm.stopRemoteListener(); step = Step.ADD_CONTENT },
            )
            Step.ADD_SOURCE -> AddSourceScreen(
                onStartXtream = { name, server, user, pass, ua, ref, epg, refresh, live, movies, series, _, preferHls ->
                    importName = name.ifBlank { defaultIptvName }
                    vm.startXtream(importName!!, server, user, pass, ua, epg, refresh, live, movies, series, preferHls, httpReferer = ref)
                    importOrigin = Step.ADD_SOURCE
                    step = Step.IMPORTING
                },
                onStartM3u = { name, url, ua, ref, epg, refresh, _ ->
                    importName = name.ifBlank { defaultPlaylistName }
                    vm.startM3u(importName!!, url, ua, epg, refresh, httpReferer = ref)
                    importOrigin = Step.ADD_SOURCE
                    step = Step.IMPORTING
                },
                onStartStalker = { name, portalUrl, mac, serialNumber, deviceId, deviceId2, signature, ua, ref, refresh, _, live, movies, series ->
                    importName = name.ifBlank { defaultPortalName }
                    vm.startStalker(
                        importName!!, portalUrl, mac, serialNumber, deviceId,
                        deviceId2, signature, ua, refresh, live, movies, series, httpReferer = ref,
                    )
                    importOrigin = Step.ADD_SOURCE
                    step = Step.IMPORTING
                },
                // Submissions from the phone page land here pre-filled (type + fields).
                remotePayload = vm.remotePayload,
                onRemotePayloadConsumed = { vm.consumeRemotePayload() },
                onBack = { step = Step.ADD_CONTENT },
                showDefaultToggle = false, // first playlist in setup: nothing to be "default" over yet
            )
            Step.IMPORTING -> ImportProgressStep(
                state = importState,
                progress = progress,
                name = importName,
                onContinue = { vm.finish(onDone) }, // playlist + its EPG synced (auto)
                // "Add a TV guide": finish setup, then the shell opens Settings › EPG sources › Add.
                onAddGuide = { PendingShellRequest.addEpg = true; vm.finish(onDone) },
                onRetry = { vm.reset(); step = importOrigin },
                onCancel = { vm.cancelImport(); step = importOrigin },
                onBackground = { vm.continueInBackground(onDone) }, // enter the app; sync keeps running
            )
            Step.EXISTING -> ExistingSourcesStep(
                sources = existing,
                onAdd = { ids -> importName = null; vm.linkExisting(ids); importOrigin = Step.EXISTING; step = Step.IMPORTING },
                onBack = { step = Step.ADD_CONTENT },
            )
            Step.IMPORT_BACKUP_CHOOSER -> BackupChooserStep(
                wizardStep = if (backupOrigin == Step.SETUP_CHOICE) WizardStep.PROFILE else WizardStep.PLAYLIST,
                onBack = { step = backupOrigin },
                onPhone = { step = Step.IMPORT_BACKUP_REMOTE },
                onLocalFile = { step = Step.IMPORT_BACKUP },
            )
            Step.IMPORT_BACKUP_REMOTE -> RemoteBackupRestoreScreen(
                state = vm.remoteState.collectAsStateWithLifecycle().value,
                backups = vm.remoteBackups,
                onStart = { port -> vm.startRemoteRestore(port) },
                onStop = { vm.stopRemoteRestore() },
                // An uploaded file asks what to take out of it first; the state-driven IMPORT_BACKUP
                // screen shows progress, the password prompt, or the result from here on.
                onBackupReceived = { file ->
                    restoreSections = null
                    restoreFile = file
                    step = Step.IMPORT_BACKUP
                },
                onBack = { vm.stopRemoteRestore(); step = Step.IMPORT_BACKUP_CHOOSER },
            )
            Step.IMPORT_BACKUP -> ImportBackupStep(
                state = importState,
                wizardStep = if (backupOrigin == Step.SETUP_CHOICE) WizardStep.PROFILE else WizardStep.PLAYLIST,
                onPick = { file -> restoreSections = null; restoreFile = file },
                fileChosen = restoreFile != null,
                // The same choice, carried across the password question: a sealed file is chosen
                // from before it can be opened, so the answer has to outlive the prompt.
                onPassword = { file, pass ->
                    vm.restoreWithPassword(file, pass, onDone, restoreSections ?: allRestoreSections, restoreDeviceSettings.value)
                },
                onBack = { vm.reset(); restoreFile = null; restoreSections = null; step = backupOrigin },
            )
        }
        // A file is chosen and nothing has been asked of it yet — so ask, over whatever is behind.
        restoreFile?.takeIf { restoreSections == null }?.let { file ->
            SectionPickerDialog(
                title = stringResource(R.string.settings_backup_what_restore),
                // Every section, not only the ones the file holds: a sealed file says nothing until its
                // password arrives, and asking for that before the user has said what they want would
                // be the wrong order. Ticking a section the file lacks restores nothing for it.
                sections = tv.own.owntv.core.backup.BackupManager.Section.entries,
                initial = allRestoreSections,
                confirmLabel = stringResource(R.string.settings_backup_restore_action),
                onConfirm = { chosen ->
                    restoreSections = chosen
                    vm.importBackup(file, onDone, chosen, restoreDeviceSettings.value) // restore activates a profile itself
                },
                onDismiss = { vm.reset(); restoreFile = null; step = backupOrigin },
                deviceSettings = restoreDeviceSettings,
                warnWithoutSources = firstRun,
            )
        }
        // Semi-auto EPG: after the first playlist imports, ask → sync (live count) → done (overlays "All set!").
        EpgSyncDialog(
            state = epgSync,
            onSync = vm::syncPendingEpg,
            onDismiss = vm::dismissPendingEpg,
            onBackground = { vm.syncEpgInBackground(onDone) }, // enter the app; guide keeps downloading
        )
    }
}

/** A focus requester that takes focus once the step is on screen (the step's first control). */
@Composable
internal fun rememberFirstFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { fr.requestFocus() } }
    return fr
}

/** W7, Add a playlist: From your phone, Type it here, Import (and Existing when another profile has some). */
@Composable
private fun AddContentStep(hasExisting: Boolean, onPhone: () -> Unit, onType: () -> Unit, onExisting: () -> Unit, onImport: () -> Unit, onSkip: () -> Unit) {
    val first = rememberFirstFocus()
    WizardFrame(
        step = WizardStep.PLAYLIST,
        title = stringResource(R.string.setup_add_playlist),
        sub = stringResource(R.string.setup_add_playlist_description),
        back = WizardAction(stringResource(R.string.setup_skip_for_now), onSkip),
        next = null,
        // Back on the remote does not skip: this step had no Back before, and skipping is a choice.
        onBack = null,
    ) {
        WizardCards(
            listOfNotNull(
                WizardCard(OwnTVIcon.PHONE, stringResource(R.string.setup_from_phone), stringResource(R.string.setup_phone_card_line), onPhone),
                WizardCard(OwnTVIcon.PENCIL, stringResource(R.string.settings_add_type_here), stringResource(R.string.settings_line_add_type_here), onType),
                if (hasExisting) WizardCard(OwnTVIcon.PLAYLIST, stringResource(R.string.setup_existing), stringResource(R.string.setup_use_other_profile_playlists), onExisting) else null,
                WizardCard(OwnTVIcon.DOWNLOADS, stringResource(R.string.setup_import), stringResource(R.string.setup_restore_backup_file), onImport),
            ),
            first,
        )
    }
}

/** Every section, the wizard's starting point for a restore. */
private val allRestoreSections: Set<tv.own.owntv.core.backup.BackupManager.Section>
    get() = tv.own.owntv.core.backup.BackupManager.Section.entries.toSet()
