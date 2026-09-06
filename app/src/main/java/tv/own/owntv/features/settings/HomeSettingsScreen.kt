package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.model.HeroKind
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.model.HomeTrendingStyle
import tv.own.owntv.core.trending.TrendingAvailability
import tv.own.owntv.features.home.displayTitle
import tv.own.owntv.features.home.displayLabel
import tv.own.owntv.features.home.settingsDescription
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme

@Composable
fun HomeSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: HomeSettingsViewModel = koinViewModel()
    val settingsVm: SettingsViewModel = koinViewModel()
    val config by vm.config.collectAsStateWithLifecycle()
    val trendingAvailability by vm.trendingAvailability.collectAsStateWithLifecycle()
    val devRebuild by vm.devRebuild.collectAsStateWithLifecycle()
    val heroPreviewEnabled by vm.heroPreviewEnabled.collectAsStateWithLifecycle()
    val androidTvHomeEnabled by settingsVm.androidTvHomeEnabled.collectAsStateWithLifecycle()
    val tvHomeRefresh by settingsVm.tvHomeRefresh.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    val trendingEnabled = HomeRow.TRENDING !in config.hidden
    val trendingDescription = HomeRow.TRENDING.settingsDescription()
    val trendingStatus = trendingStatusText(hidden = !trendingEnabled, availability = trendingAvailability)

    val firstFocus = remember { FocusRequester() }
    // onEnter alone can miss when entering this screen: the first row lives inside a LazyColumn and may
    // not be composed/attached the instant focus crosses in, so focus falls back to the sidebar. Request
    // it once after first layout (matches VideoPlayerSettingsScreen); onEnter still covers dialog returns.
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }

    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .focusProperties {
                onEnter = { runCatching { firstFocus.requestFocus() } }
            }
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text(stringResource(R.string.settings_home_screen), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_home_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            // Pin vertical focus inside the section list — a held Up/Down that outruns composition
            // would otherwise escape to the header / sidebar (every other browse list traps this).
            modifier = Modifier.weight(1f).fillMaxWidth().trapVerticalFocusExit(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                GroupLabel(stringResource(R.string.home_row_now_trending))
            }

            item {
                Row2(
                    icon = OwnTVIcon.STAR,
                    title = stringResource(R.string.home_row_now_trending),
                    desc = "$trendingDescription\n$trendingStatus",
                    chip = stringResource(if (trendingEnabled) R.string.common_on else R.string.common_off),
                    primaryChip = trendingEnabled,
                    modifier = Modifier.focusRequester(firstFocus),
                    onClick = { vm.setRowHidden(HomeRow.TRENDING, trendingEnabled) },
                )
            }

            // Only while the row is actually on — with Trending off there is nothing for the choice
            // to apply to, so it is not shown at all.
            if (trendingEnabled) {
                item {
                    Row2(
                        icon = OwnTVIcon.MOVIES,
                        title = stringResource(R.string.home_trending_style),
                        desc = stringResource(R.string.home_row_trending_description),
                        chip = stringResource(
                            when (config.trendingStyle) {
                                HomeTrendingStyle.HERO -> R.string.home_trending_style_hero
                                HomeTrendingStyle.POSTERS -> R.string.home_trending_style_posters
                            },
                        ),
                        primaryChip = true,
                        onClick = {
                            vm.setTrendingStyle(
                                when (config.trendingStyle) {
                                    HomeTrendingStyle.HERO -> HomeTrendingStyle.POSTERS
                                    HomeTrendingStyle.POSTERS -> HomeTrendingStyle.HERO
                                },
                            )
                        },
                    )
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.settings_sections), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_hidden_sections),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }

            itemsIndexed(config.settingsRows, key = { _, row -> row.name }) { index, row ->
                HomeRowCard(
                    row = row,
                    hidden = row in config.hidden,
                    canMoveUp = index > 0,
                    canMoveDown = index < config.settingsRows.lastIndex,
                    onMoveUp = { vm.move(row, up = true) },
                    onMoveDown = { vm.move(row, up = false) },
                    onMoveTop = { vm.moveToEdge(row, top = true) },
                    onMoveBottom = { vm.moveToEdge(row, top = false) },
                    onToggleHidden = { vm.setRowHidden(row, row !in config.hidden) },
                    liveMode = when (row) {
                        HomeRow.RECENT_CHANNELS -> config.recentLiveMode
                        HomeRow.FAVORITE_CHANNELS -> config.favoriteLiveMode
                        else -> null
                    },
                    onToggleLiveMode = { mode -> vm.setLiveRowMode(row, mode.toggled()) },
                )
            }

            // Maintainer-only. BuildConfig.DEV_TOOLS is a compile-time constant that is false in every
            // published APK, so R8 removes this row (and the view-model call behind it) entirely.
            if (BuildConfig.DEV_TOOLS) {
                item {
                    Spacer(Modifier.height(14.dp))
                    GroupLabel("Developer")
                }
                item {
                    Row2(
                        icon = OwnTVIcon.SHARE,
                        title = "Rebuild Now Trending",
                        desc = "Forces a fresh TMDB trending download for every playlist, ignoring the multi-day fetch timer.",
                        chip = when (devRebuild) {
                            HomeSettingsViewModel.DevRebuildState.STARTED -> stringResource(R.string.settings_rebuilding)
                            else -> null
                        },
                        onClick = { vm.rebuildTrendingNow() },
                    )
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
                GroupLabel(stringResource(R.string.settings_keep_watching))
            }

            item {
                Row2(
                    icon = OwnTVIcon.LIVE_TV,
                    title = stringResource(R.string.settings_live_keep_watching),
                    desc = stringResource(R.string.settings_live_keep_watching_description),
                    chip = if (config.heroIncludeLive) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                    primaryChip = config.heroIncludeLive,
                    onClick = { vm.setHeroInclude(HeroKind.LIVE, !config.heroIncludeLive) },
                )
            }
            item {
                Row2(
                    icon = OwnTVIcon.MOVIES,
                    title = stringResource(R.string.settings_movies_keep_watching),
                    desc = stringResource(R.string.settings_movies_keep_watching_description),
                    chip = if (config.heroIncludeMovies) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                    primaryChip = config.heroIncludeMovies,
                    onClick = { vm.setHeroInclude(HeroKind.MOVIES, !config.heroIncludeMovies) },
                )
            }
            item {
                Row2(
                    icon = OwnTVIcon.SERIES,
                    title = stringResource(R.string.settings_series_keep_watching),
                    desc = stringResource(R.string.settings_series_keep_watching_description),
                    chip = if (config.heroIncludeSeries) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                    primaryChip = config.heroIncludeSeries,
                    onClick = { vm.setHeroInclude(HeroKind.SERIES, !config.heroIncludeSeries) },
                )
            }
            item {
                Row2(
                    icon = OwnTVIcon.PLAY,
                    title = stringResource(R.string.settings_hero_preview),
                    desc = stringResource(R.string.settings_hero_preview_description),
                    chip = if (heroPreviewEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                    primaryChip = heroPreviewEnabled,
                    onClick = { vm.setHeroPreviewEnabled(!heroPreviewEnabled) },
                )
            }

            item {
                Spacer(Modifier.height(6.dp))
                GroupLabel(stringResource(R.string.settings_android_tv_home))
            }
            item {
                Row2(
                    icon = OwnTVIcon.HISTORY,
                    title = stringResource(R.string.settings_android_tv_home),
                    desc = stringResource(R.string.settings_android_tv_home_description),
                    chip = if (androidTvHomeEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                    primaryChip = androidTvHomeEnabled,
                    onClick = { settingsVm.setAndroidTvHomeEnabled(!androidTvHomeEnabled) },
                )
            }
            if (androidTvHomeEnabled) {
                item {
                    Row2(
                        icon = OwnTVIcon.REFRESH,
                        title = stringResource(R.string.settings_refresh_now),
                        desc = stringResource(R.string.settings_refresh_description),
                        chip = when (tvHomeRefresh) {
                            SettingsViewModel.TvHomeRefresh.REFRESHING -> stringResource(R.string.settings_rebuilding)
                            SettingsViewModel.TvHomeRefresh.DONE -> stringResource(R.string.settings_done_check)
                            else -> null
                        },
                        onClick = {
                            if (tvHomeRefresh == SettingsViewModel.TvHomeRefresh.IDLE) {
                                settingsVm.refreshAndroidTvHome()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRowCard(
    row: HomeRow,
    hidden: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onToggleHidden: () -> Unit,
    liveMode: HomeLiveRowMode?,
    onToggleLiveMode: (HomeLiveRowMode) -> Unit,
) {
    val colors = OwnTVTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.displayTitle(),
                style = MaterialTheme.typography.titleSmall,
                color = if (hidden) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hidden) {
                Text(
                    stringResource(R.string.settings_hidden),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (liveMode != null) {
            OwnTVButton(
                label = stringResource(R.string.settings_mode, liveMode.displayLabel()),
                onClick = { onToggleLiveMode(liveMode) },
                style = OwnTVButtonStyle.SECONDARY,
            )
            Spacer(Modifier.width(6.dp))
        }
        OwnTVButton("⤒", onClick = onMoveTop, style = OwnTVButtonStyle.SECONDARY, enabled = canMoveUp)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("↑", onClick = onMoveUp, style = OwnTVButtonStyle.SECONDARY, enabled = canMoveUp)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("↓", onClick = onMoveDown, style = OwnTVButtonStyle.SECONDARY, enabled = canMoveDown)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("⤓", onClick = onMoveBottom, style = OwnTVButtonStyle.SECONDARY, enabled = canMoveDown)
        Spacer(Modifier.width(6.dp))
        OwnTVButton(
            label = stringResource(if (hidden) R.string.common_show else R.string.common_hide),
            onClick = onToggleHidden,
            style = OwnTVButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun trendingStatusText(hidden: Boolean, availability: TrendingAvailability): String = when {
    hidden -> stringResource(R.string.settings_trending_status_off)
    availability == TrendingAvailability.Building -> stringResource(R.string.settings_trending_status_building)
    availability == TrendingAvailability.MetadataDisabled -> stringResource(R.string.settings_trending_status_metadata_disabled)
    availability == TrendingAvailability.NoVodScope -> stringResource(R.string.settings_trending_status_no_vod)
    availability == TrendingAvailability.Failed -> stringResource(R.string.settings_trending_status_failed)
    availability == TrendingAvailability.WaitingForSync -> stringResource(R.string.settings_trending_status_waiting)
    availability is TrendingAvailability.BelowThreshold && availability.matched == 0 -> stringResource(
        R.string.settings_trending_status_no_matches,
    )
    availability is TrendingAvailability.BelowThreshold -> pluralStringResource(
        R.plurals.settings_trending_status_below_threshold,
        availability.matched,
        availability.matched,
    )
    availability is TrendingAvailability.Showing && availability.refreshFailed -> pluralStringResource(
        R.plurals.settings_trending_status_showing_refresh_failed,
        availability.count,
        availability.count,
    )
    availability is TrendingAvailability.Showing -> pluralStringResource(
        R.plurals.settings_trending_status_showing,
        availability.count,
        availability.count,
    )
    else -> stringResource(R.string.settings_trending_status_waiting)
}
