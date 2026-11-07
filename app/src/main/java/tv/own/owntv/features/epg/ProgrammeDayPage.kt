package tv.own.owntv.features.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.ProviderTags
import tv.own.owntv.core.parser.EpgDetails
import tv.own.owntv.features.live.LivePlate
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/** The facts line of a programme: "S2 E5 · 2023 · FSK 12 · 95 min" — only what the feed carries. */
internal fun programmeFacts(d: EpgDetails, lengthText: String?): List<String> =
    listOfNotNull(d.episode, d.year?.toString(), d.rating, lengthText)

/** Where a programme sits against the clock: its group on the page and the actions it offers. */
internal enum class ProgrammeTime { PAST, NOW, LATER }

internal fun programmeTime(p: EpgProgrammeEntity, now: Long): ProgrammeTime = when {
    p.stopMs <= now -> ProgrammeTime.PAST
    p.startMs <= now -> ProgrammeTime.NOW
    else -> ProgrammeTime.LATER
}

/**
 * The row the page opens on: the programme asked for (matched by start time — a row only the provider
 * gave has no id), else the one on [now], else the next one to start, else the first.
 */
internal fun openingIndex(list: List<EpgProgrammeEntity>, wantedStartMs: Long?, now: Long): Int {
    if (list.isEmpty()) return 0
    wantedStartMs?.let { s -> list.indexOfFirst { it.startMs == s }.takeIf { it >= 0 }?.let { return it } }
    list.indexOfFirst { programmeTime(it, now) == ProgrammeTime.NOW }.takeIf { it >= 0 }?.let { return it }
    list.indexOfFirst { it.startMs > now }.takeIf { it >= 0 }?.let { return it }
    return 0
}

internal enum class PageAction { WATCH, WATCH_FROM_START, RECORD, RECORD_SERIES, RECORD_FROM_CATCHUP, REMIND }

