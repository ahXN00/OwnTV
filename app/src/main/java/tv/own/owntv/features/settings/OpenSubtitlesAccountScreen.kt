package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.format.localizedInteger

/**
 * Languages the OpenSubtitles search can be restricted to (ISO 639-1, the codes their API expects).
 *
 * Its own list on purpose: VideoPlayerSettingsScreen's LANGUAGES covers embedded-track matching with
 * 3-letter codes and only 15 entries (no Greek, among many), which is far too narrow for a subtitle
 * library that carries ~60 languages. Blank is not offered — the filter row is hidden when the toggle
 * is off, and "off" is what means "all languages".
 */
private val SUB_SEARCH_LANGUAGE_CODES = listOf(
    "ar", "bg", "zh-cn", "zh-tw", "hr", "cs", "da", "nl", "en", "et", "fi", "fr",
    "de", "el", "he", "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "ms", "no",
    "fa", "pl", "pt-br", "pt-pt", "ro", "ru", "sr", "sk", "sl", "es", "sv", "th",
    "tr", "uk", "vi",
)

@Composable
private fun subSearchLanguages(): List<Pair<String, String>> {
    val displayLocale = LocalConfiguration.current.locales[0]
    return remember(displayLocale) {
        SUB_SEARCH_LANGUAGE_CODES.map { code ->
            code to java.util.Locale.forLanguageTag(code).getDisplayName(displayLocale)
        }
    }
}

/** Device language if OpenSubtitles carries it, else English — the seed when the filter is first turned on. */
private fun defaultSearchLang(): String {
    val locale = java.util.Locale.getDefault()
    val tag = "${locale.language}-${locale.country}".lowercase()
    return SUB_SEARCH_LANGUAGE_CODES.firstOrNull { it == tag }
        ?: SUB_SEARCH_LANGUAGE_CODES.firstOrNull { it == locale.language.lowercase() }
        ?: "en"
}

/**
 * Settings › Content & metadata › OpenSubtitles account.
 * The connection is per OwnTV profile; users sign in with their own free OpenSubtitles account.
 */
