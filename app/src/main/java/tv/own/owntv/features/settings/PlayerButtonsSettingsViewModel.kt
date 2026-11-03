package tv.own.owntv.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.settings.PlayerHoldOkAction
import tv.own.owntv.core.settings.PlayerRemoteOptions
import tv.own.owntv.core.settings.PlayerShortOkAction
import tv.own.owntv.core.settings.SettingsRepository

/** Settings › Remote shortcuts › Player buttons: what OK, the channel keys and the number keys do in the player. */
class PlayerButtonsSettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val options: StateFlow<PlayerRemoteOptions> = settings.playerRemoteOptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerRemoteOptions())

    fun setHoldOk(action: PlayerHoldOkAction) = update { it.copy(holdOk = action) }
    fun setShortOk(action: PlayerShortOkAction) = update { it.copy(shortOk = action) }
    fun setSwapChannelKeys(swap: Boolean) = update { it.copy(swapChannelKeys = swap) }
    fun setNumberKeysTune(tune: Boolean) = update { it.copy(numberKeysTune = tune) }

    private fun update(change: (PlayerRemoteOptions) -> PlayerRemoteOptions) {
        viewModelScope.launch { settings.updatePlayerRemoteOptions(change) }
    }
}
