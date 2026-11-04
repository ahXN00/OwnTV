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
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.core.sync.local.SyncDirection
import tv.own.owntv.core.sync.local.SyncFailure
import tv.own.owntv.core.sync.local.shortCodes
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.rememberDialogFocusRestore
import tv.own.owntv.ui.components.restoreAfterDialogClose
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.theme.animationsOn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import tv.own.owntv.ui.stage.StagePopupDivider
import tv.own.owntv.ui.stage.StagePopupIcon
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.stage.StageSwitch
import tv.own.owntv.ui.stage.StageTile
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * More → Local sync. The television's half of swapping data with the phone over the home Wi-Fi.
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
fun LocalSyncScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** More › Local sync: what OK / ▶ on the sheet focuses. Settings passes none and gets focus on open. */
    entry: FocusRequester? = null,
) {
    val vm: LocalSyncViewModel = koinViewModel()
    val paired by vm.paired.collectAsStateWithLifecycle()
    val hosting by vm.hosting.collectAsStateWithLifecycle()

    val ownFocus = remember { FocusRequester() }
    val firstFocus = entry ?: ownFocus
    val connectFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    // Deliberately NOT started on entry. Which device hosts is the user's choice, on both apps and in
    // the same words — a television that quietly opened a listener the moment you looked at the
    // screen was making that choice for you, and gave you no way to unmake it.
    //
    // Opened from Settings, focus lands on the first row by retrying per frame until a request is
    // accepted. In More the page is shown while the sheet still has focus, so it waits for OK / ▶.
    if (entry == null) LaunchedEffect(Unit) { restoreAfterDialogClose(firstFocus, scrollState, 0) }
    // Which row to put focus back on when a step popup closes.
    val stepFocus = rememberDialogFocusRestore(anyDialogOpen = vm.step != null, scrollState = scrollState)
    // The listener runs while this page does, and not a moment longer.
    DisposableEffect(Unit) { onDispose { vm.stopHosting() } }
    BackHandler(enabled = vm.step != null || onBack != null) { if (vm.step != null) vm.cancel() else onBack?.invoke() }

    val listening = hosting as? CompanionServerState.Listening
    // Title and description, Sync mode and Connect side by side, the paired devices. The PIN and
    // QR card appears beside the devices while Sync mode is on, so nothing below it moves.
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .focusGroup()
            .trapVerticalFocusExit(),
    ) {
        Text(stringResource(R.string.local_sync_title), style = stageText(42, 800, (-1).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.more_sync_description), style = stageText(18, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx))
        Row(
            Modifier.padding(top = 26.mpx).fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
        ) {
            SyncTile(
                icon = OwnTVIcon.PHONE,
                title = stringResource(R.string.local_sync_mode),
                body = stringResource(R.string.more_sync_mode_description),
                onClick = { if (listening != null) vm.stopHosting() else vm.startHosting() },
                modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(firstFocus),
            ) { StageSwitch(on = listening != null) }
            SyncTile(
                icon = OwnTVIcon.SEARCH,
                title = stringResource(R.string.local_sync_connect),
                body = stringResource(R.string.local_sync_connect_description),
                onClick = { stepFocus.value = connectFocus; vm.beginPairing() },
                modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(connectFocus),
            ) { OwnTVIcon(OwnTVIcon.CHEVRON, StageColors.Muted, Modifier.size(20.mpx)) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
            Column(Modifier.weight(1f)) {
                if (paired.isNotEmpty()) {
                    Text(
                        stringResource(R.string.local_sync_paired_devices).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
                        style = stageText(13, 800, 0.13.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 30.mpx, bottom = 12.mpx),
                    )
                    // Only the devices whose names collide get a code, so a normal household never sees one.
                    val codes = shortCodes(paired)
                    val sep = stringResource(R.string.content_epg_bits_separator)
                    paired.forEach { device ->
                        val rowFocus = remember(device.id) { FocusRequester() }
                        tv.own.owntv.features.more.StageActionRow(
                            height = 104.mpx,
                            focus = rowFocus,
                            onClick = { stepFocus.value = rowFocus; vm.chooseDevice(device) },
                            leading = { focused ->
                                Box(Modifier.width(60.mpx), contentAlignment = Alignment.Center) {
                                    OwnTVIcon(OwnTVIcon.PHONE, if (focused) StageColors.Text else StageColors.Muted, Modifier.size(34.mpx))
                                }
                            },
                            title = codes[device.id]?.let { stringResource(R.string.local_sync_device_with_code, device.name, it) } ?: device.name,
                            line = listOfNotNull(device.address.takeIf { it.isNotBlank() }, lastSyncedText(device.lastSyncAt)).joinToString(sep),
                            actions = listOf(
                                tv.own.owntv.features.more.RowAction(stringResource(R.string.settings_sync_now), OwnTVIcon.REFRESH) { stepFocus.value = rowFocus; vm.chooseDevice(device) },
                                tv.own.owntv.features.more.RowAction(stringResource(R.string.more_sync_unpair), OwnTVIcon.CLOSE) { vm.unpair(device) },
                            ),
                        )
                    }
                }
                if (vm.busy) BusyRow()
                vm.error?.let { failure ->
                    Text(stringResource(failure.messageRes()), style = stageText(17, 500), color = StageColors.Danger, modifier = Modifier.padding(top = 16.mpx))
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_close), onClick = vm::dismissError, modifier = Modifier.padding(top = 8.mpx))
                }
            }
            listening?.let { Box(Modifier.padding(top = 30.mpx)) { HostingCard(it, vm.deviceName) } }
        }

        // Each step is a popup over the page, not a block spliced into it: a popup owns its focus.
        when (val step = vm.step) {
            null -> Unit
            is LocalSyncViewModel.Step.FindDevice -> StepPopup(vm::cancel) { FindDeviceBlock(vm) }
            is LocalSyncViewModel.Step.EnterPin -> StepPopup(vm::cancel) { PinBlock(vm) }
            is LocalSyncViewModel.Step.ChooseDirection -> StepPopup(vm::cancel) { DirectionBlock(vm, step) }
            is LocalSyncViewModel.Step.ChooseSections -> StepPopup(vm::cancel) { SectionsBlock(vm, step) }
            is LocalSyncViewModel.Step.Confirm -> StepPopup(vm::cancel) { ConfirmBlock(vm, step) }
            is LocalSyncViewModel.Step.Result -> StepPopup(vm::cancel) { ResultBlock(vm, step) }
        }
        Spacer(Modifier.height(40.mpx))
    }
}

