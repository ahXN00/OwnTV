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

/** The active profile's Settings lock, and the switch for it in Customize. */
class SettingsPinViewModel(private val settings: SettingsRepository) : ViewModel() {

    val lock: StateFlow<SettingsPinLock> = settings.activeSettingsPinLock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsPinLock())

    fun setAskOnOpen(enabled: Boolean) {
        val pid = lock.value.profileId
        if (pid < 0) return
        viewModelScope.launch { settings.setSettingsPinEnabled(pid, enabled) }
    }
}
