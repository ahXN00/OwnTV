package tv.own.owntv.features.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.ItemCategory
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.player.ExternalPlayerLauncher
import tv.own.owntv.core.recording.RecordingManager
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.storage.missingFiles
import tv.own.owntv.features.categorylock.PlayingFileCategory
import tv.own.owntv.features.categorylock.anyLockSealed
import tv.own.owntv.features.categorylock.withoutSealed
import tv.own.owntv.player.OwnTVPlayer

/**
 * What has been recorded, what is being recorded, and what never was.
 *
 * Everything here is core's — the same table the phone reads — so this only observes it and offers
 * the four things a recording can be told. Recordings are **not** filtered by the customisation
 * hidden-items list the way downloads are: a recording is a file the user asked for by name, not a
 * catalogue row, and hiding a channel should not make last night's programme disappear. A locked
 * category is different: it keeps what it holds behind the PIN, so a recording of one of its channels
 * stays out of the list, and will not play, until the PIN opens the category.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModel(
    /** Only to check whether finished files are still there. */
    appContext: android.content.Context,
    private val settings: SettingsRepository,
    private val recordings: RecordingManager,
    val player: OwnTVPlayer,
    private val externalPlayerLauncher: ExternalPlayerLauncher,
    private val channelDao: ChannelDao,
    private val customize: CustomizationStore,
    private val playingFiles: PlayingFileCategory,
) : ViewModel() {

    val rows: StateFlow<List<RecordingEntity>> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(emptyList())
            else recordings.observe(pid).withoutSealed(
                customize.observeVisible(pid, MediaType.LIVE).map { it.sealedCategoryIds }.distinctUntilChanged(),
                customize.anyLockSealed(pid),
                ::channelCategory,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * False when Android will not let the app set exact alarms, so the screen can say that a
     * recording may begin a few minutes late. Read once: revoking the permission stops the app, so
     * the answer cannot change under a running process.
     */
    /** Paths of finished recordings whose file is gone (drive formatted, swapped or unplugged). */
    val missingFiles: StateFlow<Set<String>> = rows
        .map { list -> list.filter { it.status == RecordingStatus.COMPLETED }.map { it.filePath } }
        .missingFiles(appContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val timersAreExact: Boolean = recordings.timersAreExact()

    private val _lastPlayedId = MutableStateFlow<Long?>(null)

    /** The recording last asked to play, so returning from the player lands back on its row. */
    val lastPlayedId: StateFlow<Long?> = _lastPlayedId.asStateFlow()

    /**
     * Play a finished recording from the file on disk.
     *
     * `isLive = false` even though what it holds is live television: the flag describes the *source*,
     * and a file on disk seeks, pauses and ends. Treating it as live would take the scrub bar away
     * from the one recording the user most wants to skip through.
     *
     * A recording that is still running is deliberately not playable here. A `.ts` can be played
     * while it is being written, and offering that from this screen would mean two readers on one
     * growing file and a picture that stops at whatever byte it started from.
     */
    fun play(recording: RecordingEntity) {
        if (recording.status != RecordingStatus.COMPLETED) return
        val path = recording.filePath ?: return
        _lastPlayedId.value = recording.id
        viewModelScope.launch {
            val category = channelCategory(recording.channelId)
            if (customize.sealsItem(settings.activeProfileId.first(), category)) return@launch
            val categoryId = (category as? ItemCategory.Found)?.id
            if (settings.externalPlayerFor(MediaType.LIVE).first()) {
                externalPlayerLauncher.launch(path, recording.title)
                return@launch
            }
            playingFiles.started(path, categoryId)
            player.play(path, title = recording.title, isLive = false)
        }
    }

    /** True when playback should be handed to another app, so the shell does not mount its player. */
    val externalPlayerOn: StateFlow<Boolean> = settings.externalPlayerFor(MediaType.LIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Ends a running recording and keeps what it captured — a short recording, not a failure. */
    fun stop(recording: RecordingEntity) = recordings.stop(recording)

    /** Drops a scheduled recording. Nothing on disk is touched, because nothing is there yet. */
    fun cancel(recording: RecordingEntity) = recordings.cancel(recording)

    /** Removes the row **and** the file. The only thing here that deletes anything (D2). */
    fun delete(recording: RecordingEntity) = recordings.delete(recording)

    /**
     * Try a missed or failed recording again — only possible while its window is still open, which
     * for a live programme is rarely. Re-scheduling is the honest action: the engine decides again
     * whether there is a connection for it.
     */
    fun retry(recording: RecordingEntity) {
        viewModelScope.launch { recordings.schedule(recording) }
    }

    /** The category of a recording's channel; unresolved once a re-sync gave the channel a new id. */
    private suspend fun channelCategory(channelId: Long): ItemCategory =
        channelDao.getById(channelId)?.let { ItemCategory.Found(it.categoryId) } ?: ItemCategory.Unresolved
}
