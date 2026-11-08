package tv.own.owntv.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.MinQuality
import tv.own.owntv.core.sports.WatchFilter
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageSwitch
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * The channel filter panel, opened with ◀ from the Watch live list. Every choice carries the
 * number of channels it covers, and the list behind changes as soon as a choice does. OK toggles, Hold OK
 * keeps only that one, Back closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportsFilterPanel(state: MatchPageState, onFilter: (WatchFilter) -> Unit, onRemember: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val f = state.filter
    val list = state.list
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    val locale = LocalConfiguration.current.locales[0]
    val sep = stringResource(R.string.content_epg_bits_separator)

    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(Modifier.fillMaxSize().background(StageColors.Scrim), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(1020.mpx)
                    .stageGlass(33.mpx, overContent = true)
                    .trapAllFocusExit()
                    .focusGroup()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 42.mpx, vertical = 36.mpx),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sports_filter_title), style = stageText(33, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.sports_showing_of, list.shown.size, list.total), style = stageText(21, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Language: every language present (and the preferred ones, at 0), Unknown last.
                Label(stringResource(R.string.settings_language), locale)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                    val all = list.languageCounts.keys
                    list.languageCounts.entries.forEachIndexed { i, (code, n) ->
                        val on = f.languages.isEmpty() || code in f.languages
                        val name = if (code == WatchFilter.UNKNOWN) stringResource(R.string.sports_unknown) else languageName(code)
                        FilterChip(
                            name + sep + n, on,
                            onClick = { onFilter(f.copy(languages = toggled(f.languages, all, code))) },
                            onOnly = { onFilter(f.copy(languages = setOf(code))) },
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }

                Label(stringResource(R.string.sports_filter_country), locale)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                    SportsChip(stringResource(R.string.sports_any), on = f.countries.isEmpty()) { onFilter(f.copy(countries = emptySet())) }
                    list.countryCounts.forEach { (code, n) ->
                        FilterChip(
                            code + sep + n, code in f.countries,
                            onClick = { onFilter(f.copy(countries = if (code in f.countries) f.countries - code else f.countries + code)) },
                            onOnly = { onFilter(f.copy(countries = setOf(code))) },
                        )
                    }
                }

                Label(stringResource(R.string.settings_vp_cat_picture), locale)
                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    MinQuality.entries.forEach { q ->
                        SportsChip(qualityLabel(q), on = f.minQuality == q) { onFilter(f.copy(minQuality = q)) }
                    }
                }

                if (state.playlists.size > 1) {
                    Label(stringResource(R.string.settings_sort_playlist), locale)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                        SportsChip(stringResource(R.string.sports_all_playlists), on = f.sourceIds.isEmpty()) { onFilter(f.copy(sourceIds = emptySet())) }
                        state.playlists.forEach { (id, name) ->
                            SportsChip(name, on = id in f.sourceIds) {
                                onFilter(f.copy(sourceIds = if (id in f.sourceIds) f.sourceIds - id else f.sourceIds + id))
                            }
                        }
                    }
                }

                Label(stringResource(R.string.sports_filter_match), locale)
                SwitchRow(stringResource(R.string.sports_only_confirmed), stringResource(R.string.sports_only_confirmed_description), f.onlyConfirmed) {
                    onFilter(f.copy(onlyConfirmed = !f.onlyConfirmed))
                }
                SwitchRow(stringResource(R.string.sports_show_likely), stringResource(R.string.sports_show_likely_description), f.showLikely) {
                    onFilter(f.copy(showLikely = !f.showLikely))
                }
                SwitchRow(stringResource(R.string.sports_strict_filter), stringResource(R.string.sports_strict_filter_description), f.strict) {
                    onFilter(f.copy(strict = !f.strict))
                }
                SwitchRow(stringResource(R.string.sports_remember_filters), stringResource(R.string.sports_remember_filters_description), state.config.rememberFilters) {
                    onRemember(!state.config.rememberFilters)
                }

                StageKeyHints(
                    listOf(
                        stringResource(R.string.common_ok) to stringResource(R.string.sports_filter_toggle),
                        stringResource(R.string.common_back) to stringResource(R.string.common_done),
                        stringResource(R.string.content_key_hold_ok) to stringResource(R.string.sports_filter_only_this),
                    ),
                    Modifier.padding(top = 24.mpx).align(Alignment.End),
                    textSize = 18,
                )
            }
        }
    }
}

/**
 * A language toggled in a set where empty means "every language": turning one off from "all" keeps the
 * others; turning the last missing one back on returns to "all".
 */
private fun toggled(current: Set<String>, all: Set<String>, code: String): Set<String> {
    val expanded = current.ifEmpty { all }
    val next = if (code in expanded) expanded - code else expanded + code
    return if (next.containsAll(all)) emptySet() else next
}

/** `.lbl`: the dim caps group label. */
@Composable
private fun Label(text: String, locale: java.util.Locale) {
    Text(
        text.uppercase(locale), style = stageText(16.5f, 700, androidx.compose.ui.unit.TextUnit(0.12f, androidx.compose.ui.unit.TextUnitType.Em)),
        color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 24.mpx, bottom = 12.mpx),
    )
}

/** A filter choice with its count; on = accent with ✓. Hold OK keeps only it. */
@Composable
private fun FilterChip(text: String, on: Boolean, onClick: () -> Unit, onOnly: () -> Unit, modifier: Modifier = Modifier) {
    SportsChip(text, on = on, icon = if (on) OwnTVIcon.CHECK else null, modifier = modifier, onLongClick = onOnly, onClick = onClick)
}

/** `.srow` with a switch: the title, its description, and the switch on the right. */
@Composable
private fun SwitchRow(title: String, description: String, on: Boolean, onClick: () -> Unit) {
    StageRow(onClick = onClick, height = 84.mpx, modifier = Modifier.fillMaxWidth(), horizontalPadding = 18.mpx) { _ ->
        Column(Modifier.weight(1f)) {
            Text(title, style = stageText(21, 600), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, style = stageText(18, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StageSwitch(on)
    }
}
