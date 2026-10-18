package tv.own.owntv.features.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.util.Pin
import tv.own.owntv.ui.theme.mpx

/**
 * Stands in for a PIN-locked screen until the PIN is entered: the screen's [title], a "PIN-locked"
 * note, and the PIN prompt over them. A wrong PIN keeps the prompt up and says so; Back or Cancel
 * calls [onDismiss]. [pin] is the stored value — a salted hash, or a PIN saved before hashing.
 */
@Composable
internal fun PinLockedScreen(
    title: String,
    pin: String,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp, top = tv.own.owntv.features.shell.components.StageContentTop)
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text(title, style = tv.own.owntv.ui.theme.stageText(42, 800), color = tv.own.owntv.ui.theme.StageColors.Text)
        Text(
            stringResource(R.string.settings_customize_pin_locked),
            style = tv.own.owntv.ui.theme.stageText(18, 500),
            color = tv.own.owntv.ui.theme.StageColors.Muted,
            modifier = Modifier.padding(top = 10.mpx),
        )
    }
    PinPrompt(pin = pin, onUnlock = onUnlock, onDismiss = onDismiss)
}

/**
 * The PIN prompt on its own, for a door that stays on screen behind it. A wrong PIN keeps the prompt
 * up and says so; Back or Cancel calls [onDismiss].
 */
@Composable
internal fun PinPrompt(pin: String, onUnlock: () -> Unit, onDismiss: () -> Unit) {
    var pinError by remember { mutableStateOf(false) }
    PinDialog(
        title = stringResource(if (pinError) R.string.settings_customize_wrong_pin else R.string.settings_customize_enter_pin),
        onSubmit = { entered ->
            if (entered == pin || Pin.verify(entered, pin)) {
                onUnlock()
                pinError = false
            } else {
                pinError = true
            }
        },
        onDismiss = onDismiss,
        compact = true,
    )
}
