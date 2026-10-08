package tv.own.owntv.features.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.recording.RecordingRules
import tv.own.owntv.features.downloads.StageAction
import tv.own.owntv.features.downloads.StageDownloadRow
import tv.own.owntv.features.downloads.StageListLabel
import tv.own.owntv.features.downloads.StageReady
import tv.own.owntv.features.downloads.StatusLine
import tv.own.owntv.features.downloads.fileSize
import tv.own.owntv.features.downloads.recordingWhen
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent

/**
 * Everything the recorder has done, is doing, or could not do — the Downloads screen's Recordings tab.
 *
 * Five groups rather than the two downloads have, and the last is the one that matters: **Missed**.
 * A download that fails can be tried again tomorrow; a live programme that was not recorded is gone,
 * so "it did not happen, and here is why" is a result the user has to be told rather than an error
 * to retry. That is why every missed and failed row carries its reason in words.
 *
 * Cancelled recordings are not shown at all. The user cancelled it; a list that keeps reminding them
 * of something they deliberately called off is a list they stop reading.
 */
internal fun recordingGroups(recordings: List<RecordingEntity>): List<Pair<Int, List<RecordingEntity>>> = listOf(
    R.string.recording_group_now to recordings.filter { it.status == RecordingStatus.RECORDING },
    // Soonest first: a scheduled list is read forwards, unlike the finished ones.
    R.string.recording_group_scheduled to recordings.filter { it.status == RecordingStatus.SCHEDULED }.sortedBy { it.startMs },
    R.string.recording_group_completed to recordings.filter { it.status == RecordingStatus.COMPLETED },
    R.string.recording_group_failed to recordings.filter { it.status == RecordingStatus.FAILED },
    R.string.recording_group_missed to recordings.filter { it.status == RecordingStatus.MISSED },
).filter { it.second.isNotEmpty() }

/** The groups as `.dl` rows under their labels. One action per state, never more than the state can honestly offer. */
internal fun LazyListScope.recordingItems(
    recordings: List<RecordingEntity>,
    focusOf: (Long) -> FocusRequester,
    onFocused: (Long) -> Unit,
    onPlay: (RecordingEntity) -> Unit,
    onStop: (RecordingEntity) -> Unit,
    onCancel: (RecordingEntity) -> Unit,
    onRetry: (RecordingEntity) -> Unit,
    onDelete: (RecordingEntity) -> Unit,
) {
    recordingGroups(recordings).forEachIndexed { g, (labelRes, rows) ->
        item(key = "rhdr_$labelRes") { StageListLabel(stringResource(labelRes), first = g == 0) }
        items(rows, key = { "r_${it.id}" }) { r ->
            val delete = StageAction(stringResource(R.string.common_delete), OwnTVIcon.TRASH) { onDelete(r) }
            StageDownloadRow(
                title = r.title,
                line = recordingLine(r),
                path = r.filePath,
                onFocused = { onFocused(r.id) },
                focus = focusOf(r.id),
                actions = when (r.status) {
                    RecordingStatus.RECORDING -> listOf(StageAction(stringResource(R.string.recording_stop), OwnTVIcon.CLOSE) { onStop(r) }, delete)
                    // Nothing to delete while it is only scheduled: there is no file yet, and Cancel means "do not do this".
                    RecordingStatus.SCHEDULED -> listOf(StageAction(stringResource(R.string.common_cancel), OwnTVIcon.CLOSE) { onCancel(r) })
                    RecordingStatus.COMPLETED -> listOf(delete)
                    RecordingStatus.FAILED, RecordingStatus.MISSED -> listOf(StageAction(stringResource(R.string.common_retry), OwnTVIcon.REFRESH) { onRetry(r) }, delete)
                    RecordingStatus.CANCELLED -> emptyList()
                },
                // A recording still being written is not playable here: two readers on one growing file.
                onClick = if (r.status == RecordingStatus.COMPLETED) ({ onPlay(r) }) else null,
                art = {
                    // The channel's logo on its plate, as everywhere a channel is named.
                    Box(Modifier.fillMaxSize().background(Color(0xFF0F1518)).padding(14.mpx), contentAlignment = Alignment.Center) {
                        tv.own.owntv.features.live.LivePlate(r.channelIconUrl, 162.mpx, 79.mpx)
                    }
                },
            ) { RecordingStatusText(r) }
        }
    }
}

/** "Sky Cinema · 95 minutes · 840.2 MB" — the channel, and for a finished one how long it ran and what it cost. */
@Composable
private fun recordingLine(r: RecordingEntity): String = buildList {
    add(r.channelName)
    if (r.status == RecordingStatus.COMPLETED) {
        val minutes = recordedMinutes(r)
        add(pluralStringResource(R.plurals.recording_minutes, minutes, minutes))
    }
    if (r.bytes > 0) add(fileSize(r.bytes))
}.joinToString(stringResource(R.string.content_epg_bits_separator))

/** Where the recording got to, or why it never did — the reason in words, not a code. */
@Composable
private fun RecordingStatusText(r: RecordingEntity) {
    when (r.status) {
        RecordingStatus.RECORDING -> StatusLine(stringResource(R.string.recording_group_now), stageAccent.accent)
        RecordingStatus.SCHEDULED -> StatusLine(recordingWhen(r.programmeStartMs))
        RecordingStatus.COMPLETED -> StageReady()
        RecordingStatus.FAILED, RecordingStatus.MISSED -> StatusLine(
            RecordingRules.displayTextOf(r.failure, LocalContext.current.resources) ?: stringResource(R.string.recording_failed_unknown),
            Color(0xFFEF4444),
        )
        RecordingStatus.CANCELLED -> Unit
    }
}

/**
 * How many minutes were actually captured — from the clock where the recorder wrote one, and from
 * the programme's own length where it did not. Never zero: a recording that exists ran for some part
 * of a minute, and "0 minutes" would read as a failure that it is not.
 */
private fun recordedMinutes(recording: RecordingEntity): Int {
    val started = recording.startedAt
    val ended = recording.endedAt
    val span = if (started != null && ended != null && ended > started) {
        ended - started
    } else {
        recording.programmeStopMs - recording.programmeStartMs
    }
    return (span / 60_000L).toInt().coerceAtLeast(1)
}
