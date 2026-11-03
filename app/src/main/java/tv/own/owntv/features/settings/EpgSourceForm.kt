package tv.own.owntv.features.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.ui.components.DayStepperDialog
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.ui.components.rememberDialogFocusRestore
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.trapAllFocusExit

@Composable
internal fun EpgSourceForm(
    initial: EpgSource?,
    initialAutoRefresh: EpgRefresh,
    initialUseLogos: Boolean,
    loadPlaylistOptions: suspend () -> List<EpgSourcesViewModel.PlaylistEpg>,
    onSave: (name: String, url: String, userAgent: String?, autoRefresh: EpgRefresh, useLogos: Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var ua by remember { mutableStateOf(initial?.userAgent ?: "") }
    var autoRefresh by remember { mutableStateOf(initialAutoRefresh) }
    var useLogos by remember { mutableStateOf(initialUseLogos) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showAutoRefreshPicker by remember { mutableStateOf(false) }
    var showManualDays by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    // Both pickers open from rows further down the form; closing one returns focus to its row.
    val scrollState = rememberScrollState()
    val dialogFocus = rememberDialogFocusRestore(showPlaylistPicker || showAutoRefreshPicker, scrollState)
    val autoRefreshRowFocus = remember { FocusRequester() }
    val fillFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }

    // The fields as rows, Add & sync in the panel (▶ from any row).
    val parent = stringResource(R.string.settings_epg_sources_title)
    val ok = stringResource(R.string.common_ok)
    val back = stringResource(R.string.common_back)
    val fieldHelp = stringResource(R.string.settings_form_field_help)
    val submitLabel = stringResource(if (initial == null) R.string.settings_epg_sources_add_sync else R.string.settings_epg_sources_save_sync)
    val submitButton: @Composable () -> Unit = {
        if (url.isNotBlank()) {
            StageActionColumn(listOf(StageAction(OwnTVIcon.DOWNLOADS, submitLabel, { onSave(name, url, ua, autoRefresh, useLogos) })), back = null, first = submitFocus)
        }
    }
    val toSubmit = Modifier.focusProperties { right = submitFocus }
    // ▶ reaches the panel's button from any row; said in the keys while the button is there.
    val submitHint = if (url.isNotBlank()) "▶" to submitLabel else null
    fun keys(vararg k: Pair<String, String>) = listOfNotNull(*k.dropLast(1).toTypedArray(), submitHint, k.last())
    StageFullPage(
        parents = listOf(parent),
        title = stringResource(if (initial == null) R.string.content_epg_add else R.string.settings_epg_sources_edit),
        count = "",
        onBack = onCancel,
        modifier = modifier,
        scroll = scrollState,
        rowsFocus = firstFocus,
        settingsRoot = false,
    ) {
        val nameLabel = stringResource(R.string.settings_epg_sources_name)
        StageFieldRow(
            OwnTVIcon.PENCIL, nameLabel, name, { name = it },
            SettingHelp(nameLabel, stringResource(R.string.settings_help_epg_name) + " " + fieldHelp, extra = submitButton),
            placeholder = stringResource(R.string.settings_epg_sources_name_hint), backLabel = parent, submitHint = submitHint, modifier = toSubmit,
        )
        val urlLabel = stringResource(R.string.settings_epg_sources_url)
        StageFieldRow(
            OwnTVIcon.PENCIL, urlLabel, url, { url = it },
            SettingHelp(urlLabel, fieldHelp, extra = submitButton),
            placeholder = stringResource(R.string.settings_epg_sources_url_hint), keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            backLabel = parent, submitHint = submitHint, modifier = toSubmit,
        )
        val fillTitle = stringResource(R.string.settings_epg_sources_fill_playlist)
        val fillLine = stringResource(R.string.settings_line_fill_from_playlist)
        StageSettingRow(
            icon = OwnTVIcon.LIST,
            title = fillTitle,
            desc = fillLine,
            value = SettingValue.Opens(null),
            onClick = { dialogFocus.value = fillFocus; showPlaylistPicker = true },
            modifier = Modifier.focusRequester(fillFocus).then(toSubmit),
            help = SettingHelp(fillTitle, fillLine, hints = keys(ok to stringResource(R.string.settings_key_open), back to parent), extra = submitButton),
        )
        val uaLabel = stringResource(R.string.settings_epg_sources_user_agent)
        StageFieldRow(
            OwnTVIcon.PENCIL, uaLabel, ua, { ua = it },
            SettingHelp(uaLabel, fieldHelp, extra = submitButton),
            placeholder = stringResource(R.string.settings_epg_sources_user_agent_hint), backLabel = parent, submitHint = submitHint, modifier = toSubmit,
        )
        val refreshTitle = stringResource(R.string.settings_epg_sources_auto_refresh_title)
        StageSettingRow(
            icon = OwnTVIcon.REFRESH,
            title = refreshTitle,
            desc = stringResource(R.string.settings_line_epg_auto_refresh),
            value = SettingValue.Choice(epgRefreshLabel(autoRefresh)),
            onClick = { dialogFocus.value = autoRefreshRowFocus; showAutoRefreshPicker = true },
            modifier = Modifier.focusRequester(autoRefreshRowFocus).then(toSubmit),
            help = SettingHelp(
                refreshTitle, stringResource(R.string.settings_epg_sources_auto_refresh_description),
                hints = keys(ok to stringResource(R.string.settings_key_change), back to parent), extra = submitButton,
            ),
        )
        // Per-feed logo override: this guide's <icon src> replaces the playlist's channel logos.
        val logosTitle = stringResource(R.string.settings_epg_sources_use_logos)
        StageSettingRow(
            icon = OwnTVIcon.LIVE_TV,
            title = logosTitle,
            desc = stringResource(R.string.settings_line_epg_logos),
            value = SettingValue.Switch(useLogos),
            onClick = { useLogos = !useLogos },
            modifier = toSubmit,
            help = SettingHelp(
                logosTitle, stringResource(R.string.settings_epg_sources_logos_description),
                hints = keys(ok to stringResource(R.string.settings_key_switch), back to parent), extra = submitButton,
            ),
        )
    }

    if (showPlaylistPicker) {
        PlaylistEpgPicker(
            load = loadPlaylistOptions,
            onPick = { opt ->
                showPlaylistPicker = false
                if (tv.own.owntv.core.repository.EpgRepository.stalkerSourceIdOf(opt.url) != null) {
                    // Nothing to fill in: a portal guide is fetched through the playlist's own session,
                    // so showing the user an internal marker in a URL box would be worse than useless.
                    onSave(opt.name, opt.url, ua, autoRefresh, useLogos)
                } else {
                    if (name.isBlank()) name = opt.name
                    url = opt.url
                }
            },
            onDismiss = { showPlaylistPicker = false },
        )
    }
    if (showAutoRefreshPicker) {
        PickerDialog(
            title = stringResource(R.string.settings_epg_sources_auto_refresh_title),
            options = EpgAutoRefresh.entries.map { it.name to epgAutoRefreshLabel(it) },
            selected = autoRefresh.mode.name,
            onSelect = { value ->
                val mode = runCatching { EpgAutoRefresh.valueOf(value) }.getOrDefault(EpgAutoRefresh.OFF)
                showAutoRefreshPicker = false
                // "Every N days" needs the N, so it opens the same stepper the playlist picker uses.
                if (mode == EpgAutoRefresh.MANUAL) showManualDays = true else autoRefresh = EpgRefresh(mode)
            },
            onDismiss = { showAutoRefreshPicker = false },
        )
    }
    if (showManualDays) {
        DayStepperDialog(
            title = stringResource(R.string.settings_sources_refresh_days_title),
            hint = stringResource(R.string.settings_sources_refresh_days_hint),
            initialDays = autoRefresh.manualDays,
            minDays = PlaylistRefresh.MIN_MANUAL_DAYS,
            maxDays = PlaylistRefresh.MAX_MANUAL_DAYS,
            label = { days -> pluralStringResource(R.plurals.settings_sources_refresh_days, days, days) },
            onConfirm = { autoRefresh = EpgRefresh(EpgAutoRefresh.MANUAL, it); showManualDays = false },
            onDismiss = { showManualDays = false },
        )
    }
}

@Composable
private fun PlaylistEpgPicker(
    load: suspend () -> List<EpgSourcesViewModel.PlaylistEpg>,
    onPick: (EpgSourcesViewModel.PlaylistEpg) -> Unit,
    onDismiss: () -> Unit,
) {
    val options by produceState<List<EpgSourcesViewModel.PlaylistEpg>?>(initialValue = null) { value = runCatching { load() }.getOrDefault(emptyList()) }
    // A list picked from: in the page's panel, a Stage popup elsewhere.
    val list: @Composable () -> Unit = {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(options) { if (!options.isNullOrEmpty()) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } } }
        val opts = options
        when {
            opts == null -> Box(Modifier.fillMaxWidth().height(80.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
            opts.isEmpty() -> Text(stringResource(R.string.settings_epg_sources_none_playlist), style = tv.own.owntv.ui.theme.stageText(17, 500), color = tv.own.owntv.ui.theme.StageColors.Muted)
            else -> Column(Modifier.trapAllFocusExit().focusGroup()) {
                opts.forEach { opt ->
                    // A portal guide has no address to show — say what it is instead.
                    val line = if (tv.own.owntv.core.repository.EpgRepository.stalkerSourceIdOf(opt.url) != null) stringResource(R.string.settings_epg_sources_portal_guide) else tv.own.owntv.core.network.HttpClient.redactUrl(opt.url)
                    tv.own.owntv.ui.stage.StagePopupOption(
                        title = opt.name, subtitle = line, onClick = { onPick(opt) },
                        modifier = if (opt == opts.first()) Modifier.focusRequester(firstFocus) else Modifier,
                        leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.EPG) },
                    )
                }
            }
        }
    }
    if (panelEditor(onDismiss) { list() }) return
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = stringResource(R.string.settings_epg_sources_fill_playlist)) { list() }
}
