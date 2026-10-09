package tv.own.owntv.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.LocalTvImeMetrics
import tv.own.owntv.ui.components.LocalTvImeWatcher
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * `.srchf` (tool rows, 48 high, radius 15) and `.sheet .srch` (54, radius 16): a quiet white-6% field,
 * the search glyph and a dim placeholder. TV behaviour as [tv.own.owntv.ui.components.SearchBar]: the
 * field takes D-pad focus like a button, OK opens the keyboard, Back returns to the field. Focused =
 * the 2 px focus ring.
 */
@Composable
fun StageSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = 48.mpx,
    radius: Dp = 15.mpx,
    horizontalPadding: Dp = 18.mpx,
    /** Open the keyboard as soon as the field appears (Settings search reached from a full page). */
    autoEdit: Boolean = false,
    /** Set = OK calls this instead of editing here (a full page's search hands over to Settings search). */
    onActivate: (() -> Unit)? = null,
    /** Typing started (true) or ended (false) — so a host can leave ◀/▶ to the text cursor meanwhile. */
    onEditingChange: (Boolean) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pillFocused by interaction.collectIsFocusedAsState()
    var editing by remember { mutableStateOf(false) }
    // After the page's own focus restore has run, so it cannot take the field straight back.
    LaunchedEffect(autoEdit) { if (autoEdit) { kotlinx.coroutines.delay(250); editing = true } }
    val pillFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeWatcher = LocalTvImeWatcher.current
    val imeMetrics = LocalTvImeMetrics.current
    val focus = stageAccent.focus
    LaunchedEffect(editing) {
        onEditingChange(editing)
        if (editing) {
            imeWatcher?.onImeRequested()
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
            kotlinx.coroutines.delay(120)
            runCatching { bringIntoView.bringIntoView() }
        } else {
            imeWatcher?.onImeDismissed()
        }
    }
    LaunchedEffect(editing, imeMetrics.keyboardTopPx, imeMetrics.visible) {
        if (editing && imeMetrics.visible) {
            kotlinx.coroutines.delay(32)
            runCatching { bringIntoView.bringIntoView() }
        }
    }
    Box(
        modifier
            .height(height)
            .background(StageColors.ControlFill, RoundedCornerShape(radius))
            .then(if (pillFocused || editing) Modifier.drawBehind { drawOuterRing(focus, 2.mpx.toPx(), radius.toPx()) } else Modifier)
            .focusRequester(pillFocus)
            .clickable(interaction, null) { if (onActivate != null) onActivate() else editing = true },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(OwnTVIcon.SEARCH, if (pillFocused || editing) StageColors.Text else StageColors.Dim, Modifier.size(18.mpx))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(placeholder, style = stageText(18, 400), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoView)
                        .focusRequester(fieldFocus)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged { if (editing && !it.isFocused) editing = false }
                        .onPreviewKeyEvent {
                            if (it.key == Key.Back) {
                                if (it.type == KeyEventType.KeyUp) {
                                    editing = false
                                    keyboard?.hide()
                                    runCatching { pillFocus.requestFocus() }
                                }
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = stageText(18, 500).copy(color = StageColors.Text),
                    singleLine = true,
                    cursorBrush = SolidColor(stageAccent.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        editing = false
                        keyboard?.hide()
                        runCatching { pillFocus.requestFocus() }
                    }),
                )
            }
        }
    }
}

/**
 * `.keys`: remote-key hints, 17 px muted, 26 apart; each key a `.kc` cap (14/800 on white 8%). In a
 * narrow column they wrap onto a second line rather than being cut.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StageKeyHints(hints: List<Pair<String, String>>, modifier: Modifier = Modifier, textSize: Int = 17) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(26.mpx),
        verticalArrangement = Arrangement.spacedBy(8.mpx),
    ) {
        hints.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 34.mpx)
                        .height(30.mpx)
                        // The mockup's 2 px bottom shade is left out: on the TV it read as a stray bar (owner).
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.mpx))
                        .padding(horizontal = 8.mpx),
                    contentAlignment = Alignment.Center,
                ) { Text(key, style = stageText(14, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Box(Modifier.size(8.mpx, 1.mpx))
                Text(label, style = stageText(textSize, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** `.live`: the red-dot LIVE badge on a video, 15/800 +0.08em on black 55%. */
@Composable
fun StageLiveBadge(text: String) {
    val red = Color(0xFFFF4D4D)
    Row(
        Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.mpx)).padding(horizontal = 12.mpx, vertical = 6.mpx),
        horizontalArrangement = Arrangement.spacedBy(8.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.mpx).drawBehind { drawBoxShadow(red, 10.mpx.toPx(), size.minDimension / 2f); drawCircle(red) })
        Text(text, style = stageText(15, 800, 0.08.em), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.spec span`: one stream fact on a video ("FHD", "50 FPS"), 13/800 on black 50%. */
@Composable
fun StageSpecChip(text: String) {
    Text(
        text,
        style = stageText(13, 800, 0.05.em),
        color = Color(0xFFE7EFEC),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.mpx)).padding(horizontal = 9.mpx, vertical = 5.mpx),
    )
}

/**
 * A progress track: `.prog` (6 high, accent → focus sweep on white 12%) or, with [flat], the channel
 * row's `.pb` (4 high, plain accent on white 10%).
 */
@Composable
fun StageProgress(fraction: Float, modifier: Modifier = Modifier, flat: Boolean = false) {
    val a = stageAccent
    Box(
        modifier
            .height(if (flat) 4.mpx else 6.mpx)
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(Color.White.copy(alpha = if (flat) 0.10f else 0.12f), cornerRadius = r)
                val w = size.width * fraction.coerceIn(0f, 1f)
                if (w > 0f) {
                    val fill = if (flat) Brush.linearGradient(listOf(a.accent, a.accent))
                    else Brush.horizontalGradient(listOf(a.accent, a.focus), endX = w)
                    drawRoundRect(fill, size = Size(w, size.height), cornerRadius = r)
                }
            },
    )
}
