package tv.own.owntv.tifo.sports

import java.text.Normalizer
import java.util.Locale

/**
 * Pure scoring of "does this guide programme show this event", kept apart from the database so it
 * can be unit tested. Guides name games in every way there is ("PSG / Lyon", "Football : Ligue 1 -
 * Paris SG - Olympique Lyonnais", "Lakers @ Celtics"), so names are compared accent- and
 * case-blind, and short names only as whole words.
 */
object SportsMatching {

    /** Both teams in the title. */
    const val BOTH_TEAMS = 100


    /** From this score a match is shown as the best one. */
    const val BEST_MATCH = 70

    /** A race or tournament by its own name ("Singapore Grand Prix" for the Singapore GP). */
    const val EVENT_NAME = 70

    /** Only the competition ("Multiplex Ligue 1", "NFL Sunday"), airing at the event's time. */
    const val COMPETITION_ONLY = 30

    /** A channel whose name is one of the competition's usual broadcasters. */
    const val BROADCASTER = 20

    /**
     * A playlist channel named after the game itself ("NFL 03: Bears vs Packers", "LIGUE 1 | PSG -
     * Lyon"). Providers make these per game, often without a guide, so they rank first.
     */
    const val EVENT_CHANNEL = 110

    /**
     * The game's own broadcaster: the channel ESPN lists for an NFL or NBA game ("ESPN2", "CBS"),
     * or the home country's for the others ("Ligue 1+"). The most direct answer, so it goes first.
     */
    const val LISTED_BROADCASTER = 120

    /** One of the game's own broadcaster's channels whose guide also shows the game. */
    const val LISTED_AND_SHOWING = 130

    /** Spellings guides use that ESPN doesn't, keyed by ESPN's normalised display name. */
    private val aliases: Map<String, List<String>> = mapOf(
        "paris saint-germain" to listOf("PSG", "Paris SG", "Paris-SG"),
        "marseille" to listOf("Olympique de Marseille"),
        "lyon" to listOf("Olympique Lyonnais"),
        "ac milan" to listOf("Milan AC", "AC Milan"),
        "internazionale" to listOf("Inter Milan", "Inter"),
        "bayern munich" to listOf("Bayern", "Bayern Munchen"),
        "atletico madrid" to listOf("Atletico", "Atletico de Madrid"),
        "manchester united" to listOf("Man Utd", "Man United"),
        "manchester city" to listOf("Man City"),
        "tottenham hotspur" to listOf("Tottenham"),
        "borussia dortmund" to listOf("Dortmund"),
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)

    /** Every spelling worth looking for: ESPN's names plus [aliases], at least 3 characters long. */
    fun teamTerms(team: SportsTeam): List<String> =
        (listOf(team.name, team.shortName) + team.altNames + aliases[normalize(team.name)].orEmpty())
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinctBy { normalize(it) }

    /**
     * The city part of a US-style team name ("Green Bay" for "Green Bay Packers"), which guides and
     * event channels use on their own ("Chicago at Green Bay"). Null when the name has none.
     */
    fun city(team: SportsTeam): String? {
        val name = team.name.trim()
        val short = team.shortName.trim()
        if (short.isEmpty() || short.length >= name.length || !name.endsWith(short, ignoreCase = true)) return null
        return name.dropLast(short.length).trim().takeIf { it.length >= 3 }
    }

    /** Words that identify a race or tournament: its name without the generic words around it. */
    fun eventTerms(event: SportsEvent): List<String> =
        event.name.split(' ', '-', '\'')
            .map { it.trim() }
            .filter { it.length >= 4 && normalize(it) !in GENERIC_WORDS }

    /** The terms a database pre-filter should look for; [score] decides what actually matches. */
    fun searchTerms(event: SportsEvent): List<String> = buildList {
        event.home?.let { addAll(teamTerms(it)); city(it)?.let(::add) }
        event.away?.let { addAll(teamTerms(it)); city(it)?.let(::add) }
        if (event.competition.kind != SportKind.TEAM) {
            addAll(eventTerms(event))
            addAll(event.competition.keywords)
        } else if (event.home == null || event.away == null) {
            addAll(event.competition.keywords)
        }
    }.distinctBy { normalize(it) }

