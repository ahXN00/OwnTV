package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.skip.SkipButtonShow
import tv.own.owntv.core.skip.SkipKind
import tv.own.owntv.core.skip.SkipMode
import tv.own.owntv.core.skip.TheIntroDbAccount
import tv.own.owntv.core.skip.TheIntroDbClient
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * Settings → Content & metadata → TheIntroDB: what each part (intro, recap, end credits, preview) does,
 * how long the skip button stays, and the optional TheIntroDB key with its state and today's allowance.
 *
 * The times are looked up by the title's TMDB id, so they only exist for films and episodes TMDB matched;
 * the description says so rather than hiding the screen.
 */
@Composable
fun TheIntroDbSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val skipChoices by vm.skipChoices.collectAsStateWithLifecycle()
    val storedKey by vm.theIntroDbApiKey.collectAsStateWithLifecycle()
    val quota by vm.theIntroDbQuota.collectAsStateWithLifecycle()
    val status by vm.theIntroDbStatus.collectAsStateWithLifecycle()

    var skipPicker by remember { mutableStateOf<SkipKind?>(null) }
    var showButtonPicker by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showHandover by remember { mutableStateOf(false) }
    var keyDraft by remember { mutableStateOf("") }

    // A key handed over from another device lands in the field; Check key still saves it deliberately.
    val keyReceived = stringResource(R.string.settings_theintrodb_key_received)
    val toast = tv.own.owntv.ui.components.rememberInAppToast()
    LaunchedEffect(showHandover) {
        if (!showHandover) return@LaunchedEffect
        vm.remoteTheIntroDbConfigs.collect { received ->
            keyDraft = received.apiKey
            showHandover = false
            showAccount = true
            toast.show(keyReceived)
        }
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val back = stringResource(R.string.common_back)
    val content = stringResource(R.string.settings_group_content_metadata)
    // The four parts, the button and the account.
    val rows = SkipKind.entries.size + 2
    StageFullPage(
        parents = listOf(content),
        title = stringResource(R.string.settings_theintrodb),
        count = pluralStringResource(R.plurals.settings_setting_count, rows, rows),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = firstFocus,
    ) {
        StageSettingsHeading(stringResource(R.string.settings_skip_group), null, first = true)
        val partDescription = stringResource(R.string.settings_skip_part_description)
        val modeChoices = SkipMode.entries.map { skipModeLabel(it) }
        for (kind in SkipKind.entries) {
            val partTitle = stringResource(skipPartTitleRes(kind))
            val partValue = SettingValue.Choice(skipModeLabel(skipChoices.modeOf(kind)))
            StageSettingRow(
                icon = skipPartIcon(kind),
                title = partTitle,
                desc = stringResource(R.string.settings_line_skip_part),
                value = partValue,
                onClick = { skipPicker = kind },
                help = settingHelp(null, partTitle, partDescription, partValue, choices = modeChoices, pinnable = false)
                    .let { h -> h.copy(hints = listOf(h.hints.first(), back to content)) },
            )
        }
        val buttonTitle = stringResource(R.string.settings_skip_button_show)
        val buttonValue = SettingValue.Choice(skipButtonShowLabel(skipChoices.buttonShow))
        StageSettingRow(
            icon = OwnTVIcon.INFO,
            title = buttonTitle,
            desc = stringResource(R.string.settings_line_skip_button_show),
            value = buttonValue,
            onClick = { showButtonPicker = true },
            help = settingHelp(
                null, buttonTitle, stringResource(R.string.settings_line_skip_button_show), buttonValue,
                choices = SkipButtonShow.entries.map { skipButtonShowLabel(it) }, pinnable = false,
            ).let { h -> h.copy(hints = listOf(h.hints.first(), back to content)) },
        )

        StageSettingsHeading(stringResource(R.string.settings_metadata_connection), null)
        val accountTitle = stringResource(R.string.settings_theintrodb_account)
        StageSettingRow(
            icon = OwnTVIcon.PERSON,
            title = accountTitle,
            desc = stringResource(R.string.settings_line_theintrodb),
            value = SettingValue.Opens(if (storedKey.isBlank()) stringResource(R.string.settings_shared) else maskSecret(storedKey)),
            onClick = { keyDraft = storedKey; showAccount = true },
            help = SettingHelp(
                accountTitle,
                stringResource(R.string.settings_theintrodb_key_steps),
                hints = listOf(stringResource(R.string.common_ok) to stringResource(R.string.settings_key_open), back to content),
                extra = { Column(Modifier.padding(top = 12.mpx)) { TheIntroDbStatusLines(status, storedKey, quota) } },
            ),
        )

        // The credit, the way TMDB's is given: TheIntroDB's "Powered by" badge exactly as published
        // (never tinted or reshaped — its brand rules) and one line.
        Column(Modifier.padding(start = 22.mpx, top = 28.mpx)) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.theintrodb_logo),
                contentDescription = stringResource(R.string.settings_theintrodb),
                modifier = Modifier.height(96.mpx).aspectRatio(THEINTRODB_LOGO_RATIO),
            )
            Text(
                stringResource(R.string.settings_theintrodb_attribution),
                style = stageText(15, 500),
                color = StageColors.Muted,
                modifier = Modifier.padding(top = 10.mpx),
            )
            Text(
                stringResource(R.string.settings_theintrodb_contribute),
                style = stageText(15, 500),
                color = StageColors.Muted,
                modifier = Modifier.padding(top = 4.mpx),
            )
        }
    }

    // Dialogs after the page: composition order is paint order.
    skipPicker?.let { kind ->
        PickerDialog(
            title = stringResource(skipPartTitleRes(kind)),
            options = SkipMode.entries.map { it.name to skipModeLabel(it) },
            selected = skipChoices.modeOf(kind).name,
            onSelect = { picked ->
                SkipMode.entries.firstOrNull { it.name == picked }?.let { vm.setSkipMode(kind, it) }
                skipPicker = null
            },
            onDismiss = { skipPicker = null },
        )
    }
    if (showButtonPicker) {
        PickerDialog(
            title = stringResource(R.string.settings_skip_button_show),
            options = SkipButtonShow.entries.map { it.name to skipButtonShowLabel(it) },
            selected = skipChoices.buttonShow.name,
            onSelect = { picked ->
                SkipButtonShow.entries.firstOrNull { it.name == picked }?.let(vm::setSkipButtonShow)
                showButtonPicker = false
            },
            onDismiss = { showButtonPicker = false },
        )
    }
    if (showAccount) {
        val checking by vm.theIntroDbChecking.collectAsStateWithLifecycle()
        // Opening the popup checks at once, so the key's state and today's allowance are on screen.
        LaunchedEffect(Unit) { vm.checkTheIntroDb() }
        TheIntroDbPopup(
            key = keyDraft,
            hasStoredKey = storedKey.isNotBlank(),
            onKeyChange = { keyDraft = it },
            statusLines = { TheIntroDbStatusLines(if (checking) null else status, storedKey, quota) },
            onRemote = { showAccount = false; showHandover = true },
            onRemove = { keyDraft = ""; vm.saveTheIntroDbKey("") },
            onCheck = { vm.saveTheIntroDbKey(keyDraft) },
            onDismiss = { showAccount = false },
        )
    }
    if (showHandover) {
        CompanionKeyDialog(
            titleRes = R.string.settings_theintrodb_account,
            state = vm.remoteState.collectAsStateWithLifecycle().value,
            onStart = vm::startRemoteTheIntroDbConfigListener,
            onStop = vm::stopRemoteListener,
            onDismiss = { showHandover = false },
        )
    }
    tv.own.owntv.ui.components.InAppToast(toast)
}

