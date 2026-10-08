package tv.own.owntv.features.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import tv.own.owntv.ui.components.trapAllFocusExit
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.live.edgeScrollSpec
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.stage.StageStepper
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSwitch
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/*
 * Stage Settings (P10, references P9-01 … P9-13): a group is one page. The band (crumb as the title,
 * the count, "Search all settings" on the same line), the rows on the left (x 50–1110 from y 210) and
 * the context panel on the right (x 1160–1856 from y 240) explaining whatever row has focus.
 */

/** What a settings row shows on its right (`.srow .val`). */
sealed interface SettingValue {
    /** `.sw2`: a switch; OK flips it. */
    class Switch(val on: Boolean) : SettingValue
    /** A choice made in a picker, written in accent with ▾. */
    class Choice(val text: String) : SettingValue
    /** Opens a screen of its own: accent text (may be empty) and ›. The only place a chevron appears. */
    class Opens(val text: String?) : SettingValue
    /** `.stp`: "− 85% +"; ◀ ▶ change it in place. */
    class Stepper(val text: String) : SettingValue
    /** `.seg2`: the options side by side, the chosen one lit; ◀ ▶ (or OK) move the choice. */
    class Segmented(val options: List<String>, val selected: Int) : SettingValue
    /** "19 saved · Reset". With nothing saved, just the "None saved" text. */
    class Saved(val text: String, val any: Boolean) : SettingValue
    /** A plain action word in full text colour ("Forget", "Check now"). */
    class Action(val text: String) : SettingValue
    /** Anything else the mockup draws there (accent swatches, profile tags). */
    class Custom(val content: @Composable () -> Unit) : SettingValue
}

/**
 * The context panel's text for one row: [title] as its heading, the long [text], and — for a row whose
 * value is picked from a list — the [choices] with the current one ([chosen]) and the [recommended] one.
 */
@Stable
data class SettingHelp(
    val title: String,
    val text: String,
    val choices: List<String> = emptyList(),
    val chosen: Int = -1,
    val recommended: Int = -1,
    val hints: List<Pair<String, String>> = emptyList(),
    /** Drawn under the text: Playlists lists the playlists themselves (P9-03). */
    val extra: (@Composable () -> Unit)? = null,
    /** Drawn under the key hints (P10B: Customize's span help, owner). */
    val footer: (@Composable () -> Unit)? = null,
)

/**
 * What a row of a Stage settings page opens in the panel instead of a popup (owner, P12): a choice
 * list, a stepper. [owner] tells the one that opened it from a later one; [content] is drawn under the
 * row's heading and text.
 */
class PanelEditor(
    val owner: Any,
    val onDismiss: () -> Unit,
    val content: @Composable androidx.compose.foundation.layout.ColumnScope.(help: SettingHelp?) -> Unit,
)

/** A choice list for [PanelChoices]. */
class PanelPicker(
    val options: List<Pair<String, String>>,
    val selected: String,
    val onSelect: (String) -> Unit,
    val descriptions: Map<String, String>,
    val searchable: Boolean,
    /** Drawn under an option (the layout chooser's little bar preview). */
    val preview: (@Composable (String) -> Unit)? = null,
)

/** The focused row's help, published by each row and drawn by the page's panel. */
@Stable
class SettingsPanelState {
    var help by mutableStateOf<SettingHelp?>(null)
    var editor by mutableStateOf<PanelEditor?>(null)
    /** The row that opened [editor]: focus goes back to it when the list closes. */
    var opener: FocusRequester? = null
}

val LocalSettingsPanel = staticCompositionLocalOf<SettingsPanelState?> { null }

/** True inside a Stage settings page: [Row2] then draws [StageSettingRow]. Other screens keep their rows. */
val LocalStageRows = staticCompositionLocalOf { false }

/**
 * The panel text for the row [key]: its long explanation from [SETTING_HELP] (falling back to the
 * row's own line), the choices, and key hints from what the row does.
 */
@Composable
fun settingHelp(
    key: String?,
    title: String,
    desc: String?,
    value: SettingValue?,
    choices: List<String> = emptyList(),
    chosen: Int = -1,
    recommended: Int = -1,
    pinnable: Boolean = true,
): SettingHelp {
    val text = key?.let { SETTING_HELP[it] }?.let { stringResource(it, *NO_ARGS) } ?: desc.orEmpty()
    // A switch lists On and Off as its choices (P9-09, P9-11).
    if (value is SettingValue.Switch && choices.isEmpty()) {
        val rec = key?.let { SWITCH_RECOMMENDED[it] }?.let { if (it) 0 else 1 } ?: -1
        return SettingHelp(
            title, text,
            listOf(stringResource(R.string.common_on), stringResource(R.string.common_off)),
            if (value.on) 0 else 1, rec, settingHints(value, pinnable),
        )
    }
    // A choice row that was handed no index finds its current value among the choices by its label.
    val current = if (chosen >= 0) chosen else when (value) {
        is SettingValue.Choice -> choices.indexOf(value.text)
        is SettingValue.Segmented -> value.selected
        else -> -1
    }
    return SettingHelp(title, text, choices, current, recommended, settingHints(value, pinnable))
}

