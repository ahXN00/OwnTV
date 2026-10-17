package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.ui.theme.mpx

// Settings › App › App startup: the startup choice and the channel picker for "Specific channel".

@Composable
private fun StartupChannelPickerDialog(
    query: String,
    channels: List<tv.own.owntv.core.database.entity.ChannelEntity>,
    selected: tv.own.owntv.core.settings.StartupChannelRef?,
    onQueryChange: (String) -> Unit,
    onSelect: (tv.own.owntv.core.database.entity.ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    // A long list picked from: in the page's panel on a settings page, a Stage popup elsewhere.
    val list: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        StartupChannelList(query, channels, selected, onQueryChange, onSelect)
    }
    if (tv.own.owntv.features.settings.panelEditor(onDismiss) { list() }) return
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = stringResource(R.string.settings_startup_specific_channel), scroll = false, content = list)
}

/** The search field, then the channels as radio rows (number, name); focus starts in the search. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StartupChannelList(
    query: String,
    channels: List<tv.own.owntv.core.database.entity.ChannelEntity>,
    selected: tv.own.owntv.core.settings.StartupChannelRef?,
    onQueryChange: (String) -> Unit,
    onSelect: (tv.own.owntv.core.database.entity.ChannelEntity) -> Unit,
) {
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); runCatching { searchFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StageSearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.common_search_hint),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.mpx).focusRequester(searchFocus),
    )
    if (channels.isEmpty()) {
        Text(
            if (query.isBlank()) stringResource(R.string.content_no_channels_here) else stringResource(R.string.content_no_channels_found, query),
            style = tv.own.owntv.ui.theme.stageText(17, 500), color = tv.own.owntv.ui.theme.StageColors.Muted,
            modifier = Modifier.padding(vertical = 20.mpx),
        )
        return
    }
    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.mpx)) {
        items(channels, key = { it.id }) { channel ->
            val isSelected = selected?.let { ref ->
                ref.sourceId == channel.sourceId &&
                    if (!ref.remoteId.isNullOrBlank() && !channel.remoteId.isNullOrBlank()) ref.remoteId == channel.remoteId else ref.name == channel.name
            } == true
            tv.own.owntv.ui.stage.StageSurface(
                onClick = { onSelect(channel) },
                radius = 14.mpx,
                focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
                modifier = Modifier.fillMaxWidth().height(52.mpx),
            ) { focused ->
                Row(Modifier.padding(horizontal = 14.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                    tv.own.owntv.features.settings.StageRadio(isSelected)
                    Text(
                        channel.number?.toString().orEmpty(), style = tv.own.owntv.ui.theme.stageText(16, 700),
                        color = tv.own.owntv.ui.theme.StageColors.Dim, modifier = Modifier.width(54.mpx), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        channel.name, style = tv.own.owntv.ui.theme.stageText(18, 600),
                        color = if (isSelected || focused) tv.own.owntv.ui.theme.StageColors.Text else tv.own.owntv.ui.theme.StageColors.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun startupLabel(mode: tv.own.owntv.core.settings.StartupMode): String = stringResource(
    when (mode) {
        tv.own.owntv.core.settings.StartupMode.HOME -> R.string.settings_startup_home
        tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL -> R.string.settings_startup_last_channel
        tv.own.owntv.core.settings.StartupMode.FAVORITES -> R.string.settings_startup_favorites
        tv.own.owntv.core.settings.StartupMode.SPECIFIC_CHANNEL -> R.string.settings_startup_specific_channel
    },
)

@Composable
internal fun StartupDialogHost(settingsVm: SettingsViewModel, startupMode: tv.own.owntv.core.settings.StartupMode, onClose: () -> Unit, onPickChannel: () -> Unit) {
    tv.own.owntv.features.settings.PickerDialog(
        title = stringResource(R.string.settings_app_startup_dialog),
        options = tv.own.owntv.core.settings.StartupMode.entries.map { it.name to startupLabel(it) },
        selected = startupMode.name,
        onSelect = {
            val mode = tv.own.owntv.core.settings.StartupMode.valueOf(it)
            onClose()
            if (mode == tv.own.owntv.core.settings.StartupMode.SPECIFIC_CHANNEL) {
                settingsVm.setStartupChannelQuery("")
                settingsVm.refreshStartupChannelPicker()
                onPickChannel()
            } else {
                settingsVm.setStartupMode(mode)
            }
        },
        onDismiss = { onClose() },
    )
}

@Composable
internal fun StartupChannelPickerHost(settingsVm: SettingsViewModel, startupChannel: tv.own.owntv.core.settings.StartupChannelRef?, onClose: () -> Unit) {
    val startupChannelQuery by settingsVm.startupChannelQuery.collectAsStateWithLifecycle()
    val startupChannelResults by settingsVm.startupChannelResults.collectAsStateWithLifecycle()
    StartupChannelPickerDialog(
        query = startupChannelQuery,
        channels = startupChannelResults,
        selected = startupChannel,
        onQueryChange = settingsVm::setStartupChannelQuery,
        onSelect = {
            settingsVm.setStartupChannel(it)
            onClose()
        },
        onDismiss = { onClose() },
    )
}