/** TheIntroDB's full logo is drawn at its own proportions (850 × 1450), never stretched. */
private const val THEINTRODB_LOGO_RATIO = 850f / 1450f

private fun skipPartTitleRes(kind: SkipKind): Int = when (kind) {
    SkipKind.INTRO -> R.string.settings_skip_intro
    SkipKind.RECAP -> R.string.settings_skip_recap
    SkipKind.CREDITS -> R.string.settings_skip_credits
    SkipKind.PREVIEW -> R.string.settings_skip_preview
}

private fun skipPartIcon(kind: SkipKind): OwnTVIcon = when (kind) {
    SkipKind.INTRO -> OwnTVIcon.SKIP_NEXT
    SkipKind.RECAP -> OwnTVIcon.HISTORY
    SkipKind.CREDITS -> OwnTVIcon.AUTOPLAY_NEXT
    SkipKind.PREVIEW -> OwnTVIcon.FORWARD
}

@Composable
private fun skipModeLabel(mode: SkipMode): String = stringResource(
    when (mode) {
        SkipMode.OFF -> R.string.common_off
        SkipMode.BUTTON -> R.string.settings_skip_mode_button
        SkipMode.AUTO -> R.string.settings_skip_mode_auto
    },
)

@Composable
private fun skipButtonShowLabel(show: SkipButtonShow): String = stringResource(
    when (show) {
        SkipButtonShow.START -> R.string.settings_skip_button_start
        SkipButtonShow.WHOLE -> R.string.settings_skip_button_whole
    },
)

