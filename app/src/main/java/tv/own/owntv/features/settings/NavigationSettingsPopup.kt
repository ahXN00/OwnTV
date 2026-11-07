package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.settings.SettingsRepository.NavHideAfter
import tv.own.owntv.core.settings.SettingsRepository.NavLength
import tv.own.owntv.core.settings.SettingsRepository.NavMenuMode
import tv.own.owntv.core.settings.SettingsRepository.NavSize
import tv.own.owntv.core.settings.SettingsRepository.NavStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupDivider
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.stage.StagePopupRadio
import tv.own.owntv.ui.stage.StagePill
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import androidx.compose.foundation.layout.Spacer

/**
 * Settings › Layout › Navigation: Floating or Docked as radio rows, then Menu labels (see [MenuLabels]), the rail's
 * Size and Length (Docked + Compact adds Widen on focus), Menu items (Dynamic / Static; Static lists the sections as toggles) and, for Floating
 * only, the hide delay. OK on a value row steps to its next choice.
 */
@Composable
fun NavigationSettingsPopup(
    style: NavStyle,
    onStyle: (NavStyle) -> Unit,
    size: NavSize,
    onSize: (NavSize) -> Unit,
    length: NavLength,
    onLength: (NavLength) -> Unit,
    widen: NavSize?,
    onWiden: (NavSize?) -> Unit,
    hideAfterMs: Int,
    onHideAfterMs: (Int) -> Unit,
    menuMode: NavMenuMode,
    onMenuMode: (NavMenuMode) -> Unit,
    hiddenSections: Set<MainSection>,
    onSectionHidden: (MainSection, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler { onDismiss() }
    val locale = LocalConfiguration.current.locales[0]
    val eyebrow = stringResource(
        R.string.settings_breadcrumb_eyebrow,
        stringResource(R.string.settings_title),
        stringResource(R.string.settings_group_layout),
    ).uppercase(locale)
    val styles = listOf(
        NavStyle.FLOATING to (R.string.settings_nav_floating to R.string.settings_nav_floating_desc),
        NavStyle.DOCKED to (R.string.settings_nav_docked to R.string.settings_nav_docked_desc),
    )

    StagePopup(onDismiss = onDismiss, title = stringResource(R.string.settings_navigation), eyebrow = eyebrow) {
        styles.forEachIndexed { i, (value, text) ->
            StagePopupOption(
                title = stringResource(text.first),
                subtitle = stringResource(text.second),
                onClick = { onStyle(value) },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                leading = { focused -> StagePopupRadio(on = value == style, focused = focused) },
            )
        }
        StagePopupDivider()
        MenuLabelsOption(style, size, widen, onSize, onWiden)
        StagePopupOption(
            title = stringResource(R.string.settings_size),
            subtitle = stringResource(R.string.settings_nav_size_desc),
            value = stringResource(size.labelRes),
            onClick = { onSize(NavSize.entries[(size.ordinal + 1) % NavSize.entries.size]) },
            leading = { OwnTVIcon(OwnTVIcon.EXPAND, StageColors.Text, Modifier.size(21.mpx)) },
        )
        StagePopupOption(
            title = stringResource(R.string.settings_nav_length),
            subtitle = stringResource(R.string.settings_nav_length_desc),
            value = stringResource(if (length == NavLength.FIT) R.string.settings_nav_length_fit else R.string.settings_nav_length_full),
            onClick = { onLength(if (length == NavLength.FIT) NavLength.FULL else NavLength.FIT) },
            leading = { OwnTVIcon(OwnTVIcon.SORT, StageColors.Text, Modifier.size(21.mpx)) },
        )
        // Docked + Compact: the size the rail opens to, over the content, while it has focus.
        if (style == NavStyle.DOCKED && size == NavSize.COMPACT) {
            val widths = listOf(null, NavSize.NORMAL, NavSize.WIDE, NavSize.EXTRA_WIDE)
            StagePopupOption(
                title = stringResource(R.string.settings_nav_widen),
                subtitle = stringResource(R.string.settings_nav_widen_desc),
                value = widen?.let { stringResource(it.labelRes) } ?: stringResource(R.string.common_off),
                onClick = { onWiden(widths[(widths.indexOf(widen) + 1) % widths.size]) },
                leading = { OwnTVIcon(OwnTVIcon.EXPAND, StageColors.Text, Modifier.size(21.mpx)) },
            )
        }
        val static = menuMode == NavMenuMode.STATIC
        StagePopupOption(
            title = stringResource(R.string.settings_nav_menu_items),
            subtitle = stringResource(R.string.settings_nav_menu_items_desc),
            value = stringResource(if (static) R.string.settings_static else R.string.settings_dynamic),
            onClick = { onMenuMode(if (static) NavMenuMode.DYNAMIC else NavMenuMode.STATIC) },
            leading = { OwnTVIcon(OwnTVIcon.GRID, StageColors.Text, Modifier.size(21.mpx)) },
        )
        // Static: the browse sections as toggles, in rail order; Search and More can never be hidden.
        if (static) {
            FlowRow(
                Modifier.padding(start = 61.mpx, end = 22.mpx, top = 6.mpx, bottom = 14.mpx),
                horizontalArrangement = Arrangement.spacedBy(12.mpx),
                verticalArrangement = Arrangement.spacedBy(12.mpx),
            ) {
                MenuSections.forEach { (section, icon) ->
                    val shown = section !in hiddenSections
                    StagePill(
                        text = stringResource(section.labelRes),
                        onClick = { onSectionHidden(section, shown) },
                        icon = icon,
                        trailingIcon = if (shown) OwnTVIcon.CHECK else OwnTVIcon.EYE_OFF,
                        dimmed = !shown,
                    )
                }
            }
        }
        if (style == NavStyle.FLOATING) {
            StagePopupOption(
                title = stringResource(R.string.settings_nav_hide_after),
                subtitle = stringResource(R.string.settings_nav_hide_after_desc),
                value = if (hideAfterMs < 1000) {
                    stringResource(R.string.settings_nav_hide_after_half)
                } else {
                    pluralStringResource(R.plurals.settings_nav_hide_after_seconds, hideAfterMs / 1000, hideAfterMs / 1000)
                },
                onClick = {
                    val choices = NavHideAfter.CHOICES_MS
                    onHideAfterMs(choices[(choices.indexOf(hideAfterMs) + 1) % choices.size])
                },
                leading = { OwnTVIcon(OwnTVIcon.CLOCK, StageColors.Text, Modifier.size(21.mpx)) },
            )
        }
    }
}

private val MenuSections = listOf(
    MainSection.HOME to OwnTVIcon.HOME,
    MainSection.LIVE_TV to OwnTVIcon.LIVE_TV,
    MainSection.SPORTS to OwnTVIcon.SPORTS,
    MainSection.EPG to OwnTVIcon.EPG,
    MainSection.MOVIES to OwnTVIcon.MOVIES,
    MainSection.SERIES to OwnTVIcon.SERIES,
    MainSection.DOWNLOADS to OwnTVIcon.DOWNLOADS,
)

internal val NavSize.labelRes: Int
    get() = when (this) {
        NavSize.COMPACT -> R.string.settings_nav_size_compact
        NavSize.NORMAL -> R.string.settings_nav_size_normal
        NavSize.WIDE -> R.string.settings_nav_size_wide
        NavSize.EXTRA_WIDE -> R.string.settings_nav_size_extra_wide
    }
