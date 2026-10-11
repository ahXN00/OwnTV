package tv.own.owntv.tifo.sports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageRow
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/** The leagues whose teams can be favourites: the team sports, not races or tournaments. */
private val TeamLeagues = Competition.entries.filter { it.kind == SportKind.TEAM }

/**
 * Sports' own settings: the favourite teams, league by league. Leagues on the left, the open
 * league's teams on the right; OK on a team stars or unstars it. Their games make the "My teams"
 * tab. Back closes it.
 */
@Composable
fun SportsTeamsSettings(
    vm: SportsViewModel,
    favorites: SportsFavorites,
    contentStart: Dp,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val keys by favorites.keys.collectAsStateWithLifecycle()
    val teams by vm.teams.collectAsStateWithLifecycle()
    var leagueName by rememberSaveable { mutableStateOf(TeamLeagues.first().name) }
    val league = TeamLeagues.firstOrNull { it.name == leagueName } ?: TeamLeagues.first()
    LaunchedEffect(league) { vm.loadTeams(league) }
    val firstLeague = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstLeague.requestFocus() } }

    Column(
        modifier.padding(start = contentStart, end = 48.mpx, top = 40.mpx, bottom = 24.mpx),
        verticalArrangement = Arrangement.spacedBy(18.mpx),
    ) {
        Text(stringResource(R.string.tifo_sports_teams_title), style = stageText(40, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.tifo_sports_teams_hint), style = stageText(18, 500), color = StageColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.mpx)) {
            LazyColumn(Modifier.weight(0.34f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                items(TeamLeagues, key = { it.name }) { c ->
                    val picked = keys.count { it.startsWith(c.name + ":") }
                    LeagueRow(
                        label = stringResource(c.labelRes),
                        count = picked,
                        selected = c == league,
                        onClick = { leagueName = c.name },
                        modifier = if (c == TeamLeagues.first()) Modifier.focusRequester(firstLeague) else Modifier,
                    )
                }
            }
            Column(Modifier.weight(0.66f).fillMaxHeight()) {
                when (val list = teams[league]) {
                    null, TeamList.Loading -> Note(stringResource(R.string.tifo_sports_teams_loading))
                    TeamList.Failed -> Note(stringResource(R.string.tifo_sports_teams_failed))
                    is TeamList.Loaded -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.mpx)) {
                        items(list.teams, key = { it.id }) { team ->
                            TeamRow(
                                team = team,
                                favorite = SportsFavorites.isFavorite(keys, league, team),
                                onClick = { favorites.toggle(league, team) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = stageText(20, 500), color = StageColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.mpx))
}

@Composable
private fun LeagueRow(label: String, count: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = stageAccent
    StageRow(
        onClick = onClick,
        height = 72.mpx,
        modifier = modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(accent.accent.copy(alpha = 0.12f), RoundedCornerShape(StageRadii.Row)) else Modifier),
    ) { focused ->
        val text = if (focused) accent.onAccent else StageColors.Text
        Text(label, style = stageText(21, 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(count.toString(), style = stageText(18, 700), color = if (focused) accent.onAccent else accent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TeamRow(team: SportsTeam, favorite: Boolean, onClick: () -> Unit) {
    val accent = stageAccent
    StageRow(onClick = onClick, height = 72.mpx, modifier = Modifier.fillMaxWidth()) { focused ->
        val text = if (focused) accent.onAccent else StageColors.Text
        if (team.logoUrl != null) {
            AsyncImage(model = team.logoUrl, contentDescription = null, modifier = Modifier.size(40.mpx))
        } else {
            Spacer(Modifier.size(40.mpx))
        }
        Text(team.name, style = stageText(21, 700), color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OwnTVIcon(
                OwnTVIcon.STAR,
                if (focused) accent.onAccent else if (favorite) accent.accent else StageColors.Dim,
                Modifier.size(28.mpx),
                filled = favorite,
            )
        }
    }
}
