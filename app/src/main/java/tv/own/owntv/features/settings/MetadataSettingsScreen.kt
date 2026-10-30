package tv.own.owntv.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.metadata.MetadataConfig
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import androidx.compose.ui.text.style.TextOverflow

/**
 * TMDB content languages (ISO 639-1, region-qualified where TMDB's coverage is meaningfully better for
 * one — e.g. pt-BR). "" keeps TMDB's own default (en-US), which is what installs used before this setting
 * existed, so an upgrade never silently changes anyone's metadata.
 *
 * Distinct from VideoPlayerSettingsScreen's LANGUAGES list, which uses 3-letter codes for audio/subtitle
 * track matching — TMDB only accepts 2-letter tags.
 */
private val TMDB_LANGUAGE_CODES = listOf(
    "", MetadataConfig.LANGUAGE_AUTO, "ar", "bg", "zh", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "he", "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "ms", "no", "fa", "pl", "pt-BR", "pt-PT", "ro", "ru", "sr", "sk", "sl", "es", "es-MX", "sv", "th", "tr", "uk", "vi",
)

@Composable
private fun tmdbLangName(code: String): String = stringResource(
    when (code) {
        "" -> R.string.settings_language_default
        MetadataConfig.LANGUAGE_AUTO -> R.string.settings_language_device
        "ar" -> R.string.settings_language_arabic
        "bg" -> R.string.settings_language_bulgarian
        "zh" -> R.string.settings_language_chinese
        "hr" -> R.string.settings_language_croatian
        "cs" -> R.string.settings_language_czech
        "da" -> R.string.settings_language_danish
        "nl" -> R.string.settings_language_dutch
        "en" -> R.string.settings_language_english
        "et" -> R.string.settings_language_estonian
        "fi" -> R.string.settings_language_finnish
        "fr" -> R.string.settings_language_french
        "de" -> R.string.settings_language_german
        "el" -> R.string.settings_language_greek
        "he" -> R.string.settings_language_hebrew
        "hi" -> R.string.settings_language_hindi
        "hu" -> R.string.settings_language_hungarian
        "id" -> R.string.settings_language_indonesian
        "it" -> R.string.settings_language_italian
        "ja" -> R.string.settings_language_japanese
        "ko" -> R.string.settings_language_korean
        "lv" -> R.string.settings_language_latvian
        "lt" -> R.string.settings_language_lithuanian
        "ms" -> R.string.settings_language_malay
        "no" -> R.string.settings_language_norwegian
        "fa" -> R.string.settings_language_persian
        "pl" -> R.string.settings_language_polish
        "pt-BR" -> R.string.settings_language_portuguese_brazil
        "pt-PT" -> R.string.settings_language_portuguese_portugal
        "ro" -> R.string.settings_language_romanian
        "ru" -> R.string.settings_language_russian
        "sr" -> R.string.settings_language_serbian
        "sk" -> R.string.settings_language_slovak
        "sl" -> R.string.settings_language_slovenian
        "es" -> R.string.settings_language_spanish
        "es-MX" -> R.string.settings_language_spanish_latam
        "sv" -> R.string.settings_language_swedish
        "th" -> R.string.settings_language_thai
        "tr" -> R.string.settings_language_turkish
        "uk" -> R.string.settings_language_ukrainian
        "vi" -> R.string.settings_language_vietnamese
        else -> R.string.settings_language_default
    },
)

/**
 * Settings → Metadata (TMDB): the master toggle and the two advanced
 * access tiers (own TMDB key / self-host URL), plus a manual "look up title" test that proves the
 * configured tier reaches TMDB end-to-end. Enrichment of actual detail screens arrives in later phases.
 *
 * Precedence: self-host URL > own key > the default caching Worker (zero setup).
 */
private fun metadataModeLabelRes(mode: tv.own.owntv.core.metadata.MetadataMode): Int = when (mode) {
    tv.own.owntv.core.metadata.MetadataMode.PROVIDER -> R.string.settings_metadata_provider_only
    tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB -> R.string.settings_metadata_provider_plus_tmdb
    tv.own.owntv.core.metadata.MetadataMode.TMDB_ONLY -> R.string.settings_metadata_tmdb_only
}

private fun metadataTierLabelRes(tier: tv.own.owntv.core.metadata.MetadataConfig.Tier): Int = when (tier) {
    tv.own.owntv.core.metadata.MetadataConfig.Tier.DEFAULT_WORKER -> R.string.settings_tier_default
    tv.own.owntv.core.metadata.MetadataConfig.Tier.OWN_KEY -> R.string.settings_tier_key
    tv.own.owntv.core.metadata.MetadataConfig.Tier.SELF_HOST -> R.string.settings_tier_self_host
}

