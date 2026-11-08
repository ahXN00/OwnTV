package tv.own.owntv.features.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.reminder.ReminderManager
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sports.Demand
import tv.own.owntv.core.sports.RebuildState
import tv.own.owntv.core.sports.FollowedTeam
import tv.own.owntv.core.sports.GuideSport
import tv.own.owntv.core.sports.GuideSportItem
import tv.own.owntv.core.sports.SportOnChannels
import tv.own.owntv.core.sports.SportsAlertsSetup
import tv.own.owntv.core.sports.CalendarMonth
import tv.own.owntv.core.sports.LeagueCard
import tv.own.owntv.core.sports.LeagueCards
import tv.own.owntv.core.sports.TeamSchedule
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.SportsBoard
import tv.own.owntv.core.sports.SportsCalendar
import tv.own.owntv.core.sports.SportsCatalog
import tv.own.owntv.core.sports.SportsTeams
import tv.own.owntv.core.sports.SportsHomeRow
import tv.own.owntv.core.sports.SportsConfig
import tv.own.owntv.core.sports.SportsMemory
import tv.own.owntv.core.sports.SportsRepository
import tv.own.owntv.core.sports.WatchFilter
import tv.own.owntv.core.sports.WatchList
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchReader
import tv.own.owntv.core.sports.WatchWindow
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchSummary
import tv.own.owntv.core.sports.model.StandingsTable
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.Team
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Where a match page's Find channels stands (channels are searched only when asked). */
sealed interface ChannelSearch {
    /** The match is being fetched afresh to judge Find channels' window. */
    data object Checking : ChannelSearch
    data class Closed(val window: WatchWindow) : ChannelSearch
    data object NotSearched : ChannelSearch
    data object Searching : ChannelSearch
    data class Found(val options: List<WatchOption>) : ChannelSearch
}

/** The Sports header's three tabs. The section always opens on [SCORES]. */
enum class SportsTab { SCORES, CALENDAR, LEAGUES }

/** One chip of the first chip row: the followed teams, or one sport (none chosen = everything). */
internal sealed interface HubChip {
    data object MyTeams : HubChip
    data class One(val sport: Sport) : HubChip
}

/**
 * What the two chip rows let through, the same on every tab. Row 1: a match passes when no chip is
 * chosen, or its sport is chosen, or My teams is chosen and it is one of theirs. Row 2: a chosen sport
 * with leagues chosen keeps only those leagues.
 */
internal class ChipFilter(val config: SportsConfig, val chips: Set<HubChip>, leagues: Set<String>) {
    private val chosenSports = chips.filterIsInstance<HubChip.One>().map { it.sport }.toSet()
    private val myTeamKeys = config.teams.map { it.key }.toSet()

    /** The followed leagues of the chosen sports, in the profile's order: the second chip row. */
    val sportLeagues: List<League> = config.followedLeagues().mapNotNull(SportsCatalog::league).filter { it.sport in chosenSports }
        .sortedWith(compareBy({ config.rankOf(it.sport, it.key).first }, { config.rankOf(it.sport, it.key).second }))

    /** The chosen leagues; a remembered league the profile no longer follows filters nothing. */
    val activeLeagues: Set<String> = leagues.filterTo(HashSet()) { k -> sportLeagues.any { it.key == k } }

    val myTeams: Boolean get() = HubChip.MyTeams in chips

    /** The one sport chosen, if exactly one is. */
    val oneSport: Sport? get() = chosenSports.singleOrNull()

    fun isMine(m: Match): Boolean = m.competitors.any { FollowedTeam(m.sport, it.team.id, it.team.name).key in myTeamKeys }

    fun passes(m: Match): Boolean {
        if (chips.isEmpty()) return true
        val bySport = m.sport in chosenSports && leaguesOf(m.sport).let { mine -> mine.isEmpty() || m.feed in mine }
        return bySport || (myTeams && isMine(m))
    }

    private fun leaguesOf(sport: Sport) = activeLeagues.filterTo(HashSet()) { k -> sportLeagues.any { it.key == k && it.sport == sport } }

