package tv.own.owntv.features.epg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.ui.components.ErrorState
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.theme.OwnTVTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CHANNEL_COL = 176.dp
private val ROW_HEIGHT = 64.dp
private val PX_PER_MIN = 4.dp
private const val SLOT_MIN = 30

private fun clock(ms: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
private fun minutesWide(fromMs: Long, toMs: Long): Dp = (((toMs - fromMs) / 60_000L).toInt().coerceAtLeast(0) * PX_PER_MIN.value).dp

/**
 * The full EPG guide: a time × channel grid. Channel labels are pinned on the left; every channel row
 * and the time axis share one horizontal scroll state, so moving the D-pad across programmes scrolls
 * the whole guide in lock-step. Picking a programme opens its details.
 */
@Composable
fun EpgScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: () -> Unit = {},
    onAddEpg: () -> Unit = {},
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
) {
    val vm: EpgViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    val hScroll = rememberScrollState()
    val rowListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val firstCell = remember { FocusRequester() }
    val tunedCell = remember { FocusRequester() }
    var detail by remember { mutableStateOf<Pair<ChannelEntity, EpgProgrammeEntity>?>(null) }
    // The pending focus target onEnter routes to: our own restore requests cross into this group
    // from outside, so onEnter must cooperate or it would hijack them to the first channel.
    var pendingEnter by remember { mutableStateOf<FocusRequester?>(null) }

    // No BackHandler here: the Guide is a top-level section, so Back is the shell's job (content →
    // sidebar → exit dialog). A screen-level handler would swallow Back forever and block app exit.
    LaunchedEffect(Unit) { vm.load() } // reload from DB each time the guide is opened
    LaunchedEffect(state.loading, state.channels.isNotEmpty()) {
        // Auto-focus the grid once it's actually composed (while state.loading the spinner branch
        // shows instead, so the cells aren't attached yet) — but never while typing a search, and
        // never when returning from playback (the tuned-channel restore below handles that).
        if (!state.loading && state.channels.isNotEmpty() && query.isBlank() && !restoreFocus) {
            kotlinx.coroutines.delay(80)
            // tunedCell fallback: when the last-tuned channel IS row 0, firstCell isn't attached.
            if (runCatching { firstCell.requestFocus() }.isFailure) runCatching { tunedCell.requestFocus() }
        }
    }

    // Back from a channel tuned in the guide: scroll to and refocus that channel's row. Must wait
    // for the reload (vm.load() runs on every mount) — while state.loading the grid isn't composed
    // at all (spinner branch), so a requestFocus would silently fail and burn the restore flag.
    LaunchedEffect(restoreFocus, state.loading, state.channels.size) {
        if (!restoreFocus || state.loading || state.channels.isEmpty()) return@LaunchedEffect
        val idx = vm.lastTunedChannelId?.let { id -> state.channels.indexOfFirst { it.id == id } } ?: -1
        val target = if (idx >= 0) tunedCell else firstCell
        if (idx >= 0) runCatching { rowListState.scrollToItem(idx) }
        pendingEnter = target
        kotlinx.coroutines.delay(80)
        runCatching { target.requestFocus() }
        onRestored()
    }

    // Closing the programme-detail dialog: put focus back into the guide (it would otherwise die
    // with the dialog and fall to the sidebar).
    var hadDetail by remember { mutableStateOf(false) }
    LaunchedEffect(detail) {
        if (detail != null) {
            hadDetail = true
        } else if (hadDetail) {
            hadDetail = false
            pendingEnter = firstCell
            kotlinx.coroutines.delay(80)
            runCatching { firstCell.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            // Entry from the sidebar lands on the first channel — unless a restore is pending
            // (back from playback / dialog close), which onEnter routes to instead of hijacking.
            // onEnter only fires for entries from OUTSIDE the group — search bar / refresh / back
            // are inside it, so moving up from the grid to them never re-triggers this.
            .focusProperties {
                onEnter = {
                    val target = pendingEnter ?: firstCell
                    pendingEnter = null
                    // tunedCell fallback: when the last-tuned channel IS row 0, firstCell isn't attached.
                    if (runCatching { target.requestFocus() }.isFailure) runCatching { tunedCell.requestFocus() }
                }
            }
            .focusGroup()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        // Header: back + title + date + refresh
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusableSurface(onClick = onBack, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), contentAlignment = Alignment.Center) { _ ->
                OwnTVIcon(OwnTVIcon.BACK, tint = colors.onSurface, modifier = Modifier.size(20.dp))
            }
            Text("TV Guide", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            if (state.windowStart > 0) {
                Text(
                    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(state.windowStart)),
                    style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
        }
        if (state.stats != null) {
            Spacer(Modifier.height(4.dp))
            Text(state.stats!!, style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
        Spacer(Modifier.height(10.dp))
        SearchBar(
            query = query,
            onQueryChange = vm::setQuery,
            placeholder = "Search guide channels…",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        when {
            state.loading -> CenterBox { OwnTVSpinner(sizeDp = 56) }
            // No EPG feed added yet → guide it can't fill. Point the user to EPG Sources.
            !state.hasEpgSources && state.channels.isEmpty() -> CenterBox {
                Text("No EPG added.", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add an EPG (XMLTV) source to fill the guide with programmes.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                OwnTVButton("Add EPG", onClick = onAddEpg, icon = OwnTVIcon.ADD)
            }
            state.channels.isEmpty() -> CenterBox {
                Text(state.message ?: "No guide.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            else -> {
                // Time axis (shares hScroll with the rows below).
                val slots = ((state.windowEnd - state.windowStart) / (SLOT_MIN * 60_000L)).toInt()
                Row {
                    Spacer(Modifier.width(CHANNEL_COL))
                    Row(Modifier.horizontalScroll(hScroll)) {
                        for (i in 0 until slots) {
                            val slotMs = state.windowStart + i * SLOT_MIN * 60_000L
                            Text(
                                clock(slotMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width((SLOT_MIN * PX_PER_MIN.value).dp).padding(start = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn(state = rowListState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(state.channels, key = { _, ch -> ch.id }) { index, channel ->
                        GuideChannelRow(
                            vm = vm,
                            channel = channel,
                            windowStart = state.windowStart,
                            windowEnd = state.windowEnd,
                            now = state.now,
                            hScroll = hScroll,
                            labelFocus = when {
                                channel.id == vm.lastTunedChannelId -> tunedCell
                                index == 0 -> firstCell
                                else -> null
                            },
                            onTune = { vm.play(channel); onFullscreen() },
                            onOpen = { detail = channel to it },
                        )
                    }
                }
            }
        }
    }

    detail?.let { (channel, p) ->
        ProgrammeDetailDialog(
            channelName = channel.name,
            programme = p,
            onWatch = { detail = null; vm.play(channel); onFullscreen() },
            onDismiss = { detail = null },
        )
    }
}

/**
 * One guide row: pinned tunable channel label + lazily loaded programme strip. Programmes are fetched
 * from the DB only when the row scrolls into view (indexed query + VM cache), so the guide can list
 * every channel without holding the whole day's data in memory.
 */
@Composable
private fun GuideChannelRow(
    vm: EpgViewModel,
    channel: ChannelEntity,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    hScroll: androidx.compose.foundation.ScrollState,
    labelFocus: FocusRequester?,
    onTune: () -> Unit,
    onOpen: (EpgProgrammeEntity) -> Unit,
) {
    val colors = OwnTVTheme.colors
    // Cache peek as the initial value → rows scrolled back into view render instantly, no flash.
    val programmes by produceState(initialValue = vm.cachedProgrammes(channel), channel.id, windowStart) {
        if (value == null) value = vm.programmesFor(channel)
    }
    Row {
        // Pinned channel label — press OK to tune straight to the channel.
        FocusableSurface(
            onClick = onTune,
            modifier = Modifier.width(CHANNEL_COL).height(ROW_HEIGHT).padding(end = 6.dp)
                .then(if (labelFocus != null) Modifier.focusRequester(labelFocus) else Modifier),
            shape = RoundedCornerShape(10.dp),
            unfocusedContainerColor = colors.surfaceContainerHigh,
            contentAlignment = Alignment.CenterStart,
        ) { focused ->
            Text(
                channel.number?.let { "$it  ${channel.name}" } ?: channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) colors.primary else colors.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        // Scrollable programme strip (shared hScroll); full-width placeholder while loading keeps
        // every row the same total width so the lock-step scroll stays aligned.
        Row(Modifier.horizontalScroll(hScroll)) {
            val progs = programmes
            if (progs == null) {
                Spacer(Modifier.width(minutesWide(windowStart, windowEnd)).height(ROW_HEIGHT))
            } else {
                ProgrammeStrip(progs, windowStart, windowEnd, now, onOpen)
            }
        }
    }
}

/** Lays a channel's programmes end-to-end across the window, with gap/trailing spacers so every row is the same total width (keeping the shared scroll aligned). */
@Composable
private fun ProgrammeStrip(
    programmes: List<EpgProgrammeEntity>,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    onOpen: (EpgProgrammeEntity) -> Unit,
) {
    var cursor = windowStart
    programmes.forEach { p ->
        val start = p.startMs.coerceIn(windowStart, windowEnd)
        val stop = p.stopMs.coerceIn(windowStart, windowEnd)
        if (stop <= cursor) return@forEach
        if (start > cursor) { Spacer(Modifier.width(minutesWide(cursor, start))); cursor = start }
        val isNow = now in p.startMs until p.stopMs
        ProgrammeCell(
            title = p.title,
            timeLabel = "${clock(p.startMs)} – ${clock(p.stopMs)}",
            width = minutesWide(start, stop),
            isNow = isNow,
            focusRequester = null,
            onClick = { onOpen(p) },
        )
        cursor = stop
    }
    if (cursor < windowEnd) Spacer(Modifier.width(minutesWide(cursor, windowEnd)))
}

@Composable
private fun ProgrammeCell(
    title: String,
    timeLabel: String,
    width: Dp,
    isNow: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Box(Modifier.width(width).height(ROW_HEIGHT).padding(end = 4.dp)) {
        FocusableSurface(
            onClick = onClick,
            modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier).fillMaxSize(),
            shape = RoundedCornerShape(10.dp),
            selected = isNow,
            selectedContainerColor = colors.primaryContainer,
            contentAlignment = Alignment.CenterStart,
        ) { focused ->
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isNow) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (isNow) "NOW · $timeLabel" else timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isNow) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProgrammeDetailDialog(channelName: String, programme: EpgProgrammeEntity, onWatch: () -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp)) {
            Text(channelName.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(programme.title, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text("${clock(programme.startMs)} – ${clock(programme.stopMs)}", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            if (!programme.description.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(programme.description!!, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Watch channel", onClick = onWatch, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(fr))
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
