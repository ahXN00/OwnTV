package tv.own.owntv.features.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.features.settings.EpgSyncDialog
import tv.own.owntv.features.settings.FirstRunLanguageSelector
import tv.own.owntv.features.settings.RemoteBackupRestoreScreen
import tv.own.owntv.features.settings.SectionPickerDialog
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.SetupLocalSyncScreen
import tv.own.owntv.features.settings.StageFieldRow
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.shell.PendingShellRequest
import tv.own.owntv.ui.components.BrandMark
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.OwnTVAvatars
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.detailText
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.format.localizedInteger
import tv.own.owntv.ui.components.primaryText
import tv.own.owntv.ui.components.remainderText
import tv.own.owntv.ui.components.summaryText
import tv.own.owntv.ui.components.warningText
import tv.own.owntv.ui.stage.stageBackground
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

private enum class Step { WELCOME, DISPLAY_SIZE, DISCLAIMER, SETUP_CHOICE, SYNC_DEVICE, CREATE_PROFILE, ADD_CONTENT, ADD_SOURCE_REMOTE, ADD_SOURCE, IMPORTING, EXISTING, IMPORT_BACKUP_CHOOSER, IMPORT_BACKUP_REMOTE, IMPORT_BACKUP }

/**
 * Onboarding for one profile (P10B-W1 … W9). [firstRun] shows language/welcome/disclaimer; otherwise it
 * starts at profile creation (used by "Add profile"). [onDone] receives the newly active profile id and
 * enters the app; [onCancel] backs out (to the gate). Every step is a [WizardFrame]; adding a playlist
 * reuses the Settings › Add a source pages (P10B step 4).
 */
@Composable
fun Onboarding(firstRun: Boolean, onDone: (Long?) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SetupViewModel = koinViewModel()
    val defaultProfileName = stringResource(R.string.setup_default_profile)
    val defaultIptvName = stringResource(R.string.setup_default_iptv)
    val defaultPlaylistName = stringResource(R.string.setup_name_default_playlist)
    val defaultPortalName = stringResource(R.string.setup_default_portal)
    var step by rememberSaveable(firstRun) { mutableStateOf(if (firstRun) Step.WELCOME else Step.CREATE_PROFILE) }
    val importState by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val epgSync by vm.epgSync.collectAsStateWithLifecycle()
    var existing by remember { mutableStateOf<List<SourceEntity>>(emptyList()) }
    // Where "Try Again" returns to when an import fails (new source vs. linking existing).
    var importOrigin by remember { mutableStateOf(Step.ADD_SOURCE) }
    // The playlist being imported, for "Importing <name>" (W8); null when linking existing playlists.
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
            Step.WELCOME -> WelcomeStep(onNext = { step = Step.DISPLAY_SIZE })
            // Before the disclaimer, which is the first screen with a paragraph of real text on it:
            // if the interface is too small to read, that is the screen it first hurts on (#179).
            Step.DISPLAY_SIZE -> DisplaySizeStep(onNext = { step = Step.DISCLAIMER }, onBack = { step = Step.WELCOME })
            Step.DISCLAIMER -> WizardFrame(
                step = WizardStep.DISPLAY,
                title = stringResource(R.string.setup_before_you_start),
                sub = stringResource(R.string.setup_disclaimer),
                back = WizardAction(stringResource(R.string.common_back), { step = Step.DISPLAY_SIZE }),
                next = WizardAction(stringResource(R.string.setup_i_understand), { step = Step.SETUP_CHOICE }, rememberFirstFocus()),
            )
            // First decision: start fresh or bring everything back from a backup (profiles included —
            // no point creating a profile first that the restore would replace).
            Step.SETUP_CHOICE -> {
                val first = rememberFirstFocus()
                WizardFrame(
                    step = WizardStep.PROFILE,
                    title = stringResource(R.string.setup_set_up_owntv),
                    sub = stringResource(R.string.setup_setup_choice_description),
                    back = WizardAction(stringResource(R.string.common_back), { step = Step.DISCLAIMER }),
                    next = null,
                ) {
                    WizardCards(
                        listOf(
                            WizardCard(OwnTVIcon.PERSON, stringResource(R.string.setup_new_profile), stringResource(R.string.setup_create_profile_add_sources)) { step = Step.CREATE_PROFILE },
                            WizardCard(OwnTVIcon.DOWNLOADS, stringResource(R.string.setup_restore_a_backup), stringResource(R.string.setup_restore_card_line)) {
                                backupOrigin = Step.SETUP_CHOICE; step = Step.IMPORT_BACKUP_CHOOSER
                            },
                            WizardCard(OwnTVIcon.PHONE, stringResource(R.string.setup_sync_device), stringResource(R.string.setup_sync_device_description)) { step = Step.SYNC_DEVICE },
                        ),
                        first,
                    )
                }
            }
            // A sync brings whole profiles with it, exactly as a restored backup does, so it finishes
            // the wizard the same way: hand over with no profile chosen and let MainActivity ask for
            // the PIN of whichever one the user picks.
            Step.SYNC_DEVICE -> SetupLocalSyncScreen(
                onRestored = { onDone(null) },
                onBack = { step = Step.SETUP_CHOICE },
            )
            Step.CREATE_PROFILE -> ProfileStep(
                onCreate = { name, avatar, kids, pin -> vm.createProfile(name.ifBlank { defaultProfileName }, avatar, kids, pin) { step = Step.ADD_CONTENT } },
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
                // "Add a TV guide" (owner, 1 Oct): finish setup, then the shell opens Settings › EPG sources › Add.
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
            Step.IMPORT_BACKUP_CHOOSER -> {
                val first = rememberFirstFocus()
                WizardFrame(
                    step = if (backupOrigin == Step.SETUP_CHOICE) WizardStep.PROFILE else WizardStep.PLAYLIST,
                    title = stringResource(R.string.setup_restore_a_backup),
                    sub = stringResource(R.string.setup_restore_choice_description),
                    back = WizardAction(stringResource(R.string.common_back), { step = backupOrigin }),
                    next = null,
                ) {
                    WizardCards(
                        listOf(
                            WizardCard(OwnTVIcon.PHONE, stringResource(R.string.setup_from_phone), stringResource(R.string.setup_upload_from_wifi_device)) { step = Step.IMPORT_BACKUP_REMOTE },
                            WizardCard(OwnTVIcon.FOLDER, stringResource(R.string.setup_local_file), stringResource(R.string.setup_pick_backup_local)) { step = Step.IMPORT_BACKUP },
                        ),
                        first,
                    )
                }
            }
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
private fun rememberFirstFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { fr.requestFocus() } }
    return fr
}

