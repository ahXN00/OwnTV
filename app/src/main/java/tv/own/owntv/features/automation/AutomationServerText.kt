package tv.own.owntv.features.automation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R

@Composable
internal fun serverText(enabled: Boolean, server: AutomationServer.State): String = when {
    !enabled -> stringResource(R.string.automation_off)
    server is AutomationServer.State.Listening ->
        stringResource(R.string.automation_listening, server.urls.joinToString("  ").ifEmpty { "port ${AutomationServer.PORT}" })
    server is AutomationServer.State.Failed -> stringResource(R.string.automation_failed, server.message)
    else -> stringResource(R.string.automation_starting)
}
