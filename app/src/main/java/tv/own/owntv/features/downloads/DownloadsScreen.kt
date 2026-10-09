package tv.own.owntv.features.downloads

import android.content.Context
import android.os.storage.StorageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.download.DownloadActivityTracker
import tv.own.owntv.core.download.DownloadQueue
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.storage.MediaFolders
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.features.epg.guideDayLabel
import tv.own.owntv.features.epg.localMidnight
import tv.own.owntv.features.live.edgeScrollSpec
import tv.own.owntv.features.recordings.RecordingsViewModel
import tv.own.owntv.features.recordings.recordingItems
import tv.own.owntv.features.shell.components.vodRuntime
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTab
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.stage.stageFocusLook
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import java.text.NumberFormat
import kotlin.math.ceil

/** Movies and Series hold downloads; Recordings is live TV saved by the recorder — a file of the user's all the same. */
private enum class DownloadsTab(val labelRes: Int) {
    MOVIES(R.string.common_nav_movies),
    SERIES(R.string.common_nav_series),
    RECORDINGS(R.string.recording_title),
}

/**
 * Downloads (Stage P7-01): title + storage crumb, the Movies · Series · Recordings tabs and the
 * Download folder tool, the list grouped DOWNLOADING / ON THIS TV, and the Recordings card on the right.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    onOpenGuide: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
    /** The content's left edge: beside the resting capsule (150), or 64 inside a docked rail's reserve. */
    contentStart: Dp = 150.mpx,
    modifier: Modifier = Modifier,
) {
    val vm: DownloadsViewModel = koinViewModel()
    val recVm: RecordingsViewModel = koinViewModel()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val lastPlayedId by vm.lastPlayedId.collectAsStateWithLifecycle()
    // Never mount the in-app player (it spins up mpv) when playback is handed to an external app.
    val externalPlayerOn by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val downloadRoot by vm.downloadRoot.collectAsStateWithLifecycle()
    val recordings by recVm.rows.collectAsStateWithLifecycle()
    val recExternal by recVm.externalPlayerOn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(DownloadsTab.MOVIES) }
    // Which tab holds which kind is core's rule: an episode download is EPISODE, which files under Series.
    val movies = remember(downloads) { downloads.filter { MediaFolders.folderFor(it.mediaType) == MediaFolders.MOVIES } }
    val series = remember(downloads) { downloads.filter { MediaFolders.folderFor(it.mediaType) == MediaFolders.SERIES } }
    val recorded = recordings.count { it.status == RecordingStatus.COMPLETED || it.status == RecordingStatus.RECORDING }
    val shown = if (tab == DownloadsTab.SERIES) series else movies
    // DOWNLOADING in queue order (the running one first, so "starts after" reads downwards), then ON THIS TV.
    val downloading = remember(shown) {
        shown.filter { it.status != DownloadStatus.COMPLETED }
            .sortedWith(compareBy<DownloadEntity> { it.status != DownloadStatus.RUNNING }.thenBy { it.createdAt })
    }
    val onTv = remember(shown) { shown.filter { it.status == DownloadStatus.COMPLETED } }
    // The ids in the list, top to bottom, for focus: entry, restore after playing, the neighbour after a delete.
    val listIds: List<Long> = if (tab == DownloadsTab.RECORDINGS) recordingIdsInOrder(recordings) else downloading.map { it.id } + onTv.map { it.id }

    val rowFocus = remember { HashMap<Long, FocusRequester>() }
    fun focusOf(id: Long) = rowFocus.getOrPut(id) { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    val folderFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    /** Focus the row for [id], scrolling near it first when it is not composed (the index counts no group labels, so it lands close). */
    suspend fun focusRow(id: Long) {
        if (runCatching { focusOf(id).requestFocus() }.isSuccess) return
        runCatching { listState.scrollToItem(listIds.indexOf(id).coerceAtLeast(0)) }
        withFrameNanos { }
        runCatching { focusOf(id).requestFocus() }
    }

    // The rail's ▶ lands on the first row, or on the tabs when this tab is empty.
    val entry: () -> Boolean = {
        val first = listIds.firstOrNull()
        if (first != null) runCatching { focusOf(first).requestFocus() }.isSuccess || runCatching { tabFocus.requestFocus() }.isSuccess
        else runCatching { tabFocus.requestFocus() }.isSuccess
    }
    DisposableEffect(onEntryHook, listIds.firstOrNull()) {
        onEntryHook?.invoke(entry)
        onDispose { onEntryHook?.invoke(null) }
    }

    // Returning from the player: back on the download that was played.
    LaunchedEffect(restoreFocus, listIds.size) {
        if (!restoreFocus || listIds.isEmpty()) return@LaunchedEffect
        val id = lastPlayedId ?: recVm.lastPlayedId.value
        withFrameNanos { }
        if (id != null && id in listIds) focusRow(id) else entry()
        onRestored()
    }

    // A deleted row takes its focus with it: move to the row that now sits in its place.
    var actedId by remember { mutableStateOf<Long?>(null) }
    var actedIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(listIds) {
        val id = actedId ?: return@LaunchedEffect
        if (id in listIds) return@LaunchedEffect
        actedId = null
        withFrameNanos { }
        val neighbour = listIds.getOrNull(actedIndex.coerceAtMost(listIds.lastIndex))
        if (neighbour != null) focusRow(neighbour) else runCatching { tabFocus.requestFocus() }
    }
    val acted: (Long) -> Unit = { id -> actedId = id; actedIndex = listIds.indexOf(id) }

    // A row that changes group (a download finishes, a recording ends) is laid out anew and drops focus, which
    // then falls to the tabs. Put it back on that row — only when focus left the list at that very moment.
    var lastFocusedId by remember { mutableStateOf<Long?>(null) }
    var listFocused by remember { mutableStateOf(false) }
    var listFocusLostAt by remember { mutableStateOf(0L) }
    val focusedGroup = lastFocusedId?.let { id ->
        if (tab == DownloadsTab.RECORDINGS) recordings.firstOrNull { it.id == id }?.status
        else downloads.firstOrNull { it.id == id }?.let { it.status == DownloadStatus.COMPLETED }
    }
    LaunchedEffect(focusedGroup) {
        val id = lastFocusedId ?: return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (!listFocused && System.currentTimeMillis() - listFocusLostAt < 1_000 && id in listIds) focusRow(id)
    }
    val rowFocused: (Long) -> Unit = { lastFocusedId = it }

    // Back from an external player the window regains focus on the rail; return it to the row that was played.
    var externalPending by remember { mutableStateOf(false) }
    val wentExternal: () -> Unit = { externalPending = true }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (externalPending) {
            externalPending = false
            lastFocusedId?.let { id -> scope.launch { withFrameNanos { }; withFrameNanos { }; focusRow(id) } }
        }
    }

    var showFolderPicker by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        // Horizontal geometry is a fraction of the mockup's 1920 width, so every zoom reflows.
        val screenW = maxWidth
        fun fx(px: Int) = screenW * (px / 1920f)

        // "Downloads" 46/800, then "USB drive · **57.6 GB free of 57.7 GB**".
        Column(Modifier.padding(start = contentStart + 20.mpx, top = 52.mpx).width(fx(1300) - contentStart)) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.mpx), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.content_downloads_title), style = stageText(46, 800, (-1).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                storage?.let { info ->
                    val sep = stringResource(R.string.content_epg_bits_separator)
                    val free = stringResource(R.string.content_downloads_storage_free, gigabytes(info.freeBytes), gigabytes(info.totalBytes))
                    val volume = remember(downloadRoot, info.usingFallback) {
                        volumeName(context, downloadRoot.takeUnless { info.usingFallback })
                    }
                    Text(
                        buildAnnotatedString {
                            volume?.let { append(it); append(sep) }
                            withStyle(SpanStyle(color = StageColors.Text)) { append(free) }
                        },
                        style = stageText(20, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 3.mpx),
                    )
                }
            }
            // The chosen folder is missing (USB stick out): new downloads go to the app's own folder until it is back.
            if (storage?.usingFallback == true) {
                Text(
                    stringResource(R.string.content_storage_folder_fallback),
                    style = stageText(16, 500), color = WarnColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.mpx),
                )
            }
        }

        // The fallback warning takes a line under the title; everything below moves down by it, so a focused
        // tab's glow never sits on the warning.
        val warnShift = if (storage?.usingFallback == true) 24.mpx else 0.mpx

        // Tabs, 46 apart (each carries 12 of padding a side), and the Download folder tool at the list's right edge.
        Row(
            Modifier.padding(start = contentStart + 8.mpx, top = 124.mpx + warnShift).width(fx(1856) - contentStart - 8.mpx).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(22.mpx),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(DownloadsTab.MOVIES to movies.size, DownloadsTab.SERIES to series.size, DownloadsTab.RECORDINGS to recorded).forEach { (t, n) ->
                StageTab(
                    label = stringResource(t.labelRes), count = n.toString(), selected = t == tab,
                    onClick = { tab = t; scope.launch { runCatching { listState.scrollToItem(0) } } },
                    modifier = if (t == tab) Modifier.focusRequester(tabFocus) else Modifier,
                )
            }
            Spacer(Modifier.weight(1f))
            StageTool(
                text = stringResource(R.string.settings_download_folder), icon = OwnTVIcon.FOLDER,
                onClick = { showFolderPicker = true },
                modifier = Modifier.padding(bottom = 4.mpx).focusRequester(folderFocus),
            )
        }

        // Where this tab's files go (owner): the folder in use plus core's fixed subfolder, right under
        // the Download folder tool that changes it, in the free band above the Recordings card.
        storage?.let { info ->
            val sub = when (tab) {
                DownloadsTab.MOVIES -> MediaFolders.MOVIES
                DownloadsTab.SERIES -> MediaFolders.SERIES
                DownloadsTab.RECORDINGS -> MediaFolders.TV
            }
            val root = remember(downloadRoot, info.usingFallback) {
                downloadRoot.takeUnless { info.usingFallback }?.let(StorageAccess::folderLabel)
                    ?: StorageAccess.defaultRoot(context).absolutePath
            }
            Text(
                root.trimEnd('/') + "/" + sub,
                style = stageText(16, 500), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.padding(start = fx(1100), top = 178.mpx + warnShift).width(fx(1856) - fx(1100) - 14.mpx),
            )
        }

        // The list: 1110 wide from the content edge, laid out wider by the focused row's glow and padded back.
        val glowRoom = 24.mpx
        val glowWide = Modifier.layout { measurable, constraints ->
            val extra = glowRoom.roundToPx()
            val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-extra, 0) }
        }
        Box(Modifier.padding(start = contentStart, top = 210.mpx + warnShift).width(fx(1260) - contentStart).fillMaxSize()) {
            if (listIds.isEmpty()) {
                Text(
                    stringResource(if (tab == DownloadsTab.RECORDINGS) R.string.recording_empty else R.string.content_downloads_empty),
                    style = stageText(20, 500), color = StageColors.Muted, modifier = Modifier.padding(start = 20.mpx, top = 26.mpx),
                )
            } else {
                CompositionLocalProvider(LocalBringIntoViewSpec provides edgeScrollSpec) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = glowRoom, end = glowRoom, bottom = 40.mpx),
                        modifier = Modifier.fillMaxSize().then(glowWide)
                            .onFocusChanged {
                                if (listFocused && !it.hasFocus) listFocusLostAt = System.currentTimeMillis()
                                listFocused = it.hasFocus
                            }
                            .focusGroup(),
                    ) {
                        if (tab == DownloadsTab.RECORDINGS) {
                            recordingItems(
                                recordings = recordings,
                                focusOf = ::focusOf,
                                onFocused = rowFocused,
                                onPlay = { r -> recVm.play(r); if (!recExternal) onFullscreen() else wentExternal() },
                                onStop = { r -> recVm.stop(r) },
                                onCancel = { r -> acted(r.id); recVm.cancel(r) },
                                onRetry = { r -> recVm.retry(r) },
                                onDelete = { r -> acted(r.id); recVm.delete(r) },
                            )
                        } else {
                            downloadGroup(R.string.content_downloads_group_downloading, downloading, first = true) { d ->
                                DownloadItem(d, details[d.id], active?.takeIf { it.id == d.id }, downloading, vm, onFullscreen, externalPlayerOn, focusOf(d.id), acted, rowFocused, wentExternal)
                            }
                            downloadGroup(R.string.content_downloads_group_on_tv, onTv, first = downloading.isEmpty()) { d ->
                                DownloadItem(d, details[d.id], null, downloading, vm, onFullscreen, externalPlayerOn, focusOf(d.id), acted, rowFocused, wentExternal)
                            }
                        }
                    }
                }
            }
        }

        RecordingsCard(
            recordings = recordings, recorded = recorded, onOpenGuide = onOpenGuide,
            modifier = Modifier.padding(start = fx(1330), top = 236.mpx + warnShift).width(fx(526)),
        )
    }

    // The volume picker Settings used to open, unchanged — only its door moved. Closing it returns to the tool.
    if (showFolderPicker) {
        StorageBrowser(
            title = stringResource(R.string.settings_download_folder_title),
            mode = BrowseMode.FOLDER,
            onPick = { vm.setDownloadRoot(it.absolutePath); showFolderPicker = false },
            onDismiss = { showFolderPicker = false },
        )
    }
    var pickerWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showFolderPicker) {
        if (showFolderPicker) pickerWasOpen = true
        else if (pickerWasOpen) { pickerWasOpen = false; withFrameNanos { }; runCatching { folderFocus.requestFocus() } }
    }
}

