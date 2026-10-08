package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import tv.own.owntv.ui.theme.mpx
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.ui.components.trapAllFocusExit

/**
 * The Stage playlist switcher (P1-07): the options-menu style, 520 wide under the playlist pill, over
 * the Stage scrim with the pill itself redrawn above it, lit, at [pillBounds]. Rows: "All playlists"
 * with the layers icon, then each playlist with its mark and channel count; a ✓ marks the current one.
 */
@Composable
fun StagePlaylistMenu(
    playlists: List<SourceEntity>,
    activeId: Long,
    pillLabel: String,
    pillBounds: androidx.compose.ui.geometry.Rect?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val channelDao = org.koin.compose.koinInject<tv.own.owntv.core.database.dao.ChannelDao>()
    val counts by androidx.compose.runtime.produceState(emptyMap<Long, Int>(), playlists) {
        value = playlists.associate { it.id to runCatching { channelDao.countForSourceOnce(it.id) }.getOrDefault(0) }
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { java.text.NumberFormat.getIntegerInstance(locale) }
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().background(Color(2, 5, 6).copy(alpha = 0.55f))) {
        if (pillBounds != null) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            with(density) {
                tv.own.owntv.ui.stage.StagePill(
                    text = pillLabel,
                    icon = tv.own.owntv.ui.components.OwnTVIcon.LAYERS,
                    trailingIcon = tv.own.owntv.ui.components.OwnTVIcon.CHEVRON_DOWN,
                    onClick = {},
                    enabled = false,
                    highlighted = true,
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(pillBounds.left.toInt(), pillBounds.top.toInt()) }
                        .width(pillBounds.width.toDp()),
                )
            }
        }
        tv.own.owntv.ui.stage.StageMenu(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 104.mpx, end = 100.mpx, bottom = 24.mpx)
                .width(520.mpx)
                .trapAllFocusExit()
                .focusGroup(),
        ) {
            tv.own.owntv.ui.stage.StageMenuHeader(
                title = stringResource(R.string.content_playlist_picker_title),
                subtitle = stringResource(R.string.content_playlist_picker_subtitle),
            )
            // The list scrolls under a fixed heading: without it the menu ran off the bottom of the screen
            // at about 17 playlists, and the rest could not be reached (#6).
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                tv.own.owntv.ui.stage.StageMenuItem(
                    text = stringResource(R.string.content_all_playlists),
                    icon = tv.own.owntv.ui.components.OwnTVIcon.LAYERS,
                    checked = activeId <= 0,
                    onClick = { onSelect(-1L); onDismiss() },
                    modifier = if (activeId <= 0) Modifier.focusRequester(selectedFocus) else Modifier,
                )
                playlists.forEachIndexed { index, source ->
                    val current = source.id == activeId
                    tv.own.owntv.ui.stage.StageMenuItem(
                        text = source.name,
                        leading = { tv.own.owntv.ui.stage.StagePlaylistMark(tv.own.owntv.ui.stage.PlaylistMark.of(source.name, index)) },
                        value = if (current) null else counts[source.id]?.let(numbers::format),
                        checked = current,
                        onClick = { onSelect(source.id); onDismiss() },
                        modifier = if (current) Modifier.focusRequester(selectedFocus) else Modifier,
                    )
                }
            }
        }
    }
}