/** A focusable `.tile2` with an icon, a 21/800 title, a 16 muted line and a control on the right (switch, ›). */
@Composable
private fun SyncTile(
    icon: OwnTVIcon,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    StageTile(modifier = modifier, onClick = onClick) { focused ->
        Row(horizontalArrangement = Arrangement.spacedBy(18.mpx), verticalAlignment = Alignment.CenterVertically) {
            OwnTVIcon(icon, if (focused) StageColors.Text else StageColors.Muted, Modifier.size(30.mpx))
            Column(Modifier.weight(1f)) {
                Text(title, style = stageText(21, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(body, style = stageText(16, 500), color = if (focused) StageColors.Text.copy(alpha = 0.8f) else StageColors.Muted, modifier = Modifier.padding(top = 2.mpx))
            }
            trailing()
        }
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
    val leave = { if (step is LocalSyncViewModel.Step.Result) onRestored() else vm.cancel() }
    var manual by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(step is LocalSyncViewModel.Step.FindDevice) { kotlinx.coroutines.delay(80); runCatching { firstFocus.requestFocus() } }
    val finding = step is LocalSyncViewModel.Step.FindDevice
    // The wizard frame; the devices found and the address as rows, Cancel and Continue below.
    tv.own.owntv.features.setup.WizardFrame(
        step = tv.own.owntv.features.setup.WizardStep.PROFILE,
        title = stringResource(R.string.setup_sync_device),
        sub = stringResource(R.string.local_sync_find_hint),
        back = tv.own.owntv.features.setup.WizardAction(stringResource(R.string.common_cancel), leave),
        next = if (finding) {
            tv.own.owntv.features.setup.WizardAction(
                stringResource(R.string.settings_backup_continue),
                { if (manual.isNotBlank()) vm.chooseAddress(manual.trim(), portOf(manual)) },
                enabled = manual.isNotBlank(),
            )
        } else null,
    ) {
        when (step) {
            null -> Unit
            is LocalSyncViewModel.Step.FindDevice -> {
                if (vm.found.isEmpty()) {
                    StageSettingRow(
                        icon = OwnTVIcon.REFRESH,
                        title = stringResource(R.string.local_sync_searching),
                        desc = stringResource(R.string.setup_device_appear),
                        value = SettingValue.Custom { Text("…", style = tv.own.owntv.ui.theme.stageText(18, 700), color = tv.own.owntv.ui.theme.StageColors.Dim) },
                        onClick = {},
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                } else {
                    vm.found.forEachIndexed { i, device ->
                        // A device already paired says so instead of showing an address the user has no
                        // use for, and opens its actions rather than asking for a PIN it does not need.
                        val known = vm.pairedMatch(device)
                        StageSettingRow(
                            icon = OwnTVIcon.PHONE,
                            title = device.name,
                            desc = if (known != null) stringResource(R.string.local_sync_already_paired) else device.address,
                            value = SettingValue.Opens(null),
                            onClick = { vm.choose(device) },
                            modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
                val addressLabel = stringResource(R.string.local_sync_manual_address_label)
                StageFieldRow(
                    OwnTVIcon.PENCIL, addressLabel, manual, { manual = it },
                    SettingHelp(addressLabel, stringResource(R.string.settings_form_field_help)),
                    placeholder = stringResource(R.string.setup_device_address_hint),
                )
            }
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
            Text(
                text = stringResource(failure.messageRes()),
                style = tv.own.owntv.ui.theme.stageText(25, 400),
                color = DestructiveRed,
                modifier = Modifier.padding(top = 12.dp),
            )
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_close), onClick = vm::dismissError)
        }
        if (vm.busy) {
            Text(stringResource(R.string.local_sync_working), style = tv.own.owntv.ui.theme.stageText(25, 400), color = tv.own.owntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** [ResultBlock] with the one difference setup needs: Done leaves the wizard instead of the step. */
@Composable
private fun SetupResultBlock(step: LocalSyncViewModel.Step.Result, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.local_sync_done), style = tv.own.owntv.ui.theme.stageText(32, 600), color = tv.own.owntv.ui.theme.StageColors.Text)
        step.received?.let {
            Text(stringResource(R.string.local_sync_received_items, it.items), style = tv.own.owntv.ui.theme.stageText(25, 400), color = tv.own.owntv.ui.theme.StageColors.Muted)
        }
        Spacer(Modifier.height(10.dp))
        tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = onDone, tinted = true)
    }
}

/**
 * The one panel every Local sync step is drawn in: a [StagePopup][tv.own.owntv.ui.stage.StagePopup],
 * which owns the focus-isolated window and the TV-keyboard geometry (two of these steps have a text
 * field in them), the glass panel and its scroll, and keeps the D-pad from wandering back onto the
 * list behind.
 */
@Composable
private fun StepPopup(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val panel = remember { FocusRequester() }
    // Requesting focus on the group hands it to the step's first focusable child, so every step lands
    // on its own first control without each block naming one. Retried per frame: the popup's window
    // does not own focus for the first frame or two after it opens.
    LaunchedEffect(Unit) { restoreAfterDialogClose(panel) }
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = null, eyebrow = stringResource(R.string.local_sync_title).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), width = 880.mpx) {
        Column(Modifier.focusGroup().focusRequester(panel), verticalArrangement = Arrangement.spacedBy(2.mpx), content = content)
    }
}

/** "Working…", with a spinner, in the shape of a row — not a bare grey line under the list. */
@Composable
private fun BusyRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tv.own.owntv.ui.theme.StageColors.Ink.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BusySpinner(Modifier.size(18.dp), tv.own.owntv.ui.theme.stageAccent.accent)
        Text(
            stringResource(R.string.local_sync_working),
            style = tv.own.owntv.ui.theme.stageText(25, 400),
            color = tv.own.owntv.ui.theme.StageColors.Muted,
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
    Column(
        modifier = Modifier
            .width(HOSTING_CARD_WIDTH)
            .clip(RoundedCornerShape(26.mpx))
            .background(StageColors.Ink.copy(alpha = 0.05f))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(deviceName, style = tv.own.owntv.ui.theme.stageText(32, 600), color = tv.own.owntv.ui.theme.StageColors.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.local_sync_host_hint),
            style = tv.own.owntv.ui.theme.stageText(25, 400),
            color = tv.own.owntv.ui.theme.StageColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            state.pin,
            style = tv.own.owntv.ui.theme.stageText(54, 700),
            fontWeight = FontWeight.Bold,
            color = tv.own.owntv.ui.theme.stageAccent.accent,
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
                style = tv.own.owntv.ui.theme.stageText(25, 400),
                color = tv.own.owntv.ui.theme.StageColors.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Discovery, plus an address typed with the remote for the networks where discovery fails. */
@Composable
private fun FindDeviceBlock(vm: LocalSyncViewModel) {
    var manual by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
        Text(stringResource(R.string.local_sync_find_hint), style = stageText(18, 400), color = StageColors.Muted)
        Spacer(Modifier.height(14.mpx))
        if (vm.found.isEmpty()) {
            Text(stringResource(R.string.local_sync_searching), style = stageText(18, 400), color = StageColors.Muted)
        } else {
            vm.found.forEach { device ->
                // A device already paired says so instead of showing an address the user has no use
                // for, and opens its actions rather than asking for a PIN it does not need.
                val known = vm.pairedMatch(device)
                StagePopupOption(
                    title = device.name,
                    subtitle = if (known != null) stringResource(R.string.local_sync_already_paired) else device.address,
                    onClick = { vm.choose(device) },
                    leading = { StagePopupIcon(OwnTVIcon.PHONE) },
                )
            }
        }
        Spacer(Modifier.height(14.mpx))
        tv.own.owntv.features.settings.StageFieldRow(
            icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
            label = stringResource(R.string.local_sync_manual_address_label),
            value = manual,
            onValueChange = { manual = it },
        )
        Row(Modifier.fillMaxWidth().padding(top = 22.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx, Alignment.End)) {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.common_cancel), onClick = vm::cancel, primary = false)
            tv.own.owntv.ui.stage.StagePopupButton(
                stringResource(R.string.settings_backup_continue),
                onClick = { if (manual.isNotBlank()) vm.chooseAddress(manual.trim(), portOf(manual)) },
            )
        }
    }
}

@Composable
private fun PinBlock(vm: LocalSyncViewModel) {
    var pin by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
        Text(stringResource(R.string.local_sync_enter_pin_description), style = stageText(18, 400), color = StageColors.Muted)
        Spacer(Modifier.height(14.mpx))
        tv.own.owntv.features.settings.StageFieldRow(
            icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
            label = stringResource(R.string.local_sync_pin_label),
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(PIN_LENGTH) },
            keyboardType = KeyboardType.NumberPassword,
        )
        Row(Modifier.fillMaxWidth().padding(top = 22.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx, Alignment.End)) {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.common_cancel), onClick = vm::cancel, primary = false)
            tv.own.owntv.ui.stage.StagePopupButton(
                stringResource(R.string.local_sync_pair),
                onClick = { if (pin.length == PIN_LENGTH) vm.submitPin(pin) },
            )
        }
    }
}