/** A row's name and short line on a Stage page: the page's own wording where [SETTING_TITLE] / [SETTING_LINE] have one. */
class SettingWords(val title: String, val line: String?)

@Composable
fun settingWords(key: String?, title: String, desc: String?): SettingWords = SettingWords(
    key?.let { SETTING_TITLE[it] }?.let { stringResource(it) } ?: title,
    key?.let { SETTING_LINE[it] }?.let { stringResource(it, *NO_ARGS) } ?: desc,
)

/** Key hints for a row, from what pressing it does. [pinnable] adds "Hold OK · Pin to Quick". */
@Composable
fun settingHints(value: SettingValue?, pinnable: Boolean = true): List<Pair<String, String>> {
    val ok = stringResource(R.string.common_ok)
    val change = stringResource(R.string.settings_key_change)
    val keys = when (value) {
        is SettingValue.Stepper -> listOf(ok to stringResource(R.string.settings_key_open), "◀ ▶" to change)
        is SettingValue.Segmented -> listOf("◀ ▶" to change)
        is SettingValue.Switch -> listOf(ok to stringResource(R.string.settings_key_switch))
        is SettingValue.Opens, null -> listOf(ok to stringResource(R.string.settings_key_open))
        else -> listOf(ok to change)
    }
    return keys + listOfNotNull(
        if (pinnable) stringResource(R.string.content_key_hold_ok) to stringResource(R.string.settings_row_menu_pin) else null,
        stringResource(R.string.common_back) to stringResource(R.string.common_nav_settings),
    )
}

/**
 * The whole page: band, rows and panel. [rows] is the scrolling column; [panelTop] is drawn above the
 * focused row's explanation (Appearance's live preview).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StageSettingsPage(
    group: String,
    count: String,
    searchQuery: String,
    onSearchQuery: (String) -> Unit,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    searchFocus: FocusRequester = remember { FocusRequester() },
    /** On the rows column: requesting it puts focus on the page's first row. */
    rowsFocus: FocusRequester = remember { FocusRequester() },
    panel: SettingsPanelState = remember { SettingsPanelState() },
    /** A short title with no "Settings ›" before it (the search results page). */
    crumb: Boolean = true,
    panelTop: (@Composable ColumnScope.() -> Unit)? = null,
    /** A full page's path between "Settings ›" and [group] ("Layout" for Settings › Layout › Panel widths). */
    parents: List<String> = emptyList(),
    /** A full page's own tools in the band, left of the search (which then narrows to 360). */
    tools: (@Composable RowScope.() -> Unit)? = null,
    searchAutoEdit: Boolean = false,
    onSearchActivate: (() -> Unit)? = null,
    /** A list page's tool row (P10B: Customize's tabs, Sort, Filter…), above the rows. */
    toolbar: (@Composable RowScope.() -> Unit)? = null,
    /** A list too long for one column (Customize): drawn in place of [rows], given the rows' position. */
    list: (@Composable (Modifier) -> Unit)? = null,
    /** Off: the path starts at [parents] ("Add a source › Type it here", P10B-08 … 12), not at Settings. */
    settingsRoot: Boolean = true,
    /** Off where there is no Settings to search (the setup wizard reuses the Add a source pages). */
    showSearch: Boolean = true,
    rows: @Composable ColumnScope.() -> Unit = {},
) {
    val searching = searchQuery.isNotBlank()
    androidx.compose.runtime.LaunchedEffect(searching) { if (searching) panel.help = null }
    // Popups opened from this page are labelled with its path, "SETTINGS · PLAYER" (P1-06).
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val from = ((if (settingsRoot) listOf(stringResource(R.string.common_nav_settings)) else emptyList()) + parents).lastOrNull()
    val eyebrow = (if (from != null) stringResource(R.string.settings_breadcrumb_eyebrow, from, group) else group).uppercase(locale)
    CompositionLocalProvider(LocalSettingsPanel provides panel, tv.own.owntv.ui.stage.LocalPopupEyebrow provides eyebrow) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            // The mockup's 1920 px frame as fractions of the real width, so other zooms reflow.
            val w = maxWidth / 1920f
            Row(
                Modifier.fillMaxWidth().padding(start = w * 64, end = w * 64, top = 128.mpx).height(60.mpx),
                horizontalArrangement = Arrangement.spacedBy(16.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    // A long path shortens before it pushes the tools and the search off the band.
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (crumb) {
                        // Two steps only (owner): the page it came from, then this one.
                        ((if (settingsRoot) listOf(stringResource(R.string.common_nav_settings)) else emptyList()) + parents).takeLast(1).forEach { p ->
                            Text(p, style = stageText(42, 800, (-1f / 46f).em), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            OwnTVIcon(OwnTVIcon.CHEVRON, StageColors.Dim, Modifier.size(30.mpx))
                        }
                    }
                    // The path before the title shortens first (it is measured last); the tools and the search never move.
                    Text(group, style = stageText(42, 800, (-1f / 46f).em), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(count, style = stageText(17, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.mpx))
                }
                if (tools != null) Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically, content = tools)
                if (showSearch) StageSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQuery,
                    placeholder = stringResource(R.string.more_settings_search),
                    modifier = Modifier.widthIn(max = if (tools != null) 360.mpx else 520.mpx).focusRequester(searchFocus),
                    height = 52.mpx,
                    radius = 18.mpx,
                    horizontalPadding = 20.mpx,
                    autoEdit = searchAutoEdit,
                    onActivate = onSearchActivate,
                )
            }
            val rowsTop = if (toolbar != null) 300.mpx else 210.mpx
            if (toolbar != null) {
                Row(
                    Modifier.padding(start = w * 50, top = 210.mpx).width(w * 1060).height(84.mpx).padding(horizontal = 6.mpx),
                    horizontalArrangement = Arrangement.spacedBy(8.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                    content = toolbar,
                )
            }
            if (list != null) list(Modifier.padding(start = w * 50, top = rowsTop).width(w * 1060).fillMaxHeight())
            else CompositionLocalProvider(LocalBringIntoViewSpec provides edgeScrollSpec) {
                Column(
                    Modifier
                        .padding(start = w * 50, top = rowsTop)
                        .width(w * 1060)
                        .fillMaxHeight()
                        .focusRequester(rowsFocus)
                        .focusGroup()
                        .verticalScroll(scroll)
                        .padding(bottom = 40.mpx),
                    verticalArrangement = Arrangement.spacedBy(6.mpx),
                    content = rows,
                )
            }
            val help = panel.help
            val editor = panel.editor
            if (help != null || panelTop != null || editor != null) {
                Column(
                    Modifier
                        .padding(start = w * 1160, top = 240.mpx)
                        .width(w * 696)
                        .heightIn(max = maxHeight - 280.mpx)
                        .stageGlass(30.mpx)
                        .padding(26.mpx),
                ) {
                    if (editor != null) SettingPanelEditor(editor, help, onClosed = { panel.opener?.let { r -> runCatching { r.requestFocus() } } })
                    else {
                        // A row that brings its own picture (Popup size's sample) shows it in place of the page's.
                        if (panelTop != null && help?.extra == null) panelTop()
                        if (help != null) SettingPanelBody(help)
                    }
                }
            }
        }
    }
}

