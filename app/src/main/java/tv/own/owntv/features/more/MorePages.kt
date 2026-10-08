package tv.own.owntv.features.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.core.i18n.SupportedLocales
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.features.downloads.recordingWhen
import tv.own.owntv.features.home.ChannelPlate
import tv.own.owntv.features.home.OnNowCard
import tv.own.owntv.features.home.timeLeftText
import tv.own.owntv.features.shell.components.ClearHistoryDialog
import tv.own.owntv.features.shell.components.GITHUB_REPO
import tv.own.owntv.features.shell.components.TELEGRAM_LINK
import tv.own.owntv.features.shell.components.playbackDisplayName
import tv.own.owntv.features.update.UpdateDialog
import tv.own.owntv.player.PlaybackErrorLog
import tv.own.owntv.player.displayText
import tv.own.owntv.ui.components.BrandMark
import tv.own.owntv.ui.components.MoveOrderOverlay
import tv.own.owntv.ui.components.Wordmark
import tv.own.owntv.ui.components.rememberAppliedIcon
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageMenu
import tv.own.owntv.ui.stage.StageMenuHeader
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.StageSegmented
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTile
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.stage.stageFocusLook
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.format.formatBestDateTime
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import tv.own.owntv.features.home.durationText

// ---------------------------------------------------------------------------------------------
// Shared parts
// ---------------------------------------------------------------------------------------------