@Composable
private fun DirectionBlock(vm: LocalSyncViewModel, step: LocalSyncViewModel.Step.ChooseDirection) {
    val name = step.device.name
    Column(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
        // Which device this is about. Inline, nothing said — the header above still read
        // "Local sync" while four rows offered to move data to somewhere unnamed.
        Text(name, style = stageText(38, 800), color = StageColors.Text)
        Text(
            lastSyncedText(step.device.lastSyncAt),
            style = stageText(18, 400),
            color = StageColors.Muted,
        )
        Spacer(Modifier.height(14.mpx))
        StagePopupOption(
            title = stringResource(R.string.local_sync_send_to, name),
            subtitle = stringResource(R.string.local_sync_send_description),
            onClick = { vm.chooseDirection(SyncDirection.SEND) },
            leading = { StagePopupIcon(OwnTVIcon.SEND) },
        )
        StagePopupOption(
            title = stringResource(R.string.local_sync_receive_from, name),
            subtitle = stringResource(R.string.local_sync_receive_description),
            onClick = { vm.chooseDirection(SyncDirection.RECEIVE) },
            leading = { StagePopupIcon(OwnTVIcon.DOWNLOADS) },
        )
        StagePopupOption(
            title = stringResource(R.string.local_sync_merge_with, name),
            subtitle = stringResource(R.string.local_sync_merge_description),
            onClick = { vm.chooseDirection(SyncDirection.MERGE) },
            // Two arrows say "both ways" at a glance, and it stops REFRESH meaning both
            // "find a device" and "two-way sync" on the same screen.
            leading = { StagePopupIcon(OwnTVIcon.SWAP) },
        )
        // The only destructive row here. The glyph is right; what it lacked was any sign that it is
        // not a fourth way of syncing, sitting one arrow-press under "Merge".
        StagePopupDivider()
        StagePopupOption(
            title = stringResource(R.string.local_sync_unpair),
            subtitle = stringResource(R.string.local_sync_unpair_description),
            onClick = { vm.unpair(step.device); vm.cancel() },
            danger = true,
            leading = { StagePopupIcon(OwnTVIcon.CLOSE, DestructiveRed) },
        )
        Row(Modifier.fillMaxWidth().padding(top = 22.mpx), horizontalArrangement = Arrangement.End) {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.common_cancel), onClick = vm::cancel, primary = false)
        }
    }
}

