package tv.own.owntv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.own.owntv.core.theme.GlassConfig
import tv.own.owntv.core.theme.GlassPreset
import tv.own.owntv.core.theme.GlassSurface

/**
 * Glass effect — translucent frosted surface treatment.
 *
 * Each surface that can go glassy is tagged with a [GlassSurface]. When the feature is enabled
 * a panel whose surface is in [GlassConfig.scope] renders with a translucent fill and directional
 * edge light. A wallpaper adds real cached backdrop frost; without one the same roles use a tonal
 * ceramic-glass fallback so contrast remains predictable.
 *
 * The fullscreen player is intentionally NOT a [GlassSurface] — it covers the shell when visible,
 * so leaving it opaque is correct.
 */
/** Surface interaction passed to the material renderer; only FOCUSED receives the rich light lens. */
enum class GlassInteraction { IDLE, SELECTED, FOCUSED, PRESSED }

/** Material role shared by every surface; this replaces independent per-widget tuning tables. */
enum class GlassDepth { FLOATING, CHROME, CONTAINER, INLINE }

@Immutable
data class GlassMaterial(
    val tintDelta: Float,
    val lensPeak: Float,
    val rimIdle: Float,
    val rimSelected: Float,
    val rimFocused: Float,
    val edgeDark: Float,
    val depth: GlassDepth,
    val participatesInFrost: Boolean,
)

private val FloatingGlassMaterial = GlassMaterial(0.12f, 0.30f, 0.18f, 0.34f, 0.78f, 0.30f, GlassDepth.FLOATING, true)
private val ChromeGlassMaterial = GlassMaterial(0.04f, 0.24f, 0.15f, 0.34f, 0.72f, 0.22f, GlassDepth.CHROME, true)
private val ContainerGlassMaterial = GlassMaterial(0f, 0.20f, 0.11f, 0.32f, 0.68f, 0.18f, GlassDepth.CONTAINER, true)
private val InlineGlassMaterial = GlassMaterial(-0.08f, 0.18f, 0.06f, 0.30f, 0.64f, 0.14f, GlassDepth.INLINE, false)

val GlassSurface.material: GlassMaterial
    get() = when (this) {
        GlassSurface.DIALOGS -> FloatingGlassMaterial
        GlassSurface.SIDEBAR, GlassSurface.TOPBAR, GlassSurface.MINI_PLAYER,
        GlassSurface.PLAYER_CONTROLS,
        -> ChromeGlassMaterial
        GlassSurface.TOASTS -> FloatingGlassMaterial
        GlassSurface.PANELS, GlassSurface.PREVIEW -> ContainerGlassMaterial
        GlassSurface.CARDS -> InlineGlassMaterial
    }

/** Ambient nesting floor. Screen-level providers can raise it for exceptional deeper stacks. */
val LocalGlassDepth = compositionLocalOf { 0 }

@Immutable
data class GlassLayerTreatment(
    val frost: Boolean,
    val glint: Boolean,
    val brightRim: Boolean,
    val darkEdge: Boolean,
)

fun glassLayerTreatment(layer: Int): GlassLayerTreatment = when {
    layer <= 1 -> GlassLayerTreatment(frost = true, glint = true, brightRim = true, darkEdge = true)
    layer == 2 -> GlassLayerTreatment(frost = false, glint = true, brightRim = true, darkEdge = true)
    else -> GlassLayerTreatment(frost = false, glint = false, brightRim = false, darkEdge = true)
}

private fun GlassSurface.defaultLayer(): Int = when (this) {
    GlassSurface.PREVIEW, GlassSurface.CARDS -> 2
    else -> 1
}

/** Every surface that can be glassed. Used to implement the "All" master tick. */
/**
 * The surfaces this app can actually render as glass.
 *
 * `PLAYER_CONTROLS` and `TOASTS` were added to core for the phone's player chrome; the ten-foot HUD
 * does not use them yet, and a switch that does nothing is worse than no switch — nor should "all
 * surfaces on" mean a state the user can never reach by hand. They join this set on the day the TV
 * HUD honours them.
 */
val ALL_GLASS_SURFACES: Set<GlassSurface> =
    GlassSurface.entries.toSet() - setOf(GlassSurface.PLAYER_CONTROLS, GlassSurface.TOASTS)

/** Ambient glass state. Default is "off" so the app looks unchanged until a background image is set. */
val LocalGlass = compositionLocalOf { GlassConfig() }

/** True when the active screen's content has moved beneath persistent shell chrome. */
val LocalContentScrolled = compositionLocalOf { false }