/**
 * The key's state — "Connected · submissions: 3", no key, not accepted, unreachable — then today's
 * lookups, "Day · 4994 / 5000 left · Refills 18:40", once the service has reported them.
 */
@Composable
internal fun TheIntroDbStatusLines(status: TheIntroDbAccount.Status?, storedKey: String, quota: TheIntroDbClient.Quota?) {
    val context = LocalContext.current
    val keyLine = when (val k = status?.key) {
        is TheIntroDbClient.KeyCheck.Valid -> stringResource(R.string.settings_theintrodb_connected, k.submissions)
        TheIntroDbClient.KeyCheck.Invalid -> stringResource(R.string.settings_theintrodb_invalid)
        TheIntroDbClient.KeyCheck.Unreachable -> stringResource(R.string.settings_theintrodb_unreachable)
        null -> if (storedKey.isBlank()) stringResource(R.string.settings_theintrodb_no_key) else null
    }
    if (keyLine != null) {
        val bad = status?.key is TheIntroDbClient.KeyCheck.Invalid
        Text(keyLine, style = stageText(16, 600), color = if (bad) androidx.compose.ui.graphics.Color(0xFFEF4444) else StageColors.Text)
    }
    val q = quota ?: status?.quota ?: return
    val refill = if (q.resetSeconds > 0) {
        dotSeparator() + stringResource(
            R.string.settings_allowance_refills,
            android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(q.atMs + q.resetSeconds * 1000)),
        )
    } else ""
    Text(
        stringResource(R.string.settings_allowance_day) + dotSeparator() +
            pluralStringResource(R.plurals.settings_allowance_value, q.remaining, q.remaining, q.limit) + refill,
        style = stageText(16, 600), color = StageColors.Text, modifier = Modifier.padding(top = 6.mpx),
    )
}

@Composable
private fun TheIntroDbPopup(
    key: String,
    hasStoredKey: Boolean,
    onKeyChange: (String) -> Unit,
    statusLines: @Composable () -> Unit,
    onRemote: () -> Unit,
    onRemove: () -> Unit,
    onCheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_theintrodb_account),
        body = stringResource(R.string.settings_theintrodb_key_steps),
        buttons = {
            // Only a stored key can be removed.
            if (hasStoredKey) {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_metadata_clear_advanced_title), onClick = onRemove, height = 56.mpx, textSize = 19)
            }
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_theintrodb_check), onClick = onCheck, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_metadata_key_from_phone),
            subtitle = stringResource(R.string.settings_theintrodb_api_key),
            onClick = onRemote,
            modifier = Modifier.focusRequester(firstFocus),
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.SHARE) },
        )
        Column(Modifier.padding(top = 10.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
            StageFieldRow(
                icon = OwnTVIcon.PENCIL,
                label = stringResource(R.string.settings_theintrodb_api_key),
                value = key,
                onValueChange = onKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.settings_metadata_optional),
            )
            Column(Modifier.padding(start = 6.mpx)) { statusLines() }
        }
    }
}
