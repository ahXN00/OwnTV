package tv.own.owntv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupIcon
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent

/**
 * The Move window, as Sports and leagues has it: ▲ ▼ choose a row, OK picks it up and puts it down, so
 * several can move in one go, and only Save or Cancel closes it. The row being carried also goes a page
 * with the paging keys (CH± by default) and to the first or last place on a long press of them. It
 * opens with the row it was asked for already picked up. [itemNames] is the caller's order, which
 * [onMove] changes.
 */
@Composable
fun MoveOrderOverlay(
    title: String,
    itemNames: List<String>,
    activeIndex: Int,
    onMove: (from: Int, to: Int) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val start = remember { activeIndex.coerceIn(0, itemNames.lastIndex.coerceAtLeast(0)) }
    // Positions in itemNames: the row being carried, and the row that has the focus.
    var picked by remember { mutableStateOf<Int?>(start) }
    var focusPos by remember { mutableIntStateOf(start) }
    var focusTick by remember { mutableIntStateOf(0) }
    // ▲ ▼ carry the row only from the list; from Cancel/Save they move the focus as usual.
    var listFocused by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    // The popup focuses its first row as it opens; until the row asked for is focused, that must not move [focusPos].
    var opened by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Brings the focus row on screen (a page jump can land far off it), then focuses it.
    LaunchedEffect(focusTick) {
        if (focusTick == 0) delay(60)
        val info = listState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.index == focusPos }
        val fullyVisible = row != null && row.offset >= info.viewportStartOffset && row.offset + row.size <= info.viewportEndOffset
        if (!fullyVisible) listState.scrollToItem((focusPos - 2).coerceAtLeast(0))
        withFrameNanos { }
        runCatching { rowFocus.requestFocus() }
        opened = true
    }
    // Carries the picked row to [pos], or moves only the focus when nothing is picked up.
    fun goTo(pos: Int) {
        if (itemNames.isEmpty()) return
        val to = pos.coerceIn(0, itemNames.lastIndex)
        picked?.let { from -> if (from != to) { onMove(from, to); picked = to } }
        focusPos = to
        focusTick++
    }
    StagePopup(
        onDismiss = onCancel,
        title = title,
        body = stringResource(R.string.sports_move_instructions),
        width = 760.mpx,
        scroll = false,
        modifier = Modifier
            .chNavPaging(
                enabled = chNavEnabled,
                upSkip = chNavUpSkip,
                downSkip = chNavDownSkip,
                isFocused = { true },
                lastIndex = { itemNames.lastIndex },
                currentTargetIndex = { picked ?: focusPos },
                onJumpToIndex = ::goTo,
            )
            .onPreviewKeyEvent { e ->
                val carried = picked?.takeIf { listFocused } ?: return@onPreviewKeyEvent false
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { goTo(carried - 1); true }
                    Key.DirectionDown -> { goTo(carried + 1); true }
                    else -> false
                }
            },
        buttons = {
            StageButton(stringResource(R.string.common_cancel), onClick = onCancel, height = 56.mpx, textSize = 19)
            StageButton(stringResource(R.string.common_save), onClick = onCommit, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        val accent = stageAccent.accent
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(top = 18.mpx)
                .onFocusChanged { listFocused = it.hasFocus },
            verticalArrangement = Arrangement.spacedBy(6.mpx),
        ) {
            itemsIndexed(itemNames) { idx, name ->
                val carried = picked == idx
                StagePopupOption(
                    title = name,
                    chosen = carried,
                    onClick = { picked = if (carried) null else idx },
                    modifier = (if (idx == focusPos) Modifier.focusRequester(rowFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused && opened) focusPos = idx },
                    leading = { _ -> StagePopupIcon(OwnTVIcon.MOVE, if (carried) accent else StageColors.Dim) },
                )
            }
        }
    }
}
