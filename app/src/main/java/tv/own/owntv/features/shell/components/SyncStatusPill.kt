package tv.own.owntv.features.shell.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.tv.material3.Text
import tv.own.owntv.core.download.DownloadActivityTracker
import tv.own.owntv.core.recording.RecordingActivityTracker
import tv.own.owntv.core.sync.EpgActivityTracker
import tv.own.owntv.core.sync.SyncActivityTracker
import tv.own.owntv.core.sync.TrendingActivityTracker
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.util.classifySyncFailure
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.compactCount
import tv.own.owntv.ui.components.displayText

/**
 * Unobtrusive "sync running" pill (shell overlay, bottom middle). Reflects BOTH catalog syncs (a
 * backgrounded first import, the movies/series remainder worker, auto refresh — all funnel through
 * SyncManager → [SyncActivityTracker]) AND EPG/guide syncs (manual resync from Settings, auto startup
 * refresh — [EpgSyncWorker] → [EpgActivityTracker]). Semi-transparent, never focusable, renders nothing
 * when no sync is active. The shell hides it during fullscreen playback.
 *
 * **One line per running sync.** It used to show a single line for whichever sync happened to be
 * first, with the rest collapsed into "(+2 more) · EPG too" — so with a playlist and a guide running
 * together you could see neither one's progress. Two playlists syncing at once were entirely
 * indistinguishable. Now every active sync gets its own row with its own counter, capped at
 * [MAX_ROWS] so a many-source setup can't grow the overlay across the screen.
 */
