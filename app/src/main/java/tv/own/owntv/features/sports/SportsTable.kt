package tv.own.owntv.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.StandingsColumn
import tv.own.owntv.core.sports.model.RankingRow
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.StandingsRow
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/** The number columns of a league table (football: P W D L GD Pts), each 51 wide. */
private val NumberColumn = 51.mpx

private val StandingsColumn.label: Int get() = when (this) {
    StandingsColumn.PLAYED -> R.string.sports_table_played
    StandingsColumn.WON -> R.string.sports_table_won
    StandingsColumn.DRAWN -> R.string.sports_table_drawn
    StandingsColumn.TIED -> R.string.sports_table_tied
    StandingsColumn.LOST -> R.string.sports_table_lost
    StandingsColumn.OT_LOSSES -> R.string.sports_table_ot_losses
    StandingsColumn.DIFFERENCE -> R.string.sports_table_difference
    StandingsColumn.WIN_PERCENT -> R.string.sports_table_win_percent
    StandingsColumn.GAMES_BEHIND -> R.string.sports_table_games_behind
    StandingsColumn.POINTS -> R.string.sports_table_points
}

/** `# Team` and [sport]'s own columns (football: `P W D L GD Pts`), 16.5 px dim. */
@Composable
internal fun StandingsHeader(sport: Sport?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.mpx, vertical = 6.mpx), verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = stageText(16.5f, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(42.mpx))
        Text(stringResource(R.string.sports_table_team), style = stageText(16.5f, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        StandingsColumn.of(sport).forEach { Text(stringResource(it.label), style = stageText(16.5f, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(NumberColumn)) }
    }
}

/**
 * One table row; [highlight] (a side of the open match, a followed team) on accent 14%, [star] for one
 * of My teams. With [onClick] the row takes focus and OK opens the team's page.
 */
@Composable
internal fun StandingsLine(row: StandingsRow, sport: Sport?, highlight: Boolean, star: Boolean = false, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    if (onClick == null) {
        StandingsCells(row, sport, highlight, star, focused = false)
        return
    }
    StageSurface(onClick = onClick, radius = 10.mpx, modifier = modifier.fillMaxWidth()) { focused -> StandingsCells(row, sport, highlight, star, focused) }
}

@Composable
private fun StandingsCells(row: StandingsRow, sport: Sport?, highlight: Boolean, star: Boolean, focused: Boolean) {
    val on = if (focused) stageAccent.onAccent else null
    Row(
        Modifier.fillMaxWidth()
            .then(if (highlight && !focused) Modifier.background(stageAccent.accent.copy(alpha = 0.14f), RoundedCornerShape(10.mpx)) else Modifier)
            .padding(horizontal = 12.mpx, vertical = 7.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.rank.toString(), style = stageText(19, 700), color = on ?: StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(42.mpx))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
            Crest(row.team, 30)
            Text(row.team.shortName, style = stageText(19, if (highlight) 800 else 600), color = on ?: StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (star) Text("★", style = stageText(17, 700), color = on ?: Color(0xFFFFD166), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val main = StandingsColumn.main(sport)
        StandingsColumn.of(sport).forEach { c ->
            val bold = c == main
            Text(c.valueOf(row), style = stageText(19, if (bold) 800 else 500), color = on ?: if (bold) StageColors.Text else StageColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(NumberColumn))
        }
    }
}

/** A ranking's head: `#`, [name] (Drivers, Constructors, Rankings), the gap to the leader, Pts. */
@Composable
internal fun RankingHeader(name: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.mpx, vertical = 6.mpx), verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = stageText(16.5f, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(42.mpx))
        Text(name, style = stageText(16.5f, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.sports_table_points), style = stageText(16.5f, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(RankColumn))
    }
}

/**
 * One place in a ranking: rank, the initials (an athlete has no crest) or crest, the name with its
 * country, the gap to [leaderPoints] ("−102") and the points. Focusable so the list scrolls; OK does nothing.
 */
@Composable
internal fun RankingLine(row: RankingRow, leaderPoints: Int, highlight: Boolean = false) {
    StageSurface(onClick = {}, radius = 10.mpx, modifier = Modifier.fillMaxWidth()) { focused ->
        val on = if (focused) stageAccent.onAccent else null
        Row(
            Modifier.fillMaxWidth()
                .then(if (highlight && !focused) Modifier.background(stageAccent.accent.copy(alpha = 0.12f), RoundedCornerShape(10.mpx)) else Modifier)
                .padding(horizontal = 12.mpx, vertical = 7.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.rank.toString(), style = stageText(19, 700), color = on ?: StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(42.mpx))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                // No crest: a driver by initials, a constructor by its first letters ("MER"); ESPN's own abbreviation for constructors is not theirs.
                Crest(row.team.copy(abbreviation = if (row.team.isAthlete) "" else row.team.shortName.take(3).uppercase(java.util.Locale.ROOT)), 30)
                Text(row.team.name, style = stageText(19, 700), color = on ?: StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                row.team.country?.let { Text(it, style = stageText(16, 600), color = on ?: StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            val gap = row.points - leaderPoints
            Text(if (gap == 0) "—" else "−${-gap}", style = stageText(18, 500), color = on ?: StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(RankColumn))
            Text(row.points.toString(), style = stageText(19, 800), color = on ?: StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.width(RankColumn))
        }
    }
}

/** The gap and points columns of a ranking. */
private val RankColumn = 90.mpx
