package tv.own.owntv.features.epg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.live.LiveCategories
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.live.ProviderTags
import tv.own.owntv.features.live.edgeScrollSpec
import tv.own.owntv.features.live.liveCategoryEntries
import tv.own.owntv.features.live.liveEpgShiftLabel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.stageFocusLook
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

@Composable
private fun epgMessageText(message: EpgMessage): String = when (message) {
    EpgMessage.CreateProfile -> stringResource(R.string.content_epg_create_profile)
    EpgMessage.AddPlaylist -> stringResource(R.string.content_epg_add_playlist)
    is EpgMessage.NoChannelsForQuery -> stringResource(R.string.content_epg_no_channels_query, message.query)
    EpgMessage.MismatchedIds -> stringResource(R.string.content_epg_mismatched_ids)
}

@Composable
private fun epgMatchSummaryText(summary: EpgMatchSummary): String = when (summary) {
    EpgMatchSummary.CatchupUnavailable -> stringResource(R.string.content_epg_catchup_unavailable)
    EpgMatchSummary.MatchedNoProgrammes -> stringResource(R.string.content_epg_matched_no_programmes)
    EpgMatchSummary.AddPlaylist -> stringResource(R.string.content_epg_add_playlist_first)
    EpgMatchSummary.NoData -> stringResource(R.string.content_epg_no_match_data)
    EpgMatchSummary.AllMatched -> stringResource(R.string.content_epg_all_matched)
    is EpgMatchSummary.NoMatch -> stringResource(R.string.content_epg_no_match, summary.channelName)
    is EpgMatchSummary.AutoMatched -> if (summary.review > 0) {
        stringResource(
            R.string.content_epg_auto_matched,
            pluralStringResource(R.plurals.content_epg_auto_matched_applied, summary.applied, summary.applied),
            pluralStringResource(R.plurals.content_epg_auto_matched_review, summary.review, summary.review),
        )
    } else {
        pluralStringResource(R.plurals.content_epg_auto_matched_no_review, summary.applied, summary.applied)
    }
}

/** The programme on at [time] in a row, or null in a stretch with nothing on. */
private fun programmeAt(progs: List<EpgProgrammeEntity>?, time: Long): EpgProgrammeEntity? =
    progs?.firstOrNull { time in it.startMs until it.stopMs }

/** Across a gap the cursor moves by half an hour, rather than leaping hours to the next programme. */
private const val GAP_STEP_MS = 30L * 60 * 1000

/** Where ◀ ([dir] -1) or ▶ (+1) takes the cursor: programme by programme, half an hour at a time across gaps. */
private fun cursorStep(progs: List<EpgProgrammeEntity>, cursor: Long, dir: Int, windowStart: Long, windowEnd: Long): Long {
    val cur = programmeAt(progs, cursor)
    val target = if (dir > 0) {
        val from = cur?.stopMs ?: cursor
        val next = progs.firstOrNull { it.startMs >= from && it.startMs > cursor }
        when {
            cur != null && (next == null || next.startMs - cur.stopMs > GAP_STEP_MS) -> cur.stopMs
            cur != null -> next!!.startMs
            next != null && next.startMs - cursor <= GAP_STEP_MS -> next.startMs
            else -> cursor + GAP_STEP_MS
        }
    } else {
        val until = cur?.startMs ?: cursor
        val prev = progs.lastOrNull { it.stopMs <= until }
        when {
            cur != null && (prev == null || cur.startMs - prev.stopMs > GAP_STEP_MS) -> cur.startMs - GAP_STEP_MS
            cur != null -> prev!!.startMs
            prev != null && cursor - prev.stopMs <= GAP_STEP_MS -> prev.startMs
            else -> cursor - GAP_STEP_MS
        }
    }
    return target.coerceIn(windowStart, windowEnd - 1)
}

