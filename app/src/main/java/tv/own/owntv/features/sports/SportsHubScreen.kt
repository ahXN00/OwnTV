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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.GuideSportItem
import tv.own.owntv.core.sports.MatchNotifications
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageMenu
import tv.own.owntv.ui.stage.StageMenuHeader
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.stage.stageStillBack
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/** Sports whose score is a line (sets, innings), not one number a side. */
private val LINE_SCORE_SPORTS = setOf(Sport.TENNIS, Sport.CRICKET, Sport.GOLF, Sport.RACING)

/** Chips shown before "+N": eight sports. */
private const val SPORT_CHIPS = 8

/**
 * The Sports hub (the Scores tab): title, tabs and today's counts, sport chips ending in ⟳ (rebuild),
 * *Live now* with the featured first card, *Sport on your channels now* from the guide, *Starting soon*,
 * and the key hints. Focus enters on the featured card. OK and the Play key open a match (channels are
 * found there), Hold OK follows or reminds. With nothing followed, a note says where to choose.
 */
@Composable
fun SportsHubScreen(
    vm: SportsViewModel,
    onTab: (SportsTab) -> Unit,
    tabFocus: FocusRequester,
    onOpenMatch: (Match) -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onChildFocused: () -> Unit,
    onEntryHook: (((() -> Boolean)?) -> Unit)?,
    contentStart: Dp,
    modifier: Modifier = Modifier,
    /** Back from a match: the card it was opened from takes focus again. */
    restoreKey: String? = null,
) {
    val state by vm.hub.collectAsStateWithLifecycle()
    val cardFocus = remember { HashMap<String, FocusRequester>() }
    fun focusOf(key: String) = cardFocus.getOrPut(key) { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(restoreKey) {
        val key = restoreKey ?: return@LaunchedEffect
        for (attempt in 0 until 10) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { focusOf(key).requestFocus() }.isSuccess) break
        }
    }
    val config = state.config
    val now = System.currentTimeMillis()
    val chips by vm.chips.collectAsStateWithLifecycle()
    val leagues by vm.leagues.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Match?>(null) }
    // The menu's Remind me needs a channel: one Find channels already found for that match, if any.
    var menuChannels by remember { mutableStateOf<List<WatchOption>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(menuFor) { menuChannels = menuFor?.let { vm.channelsFound(it) }.orEmpty() }
    // The tab under focus in the header, for the key hints.
    var tabFocused by remember { mutableStateOf<SportsTab?>(null) }

    val filter = remember(config, chips, leagues) { ChipFilter(config, chips, leagues) }
    fun Match.isMine() = filter.isMine(this)
    fun Match.passes() = filter.passes(this)
    // ESPN's tennis and golf feeds list a whole tournament; "today" is what is live or starts today.
    val today = state.board.today.values.flatten().distinctBy { it.key }.filter { it.isLive || isToday(it.startMs) }
    val live = today.filter { it.isLive && it.passes() }
        .sortedWith(compareBy<Match>({ config.rankOf(it.sport, it.feed).first }, { config.rankOf(it.sport, it.feed).second }, { !it.isMine() }, { it.startMs }))
    val soon = state.board.matches.filter { it.state == MatchState.PRE && it.startMs > now && it.passes() }
        .sortedWith(compareBy<Match>({ config.rankOf(it.sport, it.feed).first }, { config.rankOf(it.sport, it.feed).second }, { it.startMs })).take(12)
    val onNow = state.onNow.values.flatten().take(6)

    val featuredFocus = remember { FocusRequester() }
    val chipFocus = remember { FocusRequester() }
    val entry by rememberUpdatedState(if (live.isNotEmpty()) featuredFocus else chipFocus)
    DisposableEffect(onEntryHook) {
        onEntryHook?.invoke { runCatching { entry.requestFocus() }.getOrDefault(false) }
        onDispose { onEntryHook?.invoke(null) }
    }
    Box(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        LazyColumn(
            Modifier.fillMaxSize().focusGroup(),
            contentPadding = PaddingValues(top = 39.mpx, bottom = 110.mpx),
        ) {
            // The header and the chips are one item, so the tabs are always composed when ▲ leaves the chips.
            item(key = "head") {
                Column {
                    SportsHeader(SportsTab.SCORES, onTab, tabFocus, { tabFocused = it }, Modifier.padding(start = contentStart, end = 360.mpx)) {
                        HubSubtitle(today.filter { it.passes() })
                        if (state.board.paused) {
                            Spacer(Modifier.height(6.mpx))
                            Text(stringResource(R.string.sports_paused), style = stageText(18, 600), color = StageColors.Warn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    SportsChipRows(vm, filter, contentStart, tabFocus, chipFocus)
                }
            }

            val nothing = live.isEmpty() && soon.isEmpty() && onNow.isEmpty()
            if (config.followedLeagues().isEmpty()) {
                item(key = "none") { NothingFollowedNote(Modifier.padding(start = contentStart, top = 80.mpx, end = 360.mpx)) }
            } else if (nothing && !state.rebuilding) {
                item(key = "empty") {
                    val unreachable = state.board.failing.isNotEmpty() && state.board.today.isEmpty()
                    Text(
                        stringResource(if (unreachable) R.string.sports_empty_unreachable else R.string.sports_empty_nothing),
                        style = stageText(22, 600), color = StageColors.Muted,
                        modifier = Modifier.padding(start = contentStart, top = 80.mpx),
                    )
                }
            }

            if (live.isNotEmpty()) {
                item(key = "live") {
                    Column {
                        RowHeader(contentStart, stringResource(R.string.sports_live_now), null, live = true)
                        LazyRow(
                            Modifier.focusRestorer().focusGroup(),
                            contentPadding = PaddingValues(start = contentStart, end = 64.mpx, top = 6.mpx, bottom = 6.mpx),
                            horizontalArrangement = Arrangement.spacedBy(18.mpx),
                        ) {
                            itemsIndexed(live, key = { _, m -> m.key }) { i, m ->
                                MatchCard(
                                    m, featured = i == 0, config = config, now = now,
                                    reminded = false,
                                    onClick = { onOpenMatch(m) }, onPlay = { onOpenMatch(m) }, onHold = { menuFor = m },
                                    modifier = (if (i == 0) Modifier.focusRequester(featuredFocus) else Modifier).focusRequester(focusOf(m.key)),
                                )
                            }
                        }
                    }
                }
            }

            if (onNow.isNotEmpty()) {
                item(key = "onNow") {
                    Column {
                        RowHeader(contentStart, stringResource(R.string.sports_on_channels_now), stringResource(R.string.sports_on_channels_now_hint))
                        Column(Modifier.padding(start = contentStart - 18.mpx, end = 40.mpx).focusGroup()) {
                            onNow.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(21.mpx)) {
                                    row.forEach { item ->
                                        OnChannelsRow(item, now, Modifier.weight(1f)) {
                                            onPlayChannel(item.channels.first(), item.channels)
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }

            if (soon.isNotEmpty()) {
                item(key = "soon") {
                    val context = LocalContext.current
                    Column {
                        RowHeader(contentStart, stringResource(R.string.sports_starting_soon), null)
                        // A new filter starts the row at its first card, not where the old list was scrolled.
                        val soonState = androidx.compose.foundation.lazy.rememberLazyListState()
                        androidx.compose.runtime.LaunchedEffect(chips, leagues) { soonState.scrollToItem(0) }
                        LazyRow(
                            state = soonState,
                            modifier = Modifier.focusRestorer().focusGroup(),
                            contentPadding = PaddingValues(start = contentStart, end = 64.mpx, top = 6.mpx, bottom = 6.mpx),
                            horizontalArrangement = Arrangement.spacedBy(18.mpx),
                        ) {
                            items(soon, key = { it.key }) { m ->
                                MatchCard(
                                    m, featured = false, config = config, now = now,
                                    reminded = SportsViewModel.reminderKey(m.startMs, MatchNotifications.pair(context, m)) in state.reminders,
                                    onClick = { onOpenMatch(m) }, onPlay = { onOpenMatch(m) }, onHold = { menuFor = m },
                                    modifier = Modifier.focusRequester(focusOf(m.key)),
                                )
                            }
                        }
                    }
                }
            }
        }

        StageKeyHints(
            tabHints(SportsTab.SCORES, tabFocused) ?: listOf(
                stringResource(R.string.common_ok) to stringResource(R.string.sports_hint_match_page),
                stringResource(R.string.content_key_hold_ok) to stringResource(R.string.sports_hint_follow_remind),
            ),
            Modifier.align(Alignment.BottomEnd).padding(end = 60.mpx, bottom = 27.mpx),
            textSize = 18,
        )
    }

    menuFor?.let { m ->
        MatchMenu(
            match = m, config = config, channels = menuChannels, now = now,
            onFollow = { vm.toggleTeam(it) },
            onRemind = { vm.remind(m, menuChannels) },
            onOpenTeam = onOpenTeam,
            onOpenMatch = { onOpenMatch(m) },
            onDismiss = { menuFor = null },
        )
    }
}

/**
 * `.ph`: "Sports", then the Scores | Calendar | Leagues tabs and the NEW tag, then [subtitle] below.
 * [tabFocus] sits on the open tab; [onTabFocus] reports the tab under focus (null once focus leaves).
 */
@Composable
internal fun SportsHeader(
    tab: SportsTab,
    onTab: (SportsTab) -> Unit,
    tabFocus: FocusRequester,
    onTabFocus: (SportsTab?) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: @Composable () -> Unit,
) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(26.mpx), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sports_title), style = stageText(48, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            SportsTabs(tab, onTab, tabFocus, onTabFocus)
        }
        Spacer(Modifier.height(9.mpx))
        subtitle()
    }
}

/** `.seg`: the three tabs in one capsule, each with its mark; the open one on white 13%. Focused = FILLED. */
@Composable
private fun SportsTabs(tab: SportsTab, onTab: (SportsTab) -> Unit, tabFocus: FocusRequester, onTabFocus: (SportsTab?) -> Unit) {
    val a = stageAccent
    val segmentOn = tv.own.owntv.ui.theme.LocalStagePalette.current.segmentOn
    Row(
        Modifier
            // Coming in from the chips lands on the open tab, not the nearest one.
            .focusProperties { onEnter = { runCatching { tabFocus.requestFocus() } } }
            .onFocusChanged { if (!it.hasFocus) onTabFocus(null) }
            .focusGroup()
            .background(StageColors.ControlFill, RoundedCornerShape(16.mpx))
            .padding(4.mpx),
        horizontalArrangement = Arrangement.spacedBy(2.mpx),
    ) {
        SportsTab.entries.forEach { t ->
            val on = t == tab
            StageSurface(
                onClick = { onTab(t) },
                radius = 12.mpx,
                modifier = Modifier.height(42.mpx)
                    .then(if (on) Modifier.focusRequester(tabFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused) onTabFocus(t) },
                idle = if (on) Modifier.background(segmentOn, RoundedCornerShape(12.mpx)) else Modifier,
            ) { focused ->
                val color = when {
                    focused -> a.onAccent
                    on -> StageColors.Text
                    else -> StageColors.Muted
                }
                Row(
                    Modifier.padding(horizontal = 20.mpx),
                    horizontalArrangement = Arrangement.spacedBy(9.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tv.own.owntv.ui.components.OwnTVIcon(t.icon, color, Modifier.size(19.mpx))
                    Text(tabLabel(t), style = stageText(19, 700), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val SportsTab.icon: OwnTVIcon
    get() = when (this) {
        SportsTab.SCORES -> OwnTVIcon.LIST
        SportsTab.CALENDAR -> OwnTVIcon.CALENDAR
        SportsTab.LEAGUES -> OwnTVIcon.SPORTS
    }

@Composable
private fun tabLabel(t: SportsTab): String = stringResource(
    when (t) {
        SportsTab.SCORES -> R.string.sports_tab_scores
        SportsTab.CALENDAR -> R.string.sports_tab_calendar
        SportsTab.LEAGUES -> R.string.sports_tab_leagues
    },
)

/** The key hints while the tabs have focus: "OK Calendar" (on another tab than the open one), "▼ Sport chips". Null otherwise. */
@Composable
internal fun tabHints(open: SportsTab, focused: SportsTab?): List<Pair<String, String>>? {
    if (focused == null) return null
    val down = "▼" to stringResource(R.string.sports_hint_sport_chips)
    return if (focused == open) listOf(down) else listOf(stringResource(R.string.common_ok) to tabLabel(focused), down)
}

/**
 * The chip rows every tab shares. Row 1: All, My teams, the followed sports (eight, then "+N") and ⟳.
 * Row 2, when the chosen sports follow more than one league: All and those leagues. Both multi-select,
 * nothing chosen = All. ▲ from row 1 reaches the tabs ([tabFocus]); [chipFocus] is on All.
 */
@Composable
internal fun SportsChipRows(
    vm: SportsViewModel,
    filter: ChipFilter,
    contentStart: Dp,
    tabFocus: FocusRequester,
    chipFocus: FocusRequester,
) {
    val config = filter.config
    val chips = filter.chips
    val leagues by vm.leagues.collectAsStateWithLifecycle()
    val followedSports = remember(config) { vm.followedSports(config) }
    var showAllSports by rememberSaveable { mutableStateOf(false) }
    val sportLeagues = filter.sportLeagues
    val activeLeagues = filter.activeLeagues
    fun toggle(c: HubChip) = vm.setChips(if (c in chips) chips - c else chips + c, leagues)
    fun toggleLeague(k: String) = vm.setChips(chips, if (k in activeLeagues) activeLeagues - k else activeLeagues + k)
    val shownSports = if (showAllSports) followedSports else followedSports.take(SPORT_CHIPS)
    LazyRow(
        Modifier.padding(top = 24.mpx).focusRestorer().focusProperties { up = tabFocus }.focusGroup(),
        contentPadding = PaddingValues(start = contentStart, end = 64.mpx),
        horizontalArrangement = Arrangement.spacedBy(12.mpx),
    ) {
        item { SportsChip(stringResource(R.string.content_epg_all), chips.isEmpty(), Modifier.focusRequester(chipFocus)) { vm.setChips(emptySet(), emptySet()) } }
        item { SportsChip(stringResource(R.string.sports_my_teams), HubChip.MyTeams in chips, icon = OwnTVIcon.STAR) { toggle(HubChip.MyTeams) } }
        items(shownSports, key = { it.name }) { s ->
            SportsChip("${sportEmoji(s)} ${sportLabel(s)}", HubChip.One(s) in chips) { toggle(HubChip.One(s)) }
        }
        if (!showAllSports && followedSports.size > SPORT_CHIPS) {
            item { SportsChip("+${followedSports.size - SPORT_CHIPS}", false, dim = true) { showAllSports = true } }
        }
        // ⟳: build the whole board again; its progress shows in the bottom status pill.
        item { SportsChip("", false, icon = OwnTVIcon.REFRESH) { vm.rebuild() } }
    }
    if (sportLeagues.size > 1) {
        LazyRow(
            Modifier.padding(top = 12.mpx).focusRestorer().focusGroup(),
            contentPadding = PaddingValues(start = contentStart, end = 64.mpx),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
        ) {
            item { SportsChip(stringResource(R.string.content_epg_all), activeLeagues.isEmpty()) { vm.setChips(chips, emptySet()) } }
            items(sportLeagues, key = { it.key }) { l ->
                val name = if (l.key == "cricket/all") stringResource(R.string.sports_sport_cricket) else l.name
                SportsChip(name, l.key in activeLeagues) { toggleLeague(l.key) }
            }
        }
    }
}

/** "**Today** · 6 live · 41 matches · Scores: ESPN" (no channel count — channels are found per match). */
@Composable
private fun HubSubtitle(today: List<Match>) {
    val accent = stageAccent.accent
    val sep = stringResource(R.string.content_epg_bits_separator)
    val liveCount = today.count { it.isLive }
    val todayLabel = stringResource(R.string.content_epg_today)
    val liveText = pluralStringResource(R.plurals.sports_hub_live, liveCount, liveCount)
    val matchesText = pluralStringResource(R.plurals.sports_hub_matches, today.size, today.size)
    val credit = stringResource(R.string.sports_scores_espn)
    val dim = StageColors.Dim
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(todayLabel) }
            append(sep); append(liveText); append(sep); append(matchesText)
            append(sep)
            withStyle(SpanStyle(color = dim)) { append(credit) }
        },
        style = stageText(21, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** The hub with nothing followed (new users follow nothing): what to do, where, and why to choose with care. */
@Composable
private fun NothingFollowedNote(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.mpx)) {
        Text(stringResource(R.string.sports_empty_nothing_followed), style = stageText(30, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.sports_empty_choose_in), style = stageText(22, 600), color = StageColors.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.sports_load_time_warning), style = stageText(19, 400), color = StageColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** `.rowh`: a 25.5/800 title (with the LIVE mark on Live now) and a muted 18 px hint on its baseline. */
@Composable
private fun RowHeader(start: Dp, title: String, hint: String?, live: Boolean = false) {
    Row(
        Modifier.padding(start = start, top = 27.mpx, bottom = 10.mpx),
        horizontalArrangement = Arrangement.spacedBy(15.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) LiveMark(stringResource(R.string.player_live), 18f)
        Text(title, style = stageText(25.5f, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hint != null) Text(hint, style = stageText(18, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** `.chip`: 51 high, pill-round; the chosen one accent 22% with a 1.5 px accent ring. Focused = FILLED. */
@Composable
internal fun SportsChip(
    text: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    icon: OwnTVIcon? = null,
    dim: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val a = stageAccent
    val r = 26.mpx
    StageSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        radius = r,
        modifier = modifier.height(51.mpx),
        idle = if (on) {
            Modifier.background(a.accent.copy(alpha = 0.22f), RoundedCornerShape(r))
                .drawBehind { drawInnerRing(a.accent.copy(alpha = 0.7f), 1.5f * 1.mpx.toPx(), r.toPx()) }
        } else Modifier.background(StageColors.ControlFill, RoundedCornerShape(r)),
    ) { focused ->
        val color = when {
            focused -> a.onAccent
            dim -> StageColors.Muted
            on -> StageColors.Text
            else -> StageColors.Body
        }
        Row(
            Modifier.padding(horizontal = 21.mpx),
            horizontalArrangement = Arrangement.spacedBy(10.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) tv.own.owntv.ui.components.OwnTVIcon(icon, color, Modifier.size(20.mpx))
            if (text.isNotEmpty()) Text(text, style = stageText(19.5f, 600), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * `.mc`: a match card, 324 wide (the featured first card 648, with the big score). League line and
 * minute or kick-off on top, both sides with crest and score, then a Reminder tag or the followed star.
 * No channels: they are found on the match page.
 */
@Composable
internal fun MatchCard(
    m: Match,
    featured: Boolean,
    config: SportsConfig,
    now: Long,
    reminded: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = 20.mpx
    val locale = LocalConfiguration.current.locales[0]
    StageSurface(
        onClick = onClick,
        onLongClick = onHold,
        radius = r,
        focusStyle = StageFocus.FX,
        contentAlignment = Alignment.TopStart,
        modifier = modifier
            .width((if (featured) 648 else 324).mpx)
            .height(if (featured) 216.mpx else 204.mpx)
            .onPreviewKeyEvent { e ->
                val playKey = e.key == Key.MediaPlay || e.key == Key.MediaPlayPause || e.key == Key.MediaFastForward
                if (playKey && e.type == KeyEventType.KeyUp) onPlay()
                playKey
            },
        idle = Modifier
            .background(StageColors.Ink.copy(alpha = 0.045f), RoundedCornerShape(r))
            .drawBehind { drawInnerRing(Color.White.copy(alpha = 0.05f), 1.mpx.toPx(), r.toPx()) },
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(horizontal = 20.mpx, vertical = 16.mpx)) {
            // League line · minute (red while live) or kick-off.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val day = if (m.isLive) null else dayOf(m.startMs, locale)
                val league = listOfNotNull(leagueName(m), m.subtitle?.takeIf { featured }, day).joinToString(stringResource(R.string.content_epg_bits_separator))
                Text(league, style = stageText(16.5f, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val right = when {
                    m.off -> m.statusText
                    m.state == MatchState.POST -> m.statusText
                    else -> timeText(m.startMs)
                }
                // Live: the red dot and LIVE, so a live card stands out among ones still to come.
                if (m.isLive) LiveMark(stringResource(R.string.player_live) + stringResource(R.string.content_epg_bits_separator) + clockText(m), 17f)
                else Text(right, style = stageText(17, 800), maxLines = 1, overflow = TextOverflow.Ellipsis, color = StageColors.Muted)
            }
            val spoiler = config.spoilerFree
            // The big "2 – 1" only for one score a side; sets and innings read as the two sides' lines.
            if (featured && m.sport !in LINE_SCORE_SPORTS) FeaturedScore(m, spoiler) else Sides(m, spoiler)
            Spacer(Modifier.weight(1f))
            val followed = m.competitors.any { c -> config.teams.any { it.sport == m.sport && it.id == c.team.id } }
            CardFooter(m.venue, reminded, followed)
        }
    }
}

/** Two sides, each a 42 px crest, the name and the score; the losing side in muted grey. */
@Composable
private fun Sides(m: Match, spoiler: Boolean) {
    if (m.competitors.size < 2) {
        // Racing, a card: the session and its subtitle.
        Spacer(Modifier.height(12.mpx))
        Text(m.title, style = stageText(21, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        m.subtitle?.let { Text(it, style = stageText(19, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        return
    }
    listOfNotNull(m.home, m.away).forEach { c ->
        Spacer(Modifier.height(12.mpx))
        val lost = c.winner == false
        Row(horizontalArrangement = Arrangement.spacedBy(13.mpx), verticalAlignment = Alignment.CenterVertically) {
            Crest(c.team, 42)
            Text(teamLabel(c.team), style = stageText(21, 700), color = if (lost) StageColors.Muted else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (m.state != MatchState.PRE && !m.off) {
                val s = if (spoiler) "–" else scoreText(m.sport, c.score)
                Text(s, style = stageText(if (s.length > 2) 21f else 27f, 800), color = if (lost) StageColors.Muted else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The featured card's middle: crest and name each side, the score 51 px in between. */
@Composable
private fun FeaturedScore(m: Match, spoiler: Boolean) {
    val home = m.home
    val away = m.away
    if (home == null || away == null) { Sides(m, spoiler); return }
    Row(Modifier.fillMaxWidth().padding(top = 12.mpx), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(13.mpx), verticalAlignment = Alignment.CenterVertically) {
            Crest(home.team, 60)
            Text(teamLabel(home.team), style = stageText(21, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val score = if (spoiler) "– : –" else "${scoreText(m.sport, home.score)} – ${scoreText(m.sport, away.score)}"
        Text(score, style = stageText(51, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 18.mpx))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(13.mpx, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Text(teamLabel(away.team), style = stageText(21, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Crest(away.team, 60)
        }
    }
}

/** `.ft`: the card's foot — the venue (ESPN's), then a Reminder tag or the star of a followed side. */
@Composable
private fun CardFooter(venue: String?, reminded: Boolean, followed: Boolean) {
    Box(Modifier.fillMaxWidth().height(1.mpx).background(Color.White.copy(alpha = 0.07f)))
    Spacer(Modifier.height(12.mpx))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
        Text(venue.orEmpty(), style = stageText(16, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (reminded) StageTag(stringResource(R.string.sports_reminder_set))
        else if (followed) tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.STAR, Color(0xFFE7C56A), Modifier.size(20.mpx))
    }
}

/** `.lrow` in "Sport on your channels now": the logo, "Handball · THW Kiel – SC Magdeburg", "DE| DYN SPORT 1 · 19:00–20:45" and how far it is. */
@Composable
private fun OnChannelsRow(item: GuideSportItem, now: Long, modifier: Modifier, onClick: () -> Unit) {
    val ch = item.channels.first()
    val locale = LocalConfiguration.current.locales[0]
    StageRow(onClick = onClick, height = 96.mpx, modifier = modifier, horizontalPadding = 18.mpx) { _ ->
        ChannelLogoTile(
            logoUrl = ch.logoUrl,
            modifier = Modifier.size(78.mpx, 48.mpx).background(stageStillBack, RoundedCornerShape(10.mpx)),
            fill = Color.Transparent,
        ) {}
        Column(Modifier.weight(1f)) {
            val sep = stringResource(R.string.content_epg_bits_separator)
            Text(guideSportLabel(item.sport) + sep + item.title, style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                ch.name + sep + stringResource(R.string.content_epg_time_range, timeText(item.startMs), timeText(item.stopMs)),
                style = stageText(18, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val span = (item.stopMs - item.startMs).coerceAtLeast(1)
            StageProgress(((now - item.startMs).toFloat() / span).coerceIn(0f, 1f), Modifier.padding(top = 7.mpx).fillMaxWidth(0.7f), flat = true)
        }
    }
}

/**
 * Hold OK on a match (a Scores card, a Calendar row): "Remind me" for a match still to come, follow
 * either side (or stop following), each side's team page, and the match page. A finished match offers
 * only the team pages and the match page.
 */
@Composable
internal fun MatchMenu(
    match: Match,
    config: SportsConfig,
    channels: List<WatchOption>,
    now: Long,
    onFollow: (FollowedTeam) -> Unit,
    onRemind: () -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
    onOpenMatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    val over = match.state == MatchState.POST || match.off
    val remind = match.state == MatchState.PRE && match.startMs > now && channels.isNotEmpty()
    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(Modifier.fillMaxSize().background(StageColors.Scrim).longPressMenuGuard(), contentAlignment = Alignment.Center) {
            StageMenu(Modifier.width(560.mpx).trapAllFocusExit().focusGroup()) {
                StageMenuHeader(MatchNotifications.pair(LocalContext.current, match), leagueName(match))
                if (remind) {
                    StageMenuItem(
                        text = stringResource(R.string.content_remind_me),
                        icon = OwnTVIcon.BELL,
                        value = pluralStringResource(R.plurals.recording_minutes, config.reminderLeadMinutes, config.reminderLeadMinutes),
                        onClick = { onRemind(); onDismiss() },
                        modifier = Modifier.focusRequester(first),
                    )
                }
                if (!over) {
                    match.competitors.forEachIndexed { i, c ->
                        val team = FollowedTeam(match.sport, c.team.id, c.team.name, c.team.abbreviation)
                        val following = config.teams.any { it.key == team.key }
                        StageMenuItem(
                            text = c.team.name,
                            icon = OwnTVIcon.STAR,
                            iconFilled = following,
                            value = stringResource(if (following) R.string.sports_following else R.string.sports_follow),
                            onClick = { onFollow(team); onDismiss() },
                            modifier = if (i == 0 && !remind) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
                val pages = match.competitors.mapNotNull { teamRefOf(match, it.team) }
                pages.forEachIndexed { i, ref ->
                    StageMenuItem(
                        text = ref.team.name,
                        icon = OwnTVIcon.HOME,
                        value = stringResource(R.string.sports_team_page),
                        onClick = { onDismiss(); onOpenTeam(ref.copy(from = match.key)) },
                        modifier = if (i == 0 && over) Modifier.focusRequester(first) else Modifier,
                    )
                }
                StageMenuItem(
                    text = stringResource(R.string.sports_open_match_page),
                    icon = OwnTVIcon.CHEVRON,
                    onClick = { onDismiss(); onOpenMatch() },
                    modifier = if ((over && pages.isEmpty()) || (!over && !remind && match.competitors.isEmpty())) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

/** A side's name on a card: the short name, unless ESPN gives only a code ("GHA" in cricket), then the full name. */
internal fun teamLabel(t: tv.own.owntv.core.sports.model.Team): String =
    if (t.shortName.length <= 4 && t.shortName == t.shortName.uppercase() && t.name.length > t.shortName.length) t.name else t.shortName
