package tv.own.owntv.features.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.content.SearchIntent
import tv.own.owntv.core.content.ProgrammeSearchResult
import tv.own.owntv.core.content.SearchResults
import tv.own.owntv.core.database.dao.ChannelSearchResult
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.metadata.HeadlineTitle
import tv.own.owntv.features.live.LivePlate
import tv.own.owntv.features.live.LiveStagePane
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.live.ProviderTags
import tv.own.owntv.features.movies.movieTitleInfo
import tv.own.owntv.features.series.seriesTitleInfo
import tv.own.owntv.features.settings.dotSeparator
import tv.own.owntv.features.shell.components.VodCast
import tv.own.owntv.features.shell.components.VodHeader
import tv.own.owntv.features.shell.components.VodMeta
import tv.own.owntv.features.shell.components.VodTitleInfo
import tv.own.owntv.features.shell.components.vodRating
import tv.own.owntv.features.shell.components.vodRuntime
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.localizedInteger
import tv.own.owntv.ui.stage.PlaylistMark
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StagePlaylistMark
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageSearchField
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTab
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.StageTool
import tv.own.owntv.ui.stage.drawBoxShadow
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.format.rememberSystemTimeFormatter

@Composable
private fun SearchIntent.displayLabel(): String = stringResource(
    when (this) {
        SearchIntent.CONTINUE -> R.string.search_continue
        SearchIntent.UNWATCHED -> R.string.search_unwatched
        SearchIntent.CHANNELS -> R.string.search_channels
    },
)

/** One line of the results column: a group heading, a result, or the "All n channels" row. */
private sealed interface Entry {
    val key: String
    data class Heading(val text: String, override val key: String) : Entry
    data class Channel(val row: ChannelSearchResult) : Entry { override val key = "c${row.channel.id}" }
    data class Movie(val movie: MovieEntity) : Entry { override val key = "m${movie.id}" }
    data class Series(val series: SeriesEntity) : Entry { override val key = "s${series.id}" }
    data class Programme(val found: ProgrammeSearchResult) : Entry {
        override val key = "p${found.programme.id}-${found.channel.id}"
    }
    data class AllChannels(val count: Int) : Entry { override val key = "all-channels" }
}

/** All shows the first four channels, then the "All n channels" row, so movies are always on screen. */
private const val ALL_TAB_CHANNELS = 4

/**
 * Global search in the Stage design (SR-01 … SR-07): the title band, the field, tabs over grouped
 * results on the left, and on the right the focused result — Live TV's own preview pane for a channel,
 * the poster panel for a movie or series. Search only shows: OK goes to the item (issue #233), where
 * playing, downloading and every option already live. Back clears the search first, then leaves.
 */