/** The buttons an open programme shows, by where it sits; ones the channel cannot do are dimmed, not hidden. */
internal fun pageActions(time: ProgrammeTime): List<PageAction> = when (time) {
    ProgrammeTime.PAST -> listOf(PageAction.WATCH_FROM_START, PageAction.RECORD_FROM_CATCHUP)
    ProgrammeTime.NOW -> listOf(PageAction.WATCH, PageAction.WATCH_FROM_START, PageAction.RECORD, PageAction.RECORD_SERIES)
    ProgrammeTime.LATER -> listOf(PageAction.REMIND, PageAction.RECORD, PageAction.RECORD_SERIES)
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** One line of the page's list: a group heading or a programme. */
private sealed interface PageLine {
    data class Heading(val text: String) : PageLine
    data class Programme(val index: Int) : PageLine
}

/**
 * Program details as a full page: one channel's day, every programme one line, the one under the cursor
 * opened in place with its facts, categories, whole description and what it can do. Yesterday, Today and
 * Tomorrow at the top; today is grouped Earlier · On now · Later.
 *
 * The page owns the remote, as the film details page does, and draws its own highlight: ▲▼ programmes,
 * ▶ or OK into the open programme's buttons, ◀▶ between them, OK runs one, ▲▼ or Back leave them; ▲ from
 * the first programme reaches the days, where ◀▶ change the day. Back closes. Keys act on release so the
 * release of a press that closes the page cannot land on the screen behind it.
 */
@Composable
internal fun ProgrammeDayPage(
    vm: EpgViewModel,
    channel: ChannelEntity,
    /** Open on this programme (its start time, on the user's clock); null opens on the one on now. */
    focusStartMs: Long?,
    onWatch: () -> Unit,
    onPlayFromStart: (EpgProgrammeEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val today = remember { localMidnight(System.currentTimeMillis()) }
    var day by remember { mutableIntStateOf(focusStartMs?.let { (((localMidnight(it) - today) / DAY_MS).toInt()).coerceIn(-1, 1) } ?: 0) }
    var wanted by remember { mutableStateOf(focusStartMs) }
    val dayStart = today + day * DAY_MS
    val loaded by produceState<List<EpgProgrammeEntity>?>(null, dayStart) { value = null; value = vm.dayProgrammes(channel, dayStart) }
    val list = loaded.orEmpty()

    var index by remember { mutableIntStateOf(0) }
    var inActions by remember { mutableStateOf(false) }
    var action by remember { mutableIntStateOf(0) }
    var onDays by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        if (loaded == null) return@LaunchedEffect
        index = openingIndex(list, wanted, now); wanted = null; inActions = false
    }

    val earlier = stringResource(R.string.content_programmes_earlier)
    val onNow = stringResource(R.string.home_row_on_now)
    val later = stringResource(R.string.content_programmes_later)
    val lines = remember(list, day, now) {
        buildList {
            var last: ProgrammeTime? = null
            list.forEachIndexed { i, p ->
                val t = programmeTime(p, now)
                if (day == 0 && t != last) {
                    add(PageLine.Heading(when (t) { ProgrammeTime.PAST -> earlier; ProgrammeTime.NOW -> onNow; ProgrammeTime.LATER -> later }))
                    last = t
                }
                add(PageLine.Programme(i))
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(index, lines) {
        val line = lines.indexOfFirst { it is PageLine.Programme && it.index == index }
        if (line >= 0) runCatching { listState.animateScrollToItem((line - 2).coerceAtLeast(0)) }
    }

    // The open programme's buttons, filled in by the open row (they read the guide's live state).
    var runAction by remember { mutableStateOf<(Int) -> Boolean>({ false }) }
    var actionCount by remember { mutableIntStateOf(0) }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { focus.requestFocus() } }
    val page = tv.own.owntv.ui.theme.stageTone(Color(0xFF070B0E), tv.own.owntv.ui.theme.LocalStagePalette.current.page)
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true, stageScaled = false) {
        Box(
            Modifier.fillMaxSize().background(page)
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    val up = e.type == KeyEventType.KeyUp
                    if (e.type != KeyEventType.KeyDown && !up) return@onPreviewKeyEvent false
                    val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if ((isOk || e.key == Key.Back) && !up) return@onPreviewKeyEvent true
                    if (!isOk && e.key != Key.Back && up) return@onPreviewKeyEvent false
                    when {
                        e.key == Key.Back -> when {
                            inActions -> inActions = false
                            onDays -> onDays = false
                            else -> onDismiss()
                        }
                        isOk -> when {
                            onDays -> onDays = false
                            !inActions -> if (list.isNotEmpty() && actionCount > 0) { inActions = true; action = 0 }
                            else -> runAction(action)
                        }
                        e.key == Key.DirectionUp -> when {
                            inActions -> inActions = false
                            onDays -> Unit
                            index == 0 || list.isEmpty() -> onDays = true
                            else -> index--
                        }
                        e.key == Key.DirectionDown -> when {
                            inActions -> inActions = false
                            onDays -> onDays = false
                            else -> index = (index + 1).coerceAtMost((list.size - 1).coerceAtLeast(0))
                        }
                        // Physical keys, as the guide's timeline: the days run left to right in every language.
                        e.key == Key.DirectionRight -> when {
                            onDays -> if (day < 1) day++
                            !inActions -> if (list.isNotEmpty() && actionCount > 0) { inActions = true; action = 0 }
                            else -> action = (action + 1).coerceAtMost(actionCount - 1)
                        }
                        e.key == Key.DirectionLeft -> when {
                            onDays -> if (day > -1) day--
                            inActions -> if (action == 0) inActions = false else action--
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .focusable(),
        ) {
            Column(Modifier.fillMaxSize().padding(start = 120.mpx, end = 120.mpx, top = 56.mpx, bottom = 40.mpx)) {
                PageHeader(channel, day, onDays)
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 24.mpx)) {
                    when {
                        loaded == null -> tv.own.owntv.ui.components.OwnTVSpinner(sizeDp = 28, modifier = Modifier.align(Alignment.Center))
                        list.isEmpty() -> Text(
                            stringResource(R.string.content_epg_no_programme), style = stageText(20, 500), color = StageColors.Muted,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 200.mpx)) {
                            items(lines.size, key = { i -> lines[i].let { if (it is PageLine.Programme) "p${it.index}-${list[it.index].startMs}" else "h$i" } }) { i ->
                                when (val line = lines[i]) {
                                    is PageLine.Heading -> Text(
                                        line.text.uppercase(androidx.compose.ui.text.intl.Locale.current.platformLocale),
                                        style = stageText(14, 800, 0.14.em), color = StageColors.Dim,
                                        modifier = Modifier.padding(start = 8.mpx, top = 18.mpx, bottom = 8.mpx),
                                    )
                                    is PageLine.Programme -> {
                                        val p = list[line.index]
                                        val open = line.index == index && !onDays
                                        if (open) {
                                            OpenProgramme(
                                                vm, channel, p, now,
                                                inActions = inActions, action = action,
                                                onActions = { count, run -> actionCount = count; runAction = run },
                                                onWatch = { onDismiss(); onWatch() },
                                                onPlayFromStart = { onDismiss(); onPlayFromStart(p) },
                                            )
                                        } else {
                                            ProgrammeLine(p, now, highlighted = line.index == index)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                PageHints(inActions, onDays)
            }
        }
    }
}

@Composable
private fun PageHeader(channel: ChannelEntity, day: Int, onDays: Boolean) {
    val a = stageAccent
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
        LivePlate(channel.displayLogoUrl, 84.mpx, 60.mpx)
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(channel.number?.toString(), ProviderTags.parse(channel.name).name).joinToString(" · "),
                style = stageText(32, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.content_programme_details), style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        listOf(-1 to R.string.content_epg_yesterday, 0 to R.string.content_epg_today, 1 to R.string.content_epg_tomorrow).forEach { (d, res) ->
            val chosen = d == day
            val bg = when {
                chosen && onDays -> a.accent
                chosen -> Color(0xFF1D2A1D)
                else -> Color(0xFF141D21)
            }
            val fg = when {
                chosen && onDays -> a.onAccent
                chosen -> a.accent
                else -> StageColors.Muted
            }
            Text(
                stringResource(res), style = stageText(16, 700), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 18.mpx, vertical = 9.mpx),
            )
        }
    }
}

/** A programme on one line: times, title, the start of its description, a LIVE mark on the one on now. */
@Composable
private fun ProgrammeLine(p: EpgProgrammeEntity, now: Long, highlighted: Boolean) {
    val formatTime = rememberSystemTimeFormatter()
    val time = programmeTime(p, now)
    val titleColor = if (time == ProgrammeTime.PAST) StageColors.Dim else StageColors.Text
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.mpx)
            .background(if (time == ProgrammeTime.NOW) Color(0xFF111B14) else Color.Transparent, RoundedCornerShape(16.mpx))
            .then(if (highlighted) Modifier.border(2.mpx, stageAccent.accent.copy(alpha = 0.5f), RoundedCornerShape(16.mpx)) else Modifier)
            .padding(horizontal = 20.mpx, vertical = 12.mpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.mpx),
    ) {
        Column(Modifier.width(110.mpx)) {
            Text(formatTime(p.startMs), style = stageText(18, 700), color = if (time == ProgrammeTime.PAST) StageColors.Dim else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatTime(p.stopMs), style = stageText(13, 600), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f)) {
            Text(p.title, style = stageText(20, 700).copy(textDirection = TextDirection.Content), color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            p.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = stageText(15, 400).copy(textDirection = TextDirection.Content), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.mpx))
            }
            if (time == ProgrammeTime.NOW) StageProgress(progressOf(p, now), Modifier.width(340.mpx).padding(top = 8.mpx))
        }
        if (time == ProgrammeTime.NOW) tv.own.owntv.ui.stage.StageLiveBadge(stringResource(R.string.player_live))
    }
}

/** The programme under the cursor, opened: facts, categories, the whole description and its buttons. */
@Composable
private fun OpenProgramme(
    vm: EpgViewModel,
    channel: ChannelEntity,
    p: EpgProgrammeEntity,
    now: Long,
    inActions: Boolean,
    action: Int,
    onActions: (count: Int, run: (Int) -> Boolean) -> Unit,
    onWatch: () -> Unit,
    onPlayFromStart: () -> Unit,
) {
    val a = stageAccent
    val formatTime = rememberSystemTimeFormatter()
    val time = programmeTime(p, now)
    val actions = guideProgrammeActions(
        vm, channel, p, now,
        onWatch = onWatch, onPlayFromStart = onPlayFromStart,
        onPickEpg = {}, onEpgOffset = {}, autoMatch = false,
    )
    val buttons = pageActions(time).map { kind ->
        when (kind) {
            PageAction.WATCH -> Triple(stringResource(R.string.content_epg_watch_channel), true, actions.onWatchChannel)
            PageAction.WATCH_FROM_START -> Triple(stringResource(R.string.content_epg_watch_start), actions.onWatchFromStart != null, { actions.onWatchFromStart?.invoke(); Unit })
            PageAction.RECORD -> Triple(actions.recordLabel, actions.onRecord != null, { actions.onRecord?.invoke(); Unit })
            PageAction.RECORD_SERIES -> Triple(stringResource(R.string.recording_record_series), actions.onSeries != null, { actions.onSeries?.invoke(); Unit })
            PageAction.RECORD_FROM_CATCHUP -> Triple(stringResource(R.string.recording_from_archive), actions.onRecordFromCatchup != null, { actions.onRecordFromCatchup?.invoke(); Unit })
            PageAction.REMIND -> Triple(
                stringResource(if (actions.reminderSet) R.string.content_reminder_set else R.string.content_remind_me),
                actions.onReminder != null, { actions.onReminder?.invoke(); Unit },
            )
        }
    }
    val checked = pageActions(time).map { (it == PageAction.REMIND && actions.reminderSet) || (it == PageAction.RECORD_SERIES && actions.seriesActive) }
    // Read at the moment OK is pressed: after Record the same button stops, and must not record twice.
    val latest = androidx.compose.runtime.rememberUpdatedState(buttons)
    LaunchedEffect(buttons.size) {
        onActions(buttons.size) { i -> latest.value.getOrNull(i)?.takeIf { it.second }?.let { it.third(); true } ?: false }
    }
    // The stored copy carries the synopsis and details; the times stay this row's (they carry the EPG offset).
    val full by produceState<EpgProgrammeEntity?>(null, p.id) { value = if (p.description == null && p.id > 0) vm.programmeFull(p.id) else null }
    val shown = full?.copy(startMs = p.startMs, stopMs = p.stopMs) ?: p
    val length = shown.details.lengthMin?.let { stringResource(R.string.player_duration_minutes, it) }
    val facts = listOfNotNull(
        stringResource(R.string.content_live_now).takeIf { time == ProgrammeTime.NOW },
        stringResource(R.string.content_live_time_range_plain, formatTime(shown.startMs), formatTime(shown.stopMs)),
    ) + programmeFacts(shown.details, length)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.mpx)
            .background(Color(0xFF0F1619), RoundedCornerShape(20.mpx))
            .border(2.mpx, a.accent, RoundedCornerShape(20.mpx))
            .padding(horizontal = 24.mpx, vertical = 20.mpx),
        horizontalArrangement = Arrangement.spacedBy(20.mpx),
    ) {
        Column(Modifier.width(110.mpx)) {
            Text(formatTime(shown.startMs), style = stageText(18, 700), color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatTime(shown.stopMs), style = stageText(13, 600), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f)) {
            Text(shown.title, style = stageText(30, 800, (-0.5).mpxSp).copy(textDirection = TextDirection.Content), color = StageColors.Text)
            Text(facts.joinToString(" · "), style = stageText(16, 600), color = StageColors.Muted, modifier = Modifier.padding(top = 6.mpx))
            if (time == ProgrammeTime.NOW) StageProgress(progressOf(shown, now), Modifier.width(560.mpx).padding(top = 10.mpx))
            val tags = shown.details.categoryList
            if (tags.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 12.mpx), horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                    tags.forEach { StageTag(it) }
                }
            }
            shown.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, style = stageText(18, 400).copy(lineHeight = (18 * 1.55f).mpxSp, textDirection = TextDirection.Content),
                    color = StageColors.ItemText, modifier = Modifier.padding(top = 12.mpx).widthIn(max = 1300.mpx),
                )
            }
            FlowRow(Modifier.padding(top = 16.mpx), horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                buttons.forEachIndexed { i, (label, enabled, _) ->
                    val on = inActions && i == action
                    Text(
                        (if (checked[i]) "✓ " else "") + label,
                        style = stageText(16, 700), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = when { on -> a.onAccent; enabled -> StageColors.Text; else -> StageColors.Dim },
                        modifier = Modifier
                            .background(if (on) a.accent else Color(0xFF1A2427), RoundedCornerShape(12.mpx))
                            .padding(horizontal = 18.mpx, vertical = 10.mpx),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageHints(inActions: Boolean, onDays: Boolean) {
    val hints = when {
        inActions -> listOf("◀ ▶" to stringResource(R.string.settings_key_actions), stringResource(R.string.common_back) to stringResource(R.string.content_key_programmes))
        onDays -> listOf("◀ ▶" to stringResource(R.string.content_epg_today), "▼" to stringResource(R.string.content_key_programmes), stringResource(R.string.common_back) to stringResource(R.string.content_close))
        else -> listOf("▲ ▼" to stringResource(R.string.content_key_programmes), "▶" to stringResource(R.string.settings_key_actions), stringResource(R.string.common_back) to stringResource(R.string.content_close))
    }
    tv.own.owntv.ui.stage.StageKeyHints(hints, textSize = 16, modifier = Modifier.padding(top = 14.mpx))
}

private fun progressOf(p: EpgProgrammeEntity, now: Long): Float =
    if (p.stopMs <= p.startMs) 0f else ((now - p.startMs).toFloat() / (p.stopMs - p.startMs)).coerceIn(0f, 1f)
