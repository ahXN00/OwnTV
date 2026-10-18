@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.settings.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.settings.SettingsRepository

/** The active profile's Settings lock, and the switch for it in Customize. */
class SettingsPinViewModel(private val settings: SettingsRepository) : ViewModel() {

    val lock: StateFlow<SettingsPinLock> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) {
                flowOf(SettingsPinLock(loaded = true, profileId = pid))
            } else {
                combine(settings.customizePin(pid), settings.settingsPinEnabled(pid)) { pin, ask ->
                    SettingsPinLock(loaded = true, profileId = pid, pin = pin, askOnOpen = ask)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsPinLock())

    fun setAskOnOpen(enabled: Boolean) {
        val pid = lock.value.profileId
        if (pid < 0) return
        viewModelScope.launch { settings.setSettingsPinEnabled(pid, enabled) }
    }
}
