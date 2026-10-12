package tv.own.owntv.tifo.sports

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** One station FotMob lists for a game: a TV channel ("Canal+ Live 3") or a service ("Ligue 1+"). */
data class FotMobStation(val name: String, val isTv: Boolean)

/** A game in FotMob's TV guide for one country: its kick-off, its two teams, who shows it. */
data class FotMobListing(val startMs: Long, val teams: List<String>, val stations: List<FotMobStation>)

/**
 * FotMob's TV guide (what fotmob.com/tv-guide shows): for each football game of the coming week,
 * the channels that show it in one country — "Canal+ Live 3" in France, "Sky Sports Main Event"
 * in the UK, "M+ LaLiga" in Spain. ESPN only knows the US networks, so this is the only source
 * that names the exact channel elsewhere. Free, no key, but unofficial: every read is defensive
 * and a country that fails to load simply adds nothing.
 */
class FotMobTv(private val http: OkHttpClient) {

    private class Cached(val fetchedAtMs: Long, val listings: List<FotMobListing>)

    private val cache = ConcurrentHashMap<String, Cached>()

    /** [country]'s listings (FotMob's code: "FR", "GB", "ES"), fetched at most once an hour. */
    suspend fun listings(country: String, nowMs: Long): List<FotMobListing> = withContext(Dispatchers.IO) {
        val cached = cache[country]
        if (cached != null && nowMs - cached.fetchedAtMs < TTL_MS) return@withContext cached.listings
        runCatching {
            val request = Request.Builder().url(URL + country).header("User-Agent", USER_AGENT).build()
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "FotMob $country answered ${response.code}" }
                parseFotMobListings(response.body.string())
            }
        }.onSuccess { cache[country] = Cached(nowMs, it) }
            .getOrElse { cached?.listings.orEmpty() }
    }

    /**
     * Who shows [event] in each of [countries], by FotMob's country code. Empty for anything but
     * football, and for a country whose guide doesn't list the game.
     */
    suspend fun stationsFor(event: SportsEvent, countries: List<String>, nowMs: Long): Map<String, List<FotMobStation>> {
        if (!event.competition.isFootball || event.home == null || event.away == null) return emptyMap()
        return countries.distinct().mapNotNull { country ->
            listingFor(event, listings(country, nowMs))?.let { country to it.stations }
        }.toMap()
    }

    companion object {
        private const val URL = "https://www.fotmob.com/api/data/tvlistings?countryCode="
        private const val TTL_MS = 60 * 60_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
        private const val HOUR = 60 * 60_000L
        private const val MINUTE = 60_000L

        /**
         * The listing of [event] among [listings]: kick-off within 3 hours and both teams named,
         * or within 20 minutes and one of them (FotMob and ESPN spell clubs differently:
         * "Bodø/Glimt", "Brighton & Hove Albion"). The closest kick-off wins.
         */
        fun listingFor(event: SportsEvent, listings: List<FotMobListing>): FotMobListing? {
            val home = event.home ?: return null
            val away = event.away ?: return null
            fun names(team: SportsTeam, name: String): Boolean {
                val text = SportsMatching.normalize(name)
                return SportsMatching.teamTerms(team).any { SportsMatching.containsTerm(text, it) } ||
                    SportsMatching.containsTerm(SportsMatching.normalize(team.name), name)
            }
            return listings
                .filter { it.teams.size >= 2 }
                .mapNotNull { listing ->
                    val gap = kotlin.math.abs(listing.startMs - event.startMs)
                    if (gap > 3 * HOUR) return@mapNotNull null
                    val h = listing.teams.any { names(home, it) }
                    val a = listing.teams.any { names(away, it) }
                    if ((h && a) || ((h || a) && gap <= 20 * MINUTE)) listing to gap else null
                }
                .minByOrNull { it.second }
                ?.first
        }
    }
}

/**
 * FotMob's answer: an object keyed by match id, each a list of rows, one per station:
 * `{"5071411":[{"startTime":"/Date(1792020600000)/","station":{"name":"Apple TV (FR)","type":"Stream"},
 * "program":{"teams":[{"name":"FC Cincinnati"},{"name":"New England Revolution"}]}}]}`.
 * A station's type is "TV", "Stream" or sometimes empty for a TV channel; apps ("Sky Sports+ app",
 * "NOW", "DAZN") are never channels.
 */
internal fun parseFotMobListings(json: String): List<FotMobListing> {
    val root = JSONObject(json)
    return root.keys().asSequence().mapNotNull { key ->
        runCatching {
            val rows = root.getJSONArray(key)
            val first = rows.getJSONObject(0)
            val start = Regex("-?\\d+").find(first.optString("startTime"))?.value?.toLong() ?: return@runCatching null
            val teamsJson = first.optJSONObject("program")?.optJSONArray("teams")
            val teams = (0 until (teamsJson?.length() ?: 0)).mapNotNull { i ->
                teamsJson?.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
            }
            val stations = LinkedHashMap<String, FotMobStation>()
            for (i in 0 until rows.length()) {
                val station = rows.optJSONObject(i)?.optJSONObject("station") ?: continue
                val raw = station.optString("name").trim()
                if (raw.isEmpty()) continue
                val name = raw.replace(COUNTRY_SUFFIX, "").trim()
                val type = if (station.isNull("type")) "" else station.optString("type")
                val tv = type == "TV" || (type != "Stream" && !APP.matches(name))
                // A station listed twice keeps "TV" if either row says so.
                val old = stations[name]
                stations[name] = FotMobStation(name, tv || old?.isTv == true)
            }
            // TV channels before streaming services.
            FotMobListing(start, teams, stations.values.sortedByDescending { it.isTv })
        }.getOrNull()
    }.toList()
}

/** "DAZN 1 (FR)" → "DAZN 1". */
private val COUNTRY_SUFFIX = Regex("""\s*\((FR|US|UK|GB|ES)\)\s*$""", RegexOption.IGNORE_CASE)

private val APP = Regex("""(?i).*\bapp\b.*|now|youtube|dazn""")
