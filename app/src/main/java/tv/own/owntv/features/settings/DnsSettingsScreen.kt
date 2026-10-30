package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.owntv.R
import androidx.compose.ui.res.pluralStringResource
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.core.network.DohPresets
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.features.automation.SensitiveScreenMarker

/** This screen's rows as Settings search finds them. */
internal val DNS_SEARCH_ROWS: List<Int> =
    listOf(R.string.settings_dns_use_custom, R.string.settings_presets, R.string.settings_dns_server, R.string.settings_dns_test)

@Composable
fun DnsSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SensitiveScreenMarker()
    val vm: SettingsViewModel = koinViewModel()

    val dnsConfig by vm.dnsConfig.collectAsStateWithLifecycle()
    val dnsTestState by vm.dnsTest.collectAsStateWithLifecycle()

    fun dnsToServerText(cfg: tv.own.owntv.core.network.DnsConfig): String {
        if (cfg.dohUrl.isNotBlank()) return cfg.dohUrl
        if (cfg.host.isNotBlank()) {
            val p = if (cfg.port > 0 && cfg.port != 53) ":${cfg.port}" else ""
            return "${cfg.host}$p"
        }
        return ""
    }

    // DataStore can emit after the screen's initial empty value. Synchronize only when persisted
    // DNS fields change so restored values do not overwrite normal editing.
    val hasServer = dnsConfig.host.isNotBlank() || dnsConfig.dohUrl.isNotBlank()
    var toggleOn by remember { mutableStateOf(dnsConfig.enabled || hasServer) }
    var server by remember { mutableStateOf(dnsToServerText(dnsConfig)) }

    LaunchedEffect(dnsConfig.enabled, dnsConfig.host, dnsConfig.port, dnsConfig.dohUrl) {
        toggleOn = dnsConfig.enabled || dnsConfig.host.isNotBlank() || dnsConfig.dohUrl.isNotBlank()
        server = dnsToServerText(dnsConfig)
    }

    val serverConfigured = server.trim().isNotBlank()
    val effectiveEnabled = toggleOn && serverConfigured

    // Toggle: ON = show fields (no persistence needed). OFF = hide fields + immediately persist disabled.
    fun applyToggle(on: Boolean) {
        toggleOn = on
        if (!on) {
            // Immediately disable DNS — fire and forget, no waiting for response.
            vm.saveDns(enabled = false, host = "", port = 53, dohUrl = "")
            vm.resetDnsTest()
        }
    }

    // Save: persist the server URL. DNS is enabled only when a server is configured.
    fun applySave() {
        val s = server.trim()
        val (host, port, doh) = if (s.startsWith("https://", ignoreCase = true)) {
            Triple("", 53, s)
        } else {
            val colon = s.lastIndexOf(':')
            if (colon > 0 && s.indexOf(':') == colon) {
                val h = s.substring(0, colon).trim()
                val p = s.substring(colon + 1).trim().toIntOrNull() ?: 53
                Triple(h, p, "")
            } else {
                Triple(s, 53, "")
            }
        }
        vm.saveDns(enabled = s.isNotBlank(), host, port, doh)
        vm.resetDnsTest()
    }

    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rowsFocus.requestFocus() } }

    // The switch, then (while it is on) the presets, the server and Test DNS; the explanation in the panel.
    val explanation = stringResource(R.string.settings_dns_explanation)
    val limits = stringResource(R.string.settings_dns_limitations)
    val notes: @Composable () -> Unit = {
        Text(limits, style = tv.own.owntv.ui.theme.stageText(16, 500), color = tv.own.owntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(top = 14.mpx))
    }
    val rows = if (toggleOn) 5 else 1
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_app)),
        title = stringResource(R.string.settings_dns),
        count = pluralStringResource(R.plurals.settings_setting_count, rows, rows),
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
    ) {
        val useTitle = stringResource(R.string.settings_dns_use_custom)
        val useValue = SettingValue.Switch(effectiveEnabled)
        StageSettingRow(
            icon = OwnTVIcon.DNS,
            title = useTitle,
            desc = if (toggleOn && !serverConfigured) stringResource(R.string.settings_dns_server_missing)
                else stringResource(R.string.settings_dns_toggle_description),
            value = useValue,
            onClick = { applyToggle(!toggleOn) },
            help = settingHelp(null, useTitle, explanation, useValue, pinnable = false).copy(text = explanation, extra = notes),
        )
        // Simple conditional visibility — AnimatedVisibility interferes with D-pad focus on TV.
        if (toggleOn) {
            val presets = DohPresets.all
            val at = presets.indexOfFirst { it.second == server.trim() }
            val presetTitle = stringResource(R.string.settings_presets)
            val presetValue = SettingValue.Segmented(presets.map { it.first }, at)
            val pick: (Int) -> Unit = { i ->
                server = presets[i].second
                applySave()
            }
            StageSettingRow(
                icon = OwnTVIcon.DNS,
                title = presetTitle,
                desc = null,
                value = presetValue,
                onClick = { pick(if (at < 0) 0 else (at + 1) % presets.size) },
                onStep = { d -> pick(((if (at < 0) -1 else at) + d).coerceIn(0, presets.size - 1)) },
                help = SettingHelp(presetTitle, explanation, presets.map { it.first }, at, hints = settingHints(presetValue, pinnable = false), extra = notes),
            )
            val serverLabel = stringResource(R.string.settings_dns_server)
            StageFieldRow(
                OwnTVIcon.PENCIL, serverLabel, server, { server = it },
                SettingHelp(serverLabel, explanation, extra = notes),
                placeholder = stringResource(R.string.settings_dns_server_hint),
                onDone = { applySave() },
            )
            // The missing-server warning says "select Save", so Save stays a row of its own (the field
            // also saves when its keyboard closes).
            val saveTitle = stringResource(R.string.common_save)
            StageSettingRow(
                icon = OwnTVIcon.CHECK,
                title = saveTitle,
                desc = null,
                value = null,
                onClick = { applySave() },
                help = SettingHelp(saveTitle, explanation, hints = settingHints(null, pinnable = false), extra = notes),
            )
            val testTitle = stringResource(if (dnsTestState is SettingsViewModel.DnsTestState.Testing) R.string.settings_testing else R.string.settings_dns_test)
            StageSettingRow(
                icon = OwnTVIcon.REFRESH,
                title = testTitle,
                desc = dnsTestText(dnsTestState),
                value = null,
                onClick = {
                    val s = server.trim()
                    val doh = if (s.startsWith("https://", ignoreCase = true)) s else ""
                    vm.testDns(toggleOn, s, 53, doh)
                },
                help = SettingHelp(testTitle, explanation, hints = settingHints(null, pinnable = false), extra = notes),
            )
        }
    }
}

