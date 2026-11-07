package tv.own.owntv.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.LeagueCard
import tv.own.owntv.core.sports.LeagueCards
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.SportsBoard
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsWindow
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.RaceSession
import tv.own.owntv.core.sports.model.RankingKind
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageMenu
import tv.own.owntv.ui.stage.StageMenuHeader
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTab
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A league's own tabs, which depend on its sport. */
private enum class LeaguePane { TABLE, FIXTURES, RESULTS, DRIVERS, CONSTRUCTORS, RACES, RANKINGS, MATCHES }

/**
 * Football and the other team sports: Table | Fixtures | Results (no table: Fixtures | Results). F1:
 * Drivers | Constructors | Races; the other racing series Drivers | Races. ATP / WTA: Rankings | Matches.
 */
private fun panesOf(league: League): List<LeaguePane> = when {
    league.sport == Sport.RACING -> listOfNotNull(LeaguePane.DRIVERS, LeaguePane.CONSTRUCTORS.takeIf { league.slug == "f1" }, LeaguePane.RACES)
    SportsWindow.hasRanking(league) -> listOf(LeaguePane.RANKINGS, LeaguePane.MATCHES)
    SportsWindow.hasTable(league) -> listOf(LeaguePane.TABLE, LeaguePane.FIXTURES, LeaguePane.RESULTS)
    else -> listOf(LeaguePane.FIXTURES, LeaguePane.RESULTS)
}

private val LeaguePane.label: Int get() = when (this) {
    LeaguePane.TABLE -> R.string.sports_tab_table
    LeaguePane.FIXTURES -> R.string.sports_tab_fixtures
    LeaguePane.RESULTS -> R.string.sports_tab_results
    LeaguePane.DRIVERS -> R.string.sports_tab_drivers
    LeaguePane.CONSTRUCTORS -> R.string.sports_tab_constructors
    LeaguePane.RACES -> R.string.sports_tab_races
    LeaguePane.RANKINGS -> R.string.sports_tab_rankings
    LeaguePane.MATCHES -> R.string.sports_tab_matches
}

/** The yellow of a followed team's star. */
private val StarYellow = Color(0xFFFFD166)

/**
 * The Leagues tab. With All, My teams or a sport with several leagues: a card per followed league (who
 * leads, what is next); OK picks that league (its chip turns on), Hold OK follows or unfollows it.
 * With one league chosen — or a sport whose only followed league it is — that league opens here: its
 * name, Following, and its own tabs; ▲ from them reaches the chips. Back returns to the cards when a
 * league chip was chosen, else to Scores.
 */
