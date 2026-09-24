package tv.own.owntv.features.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.features.profiles.ProfileEditorDialog
import tv.own.owntv.features.settings.SectionPickerDialog
import tv.own.owntv.features.settings.FirstRunLanguageSelector
import tv.own.owntv.ui.components.AppIconPicker
import tv.own.owntv.ui.components.BrandLockup
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.features.settings.EpgSyncDialog
import tv.own.owntv.features.settings.RemoteBackupRestoreScreen
import tv.own.owntv.features.settings.SetupLocalSyncScreen
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.detailText
import tv.own.owntv.ui.components.primaryText
import tv.own.owntv.ui.components.remainderText
import tv.own.owntv.ui.components.summaryText
import tv.own.owntv.ui.components.warningText
import tv.own.owntv.ui.theme.OwnTVTheme

private enum class Step { WELCOME, DISPLAY_SIZE, DISCLAIMER, SETUP_CHOICE, SYNC_DEVICE, CREATE_PROFILE, ADD_CONTENT, ADD_SOURCE_CHOOSER, ADD_SOURCE_REMOTE, ADD_SOURCE, IMPORTING, EXISTING, IMPORT_BACKUP_CHOOSER, IMPORT_BACKUP_REMOTE, IMPORT_BACKUP }

/**
 * Onboarding for one profile. [firstRun] shows language/welcome/disclaimer; otherwise it starts at profile
 * creation (used by "Add profile"). [onDone] receives the newly active profile id and enters the
 * app; [onCancel] backs out (to the gate).
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
    // Where Back from the backup-restore picker returns to (first-run choice vs. add-content step).
    var backupOrigin by remember { mutableStateOf(Step.ADD_CONTENT) }
    // The chosen backup file and what the user wants out of it. A file with no choice yet is what
    // raises the section dialog below; picking a file no longer starts the restore by itself.
    // Settings → Backup & Restore has always asked which sections to apply — the wizard took the
    // whole file, so "playlists but not that device's settings" could not be said here.
    var restoreFile by remember { mutableStateOf<java.io.File?>(null) }
    var restoreSections by remember { mutableStateOf<Set<tv.own.owntv.core.backup.BackupManager.Section>?>(null) }
    // "Hardware settings from the other device" — asked with the sections, carried like them.
    val restoreDeviceSettings = remember { mutableStateOf(false) }

    // Refresh the "existing playlists" availability whenever we land on the add-content step.
    LaunchedEffect(step) { if (step == Step.ADD_CONTENT) existing = runCatching { vm.availableExistingSources() }.getOrDefault(emptyList()) }

    Box(modifier = modifier.fillMaxSize().background(OwnTVTheme.colors.background)) {
        when (step) {
            Step.WELCOME -> WelcomeScreen(onNext = { step = Step.DISPLAY_SIZE })
            // Before the disclaimer, which is the first screen with a paragraph of real text on it:
            // if the interface is too small to read, that is the screen it first hurts on (#179).
            Step.DISPLAY_SIZE -> DisplaySizeScreen(
                onNext = { step = Step.DISCLAIMER },
                onBack = { step = Step.WELCOME },
            )
            Step.DISCLAIMER -> DisclaimerScreen(onAgree = { step = Step.SETUP_CHOICE }, onBack = { step = Step.DISPLAY_SIZE })
            // First decision: start fresh or bring everything back from a backup (profiles included —
            // no point creating a profile first that the restore would replace).
            Step.SETUP_CHOICE -> SetupChoiceScreen(
                onCreate = { step = Step.CREATE_PROFILE },
                onRestore = { backupOrigin = Step.SETUP_CHOICE; step = Step.IMPORT_BACKUP_CHOOSER },
                onSyncDevice = { step = Step.SYNC_DEVICE },
                onBack = { step = Step.DISCLAIMER },
            )
            // A sync brings whole profiles with it, exactly as a restored backup does, so it finishes
            // the wizard the same way: hand over with no profile chosen and let MainActivity ask for
            // the PIN of whichever one the user picks.
            Step.SYNC_DEVICE -> SetupLocalSyncScreen(
                onRestored = { onDone(null) },
                onBack = { step = Step.SETUP_CHOICE },
            )
            Step.CREATE_PROFILE -> ProfileEditorDialog(
                initial = null,
                onConfirm = { name, avatar, kids, pin -> vm.createProfile(name.ifBlank { defaultProfileName }, avatar, kids, pin) { step = Step.ADD_CONTENT } },
                onDismiss = { if (firstRun) step = Step.SETUP_CHOICE else onCancel() },
            )
            Step.ADD_CONTENT -> AddContentScreen(
                hasExisting = existing.isNotEmpty(),
                onNew = { step = Step.ADD_SOURCE_CHOOSER },
                onExisting = { step = Step.EXISTING },
                onImport = { backupOrigin = Step.ADD_CONTENT; step = Step.IMPORT_BACKUP_CHOOSER },
                onSkip = { vm.finish(onDone) },
            )
            Step.ADD_SOURCE_CHOOSER -> AddSourceChooserScreen(
                onRemote = { step = Step.ADD_SOURCE_REMOTE },
                onManual = { step = Step.ADD_SOURCE },
                onBack = { step = Step.ADD_CONTENT },
            )
            Step.ADD_SOURCE_REMOTE -> RemoteSetupScreen(
                state = vm.remoteState.collectAsStateWithLifecycle().value,
                payloads = vm.remotePayloads,
                onStartListener = { port -> vm.startRemoteListener(port) },
                onStopListener = { vm.stopRemoteListener() },
                // A remote submission hands off to the pre-filled Manual form (the user presses Start Import).
                onPayloadReceived = { step = Step.ADD_SOURCE },
                onBack = { vm.stopRemoteListener(); step = Step.ADD_SOURCE_CHOOSER },
            )
            Step.ADD_SOURCE -> AddSourceScreen(
                onStartXtream = { name, server, user, pass, ua, ref, epg, refresh, live, movies, series, _, preferHls ->
                    vm.startXtream(name.ifBlank { defaultIptvName }, server, user, pass, ua, epg, refresh, live, movies, series, preferHls, httpReferer = ref)
                    importOrigin = Step.ADD_SOURCE
                    step = Step.IMPORTING
                },
                onStartM3u = { name, url, ua, ref, epg, refresh, _ -> vm.startM3u(name.ifBlank { defaultPlaylistName }, url, ua, epg, refresh, httpReferer = ref); importOrigin = Step.ADD_SOURCE; step = Step.IMPORTING },
                onStartStalker = { name, portalUrl, mac, serialNumber, deviceId, deviceId2, signature, ua, ref, refresh, _, live, movies, series ->
                    vm.startStalker(
                        name.ifBlank { defaultPortalName }, portalUrl, mac, serialNumber, deviceId,
                        deviceId2, signature, ua, refresh, live, movies, series, httpReferer = ref,
                    )
                    importOrigin = Step.ADD_SOURCE
                    step = Step.IMPORTING
                },
                // Submissions from the Remote screen land here pre-filled (type + fields).
                remotePayload = vm.remotePayload,
                onRemotePayloadConsumed = { vm.consumeRemotePayload() },
                onBack = { step = Step.ADD_SOURCE_CHOOSER },
                showDefaultToggle = false, // first playlist in setup: nothing to be "default" over yet
            )
            Step.IMPORTING -> ImportProgressScreen(
                state = importState,
                progress = progress,
                onContinue = { vm.finish(onDone) }, // playlist + its EPG synced (auto)
                onRetry = { vm.reset(); step = importOrigin },
                onCancel = { vm.cancelImport(); step = importOrigin },
                onBackground = { vm.continueInBackground(onDone) }, // enter the app; sync keeps running
            )
            Step.EXISTING -> ExistingSourcesScreen(
                sources = existing,
                onAdd = { ids -> vm.linkExisting(ids); importOrigin = Step.EXISTING; step = Step.IMPORTING },
                onBack = { step = Step.ADD_CONTENT },
            )
            Step.IMPORT_BACKUP_CHOOSER -> ImportBackupChooserScreen(
                onRemote = { step = Step.IMPORT_BACKUP_REMOTE },
                onLocal = { step = Step.IMPORT_BACKUP },
                onBack = { step = backupOrigin },
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
            Step.IMPORT_BACKUP -> ImportBackupScreen(
                state = importState,
                onPick = { file -> restoreSections = null; restoreFile = file },
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
                // Every section, not only the ones the file holds: Settings can narrow the list
                // because it has already opened the container, and this has not — a sealed file says
                // nothing until its password arrives, and asking for that before the user has said
                // what they want would be the wrong order. Ticking a section the file lacks restores
                // nothing for it.
                sections = tv.own.owntv.core.backup.BackupManager.Section.entries,
                initial = allRestoreSections,
                confirmLabel = stringResource(R.string.settings_backup_restore_action),
                onConfirm = { chosen ->
                    restoreSections = chosen
                    vm.importBackup(file, onDone, chosen, restoreDeviceSettings.value) // restore activates a profile itself
                },
                onDismiss = { vm.reset(); restoreFile = null; step = backupOrigin },
                // Offered whatever the file is: this has not opened it yet. Harmless for a backup of
                // this very device, whose hardware settings core restores regardless.
                deviceSettings = restoreDeviceSettings,
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

@Composable
private fun WelcomeScreen(onNext: () -> Unit) {
    MainSetupPage {
        Text(
            stringResource(R.string.setup_welcome_to),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            ),
            color = OwnTVTheme.colors.primary.copy(alpha = 0.82f),
        )
        Spacer(Modifier.height(19.dp))
        BrandLockup(markSize = 82, textSize = 62, stacked = true)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_welcome_tagline), style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
        SetupAccentRule()
        Spacer(Modifier.height(26.dp))
        FirstRunLanguageSelector()
        Spacer(Modifier.height(14.dp))
        OwnTVButton(
            stringResource(R.string.setup_get_started),
            onClick = onNext,
            modifier = Modifier.width(192.dp).height(50.dp),
            icon = OwnTVIcon.PLAY,
        )
    }
}

/**
 * First-run interface size (#179): UI zoom and text size, with a sample line to judge them by.
 *
 * Rendered at [FULL_SETUP_CONTENT_SCALE] rather than the wizard's usual 0.62 — every other setup
 * page is deliberately drawn smaller than the app it leads into, so tuning a size against one of
 * them would be tuning against the wrong thing. Here the sample text is the size it will really be.
 *
 * Both steppers write straight through to the stored settings, so the whole screen — buttons,
 * labels and sample alike — resizes under the user's thumb as they press. That live feedback is the
 * feature; there is no draft state and no Apply.
 */
