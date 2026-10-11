package tv.own.owntv.tifo.sports

import androidx.annotation.StringRes
import tv.own.owntv.R

/** How a competition's events are shaped, which decides how a game is matched to a channel. */
enum class SportKind {
    /** Two sides: home and away. */
    TEAM,

    /** One event per weekend (a Grand Prix): matched on the event's name and the series. */
    RACE,

    /** A tournament running for days: matched on its name and the tour. */
    TOURNAMENT,
}

/**
 * The competitions the Sports tab follows. [espnPath] is the league's path on ESPN's public
 * scoreboard API. [keywords] are how a guide names the competition in a programme title.
 *
 * Which channel goes first: for the US leagues ([usesEspnBroadcasts]), the one ESPN lists for the
 * game. For the others ESPN's list is the US one (beIN Sports for Ligue 1), so [homeBroadcasters]
 * go first instead: who airs the competition in its own country (Ligue 1+ in France, Sky Sports and
 * TNT Sports in England), or in France where there is no single country. [broadcasters] are other
 * channels that usually carry it, ranked last.
 */
enum class Competition(
    @param:StringRes val labelRes: Int,
    val espnPath: String,
    val kind: SportKind,
    val keywords: List<String>,
    val homeBroadcasters: List<String>,
    val broadcasters: List<String>,
    val usesEspnBroadcasts: Boolean = false,
) {
    LIGUE_1(
        R.string.tifo_sports_ligue_1, "soccer/fra.1", SportKind.TEAM,
        listOf("Ligue 1"),
        // Every game, from 2026-27.
        homeBroadcasters = listOf("Ligue 1+"),
        broadcasters = emptyList(),
    ),
    PREMIER_LEAGUE(
        R.string.tifo_sports_premier_league, "soccer/eng.1", SportKind.TEAM,
        listOf("Premier League"),
        homeBroadcasters = listOf("Sky Sports", "TNT Sports"),
        broadcasters = listOf("Canal+ Foot", "Canal+ Sport"),
    ),
    LALIGA(
        R.string.tifo_sports_laliga, "soccer/esp.1", SportKind.TEAM,
        listOf("LaLiga", "La Liga", "Liga"),
        homeBroadcasters = listOf("M+ LaLiga", "Movistar", "DAZN LaLiga"),
        broadcasters = listOf("beIN Sports"),
    ),
    CHAMPIONS_LEAGUE(
        R.string.tifo_sports_champions_league, "soccer/uefa.champions", SportKind.TEAM,
        listOf("Champions League", "Ligue des champions"),
        homeBroadcasters = listOf("Canal+ Foot", "Canal+ Sport"),
        broadcasters = listOf("Canal+", "TNT Sports"),
    ),
    NBA(
        R.string.tifo_sports_nba, "basketball/nba", SportKind.TEAM,
        listOf("NBA"),
        homeBroadcasters = emptyList(),
        broadcasters = listOf("beIN Sports", "NBA TV", "ESPN"),
        usesEspnBroadcasts = true,
    ),
    NFL(
        R.string.tifo_sports_nfl, "football/nfl", SportKind.TEAM,
        listOf("NFL"),
        homeBroadcasters = emptyList(),
        broadcasters = listOf("beIN Sports", "NFL Network", "DAZN", "ESPN"),
        usesEspnBroadcasts = true,
    ),
    FORMULA_1(
        R.string.tifo_sports_formula_1, "racing/f1", SportKind.RACE,
        listOf("Formula 1", "Formule 1", "F1", "Grand Prix"),
        homeBroadcasters = emptyList(),
        broadcasters = listOf("Canal+ Sport", "Canal+ F1", "Sky Sports F1", "F1 TV"),
    ),
    ATP(
        R.string.tifo_sports_atp, "tennis/atp", SportKind.TOURNAMENT,
        listOf("ATP", "Tennis"),
        homeBroadcasters = emptyList(),
        broadcasters = listOf("Tennis Channel", "beIN Sports", "Eurosport"),
    ),
}

enum class EventState { UPCOMING, LIVE, FINISHED }

data class SportsTeam(
    val name: String,
    val shortName: String,
    /** ESPN's other spellings of the team (nickname, abbreviation), used only for matching. */
    val altNames: List<String>,
    val logoUrl: String?,
    val score: String?,
    /** ESPN's id for the team, the same in every game and in the league's team list. */
    val id: String = "",
)

data class SportsEvent(
    val id: String,
    val competition: Competition,
    /** "Lyon at Paris Saint-Germain", "Singapore Grand Prix", "Shanghai Masters". */
    val name: String,
    val startMs: Long,
    val state: EventState,
    /** ESPN's own short status: "45'", "Q3 5:12", "FT". Blank when it has none. */
    val statusDetail: String,
    val home: SportsTeam?,
    val away: SportsTeam?,
    /** Broadcaster names ESPN lists for the event (mostly US networks). */
    val broadcasts: List<String>,
    /** A tournament's last day; null for a single game or race. */
    val endMs: Long? = null,
)
