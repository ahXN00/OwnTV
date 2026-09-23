package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.core.sync.local.SyncDirection
import tv.own.owntv.core.sync.local.SyncFailure
import tv.own.owntv.core.sync.local.shortCodes
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.rememberDialogFocusRestore
import tv.own.owntv.ui.components.restoreAfterDialogClose
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.animationsOn

/**
 * Settings → Local sync. The television's half of swapping data with the phone over the home Wi-Fi.
 *
 * It starts listening as soon as it opens, because that is the role a television usually plays: the
 * PIN and the QR code go on the screen and the phone's camera reads them. The other direction is
 * here too — the phone can be found on the network and its PIN typed with the remote — so either
 * device can start a sync.
 *
 * Nothing arrives silently. A container pushed here becomes a summary of what it would change, and
 * waits for someone to press OK.
 */
@Composable
fun LocalSyncScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: LocalSyncViewModel = koinViewModel()
    val paired by vm.paired.collectAsStateWithLifecycle()
    val hosting by vm.hosting.collectAsStateWithLifecycle()

    val firstFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    // Deliberately NOT started on entry. Which device hosts is the user's choice, on both apps and in
    // the same words — a television that quietly opened a listener the moment you looked at the
    // screen was making that choice for you, and gave you no way to unmake it.
    //
    // Landing focus on the first row was a 50 ms timer against Compose's own initial-focus pass, and
    // on a real TV the timer lost: the screen opened with nothing focused at all, and the first
    // D-pad press then escaped to the top bar. This is the same retry-per-frame the rest of the app
    // uses to get focus back after a dialog — it keeps asking until a request is accepted, so there
    // is no duration to guess.
    LaunchedEffect(Unit) { restoreAfterDialogClose(firstFocus, scrollState, 0) }
    // Which row to put focus back on when a step popup closes.
    val stepFocus = rememberDialogFocusRestore(anyDialogOpen = vm.step != null, scrollState = scrollState)
    // The listener runs while this screen does, and not a moment longer.
    DisposableEffect(Unit) { onDispose { vm.stopHosting() } }
    BackHandler { if (vm.step != null) vm.cancel() else onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .verticalScroll(scrollState)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val listening = hosting as? CompanionServerState.Listening
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Header(
                    stringResource(R.string.local_sync_title),
                    onBack,
                    subtitle = stringResource(R.string.local_sync_description),
                )
            }
            SyncModePill(on = listening != null)
        }
        Spacer(Modifier.height(12.dp))

        // Two columns, because the PIN and the QR used to be injected *between* the rows: turning
        // Sync mode on shoved the paired devices a third of a screen down while the user was
        // looking at them. Beside the list, nothing moves — the card simply appears.
        //
        // focusGroup + trapVerticalFocusExit is the other half of the focus fix: with nothing
        // focused, a D-pad Down found no target inside this pane and escaped to the top bar's
        // Search chip. Cancelling the vertical exit pins focus to the pane's edge row instead.
        Row(
            modifier = Modifier.focusGroup().trapVerticalFocusExit(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Only the second group is labelled, exactly as the More screen's preview pane
                // already labels this same feature. A "This device" heading would be a new
                // user-visible string, and those live in core and ship in 24 languages.
                // One state, one switch. Sync mode is what makes this device reachable at all —
                // every other action on this screen, in either direction, needs the FAR device to
                // have it on too, which is why it is the first row and why the failure message
                // names where to find it.
                Row2(
                    // A listening port and an announcement on the LAN — a network state, not an
                    // archive.
                    icon = OwnTVIcon.NETWORK,
                    title = stringResource(R.string.local_sync_mode),
                    desc = stringResource(R.string.local_sync_mode_description),
                    chip = stringResource(if (listening != null) R.string.common_on else R.string.common_off),
                    primaryChip = listening != null,
                    modifier = Modifier.focusRequester(firstFocus),
                    onClick = { if (listening != null) vm.stopHosting() else vm.startHosting() },
                )
                Row2(
                    // This starts discovery. REFRESH reads as "sync again", the far end of the flow.
                    icon = OwnTVIcon.SEARCH,
                    title = stringResource(R.string.local_sync_connect),
                    desc = stringResource(R.string.local_sync_connect_description),
                    modifier = Modifier.focusRequester(connectFocus),
                    onClick = { stepFocus.value = connectFocus; vm.beginPairing() },
                )
                if (paired.isNotEmpty()) {
                    GroupLabel(stringResource(R.string.local_sync_paired_devices))
                    // Only the devices whose names collide get a code, so a normal household
                    // never sees one.
                    val codes = shortCodes(paired)
                    paired.forEach { device ->
                        val rowFocus = remember(device.id) { FocusRequester() }
                        Row2(
                            icon = OwnTVIcon.PHONE,
                            title = codes[device.id]
                                ?.let { stringResource(R.string.local_sync_device_with_code, device.name, it) }
                                ?: device.name,
                            desc = lastSyncedText(device.lastSyncAt),
                            chevron = true,
                            modifier = Modifier.focusRequester(rowFocus),
                            onClick = { stepFocus.value = rowFocus; vm.chooseDevice(device) },
                        )
                    }
                }
                if (vm.busy) BusyRow()
            }
            listening?.let { HostingCard(it, vm.deviceName) }
        }

        // Each step is a popup over the list, not a block spliced into it. Inline, the step's first
        // action was never focused — the header's Back arrow is the first focusable in the tree and
        // took the ring instead. A popup owns its focus, so it cannot. This also matches the phone,
        // which puts every one of these steps in a bottom sheet.
        when (val step = vm.step) {
            null -> Unit
            is LocalSyncViewModel.Step.FindDevice -> StepPopup(vm::cancel) { FindDeviceBlock(vm) }
            is LocalSyncViewModel.Step.EnterPin -> StepPopup(vm::cancel) { PinBlock(vm) }
            is LocalSyncViewModel.Step.ChooseDirection -> StepPopup(vm::cancel) { DirectionBlock(vm, step) }
            is LocalSyncViewModel.Step.ChooseSections -> StepPopup(vm::cancel) { SectionsBlock(vm, step) }
            is LocalSyncViewModel.Step.Confirm -> StepPopup(vm::cancel) { ConfirmBlock(vm, step) }
            is LocalSyncViewModel.Step.Result -> StepPopup(vm::cancel) { ResultBlock(vm, step) }
        }

        vm.error?.let { failure ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(failure.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = DestructiveRed,
            )
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_close), onClick = vm::dismissError, style = OwnTVButtonStyle.SECONDARY)
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The same feature offered during first-run setup, as the third way of getting a new television
 * furnished: copy the old one over the Wi-Fi instead of typing a playlist in or finding a backup file.
 *
 * It lives beside [LocalSyncScreen] rather than in the setup package so it can reuse that screen's
 * step blocks unchanged — they are the substance of it, and a second copy of them would be a second
 * thing to keep right. Presented as a settings panel for the same reason `RemoteBackupRestoreScreen`
 * is: the wizard already borrows a settings screen for a step.
 *
 * Two things differ from the settings screen, and only two:
 *  - **it never hosts.** A television still being set up has nothing worth serving, and announcing an
 *    empty container on the network would only be something for the other device to find by mistake.
 *  - **the direction is not a question.** A device at this point in its life can only receive, so
 *    [LocalSyncViewModel.Step.ChooseDirection] is answered for the user rather than drawn. What *is*
 *    still asked is which sections to take — someone moving to a new television may well want the
 *    playlists without the old one's settings.
 */
