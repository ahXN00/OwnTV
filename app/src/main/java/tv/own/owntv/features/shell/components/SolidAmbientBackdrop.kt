package tv.own.owntv.features.shell.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.drawRadialGlow

/**
 * Setup-wizard-style radiance for the solid shell. It is a single lightweight Canvas overlay: no
 * blur or per-card work; the glow itself is one small cached gradient image. Glass mode already gets
 * depth from its wallpaper/frost and skips this layer entirely.
 */
@Composable
fun SolidAmbientBackdrop(
    glowEnabled: Boolean,
    pulseEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val colors = OwnTVTheme.colors
    if (!glowEnabled || glass.enabled || !colors.isDark) return

    val primary = colors.primary
    val transition = if (pulseEnabled) rememberInfiniteTransition(label = "solidAmbientPulse") else null
    val ringScale = transition?.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "solidAmbientRingScale",
    )
    val ringPresence = transition?.animateFloat(
        initialValue = 0.38f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "solidAmbientRingPresence",
    )
    val scale by ringScale ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val presence by ringPresence ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }

    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.54f, size.height * 0.45f)
        val glowRadius = size.minDimension * 0.46f
        // A cached gradient image, not a brush: a screen-sized gradient is the costliest draw on
        // low-end TV GPUs, and the pulse below redraws this every frame (see GradientTextures.kt).
        drawRadialGlow(
            colors = listOf(
                primary.copy(alpha = 0.14f),
                primary.copy(alpha = 0.052f),
                Color.Transparent,
            ),
            center = center,
            radius = glowRadius,
        )
        if (pulseEnabled) {
            drawCircle(
                color = primary.copy(alpha = 0.20f * presence),
                radius = size.minDimension * 0.34f * scale,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
