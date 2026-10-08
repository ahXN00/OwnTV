package tv.own.owntv.features.shell.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import tv.own.owntv.R
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.metadata.CastMember
import tv.own.owntv.core.metadata.HeadlineTitle
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.core.settings.SettingsRepository.SortMode
import tv.own.owntv.features.epg.GuideMenuHost
import tv.own.owntv.features.live.LiveCategoryEntry
import tv.own.owntv.features.live.LiveRailItem
import tv.own.owntv.features.live.ProviderTags
import tv.own.owntv.features.live.displayLabel
import tv.own.owntv.ui.components.MenuAction
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.arranged
import tv.own.owntv.ui.stage.PlaylistMark
import tv.own.owntv.ui.stage.StageGroupLabel
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.StageMenuHeader
import tv.own.owntv.ui.stage.StageMenuItem
import tv.own.owntv.ui.stage.StagePlaylistMark
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.dissolveEdges
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.ownTvTween
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/*
 * Movies & Series in the Stage design (P5-01 … P5-12): the header, the Cinematic backdrop and hero,
 * the Separate details card, the list row, the sort and options menus, and the category list's
 * entries. MoviesScreen and SeriesScreen own the state and the focus plumbing; these only draw.
 */

/** What a title shows in the hero (Cinematic) and the details card (Separate); every part is optional. */
internal data class VodTitleInfo(
    val title: String,
    val logoUrl: String?,
    /** The Cinematic backdrop; never a poster stretched to 1400 wide. */
    val backdropUrl: String?,
    val posterUrl: String?,
    val year: Int?,
    val genres: List<String>,
    val runtimeSecs: Int?,
    val rating: Double?,
    val tags: List<String>,
    val plot: String?,
    val cast: List<CastMember>,
)

/** A rating as the mockup writes it: one decimal, in the user's number format ("8.4", "8,4"). */
internal fun vodRating(rating: Double): String = String.format(Locale.getDefault(), "%.1f", rating)

/** "1 h 42 min", or "48 min" under an hour. */
@Composable
internal fun vodRuntime(secs: Int): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) stringResource(R.string.home_duration_hours_minutes, h, m) else stringResource(R.string.player_duration_minutes, m)
}

/**
 * The outlined quality badges for a title — "4K", "HDR10", "5.1" and the like.
 *
 * Both halves come from what the provider advertised in the item's own name, parsed at sync time:
 * [qualityRank] is the resolution ladder core assigns, and [advertisedCapabilities] is its
 * "•"-joined list of HDR and audio markers. They are technical tokens, not prose — "4K" is "4K" in
 * every language — so nothing here is translated.
 */
fun cinematicQualityBadges(qualityRank: Int, advertisedCapabilities: String?): List<String> = buildList {
    when (qualityRank) {
        5 -> add("8K")
        4 -> add("4K")
        3 -> add("1080p")
        2 -> add("720p")
        1 -> add("SD")
    }
    advertisedCapabilities?.split("•")?.forEach { it.trim().takeIf(String::isNotEmpty)?.let(::add) }
}

