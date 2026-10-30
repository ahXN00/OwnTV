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
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.mpx

/**
 * Advanced access for an already signed-in account. Signing in carries its own copy of these fields.
 * Private for the automation API ([SensitiveScreenMarker]): it shows and edits the API key and server URL.
 */
@Composable
internal fun OpenSubtitlesApiPopup(
    key: String,
    url: String,
    onKeyChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onRemote: () -> Unit,
    onRemove: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    SensitiveScreenMarker()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_open_subtitles_advanced),
        body = stringResource(R.string.settings_open_subtitles_advanced_description),
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_remove_custom_access), onClick = onRemove, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_save), onClick = onSave, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_open_subtitles_setup_remote),
            subtitle = stringResource(R.string.settings_metadata_key_from_phone_desc),
            onClick = onRemote,
            modifier = Modifier.focusRequester(firstFocus),
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.SHARE) },
        )
        Column(Modifier.padding(top = 10.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
            tv.own.owntv.features.settings.StageFieldRow(
                icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                label = stringResource(R.string.settings_open_subtitles_api_key),
                value = key,
                onValueChange = onKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.settings_metadata_optional),
            )
            tv.own.owntv.features.settings.StageFieldRow(
                icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                label = stringResource(R.string.settings_worker_server_url),
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.settings_metadata_optional),
            )
            Text(stringResource(R.string.settings_open_subtitles_access_priority), style = tv.own.owntv.ui.theme.stageText(15, 500), color = tv.own.owntv.ui.theme.StageColors.Muted)
        }
    }
}
