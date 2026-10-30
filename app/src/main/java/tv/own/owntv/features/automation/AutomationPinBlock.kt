package tv.own.owntv.features.automation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** The PIN, large, with the seconds left; the window closes itself when they run out. */
@Composable
internal fun PinBlock(open: PairingWindow.Open, onExpired: () -> Unit) {
    val secondsLeft by produceState(secondsUntil(open.expiresAt), open) {
        while (value > 0) {
            delay(1_000)
            value = secondsUntil(open.expiresAt)
        }
        onExpired()
    }
    Column(Modifier.padding(start = 24.mpx, top = 8.mpx, bottom = 12.mpx)) {
        Text(open.pin, style = stageText(56, 800), color = StageColors.Text)
        Text(
            stringResource(R.string.automation_pin_hint, secondsLeft / 60, secondsLeft % 60),
            style = stageText(18, 500), color = StageColors.Muted,
        )
    }
}

internal fun secondsUntil(at: Long): Int = ((at - System.currentTimeMillis()) / 1_000).toInt().coerceAtLeast(0)
