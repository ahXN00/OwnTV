package tv.own.owntv.ui.stage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageAccent
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.ownTvTween
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/*
 * The Stage component set (mockup `.mine` classes). Idle = nothing, selected = accent text + dot or bar,
 * focused = the one loud thing, in one of four treatments:
 *
 *  FX       rows and cards: accent 34%→14% sweep, 2 px focus ring inside, 44 px accent glow.
 *  FILLED   menu items, sheet items, pills, tools, cells: accent fill, 2 px focus ring outside, glow.
 *  PRIMARY  buttons: accent fill, 3 px ring outside, a larger glow, lifted to 1.04.
 *  POSTER   posters: 3 px focus ring + 4 px accent-35% ring outside, glow, lifted to 1.08.
 */
enum class StageFocus { FX, FILLED, PRIMARY, POSTER }

/** A focus treatment on a custom-built element (the audio card), drawn exactly as [StageSurface] draws it. */
@Composable
fun Modifier.stageFocusLook(style: StageFocus, radius: Dp): Modifier = stageFocusDecor(style, radius, stageAccent)

private fun Modifier.stageFocusDecor(style: StageFocus, radius: Dp, a: StageAccent) = drawBehind {
    val r = radius.toPx()
    val px = 1.mpx.toPx()
    when (style) {
        StageFocus.FX -> {
            drawBoxShadow(a.accent.copy(alpha = 0.28f), 44 * px, r)
            // A row-sized sweep: small enough to stay a brush (see GradientTextures).
            drawRoundRect(
                Brush.horizontalGradient(listOf(a.accent.copy(alpha = 0.34f), a.accent.copy(alpha = 0.14f))),
                cornerRadius = CornerRadius(r),
            )
            drawInnerRing(a.focus, 2 * px, r)
        }
        StageFocus.FILLED -> {
            drawBoxShadow(a.accent.copy(alpha = 0.35f), 30 * px, r, dy = 10 * px)
            drawRoundRect(a.accent, cornerRadius = CornerRadius(r))
            drawOuterRing(a.focus, 2 * px, r)
        }
        StageFocus.PRIMARY -> {
            drawBoxShadow(a.accent.copy(alpha = 0.45f), 40 * px, r, dy = 14 * px)
            drawRoundRect(a.accent, cornerRadius = CornerRadius(r))
            drawOuterRing(a.focus, 3 * px, r)
        }
        StageFocus.POSTER -> {
            drawBoxShadow(a.accent.copy(alpha = 0.35f), 60 * px, r, dy = 24 * px)
            drawOuterRing(a.accent.copy(alpha = 0.35f), 4 * px, r, inset = 3 * px)
            drawOuterRing(a.focus, 3 * px, r)
        }
    }
}

/**
 * The focusable base every Stage control is built on: D-pad focus and click wiring as in
 * FocusableSurface, the [focusStyle] treatment while focused, [idle] (a fill or [stageGlass]) otherwise.
 * Nothing is clipped, so rings and glows reach past the bounds exactly as a `box-shadow` does.
 */
@Composable
fun StageSurface(
    onClick: () -> Unit,
    radius: Dp,
    modifier: Modifier = Modifier,
    focusStyle: StageFocus = StageFocus.FILLED,
    idle: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    /** Drawn as focused without holding focus: a pill whose menu is open over it. */
    highlighted: Boolean = false,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value || highlighted
    val accent = stageAccent
    Box(
        modifier = modifier
            .stageLift(focused, focusStyle)
            .then(if (focused) Modifier.stageFocusDecor(focusStyle, radius, accent) else idle)
            .stageClickable(interaction, enabled, onClick, onLongClick),
        contentAlignment = contentAlignment,
    ) { content(focused) }
}

/** The PRIMARY (1.04) and POSTER (1.08, from 50% 40%) lift; FX and FILLED stay put. An episode lifts 1.06 from 50% 30%. */
@Composable
private fun Modifier.stageLift(focused: Boolean, style: StageFocus, episode: Boolean = false): Modifier {
    val lift = when {
        episode -> 1.06f
        style == StageFocus.PRIMARY -> 1.04f
        style == StageFocus.POSTER -> 1.08f
        else -> 1f
    }
    val scale by animateFloatAsState(if (focused) lift else 1f, ownTvTween(170), label = "stageLift")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        transformOrigin = when {
            episode -> TransformOrigin(0.5f, 0.3f)
            style == StageFocus.POSTER -> TransformOrigin(0.5f, 0.4f)
            else -> TransformOrigin.Center
        }
    }
}

