@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package tv.own.owntv.features.multiview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.player.CornerState
import tv.own.owntv.player.SecondaryVideoSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * MultiView — up to four live streams on screen at once, in either an equal **grid** or a **dominant**
 * (one large + a small strip) layout. D-pad focus moves between tiles; the focused tile is the only audible
 * one. Pressing OK on a tile promotes it to the dominant layout. Back exits (the controller releases every
 * decoder). Each tile renders a [SecondaryVideoSurface] — the constrained second-decoder pattern from PiP.
 */
@Composable
fun MultiViewScreen(
    controller: MultiViewController,
    addableChannels: List<ChannelEntity>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles by controller.tiles.collectAsStateWithLifecycle()
    val activeIndex by controller.activeIndex.collectAsStateWithLifecycle()
    val layout by controller.layout.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    BackHandler { if (picking) picking = false else onExit() }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        when {
            tiles.isEmpty() -> Unit
            layout == MultiLayout.DOMINANT && tiles.size > 1 ->
                DominantLayout(tiles, activeIndex, controller)
            else -> GridLayout(tiles, activeIndex, controller)
        }

        // Bottom control row: layout toggle, add a stream (if there's room on this device), exit.
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(if (layout == MultiLayout.GRID) "▦ Grid" else "▣ Dominant") { controller.toggleLayout() }
            if (tiles.size < controller.maxTiles) BarButton("＋ Add stream") { picking = true }
            BarButton("✕ Exit") { onExit() }
        }

        if (picking) {
            AddStreamPicker(
                channels = addableChannels.filter { ch -> tiles.none { it.id == ch.id } },
                onPick = { controller.addTile(it); picking = false },
                onDismiss = { picking = false },
            )
        }
    }
}

/** A small focusable text button for the MultiView control bar. */
@Composable
private fun BarButton(label: String, onClick: () -> Unit) {
    tv.own.owntv.ui.components.FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.08f,
        focusedContainerColor = Color.White.copy(alpha = 0.30f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.14f),
        selectedContainerColor = Color.White.copy(alpha = 0.14f),
    ) { _ ->
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** Pick another channel to add as a tile — sourced from the caller's recently-watched list (no new data). */
@Composable
private fun AddStreamPicker(channels: List<ChannelEntity>, onPick: (ChannelEntity) -> Unit, onDismiss: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (channels.isNotEmpty()) runCatching { firstFocus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.5f).clip(RoundedCornerShape(16.dp)).background(OwnTVTheme.colors.surfaceContainerHigh).padding(20.dp),
        ) {
            Text("Add a stream", style = MaterialTheme.typography.titleLarge, color = OwnTVTheme.colors.onSurface)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
            if (channels.isEmpty()) {
                Text("No recent channels to add.", style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant)
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    androidx.compose.foundation.lazy.items(channels, key = { it.id }) { ch ->
                        tv.own.owntv.ui.components.FocusableSurface(
                            onClick = { onPick(ch) },
                            modifier = if (ch.id == channels.first().id) Modifier.fillMaxWidth().focusRequester(firstFocus) else Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { _ ->
                            Text(ch.name, style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.onSurface, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Equal-size tiles: 1 = full, 2 = side-by-side, 3 = two over one, 4 = 2×2. */
@Composable
private fun GridLayout(tiles: List<ChannelEntity>, activeIndex: Int, controller: MultiViewController) {
    val gap = 4.dp
    when (tiles.size) {
        1 -> TileRow(tiles, 0, activeIndex, controller, Modifier.fillMaxSize())
        2 -> TileRow(tiles, 0, activeIndex, controller, Modifier.fillMaxSize(), count = 2)
        3 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            TileRow(tiles, 0, activeIndex, controller, Modifier.fillMaxWidth().weight(1f), count = 2)
            TileRow(tiles, 2, activeIndex, controller, Modifier.fillMaxWidth().weight(1f), count = 1)
        }
        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            TileRow(tiles, 0, activeIndex, controller, Modifier.fillMaxWidth().weight(1f), count = 2)
            TileRow(tiles, 2, activeIndex, controller, Modifier.fillMaxWidth().weight(1f), count = 2)
        }
    }
}

/** A row of [count] tiles starting at [start]. */
@Composable
private fun TileRow(
    tiles: List<ChannelEntity>,
    start: Int,
    activeIndex: Int,
    controller: MultiViewController,
    modifier: Modifier,
    count: Int = 1,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (offset in 0 until count) {
            val index = start + offset
            if (index in tiles.indices) {
                Tile(index, tiles[index], active = index == activeIndex, controller, Modifier.fillMaxHeight().weight(1f))
            }
        }
    }
}

/** Dominant layout: the active tile fills the left ~3/4; the others stack down the right ~1/4. */
@Composable
private fun DominantLayout(tiles: List<ChannelEntity>, activeIndex: Int, controller: MultiViewController) {
    val big = activeIndex.coerceIn(0, tiles.lastIndex) // guard against a transient tiles/active mismatch
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tile(big, tiles[big], active = true, controller, Modifier.fillMaxHeight().weight(3f))
        Column(Modifier.fillMaxHeight().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tiles.forEachIndexed { i, ch ->
                if (i != big) Tile(i, ch, active = false, controller, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** One tile: the stream's video, a title strip, and a focus/active border. Focus → audio; OK → make dominant. */
@Composable
private fun Tile(
    index: Int,
    channel: ChannelEntity,
    active: Boolean,
    controller: MultiViewController,
    modifier: Modifier,
) {
    val engine = remember(index) { controller.engineAt(index) }
    val state by engine.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    // Pull focus to the active tile when MultiView opens / the active tile changes.
    LaunchedEffect(active) { if (active) runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(
                width = if (active) 3.dp else 1.dp,
                color = if (active) OwnTVTheme.colors.primary else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) controller.setActive(index) }
            // clickable is itself focusable, so it's the single focus target (OK = make this tile dominant).
            .clickable { controller.promoteToDominant(index) },
    ) {
        if (state == CornerState.ERROR) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Couldn't play", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        } else {
            SecondaryVideoSurface(engine = engine, modifier = Modifier.fillMaxSize())
        }
        if (state == CornerState.LOADING) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                tv.own.owntv.ui.components.OwnTVSpinner(sizeDp = 22)
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (active) "🔊" else "",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
            Text(channel.name, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
        }
    }
}
