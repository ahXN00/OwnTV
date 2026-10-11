package tv.own.owntv.features.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Sports hub state: ESPN boards per league (cached 15 minutes), follow sets, the selected league
 * and fixture, channel matches and standings. Playback itself stays in [LiveViewModel] — the
 * screen calls it with a matched channel id, so there is exactly one zap path.
 */
class SportsViewModel(
    private val prefs: SportsPrefs,
    private val api: EspnApi,
    private val matcher: SportsMatcher,
) : ViewModel() {

    sealed interface BoardState {
        data object Loading : BoardState
        data class Content(val board: EspnApi.Scoreboard) : BoardState
        data object Error : BoardState
    }

    sealed interface MatchesState {
        data object Idle : MatchesState
        data object Loading : MatchesState
        data class Content(val matches: List<ChannelMatch>) : MatchesState
    }

    data class LeagueUi(
        val league: SportLeague,
        val name: String,
        val followed: Boolean,
        val liveCount: Int,
        val loading: Boolean,
    )

    val followedLeagues: StateFlow<Set<String>> = prefs.followedLeagues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsLeagues.map { it.id }.toSet())

    val followedTeams: StateFlow<Set<String>> = prefs.followedTeams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val hubVisible: StateFlow<Boolean> = prefs.hubVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val boards = MutableStateFlow<Map<String, BoardState>>(emptyMap())
    private val boardAt = HashMap<String, Long>()
    private val standingsCache = HashMap<String, List<LeagueTable>>()
    private val matchesCache = HashMap<String, List<ChannelMatch>>()

    private val _selectedLeagueId = MutableStateFlow(SportsLeagues.first().id)
    val selectedLeagueId: StateFlow<String> = _selectedLeagueId.asStateFlow()

    private val _selectedFixture = MutableStateFlow<Fixture?>(null)
    val selectedFixture: StateFlow<Fixture?> = _selectedFixture.asStateFlow()

    /** The selected league's board (null until first load), for fixtures + error state. */
    val selectedBoard: StateFlow<BoardState?> =
        combine(boards, _selectedLeagueId) { current, id -> current[id] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _matches = MutableStateFlow<MatchesState>(MatchesState.Idle)
    val matches: StateFlow<MatchesState> = _matches.asStateFlow()

    private val _standings = MutableStateFlow<List<LeagueTable>>(emptyList())
    val standings: StateFlow<List<LeagueTable>> = _standings.asStateFlow()

    private val _standingsLoading = MutableStateFlow(false)
    val standingsLoading: StateFlow<Boolean> = _standingsLoading.asStateFlow()

    val leagues: StateFlow<List<LeagueUi>> =        combine(boards, followedLeagues) { current, followed ->
            val ordered = SportsLeagues.sortedBy { if (it.id in followed) 0 else 1 }
            ordered.map { league ->
                val board = (current[league.id] as? BoardState.Content)?.board
                LeagueUi(
                    league = league,
                    name = board?.leagueName?.ifBlank { null } ?: league.id,
                    followed = league.id in followed,
                    liveCount = board?.fixtures?.count { it.state == FixtureState.LIVE } ?: 0,
                    loading = current[league.id] is BoardState.Loading,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsLeagues.map {
            LeagueUi(it, it.id, true, 0, false)
        })

    /** Fixtures involving followed teams, across every loaded board — the "My teams" rail. */
    val myTeamFixtures: StateFlow<List<Fixture>> =
        combine(boards, followedTeams) { current, followed ->
            if (followed.isEmpty()) return@combine emptyList()
            current.values.filterIsInstance<BoardState.Content>()
                .flatMap { it.board.fixtures }
                .filter { fixture ->
                    followed.contains(SportsPrefs.teamKey(fixture.leagueId, fixture.home.abbreviation)) ||
                        followed.contains(SportsPrefs.teamKey(fixture.leagueId, fixture.away.abbreviation))
                }
                .sortedWith(compareBy<Fixture> { it.state != FixtureState.LIVE }.thenBy { it.dateMs })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            supervisorScope {
                SportsLeagues.map { league ->
                    async { loadBoard(league, force = false) }
                }.awaitAll()
            }
        }
    }

    fun refreshSelected() {
        val league = sportsLeagueById(_selectedLeagueId.value) ?: return
        viewModelScope.launch { loadBoard(league, force = true) }
    }

    fun selectLeague(id: String) {
        _selectedLeagueId.value = id
        _selectedFixture.value = null
        _matches.value = MatchesState.Idle
        val league = sportsLeagueById(id) ?: return
        viewModelScope.launch {
            loadBoard(league, force = false)
            loadStandings(league)
        }
    }

    fun selectFixture(fixture: Fixture?) {
        _selectedFixture.value = fixture
        if (fixture == null) {
            _matches.value = MatchesState.Idle
            return
        }
        matchesCache[fixture.id]?.let {
            _matches.value = MatchesState.Content(it)
            return
        }
        _matches.value = MatchesState.Loading
        viewModelScope.launch {
            val found = runCatching { matcher.match(fixture) }.getOrDefault(emptyList())
            matchesCache[fixture.id] = found
            if (_selectedFixture.value?.id == fixture.id) _matches.value = MatchesState.Content(found)
        }
    }

    fun toggleLeagueFollowed(leagueId: String) {
        viewModelScope.launch {
            prefs.setLeagueFollowed(leagueId, followedLeagues.value.contains(leagueId).not())
        }
    }

    fun toggleTeamFollowed(leagueId: String, abbrev: String) {
        viewModelScope.launch {
            val key = SportsPrefs.teamKey(leagueId, abbrev)
            prefs.setTeamFollowed(key, followedTeams.value.contains(key).not())
        }
    }

    fun isTeamFollowed(leagueId: String, abbrev: String): Boolean =
        followedTeams.value.contains(SportsPrefs.teamKey(leagueId, abbrev))

    /** Resolve matched ids to entities for the zap context, then hand off to the player caller. */
    fun playMatch(match: ChannelMatch, matches: List<ChannelMatch>, onPlay: (Long, List<tv.own.owntv.core.database.entity.ChannelEntity>) -> Unit) {
        viewModelScope.launch {
            val entities = runCatching { matcher.channels(matches.map { it.channelId }) }.getOrDefault(emptyList())
            onPlay(match.channelId, entities)
        }
    }

    /** Entities for the channel matches, in order — for the in-pane preview. */
    suspend fun entitiesFor(matches: List<ChannelMatch>): List<tv.own.owntv.core.database.entity.ChannelEntity> =
        runCatching { matcher.channels(matches.map { it.channelId }) }.getOrDefault(emptyList())

    fun setHubVisible(visible: Boolean) {
        viewModelScope.launch { prefs.setHubVisible(visible) }
    }

    private suspend fun loadBoard(league: SportLeague, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force) {
            val at = boardAt[league.id] ?: 0L
            if (now - at < BoardTtlMs && boards.value[league.id] is BoardState.Content) return
            if (boards.value[league.id] is BoardState.Loading) return
        }
        boards.value = boards.value + (league.id to BoardState.Loading)
        val result = runCatching { api.scoreboard(league) }
        result.onFailure { android.util.Log.w("SportsHub", "board failed " + league.id, it) }
        result.onSuccess { android.util.Log.d("SportsHub", "board ok " + league.id + " fixtures=" + it.fixtures.size) }
        boardAt[league.id] = now
        boards.value = boards.value + (
            league.id to
                (result.getOrNull()?.let { BoardState.Content(it) } ?: BoardState.Error)
            )
    }

    private suspend fun loadStandings(league: SportLeague) {        standingsCache[league.id]?.let {
            _standings.value = it
            return
        }
        _standingsLoading.value = true
        val tables = runCatching { api.standings(league) }.getOrDefault(emptyList())
        standingsCache[league.id] = tables
        _standings.value = tables
        _standingsLoading.value = false
    }

    companion object {
        private const val BoardTtlMs = 15L * 60L * 1000L
    }
}