/** W1: the language as a row (OK opens the list), then Get started. */
@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    WizardFrame(
        step = WizardStep.LANGUAGE,
        title = stringResource(R.string.setup_welcome_title),
        sub = stringResource(R.string.setup_welcome_sub),
        back = null,
        next = WizardAction(stringResource(R.string.setup_get_started), onNext),
    ) {
        FirstRunLanguageSelector(Modifier.focusRequester(rememberFirstFocus()))
    }
}

/**
 * W2, first-run interface size (#179): UI zoom and text size as steppers (◀ ▶), the app icon, and a
 * sample row to judge them by. Both steppers write straight through, so the whole step — rows and
 * sample alike — resizes as the user presses. No draft state and no Apply.
 */
@Composable
private fun DisplaySizeStep(onNext: () -> Unit, onBack: () -> Unit) {
    val vm: DisplaySizeViewModel = koinViewModel()
    val zoom by vm.uiZoomPercent.collectAsStateWithLifecycle()
    val fontSize by vm.fontSizePercent.collectAsStateWithLifecycle()
    val appIcon by vm.appIcon.collectAsStateWithLifecycle()
    val zoomFocus = rememberFirstFocus()
    // Zoom below LOW_RAM_WARN can OOM a 2 GB device (#51), so the first step under the line is gated
    // exactly as the Settings dialog gates it. Accepting once arms the rest of this visit; arriving
    // already below the line (a restored backup) must not nag.
    var lowZoomAccepted by remember { mutableStateOf(zoom < UiZoom.LOW_RAM_WARN) }
    var pendingLowZoom by remember { mutableStateOf<Int?>(null) }
    var pickIcon by remember { mutableStateOf(false) }
    val stepZoom: (Int) -> Unit = { dir ->
        if (dir > 0) vm.setZoom(zoom + UiZoom.STEP)
        else {
            val next = UiZoom.clamp(zoom - UiZoom.STEP)
            if (next < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) pendingLowZoom = next else vm.setZoom(next)
        }
    }
    WizardFrame(
        step = WizardStep.DISPLAY,
        title = stringResource(R.string.setup_display_size_title),
        sub = stringResource(R.string.setup_display_sub),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = WizardAction(stringResource(R.string.setup_continue), onNext),
        extraLeft = listOf(WizardAction(stringResource(R.string.settings_reset), { vm.reset() })),
    ) {
        StageSettingRow(
            icon = OwnTVIcon.EXPAND,
            title = stringResource(R.string.settings_ui_zoom),
            desc = stringResource(R.string.setup_line_zoom),
            value = SettingValue.Stepper(stringResource(R.string.common_percent, zoom)),
            onClick = { stepZoom(1) },
            onStep = stepZoom,
            modifier = Modifier.focusRequester(zoomFocus),
        )
        StageSettingRow(
            icon = OwnTVIcon.PENCIL,
            title = stringResource(R.string.settings_font_size),
            desc = stringResource(R.string.setup_line_font),
            value = SettingValue.Stepper(stringResource(R.string.common_percent, fontSize)),
            onClick = { vm.setFontSize(fontSize + UiFontScale.STEP) },
            onStep = { dir -> vm.setFontSize(fontSize + dir * UiFontScale.STEP) },
        )
        // No restart prompt here: nothing is on the home screen yet, and the pick applies as soon as
        // the app is next in the background.
        val icons = AppIcon.entries
        val at = icons.indexOf(appIcon).coerceAtLeast(0)
        StageSettingRow(
            icon = OwnTVIcon.SPARKLE,
            title = stringResource(R.string.settings_app_icon),
            desc = stringResource(R.string.setup_line_app_icon),
            value = SettingValue.Custom { AppIconSwatches(icons, appIcon) },
            onClick = { pickIcon = true },
            onStep = { dir -> vm.setAppIcon(icons[(at + dir + icons.size) % icons.size]) },
        )
        // The sample (W2): a row's own type at the size it will really be.
        Row(
            Modifier
                .padding(top = 14.mpx)
                .fillMaxWidth()
                .height(84.mpx)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.mpx))
                .padding(horizontal = 22.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_panel_width_preview).uppercase(), style = stageText(13, 800, androidx.compose.ui.unit.TextUnit(0.13f, androidx.compose.ui.unit.TextUnitType.Em)), color = StageColors.Dim)
            Text(stringResource(R.string.home_row_now_trending), style = stageText(21, 700), color = StageColors.Text, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.common_on), style = stageText(18, 700), color = stageAccent.accent)
        }
    }

    // OK on App icon: the icon tiles in a popup (no restart question — nothing is on the home screen yet).
    if (pickIcon) {
        val tileFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { tileFocus.requestFocus() } }
        tv.own.owntv.ui.stage.StagePopup(onDismiss = { pickIcon = false }, title = stringResource(R.string.settings_app_icon), eyebrow = null, width = 880.mpx) {
            tv.own.owntv.ui.components.AppIconPicker(
                selected = appIcon,
                onPick = { vm.setAppIcon(it); pickIcon = false },
                firstFocus = tileFocus,
            )
        }
    }

    // Accept-the-risk gate for zoom below LOW_RAM_WARN (#51), the same prompt Settings shows. One
    // button, focus locked in every D-pad direction — OK accepts and applies the pending step, Back
    // cancels and leaves the zoom where it was.
    pendingLowZoom?.let { target ->
        // The same low-memory question Settings asks, as a Stage question with focus on Cancel.
        tv.own.owntv.ui.stage.StageConfirm(
            title = stringResource(R.string.settings_low_zoom_warning_title),
            body = stringResource(R.string.settings_low_zoom_warning, UiZoom.LOW_RAM_WARN, UiZoom.LOW_RAM_WARN),
            confirm = stringResource(R.string.settings_low_zoom_accept),
            onConfirm = {
                lowZoomAccepted = true
                pendingLowZoom = null
                vm.setZoom(target)
                runCatching { zoomFocus.requestFocus() }
            },
            onCancel = {
                pendingLowZoom = null
                runCatching { zoomFocus.requestFocus() }
            },
            focusCancel = true,
            eyebrow = null,
        )
    }
}

