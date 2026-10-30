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
import tv.own.owntv.R
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.mpx

/**
 * Advanced TMDB access: the user's own API key and worker server URL. Private for the automation API
 * ([SensitiveScreenMarker]): it shows and edits the key.
 */
@Composable
internal fun AdvancedMetadataPopup(
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
        title = stringResource(R.string.settings_metadata_remote_advanced),
        body = stringResource(R.string.settings_metadata_server_description),
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_metadata_clear_advanced_title), onClick = onRemove, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_save), onClick = onSave, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_metadata_key_from_phone),
            subtitle = stringResource(R.string.settings_metadata_key_from_phone_desc),
            onClick = onRemote,
            modifier = Modifier.focusRequester(firstFocus),
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.SHARE) },
        )
        Column(Modifier.padding(top = 10.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
            tv.own.owntv.features.settings.StageFieldRow(
                icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
                label = stringResource(R.string.settings_tmdb_api_key),
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
                placeholder = "https://your-worker.example.workers.dev",
            )
        }
    }
}
