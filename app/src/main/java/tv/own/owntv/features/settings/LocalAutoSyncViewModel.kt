package tv.own.owntv.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.sync.local.LocalAutoSyncConfig
import tv.own.owntv.core.sync.local.LocalAutoSyncStore
import tv.own.owntv.core.sync.local.LocalAutoSyncTiming
import tv.own.owntv.core.sync.work.LocalAutoSyncScheduler

/**
 * The auto-sync settings, seen from the screen that edits them.
 *
 * The settings themselves live in core ([LocalAutoSyncStore]) because they are shared with the
 * phone and because the job that runs them is core's; this only sequences the screen — read what
 * is saved, hold what the user is changing, and put the result back when they press Save.
 *
 * Saving and scheduling are one act on purpose. A setting that says "daily" while the timer still
 * says "every six hours" is a setting the user has to trust blindly, and there is no way to tell
 * which of the two is wrong from the outside.
 */
class LocalAutoSyncViewModel(
    private val store: LocalAutoSyncStore,
    private val scheduler: LocalAutoSyncScheduler,
) : ViewModel() {

    /** What is saved right now. */
    val config: StateFlow<LocalAutoSyncConfig> = store.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LocalAutoSyncConfig())

    /**
     * Saves the settings and puts the new schedule in front of WorkManager.
     *
     * Everything else about the config is kept — the direction and the devices it applies to are
     * not this screen's business, and a screen that rewrote them with its own defaults would
     * quietly undo a setting made elsewhere.
     */
    fun save(timing: LocalAutoSyncTiming, sections: Set<BackupManager.Section>) {
        viewModelScope.launch {
            val config = store.current().copy(timing = timing, sections = sections)
            store.save(config)
            scheduler.schedule(config)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