/**
 * The TV Guide in the Stage design (P4-01): the focused channel's preview and the focused programme
 * with its actions on top; one control line (Today · Now · Category · Order · Search · Auto-match EPG);
 * the ruler with the now-line; 8 slim rows of channel labels and programme cells.
 *
 * Each row's programmes are one focus target with a time cursor (◀ ▶ move it programme by programme,
 * ▲ ▼ step rows at the same time), so a week of guide stays one node per row. OK watches — the channel,
 * or a programme that has been on from the archive; holding OK or ☰ opens the programme menu.
 *
 * With [liveMode] this is Live TV's Guide view (G14): the category is Live TV's own, the title says
 * Live TV, and List view goes back to the channel list.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EpgScreen(
    modifier: Modifier = Modifier,
    onFullscreen: () -> Unit = {},
    onAddEpg: () -> Unit = {},
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    onContentScrolled: (Boolean) -> Unit = {},
    /** Required: every live tune in the app goes through the one shared path in LiveViewModel, so a
     *  channel gets the same Prefer HLS handling, ExoPlayer→mpv ladder, per-channel engine pin and
     *  external-player routing however the user reached it. */
    onPlayChannel: (channel: ChannelEntity, channels: List<ChannelEntity>) -> Unit,
    /** "Watch from start" on a catch-up programme. Required for the same reason, and additionally so the
     *  archive is tracked as catch-up playback (which decides what engine toggle the player HUD offers). */
    onPlayCatchup: (channel: ChannelEntity, programme: EpgProgrammeEntity) -> Unit,
    /** False while the full-screen or mini player owns the video surface. */
    previewEnabled: Boolean = true,
    liveMode: Boolean = false,
    onListView: () -> Unit = {},
    /** How ▶ from the rail enters the guide: at the grid row focus was on (the rail's entry hook, as Home's). */
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
) {
    val vm: EpgViewModel = koinViewModel()
    // Live TV's view model: its preview player (no second decoder), its category list and playlist marks.
    val liveVm: LiveViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val liveNow by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000L)
            value = System.currentTimeMillis()
        }
    }
    val query by vm.query.collectAsStateWithLifecycle()
    val matching by vm.matching.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val matchSummary by vm.matchSummary.collectAsStateWithLifecycle()
    val sortGuide by vm.sortGuide.collectAsStateWithLifecycle()
    val categoryFilter by vm.categoryFilter.collectAsStateWithLifecycle()
    val guideWidthShares by vm.guideWidthShares.collectAsStateWithLifecycle()
    val catchupPlayer by vm.catchupPlayer.collectAsStateWithLifecycle()
    val recordingRows by vm.recordingRows.collectAsStateWithLifecycle()
    val reminderRows by vm.reminderRows.collectAsStateWithLifecycle()
    val leadMinutes by vm.reminderLeadMinutes.collectAsStateWithLifecycle()
    val railItems by liveVm.railItems.collectAsStateWithLifecycle()
    val liveSelected by liveVm.selectedKey.collectAsStateWithLifecycle()
    val playlistMarks by liveVm.playlistMarks.collectAsStateWithLifecycle()
    val livePreviewSetting by liveVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val previewChannel by liveVm.previewChannel.collectAsStateWithLifecycle()
    val previewArmed by liveVm.previewArmed.collectAsStateWithLifecycle()
    val guidePreview by vm.guidePreview.collectAsStateWithLifecycle()

    // Live TV's Guide view shows Live TV's own category; the TV Guide keeps its own choice.
    val categoryKey: LiveKey? = if (liveMode) liveSelected.takeUnless { it == LiveKey.All } else categoryFilter
    LaunchedEffect(liveMode, liveSelected) { if (liveMode) vm.setCategoryFilter(liveSelected) }
    LaunchedEffect(Unit) { vm.load() } // reload from DB each time the guide is opened

    // The focused channel plays in the video after the focus settles, as in Live TV.
    val effectivePreview = previewEnabled && livePreviewSetting && guidePreview
    // Switching the Guide's own preview off stops what is already playing, not only the next channel.
    LaunchedEffect(guidePreview) { if (!guidePreview && previewEnabled) liveVm.stopPreview() }
    LaunchedEffect(previewChannel?.id, effectivePreview, previewArmed) {
        if (!effectivePreview || !previewArmed) return@LaunchedEffect
        val ch = previewChannel ?: return@LaunchedEffect
        delay(700)
        liveVm.playPreview(ch)
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rowListState = rememberLazyListState()
    LaunchedEffect(rowListState.firstVisibleItemIndex > 0) { onContentScrolled(rowListState.firstVisibleItemIndex > 0) }

    // --- The time axis: one scroll position shared by the ruler, the now-line and every row. ----------
    val ppm = with(density) { GuideGridDefaults.PxPerMin.toPx() }
    var timelinePx by remember { mutableIntStateOf(0) }
    var scrollPx by remember { mutableIntStateOf(0) }
    fun pxAt(t: Long): Float = (t - state.windowStart) / 60_000f * ppm
    fun maxScroll(): Int = (pxAt(state.windowEnd) - timelinePx).toInt().coerceAtLeast(0)
    fun timeAt(px: Float): Long = state.windowStart + (px / ppm * 60_000f).toLong()
    // A time is shown 3/8 of the way across, as the guide always had it: the programme on now keeps room
    // on both sides and its title is never cut (owner, 2026-09-30).
    fun showTime(t: Long) {
        scrollPx = (pxAt(t) - timelinePx * 3f / 8f).toInt().coerceIn(0, maxScroll())
    }
    LaunchedEffect(state.windowStart, timelinePx, state.channels.isNotEmpty()) {
        if (timelinePx > 0 && state.channels.isNotEmpty()) showTime(System.currentTimeMillis())
    }

    // --- Rows, focus and the cursor ------------------------------------------------------------------
    val rowRequesters = remember { HashMap<Int, FocusRequester>() }
    fun rowFocus(i: Int): FocusRequester = rowRequesters.getOrPut(i) { FocusRequester() }
    var focusedRow by remember { mutableIntStateOf(0) }
    var gridHasFocus by remember { mutableStateOf(false) }
    var cursorTime by remember { mutableStateOf(0L) }
    // Row stage (false): ▲ ▼ move between channels, ◀ leaves for the menu. Cell stage (true): ◀ ▶ go
    // through the programmes. Without it, reaching the channel list meant ◀ past every programme.
    var inCellMode by remember { mutableStateOf(false) }
    BackHandler(enabled = inCellMode && gridHasFocus) { inCellMode = false }
    // The programme the block on top shows and the buttons act on: the one under the cursor.
    var selected by remember { mutableStateOf<Pair<ChannelEntity, EpgProgrammeEntity?>?>(null) }

    // ▲ ▼ by row number (playbook 2): scroll just enough, wait a frame, focus. A held key keeps counting
    // from the target, and the newest press cancels the step still in flight.
    var stepTarget by remember { mutableStateOf<Int?>(null) }
    var stepJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun stepTo(i: Int) {
        stepTarget = i
        stepJob?.cancel()
        stepJob = scope.launch {
            val info = rowListState.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.index == i }
            val step = ((info.visibleItemsInfo.firstOrNull()?.size ?: 0) + info.mainAxisItemSpacing).toFloat()
            val delta = when {
                row == null -> if (i > (info.visibleItemsInfo.lastOrNull()?.index ?: 0)) step else -step
                row.offset - step < info.viewportStartOffset -> row.offset - step - info.viewportStartOffset
                row.offset + row.size + step > info.viewportEndOffset -> row.offset + row.size + step - info.viewportEndOffset
                else -> 0f
            }
            if (delta != 0f) runCatching { rowListState.scrollBy(delta) }
            if (rowListState.layoutInfo.visibleItemsInfo.none { it.index == i }) runCatching { rowListState.scrollToItem(i) }
            withFrameNanos { }
            runCatching { rowFocus(i).requestFocus() }
            stepTarget = null
        }
    }
    // Back to the grid at the row focus was on (after a menu, the sheet, a jump).
    fun focusGrid(row: Int = focusedRow) {
        val i = row.coerceIn(0, (state.channels.size - 1).coerceAtLeast(0))
        scope.launch {
            val visible = rowListState.layoutInfo.visibleItemsInfo.any { it.index == i }
            if (!visible) runCatching { rowListState.scrollToItem(i) }
            withFrameNanos { }
            if (!runCatching { rowFocus(i).requestFocus() }.getOrDefault(false)) {
                withFrameNanos { }
                runCatching { rowFocus(i).requestFocus() }
            }
        }
    }
    // The cursor stays in view: scroll only when it reaches an edge, by one slot of room.
    LaunchedEffect(cursorTime, timelinePx) {
        if (!gridHasFocus || timelinePx <= 0 || cursorTime <= 0L) return@LaunchedEffect
        val x = pxAt(cursorTime)
        val margin = GuideGridDefaults.SlotMin * ppm
        when {
            x < scrollPx + margin -> scrollPx = (x - margin).toInt().coerceIn(0, maxScroll())
            x > scrollPx + timelinePx - margin -> scrollPx = (x - timelinePx + margin).toInt().coerceIn(0, maxScroll())
        }
    }

    // Back from a channel tuned in the guide: its row again, once the reload has the grid on screen.
    LaunchedEffect(restoreFocus, state.loading, state.channels.size) {
        if (!restoreFocus || state.loading || state.channels.isEmpty()) return@LaunchedEffect
        val idx = vm.lastTunedChannelId?.let { id -> state.channels.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
        focusedRow = idx
        delay(80)
        focusGrid(idx)
        onRestored()
    }

    // --- Overlays --------------------------------------------------------------------------------------
    var menuFor by remember { mutableStateOf<Pair<ChannelEntity, EpgProgrammeEntity>?>(null) }
    var channelMenuFor by remember { mutableStateOf<ChannelEntity?>(null) }
    var recordMenuFor by remember { mutableStateOf<Pair<ChannelEntity, EpgProgrammeEntity>?>(null) }
    var askPlayerFor by remember { mutableStateOf<Pair<ChannelEntity, EpgProgrammeEntity>?>(null) }
    var matchingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var offsetChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var orderOpen by remember { mutableStateOf(false) }
    var dayOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var sheetHadFocus by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    // The field reports "not focused" when it first attaches; it only closes after it has had focus (playbook 10).
    var searchHadFocus by remember { mutableStateOf(false) }
    // Where focus goes back to when an overlay closes: the control or button that opened it.
    var returnTo by remember { mutableStateOf<(() -> Unit)?>(null) }
    val anyOverlay = menuFor != null || channelMenuFor != null || recordMenuFor != null || askPlayerFor != null || matchingChannel != null ||
        offsetChannel != null || orderOpen || dayOpen || review.isNotEmpty()
    var hadOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(anyOverlay) {
        if (anyOverlay) { hadOverlay = true; return@LaunchedEffect }
        if (!hadOverlay) return@LaunchedEffect
        hadOverlay = false
        delay(60)
        (returnTo ?: { focusGrid() }).invoke()
    }
    // Leaving the grid (not for one of its own menus) goes back to the row stage.
    LaunchedEffect(gridHasFocus, anyOverlay) { if (!gridHasFocus && !anyOverlay) inCellMode = false }
    // The one-line outcome beside Auto-match EPG goes after a while (the review, when there is one, says it instead).
    LaunchedEffect(matchSummary, review.isEmpty()) {
        if (matchSummary != null && review.isEmpty()) { delay(8_000); vm.clearSummary() }
    }

    val channelsNow = state.channels
    val watch: (ChannelEntity) -> Unit = { ch -> vm.noteChannelTuned(ch); onPlayChannel(ch, channelsNow) }
    val playFromStart: (ChannelEntity, EpgProgrammeEntity) -> Unit = { ch, p ->
        when (catchupPlayer) {
            SettingsRepository.CatchupPlayer.ASK -> askPlayerFor = ch to p
            SettingsRepository.CatchupPlayer.INTERNAL -> { vm.noteChannelTuned(ch); onPlayCatchup(ch, p) }
            SettingsRepository.CatchupPlayer.EXTERNAL -> { vm.noteChannelTuned(ch); vm.playCatchupExternal(ch, p) }
        }
    }
    // OK on a programme: watch — the archive for one that has been on and can be replayed, else the channel.
    val onOk: (ChannelEntity, EpgProgrammeEntity?) -> Unit = { ch, p ->
        val now = System.currentTimeMillis()
        if (p != null && p.stopMs <= now && vm.canCatchup(ch, p, now)) playFromStart(ch, p) else watch(ch)
    }

    /** What [p] on [ch] can do right now — the programme menu, Record ▾ and the buttons on top share it. */
    @Composable
    fun actionsFor(ch: ChannelEntity, p: EpgProgrammeEntity): GuideProgrammeActions = guideProgrammeActions(
        vm, ch, p, liveNow,
        onWatch = { watch(ch) },
        onPlayFromStart = { playFromStart(ch, p) },
        onPickEpg = { matchingChannel = ch },
        onEpgOffset = { offsetChannel = ch },
    )

    // Channel names as the rows show them: provider tags parsed off, and the category's own name left out
    // when every channel in it starts with it ("Sky Cinema Highlights" under Sky Cinema → "Highlights").
    val (entries, groupsHeading) = liveCategoryEntries(railItems, emptyMap())
    val categoryEntry = entries.firstOrNull { it.item.key == (categoryKey ?: LiveKey.All) }
    val categoryLabel = categoryEntry?.label ?: stringResource(R.string.content_category_all_channels)
    val prefix = categoryLabel.takeIf { categoryKey is LiveKey.Folder || categoryKey is LiveKey.Custom }
    fun rowName(ch: ChannelEntity): String {
        val name = ProviderTags.parse(ch.name).name
        val rest = prefix?.let { p -> name.takeIf { it.startsWith("$p ", ignoreCase = true) }?.substring(p.length)?.trim() }
        return rest?.takeIf { it.isNotEmpty() } ?: name
    }
    val remindersByChannel = remember(reminderRows) { reminderRows.groupBy({ it.channelId }, { it.startMs }).mapValues { it.value.toSet() } }
    val recordingsByChannel = remember(recordingRows) {
        recordingRows.filter { it.status != RecordingStatus.CANCELLED }
            .groupBy({ it.channelId }, { it.programmeStartMs }).mapValues { it.value.toSet() }
    }

    val dayTool = remember { FocusRequester() }
    val categoryTool = remember { FocusRequester() }
    val orderTool = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val sheetFocus = remember { FocusRequester() }
    val catListState = rememberLazyListState()

    DisposableEffect(onEntryHook) {
        onEntryHook?.invoke {
            state.channels.isNotEmpty() &&
                runCatching { rowFocus(focusedRow.coerceIn(0, state.channels.size - 1)).requestFocus() }.getOrDefault(false)
        }
        onDispose { onEntryHook?.invoke(null) }
    }

    BackHandler(enabled = sheetOpen) { sheetOpen = false; runCatching { categoryTool.requestFocus() } }
    LaunchedEffect(sheetOpen) {
        if (sheetOpen) { withFrameNanos { }; runCatching { sheetFocus.requestFocus() } } else sheetHadFocus = false
    }
    LaunchedEffect(searchOpen) { if (searchOpen) { withFrameNanos { }; runCatching { searchFocus.requestFocus() } } }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // Entering from the rail lands in the grid, on the row focus was on.
            .focusProperties { onEnter = { if (state.channels.isNotEmpty()) runCatching { rowFocus(focusedRow.coerceIn(0, state.channels.size - 1)).requestFocus() } } }
            .focusGroup(),
    ) {
        val screenW = maxWidth
        fun fx(px: Int) = screenW * (px / 1920f)
        val labelW = guideWidthShares?.let { (screenW - fx(112)) * (it.channels / 100f) } ?: (screenW * GuideGridDefaults.ChannelColShare)
        val timelineW = screenW - fx(48) - labelW - fx(64)
        LaunchedEffect(timelineW) { timelinePx = with(density) { timelineW.roundToPx() } }

        val top = selected ?: state.channels.getOrNull(focusedRow)?.let { ch -> ch to programmeAt(vm.cachedProgrammes(ch), liveNow) }
        val topChannel = top?.first
        val topProgramme = top?.second
        val full by produceState<EpgProgrammeEntity?>(null, topProgramme?.id) {
            value = topProgramme?.id?.takeIf { it > 0 }?.let { vm.programmeFull(it) }
        }
        val shown = full ?: topProgramme

        Column(Modifier.fillMaxSize()) {
        // Top (44 → 404): the video, and beside it the programme under the cursor.
        Row(Modifier.padding(start = fx(64), end = fx(64), top = 44.mpx).fillMaxWidth().height(360.mpx)) {
        val videoChannel = if (effectivePreview) previewChannel ?: topChannel else topChannel
        GuideVideo(
            channel = videoChannel,
            channelLine = videoChannel?.let { listOfNotNull(it.number?.toString(), ProviderTags.parse(it.name).name).joinToString(" · ") },
            nowTitle = videoChannel?.let { ch -> programmeAt(vm.cachedProgrammes(ch), liveNow)?.takeIf { liveNow < it.stopMs }?.title },
            previewEngine = liveVm.previewEngine,
            showVideo = effectivePreview && previewArmed,
            modifier = Modifier.width(640.mpx),
        )
        // … and the programme under the cursor, with what it can do.
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (topChannel != null) {
                val chName = ProviderTags.parse(topChannel.name).name
                GuideProgrammeBlock(
                    eyebrow = shown?.let { programmeEyebrow(it, chName, liveNow) },
                    title = shown?.title ?: chName,
                    details = if (shown == null) stringResource(R.string.content_epg_no_programme) else programmeDetailsLine(shown),
                    synopsis = shown?.description,
                    modifier = Modifier.padding(start = 46.mpx, top = 76.mpx).widthIn(max = 1080.mpx),
                )
            }
            // The key hints at the bottom right of the top area, level with the video's bottom edge (owner, 2026-10-01).
            Box(Modifier.align(Alignment.BottomEnd)) { GuideKeyHints(cellMode = inCellMode && gridHasFocus) }
        }

        }

        // The control line (452): one row, every header button of the old guide kept.
        val viewTime = timeAt(scrollPx + timelinePx * 3f / 8f)
        val viewDay = localMidnight(viewTime)
        Row(
            Modifier.padding(start = fx(68), end = fx(64), top = 48.mpx).fillMaxWidth().height(48.mpx),
            horizontalArrangement = Arrangement.spacedBy(8.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (liveMode) R.string.common_nav_live_tv else R.string.content_epg_title),
                style = stageText(30, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.mpx, end = 14.mpx),
            )
            StageTool(
                text = null, icon = OwnTVIcon.CALENDAR, value = guideDayLabel(viewDay), trailingIcon = OwnTVIcon.CHEVRON_DOWN,
                onClick = { returnTo = { runCatching { dayTool.requestFocus() } }; dayOpen = true },
                modifier = Modifier.focusRequester(dayTool),
            )
            StageTool(text = stringResource(R.string.content_epg_now_short), icon = OwnTVIcon.NOW, onClick = { showTime(System.currentTimeMillis()); cursorTime = System.currentTimeMillis() })
            StageTool(
                text = null, icon = OwnTVIcon.LIST, value = categoryLabel, valueAccent = true, trailingIcon = OwnTVIcon.CHEVRON_DOWN,
                onClick = { sheetOpen = true },
                modifier = Modifier.focusRequester(categoryTool).widthIn(max = 380.mpx),
            )
            StageTool(
                text = null, icon = OwnTVIcon.SORT, value = guideSortLabel(sortGuide), trailingIcon = OwnTVIcon.CHEVRON_DOWN,
                onClick = { returnTo = { runCatching { orderTool.requestFocus() } }; orderOpen = true },
                modifier = Modifier.focusRequester(orderTool),
            )
            if (searchOpen || query.isNotEmpty()) {
                StageSearchField(
                    query = query, onQueryChange = vm::setQuery,
                    placeholder = stringResource(R.string.content_epg_search_hint).trimEnd('…'),
                    modifier = Modifier.width(360.mpx).focusRequester(searchFocus)
                        .onFocusChanged {
                            if (it.hasFocus) searchHadFocus = true
                            else if (searchHadFocus && query.isEmpty()) { searchHadFocus = false; searchOpen = false }
                        },
                )
            } else {
                StageTool(text = null, icon = OwnTVIcon.SEARCH, onClick = { searchOpen = true })
            }
            if (liveMode) StageTool(text = stringResource(R.string.content_live_list_view), icon = OwnTVIcon.LIST, onClick = onListView)
            Spacer(Modifier.weight(1f))
            matchSummary?.takeIf { review.isEmpty() }?.let {
                Text(
                    epgMatchSummaryText(it), style = stageText(16, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    // A capped width, not a weight: a second weight split the line and moved Auto-match off the edge.
                    modifier = Modifier.widthIn(max = 620.mpx), textAlign = TextAlign.End,
                )
            }
            if (matching) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.mpx), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.mpx)) {
                    OwnTVSpinner(sizeDp = 18)
                    Text(stringResource(R.string.content_epg_matching), style = stageText(18, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                StageTool(text = stringResource(R.string.content_epg_match_button), icon = OwnTVIcon.SPARKLE, onClick = vm::autoMatchEpg)
            }
        }

        // The grid: ruler at 518, rows from 566.
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 18.mpx)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 44) }
                // No EPG feed added yet → a guide it can't fill. Point the user to EPG Sources.
                !state.hasEpgSources && state.channels.isEmpty() -> GuideEmpty(
                    title = stringResource(R.string.content_epg_empty),
                    body = stringResource(R.string.content_epg_add_description),
                    action = stringResource(R.string.content_epg_add), onAction = onAddEpg,
                )
                state.channels.isEmpty() -> GuideEmpty(title = state.message?.let { epgMessageText(it) } ?: stringResource(R.string.content_epg_no_guide))
                else -> {
                    val gx = fx(48) + labelW
                    // The number column fits the longest channel number (38 = the mockup's three digits).
                    val numberWidth = maxOf(38f, (state.channels.maxOfOrNull { it.number?.toString()?.length ?: 0 } ?: 0) * 11f).mpx
                    GuideRuler(
                        windowStart = state.windowStart, windowEnd = state.windowEnd, now = liveNow, scrollPx = scrollPx,
                        modifier = Modifier.padding(start = gx).width(timelineW).height(40.mpx),
                    )
                    CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides edgeScrollSpec) {
                        LazyColumn(
                            state = rowListState,
                            modifier = Modifier
                                .padding(start = fx(48), end = fx(64), top = 48.mpx)
                                .fillMaxSize()
                                .onFocusChanged { gridHasFocus = it.hasFocus }
                                .focusProperties { onEnter = { runCatching { rowFocus(focusedRow.coerceIn(0, state.channels.size - 1)).requestFocus() } } }
                                .focusGroup(),
                            verticalArrangement = Arrangement.spacedBy(GuideGridDefaults.RowGap),
                            contentPadding = PaddingValues(bottom = 24.mpx),
                        ) {
                            itemsIndexed(state.channels, key = { _, ch -> ch.id }) { index, channel ->
                                GuideRow(
                                    vm = vm,
                                    channel = channel,
                                    name = rowName(channel),
                                    dot = playlistMarks[channel.sourceId]?.color,
                                    labelWidth = labelW,
                                    numberWidth = numberWidth,
                                    windowStart = state.windowStart,
                                    windowEnd = state.windowEnd,
                                    now = liveNow,
                                    scrollPx = scrollPx,
                                    focusRequester = rowFocus(index),
                                    selectedRow = gridHasFocus && index == focusedRow,
                                    cursorTime = cursorTime,
                                    cellMode = inCellMode,
                                    onEnterCell = { cursorTime = System.currentTimeMillis(); inCellMode = true },
                                    recordingStarts = recordingsByChannel[channel.id].orEmpty(),
                                    reminderStarts = remindersByChannel[channel.id].orEmpty(),
                                    onFocused = {
                                        focusedRow = index
                                        if (cursorTime !in state.windowStart until state.windowEnd) cursorTime = System.currentTimeMillis()
                                        liveVm.onChannelFocused(channel)
                                    },
                                    onCursor = { cursorTime = it },
                                    onShow = { p -> selected = channel to p },
                                    onStep = { delta ->
                                        val from = stepTarget ?: index
                                        val to = from + delta
                                        when {
                                            to < 0 -> false // ▲ from the first row: up to the control line
                                            to >= state.channels.size -> true
                                            else -> { stepTo(to); true }
                                        }
                                    },
                                    onOk = { p -> onOk(channel, p) },
                                    onMenu = { p -> returnTo = null; if (p != null) menuFor = channel to p else channelMenuFor = channel },
                                )
                            }
                        }
                    }
                    // The now-line, 3 px with its glow, over every row.
                    val nowX = with(density) { (pxAt(liveNow) - scrollPx).toDp() }
                    if (liveNow in state.windowStart..state.windowEnd && nowX >= 0.mpx && nowX <= timelineW) {
                        Box(
                            Modifier
                                .padding(start = gx + nowX - 1.5.mpx, top = 38.mpx, bottom = 24.mpx)
                                .width(3.mpx)
                                .fillMaxHeight()
                                .guideNowLine(stageAccent.accent),
                        )
                    }
                }
            }
        }

        }

        // ◀ … Category ▾ (P4-03): Live TV's own list as the sheet, titled for the guide.
        if (sheetOpen) {
            val railCounts by liveVm.railCounts.collectAsStateWithLifecycle()
            val (sheetEntries, _) = liveCategoryEntries(railItems, railCounts)
            LiveCategories(
                searchQuery = vm.categoryQuery.collectAsStateWithLifecycle().value,
                onSearchQueryChange = vm::setCategoryQuery,
                entries = sheetEntries,
                selectedIndex = railItems.indexOfFirst { it.key == (categoryKey ?: LiveKey.All) }.coerceAtLeast(0),
                groupsHeading = groupsHeading,
                sheet = true,
                listState = catListState,
                onSelect = { idx ->
                    railItems.getOrNull(idx)?.key?.let { key -> if (liveMode) liveVm.select(key) else vm.setCategoryFilter(key) }
                    sheetOpen = false
                    focusedRow = 0
                    scope.launch { runCatching { rowListState.scrollToItem(0) } }
                    focusGrid(0)
                },
                onLongSelect = {},
                onNavigateRight = { sheetOpen = false; focusGrid() },
                focusRequester = sheetFocus,
                sheetTitle = stringResource(R.string.content_epg_guide_category),
                sheetHint = stringResource(R.string.content_epg_category_hint),
                modifier = Modifier
                    .padding(start = fx(24), top = 24.mpx, bottom = 24.mpx)
                    .width(fx(450))
                    .fillMaxHeight()
                    .onFocusChanged {
                        // Closes when focus leaves it — not on the "unfocused" report every node gets when it attaches.
                        if (it.hasFocus) sheetHadFocus = true else if (sheetHadFocus && sheetOpen) sheetOpen = false
                    },
            )
        }
    }

    menuFor?.let { (ch, p) ->
        val formatTime = tv.own.owntv.ui.format.rememberSystemTimeFormatter()
        GuideProgrammeMenu(
            title = p.title,
            subtitle = listOf(
                stringResource(R.string.content_live_time_range_plain, formatTime(p.startMs), formatTime(p.stopMs)),
                listOfNotNull(ch.number?.toString(), ProviderTags.parse(ch.name).name).joinToString(" "),
            ).joinToString(" · "),
            actions = actionsFor(ch, p),
            leadMinutes = leadMinutes,
            onDismiss = { menuFor = null },
        )
    }
    recordMenuFor?.let { (ch, p) -> GuideRecordMenu(title = p.title, actions = actionsFor(ch, p), onDismiss = { recordMenuFor = null }) }
    askPlayerFor?.let { (ch, p) ->
        GuideCatchupChooser(
            onInternal = { vm.noteChannelTuned(ch); onPlayCatchup(ch, p) },
            onExternal = { vm.noteChannelTuned(ch); vm.playCatchupExternal(ch, p) },
            onDismiss = { askPlayerFor = null },
        )
    }
    if (orderOpen) {
        val showEmpty by vm.showEmpty.collectAsStateWithLifecycle()
        GuideOrderMenu(
            current = sortGuide, onPick = vm::setGuideSort,
            showEmpty = showEmpty, onShowEmpty = vm::setShowEmpty,
            showPreview = guidePreview, onShowPreview = vm::setGuidePreview,
            onDismiss = { orderOpen = false },
        )
    }
    channelMenuFor?.let { ch ->
        GuideChannelMenu(
            channel = ch,
            isFavorite = ch.id in vm.favoriteChannelIds.collectAsStateWithLifecycle().value,
            epgOffsetValue = vm.currentEpgShift(ch)?.let { liveEpgShiftLabel(it) } ?: stringResource(R.string.content_epg_offset_global_short),
            onWatch = { watch(ch) },
            onFavorite = { vm.toggleFavoriteChannel(ch) },
            onAutoMatch = { vm.autoMatchOne(ch) },
            onPickEpg = { matchingChannel = ch },
            onEpgOffset = { offsetChannel = ch },
            onDismiss = { channelMenuFor = null },
        )
    }
    if (dayOpen) {
        val days = remember(state.windowStart, state.windowEnd) {
            generateSequence(localMidnight(state.windowStart)) { localMidnight(it + 36L * 60 * 60 * 1000) }.takeWhile { it < state.windowEnd }.toList()
        }
        GuideDayMenu(
            days = days,
            currentDay = localMidnight(timeAt(scrollPx + timelinePx * 3f / 8f)),
            onPick = { day ->
                val now = System.currentTimeMillis()
                showTime(day + (now - localMidnight(now)))
            },
            onDismiss = { dayOpen = false },
        )
    }
    offsetChannel?.let { channel ->
        tv.own.owntv.features.live.EpgOffsetDialog(
            channelName = channel.name,
            currentMinutes = vm.currentEpgShift(channel),
            globalMinutes = vm.globalEpgShift(),
            onSet = { vm.setEpgShift(channel, it) },
            onDismiss = { offsetChannel = null },
        )
    }
    matchingChannel?.let { channel ->
        tv.own.owntv.features.live.EpgMatchDialog(
            channelName = channel.name,
            currentMatch = vm.currentEpgMatch(channel),
            loadChannels = { vm.availableEpgChannels(channel.name, it) },
            onPick = { vm.setEpgMatch(channel, it); matchingChannel = null },
            onClear = { vm.setEpgMatch(channel, null); matchingChannel = null },
            onDismiss = { matchingChannel = null },
        )
    }
    if (review.isNotEmpty()) {
        val includeLogos by vm.includeGuideLogos.collectAsStateWithLifecycle()
        val settingsVm: tv.own.owntv.features.settings.SettingsViewModel = koinViewModel()
        val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
        val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
        val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
        GuideReviewPopup(
            suggestions = review,
            matchedCount = (matchSummary as? EpgMatchSummary.AutoMatched)?.applied,
            includeLogos = includeLogos,
            onIncludeLogos = vm::setIncludeGuideLogos,
            onAccept = vm::acceptSuggestion,
            onSkip = vm::dismissSuggestion,
            onAcceptAll = vm::acceptAllSuggestions,
            onSkipAll = vm::clearReview,
            onDone = vm::clearReview,
            chNavEnabled = chNavEnabled,
            chNavUpSkip = chNavUpSkip,
            chNavDownSkip = chNavDownSkip,
        )
    }
}

