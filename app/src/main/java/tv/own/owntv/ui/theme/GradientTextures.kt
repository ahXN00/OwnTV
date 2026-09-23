package tv.own.owntv.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Large soft gradients drawn as small pre-rendered images.
 *
 * Measured on the TCL G10 (2026-09-23): a gradient brush painted over a panel costs the GPU ≈30 ms per
 * screen-sized pass, while stretching an image over the same area is close to free. The glass light
 * layer alone took a D-pad scroll frame from ≈25 ms to ≈180 ms. So each gradient is rendered ONCE, on the
 * CPU, by the same Android gradient shader the brush would have used, into a small bitmap; every frame
 * then draws that bitmap scaled — the same colours, stops and interpolation, bilinearly stretched.
 *
 * Only gradients that fade smoothly and cover large areas belong here. Thin lines, small focus sweeps and
 * anything with a hard edge stay ordinary brushes.
 */
private object GradientTextures {
    enum class Kind { RADIAL, VERTICAL, HORIZONTAL }

    private const val RADIAL_PX = 256
    private const val RAMP_PX = 256
    private const val RAMP_THICKNESS_PX = 4
    private const val MAX_ENTRIES = 32

    private data class Key(val kind: Kind, val colors: List<Int>, val positions: List<Float>?)

    private val cache = LinkedHashMap<Key, ImageBitmap>()

    @Synchronized
    fun get(kind: Kind, colors: List<Color>, positions: List<Float>? = null): ImageBitmap {
        val key = Key(kind, colors.map { it.toArgb() }, positions)
        cache[key]?.let { return it }
        // A few dozen distinct gradients exist app-wide. The bound only guards against an unexpected
        // caller producing unbounded keys; each entry is at most 256 KB.
        if (cache.size >= MAX_ENTRIES) cache.clear()
        return render(key).asImageBitmap().also { cache[key] = it }
    }

    private fun render(key: Key): Bitmap {
        val colors = key.colors.toIntArray()
        val positions = key.positions?.toFloatArray()
        val (w, h) = when (key.kind) {
            Kind.RADIAL -> RADIAL_PX to RADIAL_PX
            Kind.VERTICAL -> RAMP_THICKNESS_PX to RAMP_PX
            Kind.HORIZONTAL -> RAMP_PX to RAMP_THICKNESS_PX
        }
        val shader = when (key.kind) {
            Kind.RADIAL -> RadialGradient(w / 2f, h / 2f, w / 2f, colors, positions, Shader.TileMode.CLAMP)
            Kind.VERTICAL -> LinearGradient(0f, 0f, 0f, h.toFloat(), colors, positions, Shader.TileMode.CLAMP)
            Kind.HORIZONTAL -> LinearGradient(0f, 0f, w.toFloat(), 0f, colors, positions, Shader.TileMode.CLAMP)
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { this.shader = shader })
        return bitmap
    }
}

/**
 * Same pixels as `drawRect(Brush.radialGradient(colors, center, radius))` over this scope's bounds, with
 * the whole draw faded by [alpha]. The last colour must be fully transparent (the area beyond [radius]
 * is then empty, as the brush's clamp would leave it).
 */
fun DrawScope.drawRadialGlow(colors: List<Color>, center: Offset, radius: Float, alpha: Float = 1f) {
    if (radius <= 0f || alpha <= 0f) return
    val texture = GradientTextures.get(GradientTextures.Kind.RADIAL, colors)
    // Snap outward to whole pixels: the texture's rim is transparent, so a slightly larger circle is
    // invisible while an integer destination avoids a sub-pixel seam.
    val left = floor(center.x - radius).toInt()
    val top = floor(center.y - radius).toInt()
    val side = ceil(radius * 2f).toInt() + 1
    clipRect {
        drawImage(
            image = texture,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(side, side),
            alpha = alpha.coerceAtMost(1f),
        )
    }
}

/**
 * Same pixels as `drawRect(Brush.verticalGradient(colors, startY, endY))` over this scope's bounds, with
 * the whole draw faded by [alpha]. The first colour must be fully transparent (nothing is drawn above
 * [startY]); below [endY] the last colour continues, as the brush's clamp does.
 */
fun DrawScope.drawVerticalFade(colors: List<Color>, startY: Float, endY: Float, alpha: Float = 1f) {
    if (endY <= startY || alpha <= 0f) return
    val texture = GradientTextures.get(GradientTextures.Kind.VERTICAL, colors)
    val top = floor(startY).toInt()
    val bottom = ceil(endY).toInt()
    clipRect {
        drawImage(
            image = texture,
            dstOffset = IntOffset(0, top),
            dstSize = IntSize(ceil(size.width).toInt(), bottom - top),
            alpha = alpha.coerceAtMost(1f),
        )
        if (bottom < size.height) {
            drawRect(
                color = colors.last(),
                topLeft = Offset(0f, bottom.toFloat()),
                size = Size(size.width, size.height - bottom),
                alpha = alpha.coerceAtMost(1f),
            )
        }
    }
}

/**
 * Same pixels as `Modifier.background(Brush.verticalGradient(*stops))` — or `horizontalGradient` when
 * [vertical] is false — spanning the whole element, drawn from a cached image.
 */
fun Modifier.gradientWash(vertical: Boolean, vararg stops: Pair<Float, Color>): Modifier {
    val colors = stops.map { it.second }
    val positions = stops.map { it.first }
    val kind = if (vertical) GradientTextures.Kind.VERTICAL else GradientTextures.Kind.HORIZONTAL
    return drawWithCache {
        val texture = GradientTextures.get(kind, colors, positions)
        val dstSize = IntSize(ceil(size.width).toInt(), ceil(size.height).toInt())
        onDrawBehind { drawImage(image = texture, dstSize = dstSize) }
    }
}

/** [gradientWash] with evenly spaced [colors], like `Brush.verticalGradient(listOf(...))`. */
fun Modifier.gradientWash(vertical: Boolean, colors: List<Color>): Modifier =
    gradientWash(vertical, *colors.mapIndexed { i, c -> i / (colors.size - 1f) to c }.toTypedArray())