@Composable
fun SearchScreen(
    /** OK on a result goes to it: the channel in Live TV, the movie in Movies, the series on its page. */
    onGoToChannel: (ChannelEntity) -> Unit,
    onGoToMovie: (MovieEntity) -> Unit,
    onOpenSeries: (SeriesEntity) -> Unit,
    onChildFocused: () -> Unit,
    /** The channel panel is Live TV's own: its preview engine, guide data and Preview setting. */
    liveVm: LiveViewModel,
    /** False while the fullscreen or mini player owns the picture. */
    previewEnabled: Boolean,
    vm: SearchViewModel = koinViewModel(),
    returnToHomeOnBack: Boolean = false,
    onReturnToHome: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val curated by vm.curatedResults.collectAsStateWithLifecycle()
    val intent by vm.intent.collectAsStateWithLifecycle()
    val recent by vm.recentSearches.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val marks by vm.playlistMarks.collectAsStateWithLifecycle()
    val categoryNames by vm.categoryNames.collectAsStateWithLifecycle()
    val focusedMeta by vm.focusedMeta.collectAsStateWithLifecycle()
    val metadataMode by vm.metadataMode.collectAsStateWithLifecycle()
    val nowPlaying by liveVm.nowPlaying.collectAsStateWithLifecycle()
    val nowNext by liveVm.nowNext.collectAsStateWithLifecycle()
    val livePreviewSetting by liveVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val singleSessionBlocked by liveVm.previewBlockedSingleSession.collectAsStateWithLifecycle()

    val fieldFocus = remember { FocusRequester() }
    val tabFocus = remember { SearchTab.entries.associateWith { FocusRequester() } }
    val jumpFocus = remember { FocusRequester() }
    val scheduleFocus = remember { FocusRequester() }
    val rowFocus = remember { HashMap<String, FocusRequester>() }
    fun requesterFor(key: String) = rowFocus.getOrPut(key) { FocusRequester() }

    val searching = query.trim().length >= 2
    val showingResults = searching || intent != null
    val shown = if (searching) results else curated
    val entries = searchEntries(shown, if (searching) tab else SearchTab.ALL, capChannels = searching)

    // The focused row drives the right side; nothing focused yet = the first result.
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val active = entries.firstOrNull { it.key == focusedKey && it.isResult() } ?: entries.firstOrNull { it.isResult() }
    // The preview plays only while the user is on a channel row (or its schedule), never just because
    // a channel is the first result under the field.
    var listHasFocus by remember { mutableStateOf(false) }
    var scheduleHasFocus by remember { mutableStateOf(false) }
    val activeChannel = active?.channel()?.takeIf { showingResults && (listHasFocus || scheduleHasFocus) }
    val previewOn = previewEnabled && livePreviewSetting

    // A channel shows Live TV's preview: the same guide lookup, and the same 700 ms settle before playing.
    LaunchedEffect(activeChannel?.id, previewOn) {
        val ch = activeChannel
        if (ch == null) { liveVm.stopPreview(); return@LaunchedEffect }
        liveVm.onChannelFocused(ch)
        if (!previewOn) return@LaunchedEffect
        delay(700)
        liveVm.playPreview(ch)
    }
    LaunchedEffect(active?.key, showingResults) {
        vm.focusVod(if (!showingResults) null else when (active) { is Entry.Movie -> active.movie; is Entry.Series -> active.series; else -> null })
    }
    // The "now on" part of a channel row's line (the cache the Live TV list fills).
    val channelRows = shown.channels.map { it.channel }
    val formatTime = rememberSystemTimeFormatter()
    LaunchedEffect(channelRows.map { it.id }) { liveVm.ensureNowPlaying(channelRows) }

    // Back clears an active query/intent (returning to the launcher) before leaving the screen.
    BackHandler(enabled = returnToHomeOnBack || query.isNotBlank() || intent != null) {
        vm.setQuery("")
        vm.setIntent(null)
        if (returnToHomeOnBack) onReturnToHome()
        else runCatching { fieldFocus.requestFocus() }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Back from a "Go to" lands on the row it left from; otherwise the field.
    LaunchedEffect(Unit) {
        val back = vm.lastFocusKey
        if (back != null) {
            repeat(30) {
                val idx = entries.indexOfFirst { it.key == back }
                if (idx >= 0) {
                    runCatching { listState.scrollToItem(idx) }
                    withFrameNanos { }
                    if (runCatching { requesterFor(back).requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                }
                delay(100)
            }
        }
        runCatching { fieldFocus.requestFocus() }
    }
    // An intent's list replaces the launcher under the cursor: land on its first row.
    LaunchedEffect(intent, entries.isNotEmpty()) {
        if (intent != null && !searching) entries.firstOrNull { it !is Entry.Heading }?.let { e ->
            withFrameNanos { }
            runCatching { requesterFor(e.key).requestFocus() }
        }
    }

    val goTo: (Entry) -> Unit = { e ->
        vm.lastFocusKey = e.key
        vm.rememberCurrentQuery()
        when (e) {
            is Entry.Channel -> onGoToChannel(e.row.channel)
            is Entry.Programme -> onGoToChannel(e.found.channel)
            is Entry.Movie -> onGoToMovie(e.movie)
            is Entry.Series -> onOpenSeries(e.series)
            else -> Unit
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        val screenW = maxWidth
        fun fx(px: Int) = screenW * (px / 1920f)
        val listX = fx(64)
        val listW = fx(846)
        val panelX = fx(944)
        val panelW = fx(912)

        // Title band: "Search" + "“sky” · 12 results" (SR-01).
        val titleModifier = Modifier.padding(start = fx(84), top = 52.mpx).width(fx(826))
        if (showingResults) {
            val total = shown.channels.size + shown.movies.size + shown.series.size + shown.programmes.size
            VodHeader(
                section = stringResource(R.string.search_title),
                category = if (searching) stringResource(R.string.search_query_quoted, query.trim()) else intent?.displayLabel().orEmpty(),
                count = pluralStringResource(R.plurals.search_result_count, total, total),
                showChevron = false,
                modifier = titleModifier,
            )
        } else {
            Text(stringResource(R.string.search_title), style = stageText(46, 800, (-1).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = titleModifier)
        }
        StageSearchField(
            query = query,
            onQueryChange = vm::setQuery,
            placeholder = stringResource(R.string.search_field_hint),
            modifier = Modifier
                .padding(start = listX, top = 118.mpx)
                .width(listW)
                .focusRequester(fieldFocus)
                // ▼ lands on the open tab, not whichever tab is nearest below.
                .focusProperties { down = if (searching) tabFocus.getValue(tab) else if (!showingResults) jumpFocus else FocusRequester.Default },
        )

        if (!showingResults) {
            Launcher(
                firstFocus = jumpFocus,
                recent = recent,
                onIntent = vm::setIntent,
                onRecent = vm::setQuery,
                onClearRecent = vm::clearRecentSearches,
                modifier = Modifier.padding(start = listX, top = 192.mpx).width(listW),
            )
            LauncherCard(Modifier.padding(start = panelX, top = 200.mpx).width(panelW))
        } else if (shown.isEmpty) {
            NothingFound(
                title = if (searching) stringResource(R.string.search_nothing_found, query.trim())
                else stringResource(R.string.search_nothing_in_list, intent?.displayLabel().orEmpty()),
                modifier = Modifier.padding(start = fx(84), top = if (searching) 300.mpx else 230.mpx).width(fx(820)),
            )
        }

        if (searching) {
            // Tabs (SR-01): the focused tab is the open one, so ◀ ▶ change it.
            Row(Modifier.padding(start = fx(84) - 12.mpx, top = 192.mpx), horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                listOf(
                    Triple(SearchTab.ALL, stringResource(R.string.settings_customize_filter_all), results.channels.size + results.movies.size + results.series.size + results.programmes.size),
                    Triple(SearchTab.LIVE, stringResource(R.string.common_nav_live_tv), results.channels.size + results.programmes.size),
                    Triple(SearchTab.MOVIES, stringResource(R.string.common_nav_movies), results.movies.size),
                    Triple(SearchTab.SERIES, stringResource(R.string.common_nav_series), results.series.size),
                ).forEach { (t, label, n) ->
                    StageTab(
                        label = label,
                        count = localizedInteger(n),
                        selected = tab == t,
                        onClick = { vm.setTab(t) },
                        modifier = Modifier.focusRequester(tabFocus.getValue(t)).onFocusChanged { if (it.isFocused) vm.setTab(t) },
                    )
                }
            }
        }

        if (showingResults && !shown.isEmpty) {
            val sep = dotSeparator()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(start = listX, top = if (searching) 252.mpx else 192.mpx, bottom = 80.mpx)
                    .width(listW)
                    .fillMaxHeight()
                    .onFocusChanged { listHasFocus = it.hasFocus }
                    // ▲ out of the results lands on the open tab, not the tab nearest the row.
                    .focusProperties {
                        onExit = {
                            if (searching && requestedFocusDirection == FocusDirection.Up) {
                                cancelFocusChange()
                                runCatching { tabFocus.getValue(tab).requestFocus() }
                            }
                        }
                    }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.mpx),
            ) {
                itemsIndexed(entries, key = { _, e -> e.key }) { i, e ->
                    val rowModifier = Modifier
                        .focusRequester(requesterFor(e.key))
                        .onFocusChanged { if (it.isFocused) focusedKey = e.key }
                    when (e) {
                        is Entry.Heading -> GroupHeading(e.text, Modifier.padding(top = if (i == 0) 0.mpx else 18.mpx))
                        is Entry.AllChannels -> StageTool(
                            text = pluralStringResource(R.plurals.search_all_channels, e.count, e.count),
                            icon = OwnTVIcon.LIST,
                            // The first channel keeps its row (same key) in the Live TV tab, so focus moves
                            // onto it first; otherwise it would fall onto the All tab and switch back.
                            onClick = {
                                scope.launch {
                                    shown.channels.firstOrNull()?.let { first ->
                                        val key = Entry.Channel(first).key
                                        runCatching { listState.scrollToItem(entries.indexOfFirst { it.key == key }.coerceAtLeast(0)) }
                                        withFrameNanos { }
                                        runCatching { requesterFor(key).requestFocus() }
                                    }
                                    vm.setTab(SearchTab.LIVE)
                                }
                            },
                            modifier = rowModifier.padding(top = 10.mpx),
                        )
                        is Entry.Channel -> {
                            val ch = e.row.channel
                            val name = ProviderTags.parse(ch.name)
                            val line = listOfNotNull(
                                e.row.categoryName?.takeIf { it.isNotBlank() }?.let { ProviderTags.parse(it).name },
                                ch.number?.toString(),
                                nowPlaying[ch.id],
                            ).joinToString(sep)
                            SearchRow(
                                title = name.name, line = AnnotatedString(line), tags = name.tags, mark = marks[ch.sourceId],
                                onClick = { goTo(e) },
                                leading = { LivePlate(ch.displayLogoUrl, 76.mpx, 54.mpx) },
                                modifier = rowModifier.onPreviewKeyEvent { k ->
                                    // ▶ reaches the schedule under the preview, as on Live TV.
                                    if (k.type == KeyEventType.KeyDown && k.key == Key.DirectionRight && nowNext?.next != null) {
                                        runCatching { scheduleFocus.requestFocus() }.getOrDefault(false)
                                    } else {
                                        false
                                    }
                                },
                            )
                        }
                        is Entry.Programme -> {
                            val ch = e.found.channel
                            val p = e.found.programme
                            SearchRow(
                                title = p.title,
                                line = AnnotatedString(
                                    ProviderTags.parse(ch.name).name + sep +
                                        stringResource(R.string.content_live_time_range_plain, formatTime(p.startMs), formatTime(p.stopMs)),
                                ),
                                tags = emptyList(), mark = marks[ch.sourceId],
                                onClick = { goTo(e) },
                                leading = { LivePlate(ch.displayLogoUrl, 76.mpx, 54.mpx) },
                                modifier = rowModifier,
                            )
                        }
                        is Entry.Movie -> SearchRow(
                            title = e.movie.name,
                            line = vodRowLine(e.movie.year, e.movie.rating, e.movie.categoryId?.let { categoryNames[it] }),
                            tags = emptyList(), mark = marks[e.movie.sourceId],
                            onClick = { goTo(e) },
                            leading = { SmallPoster(e.movie.posterUrl, OwnTVIcon.MOVIES) },
                            modifier = rowModifier,
                        )
                        is Entry.Series -> SearchRow(
                            title = e.series.name,
                            line = vodRowLine(e.series.year, e.series.rating, e.series.categoryId?.let { categoryNames[it] }),
                            tags = emptyList(), mark = marks[e.series.sourceId],
                            onClick = { goTo(e) },
                            leading = { SmallPoster(e.series.posterUrl, OwnTVIcon.SERIES) },
                            modifier = rowModifier,
                        )
                    }
                }
            }

            // The right side, in one slot (x 944, w 912): Live TV's preview pane or the poster panel.
            val panelModifier = Modifier.padding(start = panelX, top = 120.mpx).width(panelW)
            val activeCh = active?.channel()
            when {
                activeCh != null -> LiveStagePane(
                    channel = activeCh,
                    channelName = ProviderTags.parse(activeCh.name).name,
                    nowNext = nowNext,
                    previewEngine = liveVm.previewEngine,
                    showVideo = previewOn,
                    singleSessionBlocked = singleSessionBlocked,
                    scheduleFocus = scheduleFocus,
                    // Search only shows: a programme picked here goes to the channel, where its menu lives.
                    onOpenProgramme = { goTo(active) },
                    onBackToList = { runCatching { requesterFor(active.key).requestFocus() } },
                    modifier = panelModifier.onFocusChanged { scheduleHasFocus = it.hasFocus },
                )
                active is Entry.Movie -> PosterPanel(
                    movieTitleInfo(active.movie, focusedMeta?.takeIf { it.first == active.key }?.second, metadataMode.tmdbWins),
                    series = false, modifier = panelModifier,
                )
                active is Entry.Series -> PosterPanel(
                    seriesTitleInfo(active.series, focusedMeta?.takeIf { it.first == active.key }?.second, metadataMode.tmdbWins),
                    series = true, modifier = panelModifier,
                )
                else -> Unit
            }
        }

        // Key hints, bottom left (SR-01).
        val hints = buildList {
            if (showingResults && !shown.isEmpty) {
                add(
                    stringResource(R.string.common_ok) to stringResource(
                        when (active) {
                            is Entry.Channel, is Entry.Programme -> R.string.search_go_to_channel
                            is Entry.Series -> R.string.search_open_series
                            else -> R.string.search_go_to_movie
                        },
                    ),
                )
                if (active?.channel() != null) add("▶" to stringResource(R.string.content_key_schedule))
            }
            if (showingResults) {
                add("▲" to stringResource(R.string.common_search))
                add(stringResource(R.string.common_back) to stringResource(R.string.common_clear))
            } else {
                add(stringResource(R.string.common_ok) to stringResource(R.string.content_key_select))
                add(stringResource(R.string.common_back) to stringResource(R.string.content_key_menu))
            }
        }
        StageKeyHints(hints, Modifier.align(Alignment.BottomStart).padding(start = fx(84), bottom = 40.mpx))
    }
}

private fun Entry.isResult() = this is Entry.Channel || this is Entry.Programme || this is Entry.Movie || this is Entry.Series

/** The channel a row is about — its own, or the one showing the programme. */
private fun Entry.channel(): ChannelEntity? = when (this) {
    is Entry.Channel -> row.channel
    is Entry.Programme -> found.channel
    else -> null
}

/** The rows in display order for [tab]; All groups them under headings (SR-01). */
@Composable
private fun searchEntries(results: SearchResults, tab: SearchTab, capChannels: Boolean): List<Entry> {
    val live = stringResource(R.string.common_nav_live_tv)
    val movies = stringResource(R.string.common_nav_movies)
    val series = stringResource(R.string.common_nav_series)
    val onTv = stringResource(R.string.search_on_tv)
    val sep = dotSeparator()
    return remember(results, tab, capChannels, live, movies, series, onTv, sep) {
        val locale = java.util.Locale.getDefault()
        fun heading(label: String, n: Int, key: String) = Entry.Heading(label.uppercase(locale) + sep + n.toString(), key)
        when (tab) {
            SearchTab.LIVE -> results.channels.map { Entry.Channel(it) } + results.programmes.map { Entry.Programme(it) }
            SearchTab.MOVIES -> results.movies.map { Entry.Movie(it) }
            SearchTab.SERIES -> results.series.map { Entry.Series(it) }
            SearchTab.ALL -> buildList {
                if (results.channels.isNotEmpty()) {
                    add(heading(live, results.channels.size, "h-live"))
                    val cap = capChannels && results.channels.size > ALL_TAB_CHANNELS
                    (if (cap) results.channels.take(ALL_TAB_CHANNELS) else results.channels).forEach { add(Entry.Channel(it)) }
                    if (cap) add(Entry.AllChannels(results.channels.size))
                }
                if (results.programmes.isNotEmpty()) {
                    add(heading(onTv, results.programmes.size, "h-ontv"))
                    results.programmes.forEach { add(Entry.Programme(it)) }
                }
                if (results.movies.isNotEmpty()) {
                    add(heading(movies, results.movies.size, "h-movies"))
                    results.movies.forEach { add(Entry.Movie(it)) }
                }
                if (results.series.isNotEmpty()) {
                    add(heading(series, results.series.size, "h-series"))
                    results.series.forEach { add(Entry.Series(it)) }
                }
            }
        }
    }
}

/** "2005 · ★ 6.2 · Disney+ Movies": only the parts the title has. */
@Composable
private fun vodRowLine(year: Int?, rating: Double?, category: String?): AnnotatedString {
    val sep = dotSeparator()
    val yearText = year?.let { localizedInteger(it, grouping = false) }
    return buildAnnotatedString {
        var first = true
        fun next() { if (!first) append(sep); first = false }
        yearText?.let { next(); append(it) }
        rating?.takeIf { it > 0 }?.let { r ->
            next()
            withStyle(SpanStyle(color = StageColors.RatingStar)) { append("★") }
            append(" " + vodRating(r))
        }
        category?.takeIf { it.isNotBlank() }?.let { next(); append(ProviderTags.parse(it).name) }
    }
}

/** `.sgh`: "LIVE TV · 9", 12.5/800 with wide tracking, dim, 20 in from the rows. */
@Composable
private fun GroupHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = stageText(12.5f, 800, 0.13.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 20.mpx),
    )
}

/** `.srow`: 84 high, 18 padding and gap; art, title 21/700, a 16 px muted line, then tags and the playlist mark. Focused = FX. */
@Composable
private fun SearchRow(
    title: String,
    line: AnnotatedString,
    tags: List<String>,
    mark: PlaylistMark?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hold OK does nothing here (owner, #233): the item's options live where OK takes you.
    StageRow(onClick = onClick, onLongClick = {}, height = 84.mpx, horizontalPadding = 18.mpx, gap = 18.mpx, modifier = modifier.fillMaxWidth()) { focused ->
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = stageText(21, 700), color = if (focused) Color.White else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.mpx))
            Text(line, style = stageText(16, 400), color = if (focused) Color(0xFFD9E6E1) else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
            tags.forEach { StageTag(it) }
            mark?.let { StagePlaylistMark(it) }
        }
    }
}

