package tv.own.owntv.tifo.sports

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.recording.RecordingManager
import tv.own.owntv.core.settings.SettingsRepository

data class SportsUiState(
    val loading: Boolean = true,
    /** Every competition failed to load (offline, or ESPN changed its API). */
    val failed: Boolean = false,
    /** Null = all competitions. */
    val filter: Competition? = null,
    val events: List<SportsEvent> = emptyList(),
    val selected: SportsEvent? = null,
    val matching: Boolean = false,
    val matches: List<SportsChannelMatch> = emptyList(),
    /** The selected game's own broadcasters ("CBS", "Ligue 1+"), each a search of all channels. */
    val broadcasters: List<String> = emptyList(),
) {
    val visibleEvents: List<SportsEvent> get() = if (filter == null) events else events.filter { it.competition == filter }
}

class SportsViewModel(
    http: OkHttpClient,
    db: OwnTVDatabase,
    channelDao: ChannelDao,
    sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    private val recordings: RecordingManager,
) : ViewModel() {

    private val scoreboard = EspnScoreboard(http)
    private val finder = SportsChannelFinder(db, channelDao, sourceDao, settings)

    private val _state = MutableStateFlow(SportsUiState())
    val state: StateFlow<SportsUiState> = _state.asStateFlow()

    private var matchJob: Job? = null

    /** Where the cursor goes when the tab comes back: the broadcaster search it left for. */
    private var returnFocus: String? = null

    fun setReturnFocus(key: String) { returnFocus = key }

    fun takeReturnFocus(): String? = returnFocus.also { returnFocus = null }

    /**
     * Keeps the scores fresh while the Sports tab is on screen: every 30 seconds while a game is
     * live, every 5 minutes otherwise. Call from a LaunchedEffect so it stops when the tab closes.
     */
    suspend fun refreshWhileVisible() {
        while (true) {
            refresh()
            val live = _state.value.events.any { it.state == EventState.LIVE }
            delay(if (live) LIVE_REFRESH_MS else IDLE_REFRESH_MS)
        }
    }

    private suspend fun refresh() {
        val now = System.currentTimeMillis()
        val results = coroutineScope {
            Competition.entries.map { competition ->
                async {
                    runCatching { scoreboard.load(competition, now) }
                        .onFailure { Log.w(TAG, "scoreboard ${competition.name} failed", it) }
                        .getOrNull()
                }
            }.awaitAll()
        }
        val events = results.filterNotNull().flatten()
            .filter { it.state != EventState.FINISHED || now - it.startMs < FINISHED_KEEP_MS }
            .sortedWith(eventOrder)
        _state.update { s ->
            s.copy(
                loading = false,
                failed = results.all { it == null },
                events = events,
                // Keep the open game current: its score and status change while it is shown.
                selected = s.selected?.let { sel -> events.firstOrNull { it.id == sel.id } ?: sel },
            )
        }
    }

    fun setFilter(competition: Competition?) = _state.update { it.copy(filter = competition) }

    /**
     * Opens [event] under its row and looks for the channels that show it; the open game again
     * closes it.
     */
    fun select(event: SportsEvent) {
        matchJob?.cancel()
        if (_state.value.selected?.id == event.id) {
            _state.update { it.copy(selected = null, matching = false, matches = emptyList(), broadcasters = emptyList()) }
            return
        }
        _state.update {
            it.copy(
                selected = event, matching = true, matches = emptyList(),
                broadcasters = finder.firstBroadcasters(event),
            )
        }
        matchJob = viewModelScope.launch {
            val matches = runCatching { finder.find(event, System.currentTimeMillis()) }
                .onFailure { Log.w(TAG, "channel matching failed", it) }
                .getOrDefault(emptyList())
            _state.update { if (it.selected?.id == event.id) it.copy(matching = false, matches = matches) else it }
        }
    }

    /** This profile's recordings, so a channel knows whether its game is already set to record. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recordingRows: StateFlow<List<RecordingEntity>> =
        settings.activeProfileId
            .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else recordings.observe(pid) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The recording already covering [match]'s guide programme, if there is one. */
    fun recordingFor(match: SportsChannelMatch, rows: List<RecordingEntity>): RecordingEntity? = rows.firstOrNull {
        it.channelId == match.channel.id && it.programmeStartMs == match.programmeStartMs &&
            it.status != RecordingStatus.CANCELLED
    }

    /**
     * Records the guide programme that shows the game on [match]'s channel, the way the guide's
     * Record does (with the user's padding), or cancels it when it is already set. Only for a
     * programme still to come: a game under way is watched, not scheduled.
     */
    fun toggleRecording(match: SportsChannelMatch) {
        val title = match.programmeTitle ?: return
        val start = match.programmeStartMs ?: return
        val stop = match.programmeStopMs ?: return
        viewModelScope.launch {
            recordingFor(match, recordingRows.value)?.let { recordings.cancel(it); return@launch }
            if (start <= System.currentTimeMillis()) return@launch
            val preferred = settings.activeProfileId.first()
            val pid = if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
            if (pid == null) return@launch
            val channel = match.channel
            val window = recordings.windowFor(start, stop)
            recordings.schedule(
                RecordingEntity(
                    profileId = pid,
                    sourceId = channel.sourceId,
                    channelId = channel.id,
                    channelName = channel.name,
                    channelIconUrl = channel.logoUrl,
                    epgChannelId = channel.epgChannelId,
                    streamUrl = channel.streamUrl,
                    httpHeaders = channel.httpHeaders,
                    title = title,
                    description = null,
                    programmeStartMs = start,
                    programmeStopMs = stop,
                    startMs = window.first,
                    stopMs = window.last,
                ),
            )
        }
    }

    private companion object {
        const val TAG = "TifoSports"
        const val LIVE_REFRESH_MS = 30_000L
        const val IDLE_REFRESH_MS = 5 * 60_000L

        /** Finished games stay listed for a day, for the result. */
        const val FINISHED_KEEP_MS = 24 * 60 * 60_000L

        /** Live first, then upcoming soonest first, then results newest first. */
        val eventOrder = Comparator<SportsEvent> { a, b ->
            val rank = { e: SportsEvent -> when (e.state) { EventState.LIVE -> 0; EventState.UPCOMING -> 1; EventState.FINISHED -> 2 } }
            val byState = rank(a).compareTo(rank(b))
            when {
                byState != 0 -> byState
                a.state == EventState.FINISHED -> b.startMs.compareTo(a.startMs)
                else -> a.startMs.compareTo(b.startMs)
            }
        }
    }
}