@Composable
private fun DisplaySizeScreen(onNext: () -> Unit, onBack: () -> Unit) {
    val vm: DisplaySizeViewModel = koinViewModel()
    val zoom by vm.uiZoomPercent.collectAsStateWithLifecycle()
    val fontSize by vm.fontSizePercent.collectAsStateWithLifecycle()
    val appIcon by vm.appIcon.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    // Zoom below LOW_RAM_WARN can OOM a 2 GB device (#51), so the first step under the line is
    // gated exactly as the Settings dialog gates it. Accepting once arms the rest of this visit;
    // arriving already below the line (a restored backup) must not nag.
    var lowZoomAccepted by remember { mutableStateOf(zoom < UiZoom.LOW_RAM_WARN) }
    var pendingLowZoom by remember { mutableStateOf<Int?>(null) }
    BackHandler { onBack() }
    Box(Modifier.fillMaxSize()) {
    MainSetupPage(contentScale = FULL_SETUP_CONTENT_SCALE) {
        Text(
            stringResource(R.string.setup_display_size_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.setup_display_size_description),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(22.dp))
        // Zoom first: it scales everything including the font row below it, so it is the coarse
        // control and the one most likely to be enough on its own.
        DisplaySizeRow(
            label = stringResource(R.string.settings_ui_zoom),
            percent = zoom,
            atMin = zoom <= UiZoom.MIN,
            atMax = zoom >= UiZoom.MAX,
            // Focus lands on "+", the opposite of the Settings zoom dialog. That dialog is opened to
            // escape an over-zoomed screen; this step exists because everything was too small (#179),
            // so the button the user reaches for first is the one that makes things bigger.
            increaseFocus = fr,
            onDecrease = {
                val next = UiZoom.clamp(zoom - UiZoom.STEP)
                if (next < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) pendingLowZoom = next else vm.setZoom(next)
            },
            onIncrease = { vm.setZoom(zoom + UiZoom.STEP) },
        )
        Spacer(Modifier.height(14.dp))
        DisplaySizeRow(
            label = stringResource(R.string.settings_font_size),
            percent = fontSize,
            atMin = fontSize <= UiFontScale.MIN,
            atMax = fontSize >= UiFontScale.MAX,
            increaseFocus = null,
            onDecrease = { vm.setFontSize(fontSize - UiFontScale.STEP) },
            onIncrease = { vm.setFontSize(fontSize + UiFontScale.STEP) },
        )
        Spacer(Modifier.height(18.dp))
        // No restart prompt here: nothing is on the home screen yet, and the pick applies as soon as
        // the app is next in the background.
        Text(
            stringResource(R.string.settings_app_icon),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        AppIconPicker(selected = appIcon, onPick = vm::setAppIcon)
        Spacer(Modifier.height(22.dp))
        SetupAccentRule()
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.setup_display_size_preview),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 620.dp),
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton(
                stringResource(R.string.common_back),
                onClick = onBack,
                modifier = Modifier.width(140.dp),
                style = OwnTVButtonStyle.SECONDARY,
            )
            OwnTVButton(
                stringResource(R.string.settings_reset),
                onClick = { vm.reset() },
                modifier = Modifier.width(150.dp),
                style = OwnTVButtonStyle.SECONDARY,
            )
            OwnTVButton(
                stringResource(R.string.setup_continue),
                onClick = onNext,
                modifier = Modifier.width(200.dp),
            )
        }
    }

    // Accept-the-risk gate for zoom below LOW_RAM_WARN (#51), the same prompt Settings shows. One
    // button, focus locked in every D-pad direction — OK accepts and applies the pending step, Back
    // cancels and leaves the zoom where it was.
    pendingLowZoom?.let { target ->
        val acceptFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { acceptFocus.requestFocus() } }
        // Composed after the screen's own BackHandler, so it wins while the warning is up and Back
        // dismisses the warning instead of leaving the step.
        BackHandler {
            pendingLowZoom = null
            runCatching { fr.requestFocus() }
        }
        Box(
            modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.dialogPanel(width = 460.dp, padding = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.settings_low_zoom_warning_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.settings_low_zoom_warning, UiZoom.LOW_RAM_WARN, UiZoom.LOW_RAM_WARN),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                OwnTVButton(
                    stringResource(R.string.settings_low_zoom_accept),
                    onClick = {
                        lowZoomAccepted = true
                        pendingLowZoom = null
                        vm.setZoom(target)
                        runCatching { fr.requestFocus() }
                    },
                    modifier = Modifier
                        .focusRequester(acceptFocus)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            start = FocusRequester.Cancel
                            end = FocusRequester.Cancel
                        },
                )
            }
        }
    }
    }
}