/**
 * Which [GlassSurface] an ambient action control (e.g. `OwnTVButton`) should frost with, or null to
 * stay flat. Used by the shared action pill so a button frosts with whatever surface its host renders
 * on — `DIALOGS` inside a popup, `CARDS` on a settings panel — without each call site passing a flag.
 *
 * Defaults to [GlassSurface.DIALOGS]: most action pills live in dialogs/popups, and a `Popup` does
 * NOT inherit the screen's CompositionLocals, so each popup would otherwise need its own provider.
 * The DIALOGS default means dialog buttons are correct with zero per-dialog wiring; screens whose
 * buttons sit on a panel (e.g. the settings list, Customize) override it to `CARDS`, and surfaces that
 * should never frost (the fullscreen player, over opaque video) provide null.
 */
val LocalActionSurface = compositionLocalOf<GlassSurface?> { GlassSurface.DIALOGS }

/**
 * One shared focus-motion timeline for the whole app. A single scalar [Animatable] drives the
 * 140 ms glint/rim arrival and the 220 ms backdrop parallax, so rapid D-pad movement cancels the
 * previous motion instead of creating one queued animation per card.
 */
@Stable
class GlassMotionState(private val scope: CoroutineScope) {
    private val timeline = Animatable(1f)
    private var currentFocus: Any? = null
    private var lastFocusCenter: Offset? = null
    private var arrivalOffset = Offset.Zero
    private var parallaxStart = Offset.Zero
    private var parallaxTarget = Offset.Zero
    private var rootSize = Size.Zero
    private var density = 1f
    private var enabled = true

    fun setRootSize(size: Size, density: Float) {
        rootSize = size
        this.density = density.coerceAtLeast(0.1f)
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) scope.launch {
            timeline.stop()
            arrivalOffset = Offset.Zero
            parallaxStart = Offset.Zero
            parallaxTarget = Offset.Zero
            timeline.snapTo(1f)
        }
    }

    fun focusArrived(token: Any, center: Offset, travelPx: Float, animate: Boolean) {
        if (!center.x.isFinite() || !center.y.isFinite()) return
        if (!animate || !enabled) {
            currentFocus = token
            lastFocusCenter = center
            scope.launch {
                timeline.stop()
                arrivalOffset = Offset.Zero
                parallaxStart = Offset.Zero
                parallaxTarget = Offset.Zero
                timeline.snapTo(1f)
            }
            return
        }
        if (currentFocus === token) {
            updateFocusedPosition(token, center)
            return
        }
        val previous = lastFocusCenter
        val delta = if (previous == null) Offset.Zero else center - previous
        val direction = when {
            kotlin.math.abs(delta.x) >= kotlin.math.abs(delta.y) && delta.x != 0f -> Offset(-kotlin.math.sign(delta.x), 0f)
            delta.y != 0f -> Offset(0f, -kotlin.math.sign(delta.y))
            else -> Offset.Zero
        }
        currentFocus = token
        lastFocusCenter = center
        scope.launch {
            val currentParallax = parallaxOffset()
            timeline.stop()
            arrivalOffset = direction * travelPx
            parallaxStart = currentParallax
            parallaxTarget = targetParallax(center)
            timeline.snapTo(0f)
            timeline.animateTo(1f, tween(durationMillis = 220, easing = LinearEasing))
        }
    }

    fun updateFocusedPosition(token: Any, center: Offset) {
        if (currentFocus !== token || !center.x.isFinite() || !center.y.isFinite() || !enabled) return
        lastFocusCenter = center
        parallaxTarget = targetParallax(center)
    }

    private fun targetParallax(center: Offset): Offset {
        if (rootSize.width <= 0f || rootSize.height <= 0f) return Offset.Zero
        return Offset(
            x = (center.x / rootSize.width - 0.5f) * 12f * density,
            y = (center.y / rootSize.height - 0.5f) * 8f * density,
        )
    }

    private fun eased(durationMs: Float): Float {
        val local = (timeline.value * 220f / durationMs).coerceIn(0f, 1f)
        return FastOutSlowInEasing.transform(local)
    }

    fun glintOffset(): Offset = if (enabled) arrivalOffset * (1f - eased(140f)) else Offset.Zero

    fun focusedRimScale(): Float = if (enabled) {
        androidx.compose.ui.util.lerp(0.92f / 0.78f, 1f, eased(140f))
    } else 1f

    fun parallaxOffset(): Offset {
        if (!enabled) return Offset.Zero
        val fraction = eased(220f)
        return Offset(
            androidx.compose.ui.util.lerp(parallaxStart.x, parallaxTarget.x, fraction),
            androidx.compose.ui.util.lerp(parallaxStart.y, parallaxTarget.y, fraction),
        )
    }
}

