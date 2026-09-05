package tv.own.owntv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.core.theme.OwnTVPalette
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.glass

// Per-region colour identity from the established shell design. The three roles remain distinct in
// both themes without collapsing the interface into the greyer generic M3 elevation ladder. The
// values are core's, because the mobile app paints the same three regions.
val RailPanelFill: Color
    @Composable @ReadOnlyComposable get() =
        Color(if (OwnTVTheme.colors.isDark) OwnTVPalette.DarkRailPanel else OwnTVPalette.LightRailPanel)

val ContentPanelFill: Color
    @Composable @ReadOnlyComposable get() =
        Color(if (OwnTVTheme.colors.isDark) OwnTVPalette.DarkContentPanel else OwnTVPalette.LightContentPanel)

val PreviewPanelFill: Color
    @Composable @ReadOnlyComposable get() =
        Color(if (OwnTVTheme.colors.isDark) OwnTVPalette.DarkPreviewPanel else OwnTVPalette.LightPreviewPanel)

/**
 * Phase 6 — a rounded visual container matching the new-shell mockup's "panel 2/3/4" look: large rounded
 * corners, a subtle surface fill, and a hairline top-edge lift. Content is clipped to the shape.
 *
 * This is a VISUAL wrapper only — a plain [Box], no `clickable`/`selectable`/focus of its own.
 *
 * Glass effect: when a background image is active and [GlassSurface.PANELS] is in scope, the fill
 * becomes translucent (alpha from the user's transparency setting) and gains a soft specular
 * top-edge highlight. Callers that pass an explicit [fillColor] still go glassy — the explicit
 * colour is simply used as the translucent base, so per-region tints (ContentPanelFill etc.)
 * keep their identity. Pass [surface] to tag this panel as something else (e.g. SIDEBAR/PREVIEW).
 *
 * @param fillColor the panel surface colour, or null for the theme default.
 * @param radius corner radius (≈24px on the mockup; 22dp reads well at TV distance).
 * @param innerPadding inset between the rounded edge and the content.
 * @param surface which glass surface this panel represents (default PANELS).
 */
@Composable
fun RoundedPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    fillColor: Color? = null,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    surface: GlassSurface = GlassSurface.PANELS,
    content: @Composable () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(radius)
    val glassy = LocalGlass.current.isGlassy(surface)
    val bg = fillColor ?: colors.surfaceContainerLowest
    val outline = colors.outlineVariant.copy(alpha = 0.66f)
    Box(
        modifier = modifier
            .clip(shape)
            .glass(
                surface = surface,
                baseFill = bg,
                shape = shape,
                condenseChrome = surface == GlassSurface.SIDEBAR,
            )
            .then(
                if (glassy) Modifier else Modifier
                    .border(width = 1.dp, color = outline, shape = shape)
                    .solidPanelMaterial(
                        edgeColor = colors.outlineVariant,
                        accent = colors.primary,
                        isDark = colors.isDark,
                    )
            )
            .padding(innerPadding),
    ) {
        content()
    }
}

/**
 * Phase 6 — the rounded-panel look as a [Modifier], for applying to an EXISTING container.
 * Same spec as [RoundedPanel]. See [RoundedPanel] for the glass behaviour; pass [surface] to tag
 * this container as SIDEBAR/PREVIEW/etc. when it is not a generic content panel.
 */
@Composable
fun Modifier.roundedPanel(
    radius: Dp = 22.dp,
    fillColor: Color? = null,
    surface: GlassSurface = GlassSurface.PANELS,
): Modifier {
    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(radius)
    val glassy = LocalGlass.current.isGlassy(surface)
    val bg = fillColor ?: colors.surfaceContainerLowest
    val outline = colors.outlineVariant.copy(alpha = 0.66f)
    return this
        .clip(shape)
        .glass(
            surface = surface,
            baseFill = bg,
            shape = shape,
            condenseChrome = surface == GlassSurface.SIDEBAR,
        )
        .then(
            if (glassy) Modifier else Modifier
                .border(width = 1.dp, color = outline, shape = shape)
                .solidPanelMaterial(
                    edgeColor = colors.outlineVariant,
                    accent = colors.primary,
                    isDark = colors.isDark,
                )
        )
}

/**
 * Cached solid-material lighting: one broad accent reflection, a restrained lower depth tone, and
 * the existing top-edge lift. These are plain brush draws inside the panel clip—no blur, shadow
 * layer, animation, or per-frame brush allocation.
 */
private fun Modifier.solidPanelMaterial(
    edgeColor: Color,
    accent: Color,
    isDark: Boolean,
): Modifier = drawWithCache {
    val edgeHeight = 2.dp.toPx()
    val edge = Brush.verticalGradient(
        colors = listOf(edgeColor.copy(alpha = 0.42f), Color.Transparent),
        endY = edgeHeight,
    )
    val ambient = Brush.radialGradient(
        colors = listOf(
            accent.copy(alpha = if (isDark) 0.055f else 0.032f),
            Color.Transparent,
        ),
        center = Offset(
            x = minOf(size.width * 0.16f, 120.dp.toPx()),
            y = -minOf(size.height * 0.08f, 20.dp.toPx()),
        ),
        radius = maxOf(size.minDimension * 1.45f, 260.dp.toPx()),
    )
    val depth = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = if (isDark) 0.045f else 0.018f),
        ),
        startY = size.height * 0.58f,
        endY = size.height,
    )
    onDrawWithContent {
        drawRect(brush = ambient)
        drawRect(brush = depth)
        drawContent()
        drawRect(brush = edge, size = Size(size.width, edgeHeight))
    }
}
