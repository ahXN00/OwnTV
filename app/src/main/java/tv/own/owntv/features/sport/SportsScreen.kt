package tv.own.owntv.features.sport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.epg.GuideVideo
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageMenu
import tv.own.owntv.ui.stage.StageMenuHeader
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

private sealed interface SportsRow {
    data class Header(val labelRes: Int) : SportsRow
    data class Game(val fixture: Fixture) : SportsRow
}

/**
 * Sports hub (Stage): league rail, fixture cards grouped Live / Today / Upcoming / Results,
 * and a detail pane with venue, broadcasts, standings and the matched user channels — one tap
 * on a channel starts playback through the shared LiveViewModel zap path.
 */
@Composable
fun SportsScreen(
    onPlayChannel: (Long, List<ChannelEntity>) -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean = true,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
    onContentScrolled: (Boolean) -> Unit = {},
    contentStart: Dp = 150.mpx,
    modifier: Modifier = Modifier,
) {
    val vm: SportsViewModel = koinViewModel()
    val liveVm: LiveViewModel = koinViewModel()
    val leagues by vm.leagues.collectAsStateWithLifecycle()
    val selectedLeagueId by vm.selectedLeagueId.collectAsStateWithLifecycle()
    val board by vm.selectedBoard.collectAsStateWithLifecycle()
    val selectedFixture by vm.selectedFixture.collectAsStateWithLifecycle()
    val matchesState by vm.matches.collectAsStateWithLifecycle()
    val standings by vm.standings.collectAsStateWithLifecycle()
    val standingsLoading by vm.standingsLoading.collectAsStateWithLifecycle()
    val myTeams by vm.myTeamFixtures.collectAsStateWithLifecycle()
    val followedTeams by vm.followedTeams.collectAsStateWithLifecycle()
    val livePreviewSetting by liveVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val previewArmed by liveVm.previewArmed.collectAsStateWithLifecycle()
    val effectivePreview = previewEnabled && livePreviewSetting
    var showSettings by remember { mutableStateOf(false) }

    val fixtures = (board as? SportsViewModel.BoardState.Content)?.board?.fixtures.orEmpty()
    val boardError = board is SportsViewModel.BoardState.Error
    val boardLoading = board == null || board is SportsViewModel.BoardState.Loading

    val rows = remember(fixtures) { buildRows(fixtures) }

    val leagueFocus = remember { mutableStateMapOf<String, FocusRequester>() }
    fun leagueRequester(id: String) = leagueFocus.getOrPut(id) { FocusRequester() }
    val gameFocus = remember { mutableStateMapOf<String, FocusRequester>() }
    fun gameRequester(id: String) = gameFocus.getOrPut(id) { FocusRequester() }
    val matchFocus = remember { mutableStateMapOf<Long, FocusRequester>() }
    fun matchRequester(id: Long) = matchFocus.getOrPut(id) { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    suspend fun focusGame(id: String) {
        if (runCatching { gameRequester(id).requestFocus() }.isSuccess) return
        val index = rows.indexOfFirst { it is SportsRow.Game && it.fixture.id == id }
        if (index >= 0) runCatching { listState.scrollToItem(index) }
        withFrameNanos { }
        runCatching { gameRequester(id).requestFocus() }
    }

    val entry: () -> Boolean = {
        val firstGame = rows.firstOrNull { it is SportsRow.Game } as? SportsRow.Game
        if (firstGame != null) runCatching { gameRequester(firstGame.fixture.id).requestFocus() }.isSuccess
        else runCatching { leagueRequester(leagues.firstOrNull()?.league?.id.orEmpty()).requestFocus() }.isSuccess
    }
    DisposableEffect(onEntryHook, rows) {
        onEntryHook?.invoke(entry)
        onDispose { onEntryHook?.invoke(null) }
    }

    // Detail always populated: pick the live game first, else the next upcoming one.
    LaunchedEffect(fixtures) {
        if (selectedFixture == null && fixtures.isNotEmpty()) {
            val pick = fixtures.firstOrNull { it.state == FixtureState.LIVE }
                ?: fixtures.filter { it.state == FixtureState.UPCOMING }.minByOrNull { it.dateMs }
                ?: fixtures.maxByOrNull { it.dateMs }
            vm.selectFixture(pick)
        }
    }

    LaunchedEffect(restoreFocus) {
        if (!restoreFocus) return@LaunchedEffect
        withFrameNanos { }
        val id = selectedFixture?.id
        if (id != null) focusGame(id) else entry()
        onRestored()
    }

    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24 } }
    LaunchedEffect(scrolled) { onContentScrolled(scrolled) }
    DisposableEffect(Unit) { onDispose { liveVm.stopPreview() } }

    BoxWithConstraints(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        val screenW = maxWidth
        fun fx(px: Int) = screenW * (px / 1920f)
        Row(
            Modifier
                .padding(start = contentStart + 20.mpx, top = 52.mpx, end = 28.mpx)
                .fillMaxSize(),
        ) {
            // League rail.
            Column(Modifier.width(fx(330)).fillMaxHeight()) {
                Text(stringResource(R.string.sport_title), style = stageText(46, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.sport_leagues), style = stageText(20, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx, bottom = 10.mpx))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.mpx), modifier = Modifier.fillMaxHeight()) {
                    items(leagues, key = { it.league.id }) { league ->
                        val selected = league.league.id == selectedLeagueId
                        StageSurface(
                            onClick = { vm.selectLeague(league.league.id) },
                            onLongClick = { vm.toggleLeagueFollowed(league.league.id) },
                            radius = 18.mpx,
                            modifier = Modifier.fillMaxWidth().focusRequester(leagueRequester(league.league.id)),
                            idle = if (selected) Modifier.background(stageAccent.accent.copy(alpha = 0.14f), RoundedCornerShape(18.mpx)) else Modifier,
                        ) { focused ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.mpx, vertical = 12.mpx),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.mpx),
                            ) {
                                OwnTVIcon(
                                    OwnTVIcon.SPORT,
                                    if (focused) stageAccent.onAccent else if (selected) stageAccent.accent else StageColors.NavText,
                                    Modifier.size(24.mpx),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(league.name, style = stageText(20, if (selected) 700 else 500), color = if (focused) stageAccent.onAccent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!league.followed) {
                                        Text(
                                            stringResource(R.string.sport_follow),
                                            style = stageText(14, 500),
                                            color = if (focused) stageAccent.onAccent.copy(alpha = 0.8f) else StageColors.Dim,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (league.liveCount > 0) {
                                    LiveDot(league.liveCount.toString())
                                } else if (league.loading) {
                                    Text(stringResource(R.string.sport_loading), style = stageText(14, 500), color = StageColors.Dim, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            // Fixtures (extra top padding like the detail pane: shell clock/weather overlays the top).
            Column(Modifier.weight(1.35f).fillMaxHeight().padding(start = 20.mpx, end = 20.mpx, top = 64.mpx)) {
                val leagueName = leagues.firstOrNull { it.league.id == selectedLeagueId }?.name.orEmpty()
                Text(leagueName, style = stageText(30, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mpx), modifier = Modifier.fillMaxWidth().padding(top = 10.mpx)) {
                    StageSurface(
                        onClick = { showSettings = true },
                        radius = 16.mpx,
                        modifier = Modifier.focusRequester(settingsFocus),
                    ) { focused ->
                        Row(Modifier.padding(horizontal = 14.mpx, vertical = 8.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                            OwnTVIcon(OwnTVIcon.SETTINGS, if (focused) stageAccent.onAccent else StageColors.Text, Modifier.size(20.mpx))
                            Text(stringResource(R.string.sport_settings), style = stageText(17, 600), color = if (focused) stageAccent.onAccent else StageColors.Text, maxLines = 1)
                        }
                    }
                    StageSurface(
                        onClick = { vm.refreshSelected() },
                        radius = 16.mpx,
                        modifier = Modifier.focusRequester(refreshFocus),
                    ) { focused ->
                        Row(Modifier.padding(horizontal = 14.mpx, vertical = 8.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                            OwnTVIcon(OwnTVIcon.REFRESH, if (focused) stageAccent.onAccent else StageColors.Text, Modifier.size(20.mpx))
                            Text(stringResource(R.string.sport_retry), style = stageText(17, 600), color = if (focused) stageAccent.onAccent else StageColors.Text, maxLines = 1)
                        }
                    }
                }
                if (myTeams.isNotEmpty()) {
                    Text(stringResource(R.string.sport_my_teams), style = stageText(20, 600), color = StageColors.Muted, maxLines = 1, modifier = Modifier.padding(top = 12.mpx, bottom = 8.mpx))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.mpx), modifier = Modifier.fillMaxWidth()) {
                        items(myTeams, key = { it.id }) { fixture ->
                            StageSurface(
                                onClick = { vm.selectFixture(fixture) },
                                radius = 14.mpx,
                                focusStyle = StageFocus.FX,
                                modifier = Modifier.focusRequester(gameRequester(fixture.id)),
                            ) { focused ->
                                FixtureChip(fixture, followedTeams, focused)
                            }
                        }
                    }
                }
                when {
                    boardLoading && fixtures.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().padding(top = 60.mpx), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.sport_loading), style = stageText(22, 500), color = StageColors.Muted)
                        }
                    }
                    boardError && fixtures.isEmpty() -> {
                        Column(Modifier.fillMaxWidth().padding(top = 60.mpx), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                            Text(stringResource(R.string.sport_load_failed), style = stageText(22, 500), color = StageColors.Muted)
                            StageSurface(onClick = { vm.refreshSelected() }, radius = 16.mpx) { focused ->
                                Text(stringResource(R.string.sport_retry), style = stageText(19, 600), color = if (focused) stageAccent.onAccent else StageColors.Text, modifier = Modifier.padding(horizontal = 22.mpx, vertical = 10.mpx))
                            }
                        }
                    }
                    rows.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().padding(top = 60.mpx), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.sport_no_games), style = stageText(22, 500), color = StageColors.Muted)
                        }
                    }
                    else -> {
                        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.mpx), modifier = Modifier.fillMaxHeight().padding(top = 12.mpx)) {
                            rows.forEach { row ->
                                when (row) {
                                    is SportsRow.Header -> {
                                        item(key = "h" + row.labelRes) {
                                            Text(stringResource(row.labelRes), style = stageText(20, 700), color = StageColors.Muted, maxLines = 1, modifier = Modifier.padding(top = 10.mpx, bottom = 2.mpx))
                                        }
                                    }
                                    is SportsRow.Game -> {
                                        val fixture = row.fixture
                                        val selected = fixture.id == selectedFixture?.id
                                        item(key = fixture.id) {
                                            StageSurface(
                                                onClick = { vm.selectFixture(fixture) },
                                                radius = 18.mpx,
                                                modifier = Modifier.fillMaxWidth().focusRequester(gameRequester(fixture.id)),
                                                idle = if (selected) Modifier.background(stageAccent.accent.copy(alpha = 0.14f), RoundedCornerShape(18.mpx)) else Modifier,
                                            ) { focused ->
                                                FixtureCard(fixture, followedTeams, focused, selected)
                                            }
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(40.mpx)) }
                        }
                    }
                }
            }
            // Detail pane (extra top padding: shell clock/weather overlays the top-right).
            Column(Modifier.weight(1f).fillMaxHeight().padding(top = 64.mpx)) {
                val fixture = selectedFixture
                if (fixture == null) {
                    Text(stringResource(R.string.sport_details), style = stageText(30, 700), color = StageColors.Text, maxLines = 1, modifier = Modifier.padding(bottom = 12.mpx))
                    Text(stringResource(R.string.sport_no_games), style = stageText(20, 500), color = StageColors.Muted)
                } else {
                    FixtureDetail(
                        fixture = fixture,
                        followedTeams = followedTeams,
                        matchesState = matchesState,
                        standings = standings,
                        standingsLoading = standingsLoading,
                        matchRequester = ::matchRequester,
                        onPlayMatch = { match, list -> vm.playMatch(match, list, onPlayChannel) },
                        onToggleTeam = { leagueId, abbrev -> vm.toggleTeamFollowed(leagueId, abbrev) },
                        sportsVm = vm,
                        liveVm = liveVm,
                        effectivePreview = effectivePreview,
                        previewArmed = previewArmed,
                    )
                }
            }
        }
        if (showSettings) {
            SportsSettingsDialog(
                leagues = leagues,
                onToggleLeague = { vm.toggleLeagueFollowed(it) },
                onDismiss = { showSettings = false },
            )
        }
    }
}