@Composable
internal fun SportsLeaguesScreen(
    vm: SportsViewModel,
    frame: TabFrame,
    onOpenMatch: (Match) -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
    restoreKey: String?,
    modifier: Modifier = Modifier,
) {
    val filter = rememberChipFilter(vm)
    val oneKey = filter.leagueKeys.singleOrNull()
    LaunchedEffect(oneKey, filter.leagueKeys) {
        vm.showLeague(oneKey)
        vm.showCards(if (oneKey == null) filter.leagueKeys else emptyList())
    }
    // Back from a league chosen by its chip: the sport's cards again.
    val backToCards = if (filter.activeLeagues.isNotEmpty() && filter.sportLeagues.size > 1) {
        { vm.setChips(filter.chips, emptySet()) }
    } else null
    val locale = LocalConfiguration.current.locales[0]
    val chipsHint = "▲" to stringResource(if (filter.sportLeagues.size > 1) R.string.sports_hint_league_chips else R.string.sports_hint_sport_chips)
    val view = vm.leagueView.collectAsStateWithLifecycle().value?.takeIf { it.league.key == oneKey }
    val league = oneKey?.let(SportsCatalog::league)
    val panes = league?.let(::panesOf).orEmpty()
    // Set when a card opens its league: the card is gone, so the league's open tab takes the focus.
    var focusPane by remember { mutableStateOf(false) }
    val paneIndex by vm.leagueTab.collectAsStateWithLifecycle()
    val pane = panes.getOrNull(paneIndex) ?: panes.firstOrNull()
    // A new league starts on its first tab; Back from one of its matches keeps the tab it was on.
    LaunchedEffect(oneKey) {
        if (oneKey != null && oneKey != vm.leagueTabOf) {
            vm.leagueTab.value = 0
            vm.leagueTabOf = oneKey
        }
    }

    val hints = when (pane) {
        null -> listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.sports_hint_open_league),
            "▲" to stringResource(R.string.sports_hint_sport_chips),
            stringResource(R.string.content_key_hold_ok) to stringResource(R.string.sports_hint_follow_unfollow),
        )
        LeaguePane.TABLE -> listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.sports_team_page),
            "▶" to stringResource(R.string.sports_this_matchday),
            chipsHint,
        )
        LeaguePane.FIXTURES, LeaguePane.MATCHES -> listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.sports_hint_match_page),
            stringResource(R.string.content_key_hold_ok) to stringResource(R.string.sports_hint_follow_remind),
            chipsHint,
        )
        LeaguePane.DRIVERS, LeaguePane.CONSTRUCTORS -> listOf("▶" to stringResource(R.string.sports_next_race_weekend), chipsHint)
        else -> listOf(stringResource(R.string.common_ok) to stringResource(R.string.sports_hint_match_page), chipsHint)
    }

    SportsTabFrame(
        vm = vm, frame = frame, filter = filter, modifier = modifier, hints = hints,
        subtitle = { LeaguesSubtitle(filter, league) },
        onBack = backToCards,
    ) {
        if (league == null) {
            LeagueCardsPane(vm, filter, locale, frame, onOpen = { focusPane = true })
        } else {
            LeaguePage(vm, league, view, panes, pane, filter, locale, frame, onOpenMatch, onOpenTeam, restoreKey, focusPane, onPaneFocused = { focusPane = false })
        }
    }
}

/** "**Premier League** · Football · Scores: ESPN"; the cards: "**Leagues** · Football", or "The leagues your teams play in". */
@Composable
private fun LeaguesSubtitle(filter: ChipFilter, league: League?) {
    val accent = stageAccent.accent
    val sep = stringResource(R.string.content_epg_bits_separator)
    val credit = stringResource(R.string.sports_scores_espn)
    val myTeamsOnly = filter.myTeams && filter.chips.size == 1
    val head = league?.name ?: if (myTeamsOnly) stringResource(R.string.sports_my_teams_leagues) else stringResource(R.string.sports_tab_leagues)
    val scope = league?.sport?.let { sportLabel(it) } ?: filter.oneSport?.let { sportLabel(it) }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(head) }
            if (scope != null) { append(sep); append(scope) }
            append(sep)
            withStyle(SpanStyle(color = StageColors.Dim)) { append(credit) }
        },
        style = stageText(21, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

// --- The cards ---------------------------------------------------------------------------------------

/** Cards per row. */
private const val CARDS_PER_ROW = 4

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LeagueCardsPane(vm: SportsViewModel, filter: ChipFilter, locale: Locale, frame: TabFrame, onOpen: () -> Unit) {
    val state = vm.leagueCards.collectAsStateWithLifecycle().value
    var menuFor by remember { mutableStateOf<League?>(null) }
    val myTeamsOnly = filter.myTeams && filter.chips.size == 1
    val teamIds = filter.config.teams.map { it.sport to it.id }.toSet()
    fun mineIn(card: LeagueCard, board: SportsBoard) = board.standings[card.league.key]?.groups?.flatMap { it.rows }
        ?.firstOrNull { (card.league.sport to it.team.id) in teamIds }
    val cards = state?.cards.orEmpty().filter { c ->
        !myTeamsOnly || mineIn(c, state!!.board) != null || c.next?.let(filter::isMine) == true
    }
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth().focusRestorer().focusGroup(),
        contentPadding = PaddingValues(start = frame.contentStart, end = 56.mpx, top = 24.mpx, bottom = 90.mpx),
        verticalArrangement = Arrangement.spacedBy(18.mpx),
    ) {
        items(cards.chunked(CARDS_PER_ROW), key = { row -> row.first().league.key }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                row.forEach { c ->
                    LeagueCardView(
                        card = c, locale = locale,
                        top = state?.board?.standings?.get(c.league.key)?.groups?.flatMap { it.rows }?.firstOrNull { it.team.id == c.leader?.id },
                        mine = if (myTeamsOnly) state?.board?.let { mineIn(c, it) } else null,
                        onClick = { onOpen(); vm.setChips(setOf(HubChip.One(c.league.sport)), setOf(c.league.key)) },
                        onHold = { menuFor = c.league },
                    )
                }
            }
        }
    }
    menuFor?.let { l ->
        FollowLeagueMenu(l, following = l.key in filter.config.followedLeagues(), onToggle = { vm.toggleLeague(l.key) }, onDismiss = { menuFor = null })
    }
}