/**
 * One labelled "– 100% +" stepper. The buttons stay focusable at the limits (dimmed, never
 * disabled) for the same reason the Settings zoom dialog does it: a disabled button at the limit
 * strands D-pad focus, which on a screen about escaping an unreadable size is the worst outcome.
 */
@Composable
private fun DisplaySizeRow(
    label: String,
    percent: Int,
    atMin: Boolean,
    atMax: Boolean,
    increaseFocus: FocusRequester?,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SetupStepButton(stringResource(R.string.settings_decrease), dimmed = atMin, onClick = onDecrease)
            Text(
                stringResource(R.string.common_percent, percent),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.primary,
                modifier = Modifier.width(130.dp),
                textAlign = TextAlign.Center,
            )
            SetupStepButton(
                stringResource(R.string.settings_increase),
                dimmed = atMax,
                modifier = increaseFocus?.let { Modifier.focusRequester(it) } ?: Modifier,
                onClick = onIncrease,
            )
        }
    }
}

@Composable
private fun SetupStepButton(
    label: String,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(58.dp),
        shape = RoundedCornerShape(18.dp),
        contentAlignment = Alignment.Center,
        surface = GlassSurface.CARDS,
    ) { _ ->
        Text(
            label,
            style = MaterialTheme.typography.headlineMedium,
            color = if (dimmed) colors.outline else colors.onSurface,
        )
    }
}

