package tv.own.owntv.features.shell.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.settings.ClockColors
import tv.own.owntv.core.weather.WeatherInfo
import androidx.compose.runtime.staticCompositionLocalOf
import tv.own.owntv.ui.theme.parseAccentHex
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StagePill
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageText
import java.util.Date

/** The Continue pill's content: the icon, the small action label ("Resume") and the title. */
data class ContinuePill(val icon: OwnTVIcon, val iconFilled: Boolean, val action: String, val title: String, val onClick: () -> Unit)

/**
 * The top-right cluster on every screen: the Continue pill —
 * or, in Audio mode, the now-playing bar in its place — the playlist pill, then the clock with the date
 * and weather below it. Placed by the shell at the top right, 64 × 34 from the corner.
 *
 * [pillsFocusable] keeps the pills out of D-pad reach while focus is deep in a content list, so an
 * up-press at the top of a list never jumps here. [onPlaylistPillBounds] reports the playlist pill's
 * window bounds, where the playlist menu draws its copy of the pill above its scrim.
 */
@Composable
fun StageStatusCluster(
    continuePill: ContinuePill?,
    audioBar: (@Composable () -> Unit)?,
    playlistLabel: String,
    playlistInteractive: Boolean,
    onPlaylistClick: () -> Unit,
    pillsFocusable: Boolean,
    weatherInfo: WeatherInfo?,
    weatherFahrenheit: Boolean,
    clockColors: ClockColors,
    modifier: Modifier = Modifier,
    playlistDownFocusRequester: FocusRequester? = null,
    onPlaylistPillBounds: (Rect) -> Unit = {},
) {
    Row(
        modifier.padding(end = 64.mpx, top = 34.mpx),
        horizontalArrangement = Arrangement.spacedBy(22.mpx),
        // The pills' top lines up with the top of the clock's digits, not with the block's middle.
        verticalAlignment = Alignment.Top,
    ) {
        // Top, not centred: a taller audio bar (focused / open) must not push the playlist pill down.
        Row(Modifier.padding(top = 6.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.Top) {
            when {
                audioBar != null -> audioBar()
                continuePill != null -> StagePill(
                    text = continuePill.title,
                    small = continuePill.action,
                    icon = continuePill.icon,
                    iconFilled = continuePill.iconFilled,
                    onClick = continuePill.onClick,
                    // Narrower than a pill's usual cap: "Last channel" and a long channel name, in a
                    // long language, otherwise reach across the section's own header.
                    modifier = Modifier.widthIn(max = 300.mpx).focusProperties { canFocus = pillsFocusable },
                )
            }
            StagePill(
                text = playlistLabel,
                icon = OwnTVIcon.LAYERS,
                trailingIcon = if (playlistInteractive) OwnTVIcon.CHEVRON_DOWN else null,
                onClick = onPlaylistClick,
                enabled = playlistInteractive,
                modifier = Modifier
                    .onGloballyPositioned { onPlaylistPillBounds(it.boundsInWindow()) }
                    .focusProperties {
                        canFocus = pillsFocusable
                        if (playlistDownFocusRequester != null) down = playlistDownFocusRequester
                    },
            )
        }
        StageClock(weatherInfo, weatherFahrenheit, clockColors)
    }
}

/** The shell's weather and °F choice, for the clock preview on Settings › Date, time & weather. */
val LocalClockWeather = staticCompositionLocalOf<Pair<WeatherInfo?, Boolean>> { null to false }

/** A stored clock colour: blank = [default], "accent" = the accent, else the hex code. */
@Composable
fun clockColor(stored: String, default: Color): Color = when (stored) {
    "" -> default
    ClockColors.ACCENT -> stageAccent.accent
    else -> parseAccentHex(stored) ?: default
}

/**
 * `.status .clk` 40/700 tabular, and "Tue 29 Sep · ☀ 26° Düsseldorf" 17 px under it, right-aligned. Each
 * part takes its colour from [colors] (Settings › Appearance › Date, time & weather).
 */
@Composable
fun StageClock(weatherInfo: WeatherInfo?, fahrenheit: Boolean, colors: ClockColors) {
    val timeColor = clockColor(colors.time, StageColors.Text)
    val dateColor = clockColor(colors.date, StageColors.Muted)
    val weatherColor = clockColor(colors.weather, StageColors.Muted)
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val time = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now, locale) {
        android.text.format.DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), now).toString()
    }
    // The cluster's text is lifted off any backdrop with a soft black shadow. The TV cuts a text
    // shadow's blur off at the line's bounds, so a wide one showed as a dark box behind each line over a
    // bright backdrop: the shadow stays tight, and a soft oval glow behind the whole block does the lifting.
    // Light (`.mine.light .status`): no shadow and no dark glow; the block sits on a small white chip
    // instead (white 74%, ink 7% rim), drawn outside its bounds so the layout is the same as in dark.
    val light = LocalStagePalette.current.isLight
    val rim = StageColors.Ink.copy(alpha = 0.07f)
    val shadow = if (light) null else with(LocalDensity.current) { Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1.mpx.toPx()), 3.mpx.toPx()) }
    Column(
        horizontalAlignment = Alignment.End,
        // Unclipped (the GradientTextures glows clip to the node, which drew a hard box); a few hundred
        // pixels, so a brush is fine here. Drawn as a circle squashed to an oval around the block.
        modifier = Modifier.drawBehind {
            if (light) {
                val dx = 16.mpx.toPx()
                val dy = 10.mpx.toPx()
                val cr = androidx.compose.ui.geometry.CornerRadius(18.mpx.toPx())
                val tl = Offset(-dx, -dy)
                val sz = androidx.compose.ui.geometry.Size(size.width + 2 * dx, size.height + 2 * dy)
                drawRoundRect(Color.White.copy(alpha = 0.74f), tl, sz, cr)
                drawRoundRect(rim, tl, sz, cr, style = androidx.compose.ui.graphics.drawscope.Stroke(1.mpx.toPx()))
                return@drawBehind
            }
            val r = size.height * 1.2f

            scale(scaleX = (size.width * 0.8f) / r, scaleY = 1f, pivot = center) {
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = 0.34f),
                        0.55f to Color.Black.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                    center = center,
                )
            }
        },
    ) {
        Text(
            time,
            style = stageText(40, 700, (-0.5).mpxSp).copy(fontFeatureSettings = "tnum", shadow = shadow, lineHeight = 40.mpxSp),
            color = timeColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.padding(top = 6.mpx),
            horizontalArrangement = Arrangement.spacedBy(10.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sub: TextStyle = stageText(17, 400).copy(shadow = shadow)
            Text(date, style = sub, color = dateColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (weatherInfo != null) {
                Text("·", style = sub, color = StageColors.Muted.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                WeatherConditionIcon(weatherInfo, Modifier.size(17.mpx))
                val degrees = if (fahrenheit) (weatherInfo.temperatureC * 9 / 5 + 32).toInt() else weatherInfo.temperatureC.toInt()
                Text(
                    if (weatherInfo.city.isNotBlank()) {
                        stringResource(R.string.common_weather_degrees_city, degrees, weatherInfo.city)
                    } else {
                        stringResource(R.string.common_weather_degrees_city, degrees, "").trim()
                    },
                    style = sub,
                    color = weatherColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