    /**
     * How sure we are that a programme titled [title] (with [extra]: its categories) shows [event].
     * 0 means not at all.
     */
    fun score(event: SportsEvent, title: String, extra: String? = null): Int {
        val text = normalize(title)
        val all = if (extra.isNullOrBlank()) text else text + " " + normalize(extra)
        if (mentionsOtherSport(event, all)) return 0
        val competition = event.competition.keywords.any { containsTerm(all, it) }
        return when (event.competition.kind) {
            SportKind.TEAM -> {
                when {
                    namesBothTeams(event, text, competition) -> BOTH_TEAMS
                    competition && containsTerm(text, event.competition.keywords.first()) -> COMPETITION_ONLY
                    else -> 0
                }
            }
            SportKind.RACE, SportKind.TOURNAMENT -> {
                val named = eventTerms(event).any { containsTerm(text, it) }
                when {
                    named && competition -> EVENT_NAME + 10
                    named -> EVENT_NAME
                    competition && event.competition.keywords.any { containsTerm(text, it) } -> COMPETITION_ONLY + 10
                    else -> 0
                }
            }
        }
    }

    /** The terms to look for in channel names: no abbreviations, which hide in too many words. */
    fun channelSearchTerms(event: SportsEvent): List<String> =
        searchTerms(event).filter { it.length >= 4 && normalize(it) !in GENERIC_WORDS }

    /**
     * How sure we are that a channel named [channelName] shows [event] whatever its guide says.
     * Races and tournaments need their name and the competition ("F1 | Singapore GP").
     */
    fun channelScore(event: SportsEvent, channelName: String): Int {
        val text = normalize(channelName)
        if (mentionsOtherSport(event, text)) return 0
        val competition = event.competition.keywords.any { containsTerm(text, it) }
        return when (event.competition.kind) {
            SportKind.TEAM -> if (namesBothTeams(event, text, competition)) EVENT_CHANNEL else 0
            SportKind.RACE, SportKind.TOURNAMENT -> {
                val named = eventTerms(event).any { containsTerm(text, it) }
                if (named && competition) EVENT_NAME else 0
            }
        }
    }

    /**
     * Whether [text] names both teams. One team is never enough: "Spurs" alone is also Tottenham,
     * and a club's own channel doesn't show every game. A city ("Chicago at Green Bay") stands for
     * its team, but two cities alone also name other sports' games, so then the competition has to
     * be named too; and a city both teams share (Lakers v Clippers) names neither.
     */
    private fun namesBothTeams(event: SportsEvent, text: String, competitionNamed: Boolean): Boolean {
        val home = event.home ?: return false
        val away = event.away ?: return false
        val teams = listOf(home, away)
        val named = teams.map { team -> teamTerms(team).any { containsTerm(text, it) } }
        val cities = teams.map { city(it) }
        val sameCity = cities[0] != null && cities[1] != null && normalize(cities[0]!!) == normalize(cities[1]!!)
        val byCity = cities.map { c -> c != null && !sameCity && containsTerm(text, c) }
        val found = teams.indices.all { named[it] || byCity[it] }
        return found && (named.any { it } || competitionNamed)
    }

    /**
     * Whether [normalizedText] names a sport other than the event's ("NHL", "Premier League" for an
     * NBA game): team names repeat across sports (Spurs, Kings, Rangers, Panthers…).
     */
    fun mentionsOtherSport(event: SportsEvent, normalizedText: String): Boolean {
        val own = sportOf(event.competition)
        return SPORT_WORDS.any { (sport, words) -> sport != own && words.any { containsTerm(normalizedText, it) } }
    }

    private fun sportOf(competition: Competition): String = when (competition) {
        Competition.LIGUE_1, Competition.PREMIER_LEAGUE, Competition.LALIGA, Competition.CHAMPIONS_LEAGUE -> SOCCER
        Competition.NBA -> BASKETBALL
        Competition.NFL -> AMERICAN_FOOTBALL
        Competition.FORMULA_1 -> RACING
        Competition.ATP -> TENNIS
    }