/** The app icon row's value (W2): the first five icon colours, the chosen one ringed, then "+N". */
@Composable
private fun AppIconSwatches(icons: List<AppIcon>, chosen: AppIcon) {
    val a = stageAccent
    val shown = (icons.take(5) + chosen).distinct().take(5)
    Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { icon ->
            Box(
                Modifier
                    .size(34.mpx)
                    .then(if (icon == chosen) Modifier.border(2.mpx, a.accent, RoundedCornerShape(9.mpx)) else Modifier),
                contentAlignment = Alignment.Center,
            ) { BrandMark(icon, 30.mpx, followAccent = false) }
        }
        if (icons.size > shown.size) {
            Text("+" + localizedInteger(icons.size - shown.size, grouping = false), style = stageText(17, 700), color = StageColors.Muted)
        }
    }
}

/**
 * W6, Who's watching?: Name, Avatar (◀ ▶ through the pictures, the letter first), Kids profile and an
 * optional PIN, then Create profile.
 */
@Composable
private fun ProfileStep(onCreate: (name: String, avatarId: Int, isKids: Boolean, pin: String?) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var avatarId by remember { mutableIntStateOf(-1) } // new profiles start on the letter
    var isKids by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    val nameFocus = rememberFirstFocus()
    val valid = name.isNotBlank() && (pin.isEmpty() || pin.length >= 4)
    val nameLabel = stringResource(R.string.profiles_name)
    val pinLabel = stringResource(R.string.profiles_optional_pin)
    val fieldHelp = stringResource(R.string.settings_form_field_help)
    WizardFrame(
        step = WizardStep.PROFILE,
        title = stringResource(R.string.profiles_gate_title),
        sub = stringResource(R.string.setup_profile_sub),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = WizardAction(stringResource(R.string.setup_create_profile), { if (valid) onCreate(name, avatarId, isKids, pin.takeIf { it.isNotBlank() }) }, enabled = valid),
    ) {
        StageFieldRow(
            OwnTVIcon.PENCIL, nameLabel, name, { name = it }, SettingHelp(nameLabel, fieldHelp),
            placeholder = stringResource(R.string.profiles_name_hint), modifier = Modifier.focusRequester(nameFocus),
        )
        val ids = (-1 until OwnTVAvatars.COUNT).toList()
        val at = ids.indexOf(avatarId).coerceAtLeast(0)
        StageSettingRow(
            icon = OwnTVIcon.PERSON,
            title = stringResource(R.string.setup_avatar),
            desc = stringResource(R.string.setup_avatar_line),
            value = SettingValue.Custom { AvatarStrip(ids, avatarId) },
            onClick = { avatarId = ids[(at + 1) % ids.size] },
            onStep = { dir -> avatarId = ids[(at + dir + ids.size) % ids.size] },
        )
        StageSettingRow(
            icon = OwnTVIcon.FAVORITE,
            title = stringResource(R.string.profiles_kids),
            desc = stringResource(R.string.profiles_kids_description),
            value = SettingValue.Switch(isKids),
            onClick = { isKids = !isKids },
        )
        StageFieldRow(
            OwnTVIcon.PENCIL, pinLabel, pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, SettingHelp(pinLabel, fieldHelp),
            placeholder = stringResource(R.string.profiles_pin_digits), password = true, keyboardType = KeyboardType.NumberPassword,
        )
    }
}

