package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.network.DohPresets
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

/** This screen's rows as Settings search finds them. */
internal val DNS_SEARCH_ROWS: List<Int> =
    listOf(R.string.settings_dns_use_custom, R.string.settings_dns_server, R.string.settings_dns_test)

@Composable
fun DnsSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
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

    val toggleFocus = remember { FocusRequester() }
    val firstPresetFocus = remember { FocusRequester() }
    val serverFieldFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { toggleFocus.requestFocus() } }

    // When toggle turns ON, move focus to the first preset button after layout.
    // When toggle turns OFF, return focus to the toggle row.
    LaunchedEffect(toggleOn) {
        kotlinx.coroutines.delay(60)
        if (toggleOn) {
            runCatching { firstPresetFocus.requestFocus() }
        } else {
            runCatching { toggleFocus.requestFocus() }
        }
    }

    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .focusProperties { onEnter = { runCatching { toggleFocus.requestFocus() } } }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Header(stringResource(R.string.settings_dns), onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel(stringResource(R.string.settings_dns_custom))
        Row2(
            icon = OwnTVIcon.DNS,
            title = stringResource(R.string.settings_dns_use_custom),
            desc = stringResource(R.string.settings_dns_toggle_description),
            chip = stringResource(if (effectiveEnabled) R.string.common_on else R.string.common_off),
            primaryChip = effectiveEnabled,
            modifier = Modifier
                .focusRequester(toggleFocus)
                .focusProperties { if (toggleOn) down = firstPresetFocus },
            onClick = { applyToggle(!toggleOn) },
        )

        // Red warning: toggle is on but no server configured
        if (toggleOn && !serverConfigured) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_dns_server_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFEF4444),
            )
        }

        // Simple conditional visibility — AnimatedVisibility interferes with D-pad focus on TV.
        if (toggleOn) {
            Column {
                Spacer(Modifier.height(12.dp))

                // DoH preset chips — first chip gets focus when toggle turns on.
                // Explicit vertical links: the 2D focus search otherwise jumps straight from the
                // preset row to the header / Save, skipping the toggle above and the field below.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.focusProperties {
                        up = toggleFocus
                        down = serverFieldFocus
                    },
                ) {
                    var first = true
                    for ((label, url) in DohPresets.all) {
                        val isActive = server.trim() == url
                        OwnTVButton(
                            label = label,
                            onClick = {
                                server = url
                                toggleOn = true
                                applySave()
                            },
                            style = if (isActive) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                            modifier = if (first) {
                                first = false
                                Modifier.focusRequester(firstPresetFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OwnTVTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = stringResource(R.string.settings_dns_server),
                    placeholder = stringResource(R.string.settings_dns_server_hint),
                    focusRequester = serverFieldFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            up = firstPresetFocus
                            down = saveFocus
                        },
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.focusProperties { up = serverFieldFocus },
                ) {
                    OwnTVButton(stringResource(R.string.common_save), onClick = { applySave() }, modifier = Modifier.focusRequester(saveFocus))
                    OwnTVButton(
                        label = stringResource(
                            if (dnsTestState is SettingsViewModel.DnsTestState.Testing) R.string.settings_testing
                            else R.string.settings_dns_test,
                        ),
                        onClick = {
                            val s = server.trim()
                            val doh = if (s.startsWith("https://", ignoreCase = true)) s else ""
                            vm.testDns(toggleOn, s, 53, doh)
                        },
                        style = OwnTVButtonStyle.SECONDARY,
                    )
                    DnsTestLabel(dnsTestState)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.settings_dns_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_dns_limitations),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun DnsTestLabel(state: SettingsViewModel.DnsTestState) {
    val colors = OwnTVTheme.colors
    val (text, color) = when (state) {
        is SettingsViewModel.DnsTestState.Ok -> stringResource(
            R.string.settings_dns_resolved,
            state.millis,
        ) to colors.primary
        is SettingsViewModel.DnsTestState.Fail -> state.failure.displayText() to Color(0xFFEF4444)
        else -> null to colors.onSurfaceVariant
    }
    if (text != null) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
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
