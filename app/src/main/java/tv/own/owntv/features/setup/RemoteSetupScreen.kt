package tv.own.owntv.features.setup

import tv.own.owntv.ui.theme.stageTone
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.companion.CompanionPayload
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.settings.SettingPanelHeading
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.companionLockedText
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * Add a source › From your phone: the small LAN web server starts as the page opens;
 * the panel shows the QR, the address and the PIN. A submission hands off to the Type it here
 * form, pre-filled, where the user presses Start import ([onPayloadReceived]). New PIN restarts the server
 * with a fresh PIN. The listener stops when this page leaves composition.
 */
@Composable
fun RemoteSetupScreen(
    state: CompanionServerState,
    payloads: Flow<CompanionPayload>,
    onStartListener: (port: Int) -> Unit,
    onStopListener: () -> Unit,
    onPayloadReceived: (CompanionPayload) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SensitiveScreenMarker()
    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (state == CompanionServerState.Idle) onStartListener(CompanionLink.DEFAULT_PORT)
        runCatching { rowsFocus.requestFocus() }
    }
    // A remote submission hands off to the Type it here form; the host navigates away (which stops the server).
    LaunchedEffect(payloads) { payloads.collect(onPayloadReceived) }
    DisposableEffect(Unit) { onDispose { onStopListener() } }

    // The heading is drawn above the QR by [RemotePanel], so the help itself carries none.
    val help = SettingHelp(
        title = "",
        text = stringResource(R.string.settings_remote_details_note),
        hints = listOf(stringResource(R.string.common_back) to stringResource(R.string.settings_key_stop_back)),
    )
    val panel: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = { RemotePanel(state) }
    StageFullPage(
        parents = listOf(stringResource(R.string.setup_add_source)),
        title = stringResource(R.string.setup_from_phone),
        count = "",
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
        settingsRoot = false,
        panelTop = panel,
    ) {
        val (line, problem) = when (state) {
            CompanionServerState.Idle, CompanionServerState.Starting -> stringResource(R.string.setup_opening) to false
            is CompanionServerState.Listening -> stringResource(R.string.settings_remote_nothing_yet) to false
            is CompanionServerState.Failed -> state.failure.displayText() to true
            CompanionServerState.Locked -> companionLockedText() to true
        }
        StageSettingRow(
            icon = OwnTVIcon.PHONE,
            title = stringResource(R.string.settings_remote_waiting),
            desc = line,
            value = if (problem) null else SettingValue.Custom {
                Text("…", style = stageText(18, 700), color = StageColors.Dim)
            },
            onClick = {},
            help = help,
        )
        StageSettingRow(
            icon = OwnTVIcon.REFRESH,
            title = stringResource(R.string.settings_remote_new_pin),
            desc = stringResource(R.string.settings_line_remote_new_pin),
            value = null,
            // Restarting mints a fresh PIN — also the way out of a failed or locked server.
            onClick = { onStopListener(); onStartListener(CompanionLink.DEFAULT_PORT) },
            help = help,
        )
    }
}

/** The panel: SCAN WITH YOUR PHONE, the QR beside "or open" + the address + the PIN, then the note. */
@Composable
private fun RemotePanel(state: CompanionServerState) {
    SettingPanelHeading(stringResource(R.string.settings_remote_scan))
    val listening = state as? CompanionServerState.Listening ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalAlignment = Alignment.CenterVertically) {
        listening.qr?.let { qr ->
            Box(Modifier.size(200.mpx).clip(RoundedCornerShape(18.mpx)).background(Color.White).padding(12.mpx)) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.common_qr_code_companion_url),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Column {
            Text(stringResource(R.string.settings_remote_or_open), style = stageText(17, 500), color = PanelText)
            listening.urls.forEach { url ->
                Text(url, style = stageText(21, 800), color = StageColors.Text, modifier = Modifier.padding(top = 4.mpx))
            }
            Row(Modifier.padding(top = 6.mpx), horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.local_sync_pin_label), style = stageText(17, 500), color = PanelText)
                Text(listening.pin, style = stageText(30, 800, 0.4.em), color = stageAccent.accent)
            }
        }
    }
    Box(Modifier.size(18.mpx))
}

private val PanelText: Color @Composable get() = stageTone(Color(0xFFD3DCD8), StageColors.Body)
