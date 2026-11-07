package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.stage.StageSwitch
import tv.own.owntv.ui.theme.mpx

/**
 * A playlist's "Clean channel names" popup, opened from its row on the Playlists page: the switch,
 * then the user's own words to remove. Focus starts on the switch; Back cancels. Saving stores the
 * choice and, when the shown names change, hands the playlist to [onNeedsResync] so the next sync
 * rewrites them (cleaned, or the provider's originals again when switched off).
 */
@Composable
internal fun ChannelNameCleanupHost(
    source: SourceEntity,
    onNeedsResync: (SourceEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val vm: ChannelNameCleanupViewModel = koinViewModel()
    ChannelNameCleanupDialog(
        source = source,
        onSave = { enabled, text -> vm.save(source, enabled, text, onNeedsResync); onDismiss() },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ChannelNameCleanupDialog(
    source: SourceEntity,
    onSave: (enabled: Boolean, customText: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var enabled by remember { mutableStateOf(source.cleanChannelNames) }
    var customText by remember { mutableStateOf(source.channelNameStrip.orEmpty()) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); runCatching { first.requestFocus() } }
    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_sources_clean_names_title, source.name),
        body = stringResource(R.string.settings_sources_clean_names_description),
        width = 820.mpx,
        buttons = {
            StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            StageButton(stringResource(R.string.common_save), onClick = { onSave(enabled, customText) }, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        StagePopupOption(
            title = stringResource(R.string.settings_sources_clean_names_toggle),
            subtitle = stringResource(R.string.settings_sources_clean_names_example),
            onClick = { enabled = !enabled },
            modifier = Modifier.focusRequester(first),
            trailing = { StageSwitch(enabled) },
        )
        StageFieldRow(
            icon = OwnTVIcon.PENCIL,
            label = stringResource(R.string.settings_sources_clean_names_custom),
            value = customText,
            onValueChange = { customText = it },
            placeholder = stringResource(R.string.settings_sources_clean_names_custom_hint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