/** The same list of parts Backup & Restore offers, ticked with OK. */
@Composable
private fun SectionsBlock(vm: LocalSyncViewModel, step: LocalSyncViewModel.Step.ChooseSections) {
    var sections by remember { mutableStateOf(BackupManager.Section.entries.toSet()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
        Text(
            stringResource(
                when (step.direction) {
                    SyncDirection.SEND -> R.string.local_sync_what_to_send
                    SyncDirection.RECEIVE -> R.string.local_sync_what_to_receive
                    SyncDirection.MERGE -> R.string.local_sync_what_to_merge
                },
            ),
            style = stageText(38, 800),
            color = StageColors.Text,
        )
        Text(stringResource(R.string.local_sync_sections_hint), style = stageText(18, 400), color = StageColors.Muted)
        Spacer(Modifier.height(14.mpx))
        BackupManager.Section.entries.forEach { section ->
            OnOffOption(
                title = stringResource(section.labelRes()),
                subtitle = null,
                on = section in sections,
                onClick = { sections = if (section in sections) sections - section else sections + section },
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 22.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx, Alignment.End)) {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.common_cancel), onClick = vm::cancel, primary = false)
            tv.own.owntv.ui.stage.StagePopupButton(
                stringResource(R.string.settings_backup_continue),
                onClick = { if (sections.isNotEmpty()) vm.start(sections) },
            )
        }
    }
}

