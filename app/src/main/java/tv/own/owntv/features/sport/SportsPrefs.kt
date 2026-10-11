package tv.own.owntv.features.sport

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sportsDataStore by preferencesDataStore(name = SportsFileName)

private const val SportsFileName = "sports"

/**
 * Followed leagues/teams for the Sports hub, in the app's own DataStore file (core settings stay
 * untouched, so upstream merges never conflict here).
 */
class SportsPrefs(private val context: Context) {

    val followedLeagues: Flow<Set<String>> =
        context.sportsDataStore.data.map { it[LeaguesKey] ?: SportsLeagues.map { league -> league.id }.toSet() }

    val followedTeams: Flow<Set<String>> =
        context.sportsDataStore.data.map { it[TeamsKey].orEmpty() }

    val hubVisible: Flow<Boolean> =
        context.sportsDataStore.data.map { it[VisibleKey] ?: true }

    suspend fun setLeagueFollowed(leagueId: String, followed: Boolean) {
        context.sportsDataStore.edit { prefs ->
            val current = prefs[LeaguesKey] ?: SportsLeagues.map { league -> league.id }.toSet()
            prefs[LeaguesKey] = if (followed) current + leagueId else current - leagueId
        }
    }

    suspend fun setTeamFollowed(teamKey: String, followed: Boolean) {
        context.sportsDataStore.edit { prefs ->
            val current = prefs[TeamsKey].orEmpty()
            prefs[TeamsKey] = if (followed) current + teamKey else current - teamKey
        }
    }

    suspend fun setHubVisible(visible: Boolean) {
        context.sportsDataStore.edit { prefs -> prefs[VisibleKey] = visible }
    }

    companion object {
        fun teamKey(leagueId: String, teamAbbrev: String): String = leagueId + TeamSeparator + teamAbbrev

        private val LeaguesKey = stringSetPreferencesKey("followed_leagues")
        private val TeamsKey = stringSetPreferencesKey("followed_teams")
        private val VisibleKey = booleanPreferencesKey("hub_visible")
        private const val TeamSeparator = "|"
    }
}