    /** The followed leagues whose matches can pass, in the profile's order. */
    val leagueKeys: List<String> by lazy {
        val followed = config.followedLeagues().mapNotNull(SportsCatalog::league)
            .sortedWith(compareBy({ config.rankOf(it.sport, it.key).first }, { config.rankOf(it.sport, it.key).second }))
        if (chips.isEmpty()) return@lazy followed.map { it.key }
        val teamSports = config.teams.mapTo(HashSet()) { it.sport }
        followed.filter { l ->
            (l.sport in chosenSports && leaguesOf(l.sport).let { it.isEmpty() || l.key in it }) || (myTeams && l.sport in teamSports)
        }.map { it.key }
    }
}

/** A team page: the team, in the league its schedule is fetched for. [from] is the match row it was opened from, which takes focus again on Back. */
data class TeamRef(val league: League, val team: Team, val from: String? = null)

/** Sports whose sides have a team page. Athletes (tennis, golf, racing, MMA) have no season list; cricket's teams have no league. */
private val NO_TEAM_PAGE = setOf(Sport.TENNIS, Sport.GOLF, Sport.RACING, Sport.MMA, Sport.CRICKET)

internal fun hasTeamPages(sport: Sport): Boolean = sport !in NO_TEAM_PAGE

/** [team]'s page in [league], or null where there is none. */
internal fun teamRefOf(league: League, team: Team): TeamRef? =
    if (!hasTeamPages(league.sport) || team.isAthlete || team.id.isBlank()) null else TeamRef(league, team)

/** [team]'s page from a match of [m]'s competition, or null where there is none. */
internal fun teamRefOf(m: Match, team: Team): TeamRef? {
    val league = SportsCatalog.league(League.keyOf(m.sport, m.leagueSlug)) ?: SportsCatalog.league(m.feed) ?: return null
    return teamRefOf(league, team)
}

/** The match page: the match, its summary and table from the board, and "Watch live" once searched. */
data class MatchPageState(
    val match: Match,
    val summary: MatchSummary?,
    val standings: StandingsTable?,
    val config: SportsConfig,
    val search: ChannelSearch,
    val list: WatchList,
    val filter: WatchFilter,
    val playlists: List<Pair<Long, String>>,
    val preferredLanguages: List<String>,
) {
    val hasPlaylist: Boolean get() = playlists.isNotEmpty()
}

/** A team page: [schedule] is null while it loads; the [board] holds the league's table. */
data class TeamPageState(val schedule: TeamSchedule?, val board: SportsBoard)

/** The Leagues tab's cards, and the board they were read from (a My teams card shows the team's place). */
data class LeagueCardsState(val cards: List<LeagueCard>, val board: SportsBoard)

/**
 * One league in the Leagues tab: the board (its table, rankings, live matches) and the calendar's
 * months around today, which hold its fixtures and results.
 */
data class LeagueView(val league: League, val board: SportsBoard, val months: List<CalendarMonth>) {
    private val all: List<Match> by lazy {
        (months.flatMap { it.days.values.flatten() } + board.matches.filter { it.feed == league.key }).associateBy { it.key }.values.toList()
    }

    /** Still to be played (live first, then the soonest). */
    val fixtures: List<Match> get() = all.filter { it.state != MatchState.POST }.sortedBy { it.startMs }

    /** Played, latest first. */
    val results: List<Match> get() = all.filter { it.state == MatchState.POST }.sortedByDescending { it.startMs }

    /** Today's and tomorrow's matches on the board, live kept fresh: "This matchday". */
    val onBoard: List<Match> get() = (board.today[league.key].orEmpty() + board.tomorrow[league.key].orEmpty()).distinctBy { it.key }.sortedBy { it.startMs }
}

/**
 * Everything the hub draws. No channels: those are found per match, on the
 * match page ([SportsViewModel.matchPage]). [rebuilding] is true while the board is being rebuilt.
 */
data class SportsHubState(
    val board: SportsBoard = SportsBoard(),
    val config: SportsConfig = SportsConfig(),
    val onNow: Map<GuideSport, List<GuideSportItem>> = emptyMap(),
    val hasPlaylist: Boolean = true,
    val rebuilding: Boolean = false,
    /** Start times of the profile's programme reminders, keyed `start|title`, so a card can show "Reminder". */
    val reminders: Set<String> = emptySet(),
)