/** "Movies" 46/800, then the crumb "‹ **Disney+ Kids Movies** · 136 titles" (no ‹ when the column is on screen). */
@Composable
internal fun VodHeader(section: String, category: String, count: String, showChevron: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(22.mpx), verticalAlignment = Alignment.Bottom) {
        Text(section, style = stageText(46, 800, (-1).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.padding(bottom = 3.mpx),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showChevron) OwnTVIcon(OwnTVIcon.CHEVRON_LEFT, StageColors.Muted, Modifier.size(20.mpx))
            Text(
                category, style = stageText(20, 700), color = stageAccent.accent,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Text("· $count", style = stageText(20, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "136 titles" / "32 series" for the crumb. */
@Composable
internal fun vodCount(series: Boolean, count: Int): String =
    pluralStringResource(if (series) R.plurals.content_vod_series_count else R.plurals.content_vod_title_count, count, count)

/** How long the cursor must rest on a title before its artwork is fetched (holding ▶ past a row loads nothing). */
private const val BACKDROP_SETTLE_MS = 260L

/**
 * `.fadeimg` behind the Cinematic hero: 1400 of the 1920 width at 1400×720, top right, dissolving into
 * the page on its left (34%) and bottom (38%), under the left and bottom washes so the hero always reads.
 */
@Composable
internal fun VodCinematicBackdrop(url: String?, series: Boolean, modifier: Modifier = Modifier) {
    var settled by remember { mutableStateOf(url) }
    // Art already shown once is in the image cache: it swaps at once; only new art waits the settle.
    val seen = remember { HashSet<String>() }
    LaunchedEffect(url) {
        if (url != settled) {
            if (url == null || url !in seen) delay(BACKDROP_SETTLE_MS)
            settled = url
        }
        url?.let { if (seen.size > 300) seen.clear(); seen += it }
    }
    val wash = Color(5, 8, 10)
    // object-position 30% 40% (Movies) / 100% 40% (Series), as drawn.
    val focus = BiasAlignment(if (series) 1f else -0.4f, -0.2f)
    Box(modifier.fillMaxWidth(1400f / 1920f).aspectRatio(1400f / 720f).dissolveEdges(start = 0.34f, bottom = 0.38f)) {
        Crossfade(targetState = settled, animationSpec = ownTvTween(420), label = "vodBackdrop") { u ->
            if (!u.isNullOrBlank()) {
                AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, alignment = focus, modifier = Modifier.fillMaxSize())
            }
        }
        Box(
            Modifier.fillMaxSize().gradientWash(
                false,
                0f to wash.copy(alpha = 0.85f),
                0.34f to wash.copy(alpha = 0.35f),
                0.64f to wash.copy(alpha = 0f),
                1f to wash.copy(alpha = 0f),
            ),
        )
        Box(
            Modifier.fillMaxSize().gradientWash(
                true,
                0f to wash.copy(alpha = 0f),
                0.58f to wash.copy(alpha = 0f),
                0.97f to wash.copy(alpha = 0.9f),
                1f to wash.copy(alpha = 0.9f),
            ),
        )
    }
}

/**
 * The Cinematic hero (P5-01 / P5-10): title art 170 high (the name in 92/800 when there is none or it
 * fails to load), the meta line, a two-line synopsis and six cast photos. Display only — focus never
 * leaves the poster row.
 */
@Composable
internal fun VodHero(info: VodTitleInfo, modifier: Modifier = Modifier) {
    Column(modifier.clipToBounds()) {
        var logoFailed by remember(info.logoUrl) { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(170.mpx), contentAlignment = Alignment.BottomStart) {
            if (!info.logoUrl.isNullOrBlank() && !logoFailed) {
                AsyncImage(
                    model = info.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onState = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                VodHeroTitle(info.title)
            }
        }
        VodMeta(
            parts = listOfNotNull(info.year?.toString(), info.genres.takeIf { it.isNotEmpty() }?.joinToString(", "), info.runtimeSecs?.let { vodRuntime(it) }),
            rating = info.rating, tags = info.tags, size = 20,
            modifier = Modifier.padding(top = 18.mpx, bottom = 12.mpx),
        )
        info.plot?.takeIf { it.isNotBlank() }?.let {
            Text(
                it, style = stageText(19.5f, 400).copy(lineHeight = (19.5f * 1.55f).mpxSp), color = Color(0xFFC9D3CF),
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 860.mpx),
            )
        }
        if (info.cast.isNotEmpty()) VodCast(info.cast, 6, Modifier.padding(top = 20.mpx))
    }
}

/**
 * The hero's title when there is no title art: 92/800 at most, shrinking to 48 and wrapping to a second
 * line so a long name fits instead of running off as "Building the Ban…" (#7.3). Shared with the series
 * page's header. A leading provider tag ("|MULTI| ") is not part of a headline.
 */
@Composable
internal fun VodHeroTitle(title: String, modifier: Modifier = Modifier) {
    BasicText(
        HeadlineTitle.of(title),
        modifier,
        style = stageText(92, 800, (-2.5f / 92f).em).copy(lineHeight = 1.02.em, color = StageColors.Text),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 48.mpxSp, maxFontSize = 92.mpxSp, stepSize = 4.mpxSp),
    )
}

/** `metaHtml`: "2026 • Animation, Family • 1 h 42 min • ★ 8.4 [4K] [HDR]", 600 weight, dots at 40%. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VodMeta(parts: List<String>, rating: Double?, tags: List<String>, size: Int, modifier: Modifier = Modifier) {
    val text = Color(0xFFD3DCD8)
    val items = parts + listOfNotNull(rating?.let { "★" })
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(14.mpx),
        verticalArrangement = Arrangement.spacedBy(6.mpx),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, part ->
            if (i > 0) Text("•", style = stageText(size, 600), color = text.copy(alpha = 0.4f))
            if (part == "★" && i == items.lastIndex && rating != null) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = StageColors.RatingStar)) { append("★") }
                        append(" " + vodRating(rating))
                    },
                    style = stageText(size, 600), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(part, style = stageText(size, 600), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        tags.forEach { StageTag(it) }
    }
}

/** `.cast`: photos 64 round with a 2 px white-14% ring, the initials on the mint→blue gradient when there is no photo. */
@Composable
internal fun VodCast(cast: List<CastMember>, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
        cast.take(count).forEach { member ->
            Column(Modifier.width(84.mpx), horizontalAlignment = Alignment.CenterHorizontally) {
                val photo = MetadataImages.profile(member.profilePath)
                Box(
                    Modifier
                        .size(64.mpx)
                        .drawBehind { drawInnerRing(Color.White.copy(alpha = 0.14f), 2.mpx.toPx(), size.minDimension / 2f) }
                        .padding(2.mpx)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFCFE9E3), Color(0xFF9FB8FF)))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) {
                        AsyncImage(
                            model = photo, contentDescription = null, contentScale = ContentScale.Crop,
                            alignment = BiasAlignment(0f, -0.6f), modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            member.name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(),
                            style = stageText(22, 800), color = Color(0xFF0B1512), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.mpx))
                Text(
                    member.name, style = stageText(14.5f, 600).copy(lineHeight = (14.5f * 1.25f).mpxSp), color = StageColors.Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The Separate layout's details card (P5-05, the "Poster panel"): glass, radius 30, padding 20 — the
 * art 290 high with the title art or name over a bottom shade, the title 32/800, the short meta line,
 * the genres in accent, a five-line synopsis, five cast photos at 92%, and the key hints at the bottom.
 */
@Composable
internal fun VodDetailsCard(info: VodTitleInfo?, hints: List<Pair<String, String>>, series: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.stageGlass(30.mpx).padding(20.mpx)) {
        if (info != null) {
            Column(Modifier.fillMaxSize().clipToBounds()) {
                Box(Modifier.fillMaxWidth().height(290.mpx).clip(RoundedCornerShape(20.mpx)).background(Color.Black.copy(alpha = 0.3f))) {
                    val art = info.backdropUrl ?: info.posterUrl
                    if (!art.isNullOrBlank()) {
                        AsyncImage(
                            model = art, contentDescription = null, contentScale = ContentScale.Crop,
                            alignment = BiasAlignment(if (series) 1f else -0.4f, -0.2f), modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(Modifier.fillMaxSize().gradientWash(true, 0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)))
                    var logoFailed by remember(info.logoUrl) { mutableStateOf(false) }
                    if (!info.logoUrl.isNullOrBlank() && !logoFailed) {
                        AsyncImage(
                            model = info.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                            onState = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.mpx, bottom = 14.mpx, end = 18.mpx).height(96.mpx).fillMaxWidth(),
                        )
                    }
                    // No name over the art without title art: the line under it already says it (#7.3).
                }
                Column(Modifier.padding(horizontal = 4.mpx)) {
                    Text(
                        HeadlineTitle.of(info.title), style = stageText(24, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 18.mpx, bottom = 8.mpx),
                    )
                    VodMeta(listOfNotNull(info.year?.toString(), info.runtimeSecs?.let { vodRuntime(it) }), info.rating, info.tags, 17)
                    if (info.genres.isNotEmpty()) {
                        Text(
                            info.genres.joinToString(" · "), style = stageText(16, 700), color = stageAccent.accent,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.mpx),
                        )
                    }
                    info.plot?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it, style = stageText(17, 400).copy(lineHeight = (17 * 1.55f).mpxSp), color = Color(0xFFC9D3CF),
                            maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.mpx, bottom = 18.mpx),
                        )
                    }
                    if (info.cast.isNotEmpty()) {
                        VodCast(info.cast, 5, Modifier.graphicsLayer { scaleX = 0.92f; scaleY = 0.92f; transformOrigin = TransformOrigin(0f, 0f) })
                    }
                }
            }
        }
        StageKeyHints(hints, Modifier.align(Alignment.BottomStart).padding(start = 4.mpx, bottom = 2.mpx))
    }
}

