package tv.own.owntv.features.automation

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import java.text.DateFormat
import java.util.Date

/** More › Automation: the LAN API that lets paired scripts manage playlists. */
@Composable
fun AutomationScreen(entry: FocusRequester, modifier: Modifier = Modifier, vm: AutomationViewModel = koinViewModel()) {
    SensitiveScreenMarker()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pairing by vm.pairingState.collectAsStateWithLifecycle()
    val server by vm.serverState.collectAsStateWithLifecycle()
    val confirmRemove by vm.confirmRemove.collectAsStateWithLifecycle()

    Column(
        modifier.verticalScroll(rememberScrollState()).focusGroup(),
        verticalArrangement = Arrangement.spacedBy(6.mpx),
    ) {
        Text(stringResource(R.string.automation_title), style = stageText(42, 800), color = StageColors.Text)
        Text(
            stringResource(R.string.automation_description),
            style = stageText(18, 500), color = StageColors.Muted,
            modifier = Modifier.padding(top = 10.mpx, bottom = 20.mpx),
        )
        StageSettingRow(
            icon = OwnTVIcon.NETWORK,
            title = stringResource(R.string.automation_switch),
            desc = serverText(settings.enabled, server),
            value = SettingValue.Switch(settings.enabled),
            onClick = { vm.setEnabled(!settings.enabled) },
            modifier = Modifier.focusRequester(entry),
        )
        StageSettingRow(
            icon = OwnTVIcon.PHONE,
            title = stringResource(R.string.automation_pair),
            desc = stringResource(R.string.automation_pair_desc),
            value = SettingValue.Action(stringResource(if (pairing == null) R.string.automation_pair_action else R.string.automation_pair_cancel)),
            onClick = vm::togglePairing,
            enabled = settings.enabled,
        )
        pairing?.let { PinBlock(it, onExpired = vm::closePairing) }
        StageSettingRow(
            icon = OwnTVIcon.SHARE,
            title = stringResource(R.string.automation_tailscale),
            desc = stringResource(R.string.automation_tailscale_desc),
            value = SettingValue.Switch(settings.allowTailscale),
            onClick = { vm.setAllowTailscale(!settings.allowTailscale) },
        )
        DeviceRows(settings.devices, confirmRemove, onRemove = vm::remove, onRemoveAll = vm::removeAll)
    }
}

@Composable
private fun DeviceRows(devices: List<PairedDevice>, confirmRemove: String?, onRemove: (String) -> Unit, onRemoveAll: () -> Unit) {
    Text(
        stringResource(R.string.automation_devices),
        style = stageText(22, 700), color = StageColors.Text,
        modifier = Modifier.padding(top = 24.mpx, bottom = 6.mpx),
    )
    if (devices.isEmpty()) {
        Text(stringResource(R.string.automation_no_devices), style = stageText(18, 500), color = StageColors.Muted)
        return
    }
    val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
    devices.forEach { device ->
        StageSettingRow(
            icon = OwnTVIcon.PHONE,
            title = device.name,
            desc = stringResource(R.string.automation_device_desc, device.address, dates.format(Date(device.pairedAt))),
            value = SettingValue.Action(
                stringResource(if (confirmRemove == device.id) R.string.automation_remove_confirm else R.string.automation_remove),
            ),
            onClick = { onRemove(device.id) },
        )
    }
    if (devices.size > 1) {
        StageSettingRow(
            icon = OwnTVIcon.WARNING,
            title = stringResource(R.string.automation_remove_all),
            desc = null,
            value = null,
            onLongClick = onRemoveAll,
            onClick = {},
        )
    }
}