@Composable
fun SyncStatusPill(modifier: Modifier = Modifier) {
    val catalogTracker: SyncActivityTracker = koinInject()
    val epgTracker: EpgActivityTracker = koinInject()
    val trendingTracker: TrendingActivityTracker = koinInject()
    val downloadTracker: DownloadActivityTracker = koinInject()
    val recordingTracker: RecordingActivityTracker = koinInject()
    val connectivity: ConnectivityObserver = koinInject()
    val activeCatalog by catalogTracker.active.collectAsStateWithLifecycle()
    val activeEpg by epgTracker.active.collectAsStateWithLifecycle()
    val activeTrending by trendingTracker.active.collectAsStateWithLifecycle()
    val activeDownload by downloadTracker.active.collectAsStateWithLifecycle()
    val activeRecordings by recordingTracker.active.collectAsStateWithLifecycle()
    val lastCompleted by catalogTracker.lastCompleted.collectAsStateWithLifecycle()
    val lastTrendingCompleted by trendingTracker.lastCompleted.collectAsStateWithLifecycle()
    val sportsTracker: tv.own.owntv.core.sports.SportsActivityTracker = koinInject()
    val sportsRebuild by sportsTracker.rebuild.collectAsStateWithLifecycle()
    // A failed rebuild is said once, for 5 s, when it happens — not again each time the pill returns.
    var sportsSeen by remember { mutableStateOf(sportsRebuild) }
    var sportsFailed by remember { mutableStateOf(false) }
    LaunchedEffect(sportsRebuild) {
        val was = sportsSeen
        sportsSeen = sportsRebuild
        if (sportsRebuild == tv.own.owntv.core.sports.RebuildState.Failed && was is tv.own.owntv.core.sports.RebuildState.Running) {
            sportsFailed = true
            delay(5000)
            sportsFailed = false
        }
    }
    val named = rememberPillNamedTitles(downloadTracker, recordingTracker)


    val completedQueue = remember { mutableStateListOf<SyncActivityTracker.CompletedSync>() }
    var currentCompleted by remember { mutableStateOf<SyncActivityTracker.CompletedSync?>(null) }
    var currentTrendingCompleted by remember { mutableStateOf<TrendingActivityTracker.CompletedBuild?>(null) }

    val anyActive = activeCatalog.isNotEmpty() || activeEpg.isNotEmpty() || activeTrending.isNotEmpty() ||
        activeDownload != null || activeRecordings.isNotEmpty() || sportsRebuild is tv.own.owntv.core.sports.RebuildState.Running

    LaunchedEffect(lastCompleted) {
        val completed = lastCompleted ?: return@LaunchedEffect
        if (completedQueue.none { it.timestamp == completed.timestamp && it.sourceId == completed.sourceId }) {
            completedQueue.add(completed)
        }
    }

    LaunchedEffect(lastTrendingCompleted) {
        val completed = lastTrendingCompleted ?: return@LaunchedEffect
        currentTrendingCompleted = completed
        trendingTracker.consumeCompleted(completed.timestamp)
    }

    LaunchedEffect(currentCompleted, completedQueue.size) {
        if (currentCompleted == null && completedQueue.isNotEmpty()) {
            val next = completedQueue.removeAt(0)
            currentCompleted = next
            // Keep the just-finished catalog row visible while its follow-up Trending build runs;
            // this makes the two backend phases readable together instead of replacing one another.
            catalogTracker.consumeCompleted(next.timestamp)
        }
    }

    LaunchedEffect(currentCompleted) {
        if (currentCompleted != null) {
            delay(5000)
            currentCompleted = null
        }
    }

    LaunchedEffect(currentTrendingCompleted) {
        if (currentTrendingCompleted != null) {
            delay(5000)
            currentTrendingCompleted = null
        }
    }

    if (!anyActive && currentCompleted == null && currentTrendingCompleted == null && !sportsFailed) return

    // Catalog syncs first (they're the slower, more interesting ones), then guides, in a stable
    // order so a row doesn't jump around as progress updates arrive.
    val rows = buildList<SyncLine> {
        // Recordings first, always (D13). They are time-critical and unrecoverable — a sync that is
        // collapsed into "+N more" can be watched again in a minute, a programme that is being
        // recorded cannot. Then downloads, which are deliberately started and time-limited. Then the
        // background syncs, which are the ones that can afford to be hidden.
        activeRecordings.values.sortedBy { it.id }.forEach { add(SyncLine.Recording(it, it.id in named.recordings)) }
        activeDownload?.let { add(SyncLine.Download(it, it.id in named.downloads)) }
        activeCatalog.values.sortedBy { it.sourceId }.forEach { add(SyncLine.Catalog(it)) }
        activeTrending.values.sortedBy { it.sourceId }.forEach {
            add(SyncLine.Trending(it))
            add(SyncLine.TrendingDetail(it))
        }
        activeEpg.values.sortedBy { it.sourceId }.forEach { add(SyncLine.Epg(it)) }
        if (sportsRebuild is tv.own.owntv.core.sports.RebuildState.Running) add(SyncLine.Sports)
    }
    val shown = rows.take(MAX_ROWS)
    val hidden = rows.size - shown.size
    val lineCount = shown.size + (if (hidden > 0) 1 else 0) +
        (if (currentCompleted != null) 1 else 0) + (if (currentTrendingCompleted != null) 2 else 0) + (if (sportsFailed) 1 else 0)

    // The Stage glass card (as the top-right pills): fully round for one line, a rounded card for a stack.
    val radius = if (lineCount > 1) 22.mpx else 30.mpx
    Column(
        modifier = modifier
            .padding(bottom = 28.mpx)
            .widthIn(max = 1100.mpx)
            .stageGlass(radius, overContent = true)
            .padding(horizontal = 24.mpx, vertical = 12.mpx),
        verticalArrangement = Arrangement.spacedBy(6.mpx),
    ) {
        shown.forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OwnTVSpinner(sizeDp = 14)
                Text(
                    line.text(),
                    style = stageText(16, 600),
                    color = StageColors.ItemText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hidden > 0) {
            Text(
                pluralStringResource(R.plurals.sync_status_more, hidden, hidden),
                style = stageText(16, 600),
                color = StageColors.ItemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        currentCompleted?.let { completed ->
            Text(
                completedLine(completed, online = connectivity.isOnlineNow()),
                style = stageText(16, 600),
                color = StageColors.ItemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (sportsFailed) {
            Text(
                stringResource(R.string.sports_rebuild_failed),
                style = stageText(16, 600),
                color = StageColors.ItemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        currentTrendingCompleted?.let { completed ->
            Text(
                trendingCompletedLine(completed),
                style = stageText(16, 600),
                color = StageColors.ItemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                trendingCompletedDetailLine(completed),
                style = stageText(16, 600),
                color = StageColors.ItemText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The `|| count > 0` on each active flag mirrors ManageSourcesScreen: the syncer clears a phase's
 * active flag when it moves on, and without this a finished phase's count would vanish from the line
 * mid-sync. Counts are cumulative, so a type that never runs (M3U movies/series) stays at 0 and is
 * left out.
 */
private sealed interface SyncLine {
    data class Catalog(val sync: SyncActivityTracker.ActiveSync) : SyncLine
    /** [named]: the title may show; false while its category is locked and sealed. */
    data class Download(val download: DownloadActivityTracker.ActiveDownload, val named: Boolean) : SyncLine
    data class Recording(val recording: tv.own.owntv.core.recording.RecordingProgress, val named: Boolean) : SyncLine
    data class Trending(val build: TrendingActivityTracker.ActiveBuild) : SyncLine
    data class TrendingDetail(val build: TrendingActivityTracker.ActiveBuild) : SyncLine
    data class Epg(val sync: EpgActivityTracker.ActiveEpgSync) : SyncLine
    data object Sports : SyncLine
}

@Composable
private fun SyncLine.text(): String = when (this) {
    SyncLine.Sports -> stringResource(R.string.sports_rebuilding)
    is SyncLine.Catalog -> {
        val stage = sync.stage
        val counts = stage?.let {
            SyncProgressCounts(
                live = it.liveProcessed,
                movies = it.moviesProcessed,
                series = it.seriesProcessed,
                liveActive = it.liveActive || it.liveProcessed > 0,
                moviesActive = it.moviesActive || it.moviesProcessed > 0,
                seriesActive = it.seriesActive || it.seriesProcessed > 0,
            )
        }
        val countsText = counts?.displayText().orEmpty()
        if (countsText.isBlank()) stringResource(R.string.sync_status_catalog, sync.sourceName)
        else stringResource(R.string.sync_status_catalog_with_counts, sync.sourceName, countsText)
    }
    is SyncLine.Epg -> {
        val context = LocalContext.current
        val countParts = buildList {
            if (sync.channels > 0) add(pluralStringResource(R.plurals.sync_count_channels, sync.channels, compactCount(context, sync.channels)))
            if (sync.programmes > 0) add(pluralStringResource(R.plurals.sync_count_epg, sync.programmes, compactCount(context, sync.programmes)))
        }
        val countsText = countParts.joinToString(stringResource(R.string.sync_counts_separator))
        if (countsText.isBlank()) stringResource(R.string.sync_status_epg, sync.sourceName)
        else stringResource(R.string.sync_status_epg_with_counts, sync.sourceName, countsText)
    }
    // No percentage: a recording ends on the clock, not on a byte count, so there is nothing to be
    // a percentage of.
    // With the size as it grows: a recording has no total to count towards, so the bytes already
    // written are the only sign it is moving rather than stuck.
    is SyncLine.Recording -> listOfNotNull(
        if (named) stringResource(R.string.recording_pill_line, recording.title) else stringResource(R.string.recording_pill_line_locked),
        recording.bytes.takeIf { it > 0 }
            ?.let { stringResource(R.string.common_size_mb, recordingSizeMb(it)) },
    ).joinToString(stringResource(R.string.content_epg_bits_separator))
    is SyncLine.Download -> {
        val percent = download.progress?.let { (it * 100).toInt() }
        when {
            percent == null -> if (named) stringResource(R.string.sync_status_download, download.title)
                else stringResource(R.string.sync_status_download_locked)
            named -> stringResource(R.string.sync_status_download_with_progress, download.title, percent)
            else -> stringResource(R.string.sync_status_download_locked_with_progress, percent)
        }
    }
    is SyncLine.Trending -> when (build.stage) {
        TrendingActivityTracker.Stage.STARTING -> stringResource(R.string.sync_status_trending_starting, build.sourceName)
        TrendingActivityTracker.Stage.RECEIVED -> pluralStringResource(
            R.plurals.sync_status_trending_received,
            build.candidates,
            build.candidates,
        )
        TrendingActivityTracker.Stage.PREPARING -> stringResource(R.string.sync_status_trending_preparing)
        TrendingActivityTracker.Stage.MATCHING_MOVIES -> stringResource(R.string.sync_status_trending_matching_movies)
        TrendingActivityTracker.Stage.MATCHING_SERIES -> stringResource(R.string.sync_status_trending_matching_series)
        TrendingActivityTracker.Stage.LOADING_SEASONS -> stringResource(R.string.sync_status_trending_loading_seasons)
        TrendingActivityTracker.Stage.ENRICHING -> pluralStringResource(
            R.plurals.sync_status_trending_building,
            build.matched,
            build.matched,
        )
        TrendingActivityTracker.Stage.PUBLISHING -> pluralStringResource(
            R.plurals.sync_status_trending_publishing,
            build.matched,
            build.matched,
        )
    }
    is SyncLine.TrendingDetail -> when (build.stage) {
        TrendingActivityTracker.Stage.STARTING -> stringResource(R.string.sync_status_trending_detail_waiting)
        TrendingActivityTracker.Stage.RECEIVED -> stringResource(
            R.string.sync_status_trending_detail_received,
            build.movieCandidates,
            build.seriesCandidates,
        )
        TrendingActivityTracker.Stage.PREPARING -> stringResource(
            R.string.sync_status_trending_detail_preparing,
            build.preparationProcessed,
            build.preparationTotal,
        )
        TrendingActivityTracker.Stage.MATCHING_MOVIES -> stringResource(
            R.string.sync_status_trending_detail_movies,
            build.movieChecked,
            build.movieCandidates,
            build.movieMatches,
            build.movieTarget,
        )
        TrendingActivityTracker.Stage.MATCHING_SERIES -> stringResource(
            R.string.sync_status_trending_detail_series,
            build.seriesChecked,
            build.seriesCandidates,
            build.seriesMatches,
            build.seriesTarget,
        )
        TrendingActivityTracker.Stage.LOADING_SEASONS -> stringResource(
            R.string.sync_status_trending_detail_loading_seasons,
            build.seasonsProcessed,
            build.seasonsTotal,
        )
        TrendingActivityTracker.Stage.ENRICHING,
        TrendingActivityTracker.Stage.PUBLISHING -> stringResource(
            R.string.sync_status_trending_detail_selected,
            build.movieMatches,
            build.seriesMatches,
            build.finalItems,
        )
    }
}

@Composable
private fun completedLine(completed: SyncActivityTracker.CompletedSync, online: Boolean): String = when (val result = completed.result) {
    is SyncResult.Success -> {
        val changes = buildList {
            if (result.categoriesAdded > 0) {
                add(pluralStringResource(R.plurals.sync_categories_added, result.categoriesAdded, result.categoriesAdded))
            }
            if (result.categoriesRemoved > 0) {
                add(pluralStringResource(R.plurals.sync_categories_removed, result.categoriesRemoved, result.categoriesRemoved))
            }
        }.joinToString(stringResource(R.string.sync_counts_separator))
        if (changes.isBlank()) stringResource(R.string.sync_status_complete, completed.sourceName)
        else stringResource(R.string.sync_status_complete_with_changes, completed.sourceName, changes)
    }
    is SyncResult.Failed -> stringResource(
        R.string.sync_status_failed,
        completed.sourceName,
        classifySyncFailure(result.message, online).displayText(),
    )
    SyncResult.Cancelled -> stringResource(R.string.sync_status_cancelled, completed.sourceName)
}

@Composable
private fun trendingCompletedLine(completed: TrendingActivityTracker.CompletedBuild): String = when {
    completed.preservedFailure -> stringResource(R.string.sync_status_trending_preserved)
    completed.eligible -> pluralStringResource(
        R.plurals.sync_status_trending_ready,
        completed.itemCount,
        completed.itemCount,
    )
    else -> pluralStringResource(
        R.plurals.sync_status_trending_below_minimum,
        completed.itemCount,
        completed.itemCount,
    )
}

@Composable
private fun trendingCompletedDetailLine(completed: TrendingActivityTracker.CompletedBuild): String = stringResource(
    R.string.sync_status_trending_detail_completed,
    completed.movieMatches,
    completed.movieCandidates,
    completed.seriesMatches,
    completed.seriesCandidates,
)


/** Beyond this many concurrent syncs the pill summarises the rest, rather than covering the screen. */
private const val MAX_ROWS = 4

/** Megabytes to one decimal, formatted for the locale. */
private fun recordingSizeMb(bytes: Long): String =
    java.text.NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(bytes / 1_048_576.0)