private val WarnColor = Color(0xFFFFB74D)
private val FailColor = Color(0xFFEF4444)

/** A group label ("DOWNLOADING", 12.5/800 +0.13em dim), then its rows 138 apart; nothing when the group is empty. */
private fun LazyListScope.downloadGroup(
    labelRes: Int,
    rows: List<DownloadEntity>,
    first: Boolean,
    row: @Composable (DownloadEntity) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "hdr_$labelRes") { StageListLabel(stringResource(labelRes), first) }
    items(rows, key = { "d_${it.id}" }) { row(it) }
}

/** The group label above a run of `.dl` rows: 26 from the label's top to the first row, 22 above it after a row. */
@Composable
internal fun StageListLabel(text: String, first: Boolean) {
    Text(
        text.uppercase(),
        style = stageText(12.5f, 800, 0.13.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 20.mpx, top = if (first) 0.mpx else 8.mpx).height(26.mpx),
    )
}

@Composable
private fun DownloadItem(
    d: DownloadEntity,
    info: DownloadDetails?,
    live: DownloadActivityTracker.ActiveDownload?,
    queue: List<DownloadEntity>,
    vm: DownloadsViewModel,
    onFullscreen: () -> Unit,
    externalPlayerOn: Boolean,
    focus: FocusRequester,
    acted: (Long) -> Unit,
    onFocused: (Long) -> Unit,
    wentExternal: () -> Unit,
) {
    val delete = StageAction(stringResource(R.string.common_delete), OwnTVIcon.TRASH) { acted(d.id); vm.delete(d) }
    val actions = when (d.status) {
        DownloadStatus.COMPLETED -> listOf(StageAction(stringResource(R.string.content_downloads_external), OwnTVIcon.EXTERNAL) { wentExternal(); vm.playExternal(d) }, delete)
        DownloadStatus.FAILED -> listOf(StageAction(stringResource(R.string.common_retry), OwnTVIcon.REFRESH) { vm.retry(d) }, delete)
        DownloadStatus.PAUSED -> listOf(StageAction(stringResource(R.string.common_resume), OwnTVIcon.PLAY) { vm.resume(d) }, delete)
        DownloadStatus.RUNNING, DownloadStatus.QUEUED -> listOf(StageAction(stringResource(R.string.content_downloads_pause), OwnTVIcon.PAUSE) { vm.pause(d) }, delete)
    }
    StageDownloadRow(
        title = info?.title ?: d.title,
        line = downloadLine(d, info),
        path = d.filePath,
        onFocused = { onFocused(d.id) },
        focus = focus,
        actions = actions,
        // OK plays a finished download; on any other row it steps to the row's first action.
        onClick = if (d.status == DownloadStatus.COMPLETED) ({ vm.play(d); if (!externalPlayerOn) onFullscreen() else wentExternal() }) else null,
        art = {
            if (!info?.artUrl.isNullOrBlank() || !d.posterUrl.isNullOrBlank()) {
                AsyncImage(model = info?.artUrl ?: d.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(StageColors.ControlFill), contentAlignment = Alignment.Center) {
                    tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.MOVIES, StageColors.Dim, Modifier.size(36.mpx))
                }
            }
        },
    ) {
        DownloadStatusText(d, live, queue)
    }
}