private fun Modifier.stageClickable(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) = if (onLongClick != null) {
    combinedClickable(interaction, null, enabled, onLongClick = onLongClick, onClick = onClick)
} else {
    clickable(interaction, null, enabled, onClick = onClick)
}

/** The glowing 7 px dot that marks the selected item in a sheet (`.ci.sel::before`). */
@Composable
fun StageSelectedDot() {
    val accent = stageAccent.accent
    Box(
        Modifier
            .size(7.mpx)
            .drawBehind {
                drawBoxShadow(accent, 8.mpx.toPx(), size.minDimension / 2f)
                drawCircle(accent)
            },
    )
}

/**
 * The 4 px glowing accent bar at the left edge of a selected category (`.fcat.sel`, inset 14) or
 * settings group (`.gsheet .gi.sel`, inset 18).
 */
fun Modifier.stageSelectedBar(accent: Color, verticalInset: Dp) = drawBehind {
    val inset = verticalInset.toPx()
    if (size.height <= inset * 2f) return@drawBehind
    val bar = Rect(0f, inset, 4.mpx.toPx(), size.height - inset)
    val r = 3.mpx.toPx()
    drawBoxShadow(accent, 10.mpx.toPx(), r, bounds = bar)
    drawRoundRect(accent, topLeft = bar.topLeft, size = bar.size, cornerRadius = CornerRadius(r))
}

@Composable
private fun StageIcon(icon: OwnTVIcon, tint: Color, size: Dp, filled: Boolean = false) =
    OwnTVIcon(icon, tint, Modifier.size(size), filled = filled)

// ---------------------------------------------------------------------------------------------
// Buttons, pills, tools, segmented
// ---------------------------------------------------------------------------------------------

/**
 * `.btn`: 64 high, 22 px type. Idle glass; focused = the PRIMARY treatment (the hero's "Play").
 * [round] is the square icon-only button (ⓘ, ♥). [trailing] is the muted count in "All versions 3".
 * The guide's buttons are 56 high in 19 px ([height], [textSize]); [tinted] is their accent-20% idle
 * ("Remind me", "Done") in place of glass.
 */
@Composable
fun StageButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: OwnTVIcon? = null,
    iconFilled: Boolean = false,
    trailing: String? = null,
    round: Boolean = false,
    height: Dp = 64.mpx,
    textSize: Int = 22,
    tinted: Boolean = false,
    /** A glyph after the label: the ▾ of "● Record ▾". */
    trailingIcon: OwnTVIcon? = null,
) {
    val a = stageAccent
    val r = StageRadii.Button
    StageSurface(
        onClick = onClick,
        radius = r,
        modifier = modifier.height(height).then(if (round) Modifier.width(height) else Modifier),
        focusStyle = StageFocus.PRIMARY,
        idle = if (tinted) Modifier.background(a.accent.copy(alpha = 0.2f), RoundedCornerShape(r)) else Modifier.stageGlass(r),
        contentAlignment = Alignment.Center,
    ) { focused ->
        val color = if (focused) a.onAccent else if (tinted) a.accent else StageColors.Text
        Row(
            Modifier.then(if (round) Modifier else Modifier.padding(horizontal = 30.mpx)),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) StageIcon(icon, color, textSize.mpx, iconFilled)
            if (text != null) Text(text, style = stageText(textSize, 700), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing != null) {
                // On a tinted button the trailing part is the label's accent at 70% ("· 12 min left").
                val idle = if (tinted) a.accent.copy(alpha = 0.7f) else StageColors.Muted
                Text(trailing, style = stageText(textSize, 600), color = if (focused) a.onAccent else idle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailingIcon != null) StageIcon(trailingIcon, color, textSize.mpx)
        }
    }
}

/**
 * `.pill` (top-right cluster): 50 high, glass, 18 px type, a 20 px accent icon, an optional small
 * label before the text ("Resume · Toy Story 5"), capped at 470 wide. Focused = FILLED.
 */