private fun buildRows(fixtures: List<Fixture>): List<SportsRow> {
    val rows = ArrayList<SportsRow>()
    val live = fixtures.filter { it.state == FixtureState.LIVE }.sortedBy { it.dateMs }
    val upcoming = fixtures.filter { it.state == FixtureState.UPCOMING }.sortedBy { it.dateMs }
    val results = fixtures.filter { it.state == FixtureState.FINISHED }.sortedByDescending { it.dateMs }.take(30)
    if (live.isNotEmpty()) {
        rows.add(SportsRow.Header(R.string.sport_group_live))
        live.forEach { rows.add(SportsRow.Game(it)) }
    }
    val today = upcoming.filter { isToday(it.dateMs) }
    val future = upcoming.filter { !isToday(it.dateMs) }
    if (today.isNotEmpty()) {
        rows.add(SportsRow.Header(R.string.sport_group_today))
        today.forEach { rows.add(SportsRow.Game(it)) }
    }
    if (future.isNotEmpty()) {
        rows.add(SportsRow.Header(R.string.sport_group_upcoming))
        future.forEach { rows.add(SportsRow.Game(it)) }
    }
    if (results.isNotEmpty()) {
        rows.add(SportsRow.Header(R.string.sport_group_results))
        results.forEach { rows.add(SportsRow.Game(it)) }
    }
    return rows
}