@Composable
fun SetupLocalSyncScreen(onRestored: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: LocalSyncViewModel = koinViewModel()

    // Straight into discovery: arriving here IS the decision to look for the other device, so a
    // screen that then asked the user to press "Find a device" would be asking twice.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.beginPairing()
        started = true
    }

    val step = vm.step
    LaunchedEffect(step, started) {
        when {
            !started -> Unit
            // Cancelling any block clears the step, and on this screen that means leaving — there is
            // no device list underneath to fall back to.
            step == null -> onBack()
            step is LocalSyncViewModel.Step.ChooseDirection -> vm.chooseDirection(SyncDirection.RECEIVE)
            else -> Unit
        }
    }

    // Cancelling with the remote and cancelling with a button do the same thing — except once the
    // data has landed, when there is nothing left to cancel and Back means the same as Done.
    BackHandler { if (step is LocalSyncViewModel.Step.Result) onRestored() else vm.cancel() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Header(
            stringResource(R.string.setup_sync_device),
            onBack = { if (step is LocalSyncViewModel.Step.Result) onRestored() else vm.cancel() },
        )
        Spacer(Modifier.height(12.dp))

        when (step) {
            null -> Unit
            is LocalSyncViewModel.Step.FindDevice -> FindDeviceBlock(vm)
            is LocalSyncViewModel.Step.EnterPin -> PinBlock(vm)
            // Answered above; drawing anything for it would flash a list the user never chose from.
            is LocalSyncViewModel.Step.ChooseDirection -> Unit
            is LocalSyncViewModel.Step.ChooseSections -> SectionsBlock(vm, step)
            is LocalSyncViewModel.Step.Confirm -> ConfirmBlock(vm, step)
            // Not [ResultBlock]: its Done returns to the device list, and here the only thing left to
            // do is finish onboarding.
            is LocalSyncViewModel.Step.Result -> SetupResultBlock(step, onDone = onRestored)
        }

        vm.error?.let { failure ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(failure.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = DestructiveRed,
            )
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_close), onClick = vm::dismissError, style = OwnTVButtonStyle.SECONDARY)
        }

        if (vm.busy) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.local_sync_working), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** [ResultBlock] with the one difference setup needs: Done leaves the wizard instead of the step. */
