package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.ui.theme.mpx

/**
 * QR + PIN panel for handing a TMDB key over from another device.
 *
 * Deliberately a dialog rather than a screen: it is a short-lived side trip from the field it fills,
 * and keeping it here avoids threading a new route through the settings navigation for something the
 * user sees once.
 *
 * The QR encodes the LAN URL only — never the PIN, which is shown on the TV and typed on the remote device.
 * A photographed QR on its own therefore cannot push a key. The listener starts when the dialog opens
 * and is stopped on dispose, so it never outlives the panel. Private for the automation API
 * ([SensitiveScreenMarker]): the PIN on it lets a phone push a key or a login.
 */
@Composable
internal fun CompanionKeyDialog(
    titleRes: Int,
    state: tv.own.owntv.core.companion.CompanionServerState,
    onStart: (Int) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    SensitiveScreenMarker()
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        onStart(tv.own.owntv.core.companion.CompanionLink.DEFAULT_PORT)
        kotlinx.coroutines.delay(60)
        runCatching { closeFocus.requestFocus() }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onStop() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(titleRes),
        width = 820.mpx,
        buttons = { tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(closeFocus)) },
    ) { tv.own.owntv.ui.components.StageCompanionStatus(state) }
}
