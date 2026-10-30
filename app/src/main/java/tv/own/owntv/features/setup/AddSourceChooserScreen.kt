package tv.own.owntv.features.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * Playlists › Add a source: **From your phone** (fill the form on another device over the LAN)
 * or **Type it here** (Xtream / M3U / Stalker with the remote), each explained in the panel.
 */
@Composable
fun AddSourceChooserScreen(
    onRemote: () -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SensitiveScreenMarker()
    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rowsFocus.requestFocus() } }
    val playlists = stringResource(R.string.settings_playlists)
    val hints = listOf(
        stringResource(R.string.common_ok) to stringResource(R.string.settings_key_open),
        stringResource(R.string.common_back) to playlists,
    )
    StageFullPage(
        parents = listOf(playlists),
        title = stringResource(R.string.setup_add_source),
        count = "",
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
        settingsRoot = false,
    ) {
        val phone = stringResource(R.string.setup_from_phone)
        StageSettingRow(
            icon = OwnTVIcon.PHONE,
            title = phone,
            desc = stringResource(R.string.settings_line_add_from_phone),
            value = SettingValue.Opens(null),
            onClick = onRemote,
            help = SettingHelp(phone, stringResource(R.string.settings_help_add_from_phone), hints = hints),
        )
        val here = stringResource(R.string.settings_add_type_here)
        StageSettingRow(
            icon = OwnTVIcon.PENCIL,
            title = here,
            desc = stringResource(R.string.settings_line_add_type_here),
            value = SettingValue.Opens(null),
            onClick = onManual,
            help = SettingHelp(here, stringResource(R.string.settings_help_add_type_here), hints = hints),
        )
    }
}
