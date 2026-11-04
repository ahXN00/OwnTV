package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * Move up / Move down for a guide's panel. The EPG Sources order is the guide priority: where two
 * feeds list the same channel, the higher one is shown. Both moves are always offered, as on
 * Customize; at either end the store changes nothing, so the focused action never disappears.
 */
@Composable
internal fun epgSourceOrderActions(count: Int, onMove: (up: Boolean) -> Unit): List<StageAction> {
    if (count < 2) return emptyList()
    return listOf(
        StageAction(OwnTVIcon.CHEVRON_UP, stringResource(R.string.settings_row_menu_move_up), { onMove(true) }),
        StageAction(OwnTVIcon.CHEVRON_DOWN, stringResource(R.string.settings_row_menu_move_down), { onMove(false) }),
    )
}

/** Where the guide at [index] stands, or null when there is only one guide and nothing to rank. */
@Composable
internal fun epgSourcePriorityLine(index: Int, count: Int): String? =
    if (count < 2) null else stringResource(R.string.settings_epg_sources_priority, index + 1, count)