/** The dry run. Nothing has been written while this is on screen. */
@Composable
private fun ConfirmBlock(vm: LocalSyncViewModel, step: LocalSyncViewModel.Step.Confirm) {
    val preview = step.preview
    // The other device's hardware settings and engine pins: offered only when it sent some, unticked.
    var deviceSettings by remember(step.file) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
        Text(stringResource(R.string.local_sync_confirm_title), style = stageText(38, 800), color = StageColors.Text)
        if (preview.isEmpty) {
            Text(stringResource(R.string.local_sync_nothing_to_change), style = stageText(18, 400), color = StageColors.Muted)
        } else {
            Text(stringResource(R.string.local_sync_confirm_hint), style = stageText(18, 400), color = StageColors.Muted)
            Spacer(Modifier.height(14.mpx))
            Change(R.string.local_sync_change_profiles, preview.newProfiles)
            Change(R.string.local_sync_change_sources, preview.newSources)
            Change(R.string.local_sync_change_favorites, preview.newFavorites)
            Change(R.string.local_sync_change_history, preview.newHistory)
            Change(R.string.local_sync_change_resume, preview.newResume)
            Change(R.string.local_sync_change_reorder, preview.newReorder)
            Change(R.string.local_sync_change_settings, preview.changedSettings)
            Change(R.string.local_sync_change_deletions, preview.deletions)
            if (preview.hasCustomizations) {
                Text(stringResource(R.string.local_sync_change_customize), style = stageText(19, 500), color = StageColors.Text)
            }
        }
        if (preview.hasDeviceSettings) {
            Spacer(Modifier.height(10.mpx))
            OnOffOption(
                title = stringResource(R.string.settings_backup_device_settings),
                subtitle = stringResource(R.string.settings_backup_device_settings_desc),
                on = deviceSettings,
                onClick = { deviceSettings = !deviceSettings },
            )
        }
        if (step.direction == SyncDirection.MERGE) {
            Text(stringResource(R.string.local_sync_merge_note), style = stageText(18, 400), color = StageColors.Muted)
        }
        Row(Modifier.fillMaxWidth().padding(top = 22.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx, Alignment.End)) {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.common_cancel), onClick = vm::cancel, primary = false)
            tv.own.owntv.ui.stage.StagePopupButton(
                stringResource(R.string.local_sync_apply),
                onClick = { if (!preview.isEmpty) vm.confirm(deviceSettings) },
            )
        }
    }
}

