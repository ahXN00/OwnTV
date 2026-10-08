package tv.own.owntv.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.home.TrendingHomeItem
import tv.own.owntv.features.home.HomeViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.drawInnerRing
import tv.own.owntv.ui.stage.drawOuterRing
import tv.own.owntv.ui.stage.stageBackground
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.dissolveEdges
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * Settings › Appearance's LIVE PREVIEW (P9-04, G9): a small Home drawn from the first Trending title, at a
 * third of the screen's size. Not a second Home — no data of its own, nothing focusable — but it draws
 * with the live accent, focus colour, glass and font, so a change shows here before the row is left.
 */
@Composable
internal fun SettingsLivePreview() {
    val home: HomeViewModel = koinViewModel()
    val state by home.uiState.collectAsStateWithLifecycle()
    val items = state.trendingItems
    val lead = items.firstOrNull()
    val a = stageAccent
    SettingPanelHeading(stringResource(R.string.settings_quick_live_preview))
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.mpx))
            .drawBehind { drawInnerRing(Color.White.copy(alpha = 0.1f), 1.mpx.toPx(), 18.mpx.toPx()) },
    ) {
        // The 1920 × 1080 frame, scaled down to the box: every size below is the real screen's.
        val scale = maxWidth / 1920.mpx
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(1920.mpx, 1080.mpx)
                .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) },
        ) {
            // The user's picture, when Glass & background shows one, as behind the real Home.
            val background = tv.own.owntv.ui.theme.LocalBackground.current
            if (background.showsPicture) {
                tv.own.owntv.ui.components.BackgroundPicture(background, tv.own.owntv.ui.theme.LocalBlurredBackdrop.current, Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().stageBackground(a.accent))
            if (lead != null) {
                AsyncImage(
                    model = backdropOf(lead),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.TopEnd).size(1240.mpx, 760.mpx).dissolveEdges(start = 0.45f, bottom = 0.35f),
                )
            }
            // The rail at rest: a glass capsule of icons.
            Column(
                Modifier.padding(start = 26.mpx, top = 230.mpx).width(60.mpx).stageGlass(30.mpx).padding(vertical = 18.mpx),
                verticalArrangement = Arrangement.spacedBy(22.mpx),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf(OwnTVIcon.SEARCH, OwnTVIcon.HOME, OwnTVIcon.LIVE_TV, OwnTVIcon.EPG, OwnTVIcon.MOVIES, OwnTVIcon.SERIES, OwnTVIcon.DOWNLOADS).forEachIndexed { i, icon ->
                    OwnTVIcon(icon, if (i == 1) a.accent else StageColors.Muted, Modifier.size(26.mpx))
                }
            }
            Column(Modifier.padding(start = 150.mpx, top = 300.mpx).width(820.mpx)) {
                Text(stringResource(R.string.home_row_now_trending).uppercase(), style = stageText(15, 800, 0.12.em), color = a.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    titleOf(lead) ?: stringResource(R.string.home_row_now_trending),
                    style = stageText(92, 800, (-2.5f / 92f).em), color = StageColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx),
                )
                Row(Modifier.padding(top = 40.mpx), horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    // The hero's Play, drawn focused: the accent fill and the focus ring.
                    Row(
                        Modifier
                            .height(64.mpx)
                            .drawBehind { drawOuterRing(a.focus, 3.mpx.toPx(), 22.mpx.toPx()) }
                            .background(a.accent, RoundedCornerShape(22.mpx))
                            .padding(horizontal = 30.mpx),
                        horizontalArrangement = Arrangement.spacedBy(12.mpx),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OwnTVIcon(OwnTVIcon.PLAY, a.onAccent, Modifier.size(22.mpx), filled = true)
                        Text(stringResource(R.string.home_trending_play), style = stageText(22, 700), color = a.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(
                        Modifier.height(64.mpx).stageGlass(22.mpx).padding(horizontal = 30.mpx),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.home_trending_trailer), style = stageText(22, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.padding(start = 150.mpx, top = 760.mpx)) {
                Text(stringResource(R.string.home_row_keep_watching), style = stageText(27, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 18.mpx), horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
                    items.drop(1).take(5).forEachIndexed { i, item ->
                        Column(Modifier.width(380.mpx)) {
                            AsyncImage(
                                model = backdropOf(item),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(214.mpx).clip(RoundedCornerShape(16.mpx)).background(Color.White.copy(alpha = 0.06f)),
                            )
                            StageProgress(listOf(0.55f, 0.3f, 0.8f, 0.45f, 0.2f)[i], Modifier.padding(top = 10.mpx).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
    Text(
        stringResource(R.string.settings_live_preview_note), style = stageText(15, 500), color = StageColors.Muted,
        modifier = Modifier.padding(top = 12.mpx, start = 2.mpx),
    )
    Box(Modifier.padding(top = 22.mpx, bottom = 18.mpx).fillMaxWidth().height(1.mpx).background(Color.White.copy(alpha = 0.1f)))
}

private fun titleOf(item: TrendingHomeItem?): String? =
    item?.snapshot?.let { it.localizedTitle.ifBlank { it.canonicalTitle } }

internal fun backdropOf(item: TrendingHomeItem): String? =
    tv.own.owntv.core.metadata.MetadataImages.backdrop(item.snapshot.backdropPath, size = "w780")
        ?: when (item) {
            is TrendingHomeItem.Movie -> item.movie.backdropUrl ?: item.movie.posterUrl
            is TrendingHomeItem.Series -> item.series.backdropUrl ?: item.series.posterUrl
        }
