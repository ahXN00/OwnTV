package tv.own.owntv.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.sports.GuideSport
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.RaceSession
import tv.own.owntv.core.sports.model.Score
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.Team
import tv.own.owntv.ui.stage.drawBoxShadow
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The live red: the LIVE dot, a running minute. */
internal val SportsLive = Color(0xFFFF4D4D)

/** `.cr`: a team's crest (ESPN's 96 px logo) in a circle, or its abbreviation when there is none. */
@Composable
internal fun Crest(team: Team?, size: Int) {
    Box(
        Modifier.size(size.mpx).clip(CircleShape).background(StageColors.Ink.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        val url = team?.logoUrl
        if (url.isNullOrBlank()) {
            Text(
                team?.abbreviation?.ifBlank { null } ?: initials(team?.shortName.orEmpty()),
                style = stageText(size * 0.3f, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding((size * 0.1f).mpx))
        }
    }
}

/** "J. Sinner" → "JS": an athlete has no crest and no abbreviation, so the crest shows the initials. */
private fun initials(name: String): String {
    val words = name.split(' ', '.', '-').filter { it.isNotBlank() }
    return (words.firstOrNull()?.take(1).orEmpty() + words.drop(1).lastOrNull()?.take(1).orEmpty()).uppercase()
}

/** `.pill-live`: the red dot and a white caps label ("LIVE", "LIVE · 63'"). */
@Composable
internal fun LiveMark(text: String, textSize: Float = 16.5f) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.mpx), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.mpx).drawBehind { drawBoxShadow(SportsLive.copy(alpha = 0.25f), 4.mpx.toPx(), size.minDimension / 2f); drawCircle(SportsLive) })
        Text(text, style = stageText(textSize, 800, 0.06.em), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
@ReadOnlyComposable
internal fun sportLabel(sport: Sport): String = stringResource(
    when (sport) {
        Sport.SOCCER -> R.string.sports_sport_soccer
        Sport.BASKETBALL -> R.string.sports_sport_basketball
        Sport.TENNIS -> R.string.sports_sport_tennis
        Sport.CRICKET -> R.string.sports_sport_cricket
        Sport.RACING -> R.string.sports_sport_motorsport
        Sport.HOCKEY -> R.string.sports_sport_hockey
        Sport.FOOTBALL -> R.string.sports_sport_football
        Sport.MMA -> R.string.sports_sport_mma
        Sport.BASEBALL -> R.string.sports_sport_baseball
        Sport.GOLF -> R.string.sports_sport_golf
        Sport.RUGBY -> R.string.sports_sport_rugby
        Sport.RUGBY_LEAGUE -> R.string.sports_sport_rugby_league
        Sport.AUSSIE_RULES -> R.string.sports_sport_aussie_rules
        Sport.VOLLEYBALL -> R.string.sports_sport_volleyball
        Sport.LACROSSE -> R.string.sports_sport_lacrosse
    },
)

/** A racing session's name ("Practice 1", "Qualifying"), from ESPN's code on the match; null when it has none. */
@Composable
@ReadOnlyComposable
internal fun sessionLabel(m: Match): String? = RaceSession.of(m.subtitle)?.let { s ->
    stringResource(
        when (s) {
            RaceSession.PRACTICE_1 -> R.string.sports_session_practice_1
            RaceSession.PRACTICE_2 -> R.string.sports_session_practice_2
            RaceSession.PRACTICE_3 -> R.string.sports_session_practice_3
            RaceSession.SPRINT_QUALIFYING -> R.string.sports_session_sprint_qualifying
            RaceSession.SPRINT -> R.string.sports_session_sprint
            RaceSession.QUALIFYING -> R.string.sports_session_qualifying
            RaceSession.RACE -> R.string.sports_session_race
        },
    )
}

/** The chip emoji per sport. */
internal fun sportEmoji(sport: Sport): String = when (sport) {
    Sport.SOCCER -> "⚽"
    Sport.BASKETBALL -> "🏀"
    Sport.TENNIS -> "🎾"
    Sport.CRICKET -> "🏏"
    Sport.RACING -> "🏎"
    Sport.HOCKEY -> "🏒"
    Sport.FOOTBALL -> "🏈"
    Sport.MMA -> "🥊"
    Sport.BASEBALL -> "⚾"
    Sport.GOLF -> "⛳"
    Sport.RUGBY, Sport.RUGBY_LEAGUE, Sport.AUSSIE_RULES -> "🏉"
    Sport.VOLLEYBALL -> "🏐"
    Sport.LACROSSE -> "🥍"
}

@Composable
@ReadOnlyComposable
internal fun guideSportLabel(sport: GuideSport): String = stringResource(
    when (sport) {
        GuideSport.FOOTBALL -> R.string.sports_sport_soccer
        GuideSport.HANDBALL -> R.string.sports_sport_handball
        GuideSport.BASKETBALL -> R.string.sports_sport_basketball
        GuideSport.TENNIS -> R.string.sports_sport_tennis
        GuideSport.ICE_HOCKEY -> R.string.sports_sport_hockey
        GuideSport.MOTORSPORT -> R.string.sports_sport_motorsport
        GuideSport.DARTS -> R.string.sports_sport_darts
        GuideSport.SNOOKER -> R.string.sports_sport_snooker
        GuideSport.CYCLING -> R.string.sports_sport_cycling
        GuideSport.CRICKET -> R.string.sports_sport_cricket
        GuideSport.COMBAT -> R.string.sports_sport_combat
        GuideSport.AMERICAN_FOOTBALL -> R.string.sports_sport_american_football
        GuideSport.BASEBALL -> R.string.sports_sport_baseball
        GuideSport.GOLF -> R.string.sports_sport_golf
        GuideSport.RUGBY -> R.string.sports_sport_rugby
        GuideSport.VOLLEYBALL -> R.string.sports_sport_volleyball
        GuideSport.ATHLETICS -> R.string.sports_sport_athletics
        GuideSport.WINTER -> R.string.sports_sport_winter
        GuideSport.OTHER -> R.string.sports_sport_other
    },
)

/** The competition's brand name from the catalogue ("Bundesliga"), else ESPN's ("German Bundesliga"). */
internal fun leagueName(m: Match): String = SportsCatalog.league(League.keyOf(m.sport, m.leagueSlug))?.name ?: m.leagueName

/** A side's score as shown: tennis as its set games ("6 3"), everything else as ESPN's text. */
internal fun scoreText(sport: Sport, score: Score?): String = when {
    score == null -> ""
    sport == Sport.TENNIS && score.periods.isNotEmpty() -> score.periods.joinToString(" ") { it.value.toString() }
    else -> score.text
}

/** The state on a card's top line: the minute in red while live ("63'", "Set 2", "38.2 ov"), else the kick-off. */
@Composable
internal fun clockText(m: Match): String {
    val clock = m.clock
    return when {
        m.sport == Sport.TENNIS && clock != null && clock.period > 0 -> stringResource(R.string.sports_set, clock.period)
        m.sport == Sport.CRICKET && clock?.overs != null -> stringResource(R.string.sports_overs, clock.overs!!)
        clock != null && clock.display.isNotBlank() -> clock.display
        else -> m.statusText
    }
}

/** "13:30" in the clock format the rest of the app uses (the device's 12/24-hour setting). */
@Composable
internal fun timeText(ms: Long): String = tv.own.owntv.ui.format.rememberSystemTimeFormatter()(ms)

/** The day a match falls on, when it is not today: "Sat" within a week, "Fri 23" beyond it. Null for today. */
internal fun dayOf(ms: Long, locale: Locale, today: LocalDate = LocalDate.now()): String? {
    val date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    if (date == today) return null
    val pattern = if (date.isBefore(today.plusDays(7))) "EEE" else android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEd")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

internal fun isToday(ms: Long): Boolean = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

/** Postponed or cancelled: the match will not be played as listed. */
internal val Match.off: Boolean get() = state == MatchState.POSTPONED || state == MatchState.CANCELLED