/** "2026 · ★ 8.4 · 1 h 30 min" under a list row's title: only the parts the title has. */
@Composable
internal fun vodLine(year: Int?, rating: Double?, runtimeSecs: Int?): AnnotatedString {
    val runtime = runtimeSecs?.takeIf { it > 0 }?.let { vodRuntime(it) }
    return buildAnnotatedString {
        val parts = mutableListOf<() -> Unit>()
        year?.let { y -> parts += { append(y.toString()) } }
        rating?.takeIf { it > 0 }?.let { r -> parts += { withStyle(SpanStyle(color = StageColors.RatingStar)) { append("★") }; append(" " + vodRating(r)) } }
        runtime?.let { t -> parts += { append(t) } }
        parts.forEachIndexed { i, part -> if (i > 0) append(" · "); part() }
    }
}

/**
 * `.vrow` (List view, P5-06 / P5-09): 118 high, radius 20, padding 16, gap 20 — the poster 66×99 radius
 * 10, the title 21/700, the line 16 muted, the resume progress (260 wide) when started, the playlist mark.
 */
@Composable
internal fun VodListRow(
    title: String,
    line: AnnotatedString,
    posterUrl: String?,
    placeholder: OwnTVIcon,
    progress: Float?,
    mark: PlaylistMark?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StageRow(onClick = onClick, onLongClick = onLongClick, height = 118.mpx, horizontalPadding = 16.mpx, gap = 20.mpx, modifier = modifier.fillMaxWidth()) { focused ->
        Box(Modifier.size(66.mpx, 99.mpx).clip(RoundedCornerShape(10.mpx)).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                OwnTVIcon(placeholder, StageColors.Dim, Modifier.size(28.mpx))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = stageText(21, 700), color = if (focused) Color.White else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (line.isNotEmpty()) {
                Text(line, style = stageText(16, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
            }
            if (progress != null && progress > 0f) StageProgress(progress, Modifier.padding(top = 10.mpx).width(260.mpx))
        }
        mark?.let { StagePlaylistMark(it) }
    }
}

/** Poster art for a grid / row card: the artwork, or the section's glyph on a quiet plate. */
@Composable
internal fun VodPosterArt(url: String?, placeholder: OwnTVIcon) {
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            OwnTVIcon(placeholder, StageColors.Dim, Modifier.size(48.mpx))
        }
    }
}

