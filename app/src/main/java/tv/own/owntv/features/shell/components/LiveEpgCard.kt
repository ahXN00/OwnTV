package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.core.live.EpgNowNext

/** Which pair of slots a [LiveEpgCard] is describing: what is on air now, or what was on air
 *  at the moment being replayed out of the archive. */
enum class EpgCardVariant { LIVE, ARCHIVE }

/**
 * Now / Next for the PLAYING live channel, laid out horizontally so it can live in the player's top
 * bar beside the channel identity (the older vertical Before/Now/Next card sat on the right edge,
 * where the history channel list now goes). Informational only: never focusable, and renders NOTHING
 * when the channel has no guide data — a permanent "no info" block would be noise on every unhide.
 */
@Composable
fun LiveEpgCard(
    epg: EpgNowNext?,
    modifier: Modifier = Modifier,
    // ARCHIVE renders the same two slots for a programme being replayed: different labels, a teal
    // frame matching the "watching" clock, and progress measured against the replayed instant rather
    // than against now. Two identically-labelled cards a few pixels apart would be unreadable.
    variant: EpgCardVariant = EpgCardVariant.LIVE,
    // ARCHIVE only: the wall-clock instant on screen, which drives the progress bar and "x min left".
    atMs: Long? = null,
) {
    if (epg == null || (epg.now == null && epg.next == null)) return
    val archive = variant == EpgCardVariant.ARCHIVE
    val colors = OwnTVTheme.colors
    val formatTime = rememberSystemTimeFormatter()
    // Drives the "x min left" text and the progress bar; a slow tick is plenty for minute precision.
    var wallNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); wallNow = System.currentTimeMillis() } }
    // On the archive card every "how far through are we" question is asked about the replayed moment,
    // not about the present — otherwise a programme from yesterday reads as 100% finished.
    val nowMs = if (archive) (atMs ?: wallNow) else wallNow

    Row(
        modifier = if (!archive) modifier else modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.primary.copy(alpha = 0.09f))
            .border(1.dp, colors.primary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        epg.now?.let { entry ->
            Column(Modifier.widthIn(max = 300.dp)) {
                SlotLabel(stringResource(if (archive) R.string.content_archive_playing else R.string.content_live_now), colors.primary)
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val remaining = ((entry.stopMs - nowMs) / 60_000L).toInt()
                Text(
                    if (remaining in 1..600) {
                        stringResource(
                            R.string.content_live_time_remaining,
                            formatTime(entry.stopMs),
                            remaining,
                        )
                    } else {
                        stringResource(
                            R.string.content_live_time_range,
                            formatTime(entry.startMs),
                            formatTime(entry.stopMs),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // What the programme actually IS, not just what it is called. The guide carries this
                // for both stored XMLTV rows and the providers' own short-EPG replies, and until now
                // the only place it surfaced was the Guide screen — so anyone watching live could read
                // a title and nothing else. Two lines: enough to tell an episode apart, short enough
                // that the card stays a strip across the top of the picture.
                entry.description?.takeIf { it.isNotBlank() }?.let { synopsis ->
                    Text(
                        synopsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val span = (entry.stopMs - entry.startMs).toFloat()
                if (span > 0f) {
                    val progress = ((nowMs - entry.startMs) / span).coerceIn(0f, 1f)
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(progress).height(2.dp)
                                .clip(RoundedCornerShape(1.dp)).background(colors.primary),
                        )
                    }
                }
            }
        }
        // Hairline separator, only when both halves are present.
        if (epg.now != null && epg.next != null) {
            Box(Modifier.height(34.dp).widthIn(min = 1.dp, max = 1.dp).background(Color.White.copy(alpha = 0.18f)))
        }
        epg.next?.let { entry ->
            Column(Modifier.widthIn(max = 240.dp)) {
                SlotLabel(stringResource(if (archive) R.string.content_archive_then else R.string.content_live_next), Color.White.copy(alpha = 0.45f))
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.content_live_time_range,
                        formatTime(entry.startMs),
                        formatTime(entry.stopMs),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * N3 — the guide lines under the channel-change banner: what is on now, how far through it is, and what
 * comes next. The same labels, time formats and progress bar as [LiveEpgCard], packed to fit the
 * banner's width. Draws nothing for a channel without a guide, so the banner stays name and number.
 */
@Composable
fun ZapGuideLines(epg: EpgNowNext?, modifier: Modifier = Modifier) {
    if (epg == null || (epg.now == null && epg.next == null)) return
    val colors = OwnTVTheme.colors
    val formatTime = rememberSystemTimeFormatter()
    // The banner is up for a few seconds, so the moment it opened is precise enough.
    val nowMs = remember(epg) { System.currentTimeMillis() }
    Column(modifier.padding(top = 8.dp)) {
        epg.now?.let { entry ->
            SlotLabel(stringResource(R.string.content_live_now), colors.primary)
            Text(entry.title, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val remaining = ((entry.stopMs - nowMs) / 60_000L).toInt()
            Text(
                if (remaining in 1..600) stringResource(R.string.content_live_time_remaining, formatTime(entry.stopMs), remaining)
                else stringResource(R.string.content_live_time_range, formatTime(entry.startMs), formatTime(entry.stopMs)),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val span = (entry.stopMs - entry.startMs).toFloat()
            if (span > 0f) {
                val progress = ((nowMs - entry.startMs) / span).coerceIn(0f, 1f)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(progress).height(2.dp).clip(RoundedCornerShape(1.dp)).background(colors.primary))
                }
            }
        }
        epg.next?.let { entry ->
            Spacer(Modifier.height(8.dp))
            SlotLabel(stringResource(R.string.content_live_next), Color.White.copy(alpha = 0.45f))
            Text(entry.title, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.content_live_time_range, formatTime(entry.startMs), formatTime(entry.stopMs)),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SlotLabel(text: String, color: Color) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 1.dp),
    )
}
