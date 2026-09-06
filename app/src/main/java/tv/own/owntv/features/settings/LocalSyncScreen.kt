package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

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
    val colors = OwnTVTheme.colors
    val vm: LocalSyncViewModel = koinViewModel()
    val paired by vm.paired.collectAsStateWithLifecycle()
    val hosting by vm.hosting.collectAsStateWithLifecycle()

    val firstFocus = remember { FocusRequester() }
    // Deliberately NOT started on entry. Which device hosts is the user's choice, on both apps and in
    // the same words — a television that quietly opened a listener the moment you looked at the
    // screen was making that choice for you, and gave you no way to unmake it.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    // The listener runs while this screen does, and not a moment longer.
    DisposableEffect(Unit) { onDispose { vm.stopHosting() } }
    BackHandler { if (vm.step != null) vm.cancel() else onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val listening = hosting as? CompanionServerState.Listening
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Header(stringResource(R.string.local_sync_title), onBack) }
            if (listening != null) SyncModePill()
        }
        Spacer(Modifier.height(12.dp))

        if (vm.step == null) {
            // One state, one switch. Sync mode is what makes this device reachable at all — every
            // other action on this screen, in either direction, needs the FAR device to have it on
            // too, which is why it is the first row and why the failure message names where to find
            // it.
            Row2(
                icon = OwnTVIcon.BACKUP,
                title = stringResource(R.string.local_sync_mode),
                desc = stringResource(R.string.local_sync_mode_description),
                chip = stringResource(if (listening != null) R.string.common_on else R.string.common_off),
                primaryChip = listening != null,
                modifier = Modifier.focusRequester(firstFocus),
                onClick = { if (listening != null) vm.stopHosting() else vm.startHosting() },
            )
            Row2(
                icon = OwnTVIcon.REFRESH,
                title = stringResource(R.string.local_sync_connect),
                desc = stringResource(R.string.local_sync_connect_description),
                onClick = vm::beginPairing,
            )
            listening?.let {
                Spacer(Modifier.height(12.dp))
                HostingBlock(it, vm.deviceName)
            }
            if (paired.isNotEmpty()) Spacer(Modifier.height(12.dp))
            paired.forEach { device ->
                Row2(
                    icon = OwnTVIcon.BACKUP,
                    title = device.name,
                    desc = lastSyncedText(device.lastSyncAt),
                    chevron = true,
                    onClick = { vm.chooseDevice(device) },
                )
            }
        }

        // The steps are drawn below the list rather than replacing the screen, so the PIN on the
        // television stays readable while somebody walks across the room with the phone.
        when (val step = vm.step) {
            null -> Unit
            is LocalSyncViewModel.Step.FindDevice -> FindDeviceBlock(vm)
            is LocalSyncViewModel.Step.EnterPin -> PinBlock(vm)
            is LocalSyncViewModel.Step.ChooseDirection -> DirectionBlock(vm, step)
            is LocalSyncViewModel.Step.ChooseSections -> SectionsBlock(vm, step)
            is LocalSyncViewModel.Step.Confirm -> ConfirmBlock(vm, step)
            is LocalSyncViewModel.Step.Result -> ResultBlock(vm, step)
        }

        vm.error?.let { failure ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(failure.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFEF4444),
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

/**
 * The badge that says this television is reachable right now.
 *
 * Sync mode is the one piece of state here with a consequence off the screen — a listening port and
 * an announcement on the network — so it is said plainly rather than left to be inferred from a row.
 */
@Composable
private fun SyncModePill() {
    val colors = OwnTVTheme.colors
    Text(
        text = stringResource(R.string.local_sync_mode_pill),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onPrimaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
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

/** The PIN, the QR and the address — what the phone needs to reach this television. */
@Composable
private fun HostingBlock(state: CompanionServerState.Listening, deviceName: String) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.size(188.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(9.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(10.dp))
        }
        state.urls.forEach { url ->
            Text(url, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
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
                Row2(
                    icon = OwnTVIcon.BACKUP,
                    title = device.name,
                    desc = device.address,
                    onClick = { vm.chooseAddress(device.address, device.port) },
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row2(
            icon = OwnTVIcon.BACKUP,
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
            icon = OwnTVIcon.REFRESH,
            title = stringResource(R.string.local_sync_merge_with, name),
            desc = stringResource(R.string.local_sync_merge_description),
            onClick = { vm.chooseDirection(SyncDirection.MERGE) },
        )
        Row2(
            icon = OwnTVIcon.CLOSE,
            title = stringResource(R.string.local_sync_unpair),
            desc = stringResource(R.string.local_sync_unpair_description),
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
        if (step.direction == SyncDirection.MERGE) {
            Text(stringResource(R.string.local_sync_merge_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnTVButton(
                stringResource(R.string.local_sync_apply),
                onClick = { if (!preview.isEmpty) vm.confirm() },
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

private const val PIN_LENGTH = 6