/** `.helpc h5`: 13/800 caps, +0.13em, dim. */
@Composable
fun SettingPanelHeading(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = stageText(13, 800, 0.13.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.padding(bottom = 8.mpx))
}

@Composable
private fun SettingPanelBody(help: SettingHelp) {
    if (help.title.isNotEmpty()) SettingPanelHeading(help.title)
    Text(help.text, style = stageText(17, 500).copy(lineHeight = 27.mpxSpLine()), color = PanelText)
    help.extra?.invoke()
    if (help.choices.isNotEmpty()) {
        Box(Modifier.padding(top = 22.mpx, bottom = 16.mpx).fillMaxWidth().height(1.mpx).background(Color.White.copy(alpha = 0.1f)))
        SettingPanelHeading(stringResource(R.string.settings_panel_choices))
        val a = stageAccent
        help.choices.forEachIndexed { i, label ->
            val on = i == help.chosen
            Row(Modifier.height(44.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                StageRadio(on)
                Text(label, style = stageText(18, 600), color = if (on) StageColors.Text else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    // A long choice gives way to the RECOMMENDED tag, never the other way round.
                    modifier = Modifier.weight(1f, fill = false))
                if (i == help.recommended) {
                    Box(Modifier.padding(start = 6.mpx)) { StageTag(stringResource(R.string.settings_panel_recommended).uppercase()) }
                }
            }
        }
    }
    if (help.hints.isNotEmpty()) StageKeyHints(help.hints, Modifier.padding(top = 20.mpx), textSize = 15)
    help.footer?.invoke()
}

/**
 * Opens [content] in the page's panel while this is composed — the way a settings row edits its value
 * without a popup (owner, P12). Returns false off a Stage settings page or inside a popup, where the
 * caller draws its own popup instead.
 */
@Composable
fun panelEditor(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.(help: SettingHelp?) -> Unit): Boolean {
    val panel = LocalSettingsPanel.current
    if (panel == null || tv.own.owntv.ui.components.LocalStagePopup.current) return false
    val token = remember { Any() }
    val editor = PanelEditor(token, onDismiss, content)
    androidx.compose.runtime.SideEffect { panel.editor = editor }
    androidx.compose.runtime.DisposableEffect(panel) { onDispose { if (panel.editor?.owner === token) panel.editor = null } }
    return true
}

/** The panel while a row's editor is open: the row's heading and text, a line, then the editor. Back closes it; focus returns to the row after. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SettingPanelEditor(editor: PanelEditor, help: SettingHelp?, onClosed: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(editor.owner) { onDispose { view.post { onClosed() } } }
    androidx.activity.compose.BackHandler { editor.onDismiss() }
    if (help != null && help.title.isNotEmpty()) SettingPanelHeading(help.title)
    if (help != null) Text(help.text, style = stageText(17, 500).copy(lineHeight = 27.mpxSpLine()), color = PanelText, maxLines = 3, overflow = TextOverflow.Ellipsis)
    Box(Modifier.padding(top = 22.mpx, bottom = 16.mpx).fillMaxWidth().height(1.mpx).background(Color.White.copy(alpha = 0.1f)))
    editor.content(this, help)
}

/**
 * A choice list in the panel: CHOICES as focusable rows — focus starts on the current one, ▲ ▼ move,
 * OK picks, ◀ or Back closes without a change. Focus never leaves the list.
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.PanelChoices(picker: PanelPicker, help: SettingHelp?, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = if (picker.searchable && query.isNotBlank()) {
        picker.options.filter { it.second.contains(query.trim(), ignoreCase = true) }
    } else picker.options
    val selIndex = shown.indexOfFirst { it.first == picker.selected }.coerceAtLeast(0)
    val recommended = help?.choices?.getOrNull(help.recommended)
    val first = remember { FocusRequester() }
    val search = remember { FocusRequester() }
    val list = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (selIndex - 2).coerceAtLeast(0))
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // The list is composed in the frame the row is clicked; focus it once it is placed.
        kotlinx.coroutines.delay(60)
        runCatching { (if (picker.searchable) search else first).requestFocus() }
    }
    SettingPanelHeading(stringResource(R.string.settings_panel_choices))
    Column(
        Modifier.weight(1f, fill = false).trapAllFocusExit().focusGroup().onPreviewKeyEvent { e ->
            if (e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown) { onDismiss(); true } else false
        },
    ) {
        if (picker.searchable) {
            tv.own.owntv.ui.stage.StageSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.common_search_hint),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.mpx).focusRequester(search),
            )
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false), state = list, verticalArrangement = Arrangement.spacedBy(2.mpx)) {
            items(shown.size, key = { shown[it].first }) { i ->
                val (value, label) = shown[i]
                val on = value == picker.selected
                StageSurface(
                    onClick = { picker.onSelect(value) },
                    radius = 14.mpx,
                    focusStyle = StageFocus.FX,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.mpx).then(if (i == selIndex) Modifier.focusRequester(first) else Modifier),
                ) { focused ->
                    Row(Modifier.padding(horizontal = 14.mpx, vertical = 8.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                        StageRadio(on)
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(label, style = stageText(18, 600), color = if (on || focused) StageColors.Text else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            picker.descriptions[value]?.let { Text(it, style = stageText(14.5f, 500), color = StageColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            picker.preview?.let { Box(Modifier.padding(top = 8.mpx)) { it(value) } }
                        }
                        if (label == recommended) StageTag(stringResource(R.string.settings_panel_recommended).uppercase())
                    }
                }
            }
        }
    }
    StageKeyHints(
        listOf(
            "▲ ▼" to stringResource(R.string.content_key_select),
            stringResource(R.string.common_ok) to stringResource(R.string.settings_key_change),
            stringResource(R.string.common_back) to stringResource(R.string.common_cancel),
        ),
        Modifier.padding(top = 18.mpx), textSize = 15,
    )
}

/**
 * A number changed in the panel: the value 40/800 in accent between ◀ ▶ — ◀ ▶ change it at once (as the
 * old popup's − / + did), then Reset and Done. Back or Done closes.
 */
@Composable
fun PanelStepper(
    value: String,
    onStep: (Int) -> Unit,
    onReset: (() -> Unit)?,
    onDone: () -> Unit,
    /** Drawn above the value: what the setting does, live (Popup size's sample popup). */
    preview: (@Composable () -> Unit)? = null,
    /** The value is kept only on OK (the day steppers): the button says OK and Back cancels. */
    confirm: Boolean = false,
) {
    val a = stageAccent
    val first = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    preview?.let { Box(Modifier.fillMaxWidth().padding(bottom = 18.mpx), contentAlignment = Alignment.Center) { it() } }
    Column(Modifier.trapAllFocusExit().focusGroup()) {
        StageSurface(
            onClick = onDone,
            radius = 18.mpx,
            focusStyle = StageFocus.FX,
            modifier = Modifier.fillMaxWidth().height(84.mpx).focusRequester(first).onPreviewKeyEvent { e ->
                val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                if (d != 0 && e.type == KeyEventType.KeyDown) onStep(d)
                d != 0
            },
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.mpx), verticalAlignment = Alignment.CenterVertically) {
                OwnTVIcon(OwnTVIcon.CHEVRON, StageColors.Muted, Modifier.size(26.mpx).graphicsLayer { rotationZ = 180f })
                Text(value, style = stageText(36, 800), color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                OwnTVIcon(OwnTVIcon.CHEVRON, StageColors.Muted, Modifier.size(26.mpx))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx, Alignment.End)) {
            if (onReset != null) tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_reset), onClick = onReset, height = 52.mpx, textSize = 18)
            tv.own.owntv.ui.stage.StageButton(stringResource(if (confirm) R.string.common_ok else R.string.common_done), onClick = onDone, height = 52.mpx, textSize = 18, tinted = true)
        }
    }
    StageKeyHints(
        listOf(
            "◀ ▶" to stringResource(R.string.settings_key_change),
            stringResource(R.string.common_back) to stringResource(if (confirm) R.string.common_cancel else R.string.common_done),
        ),
        Modifier.padding(top = 18.mpx), textSize = 15,
    )
}

