package tv.own.owntv.features.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.ui.components.DayStepperDialog

/** Picker entry for a mode. Manual reads as an ellipsis label — it opens the day stepper. */
@Composable
internal fun refreshModeLabel(mode: PlaylistAutoRefresh): String = stringResource(
    when (mode) {
        PlaylistAutoRefresh.OFF -> R.string.settings_sources_refresh_off
        PlaylistAutoRefresh.STARTUP -> R.string.settings_sources_refresh_startup
        PlaylistAutoRefresh.HOURS_6 -> R.string.settings_sources_refresh_6h
        PlaylistAutoRefresh.HOURS_12 -> R.string.settings_sources_refresh_12h
        PlaylistAutoRefresh.MANUAL -> R.string.settings_sources_refresh_manual
    },
)

/** The chosen selection as shown on a row: Manual spells out the day count it resolved to. */
@Composable
internal fun playlistAutoRefreshLabel(refresh: PlaylistRefresh): String =
    if (refresh.mode == PlaylistAutoRefresh.MANUAL) {
        pluralStringResource(R.plurals.settings_sources_refresh_days, refresh.manualDays, refresh.manualDays)
    } else {
        refreshModeLabel(refresh.mode)
    }

/**
 * Day stepper for the Manual auto-refresh interval — [DayStepperDialog] with the playlist's own
 * wording and bounds.
 */
@Composable
internal fun ManualDaysDialog(initialDays: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    DayStepperDialog(
        title = stringResource(R.string.settings_sources_refresh_days_title),
        hint = stringResource(R.string.settings_sources_refresh_days_hint),
        initialDays = initialDays,
        minDays = PlaylistRefresh.MIN_MANUAL_DAYS,
        maxDays = PlaylistRefresh.MAX_MANUAL_DAYS,
        label = { days -> pluralStringResource(R.plurals.settings_sources_refresh_days, days, days) },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
