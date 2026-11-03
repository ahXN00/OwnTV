package tv.own.owntv.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.CalendarMonth
import tv.own.owntv.core.sports.CalendarStatus
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.stage.stageFocusLook
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Sports whose one-league month shows the day's pairings ("ARS – EVE") in each cell, not a count. */
private val PAIRING_SPORTS = setOf(
    Sport.SOCCER, Sport.BASKETBALL, Sport.HOCKEY, Sport.FOOTBALL, Sport.BASEBALL, Sport.RUGBY,
    Sport.RUGBY_LEAGUE, Sport.AUSSIE_RULES, Sport.VOLLEYBALL, Sport.LACROSSE,
)

/** How far back the arrows go while ESPN's season start is not known (cricket has none). */
private const val MONTHS_BACK_UNKNOWN = 12L

/** The yellow of a followed team's star. */
private val StarYellow = Color(0xFFFFD166)

/** Where the focus is on the Calendar, for the key hints. */
private enum class CalendarFocus { ELSEWHERE, GRID, LIST }

/**
 * The Calendar tab: the month on the left, the chosen day's matches on the right. Only the followed
 * leagues the chips let through. In the month ◀ ▶ move a day, ▲ ▼ a week (past the last week the month
 * turns; above the first, the month's own buttons), OK or ▶ from the last column goes into the day's
 * list, ◀ comes back. A row: OK opens the match (or shows a hidden score first), Hold OK the menu.
 * Months go back to the season's start. Today and tomorrow are kept live from the Scores board.
 */