val LocalGlassMotion = compositionLocalOf<GlassMotionState?> { null }

/**
 * Holder for the single shared blurred copy of the background image (Phase 4 backdrop blur).
 * Provided by `MainActivity` when a background image is set AND [supportsBackdropBlur]; null
 * otherwise (panels then fall back to Tier-1 translucency over the sharp image).
 *
 * [rootSizePx] is the full app viewport size in pixels (the area the bitmap stands in for, since the
 * blurred bitmap is a downscaled stand-in). Each glass surface maps its on-screen rect from root px
 * → bitmap coords using [rootSizePx], then draws the matching slice translated to its own position so
 * the frost lines up with the photo behind it.
 */
@Stable
class BackdropLuminanceMap(
    private val columns: Int,
    private val rows: Int,
    private val values: FloatArray,
) {
    init {
        require(columns > 0 && rows > 0 && values.size == columns * rows)
    }

    /** Mean of a 4×4 sample across [bounds]; all work is 16 cached-float reads. */
    fun meanIn(bounds: Rect, rootSize: Size): Float {
        if (bounds.width <= 0f || bounds.height <= 0f || rootSize.width <= 0f || rootSize.height <= 0f) {
            return 0.5f
        }
        var total = 0f
        var count = 0
        repeat(4) { y ->
            val rootY = bounds.top + bounds.height * ((y + 0.5f) / 4f)
            val gridY = ((rootY / rootSize.height) * rows).toInt().coerceIn(0, rows - 1)
            repeat(4) { x ->
                val rootX = bounds.left + bounds.width * ((x + 0.5f) / 4f)
                val gridX = ((rootX / rootSize.width) * columns).toInt().coerceIn(0, columns - 1)
                total += values[gridY * columns + gridX]
                count++
            }
        }
        return if (count == 0) 0.5f else (total / count).coerceIn(0f, 1f)
    }
}

@Stable
data class BlurredBackdrop(
    /** Ten geometric mip levels, least → most diffuse. Empty on Tier-1-only devices. */
    val frostLevels: List<ImageBitmap>,
    val luminance: BackdropLuminanceMap,
    val rootSizePx: Size,
) {
    /** Exact 10% steps use one texture; preset values between steps cross-fade adjacent mips. */
    fun frostFor(strength: Float): FrostSelection? {
        if (frostLevels.isEmpty() || strength <= 0f) return null
        val position = (strength.coerceIn(0f, 1f) * frostLevels.size - 1f)
            .coerceIn(0f, (frostLevels.size - 1).toFloat())
        val lowerIndex = kotlin.math.floor(position).toInt()
        val upperIndex = kotlin.math.ceil(position).toInt()
        return FrostSelection(
            lower = frostLevels[lowerIndex],
            upper = frostLevels[upperIndex].takeIf { upperIndex != lowerIndex },
            upperWeight = position - lowerIndex,
        )
    }

    fun sampledLuminance(bounds: Rect): Float = luminance.meanIn(bounds, rootSizePx)
}

@Stable
data class FrostSelection(
    val lower: ImageBitmap,
    val upper: ImageBitmap?,
    val upperWeight: Float,
)

/** Ambient blurred backdrop, or null when blur isn't active. See [BlurredBackdrop]. */
val LocalBlurredBackdrop = compositionLocalOf<BlurredBackdrop?> { null }

/**
 * Render this surface as glass: (Tier 2) a frosted slice of the blurred backdrop aligned to this
 * panel's on-screen position, then a translucent fill + a specular top-edge highlight, clipped to
 * [shape]. Falls back to Tier 1 (translucency + sheen, no frost) when there's no blurred backdrop
 * (no image / pre-API 31 / blurStrength 0). When the surface is not in scope the panel keeps its
 * normal solid [baseFill].
 *
 * Pass the [surface] so we honour the per-surface scope.
 *
 * @param baseFill the opaque colour this panel would normally use.
 * @param shape clip shape for the fill + highlight. Must match the container's own clip.
 */