private fun isToday(dateMs: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(dateMs).atZone(zone).toLocalDate() == LocalDate.now(zone)
}

private fun dayLabel(dateMs: Long, today: String, tomorrow: String, yesterday: String): String? {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(dateMs).atZone(zone).toLocalDate()
    val now = LocalDate.now(zone)
    return when (date) {
        now -> null
        now.plusDays(1) -> tomorrow
        now.minusDays(1) -> yesterday
        else -> date.format(DateTimeFormatter.ofPattern(DayPattern))
    }
}

@Composable
private fun LiveDot(count: String) {
    Box(
        Modifier
            .background(stageAccent.accent, RoundedCornerShape(12.mpx))
            .padding(horizontal = 10.mpx, vertical = 4.mpx),
        contentAlignment = Alignment.Center,
    ) {
        Text(count, style = stageText(15, 700), color = stageAccent.onAccent, maxLines = 1)
    }
}

@Composable
private fun TeamLogo(url: String?, size: Dp) {
    if (url.isNullOrBlank()) {
        Spacer(Modifier.size(size))
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
    }
}

private fun teamColor(hex: String?): Color? =
    hex?.takeIf { it.isNotBlank() }?.let {
        runCatching { Color((ColorPrefix + it.removePrefix(HashPrefix)).toLong(16)) }.getOrNull()
    }

