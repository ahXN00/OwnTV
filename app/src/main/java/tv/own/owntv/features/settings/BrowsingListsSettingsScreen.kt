package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * Settings › Layout › Browsing lists as a Stage page (eight switches are not a popup): what
 * each section remembers — its last category, then its last item — one switch per section.
 * The last section shows or hides the built-in Live TV categories (#322).
 */
@Composable
fun BrowsingListsSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val catLive by vm.rememberCategoryLive.collectAsStateWithLifecycle()
    val catMovies by vm.rememberCategoryMovies.collectAsStateWithLifecycle()
    val catSeries by vm.rememberCategorySeries.collectAsStateWithLifecycle()
    val itemLive by vm.rememberLastLive.collectAsStateWithLifecycle()
    val itemMovies by vm.rememberLastMovies.collectAsStateWithLifecycle()
    val itemSeries by vm.rememberLastSeries.collectAsStateWithLifecycle()
    val showAll by vm.liveShowAllRail.collectAsStateWithLifecycle()
    val showCatchup by vm.liveShowCatchupRail.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_layout)),
        title = stringResource(R.string.settings_browsing_lists),
        count = pluralStringResource(R.plurals.settings_setting_count, 8, 8),
        onBack = onBack,
        modifier = modifier,
    ) {
        val live = stringResource(R.string.settings_history_live)
        val movies = stringResource(R.string.settings_history_movies)
        val series = stringResource(R.string.settings_history_series)
        val cat = stringResource(R.string.settings_browsing_last_category)
        val catHelp = stringResource(R.string.settings_browsing_last_category_description)
        StageSettingsHeading(cat, 3, first = true)
        BrowsingSwitch(OwnTVIcon.LIVE_TV, live, cat, catHelp, catLive, Modifier.focusRequester(first)) { vm.setRememberCategoryLive(!catLive) }
        BrowsingSwitch(OwnTVIcon.MOVIES, movies, cat, catHelp, catMovies) { vm.setRememberCategoryMovies(!catMovies) }
        BrowsingSwitch(OwnTVIcon.SERIES, series, cat, catHelp, catSeries) { vm.setRememberCategorySeries(!catSeries) }
        val item = stringResource(R.string.settings_browsing_last_item)
        val itemHelp = stringResource(R.string.settings_browsing_last_item_description)
        StageSettingsHeading(item, 3)
        BrowsingSwitch(OwnTVIcon.LIVE_TV, live, item, itemHelp, itemLive) { vm.setRememberLastLive(!itemLive) }
        BrowsingSwitch(OwnTVIcon.MOVIES, movies, item, itemHelp, itemMovies) { vm.setRememberLastMovies(!itemMovies) }
        BrowsingSwitch(OwnTVIcon.SERIES, series, item, itemHelp, itemSeries) { vm.setRememberLastSeries(!itemSeries) }
        val liveCats = stringResource(R.string.settings_browsing_live_categories)
        val liveCatsHelp = stringResource(R.string.settings_browsing_live_categories_description)
        StageSettingsHeading(liveCats, 2)
        BrowsingSwitch(OwnTVIcon.CATCHUP, stringResource(R.string.content_catchup), liveCats, liveCatsHelp, showCatchup) { vm.setLiveShowCatchupRail(!showCatchup) }
        BrowsingSwitch(OwnTVIcon.LIVE_TV, stringResource(R.string.content_category_all_channels), liveCats, liveCatsHelp, showAll) { vm.setLiveShowAllRail(!showAll) }
    }
}

@Composable
private fun BrowsingSwitch(icon: OwnTVIcon, title: String, part: String, help: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val value = SettingValue.Switch(on)
    StageSettingRow(
        icon = icon,
        title = title,
        desc = null,
        value = value,
        onClick = onClick,
        // The panel names the part too: "Remember last category · Live TV".
        help = settingHelp(null, stringResource(R.string.settings_breadcrumb_eyebrow, part, title), help, value, pinnable = false),
        modifier = modifier,
    )
}