/**
 * One guide row: the channel label, then the programmes as one focus target with a cursor. ◀ ▶ move the
 * cursor programme by programme (◀ from the first leaves to the rail), ▲ ▼ step rows ([onStep]), OK
 * watches, holding OK or ☰ opens the programme menu. Programmes are read when the row comes into view.
 */
@Composable
private fun GuideRow(
    vm: EpgViewModel,
    channel: ChannelEntity,
    name: String,
    dot: Color?,
    labelWidth: androidx.compose.ui.unit.Dp,
    numberWidth: androidx.compose.ui.unit.Dp,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    scrollPx: Int,
    focusRequester: FocusRequester,
    selectedRow: Boolean,
    cursorTime: Long,
    cellMode: Boolean,
    onEnterCell: () -> Unit,
    recordingStarts: Set<Long>,
    reminderStarts: Set<Long>,
    onFocused: () -> Unit,
    onCursor: (Long) -> Unit,
    onShow: (EpgProgrammeEntity?) -> Unit,
    onStep: (Int) -> Boolean,
    onOk: (EpgProgrammeEntity?) -> Unit,
    onMenu: (EpgProgrammeEntity?) -> Unit,
) {
    // Cache peek first, so a row scrolled back into view draws at once; a miss reads this one channel.
    val cacheRevision by vm.cacheRevision.collectAsStateWithLifecycle()
    val programmes by produceState(initialValue = vm.cachedProgrammes(channel), channel.id, windowStart, cacheRevision) {
        value = vm.cachedProgrammes(channel) ?: vm.programmesFor(channel)
        // Still empty: ask again a little later, twice, before the row settles on "No program".
        repeat(2) {
            if (value?.isNotEmpty() == true) return@produceState
            delay(3_000)
            value = vm.programmesFor(channel)
        }
    }
    var focused by remember { mutableStateOf(false) }
    var okDown by remember { mutableStateOf(false) }
    LaunchedEffect(focused, cursorTime, programmes) { if (focused) onShow(programmeAt(programmes, cursorTime)) }
    val rowSelected = focused && !cellMode
    Row(Modifier.fillMaxWidth().height(GuideGridDefaults.RowHeight), verticalAlignment = Alignment.CenterVertically) {
        GuideChannelLabel(channel, name, dot, selected = selectedRow, numberWidth = numberWidth, modifier = Modifier.width(labelWidth))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
                .onPreviewKeyEvent { e ->
                    val progs = programmes.orEmpty()
                    val down = e.type == KeyEventType.KeyDown
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            val repeat = e.nativeKeyEvent.repeatCount
                            if (down && repeat == 0) okDown = true
                            else if (down && okDown) { okDown = false; onMenu(programmeAt(progs, cursorTime)) }
                            else if (!down && okDown) { okDown = false; if (cellMode) onOk(programmeAt(progs, cursorTime)) else onEnterCell() }
                            true
                        }
                        Key.Menu -> { if (down) onMenu(programmeAt(progs, cursorTime)); true }
                        // Physical by design: the timeline runs left to right in every language.
                        // Row stage: ◀ leaves for the menu, ▶ steps into the programmes.
                        Key.DirectionLeft -> if (!cellMode) false else {
                            if (down) onCursor(cursorStep(progs, cursorTime, -1, windowStart, windowEnd))
                            true
                        }
                        Key.DirectionRight -> {
                            if (down && !cellMode) onEnterCell()
                            else if (down) onCursor(cursorStep(progs, cursorTime, +1, windowStart, windowEnd))
                            true
                        }
                        Key.DirectionUp -> if (down) onStep(-1) else true
                        Key.DirectionDown -> if (down) onStep(+1) else true
                        else -> false
                    }
                }
                .focusable()
                .then(if (rowSelected) Modifier.stageFocusLook(tv.own.owntv.ui.stage.StageFocus.FX, 14.mpx) else Modifier),
        ) {
            programmes?.let { progs ->
                val catchupIds = remember(progs, channel, now) { progs.filter { vm.canCatchup(channel, it, now) }.mapTo(HashSet()) { it.id } }
                ProgrammeStripCanvas(
                    programmes = progs,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    now = now,
                    focusTime = if (focused && cellMode) cursorTime else null,
                    catchupIds = catchupIds,
                    recordingStarts = recordingStarts,
                    reminderStarts = reminderStarts,
                    scrollPx = scrollPx,
                )
            }
        }
    }
}

