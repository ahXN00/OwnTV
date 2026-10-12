package tv.own.owntv.tifo.sports

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The teams picked as favourites in Sports' settings, per league, kept on the device. */
class SportsFavorites private constructor(private val prefs: SharedPreferences) {

    private val _keys = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())

    /** "NFL:7" for the Broncos: the league and ESPN's team id. */
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    fun toggle(competition: Competition, team: SportsTeam) {
        if (team.id.isBlank()) return
        val key = keyOf(competition, team.id)
        val next = if (key in _keys.value) _keys.value - key else _keys.value + key
        _keys.value = next
        prefs.edit().putStringSet(KEY, next).apply()
    }

    companion object {
        private const val PREFS = "tifo_sports_favorites"
        private const val KEY = "teams"

        fun keyOf(competition: Competition, teamId: String): String = competition.name + ":" + teamId

        /** Whether [team] is a favourite in [competition], given the current [keys]. */
        fun isFavorite(keys: Set<String>, competition: Competition, team: SportsTeam?): Boolean =
            team != null && team.id.isNotBlank() && keyOf(competition, team.id) in keys

        @Volatile private var instance: SportsFavorites? = null

        fun get(context: Context): SportsFavorites = instance ?: synchronized(this) {
            instance ?: SportsFavorites(
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