/** A result's poster, 50 × 74, radius 8. */
@Composable
private fun SmallPoster(url: String?, placeholder: OwnTVIcon) {
    Box(Modifier.size(50.mpx, 74.mpx).clip(RoundedCornerShape(8.mpx)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else OwnTVIcon(placeholder, StageColors.Dim, Modifier.size(22.mpx))
    }
}

/**
 * The poster panel (SR-02 … 04), in the Live TV preview's frame and place: the backdrop 16:9 (or the
 * poster blurred, with the sharp poster at the right), a MOVIE / SERIES tag, the title art or name at
 * the bottom left; under it year • genres • runtime • ★ rating and tags, three lines of plot, five cast.
 */
@Composable
private fun PosterPanel(info: VodTitleInfo, series: Boolean, modifier: Modifier = Modifier) {
    val videoR = 28.mpx
    Column(modifier) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .drawBehind { drawBoxShadow(Color.Black.copy(alpha = 0.55f), 80.mpx.toPx(), videoR.toPx(), dy = 30.mpx.toPx()) }
                .clip(RoundedCornerShape(videoR))
                .background(Color.Black),
        ) {
            val frameH = maxHeight
            if (!info.backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    model = info.backdropUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, -0.4f), modifier = Modifier.fillMaxSize(),
                )
            } else if (!info.posterUrl.isNullOrBlank()) {
                AsyncImage(model = info.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(36.mpx))
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                AsyncImage(
                    model = info.posterUrl, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 40.mpx, top = 36.mpx).height(frameH - 72.mpx).clip(RoundedCornerShape(14.mpx)),
                )
            }
            // `.shade`: black 72% at the bottom, clear by 42% up.
            Box(Modifier.fillMaxSize().gradientWash(true, 0f to Color.Transparent, 0.58f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f)))
            Box(Modifier.fillMaxSize().drawBehind { drawInnerRing(Color.White.copy(alpha = 0.08f), 1.mpx.toPx(), videoR.toPx()) })
            Text(
                stringResource(if (series) R.string.search_series else R.string.search_movie).uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
                style = stageText(13, 800, 0.05.em), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.mpx, top = 22.mpx)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.mpx))
                    .padding(horizontal = 7.mpx, vertical = 3.mpx),
            )
            var logoFailed by remember(info.logoUrl) { mutableStateOf(false) }
            if (!info.logoUrl.isNullOrBlank() && !logoFailed) {
                AsyncImage(
                    model = info.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                    onState = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 26.mpx, bottom = 22.mpx, end = 26.mpx).height(110.mpx).fillMaxWidth(0.6f),
                )
            } else {
                Text(
                    HeadlineTitle.of(info.title), style = stageText(44, 800, (-1).mpxSp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 26.mpx, bottom = 20.mpx, end = 26.mpx),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 4.mpx, end = 4.mpx, top = 34.mpx)) {
            VodMeta(
                listOfNotNull(info.year?.toString(), info.genres.takeIf { it.isNotEmpty() }?.joinToString(", "), info.runtimeSecs?.let { vodRuntime(it) }),
                info.rating, info.tags, 19,
            )
            info.plot?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, style = stageText(19, 400).copy(lineHeight = (19 * 1.55f).mpxSp), color = Color(0xFFCCD6D2),
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.mpx, bottom = 22.mpx),
                )
            }
            if (info.cast.isNotEmpty()) VodCast(info.cast, 5)
        }
    }
}