/** The four sort orders, in the mockup's order, with the line each shows when it is not the current one. */
private val VodSorts = listOf(
    Triple(SortMode.PLAYLIST, R.string.content_sort_provider_order, R.string.content_sort_provider_order_hint),
    Triple(SortMode.ALPHA, R.string.settings_sort_alpha, null),
    Triple(SortMode.RATING, R.string.settings_sort_rating, R.string.content_sort_rating_hint),
    Triple(SortMode.DATE_ADDED, R.string.settings_sort_date_added, R.string.content_sort_date_added_hint),
)

/** The current order's name, for "Sort: **Date added** ▾". */
@Composable
internal fun vodSortLabel(mode: SortMode): String = stringResource(VodSorts.first { it.first == mode }.second)

/** Cinematic's "Sort: **Date added** ▾" (the label from "Sort: %1$s", the order in full colour). */
@Composable
internal fun VodSortTool(mode: SortMode, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = vodSortLabel(mode)
    val full = stringResource(R.string.content_epg_sort_button, label)
    // Bold only the order when the language puts it last ("Sort: A–Z"); otherwise the whole phrase.
    val prefix = full.removeSuffix(label).trimEnd().takeIf { full.endsWith(label) && it.isNotBlank() }
    tv.own.owntv.ui.stage.StageTool(
        text = prefix, icon = OwnTVIcon.SORT, value = if (prefix != null) label else full,
        trailingIcon = OwnTVIcon.CHEVRON_DOWN, onClick = onClick, modifier = modifier,
    )
}

