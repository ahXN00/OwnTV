package tv.own.owntv.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import tv.own.owntv.ui.components.trapVerticalFocusExit
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.sports.WatchFilter
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchEvent
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.MatchSummary
import tv.own.owntv.core.sports.model.StandingsTable
import tv.own.owntv.features.epg.GuideVideo
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTab
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.stage.stageStillBack
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.util.Locale

/**
 * The match page's tabs. ESPN's summary carries no meetings, so in Head to head's place is
 * Form: each side's last five games.
 */
private enum class MatchTab { TIMELINE, STATS, LINEUPS, TABLE, FORM, BROADCASTS }

/** The tier colours of the reasons: guide green, event blue, your pick gold, likely muted. */
internal val TierGuide = Color(0xFF9FE07E)
internal val TierEvent = Color(0xFF7FD3FF)
internal val TierPick = Color(0xFFE7C56A)

/**
 * A match. Left: the crumb, the score header, the tabs and their content, all
 * from the stored board. Right: *Watch live* — Find channels first (a search of this one match, only
 * when asked); then the preview (Live TV's own engine), the filter chips, every channel showing it with
 * the reason, and how many the filters hide. ▲ ▼ in the list preview each channel, OK plays it full
 * screen, Hold OK remembers it for the competition, ◀ opens the filter panel.
 */
