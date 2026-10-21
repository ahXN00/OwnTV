package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh

/** How an EPG source's auto refresh reads, on its row badge and in the add / edit form. */
@Composable
internal fun epgRefreshLabel(refresh: EpgRefresh): String =
    if (refresh.mode == EpgAutoRefresh.MANUAL) {
        pluralStringResource(R.plurals.settings_sources_refresh_days, refresh.manualDays, refresh.manualDays)
    } else {
        epgAutoRefreshLabel(refresh.mode)
    }

@Composable
internal fun epgAutoRefreshLabel(mode: EpgAutoRefresh): String = stringResource(
    when (mode) {
        EpgAutoRefresh.OFF -> R.string.settings_sources_refresh_off
        EpgAutoRefresh.STARTUP -> R.string.settings_sources_refresh_startup
        EpgAutoRefresh.HOURS_1 -> R.string.settings_epg_refresh_1h
        EpgAutoRefresh.HOURS_3 -> R.string.settings_epg_refresh_3h
        EpgAutoRefresh.HOURS_6 -> R.string.settings_epg_refresh_6h
        EpgAutoRefresh.HOURS_12 -> R.string.settings_epg_refresh_12h
        EpgAutoRefresh.HOURS_24 -> R.string.settings_epg_refresh_24h
        EpgAutoRefresh.HOURS_48 -> R.string.settings_epg_refresh_48h
        EpgAutoRefresh.MANUAL -> R.string.settings_sources_refresh_manual
    },
)