/** SR-06: JUMP TO (Continue watching · Unwatched · Channels), RECENT SEARCHES as rows, then Clear. */
@Composable
private fun Launcher(
    firstFocus: FocusRequester,
    recent: List<String>,
    onIntent: (SearchIntent) -> Unit,
    onRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Column(modifier.focusGroup()) {
        GroupHeading(stringResource(R.string.search_jump_to).uppercase(locale), Modifier.padding(top = 8.mpx, bottom = 10.mpx))
        Row(horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            SearchIntent.entries.forEach { i ->
                val icon = when (i) {
                    SearchIntent.CONTINUE -> OwnTVIcon.PLAY
                    SearchIntent.UNWATCHED -> OwnTVIcon.SPARKLE
                    SearchIntent.CHANNELS -> OwnTVIcon.LIVE_TV
                }
                StageTool(
                    text = i.displayLabel(), icon = icon, onClick = { onIntent(i) },
                    modifier = if (i == SearchIntent.entries.first()) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
        if (recent.isNotEmpty()) {
            GroupHeading(stringResource(R.string.search_recent).uppercase(locale), Modifier.padding(top = 46.mpx, bottom = 4.mpx))
            Column(verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                recent.forEach { term -> RecentRow(term) { onRecent(term) } }
            }
            StageTool(
                text = stringResource(R.string.search_clear_recent), icon = OwnTVIcon.TRASH, onClick = onClearRecent,
                modifier = Modifier.padding(top = 8.mpx),
            )
        }
    }
}

/** `.fcat`: a 52-high flat row, the history glyph and the term in 18.5/600. Focused = FILLED. */
@Composable
private fun RecentRow(term: String, onClick: () -> Unit) {
    val a = stageAccent
    StageSurface(onClick = onClick, radius = 15.mpx, modifier = Modifier.fillMaxWidth().height(52.mpx)) { focused ->
        Row(
            Modifier.padding(horizontal = 14.mpx),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(OwnTVIcon.HISTORY, if (focused) a.onAccent else StageColors.Muted, Modifier.size(20.mpx))
            Text(term, style = stageText(18.5f, 600), color = if (focused) a.onAccent else Color(0xFFCDD7D3), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** SR-06's glass card: what Search does, and where a result takes you. */
@Composable
private fun LauncherCard(modifier: Modifier = Modifier) {
    Column(modifier.stageGlass(30.mpx).padding(horizontal = 38.mpx, vertical = 34.mpx)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
            OwnTVIcon(OwnTVIcon.SEARCH, stageAccent.accent, Modifier.size(20.mpx))
            Text(stringResource(R.string.search_title), style = stageText(19, 700), color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            stringResource(R.string.search_card_title), style = stageText(30, 800), color = StageColors.Text,
            modifier = Modifier.padding(top = 14.mpx, bottom = 10.mpx),
        )
        Text(stringResource(R.string.search_card_body), style = stageText(19, 400).copy(lineHeight = (19 * 1.55f).mpxSp), color = StageColors.Muted)
    }
}

/** SR-05: "Nothing found for “qwzx”" and what search looks in. */
@Composable
private fun NothingFound(title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = stageText(30, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.search_nothing_found_body), style = stageText(19, 400).copy(lineHeight = (19 * 1.55f).mpxSp),
            color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx),
        )
    }
}
