package tv.own.owntv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Lightweight indeterminate spinner drawn with Canvas (no Material dependency).
 *
 * It turns in [SPINNER_STEPS] steps rather than every frame. On a TV each frame redraws the whole
 * window — about 32 ms of render work on a TCL G10 — so a spinner animated per frame kept the GPU
 * busy for as long as it showed: a quarter of the app's CPU during a catalogue import, which is
 * also when the sync pill's spinner runs. Only the draw phase reads the step, so a step costs one
 * redraw and no recomposition.
 */
@Composable
fun OwnTVSpinner(
    modifier: Modifier = Modifier,
    sizeDp: Int = 44,
    color: Color = tv.own.owntv.ui.theme.stageAccent.accent,
) {
    val step = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SPINNER_TURN_MS / SPINNER_STEPS)
            step.intValue = (step.intValue + 1) % SPINNER_STEPS
        }
    }
    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val d = size.minDimension
        val stroke = d * 0.11f
        drawArc(
            color = color,
            startAngle = step.intValue * (360f / SPINNER_STEPS),
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(d - stroke, d - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** One turn of [OwnTVSpinner], as before it was stepped. */
private const val SPINNER_TURN_MS = 900L

/** Positions per turn: 16 is about 18 redraws a second instead of the display's 60. */
private const val SPINNER_STEPS = 16
