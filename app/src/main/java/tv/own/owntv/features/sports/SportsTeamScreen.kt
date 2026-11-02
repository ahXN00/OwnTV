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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.StandingsRow
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTab
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** A finished match as the team saw it. */
private enum class Outcome { W, D, L }

private fun outcomeOf(m: Match, teamId: String): Outcome? {
    if (m.state != MatchState.POST) return null
    val us = m.competitors.firstOrNull { it.team.id == teamId } ?: return null
    return when {
        us.winner == true -> Outcome.W
        m.competitors.any { it.team.id != teamId && it.winner == true } -> Outcome.L
        else -> Outcome.D
    }
}

/**
 * A team's page: crest, name, "Sports · Football · Premier League" and In My teams; Fixtures | Results
 * on the left (ESPN's schedule, with the cups the team plays from the saved calendar months), grouped by
 * month; on the right the next match, the last five and its place in the table. OK on a row opens the
 * match, Hold OK the match menu. Spoiler-free hides scores and W / D / L until OK.
 */
@Composable
internal fun SportsTeamScreen(
    vm: SportsViewModel,
    ref: TeamRef,
    onOpenMatch: (Match) -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
    onBack: () -> Unit,
    active: Boolean,
    contentStart: Dp,
    modifier: Modifier = Modifier,
) {
    androidx.activity.compose.BackHandler(enabled = active) { onBack() }
    val state = remember(ref) { vm.teamPage(ref) }.collectAsStateWithLifecycle(null).value
    val config by vm.sportsConfig.collectAsStateWithLifecycle()
    val reminders by vm.reminderSet.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val team = ref.team
    val followed = FollowedTeam(ref.league.sport, team.id, team.name, team.abbreviation)
    val inMyTeams = config.teams.any { it.key == followed.key }
    var resultsTab by remember(ref) { mutableStateOf(false) }
    val revealed = remember(ref) { mutableStateListOf<String>() }
    var menuFor by remember { mutableStateOf<Match?>(null) }
    var menuChannels by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    LaunchedEffect(menuFor) { menuChannels = menuFor?.let { vm.channelsFound(it) }.orEmpty() }
    val schedule = state?.schedule
    val next = schedule?.fixtures?.firstOrNull()
    var nextChannels by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    LaunchedEffect(next?.key) { nextChannels = next?.let { vm.channelsFound(it) }.orEmpty() }
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        for (attempt in 0 until 10) {
            withFrameNanos { }
            if (runCatching { tabFocus.requestFocus() }.getOrDefault(false)) break
        }
    }
    val hiddenOf = { m: Match -> config.spoilerFree && m.state == MatchState.POST && m.key !in revealed }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = contentStart, end = 56.mpx, top = 36.mpx)) {
            // --- The team --------------------------------------------------------------------------
            Row(Modifier.padding(end = 304.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
                Crest(team, 96)
                Column(Modifier.weight(1f, fill = false)) {
                    Text(team.name, style = stageText(46, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sep = stringResource(R.string.content_epg_bits_separator)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = stageAccent.accent, fontWeight = FontWeight.SemiBold)) { append(stringResource(R.string.sports_title)) }
                            append(sep); append(sportLabel(ref.league.sport)); append(sep); append(ref.league.name)
                        },
                        style = stageText(20, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx),
                    )
                }
                SportsChip(stringResource(if (inMyTeams) R.string.sports_in_my_teams else R.string.sports_follow), on = inMyTeams, icon = OwnTVIcon.STAR, modifier = Modifier.padding(start = 18.mpx)) {
                    vm.toggleTeam(followed)
                }
            }
            Row(Modifier.padding(top = 18.mpx).focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                StageTab(stringResource(R.string.sports_tab_fixtures), "", selected = !resultsTab, onClick = { resultsTab = false }, modifier = if (!resultsTab) Modifier.focusRequester(tabFocus) else Modifier)
                StageTab(stringResource(R.string.sports_tab_results), "", selected = resultsTab, onClick = { resultsTab = true }, modifier = if (resultsTab) Modifier.focusRequester(tabFocus) else Modifier)
            }
            Row(Modifier.weight(1f).fillMaxWidth().padding(top = 12.mpx, bottom = 78.mpx), horizontalArrangement = Arrangement.spacedBy(31.mpx)) {
                // --- Fixtures | Results ------------------------------------------------------------
                val list = if (resultsTab) schedule?.results.orEmpty() else schedule?.fixtures.orEmpty()
                LazyColumn(Modifier.width(910.mpx).fillMaxHeight().focusRestorer().focusGroup(), contentPadding = PaddingValues(bottom = 24.mpx)) {
                    when {
                        schedule == null -> Unit
                        list.isEmpty() && schedule.failed -> item { Text(stringResource(R.string.sports_empty_unreachable), style = stageText(19, 400), color = StageColors.Muted, modifier = Modifier.padding(16.mpx)) }
                        else -> list.groupBy { monthOf(it.startMs) }.forEach { (month, ms) ->
                            item(key = "m:$month") {
                                Text(
                                    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale) + if (month.year != java.time.Year.now().value) " ${month.year}" else "",
                                    style = stageText(14, 800, 0.12.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 16.mpx, top = 18.mpx, bottom = 8.mpx),
                                )
                            }
                            items(ms, key = { it.key }) { m ->
                                val hidden = hiddenOf(m)
                                TeamMatchRow(
                                    m = m, teamId = team.id, locale = locale, isNext = m === next, hidden = hidden,
                                    reminded = SportsViewModel.reminderKey(m.startMs, MatchNotifications.pair(LocalContext.current, m)) in reminders,
                                    onClick = { if (hidden) revealed.add(m.key) else onOpenMatch(m) },
                                    onHold = { menuFor = m },
                                )
                            }
                        }
                    }
                }
                // --- Next match, last five, league position -----------------------------------------
                Column(Modifier.weight(1f).fillMaxHeight().focusGroup(), verticalArrangement = Arrangement.spacedBy(18.mpx)) {
                    next?.let { n ->
                        InfoBox(stringResource(R.string.sports_next_match)) {
                            NextMatch(n, locale)
                            Row(Modifier.fillMaxWidth().padding(top = 20.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx, Alignment.CenterHorizontally)) {
                                SportsChip(stringResource(R.string.sports_hint_match_page), on = false) { onOpenMatch(n) }
                                if (n.state == MatchState.PRE && nextChannels.isNotEmpty()) {
                                    SportsChip(stringResource(R.string.content_remind_me), on = false, icon = OwnTVIcon.BELL) { vm.remind(n, nextChannels) }
                                }
                            }
                        }
                    }
                    val five = schedule?.results.orEmpty().take(5).mapNotNull { outcomeOf(it, team.id) }
                    if (five.isNotEmpty() && !config.spoilerFree) {
                        InfoBox(stringResource(R.string.sports_last_five)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.mpx)) { five.forEach { OutcomeBadge(it) } }
                        }
                    }
                    val rows = state?.board?.standings?.get(ref.league.key)?.groups?.firstOrNull { g -> g.rows.any { it.team.id == team.id } }?.rows
                    val at = rows?.indexOfFirst { it.team.id == team.id } ?: -1
                    if (rows != null && at >= 0) {
                        InfoBox(stringResource(R.string.sports_league_position)) {
                            StandingsHeader()
                            neighbours(rows, at).forEach { r -> StandingsLine(r, highlight = r.team.id == team.id) }
                        }
                    }
                }
            }
        }
        StageKeyHints(
            listOfNotNull(
                stringResource(R.string.common_ok) to stringResource(R.string.sports_hint_match_page),
                (stringResource(R.string.content_key_hold_ok) to stringResource(R.string.sports_hint_follow_remind)).takeIf { !resultsTab },
                stringResource(R.string.common_back) to stringResource(R.string.common_back),
            ),
            Modifier.align(Alignment.BottomEnd).padding(end = 60.mpx, bottom = 27.mpx),
            textSize = 18,
        )
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

