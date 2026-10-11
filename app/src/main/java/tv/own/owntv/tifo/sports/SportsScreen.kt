package tv.own.owntv.tifo.sports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.live.ProviderTags
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageLiveBadge
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageSpecChip
import tv.own.owntv.ui.stage.StageTab
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.drawBoxShadow
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

private val LiveRed = Color(0xFFFF5A5A)

/**
 * The Sports tab: live and upcoming games of the followed competitions. OK on a game opens its
 * channels under it, with a search of all channels for each of its own broadcasters ("CBS"). The
 * channel under the cursor plays in the preview on the right, as in Live TV, and OK plays it full
 * screen. The Record button beside a channel whose guide shows the game later records that
 * programme.
 */
@Composable
fun SportsScreen(
    onPlayChannel: (ChannelEntity) -> Unit,
    contentStart: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    /** False while the full-screen or mini player owns the video, as for Live TV's preview. */
    previewEnabled: Boolean = true,
    /** Opens the Search tab on [query] ("CBS"); Back there comes back here. */
    onSearchChannels: (query: String) -> Unit = {},
) {
    val vm = org.koin.androidx.compose.koinViewModel<SportsViewModel>()
    // Live TV's view model: its preview player, so there is never a second decoder.
    val liveVm = org.koin.androidx.compose.koinViewModel<LiveViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val recordingRows by vm.recordingRows.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.refreshWhileVisible() }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val count = { n: Int -> java.text.NumberFormat.getIntegerInstance(locale).format(n) }

    val context = LocalContext.current
    val qualities = remember { StreamQualityStore.get(context) }
    val qualityVersion by qualities.version.collectAsStateWithLifecycle()
    val favorites = remember { SportsFavorites.get(context) }
    val favoriteKeys by favorites.keys.collectAsStateWithLifecycle()
    var settingsOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var dayPickerOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val shownDay = state.shownDay(today)
    if (settingsOpen) {
        SportsTeamsSettings(
            vm, favorites, contentStart,
            onClose = {
                vm.setReturnFocus(TEAMS_BUTTON_KEY)
                settingsOpen = false
            },
            modifier = modifier,
        )
        return
    }

    // The channel under the cursor plays in the preview once the cursor settles, as in Live TV,
    // and what the stream turns out to be (resolution, frame rate) is kept for its tags.
    var previewed by remember { mutableStateOf<SportsChannelMatch?>(null) }
    val livePreviewSetting by liveVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val effectivePreview = previewEnabled && livePreviewSetting
    LaunchedEffect(previewed?.channel?.id, effectivePreview) {
        val channel = previewed?.channel ?: return@LaunchedEffect
        if (!effectivePreview) return@LaunchedEffect
        delay(PREVIEW_SETTLE_MS)
        liveVm.onChannelFocused(channel)
        liveVm.playPreview(channel)
        withTimeoutOrNull(MEASURE_MS) {
            // The chips still showing belong to the channel before; this one's follow a reset.
            liveVm.previewEngine.streamChips
                .dropWhile { it.isNotEmpty() }
                .collect { chips -> if (chips.isNotEmpty()) qualities.record(channel, chips) }
        }
    }
    // Back closes the open game and puts the cursor back on it. Coming back from a broadcaster
    // search, the cursor starts on the search it left from.
    var refocus by remember { mutableStateOf(vm.takeReturnFocus()) }
    val listState = rememberLazyListState(vm.listIndex, vm.listOffset)
    DisposableEffect(listState) {
        onDispose {
            vm.listIndex = listState.firstVisibleItemIndex
            vm.listOffset = listState.firstVisibleItemScrollOffset
        }
    }
    // Back from full screen comes back to the channel that was played.
    val play = { channel: ChannelEntity ->
        vm.setReturnFocus(channelKey(channel.id))
        onPlayChannel(channel)
    }
    // Back on a game (none open) goes up to the open tab; Back there leaves Sports.
    var listFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = state.selected == null && listFocused) {
        refocus = tabKey(state)
    }
    BackHandler(enabled = state.selected != null) {
        val selected = state.selected ?: return@BackHandler
        vm.select(selected)
        refocus = eventKey(selected)
    }

    Column(
        modifier.padding(start = contentStart, end = 48.mpx, top = 40.mpx, bottom = 24.mpx),
        verticalArrangement = Arrangement.spacedBy(18.mpx),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
            Text(
                stringResource(R.string.tifo_sports_title),
                style = stageText(40, 800),
                color = StageColors.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // Sports' own settings: the favourite teams.
            StageButton(
                text = stringResource(R.string.tifo_sports_teams),
                onClick = { settingsOpen = true },
                icon = OwnTVIcon.SETTINGS,
                height = 52.mpx,
                textSize = 18,
                modifier = Modifier.refocusWhen(TEAMS_BUTTON_KEY, refocus) { refocus = null },
            )
            // The day the league tabs show. My teams lists every day, so it has none.
            if (!state.myTeams) {
                StageButton(
                    text = dayLabel(shownDay, today),
                    onClick = { dayPickerOpen = true },
                    icon = OwnTVIcon.EPG,
                    trailingIcon = OwnTVIcon.CHEVRON_DOWN,
                    tinted = state.day != null,
                    height = 52.mpx,
                    textSize = 18,
                    modifier = Modifier.refocusWhen(DAY_BUTTON_KEY, refocus) { refocus = null },
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
            item(key = "all") {
                StageTab(
                    label = stringResource(R.string.tifo_sports_all),
                    count = count(state.copy(filter = null, myTeams = false).visibleEvents(favoriteKeys, today, zone).size),
                    selected = state.filter == null && !state.myTeams,
                    onClick = { vm.setFilter(null) },
                    modifier = Modifier.refocusWhen(ALL_TAB_KEY, refocus) { refocus = null },
                )
            }
            item(key = "mine") {
                StageTab(
                    label = stringResource(R.string.tifo_sports_my_teams),
                    count = count(state.copy(myTeams = true).visibleEvents(favoriteKeys).size),
                    selected = state.myTeams,
                    onClick = { vm.showMyTeams() },
                    modifier = Modifier.refocusWhen(MY_TEAMS_TAB_KEY, refocus) { refocus = null },
                )
            }
            items(Competition.entries.toList(), key = { it.name }) { c ->
                StageTab(
                    label = stringResource(c.labelRes),
                    count = count(state.copy(filter = c, myTeams = false).visibleEvents(favoriteKeys, today, zone).size),
                    selected = state.filter == c,
                    onClick = { vm.setFilter(c) },
                    modifier = Modifier.refocusWhen(tabKey(c), refocus) { refocus = null },
                )
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.mpx)) {
            Box(Modifier.weight(0.56f).fillMaxHeight().onFocusChanged { listFocused = it.hasFocus }) {
                val events = state.visibleEvents(favoriteKeys, today, zone)
                // All lists the games league by league, under the league's name.
                val grouped = state.filter == null && !state.myTeams
                val isFavorite = { e: SportsEvent, team: SportsTeam? -> SportsFavorites.isFavorite(favoriteKeys, e.competition, team) }
                when {
                    dayPickerOpen -> DayPicker(
                        shown = shownDay,
                        today = today,
                        gamesOn = { d -> state.tabEvents().count { SportsSchedule.isOn(it, d, today, zone) } },
                        onPick = { d ->
                            vm.setDay(d)
                            dayPickerOpen = false
                            refocus = DAY_BUTTON_KEY
                        },
                        onClose = {
                            dayPickerOpen = false
                            refocus = DAY_BUTTON_KEY
                        },
                    )
                    state.loading || (state.dayLoading && events.isEmpty()) -> Message(stringResource(R.string.tifo_sports_loading))
                    events.isEmpty() && state.failed -> Message(stringResource(R.string.tifo_sports_failed))
                    events.isEmpty() && state.myTeams && favoriteKeys.isEmpty() -> Message(stringResource(R.string.tifo_sports_my_teams_none))
                    events.isEmpty() && state.myTeams -> Message(stringResource(R.string.tifo_sports_my_teams_no_games))
                    events.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                        Message(
                            if (shownDay == today) stringResource(R.string.tifo_sports_no_games)
                            else stringResource(R.string.tifo_sports_no_games_on, dayDate(shownDay)),
                        )
                        // Straight to the next day with a game, when the schedule knows one.
                        val next = SportsSchedule.nextDay(state.tabEvents(), shownDay, zone)
                        if (next != null) {
                            SportsActionRow(
                                text = stringResource(R.string.tifo_sports_next_games, dayLabel(next, today)),
                                icon = OwnTVIcon.EPG,
                                onClick = {
                                    vm.setDay(next)
                                    refocus = FIRST_EVENT_KEY
                                },
                            )
                        }
                    }
                    else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                        // Leagues with a live game first, then those still to play, then those done for the day.
                        val leagueRank = { c: Competition ->
                            val states = events.filter { it.competition == c }.map { it.state }
                            when {
                                EventState.LIVE in states -> 0
                                EventState.UPCOMING in states -> 1
                                else -> 2
                            }
                        }
                        val ordered = if (grouped) {
                            Competition.entries.sortedBy(leagueRank).flatMap { c -> events.filter { it.competition == c } }
                        } else {
                            events
                        }
                        for ((index, event) in ordered.withIndex()) {
                            if (grouped && (index == 0 || ordered[index - 1].competition != event.competition)) {
                                item(key = "league:" + event.competition.name) { LeagueHeader(stringResource(event.competition.labelRes)) }
                            }
                            val open = event.id == state.selected?.id
                            item(key = eventKey(event)) {
                                EventRow(
                                    event, selected = open, onClick = { vm.select(event) },
                                    isFavorite = { team -> isFavorite(event, team) },
                                    modifier = Modifier
                                        .refocusWhen(eventKey(event), refocus) { refocus = null }
                                        .then(if (index == 0) Modifier.refocusWhen(FIRST_EVENT_KEY, refocus) { refocus = null } else Modifier),
                                )
                            }
                            if (open) {
                                eventChannels(
                                    event = event,
                                    state = state,
                                    qualityOf = { ch -> qualityVersion.let { qualities.qualityOf(ch) } },
                                    recordingFor = { match -> vm.recordingFor(match, recordingRows) },
                                    onSearch = { name ->
                                        vm.setReturnFocus(searchKey(name))
                                        onSearchChannels(name)
                                    },
                                    refocus = refocus,
                                    onRefocused = { refocus = null },
                                    onFocusChannel = { previewed = it },
                                    onPlayChannel = play,
                                    onToggleRecording = vm::toggleRecording,
                                )
                            }
                        }
                    }
                }
            }
            SportsPreviewPane(
                match = previewed,
                event = state.selected,
                quality = previewed?.channel?.let { ch -> qualityVersion.let { qualities.qualityOf(ch) } },
                previewEngine = liveVm.previewEngine,
                showVideo = effectivePreview,
                modifier = Modifier.weight(0.44f).fillMaxHeight(),
            )
        }
    }
}