@Composable
fun SportsMatchScreen(
    vm: SportsViewModel,
    liveVm: LiveViewModel,
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenLeague: (Match) -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
    onAddPlaylist: () -> Unit,
    onBack: () -> Unit,
    contentStart: Dp,
    modifier: Modifier = Modifier,
    /** False while the player is full screen over the page. */
    active: Boolean = true,
) {
    // Back is registered before the data: while the page loads, Back must still mean "back to the hub",
    // not fall through to the shell (which would leave the section empty with focus on the rail).
    // Back also ends the preview. Leaving any other way must not: full screen re-tunes the same engines,
    // and a stop on the way out (as this page did) killed the stream it had just started.
    // Off while the player is full screen over this page: Back then belongs to the player, which returns here.
    androidx.activity.compose.BackHandler(enabled = active) { liveVm.stopPreview(); onBack() }
    val page by vm.matchPage.collectAsStateWithLifecycle()
    val state = page ?: return
    val m = state.match
    var tab by remember { mutableStateOf(MatchTab.TIMELINE) }
    var revealed by remember(m.key) { mutableStateOf(false) }
    var focused by remember { mutableStateOf<WatchOption?>(null) }
    val firstRow = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    // A side of the score box under focus: the key hints say OK opens its team page.
    var sideFocused by remember { mutableStateOf(false) }
    // The league name in the crumb: ▲ from a side reaches it (the list around the score box would not hand it on).
    val crumbFocus = remember { FocusRequester() }

    // The focused channel plays in the box above after the focus settles, exactly as in Live TV.
    val previewOn by liveVm.livePreviewEnabled.collectAsStateWithLifecycle()
    LaunchedEffect(focused?.channel?.id, previewOn) {
        val ch = focused?.channel ?: return@LaunchedEffect
        if (!previewOn) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        liveVm.playPreview(ch)
    }
    // Focus follows the search: the tabs while there is no list (Find channels' button goes away while it
    // runs, and must not take focus with it), the first channel once there is one.
    LaunchedEffect(state.search::class, state.list.shown.isNotEmpty()) {
        kotlinx.coroutines.delay(60)
        runCatching { if (state.list.shown.isNotEmpty()) firstRow.requestFocus() else tabFocus.requestFocus() }
    }

    val sep = stringResource(R.string.content_epg_bits_separator)
    val shown = state.list.shown
    val zap = shown.map { it.channel }
    val play: (WatchOption) -> Unit = { o -> vm.playedFrom(m, shown, o.channel); onPlayChannel(o.channel, zap) }
    // Back from the player: focus the channel that was playing, not the first.
    val focusTarget = shown.firstOrNull { it.channel.id == vm.lastPlayedChannelId } ?: shown.firstOrNull()

    Box(modifier.fillMaxSize()) {
        // Up and Down stay on this page: a held ▲ must never run out of the page.
        Row(Modifier.fillMaxSize().padding(start = contentStart, end = 60.mpx, top = 39.mpx).trapVerticalFocusExit(), horizontalArrangement = Arrangement.spacedBy(39.mpx)) {
            // --- Left: the match ------------------------------------------------------------------
            // The crumb is fixed in a 75-high band, so the score box (21 below it) starts at 96, level
            // with the right column's video box, and stays level while the list scrolls; both lists end
            // 84 above the bottom, clear of the key hints.
            Column(Modifier.width(915.mpx).fillMaxHeight()) {
                Box(Modifier.height(75.mpx).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
                        OwnTVIcon(OwnTVIcon.CHEVRON_LEFT, StageColors.Muted, Modifier.size(21.mpx))
                        Text(
                            stringResource(R.string.sports_title), style = stageText(21, 600), color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(sep, style = stageText(21, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // The league name opens that league in the Leagues tab.
                        val inCatalog = tv.own.owntv.core.sports.SportsCatalog.league(tv.own.owntv.core.sports.model.League.keyOf(m.sport, m.leagueSlug)) != null
                        StageSurface(onClick = { onOpenLeague(m) }, radius = 10.mpx, enabled = inCatalog, modifier = Modifier.focusRequester(crumbFocus)) { f ->
                            Text(
                                leagueName(m),
                                style = stageText(21, 400), color = if (f) stageAccent.onAccent else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.mpx, vertical = 4.mpx),
                            )
                        }
                    }
                }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(bottom = 84.mpx).focusGroup(), contentPadding = PaddingValues(bottom = 24.mpx)) {
                item {
                    ScoreHeader(
                        m, state.summary, hidden = state.config.spoilerFree && !revealed, onReveal = { revealed = true },
                        onOpenTeam = onOpenTeam, onSideFocus = { sideFocused = it }, up = crumbFocus,
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 21.mpx).focusRestorer().focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.mpx),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tabs = MatchTab.entries.filter {
                            (it != MatchTab.TABLE || state.standings?.groups?.any { g -> g.rows.isNotEmpty() } == true) &&
                                (it != MatchTab.STATS || !state.summary?.stats.isNullOrEmpty()) &&
                                (it != MatchTab.LINEUPS || !state.summary?.lineups.isNullOrEmpty()) && (it != MatchTab.FORM || !state.summary?.form.isNullOrEmpty())
                        }
                        tabs.forEach { t ->
                            StageTab(
                                label = stringResource(t.label), count = "", selected = tab == t, onClick = { tab = t },
                                modifier = if (t == MatchTab.TIMELINE) Modifier.focusRequester(tabFocus) else Modifier,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        val following = vm.follows(state.config, m.feed)
                        SportsChip(
                            stringResource(if (following) R.string.sports_following else R.string.sports_follow),
                            on = following, icon = OwnTVIcon.STAR,
                        ) { vm.toggleLeague(m.feed) }
                    }
                }
                when (tab) {
                    MatchTab.TIMELINE -> items((state.summary?.timeline ?: m.events).asReversed()) { e -> TimelineRow(e, m) }
                    MatchTab.STATS -> items(state.summary?.stats.orEmpty()) { s -> StatRow(s.label, s.home, s.away) }
                    MatchTab.LINEUPS -> item { Lineups(m, state.summary) }
                    MatchTab.TABLE -> item { TablePreview(state.standings, m, onOpenLeague) }
                    MatchTab.FORM -> item { Form(m, state.summary) }
                    MatchTab.BROADCASTS -> item { Broadcasts(m, (state.search as? ChannelSearch.Found)?.options) }
                }
            }
            }

            // --- Right: Watch live ----------------------------------------------------------------
            // Starts below the top-right status cluster (playlist pill and clock), which floats over every page.
            Column(Modifier.weight(1f).fillMaxSize().padding(top = 96.mpx).focusGroup()) {
                if (!state.hasPlaylist) {
                    NoPlaylistNote(onAddPlaylist)
                    return@Column
                }
                when (val s = state.search) {
                    is ChannelSearch.Found -> Unit
                    is ChannelSearch.Closed -> { ClosedNote(s.window); return@Column }
                    else -> {
                        FindChannels(busy = s == ChannelSearch.Searching || s == ChannelSearch.Checking, searching = s == ChannelSearch.Searching, onFind = vm::findChannels)
                        return@Column
                    }
                }
                if (state.list.total == 0) {
                    Text(stringResource(R.string.sports_watch_live), style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.sports_not_on_channels), style = stageText(19, 400), color = StageColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.mpx))
                    return@Column
                }
                val video = focused ?: shown.firstOrNull()
                GuideVideo(
                    channel = video?.channel,
                    channelLine = video?.channel?.let { listOfNotNull(it.number?.toString(), ProviderTags.parse(it.name).name).joinToString(sep) },
                    nowTitle = video?.guideTitle,
                    previewEngine = liveVm.previewEngine,
                    showVideo = previewOn && focused != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 18.mpx), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.sports_watch_live_count, state.list.total, state.list.total),
                        style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    state.preferredLanguages.firstOrNull()?.let { lang ->
                        Text(stringResource(R.string.sports_language_first, languageName(lang)), style = stageText(18, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                FilterChips(state.filter, onOpenFilters)
                // Ends above the key hints at the bottom of the screen, which would otherwise cover the last rows.
                LazyColumn(Modifier.weight(1f).padding(top = 6.mpx, bottom = 84.mpx).focusRestorer().focusGroup()) {
                    items(shown, key = { it.channel.id }) { o ->
                        ChannelRow(
                            o, m,
                            onClick = { play(o) },
                            onHold = { vm.rememberChannel(m, o.channel) },
                            modifier = Modifier
                                .then(if (o === focusTarget) Modifier.focusRequester(firstRow) else Modifier)
                                .onFocusChanged { if (it.isFocused) focused = o },
                        )
                    }
                    if (state.list.hidden > 0) {
                        item {
                            val names = state.list.hiddenLanguages.map { if (it == WatchFilter.UNKNOWN) stringResource(R.string.sports_unknown) else languageName(it) }
                            Text(
                                pluralStringResource(R.plurals.sports_hidden, state.list.hidden, state.list.hidden) +
                                    (if (names.isEmpty()) "" else " (${names.joinToString(", ")})"),
                                style = stageText(17, 400), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 18.mpx, top = 9.mpx),
                            )
                        }
                    }
                }
            }
        }
        if (sideFocused) {
            StageKeyHints(
                listOf(stringResource(R.string.common_ok) to stringResource(R.string.sports_team_page)),
                Modifier.align(Alignment.BottomEnd).padding(end = 60.mpx, bottom = 27.mpx),
                textSize = 18,
            )
        } else if (state.hasPlaylist && shown.isNotEmpty()) {
            StageKeyHints(
                listOf(
                    stringResource(R.string.common_ok) to stringResource(R.string.sports_watch_full_screen),
                    "▲▼" to stringResource(R.string.sports_preview_another),
                    stringResource(R.string.content_key_hold_ok) to stringResource(R.string.sports_always_use, leagueName(m)),
                ),
                Modifier.align(Alignment.BottomEnd).padding(end = 60.mpx, bottom = 27.mpx),
                textSize = 18,
            )
        }
    }
}

