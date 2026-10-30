package tv.own.owntv.features.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.core.nav.MainSection

/** What kind of item a [UiItem] is. */
enum class UiItemType { CHANNEL, MOVIE, SERIES, EPISODE }

/** An item on the TV, as `GET ui` names it. */
data class UiItem(val type: UiItemType, val id: Long, val title: String)

/** How the player shows, as `GET ui` reports it. */
enum class UiPlayer { NONE, FULLSCREEN, MINI, AUDIO }

/**
 * What the shell has on screen. [section] is null while the shell is not up (the profile gate, setup);
 * [focused] and [playing] are best effort, null when the shell cannot tell.
 */
data class UiSnapshot(
    val section: MainSection? = null,
    val player: UiPlayer = UiPlayer.NONE,
    val focused: UiItem? = null,
    val playing: UiItem? = null,
)

/** The shell's latest [UiSnapshot], published from the composition and read by `GET ui` on IO threads. */
class AutomationUiState {
    private val _current = MutableStateFlow(UiSnapshot())
    val current: StateFlow<UiSnapshot> = _current.asStateFlow()

    fun publish(snapshot: UiSnapshot) {
        _current.value = snapshot
    }

    /** The shell left the screen: nothing it published still holds. */
    fun clear() {
        _current.value = UiSnapshot()
    }
}
