package tv.own.owntv.tifo.sports

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * A league's teams from ESPN's public API, for picking favourite teams. Like the scoreboard it is
 * unofficial, so the read is defensive. A league's list changes once a season: it is fetched once
 * per run of the app.
 */
class EspnTeams(private val http: OkHttpClient) {

    private val cache = ConcurrentHashMap<Competition, List<SportsTeam>>()

    suspend fun load(competition: Competition): List<SportsTeam> = withContext(Dispatchers.IO) {
        cache[competition]?.let { return@withContext it }
        val request = Request.Builder().url(urlFor(competition)).build()
        val teams = http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "ESPN ${competition.espnPath} teams answered ${response.code}" }
            parseTeams(response.body.string())
        }
        check(teams.isNotEmpty()) { "ESPN ${competition.espnPath}: no teams" }
        cache[competition] = teams
        teams
    }

    companion object {
        private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/"

        internal fun urlFor(competition: Competition): String = BASE + competition.espnPath + "/teams?limit=200"
    }
}

/** `sports[0].leagues[0].teams[].team`, sorted by name. */
internal fun parseTeams(json: String): List<SportsTeam> {
    val league = JSONObject(json).optJSONArray("sports")?.optJSONObject(0)
        ?.optJSONArray("leagues")?.optJSONObject(0) ?: return emptyList()
    val entries = league.optJSONArray("teams") ?: return emptyList()
    return (0 until entries.length()).mapNotNull { i ->
        val team = entries.optJSONObject(i)?.optJSONObject("team") ?: return@mapNotNull null
        val id = team.optString("id")
        val name = team.optString("displayName").ifBlank { team.optString("name") }
        if (id.isBlank() || name.isBlank()) return@mapNotNull null
        SportsTeam(
            name = name,
            shortName = team.optString("shortDisplayName").ifBlank { name },
            altNames = emptyList(),
            logoUrl = team.optJSONArray("logos").firstHref(),
            score = null,
            id = id,
        )
    }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
}

private fun JSONArray?.firstHref(): String? {
    if (this == null) return null
    return (0 until length()).firstNotNullOfOrNull { optJSONObject(it)?.optString("href")?.takeIf { h -> h.isNotBlank() } }
}
