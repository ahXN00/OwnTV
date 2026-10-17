package tv.own.owntv.features.customize

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
import tv.own.owntv.features.categorylock.PinAttempt
import tv.own.owntv.features.categorylock.PinAttemptLimiter
import tv.own.owntv.features.categorylock.pinEntryTitle
import tv.own.owntv.features.profiles.PinDialog
import tv.own.owntv.features.shell.components.StageContentTop
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * The Customize screen behind its PIN. This PIN also takes every category lock off, so wrong entries
 * count against the same [PinAttemptLimiter] as the category locks: guessing here earns the same wait.
 */
@Composable
internal fun CustomizePinGate(storedPin: String, onUnlocked: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
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
    Column(
        modifier = modifier
            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp, top = StageContentTop)
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text(stringResource(R.string.settings_customize_title), style = stageText(42, 800), color = StageColors.Text)
        Text(
            stringResource(R.string.settings_customize_pin_locked),
            style = stageText(18, 500),
            color = StageColors.Muted,
            modifier = Modifier.padding(top = 10.mpx),
        )
    }
    key(attempt) {
        PinDialog(
            title = pinEntryTitle(wrong = wrong, waitSeconds = waitSeconds),
            onSubmit = { entered ->
                when (val result = limiter.attempt { Pin.verifyCustomize(entered, storedPin) }) {
                    PinAttempt.Accepted -> onUnlocked()
                    PinAttempt.Wrong -> { wrong = true; attempt++ }
                    is PinAttempt.Wait -> { wrong = false; waitSeconds = result.seconds; attempt++ }
                }
            },
            onDismiss = onBack,
            compact = true,
        )
    }
}
