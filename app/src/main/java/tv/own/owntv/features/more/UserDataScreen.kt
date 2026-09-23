package tv.own.owntv.features.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.features.live.LiveScreen
import tv.own.owntv.features.movies.MoviesScreen
import tv.own.owntv.features.series.SeriesScreen
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.shell.components.ClearHistoryDialog
import tv.own.owntv.features.settings.data.BrowseContainerPadding
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme

/** Which of the three types the stage is showing. */
private enum class UserDataTab { LIVE, MOVIES, SERIES }

/**
 * **Favourites** and **Watch history** — the two screens the television never had.
 *
 * Until now there was a Favorites entry inside Live TV, another inside Movies and another inside
 * Series, and nothing that answered "what have I starred". This is it: three tabs over one stage,
 * the same three the phone uses, so the two apps stay one product.
 *
 * Under each tab is **the pane that type already has**, pinned to this folder — the channel list
 * with its now/next line, the Movies grid at the user's own fixed column count, the Series grid —
 * not a second copy of any of them. The context menus are theirs too, un-favourite included, so a
 * row leaves this list the moment the star does.
 *
 * A tab whose count is zero is still shown, with that type's own empty state: a user who has
 * starred only channels and series must see *why* Movies is empty rather than wonder whether it
 * broke.
 */
@Composable
fun FavoritesScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = UserDataScreen(
    key = LiveKey.Favorites,
    titleRes = R.string.content_category_favorites,
    onFullscreen = onFullscreen,
    onChildFocused = onChildFocused,
    previewEnabled = previewEnabled,
    onBack = onBack,
    modifier = modifier,
)

/**
 * [FavoritesScreen] over history instead of favourites, newest first — and the home Clear history
 * now has. Its scope dialog and its confirm are the ones that already shipped; only the door moved,
 * onto the screen it acts on.
 */
@Composable
fun HistoryScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = UserDataScreen(
    key = LiveKey.History,
    titleRes = R.string.content_category_history,
    onFullscreen = onFullscreen,
    onChildFocused = onChildFocused,
    previewEnabled = previewEnabled,
    onBack = onBack,
    modifier = modifier,
)

/**
 * The screen both of them are. The only differences are which folder is pinned and, for history,
 * the Clear button — so it is one implementation with one branch rather than two near-copies.
 */
@Composable
private fun UserDataScreen(
    key: LiveKey,
    titleRes: Int,
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    /** Whether the Live tab may run its in-pane preview. False while a film or channel plays docked or
     *  full screen: the preview would stop that player on focus, or open a second decoder beside it. */
    previewEnabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    counts: MoreCountsViewModel = koinViewModel(),
    settingsVm: SettingsViewModel = koinViewModel(),
) {
    val history = key == LiveKey.History
    var tab by rememberSaveable { mutableStateOf(UserDataTab.LIVE) }
    var showClear by remember { mutableStateOf(false) }
    val counted by (if (history) counts.history else counts.favorites).collectAsStateWithLifecycle()

    // Arriving lands on the tab strip rather than deep in the list: the first thing a remote wants
    // here is to choose a type.
    val tabFocus = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { tabFocus.requestFocus() } }
    // Closing the clear dialog puts focus back on the button that opened it — but only after it has
    // been open once, or arriving on the screen would land on Clear instead of the tab strip.
    var returnToClear by remember { mutableStateOf(false) }
    LaunchedEffect(showClear) {
        if (showClear) {
            returnToClear = true
        } else if (returnToClear) {
            returnToClear = false
            runCatching { clearFocus.requestFocus() }
        }
    }

    // Back leaves for More, exactly as Backup and Local sync do. Without it the shell's own handler
    // ran instead, which offers to leave the app — there was no way out of this screen at all.
    // Nested handlers still win where they should: inside a show, Back closes the show first.
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            // This screen owns the panel — the title, the tabs, Clear and the list are one box, the
            // same box every other More page is. The pinned pane below draws none of its own.
            .roundedPanel(fillColor = ContentPanelFill)
            .padding(BrowseContainerPadding)
            // And it owns the vertical trap the pinned pane gave up, so Up out of the list lands on
            // the tab strip and still cannot escape past it to the shell's top bar.
            .trapVerticalFocusExit()
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .focusGroup(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = OwnTVTheme.colors.onSurface,
                modifier = Modifier.padding(end = 10.dp),
            )
            UserDataTab.entries.forEach { entry ->
                OwnTVButton(
                    label = entry.label(counted),
                    onClick = { tab = entry },
                    style = if (entry == tab) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                    selected = entry == tab,
                    compact = true,
                    modifier = if (entry == UserDataTab.LIVE) Modifier.focusRequester(tabFocus) else Modifier,
                )
            }
            if (history) {
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    label = stringResource(R.string.settings_clear_history),
                    onClick = { showClear = true },
                    style = OwnTVButtonStyle.SECONDARY,
                    compact = true,
                    modifier = Modifier.focusRequester(clearFocus),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when (tab) {
            UserDataTab.LIVE -> LiveScreen(
                onFullscreen = onFullscreen,
                onChildFocused = onChildFocused,
                lockedKey = key,
                previewEnabled = previewEnabled,
                modifier = Modifier.fillMaxSize(),
            )
            UserDataTab.MOVIES -> MoviesScreen(
                onFullscreen = onFullscreen,
                onChildFocused = onChildFocused,
                lockedKey = key,
                modifier = Modifier.fillMaxSize(),
            )
            UserDataTab.SERIES -> SeriesScreen(
                onFullscreen = onFullscreen,
                onChildFocused = onChildFocused,
                lockedKey = key,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showClear) {
        OwnTVPopup(onDismissRequest = { showClear = false }) {
            ClearHistoryDialog(
                onClear = { type -> settingsVm.clearWatchHistory(type); showClear = false },
                onDismiss = { showClear = false },
            )
        }
    }
}

/** "Live TV · 28" — the type's own name and how many of it there are. */
@Composable
private fun UserDataTab.label(counts: TypeCounts): String {
    val name = stringResource(
        when (this) {
            UserDataTab.LIVE -> R.string.common_nav_live_tv
            UserDataTab.MOVIES -> R.string.common_nav_movies
            UserDataTab.SERIES -> R.string.common_nav_series
        },
    )
    val count = when (this) {
        UserDataTab.LIVE -> counts.live
        UserDataTab.MOVIES -> counts.movies
        UserDataTab.SERIES -> counts.series
    }
    // The app's own "a · b" joiner, so one separator serves every screen and cannot drift.
    return name + stringResource(R.string.content_epg_bits_separator) + count
}
