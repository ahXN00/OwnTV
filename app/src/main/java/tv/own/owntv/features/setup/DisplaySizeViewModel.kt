package tv.own.owntv.features.setup

import tv.own.owntv.core.brand.AppIcon
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom

/**
 * The first-run display-size step (#179): interface zoom and text size, before the rest of setup.
 *
 * Both values are the same stored settings the Settings screen edits, so a choice made here is the
 * app's from then on. Writes land in DataStore immediately — [MainActivity] scales `LocalDensity`
 * from these flows, which is what makes the step's sample text resize as the user presses.
 */
class DisplaySizeViewModel(private val settings: SettingsRepository) : ViewModel() {

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiZoom.DEFAULT)

    val fontSizePercent: StateFlow<Int> = settings.fontCustomization
        .map { it.sizePercent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiFontScale.DEFAULT)

    /**
     * The full [UiZoom.MIN]..[UiZoom.MAX] range, exactly as Settings offers it. Stepping below
     * [UiZoom.LOW_RAM_WARN] is gated by the same accept-the-risk warning (#51) — the screen owns
     * that prompt, this only writes what the user settled on.
     */
    fun setZoom(percent: Int) = viewModelScope.launch {
        settings.setUiZoomPercent(UiZoom.clamp(percent))
    }

    /** Font size only — the rest of the stored customization (families, popup sizes) is preserved. */
    fun setFontSize(percent: Int) = viewModelScope.launch {
        val current = settings.fontCustomization.first()
        settings.setFontCustomization(current.copy(sizePercent = UiFontScale.clamp(percent)))
    }

    /** The icon colour picked here reaches the launcher once the app is in the background. */
    val appIcon: StateFlow<AppIcon> = settings.appIcon
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppIcon.DEFAULT)

    fun setAppIcon(icon: AppIcon) = viewModelScope.launch { settings.setAppIcon(icon) }

    fun reset() = viewModelScope.launch {
        settings.setUiZoomPercent(UiZoom.DEFAULT)
        val current = settings.fontCustomization.first()
        settings.setFontCustomization(current.copy(sizePercent = UiFontScale.DEFAULT))
    }
}