@Composable
fun Modifier.glass(
    surface: GlassSurface,
    baseFill: Color,
    shape: Shape = RectangleShape,
    // Per-call frost multiplier (0..1) applied on top of the global blurStrength — lets small chrome
    // (e.g. top-bar chips) read as lighter glass than the big panels without changing the global setting.
    frostScale: Float = 1f,
    interaction: GlassInteraction = GlassInteraction.IDLE,
    idleRimAlpha: Float? = null,
    // Top-level top-bar/sidebar chrome gains density after content passes beneath it. Nested controls
    // keep this false so they never stack a second condensed material layer.
    condenseChrome: Boolean = false,
): Modifier {
    val config = LocalGlass.current
    // Fully transparent fill = nothing to render (e.g. an idle nav/list item whose highlight fill
    // is Color.Transparent). Skip both the frost and the background so glass() can be called
    // unconditionally on highlights that toggle between transparent (idle) and filled (focused).
    if (baseFill.alpha == 0f) return this
    // No glass for this surface → keep the solid fill and avoid subscribing solid chrome to scroll.
    if (!config.isGlassy(surface)) return this.background(baseFill, shape)

    val colors = OwnTVTheme.colors
    // The luminous rim IS the focus ring in glass mode — the solid-mode border is skipped entirely
    // (see FocusableSurface). So it has to follow the user's focus highlight (#121), not the accent.
    // With no highlight chosen, focusBorder == primary and the rim renders exactly as before.
    val accent = colors.focusBorder
    val customFocusRing = colors.focusBorder != colors.primary
    // A rim that is 62% white cannot show a chosen color, and a fixed 1.5 dp cannot show a chosen
    // thickness. Both only diverge from the shipped values once the user has actually picked one.
    val focusRingMix = if (customFocusRing) 0.85f else 0.38f
    val focusRingWidth = LocalFocusBorderWidth.current.let { if (it == Dimens.FocusBorderWidth) 1.5.dp else it }
    val motion = LocalGlassMotion.current
    val motionFocused = interaction == GlassInteraction.FOCUSED || interaction == GlassInteraction.PRESSED
    fun glintMotionOffset(): Offset = if (motionFocused) motion?.glintOffset() ?: Offset.Zero else Offset.Zero
    // 55% is the compatibility baseline: existing users keep the exact Phase 7 appearance.
    // The control only scales focused light, never the material tint or idle list rendering.
    val highlightScale = (config.highlightStrength / GlassConfig.DEFAULT_HIGHLIGHT_STRENGTH)
        .coerceIn(0f, 1f / GlassConfig.DEFAULT_HIGHLIGHT_STRENGTH)
    val material = surface.material
    val layer = maxOf(LocalGlassDepth.current, surface.defaultLayer())
    val treatment = glassLayerTreatment(layer)
    val chromeProgress = if (condenseChrome) {
        // Read the local only on explicitly tagged chrome. Cards/panels never subscribe to scroll
        // state, so crossing the threshold cannot fan out recomposition through a dense list.
        val contentScrolled = LocalContentScrolled.current
        val progress by animateFloatAsState(
            targetValue = if (contentScrolled) 1f else 0f,
            animationSpec = ownTvTween(160),
            label = "chromeCondense",
        )
        progress
    } else {
        0f
    }
    val roleAdjustment = if (condenseChrome) {
        androidx.compose.ui.util.lerp(material.tintDelta, 0.22f, chromeProgress)
    } else material.tintDelta
    val interactionAdjustment = when (interaction) {
        GlassInteraction.IDLE -> 0f
        GlassInteraction.SELECTED -> 0.06f
        GlassInteraction.FOCUSED -> -0.06f
        GlassInteraction.PRESSED -> 0.02f
    }
    val adaptiveEligible = material.depth == GlassDepth.FLOATING || material.depth == GlassDepth.CONTAINER
    fun tintAlpha(sampledLuma: Float?): Float {
        val floor = if (
            sampledLuma != null && adaptiveEligible && !config.allowFullTransparency
        ) {
            requiredLegibilityAlpha(
                backdropLuma = sampledLuma,
                fillLuma = baseFill.luminance(),
                textLuma = colors.onSurface.luminance(),
            )
        } else 0f
        return (maxOf(config.alpha, floor) + roleAdjustment + interactionAdjustment)
            .coerceIn(0.22f, 1f)
    }
    fun darkLensMix(sampledLuma: Float?): Float = when {
        !colors.isDark -> 1f
        sampledLuma == null -> 0f
        else -> ((sampledLuma - 0.32f) / 0.30f).coerceIn(0f, 1f)
    }
    val resolvedIdleRim = if (condenseChrome) {
        androidx.compose.ui.util.lerp(0.15f, 0.20f, chromeProgress)
    } else {
        idleRimAlpha ?: material.rimIdle
    }

    // Phase 4 — real backdrop blur. The single shared blurred bitmap (provided by MainActivity) is
    // drawn here as a slice aligned to this panel's on-screen position, so the frost matches the photo
    // region behind it. O(1) per panel: one textured draw.
    val blurred = LocalBlurredBackdrop.current
    val effectiveFrostScale = if (condenseChrome) {
        androidx.compose.ui.util.lerp(frostScale, 1f, chromeProgress)
    } else {
        frostScale
    }
    val frostAlpha = if (treatment.frost && material.participatesInFrost) {
        (config.blurStrength * effectiveFrostScale).coerceIn(0f, 1f)
    } else 0f
    // Dense lists can contain many idle cards. Their material already reads from frost + tint; reserving
    // the extra radial light and perimeter brushes for selected/focused states removes three GPU draws
    // per idle item without changing the interaction users actually track with the D-pad.
    val lightweightIdle = surface == GlassSurface.CARDS && interaction == GlassInteraction.IDLE

    // Custom 100% is a real opaque endpoint. Keep the material lens/rim, but skip backdrop sampling
    // entirely so the wallpaper cannot bleed through and the control truthfully reaches its maximum.
    if (config.alpha >= 0.995f) {
        return this.drawWithCache {
            val rimMotionScale = if (
                interaction == GlassInteraction.FOCUSED || interaction == GlassInteraction.PRESSED
            ) motion?.focusedRimScale() ?: 1f else 1f
            val body = if (lightweightIdle || !treatment.glint) null else createLuminousBody(
                material = material,
                interaction = interaction,
                tonal = true,
                highlightScale = highlightScale,
            )
            val rim = if (lightweightIdle) null else createLuminousRim(
                material = material,
                treatment = treatment,
                accent = accent,
                focusRingMix = focusRingMix,
                focusRingWidth = focusRingWidth,
                shape = shape,
                interaction = interaction,
                idleAlpha = resolvedIdleRim,
                highlightScale = highlightScale,
                focusedMotionScale = rimMotionScale,
            )
            onDrawWithContent {
                drawRect(baseFill)
                body?.let { drawLuminousBody(it, darkLensMix(null), glintMotionOffset()) }
                drawContent()
                rim?.let(::drawLuminousRim)
            }
        }
    }

    // No wallpaper is a deliberate tonal/ceramic material, not fake transparency over a flat colour.
    if (!config.hasBackdrop) {
        return this.drawWithCache {
            val rimMotionScale = if (
                interaction == GlassInteraction.FOCUSED || interaction == GlassInteraction.PRESSED
            ) motion?.focusedRimScale() ?: 1f else 1f
            val body = if (lightweightIdle || !treatment.glint) null else createLuminousBody(material = material, interaction = interaction, tonal = true, highlightScale = highlightScale)
            val rim = if (lightweightIdle) null else createLuminousRim(material = material, treatment = treatment, accent = accent, shape = shape, interaction = interaction, idleAlpha = resolvedIdleRim, highlightScale = highlightScale, focusedMotionScale = rimMotionScale, focusRingMix = focusRingMix, focusRingWidth = focusRingWidth)
            onDrawWithContent {
                drawRect(baseFill.copy(alpha = 0.94f))
                body?.let { drawLuminousBody(it, darkLensMix(null), glintMotionOffset()) }
                drawContent()
                rim?.let(::drawLuminousRim)
            }
        }
    }

    // No cached blur (pre-API 31 / blur off) → translucent material over the real wallpaper. Idle
    // cards deliberately use this lightweight path too: list scrolling no longer resamples the full
    // backdrop texture for every visible tile; focus promotes just the active tile to real frost.
    if (lightweightIdle) {
        return this.drawWithCache {
            onDrawWithContent {
                drawRect(baseFill.copy(alpha = tintAlpha(null)))
                drawContent()
            }
        }
    }

    // Capture this panel's rect only for the Tier-2 path that consumes it. Avoiding this remembered
    // state entirely for solid/no-wallpaper/Tier-1 surfaces substantially reduces list bookkeeping.
    val position = remember { GlassPositionState() }
    val frostSelection = blurred?.frostFor(frostAlpha)

    // Tier-1 device or Frost=0: retain adaptive sampling and material light without a texture draw.
    if (blurred == null || frostSelection == null) {
        return this
            .then(if (blurred != null && adaptiveEligible) GlassPositionElement(position) else Modifier)
            .drawWithCache {
                val rimMotionScale = if (
                    interaction == GlassInteraction.FOCUSED || interaction == GlassInteraction.PRESSED
                ) motion?.focusedRimScale() ?: 1f else 1f
                val body = if (!treatment.glint) null else createLuminousBody(material = material, interaction = interaction, tonal = false, highlightScale = highlightScale)
                val rim = createLuminousRim(material = material, treatment = treatment, accent = accent, shape = shape, interaction = interaction, idleAlpha = resolvedIdleRim, highlightScale = highlightScale, focusedMotionScale = rimMotionScale, focusRingMix = focusRingMix, focusRingWidth = focusRingWidth)
                onDrawWithContent {
                    val sampled = blurred?.sampledLuminance(position.bounds)
                    drawRect(baseFill.copy(alpha = tintAlpha(sampled)))
                    body?.let { drawLuminousBody(it, darkLensMix(sampled), glintMotionOffset()) }
                    drawContent()
                    drawLuminousRim(rim)
                }
            }
    }

    // Tier 2 — frost. Compose can't blur a node's own backdrop, so we approximate glassmorphism by
    // drawing the blurred slice OPAQUELY: it fully MASKS the sharp background photo that would
    // otherwise bleed through the translucent panel (a translucent blurred copy over a sharp original
    // is visually identical → no perceptible blur). frostAlpha then blends frost vs. the panel tint.
    val rootW = blurred.rootSizePx.width
    val rootH = blurred.rootSizePx.height
    return this
        .then(GlassPositionElement(position))
        .drawWithCache {
            // Gradients depend only on this node's size and material inputs. Cache them across frames;
            // the moving backdrop bounds stay in the draw lambda so scrolling does not rebuild them.
            val body = if (treatment.glint) createLuminousBody(material = material, interaction = interaction, tonal = false, highlightScale = highlightScale) else null
            val rimMotionScale = if (
                interaction == GlassInteraction.FOCUSED || interaction == GlassInteraction.PRESSED
            ) motion?.focusedRimScale() ?: 1f else 1f
            val rim = createLuminousRim(material = material, treatment = treatment, accent = accent, shape = shape, interaction = interaction, idleAlpha = resolvedIdleRim, highlightScale = highlightScale, focusedMotionScale = rimMotionScale, focusRingMix = focusRingMix, focusRingWidth = focusRingWidth)
            onDrawWithContent {
                val bounds = position.bounds
                val sampled = blurred.sampledLuminance(bounds)
                val resolvedTint = (tintAlpha(sampled) + (1f - frostAlpha) * 0.16f).coerceIn(0f, 1f)
                val lower = frostSelection.lower
                if (rootW > 0f && rootH > 0f && bounds.width > 0f && bounds.height > 0f && lower.width > 0 && lower.height > 0) {
                // Draw the WHOLE blurred bitmap scaled to the root viewport, translated so the slice that
                // lands behind THIS panel is the one visible. The node is already clipped to `shape`
                // (by the upstream .clip() in RoundedPanel/DialogPanel), so only the panel's region shows.
                // We deliberately do NOT use drawImage's src/dst-slice overload here: that overload takes
                // IntOffset/IntSize, whose integer truncation of the panel's sub-pixel position misaligns
                // RGB subpixels under GPU bilinear filtering → saturated red/green/blue blocks. Using the
                // DrawScope's float translate/scale + whole-image drawImage keeps sampling continuous.
                val sx = size.width / bounds.width       // node px ↔ root px scale (usually ~1)
                val sy = size.height / bounds.height
                // Order + pivot matter: translate must be OUTSIDE the scale (in root px, not scaled px)
                // and the scale must pivot at the origin (DrawScope.scale defaults to the center). Both
                // are invisible while the bitmap is at full root resolution (scale ≈ 1) but misalign the
                // frost whenever the bitmap is smaller than the root — e.g. a 4K root with the 1920-capped
                // bitmap (scale = 2).
                val parallax = motion?.parallaxOffset() ?: Offset.Zero
                translate(-(bounds.left + parallax.x) * sx, -(bounds.top + parallax.y) * sy) {
                    scale(rootW / lower.width * sx, rootH / lower.height * sy, pivot = Offset.Zero) {
                        drawImage(lower, topLeft = Offset.Zero, alpha = 1f - frostSelection.upperWeight)
                    }
                    frostSelection.upper?.let { upper ->
                        scale(rootW / upper.width * sx, rootH / upper.height * sy, pivot = Offset.Zero) {
                            drawImage(upper, topLeft = Offset.Zero, alpha = frostSelection.upperWeight)
                        }
                    }
                }
                // The cached bitmap supplies the frost; the role/preset controls the coloured tint above it.
                // A small extra tint at low frost keeps sharp detail from fighting text.
                    drawRect(baseFill.copy(alpha = resolvedTint))
                } else {
                    // Bounds not resolved yet (first frame) → keep the panel readable with the solid fill.
                    drawRect(baseFill)
                }
                body?.let { drawLuminousBody(it, darkLensMix(sampled), glintMotionOffset()) }
                drawContent()
                drawLuminousRim(rim)
            }
        }
}