@Composable
private fun DisclaimerScreen(onAgree: () -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    MainSetupPage {
        BrandLockup(markSize = 36, textSize = 26)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_before_you_start), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.setup_disclaimer),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(20.dp))
        SetupAccentRule()
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton(
                stringResource(R.string.common_back),
                onClick = onBack,
                modifier = Modifier.width(140.dp),
                style = OwnTVButtonStyle.SECONDARY,
            )
            OwnTVButton(
                stringResource(R.string.setup_i_understand),
                onClick = onAgree,
                modifier = Modifier.width(220.dp).focusRequester(fr),
            )
        }
    }
}

@Composable
private fun SetupChoiceScreen(onCreate: () -> Unit, onRestore: () -> Unit, onSyncDevice: () -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onBack() }
    MainSetupPage {
        BrandLockup(markSize = 36, textSize = 26)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_set_up_owntv), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.setup_setup_choice_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(20.dp))
        SetupAccentRule()
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(icon = OwnTVIcon.PERSON, title = stringResource(R.string.setup_new_profile), desc = stringResource(R.string.setup_create_profile_add_sources), modifier = Modifier.focusRequester(fr), onClick = onCreate)
            ChoiceCard(icon = OwnTVIcon.DOWNLOADS, title = stringResource(R.string.setup_restore_backup), desc = stringResource(R.string.setup_import_profiles_playlists), onClick = onRestore)
            ChoiceCard(icon = OwnTVIcon.REFRESH, title = stringResource(R.string.setup_sync_device), desc = stringResource(R.string.setup_sync_device_description), onClick = onSyncDevice)
        }
    }
}