/**
 * A league card, 404 wide: "⚽ Football ›", the league, "🏆 Man City · 21 Pts" (cricket: no table),
 * "Next: Today 15:00 · Bournemouth – Sunderland", and with My teams the team's place.
 */
@Composable
private fun LeagueCardView(card: LeagueCard, locale: Locale, top: tv.own.owntv.core.sports.model.StandingsRow?, mine: tv.own.owntv.core.sports.model.StandingsRow?, onClick: () -> Unit, onHold: () -> Unit) {
    val r = 20.mpx
    val sep = stringResource(R.string.content_epg_bits_separator)
    val pts = stringResource(R.string.sports_table_points)
    StageSurface(
        onClick = onClick, onLongClick = onHold, radius = r, focusStyle = StageFocus.FX, contentAlignment = Alignment.TopStart,
        modifier = Modifier.width(404.mpx).height(if (mine != null) 208.mpx else 176.mpx),
        idle = Modifier.background(StageColors.Ink.copy(alpha = 0.045f), RoundedCornerShape(r)).drawBehind { drawInnerRing(Color.White.copy(alpha = 0.05f), 1.mpx.toPx(), r.toPx()) },
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(horizontal = 20.mpx, vertical = 16.mpx)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${sportEmoji(card.league.sport)} ${sportLabel(card.league.sport)}", style = stageText(15, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("›", style = stageText(18, 800), color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val name = if (card.league.sport == Sport.CRICKET) sportLabel(Sport.CRICKET) else card.league.name
            Text(name, style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx))
            val lead = when {
                card.league.sport == Sport.CRICKET -> stringResource(R.string.sports_no_table_series)
                // No league points in these sports: the leader's wins and losses ("6–2").
                card.league.sport in LeagueCards.RECORD_SPORTS && top != null -> "🏆 " + top.team.shortName + sep + "${top.won}–${top.lost}"
                card.leader != null -> "🏆 " + card.leader!!.shortName + (card.leaderPoints?.let { "$sep$it $pts" } ?: "")
                else -> null
            }
            lead?.let { Text(it, style = stageText(17, 500), color = StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx)) }
            card.next?.let { n ->
                val what = whenText(n.startMs, locale) + sep + MatchNotifications.pair(LocalContext.current, n)
                Text(stringResource(R.string.sports_next, what), style = stageText(16, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx))
            }
            mine?.let { row ->
                Text("★ ${row.team.shortName}$sep#${row.rank}$sep${row.points} $pts", style = stageText(16, 700), color = StarYellow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.mpx))
            }
        }
    }
}

/** Hold OK on a league card: Follow league or Unfollow league, as in Settings. */
@Composable
private fun FollowLeagueMenu(league: League, following: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(Modifier.fillMaxSize().background(StageColors.Scrim).longPressMenuGuard(), contentAlignment = Alignment.Center) {
            StageMenu(Modifier.width(560.mpx).trapAllFocusExit().focusGroup()) {
                StageMenuHeader(league.name, sportLabel(league.sport))
                StageMenuItem(
                    text = stringResource(if (following) R.string.sports_unfollow_league else R.string.sports_follow_league),
                    icon = OwnTVIcon.STAR, iconFilled = following,
                    onClick = { onToggle(); onDismiss() },
                    modifier = Modifier.focusRequester(first),
                )
            }
        }
    }
}