/** The avatar row's value (W6): six pictures from the chosen one on, the chosen ringed, then "+N". */
@Composable
private fun AvatarStrip(ids: List<Int>, chosen: Int) {
    val a = stageAccent
    val start = ids.indexOf(chosen).coerceAtLeast(0)
    val shown = List(6.coerceAtMost(ids.size)) { ids[(start + it) % ids.size] }
    Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { id ->
            Box(
                Modifier.size(34.mpx).then(if (id == chosen) Modifier.border(2.mpx, a.accent, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) { OwnTVAvatar(avatarId = id, modifier = Modifier.size(30.mpx)) }
        }
        if (ids.size > shown.size) Text("+" + localizedInteger(ids.size - shown.size, grouping = false), style = stageText(17, 700), color = StageColors.Muted)
    }
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

/** Existing playlists: one switch row per playlist another profile has, then Add N playlists. */
@Composable
private fun ExistingSourcesStep(sources: List<SourceEntity>, onAdd: (Set<Long>) -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val first = rememberFirstFocus()
    WizardFrame(
        step = WizardStep.PLAYLIST,
        title = stringResource(R.string.setup_use_existing_playlists),
        sub = stringResource(R.string.setup_pick_playlists),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = WizardAction(
            pluralStringResource(R.plurals.setup_add_selected_playlists, selected.size, selected.size),
            { if (selected.isNotEmpty()) onAdd(selected) },
            enabled = selected.isNotEmpty(),
        ),
    ) {
        sources.forEachIndexed { i, src ->
            val checked = src.id in selected
            StageSettingRow(
                icon = OwnTVIcon.PLAYLIST,
                title = src.name,
                desc = src.url,
                value = SettingValue.Switch(checked),
                onClick = { selected = if (checked) selected - src.id else selected + src.id },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

/** Restoring a backup: the file picker, then progress, the password question or the failure, in the frame. */
@Composable
private fun ImportBackupStep(
    state: SourceImporter.ImportState,
    wizardStep: WizardStep,
    onPick: (java.io.File) -> Unit,
    /** A file is picked: the browser closes, so it is not left behind the restore dialog. */
    fileChosen: Boolean,
    onPassword: (java.io.File, String?) -> Unit,
    onBack: () -> Unit,
) {
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

/** Every section, the wizard's starting point for a restore. */
private val allRestoreSections: Set<tv.own.owntv.core.backup.BackupManager.Section>
    get() = tv.own.owntv.core.backup.BackupManager.Section.entries.toSet()

/**
 * W8 / W9: "Importing <name>" with the bar and where the import is, Cancel and Run in background; then
 * "All set!" with what arrived and an Add a TV guide row; or the failure with Back and Try again.
 */
@Composable
private fun ImportProgressStep(
    state: SourceImporter.ImportState,
    progress: tv.own.owntv.core.sync.ImportStage?,
    name: String?,
    onContinue: () -> Unit,
    onAddGuide: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onBackground: () -> Unit,
) {
    when (state) {
        SourceImporter.ImportState.Running, SourceImporter.ImportState.Idle,
        is SourceImporter.ImportState.NeedPassword -> {
            val display = progress?.importProgressDisplay()
            WizardFrame(
                step = WizardStep.PLAYLIST,
                title = name?.let { stringResource(R.string.setup_importing_title, it) } ?: stringResource(R.string.setup_importing_catalog),
                sub = stringResource(R.string.setup_watching_during_import),
                back = WizardAction(stringResource(R.string.common_cancel), onCancel),
                // Enter the app right away; the import keeps running (VM is activity-scoped) and content
                // appears as it lands — no need to sit through a big movies/series sync.
                next = WizardAction(stringResource(R.string.setup_run_in_background), onBackground, rememberFirstFocus()),
            ) {
                StageSettingRow(
                    icon = OwnTVIcon.DOWNLOADS,
                    title = display?.primaryText() ?: stringResource(R.string.setup_preparing_catalog),
                    desc = display?.detailText(),
                    value = null,
                    onClick = {},
                )
            }
        }
        is SourceImporter.ImportState.Success -> {
            val lines = listOfNotNull(
                state.counts?.summaryText(includeEpg = true),
                state.restoredItems?.let { pluralStringResource(R.plurals.setup_restored_items, it, it) },
                if (state.passwordsOmitted) stringResource(R.string.setup_passwords_omitted) else null,
                state.skippedSources.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.setup_skipped_sources, it, it) },
                if (state.invalidLocale) stringResource(R.string.setup_invalid_locale) else null,
                state.warnings.warningText(),
                state.remainder.remainderText(),
            )
            WizardFrame(
                step = WizardStep.READY,
                title = stringResource(R.string.setup_all_set),
                sub = lines.joinToString("\n"),
                back = null,
                next = WizardAction(stringResource(R.string.setup_continue), onContinue, rememberFirstFocus()),
                onBack = onContinue,
            ) {
                StageSettingRow(
                    icon = OwnTVIcon.EPG,
                    title = stringResource(R.string.setup_add_guide),
                    desc = stringResource(R.string.setup_add_guide_line),
                    value = SettingValue.Opens(null),
                    onClick = onAddGuide,
                )
            }
        }
        is SourceImporter.ImportState.Failed -> WizardFrame(
            step = WizardStep.PLAYLIST,
            title = stringResource(R.string.setup_import_failed),
            sub = state.failure.displayText(),
            back = WizardAction(stringResource(R.string.common_back), onCancel),
            next = WizardAction(stringResource(R.string.setup_try_again_caps), onRetry, rememberFirstFocus()),
        )
    }
}