/** A few on/off values in the panel (External player's sections): one switch row each, then Done. */
@Composable
fun PanelSwitches(items: List<Triple<String, Boolean, () -> Unit>>, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    Column(Modifier.trapAllFocusExit().focusGroup()) {
        items.forEachIndexed { i, (label, on, toggle) ->
            tv.own.owntv.ui.stage.StagePopupOption(
                title = label, onClick = toggle,
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                trailing = { tv.own.owntv.ui.stage.StageSwitch(on) },
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.mpx), horizontalArrangement = Arrangement.End) {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = onDone, height = 52.mpx, textSize = 18, tinted = true)
        }
    }
}

/**
 * The exit question as a real popup draws it at [sizePercent] and the user's popup font size, not
 * focusable: Popup size's live sample in the panel.
 */
@Composable
fun PopupSizeSample(sizePercent: Int) {
    val base = androidx.compose.ui.platform.LocalDensity.current
    val sized = androidx.compose.ui.unit.Density(
        density = base.density * tv.own.owntv.core.theme.PopupSizeScale.factor(sizePercent),
        fontScale = base.fontScale / tv.own.owntv.ui.theme.LocalUiFontScaleFactor.current * tv.own.owntv.ui.theme.LocalPopupFontScaleFactor.current,
    )
    val a = stageAccent
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides sized) {
        Column(Modifier.width(560.mpx).stageGlass(30.mpx, overContent = true).padding(30.mpx)) {
            Text(stringResource(R.string.content_exit_owntv), style = stageText(38, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.content_exit_confirmation), style = stageText(18, 400), color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx))
            Row(Modifier.fillMaxWidth().padding(top = 46.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx, Alignment.End)) {
                Box(Modifier.height(56.mpx).background(a.accent, RoundedCornerShape(StageRadii.Button)).padding(horizontal = 26.mpx), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.common_cancel), style = stageText(19, 700), color = a.onAccent, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Box(Modifier.height(56.mpx).background(a.accent.copy(alpha = 0.2f), RoundedCornerShape(StageRadii.Button)).padding(horizontal = 26.mpx), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.common_exit), style = stageText(19, 700), color = a.accent, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val PanelText = Color(0xFFD3DCD8)

/** A "label  value" line in the panel (OpenSubtitles' account details). */
@Composable
fun SettingPanelLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
        Text(label, style = stageText(16, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = stageText(16, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.radio`: a 22 px circle, a 2 px dim ring; the chosen one a 7 px accent ring. */
@Composable
fun StageRadio(on: Boolean) {
    val a = stageAccent
    Box(
        Modifier.size(22.mpx).drawBehind {
            val r = size.minDimension / 2f
            val ring = (if (on) 7 else 2) * 1.mpx.toPx()
            drawCircle(if (on) a.accent else StageColors.Dim, radius = r - ring / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(ring))
        },
    )
}

@Composable
private fun Int.mpxSpLine() = with(androidx.compose.ui.platform.LocalDensity.current) { this@mpxSpLine.mpx.toSp() }

/**
 * `.sHead`: "PLAYER 8" above a group's part — 13/800 caps +0.13em in dim, the count darker.
 * [first] = the page's first heading (2 px above instead of 18).
 */
@Composable
fun StageSettingsHeading(text: String, count: Int?, first: Boolean = false) {
    Row(
        Modifier.padding(start = 22.mpx, top = if (first) 2.mpx else 18.mpx, bottom = 8.mpx),
        horizontalArrangement = Arrangement.spacedBy(10.mpx),
    ) {
        Text(text.uppercase(), style = stageText(13, 800, 0.13.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // A heading over a single setting carries no count (P10B-01 NOW TRENDING).
        if (count != null) Text(count.toString(), style = stageText(13, 800, 0.13.em), color = HeadingCount, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val HeadingCount = Color(0xFF4D5955)

/**
 * `.srow`: 84 high, radius 20, padding 0 22, gap 18 — a 24 px muted icon, the title 21/700 over a
 * 15.5 px muted line, and the value on the right. Idle = nothing, focused = FX. A held OK calls
 * [onLongClick] (pin to Quick) without the release also firing [onClick]. [onStep] receives −1 / +1
 * for ◀ ▶ on a stepper or segmented value.
 */
@Composable
fun StageSettingRow(
    icon: OwnTVIcon,
    title: String,
    desc: String?,
    value: SettingValue?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    help: SettingHelp? = null,
    onLongClick: (() -> Unit)? = null,
    onStep: ((Int) -> Unit)? = null,
    /** The small accent dot after the title: this row is also pinned to Quick. */
    pinned: Boolean = false,
    enabled: Boolean = true,
    /** Part of a span being picked (P10B): a faint accent wash. */
    marked: Boolean = false,
    /** Keep the panel on this row while focus is in its actions (P10B list pages). */
    keepPanel: Boolean = false,
) {
    val a = stageAccent
    val panel = LocalSettingsPanel.current
    var longAt by remember { mutableLongStateOf(0L) }
    val self = remember { FocusRequester() }
    StageSurface(
        onClick = {
            if (android.os.SystemClock.uptimeMillis() - longAt > 800) {
                // A choice list this opens in the panel hands focus back here when it closes.
                panel?.opener = self
                onClick()
            }
        },
        radius = StageRadii.Row,
        focusStyle = StageFocus.FX,
        enabled = enabled,
        // A span member, or the row whose actions the panel is showing while focus is in them.
        // …or the row whose choices are open in the panel.
        idle = if (marked || keepPanel || (panel?.editor != null && panel.opener === self)) Modifier.background(a.accent.copy(alpha = 0.14f), RoundedCornerShape(StageRadii.Row)) else Modifier,
        onLongClick = onLongClick?.let { l -> { longAt = android.os.SystemClock.uptimeMillis(); l() } },
        modifier = modifier
            .focusRequester(self)
            .fillMaxWidth()
            .height(84.mpx)
            .then(
                if (onStep != null) {
                    Modifier.onPreviewKeyEvent { e ->
                        val step = when (e.key) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            else -> 0
                        }
                        if (step != 0 && e.type == KeyEventType.KeyDown) onStep(step)
                        step != 0
                    }
                } else Modifier,
            ),
    ) { focused ->
        // While focused the panel follows this row, value changes included.
        if ((focused || keepPanel) && panel != null) androidx.compose.runtime.SideEffect { panel.help = help }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(icon, StageColors.Muted, Modifier.size(24.mpx))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = stageText(21, 700), color = if (enabled) StageColors.Text else StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (pinned) Box(Modifier.size(7.mpx).background(a.accent, CircleShape))
                }
                if (!desc.isNullOrBlank()) {
                    Text(
                        desc, style = stageText(15.5f, 500),
                        color = if (focused) FocusedDesc else StageColors.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.mpx),
                    )
                }
            }
            if (value != null) SettingValueView(value)
        }
    }
}

private val FocusedDesc = Color(0xFFD9E6E1)

@Composable
private fun SettingValueView(value: SettingValue) {
    val a = stageAccent
    val valueStyle = stageText(18, 700)
    Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
        when (value) {
            is SettingValue.Switch -> StageSwitch(value.on)
            is SettingValue.Choice -> {
                Text(value.text, style = valueStyle, color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                OwnTVIcon(OwnTVIcon.CHEVRON_DOWN, StageColors.Muted, Modifier.size(20.mpx))
            }
            is SettingValue.Opens -> {
                if (!value.text.isNullOrBlank()) {
                    Text(value.text, style = valueStyle, color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                OwnTVIcon(OwnTVIcon.CHEVRON, StageColors.Muted, Modifier.size(20.mpx))
            }
            is SettingValue.Stepper -> StageStepper(value.text)
            is SettingValue.Segmented -> SegmentedDisplay(value.options, value.selected)
            is SettingValue.Saved -> {
                Text(value.text, style = valueStyle, color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (value.any) {
                    Text("·", style = valueStyle, color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.offset(x = (-4).mpx))
                    Text(stringResource(R.string.common_reset), style = valueStyle, color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            is SettingValue.Action -> Text(value.text, style = valueStyle, color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            is SettingValue.Custom -> value.content()
        }
    }
}

/** `.seg2` as a row value: drawn only — the row owns focus, ◀ ▶ move the choice. */
@Composable
private fun SegmentedDisplay(options: List<String>, selected: Int) {
    Row(
        Modifier.background(StageColors.ControlFill, RoundedCornerShape(15.mpx)).padding(4.mpx),
        horizontalArrangement = Arrangement.spacedBy(2.mpx),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .height(40.mpx)
                    .then(if (on) Modifier.background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(11.mpx)) else Modifier)
                    .padding(horizontal = 14.mpx),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = stageText(17, 700), color = if (on) StageColors.Text else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** `.swrow`: the accent presets, then the colour in use ringed white (P9-04). */
@Composable
fun AccentSwatches(presets: List<Color>, current: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
        (presets + current).forEachIndexed { i, c ->
            val on = i == presets.size
            Box(
                Modifier
                    .size(30.mpx)
                    .drawBehind {
                        val px = 1.mpx.toPx()
                        val r = size.minDimension / 2f
                        if (on) {
                            drawCircle(Color.Black.copy(alpha = 0.5f), radius = r + 5 * px)
                            drawCircle(Color.White, radius = r + 3 * px)
                        }
                        drawCircle(c, radius = r)
                        if (!on) drawCircle(Color.Black.copy(alpha = 0.25f), radius = r - px, style = androidx.compose.ui.graphics.drawscope.Stroke(2 * px))
                    },
            )
        }
    }
}

/** A short message in place of rows: a bold line and an optional muted one (no search match, empty Quick). */
@Composable
fun StageSettingsNote(title: String, body: String?) {
    Column(Modifier.padding(horizontal = 22.mpx, vertical = 16.mpx)) {
        Text(title, style = stageText(21, 700), color = StageColors.Text)
        if (body != null) Text(body, style = stageText(17, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 6.mpx))
    }
}

/**
 * A search result (P8-02): where the setting lives in dim above its title, and its value muted with ›.
 * Opening it goes to the setting itself.
 */
@Composable
fun StageSearchResultRow(icon: OwnTVIcon, path: String, title: String, value: String?, onClick: () -> Unit) {
    StageSurface(
        onClick = onClick,
        radius = StageRadii.Row,
        focusStyle = StageFocus.FX,
        modifier = Modifier.fillMaxWidth().height(84.mpx),
    ) { _ ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(icon, StageColors.Muted, Modifier.size(24.mpx))
            Column(Modifier.weight(1f)) {
                Text(path, style = stageText(15.5f, 500), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 3.mpx))
                Text(title, style = stageText(21, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                if (!value.isNullOrBlank()) Text(value, style = stageText(18, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                OwnTVIcon(OwnTVIcon.CHEVRON, StageColors.Muted, Modifier.size(20.mpx))
            }
        }
    }
}

/** Playlists' panel (P9-03): each playlist's mark, its name and when it last synced. */
@Composable
fun PlaylistSyncList(sources: List<tv.own.owntv.core.database.entity.SourceEntity>) {
    Column(Modifier.padding(top = 18.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
        sources.forEachIndexed { i, src ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
                tv.own.owntv.ui.stage.StagePlaylistMark(tv.own.owntv.ui.stage.PlaylistMark.of(src.name, i))
                Text(src.name, style = stageText(18, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                src.lastSyncAt?.let {
                    Text(
                        stringResource(R.string.settings_synced_at, tv.own.owntv.features.downloads.recordingWhen(it)),
                        style = stageText(15, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Settings search from a full page (P10B): set by Settings. OK on a full page's search field calls it,
 * and Settings opens its search with the keyboard up.
 */
val LocalSettingsSearch = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * A page Settings opens full screen (P10B, references P10B-01 … 21), drawn exactly as a group page:
 * the band (Settings › [parents] › [title] + [count], [tools], search), the rows and the context panel.
 * Back returns to the group it was opened from.
 */
@Composable
fun StageFullPage(
    parents: List<String>,
    title: String,
    count: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: ScrollState = androidx.compose.foundation.rememberScrollState(),
    rowsFocus: FocusRequester = remember { FocusRequester() },
    tools: (@Composable RowScope.() -> Unit)? = null,
    panelTop: (@Composable ColumnScope.() -> Unit)? = null,
    toolbar: (@Composable RowScope.() -> Unit)? = null,
    list: (@Composable (Modifier) -> Unit)? = null,
    /** Off when the page handles Back itself (Customize cancels a span first). */
    handleBack: Boolean = true,
    /** Off for a page opened from another page: its path starts at that page (P10B-03, 06, 08 … 12). */
    settingsRoot: Boolean = true,
    rows: @Composable ColumnScope.() -> Unit = {},
) {
    androidx.activity.compose.BackHandler(enabled = handleBack) { onBack() }
    val search = LocalSettingsSearch.current
    CompositionLocalProvider(LocalStageRows provides true) {
        StageSettingsPage(
            group = title,
            count = count,
            searchQuery = "",
            onSearchQuery = {},
            scroll = scroll,
            modifier = modifier,
            rowsFocus = rowsFocus,
            parents = parents,
            tools = tools,
            panelTop = panelTop,
            onSearchActivate = { search?.invoke() },
            toolbar = toolbar,
            list = list,
            settingsRoot = settingsRoot,
            // A full page outside Settings (the setup wizard) has nowhere to hand its search to.
            showSearch = search != null,
            rows = rows,
        )
    }
}

/**
 * `fld` (P10B): a text field as a settings row — the label as the title, the value (or the dim
 * placeholder) as its line, "Edit" on the right while focused. TV behaviour as [OwnTVTextField]: D-pad
 * focus only highlights the row, OK opens the keyboard, Back or Done ends editing and calls [onDone].
 * A password row shows dots; ▶ shows or hides it.
 */
@Composable
fun StageFieldRow(
    icon: OwnTVIcon,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    help: SettingHelp,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    onDone: () -> Unit = {},
    /** Where Back goes, for the key hint ("Add a source" on a form, P10B-10). */
    backLabel: String? = null,
    /** "▶ Start Import" on a form whose button sits in the panel; a password row's ▶ shows the text instead. */
    submitHint: Pair<String, String>? = null,
) {
    val a = stageAccent
    val panel = LocalSettingsPanel.current
    var editing by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val imeWatcher = tv.own.owntv.ui.components.LocalTvImeWatcher.current
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    fun stop() {
        if (!editing) return
        editing = false
        keyboard?.hide()
        runCatching { rowFocus.requestFocus() }
        onDone()
    }
    androidx.compose.runtime.LaunchedEffect(editing) {
        if (editing) {
            imeWatcher?.onImeRequested()
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
            kotlinx.coroutines.delay(120)
            runCatching { bring.bringIntoView() }
        } else {
            imeWatcher?.onImeDismissed()
        }
    }
    StageSurface(
        onClick = { editing = true },
        radius = StageRadii.Row,
        focusStyle = StageFocus.FX,
        // While the keyboard is up the field holds focus, so the row keeps its focused look itself.
        highlighted = editing,
        modifier = modifier
            .fillMaxWidth()
            .height(84.mpx)
            .focusRequester(rowFocus)
            .then(
                if (password) Modifier.onPreviewKeyEvent { e ->
                    if (!editing && e.key == Key.DirectionRight) {
                        if (e.type == KeyEventType.KeyDown) shown = !shown
                        true
                    } else false
                } else Modifier,
            ),
    ) { focused ->
        val hints = listOfNotNull(
            stringResource(R.string.common_ok) to stringResource(R.string.common_edit),
            if (password) "▶" to stringResource(if (shown) R.string.common_hide else R.string.common_show) else submitHint,
            stringResource(R.string.common_back) to (backLabel ?: stringResource(R.string.common_nav_settings)),
        )
        if ((focused || editing) && panel != null) androidx.compose.runtime.SideEffect { panel.help = help.copy(hints = hints) }
        val lit = focused || editing
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(icon, StageColors.Muted, Modifier.size(24.mpx))
            Column(Modifier.weight(1f)) {
                Text(label, style = stageText(21, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = 3.mpx)) {
                    if (value.isEmpty() && !editing) {
                        Text(placeholder, style = stageText(15.5f, 500), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bring)
                            .focusRequester(fieldFocus)
                            .focusProperties { canFocus = editing }
                            .onFocusChanged { if (editing && !it.isFocused) stop() }
                            .onPreviewKeyEvent {
                                if (it.key == Key.Back) {
                                    if (it.type == KeyEventType.KeyUp) stop()
                                    true
                                } else false
                            },
                        textStyle = stageText(15.5f, 500).copy(color = if (focused || editing) FocusedDesc else StageColors.Text),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(a.accent),
                        // A password row gets the password keyboard: the text one auto-corrects and
                        // capitalises, which turned a correct backup password into a wrong one.
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = if (password && keyboardType == androidx.compose.ui.text.input.KeyboardType.Text) androidx.compose.ui.text.input.KeyboardType.Password else keyboardType,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                            autoCorrectEnabled = if (password) false else null,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { stop() }),
                        visualTransformation = if (password && !shown) androidx.compose.ui.text.input.PasswordVisualTransformation()
                            else androidx.compose.ui.text.input.VisualTransformation.None,
                    )
                }
            }
            // Without a settings panel (the setup wizard) nothing else tells the user ▶ shows the password.
            if (password && lit && !editing && panel == null) {
                Text("▶ " + stringResource(if (shown) R.string.common_hide else R.string.common_show), style = stageText(18, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (lit && !editing) Text(stringResource(R.string.common_edit), style = stageText(18, 700), color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One action of a list page's focused item, drawn in the panel (P10B): Edit, Re-sync, Hide… */
class StageAction(
    val icon: OwnTVIcon,
    val label: String,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val danger: Boolean = false,
)

/**
 * The focused item's actions as a column of pills in the panel. ▶ from the row reaches them; ◀ and
 * Back return to the row ([back]). A held OK runs [StageAction.onLongClick] (starts a span).
 */
@Composable
fun StageActionColumn(actions: List<StageAction>, back: FocusRequester?, first: FocusRequester? = null) {
    val a = stageAccent
    Column(Modifier.padding(top = 20.mpx), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
        actions.forEachIndexed { i, act ->
            var longAt by remember { mutableLongStateOf(0L) }
            StageSurface(
                onClick = { if (android.os.SystemClock.uptimeMillis() - longAt > 800) act.onClick() },
                radius = StageRadii.Pill,
                idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(StageRadii.Pill)),
                onLongClick = act.onLongClick?.let { l -> { longAt = android.os.SystemClock.uptimeMillis(); l() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.mpx)
                    .then(if (i == 0 && first != null) Modifier.focusRequester(first) else Modifier)
                    .focusProperties { if (back != null) left = back }
                    .onPreviewKeyEvent { e ->
                        if (back != null && e.key == Key.Back) {
                            if (e.type == KeyEventType.KeyUp) runCatching { back.requestFocus() }
                            true
                        } else false
                    },
            ) { focused ->
                Row(
                    Modifier.padding(horizontal = 18.mpx),
                    horizontalArrangement = Arrangement.spacedBy(10.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val c = when {
                        focused -> a.onAccent
                        act.danger -> StageColors.Danger
                        else -> StageColors.Text
                    }
                    OwnTVIcon(act.icon, if (focused) a.onAccent else if (act.danger) StageColors.Danger else a.accent, Modifier.size(20.mpx))
                    Text(act.label, style = stageText(18, 700), color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The panel's SPAN / MULTI-SELECT block (P10B, owner): how a span is picked, and the CH keys that reach its end. */
@Composable
fun SpanHelpBlock() {
    Box(Modifier.padding(top = 18.mpx, bottom = 14.mpx).fillMaxWidth().height(1.mpx).background(Color.White.copy(alpha = 0.1f)))
    SettingPanelHeading(stringResource(R.string.settings_span_heading))
    Text(stringResource(R.string.settings_span_help), style = stageText(15, 500), color = StageColors.Muted)
    Text(stringResource(R.string.settings_span_ch_help), style = stageText(15, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 6.mpx))
}

/** " · " between two parts of a line (the eyebrow format with both parts empty, so it is localised). */
@Composable
fun dotSeparator(): String = stringResource(R.string.settings_breadcrumb_eyebrow, "", "")
