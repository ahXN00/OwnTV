package tv.own.owntv.features.sport

/**
 * The leagues the Sports hub follows (Lume covers 150+; this is the curated TV set).
 * ESPN slugs: scoreboard lives at site.api.espn.com/apis/site/v2/sports/{sport}/{league}.
 * A league with no games that week simply renders an empty section — never an error.
 */
val SportsLeagues = listOf(
    SportLeague("soccer.ger.1", "soccer", "ger.1"),
    SportLeague("soccer.eng.1", "soccer", "eng.1"),
    SportLeague("soccer.esp.1", "soccer", "esp.1"),
    SportLeague("soccer.ita.1", "soccer", "ita.1"),
    SportLeague("soccer.fra.1", "soccer", "fra.1"),
    SportLeague("soccer.uefa.champions", "soccer", "uefa.champions"),
    SportLeague("soccer.uefa.europa", "soccer", "uefa.europa"),
    SportLeague("football.nfl", "football", "nfl"),
    SportLeague("basketball.nba", "basketball", "nba"),
    SportLeague("baseball.mlb", "baseball", "mlb"),
    SportLeague("hockey.nhl", "hockey", "nhl"),
    SportLeague("tennis.atp", "tennis", "atp"),
    SportLeague("tennis.wta", "tennis", "wta"),
    SportLeague("racing.f1", "racing", "f1"),
    SportLeague("mma.ufc", "mma", "ufc"),
    SportLeague("golf.pga", "golf", "pga"),
)

fun sportsLeagueById(id: String): SportLeague? = SportsLeagues.firstOrNull { it.id == id }