/**
 * Position holder deliberately outside Compose snapshot state. A scrolling frost surface only needs
 * a redraw when its root-space position changes; recomposition and cache rebuilding would be wasted.
 */
private class GlassPositionState(var bounds: Rect = Rect.Zero)

private data class GlassPositionElement(
    val position: GlassPositionState,
) : ModifierNodeElement<GlassPositionNode>() {
    override fun create(): GlassPositionNode = GlassPositionNode(position)

    override fun update(node: GlassPositionNode) {
        node.position = position
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassPosition"
    }
}

private class GlassPositionNode(
    var position: GlassPositionState,
) : Modifier.Node(), GlobalPositionAwareModifierNode, DrawModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val next = coordinates.boundsInRoot()
        if (next != position.bounds) {
            position.bounds = next
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
    }
}

private data class LuminousBody(
    val radial: Brush,
    val darkRadial: Brush,
    val focusSweep: Brush?,
    val darkFocusSweep: Brush?,
    val shade: Brush,
)

private data class LuminousRim(
    val edgeBrush: Brush,
    val brightBrush: Brush,
    val path: Path,
    val inset: Float,
    val edgeStroke: Stroke,
    val stroke: Stroke,
)

/** Build size-dependent light resources once per draw-cache lifetime, not once per rendered frame. */
private fun CacheDrawScope.createLuminousBody(
    material: GlassMaterial,
    interaction: GlassInteraction,
    tonal: Boolean,
    highlightScale: Float,
): LuminousBody {
    val compact = (1f - ((size.minDimension / 150f.dp.toPx()) - 1f).coerceIn(0f, 1f))
    val focused = interaction == GlassInteraction.FOCUSED || interaction == GlassInteraction.PRESSED
    val legacyPeak = when {
        focused -> material.lensPeak * (1f + compact * 0.35f)
        interaction == GlassInteraction.SELECTED -> material.lensPeak * 0.34f
        tonal -> material.lensPeak * 0.25f
        else -> material.lensPeak * (0.20f + compact * 0.12f)
    }
    val shortSide = size.minDimension.coerceAtLeast(1f)
    val aspect = size.maxDimension / shortSide
    val useWideFocusGlint = focused && aspect > 1.6f
    val aspectFade = ((aspect - 1.6f) / 5.4f).coerceIn(0f, 1f)
    val areaCompact = (1f - (shortSide / 180f.dp.toPx())).coerceIn(0f, 1f)
    val unscaledPeak = if (useWideFocusGlint) {
        material.lensPeak * (1f + areaCompact * 0.35f) * (1f - 0.62f * aspectFade)
    } else {
        legacyPeak
    }
    val peak = if (focused) unscaledPeak * highlightScale else unscaledPeak
    val origin = if (useWideFocusGlint) {
        Offset(
            x = minOf(size.width * 0.16f, 96f.dp.toPx()),
            y = -minOf(size.height * 0.10f, 12f.dp.toPx()),
        )
    } else {
        Offset(size.width * 0.14f, -size.height * 0.08f)
    }
    val radius = if (useWideFocusGlint) {
        maxOf(shortSide * 1.45f, 140f.dp.toPx())
    } else {
        size.maxDimension * 0.72f
    }
    return LuminousBody(
        radial = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = peak), Color.White.copy(alpha = 0f)),
            center = origin,
            radius = radius,
        ),
        darkRadial = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = peak * 0.70f), Color.Black.copy(alpha = 0f)),
            center = origin,
            radius = radius,
        ),
        focusSweep = if (focused) {
            Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.42f to Color.Transparent,
                    0.56f to Color.White.copy(alpha = 0.07f * highlightScale),
                    0.68f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height * 0.25f),
            )
        } else {
            null
        },
        darkFocusSweep = if (focused) {
            Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.42f to Color.Transparent,
                    0.56f to Color.Black.copy(alpha = 0.05f * highlightScale),
                    0.68f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height * 0.25f),
            )
        } else {
            null
        },
        // A restrained lower/right shade implies material thickness without blackening the whole pane.
        shade = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (tonal) 0.10f else 0.07f)),
            startY = size.height * 0.58f,
            endY = size.height,
        ),
    )
}