// --- One league --------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LeaguePage(
    vm: SportsViewModel,
    league: League,
    view: LeagueView?,
    panes: List<LeaguePane>,
    pane: LeaguePane?,
    filter: ChipFilter,
    locale: Locale,
    frame: TabFrame,
    onOpenMatch: (Match) -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
    restoreKey: String?,
    focusPane: Boolean,
    onPaneFocused: () -> Unit,
) {
    val config = filter.config
    val paneFocus = remember { FocusRequester() }
    LaunchedEffect(focusPane) {
        if (!focusPane) return@LaunchedEffect
        for (attempt in 0 until 10) {
            withFrameNanos { }
            if (runCatching { paneFocus.requestFocus() }.getOrDefault(false)) break
        }
        onPaneFocused()
    }
    var menuFor by remember { mutableStateOf<Match?>(null) }
    var menuChannels by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    LaunchedEffect(menuFor) { menuChannels = menuFor?.let { vm.channelsFound(it) }.orEmpty() }
    val revealed = remember { mutableStateListOf<String>() }
    val rowFocus = remember { HashMap<String, FocusRequester>() }
    fun focusOf(key: String) = rowFocus.getOrPut(key) { FocusRequester() }
    // Back from a match lands on the row it was opened from.
    LaunchedEffect(restoreKey, view != null) {
        val key = restoreKey ?: return@LaunchedEffect
        if (view == null) return@LaunchedEffect
        for (attempt in 0 until 10) {
            withFrameNanos { }
            if (runCatching { focusOf(key).requestFocus() }.getOrDefault(false)) break
        }
    }
    val rowOf: @Composable (Match, Boolean) -> Unit = { m, withDay ->
        val hidden = config.spoilerFree && m.state == MatchState.POST && m.key !in revealed
        LeagueMatchRow(
            m = m, locale = locale, withDay = withDay, hidden = hidden, mine = filter.isMine(m),
            onClick = { if (hidden) revealed.add(m.key) else onOpenMatch(m) },
            onHold = { menuFor = m },
            modifier = Modifier.focusRequester(focusOf(m.key)),
        )
    }

    // The league's name, Following, and its own tabs.
    Row(
        Modifier.padding(start = frame.contentStart, top = 18.mpx, end = 56.mpx).height(52.mpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.mpx),
    ) {
        Text(if (league.sport == Sport.CRICKET) sportLabel(Sport.CRICKET) else league.name, style = stageText(32, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val following = vm.follows(config, league.key)
        SportsChip(stringResource(if (following) R.string.sports_following else R.string.sports_follow), on = following, icon = OwnTVIcon.STAR) { vm.toggleLeague(league.key) }
        Box(Modifier.padding(horizontal = 10.mpx).size(1.mpx, 30.mpx).background(StageColors.Ink.copy(alpha = 0.12f)))
        // Coming in from the chips lands on the open tab, not the one below the chip.
        Row(Modifier.focusProperties { onEnter = { runCatching { paneFocus.requestFocus() } } }.focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
            panes.forEachIndexed { i, p ->
                StageTab(
                    label = stringResource(p.label), count = "", selected = p == pane, onClick = { vm.leagueTab.value = i },
                    modifier = if (p == pane) Modifier.focusRequester(paneFocus) else Modifier,
                )
            }
        }
    }

    Row(
        Modifier.weight(1f).fillMaxWidth().padding(start = frame.contentStart, end = 56.mpx, top = 14.mpx, bottom = 78.mpx),
        horizontalArrangement = Arrangement.spacedBy(31.mpx),
    ) {
        if (view == null) return@Row
        val left = Modifier.width(910.mpx).fillMaxHeight()
        val right = Modifier.weight(1f).fillMaxHeight()
        val myTeamIds = config.teams.filter { it.sport == league.sport }.map { it.id }.toSet()
        when (pane) {
            LeaguePane.TABLE -> {
                LazyColumn(left.focusRestorer().focusGroup(), contentPadding = PaddingValues(bottom = 24.mpx)) {
                    item { StandingsHeader() }
                    val groups = view.board.standings[league.key]?.groups.orEmpty()
                    groups.forEach { g ->
                        if (groups.size > 1) item { Heading(g.name) }
                        items(g.rows, key = { "t:${g.name}:${it.team.id}" }) { r ->
                            val mine = r.team.id in myTeamIds
                            val ref = teamRefOf(league, r.team)
                            StandingsLine(r, highlight = mine, star = mine, onClick = ref?.let { { onOpenTeam(it) } })
                        }
                    }
                }
                // This matchday: today's and tomorrow's games, else the next round from the fixtures.
                val matchday = view.onBoard.ifEmpty { view.fixtures.let { f -> f.firstOrNull()?.let { first -> f.filter { it.startMs - first.startMs < 4 * 86_400_000L } }.orEmpty() } }
                SidePanel(right, stringResource(R.string.sports_this_matchday), rangeText(matchday, locale)) {
                    items(matchday, key = { it.key }) { m -> rowOf(m, true) }
                }
            }
            LeaguePane.FIXTURES, LeaguePane.RESULTS -> {
                val list = if (pane == LeaguePane.FIXTURES) view.fixtures else view.results
                LazyColumn(left.focusRestorer().focusGroup(), contentPadding = PaddingValues(bottom = 24.mpx)) {
                    byRound(list, locale).forEach { (heading, ms) ->
                        item(key = "w:$heading") { Heading(heading) }
                        items(ms, key = { it.key }) { m -> rowOf(m, true) }
                    }
                }
                val mine = view.fixtures.filter(filter::isMine)
                if (pane == LeaguePane.FIXTURES && mine.isNotEmpty()) {
                    SidePanel(right, stringResource(R.string.sports_my_teams), null) {
                        items(mine, key = { "m:${it.key}" }) { m -> rowOf(m, true) }
                    }
                }
            }
            LeaguePane.DRIVERS, LeaguePane.CONSTRUCTORS, LeaguePane.RANKINGS -> {
                val kind = when (pane) {
                    LeaguePane.CONSTRUCTORS -> RankingKind.CONSTRUCTORS
                    LeaguePane.DRIVERS -> RankingKind.DRIVERS
                    else -> RankingKind.PLAYERS
                }
                val rows = view.board.rankings[league.key]?.groups?.firstOrNull { it.kind == kind }?.rows.orEmpty()
                LazyColumn(left.focusRestorer().focusGroup(), contentPadding = PaddingValues(bottom = 24.mpx)) {
                    item { RankingHeader(stringResource(pane.label)) }
                    items(rows, key = { "r:${it.rank}:${it.team.id}" }) { r -> RankingLine(r, rows.first().points) }
                }
                if (league.sport == Sport.RACING) {
                    NextRaceWeekend(right, view, locale, rowFocus = ::focusOf, onOpenMatch = onOpenMatch, onHold = { menuFor = it })
                } else {
                    val today = view.onBoard
                    SidePanel(right, stringResource(R.string.content_epg_today) + stringResource(R.string.content_epg_bits_separator) + league.name, null) {
                        items(today, key = { it.key }) { m -> rowOf(m, false) }
                    }
                }
            }
            LeaguePane.RACES -> {
                val events = raceWeekends(view.fixtures + view.results)
                val next = events.firstOrNull { e -> e.sessions.any { it.state != MatchState.POST } }
                LazyColumn(left.focusRestorer().focusGroup(), contentPadding = PaddingValues(bottom = 24.mpx)) {
                    items(events, key = { "e:${it.name}:${it.sessions.first().key}" }) { e ->
                        RaceRow(e, isNext = e === next, locale = locale, onClick = { onOpenMatch(e.main) }, modifier = Modifier.focusRequester(focusOf(e.main.key)))
                    }
                }
            }
            LeaguePane.MATCHES -> {
                val list = view.onBoard.ifEmpty { view.fixtures.take(30) }
                LazyColumn(left.focusRestorer().focusGroup(), contentPadding = PaddingValues(bottom = 24.mpx)) {
                    items(list, key = { it.key }) { m -> rowOf(m, true) }
                }
            }
            null -> Unit
        }
    }

    menuFor?.let { m ->
        MatchMenu(
            match = m, config = config, channels = menuChannels, now = System.currentTimeMillis(),
            onFollow = { vm.toggleTeam(it) },
            onRemind = { vm.remind(m, menuChannels) },
            onOpenTeam = onOpenTeam,
            onOpenMatch = { onOpenMatch(m) },
            onDismiss = { menuFor = null },
        )
    }
}

/** `.mh2`: a list heading, 14/800 caps-spaced, dim. */
@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(LocalConfiguration.current.locales[0]), style = stageText(14, 800, 0.12.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.mpx, top = 18.mpx, bottom = 8.mpx),
    )
}