/** Sort (P5-02): "Sort movies · Applies to every category in this section", 560 wide; ✓ on the current order. */
@Composable
internal fun VodSortMenu(series: Boolean, current: SortMode, x: Float, top: Dp, onPick: (SortMode) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GuideMenuHost(x = x, top = top, width = 560.mpx, onDismiss = onDismiss) {
        StageMenuHeader(
            title = stringResource(if (series) R.string.content_sort_series_title else R.string.content_sort_movies_title),
            subtitle = stringResource(R.string.content_sort_applies_hint),
        )
        VodSorts.forEach { (mode, label, hint) ->
            val on = mode == current
            StageMenuItem(
                text = stringResource(label), icon = OwnTVIcon.SORT,
                value = if (on) null else hint?.let { stringResource(it) }, checked = on,
                onClick = { onPick(mode); onDismiss() },
                modifier = if (on) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
}

/** Group ids of a title's options menu, in the mockup's order: WATCH, LIBRARY, ORGANISE, DETAILS. */
internal const val VodGroupWatch = 0
internal const val VodGroupLibrary = 1
internal const val VodGroupOrganise = 2
internal const val VodGroupDetails = 3

/**
 * ☰ Title options (P5-03 / P5-12), 540 wide at the right: the poster 52×78, the title and "2026 ·
 * Disney+ Kids Movies", then the actions under their group labels, in the order the user set in
 * Settings › Long-press menus. Keys in [disabled] are drawn at 45% and do nothing ("Remove from history"
 * for a title never watched); [values] are the dim right-hand values. Focus starts on the favourite.
 */
@Composable
internal fun VodOptionsMenu(
    title: String,
    subtitle: String?,
    posterUrl: String?,
    x: Float,
    menu: ContentMenu,
    actions: List<MenuAction>,
    disabled: Set<String>,
    onDismiss: () -> Unit,
    values: Map<String, String> = emptyMap(),
    /** P6-04 (an episode): 250 down, a 112×63 still, focus starting on Download. */
    top: Dp = 60.mpx,
    artWidth: Dp = 52.mpx,
    artHeight: Dp = 78.mpx,
    firstFocusKey: String = "favourite",
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    val labels = listOf(
        R.string.content_menu_group_watch, R.string.content_menu_group_library,
        R.string.content_menu_group_organise, R.string.content_menu_group_details,
    ).map { stringResource(it).uppercase(locale) }
    val ordered = arranged(menu, actions)
    val focusKey = ordered.firstOrNull { it.key == firstFocusKey && it.key !in disabled }?.key ?: ordered.firstOrNull { it.key !in disabled }?.key
    GuideMenuHost(x = x, top = top, width = 540.mpx, onDismiss = onDismiss) {
        StageMenuHeader(
            title = title, subtitle = subtitle,
            leading = {
                Box(Modifier.size(artWidth, artHeight).clip(RoundedCornerShape(9.mpx)).background(Color.White.copy(alpha = 0.06f))) {
                    if (!posterUrl.isNullOrBlank()) AsyncImage(model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            },
        )
        var previousGroup: Int? = null
        ordered.forEach { action ->
            if (action.group != previousGroup) StageGroupLabel(labels[action.group])
            previousGroup = action.group
            StageMenuItem(
                text = action.label, icon = action.icon, value = values[action.key],
                enabled = action.key !in disabled, onClick = action.onClick,
                modifier = if (action.key == focusKey) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
}

/**
 * The Movies / Series category list as the Stage sheet and column draw it (P5-04, P5-05): Favourites,
 * History and All movies / series with their icons, then the provider categories parsed into name and
 * tags, each with its count and playlist mark. Returns the entries and the "PROVIDERS" heading.
 */
@Composable
internal fun vodCategoryEntries(
    railItems: List<LiveRailItem>,
    counts: Map<LiveKey, Int>,
    marks: Map<Long, PlaylistMark>,
    series: Boolean,
): Pair<List<LiveCategoryEntry>, String> {
    val allLabel = if (series) R.string.content_category_all_series else R.string.content_category_all_movies
    val entries = railItems.map { item ->
        val label = item.displayLabel(allLabel)
        val parsed = if (item.key is LiveKey.Folder || item.key is LiveKey.Custom) ProviderTags.parse(label) else null
        LiveCategoryEntry(
            item = item,
            label = parsed?.name ?: label,
            name = parsed,
            icon = when (item.key) {
                LiveKey.Favorites -> OwnTVIcon.FAVORITE
                LiveKey.History -> OwnTVIcon.HISTORY
                LiveKey.All -> if (series) OwnTVIcon.SERIES else OwnTVIcon.MOVIES
                else -> null
            },
            count = counts[item.key],
            mark = item.sourceId?.let(marks::get),
        )
    }
    return entries to stringResource(R.string.content_vod_providers).uppercase(androidx.compose.ui.text.intl.Locale.current.platformLocale)
}

/**
 * Moves the List view (▲▼) and the Cinematic poster row (◀▶) by item number, as Live TV's channel list
 * does (fix playbook 2): a held key outruns focus search on a paged list and stops at the edge. Scrolls
 * just enough to show item [i] with one item of room, waits a frame, focuses it. A burst of presses
 * counts from [target], and the newest press cancels the step still in flight.
 */
internal class VodStepper(private val scope: CoroutineScope, private val state: () -> LazyListState) {
    private val requesters = HashMap<Int, FocusRequester>()
    fun focus(i: Int): FocusRequester = requesters.getOrPut(i) { FocusRequester() }
    var target: Int? = null
        private set
    private var job: Job? = null

    fun stepTo(i: Int) {
        target = i
        job?.cancel()
        job = scope.launch {
            val list = state()
            val info = list.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == i }
            val step = ((info.visibleItemsInfo.firstOrNull()?.size ?: 0) + info.mainAxisItemSpacing).toFloat()
            val delta = when {
                item == null -> if (i > (info.visibleItemsInfo.lastOrNull()?.index ?: 0)) step else -step
                item.offset - step < info.viewportStartOffset -> item.offset - step - info.viewportStartOffset
                item.offset + item.size + step > info.viewportEndOffset -> item.offset + item.size + step - info.viewportEndOffset
                else -> 0f
            }
            if (delta != 0f) runCatching { list.scrollBy(delta) }
            if (list.layoutInfo.visibleItemsInfo.none { it.index == i }) runCatching { list.scrollToItem(i) }
            withFrameNanos { }
            runCatching { focus(i).requestFocus() }
            target = null
        }
    }
}