/** Localized light replaces the old full-width rectangular sheen. */
private fun DrawScope.drawLuminousBody(
    body: LuminousBody,
    darkLensMix: Float,
    glintOffset: Offset,
) {
    val dark = darkLensMix.coerceIn(0f, 1f)
    translate(glintOffset.x, glintOffset.y) {
        if (dark < 1f) {
            drawRect(brush = body.radial, alpha = 1f - dark)
            body.focusSweep?.let { drawRect(brush = it, alpha = 1f - dark) }
        }
        if (dark > 0f) {
            drawRect(brush = body.darkRadial, alpha = dark)
            body.darkFocusSweep?.let { drawRect(brush = it, alpha = dark) }
        }
    }
    drawRect(brush = body.shade)
}

/** Two-edged perimeter: dark separation under a short bright glint from the light source. */
private fun CacheDrawScope.createLuminousRim(
    material: GlassMaterial,
    treatment: GlassLayerTreatment,
    accent: Color,
    shape: Shape,
    interaction: GlassInteraction,
    idleAlpha: Float,
    highlightScale: Float,
    focusedMotionScale: Float = 1f,
    focusRingMix: Float = 0.38f,
    focusRingWidth: Dp = 1.5.dp,
): LuminousRim {
    val focused = interaction == GlassInteraction.FOCUSED || interaction == GlassInteraction.PRESSED
    val selected = interaction == GlassInteraction.SELECTED
    val peak = when {
        !treatment.brightRim -> 0f
        focused -> material.rimFocused * highlightScale * focusedMotionScale
        selected -> material.rimSelected
        else -> idleAlpha
    }
    val tail = when {
        focused -> 0.12f * highlightScale * focusedMotionScale
        selected -> 0.10f
        else -> idleAlpha * 0.45f
    }
    val edgeDark = if (treatment.darkEdge) material.edgeDark else 0f
    val brightColor = when {
        focused -> androidx.compose.ui.graphics.lerp(Color.White, accent, focusRingMix)
        selected -> androidx.compose.ui.graphics.lerp(Color.White, accent, 0.55f)
        else -> Color.White
    }
    val stroke = if (focused) focusRingWidth.toPx() else 1.dp.toPx()
    val inset = stroke / 2f
    val outlineSize = Size(
        width = (size.width - stroke).coerceAtLeast(0f),
        height = (size.height - stroke).coerceAtLeast(0f),
    )
    val outline = shape.createOutline(outlineSize, layoutDirection, this)
    val path = when (outline) {
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
    }
    return LuminousRim(
        edgeBrush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = edgeDark * 0.25f),
                0.74f to Color.Black.copy(alpha = edgeDark * 0.55f),
                1f to Color.Black.copy(alpha = edgeDark),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        brightBrush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to brightColor.copy(alpha = peak),
                0.28f to brightColor.copy(alpha = tail),
                0.55f to Color.Transparent,
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        path = path,
        inset = inset,
        edgeStroke = Stroke(width = 1.dp.toPx()),
        stroke = Stroke(width = stroke),
    )
}