    /**
     * Whether a channel named [channelName] is the broadcaster ESPN calls [broadcaster]: "ESPN2" is
     * "US | ESPN 2 HD", "beIN Sports" is "beIN Sports 1", but "ESPN" is neither "ESPN2" nor "ESPN+".
     */
    fun broadcasterMatches(channelName: String, broadcaster: String): Boolean {
        val text = normalize(channelName)
        return broadcasterSearchTerms(broadcaster).any { variant ->
            val needle = normalize(variant)
            var from = 0
            while (true) {
                val at = text.indexOf(needle, from)
                if (at < 0) break
                val end = at + needle.length
                val before = at == 0 || !text[at - 1].isLetterOrDigit()
                val after = end >= text.length || (!text[end].isLetterOrDigit() && text[end] != '+')
                if (before && after) return true
                from = at + 1
            }
            false
        }
    }

    /**
     * What to look for in the database for [broadcaster]: "ESPN2" also as "ESPN 2", and ESPN's
     * short forms by the names playlists use ("NFL Net" is "NFL Network").
     */
    fun broadcasterSearchTerms(broadcaster: String): List<String> {
        val name = broadcaster.trim()
        return (listOf(name) + BROADCASTER_ALIASES[normalize(name)].orEmpty())
            .flatMap { listOf(it, it.replace(LETTER_DIGIT, " "), it.replace(" ", "")) }
            .filter { it.length >= 2 }
            .distinct()
    }

    /** ESPN's short broadcaster names, keyed normalised, and what channels are called instead. */
    private val BROADCASTER_ALIASES: Map<String, List<String>> = mapOf(
        "nfl net" to listOf("NFL Network"),
        "cbssn" to listOf("CBS Sports Network", "CBS Sports"),
        "fs1" to listOf("Fox Sports 1"),
        "fs2" to listOf("Fox Sports 2"),
        "espnu" to listOf("ESPN U"),
        "prime video" to listOf("Amazon Prime", "Prime Video"),
        "nbatv" to listOf("NBA TV"),
        "usa net" to listOf("USA Network"),
    )

    private val LETTER_DIGIT = Regex("(?<=\\p{L})(?=\\d)|(?<=\\d)(?=\\p{L})")

    private const val SOCCER = "soccer"
    private const val BASKETBALL = "basketball"
    private const val AMERICAN_FOOTBALL = "american football"
    private const val RACING = "racing"
    private const val TENNIS = "tennis"

    /** Words that name a sport or one of its leagues, whole-word and normalised. */
    private val SPORT_WORDS: Map<String, List<String>> = mapOf(
        SOCCER to listOf(
            "soccer", "premier league", "ligue 1", "ligue 2", "laliga", "la liga", "champions league",
            "ligue des champions", "europa league", "serie a", "bundesliga", "eredivisie", "mls", "uefa", "fifa",
            "epl", "efl", "fa cup", "coupe de france",
        ),
        BASKETBALL to listOf("nba", "wnba", "basket", "basketball", "euroleague", "ncaab"),
        AMERICAN_FOOTBALL to listOf("nfl", "ncaaf", "redzone", "super bowl"),
        "hockey" to listOf("nhl", "hockey", "khl"),
        "baseball" to listOf("mlb", "baseball"),
        RACING to listOf("f1", "formula 1", "formule 1", "nascar", "motogp", "indycar"),
        TENNIS to listOf("atp", "wta", "tennis"),
        "rugby" to listOf("rugby", "top 14"),
        "fighting" to listOf("ufc", "mma", "boxing", "boxe"),
        "golf" to listOf("golf", "pga"),
    )

    /**
     * Playlists use fake channels as section headers ("# # # NFL # # #", "##### USA #####"). They
     * play nothing, so they are never offered.
     */
    fun isSeparator(channelName: String): Boolean = channelName.trimStart().startsWith("#")

    /** [term] in [normalizedText] as a whole word (or words), accent- and case-blind. */
    fun containsTerm(normalizedText: String, term: String): Boolean {
        val needle = normalize(term)
        if (needle.isBlank()) return false
        var from = 0
        while (true) {
            val at = normalizedText.indexOf(needle, from)
            if (at < 0) return false
            val before = at == 0 || !normalizedText[at - 1].isLetterOrDigit()
            val end = at + needle.length
            val after = end >= normalizedText.length || !normalizedText[end].isLetterOrDigit()
            if (before && after) return true
            from = at + 1
        }
    }

    private val DIACRITICS = Regex("\\p{Mn}+")

    private val GENERIC_WORDS = setOf(
        "grand", "prix", "formula", "open", "masters", "championships", "championship", "tournament",
        "presented", "with", "cup", "rolex", "1000",
    )
}
