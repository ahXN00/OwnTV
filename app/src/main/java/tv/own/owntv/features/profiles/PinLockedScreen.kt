package tv.own.owntv.features.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.util.Pin
import tv.own.owntv.core.categorylock.PinAttempt
import tv.own.owntv.core.categorylock.PinAttemptLimiter
import tv.own.owntv.features.categorylock.pinEntryTitle
import tv.own.owntv.ui.theme.mpx

/**
 * Stands in for a PIN-locked screen until the PIN is entered: the screen's [title], a "PIN-locked"
 * note, and the PIN prompt over them (see [PinPrompt]); Back or Cancel calls [onDismiss]. [pin] is the
 * stored value — a salted hash, or a PIN saved before hashing.
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
 * up and says so; Back or Cancel calls [onDismiss]. Every door asks the Customize PIN, which also takes
 * the category locks off, so wrong entries count against the same [PinAttemptLimiter] as those locks:
 * guessing at any of them earns the same wait.
 */
@Composable
internal fun PinPrompt(pin: String, onUnlock: () -> Unit, onDismiss: () -> Unit) {
    val limiter: PinAttemptLimiter = koinInject()
    var wrong by remember { mutableStateOf(false) }
    var waitSeconds by remember { mutableIntStateOf(limiter.waitSeconds()) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(waitSeconds > 0) {
        while (waitSeconds > 0) {
            delay(1_000)
            waitSeconds = limiter.waitSeconds()
        }
    }
    key(attempt) {
        PinDialog(
            title = pinEntryTitle(wrong = wrong, waitSeconds = waitSeconds),
            onSubmit = { entered ->
                when (val result = limiter.attempt { Pin.verifyCustomize(entered, pin) }) {
                    PinAttempt.Accepted -> onUnlock()
                    PinAttempt.Wrong -> { wrong = true; attempt++ }
                    is PinAttempt.Wait -> { wrong = false; waitSeconds = result.seconds; attempt++ }
                }
            },
            onDismiss = onDismiss,
            compact = true,
        )
    }
}
