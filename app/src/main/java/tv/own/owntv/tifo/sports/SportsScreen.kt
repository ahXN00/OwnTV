package tv.own.owntv.tifo.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import java.util.Calendar
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePill
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageTab
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

private val LiveRed = Color(0xFFFF5A5A)

/**
 * The Sports tab: live and upcoming games of the followed competitions on the left, and for the
 * game picked, the playlist's channels that show it on the right. OK on a channel plays it; the
 * Record button beside a channel whose guide shows the game later records that programme.
 */
@Composable
fun SportsScreen(
    onPlayChannel: (ChannelEntity) -> Unit,
    contentStart: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val vm = org.koin.androidx.compose.koinViewModel<SportsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val recordingRows by vm.recordingRows.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.refreshWhileVisible() }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val count = { n: Int -> java.text.NumberFormat.getIntegerInstance(locale).format(n) }

    Column(
        modifier.padding(start = contentStart, end = 48.mpx, top = 40.mpx, bottom = 24.mpx),
        verticalArrangement = Arrangement.spacedBy(18.mpx),
    ) {
        var jellyfinStep by remember { mutableStateOf(JellyfinStep.NONE) }
        var jellyfinUrl by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
            Text(
                stringResource(R.string.tifo_sports_title),
                style = stageText(40, 800),
                color = StageColors.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StagePill(
                text = stringResource(if (state.jellyfin.isSet) R.string.tifo_jellyfin_on else R.string.tifo_jellyfin_off),
                onClick = { jellyfinStep = JellyfinStep.URL },
                icon = OwnTVIcon.REC,
                dimmed = !state.jellyfin.isSet,
            )
        }
        state.notice?.let { notice ->
            Text(
                noticeText(notice),
                style = stageText(17, 700),
                color = if (notice is SportsNotice.Failed) LiveRed else stageAccent.accent,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when (jellyfinStep) {
            JellyfinStep.NONE -> Unit
            JellyfinStep.URL -> TextInputDialog(
                title = stringResource(R.string.tifo_jellyfin_title),
                initial = state.jellyfin.url,
                label = stringResource(R.string.tifo_jellyfin_url),
                hint = stringResource(R.string.tifo_jellyfin_url_hint),
                confirmLabel = stringResource(R.string.tifo_jellyfin_next),
                onConfirm = { url ->
                    jellyfinUrl = url
                    if (url.isBlank()) {
                        vm.setJellyfin("", "")
                        jellyfinStep = JellyfinStep.NONE
                    } else {
                        jellyfinStep = JellyfinStep.KEY
                    }
                },
                onDismiss = { jellyfinStep = JellyfinStep.NONE },
            )
            JellyfinStep.KEY -> TextInputDialog(
                title = stringResource(R.string.tifo_jellyfin_title),
                initial = state.jellyfin.apiKey,
                label = stringResource(R.string.tifo_jellyfin_key),
                hint = stringResource(R.string.tifo_jellyfin_key_hint),
                confirmLabel = stringResource(R.string.tifo_jellyfin_connect),
                allowBlank = false,
                onConfirm = { key ->
                    vm.setJellyfin(jellyfinUrl, key)
                    jellyfinStep = JellyfinStep.NONE
                },
                onDismiss = { jellyfinStep = JellyfinStep.NONE },
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
            item {
                StageTab(
                    label = stringResource(R.string.tifo_sports_all),
                    count = count(state.events.size),
                    selected = state.filter == null,
                    onClick = { vm.setFilter(null) },
                )
            }
            items(Competition.entries.toList(), key = { it.name }) { c ->
                StageTab(
                    label = stringResource(c.labelRes),
                    count = count(state.events.count { it.competition == c }),
                    selected = state.filter == c,
                    onClick = { vm.setFilter(c) },
                )
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.mpx)) {
            Box(Modifier.weight(0.56f).fillMaxHeight()) {
                val events = state.visibleEvents
                when {
                    state.loading -> Message(stringResource(R.string.tifo_sports_loading))
                    events.isEmpty() && state.failed -> Message(stringResource(R.string.tifo_sports_failed))
                    events.isEmpty() -> Message(stringResource(R.string.tifo_sports_no_games))
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                        items(events, key = { it.competition.name + it.id }) { event ->
                            EventRow(event, selected = event.id == state.selected?.id, onClick = { vm.select(event) })
                        }
                    }
                }
            }
            Column(
                Modifier
                    .weight(0.44f)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(StageRadii.Sheet))
                    .padding(18.mpx),
                verticalArrangement = Arrangement.spacedBy(10.mpx),
            ) {
                val selected = state.selected
                if (selected == null) {
                    Message(stringResource(R.string.tifo_sports_pick_game))
                    return@Column
                }
                Text(
                    eventTitle(selected),
                    style = stageText(24, 700),
                    color = StageColors.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.tifo_sports_channels),
                    style = stageText(16, 700),
                    color = StageColors.Dim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                when {
                    state.matching -> Message(stringResource(R.string.tifo_sports_matching))
                    state.matches.isEmpty() -> Message(stringResource(R.string.tifo_sports_no_channels))
                    else -> {
                        val now = System.currentTimeMillis()
                        val canRecord = { match: SportsChannelMatch ->
                            selected.state == EventState.UPCOMING && (match.programmeStartMs ?: 0L) > now
                        }
                        if (state.matches.any(canRecord)) {
                            Text(
                                stringResource(if (state.jellyfin.isSet) R.string.tifo_sports_record_hint_jellyfin else R.string.tifo_sports_record_hint),
                                style = stageText(15, 500),
                                color = StageColors.Muted,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
                            items(state.matches, key = { it.channel.id }) { match ->
                                val recording: Any? = if (state.jellyfin.isSet) {
                                    vm.jellyfinTimerFor(match, state.jellyfinTimers)
                                } else {
                                    vm.recordingFor(match, recordingRows)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                                    ChannelRow(match, recordingSet = recording != null, onClick = { onPlayChannel(match.channel) }, modifier = Modifier.weight(1f))
                                    if (recording != null || canRecord(match)) {
                                        StageButton(
                                            text = null,
                                            onClick = { vm.toggleRecording(match) },
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
            }
        }
    }
}

private enum class JellyfinStep { NONE, URL, KEY }

@Composable
private fun noticeText(notice: SportsNotice): String = when (notice) {
    is SportsNotice.Connected -> stringResource(R.string.tifo_jellyfin_connected, notice.serverName)
    SportsNotice.Scheduled -> stringResource(R.string.tifo_jellyfin_scheduled)
    is SportsNotice.Failed -> stringResource(
        when (notice.failure) {
            JellyfinFailure.UNREACHABLE -> R.string.tifo_jellyfin_unreachable
            JellyfinFailure.NO_CHANNEL -> R.string.tifo_jellyfin_no_channel
            JellyfinFailure.NO_PROGRAMME -> R.string.tifo_jellyfin_no_programme
        },
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
private fun EventRow(event: SportsEvent, selected: Boolean, onClick: () -> Unit) {
    val accent = stageAccent
    StageRow(
        onClick = onClick,
        height = 96.mpx,
        modifier = Modifier
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
                    Text(home.shortName, style = stageText(21, 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val score = if (event.state == EventState.UPCOMING || home.score == null || away.score == null) {
                        stringResource(R.string.tifo_sports_vs)
                    } else {
                        stringResource(R.string.tifo_sports_score, home.score, away.score)
                    }
                    Text(score, style = stageText(21, 800), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(away.shortName, style = stageText(21, 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
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

@Composable
private fun ChannelRow(match: SportsChannelMatch, recordingSet: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = stageAccent
    val time = rememberSystemTimeFormatter()
    StageRow(onClick = onClick, height = 84.mpx, modifier = modifier.fillMaxWidth()) { focused ->
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
        StageTag(
            stringResource(
                when {
                    match.score >= SportsMatching.BEST_MATCH -> R.string.tifo_sports_match_best
                    match.score > SportsMatching.BROADCASTER -> R.string.tifo_sports_match_likely
                    else -> R.string.tifo_sports_match_broadcaster
                },
            ),
            onAccent = if (focused) accent.onAccent else null,
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
