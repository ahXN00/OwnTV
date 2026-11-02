package tv.own.owntv.features.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.RebuildState
import tv.own.owntv.core.sports.SportsActivityTracker
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.SportsMemory
import tv.own.owntv.core.sports.SportsRepository
import tv.own.owntv.core.sports.SportsSource
import tv.own.owntv.core.sports.SportsSuggestion
import tv.own.owntv.core.sports.model.Team
import java.util.Locale

/**
 * Settings → Sports. Every value is the active profile's `SportsConfig`, so it travels in
 * backups and local sync with no extra work; "Show Sports in the menu" is the Navigation setting's own
 * hidden set.
 */
class SportsSettingsViewModel(
    private val settings: SettingsRepository,
    private val source: SportsSource,
    private val repository: SportsRepository,
    private val calendar: tv.own.owntv.core.sports.SportsCalendar,
    activity: SportsActivityTracker,
    sourceDao: SourceDao,
) : ViewModel() {

    val config: StateFlow<SportsConfig> = settings.activeSportsConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsConfig())

    val shownInMenu: StateFlow<Boolean> = settings.navMenuHidden.map { MainSection.SPORTS.name !in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The profile's live playlists (id → name), for the Playlist filter. */
    val playlists: StateFlow<List<Pair<Long, String>>> = activeProfileSources(settings, sourceDao)
        .map { a -> a.sources.filter { it.syncLive }.map { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** When the sports data was last rebuilt, and whether a rebuild runs now (Refresh sports data now). */
    val rebuiltAt: StateFlow<Long?> = activity.rebuiltAt
    val rebuilding: StateFlow<Boolean> = activity.rebuild.map { it is RebuildState.Running }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Refresh sports data now: the board built again, and the saved current and future calendar months marked out of date. */
    fun rebuild() {
        repository.rebuild()
        calendar.markStale()
    }

    /** The app's language, which ranks first until the user picks commentary languages. */
    val appLanguage: String get() = Locale.getDefault().language

    fun update(transform: (SportsConfig) -> SportsConfig) = viewModelScope.launch {
        settings.updateSportsConfig(settings.activeProfileIdNow(), transform)
    }

    fun setShownInMenu(on: Boolean) = viewModelScope.launch {
        val hidden = settings.navMenuHidden.first()
        settings.setNavMenuHidden(if (on) hidden - MainSection.SPORTS.name else hidden + MainSection.SPORTS.name)
    }

    fun toggleLeague(key: String) = update { c ->
        val now = c.leagues
        c.copy(leagues = if (key in now) now - key else now + key)
    }

    /** The Move overlay saved a new sport order (Settings → Sports and leagues). */
    fun setSportOrder(order: List<tv.own.owntv.core.sports.model.Sport>) = update { it.copy(sportOrder = order) }

    /**
     * The Move overlay saved a new order for one sport's chosen leagues: they go first, the other
     * chosen leagues follow as they were (only the order within a sport ranks anything).
     */
    fun setLeagueOrder(keys: List<String>) = update { c -> c.copy(leagues = keys + (c.leagues - keys.toSet())) }

    fun toggleTeam(team: FollowedTeam) = update { c ->
        if (c.teams.any { it.key == team.key }) c.copy(teams = c.teams.filterNot { it.key == team.key }) else c.copy(teams = c.teams + team)
    }

    fun suggestions(c: SportsConfig): List<SportsSuggestion> = SportsMemory.suggestions(c, c.followedLeagues())

    /** Teams of the followed leagues, fetched once per page visit, for the My teams search. */
    private var teamIndex: List<Pair<FollowedTeam, String>>? = null

    suspend fun searchTeams(query: String): List<FollowedTeam> {
        if (query.trim().length < 2) return emptyList()
        val index = teamIndex ?: buildList {
            for (key in config.value.followedLeagues()) {
                val league = SportsCatalog.league(key) ?: continue
                val teams: List<Team> = runCatching { source.teams(league) }.getOrDefault(emptyList())
                teams.forEach { t -> add(FollowedTeam(league.sport, t.id, t.name, t.abbreviation) to t.name) }
            }
        }.distinctBy { it.first.key }.also { teamIndex = it }
        val q = query.trim().lowercase()
        return index.filter { (_, name) -> name.lowercase().contains(q) }.map { it.first }.take(30)
    }
}
