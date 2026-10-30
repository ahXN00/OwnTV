package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.ui.theme.mpx

/**
 * The whole sign-in on one panel: username, password, "Stay signed in", and the two
 * optional API fields inline. Deliberately the same four inputs, in the same order, as the remote
 * companion page — so filling it in on the TV and filling it in on a browser look like one feature.
 *
 * Scrollable and on the popup's small type scale, because five inputs plus a toggle overflow a TV
 * panel once the keyboard claims the lower half of the screen. Fields are hoisted so the caller can
 * pre-fill them from the Remote hand-over. Private for the automation API ([SensitiveScreenMarker]): it is
 * a login, so its keys must not type the username or password.
 */
@Composable
internal fun OpenSubtitlesSignInDialog(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    staySignedIn: Boolean,
    onStayChange: (Boolean) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    SensitiveScreenMarker()
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { fieldFocus.requestFocus() } }
    // One Stage popup; it scrolls and moves above the TV keyboard, so Sign in stays reachable while typing.
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.player_subtitles_sign_in_title),
        body = stringResource(R.string.player_subtitles_sign_in_to_use),
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.player_subtitles_sign_in), onClick = onSubmit, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
            tv.own.owntv.features.settings.StageFieldRow(
                icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                label = stringResource(R.string.player_subtitles_username),
                value = username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
            )
            tv.own.owntv.features.settings.StageFieldRow(
                icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                label = stringResource(R.string.player_subtitles_password),
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                password = true,
            )
        }
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.player_subtitles_stay_signed_in), subtitle = stringResource(R.string.player_subtitles_session),
            onClick = { onStayChange(!staySignedIn) }, modifier = Modifier.padding(top = 8.mpx),
            trailing = { tv.own.owntv.ui.stage.StageSwitch(staySignedIn) },
        )
        // Optional, and labelled as such: almost nobody has their own key, and a required-looking empty
        // field right above Sign in reads like something is missing.
        tv.own.owntv.ui.stage.StagePopupDivider()
        tv.own.owntv.ui.stage.StagePopupLabel(stringResource(R.string.settings_open_subtitles_advanced).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]))
        Text(stringResource(R.string.settings_open_subtitles_advanced_description), style = tv.own.owntv.ui.theme.stageText(15, 500), color = tv.own.owntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(bottom = 12.mpx))
        Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
            tv.own.owntv.features.settings.StageFieldRow(
                icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                label = stringResource(R.string.settings_open_subtitles_api_key),
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.settings_metadata_optional),
            )
            tv.own.owntv.features.settings.StageFieldRow(
                icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                label = stringResource(R.string.settings_worker_server_url),
                value = serverUrl,
                onValueChange = onServerUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.settings_metadata_optional),
            )
            Text(stringResource(R.string.settings_open_subtitles_access_priority), style = tv.own.owntv.ui.theme.stageText(15, 500), color = tv.own.owntv.ui.theme.StageColors.Muted)
        }
    }
}
