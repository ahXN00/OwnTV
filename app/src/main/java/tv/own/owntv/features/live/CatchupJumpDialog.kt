package tv.own.owntv.features.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.live.CatchupJumps
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.format.rememberBestDateFormatter

/**
 * "Go back to…" — pick a point in a live channel's catch-up archive and start there.
 *
 * Rows are wall-clock times rather than "3 hours ago" on purpose: the user is looking for the news
 * that aired at 19:00, so a clock spares them the arithmetic, and it is what the guide-less catch-up
 * pickers on other TV players show. A row that lands on an earlier day carries its weekday, because
 * "19:00" alone cannot distinguish today from yesterday.
 *
 * Shared by both entry points — the Live TV long-press Catch-up dialog (when the channel has no
 * guide, so there are no programmes to list) and the fullscreen player's own button.
 */
@Composable
internal fun CatchupJumpRows(
    offsetsSec: List<Int>,
    firstFocus: FocusRequester,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // Last row: leave the suggestions behind and type an exact day + time. Null hides it.
    onChooseExact: (() -> Unit)? = null,
    // First row: back to the start of the programme on air, focused when the list opens. Its offset is
    // taken when pressed. Null hides it.
    programmeStart: CatchupProgrammeStart? = null,
) {
    // One "now" for the whole list: recomputing per row would let the clock tick between rows and
    // print two different times for the same offset.
    val nowMs = remember { System.currentTimeMillis() }
    val zone = remember { java.util.TimeZone.getDefault() }
    val timeOnly = rememberBestDateFormatter("Hm")
    val withDay = rememberBestDateFormatter("EEEHm")
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.mpx)) {
        if (programmeStart != null) item(key = "programme-start") {
            tv.own.owntv.ui.stage.StagePopupOption(
                title = if (programmeStart.title.isEmpty()) stringResource(R.string.content_catchup_jump_programme_start_untitled)
                else stringResource(R.string.content_catchup_jump_programme_start, programmeStart.title),
                subtitle = stringResource(R.string.content_catchup_jump_programme_start_at, timeOnly(programmeStart.startMs)),
                onClick = { onPick(programmeStart.offsetSec(System.currentTimeMillis())) },
                modifier = Modifier.focusRequester(firstFocus),
                chosen = true,
                leading = { tv.own.owntv.ui.stage.StagePopupIcon(tv.own.owntv.ui.components.OwnTVIcon.SKIP_PREVIOUS) },
            )
        }
        items(offsetsSec, key = { it }) { offset ->
            val at = CatchupJumps.instantFor(offset, nowMs)
            tv.own.owntv.ui.stage.StagePopupOption(
                title = if (CatchupJumps.crossesDay(offset, nowMs, zone)) withDay(at) else timeOnly(at),
                onClick = { onPick(offset) },
                modifier = if (programmeStart == null && offset == offsetsSec.first()) Modifier.focusRequester(firstFocus) else Modifier,
                leading = { tv.own.owntv.ui.stage.StagePopupIcon(tv.own.owntv.ui.components.OwnTVIcon.CATCHUP) },
            )
        }
        if (onChooseExact != null) item(key = "exact") {
            tv.own.owntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.content_catchup_jump_exact), onClick = onChooseExact, chosen = true,
                leading = { tv.own.owntv.ui.stage.StagePopupIcon(tv.own.owntv.ui.components.OwnTVIcon.CLOCK) },
            )
        }
    }
}

/** The standalone popup form, used by the player's "Go back to…" button. */
@Composable
internal fun CatchupJumpDialog(
    title: String,
    offsetsSec: List<Int>,
    windowSec: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    programmeStart: CatchupProgrammeStart? = null,
) {
    val firstFocus = remember { FocusRequester() }
    var manual by remember { mutableStateOf(false) }
    if (manual) {
        CatchupManualTimeDialog(
            windowSec = windowSec,
            onPick = { manual = false; onPick(it) },
            onDismiss = { manual = false },
        )
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() }
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = title,
        body = stringResource(R.string.content_catchup_jump_prompt),
        eyebrow = null,
        scroll = false,
        buttons = { tv.own.owntv.ui.stage.StageButton(stringResource(R.string.content_close), onClick = onDismiss, height = 56.mpx, textSize = 19) },
    ) {
        CatchupJumpRows(
            offsetsSec = offsetsSec,
            firstFocus = firstFocus,
            onPick = onPick,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            onChooseExact = { manual = true },
            programmeStart = programmeStart,
        )
    }
}