private fun DrawScope.drawLuminousRim(rim: LuminousRim) {
    translate(rim.inset, rim.inset) {
        drawPath(path = rim.path, brush = rim.edgeBrush, style = rim.edgeStroke)
        drawPath(path = rim.path, brush = rim.brightBrush, style = rim.stroke)
    }
}

/** Minimum fill opacity needed for WCAG 4.5:1 text contrast over sampled wallpaper luminance. */
fun requiredLegibilityAlpha(
    backdropLuma: Float,
    fillLuma: Float,
    textLuma: Float,
    minimumContrast: Float = 4.5f,
): Float {
    val backdrop = backdropLuma.coerceIn(0f, 1f)
    val fill = fillLuma.coerceIn(0f, 1f)
    val text = textLuma.coerceIn(0f, 1f)
    return if (text >= 0.5f) {
        val maximumBackground = ((text + 0.05f) / minimumContrast - 0.05f).coerceIn(0f, 1f)
        when {
            backdrop <= maximumBackground -> 0f
            fill >= backdrop -> 1f
            else -> ((backdrop - maximumBackground) / (backdrop - fill)).coerceIn(0f, 1f)
        }
    } else {
        val minimumBackground = (minimumContrast * (text + 0.05f) - 0.05f).coerceIn(0f, 1f)
        when {
            backdrop >= minimumBackground -> 0f
            fill <= backdrop -> 1f
            else -> ((minimumBackground - backdrop) / (fill - backdrop)).coerceIn(0f, 1f)
        }
    }
}

/**
 * True when backdrop blur is available on this device. Tier 2 (Phase 4) uses a cached blurred
 * copy of the background image; older devices fall back to Tier 1 translucency only.
 */
@Composable
@ReadOnlyComposable
fun supportsBackdropBlur(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