@Composable
fun OpenSubtitlesAccountScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: OpenSubtitlesViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    // Search-language filter lives in the shared settings VM (plain DataStore prefs, not account state).
    val settingsVm: SettingsViewModel = koinViewModel()
    val filterEnabled by settingsVm.subSearchFilterEnabled.collectAsStateWithLifecycle()
    val searchLang by settingsVm.subSearchLanguages.collectAsStateWithLifecycle()
    val searchLanguages = subSearchLanguages()
    val searchLanguageName = searchLanguages.firstOrNull { it.first == searchLang }?.second
        ?: searchLang.ifBlank { stringResource(R.string.player_subtitles_language_not_set) }
    val storedApiKey by settingsVm.openSubtitlesApiKey.collectAsStateWithLifecycle()
    val storedServerUrl by settingsVm.openSubtitlesServerUrl.collectAsStateWithLifecycle()

    // Sign-in setup: a Remote/Enter-here chooser, then one panel holding every field. Hoisted here so
    // the Remote hand-over can pre-fill them before the panel opens.
    var showSetupChooser by remember { mutableStateOf(false) }
    var showSignIn by remember { mutableStateOf(false) }
    var signInUser by remember { mutableStateOf("") }
    var signInPass by remember { mutableStateOf("") }
    var signInStay by remember { mutableStateOf(true) }
    var showDeleteSubs by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }
    var showApiAccess by remember { mutableStateOf(false) }
    var apiWasOpen by remember { mutableStateOf(false) }
    val apiRowFocus = remember { FocusRequester() }
    var showRemoteSetup by remember { mutableStateOf(false) }
    // Which door opened Remote: the sign-in chooser (credentials + advanced) or the standalone
    // Advanced row (key/URL only). The companion delivers on one flow either way.
    var remoteForSignIn by remember { mutableStateOf(false) }
    var apiKey by remember(storedApiKey) { mutableStateOf(storedApiKey) }
    var serverUrl by remember(storedServerUrl) { mutableStateOf(storedServerUrl) }
    // What the remote browser sent, parked until the companion dialog has actually left the
    // composition. Two stages on purpose: the collector below is keyed on the dialog's visibility,
    // so anything it tried to do AFTER closing the dialog would be cancelled with it. Applying the
    // payload from its own effect also guarantees only one focus-trapping popup is ever alive.
    var pendingRemote by remember {
        mutableStateOf<tv.own.owntv.core.companion.CompanionServiceConfig?>(null)
    }
    LaunchedEffect(showRemoteSetup) {
        if (!showRemoteSetup) return@LaunchedEffect
        settingsVm.remoteOpenSubtitlesConfigs.collect { received ->
            pendingRemote = received
            showRemoteSetup = false
        }
    }
    LaunchedEffect(pendingRemote) {
        val received = pendingRemote ?: return@LaunchedEffect
        // One frame for the companion popup to be torn down before the next one mounts.
        kotlinx.coroutines.delay(150)
        if (remoteForSignIn) {
            remoteForSignIn = false
            signInUser = received.username
            signInPass = received.password
            // Blank means "left empty on the remote", never "clear what's saved" — the form shows
            // the stored values, so the user can still clear them there deliberately.
            if (received.apiKey.isNotBlank()) apiKey = received.apiKey
            if (received.serverUrl.isNotBlank()) serverUrl = received.serverUrl
            showSignIn = true
        } else {
            apiKey = received.apiKey
            serverUrl = received.serverUrl
            showApiAccess = true
        }
        // Consumed LAST. Clearing it first would change this effect's key and cancel the coroutine
        // at the delay above, so the payload would silently never be applied.
        pendingRemote = null
    }
    var langPickerWasOpen by remember { mutableStateOf(false) }
    val langRowFocus = remember { FocusRequester() }
    // Returning from the Delete-subtitles screen should land back on the row that opened it,
    // not the first row (Sign out / Sign in).
    var returnedFromDelete by remember { mutableStateOf(false) }
    if (showDeleteSubs) {
        DeleteSubtitlesScreen(
            onBack = { showDeleteSubs = false; returnedFromDelete = true },
            modifier = modifier,
        )
        return
    }
    val firstFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    // Entry focus once, on first composition (keying on `state` stole focus on every refresh).
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { firstFocus.requestFocus() }
    }
    // Returning from Delete subtitles lands back on the row that opened it.
    LaunchedEffect(showDeleteSubs) {
        if (!showDeleteSubs && returnedFromDelete) {
            returnedFromDelete = false
            kotlinx.coroutines.delay(60)
            runCatching { deleteFocus.requestFocus() }
        }
    }
    val deleteVm: DeleteSubtitlesViewModel = koinViewModel()
    val movieSubs by deleteVm.movieCount.collectAsStateWithLifecycle()
    val seriesSubs by deleteVm.seriesCount.collectAsStateWithLifecycle()
    val downloaded = movieSubs + seriesSubs

    // ACCOUNT, SEARCH and ON THIS TV as rows; the account's details in the panel.
    val ok = stringResource(R.string.common_ok)
    val back = stringResource(R.string.common_back)
    val content = stringResource(R.string.settings_group_content_metadata)
    val signedIn = state as? OpenSubtitlesViewModel.UiState.SignedIn
    val connection = when {
        storedServerUrl.isNotBlank() -> stringResource(R.string.settings_tier_self_host)
        storedApiKey.isNotBlank() -> stringResource(R.string.settings_tier_key)
        else -> stringResource(R.string.settings_shared)
    }
    val rowCount = (if (signedIn != null) 3 else 1) + (if (filterEnabled) 2 else 1) + 1
    StageFullPage(
        parents = listOf(content),
        title = stringResource(R.string.settings_open_subtitles),
        count = pluralStringResource(R.plurals.settings_setting_count, rowCount, rowCount),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = firstFocus,
    ) {
        val accountTitle = stringResource(R.string.player_subtitles_account)
        StageSettingsHeading(accountTitle, null, first = true)
        when (val s = state) {
            is OpenSubtitlesViewModel.UiState.SignedIn -> {
                val session = s.session
                val account = listOfNotNull(session.level, stringResource(R.string.player_subtitles_vip).takeIf { session.vip })
                    .joinToString(stringResource(R.string.player_subtitles_tags_separator))
                    .ifBlank { stringResource(R.string.player_subtitles_free_account) }
                val remaining = session.remainingDownloads
                val total = session.allowedDownloads
                val downloads = when {
                    remaining != null && total != null -> pluralStringResource(R.plurals.player_subtitles_remaining, remaining, remaining, total)
                    remaining != null -> pluralStringResource(R.plurals.player_subtitles_remaining_short, remaining, remaining)
                    else -> null
                }
                val resets = openSubtitlesResetLabel(session.resetTime)
                val accountLines: @Composable () -> Unit = {
                    Column(Modifier.padding(top = 10.mpx), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
                        SettingPanelLine(stringResource(R.string.player_subtitles_connected_as), session.username)
                        SettingPanelLine(accountTitle, account)
                        if (downloads != null) SettingPanelLine(stringResource(R.string.player_subtitles_downloads), downloads)
                        SettingPanelLine(stringResource(R.string.player_subtitles_resets), resets)
                        SettingPanelLine(stringResource(R.string.settings_metadata_connection), connection)
                    }
                }
                val signOut = stringResource(R.string.player_subtitles_sign_out)
                StageSettingRow(
                    icon = OwnTVIcon.PERSON,
                    title = signOut,
                    desc = stringResource(R.string.player_subtitles_connected_user, session.username),
                    value = null,
                    onClick = { vm.signOut() },
                    modifier = Modifier.focusRequester(firstFocus),
                    help = SettingHelp(
                        accountTitle,
                        stringResource(R.string.player_subtitles_delete_login_message),
                        hints = listOf(ok to signOut, back to content),
                        extra = accountLines,
                    ),
                )
                val refresh = stringResource(R.string.player_subtitles_refresh)
                StageSettingRow(
                    icon = OwnTVIcon.REFRESH,
                    title = refresh,
                    desc = resets,
                    value = null,
                    onClick = { vm.refresh() },
                    help = SettingHelp(accountTitle, refresh, hints = listOf(ok to refresh, back to content), extra = accountLines),
                )
                val advTitle = stringResource(R.string.settings_open_subtitles_advanced)
                StageSettingRow(
                    icon = OwnTVIcon.GEAR,
                    title = advTitle,
                    desc = stringResource(R.string.settings_line_os_advanced),
                    value = SettingValue.Opens(connection),
                    onClick = { showApiAccess = true },
                    modifier = Modifier.focusRequester(apiRowFocus),
                    help = SettingHelp(advTitle, stringResource(R.string.settings_open_subtitles_advanced_description), hints = listOf(ok to stringResource(R.string.settings_key_open), back to content)),
                )
            }
            OpenSubtitlesViewModel.UiState.Busy -> StageSettingsNote(stringResource(R.string.player_subtitles_contacting), null)
            OpenSubtitlesViewModel.UiState.SignedOut -> {
                val signIn = stringResource(R.string.player_subtitles_sign_in)
                val connect = stringResource(R.string.player_subtitles_connect_description)
                StageSettingRow(
                    icon = OwnTVIcon.PERSON,
                    title = signIn,
                    desc = stringResource(R.string.settings_signed_out),
                    value = SettingValue.Opens(null),
                    onClick = { showSetupChooser = true },
                    modifier = Modifier.focusRequester(firstFocus),
                    help = SettingHelp(accountTitle, connect, hints = listOf(ok to signIn, back to content)),
                )
            }
        }

        StageSettingsHeading(stringResource(R.string.player_subtitles_search), null)
        val filterTitle = stringResource(R.string.player_subtitles_filter_title)
        val filterValue = SettingValue.Switch(filterEnabled)
        StageSettingRow(
            icon = OwnTVIcon.LANGUAGE,
            title = filterTitle,
            desc = stringResource(R.string.settings_line_os_filter),
            value = filterValue,
            onClick = {
                // Turning the filter on with nothing chosen yet would silently behave like "off"
                // (no codes = no filter), so seed it from the device language, falling back to English.
                if (!filterEnabled && searchLang.isBlank()) settingsVm.setSubSearchLanguages(defaultSearchLang())
                settingsVm.setSubSearchFilterEnabled(!filterEnabled)
            },
            help = settingHelp(null, filterTitle, stringResource(R.string.player_subtitles_filter_description), filterValue, pinnable = false),
        )
        if (filterEnabled) {
            val langTitle = stringResource(R.string.player_subtitles_search_language)
            StageSettingRow(
                icon = OwnTVIcon.LANGUAGE,
                title = langTitle,
                desc = stringResource(R.string.settings_line_os_search_language),
                value = SettingValue.Choice(searchLanguageName),
                onClick = { showLangPicker = true },
                modifier = Modifier.focusRequester(langRowFocus),
                help = SettingHelp(langTitle, stringResource(R.string.player_subtitles_search_language_description), hints = listOf(ok to stringResource(R.string.settings_key_change), back to content)),
            )
        }

        StageSettingsHeading(stringResource(R.string.content_downloads_group_on_tv), null)
        val deleteTitle = stringResource(R.string.settings_delete_subtitles)
        StageSettingRow(
            icon = OwnTVIcon.TRASH,
            title = deleteTitle,
            desc = pluralStringResource(R.plurals.settings_subtitles_downloaded_count, downloaded, downloaded),
            value = SettingValue.Opens(localizedInteger(downloaded, grouping = false)),
            onClick = { showDeleteSubs = true },
            modifier = Modifier.focusRequester(deleteFocus),
            help = SettingHelp(deleteTitle, stringResource(R.string.player_subtitles_delete_description), hints = listOf(ok to stringResource(R.string.settings_key_open), back to content)),
        )

        // OpenSubtitles attribution — logo + line, mirroring the TMDB credit in Metadata.
        Column(Modifier.padding(start = 22.mpx, top = 28.mpx)) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(tv.own.owntv.R.drawable.ic_opensubtitles_logo),
                contentDescription = stringResource(R.string.settings_open_subtitles),
            )
            Text(
                stringResource(R.string.player_subtitles_api_notice),
                style = stageText(15, 500),
                color = StageColors.Muted,
                modifier = Modifier.padding(top = 8.mpx),
            )
        }
    }

    if (showSetupChooser) {
        OpenSubtitlesSetupChooser(
            onRemote = { showSetupChooser = false; remoteForSignIn = true; showRemoteSetup = true },
            onLocal = { showSetupChooser = false; showSignIn = true },
            onDismiss = { showSetupChooser = false },
        )
    }

    if (showSignIn) {
        OpenSubtitlesSignInDialog(
            username = signInUser, onUsernameChange = { signInUser = it },
            password = signInPass, onPasswordChange = { signInPass = it },
            staySignedIn = signInStay, onStayChange = { signInStay = it },
            apiKey = apiKey, onApiKeyChange = { apiKey = it },
            serverUrl = serverUrl, onServerUrlChange = { serverUrl = it },
            onSubmit = {
                showSignIn = false
                // The optional fields are edited in place here, so Sign in is also their Save. A
                // blank one is a deliberate clear: the form opened showing whatever was stored.
                settingsVm.setOpenSubtitlesApiKey(apiKey)
                settingsVm.setOpenSubtitlesServerUrl(serverUrl)
                vm.signIn(signInUser.trim(), signInPass, signInStay)
                signInPass = ""
            },
            // Back returns to the local/remote chooser rather than closing both levels.
            onDismiss = { showSignIn = false; signInPass = ""; showSetupChooser = true },
        )
    }

    if (showApiAccess) {
        OpenSubtitlesApiPopup(
            key = apiKey, url = serverUrl,
            onKeyChange = { apiKey = it }, onUrlChange = { serverUrl = it },
            onRemote = { showApiAccess = false; remoteForSignIn = false; showRemoteSetup = true },
            onRemove = {
                apiKey = ""; serverUrl = ""
                settingsVm.setOpenSubtitlesApiKey(""); settingsVm.setOpenSubtitlesServerUrl("")
                showApiAccess = false
            },
            onSave = {
                settingsVm.setOpenSubtitlesApiKey(apiKey); settingsVm.setOpenSubtitlesServerUrl(serverUrl)
                showApiAccess = false
                vm.refresh()
            },
            onDismiss = { showApiAccess = false },
        )
    }
    LaunchedEffect(showApiAccess) {
        if (showApiAccess) apiWasOpen = true
        else if (apiWasOpen && !showRemoteSetup) {
            apiWasOpen = false
            kotlinx.coroutines.delay(80)
            runCatching { apiRowFocus.requestFocus() }
        }
    }

    if (showRemoteSetup) {
        CompanionKeyDialog(
            titleRes = if (remoteForSignIn) R.string.settings_open_subtitles_setup_title
                else R.string.settings_open_subtitles_advanced,
            state = settingsVm.remoteState.collectAsStateWithLifecycle().value,
            onStart = settingsVm::startRemoteOpenSubtitlesConfigListener,
            onStop = settingsVm::stopRemoteListener,
            onDismiss = { showRemoteSetup = false },
        )
    }

    if (showLangPicker) {
        // Searchable — the list is long enough that D-pad scrolling to e.g. Ukrainian is tedious.
        PickerDialog(
            title = stringResource(R.string.player_subtitles_search_language),
            options = searchLanguages,
            selected = searchLang,
            searchable = true,
            onSelect = {
                if (it != searchLang) settingsVm.setSubSearchLanguages(it)
                showLangPicker = false
            },
            onDismiss = { showLangPicker = false },
        )
    }
    // Return focus to the language row after the dialog closes instead of letting it fall to the first
    // row (same pattern as MetadataSettingsScreen). Gated so it can't steal entry focus on first compose.
    LaunchedEffect(showLangPicker) {
        if (showLangPicker) {
            langPickerWasOpen = true
        } else if (langPickerWasOpen) {
            langPickerWasOpen = false
            kotlinx.coroutines.delay(80)
            runCatching { langRowFocus.requestFocus() }
        }
    }

    error?.let { err ->
        val message = when (err.kind) {
            OpenSubtitlesViewModel.ErrorKind.EMPTY_CREDENTIALS -> stringResource(R.string.player_subtitles_enter_credentials)
            OpenSubtitlesViewModel.ErrorKind.INVALID_CREDENTIALS -> stringResource(R.string.player_subtitles_invalid_credentials)
            // The server answered and said no — showing the code is what makes a user report usable.
            OpenSubtitlesViewModel.ErrorKind.SERVER_ERROR -> stringResource(R.string.player_subtitles_sign_in_server_error, err.httpCode)
            OpenSubtitlesViewModel.ErrorKind.NETWORK -> stringResource(R.string.player_subtitles_sign_in_network_error)
            OpenSubtitlesViewModel.ErrorKind.REFRESH_NETWORK -> stringResource(R.string.player_subtitles_refresh_network_error)
        }
        ErrorDialog(message = message, onDismiss = { vm.dismissError() })
    }
}

