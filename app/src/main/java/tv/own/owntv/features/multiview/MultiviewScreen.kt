package tv.own.owntv.features.multiview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.displayText
import tv.own.owntv.player.ExoPreviewSurface
import tv.own.owntv.player.PlaybackFailure
import tv.own.owntv.player.describe
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * The Multiview grid: up to four live channels at once, grown from the one already playing.
 *
 * Tile 1 arrives filled — it is the channel the user pressed the button on — and the rest say
 * "+ Add channel" until they are given one. Nothing here decides whether a tile *may* tune; that is
 * [MultiviewState] asking core's connection budget, and a refused tile draws the sentence it got
 * back rather than a spinner (D5: the grid never comes down because one tile could not start).
 *
 * | Press | Does |
 * | --- | --- |
 * | D-pad | move between tiles |
 * | OK on a filled tile | give it the sound |
 * | OK on an empty tile | open the channel list |
 * | long OK, or MENU | the tile menu — fullscreen is the first thing in it |
 * | Back | leave, stopping every tile |
 *
 * Long OK opens the menu because that is what a long press does everywhere else in this app — the
 * channel list, the guide, Customize — and because most TV remotes have no MENU key at all, which
 * left the menu unreachable on the owner's television.
 */
@UnstableApi
@Composable
fun MultiviewScreen(
    state: MultiviewState,
    onPickChannel: (tile: Int) -> Unit,
    onFullscreen: (ChannelEntity) -> Unit,
    /** Back. Takes no channel: leaving the grid now stops playback rather than promoting a tile. */
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val focusRequesters = remember(state.tiles.size) { List(state.tiles.size) { FocusRequester() } }

    LaunchedEffect(Unit) { runCatching { focusRequesters[state.focused].requestFocus() } }
    BackHandler(enabled = menuFor == null) { onExit() }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // Two rows of two. With two tiles the second row is empty and never drawn, which is the
        // side-by-side layout the plan asks for without a second code path.
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TileGap)) {
            state.tiles.indices.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    row.forEach { index ->
                        Tile(
                            state = state,
                            index = index,
                            focusRequester = focusRequesters[index],
                            onPickChannel = { onPickChannel(index) },
                            onMenu = { menuFor = index },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                    // An odd tile count leaves the last cell blank rather than stretching its neighbour
                    // across the screen, so the grid stays a grid.
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }

    menuFor?.let { tile ->
        MultiviewTileMenu(
            filled = state.tiles[tile].channel != null,
            soundOnly = tile in state.soundOnly,
            onAddTile = if (state.canAddTile) {
                { menuFor = null; state.addTile(); state.focus(state.tiles.lastIndex) }
            } else {
                null
            },
            onChangeChannel = { menuFor = null; onPickChannel(tile) },
            onFullscreen = { menuFor = null; state.tiles[tile].channel?.let(onFullscreen) },
            onSound = { menuFor = null; state.giveSoundTo(tile) },
            onSoundOnly = { menuFor = null; state.setSoundOnly(tile, tile !in state.soundOnly) },
            onRemove = { menuFor = null; state.clear(tile) },
            onDismiss = { menuFor = null },
        )
    }
}

@UnstableApi
@Composable
private fun Tile(
    state: MultiviewState,
    index: Int,
    focusRequester: FocusRequester,
    onPickChannel: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val tile = state.tiles[index]
    val focused = state.focused == index
    // Long OK is a key-down held past the system's long-press threshold; Compose reports the repeat
    // as another KeyDown, which is what separates "give it the sound" from "open the tile menu".
    var okDownAt by remember { mutableStateOf(0L) }

    Box(
        modifier
            .clip(TileShape)
            .background(colors.surfaceContainerLowest)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) colors.primary else colors.surfaceContainerHigh,
                shape = TileShape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) state.focus(index) }
            .focusable()
            .onKeyEvent { event ->
                when {
                    event.key == Key.Menu && event.type == KeyEventType.KeyDown -> { onMenu(); true }
                    event.key != Key.DirectionCenter && event.key != Key.Enter -> false
                    event.type == KeyEventType.KeyDown -> {
                        if (okDownAt == 0L) okDownAt = System.currentTimeMillis()
                        true
                    }
                    event.type == KeyEventType.KeyUp -> {
                        val held = System.currentTimeMillis() - okDownAt
                        okDownAt = 0L
                        when {
                            // Held: the menu, on a filled tile or an empty one, exactly as MENU did.
                            held >= LongPressMs -> onMenu()
                            tile.channel == null -> onPickChannel()
                            else -> state.giveSoundTo(index)
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        val channel = tile.channel
        val refusal = tile.refusal
        when {
            channel != null -> {
                // A sound-only tile has no picture to draw by design, so the tile says what it is
                // instead of showing the black rectangle that would otherwise read as a fault.
                val engine = state.pool.engineFor(index)
                val engineState by engine.state.collectAsState()
                val failure by engine.error.collectAsState()
                // A tile whose picture stops while the rest of the grid plays on. The provider was
                // already asked and said yes, so this is the television running out of what it has —
                // and the tile says that, rather than the eighty seconds of silent black the engine's
                // own reconnect ladder would spend before admitting anything.
                LaunchedEffect(index, channel.id) {
                    engine.isPlaying.collectLatest { playing ->
                        if (playing) return@collectLatest
                        delay(StreamLostGraceMs)
                        // An engine that has diagnosed itself is left alone: the ERROR branch below
                        // prints its own words, which are more specific than anything guessed here.
                        if (engine.error.value != null) return@collectLatest
                        if (!engine.isPlaying.value) state.refuseDeviceLimit(index)
                    }
                }
                // The picture failing does not stop the audio track, so a tile showing its failure
                // went on making a noise from a channel nobody could see.
                LaunchedEffect(engineState, index) {
                    if (engineState == LivePreviewEngine.State.ERROR) state.silenceFailed(index)
                }
                when {
                    index in state.soundOnly -> Text(
                        text = stringResource(R.string.multiview_sound_only_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    // A tile whose stream died must say so. It used to draw the surface regardless,
                    // which on a provider that quietly cut the stream left a black rectangle and no
                    // explanation — indistinguishable, to the user, from the app being broken.
                    engineState == LivePreviewEngine.State.ERROR -> Text(
                        text = tileFailureText(failure = failure),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    // Tiles draw through a TextureView, not the hardware video plane a SurfaceView
                    // takes: a television has very few of those and several have one, which is why a
                    // second tile could end up with sound and no picture while the first kept both.
                    else -> ExoPreviewSurface(
                        engine = engine,
                        modifier = Modifier.fillMaxSize(),
                        useTextureView = true,
                        // The grid is watched, not read: without this the screensaver started mid-match.
                        keepAwake = true,
                    )
                }
                TileCaption(name = channel.name, audible = state.audible == index)
            }
            // The television, not the provider: it had already allowed this stream.
            tile.deviceLimit -> Text(
                text = stringResource(R.string.multiview_decoder_exhausted),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            refusal != null -> {
                // The honest reason, in the tile, with the rest of the grid still playing.
                val text = refusal.displayText(LocalContext.current.resources)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            else -> {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OwnTVIcon(
                        OwnTVIcon.ADD,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = stringResource(R.string.multiview_add_channel),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * What a failed tile says: the engine's own diagnosis, and a plain fallback when it has none.
 *
 * It deliberately never invents a number of provider connections. An earlier version did, reading a
 * stopped tile as proof of the provider's limit, and it was wrong every time it fired — including
 * telling the owner his provider allowed three streams while three of them played beside the
 * message. Whether the provider allows this is settled before a tile tunes, by a measurement; it is
 * not something to be re-guessed from a stall.
 */
@Composable
private fun tileFailureText(failure: PlaybackFailure?): String = when {
    failure == PlaybackFailure.DecoderExhausted -> stringResource(R.string.multiview_decoder_exhausted)
    failure != null -> {
        LocalConfiguration.current // so a language change recomposes this, as stringResource would
        val resources = LocalContext.current.resources
        failure.describe { id, args -> resources.getString(id, *args.toTypedArray()) }
    }
    else -> stringResource(R.string.player_error_channel)
}

/** The channel's name, and the speaker badge on whichever tile the sound is coming from. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.TileCaption(name: String, audible: Boolean) {
    val colors = OwnTVTheme.colors
    val audioTileLabel = stringResource(R.string.multiview_audio_tile)
    Row(
        Modifier
            .align(Alignment.BottomStart)
            .padding(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (audible) {
            OwnTVIcon(
                OwnTVIcon.VOLUME_HIGH,
                tint = colors.primary,
                modifier = Modifier
                    .size(14.dp)
                    .semantics { contentDescription = audioTileLabel },
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val TileGap = 6.dp
private val TileShape = RoundedCornerShape(10.dp)

/** How long OK must be held for "open the tile menu" rather than "give it the sound". */
private const val LongPressMs = 500L

/**
 * How long a tile may be stopped before the grid calls it lost.
 *
 * Comfortably longer than the slowest tile open measured on the owner's television (2.9 s), so a
 * channel that is merely slow to start is never mistaken for one the provider refused — and far
 * shorter than the engine's own eight-attempt ladder, which takes over eighty seconds to conclude
 * anything and would leave a black tile and no explanation for all of it.
 */
private const val StreamLostGraceMs = 12_000L