@Composable
fun MetadataSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val mode by vm.metadataMode.collectAsStateWithLifecycle()
    val storedKey by vm.tmdbApiKey.collectAsStateWithLifecycle()
    val storedUrl by vm.metadataServerUrl.collectAsStateWithLifecycle()
    val tier by vm.metadataTier.collectAsStateWithLifecycle()
    val testState by vm.metadataTest.collectAsStateWithLifecycle()
    val language by vm.metadataLanguage.collectAsStateWithLifecycle()
    val budget by vm.metadataBudgetStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(tier) {
        if (tier == MetadataConfig.Tier.DEFAULT_WORKER) vm.refreshMetadataBudget()
    }

    var showLangPicker by remember { mutableStateOf(false) }
    var langPickerWasOpen by remember { mutableStateOf(false) }
    val langRowFocus = remember { FocusRequester() }

    // Seed the editable fields once; local edit → Save persists (same pattern as NetworkSettingsScreen).
    var seeded by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val defaultTestTitle = stringResource(R.string.settings_metadata_test_title)
    var testTitle by remember(defaultTestTitle) { mutableStateOf(defaultTestTitle) }
    // Advanced options are hidden by default. Auto-expand if the user already has a key/URL saved, so the
    // fields aren't silently hidden when they're actually in use.
    var showAdvanced by remember { mutableStateOf(false) }
    var advancedWasOpen by remember { mutableStateOf(false) }
    val advancedRowFocus = remember { FocusRequester() }
    var confirmClearAdvanced by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    var showRemoteHandover by remember { mutableStateOf(false) }
    LaunchedEffect(storedKey, storedUrl) {
        if (!seeded) {
            key = storedKey; url = storedUrl
            seeded = true
        }
    }

    // A key handed over from the remote device lands straight in the field. Saving stays a deliberate act:
    // the user still presses Save, so an accidental send cannot silently replace a working key.
    val keyReceivedMessage = stringResource(R.string.settings_metadata_key_received)
    val toast = tv.own.owntv.ui.components.rememberInAppToast()
    LaunchedEffect(showRemoteHandover) {
        if (!showRemoteHandover) return@LaunchedEffect
        vm.remoteTmdbConfigs.collect { received ->
            key = received.apiKey
            url = received.serverUrl
            showAdvanced = true
            showRemoteHandover = false
            toast.show(keyReceivedMessage)
        }
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // LIBRARY DETAILS and CONNECTION as rows; the active source on top of the panel.
    val back = stringResource(R.string.common_back)
    val content = stringResource(R.string.settings_group_content_metadata)
    val modeLabels = tv.own.owntv.core.metadata.MetadataMode.entries.map { stringResource(metadataModeLabelRes(it)) }
    val rows = if (mode.enrich) 4 else 1
    StageFullPage(
        parents = listOf(content),
        title = stringResource(R.string.settings_metadata),
        count = pluralStringResource(R.plurals.settings_setting_count, rows, rows),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = firstFocus,
        panelTop = if (mode.enrich) {
            {
                val b = budget
                SettingPanelHeading(stringResource(R.string.settings_metadata_active_source))
                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(metadataTierLabelRes(tier)), style = stageText(22, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = when (tier) {
                        MetadataConfig.Tier.DEFAULT_WORKER -> null
                        MetadataConfig.Tier.OWN_KEY -> maskSecret(storedKey)
                        MetadataConfig.Tier.SELF_HOST -> storedUrl
                    }
                    if (detail != null) Text(detail, style = stageText(17, 500), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (tier == MetadataConfig.Tier.DEFAULT_WORKER) {
                    Text(stringResource(R.string.settings_metadata_shared_worker_description), style = stageText(16, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 8.mpx))
                    if (b != null) {
                        val refill = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(b.resetAtMs))
                        Text(
                            stringResource(R.string.settings_allowance_day) + dotSeparator() +
                                pluralStringResource(R.plurals.settings_allowance_value, b.remainingDay, b.remainingDay, b.limitDay) +
                                dotSeparator() + refill,
                            style = stageText(16, 600), color = StageColors.Text, modifier = Modifier.padding(top = 6.mpx),
                        )
                    }
                    Text(stringResource(R.string.settings_metadata_fair_share), style = stageText(15, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 6.mpx))
                }
                Box(Modifier.padding(top = 18.mpx, bottom = 18.mpx).fillMaxWidth().height(1.mpx).background(StageColors.Ink.copy(alpha = 0.1f)))
            }
        } else null,
    ) {
        StageSettingsHeading(stringResource(R.string.settings_metadata_library_details), null, first = true)
        val sourceTitle = stringResource(R.string.settings_metadata_source)
        val sourceValue = SettingValue.Choice(stringResource(metadataModeLabelRes(mode)))
        StageSettingRow(
            icon = OwnTVIcon.MOVIES,
            title = sourceTitle,
            desc = stringResource(R.string.settings_line_metadata_source),
            value = sourceValue,
            onClick = { showModePicker = true },
            help = settingHelp(null, sourceTitle, stringResource(R.string.settings_metadata_source_description), sourceValue, choices = modeLabels, pinnable = false)
                .let { h -> h.copy(hints = listOf(h.hints.first(), back to content)) },
        )
        // The language and the TMDB tier only make sense when TMDB is on (mode != Provider).
        if (mode.enrich) {
            val langTitle = stringResource(R.string.settings_metadata_language)
            val langValue = SettingValue.Choice(tmdbLangName(language))
            StageSettingRow(
                icon = OwnTVIcon.LANGUAGE,
                title = langTitle,
                desc = stringResource(R.string.settings_line_metadata_language),
                value = langValue,
                onClick = { showLangPicker = true },
                modifier = Modifier.focusRequester(langRowFocus),
                help = SettingHelp(langTitle, stringResource(R.string.settings_metadata_language_description), hints = listOf(stringResource(R.string.common_ok) to stringResource(R.string.settings_key_change), back to content)),
            )

            StageSettingsHeading(stringResource(R.string.settings_metadata_connection), null)
            val advTitle = stringResource(R.string.settings_metadata_remote_advanced)
            val advValue = SettingValue.Opens(
                if (tier == MetadataConfig.Tier.DEFAULT_WORKER) stringResource(R.string.settings_shared) else stringResource(metadataTierLabelRes(tier)),
            )
            StageSettingRow(
                icon = OwnTVIcon.GEAR,
                title = advTitle,
                desc = stringResource(R.string.settings_line_metadata_advanced),
                value = advValue,
                onClick = { showAdvanced = true },
                modifier = Modifier.focusRequester(advancedRowFocus),
                help = SettingHelp(advTitle, stringResource(R.string.settings_metadata_remote_advanced_description), hints = listOf(stringResource(R.string.common_ok) to stringResource(R.string.settings_key_open), back to content)),
            )
            val testLabel = stringResource(R.string.settings_metadata_test_connection)
            StageFieldRow(
                icon = OwnTVIcon.SEARCH,
                label = testLabel,
                value = testTitle,
                onValueChange = { testTitle = it },
                placeholder = stringResource(R.string.settings_metadata_test_title),
                help = SettingHelp(
                    testLabel,
                    stringResource(R.string.settings_lookup_movie),
                    extra = { Box(Modifier.padding(top = 12.mpx)) { MetadataTestLabel(testState) } },
                ),
                onDone = { vm.testMetadataLookup(testTitle) },
            )
        }

        // TMDB attribution — logo + line, required by TMDB's API terms.
        Column(Modifier.padding(start = 22.mpx, top = 28.mpx)) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(tv.own.owntv.R.drawable.ic_tmdb_logo),
                contentDescription = stringResource(R.string.settings_metadata),
            )
            Text(
                stringResource(R.string.settings_tmdb_attribution),
                style = stageText(15, 500),
                color = StageColors.Muted,
                modifier = Modifier.padding(top = 8.mpx),
            )
        }
    }

    // Dialogs must come AFTER the scrolling Column: composition order is paint order, and
    // declaring this above it drew the whole settings list on top of the dialog.
    if (showAdvanced) {
        AdvancedMetadataPopup(
            key = key, url = url,
            onKeyChange = { key = it }, onUrlChange = { url = it },
            onRemote = { showAdvanced = false; showRemoteHandover = true },
            onRemove = {
                showAdvanced = false
                if (storedKey.isNotBlank() || storedUrl.isNotBlank()) confirmClearAdvanced = true
            },
            onSave = {
                vm.setTmdbApiKey(key); vm.setMetadataServerUrl(url); vm.resetMetadataTest()
                showAdvanced = false
            },
            onDismiss = { showAdvanced = false },
        )
    }
    LaunchedEffect(showAdvanced) {
        if (showAdvanced) advancedWasOpen = true
        else if (advancedWasOpen && !showRemoteHandover) {
            advancedWasOpen = false
            kotlinx.coroutines.delay(80)
            runCatching { advancedRowFocus.requestFocus() }
        }
    }

    if (confirmClearAdvanced) {
        ConfirmDialog(
            title = stringResource(R.string.settings_metadata_clear_advanced_title),
            message = stringResource(R.string.settings_metadata_clear_advanced_message),
            onConfirm = {
                key = ""
                url = ""
                vm.setTmdbApiKey("")
                vm.setMetadataServerUrl("")
                vm.resetMetadataTest()
                showAdvanced = false
                confirmClearAdvanced = false
            },
            onDismiss = { confirmClearAdvanced = false },
        )
    }

    tv.own.owntv.ui.components.InAppToast(toast)

    if (showRemoteHandover) {
        CompanionKeyDialog(
            titleRes = R.string.settings_metadata_remote_advanced,
            state = vm.remoteState.collectAsStateWithLifecycle().value,
            onStart = vm::startRemoteTmdbConfigListener,
            onStop = vm::stopRemoteListener,
            onDismiss = { showRemoteHandover = false },
        )
    }

    if (showModePicker) {
        PickerDialog(
            title = stringResource(R.string.settings_metadata_source),
            options = tv.own.owntv.core.metadata.MetadataMode.entries.map {
                it.name to stringResource(metadataModeLabelRes(it))
            },
            selected = mode.name,
            onSelect = { picked ->
                tv.own.owntv.core.metadata.MetadataMode.entries.firstOrNull { it.name == picked }?.let {
                    if (it != mode) {
                        vm.setMetadataMode(it)
                        vm.resetMetadataTest()
                    }
                }
                showModePicker = false
            },
            onDismiss = { showModePicker = false },
        )
    }

    if (showLangPicker) {
        // searchable: the list is long enough that D-pad scrolling to e.g. Ukrainian is tedious.
        PickerDialog(
            title = stringResource(R.string.settings_metadata_language),
            options = TMDB_LANGUAGE_CODES.map { it to tmdbLangName(it) },
            selected = language,
            searchable = true,
            onSelect = {
                if (it != language) vm.setMetadataLanguage(it)
                showLangPicker = false
            },
            onDismiss = { showLangPicker = false },
        )
    }
    // Return focus to the language row after the dialog closes, rather than letting it fall to the
    // screen's first mode row (same pattern as WeatherSettingsScreen's location dialog). Gated on
    // langPickerWasOpen so this doesn't fire on first composition and steal focus from firstFocus.
    LaunchedEffect(showLangPicker) {
        if (showLangPicker) {
            langPickerWasOpen = true
        } else if (langPickerWasOpen) {
            langPickerWasOpen = false
            kotlinx.coroutines.delay(80)
            runCatching { langRowFocus.requestFocus() }
        }
    }
}

