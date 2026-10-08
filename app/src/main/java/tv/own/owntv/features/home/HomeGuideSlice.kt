package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.home.GuideSliceState
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * A live rail on Home (Favourite channels, Recent channels): one `.oncard` per channel, 336 × 132 —
 * the logo on its plate, the name, and in "On now" mode the programme, the time left and its
 * progress. "Cards" mode is the same card with the name only.
 */
@Composable
fun HomeLiveRow(
    title: String,
    mode: HomeLiveRowMode,
    channels: List<ChannelEntity>,
    guide: GuideSliceState,
    onChannelClick: (Long, List<ChannelEntity>) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    start: Dp,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onNow = mode == HomeLiveRowMode.ON_NOW
    val shown = if (onNow) guide.channels else channels
    if (shown.isEmpty()) return
    Column(modifier) {
        HomeRowHeader(title, small = if (onNow) stringResource(R.string.home_row_on_now) else null, start = start)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(shown, key = { _, c -> c.id }) { index, channel ->
                OnNowCard(
                    channel = channel,
                    programme = if (onNow) currentProgramme(guide.programmes[channel.id].orEmpty(), guide.now) else null,
                    now = guide.now,
                    onClick = { onChannelClick(channel.id, shown) },
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                )
            }
        }
    }
}

/**
 * `.oncard`. Home's are 336 wide with the programme and the time left on two lines; More › Favourites
 * draws them 420 wide with both on one line ([oneLine], "Resident Evil · 1 h 23 min left").
 */
@Composable
internal fun OnNowCard(
    channel: ChannelEntity,
    programme: EpgProgrammeEntity?,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 336.mpx,
    oneLine: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val a = stageAccent
    StageSurface(
        onClick = onClick,
        radius = StageRadii.Row,
        modifier = modifier.size(width, 132.mpx),
        onLongClick = onLongClick,
        focusStyle = StageFocus.FX,
        idle = Modifier.background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(StageRadii.Row)),
        contentAlignment = Alignment.TopStart,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.mpx, vertical = 16.mpx),
            horizontalArrangement = Arrangement.spacedBy(16.mpx),
        ) {
            ChannelPlate(channel, Modifier.size(84.mpx, 60.mpx))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(channel.name, style = stageText(20, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (programme != null) {
                    val left = timeLeftText(((programme.stopMs - now).coerceAtLeast(0L) + 59_999L) / 60_000L)
                    Spacer(Modifier.height(3.mpx))
                    if (oneLine) {
                        Text(programme.title + stringResource(R.string.content_epg_bits_separator) + left, style = stageText(15, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.mpx))
                    } else {
                        Text(programme.title, style = stageText(15, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.mpx))
                        Text(left, style = stageText(15, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.mpx))
                    }
                    val fraction = programmeProgress(programme, now)
                    Box(Modifier.fillMaxWidth().height(5.mpx).clip(RoundedCornerShape(3.mpx)).background(Color.White.copy(alpha = 0.12f))) {
                        // A 5 px bar: small enough to stay a brush (see GradientTextures).
                        Box(
                            Modifier.fillMaxWidth(fraction).height(5.mpx).clip(RoundedCornerShape(3.mpx))
                                .background(Brush.horizontalGradient(listOf(a.accent, a.focus))),
                        )
                    }
                }
            }
        }
    }
}

/**
 * `.plate`: a channel logo, transparent unless it would be unreadable ([ChannelLogoTile]); the
 * channel's number or initial when it has no logo.
 */
@Composable
fun ChannelPlate(channel: ChannelEntity, modifier: Modifier = Modifier) {
    ChannelLogoTile(
        logoUrl = channel.displayLogoUrl,
        modifier = modifier.clip(RoundedCornerShape(StageRadii.Plate)),
        fill = StageColors.ControlFill,
    ) {
        Text(
            channel.number?.toString() ?: channel.name.take(1).uppercase(),
            style = stageText(18, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun currentProgramme(programmes: List<EpgProgrammeEntity>, now: Long): EpgProgrammeEntity? =
    programmes.firstOrNull { now in it.startMs until it.stopMs }

private fun programmeProgress(programme: EpgProgrammeEntity, now: Long): Float {
    val duration = (programme.stopMs - programme.startMs).coerceAtLeast(1L)
    return ((now - programme.startMs).toFloat() / duration).coerceIn(0f, 1f)
}