/** "2026 · 1 h 38 min · 1080p · 3.1 GB" for a film, "Season 1 · Episode 3 · 2.4 GB" for an episode. */
@Composable
private fun downloadLine(d: DownloadEntity, info: DownloadDetails?): String = buildList {
    info?.year?.let { add(it.toString()) }
    info?.season?.let { add(stringResource(R.string.content_season, it)) }
    info?.episode?.let { add(stringResource(R.string.player_episode_number, it)) }
    if (info?.season == null) info?.runtimeSecs?.takeIf { it >= 60 }?.let { add(vodRuntime(it)) }
    info?.quality?.let { add(it) }
    if (d.totalBytes > 0) add(fileSize(d.totalBytes))
}.joinToString(stringResource(R.string.content_epg_bits_separator))

/**
 * The right-hand status (`.st`, 380 wide, right-aligned, 17 muted): "**64%** · 12.4 MB/s · 3 min left" over the
 * progress bar, "Queued · starts after …", "✓ Ready to watch", or why it failed.
 */
@Composable
private fun DownloadStatusText(d: DownloadEntity, live: DownloadActivityTracker.ActiveDownload?, queue: List<DownloadEntity>) {
    val sep = stringResource(R.string.content_epg_bits_separator)
    when (d.status) {
        DownloadStatus.COMPLETED -> StageReady()
        DownloadStatus.FAILED -> StatusLine(stringResource(R.string.content_downloads_failed_group), FailColor)
        DownloadStatus.QUEUED -> StatusLine(
            DownloadQueue.startsAfter(queue, d.id)?.let { stringResource(R.string.content_downloads_starts_after, it.title) }
                ?: stringResource(R.string.content_downloads_queued),
        )
        DownloadStatus.RUNNING, DownloadStatus.PAUSED -> {
            val done = live?.downloadedBytes ?: d.downloadedBytes
            val total = live?.totalBytes?.takeIf { it > 0 } ?: d.totalBytes
            val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
            val rest = buildList {
                if (d.status == DownloadStatus.PAUSED) add(stringResource(R.string.content_downloads_paused))
                live?.bytesPerSecond?.takeIf { it > 0 }?.let { add(stringResource(R.string.content_downloads_speed, decimal(it / 1_048_576.0))) }
                live?.secondsLeft?.let { add(stringResource(R.string.home_time_left, vodRuntime(maxOf(60, (ceil(it / 60.0) * 60).toInt())))) }
                if (fraction == null) add(fileSize(done))
            }
            Text(
                buildAnnotatedString {
                    fraction?.let { withStyle(SpanStyle(color = StageColors.Text, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)) { append(stringResource(R.string.content_progress_percent, (it * 100).toInt())) } }
                    rest.forEachIndexed { i, s -> if (i > 0 || fraction != null) append(sep); append(s) }
                },
                style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.mpx),
            )
            StageProgress(fraction ?: 0f, Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun StatusLine(text: String, color: Color = StageColors.Muted) {
    Text(text, style = stageText(17, 500), color = color, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
}

/** `.ok`: "✓ Ready to watch", 17/700 in green. */
@Composable
internal fun StageReady() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
        tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.CHECK, StageColors.Ok, Modifier.size(17.mpx))
        Text(stringResource(R.string.content_downloads_ready), style = stageText(17, 700), color = StageColors.Ok, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A button on the focused row (`.tool.box`, 40 high, 16 px, full text colour). */
internal data class StageAction(val text: String, val icon: OwnTVIcon, val onClick: () -> Unit)

/**
 * A `.dl` row, 1110 × 124 (radius 24, padding 22, gap 24): the 190 × 107 picture, title 24/700 over a
 * 17 muted line, and the 380-wide status on the right. Focused = FX, and only then do its [actions]
 * show under the status (G3). The picture-and-title part holds focus; the actions sit to its right,
 * so ▶ reaches them and ▲ ▼ move between rows.
 */
@Composable
internal fun StageDownloadRow(
    title: String,
    line: String,
    /** Where the file is, or will be, on disk — the whole path, because the disk is the part worth reading. */
    path: String?,
    focus: FocusRequester,
    actions: List<StageAction>,
    onClick: (() -> Unit)?,
    onFocused: () -> Unit,
    art: @Composable () -> Unit,
    status: @Composable () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val firstAction = remember { FocusRequester() }
    Box(
        Modifier
            .padding(bottom = 14.mpx)
            .fillMaxWidth()
            .heightIn(min = 124.mpx)
            .onFocusChanged { hasFocus = it.hasFocus; if (it.hasFocus) onFocused() }
            .then(if (hasFocus) Modifier.stageFocusLook(StageFocus.FX, 24.mpx) else Modifier)
            .focusGroup()
            .padding(horizontal = 22.mpx, vertical = 8.mpx),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .clickable(interactionSource = null, indication = null) {
                        if (onClick != null) onClick() else runCatching { firstAction.requestFocus() }
                    },
                horizontalArrangement = Arrangement.spacedBy(24.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(190.mpx, 107.mpx).clip(RoundedCornerShape(14.mpx))) { art() }
                Column(Modifier.weight(1f)) {
                    Text(title, style = stageText(24, 700), color = if (hasFocus) Color.White else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(line, style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
                    path?.takeIf { it.isNotBlank() }?.let {
                        Text(StorageAccess.folderLabel(it) ?: it, style = stageText(15, 500), color = StageColors.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
                    }
                }
            }
            Column(Modifier.width(380.mpx), horizontalAlignment = Alignment.End) {
                status()
                if (hasFocus && actions.isNotEmpty()) {
                    Row(Modifier.padding(top = 12.mpx), horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                        actions.forEachIndexed { i, a ->
                            StageSurface(
                                onClick = a.onClick, radius = 15.mpx,
                                modifier = Modifier.height(40.mpx).then(if (i == 0) Modifier.focusRequester(firstAction) else Modifier),
                                idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(15.mpx)),
                            ) { focused ->
                                val c = if (focused) stageAccent.onAccent else StageColors.Text
                                Row(Modifier.padding(horizontal = 16.mpx), horizontalArrangement = Arrangement.spacedBy(9.mpx), verticalAlignment = Alignment.CenterVertically) {
                                    tv.own.owntv.ui.components.OwnTVIcon(a.icon, c, Modifier.size(18.mpx))
                                    Text(a.text, style = stageText(16, 700), color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The glass card beside the list (526 wide, radius 30, padding 30): ● Recordings, "Nothing recorded yet" (or
 * how many there are), how to record, Open TV Guide, and under a divider the next scheduled recording.
 */
@Composable
private fun RecordingsCard(recordings: List<RecordingEntity>, recorded: Int, onOpenGuide: () -> Unit, modifier: Modifier = Modifier) {
    val accent = stageAccent.accent
    val next = remember(recordings) { recordings.filter { it.status == RecordingStatus.SCHEDULED }.minByOrNull { it.programmeStartMs } }
    Column(modifier.stageGlass(30.mpx).padding(30.mpx)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
            tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.REC, accent, Modifier.size(18.mpx), filled = true)
            Text(stringResource(R.string.recording_title), style = stageText(18, 700), color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (recorded == 0) stringResource(R.string.recording_card_empty_title) else pluralStringResource(R.plurals.recording_card_count, recorded, recorded),
            style = stageText(30, 800), color = StageColors.Text, modifier = Modifier.padding(top = 14.mpx, bottom = 8.mpx),
        )
        Text(stringResource(R.string.recording_card_body), style = stageText(18, 500).copy(lineHeight = 27.mpxSp), color = StageColors.Muted)
        StageButton(
            text = stringResource(R.string.recording_open_guide), onClick = onOpenGuide, icon = OwnTVIcon.EPG,
            height = 58.mpx, textSize = 20, modifier = Modifier.padding(top = 22.mpx),
        )
        if (next != null) {
            Box(Modifier.padding(top = 28.mpx, bottom = 22.mpx).fillMaxWidth().height(1.mpx).background(Color.White.copy(alpha = 0.1f)))
            Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.CLOCK, StageColors.Muted, Modifier.size(18.mpx))
                Text(stringResource(R.string.recording_group_scheduled), style = stageText(18, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 14.mpx), horizontalArrangement = Arrangement.spacedBy(16.mpx), verticalAlignment = Alignment.CenterVertically) {
                tv.own.owntv.features.live.LivePlate(next.channelIconUrl, 70.mpx, 50.mpx)
                Column {
                    Text(next.title, style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        recordingWhen(next.programmeStartMs) + stringResource(R.string.content_epg_bits_separator) + next.channelName,
                        style = stageText(16, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** "Today 21:10", "Tomorrow 06:00", "Thu 2 Oct 20:15" — the guide's own day words. */
@Composable
internal fun recordingWhen(atMs: Long): String =
    guideDayLabel(localMidnight(atMs)) + " " + tv.own.owntv.ui.format.rememberBestDateFormatter("jm")(atMs)

/** The recordings tab's rows, top to bottom, in the order [recordingItems] draws them. */
private fun recordingIdsInOrder(recordings: List<RecordingEntity>): List<Long> =
    tv.own.owntv.features.recordings.recordingGroups(recordings).flatMap { (_, list) -> list.map { it.id } }

/** 3.1 GB from a gigabyte up, 840.2 MB below it. */
@Composable
internal fun fileSize(bytes: Long): String =
    if (bytes >= 1_073_741_824L) stringResource(R.string.common_size_gb, decimal(bytes / 1_073_741_824.0))
    else stringResource(R.string.common_size_mb, decimal(bytes / 1_048_576.0))

private fun gigabytes(bytes: Long): String = decimal(bytes.coerceAtLeast(0) / 1_073_741_824.0)

private fun decimal(value: Double): String = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}.format(value)

/**
 * The volume's own name as Android words it, in the user's language — "USB drive", "Internal shared
 * storage", a stick's label. [root] is the download folder (a path or a document-tree URI); blank or null = the
 * app's own folder. Null when the system cannot say, and the crumb then shows the free space alone.
 */
private fun volumeName(context: Context, root: String?): String? = runCatching {
    val sm = context.getSystemService(StorageManager::class.java)
    val volume = if (root != null && root.startsWith("content://")) {
        val id = android.net.Uri.decode(root.substringAfterLast('/')).substringBefore(':')
        sm.storageVolumes.firstOrNull { if (id == "primary") it.isPrimary else it.uuid.equals(id, ignoreCase = true) }
    } else {
        sm.getStorageVolume(java.io.File(root?.takeIf { it.isNotBlank() } ?: StorageAccess.defaultRoot(context).absolutePath))
    }
    volume?.getDescription(context)
}.getOrNull()