@Composable
private fun MetadataTestLabel(state: SettingsViewModel.MetadataTestState) {
    val (text, color) = when (state) {
        is SettingsViewModel.MetadataTestState.Ok -> stringResource(
            R.string.settings_metadata_match_result,
            state.title,
            // The space lives here: Weblate trims it from the start of the string resource.
            state.year?.let { " " + stringResource(R.string.settings_metadata_year, it) } ?: "",
            state.tmdbId,
        ) to tv.own.owntv.ui.theme.stageAccent.accent
        is SettingsViewModel.MetadataTestState.Fail -> when (val failure = state.failure) {
            SettingsViewModel.MetadataFailure.EmptyTitle -> stringResource(R.string.settings_metadata_empty_title)
            SettingsViewModel.MetadataFailure.ServerUnavailable -> stringResource(R.string.settings_metadata_server_unavailable)
            is SettingsViewModel.MetadataFailure.NoMatch -> stringResource(R.string.settings_metadata_no_match, failure.query)
            is SettingsViewModel.MetadataFailure.Unknown -> failure.rawMessage ?: stringResource(R.string.settings_metadata_lookup_failed)
        } to androidx.compose.ui.graphics.Color(0xFFEF4444)
        else -> null to tv.own.owntv.ui.theme.StageColors.Muted
    }
    if (text != null) {
        Text(text, style = stageText(16, 600), color = color)
    }
}

/** Show only the tail of a secret: enough to tell two keys apart, useless to a shoulder-surfer. */
private fun maskSecret(secret: String): String {
    val trimmed = secret.trim()
    if (trimmed.length <= 4) return "\u2022".repeat(4)
    return "\u2022".repeat(8) + trimmed.takeLast(4)
}