@Composable
fun StagePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: OwnTVIcon? = null,
    iconFilled: Boolean = false,
    small: String? = null,
    trailingIcon: OwnTVIcon? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    /** Idle text in the dim colour: a choice that is currently off. */
    dimmed: Boolean = false,
) {
    val a = stageAccent
    val r = StageRadii.Pill
    StageSurface(
        onClick = onClick,
        radius = r,
        modifier = modifier.height(50.mpx).widthIn(max = 470.mpx),
        idle = Modifier.stageGlass(r),
        enabled = enabled,
        highlighted = highlighted,
    ) { focused ->
        Row(
            Modifier.padding(horizontal = 18.mpx),
            horizontalArrangement = Arrangement.spacedBy(10.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val on = if (focused) a.onAccent else null
            if (icon != null) StageIcon(icon, on ?: a.accent, 20.mpx, iconFilled)
            if (small != null) Text(small, style = stageText(15, 700), color = on ?: StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text,
                style = stageText(18, 700),
                color = on ?: if (dimmed) StageColors.Dim else StageColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (trailingIcon != null) StageIcon(trailingIcon, on ?: a.accent, 20.mpx)
        }
    }
}

/**
 * `.tool`: a quiet 48-high text button in a tool row, muted 18 px type; [boxed] gives it the idle
 * white-6% fill. [value] is the bold part of "Sort: **Date added** ▾", [trailingIcon] its ▾. Focused = FILLED.
 */
@Composable
fun StageTool(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: OwnTVIcon? = null,
    value: String? = null,
    trailingIcon: OwnTVIcon? = null,
    boxed: Boolean = false,
    /** The value in accent (the guide's category, "**Sky Cinema** ▾"). */
    valueAccent: Boolean = false,
    /** `.danger`: a destructive action in red ("Clear watch history"). */
    danger: Boolean = false,
) {
    val a = stageAccent
    val r = StageRadii.Tool
    StageSurface(
        onClick = onClick,
        radius = r,
        modifier = modifier.height(48.mpx),
        idle = if (boxed) Modifier.background(StageColors.ControlFill, RoundedCornerShape(r)) else Modifier,
    ) { focused ->
        Row(
            Modifier.padding(horizontal = 16.mpx),
            horizontalArrangement = Arrangement.spacedBy(9.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = if (focused) a.onAccent else if (danger) StageColors.Danger else StageColors.Muted
            if (icon != null) StageIcon(icon, color, 20.mpx)
            if (text != null) Text(text, style = stageText(18, 700), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) Text(value, style = stageText(18, 700), color = if (focused) a.onAccent else if (valueAccent) a.accent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailingIcon != null) StageIcon(trailingIcon, color, 20.mpx)
        }
    }
}

/**
 * `.ttab`: "Season 1 12", "Movies 3" — 22/700 muted, the count 16 dim; the open tab in full text colour
 * with a 4 px accent underline glowing at 70%. Focused = FILLED (the mockup draws no focused tab).
 */
@Composable
fun StageTab(label: String, count: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val a = stageAccent
    StageSurface(onClick = onClick, radius = 12.mpx, modifier = modifier) { focused ->
        Row(
            Modifier
                .padding(horizontal = 12.mpx)
                .padding(top = 6.mpx)
                .then(
                    if (selected) {
                        Modifier.drawBehind {
                            val h = 4.mpx.toPx()
                            val bar = Rect(0f, size.height - h, size.width, size.height)
                            val r = 2.mpx.toPx()
                            if (!focused) drawBoxShadow(a.accent.copy(alpha = 0.7f), 12.mpx.toPx(), r, bounds = bar)
                            drawRoundRect(
                                if (focused) a.onAccent else a.accent, topLeft = bar.topLeft, size = bar.size,
                                cornerRadius = CornerRadius(r),
                            )
                        }
                    } else Modifier,
                )
                .padding(bottom = 12.mpx),
            horizontalArrangement = Arrangement.spacedBy(10.mpx),
            verticalAlignment = Alignment.Bottom,
        ) {
            val on = if (focused) a.onAccent else null
            Text(label, style = stageText(22, 700), color = on ?: if (selected) StageColors.Text else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
            Text(count, style = stageText(16, 600), color = on ?: StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
        }
    }
}

/**
 * `.seg2`: two or more options in one white-6% capsule; the chosen one sits on white 13% in full text
 * colour. The mockup draws no focused segment; a focused one takes the FILLED treatment, as `.row2`
 * does in menus.
 */
@Composable
fun StageSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<OwnTVIcon?> = emptyList(),
) {
    val a = stageAccent
    val optionFocus = remember(options.size) { List(options.size) { FocusRequester() } }
    Row(
        modifier
            // Coming in from above or below lands on the chosen option, not the nearest one.
            .focusProperties { onEnter = { optionFocus.getOrNull(selected)?.let { runCatching { it.requestFocus() } } } }
            .focusGroup()
            .background(StageColors.ControlFill, RoundedCornerShape(15.mpx))
            .padding(4.mpx),
        horizontalArrangement = Arrangement.spacedBy(2.mpx),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            StageSurface(
                onClick = { onSelect(i) },
                radius = 11.mpx,
                modifier = Modifier.height(40.mpx).focusRequester(optionFocus[i]),
                idle = if (on) Modifier.background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(11.mpx)) else Modifier,
            ) { focused ->
                val color = when {
                    focused -> a.onAccent
                    on -> StageColors.Text
                    else -> StageColors.Muted
                }
                Row(
                    Modifier.padding(horizontal = 14.mpx),
                    horizontalArrangement = Arrangement.spacedBy(8.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icons.getOrNull(i)?.let { StageIcon(it, color, 17.mpx) }
                    Text(label, style = stageText(17, 700), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tags and playlist marks
// ---------------------------------------------------------------------------------------------

/** `.tag`: a provider or quality badge, 13/800 caps-spaced on white 8%. [onAccent] inside a focused item; [tint] for a coloured tag. */
@Composable
fun StageTag(text: String, onAccent: Color? = null, tint: Color? = null) {
    Text(
        text,
        style = stageText(13, 800, 0.05.em),
        color = onAccent ?: tint ?: StageColors.TagText,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(
                when {
                    onAccent != null -> Color.Black.copy(alpha = 0.18f)
                    // A coloured tag ("IN USE", "UP TO DATE"): the colour at 20% behind its own text.
                    tint != null -> tint.copy(alpha = 0.2f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                RoundedCornerShape(StageRadii.Tag),
            )
            .padding(horizontal = 7.mpx, vertical = 3.mpx),
    )
}

/** `.pm`: which playlist a row comes from, outlined in that playlist's colour (see [PlaylistMark]). */
@Composable
fun StagePlaylistMark(mark: PlaylistMark) {
    val shape = RoundedCornerShape(7.mpx)
    Box(
        Modifier
            .height(22.mpx)
            .defaultMinSize(minWidth = 30.mpx)
            .drawBehind { drawInnerRing(mark.color, 1.5f * 1.mpx.toPx(), 7.mpx.toPx()) }
            .clip(shape)
            .padding(horizontal = 6.mpx),
        contentAlignment = Alignment.Center,
    ) {
        Text(mark.text, style = stageText(12, 800, 0.04.em), color = mark.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------------------------
// Rows, menus, sheets
// ---------------------------------------------------------------------------------------------

/**
 * A Stage list row: nothing when idle, FX when focused. Channel rows are 84 high with 20 px padding,
 * settings rows 84 with 22, Movies list rows 118 with 16 — the caller passes its own.
 */
@Composable
fun StageRow(
    onClick: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.mpx,
    gap: Dp = 18.mpx,
    onLongClick: (() -> Unit)? = null,
    /** The episode list's `.dl` rows are 24. */
    radius: Dp = StageRadii.Row,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    StageSurface(
        onClick = onClick,
        radius = radius,
        modifier = modifier.height(height),
        focusStyle = StageFocus.FX,
        onLongClick = onLongClick,
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) { content(focused) }
    }
}

/** `.menu`: the options / picker panel (☰ menus, sort pickers, the playlist switcher). */
@Composable
fun StageMenu(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .stageGlass(StageRadii.Menu, overContent = true)
            .padding(start = 16.mpx, end = 16.mpx, top = 22.mpx, bottom = 16.mpx),
        content = content,
    )
}

/** `.menu .mh`: the menu's heading — optional leading art, a 23/800 title and a 15 px muted line. */
@Composable
fun StageMenuHeader(title: String, subtitle: String?, leading: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.padding(start = 12.mpx, end = 12.mpx, bottom = 12.mpx),
        horizontalArrangement = Arrangement.spacedBy(14.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column {
            Text(title, style = stageText(23, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.mpx))
                Text(subtitle, style = stageText(15, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * `.menu .mg` / `.sheet .grp`: a dim group label ("WATCH", "GROUPS · DE"). The text is passed in
 * capitals, as the mockup writes it; upper-casing in code would break Turkish (i → İ).
 */
@Composable
fun StageGroupLabel(text: String, sheet: Boolean = false) {
    Text(
        text,
        style = if (sheet) stageText(13, 800, 0.12.em) else stageText(12.5f, 800, 0.13.em),
        color = StageColors.Dim,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = if (sheet) {
            Modifier.padding(start = 16.mpx, end = 16.mpx, top = 14.mpx, bottom = 6.mpx)
        } else {
            Modifier.padding(start = 14.mpx, end = 14.mpx, top = 12.mpx, bottom = 5.mpx)
        },
    )
}

/**
 * `.menu .mi`: 50 high, 19/600, a 21 px muted icon, an optional dim value on the right ("7 days") or
 * an accent ✓ when [checked]. [enabled] = false dims it to 45% ("Record from catch-up").
 */
@Composable
fun StageMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: OwnTVIcon? = null,
    iconFilled: Boolean = false,
    value: String? = null,
    checked: Boolean = false,
    enabled: Boolean = true,
    /** Drawn where the icon goes, for rows that lead with something else (a playlist mark). */
    leading: (@Composable () -> Unit)? = null,
) {
    val a = stageAccent
    StageSurface(
        onClick = onClick,
        radius = StageRadii.MenuItem,
        modifier = modifier.fillMaxWidth().height(50.mpx).alpha(if (enabled) 1f else 0.45f),
        enabled = enabled,
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.mpx),
            horizontalArrangement = Arrangement.spacedBy(14.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val on = if (focused) a.onAccent else null
            if (icon != null) StageIcon(icon, on ?: StageColors.Muted, 21.mpx, iconFilled)
            leading?.invoke()
            Text(text, style = stageText(19, 600), color = on ?: StageColors.MenuItemText, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = stageText(15, 700), color = on ?: StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (checked) StageIcon(OwnTVIcon.CHECK, on ?: a.accent, 22.mpx)
        }
    }
}

/** `.sheet`: a tier that slides over the content (categories, settings groups). */
@Composable
fun StageSheet(title: String, modifier: Modifier = Modifier, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .stageGlass(StageRadii.Sheet, overContent = true)
            .padding(horizontal = 16.mpx, vertical = 26.mpx),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.mpx, end = 14.mpx, bottom = 16.mpx),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing != null) Text(trailing, style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        content()
    }
}

/**
 * `.sheet .ci`: 56 high, 20/600. Selected = accent text with the glowing dot before it; focused =
 * FILLED. [tags] follow the name; [count] and then [trailing] (the playlist mark) sit on the right.
 */
@Composable
fun StageSheetItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: OwnTVIcon? = null,
    count: String? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    /** 2 lets a long name wrap instead of being cut; the row then grows past 56 (owner, 2026-09-30). */
    maxLines: Int = 1,
    tags: (@Composable RowScope.(focused: Boolean) -> Unit)? = null,
    trailing: (@Composable RowScope.(focused: Boolean) -> Unit)? = null,
) {
    val a = stageAccent
    StageSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        radius = StageRadii.SheetItem,
        modifier = modifier.fillMaxWidth().then(if (maxLines > 1) Modifier.heightIn(min = 56.mpx) else Modifier.height(56.mpx)),
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.mpx, vertical = if (maxLines > 1) 8.mpx else 0.mpx),
            horizontalArrangement = Arrangement.spacedBy(14.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val on = if (focused) a.onAccent else null
            // The dot has `margin-right: -4px`: a 3 px slot it overflows, so it sits 10 px from the text.
            if (selected && !focused) {
                Box(Modifier.width(3.mpx).wrapContentWidth(Alignment.Start, unbounded = true)) { StageSelectedDot() }
            }
            if (icon != null) StageIcon(icon, on ?: if (selected) a.accent else StageColors.Muted, 22.mpx)
            val label = @Composable { m: Modifier ->
                Text(
                    text,
                    style = stageText(20, 600),
                    color = on ?: if (selected) a.accent else StageColors.ItemText,
                    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                    modifier = m,
                )
            }
            if (maxLines > 1) {
                // Wrapping names get the full width: the tags move to their own line underneath.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                    label(Modifier)
                    if (tags != null) Row(horizontalArrangement = Arrangement.spacedBy(8.mpx)) { tags(this, focused) }
                }
            } else {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    label(Modifier.weight(1f, fill = false))
                    tags?.invoke(this, focused)
                }
            }
            if (count != null) Text(count, style = stageText(16, 600), color = on ?: StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing?.invoke(this, focused)
        }
    }
}

/**
 * `.gsheet .gi` (More, Settings): 64 high, radius 18, a 23 px muted icon, 20/700 text, the [value] 16/700
 * dim on the right ([warn] = amber with a ⚠). Selected = accent text and icon with the 4 px bar (inset 18);
 * focused = FILLED.
 */
@Composable
fun StageGroupItem(
    text: String,
    icon: OwnTVIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    selected: Boolean = false,
    warn: Boolean = false,
) {
    val a = stageAccent
    StageSurface(
        onClick = onClick,
        radius = 18.mpx,
        modifier = modifier.fillMaxWidth().height(64.mpx),
        idle = if (selected) Modifier.stageSelectedBar(a.accent, 18.mpx) else Modifier,
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.mpx),
            horizontalArrangement = Arrangement.spacedBy(16.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val on = if (focused) a.onAccent else null
            StageIcon(icon, on ?: if (selected) a.accent else StageColors.Muted, 23.mpx)
            Text(text, style = stageText(20, 700), color = on ?: if (selected) a.accent else StageColors.ItemText,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (value != null) {
                val c = on ?: if (warn) StageColors.Warn else StageColors.Dim
                Row(horizontalArrangement = Arrangement.spacedBy(5.mpx), verticalAlignment = Alignment.CenterVertically) {
                    if (warn) StageIcon(OwnTVIcon.WARNING, c, 18.mpx)
                    Text(value, style = stageText(16, 700), color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * `.tile2`: a card on white 5%, radius 26, padding 24 (More's pages, Settings' group cards). With [onClick]
 * it is focusable and focused = FX; without, a plain card. [focusedLook] draws FX for a card whose own
 * buttons hold the focus (Back up now).
 */
@Composable
fun StageTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    focusedLook: Boolean = false,
    padding: Dp = 24.mpx,
    content: @Composable ColumnScope.(focused: Boolean) -> Unit,
) {
    val r = 26.mpx
    val idle = Modifier.background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(r))
    if (onClick == null) {
        Column(
            modifier.then(if (focusedLook) Modifier.stageFocusLook(StageFocus.FX, r) else idle).padding(padding),
        ) { content(focusedLook) }
    } else {
        StageSurface(onClick = onClick, radius = r, modifier = modifier, focusStyle = StageFocus.FX, idle = idle, contentAlignment = Alignment.TopStart) { focused ->
            Column(Modifier.padding(padding)) { content(focused) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Posters, stepper, switch
// ---------------------------------------------------------------------------------------------

/**
 * `.pc`: a poster card — artwork with rounded corners and a soft drop shadow, the rating chip in its
 * corner, a one-line title below. Focused: the card lifts to 1.08 from 50% 40%, the POSTER ring and
 * glow go round the artwork only, and the title turns white/700.
 */
@Composable
fun StagePoster(
    title: String,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    rating: String? = null,
    onLongClick: (() -> Unit)? = null,
    /** The Separate grid's smaller posters: 15.5 px title, the 66×28 chip. */
    compact: Boolean = false,
    /** More › Favourites: a muted line under the title ("Movie"); the title is then 18/700 white. */
    line: String? = null,
    /** How far the title has been watched: the 6 px accent bar along the artwork's bottom, as on `.ep`. */
    progress: Float? = null,
    /** A finished title: the `.ep` ✓ badge in the top corner instead of a bar. */
    watched: Boolean = false,
    artwork: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = stageAccent
    val r = StageRadii.Poster
    val badge = Color(8, 12, 14)
    Column(
        modifier
            .width(width)
            .stageLift(focused, StageFocus.POSTER)
            .stageClickable(interaction, true, onClick, onLongClick),
    ) {
        Box(
            Modifier
                .size(width, height)
                .then(
                    if (focused) {
                        Modifier.stageFocusDecor(StageFocus.POSTER, r, accent)
                    } else {
                        Modifier.drawBehind {
                            drawBoxShadow(Color.Black.copy(alpha = 0.45f), 26.mpx.toPx(), r.toPx(), dy = 10.mpx.toPx())
                        }
                    },
                )
                .clip(RoundedCornerShape(r)),
        ) {
            artwork()
            if (rating != null) StageRatingChip(rating, Modifier.padding(8.mpx), small = compact)
            if (watched) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.mpx).size(if (compact) 26.mpx else 30.mpx)
                        .background(badge.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { StageIcon(OwnTVIcon.CHECK, accent.accent, if (compact) 16.mpx else 18.mpx) }
            } else if (progress != null && progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(6.mpx)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.mpx).background(accent.accent))
                }
            }
        }
        Spacer(Modifier.height(12.mpx))
        Text(
            title,
            style = if (line != null) stageText(18, 700) else stageText(if (compact) 15.5f else 17f, if (focused) 700 else 600),
            color = if (focused || line != null) Color.White else StageColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (line != null) Text(line, style = stageText(15, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Home's Keep watching card (`.pc` with a 16:9 `.img`): the still, a 6 px accent progress bar along its
 * bottom on black 50%, then the title in 700 and a muted line ("S1 · E2 · 31 min left"). Focus is the
 * POSTER treatment, as on any `.pc`. [titleSize] / [lineSize] differ between the hero (20 / 16) and the
 * rows (19 / 15).
 */
@Composable
fun StageStill(
    title: String,
    line: String?,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    titleSize: Int,
    lineSize: Int,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    artwork: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = stageAccent
    val r = StageRadii.Poster
    Column(
        modifier
            .width(width)
            .stageLift(focused, StageFocus.POSTER)
            .stageClickable(interaction, true, onClick, null),
    ) {
        Box(
            Modifier
                .size(width, height)
                .then(
                    if (focused) {
                        Modifier.stageFocusDecor(StageFocus.POSTER, r, accent)
                    } else {
                        Modifier.drawBehind {
                            drawBoxShadow(Color.Black.copy(alpha = 0.45f), 26.mpx.toPx(), r.toPx(), dy = 10.mpx.toPx())
                        }
                    },
                )
                .clip(RoundedCornerShape(r)),
        ) {
            artwork()
            if (progress != null && progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(6.mpx)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.mpx).background(accent.accent))
                }
            }
        }
        Spacer(Modifier.height(12.mpx))
        Text(title, style = stageText(titleSize, 700), color = Color(0xFFE6EEEA), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (line != null) {
            Spacer(Modifier.height((if (lineSize >= 16) 4 else 3).mpx))
            Text(line, style = stageText(lineSize, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** `.ep.w`: a watched episode's still at saturate(.55) brightness(.72). */
private val WatchedFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(0.55f)
        timesAssign(ColorMatrix().apply { setToScale(0.72f, 0.72f, 0.72f, 1f) })
    },
)

/**
 * `.ep` (P6-01): the 16:9 still, radius 16, with the episode number in its corner, a ✓ for a watched
 * one (its still dimmed), the 6 px progress bar for one in progress; the title 19/700 and the muted
 * line ("Jan 21, 2024 · 24 min · 12 min left") under it. Focused: lifted 1.06 from 50% 30%, the POSTER
 * ring and glow round the still. [artwork] gets the watched filter to apply to its image.
 */
@Composable
fun StageEpisode(
    title: String,
    line: String?,
    number: String,
    watched: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    artwork: @Composable BoxScope.(ColorFilter?) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = stageAccent
    val r = StageRadii.Poster
    val badge = Color(8, 12, 14)
    Column(
        modifier
            .width(width)
            .stageLift(focused, StageFocus.POSTER, episode = true)
            .stageClickable(interaction, true, onClick, onLongClick),
    ) {
        Box(
            Modifier
                .size(width, height)
                .then(
                    if (focused) {
                        Modifier.stageFocusDecor(StageFocus.POSTER, r, accent)
                    } else {
                        Modifier.drawBehind {
                            drawBoxShadow(Color.Black.copy(alpha = 0.45f), 26.mpx.toPx(), r.toPx(), dy = 10.mpx.toPx())
                        }
                    },
                )
                .clip(RoundedCornerShape(r)),
        ) {
            artwork(if (watched) WatchedFilter else null)
            Text(
                number,
                style = stageText(15, 800),
                color = StageColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .padding(10.mpx)
                    .height(30.mpx)
                    .defaultMinSize(minWidth = 30.mpx)
                    .background(badge.copy(alpha = 0.92f), RoundedCornerShape(9.mpx))
                    .padding(horizontal = 9.mpx)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
            if (watched) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.mpx).size(30.mpx).background(badge.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { StageIcon(OwnTVIcon.CHECK, accent.accent, 18.mpx) }
            }
            if (progress != null && progress > 0f && !watched) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(6.mpx)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.mpx).background(accent.accent))
                }
            }
        }
        Spacer(Modifier.height(12.mpx))
        Text(title, style = stageText(19, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (line != null) {
            Spacer(Modifier.height(3.mpx))
            Text(line, style = stageText(15.5f, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * `.menu .row2`: two choices side by side in a menu ("Oldest first | Newest first"), 46 high, 17/700.
 * Idle white 6% in muted; the current one accent 18% with accent text and a 1.5 px accent-60% ring;
 * focused = FILLED.
 */
@Composable
fun StageMenuChoice(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Attached to the current choice, so a menu can open on it. */
    focusRequester: FocusRequester? = null,
) {
    val a = stageAccent
    Row(
        modifier.fillMaxWidth().padding(start = 8.mpx, end = 8.mpx, top = 4.mpx, bottom = 8.mpx),
        horizontalArrangement = Arrangement.spacedBy(8.mpx),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val r = 13.mpx
            StageSurface(
                onClick = { onSelect(i) },
                radius = r,
                modifier = Modifier.weight(1f).height(46.mpx).then(if (on && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                idle = if (on) {
                    Modifier
                        .background(a.accent.copy(alpha = 0.18f), RoundedCornerShape(r))
                        .drawBehind { drawInnerRing(a.accent.copy(alpha = 0.6f), 1.5f * 1.mpx.toPx(), r.toPx()) }
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(r))
                },
                contentAlignment = Alignment.Center,
            ) { focused ->
                Text(
                    label,
                    style = stageText(17, 700),
                    color = if (focused) a.onAccent else if (on) a.accent else StageColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** `.pc .rt`: "★ 7.8" on near-black in the poster's top-left corner; [small] = 66×28 at 14 px. */
@Composable
fun StageRatingChip(rating: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Row(
        modifier
            .height(if (small) 28.mpx else 32.mpx)
            .defaultMinSize(minWidth = if (small) 66.mpx else 76.mpx)
            .background(Color(8, 12, 14).copy(alpha = 0.92f), RoundedCornerShape(10.mpx))
            .padding(horizontal = 10.mpx),
        horizontalArrangement = Arrangement.spacedBy(6.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StageIcon(OwnTVIcon.STAR, StageColors.RatingStar, if (small) 14.mpx else 15.mpx, filled = true)
        Text(rating, style = stageText(if (small) 14 else 15, 800), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * `.stp`: "− 90% +" inside a settings row. Display only: the row owns focus and ◀ ▶ change the value
 * (P10, "controls inside the rows").
 */
@Composable
fun StageStepper(value: String) {
    Row(
        Modifier
            .background(StageColors.ControlFill, RoundedCornerShape(13.mpx))
            .padding(4.mpx),
        horizontalArrangement = Arrangement.spacedBy(4.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.mpx), contentAlignment = Alignment.Center) {
            Text("−", style = stageText(20, 700), color = StageColors.Muted)
        }
        Text(
            value,
            style = stageText(18, 700),
            color = StageColors.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.mpx),
        )
        Box(Modifier.size(34.mpx), contentAlignment = Alignment.Center) {
            Text("+", style = stageText(20, 700), color = StageColors.Muted)
        }
    }
}

/** `.sw2`: 56×32 switch, display only like [StageStepper]. */
@Composable
fun StageSwitch(on: Boolean) {
    val a = stageAccent
    Box(
        Modifier
            .size(56.mpx, 32.mpx)
            .background(if (on) a.accent else Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.mpx)),
    ) {
        Box(
            Modifier
                .offset(x = if (on) 28.mpx else 4.mpx, y = 4.mpx)
                .size(24.mpx)
                .background(if (on) Color.White else Color(0xFFCFD8D4), CircleShape),
        )
    }
}
