package tv.own.owntv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.GlassInteraction
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.LocalGlassMotion
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.animationsOn
import tv.own.owntv.ui.theme.glass

@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Dimens.CardCorner),
    focusedContainerColor: Color = OwnTVTheme.colors.card,
    unfocusedContainerColor: Color = Color.Transparent,
    selectedContainerColor: Color = OwnTVTheme.colors.card,
    focusedScale: Float = 1.012f,
    glowElevation: Int = 6,
    // When non-null AND that surface is glassy (glass mode on + surface in scope), the focused/
    // selected highlight fill renders as a frosted glass slice (Modifier.glass) with a bright white
    // rim instead of the flat tonal fill + accent border. Idle fills are transparent, which glass()
    // skips, so the toggle is safe. Null = the original flat-fill behaviour (unchanged for callers).
    surface: GlassSurface? = null,
    // Per-call frost multiplier for lighter glass on small chrome (see Modifier.glass). Ignored when [surface] is null.
    glassFrostScale: Float = 1f,
    // When >0 AND this surface is glassy, an always-on faint white rim lenses the whole edge even when
    // unfocused — the glass edge highlight. Focus still swaps to the brighter rim.
    // Default 0 = no idle rim (unchanged for the 90+ existing callers); opt-in for discrete controls
    // like buttons where a permanent glass edge suits them.
    glassIdleRimAlpha: Float = 0f,
    glassCondensesWithContent: Boolean = false,
    // When false, this surface never draws the built-in focus/selected outline, so the caller can
    // manage its own border (e.g. the nav ladder, which outlines only the focused-unselected cursor).
    showFocusBorder: Boolean = true,
    // Focus drawn as light rather than as a tonal plate: a 1.6 dp rim in this colour, a 22% fill of it,
    // and a soft bloom of it behind the control. Used by the player dock, whose accent has to read over
    // moving video where the theme's surface tones have nothing to sit on. Reduce animations drops the
    // bloom and keeps the rim. Null = the standard ladder, unchanged for every existing caller.
    focusLight: Color? = null,
    // Keep selected semantics/click behaviour but let custom content own the complete selected
    // appearance. This prevents a second material plate behind controls such as the nav beacon.
    renderSelectionContainer: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val colors = OwnTVTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val visuallySelected = selected && renderSelectionContainer
    val visualState = when {
        pressed -> GlassInteraction.PRESSED
        focused -> GlassInteraction.FOCUSED
        visuallySelected -> GlassInteraction.SELECTED
        else -> GlassInteraction.IDLE
    }
    // Row-sized glass controls use only the restrained 1.008 focus lift from the material ladder;
    // larger poster cards (1.03+) retain their deliberate depth motion. Glass never adds the separate
    // solid-mode glow shadow, avoiding trails while scrolling wide rows.
    val glassConfig = LocalGlass.current
    val glassy = surface != null && glassConfig.isGlassy(surface)
    val motion = LocalGlassMotion.current
    val motionToken = remember { Any() }
    val focusCenter = remember { arrayOf(Offset.Unspecified) }
    val travelPx = with(LocalDensity.current) { 40.dp.toPx() }
    val motionEnabled = glassy && glassConfig.depthEffects && animationsOn
    LaunchedEffect(focused, motionEnabled, motion) {
        if (focused && motion != null) {
            // Let focus-driven bringIntoView settle its first layout before measuring arrival direction.
            withFrameNanos { }
            motion.focusArrived(motionToken, focusCenter[0], travelPx, motionEnabled)
        }
    }
    val compactFocusableRow = focusedScale <= 1.012f
    val compactGlassRow = glassy && compactFocusableRow
    // Primary action pills deliberately remain solid brand anchors. All other standard solid-mode
    // controls share the same M3 tonal focus ladder instead of inheriting dozens of unrelated fills.
    val solidBrandAnchor = unfocusedContainerColor == colors.primary &&
        focusedContainerColor == colors.primary
    val lit = focusLight != null && !glassy
    val useSolidTonalLadder = !glassy && showFocusBorder && !solidBrandAnchor && !lit
    val solidTonalBase = if (unfocusedContainerColor.alpha > 0f) {
        unfocusedContainerColor
    } else {
        // Transparent list rows still need the tonal focus to sit on the same elevated card tone
        // they used before Phase 5; using surfaceContainerLow made both themes look flat and muddy.
        colors.surfaceContainerHigh
    }
    val solidFocusedContainer = colors.primaryContainer.copy(alpha = 0.22f)
        .compositeOver(solidTonalBase)
    val solidSelectedContainer = colors.primaryContainer.copy(alpha = 0.14f)
        .compositeOver(solidTonalBase)

    // Fast D-pad navigation can move focus through several cards before the previous frame reaches
    // the GPU. Keep the immediate tint/lens/rim feedback, but promote only the surface that remains
    // focused long enough to the full aligned-backdrop frost path. This avoids a 4K texture sample at
    // every transient focus stop without making navigation feel delayed.
    var focusFrostSettled by remember { mutableStateOf(false) }
    if (focused && glassy && surface == GlassSurface.CARDS) {
        // Conditional effect means only the one focused card owns a timer; dense idle lists launch
        // no settle coroutines. Leaving this composition group also cancels the timer immediately.
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(FOCUS_FROST_SETTLE_MS)
            focusFrostSettled = true
        }
    } else {
        SideEffect {
            if (focusFrostSettled) focusFrostSettled = false
        }
    }
    val effectiveFrostScale = if (
        focused && glassy && surface == GlassSurface.CARDS && !focusFrostSettled
    ) 0f else glassFrostScale

    val scale by animateFloatAsState(
        when {
            (focused || pressed) && glassy && !glassConfig.depthEffects -> 1f
            pressed -> 0.992f
            compactGlassRow && focused && glassConfig.depthEffects -> 1.008f
            focused -> focusedScale
            else -> 1f
        },
        animationSpec = tv.own.owntv.ui.theme.ownTvTween(if (pressed) 80 else 170),
        label = "focusScale",
    )
    val container by animateColorAsState(
        when {
            focused && lit -> focusLight.copy(alpha = 0.22f)
            focused && useSolidTonalLadder -> solidFocusedContainer
            visuallySelected && useSolidTonalLadder -> solidSelectedContainer
            focused -> focusedContainerColor
            visuallySelected -> selectedContainerColor
            else -> unfocusedContainerColor
        },
        // A row that loses focus is commonly moved by bringIntoView in the same frame. Fading its
        // wide focus fill toward transparent therefore produces a dark plate that visibly follows
        // behind the new focus position. Compact rows snap the fill state in both material modes;
        // poster cards retain the softer transition because they do not scroll as one-row plates.
        animationSpec = tv.own.owntv.ui.theme.ownTvTween(if (compactFocusableRow) 0 else 160),
        label = "focusContainer",
    )
    val showBorder = showFocusBorder && (focused || visuallySelected)
    // #121: the user's chosen ring width. A wider ring also opens the glow up with it, so "extra
    // thick" reads as a halo from sofa distance instead of just a fatter line.
    val focusBorderWidth = tv.own.owntv.ui.theme.LocalFocusBorderWidth.current
    val glowScale = focusBorderWidth.value / Dimens.FocusBorderWidth.value
    // Focus border honors the user's chosen highlight colour and width in both standard and glass
    // modes, framing the focused card on top of the frosted glass material plate.
    val borderColor = if (focused) colors.focusBorder else colors.focusBorder.copy(alpha = 0.28f)
    val glassMaterialContainer = if (glassy && visuallySelected && !focused) {
        colors.surfaceContainerHigh
    } else {
        container
    }

    Box(
        modifier = modifier
            .then(
                if (motion != null) Modifier.onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    val center = bounds.center
                    focusCenter[0] = center
                    if (focused) motion.updateFocusedPosition(motionToken, center)
                } else Modifier,
            )
            .scale(scale)
            .then(
                // A separately elevated row layer can remain at its old GPU position for a frame
                // while bringIntoView scrolls its parent, which reads as a moving black bar in light
                // mode. The tonal fill + accent boundary are the compact-row focus signal; reserve
                // the depth shadow for larger cards that do not exhibit the scrolling trail.
                if (focused && lit && animationsOn) Modifier.shadow(
                    // The bloom IS the animation here: at reduced level it goes and the rim carries
                    // the focus on its own.
                    elevation = (10 * glowScale).dp,
                    shape = shape,
                    clip = false,
                    ambientColor = focusLight,
                    spotColor = focusLight,
                ) else if (focused && !glassy && !compactFocusableRow) Modifier.shadow(
                    elevation = (glowElevation * glowScale).dp,
                    shape = shape,
                    clip = false,
                    ambientColor = colors.focusGlow,
                    spotColor = colors.focusGlow,
                ) else Modifier,
            )
            .clip(shape)
            .then(
                // Frosted glass fill when this surface is glassy (glass() skips transparent idle
                // fills); plain tonal fill otherwise. When surface is null, behaviour is unchanged.
                if (surface != null) Modifier.glass(
                    surface = surface,
                    baseFill = glassMaterialContainer,
                    shape = shape,
                    frostScale = effectiveFrostScale,
                    interaction = visualState,
                    idleRimAlpha = glassIdleRimAlpha,
                    condenseChrome = glassCondensesWithContent,
                )
                else Modifier.background(container)
            )
            .then(
                if (glassy && visuallySelected && !focused) {
                    Modifier.background(colors.primaryContainer.copy(alpha = 0.14f), shape)
                } else {
                    Modifier
                },
            )
            .then(
                when {
                    showBorder && focused && lit -> Modifier.border(1.6.dp, focusLight, shape)
                    showBorder && focused -> Modifier.border(
                        focusBorderWidth,
                        borderColor,
                        shape,
                    )
                    // The idle "selected" hairline stays a muted 1.dp user highlight stroke: it marks
                    // position without competing with the live remote cursor.
                    showBorder && visuallySelected && !focused -> Modifier.border(
                        1.dp,
                        colors.focusBorder.copy(alpha = 0.28f),
                        shape,
                    )
                    else -> Modifier
                }
            )
            .then(
                if (focused && useSolidTonalLadder) {
                    Modifier.drawWithCache {
                        val highlightHeight = 2.dp.toPx()
                        val highlight = Brush.verticalGradient(
                            colors = listOf(
                                colors.onSurface.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                            endY = highlightHeight,
                        )
                        val radiance = Brush.radialGradient(
                            colors = listOf(
                                colors.primary.copy(alpha = if (colors.isDark) 0.09f else 0.055f),
                                Color.Transparent,
                            ),
                            center = androidx.compose.ui.geometry.Offset(
                                x = minOf(size.width * 0.14f, 72.dp.toPx()),
                                y = 0f,
                            ),
                            radius = maxOf(size.minDimension * 2.2f, 120.dp.toPx()),
                        )
                        onDrawWithContent {
                            drawRect(brush = radiance)
                            drawContent()
                            drawRect(
                                brush = highlight,
                                size = Size(size.width, highlightHeight),
                            )
                        }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                } else {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                },
            ),
        contentAlignment = contentAlignment,
    ) {
        content(focused)
    }
}

private const val FOCUS_FROST_SETTLE_MS = 96L