@Composable
private fun FixtureCard(fixture: Fixture, followedTeams: Set<String>, focused: Boolean, selected: Boolean) {
    val accent = stageAccent
    val titleColor = if (focused) accent.onAccent else StageColors.Text
    val dimColor = if (focused) accent.onAccent.copy(alpha = 0.8f) else StageColors.Dim
    val today = stringResource(R.string.sport_today_word)
    val tomorrow = stringResource(R.string.sport_tomorrow_word)
    val yesterday = stringResource(R.string.sport_yesterday_word)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.mpx, vertical = 12.mpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.mpx),
    ) {
        Box(Modifier.width(5.mpx).height(52.mpx).background(teamColor(fixture.home.colorHex) ?: Color.Transparent, RoundedCornerShape(3.mpx)))
        TeamLogo(fixture.home.logoUrl, 40.mpx)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                Text(fixture.home.abbreviation, style = stageText(22, 700), color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    fixture.home.score ?: fixture.statusLine,
                    style = stageText(22, 700),
                    color = if (fixture.state == FixtureState.LIVE) accent.accent else titleColor,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                Text(fixture.away.abbreviation, style = stageText(22, 700), color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    fixture.away.score ?: fixture.statusLine,
                    style = stageText(22, 700),
                    color = if (fixture.state == FixtureState.LIVE) accent.accent else titleColor,
                    maxLines = 1,
                )
            }
            val day = if (fixture.state == FixtureState.UPCOMING) dayLabel(fixture.dateMs, today, tomorrow, yesterday) else null
            val subLine = day ?: fixture.statusLine.takeIf { fixture.state == FixtureState.FINISHED }
            if (subLine != null) {
                Text(
                    subLine,
                    style = stageText(15, 500),
                    color = dimColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.mpx),
                )
            }
        }
        TeamLogo(fixture.away.logoUrl, 40.mpx)
        if (fixture.state == FixtureState.LIVE) {
            LiveDot(fixture.statusLine)
        } else if (selected) {
            OwnTVIcon(OwnTVIcon.CHECK, accent.accent, Modifier.size(22.mpx))
        }
    }
}