private val MatchTab.label: Int get() = when (this) {
    MatchTab.TIMELINE -> R.string.sports_tab_timeline
    MatchTab.STATS -> R.string.sports_tab_stats
    MatchTab.LINEUPS -> R.string.sports_tab_lineups
    MatchTab.TABLE -> R.string.sports_tab_table
    MatchTab.FORM -> R.string.sports_table_form
    MatchTab.BROADCASTS -> R.string.sports_tab_broadcasts
}

/**
 * Who shows the match: the channels Find channels found in the user's own guide —
 * one per row, under their country, countries and names in order, any without a country last — then
 * the broadcasters ESPN lists, United States services only. The page's list scrolls, so this does too.
 */
@Composable
private fun Broadcasts(m: Match, found: List<WatchOption>?) {
    Column(Modifier.fillMaxWidth().padding(18.mpx), verticalArrangement = Arrangement.spacedBy(9.mpx)) {
        Text(stringResource(R.string.sports_broadcasts_guide), style = stageText(20, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val guide = found?.filter { it.tier == WatchTier.GUIDE || it.tier == WatchTier.EVENT }
        when {
            guide == null -> Text(stringResource(R.string.sports_broadcasts_find_first), style = stageText(18, 400), color = StageColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            guide.isEmpty() -> Text(stringResource(R.string.sports_broadcasts_none), style = stageText(18, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else -> byCountry(guide.map { it.facets.country.orEmpty() to it.facets.name }).forEach { (country, names) -> BroadcastGroup(country, names) }
        }
        Spacer(Modifier.height(12.mpx))
        Text(stringResource(R.string.sports_broadcasts_espn), style = stageText(20, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (m.broadcasts.isEmpty()) Text(stringResource(R.string.sports_broadcasts_none), style = stageText(18, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        byCountry(m.broadcasts.map { it.market?.takeIf { c -> c.length == 2 }.orEmpty() to it.name }).forEach { (country, names) -> BroadcastGroup(country, names) }
    }
}

/** (country, name) pairs grouped by country — countries in order, none last — each group's names sorted, once each. */
private fun byCountry(pairs: List<Pair<String, String>>): List<Pair<String, List<String>>> =
    pairs.groupBy({ it.first }, { it.second }).entries
        .sortedWith(compareBy({ it.key.isEmpty() }, { it.key }))
        .map { (country, names) -> country to names.distinct().sortedBy { it.lowercase() } }

/**
 * A country's tag, then one channel per row. Each row takes focus, so ▲ ▼ walk a long list and the page
 * scrolls with them — plain text cannot be reached with the remote. OK does nothing: it is a list to read.
 */
@Composable
private fun BroadcastGroup(country: String, names: List<String>) {
    Column(Modifier.padding(top = 6.mpx), verticalArrangement = Arrangement.spacedBy(3.mpx)) {
        if (country.isNotEmpty()) StageTag(country)
        names.forEach { name ->
            StageRow(onClick = {}, height = 51.mpx, modifier = Modifier.fillMaxWidth(), horizontalPadding = 12.mpx) { focused ->
                Text(name, style = stageText(18, 600), color = if (focused) StageColors.Text else StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Each side's last five games, side by side: the result (W D L, in the table's letters), the opponent with ⌂ for a home game, the score. */
@Composable
private fun Form(m: Match, summary: MatchSummary?) {
    val form = summary?.form.orEmpty()
    Row(Modifier.fillMaxWidth().padding(18.mpx), horizontalArrangement = Arrangement.spacedBy(30.mpx)) {
        listOfNotNull(m.home, m.away).forEach { c ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.mpx)) {
                Text(c.team.shortName, style = stageText(20, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                form[c.team.id].orEmpty().forEach { g ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                        val (letter, color) = when (g.result) {
                            "W" -> stringResource(R.string.sports_table_won) to TierGuide
                            "L" -> stringResource(R.string.sports_table_lost) to SportsLive
                            else -> stringResource(R.string.sports_table_drawn) to StageColors.Muted
                        }
                        Box(Modifier.size(30.mpx).background(color.copy(alpha = 0.22f), RoundedCornerShape(8.mpx)), contentAlignment = Alignment.Center) {
                            Text(letter, style = stageText(16, 800), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (g.home) OwnTVIcon(OwnTVIcon.HOME, StageColors.Muted, Modifier.size(18.mpx)) else Spacer(Modifier.size(18.mpx))
                        Text(g.opponent, style = stageText(18, 600), color = StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(g.score, style = stageText(18, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** A language code as the user's language names it ("DE" → "German"). */
@Composable
internal fun languageName(code: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return Locale.forLanguageTag(code.lowercase()).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

/**
 * The score header: crest (93) and name each side, the score 81 px, LIVE · minute, and each side's scorers
 * under it. Spoiler-free: the score is a dash until OK on the header. Once shown, each side with a team
 * page takes focus, and OK opens it.
 */
@Composable
private fun ScoreHeader(
    m: Match,
    summary: MatchSummary?,
    hidden: Boolean,
    onReveal: () -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
    onSideFocus: (Boolean) -> Unit,
    up: FocusRequester,
) {
    val r = 24.mpx
    StageSurface(
        onClick = onReveal,
        enabled = hidden,
        radius = r,
        focusStyle = StageFocus.FX,
        contentAlignment = Alignment.TopStart,
        modifier = Modifier.fillMaxWidth().padding(top = 21.mpx),
        idle = Modifier
            .background(StageColors.Ink.copy(alpha = 0.04f), RoundedCornerShape(r))
            .drawBehind { drawInnerRing(Color.White.copy(alpha = 0.06f), 1.mpx.toPx(), r.toPx()) },
    ) { _ ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 33.mpx, vertical = 27.mpx)) {
            val home = m.home
            val away = m.away
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Side(m, home?.team, !hidden, onOpenTeam, onSideFocus, Modifier.weight(1f).onPreviewKeyEvent { e -> e.key == Key.DirectionUp && e.type == KeyEventType.KeyDown && runCatching { up.requestFocus() }.getOrDefault(false) })
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val score = when {
                        m.competitors.size < 2 -> m.title
                        m.state == MatchState.PRE -> timeText(m.startMs)
                        hidden -> "– : –"
                        // Tennis reads set by set ("6–1 4–6 5–3"); one score a side reads "2 – 1".
                        m.sport == tv.own.owntv.core.sports.model.Sport.TENNIS && !home?.score?.periods.isNullOrEmpty() ->
                            home!!.score!!.periods.zip(away?.score?.periods.orEmpty()).joinToString("  ") { (h, a) -> "${h.value}–${a.value}" }
                        else -> "${scoreText(m.sport, home?.score)} – ${scoreText(m.sport, away?.score)}"
                    }
                    Text(score, style = stageText(if (m.competitors.size < 2) 30f else if (score.length > 7) 51f else 81f, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(9.mpx))
                    if (m.isLive) LiveMark(stringResource(R.string.player_live) + stringResource(R.string.content_epg_bits_separator) + clockText(m))
                    else Text(
                        // Before kick-off ESPN's text is US-styled ("10/16 - 9:00 PM EDT"); the day in the user's own words instead.
                        if (m.state == MatchState.PRE) dayOf(m.startMs, LocalConfiguration.current.locales[0]) ?: stringResource(R.string.content_epg_today) else m.statusText,
                        style = stageText(17, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Side(m, away?.team, !hidden, onOpenTeam, onSideFocus, Modifier.weight(1f).onPreviewKeyEvent { e -> e.key == Key.DirectionUp && e.type == KeyEventType.KeyDown && runCatching { up.requestFocus() }.getOrDefault(false) })
            }
            // Kick-off, round, venue (ESPN's), in the box rather than the crumb.
            val locale = LocalConfiguration.current.locales[0]
            val info = listOfNotNull(
                // Before kick-off the big number already says the day and time.
                ((dayOf(m.startMs, locale) ?: stringResource(R.string.content_epg_today)) + " " + timeText(m.startMs)).takeIf { m.state != MatchState.PRE },
                m.subtitle,
                m.venue,
            ).joinToString(stringResource(R.string.content_epg_bits_separator))
            Text(info, style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.mpx))
            if (!hidden && m.competitors.size == 2) {
                val goals = (summary?.timeline ?: m.events).filter { it.kind == MatchEvent.Kind.GOAL || it.kind == MatchEvent.Kind.PENALTY_GOAL || it.kind == MatchEvent.Kind.OWN_GOAL }
                fun line(teamId: String?) = goals.filter { it.teamId == teamId }.joinToString(" · ") { "⚽ ${it.players.firstOrNull().orEmpty().substringAfterLast(' ')} ${it.minute}" }
                Row(Modifier.fillMaxWidth().padding(top = 18.mpx)) {
                    Text(line(home?.team?.id), style = stageText(18, 400), color = StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(line(away?.team?.id), style = stageText(18, 400), color = StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** One side of the score box: crest and name, focusable ([enabled]) when the side has a team page. */
@Composable
private fun Side(
    m: Match,
    team: tv.own.owntv.core.sports.model.Team?,
    enabled: Boolean,
    onOpenTeam: (TeamRef) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val ref = team?.let { teamRefOf(m, it) }
    StageSurface(
        onClick = { ref?.let(onOpenTeam) },
        radius = 18.mpx,
        enabled = enabled && ref != null,
        contentAlignment = Alignment.Center,
        modifier = modifier.onFocusChanged { onFocus(it.isFocused) },
    ) { focused ->
        Column(Modifier.padding(vertical = 9.mpx), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.mpx)) {
            if (team != null) {
                Crest(team, 93)
                Text(team.name, style = stageText(22.5f, 700), color = if (focused) stageAccent.onAccent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** `.lrow` in the timeline: the minute, the event's mark, "Goal · Mario Götze" and its team (· assist). */
@Composable
private fun TimelineRow(e: MatchEvent, m: Match) {
    val team = m.competitors.firstOrNull { it.team.id == e.teamId }?.team?.name
    val who = e.players.firstOrNull().orEmpty()
    val title = when (e.kind) {
        MatchEvent.Kind.GOAL -> stringResource(R.string.sports_event_goal, who)
        MatchEvent.Kind.OWN_GOAL -> stringResource(R.string.sports_event_own_goal, who)
        MatchEvent.Kind.PENALTY_GOAL -> stringResource(R.string.sports_event_penalty, who)
        MatchEvent.Kind.YELLOW_CARD -> stringResource(R.string.sports_event_yellow, who)
        MatchEvent.Kind.RED_CARD -> stringResource(R.string.sports_event_red, who)
        MatchEvent.Kind.SUBSTITUTION -> stringResource(R.string.sports_event_sub, who, e.players.getOrNull(1).orEmpty())
        MatchEvent.Kind.WICKET -> stringResource(R.string.sports_event_wicket, who)
        MatchEvent.Kind.KICKOFF -> stringResource(R.string.sports_event_kickoff)
        MatchEvent.Kind.HALF_TIME -> stringResource(R.string.sports_event_half_time)
        MatchEvent.Kind.FULL_TIME -> stringResource(R.string.sports_event_full_time)
    }
    val assist = if (e.kind == MatchEvent.Kind.GOAL) e.players.getOrNull(1)?.let { stringResource(R.string.sports_event_assist, it) } else null
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.mpx, vertical = 12.mpx),
        horizontalArrangement = Arrangement.spacedBy(18.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(e.minute, style = stageText(20, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(78.mpx))
        Box(Modifier.size(24.mpx), contentAlignment = Alignment.Center) {
            when (e.kind) {
                MatchEvent.Kind.YELLOW_CARD -> Box(Modifier.size(14.mpx, 21.mpx).background(Color(0xFFFFD23F), RoundedCornerShape(3.mpx)))
                MatchEvent.Kind.RED_CARD -> Box(Modifier.size(14.mpx, 21.mpx).background(SportsLive, RoundedCornerShape(3.mpx)))
                MatchEvent.Kind.SUBSTITUTION -> OwnTVIcon(OwnTVIcon.SWAP, StageColors.Muted, Modifier.size(22.mpx))
                MatchEvent.Kind.GOAL, MatchEvent.Kind.OWN_GOAL, MatchEvent.Kind.PENALTY_GOAL -> Text("⚽", style = stageText(20, 400), maxLines = 1, overflow = TextOverflow.Ellipsis)
                else -> OwnTVIcon(OwnTVIcon.CLOCK, StageColors.Muted, Modifier.size(20.mpx))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(team, assist).joinToString(stringResource(R.string.content_epg_bits_separator))
            if (sub.isNotEmpty()) Text(sub, style = stageText(18, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One stat: home value, the name, away value, each side's share as a bar. */
@Composable
private fun StatRow(label: String, home: String, away: String) {
    val h = home.trimEnd('%').toFloatOrNull() ?: 0f
    val a = away.trimEnd('%').toFloatOrNull() ?: 0f
    val share = if (h + a > 0f) h / (h + a) else 0.5f
    val accent = stageAccent.accent
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.mpx, vertical = 10.mpx)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(home, style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(90.mpx))
            Text(label, style = stageText(18, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(away, style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(90.mpx))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.mpx).height(6.mpx), horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
            Box(Modifier.weight(share.coerceIn(0.01f, 0.99f)).fillMaxSize().background(accent, RoundedCornerShape(3.mpx)))
            Box(Modifier.weight((1f - share).coerceIn(0.01f, 0.99f)).fillMaxSize().background(StageColors.Ink.copy(alpha = 0.18f), RoundedCornerShape(3.mpx)))
        }
    }
}

/** Both line-ups side by side: formation, then the starters with their shirt numbers. */
@Composable
private fun Lineups(m: Match, summary: MatchSummary?) {
    val lineups = summary?.lineups.orEmpty()
    Row(Modifier.fillMaxWidth().padding(18.mpx), horizontalArrangement = Arrangement.spacedBy(30.mpx)) {
        listOfNotNull(m.home, m.away).forEach { c ->
            val l = lineups.firstOrNull { it.teamId == c.team.id }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                Text(listOfNotNull(c.team.shortName, l?.formation).joinToString(stringResource(R.string.content_epg_bits_separator)), style = stageText(20, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                l?.starters?.forEach { p ->
                    Text(listOfNotNull(p.jersey, p.name).joinToString("  "), style = stageText(18, 500), color = if (p.subbedOut) StageColors.Muted else StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The table tab: the match's two sides highlighted in the league table; OK opens the league page. */
@Composable
private fun TablePreview(table: StandingsTable?, m: Match, onOpenLeague: (Match) -> Unit) {
    val group = table?.groups?.firstOrNull { g -> g.rows.any { r -> m.competitors.any { it.team.id == r.team.id } } } ?: table?.groups?.firstOrNull() ?: return
    StageSurface(onClick = { onOpenLeague(m) }, radius = 20.mpx, focusStyle = StageFocus.FX, modifier = Modifier.fillMaxWidth().padding(top = 9.mpx)) { _ ->
        Column(Modifier.fillMaxWidth().padding(18.mpx)) {
            StandingsHeader()
            group.rows.forEach { row -> StandingsLine(row, highlight = m.competitors.any { it.team.id == row.team.id }) }
        }
    }
}

/** "German, English" · "Any country" · the quality floor · the filter mark — any of them opens the panel. */
@Composable
private fun FilterChips(filter: WatchFilter, onOpen: () -> Unit) {
    val langs = (filter.languages - WatchFilter.UNKNOWN).map { languageName(it) }
    Row(Modifier.padding(top = 12.mpx).focusGroup(), horizontalArrangement = Arrangement.spacedBy(9.mpx)) {
        SportsChip(if (langs.isEmpty()) stringResource(R.string.sports_any) else langs.joinToString(", "), on = langs.isNotEmpty(), icon = OwnTVIcon.LANGUAGE, onClick = onOpen)
        SportsChip(if (filter.countries.isEmpty()) stringResource(R.string.sports_any_country) else filter.countries.joinToString(", "), on = filter.countries.isNotEmpty(), icon = OwnTVIcon.NETWORK, onClick = onOpen)
        if (filter.minQuality != tv.own.owntv.core.sports.MinQuality.ANY) SportsChip(qualityLabel(filter.minQuality), on = true, icon = OwnTVIcon.VIDEO, onClick = onOpen)
        // The panel itself (its filter icon is the SORT glyph).
        SportsChip(stringResource(R.string.sports_filter_title), on = false, icon = OwnTVIcon.SORT, onClick = onOpen)
    }
}

@Composable
internal fun qualityLabel(q: tv.own.owntv.core.sports.MinQuality): String = stringResource(
    when (q) {
        tv.own.owntv.core.sports.MinQuality.ANY -> R.string.sports_any
        tv.own.owntv.core.sports.MinQuality.HD -> R.string.sports_quality_hd
        tv.own.owntv.core.sports.MinQuality.FHD -> R.string.sports_quality_fhd
        tv.own.owntv.core.sports.MinQuality.UHD -> R.string.sports_quality_uhd
    },
)

/** A quality rank as its usual tag. */
internal fun qualityTag(rank: Int): String? = when (rank) {
    2 -> "HD"
    3 -> "FHD"
    4 -> "4K"
    5 -> "8K"
    else -> null
}

/**
 * One channel under Watch live: logo, name with its quality tag, why it is listed (in the tier's colour),
 * and its country.
 */
@Composable
internal fun ChannelRow(o: WatchOption, m: Match, onClick: () -> Unit, onHold: (() -> Unit)?, modifier: Modifier = Modifier, playing: Boolean = false) {
    StageRow(onClick = onClick, onLongClick = onHold, height = 90.mpx, modifier = modifier.fillMaxWidth(), horizontalPadding = 18.mpx) { _ ->
        ChannelLogoTile(
            logoUrl = o.channel.logoUrl,
            modifier = Modifier.size(69.mpx, 42.mpx).background(stageStillBack, RoundedCornerShape(10.mpx)),
            fill = Color.Transparent,
        ) {}
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.mpx), verticalAlignment = Alignment.CenterVertically) {
                Text(o.facets.name, style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                qualityTag(o.facets.qualityRank)?.let { StageTag(it) }
            }
            val (color, icon, text) = when {
                playing -> Triple(StageColors.Muted, null, stringResource(R.string.sports_playing_now))
                o.tier == WatchTier.GUIDE -> Triple(TierGuide, OwnTVIcon.EPG, o.guideTitle?.let { stringResource(R.string.sports_reason_guide, it) } ?: stringResource(R.string.sports_confirmed))
                o.tier == WatchTier.EVENT -> Triple(TierEvent, OwnTVIcon.LIVE_TV, stringResource(R.string.sports_reason_event))
                o.tier == WatchTier.REMEMBERED -> Triple(TierPick, OwnTVIcon.STAR, stringResource(R.string.sports_reason_pick, leagueName(m)))
                else -> Triple(StageColors.Muted, null, stringResource(R.string.sports_reason_likely, leagueName(m)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.mpx), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) OwnTVIcon(icon, color, Modifier.size(17.mpx))
                Text(text, style = stageText(17, 600), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (playing) OwnTVIcon(OwnTVIcon.CHECK, stageAccent.accent, Modifier.size(22.mpx))
        else o.facets.country?.let { StageTag(it) }
    }
}

/** *Watch live* before a search: what Find channels does, and the button — or, while it runs, "Searching your channels…". */
@Composable
private fun FindChannels(busy: Boolean, searching: Boolean, onFind: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
        Text(stringResource(R.string.sports_watch_live), style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (busy && !searching) {
            tv.own.owntv.ui.components.OwnTVSpinner(sizeDp = 18)
        } else if (searching) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                tv.own.owntv.ui.components.OwnTVSpinner(sizeDp = 18)
                Text(stringResource(R.string.sports_finding_channels), style = stageText(19, 600), color = StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(stringResource(R.string.sports_find_channels_description), style = stageText(18, 400), color = StageColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            StageButton(stringResource(R.string.sports_find_channels), onClick = onFind, icon = OwnTVIcon.SEARCH, height = 56.mpx, textSize = 19)
        }
    }
}

/** Find channels outside its window: when it opens, or that the match is over. */
@Composable
private fun ClosedNote(window: tv.own.owntv.core.sports.WatchWindow) {
    val locale = LocalConfiguration.current.locales[0]
    val text = when (window) {
        is tv.own.owntv.core.sports.WatchWindow.TooEarly ->
            stringResource(R.string.sports_find_channels_from, (dayOf(window.opensAt, locale) ?: stringResource(R.string.content_epg_today)) + " " + timeText(window.opensAt))
        else -> stringResource(R.string.sports_find_channels_ended)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
        Text(stringResource(R.string.sports_watch_live), style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text, style = stageText(19, 400), color = StageColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** No playlist on this profile (shown on the TV's Watch live side): scores work, channels need one. */
@Composable
private fun NoPlaylistNote(onAddPlaylist: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(StageColors.Ink.copy(alpha = 0.05f), RoundedCornerShape(24.mpx)).padding(30.mpx),
        verticalArrangement = Arrangement.spacedBy(12.mpx),
    ) {
        Text(stringResource(R.string.sports_no_playlist_title), style = stageText(24, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.sports_no_playlist_body), style = stageText(18, 400), color = StageColors.Muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
        StageButton(stringResource(R.string.sports_add_playlist), onClick = onAddPlaylist, icon = OwnTVIcon.ADD, height = 56.mpx, textSize = 19)
    }
}
