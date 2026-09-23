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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

/** This screen's rows as Settings search finds them (the host/port fields are found by the proxy keywords). */
internal val PROXY_SEARCH_ROWS: List<Int> = listOf(R.string.settings_use_proxy, R.string.settings_test_proxy)

/**
 * Network → Proxy: one app-wide HTTP proxy. Enabling it routes all app traffic (playlist,
 * Xtream API, EPG, images, downloads, updates, ExoPlayer) and mpv playback through the proxy.
 */
@Composable
fun NetworkSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: SettingsViewModel = koinViewModel()
    val config by vm.proxyConfig.collectAsStateWithLifecycle()
    val testState by vm.proxyTest.collectAsStateWithLifecycle()

    var seeded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    LaunchedEffect(config) {
        if (!seeded) {
            enabled = config.enabled
            host = config.host
            port = if (config.port > 0) config.port.toString() else ""
            user = config.username
            pass = config.password
            seeded = true
        }
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onBack() }

    val portInt = port.trim().toIntOrNull() ?: 0
    val save = {
        vm.saveProxy(enabled, host, portInt, user, pass)
        vm.resetProxyTest()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Header(stringResource(R.string.common_proxy), onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel(stringResource(R.string.settings_http_proxy))
        Row2(
            icon = OwnTVIcon.NETWORK,
            title = stringResource(R.string.settings_use_proxy),
            desc = stringResource(R.string.settings_proxy_description),
            chip = if (enabled) stringResource(R.string.common_on) else stringResource(R.string.common_off), primaryChip = enabled,
            modifier = Modifier.focusRequester(firstFocus),
            onClick = { enabled = !enabled; save() },
        )

        Spacer(Modifier.height(12.dp))
        OwnTVTextField(
            value = host,
            onValueChange = { host = it },
            label = stringResource(R.string.settings_host),
            placeholder = stringResource(R.string.settings_proxy_host_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OwnTVTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
            label = stringResource(R.string.settings_port),
            placeholder = stringResource(R.string.settings_proxy_port_hint),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.width(220.dp),
        )
        Spacer(Modifier.height(12.dp))
        OwnTVTextField(
            value = user,
            onValueChange = { user = it },
            label = stringResource(R.string.settings_username_optional),
            placeholder = "",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OwnTVTextField(
            value = pass,
            onValueChange = { pass = it },
            label = stringResource(R.string.settings_password_optional),
            placeholder = "",
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OwnTVButton(stringResource(R.string.common_save), onClick = { save() })
            OwnTVButton(
                label = if (testState is SettingsViewModel.ProxyTestState.Testing) stringResource(R.string.settings_testing) else stringResource(R.string.settings_test_proxy),
                onClick = { vm.testProxy(host, portInt, user, pass) },
                style = OwnTVButtonStyle.SECONDARY,
            )
            ProxyTestLabel(testState)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.settings_proxy_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_proxy_limitations),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProxyTestLabel(state: SettingsViewModel.ProxyTestState) {
    val colors = OwnTVTheme.colors
    val text = when (state) {
        is SettingsViewModel.ProxyTestState.Ok -> stringResource(R.string.settings_proxy_connected, state.millis)
        is SettingsViewModel.ProxyTestState.Fail -> when (val failure = state.failure) {
            SettingsViewModel.ProxyFailure.InvalidAddress -> stringResource(R.string.settings_proxy_invalid_address)
            SettingsViewModel.ProxyFailure.HostUnreachable -> stringResource(R.string.settings_proxy_host_unreachable)
            SettingsViewModel.ProxyFailure.TimedOut -> stringResource(R.string.settings_proxy_timed_out)
            SettingsViewModel.ProxyFailure.ConnectionFailed -> stringResource(R.string.settings_proxy_connection_failed)
            is SettingsViewModel.ProxyFailure.Http -> stringResource(R.string.settings_proxy_http, failure.code)
            is SettingsViewModel.ProxyFailure.Unknown -> failure.rawMessage ?: stringResource(R.string.settings_proxy_failed)
        }
        else -> null
    }
    if (text != null) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (state is SettingsViewModel.ProxyTestState.Ok) colors.primary else androidx.compose.ui.graphics.Color(0xFFEF4444))
    }
}