@Composable
private fun dnsTestText(state: SettingsViewModel.DnsTestState): String? = when (state) {
    is SettingsViewModel.DnsTestState.Ok -> stringResource(R.string.settings_dns_resolved, state.millis)
    is SettingsViewModel.DnsTestState.Fail -> state.failure.displayText()
    else -> null
}

@Composable
private fun SettingsViewModel.DnsTestFailure.displayText(): String = when (this) {
    SettingsViewModel.DnsTestFailure.ServerRequired -> stringResource(R.string.settings_dns_enter_server)
    SettingsViewModel.DnsTestFailure.ServerNotReachable -> stringResource(R.string.settings_dns_not_reachable)
    SettingsViewModel.DnsTestFailure.TimedOut -> stringResource(R.string.settings_dns_timed_out)
    SettingsViewModel.DnsTestFailure.NetworkUnreachable -> stringResource(R.string.settings_dns_network_unreachable)
    SettingsViewModel.DnsTestFailure.ConnectionRefused -> stringResource(R.string.settings_dns_connection_refused)
    is SettingsViewModel.DnsTestFailure.NoAddresses -> stringResource(R.string.settings_dns_no_addresses, host)
    is SettingsViewModel.DnsTestFailure.Unknown -> rawMessage
    SettingsViewModel.DnsTestFailure.Generic -> stringResource(R.string.settings_dns_test_failed)
}