/** The team's row and the two either side (at the top or bottom, the three nearest). */
private fun neighbours(rows: List<StandingsRow>, at: Int): List<StandingsRow> {
    val from = (at - 1).coerceIn(0, (rows.size - 3).coerceAtLeast(0))
    return rows.subList(from, (from + 3).coerceAtMost(rows.size))
}

private fun monthOf(ms: Long): java.time.YearMonth = java.time.YearMonth.from(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** `.box`: a panel on the right with its caps heading. */
@Composable
private fun InfoBox(title: String, content: @Composable () -> Unit) {
    val r = 24.mpx
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFF12181B).copy(alpha = 0.72f), RoundedCornerShape(r))
            .drawBehind { drawInnerRing(Color.White.copy(alpha = 0.06f), 1.mpx.toPx(), r.toPx()) }
            .padding(horizontal = 24.mpx, vertical = 22.mpx),
    ) {
        Text(title.uppercase(LocalConfiguration.current.locales[0]), style = stageText(14, 800, 0.12.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 14.mpx))
        content()
    }
}

/** `.nm`: both sides with 74 px crests, the kick-off big between them, its day and venue under it. */
@Composable
private fun NextMatch(m: Match, locale: Locale) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        listOfNotNull(m.home, m.away).forEachIndexed { i, c ->
            if (i == 1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.mpx)) {
                    if (m.isLive) LiveMark(clockText(m), 22f) else Text(timeText(m.startMs), style = stageText(46, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(dayOf(m.startMs, locale) ?: stringResource(R.string.content_epg_today), style = stageText(16, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    m.venue?.let { Text(it, style = stageText(16, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                Crest(c.team, 74)
                Text(teamLabel(c.team), style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/** `.wdl`: W, D or L on green, grey or red. */
@Composable
private fun OutcomeBadge(o: Outcome) {
    val (bg, fg, label) = when (o) {
        Outcome.W -> Triple(Color(0xFF5FB83D).copy(alpha = 0.22f), Color(0xFF9EE07C), R.string.sports_table_won)
        Outcome.D -> Triple(Color(0xFFA0AAA6).copy(alpha = 0.2f), Color(0xFFD3DAD7), R.string.sports_table_drawn)
        Outcome.L -> Triple(Color(0xFFE0574A).copy(alpha = 0.2f), Color(0xFFFF9A8F), R.string.sports_table_lost)
    }
    Box(Modifier.size(28.mpx).background(bg, RoundedCornerShape(7.mpx)), contentAlignment = Alignment.Center) {
        Text(stringResource(label), style = stageText(14, 800), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * `.fx`: the day and kick-off, the competition's tag ("PL", "UCL"), ⌂ for a home match, the opponent's
 * crest and the pairing; NEXT on the next one, 🔔 for a reminder; on Results the score and W / D / L.
 */
@Composable
private fun TeamMatchRow(m: Match, teamId: String, locale: Locale, isNext: Boolean, hidden: Boolean, reminded: Boolean, onClick: () -> Unit, onHold: () -> Unit) {
    val home = m.competitors.firstOrNull { it.team.id == teamId }?.home == true
    val opponent = m.competitors.firstOrNull { it.team.id != teamId }?.team
    val accent = stageAccent.accent
    StageSurface(
        onClick = onClick, onLongClick = onHold, radius = 14.mpx,
        modifier = Modifier.fillMaxWidth().height(62.mpx),
        idle = if (isNext) Modifier.background(accent.copy(alpha = 0.08f), RoundedCornerShape(14.mpx)).drawBehind { drawInnerRing(accent.copy(alpha = 0.35f), 1.mpx.toPx(), 14.mpx.toPx()) } else Modifier,
    ) { focused ->
        val text = if (focused) stageAccent.onAccent else StageColors.Text
        val muted = if (focused) stageAccent.onAccent else StageColors.Muted
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
            Column(Modifier.width(110.mpx)) {
                Text(dayOf(m.startMs, locale) ?: stringResource(R.string.content_epg_today), style = stageText(17, 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timeText(m.startMs), style = stageText(15, 600), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StageTag(competitionTag(m), onAccent = if (focused) stageAccent.onAccent else null)
            Text(if (home) "⌂" else "", style = stageText(15, 400), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(22.mpx), textAlign = TextAlign.Center)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                if (opponent != null) Crest(opponent, 30)
                Text(MatchNotifications.pair(LocalContext.current, m), style = stageText(19, 600), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (m.isLive) LiveMark(clockText(m), 15f)
            if (m.state == MatchState.POST) {
                Text(if (hidden) "• – •" else "${scoreText(m.sport, m.home?.score)} – ${scoreText(m.sport, m.away?.score)}", style = stageText(19, 800), color = if (hidden) muted else text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!hidden) outcomeOf(m, teamId)?.let { OutcomeBadge(it) }
            }
            if (isNext) StageTag(stringResource(R.string.sports_next_badge).uppercase(locale), tint = stageAccent.accent)
            if (reminded) tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.BELL, if (focused) stageAccent.onAccent else stageAccent.accent, Modifier.size(18.mpx))
        }
    }
}

/** A competition's short tag: the catalogue's abbreviation when it is short, else the name's initials ("UCL"). */
private fun competitionTag(m: Match): String {
    val league = SportsCatalog.league(League.keyOf(m.sport, m.leagueSlug))
    val abbr = league?.abbreviation?.takeIf { it.length <= 5 }
    val words = (league?.name ?: m.leagueName).split(' ', '-').filter { it.isNotBlank() }
    // One word ("Bundesliga") reads by its first three letters; several by their initials.
    val tag = if (words.size == 1) words.first().take(3) else words.joinToString("") { it.take(1) }
    return abbr ?: tag.uppercase(Locale.ROOT).take(4)
}