@Composable
private fun FixtureChip(fixture: Fixture, followedTeams: Set<String>, focused: Boolean) {
    val accent = stageAccent
    Row(
        Modifier.padding(horizontal = 14.mpx, vertical = 9.mpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.mpx),
    ) {
        Text(fixture.home.abbreviation, style = stageText(17, 700), color = if (focused) accent.onAccent else StageColors.Text, maxLines = 1)
        Text(
            fixtureScoreLine(fixture),
            style = stageText(17, 600),
            color = if (fixture.state == FixtureState.LIVE) accent.accent else if (focused) accent.onAccent else StageColors.Dim,
            maxLines = 1,
        )
        Text(fixture.away.abbreviation, style = stageText(17, 700), color = if (focused) accent.onAccent else StageColors.Text, maxLines = 1)
    }
}

@Composable
private fun fixtureScoreLine(fixture: Fixture): String {
    val home = fixture.home.score
    val away = fixture.away.score
    return if (home != null && away != null) stringResource(R.string.sport_score, home, away) else fixture.statusLine
}

@Composable
private fun FixtureDetail(
    fixture: Fixture,
    followedTeams: Set<String>,
    matchesState: SportsViewModel.MatchesState,
    standings: List<LeagueTable>,
    standingsLoading: Boolean,
    matchRequester: (Long) -> FocusRequester,
    onPlayMatch: (ChannelMatch, List<ChannelMatch>) -> Unit,
    onToggleTeam: (String, String) -> Unit,
    sportsVm: SportsViewModel,
    liveVm: LiveViewModel,
    effectivePreview: Boolean,
    previewArmed: Boolean,
) {
    val accent = stageAccent
    val matches = (matchesState as? SportsViewModel.MatchesState.Content)?.matches.orEmpty()
    var entities by remember(fixture.id) { mutableStateOf(emptyList<ChannelEntity>()) }
    var focusedChannelId by remember(fixture.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(fixture.id, matches) {
        entities = if (matches.isEmpty()) emptyList() else sportsVm.entitiesFor(matches)
        if (focusedChannelId == null) focusedChannelId = matches.firstOrNull()?.channelId
    }
    DisposableEffect(fixture.id) { onDispose { liveVm.stopPreview() } }
    val previewEntity = entities.firstOrNull { it.id == focusedChannelId } ?: entities.firstOrNull()
    val previewEpg = matches.firstOrNull { it.channelId == previewEntity?.id }?.epgTitle
    LaunchedEffect(previewEntity?.id, effectivePreview, previewArmed) {
        val entity = previewEntity ?: return@LaunchedEffect
        if (!effectivePreview || !previewArmed) return@LaunchedEffect
        delay(700)
        liveVm.playPreview(entity)
    }
    Column(modifier = Modifier.fillMaxHeight()) {
        if (previewEntity != null) {
            GuideVideo(
                channel = previewEntity,
                channelLine = previewEntity.name,
                nowTitle = previewEpg,
                previewEngine = liveVm.previewEngine,
                showVideo = effectivePreview && previewArmed,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.mpx),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.mpx), modifier = Modifier.weight(1f)) {
        item(key = "detail" + fixture.id) {
            Column(verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                Text(stringResource(R.string.sport_details), style = stageText(30, 700), color = StageColors.Text, maxLines = 1)
                TeamScoreRow(fixture.home, fixture.state)
                TeamScoreRow(fixture.away, fixture.state)
                if (fixture.state != FixtureState.UPCOMING || fixture.statusLine.isNotBlank()) {
                    Text(fixture.statusLine, style = stageText(18, 600), color = if (fixture.state == FixtureState.LIVE) accent.accent else StageColors.Dim, maxLines = 1)
                }
                fixture.venue?.let { venue ->
                    Text(stringResource(R.string.sport_venue), style = stageText(15, 600), color = StageColors.Dim, maxLines = 1)
                    Text(venue, style = stageText(18, 500), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (fixture.broadcasts.isNotEmpty()) {
                    Text(stringResource(R.string.sport_broadcast), style = stageText(15, 600), color = StageColors.Dim, maxLines = 1)
                    fixture.broadcasts.take(4).forEach { name ->
                        Text(name, style = stageText(18, 500), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), modifier = Modifier.fillMaxWidth()) {
                    FollowButton(fixture.leagueId, fixture.home.abbreviation, followedTeams.contains(SportsPrefs.teamKey(fixture.leagueId, fixture.home.abbreviation)), Modifier.weight(1f)) {
                        onToggleTeam(fixture.leagueId, fixture.home.abbreviation)
                    }
                    FollowButton(fixture.leagueId, fixture.away.abbreviation, followedTeams.contains(SportsPrefs.teamKey(fixture.leagueId, fixture.away.abbreviation)), Modifier.weight(1f)) {
                        onToggleTeam(fixture.leagueId, fixture.away.abbreviation)
                    }
                }
            }
        }
        item(key = "channels" + fixture.id) {
            Column(verticalArrangement = Arrangement.spacedBy(8.mpx), modifier = Modifier.padding(top = 8.mpx)) {
                Text(stringResource(R.string.sport_channels), style = stageText(22, 700), color = StageColors.Text, maxLines = 1)
                when (matchesState) {
                    SportsViewModel.MatchesState.Idle, SportsViewModel.MatchesState.Loading -> {
                        Text(stringResource(R.string.sport_loading), style = stageText(17, 500), color = StageColors.Dim)
                    }
                    is SportsViewModel.MatchesState.Content -> {
                        val list = matchesState.matches
                        if (list.isEmpty()) {
                            Text(stringResource(R.string.sport_no_channel), style = stageText(17, 500), color = StageColors.Dim)
                        } else {
                            list.forEach { match ->
                                StageSurface(
                                    onClick = { onPlayMatch(match, list) },
                                    radius = 14.mpx,
                                    modifier = Modifier.fillMaxWidth().focusRequester(matchRequester(match.channelId))
                                        .onFocusChanged {
                                            if (it.hasFocus) {
                                                focusedChannelId = match.channelId
                                                entities.firstOrNull { entity -> entity.id == match.channelId }?.let { entity ->
                                                    liveVm.onChannelFocused(entity)
                                                }
                                            }
                                        },
                                ) { focused ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 14.mpx, vertical = 10.mpx),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.mpx),
                                    ) {
                                        OwnTVIcon(OwnTVIcon.PLAY, if (focused) accent.onAccent else accent.accent, Modifier.size(22.mpx))
                                        Column(Modifier.weight(1f)) {
                                            Text(match.channelName, style = stageText(19, 600), color = if (focused) accent.onAccent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            match.epgTitle?.let { title ->
                                                Text(title, style = stageText(14, 500), color = if (focused) accent.onAccent.copy(alpha = 0.8f) else StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        Text(stringResource(R.string.sport_play), style = stageText(15, 600), color = if (focused) accent.onAccent else StageColors.Dim, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (standingsLoading) {
            item(key = "table-loading") {
                Text(stringResource(R.string.sport_loading), style = stageText(17, 500), color = StageColors.Dim, modifier = Modifier.padding(top = 8.mpx))
            }
        }
        standings.forEach { table ->
            item(key = "table" + fixture.leagueId + (table.groupName.orEmpty())) {
                Column(verticalArrangement = Arrangement.spacedBy(6.mpx), modifier = Modifier.padding(top = 8.mpx)) {
                    Text(stringResource(R.string.sport_standings), style = stageText(22, 700), color = StageColors.Text, maxLines = 1)
                    table.groupName?.let { name ->
                        Text(name, style = stageText(15, 600), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    table.rows.take(10).forEach { row ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx), modifier = Modifier.fillMaxWidth()) {
                            Text(row.rank, style = stageText(16, 600), color = StageColors.Dim, maxLines = 1, modifier = Modifier.width(30.mpx))
                            TeamLogo(row.logoUrl, 26.mpx)
                            Text(row.teamAbbrev.ifBlank { row.teamName }, style = stageText(17, 600), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            row.stats.take(3).forEach { stat ->
                                Text(stat, style = stageText(16, 500), color = StageColors.Dim, maxLines = 1, modifier = Modifier.width(44.mpx))
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(60.mpx)) }
        }
    }
}

/**
 * League selection for the Sports hub: which of the curated leagues appear in the rail.
 * TV-local checklist; the QR/phone web page is the next step (companion server page).
 */
@Composable
private fun SportsSettingsDialog(
    leagues: List<SportsViewModel.LeagueUi>,
    onToggleLeague: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler { onDismiss() }
    OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(Modifier.fillMaxSize().background(StageColors.Scrim)) {
            Column(
                Modifier
                    .padding(start = 560.mpx, top = 120.mpx, bottom = 24.mpx)
                    .width(640.mpx)
                    .trapAllFocusExit()
                    .focusGroup(),
            ) {
                StageMenu(Modifier.verticalScroll(rememberScrollState())) {
                    StageMenuHeader(
                        title = stringResource(R.string.sport_settings),
                        subtitle = stringResource(R.string.sport_settings_leagues),
                    )
                    leagues.forEachIndexed { index, league ->
                        StageMenuItem(
                            text = league.name,
                            icon = OwnTVIcon.SPORT,
                            checked = league.followed,
                            onClick = { onToggleLeague(league.league.id) },
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamScoreRow(team: SportTeam, state: FixtureState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.width(5.mpx).height(40.mpx).background(teamColor(team.colorHex) ?: Color.Transparent, RoundedCornerShape(3.mpx)))
        TeamLogo(team.logoUrl, 44.mpx)
        Text(team.name, style = stageText(22, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        team.score?.let { score ->
            Text(score, style = stageText(30, 800), color = if (state == FixtureState.LIVE) stageAccent.accent else StageColors.Text, maxLines = 1)
        }
    }
}

@Composable
private fun FollowButton(leagueId: String, abbrev: String, followed: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val accent = stageAccent
    StageSurface(
        onClick = onToggle,
        radius = 14.mpx,
        focusStyle = StageFocus.FX,
        modifier = modifier,
        idle = if (followed) Modifier.background(accent.accent.copy(alpha = 0.14f), RoundedCornerShape(14.mpx)) else Modifier,
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.mpx, vertical = 9.mpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.mpx),
        ) {
            OwnTVIcon(OwnTVIcon.STAR, if (focused) accent.onAccent else if (followed) accent.accent else StageColors.Dim, Modifier.size(20.mpx))
            Column(Modifier.weight(1f)) {
                Text(abbrev, style = stageText(17, 700), color = if (focused) accent.onAccent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (followed) R.string.sport_unfollow else R.string.sport_follow),
                    style = stageText(13, 500),
                    color = if (focused) accent.onAccent.copy(alpha = 0.8f) else StageColors.Dim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val DayPattern = "EEE d.M."
private const val ColorPrefix = "FF"
private const val HashPrefix = "#"
