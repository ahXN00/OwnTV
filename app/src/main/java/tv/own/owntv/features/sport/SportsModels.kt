package tv.own.owntv.features.sport

/**
 * Sports hub data (Lume-style): fixtures, live scores and standings from ESPN, matched against
 * the user's own live channels via EPG so one tap starts playback.
 *
 * ESPN's site API needs no key. All display text for leagues/teams comes from the API response
 * itself (proper nouns need no translation); every label around them is a string resource.
 */
data class SportLeague(
    /** Stable id, also the follow key (e.g. "soccer.ger.1"). */
    val id: String,
    /** ESPN scoreboard path segment, e.g. "soccer". */
    val espnSport: String,
    /** ESPN league slug, e.g. "ger.1". */
    val espnLeague: String,
)

data class SportTeam(
    val id: String,
    val name: String,
    val abbreviation: String,
    val logoUrl: String?,
    /** ESPN "color" hex without '#', may be blank. */
    val colorHex: String?,
    val score: String?,
    val winner: Boolean,
    val homeAway: String,
)

enum class FixtureState { LIVE, UPCOMING, FINISHED }

data class Fixture(
    val id: String,
    val leagueId: String,
    val dateMs: Long,
    val state: FixtureState,
    /** Short status line from ESPN ("Q3 04:12", "FT", "19:30"), never translated. */
    val statusLine: String,
    val home: SportTeam,
    val away: SportTeam,
    /** Broadcast names from ESPN ("Sky Sport Bundesliga 1"), matched against channel names. */
    val broadcasts: List<String>,
    val venue: String?,
)

data class StandingRow(
    val rank: String,
    val teamName: String,
    val teamAbbrev: String,
    val logoUrl: String?,
    /** The stat columns ESPN reports for this league (W/D/L, PTS, ...), in order. */
    val stats: List<String>,
)

data class LeagueTable(
    val leagueId: String,
    val groupName: String?,
    val columns: List<String>,
    val rows: List<StandingRow>,
)

/** A user channel that (probably) carries [fixture], highest score first. */
data class ChannelMatch(
    val channelId: Long,
    val channelName: String,
    val logoUrl: String?,
    /** EPG programme title that matched, when the match came from the guide. */
    val epgTitle: String?,
    val score: Int,
)