@Composable
private fun ResultBlock(vm: LocalSyncViewModel, step: LocalSyncViewModel.Step.Result) {
    Column(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
        Text(stringResource(R.string.local_sync_done), style = stageText(38, 800), color = StageColors.Text)
        step.received?.let {
            Text(stringResource(R.string.local_sync_received_items, it.items), style = stageText(18, 400), color = StageColors.Muted)
        }
        if (step.sent) {
            Text(stringResource(R.string.local_sync_sent), style = stageText(18, 400), color = StageColors.Muted)
        }
        Row(Modifier.fillMaxWidth().padding(top = 22.mpx), horizontalArrangement = Arrangement.End) {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.common_done), onClick = vm::cancel, primary = false)
        }
    }
}

/** A popup row ticked with OK: the backup glyph, then "On" in accent or "Off" muted on the right. */
@Composable
private fun OnOffOption(title: String, subtitle: String?, on: Boolean, onClick: () -> Unit) {
    StagePopupOption(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = { StagePopupIcon(OwnTVIcon.BACKUP) },
        trailing = {
            Text(
                stringResource(if (on) R.string.common_on else R.string.common_off),
                style = stageText(18, 700),
                color = if (on) stageAccent.accent else StageColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** One counted change; a nought is left out rather than listed as nothing. */
@Composable
private fun Change(labelRes: Int, count: Int) {
    if (count <= 0) return
    Text(
        stringResource(labelRes, count),
        style = stageText(19, 500),
        color = StageColors.Text,
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

/** The destructive tone: the Unpair mark, and the error text's colour. */
private val DestructiveRed = Color(0xFFEF4444)

/**
 * Wide enough for [QR_SIZE] plus the card's padding, and no wider — the list beside it is what the
 * screen is for. The QR keeps its size: it is read by a phone camera from across the room.
 */
private val HOSTING_CARD_WIDTH = 250.dp
private val QR_SIZE = 188.dp

private const val PIN_LENGTH = 6