/**
 * The Sports hub's state. Collecting [hub] is what asks core for scores (`Demand.Hub`); nothing is
 * fetched while no sports screen is shown, and core drops the board five minutes after.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SportsViewModel(
    private val settings: SettingsRepository,
    private val repository: SportsRepository,
    private val watchReader: WatchReader,
    private val onChannels: SportOnChannels,
    private val alerts: SportsAlertsSetup,
    private val reminders: ReminderManager,
    private val playback: SportsPlayback,
    private val calendar: SportsCalendar,
    private val teams: SportsTeams,
    sourceDao: SourceDao,
) : ViewModel() {

    private val config = settings.activeSportsConfig.distinctUntilChanged()

    private val board = repository.observe(Demand.Hub)

    /** The profile's live playlists (id → name), for the filter panel's Playlist choice. */
    private val playlists = activeProfileSources(settings, sourceDao)
        .map { a -> a.sources.filter { it.syncLive }.map { it.id to it.name } }.distinctUntilChanged()

    private val hasPlaylist = playlists.map { it.isNotEmpty() }.distinctUntilChanged()

    /** This device's live playlist ids: a Settings playlist filter only counts these (core `WatchFilter.from`). */
    private val liveSources = playlists.map { l -> l.map { it.first } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The chips' ⟳, as Settings → Sports → Refresh sports data now: the whole board built again, and
     * the saved current and future calendar months marked out of date.
     */
    fun rebuild() {
        repository.rebuild()
        calendar.markStale()
    }

    private val onNow = combine(board, hasPlaylist) { b, has -> has to b.matches.filter { it.isLive }.map { it.key } }
        .distinctUntilChanged()
        .flatMapLatest { (has, _) ->
            if (!has) flowOf(emptyMap()) else onChannels.observe { repository.board.value.matches.filter { it.isLive } }
        }
        .flowOn(Dispatchers.Default)
        .onStart { emit(emptyMap()) }

    private val reminderKeys = settings.activeProfileId.flatMapLatest { pid ->
        if (pid < 0) flowOf(emptySet()) else reminders.observe(pid).map { list -> list.map { reminderKey(it.startMs, it.title) }.toSet() }
    }

    val hub: StateFlow<SportsHubState> = combine(board, config, onNow, hasPlaylist, repository.rebuild) { b, c, n, has, r ->
        SportsHubState(b, c, n, has, rebuilding = r is RebuildState.Running)
    }.combine(reminderKeys) { s, r -> s.copy(reminders = r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsHubState())

    /** The profile's programme reminders, keyed `start|title` ([reminderKey]), so a row can show its bell. */
    val reminderSet: StateFlow<Set<String>> = reminderKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** The profile's sports settings alone — no scores are asked for by reading it. */
    val sportsConfig: StateFlow<SportsConfig> = config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportsConfig())

    // --- The chips, shared by the three tabs ---------------------------------------------------------

    private val _chips = MutableStateFlow(playback.hubChip)
    private val _leagues = MutableStateFlow(playback.hubLeague)

    /** Both chip rows are multi-select: nothing chosen = All. Kept while the app runs (in [SportsPlayback]). */
    internal val chips: StateFlow<Set<HubChip>> = _chips.asStateFlow()
    val leagues: StateFlow<Set<String>> = _leagues.asStateFlow()

    internal fun setChips(chips: Set<HubChip>, leagues: Set<String>) {
        _chips.value = chips
        _leagues.value = leagues
        playback.hubChip = chips
        playback.hubLeague = leagues
    }

    /** Opens [leagueKey] in the Leagues tab: its sport and that league chosen, as the chips would. */
    fun chooseLeague(leagueKey: String) {
        val league = SportsCatalog.league(leagueKey) ?: return
        setChips(setOf(HubChip.One(league.sport)), setOf(leagueKey))
        tab.value = SportsTab.LEAGUES
    }

    // --- Where the section stands, kept while a match or team page is open over it ------------------

    /** The open tab. [SportsScreen] sets it back to Scores whenever the section is opened afresh. */
    val tab = MutableStateFlow(SportsTab.SCORES)

    /** The Calendar's chosen day (its month is the one shown). */
    val calendarDay = MutableStateFlow(LocalDate.now())

    /** The open league's own tab (Table, Fixtures, Results …), so Back from a match lands on it again. */
    val leagueTab = MutableStateFlow(0)

    /** The league [leagueTab] belongs to. */
    var leagueTabOf: String? = null

    // --- The Calendar tab ----------------------------------------------------------------------------

    private data class MonthAsk(val leagues: Set<String>, val month: YearMonth, val focus: LocalDate?)

    private val monthAsk = MutableStateFlow<MonthAsk?>(null)

    /** The Calendar shows [month] of [leagues]; [focus] is fetched first where the feed is per day (cricket). */
    fun showMonth(leagues: Set<String>, month: YearMonth, focus: LocalDate?) {
        monthAsk.value = MonthAsk(leagues, month, focus)
    }

    /** The shown month, while the Calendar is on screen (`Demand.Calendar` keeps today and tomorrow live). */
    val calendarMonth: StateFlow<CalendarMonth?> = monthAsk.flatMapLatest { a ->
        if (a == null) flowOf(null)
        else combine(calendar.month(a.leagues, a.month, a.focus), repository.observe(Demand.Calendar)) { m, _ -> m }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // --- The Home row --------------------------------------------------------------------------------

    /**
     * The Home "Live sports" row: followed matches while one is live or kicks off within the hour (core's
     * `SportsHomeRow`) — the leagues the profile chose and its teams, never "All sports"' every league.
     * No channels (found per match). Asks for scores only while collected, and only after
     * [HOME_ROW_DELAY_MS], so opening the app is never slowed by it.
     */
    val homeRow: StateFlow<List<Match>> = flow {
        delay(HOME_ROW_DELAY_MS)
        emitAll(
            // In the user's order (Settings → Sports and leagues), live first within it, then by kick-off.
            combine(repository.observe(Demand.HomeRow), config) { b, c ->
                SportsHomeRow.items(b, c, c.leagues.toSet(), System.currentTimeMillis())
                    .sortedWith(compareBy<Match>({ c.rankOf(it.sport, it.feed).first }, { c.rankOf(it.sport, it.feed).second }, { !it.isLive }, { it.startMs }))
            },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- The match page ------------------------------------------------------------------------------

    private val openMatch = MutableStateFlow<Match?>(null)

    /** The filter while a match page is open; null = the profile's defaults (Settings → Sports). */
    private val pageFilter = MutableStateFlow<WatchFilter?>(null)

    /** Set by Find channels on the open match page; reset when another match opens. */
    private val findAsked = MutableStateFlow(false)

    /** Find channels: search this match's channels (only this match, only now). */
    fun findChannels() { findAsked.value = true }

    /**
     * The open match, kept fresh while shown: its details and table come from the stored board
     * (`Demand.MatchPage` / `Demand.LeaguePage` fetch only what is live or missing). Channels are not
     * searched until Find channels; a search already made under the current guide shows at once.
     */
    val matchPage: StateFlow<MatchPageState?> = openMatch.flatMapLatest { m ->
        if (m == null) return@flatMapLatest flowOf(null)
        val leagueKey = League.keyOf(m.sport, m.leagueSlug).takeIf { SportsCatalog.league(it) != null }
        val boards = if (leagueKey == null) repository.observe(Demand.MatchPage(m))
        else combine(repository.observe(Demand.MatchPage(m)), repository.observe(Demand.LeaguePage(leagueKey))) { a, _ -> a }
        // Find channels' window is judged on the match fetched now, not the stored copy: a match can run
        // long, and a kick-off can move. Re-judged each minute while the page is open.
        val window = flow {
            emit(null)
            val fresh = repository.refetch(m) ?: m
            while (true) {
                val b = repository.board.value
                val now = System.currentTimeMillis()
                emit(WatchWindow.of(b.match(m.key) ?: fresh, b.endedAt[m.key], now))
                delay(60_000L)
            }
        }.distinctUntilChanged()
        val search = combine(config, hasPlaylist, findAsked, window) { c, has, asked, w -> listOf(c, has, asked, w) }.transformLatest { (cv, hv, av, w) ->
            val c = cv as SportsConfig
            val has = hv as Boolean
            val asked = av as Boolean
            if (!has) return@transformLatest emit(ChannelSearch.NotSearched)
            if (w == null) return@transformLatest emit(ChannelSearch.Checking)
            if (w != WatchWindow.Open) return@transformLatest emit(ChannelSearch.Closed(w as WatchWindow))
            val found = watchReader.found(m) { SportsMemory.rememberedFor(c, it) }
            when {
                found != null -> emit(ChannelSearch.Found(found))
                !asked -> emit(ChannelSearch.NotSearched)
                else -> {
                    emit(ChannelSearch.Searching)
                    emit(ChannelSearch.Found(watchReader.watchFor(listOf(m)) { SportsMemory.rememberedFor(c, it) }[m.key].orEmpty()))
                }
            }
        }.flowOn(Dispatchers.Default)
        combine(boards, config, search, pageFilter, playlists) { b, c, s, f, lists ->
            val appLanguage = Locale.getDefault().language
            val filter = f ?: WatchFilter.from(c, appLanguage, lists.map { it.first })
            val preferred = c.preferredLanguages(appLanguage).toList()
            val opts = (s as? ChannelSearch.Found)?.options.orEmpty()
            MatchPageState(
                match = b.match(m.key) ?: m,
                summary = b.summaries[m.key],
                standings = leagueKey?.let { b.standings[it] },
                config = c,
                search = s,
                list = WatchList.of(opts, filter, preferred, alsoList = preferred.toSet()),
                filter = filter,
                playlists = lists,
                preferredLanguages = preferred,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // --- The Leagues tab ----------------------------------------------------------------------------

    private val cardsAsk = MutableStateFlow<List<String>>(emptyList())
    private val leagueAsk = MutableStateFlow<String?>(null)

    /** The Leagues tab shows a card for each of [keys] (empty: none, or one league open instead). */
    fun showCards(keys: List<String>) { cardsAsk.value = keys }

    /** The Leagues tab shows one league ([key]), or none. */
    fun showLeague(key: String?) { leagueAsk.value = key }

    /**
     * The cards: each league's leader and its next match, from the board and the calendar's current and
     * next month. `Demand.Leagues` keeps the board fresh while shown.
     */
    val leagueCards: StateFlow<LeagueCardsState?> = cardsAsk.flatMapLatest { keys ->
        if (keys.isEmpty()) return@flatMapLatest flowOf(null)
        val now = YearMonth.now()
        combine(
            repository.observe(Demand.Leagues),
            calendar.month(keys.toSet(), now),
            calendar.month(keys.toSet(), now.plusMonths(1)),
        ) { b, m1, m2 ->
            val at = System.currentTimeMillis()
            LeagueCardsState(keys.mapNotNull(SportsCatalog::league).map { LeagueCards.card(it, b, listOf(m1, m2), at) }, b)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * One league: its table or rankings and live matches from the board (`Demand.LeaguePage`), and its
     * fixtures and results from the calendar's months, last month to next.
     */
    val leagueView: StateFlow<LeagueView?> = leagueAsk.flatMapLatest { key ->
        val league = key?.let(SportsCatalog::league) ?: return@flatMapLatest flowOf(null)
        val now = YearMonth.now()
        val keys = setOf(key)
        combine(
            repository.observe(Demand.LeaguePage(key)),
            calendar.month(keys, now.minusMonths(1)),
            calendar.month(keys, now),
            calendar.month(keys, now.plusMonths(1)),
        ) { b, m0, m1, m2 -> LeagueView(league, b, listOf(m0, m1, m2)) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // --- The team page -------------------------------------------------------------------------------

    /**
     * [ref]'s page: its schedule (null while it loads; ESPN's, kept for the day) and the board, whose
     * table gives its place (`Demand.TeamPage` asks for it, and keeps its league's live matches fresh).
     */
    fun teamPage(ref: TeamRef): Flow<TeamPageState> = combine(
        repository.observe(Demand.TeamPage(ref.league.key)),
        flow {
            emit(null)
            emit(teams.schedule(ref.league, ref.team.id, repository.board.value))
        },
    ) { b, s -> TeamPageState(s, b) }.flowOn(Dispatchers.Default)

    /**
     * A followed team's page from Settings, which knows its sport but not its league: the followed league
     * whose table on the board lists it, else the sport's first followed league, else its first league.
     */
    suspend fun teamRefFor(team: FollowedTeam): TeamRef? {
        val c = config.first()
        val board = repository.board.value
        val leagues = c.followedLeagues().mapNotNull(SportsCatalog::league).filter { it.sport == team.sport }
        val league = leagues.firstOrNull { l -> board.standings[l.key]?.groups?.any { g -> g.rows.any { it.team.id == team.id } } == true }
            ?: leagues.firstOrNull() ?: SportsCatalog.leaguesOf(team.sport).firstOrNull() ?: return null
        return teamRefOf(league, Team(team.id, team.name, abbreviation = team.abbreviation))
    }

    fun open(match: Match?) {
        pageFilter.value = null
        findAsked.value = false
        openMatch.value = match
    }

    /** A change in the filter panel. With "Remember these filters" on, it is saved to Settings → Sports as well. */
    fun setFilter(filter: WatchFilter) = viewModelScope.launch {
        pageFilter.value = filter
        if (config.first().rememberFilters) saveFilter(filter, remember = true)
    }

    /** "Remember these filters for every match": on saves the current filter as the profile's default. */
    fun setRememberFilters(on: Boolean) = viewModelScope.launch {
        val filter = matchPage.value?.filter ?: return@launch
        saveFilter(filter, remember = on)
    }

    private suspend fun saveFilter(filter: WatchFilter, remember: Boolean) {
        settings.updateSportsConfig(settings.activeProfileIdNow()) { c ->
            if (!remember) c.copy(rememberFilters = false)
            else c.copy(
                rememberFilters = true,
                languages = filter.languages - WatchFilter.UNKNOWN,
                // Settings keep two-letter countries; a three-letter prefix ("USA") filters this page only.
                countries = filter.countries.filter { it.length == 2 }.toSet(),
                minQuality = filter.minQuality,
                onlyConfirmed = filter.onlyConfirmed,
                showLikely = filter.showLikely,
                strictFilter = filter.strict,
                sourceIds = filter.sourceIds,
            )
        }
    }

    /** Hold OK on a channel: "Always use for <competition>". */
    fun rememberChannel(match: Match, channel: ChannelEntity) = viewModelScope.launch {
        settings.updateSportsConfig(settings.activeProfileIdNow()) { SportsMemory.remember(it, match, channel) }
    }

    /** The match page's Following: the competition it is fetched under, in or out of the followed set. */
    fun toggleLeague(feed: String) = viewModelScope.launch {
        settings.updateSportsConfig(settings.activeProfileIdNow()) { c ->
            val now = c.leagues
            c.copy(leagues = if (feed in now) now - feed else now + feed)
        }
    }

    fun follows(config: SportsConfig, feed: String): Boolean = feed in config.followedLeagues()

    /** Follow or unfollow a team (Hold OK on a card). */
    fun toggleTeam(team: FollowedTeam) = viewModelScope.launch {
        val pid = settings.activeProfileIdNow()
        settings.updateSportsConfig(pid) { c ->
            if (c.teams.any { it.key == team.key }) c.copy(teams = c.teams.filterNot { it.key == team.key })
            else c.copy(teams = c.teams + team)
        }
    }

    /** "Remind me" on a match not started yet; the best channel's programme reminder. */
    fun remind(match: Match, channels: List<WatchOption>) = viewModelScope.launch {
        val pid = settings.activeProfileIdNow()
        alerts.remind(pid, match, channels, config.first().reminderLeadMinutes)
    }

    /**
     * A channel is played from [match]: the player then offers its other [channels] and its page,
     * and the play counts toward "Suggested from what you watch".
     */
    val lastPlayedChannelId: Long? get() = playback.lastPlayedChannelId

    fun playedFrom(match: Match, channels: List<WatchOption>, playing: ChannelEntity? = null) {
        playback.lastPlayedChannelId = playing?.id
        playback.start(match, channels)
        recordPlay(match)
    }

    private fun recordPlay(match: Match) = viewModelScope.launch {
        val pid = settings.activeProfileIdNow()
        settings.updateSportsConfig(pid) { SportsMemory.recordPlay(it, match) }
    }

    /** The sports the profile follows a league of, in chip order. */
    fun followedSports(config: SportsConfig): List<Sport> {
        val keys = config.followedLeagues()
        return config.orderSports(SportsCatalog.sports.filter { s -> keys.any { SportsCatalog.league(it)?.sport == s } })
    }

    /** [match]'s channels if Find channels already ran for it (the hub's Remind me needs one); never searches. */
    suspend fun channelsFound(match: Match): List<WatchOption> {
        val c = config.first()
        val options = watchReader.found(match) { SportsMemory.rememberedFor(c, it) } ?: return emptyList()
        return WatchList.of(options, WatchFilter.from(c, Locale.getDefault().language, liveSources.value)).shown
    }

    companion object {
        private const val HOME_ROW_DELAY_MS = 3_000L

        fun reminderKey(startMs: Long, title: String) = "$startMs|$title"
    }
}
