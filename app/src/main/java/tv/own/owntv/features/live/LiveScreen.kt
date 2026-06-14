package tv.own.owntv.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.components.SortChip
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.formatCount
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

/** Layer 2–4 for Live TV: real category rail, Paging channel list, and a live preview pane. */
@Composable
fun LiveScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean = true,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: LiveViewModel = koinViewModel()
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val previewChannel by vm.previewChannel.collectAsStateWithLifecycle()
    val nowNext by vm.nowNext.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val livePreviewSetting by vm.livePreviewEnabled.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    // Preview runs only when the player isn't busy (previewEnabled) AND the user hasn't turned it off.
    val effectivePreview = previewEnabled && livePreviewSetting

    // NOTE: do NOT stop the player when LiveScreen leaves composition — going fullscreen disposes
    // this screen, and stopping here would abort the stream that was just started. Playback is
    // stopped on fullscreen exit (shell BackHandler) instead.

    // In-pane preview: play the focused channel after the focus settles (700ms). Disabled while the
    // fullscreen/mini player owns the surface (previewEnabled=false) to avoid two surfaces fighting.
    LaunchedEffect(previewChannel?.id, effectivePreview) {
        if (!effectivePreview) return@LaunchedEffect
        val ch = previewChannel ?: return@LaunchedEffect
        delay(700)
        vm.playPreview(ch)
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    var renaming by remember { mutableStateOf<ChannelEntity?>(null) }
    var matchingEpg by remember { mutableStateOf<ChannelEntity?>(null) }
    // Returning from fullscreen: scroll to and focus the channel you were watching (waits for the list to load).
    LaunchedEffect(restoreFocus, channels.itemCount) {
        if (!restoreFocus || channels.itemCount == 0) return@LaunchedEffect
        val ch = previewChannel
        val idx = if (ch != null) channels.itemSnapshotList.items.indexOfFirst { it.id == ch.id } else -1
        if (idx >= 0) {
            runCatching { listState.scrollToItem(idx) }
            delay(60)
            runCatching { selFocus.requestFocus() }
        }
        onRestored()
    }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)

    Row(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onChildFocused() },
    ) {
        CategoryRail(
            categories = railItems.map { RailCategory(it.abbr, it.title, it.icon) },
            selectedIndex = selectedIndex,
            onSelect = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
            // Focusing a folder stops the in-pane preview — but only when a preview is actually running.
            // When the player is docked (live PiP) or fullscreen, previewEnabled is false and stopPreview
            // would kill that stream (e.g. while navigating left to leave Live), so we skip it.
            onFocused = { if (previewEnabled) vm.stopPreview() },
        )

        // Layer 3 — header + channel list
        Column(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxSize()
                // Entering this pane (from the rail or the preview) must land on a channel row, never
                // the search bar: prefer the last-focused channel, else the first row. onEnter fires
                // only for directional entry from outside (internal moves don't re-trigger it).
                .focusProperties {
                    onEnter = {
                        if (runCatching { selFocus.requestFocus() }.isFailure) {
                            runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                }
                .focusGroup()
                .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
        ) {
            Text("Live TV / ${selectedItem?.title ?: "All"}", style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "${selectedItem?.abbr ?: "ALL"} (${formatCount(count)} channels)",
                style = MaterialTheme.typography.titleMedium,
                color = OwnTVTheme.colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    placeholder = "Search ${selectedItem?.title ?: "channels"}…",
                    modifier = Modifier.weight(1f).onFocusChanged { if (it.hasFocus && previewEnabled) vm.stopPreview() },
                )
                Spacer(Modifier.size(10.dp))
                SortChip(mode = sortMode, onToggle = vm::toggleSort)
            }
            Spacer(Modifier.height(14.dp))

            if (channels.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "No channels found for “${searchQuery.trim()}”" else "No channels here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OwnTVTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(channels.itemCount) { index ->
                        val channel = channels[index]
                        if (channel != null) {
                            ChannelRow(
                                channel = channel,
                                isFavorite = favoriteIds.contains(channel.id),
                                modifier = when {
                                    channel.id == previewChannel?.id -> Modifier.focusRequester(selFocus)
                                    index == 0 -> Modifier.focusRequester(firstItemFocus)
                                    else -> Modifier
                                },
                                onFocus = { vm.onChannelFocused(channel) },
                                onClick = {
                                    vm.watchFullscreen(channel, channels.itemSnapshotList.items.filterNotNull())
                                    onFullscreen()
                                },
                            )
                        }
                    }
                }
            }
        }

        // Layer 4 — preview pane
        Box(modifier = Modifier.weight(1f).fillMaxSize().padding(Dimens.GapLarge)) {
            LivePreviewPane(
                channel = previewChannel,
                nowNext = nowNext,
                player = vm.player,
                showVideo = effectivePreview,
                isFavorite = previewChannel?.let { favoriteIds.contains(it.id) } ?: false,
                onToggleFavorite = { previewChannel?.let { vm.toggleFavorite(it) } },
                onRename = { renaming = previewChannel },
                onHide = { previewChannel?.let { vm.hideChannel(it) } },
                onMatchEpg = { matchingEpg = previewChannel },
            )
        }
    }

    renaming?.let { ch ->
        TextInputDialog(
            title = "Rename channel",
            initial = ch.name,
            hint = "Only for this profile. Leave blank to restore the original name.",
            onConfirm = { vm.renameChannel(ch, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    matchingEpg?.let { ch ->
        EpgMatchDialog(
            channelName = ch.name,
            currentMatch = vm.currentEpgMatch(ch),
            loadChannels = { q -> vm.availableEpgChannels(q) },
            onPick = { epgId -> vm.setEpgMatch(ch, epgId); matchingEpg = null },
            onClear = { vm.setEpgMatch(ch, null); matchingEpg = null },
            onDismiss = { matchingEpg = null },
        )
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    isFavorite: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.hasFocus) onFocus() },
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (focused) colors.primary else colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isFavorite) {
                OwnTVIcon(OwnTVIcon.STAR, tint = colors.favorite, filled = true, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LivePreviewPane(
    channel: ChannelEntity?,
    nowNext: EpgNowNext?,
    player: tv.own.owntv.player.OwnTVPlayer,
    showVideo: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onMatchEpg: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    if (channel == null) {
        PreviewPane(hint = "Focus a channel to see it here.")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Dimens.CardCorner)).background(colors.panel).padding(Dimens.GapLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            // Logo placeholder behind, live video on top once it starts.
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.size(120.dp))
            } else {
                OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
            }
            if (showVideo) tv.own.owntv.player.MpvVideoSurface(player = player, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(14.dp))
        Text(channel.name, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)

        EpgSection(nowNext)

        Spacer(Modifier.height(16.dp))
        OwnTVButton(
            label = if (isFavorite) "Favorited" else "Favorite",
            onClick = onToggleFavorite,
            style = OwnTVButtonStyle.SECONDARY,
            icon = OwnTVIcon.STAR,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OwnTVButton(label = "Rename", onClick = onRename, style = OwnTVButtonStyle.SECONDARY)
            OwnTVButton(label = "Hide", onClick = onHide, style = OwnTVButtonStyle.SECONDARY)
        }
        Spacer(Modifier.height(10.dp))
        OwnTVButton(label = "Match EPG", onClick = onMatchEpg, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.EPG)
        Spacer(Modifier.height(8.dp))
        Text("Press OK to watch fullscreen", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** Now-playing (with progress) + up-next, from the channel's short EPG. Hidden when no guide exists. */
@Composable
private fun EpgSection(nowNext: EpgNowNext?) {
    val colors = OwnTVTheme.colors
    val now = nowNext?.now
    val next = nowNext?.next
    if (now == null && next == null) return

    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (now != null) {
            Text("NOW", style = MaterialTheme.typography.labelSmall, color = colors.primary, fontWeight = FontWeight.Bold)
            Text(
                now.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val span = (now.stopMs - now.startMs).coerceAtLeast(1)
            val progress = ((System.currentTimeMillis() - now.startMs).toFloat() / span).coerceIn(0f, 1f)
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceContainerLowest),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.primary))
            }
            Text(
                "${formatClock(now.startMs)} – ${formatClock(now.stopMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (next != null) {
            Spacer(Modifier.height(2.dp))
            Text("NEXT  ·  ${formatClock(next.startMs)}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(
                next.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val clockFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
private fun formatClock(ms: Long): String = clockFormat.format(java.util.Date(ms))

/** Manual EPG matching: pick which guide channel this channel uses (search across all EPG feeds). */
@Composable
private fun EpgMatchDialog(
    channelName: String,
    currentMatch: String?,
    loadChannels: suspend (String) -> List<tv.own.owntv.core.database.entity.EpgChannelEntity>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var query by remember { mutableStateOf("") }
    val results by androidx.compose.runtime.produceState<List<tv.own.owntv.core.database.entity.EpgChannelEntity>?>(initialValue = null, query) {
        kotlinx.coroutines.delay(250)
        value = runCatching { loadChannels(query) }.getOrDefault(emptyList())
    }
    androidx.activity.compose.BackHandler { onDismiss() }

    // Pull focus into the dialog once the list first arrives (first result, else the search bar).
    // One-shot, so later search-driven reloads don't steal focus from the field while typing.
    val firstItemFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var didInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(results) {
        if (didInitialFocus || results == null) return@LaunchedEffect
        didInitialFocus = true
        kotlinx.coroutines.delay(60)
        if (results!!.isNotEmpty()) runCatching { firstItemFocus.requestFocus() }
        else runCatching { searchFocus.requestFocus() }
    }

    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(580.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp)) {
            Text("Match EPG", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                "Pick the guide channel for “$channelName”." + (currentMatch?.let { "  Current: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search guide channels…", modifier = Modifier.fillMaxWidth().focusRequester(searchFocus))
            Spacer(Modifier.height(12.dp))
            val list = results
            when {
                list == null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                list.isEmpty() -> Text(
                    if (query.isBlank()) "No EPG data yet — add an EPG source in Settings." else "No guide channels match “$query”.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.fillMaxWidth().height(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list, key = { it.id }) { epg ->
                        FocusableSurface(
                            onClick = { onPick(epg.epgChannelId) },
                            modifier = if (epg == list.first()) Modifier.fillMaxWidth().focusRequester(firstItemFocus) else Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { _ ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(epg.displayName ?: epg.epgChannelId, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(epg.epgChannelId, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                if (currentMatch != null) OwnTVButton("Clear match", onClick = onClear, style = OwnTVButtonStyle.SECONDARY)
            }
        }
    }
}