@Composable
private fun openSubtitlesResetLabel(raw: String?): String {
    val now = System.currentTimeMillis()
    val target = raw?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
        value.toLongOrNull()?.let { epoch -> if (epoch < 10_000_000_000L) epoch * 1_000L else epoch }
            ?: runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: Regex("^(\\d{1,2}):(\\d{2}):(\\d{2})$").matchEntire(value)?.let { match ->
                now + (match.groupValues[1].toLong() * 3_600L +
                    match.groupValues[2].toLong() * 60L + match.groupValues[3].toLong()) * 1_000L
            }
    }
    if (raw != null && target == null) return raw

    // OpenSubtitles may omit reset_time while the full daily quota is untouched. Its daily
    // allowance rolls at UTC midnight, so still show the next useful reset instead of "Not set".
    val resetAt = target ?: java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
        .toLocalDate().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val totalMinutes = ((resetAt - now).coerceAtLeast(0L) / 60_000L).toInt()
    return stringResource(R.string.settings_open_subtitles_reset_in, totalMinutes / 60, totalMinutes % 60)
}

/** Remote (a browser on the same Wi-Fi) or Enter here (type on the TV) — the one door into sign-in. */
@Composable
private fun OpenSubtitlesSetupChooser(onRemote: () -> Unit, onLocal: () -> Unit, onDismiss: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_open_subtitles_setup_title),
        body = stringResource(R.string.settings_open_subtitles_setup_description),
        buttons = { tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19) },
    ) {
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_open_subtitles_setup_remote),
            subtitle = stringResource(R.string.settings_open_subtitles_setup_remote_description),
            onClick = onRemote,
            modifier = Modifier.focusRequester(firstFocus),
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.SHARE) },
        )
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_open_subtitles_setup_local),
            subtitle = stringResource(R.string.settings_open_subtitles_setup_local_description),
            onClick = onLocal,
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.PERSON) },
        )
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    tv.own.owntv.ui.stage.StageNotice(
        title = stringResource(R.string.settings_open_subtitles),
        body = message,
        ok = stringResource(R.string.settings_close),
        onDismiss = onDismiss,
    )
}
