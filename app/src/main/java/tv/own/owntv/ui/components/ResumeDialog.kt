package tv.own.owntv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Small "Resume playback?" prompt shown (in the "Ask to resume" mode) when a movie/episode has a
 * saved position. Resume is pre-focused; Back dismisses without starting playback.
 */
@Composable
fun ResumeDialog(
    positionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    // Lighter scrim + translucent card: the paused video stays visible behind the prompt.
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(padding = 28.dp, fill = colors.surfaceContainerHigh.copy(alpha = 0.88f))) {
            Text(stringResource(R.string.common_resume_prompt), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.common_resume_position, formatTimestamp(positionMs)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_start_over), onClick = onStartOver, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.common_resume), onClick = onResume, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(focus))
            }
        }
    }
}

/**
 * 0:42 / 23:45 / 1:23:45 style timestamp — the app's one duration format.
 *
 * Used for a resume position, the player's elapsed/remaining readout and the audio bar alike. The
 * player used to carry two private copies of this arithmetic against a duplicate pair of string
 * resources (`player_track_*`), which were byte-identical to these in every locale.
 */
@Composable
fun formatTimestamp(ms: Long): String = formatTimestamp(LocalResources.current, ms)

/** The same format outside composition — a value read at a button press rather than on every tick. */
fun formatTimestamp(res: android.content.res.Resources, ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        res.getString(R.string.common_timestamp_hours, h, m, s)
    } else {
        res.getString(R.string.common_timestamp_minutes, m, s)
    }
}