/** Under the open game: its broadcaster searches, then its matches. */
private fun LazyListScope.eventChannels(
    event: SportsEvent,
    state: SportsUiState,
    qualityOf: (ChannelEntity) -> StreamQuality?,
    recordingFor: (SportsChannelMatch) -> RecordingEntity?,
    onSearch: (String) -> Unit,
    refocus: String?,
    onRefocused: () -> Unit,
    onFocusChannel: (SportsChannelMatch) -> Unit,
    onPlayChannel: (ChannelEntity) -> Unit,
    onToggleRecording: (SportsChannelMatch) -> Unit,
) {
    val key = event.competition.name + event.id
    val indent = Modifier.padding(start = CHANNEL_INDENT)
    // The game's own broadcasters, each a search of every channel named after it ("CBS") in the
    // Search tab. Rows like the channels under them, so the cursor walks onto them the same way.
    items(state.broadcasters, key = { searchKey(it) }) { name ->
        SportsActionRow(
            text = stringResource(R.string.tifo_sports_search_broadcaster, name),
            icon = OwnTVIcon.SEARCH,
            onClick = { onSearch(name) },
            modifier = indent.refocusWhen(searchKey(name), refocus, onRefocused),
        )
    }

    when {
        state.matching -> item(key = "m:$key") { Box(indent) { Message(stringResource(R.string.tifo_sports_matching)) } }
        state.matches.isEmpty() -> item(key = "m:$key") { Box(indent) { Message(stringResource(R.string.tifo_sports_no_channels)) } }
        else -> {
            val now = System.currentTimeMillis()
            val canRecord = { match: SportsChannelMatch ->
                event.state == EventState.UPCOMING && (match.programmeStartMs ?: 0L) > now
            }
            if (state.matches.any(canRecord)) {
                item(key = "h:$key") {
                    Text(
                        stringResource(R.string.tifo_sports_record_hint),
                        style = stageText(15, 500), color = StageColors.Muted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = indent,
                    )
                }
            }
            items(state.matches, key = { channelKey(it.channel.id) }) { match ->
                val recording = recordingFor(match)
                Row(indent, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                    ChannelRow(
                        match, quality = qualityOf(match.channel), recordingSet = recording != null,
                        onFocused = { onFocusChannel(match) }, onClick = { onPlayChannel(match.channel) },
                        modifier = Modifier.weight(1f).refocusWhen(channelKey(match.channel.id), refocus, onRefocused),
                    )
                    if (recording != null || canRecord(match)) {
                        StageButton(
                            text = null,
                            onClick = { onToggleRecording(match) },
                            icon = OwnTVIcon.REC,
                            iconFilled = recording != null,
                            tinted = recording != null,
                            round = true,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The right-hand side: the channel under the cursor playing muted, as Live TV's preview, with what
 * the stream is (resolution, frame rate) and the game's programme on it.
 */
@Composable
private fun SportsPreviewPane(
    match: SportsChannelMatch?,
    event: SportsEvent?,
    quality: StreamQuality?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    val channel = match?.channel
    val previewState by previewEngine.state.collectAsStateWithLifecycle()
    val streamChips by previewEngine.streamChips.collectAsStateWithLifecycle()
    val playing = showVideo && channel != null &&
        previewState != tv.own.owntv.player.LivePreviewEngine.State.ERROR &&
        previewState != tv.own.owntv.player.LivePreviewEngine.State.IDLE
    val r = 28.mpx
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.mpx)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .drawBehind { drawBoxShadow(Color.Black.copy(alpha = 0.55f), 80.mpx.toPx(), r.toPx(), dy = 30.mpx.toPx()) }
                .clip(RoundedCornerShape(r))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (channel != null) {
                ChannelLogoTile(logoUrl = channel.displayLogoUrl, modifier = Modifier.size(200.mpx), fill = Color.Transparent) {
                    OwnTVIcon(OwnTVIcon.LIVE_TV, tint = StageColors.Dim, modifier = Modifier.size(48.mpx))
                }
            }
            if (playing) tv.own.owntv.player.ExoPreviewSurface(engine = previewEngine, modifier = Modifier.fillMaxSize())
            if (playing && previewState == tv.own.owntv.player.LivePreviewEngine.State.LOADING) OwnTVSpinner(sizeDp = 24)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.58f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
            Box(Modifier.fillMaxSize().drawBehind { drawInnerRing(Color.White.copy(alpha = 0.08f), 1.mpx.toPx(), r.toPx()) })
            if (channel != null) {
                Box(Modifier.align(Alignment.TopStart).padding(start = 20.mpx, top = 18.mpx)) { StageLiveBadge(stringResource(R.string.player_live)) }
                if (playing && streamChips.isNotEmpty()) {
                    Row(Modifier.align(Alignment.TopEnd).padding(end = 20.mpx, top = 18.mpx), horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
                        // Quality, frame rate and sound, as Live TV's preview shows them.
                        streamChips.filterNot { it.contains(':') || it.endsWith("Mbps") }.forEach { StageSpecChip(it) }
                    }
                }
            }
        }
        if (match == null) {
            Text(
                stringResource(if (event == null) R.string.tifo_sports_pick_game else R.string.tifo_sports_preview_hint),
                style = stageText(20, 500), color = StageColors.Muted,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            return@Column
        }
        Text(
            ProviderTags.parse(match.channel.name).name,
            style = stageText(30, 800), color = StageColors.Text,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        val time = rememberSystemTimeFormatter()
        val title = match.programmeTitle
        val start = match.programmeStartMs
        val stop = match.programmeStopMs
        if (title != null && start != null && stop != null) {
            Text(
                stringResource(R.string.tifo_sports_programme, time(start), time(stop), title),
                style = stageText(18, 500), color = StageColors.Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        val tags = qualityTags(quality)
        if (tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.mpx), verticalAlignment = Alignment.CenterVertically) {
                tags.forEach { StageTag(it) }
                if (quality?.measured == false) {
                    Text(stringResource(R.string.tifo_sports_quality_from_name), style = stageText(15, 500), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(
            stringResource(R.string.tifo_sports_play_hint),
            style = stageText(15, 500), color = StageColors.Dim,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "1080p", "50 FPS": a channel's quality as tags. */
@Composable
private fun qualityTags(quality: StreamQuality?): List<String> {
    if (quality == null) return emptyList()
    return listOfNotNull(
        quality.resolution,
        quality.fps?.let { stringResource(R.string.tifo_sports_fps, it) },
    )
}

/** A favourite team's mark beside its name. */
@Composable
private fun FavoriteStar(focused: Boolean) {
    val accent = stageAccent
    OwnTVIcon(OwnTVIcon.STAR, if (focused) accent.onAccent else accent.accent, Modifier.size(18.mpx), filled = true)
}

/** "Today", "Tomorrow", or the weekday and date: "Sat 18 Oct". */
@Composable
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.tifo_sports_today)
    today.plusDays(1) -> stringResource(R.string.tifo_sports_tomorrow)
    today.minusDays(1) -> stringResource(R.string.tifo_sports_yesterday)
    else -> dayDate(day)
}

/** The weekday and date: "Sat 18 Oct". */
@Composable
private fun dayDate(day: LocalDate): String {
    val format = rememberBestDateFormatter(DAY_SKELETON)
    return format(day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
}

/**
 * The schedule's days, yesterday to two weeks ahead, in place of the games. OK shows that day's
 * games, Back keeps the day.
 */
@Composable
private fun DayPicker(
    shown: LocalDate,
    today: LocalDate,
    gamesOn: (LocalDate) -> Int,
    onPick: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val accent = stageAccent
    val days = remember(today) {
        (-SportsSchedule.DAYS_BACK..SportsSchedule.DAYS_AHEAD).map { today.plusDays(it) }
    }
    val shownRow = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { shownRow.requestFocus() }
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(10.mpx)) {
        Text(
            stringResource(R.string.tifo_sports_pick_day),
            style = stageText(18, 500), color = StageColors.Muted,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        LazyColumn(
            state = rememberLazyListState(days.indexOf(shown).coerceAtLeast(0)),
            verticalArrangement = Arrangement.spacedBy(6.mpx),
        ) {
            items(days, key = { it.toEpochDay() }) { day ->
                val selected = day == shown
                StageRow(
                    onClick = { onPick(day) },
                    height = 72.mpx,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (selected) Modifier.focusRequester(shownRow).background(accent.accent.copy(alpha = 0.12f), RoundedCornerShape(StageRadii.Row)) else Modifier),
                ) { focused ->
                    val text = if (focused) accent.onAccent else StageColors.Text
                    Text(dayLabel(day, today), style = stageText(21, 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val games = gamesOn(day)
                    if (games > 0) {
                        Text(
                            java.text.NumberFormat.getIntegerInstance(locale).format(games),
                            style = stageText(18, 700),
                            color = if (focused) accent.onAccent else accent.accent,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** "NFL" above the league's games on the All tab. */
@Composable
private fun LeagueHeader(text: String) {
    Text(
        text,
        style = stageText(17, 800),
        color = StageColors.Dim,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 8.mpx, top = 14.mpx, bottom = 2.mpx),
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = stageText(20, 500),
        color = StageColors.Muted,
        maxLines = 3, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 12.mpx),
    )
}

/** "Lyon – Paris Saint-Germain" for a game, the event's own name otherwise. */
@Composable
private fun eventTitle(event: SportsEvent): String {
    val home = event.home
    val away = event.away
    return if (home != null && away != null) {
        stringResource(R.string.tifo_sports_versus, home.shortName, away.shortName)
    } else {
        event.name
    }
}

@Composable
private fun EventRow(
    event: SportsEvent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: (SportsTeam?) -> Boolean = { false },
) {
    val accent = stageAccent
    StageRow(
        onClick = onClick,
        height = 96.mpx,
        modifier = modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(accent.accent.copy(alpha = 0.12f), RoundedCornerShape(StageRadii.Row)) else Modifier),
    ) { focused ->
        val text = if (focused) accent.onAccent else StageColors.Text
        val dim = if (focused) accent.onAccent.copy(alpha = 0.8f) else StageColors.Dim
        Column(Modifier.width(150.mpx), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
            EventStatus(event, focused)
        }
        val home = event.home
        val away = event.away
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
            if (home != null && away != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                    TeamLogo(home.logoUrl)
                    if (isFavorite(home)) FavoriteStar(focused)
                    Text(home.shortName, style = stageText(21, 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val score = if (event.state == EventState.UPCOMING || home.score == null || away.score == null) {
                        stringResource(R.string.tifo_sports_vs)
                    } else {
                        stringResource(R.string.tifo_sports_score, home.score, away.score)
                    }
                    Text(score, style = stageText(21, 800), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(away.shortName, style = stageText(21, 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    if (isFavorite(away)) FavoriteStar(focused)
                    TeamLogo(away.logoUrl)
                }
            } else {
                Text(event.name, style = stageText(21, 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(stringResource(event.competition.labelRes), style = stageText(15, 600), color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EventStatus(event: SportsEvent, focused: Boolean) {
    val accent = stageAccent
    val dim = if (focused) accent.onAccent.copy(alpha = 0.8f) else StageColors.Muted
    when (event.state) {
        EventState.LIVE -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                Box(Modifier.size(10.mpx).background(LiveRed, CircleShape))
                Text(stringResource(R.string.tifo_sports_live), style = stageText(16, 800), color = if (focused) accent.onAccent else LiveRed, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (event.statusDetail.isNotBlank()) {
                Text(event.statusDetail, style = stageText(15, 600), color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        EventState.UPCOMING -> {
            val time = rememberSystemTimeFormatter()
            val day = rememberBestDateFormatter(DAY_SKELETON)
            Text(time(event.startMs), style = stageText(19, 700), color = if (focused) accent.onAccent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isToday(event.startMs)) stringResource(R.string.tifo_sports_today) else day(event.startMs),
                style = stageText(15, 600), color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        EventState.FINISHED -> {
            Text(
                event.statusDetail.ifBlank { stringResource(R.string.tifo_sports_final) },
                style = stageText(16, 700), color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TeamLogo(url: String?) {
    if (url == null) {
        Spacer(Modifier.size(36.mpx))
    } else {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(36.mpx))
    }
}

/** "🔍 All CBS channels": an action in the list under a game, as tall as a channel row's text. */
@Composable
private fun SportsActionRow(text: String, icon: OwnTVIcon, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = stageAccent
    StageRow(
        onClick = onClick,
        height = 64.mpx,
        modifier = modifier.fillMaxWidth(),
    ) { focused ->
        val color = if (focused) accent.onAccent else accent.accent
        OwnTVIcon(icon, color, Modifier.size(22.mpx))
        Text(text, style = stageText(19, 700), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** Focuses this element when [target] names [key], once it is on screen, then calls [onDone]. */
@Composable
private fun Modifier.refocusWhen(key: String, target: String?, onDone: () -> Unit): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(target) {
        if (target != key) return@LaunchedEffect
        withFrameNanos { }
        runCatching { requester.requestFocus() }
        onDone()
    }
    return focusRequester(requester)
}

private fun eventKey(event: SportsEvent): String = "e:" + event.competition.name + event.id

private fun searchKey(broadcaster: String): String = "q:$broadcaster"

private fun channelKey(channelId: Long): String = "c:$channelId"

private fun tabKey(competition: Competition): String = "tab:" + competition.name

/** The tab the list under it belongs to. */
private fun tabKey(state: SportsUiState): String = when {
    state.myTeams -> MY_TEAMS_TAB_KEY
    state.filter == null -> ALL_TAB_KEY
    else -> tabKey(state.filter)
}

private const val ALL_TAB_KEY = "tab:all"

private const val MY_TEAMS_TAB_KEY = "tab:mine"

@Composable
private fun ChannelRow(
    match: SportsChannelMatch,
    quality: StreamQuality?,
    recordingSet: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = stageAccent
    val time = rememberSystemTimeFormatter()
    StageRow(
        onClick = onClick,
        height = 84.mpx,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocused() },
    ) { focused ->
        val text = if (focused) accent.onAccent else StageColors.Text
        val dim = if (focused) accent.onAccent.copy(alpha = 0.8f) else StageColors.Dim
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.mpx)) {
            Text(match.channel.name, style = stageText(20, 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val title = match.programmeTitle
            val start = match.programmeStartMs
            val stop = match.programmeStopMs
            if (title != null && start != null && stop != null) {
                val programme = stringResource(R.string.tifo_sports_programme, time(start), time(stop), title)
                Text(
                    if (recordingSet) stringResource(R.string.tifo_sports_recording_set, programme) else programme,
                    style = stageText(15, 500), color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val onAccent = if (focused) accent.onAccent else null
        qualityTags(quality).forEach { StageTag(it, onAccent = onAccent) }
        StageTag(
            stringResource(
                when {
                    match.score >= SportsMatching.BEST_MATCH -> R.string.tifo_sports_match_best
                    match.score > SportsMatching.BROADCASTER -> R.string.tifo_sports_match_likely
                    else -> R.string.tifo_sports_match_broadcaster
                },
            ),
            onAccent = onAccent,
        )
    }
}

private fun isToday(ms: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

/** Weekday and day: "Sat 11 Oct". */
private const val DAY_SKELETON = "EEEdMMM"

/** How long the cursor rests on a channel before it plays, as in Live TV. */
private const val PREVIEW_SETTLE_MS = 700L

/** How long a preview is watched for the stream's resolution and frame rate. */
private const val MEASURE_MS = 30_000L

private val CHANNEL_INDENT = 40.mpx

/** The day button, where the cursor returns when the day picker closes. */
private const val DAY_BUTTON_KEY = "day"

/** The first game listed, where the cursor goes after "Next games". */
private const val FIRST_EVENT_KEY = "first"

/** The Teams button, where the cursor returns when the favourites picker closes. */
private const val TEAMS_BUTTON_KEY = "teams"