/** An empty guide: what is missing, and — with no EPG added — the way to add one. */
@Composable
private fun GuideEmpty(title: String, body: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(bottom = 80.mpx), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = stageText(24, 700), color = StageColors.Text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 900.mpx))
            if (body != null) {
                Text(body, style = stageText(19, 400), color = StageColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 900.mpx).padding(top = 8.mpx))
            }
            if (action != null) {
                Spacer(Modifier.height(24.mpx))
                StageButton(action, onClick = onAction, icon = OwnTVIcon.ADD, height = 56.mpx, textSize = 19)
            }
        }
    }
}

/**
 * How to move, under the programme beside the video (owner, 2026-10-01: in place of the old action buttons,
 * which could not be reached without leaving the channel — they are all in Hold OK's menu). At row level:
 * OK Browse · ▲▼ Channels · ◀ Menu · Hold OK Options; inside a row: OK Watch · ◀ ▶ Programs · Back Channels.
 */
@Composable
private fun GuideKeyHints(cellMode: Boolean) {
    val hints = if (cellMode) {
        listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
            "◀ ▶" to stringResource(R.string.content_key_programmes),
            stringResource(R.string.common_back) to stringResource(R.string.content_key_channels),
            stringResource(R.string.content_key_hold_ok) to stringResource(R.string.content_key_options),
        )
    } else {
        listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.content_key_browse),
            "▲ ▼" to stringResource(R.string.content_key_channels),
            "◀" to stringResource(R.string.content_key_menu),
            stringResource(R.string.content_key_hold_ok) to stringResource(R.string.content_key_options),
        )
    }
    tv.own.owntv.ui.stage.StageKeyHints(hints)
}
