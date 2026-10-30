package tv.own.owntv.features.setup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.features.automation.AutomationSetupStatus
import tv.own.owntv.features.automation.AutomationSetupViewModel
import tv.own.owntv.features.automation.PinBlock
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.settings.SettingPanelHeading
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * Set up by Automation: the API comes on as the page opens, and the panel shows the TV's address and a
 * PIN for a computer on the home network to pair with. Once one has, the page waits for it to finish
 * setup ([AutomationSetupViewModel]), which takes the TV to Home. [onHere] goes on with the normal
 * wizard, [onCancel] (also Back) back to its first screen; either way pairing closes, the paired device
 * is forgotten, and the API goes off again if it was off before. Leaving the app closes the PIN, and the
 * whole visit when nothing has paired yet; coming back opens it again.
 */
@Composable
internal fun SetupAutomationStep(onHere: () -> Unit, onCancel: () -> Unit, vm: AutomationSetupViewModel = koinViewModel()) {
    SensitiveScreenMarker()
    val status by vm.status.collectAsStateWithLifecycle()
    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rowsFocus.requestFocus() } }
    LifecycleStartEffect(vm) {
        vm.start()
        onStopOrDispose { vm.away() }
    }
    val cancel = { vm.close(); onCancel() }

    val help = SettingHelp(
        title = "",
        text = stringResource(R.string.setup_automation_note),
        hints = listOf(stringResource(R.string.common_back) to stringResource(R.string.settings_key_stop_back)),
    )
    val panel: @Composable ColumnScope.() -> Unit = { SetupAutomationPanel(status, onExpired = vm::expire) }
    StageFullPage(
        parents = listOf(stringResource(R.string.setup_set_up_owntv)),
        title = stringResource(R.string.setup_automation_action),
        count = "",
        onBack = cancel,
        rowsFocus = rowsFocus,
        settingsRoot = false,
        panelTop = panel,
    ) {
        // One row whose job changes once a device has paired, so focus stays where it is.
        val paired = status is AutomationSetupStatus.Paired
        StageSettingRow(
            icon = if (paired) OwnTVIcon.PERSON else OwnTVIcon.REFRESH,
            title = stringResource(if (paired) R.string.setup_automation_here else R.string.settings_remote_new_pin),
            desc = stringResource(if (paired) R.string.setup_automation_here_line else R.string.settings_line_remote_new_pin),
            value = null,
            onClick = if (paired) ({ vm.close(); onHere() }) else vm::newPin,
            help = help,
        )
        StageSettingRow(
            icon = OwnTVIcon.CLOSE,
            title = stringResource(R.string.common_cancel),
            desc = stringResource(R.string.setup_automation_cancel_line),
            value = null,
            onClick = cancel,
            help = help,
        )
    }
}

/** Pair from your computer: the address and the PIN, or what happened instead. */
@Composable
private fun SetupAutomationPanel(status: AutomationSetupStatus, onExpired: () -> Unit) {
    SettingPanelHeading(stringResource(R.string.setup_automation_heading))
    when (status) {
        AutomationSetupStatus.Starting -> PanelLine(stringResource(R.string.automation_starting))
        is AutomationSetupStatus.Failed -> PanelLine(stringResource(R.string.automation_failed, status.message))
        AutomationSetupStatus.Expired -> PanelLine(stringResource(R.string.setup_automation_expired))
        is AutomationSetupStatus.Waiting -> {
            PanelLine(stringResource(R.string.setup_automation_waiting))
            status.urls.forEach { url ->
                Text(url, style = stageText(24, 800), color = StageColors.Text, modifier = Modifier.padding(top = 6.mpx))
            }
            PinBlock(status.pairing, onExpired)
        }
        is AutomationSetupStatus.Paired -> {
            Text(stringResource(R.string.setup_automation_paired, status.deviceName), style = stageText(26, 800), color = StageColors.Text)
            PanelLine(stringResource(R.string.setup_automation_paired_line))
        }
    }
}

@Composable
private fun PanelLine(text: String) {
    Text(text, style = stageText(18, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 6.mpx))
}