@Composable
private fun AddContentScreen(hasExisting: Boolean, onNew: () -> Unit, onExisting: () -> Unit, onImport: () -> Unit, onSkip: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    MainSetupPage {
        BrandLockup(markSize = 36, textSize = 26)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_add_playlist), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.setup_add_playlist_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(20.dp))
        SetupAccentRule()
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(icon = OwnTVIcon.ADD, title = stringResource(R.string.setup_new), desc = stringResource(R.string.setup_add_m3u_xtream), modifier = Modifier.focusRequester(fr), onClick = onNew)
            if (hasExisting) {
                ChoiceCard(icon = OwnTVIcon.PLAYLIST, title = stringResource(R.string.setup_existing), desc = stringResource(R.string.setup_use_other_profile_playlists), onClick = onExisting)
            }
            ChoiceCard(icon = OwnTVIcon.DOWNLOADS, title = stringResource(R.string.setup_import), desc = stringResource(R.string.setup_restore_backup_file), onClick = onImport)
        }
        Spacer(Modifier.height(24.dp))
        OwnTVButton(
            stringResource(R.string.setup_skip_for_now),
            onClick = onSkip,
            modifier = Modifier.width(190.dp),
            style = OwnTVButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun SetupAccentRule() {
    Box(
        Modifier
            .width(38.dp)
            .height(3.dp)
            .background(OwnTVTheme.colors.primary, RoundedCornerShape(50)),
    )
}

private const val MAIN_SETUP_CONTENT_SCALE = 0.62f

/** No shrink — for the display-size step, whose whole job is to show sizes truthfully (#179). */
private const val FULL_SETUP_CONTENT_SCALE = 1f

@Composable
private fun MainSetupPage(
    contentScale: Float = MAIN_SETUP_CONTENT_SCALE,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        SetupAmbientBackdrop()
        Box(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                    }
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
private fun SetupAmbientBackdrop() {
    val primary = OwnTVTheme.colors.primary
    val transition = rememberInfiniteTransition()
    val ringScale by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.5f, size.height * 0.48f)
        val glowRadius = size.minDimension * 0.46f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.12f),
                    primary.copy(alpha = 0.045f),
                    Color.Transparent,
                ),
                center = center,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.075f),
            radius = size.minDimension * 0.34f * ringScale,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun ExistingSourcesScreen(sources: List<SourceEntity>, onAdd: (Set<Long>) -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onBack() }
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.setup_use_existing_playlists), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.setup_pick_playlists), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            // Cap to the screen (minus header/footer) so Back/Add stay reachable on small screens.
            val listMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 260.dp).coerceIn(140.dp, 320.dp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMax), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources, key = { it.id }) { src ->
                    val checked = src.id in selected
                    FocusableSurface(
                        onClick = { selected = if (checked) selected - src.id else selected + src.id },
                        modifier = if (src.id == sources.firstOrNull()?.id) Modifier.fillMaxWidth().focusRequester(fr) else Modifier.fillMaxWidth(),
                        selected = checked,
                        shape = RoundedCornerShape(12.dp),
                        selectedContainerColor = colors.primaryContainer,
                        contentAlignment = Alignment.CenterStart,
                    ) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(src.name, style = MaterialTheme.typography.titleMedium, color = if (checked) colors.onPrimaryContainer else colors.onSurface)
                                Text(src.url, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (checked) OwnTVIcon(OwnTVIcon.STAR, tint = colors.onPrimaryContainer, filled = true, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton(pluralStringResource(R.plurals.setup_add_selected_playlists, selected.size, selected.size), onClick = { onAdd(selected) }, enabled = selected.isNotEmpty())
            }
        }
    }
}