@Composable
private fun SetupResultBlock(step: LocalSyncViewModel.Step.Result, onDone: () -> Unit) {
    val colors = OwnTVTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.local_sync_done), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        step.received?.let {
            Text(stringResource(R.string.local_sync_received_items, it.items), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        OwnTVButton(stringResource(R.string.common_done), onClick = onDone)
    }
}

/**
 * The badge that says this television is reachable right now.
 *
 * Sync mode is the one piece of state here with a consequence off the screen — a listening port and
 * an announcement on the network — so it is said plainly rather than left to be inferred from a row.
 */
@Composable
private fun SyncModePill(on: Boolean) {
    val colors = OwnTVTheme.colors
    Text(
        // Off says only "Off": the alternative is a second "Sync mode …" string, and this slot
        // already reads "Off" in the More screen's preview pane for this very feature.
        text = stringResource(if (on) R.string.local_sync_mode_pill else R.string.common_off),
        style = MaterialTheme.typography.labelMedium,
        color = if (on) colors.onPrimaryContainer else colors.onSecondaryContainer,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) colors.primaryContainer else colors.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/**
 * The one panel every Local sync step is drawn in.
 *
 * Built from the shared popup method rather than a hand-rolled overlay: [OwnTVPopup] owns the
 * focus-isolated window and the TV-keyboard geometry (two of these steps have a text field in them),
 * [dialogPanel] brings the Glass-aware fill and its own scroll, and [trapAllFocusExit] keeps the
 * D-pad from wandering back onto the list behind.
 *
 * Everything here is the house default on purpose. [OwnTVPopup]'s `fontScale` is deliberately NOT
 * passed: 65 of the app's 72 popups leave it alone, and the seven that lower it are dense forms of
 * text fields. These steps are menus, so a lowered scale just made them look unlike every other
 * popup in the app. Width and padding likewise use a combination the app already uses elsewhere.
 */
@Composable
private fun StepPopup(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    OwnTVPopup(onDismissRequest = onDismiss) {
        val panel = remember { FocusRequester() }
        // Requesting focus on the panel group hands it to the panel's first focusable child, so every
        // step lands on its own first control without each block naming one. Retried per frame: the
        // popup's window does not own focus for the first frame or two after it opens.
        LaunchedEffect(Unit) { restoreAfterDialogClose(panel) }
        Box(
            Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .dialogPanel(width = STEP_PANEL_WIDTH, padding = 28.dp)
                    .focusGroup()
                    .focusRequester(panel),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
    }
}

/** "Working…", with a spinner, in the shape of a row — not a bare grey line under the list. */
@Composable
private fun BusyRow() {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BusySpinner(Modifier.size(18.dp), colors.primary)
        Text(
            stringResource(R.string.local_sync_working),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * An indeterminate ring. Compose Material3 is not a dependency of this app, so the one spinner this
 * screen needs is drawn rather than dragged in.
 *
 * Gated on [animationsOn], and the repeat uses a plain fixed `tween` — `ownTvTween` collapses to 0 ms
 * when the user turns Animations off, and a 0 ms iteration inside `infiniteRepeatable` is a
 * divide-by-zero on the main thread. See the warning on `ownTvTween`.
 */
@Composable
private fun BusySpinner(modifier: Modifier, color: Color) {
    val angle = if (animationsOn) {
        rememberInfiniteTransition(label = "localSyncBusy").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "localSyncBusyAngle",
        ).value
    } else {
        0f
    }
    Canvas(modifier) {
        val stroke = size.minDimension * 0.14f
        val d = size.minDimension - stroke
        drawArc(
            color = color,
            startAngle = angle - 90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f),
            size = Size(d, d),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** "Last synced: 2 hours ago", or the plain "Not synced yet" the EPG rows already use. */
@Composable
private fun lastSyncedText(at: Long): String = if (at <= 0) {
    stringResource(R.string.settings_epg_sources_not_synced)
} else {
    stringResource(
        R.string.local_sync_last_synced,
        android.text.format.DateUtils
            .getRelativeTimeSpanString(at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
            .toString(),
    )
}

/**
 * The PIN, the QR and the address — what the phone needs to reach this television.
 *
 * A card in its own column, not a block spliced into the list. It used to sit between "Connect" and
 * the paired devices, so switching Sync mode on pushed the device rows a third of a screen downwards
 * with the user's focus already on them.
 */
@Composable
private fun HostingCard(state: CompanionServerState.Listening, deviceName: String) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = Modifier
            .width(HOSTING_CARD_WIDTH)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(deviceName, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.local_sync_host_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            state.pin,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            letterSpacing = 8.sp,
        )
        Spacer(Modifier.height(12.dp))
        state.qr?.let { qr ->
            Image(
                bitmap = qr.asImageBitmap(),
                contentDescription = stringResource(R.string.local_sync_qr_description),
                modifier = Modifier.size(QR_SIZE).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(9.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(10.dp))
        }
        state.urls.forEach { url ->
            Text(
                url,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Discovery, plus an address typed with the remote for the networks where discovery fails. */
@Composable
private fun FindDeviceBlock(vm: LocalSyncViewModel) {
    val colors = OwnTVTheme.colors
    var manual by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.local_sync_find_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (vm.found.isEmpty()) {
            Text(stringResource(R.string.local_sync_searching), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            vm.found.forEach { device ->
                // A device already paired says so instead of showing an address the user has no use
                // for, and opens its actions rather than asking for a PIN it does not need.
                val known = vm.pairedMatch(device)
                Row2(
                    icon = OwnTVIcon.PHONE,
                    title = device.name,
                    desc = if (known != null) stringResource(R.string.local_sync_already_paired) else device.address,
                    onClick = { vm.choose(device) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OwnTVTextField(
            value = manual,
            onValueChange = { manual = it },
            label = stringResource(R.string.local_sync_manual_address_label),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnTVButton(
                stringResource(R.string.settings_backup_continue),
                onClick = { if (manual.isNotBlank()) vm.chooseAddress(manual.trim(), portOf(manual)) },
            )
            OwnTVButton(stringResource(R.string.common_cancel), onClick = vm::cancel, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

@Composable
private fun PinBlock(vm: LocalSyncViewModel) {
    val colors = OwnTVTheme.colors
    var pin by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.local_sync_enter_pin_description), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        OwnTVTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(PIN_LENGTH) },
            label = stringResource(R.string.local_sync_pin_label),
            keyboardType = KeyboardType.NumberPassword,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnTVButton(
                stringResource(R.string.local_sync_pair),
                onClick = { if (pin.length == PIN_LENGTH) vm.submitPin(pin) },
            )
            OwnTVButton(stringResource(R.string.common_cancel), onClick = vm::cancel, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

@Composable
private fun DirectionBlock(vm: LocalSyncViewModel, step: LocalSyncViewModel.Step.ChooseDirection) {
    val name = step.device.name
    val colors = OwnTVTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Which device this is about. Inline, nothing said — the header above still read
        // "Local sync" while four rows offered to move data to somewhere unnamed.
        Text(name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(
            lastSyncedText(step.device.lastSyncAt),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row2(
            icon = OwnTVIcon.SEND,
            title = stringResource(R.string.local_sync_send_to, name),
            desc = stringResource(R.string.local_sync_send_description),
            onClick = { vm.chooseDirection(SyncDirection.SEND) },
        )
        Row2(
            icon = OwnTVIcon.DOWNLOADS,
            title = stringResource(R.string.local_sync_receive_from, name),
            desc = stringResource(R.string.local_sync_receive_description),
            onClick = { vm.chooseDirection(SyncDirection.RECEIVE) },
        )
        Row2(
            // Two arrows say "both ways" at a glance, and it stops REFRESH meaning both
            // "find a device" and "two-way sync" on the same screen.
            icon = OwnTVIcon.SWAP,
            title = stringResource(R.string.local_sync_merge_with, name),
            desc = stringResource(R.string.local_sync_merge_description),
            onClick = { vm.chooseDirection(SyncDirection.MERGE) },
        )
        // The only destructive row here. The glyph is right; what it lacked was any sign that it is
        // not a fourth way of syncing, sitting one arrow-press under "Merge".
        Divider()
        Row2(
            icon = OwnTVIcon.CLOSE,
            title = stringResource(R.string.local_sync_unpair),
            desc = stringResource(R.string.local_sync_unpair_description),
            iconTint = DestructiveRed,
            iconBackground = DestructiveTile,
            titleTint = DestructiveTitle,
            onClick = { vm.unpair(step.device); vm.cancel() },
        )
        Spacer(Modifier.height(10.dp))
        OwnTVButton(stringResource(R.string.common_cancel), onClick = vm::cancel, style = OwnTVButtonStyle.SECONDARY)
    }
}

/** The same list of parts Backup & Restore offers, ticked with OK. */
@Composable
private fun SectionsBlock(vm: LocalSyncViewModel, step: LocalSyncViewModel.Step.ChooseSections) {
    val colors = OwnTVTheme.colors
    var sections by remember { mutableStateOf(BackupManager.Section.entries.toSet()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(
                when (step.direction) {
                    SyncDirection.SEND -> R.string.local_sync_what_to_send
                    SyncDirection.RECEIVE -> R.string.local_sync_what_to_receive
                    SyncDirection.MERGE -> R.string.local_sync_what_to_merge
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Text(stringResource(R.string.local_sync_sections_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        BackupManager.Section.entries.forEach { section ->
            Row2(
                icon = OwnTVIcon.BACKUP,
                title = stringResource(section.labelRes()),
                chip = stringResource(if (section in sections) R.string.common_on else R.string.common_off),
                primaryChip = section in sections,
                onClick = { sections = if (section in sections) sections - section else sections + section },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnTVButton(
                stringResource(R.string.settings_backup_continue),
                onClick = { if (sections.isNotEmpty()) vm.start(sections) },
            )
            OwnTVButton(stringResource(R.string.common_cancel), onClick = vm::cancel, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

/** The dry run. Nothing has been written while this is on screen. */
@Composable
private fun ConfirmBlock(vm: LocalSyncViewModel, step: LocalSyncViewModel.Step.Confirm) {
    val colors = OwnTVTheme.colors
    val preview = step.preview
    // The other device's hardware settings and engine pins: offered only when it sent some, unticked.
    var deviceSettings by remember(step.file) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.local_sync_confirm_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        if (preview.isEmpty) {
            Text(stringResource(R.string.local_sync_nothing_to_change), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.local_sync_confirm_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Change(R.string.local_sync_change_profiles, preview.newProfiles)
            Change(R.string.local_sync_change_sources, preview.newSources)
            Change(R.string.local_sync_change_favorites, preview.newFavorites)
            Change(R.string.local_sync_change_history, preview.newHistory)
            Change(R.string.local_sync_change_resume, preview.newResume)
            Change(R.string.local_sync_change_reorder, preview.newReorder)
            Change(R.string.local_sync_change_settings, preview.changedSettings)
            Change(R.string.local_sync_change_deletions, preview.deletions)
            if (preview.hasCustomizations) {
                Text(stringResource(R.string.local_sync_change_customize), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            }
        }
        if (preview.hasDeviceSettings) {
            Spacer(Modifier.height(6.dp))
            Row2(
                icon = OwnTVIcon.BACKUP,
                title = stringResource(R.string.settings_backup_device_settings),
                desc = stringResource(R.string.settings_backup_device_settings_desc),
                chip = stringResource(if (deviceSettings) R.string.common_on else R.string.common_off),
                primaryChip = deviceSettings,
                onClick = { deviceSettings = !deviceSettings },
            )
        }
        if (step.direction == SyncDirection.MERGE) {
            Text(stringResource(R.string.local_sync_merge_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnTVButton(
                stringResource(R.string.local_sync_apply),
                onClick = { if (!preview.isEmpty) vm.confirm(deviceSettings) },
            )
            OwnTVButton(stringResource(R.string.common_cancel), onClick = vm::cancel, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

@Composable
private fun ResultBlock(vm: LocalSyncViewModel, step: LocalSyncViewModel.Step.Result) {
    val colors = OwnTVTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.local_sync_done), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        step.received?.let {
            Text(stringResource(R.string.local_sync_received_items, it.items), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (step.sent) {
            Text(stringResource(R.string.local_sync_sent), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        OwnTVButton(stringResource(R.string.common_done), onClick = vm::cancel, style = OwnTVButtonStyle.SECONDARY)
    }
}

/** One counted change; a nought is left out rather than listed as nothing. */
@Composable
private fun Change(labelRes: Int, count: Int) {
    if (count <= 0) return
    Text(
        stringResource(labelRes, count),
        style = MaterialTheme.typography.bodyMedium,
        color = OwnTVTheme.colors.onSurface,
    )
}

private fun SyncFailure.messageRes(): Int = when (this) {
    SyncFailure.Unreachable -> R.string.local_sync_error_unreachable
    SyncFailure.NotAuthorized -> R.string.local_sync_error_unauthorized
    SyncFailure.BadPayload -> R.string.local_sync_error_bad_payload
    SyncFailure.Unknown -> R.string.local_sync_error_unknown
}

private fun BackupManager.Section.labelRes(): Int = when (this) {
    BackupManager.Section.SOURCES -> R.string.settings_backup_section_sources
    BackupManager.Section.CUSTOMIZE -> R.string.settings_backup_section_customize
    BackupManager.Section.FAVORITES -> R.string.settings_backup_section_favorites
    BackupManager.Section.HISTORY -> R.string.settings_backup_section_history
    BackupManager.Section.RESUME -> R.string.settings_backup_section_resume
    BackupManager.Section.MANUAL_REORDER -> R.string.settings_backup_section_reorder
    BackupManager.Section.SETTINGS -> R.string.settings_backup_section_settings
}

/** `192.168.1.5:8089` or a whole URL both carry a port; a bare address means the usual one. */
private fun portOf(address: String): Int =
    address.removePrefix("http://").substringAfter(':', "").substringBefore('/')
        .toIntOrNull() ?: tv.own.owntv.core.companion.CompanionLink.DEFAULT_PORT

/** The destructive tone: the mark, the tile behind it, and the label. Also the error text's colour. */
private val DestructiveRed = Color(0xFFEF4444)
private val DestructiveTile = DestructiveRed.copy(alpha = 0.14f)
private val DestructiveTitle = Color(0xFFF0A3A3)

/**
 * Wide enough for [QR_SIZE] plus the card's padding, and no wider — the list beside it is what the
 * screen is for. The QR keeps its size: it is read by a phone camera from across the room.
 */
private val HOSTING_CARD_WIDTH = 250.dp
private val QR_SIZE = 188.dp

/** `520.dp` + `28.dp` padding is one of the app's commonest panel sizes; these steps are not special. */
private val STEP_PANEL_WIDTH = 520.dp

private const val PIN_LENGTH = 6
