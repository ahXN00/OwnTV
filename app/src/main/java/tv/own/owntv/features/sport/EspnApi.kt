package tv.own.owntv.features.sport

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.own.owntv.R

/**
 * ESPN scoreboard + standings. Keyless JSON, the same source Lume's Sports hub uses.
 * Every parse is defensive (`opt*` + skips): ESPN varies the shape per sport, and a league whose
 * shape changed must render empty — never crash the hub.
 */
class EspnApi(private val client: OkHttpClient, context: Context) {

    data class Scoreboard(val leagueName: String, val fixtures: List<Fixture>)

    private val appContext = context.applicationContext
    private val venueSeparator: String by lazy { appContext.getString(R.string.sport_venue_separator) }

    suspend fun scoreboard(league: SportLeague, date: LocalDate? = null): Scoreboard =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append(EspnBase)
                append(league.espnSport)
                append(ESP)
                append(league.espnLeague)
                append(ScoreboardPath)
                if (date != null) {
                    append(DatesQuery)
                    append(date.format(DateFormat))
                    append(LimitQuery)
                }
            }
            val root = get(url)
            val leagueName = root.optJSONArray(LeaguesKey)
                ?.optJSONObject(0)?.optString(NameKey).orEmpty()
                .ifBlank { league.id }
            val events = root.optJSONArray(EventsKey)
            val fixtures = ArrayList<Fixture>(events?.length() ?: 0)
            if (events != null) {
                for (i in 0 until events.length()) {
                    runCatching { parseEvent(league, events.getJSONObject(i)) }
                        .getOrNull()?.let(fixtures::add)
                }
            }
            Scoreboard(leagueName, fixtures)
        }

    suspend fun standings(league: SportLeague): List<LeagueTable> =
        withContext(Dispatchers.IO) {
            // Standings live on the web API host; the site host answers 200 with an empty body.
            val url = StandingsBase + league.espnSport + ESP + league.espnLeague + StandingsPath
            val root = runCatching { get(url) }.getOrNull() ?: return@withContext emptyList()
            val tables = ArrayList<LeagueTable>()
            val children = root.optJSONArray(ChildrenKey) ?: return@withContext emptyList()
            for (c in 0 until children.length()) {
                val group = children.optJSONObject(c) ?: continue
                val groupName = group.optString(NameKey).ifBlank { null }
                val table = group.optJSONObject(StandingsKey) ?: continue
                val entries = table.optJSONArray(EntriesKey) ?: continue
                var columns: List<String> = emptyList()
                val rows = ArrayList<StandingRow>()
                for (e in 0 until entries.length()) {
                    val entry = entries.optJSONObject(e) ?: continue
                    val team = entry.optJSONObject(TeamKey) ?: continue
                    val statCells = ArrayList<String>()
                    val headerCells = ArrayList<String>()
                    val stats = entry.optJSONArray(StatsKey)
                    if (stats != null) {
                        for (s in 0 until stats.length()) {
                            val stat = stats.optJSONObject(s) ?: continue
                            headerCells.add(
                                stat.optString(AbbrevKey)
                                    .ifBlank { stat.optString(ShortNameKey) }
                                    .ifBlank { stat.optString(NameKey) },
                            )
                            statCells.add(stat.optString(DisplayValueKey))
                        }
                    }
                    if (columns.isEmpty() && headerCells.isNotEmpty()) columns = headerCells
                    rows.add(
                        StandingRow(
                            rank = entry.optString(RankKey).ifBlank { (rows.size + 1).toString() },
                            teamName = team.optString(DisplayNameKey),
                            teamAbbrev = team.optString(AbbrevKey).ifBlank { team.optString(ShortNameKey) },
                            logoUrl = team.optJSONArray(LogosKey)?.optJSONObject(0)?.optString(HrefKey),
                            stats = statCells,
                        ),
                    )
                }
                if (rows.isNotEmpty()) tables.add(LeagueTable(league.id, groupName, columns, rows))
            }
            tables
        }

    private fun get(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header(UserAgentHeader, UserAgentValue)
            .header(AcceptHeader, JsonMime)
            .header(RefererHeader, EspnReferer)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("ESPN HTTP " + response.code)
            return JSONObject(response.body.string())
        }
    }

    private fun parseEvent(league: SportLeague, event: JSONObject): Fixture? {
        val competition = event.optJSONArray(CompetitionsKey)?.optJSONObject(0) ?: return null
        val dateMs = runCatching { Instant.parse(normalizeEspnDate(event.optString(DateKey))).toEpochMilli() }.getOrNull()
        if (dateMs == null) {
            android.util.Log.w(LogTag, "nodate " + league.id + " raw=" + event.optString(DateKey))
            return null
        }
        var home: SportTeam? = null
        var away: SportTeam? = null
        val competitors = competition.optJSONArray(CompetitorsKey)
        if (competitors != null) {
            for (i in 0 until competitors.length()) {
                val parsed = parseTeam(competitors.optJSONObject(i) ?: continue) ?: continue
                if (parsed.homeAway == HomeValue) home = parsed else away = parsed
            }
        }
        if (home == null || away == null) {
            android.util.Log.w(
                LogTag,
                "noteams " + league.id + " game=" + event.optString(IdKey) +
                    " comps=" + (competitors?.length() ?: -1),
            )
            return null
        }
        val homeTeam = home
        val awayTeam = away
        val status = competition.optJSONObject(StatusKey)
        val state = status?.optJSONObject(TypeKey)?.optString(StateKey)
        val fixtureState = when (state) {
            InState -> FixtureState.LIVE
            PostState -> FixtureState.FINISHED
            else -> FixtureState.UPCOMING
        }
        val shortDetail = status?.optString(ShortDetailKey).orEmpty()
        val statusLine = if (fixtureState == FixtureState.UPCOMING || shortDetail.isBlank()) {
            DateTimeFormatter.ofPattern(TimePattern)
                .withZone(java.time.ZoneId.systemDefault()).format(Instant.ofEpochMilli(dateMs))
        } else {
            shortDetail
        }
        val broadcasts = ArrayList<String>()
        competition.optJSONArray(BroadcastsKey)?.let { list ->
            for (i in 0 until list.length()) {
                list.optJSONObject(i)?.optJSONArray(NamesKey)?.let { names ->
                    for (n in 0 until names.length()) {
                        names.optString(n).takeIf { it.isNotBlank() }?.let(broadcasts::add)
                    }
                }
            }
        }
        val venue = competition.optJSONObject(VenueKey)?.let { venueObj ->
            val name = venueObj.optString(FullNameKey)
            val city = venueObj.optJSONObject(AddressKey)?.optString(CityKey)
            when {
                name.isNotBlank() && city?.isNotBlank() == true -> name + venueSeparator + city
                name.isNotBlank() -> name
                else -> null
            }
        }
        return Fixture(
            id = league.id + ESP + event.optString(IdKey),
            leagueId = league.id,
            dateMs = dateMs,
            state = fixtureState,
            statusLine = statusLine,
            home = homeTeam,
            away = awayTeam,
            broadcasts = broadcasts.distinct(),
            venue = venue,
        )
    }

    private fun parseTeam(competitor: JSONObject): SportTeam? {
        val team = competitor.optJSONObject(TeamKey) ?: return null
        val name = team.optString(DisplayNameKey).ifBlank { team.optString(ShortNameKey) }
        if (name.isBlank()) return null
        return SportTeam(
            id = team.optString(IdKey),
            name = name,
            abbreviation = team.optString(AbbrevKey).ifBlank { team.optString(ShortNameKey) },
            logoUrl = team.optJSONArray(LogosKey)?.optJSONObject(0)?.optString(HrefKey)?.takeIf { it.isNotBlank() },
            colorHex = team.optString(ColorKey).ifBlank { null },
            score = competitor.optString(ScoreKey).ifBlank { null },
            winner = competitor.optBoolean(WinnerKey),
            homeAway = competitor.optString(HomeAwayKey),
        )
    }

    companion object {
        private const val LogTag = "SportsHub"

        /**
         * ESPN omits the seconds ("2026-10-11T13:30Z") but [Instant.parse] requires them, so a
         * missing ":ss" is inserted before the zone instead of failing the whole fixture.
         */
        private val ShortTimePattern = Regex("(T\\d{2}:\\d{2})(Z|[+-]\\d{2}:?\\d{2})$")

        private fun normalizeEspnDate(raw: String): String =
            ShortTimePattern.replace(raw.trim()) { match ->
                match.groupValues[1] + SecondsInsert + match.groupValues[2]
            }

        private const val SecondsInsert = ":00"
        private const val EspnBase = "https://site.api.espn.com/apis/site/v2/sports/"
        private const val StandingsBase = "https://site.web.api.espn.com/apis/v2/sports/"
        private const val ESP = "/"
        private const val ScoreboardPath = "/scoreboard"
        private const val StandingsPath = "/standings"
        private const val DatesQuery = "?dates="
        private const val LimitQuery = "&limit=100"
        private const val UserAgentHeader = "User-Agent"
        private const val UserAgentValue = "Mozilla/5.0 (Linux; Android 11; AFTTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        private const val AcceptHeader = "Accept"
        private const val JsonMime = "application/json"
        private const val RefererHeader = "Referer"
        private const val EspnReferer = "https://www.espn.com/"
        private const val TimePattern = "HH:mm"
        private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

        private const val EventsKey = "events"
        private const val CompetitionsKey = "competitions"
        private const val CompetitorsKey = "competitors"
        private const val TeamKey = "team"
        private const val StatusKey = "status"
        private const val TypeKey = "type"
        private const val StateKey = "state"
        private const val ShortDetailKey = "shortDetail"
        private const val BroadcastsKey = "broadcasts"
        private const val NamesKey = "names"
        private const val VenueKey = "venue"
        private const val FullNameKey = "fullName"
        private const val AddressKey = "address"
        private const val CityKey = "city"
        private const val LeaguesKey = "leagues"
        private const val NameKey = "name"
        private const val DateKey = "date"
        private const val IdKey = "id"
        private const val DisplayNameKey = "displayName"
        private const val ShortNameKey = "shortDisplayName"
        private const val AbbrevKey = "abbreviation"
        private const val LogosKey = "logos"
        private const val HrefKey = "href"
        private const val ColorKey = "color"
        private const val ScoreKey = "score"
        private const val WinnerKey = "winner"
        private const val HomeAwayKey = "homeAway"
        private const val HomeValue = "home"
        private const val InState = "in"
        private const val PostState = "post"
        private const val ChildrenKey = "children"
        private const val StandingsKey = "standings"
        private const val EntriesKey = "entries"
        private const val StatsKey = "stats"
        private const val DisplayValueKey = "displayValue"
        private const val RankKey = "rank"
    }
}