@Composable
internal fun SportsCalendarScreen(
    vm: SportsViewModel,
    frame: TabFrame,
    onOpenMatch: (Match) -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
    onOpenLeague: (String) -> Unit,
    restoreKey: String?,
    modifier: Modifier = Modifier,
) {
    val filter = rememberChipFilter(vm)
    val config = filter.config
    val day by vm.calendarDay.collectAsStateWithLifecycle()
    val month = YearMonth.from(day)
    val keys = filter.leagueKeys
    val cricket = keys.any { SportsCatalog.league(it)?.sport == Sport.CRICKET }
    // Cricket's feed is per day: the chosen day is fetched first. Other feeds are per month.
    LaunchedEffect(keys, month, if (cricket) day else null) { vm.showMonth(keys.toSet(), month, if (cricket) day else null) }
    val cal = vm.calendarMonth.collectAsStateWithLifecycle().value?.takeIf { it.month == month }
    val days = remember(cal, filter) { cal?.days.orEmpty().mapValues { (_, l) -> l.filter(filter::passes) }.filterValues { it.isNotEmpty() } }

    // The earliest season start seen for these leagues stops the arrows.
    var seasonStart by remember(keys) { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(cal?.seasonStart) { cal?.seasonStart?.let { s -> seasonStart = seasonStart?.let { minOf(it, s) } ?: s } }
    val thisMonth = YearMonth.now()
    val minMonth = (seasonStart?.let(YearMonth::from) ?: thisMonth.minusMonths(MONTHS_BACK_UNKNOWN)).let { if (it > thisMonth) thisMonth else it }
    fun go(to: LocalDate): Boolean {
        if (YearMonth.from(to) < minMonth) return false
        vm.calendarDay.value = to
        return true
    }

    val locale = LocalConfiguration.current.locales[0]
    val oneLeague = keys.singleOrNull()?.let(SportsCatalog::league)
    var focusAt by remember { mutableStateOf(CalendarFocus.ELSEWHERE) }
    var rowFocused by remember { mutableStateOf<Match?>(null) }
    val revealed = remember { mutableStateListOf<String>() }
    var menuFor by remember { mutableStateOf<Match?>(null) }
    var menuChannels by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    LaunchedEffect(menuFor) { menuChannels = menuFor?.let { vm.channelsFound(it) }.orEmpty() }
    val gridFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val rowFocus = remember { HashMap<String, FocusRequester>() }
    fun focusOf(key: String) = rowFocus.getOrPut(key) { FocusRequester() }
    // Back from a match lands on the row it was opened from.
    LaunchedEffect(restoreKey) {
        val key = restoreKey ?: return@LaunchedEffect
        for (attempt in 0 until 10) {
            withFrameNanos { }
            if (runCatching { focusOf(key).requestFocus() }.getOrDefault(false)) break
        }
    }

    val dayMatches = days[day].orEmpty()
    val reminders by vm.reminderSet.collectAsStateWithLifecycle()
    val spoilerHidden = { m: Match -> config.spoilerFree && m.state == MatchState.POST && m.key !in revealed }
    val hints = when (focusAt) {
        CalendarFocus.GRID -> listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.sports_hint_day_matches),
            "◀▶" to stringResource(R.string.sports_hint_day),
            "▲▼" to stringResource(R.string.sports_hint_week),
            stringResource(R.string.common_back) to stringResource(R.string.sports_tab_scores),
        )
        CalendarFocus.LIST -> listOf(
            stringResource(R.string.common_ok) to stringResource(if (rowFocused?.let(spoilerHidden) == true) R.string.sports_hint_show_score else R.string.sports_hint_match_page),
            stringResource(R.string.content_key_hold_ok) to stringResource(R.string.sports_hint_follow_remind),
            "◀" to stringResource(R.string.sports_hint_back_to_day),
        )
        CalendarFocus.ELSEWHERE -> listOf(stringResource(R.string.common_back) to stringResource(R.string.sports_tab_scores))
    }

    SportsTabFrame(
        vm = vm, frame = frame, filter = filter, modifier = modifier, hints = hints,
        subtitle = { CalendarSubtitle(month, filter, oneLeague?.name, locale) },
    ) {
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(start = frame.contentStart, end = 56.mpx, top = 18.mpx, bottom = 78.mpx),
            horizontalArrangement = Arrangement.spacedBy(31.mpx),
        ) {
            // --- The month ---------------------------------------------------------------------------
            Column(Modifier.width(910.mpx).fillMaxHeight()) {
                MonthHead(
                    month = month, locale = locale, cal = cal, total = days.values.sumOf { it.size },
                    canGoBack = month > minMonth,
                    onMonth = { m -> go(if (m == thisMonth) LocalDate.now() else m.atDay(1)) },
                    onToday = { go(LocalDate.now()) },
                )
                val firstDay = WeekFields.of(locale).firstDayOfWeek
                fun columnOf(d: LocalDate) = (d.dayOfWeek.value - firstDay.value + 7) % 7
                Row(Modifier.fillMaxWidth().padding(top = 14.mpx), horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
                    repeat(7) { i ->
                        Text(
                            firstDay.plus(i.toLong()).getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                            style = stageText(14, 700, 0.1.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 12.mpx),
                        )
                    }
                }
                var gridFocused by remember { mutableStateOf(false) }
                MonthGrid(
                    month = month, day = day, today = LocalDate.now(), days = days, filter = filter,
                    pairings = oneLeague != null && oneLeague.sport in PAIRING_SPORTS,
                    focused = gridFocused, columnOf = ::columnOf,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.mpx)
                        .focusRequester(gridFocus)
                        .focusProperties { right = if (dayMatches.isNotEmpty()) listFocus else FocusRequester.Cancel }
                        .onFocusChanged { gridFocused = it.isFocused; if (it.isFocused) focusAt = CalendarFocus.GRID else if (focusAt == CalendarFocus.GRID) focusAt = CalendarFocus.ELSEWHERE }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionLeft -> go(day.minusDays(1))
                                // ▶ from the last column leaves for the day's list.
                                Key.DirectionRight -> columnOf(day) != 6 && go(day.plusDays(1))
                                // ▲ above the month's first week reaches the month's buttons.
                                Key.DirectionUp -> YearMonth.from(day.minusDays(7)) == month && go(day.minusDays(7))
                                Key.DirectionDown -> { go(day.plusDays(7)); true }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    if (dayMatches.isNotEmpty()) runCatching { listFocus.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable(),
                )
            }

            // --- The chosen day ----------------------------------------------------------------------
            DayPanel(
                day = day, matches = dayMatches, cal = cal, filter = filter, locale = locale,
                nextAfter = days.filterKeys { it > day }.toSortedMap().values.firstOrNull()?.firstOrNull(),
                onOpenLeague = onOpenLeague, gridFocus = gridFocus,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { i, m ->
                val requester = focusOf(m.key)
                CalendarRow(
                    m = m, filter = filter, reminders = reminders,
                    hidden = spoilerHidden(m),
                    onClick = { if (spoilerHidden(m)) revealed.add(m.key) else onOpenMatch(m) },
                    onHold = { menuFor = m },
                    modifier = Modifier
                        .then(if (i == 0) Modifier.focusRequester(listFocus) else Modifier)
                        .focusRequester(requester)
                        .focusProperties { left = gridFocus }
                        .onFocusChanged {
                            if (it.isFocused) { focusAt = CalendarFocus.LIST; rowFocused = m }
                            else if (rowFocused?.key == m.key) { focusAt = CalendarFocus.ELSEWHERE; rowFocused = null }
                        },
                )
            }
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

/** "**October 2026** · Premier League · Scores: ESPN". */
@Composable
private fun CalendarSubtitle(month: YearMonth, filter: ChipFilter, leagueName: String?, locale: Locale) {
    val accent = stageAccent.accent
    val sep = stringResource(R.string.content_epg_bits_separator)
    val scope = leagueName ?: filter.oneSport?.let { sportLabel(it) } ?: stringResource(R.string.sports_my_teams).takeIf { filter.myTeams && filter.chips.size == 1 }
    val credit = stringResource(R.string.sports_scores_espn)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(monthTitle(month, locale)) }
            if (scope != null) { append(sep); append(scope) }
            append(sep)
            withStyle(SpanStyle(color = StageColors.Dim)) { append(credit) }
        },
        style = stageText(21, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** `.mh`: ‹ "October 2026" › and Today, then the month's count (or "Loading October…") on the right. */
@Composable
private fun MonthHead(
    month: YearMonth,
    locale: Locale,
    cal: CalendarMonth?,
    total: Int,
    canGoBack: Boolean,
    onMonth: (YearMonth) -> Unit,
    onToday: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(50.mpx).focusGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
        ArrowButton(OwnTVIcon.CHEVRON_LEFT, enabled = canGoBack) { onMonth(month.minusMonths(1)) }
        Text(monthTitle(month, locale), style = stageText(28, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 250.mpx))
        ArrowButton(OwnTVIcon.CHEVRON, enabled = true) { onMonth(month.plusMonths(1)) }
        SportsChip(stringResource(R.string.content_epg_today), on = false, modifier = Modifier.padding(start = 6.mpx)) { onToday() }
        Spacer(Modifier.weight(1f))
        val sum = if (cal == null || cal.status == CalendarStatus.Loading) {
            stringResource(R.string.sports_loading_month, month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale))
        } else pluralStringResource(R.plurals.sports_hub_matches, total, total)
        Text(sum, style = stageText(16, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.ar`: a 46 px month arrow. */
@Composable
private fun ArrowButton(icon: OwnTVIcon, enabled: Boolean, onClick: () -> Unit) {
    StageSurface(
        onClick = onClick, radius = 14.mpx, enabled = enabled, contentAlignment = Alignment.Center,
        modifier = Modifier.size(46.mpx).alpha(if (enabled) 1f else 0.4f),
        idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(14.mpx)),
    ) { focused ->
        tv.own.owntv.ui.components.OwnTVIcon(icon, if (focused) stageAccent.onAccent else StageColors.Body, Modifier.size(22.mpx))
    }
}

/**
 * `.grid`: the month's weeks, the cells filling the height. Each day shows its matches per sport
 * ("⚽ 14 · 🏀 11"), or with one league its pairings ("ARS – EVE", two, then "+3"); ★ for a day one of
 * My teams plays, a red dot while something is live. Past days dimmed, today ringed, the chosen day
 * highlighted (with the focus look while the month has focus). One focus target: the keys move the day.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    day: LocalDate,
    today: LocalDate,
    days: Map<LocalDate, List<Match>>,
    filter: ChipFilter,
    pairings: Boolean,
    focused: Boolean,
    columnOf: (LocalDate) -> Int,
    modifier: Modifier,
) {
    val first = month.atDay(1)
    val start = first.minusDays(columnOf(first).toLong())
    val weeks = (columnOf(first) + month.lengthOfMonth() + 6) / 7
    val accent = stageAccent.accent
    BoxWithConstraints(modifier) {
        val gap = 6.mpx
        val cellHeight = (maxHeight - gap * (weeks - 1)) / weeks
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(weeks) { w ->
                Row(Modifier.fillMaxWidth().height(cellHeight), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(7) { c ->
                        val d = start.plusDays((w * 7 + c).toLong())
                        val inMonth = YearMonth.from(d) == month
                        val shape = RoundedCornerShape(14.mpx)
                        val look = when {
                            !inMonth -> Modifier
                            d == day && focused -> Modifier.stageFocusLook(StageFocus.FX, 14.mpx)
                            d == day -> Modifier.background(accent.copy(alpha = 0.14f), shape).drawBehind { drawInnerRing(accent.copy(alpha = 0.85f), 1.5f * 1.mpx.toPx(), 14.mpx.toPx()) }
                            d == today -> Modifier.background(StageColors.Ink.copy(alpha = 0.035f), shape).drawBehind { drawInnerRing(accent.copy(alpha = 0.7f), 1.5f * 1.mpx.toPx(), 14.mpx.toPx()) }
                            else -> Modifier.background(StageColors.Ink.copy(alpha = 0.035f), shape).drawBehind { drawInnerRing(Color.White.copy(alpha = 0.04f), 1.mpx.toPx(), 14.mpx.toPx()) }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().then(look).padding(horizontal = 11.mpx, vertical = 9.mpx)) {
                            DayCell(d, inMonth, past = d < today, today = d == today, matches = if (inMonth) days[d].orEmpty() else emptyList(), filter = filter, pairings = pairings)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, inMonth: Boolean, past: Boolean, today: Boolean, matches: List<Match>, filter: ChipFilter, pairings: Boolean) {
    val a = stageAccent
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val number = d.dayOfMonth.toString()
            if (today) {
                Text(number, style = stageText(20, 800), color = a.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(a.accent, RoundedCornerShape(9.mpx)).padding(horizontal = 8.mpx))
            } else {
                Text(
                    number, style = stageText(20, 700), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = when {
                        !inMonth -> StageColors.Dim.copy(alpha = 0.5f)
                        past -> StageColors.Muted
                        else -> StageColors.Text
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            if (matches.any { it.isLive }) Box(Modifier.size(9.mpx).background(SportsLive, CircleShape))
            if (matches.any(filter::isMine)) Text("★", style = stageText(16, 700), color = StarYellow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.mpx))
        }
        if (matches.isEmpty()) return@Column
        val dim = Modifier.alpha(if (past) 0.6f else 1f)
        if (pairings) {
            matches.take(2).forEach { m ->
                Text(
                    listOfNotNull(m.home, m.away).joinToString(" – ") { code(it.team) },
                    style = stageText(15, 700, 0.02.em), color = StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = dim.padding(top = 6.mpx),
                )
            }
            if (matches.size > 2) Text("+${matches.size - 2}", style = stageText(12, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.mpx))
        } else {
            val bySport = matches.groupBy { it.sport }
            FlowRow(dim.padding(top = 8.mpx), horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalArrangement = Arrangement.spacedBy(5.mpx)) {
                filter.config.orderSports(bySport.keys.toList()).forEach { s ->
                    Text("${sportEmoji(s)} ${bySport[s].orEmpty().size}", style = stageText(16, 700), color = StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A side's three-letter code in a cell: ESPN's abbreviation, else the start of its short name. */
private fun code(t: tv.own.owntv.core.sports.model.Team): String =
    t.abbreviation.ifBlank { t.shortName.take(3) }.uppercase(Locale.ROOT)

/**
 * `.ag`: the chosen day — its title and count, the "saved earlier" note when ESPN cannot be reached,
 * then the matches grouped by league in the profile's order, each heading opening that league in the
 * Leagues tab. Nothing that day: says so, where to change the leagues, and what comes next.
 */
@Composable
private fun DayPanel(
    day: LocalDate,
    matches: List<Match>,
    cal: CalendarMonth?,
    filter: ChipFilter,
    locale: Locale,
    nextAfter: Match?,
    onOpenLeague: (String) -> Unit,
    gridFocus: FocusRequester,
    modifier: Modifier,
    row: @Composable (Int, Match) -> Unit,
) {
    val r = 24.mpx
    Column(
        modifier
            .background(Color(0xFF12181B).copy(alpha = 0.72f), RoundedCornerShape(r))
            .drawBehind { drawInnerRing(Color.White.copy(alpha = 0.06f), 1.mpx.toPx(), r.toPx()) }
            .padding(horizontal = 22.mpx, vertical = 20.mpx),
    ) {
        (cal?.status as? CalendarStatus.Offline)?.let { off ->
            Text(
                stringResource(R.string.sports_offline_saved, savedText(off.savedAt, locale)),
                style = stageText(17, 500), color = StageColors.Warn, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.mpx).background(StageColors.Warn.copy(alpha = 0.12f), RoundedCornerShape(14.mpx)).padding(horizontal = 16.mpx, vertical = 12.mpx),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
            Text(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(day), style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (matches.isNotEmpty()) Text(pluralStringResource(R.plurals.sports_hub_matches, matches.size, matches.size), style = stageText(16, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val loading = cal == null || cal.status == CalendarStatus.Loading
        when {
            matches.isEmpty() && loading -> Column(Modifier.padding(top = 14.mpx), verticalArrangement = Arrangement.spacedBy(16.mpx)) {
                repeat(6) { i ->
                    Row(Modifier.height(36.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.mpx, 18.mpx).background(StageColors.ControlFill, RoundedCornerShape(6.mpx)))
                        Box(Modifier.size((280 + i * 20).mpx, 18.mpx).background(StageColors.ControlFill, RoundedCornerShape(6.mpx)))
                    }
                }
            }
            matches.isEmpty() && cal?.status == CalendarStatus.Failed -> Text(
                stringResource(R.string.sports_empty_unreachable), style = stageText(19, 400), color = StageColors.Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 60.mpx),
            )
            matches.isEmpty() -> Column(Modifier.fillMaxWidth().padding(top = 60.mpx, start = 20.mpx, end = 20.mpx), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                val sport = filter.oneSport
                Text(
                    if (sport != null) stringResource(R.string.sports_nothing_in_sport, sportLabel(sport)) else stringResource(R.string.sports_nothing_on_day),
                    style = stageText(24, 800), color = StageColors.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.sports_change_leagues_hint), style = stageText(19, 400), color = StageColors.Muted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                nextAfter?.let { n ->
                    val what = (dayOf(n.startMs, locale) ?: stringResource(R.string.content_epg_today)) + stringResource(R.string.content_epg_bits_separator) + MatchNotifications.pair(LocalContext.current, n)
                    Text(stringResource(R.string.sports_next, what), style = stageText(19, 600), color = StageColors.Body, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            else -> {
                val groups = matches.groupBy { it.feed }.toList()
                    .sortedWith(compareBy({ filter.config.rankOf(it.second.first().sport, it.first).first }, { filter.config.rankOf(it.second.first().sport, it.first).second }))
                // A new day, or a new chip choice, starts the list at the top.
                key(day, groups.map { it.first }) {
                    LazyColumn(Modifier.fillMaxSize().padding(top = 6.mpx).focusGroup(), contentPadding = PaddingValues(bottom = 24.mpx)) {
                        var index = 0
                        groups.forEach { (feed, list) ->
                            item(key = "h:$feed") { GroupHeading(feed, list, onOpenLeague, gridFocus) }
                            list.forEach { m ->
                                val i = index++
                                item(key = m.key) { row(i, m) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** `.gh`: "⚽ PREMIER LEAGUE" and its count; OK opens the league in the Leagues tab. */
@Composable
private fun GroupHeading(feed: String, list: List<Match>, onOpenLeague: (String) -> Unit, gridFocus: FocusRequester) {
    val league = SportsCatalog.league(feed)
    val sport = list.first().sport
    val name = if (league == null || sport == Sport.CRICKET) sportLabel(sport) else league.name
    val locale = LocalConfiguration.current.locales[0]
    StageSurface(
        onClick = { if (league != null) onOpenLeague(feed) }, radius = 10.mpx, enabled = league != null,
        modifier = Modifier.fillMaxWidth().padding(top = 14.mpx).focusProperties { left = gridFocus },
    ) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.mpx, vertical = 6.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            val color = if (focused) stageAccent.onAccent else StageColors.Dim
            Text("${sportEmoji(sport)} ${name.uppercase(locale)}", style = stageText(13.5f, 800, 0.11.em), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(list.size.toString(), style = stageText(14, 700), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * `.m`: kick-off (or the live dot and minute), both sides with their crests — a racing session by its
 * name — then the result and FT, ★ for one of My teams, 🔔 for a reminder. Spoiler-free shows "• – •"
 * until OK.
 */
@Composable
private fun CalendarRow(m: Match, filter: ChipFilter, reminders: Set<String>, hidden: Boolean, onClick: () -> Unit, onHold: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    StageSurface(onClick = onClick, onLongClick = onHold, radius = 12.mpx, modifier = modifier.fillMaxWidth().height(52.mpx)) { focused ->
        val text = if (focused) stageAccent.onAccent else StageColors.Text
        val muted = if (focused) stageAccent.onAccent else StageColors.Muted
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
            // Wide enough for a live cricket clock ("● 74.1 ov").
            Box(Modifier.width(86.mpx)) {
                when {
                    m.isLive -> LiveMark(clockText(m), 15f)
                    m.state == MatchState.PRE -> Text(timeText(m.startMs), style = stageText(17, 700), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                val home = m.home
                val away = m.away
                if (home == null || away == null) {
                    val name = listOfNotNull(sessionLabel(m), m.title).joinToString(stringResource(R.string.content_epg_bits_separator))
                    Text(name, style = stageText(18, 600), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Crest(home.team, 26)
                    Text(teamLabel(home.team), style = stageText(18, 600), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("–", style = stageText(18, 600), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(teamLabel(away.team), style = stageText(18, 600), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Crest(away.team, 26)
                }
            }
            if (m.state == MatchState.POST && m.competitors.size == 2) {
                val score = if (hidden) "• – •" else "${scoreText(m.sport, m.home?.score)} – ${scoreText(m.sport, m.away?.score)}"
                Text(score, style = stageText(18, 800), color = if (hidden) muted else text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (m.state == MatchState.POST || m.off) Text(m.statusText, style = stageText(13, 800, 0.05.em), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.mpx))
            if (filter.isMine(m)) Text("★", style = stageText(16, 700), color = StarYellow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (SportsViewModel.reminderKey(m.startMs, MatchNotifications.pair(context, m)) in reminders) {
                tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.BELL, if (focused) stageAccent.onAccent else stageAccent.accent, Modifier.size(18.mpx))
            }
        }
    }
}

/** "October 2026", in the user's language. */
private fun monthTitle(month: YearMonth, locale: Locale): String =
    DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale).format(month)

/** When an offline month was saved: "Fri 16 Oct, 21:00" in the user's language. */
private fun savedText(ms: Long, locale: Locale): String =
    DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMHHmm"), locale)
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