@Composable
private fun ImportBackupScreen(
    state: SourceImporter.ImportState,
    onPick: (java.io.File) -> Unit,
    onPassword: (java.io.File, String?) -> Unit,
    onBack: () -> Unit,
) {
    when (state) {
        SourceImporter.ImportState.Running -> Centered {
            OwnTVSpinner(sizeDp = 56); Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.setup_restoring), style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.onSurface)
        }
        is SourceImporter.ImportState.NeedPassword -> Centered {
            var password by remember { mutableStateOf("") }
            val firstFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
            Text(
                if (state.retry) stringResource(R.string.setup_wrong_backup_password) else stringResource(R.string.setup_enter_backup_password),
                style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    state.retry && state.sealed -> stringResource(R.string.setup_password_mismatch_sealed)
                    state.retry -> stringResource(R.string.setup_password_mismatch)
                    state.sealed -> stringResource(R.string.setup_backup_encrypted_prompt)
                    else -> stringResource(R.string.setup_backup_passwords_encrypted_prompt)
                },
                style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp),
            )
            Spacer(Modifier.height(20.dp))
            OwnTVTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.setup_backup_password),
                isPassword = true,
                focusRequester = firstFocus,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                // No "Skip" for a sealed container: without the password there is nothing to restore.
                if (!state.sealed) {
                    OwnTVButton(stringResource(R.string.setup_skip_no_passwords), onClick = { onPassword(state.file, null) }, style = OwnTVButtonStyle.SECONDARY)
                }
                OwnTVButton(stringResource(R.string.setup_restore), onClick = { onPassword(state.file, password) }, enabled = password.isNotBlank())
            }
        }
        is SourceImporter.ImportState.Failed -> Centered {
            Text(stringResource(R.string.setup_restore_failed), style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(state.failure.displayText(), style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
            Spacer(Modifier.height(20.dp))
            OwnTVButton(stringResource(R.string.common_back), onClick = onBack)
        }
        else -> StorageBrowser(
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

/** Restore chooser: send the backup from another device (LAN companion server) or pick a local file. */
@Composable
private fun ImportBackupChooserScreen(onRemote: () -> Unit, onLocal: () -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onBack() }
    Centered {
        Text(stringResource(R.string.setup_restore_a_backup), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.setup_restore_choice_description),
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(icon = OwnTVIcon.PLAYLIST, title = stringResource(R.string.setup_from_phone), desc = stringResource(R.string.setup_upload_from_wifi_device), modifier = Modifier.focusRequester(fr), onClick = onRemote)
            ChoiceCard(icon = OwnTVIcon.DOWNLOADS, title = stringResource(R.string.setup_local_file), desc = stringResource(R.string.setup_pick_backup_local), onClick = onLocal)
        }
        Spacer(Modifier.height(24.dp))
        OwnTVButton(stringResource(R.string.common_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
    }
}

@Composable
private fun ChoiceCard(icon: OwnTVIcon, title: String, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(width = 220.dp, height = 170.dp),
        shape = RoundedCornerShape(22.dp),
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                OwnTVIcon(icon, tint = colors.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (focused) colors.primary else colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ImportProgressScreen(
    state: SourceImporter.ImportState,
    progress: tv.own.owntv.core.sync.ImportStage?,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onBackground: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    val bgFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { bgFr.requestFocus() } }
    LaunchedEffect(state) {
        if (state is SourceImporter.ImportState.Success || state is SourceImporter.ImportState.Failed) runCatching { fr.requestFocus() }
    }
    BackHandler(enabled = state is SourceImporter.ImportState.Running || state is SourceImporter.ImportState.Idle) { onCancel() }
    Centered {
        when (state) {
            SourceImporter.ImportState.Running, SourceImporter.ImportState.Idle,
            is SourceImporter.ImportState.NeedPassword -> {
                val display = progress?.importProgressDisplay()
                OwnTVSpinner(sizeDp = 56)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.setup_importing_catalog), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    display?.primaryText() ?: stringResource(R.string.setup_preparing_catalog),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    display?.detailText() ?: stringResource(R.string.setup_preparing_catalog),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Enter the app right away; the import keeps running (VM is activity-scoped) and
                    // content appears as it lands — no need to sit through a big movies/series sync.
                    OwnTVButton(stringResource(R.string.setup_run_in_background), onClick = onBackground, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(bgFr))
                    OwnTVButton(stringResource(R.string.common_cancel), onClick = onCancel, style = OwnTVButtonStyle.SECONDARY)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.setup_watching_during_import),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            is SourceImporter.ImportState.Success -> {
                Text(stringResource(R.string.setup_all_set), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(10.dp))
                state.counts?.let { counts ->
                    Text(counts.summaryText(includeEpg = true), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
                }
                state.restoredItems?.let { items ->
                    Text(pluralStringResource(R.plurals.setup_restored_items, items, items), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
                }
                state.passwordsOmitted.takeIf { it }?.let {
                    Text(stringResource(R.string.setup_passwords_omitted), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                state.skippedSources.takeIf { it > 0 }?.let { skipped ->
                    Text(pluralStringResource(R.plurals.setup_skipped_sources, skipped, skipped), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                if (state.invalidLocale) {
                    Text(stringResource(R.string.setup_invalid_locale), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                state.warnings.warningText()?.let { warning ->
                    Text(warning, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                state.remainder.remainderText()?.let { remainder ->
                    Text(remainder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(28.dp))
                OwnTVButton(stringResource(R.string.setup_continue), onClick = onContinue, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(fr))
            }
            is SourceImporter.ImportState.Failed -> {
                Text(stringResource(R.string.setup_import_failed), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(10.dp))
                Text(state.failure.displayText(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton(stringResource(R.string.common_back), onClick = onCancel, style = OwnTVButtonStyle.SECONDARY)
                    OwnTVButton(stringResource(R.string.setup_try_again_caps), onClick = onRetry, modifier = Modifier.focusRequester(fr))
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        // Scrollable so wizard steps taller than a small/low-res screen keep all buttons reachable.
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
