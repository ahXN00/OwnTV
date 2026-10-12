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
     * A channel of the game's own broadcaster (the one ESPN lists for an NFL or NBA game, the home
     * country's for the others) whose guide or name also shows the game. The most direct answer,
     * so it goes first.
     */
    const val LISTED_AND_SHOWING = 130

    /**
     * One of the user's favourite channels from the game's own broadcaster, which the guide may
     * not confirm: the channel they already watch it on.
     */
    const val FAVORITE_BROADCASTER = 150

    /**
     * The exact channel FotMob's TV guide lists for the game in the channel's own country ("Canal+
     * Live 3" in France, "Sky Sports Main Event" in the UK): named for this very game, so above a
     * broadcaster that merely also shows it.
     */
    const val LISTED_CHANNEL = 140

    /**
     * A feed of the streaming service FotMob lists without naming a channel ("Ligue 1+" →
     * "FR| LIGUE 1+ 3", "FR| LIGUE1+ (Samedi Match 1)"): the game is on one of them.
     */
    const val LISTED_SERVICE = 60

    /**
     * The countries whose channels can be guessed from a broadcaster's name alone (the
     * competition's usual [Competition.broadcasters]); elsewhere "Paramount" or "Canal" name
     * unrelated channels (ARG| PARAMOUNT, ARG| CANAL 13).
     */
    val GUESS_COUNTRIES = setOf("US", "CA", "FR")

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
        // Premier League
        "afc bournemouth" to listOf("Bournemouth"),
        "brighton & hove albion" to listOf("Brighton"),
        "nottingham forest" to listOf("Nottm Forest", "Forest"),
        "west ham united" to listOf("West Ham"),
        "wolverhampton wanderers" to listOf("Wolves"),
        "newcastle united" to listOf("Newcastle"),
        "leeds united" to listOf("Leeds"),
        "crystal palace" to listOf("Palace"),
        // Ligue 1
        "lille" to listOf("LOSC"),
        "stade rennais" to listOf("Rennes"),
        "aj auxerre" to listOf("Auxerre"),
        "le havre ac" to listOf("Le Havre"),
        "troyes" to listOf("ESTAC"),
        "as monaco" to listOf("Monaco"),
        "brest" to listOf("Stade Brestois"),
        // LaLiga
        "real betis" to listOf("Betis"),
        "celta vigo" to listOf("Celta"),
        "deportivo alaves" to listOf("Alaves"),
        "athletic club" to listOf("Athletic Bilbao", "Athletic"),
        "rayo vallecano" to listOf("Rayo"),
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
        if (mentionsOtherSport(event, normalize(withoutCategoryLabels(channelName)))) return 0
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
     * [channelName] without the provider's category labels after its first part: in "Live | Broncos
     * vs. Chargers | all | 8K EXCLUSIVE | US: SOCCER" the "US: SOCCER" is the folder the provider
     * filed the channel in, not the sport it shows. The first part stays, whatever it looks like:
     * "US: NBA 03 Lakers vs Celtics" names its own sport.
     */
    fun withoutCategoryLabels(channelName: String): String {
        val parts = channelName.split('|')
        return (parts.take(1) + parts.drop(1).filterNot { CATEGORY_LABEL.containsMatchIn(it) }).joinToString("|")
    }

    private val CATEGORY_LABEL = Regex("""^\s*[A-Z]{2,3}\s*:\s""")

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

    /**
     * A channel name reduced to what identifies the channel, so a TV guide's spelling and the
     * playlist's meet: no country prefix ("FR| ", "UK-NOWTV| "), no quality or source marks (HD, ᴿᴬᵂ,
     * HEVC), "+" kept as a word, Sports/Events and La Liga/Ligue1 spelled one way.
     * "UK-NOWTV| SKY SPORTS MAIN EVENTS HD(BK)" and "Sky Sports Main Event" → "sky sport main event";
     * "FR| CANAL+LIVE 3 FHD" and "Canal+ Live 3" → "canal plus live 3".
     */
    fun tvKey(name: String): String {
        val folded = Normalizer.normalize(name, Normalizer.Form.NFKC)
            .replace(COUNTRY_PREFIX, "")
            .replace(COUNTRY_IN_BRACKETS, " ")
            .replace("+", " plus ")
        return normalize(folded)
            .replace(NON_ALNUM, " ")
            .replace(LA_LIGA, "laliga")
            .replace(LIGUE1, "ligue 1")
            .split(' ')
            .filter { it.isNotEmpty() && it !in QUALITY_WORDS }
            .joinToString(" ") { if (it == "sports") "sport" else if (it == "events") "event" else it }
    }

    /**
     * The country a playlist channel's name starts with ("FR| …", "UK-NOWTV| …", "[ES] …"), upper
     * case, with GB written UK as playlists do; null when it has none.
     */
    fun countryOf(channelName: String): String? =
        CHANNEL_COUNTRY.find(channelName)?.groupValues?.get(1)?.uppercase(Locale.ROOT)?.let { if (it == "GB") "UK" else it }

    /** FotMob's country code as playlists write it: "GB" is "UK". */
    fun playlistCountry(fotMobCountry: String): String =
        if (fotMobCountry.equals("GB", ignoreCase = true)) "UK" else fotMobCountry.uppercase(Locale.ROOT)

    /**
     * Whether a playlist channel is the station a TV guide lists: the same [tvKey], or the
     * playlist's "<name> TV" ("M+ LALIGA" is "ES| M+ LALIGA TV FHD"), and in Spain its main feed
     * "<name> 1" ("DAZN LaLiga" is "ES| DAZN LA LIGA 1 HD"). Not "<name> 1" elsewhere: France's
     * "Canal+ Sport" is not "FR| CANAL+ SPORT 1".
     */
    fun isListedChannel(channelName: String, station: String, country: String): Boolean {
        val key = tvKey(station)
        if (key.isEmpty()) return false
        val channel = tvKey(channelName)
        return channel == key || channel == "$key tv" || (country == "ES" && channel == "$key 1")
    }

    /**
     * Whether a playlist channel is one of the feeds of [service] ("Ligue 1+"): the service itself,
     * a numbered feed ("FR| LIGUE 1+ 3 HD", "FR| LIGUE1 + 7") or a match-day one ("FR| LIGUE1+ FHD
     * ( Samedi Match 1)") — never the service's other channels ("Disney+ Films 1"). Generic apps
     * (DAZN, myCANAL) name no feed at all.
     */
    fun isServiceFeed(channelName: String, service: String): Boolean {
        val key = tvKey(service)
        if (key.length < 4 || key in GENERIC_SERVICES) return false
        val channel = tvKey(channelName)
        return channel == key || (channel.startsWith("$key ") && SERVICE_FEED.containsMatchIn(channel.substring(key.length + 1)))
    }

    /**
     * A channel that carries one event at a time — a PPV or event slot ("US| ESPN+ PPV 23", "US
     * (ESPN+ 002) | Football: …") or one titled with a game ("Ravens vs. Falcons"): its name says
     * nothing about which game it shows unless the game itself is in it, so it is never offered as
     * one of a broadcaster's channels.
     */
    fun isEventSlot(channelName: String): Boolean =
        SLOT_WORDS.containsMatchIn(normalize(channelName)) ||
            GAME_TITLE.containsMatchIn(channelName) ||
            NUMBERED_SLOT.containsMatchIn(channelName)

    private val COUNTRY_PREFIX = Regex("""^\s*(\[[^\]]*]|[A-Za-z0-9]{2,6}(-[A-Za-z0-9]+)?\s*[|:])\s*""")
    private val COUNTRY_IN_BRACKETS = Regex("""\((FR|US|UK|GB|ES|BK)\)""", RegexOption.IGNORE_CASE)
    private val CHANNEL_COUNTRY = Regex("""^\s*\[?([A-Za-z]{2,4})(-[A-Za-z0-9]+)?\s*[|:\]]""")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val LA_LIGA = Regex("""\bla liga\b""")
    private val LIGUE1 = Regex("""\bligue1\b""")
    private val QUALITY_WORDS = setOf(
        "8k", "4k", "uhd", "fhd", "hd", "hdr", "sd", "lq", "1080p", "720p", "3840p", "2160p", "hevc", "h265",
        "50fps", "60fps", "raw", "backup", "bk", "vip",
    )
    private val GENERIC_SERVICES = setOf("dazn", "dazn com", "mycanal", "youtube", "now", "apple tv", "apple tv plus")
    private val SERVICE_FEED = Regex("""^(\d+|(vendredi|samedi|dimanche|lundi|mardi|mercredi|jeudi|match|multiplex|canal multiplex)\b)""")
    private val SLOT_WORDS = Regex("""\b(ppv|event|events|evento|eventos)\b""")
    private val GAME_TITLE = Regex("""\s(vs?\.?|x|@)\s""", RegexOption.IGNORE_CASE)
    private val NUMBERED_SLOT = Regex("""\(\s*[^)]*\d{2,3}\s*\)\s*\|""")

    private val DIACRITICS = Regex("\\p{Mn}+")

    private val GENERIC_WORDS = setOf(
        "grand", "prix", "formula", "open", "masters", "championships", "championship", "tournament",
        "presented", "with", "cup", "rolex", "1000",
    )
}