/** "Favourites" 42/800 −1, then whatever sits beside it (the segmented control, a count, tools). */
@Composable
private fun PageHead(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.mpx), horizontalArrangement = Arrangement.spacedBy(22.mpx), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = stageText(42, 800, (-1).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** A dim caps heading inside a page ("WHAT'S INCLUDED"). */
@Composable
internal fun PageLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), style = stageText(13, 800, 0.13.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** `.tool.box` on a focused row ("▶ Resume", "✕ Remove"): 44 high, 16/700. */
data class RowAction(val text: String, val icon: OwnTVIcon, val onClick: () -> Unit)

/**
 * A `.dl` row (History, paired devices, the error log): radius 24, padding 22, gap 24. The row itself
 * holds focus and takes OK; its [actions] show only while it is focused, to its right, so ▶ reaches them
 * and ▲ ▼ move between rows. [extra] draws under the line (a progress bar, the raw error).
 */
@Composable
internal fun StageActionRow(
    height: Dp,
    focus: FocusRequester?,
    onClick: () -> Unit,
    leading: @Composable (focused: Boolean) -> Unit,
    title: String,
    line: String?,
    actions: List<RowAction> = emptyList(),
    titleSize: Int = 24,
    trailing: (@Composable () -> Unit)? = null,
    extra: @Composable (focused: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Box(
        modifier
            .padding(bottom = 10.mpx)
            .fillMaxWidth()
            .heightIn(min = height)
            .onFocusChanged { hasFocus = it.hasFocus }
            .then(if (hasFocus) Modifier.stageFocusLook(StageFocus.FX, 24.mpx) else Modifier)
            .focusGroup()
            .padding(horizontal = 22.mpx, vertical = 10.mpx),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                    .clickable(interactionSource = null, indication = null, onClick = onClick),
                horizontalArrangement = Arrangement.spacedBy(24.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading(hasFocus)
                Column(Modifier.weight(1f)) {
                    Text(title, style = stageText(titleSize, 700), color = if (hasFocus) Color.White else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line != null) Text(line, style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
                    extra(hasFocus)
                }
                trailing?.invoke()
            }
            if (hasFocus) actions.forEach { a ->
                StageSurface(
                    onClick = a.onClick, radius = 15.mpx, modifier = Modifier.height(44.mpx),
                    idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(15.mpx)),
                ) { focused ->
                    val c = if (focused) stageAccent.onAccent else StageColors.Text
                    Row(Modifier.padding(horizontal = 16.mpx), horizontalArrangement = Arrangement.spacedBy(9.mpx), verticalAlignment = Alignment.CenterVertically) {
                        OwnTVIcon(a.icon, c, Modifier.size(18.mpx), filled = a.icon == OwnTVIcon.PLAY)
                        Text(a.text, style = stageText(16, 700), color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** What a page says when its list is empty. */
@Composable
private fun EmptyNote(title: String, body: String? = null) {
    Column(Modifier.padding(top = 34.mpx)) {
        Text(title, style = stageText(24, 800), color = StageColors.Text)
        if (body != null) Text(body, style = stageText(18, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 8.mpx))
    }
}

/** "Live TV 2" — a segment's name and its count. */
@Composable
private fun counted(nameRes: Int, n: Int) = stringResource(nameRes) + " " + n

// ---------------------------------------------------------------------------------------------
// Settings (P8-01): Search and the twelve group cards
// ---------------------------------------------------------------------------------------------

/**
 * The twelve Settings groups (P10), in the order of `SettingsGroup`: a card's index is the group it opens.
 */
private data class SettingsGroupCard(val icon: OwnTVIcon, val nameRes: Int, val summaryRes: Int, val count: Int?)

private val SettingsCards = listOf(
    SettingsGroupCard(OwnTVIcon.SPARKLE, R.string.settings_group_quick, R.string.settings_group_summary_quick, null),
    SettingsGroupCard(OwnTVIcon.PERSON, R.string.settings_group_profile, R.string.settings_card_profile, 3),
    SettingsGroupCard(OwnTVIcon.LIST, R.string.settings_group_sources, R.string.settings_card_sources, 8),
    SettingsGroupCard(OwnTVIcon.PALETTE, R.string.settings_group_appearance, R.string.settings_card_appearance, 9),
    SettingsGroupCard(OwnTVIcon.GRID, R.string.settings_group_layout, R.string.settings_card_layout, 10),
    SettingsGroupCard(OwnTVIcon.MOVIES, R.string.settings_group_content_metadata, R.string.settings_card_content, 3),
    SettingsGroupCard(OwnTVIcon.PLAY_CIRCLE, R.string.settings_vp_cat_player, R.string.settings_card_player, 11),
    SettingsGroupCard(OwnTVIcon.EXPAND, R.string.settings_vp_cat_picture, R.string.settings_card_picture, 9),
    SettingsGroupCard(OwnTVIcon.HEADPHONES, R.string.settings_group_sound_subtitles, R.string.settings_card_sound, 11),
    SettingsGroupCard(OwnTVIcon.LIVE_TV, R.string.settings_live_tv, R.string.settings_card_live, 12),
    SettingsGroupCard(OwnTVIcon.REC, R.string.settings_group_watching_recording, R.string.settings_card_watching, 11),
    SettingsGroupCard(OwnTVIcon.INFO, R.string.settings_group_app, R.string.settings_card_app, 10),
)

/** The card last opened, so Back from its group lands on it again rather than on the search field. */
private var lastOpenedCard: Int? = null

@Composable
internal fun SettingsPage(entry: FocusRequester, pinned: Int, onOpenGroup: (Int) -> Unit, onSearch: () -> Unit) {
    val back = lastOpenedCard
    val profiles = tv.own.owntv.features.settings.profileCount()
    Column(Modifier.fillMaxSize().focusGroup()) {
        PageHead(stringResource(R.string.common_nav_settings)) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.more_settings_pin_hint), style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StageSurface(
            onClick = { lastOpenedCard = null; onSearch() }, radius = 18.mpx,
            modifier = Modifier.padding(top = 26.mpx).fillMaxWidth().height(60.mpx).then(if (back == null) Modifier.focusRequester(entry) else Modifier),
            focusStyle = StageFocus.FX,
            idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(18.mpx)),
        ) { focused ->
            Row(Modifier.padding(horizontal = 20.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
                OwnTVIcon(OwnTVIcon.SEARCH, if (focused) StageColors.Text else StageColors.Dim, Modifier.size(19.mpx))
                Text(stringResource(R.string.more_settings_search), style = stageText(19, 500), color = if (focused) StageColors.Text else StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(top = 22.mpx), verticalArrangement = Arrangement.spacedBy(20.mpx)) {
            SettingsCards.chunked(3).forEach { row ->
                // 150 at the default font; a larger app font grows the row rather than cutting the
                // summary, and the row's cards keep one height.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
                    row.forEach { card ->
                        val index = SettingsCards.indexOf(card)
                        StageTile(
                            modifier = Modifier.weight(1f).heightIn(min = 150.mpx).fillMaxHeight().then(if (index == back) Modifier.focusRequester(entry) else Modifier),
                            onClick = { lastOpenedCard = index; onOpenGroup(index) },
                            padding = 0.mpx,
                        ) { focused ->
                            Column(Modifier.padding(horizontal = 26.mpx, vertical = 24.mpx)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OwnTVIcon(card.icon, if (focused) StageColors.Text else StageColors.Muted, Modifier.size(28.mpx))
                                    Spacer(Modifier.weight(1f))
                                    Text((if (index == 1) profiles else card.count ?: pinned).toString(), style = stageText(17, 700), color = StageColors.Dim)
                                }
                                Text(stringResource(card.nameRes), style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.mpx))
                                Text(stringResource(card.summaryRes), style = stageText(16, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Favourites (P8-03)
// ---------------------------------------------------------------------------------------------

private enum class FavFilter { ALL, LIVE, MOVIES, SERIES }

@Composable
internal fun FavouritesPage(
    vm: MoreCountsViewModel,
    /** Hands More what OK / ▶ on the sheet does: focus the first card, or the filter when there is none. */
    setEntry: (() -> Boolean) -> Unit,
    onPlayChannel: (Long) -> Unit,
    onPlayMovie: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val counts by vm.favorites.collectAsStateWithLifecycle()
    val lists by vm.favouriteLists.collectAsStateWithLifecycle()
    val move by vm.move.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(FavFilter.ALL) }
    val segFocus = remember { FocusRequester() }
    // Hold OK: the item whose small menu is open, and the card to return to when it closes.
    var menu by remember { mutableStateOf<Triple<MediaType, Long, String>?>(null) }
    val itemFocus = remember { HashMap<Pair<MediaType, Long>, FocusRequester>() }
    fun focusOf(type: MediaType, id: Long) = itemFocus.getOrPut(type to id) { FocusRequester() }
    var returnTo by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(menu, move) {
        if (menu == null && move == null) returnTo?.let { withFrameNanos { }; runCatching { it.requestFocus() }; returnTo = null }
    }

    val showLive = filter == FavFilter.ALL || filter == FavFilter.LIVE
    val vodShown = when (filter) {
        FavFilter.ALL -> lists.movies.map { MediaType.MOVIE to it } + lists.series.map { MediaType.SERIES to it }
        FavFilter.MOVIES -> lists.movies.map { MediaType.MOVIE to it }
        FavFilter.SERIES -> lists.series.map { MediaType.SERIES to it }
        FavFilter.LIVE -> emptyList()
    }
    // OK / ▶ from the sheet lands on the first card, as drawn; on the filter when there is none.
    val firstTarget = when {
        showLive && lists.live.isNotEmpty() -> focusOf(MediaType.LIVE, lists.live.first().id)
        vodShown.isNotEmpty() -> vodShown.first().let { (t, item) -> focusOf(t, (item as? tv.own.owntv.core.database.entity.MovieEntity)?.id ?: (item as tv.own.owntv.core.database.entity.SeriesEntity).id) }
        else -> segFocus
    }

    Column(Modifier.fillMaxSize().focusGroup()) {
        PageHead(stringResource(R.string.content_category_favorites)) {
            StageSegmented(
                options = listOf(
                    counted(R.string.content_epg_all, counts.total), counted(R.string.common_nav_live_tv, counts.live),
                    counted(R.string.common_nav_movies, counts.movies), counted(R.string.common_nav_series, counts.series),
                ),
                selected = filter.ordinal,
                onSelect = { filter = FavFilter.entries[it] },
                modifier = Modifier.focusRequester(segFocus),
            )
        }
        androidx.compose.runtime.SideEffect { setEntry { runCatching { firstTarget.requestFocus() }.isSuccess } }
        if (counts.total == 0) {
            EmptyNote(stringResource(R.string.more_fav_empty_title), stringResource(R.string.more_fav_empty_body))
            return@Column
        }
        if (showLive && lists.live.isNotEmpty()) {
            RowHead(stringResource(R.string.common_nav_live_tv), stringResource(R.string.home_row_on_now), top = 34.mpx)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.mpx), contentPadding = PaddingValues(end = 24.mpx)) {
                items(lists.live, key = { it.id }) { ch ->
                    val f = focusOf(MediaType.LIVE, ch.id)
                    OnNowCard(
                        channel = ch, programme = lists.onNow[ch.id]?.now, now = lists.now,
                        onClick = { onPlayChannel(ch.id) },
                        onLongClick = { returnTo = f; menu = Triple(MediaType.LIVE, ch.id, ch.name) },
                        width = 420.mpx, oneLine = true,
                        modifier = Modifier.focusRequester(f),
                    )
                }
            }
        }
        if (vodShown.isNotEmpty()) {
            val heading = when (filter) {
                FavFilter.MOVIES -> stringResource(R.string.common_nav_movies)
                FavFilter.SERIES -> stringResource(R.string.common_nav_series)
                else -> stringResource(R.string.more_fav_movies_series)
            }
            RowHead(heading, null, top = if (showLive && lists.live.isNotEmpty()) 36.mpx else 34.mpx)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(24.mpx), contentPadding = PaddingValues(end = 24.mpx, bottom = 24.mpx)) {
                items(vodShown, key = { (t, item) -> t.name + ((item as? tv.own.owntv.core.database.entity.MovieEntity)?.id ?: (item as tv.own.owntv.core.database.entity.SeriesEntity).id) }) { (type, item) ->
                    val movie = item as? tv.own.owntv.core.database.entity.MovieEntity
                    val series = item as? tv.own.owntv.core.database.entity.SeriesEntity
                    val id = movie?.id ?: series!!.id
                    val name = movie?.name ?: series!!.name
                    val f = focusOf(type, id)
                    StagePoster(
                        title = name,
                        line = stringResource(if (type == MediaType.MOVIE) R.string.search_movie else R.string.search_series),
                        onClick = { if (movie != null) onPlayMovie(id) else onOpenSeries(id) },
                        onLongClick = { returnTo = f; menu = Triple(type, id, name) },
                        width = 200.mpx, height = 300.mpx,
                        modifier = Modifier.focusRequester(f),
                    ) {
                        val art = movie?.posterUrl ?: series?.posterUrl
                        if (!art.isNullOrBlank()) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Box(Modifier.fillMaxSize().background(StageColors.ControlFill), contentAlignment = Alignment.Center) {
                            OwnTVIcon(if (movie != null) OwnTVIcon.MOVIES else OwnTVIcon.SERIES, StageColors.Dim, Modifier.size(40.mpx))
                        }
                    }
                }
            }
        }
    }

    menu?.let { (type, id, name) ->
        val close = { menu = null }
        OwnTVPopup(onDismissRequest = close, stageLayout = true) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
            BackHandler { close() }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
                StageMenu(Modifier.width(440.mpx)) {
                    StageMenuHeader(name, null)
                    StageMenuItem(stringResource(R.string.content_remove_favourite), onClick = { vm.removeFavourite(type, id); returnTo = segFocus; close() }, icon = OwnTVIcon.FAVORITE, modifier = Modifier.focusRequester(first))
                    StageMenuItem(stringResource(R.string.content_move), onClick = { close(); vm.startMove(type, id) }, icon = OwnTVIcon.MOVE)
                }
            }
        }
    }
    move?.let { m ->
        MoveOrderOverlay(
            title = stringResource(R.string.content_move),
            itemNames = m.names,
            activeIndex = m.index,
            onMoveUp = { vm.moveBy(-1) },
            onMoveDown = { vm.moveBy(1) },
            onCommit = vm::commitMove,
            onCancel = vm::cancelMove,
        )
    }
}

/** `.rowh`: a 26/800 heading with an optional dim 17/700 word ("Live TV  On now"), 16 above the row. */
@Composable
private fun RowHead(title: String, small: String?, top: Dp) {
    Row(Modifier.padding(top = top, bottom = 16.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
        Text(title, style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
        if (small != null) Text(small, style = stageText(17, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
    }
}

// ---------------------------------------------------------------------------------------------
// History (P8-04)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun HistoryPage(
    vm: MoreCountsViewModel,
    setEntry: (() -> Boolean) -> Unit,
    onClearHistory: (MediaType?) -> Unit,
    onPlayChannel: (Long) -> Unit,
    onPlayMovie: (Long, Long) -> Unit,
    onPlayEpisode: (Long, Long, Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val counts by vm.history.collectAsStateWithLifecycle()
    val lists by vm.historyLists.collectAsStateWithLifecycle()
    // Opens on the type watched most recently.
    val latest = listOfNotNull(
        lists.live.firstOrNull()?.let { MediaType.LIVE to it.watchedAt },
        lists.movies.firstOrNull()?.let { MediaType.MOVIE to it.watchedAt },
        lists.series.firstOrNull()?.let { MediaType.SERIES to it.watchedAt },
    ).maxByOrNull { it.second }?.first ?: MediaType.MOVIE
    var tab by rememberSaveable { mutableStateOf<MediaType?>(null) }
    val type = tab ?: latest
    val rows = when (type) { MediaType.LIVE -> lists.live; MediaType.MOVIE -> lists.movies; else -> lists.series }
    var showClear by remember { mutableStateOf(false) }
    val clearFocus = remember { FocusRequester() }
    val segFocus = remember { FocusRequester() }
    val rowFocus = remember { HashMap<Long, FocusRequester>() }
    fun focusOf(id: Long) = rowFocus.getOrPut(id) { FocusRequester() }
    var clearWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showClear) { if (showClear) clearWasOpen = true else if (clearWasOpen) { clearWasOpen = false; withFrameNanos { }; runCatching { clearFocus.requestFocus() } } }

    val sep = stringResource(R.string.content_epg_bits_separator)
    val play: (HistoryEntry) -> Unit = { e ->
        when (e.type) {
            MediaType.LIVE -> onPlayChannel(e.id)
            MediaType.MOVIE -> onPlayMovie(e.id, e.positionMs)
            else -> e.episode?.let { onPlayEpisode(e.id, it.id, e.positionMs) } ?: onOpenSeries(e.id)
        }
    }

    Column(Modifier.fillMaxSize().focusGroup()) {
        PageHead(stringResource(R.string.content_category_history)) {
            val order = listOf(MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES)
            StageSegmented(
                options = listOf(counted(R.string.common_nav_live_tv, counts.live), counted(R.string.common_nav_movies, counts.movies), counted(R.string.common_nav_series, counts.series)),
                selected = order.indexOf(type),
                onSelect = { tab = order[it] },
                modifier = Modifier.focusRequester(segFocus),
            )
            Spacer(Modifier.weight(1f))
            if (counts.total > 0) StageTool(stringResource(R.string.settings_clear_history), onClick = { showClear = true }, icon = OwnTVIcon.TRASH, danger = true, modifier = Modifier.focusRequester(clearFocus))
        }
        androidx.compose.runtime.SideEffect { setEntry { runCatching { (rows.firstOrNull()?.let { r -> focusOf(r.id) } ?: segFocus).requestFocus() }.isSuccess } }
        if (rows.isEmpty()) {
            EmptyNote(stringResource(R.string.more_history_empty))
            return@Column
        }
        LazyColumn(Modifier.padding(top = 26.mpx).fillMaxSize(), contentPadding = PaddingValues(bottom = 40.mpx)) {
            items(rows, key = { it.type.name + it.id }) { e ->
                val left = when {
                    e.durationMs > 0 && e.positionMs > 0 -> timeLeftText(((e.durationMs - e.positionMs).coerceAtLeast(0L) + 59_999L) / 60_000L)
                    // "0:14 watched": hours and minutes, as the mockup writes it.
                    e.positionMs > 0 -> (e.positionMs / 60_000L).let { m -> stringResource(R.string.content_watched_duration, String.format(androidx.compose.ui.platform.LocalConfiguration.current.locales[0], "%d:%02d", m / 60, m % 60)) }
                    else -> null
                }
                val line = when (e.type) {
                    MediaType.LIVE -> listOf(stringResource(R.string.common_nav_live_tv), recordingWhen(e.watchedAt))
                    MediaType.MOVIE -> listOfNotNull(stringResource(R.string.search_movie), e.year?.toString(), recordingWhen(e.watchedAt), left)
                    else -> listOfNotNull(
                        e.episode?.let { ep -> stringResource(R.string.content_season_episode, ep.seasonNumber, ep.episodeNumber) + (ep.name.takeIf { it.isNotBlank() }?.let { " $it" } ?: "") },
                        recordingWhen(e.watchedAt), left,
                    )
                }.joinToString(sep)
                StageActionRow(
                    height = 118.mpx,
                    focus = focusOf(e.id),
                    onClick = { play(e) },
                    leading = {
                        Box(Modifier.size(176.mpx, 99.mpx).clip(RoundedCornerShape(14.mpx)).background(Color(0xFF0F1518)), contentAlignment = Alignment.Center) {
                            when {
                                e.channel != null -> ChannelPlate(e.channel, Modifier.size(110.mpx, 70.mpx))
                                !e.art.isNullOrBlank() -> AsyncImage(model = e.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else -> OwnTVIcon(if (e.type == MediaType.MOVIE) OwnTVIcon.MOVIES else OwnTVIcon.SERIES, StageColors.Dim, Modifier.size(36.mpx))
                            }
                        }
                    },
                    title = e.title,
                    line = line,
                    extra = {
                        if (e.durationMs > 0 && e.positionMs > 0) {
                            StageProgress((e.positionMs.toFloat() / e.durationMs).coerceIn(0f, 1f), Modifier.padding(top = 10.mpx).width(280.mpx))
                        }
                    },
                    actions = listOf(
                        if (e.type == MediaType.LIVE) RowAction(stringResource(R.string.content_key_watch), OwnTVIcon.PLAY) { play(e) }
                        else RowAction(stringResource(R.string.common_resume), OwnTVIcon.PLAY) { play(e) },
                        RowAction(stringResource(R.string.settings_customize_remove), OwnTVIcon.CLOSE) {
                            // The removed row takes its focus with it: the next one takes over.
                            val i = rows.indexOf(e)
                            vm.removeHistory(e)
                            (rows.getOrNull(i + 1) ?: rows.getOrNull(i - 1))?.let { n -> runCatching { focusOf(n.id).requestFocus() } }
                        },
                    ),
                )
            }
        }
    }
    if (showClear) {
        ClearHistoryDialog(onClear = { t -> onClearHistory(t); showClear = false }, onDismiss = { showClear = false })
    }
}

// ---------------------------------------------------------------------------------------------
// Error log (P8-07)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ErrorLogPage(setEntry: (() -> Boolean) -> Unit, onCountChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var exportPath by remember { mutableStateOf<String?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    val entries by produceState<List<PlaybackErrorLog.Entry>?>(initialValue = null, refresh) {
        value = withContext(Dispatchers.IO) { PlaybackErrorLog.read(context) }
    }
    val exportFocus = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }
    val sep = stringResource(R.string.content_epg_bits_separator)

    Column(Modifier.fillMaxSize().focusGroup()) {
        PageHead(stringResource(R.string.settings_playback_error_log)) {
            Text((entries?.size ?: 0).toString(), style = stageText(22, 700), color = StageColors.Muted)
            Spacer(Modifier.weight(1f))
            // Export also carries the live diagnostics ring, so it stays even when the list is empty.
            StageTool(stringResource(R.string.settings_export), boxed = true, icon = OwnTVIcon.DOWNLOADS, modifier = Modifier.focusRequester(exportFocus), onClick = {
                scope.launch {
                    val path = withContext(Dispatchers.IO) { PlaybackErrorLog.export(context) }
                    exportPath = path
                    exportFailed = path == null
                }
            })
            if (!entries.isNullOrEmpty()) StageTool(stringResource(R.string.settings_clear_log), icon = OwnTVIcon.TRASH, danger = true, onClick = {
                PlaybackErrorLog.clear(context)
                exportPath = null
                exportFailed = false
                refresh++
                onCountChanged()
                runCatching { exportFocus.requestFocus() }
            })
        }
        androidx.compose.runtime.SideEffect { setEntry { runCatching { (if (entries.isNullOrEmpty()) exportFocus else firstRow).requestFocus() }.isSuccess } }
        Text(stringResource(R.string.settings_playback_error_description_full), style = stageText(17, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx))
        exportPath?.let { Text(stringResource(R.string.settings_backup_saved_to, it), style = stageText(16, 500), color = stageAccent.accent, modifier = Modifier.padding(top = 8.mpx)) }
        if (exportFailed) Text(stringResource(R.string.settings_backup_export_error), style = stageText(16, 500), color = StageColors.Danger, modifier = Modifier.padding(top = 8.mpx))
        val list = entries
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyNote(stringResource(R.string.settings_no_playback_errors))
            else -> LazyColumn(Modifier.padding(top = 22.mpx).fillMaxSize(), contentPadding = PaddingValues(bottom = 40.mpx)) {
                itemsIndexed(list) { i, e ->
                    val kind = when (e.kind) {
                        PlaybackErrorLog.Kind.ERROR -> stringResource(R.string.settings_playback_kind_error) to StageColors.Danger
                        PlaybackErrorLog.Kind.EVENT -> stringResource(R.string.settings_playback_kind_event) to StageColors.TagText
                        PlaybackErrorLog.Kind.REPORT -> stringResource(R.string.settings_playback_kind_report) to StageColors.Warn
                    }
                    StageActionRow(
                        height = 96.mpx,
                        focus = if (i == 0) firstRow else null,
                        onClick = {},
                        titleSize = 21,
                        leading = {
                            Text(
                                kind.first.uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
                                style = stageText(13, 800, 0.05.em), color = kind.second, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(min = 72.mpx)
                                    .background(if (kind.second == StageColors.TagText) Color.White.copy(alpha = 0.08f) else kind.second.copy(alpha = 0.16f), RoundedCornerShape(6.mpx))
                                    .padding(horizontal = 7.mpx, vertical = 3.mpx),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        },
                        title = e.reason?.displayText() ?: e.legacyReason ?: e.engine.playbackDisplayName(),
                        line = listOfNotNull(
                            e.engine.playbackDisplayName(),
                            stringResource(if (e.live) R.string.settings_live else R.string.settings_vod),
                            e.mediaSpec()?.displayText() ?: e.spec,
                        ).joinToString(sep),
                        trailing = { Text(formatBestDateTime(context, "dMMMjm", e.atMs), style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        extra = { focused ->
                            if (focused) {
                                e.raw?.let { Text(it, style = stageText(15, 500), color = StageColors.Dim, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.mpx)) }
                                Text(stringResource(R.string.settings_device_details, e.model, e.android), style = stageText(15, 500), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
                            }
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// About (P8-08)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AboutPage(vm: MoreCountsViewModel, entry: FocusRequester, onOpenLanguage: () -> Unit) {
    val context = LocalContext.current
    val manager: UpdateManager = koinInject()
    val updateState by manager.state.collectAsStateWithLifecycle()
    val checkedAt by vm.lastUpdateCheckAt.collectAsStateWithLifecycle()
    var showUpdate by remember { mutableStateOf(false) }
    val updateFocus = remember { FocusRequester() }
    var updateWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showUpdate) { if (showUpdate) updateWasOpen = true else if (updateWasOpen) { updateWasOpen = false; withFrameNanos { }; runCatching { updateFocus.requestFocus() } } }
    val sep = stringResource(R.string.content_epg_bits_separator)
    val accent = stageAccent.accent

    Column(Modifier.fillMaxSize().focusGroup()) {
        Row(horizontalArrangement = Arrangement.spacedBy(40.mpx), verticalAlignment = Alignment.CenterVertically) {
            // The app's own icon (150) and the #227 wordmark (300).
            BrandMark(rememberAppliedIcon(), 150.mpx)
            Column {
                Wordmark(300.mpx)
                Row(Modifier.padding(top = 16.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), style = stageText(22, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Only what a real check said: no tag before one has run.
                    val tag = when (updateState) {
                        UpdateManager.State.UpToDate -> stringResource(R.string.more_about_up_to_date)
                        is UpdateManager.State.Available -> stringResource(R.string.update_available)
                        else -> null
                    }
                    tag?.let {
                        Text(it.uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), style = stageText(13, 800, 0.05.em), color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.background(accent.copy(alpha = 0.2f), RoundedCornerShape(6.mpx)).padding(horizontal = 7.mpx, vertical = 3.mpx))
                    }
                }
                Text(
                    listOfNotNull(
                        checkedAt?.let { stringResource(R.string.more_about_checked, recordingWhen(it)) },
                        stringResource(R.string.more_about_license),
                    ).joinToString(sep),
                    style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.mpx),
                )
            }
        }
        Text(stringResource(R.string.more_about_description), style = stageText(18, 500).copy(lineHeight = 29.mpxSp), color = Color(0xFFD3DCD8),
            modifier = Modifier.padding(top = 26.mpx).widthIn(max = 860.mpx))
        Text(stringResource(R.string.settings_contributions), style = stageText(16, 500), color = StageColors.Dim,
            modifier = Modifier.padding(top = 10.mpx, bottom = 24.mpx).widthIn(max = 860.mpx))
        StageTile(Modifier.width(640.mpx)) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.mpx), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.telegram_qr),
                    contentDescription = stringResource(R.string.settings_telegram_qr),
                    modifier = Modifier.size(150.mpx).clip(RoundedCornerShape(14.mpx)).background(Color.White),
                )
                Column {
                    Text(stringResource(R.string.settings_join_telegram), style = stageText(22, 800), color = StageColors.Text)
                    Text(TELEGRAM_LINK, style = stageText(17, 500), color = accent, modifier = Modifier.padding(top = 4.mpx))
                    Text(stringResource(R.string.more_about_scan), style = stageText(15.5f, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 8.mpx))
                }
            }
        }
        Row(Modifier.padding(top = 22.mpx), horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            // The link is for reading: a TV has no browser to open it in.
            Row(
                Modifier.height(44.mpx).background(StageColors.ControlFill, RoundedCornerShape(15.mpx)).padding(horizontal = 16.mpx),
                horizontalArrangement = Arrangement.spacedBy(9.mpx), verticalAlignment = Alignment.CenterVertically,
            ) {
                OwnTVIcon(OwnTVIcon.NETWORK, StageColors.Text, Modifier.size(18.mpx))
                Text(GITHUB_REPO, style = stageText(16, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val languages = SupportedLocales.all.count { it.packaged }
            StageTool(pluralStringResource(R.plurals.more_about_languages, languages, languages), onClick = onOpenLanguage, icon = OwnTVIcon.LIST, boxed = true, modifier = Modifier.focusRequester(entry))
            StageTool(stringResource(R.string.settings_check_updates), onClick = { showUpdate = true }, icon = OwnTVIcon.REFRESH, boxed = true, modifier = Modifier.focusRequester(updateFocus))
        }
    }
    if (showUpdate) {
        UpdateDialog(onDismiss = { showUpdate = false }, checkOnOpen = true)
    }
}
