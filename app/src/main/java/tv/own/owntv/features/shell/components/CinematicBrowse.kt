package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import tv.own.owntv.ui.theme.animationsOn
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.ownTvTween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import tv.own.owntv.R
import androidx.compose.ui.res.stringResource
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * The Cinematic frame shared by Movies and Series: the focused title's backdrop full-bleed behind
 * everything, two fixed wash gradients over it, and the caller's own browse row on top.
 *
 * It owns no data and no focus. The rail, the detail block and the grid all stay where they already
 * live, so every focus, paging and restore behaviour the Separate layout has is unchanged — this
 * only changes what is drawn behind them.
 *
 * The washes are deliberately fixed and heavy on the left: the rail and the detail text sit there,
 * and a bright backdrop must never win against them. Verified on a television, not on a monitor.
 */
@Composable
fun CinematicBrowse(
    enabled: Boolean,
    backdropUrl: String?,
    modifier: Modifier = Modifier,
    /** False while this pane is pinned inside More, which already draws the panel around it. */
    rounded: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val colors = OwnTVTheme.colors
    val wash = colors.surfaceContainerLowest
    // Same radius and hairline edge as every other panel in the shell. Cinematic drops the opaque
    // plate so the artwork can show, but it must not drop the SHAPE with it — square corners next to
    // the rounded category panel is the one thing that makes this look bolted on.
    val shape = RoundedCornerShape(CINEMATIC_PANEL_RADIUS)
    // Holding Down across a row of posters would otherwise fire one image load per poster it passes
    // under. Settle first, then swap: only the title the cursor actually rests on is ever fetched.
    var settledBackdrop by remember { mutableStateOf(backdropUrl) }
    LaunchedEffect(backdropUrl) {
        if (backdropUrl != settledBackdrop) {
            delay(BACKDROP_SETTLE_MS)
            settledBackdrop = backdropUrl
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .then(if (rounded) Modifier.clip(shape).border(1.dp, colors.outlineVariant.copy(alpha = 0.66f), shape) else Modifier),
    ) {
        // Off snaps instantly — ownTvTween already resolves to 0 ms, and Crossfade is safe with it
        // (it is a one-shot tween, not an infiniteRepeatable).
        Crossfade(targetState = settledBackdrop, animationSpec = ownTvTween(420), label = "cinematic-backdrop") { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Left wash — keeps the rail and the detail text legible over any artwork.
        Box(
            Modifier.fillMaxSize().gradientWash(
                vertical = false,
                0f to wash.copy(alpha = 0.96f),
                0.34f to wash.copy(alpha = 0.86f),
                0.66f to wash.copy(alpha = 0.30f),
                1f to wash.copy(alpha = 0.10f),
            ),
        )
        // Vertical wash — dark at the very top for the shell's top bar, opening up across the
        // artwork, then closing down hard so the poster grid has a settled floor to sit on.
        Box(
            Modifier.fillMaxSize().gradientWash(
                vertical = true,
                0f to wash.copy(alpha = 0.55f),
                0.26f to wash.copy(alpha = 0.10f),
                0.58f to wash.copy(alpha = 0.55f),
                0.82f to wash.copy(alpha = 0.94f),
                1f to wash.copy(alpha = 0.98f),
            ),
        )
        content()
    }
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

/**
 * The read-only detail block that sits above the Cinematic poster grid: title-logo artwork, the
 * metadata line with its badges, genres, a clamped plot and cast photos.
 *
 * **It contains no focusable children at all** — that is the whole design. Focus never leaves the
 * grid, so a button up here could not be reached from the second poster row anyway. What would have
 * been a Resume button is kept as information instead: [resumeLabel] renders as the green badge, and
 * the poster carries the same progress as a sliver.
 *
 * Every field degrades on its own. No logo draws the title as text, no cast omits the row rather
 * than leaving empty circles, and a title with no TMDB match at all still shows the provider's name
 * and whatever the provider gave.
 */
@Composable
fun CinematicDetails(
    title: String,
    logoUrl: String?,
    metaLine: String,
    qualityBadges: List<String>,
    resumeLabel: String?,
    genres: List<String>,
    plot: String?,
    cast: List<tv.own.owntv.core.metadata.CastMember>,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    // Staggered fade-in: the title settles first and the supporting rows follow it in, so moving
    // between titles reads as a change rather than a flicker. Keyed on the title, so it replays for
    // each new one; with Animations = Off every tween is 0 ms and it simply appears.
    val staggerSpec = ownTvTween<Float>(380)
    val stagger = if (!animationsOn) 1f else {
        val alpha = remember(title) { Animatable(0f) }
        LaunchedEffect(title) { alpha.animateTo(1f, staggerSpec) }
        alpha.value
    }
    // Each row starts a little after the one above it, all off the single animation above.
    fun step(order: Int): Float = ((stagger - order * 0.14f) / 0.58f).coerceIn(0f, 1f)
    // Clipped, because the caller caps the height from Panel Width Adjustment: a long plot must be
    // cut off cleanly rather than drawn over the poster grid below it.
    Column(modifier.width(640.dp).clipToBounds()) {
        // Title-logo artwork AND the name — never the logo alone.
        //
        // TMDB serves both light and dark variants of a title logo and nothing in the data says
        // which one this is. A black line-art logo over the dark wash renders perfectly and is
        // completely unreadable, so a logo-only title can vanish without anything having failed.
        // Showing the name too costs one small line and means the title is always legible,
        // whatever artwork comes back. [logoFailed] then only has to cover an outright bad URL.
        var logoFailed by remember(logoUrl) { mutableStateOf(false) }
        val showLogo = !logoUrl.isNullOrBlank() && !logoFailed
        if (showLogo) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onState = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
                modifier = Modifier.heightIn(max = 64.dp).padding(bottom = 4.dp).alpha(step(0)),
            )
        }
        Text(
            title,
            // Under a logo the name is a caption; on its own it is the headline.
            style = if (showLogo) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (showLogo) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 8.dp).alpha(step(0)),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.alpha(step(1)),
        ) {
            if (metaLine.isNotBlank()) {
                Text(metaLine, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            // Outlined, never filled: these should read as film-poster credits, not as UI chips.
            qualityBadges.forEach { badge ->
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurface,
                    modifier = Modifier
                        .border(1.dp, colors.onSurface.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
            if (resumeLabel != null) {
                Text(
                    resumeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.primary.copy(alpha = 0.20f))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                genres.joinToString(stringResource(R.string.content_genres_separator)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(step(2)),
            )
        }
        if (!plot.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                plot,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(step(3)),
            )
        }
        if (cast.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp), modifier = Modifier.alpha(step(4))) {
                cast.take(6).forEach { member ->
                    Column(
                        modifier = Modifier.width(62.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val photo = tv.own.owntv.core.metadata.MetadataImages.profile(member.profilePath)
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(23.dp))
                                .background(colors.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (photo != null) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                // Plenty of credited actors have no TMDB photo; initials beat a gap.
                                Text(
                                    member.name.split(' ').mapNotNull { it.firstOrNull() }.take(2)
                                        .joinToString("").uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            member.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** How long the cursor must rest on a title before its artwork is fetched. */
private const val BACKDROP_SETTLE_MS = 260L

/** Matches `roundedPanel`'s default, so Cinematic sits in the same shell as every other screen. */
private val CINEMATIC_PANEL_RADIUS = 22.dp