/** `.ag` / `.box` on the right: a title, a small line, and its rows. */
@Composable
private fun SidePanel(modifier: Modifier, title: String, small: String?, rows: LazyListScope.() -> Unit) {
    val r = 24.mpx
    Column(
        modifier
            .background(Color(0xFF12181B).copy(alpha = 0.72f), RoundedCornerShape(r))
            .drawBehind { drawInnerRing(Color.White.copy(alpha = 0.06f), 1.mpx.toPx(), r.toPx()) }
            .padding(horizontal = 22.mpx, vertical = 20.mpx),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
            Text(title, style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            small?.let { Text(it, style = stageText(16, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 10.mpx).focusRestorer().focusGroup(), contentPadding = PaddingValues(bottom = 24.mpx), content = rows)
    }
}

/**
 * `.fx`: a match in a league list — its day ("Sat 24 Oct") when [withDay], both sides with crests, then
 * the kick-off, the live minute, or the result with FT ("• – •" while Spoiler-free hides it).
 */
@Composable
internal fun LeagueMatchRow(m: Match, locale: Locale, withDay: Boolean, hidden: Boolean, mine: Boolean, onClick: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    StageSurface(onClick = onClick, onLongClick = onHold, radius = 12.mpx, modifier = modifier.fillMaxWidth().height(52.mpx)) { focused ->
        val text = if (focused) stageAccent.onAccent else StageColors.Text
        val muted = if (focused) stageAccent.onAccent else StageColors.Muted
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
            if (withDay) Text(dateText(m.startMs, locale), style = stageText(17, 700), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.mpx))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                val home = m.home
                val away = m.away
                if (home == null || away == null) {
                    Text(listOfNotNull(sessionLabel(m), m.title).joinToString(stringResource(R.string.content_epg_bits_separator)), style = stageText(18, 600), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Crest(home.team, 26)
                    Text(teamLabel(home.team), style = stageText(18, 600), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("–", style = stageText(18, 600), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(teamLabel(away.team), style = stageText(18, 600), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Crest(away.team, 26)
                }
            }
            if (mine) Text("★", style = stageText(16, 700), color = if (focused) text else StarYellow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                m.isLive -> LiveMark(clockText(m), 15f)
                m.state == MatchState.POST && m.competitors.size == 2 -> {
                    Text(if (hidden) "• – •" else "${scoreText(m.sport, m.home?.score)} – ${scoreText(m.sport, m.away?.score)}", style = stageText(18, 800), color = if (hidden) muted else text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.statusText, style = stageText(13, 800, 0.05.em), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.mpx))
                }
                m.state == MatchState.POST || m.off -> Text(m.statusText, style = stageText(15, 700), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else -> Text(timeText(m.startMs), style = stageText(17, 700), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// --- Racing ------------------------------------------------------------------------------------------

/** A race weekend: its sessions in order; [main] is the race itself (else the last session). */
private class RaceWeekend(val name: String, val sessions: List<Match>) {
    val main: Match = sessions.lastOrNull { RaceSession.of(it.subtitle) == RaceSession.RACE } ?: sessions.last()
}

/** The season's sessions grouped into weekends by their event name, in date order. */
private fun raceWeekends(sessions: List<Match>): List<RaceWeekend> =
    sessions.sortedBy { it.startMs }.groupBy { it.title }.map { (name, list) -> RaceWeekend(name, list) }.sortedBy { it.sessions.first().startMs }

/** `.fx` in Races: the dates, the race, NEXT on the coming one, 🏆 and the winner on a finished one. */
@Composable
private fun RaceRow(e: RaceWeekend, isNext: Boolean, locale: Locale, onClick: () -> Unit, modifier: Modifier) {
    StageSurface(
        onClick = onClick, radius = 14.mpx, modifier = modifier.fillMaxWidth().height(58.mpx),
        idle = if (isNext) Modifier.background(stageAccent.accent.copy(alpha = 0.08f), RoundedCornerShape(14.mpx)) else Modifier,
    ) { focused ->
        val text = if (focused) stageAccent.onAccent else StageColors.Text
        val muted = if (focused) stageAccent.onAccent else StageColors.Muted
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
            Text(rangeText(e.sessions, locale).orEmpty(), style = stageText(17, 700), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(170.mpx))
            Text(e.name, style = stageText(19, 600), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val winner = e.main.takeIf { it.state == MatchState.POST }?.competitors?.firstOrNull { it.winner == true }?.team?.name
            when {
                winner != null -> Text("🏆 $winner", style = stageText(16, 600), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                isNext -> StageTag(stringResource(R.string.sports_next_badge).uppercase(locale), tint = stageAccent.accent)
            }
        }
    }
}

/** The right of Drivers / Constructors: the next race weekend with every session in local time, then the three after it. */
@Composable
private fun NextRaceWeekend(modifier: Modifier, view: LeagueView, locale: Locale, rowFocus: (String) -> FocusRequester, onOpenMatch: (Match) -> Unit, onHold: (Match) -> Unit) {
    val events = raceWeekends(view.fixtures + view.results)
    val nextIndex = events.indexOfFirst { e -> e.sessions.any { it.state != MatchState.POST } }
    val next = events.getOrNull(nextIndex)
    SidePanel(modifier, stringResource(R.string.sports_next_race_weekend), null) {
        if (next == null) return@SidePanel
        item(key = "name") {
            Column(Modifier.padding(start = 12.mpx, bottom = 10.mpx)) {
                Text(next.name, style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(next.main.venue, rangeText(next.sessions, locale)).joinToString(stringResource(R.string.content_epg_bits_separator)), style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        items(next.sessions, key = { "s:${it.key}" }) { s ->
            StageSurface(onClick = { onOpenMatch(s) }, onLongClick = { onHold(s) }, radius = 12.mpx, modifier = Modifier.fillMaxWidth().height(48.mpx).focusRequester(rowFocus(s.key))) { focused ->
                val color = if (focused) stageAccent.onAccent else StageColors.Text
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    Text(dateText(s.startMs, locale), style = stageText(17, 600), color = if (focused) color else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(130.mpx))
                    Text(sessionLabel(s) ?: s.subtitle.orEmpty(), style = stageText(18, 700), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (s.isLive) LiveMark(clockText(s), 15f) else Text(timeText(s.startMs), style = stageText(18, 700), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val after = events.drop(nextIndex + 1).take(3)
        items(after, key = { "a:${it.name}:${it.sessions.first().key}" }) { e ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.mpx, vertical = 10.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                Text(rangeText(e.sessions, locale).orEmpty(), style = stageText(16, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.mpx))
                Text(e.name, style = stageText(17, 600), color = StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// --- Dates -------------------------------------------------------------------------------------------

private fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

/** "Sat 24 Oct" in the user's language. */
internal fun dateText(ms: Long, locale: Locale): String =
    DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(localDate(ms))

/** "Today 15:00", "Sat 24 Oct 16:00": when a match starts. */
@Composable
private fun whenText(ms: Long, locale: Locale): String {
    val day = if (localDate(ms) == LocalDate.now()) stringResource(R.string.content_epg_today) else dateText(ms, locale)
    return day + " " + timeText(ms)
}

/** "23 – 25 Oct" (or one day, "Sat 24 Oct"): the days [matches] cover. Null when empty. */
private fun rangeText(matches: List<Match>, locale: Locale): String? {
    if (matches.isEmpty()) return null
    val first = localDate(matches.minOf { it.startMs })
    val last = localDate(matches.maxOf { it.startMs })
    if (first == last) return DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(first)
    // Both ends in full: joining a bare day number to a date reads wrongly where the month comes first ("2 – Nov 7").
    val dm = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
    return "${dm.format(first)} – ${dm.format(last)}"
}

/** [list] in rounds — matches on days next to each other ("6 – 8 Nov") — each headed by the days it covers, in [list]'s order. */
private fun byRound(list: List<Match>, locale: Locale): List<Pair<String, List<Match>>> {
    val rounds = ArrayList<MutableList<Match>>()
    var lastDay: LocalDate? = null
    for (m in list) {
        val day = localDate(m.startMs)
        if (lastDay == null || kotlin.math.abs(day.toEpochDay() - lastDay.toEpochDay()) > 1) rounds.add(ArrayList())
        rounds.last().add(m)
        lastDay = day
    }
    return rounds.map { ms -> rangeText(ms, locale).orEmpty() to ms }
}
