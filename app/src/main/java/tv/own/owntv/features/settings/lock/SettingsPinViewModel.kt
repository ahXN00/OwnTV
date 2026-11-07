package tv.own.owntv.features.settings.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.settings.SettingsPinLock
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.activeSettingsPinLock

/** The active profile's Settings lock, and Settings › App › Settings lock's changes to it. */
class SettingsPinViewModel(private val settings: SettingsRepository) : ViewModel() {

    val lock: StateFlow<SettingsPinLock> = settings.activeSettingsPinLock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsPinLock())

    /** A new PIN turns the lock on (or changes it); null turns it off. */
    fun setPin(pin: String?) = edit { settings.setSettingsLockPin(it, pin) }

    fun setLockMenus(enabled: Boolean) = edit { settings.setSettingsLockMenus(it, enabled) }

    private fun edit(block: suspend (Long) -> Unit) {
        val pid = lock.value.profileId
        if (pid < 0) return
        viewModelScope.launch { block(pid) }
    }
}
