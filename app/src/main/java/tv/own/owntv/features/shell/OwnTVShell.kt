package tv.own.owntv.features.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.features.update.UpdateDialog
import tv.own.owntv.features.update.UpdateStatusToast
import tv.own.owntv.features.downloads.DownloadsScreen
import tv.own.owntv.features.epg.EpgScreen
import tv.own.owntv.features.live.LiveScreen
import tv.own.owntv.features.movies.MoviesScreen
import tv.own.owntv.features.search.SearchScreen
import tv.own.owntv.features.series.SeriesScreen
import tv.own.owntv.player.MiniPlayer
import tv.own.owntv.player.MpvVideoSurface
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlayerHud
import tv.own.owntv.features.shell.components.AvatarPickerDialog
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.features.shell.components.ContentPane
import tv.own.owntv.features.shell.components.ExitDialog
import tv.own.owntv.features.shell.components.PreviewPane
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.features.shell.components.SettingsScreen
import tv.own.owntv.features.shell.components.Sidebar
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.ThemeMode

/** Which layer currently holds focus (drives Back navigation). */
private enum class ShellLayer { SIDEBAR, RAIL, CONTENT }

/** Player presentation: hidden, fullscreen, or docked mini-player over the browse UI. */
private enum class PlayerMode { NONE, FULLSCREEN, MINI }

/**
 * The MD3 shell: a fixed navigation panel (Layer 1) plus the active destination. Settings is a
 * single-pane sectioned screen; browse sections keep the Folder Rail → Content → Preview layout.
 */
@Composable
fun OwnTVShell(
    selectedSection: MainSection,
    onSelectSection: (MainSection) -> Unit,
    themeMode: ThemeMode,
    uiZoomPercent: Int,
    onSetZoom: (Int) -> Unit,
    avatarId: Int,
    onSetAvatar: (Int) -> Unit,
    profileName: String,
    sourceSummary: String,
    isOffline: Boolean = false,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val railSelection = remember { mutableStateMapOf<MainSection, Int>() }
    val selectedRail = railSelection[selectedSection] ?: 0
    val categories = railCategoriesFor(selectedSection)

    val sidebarFocus = remember { FocusRequester() }
    var focusedLayer by remember { mutableStateOf(ShellLayer.SIDEBAR) }
    var showExit by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var playerMode by remember { mutableStateOf(PlayerMode.NONE) }
    // Deep-link: the Guide's "Add EPG" button switches to Settings and opens EPG Sources → add.
    var openEpgAdd by remember { mutableStateOf(false) }
    // One-shot: set when leaving the player so the returning browse screen re-focuses the item you played.
    var restoreFocus by remember { mutableStateOf(false) }
    val player = koinInject<OwnTVPlayer>()
    // Same activity-scoped instance LiveScreen uses — lets the fullscreen HUD zap channels up/down.
    val liveVm = org.koin.androidx.compose.koinViewModel<tv.own.owntv.features.live.LiveViewModel>()
    val canZap by liveVm.canZap.collectAsStateWithLifecycle()

    // Opening content from a browse screen goes fullscreen — UNLESS the player is already docked as a
    // mini-player, in which case it stays docked and just swaps to the newly-selected stream (the VM
    // already started it), so picking a channel updates the PiP window in place (#6).
    val openFullscreen = { restoreFocus = false; if (playerMode != PlayerMode.MINI) playerMode = PlayerMode.FULLSCREEN }
    // The mini-player's own expand button always maximizes.
    val expandPlayer = { restoreFocus = false; playerMode = PlayerMode.FULLSCREEN }
    val exitPlayer = {
        playerMode = PlayerMode.NONE
        player.stop()
        restoreFocus = true
        runCatching { sidebarFocus.requestFocus() } // fallback if the screen has nothing to restore
        Unit
    }
    val dockPlayer = {
        playerMode = PlayerMode.MINI
        restoreFocus = true
        runCatching { sidebarFocus.requestFocus() }
        Unit
    }

    LaunchedEffect(Unit) { runCatching { sidebarFocus.requestFocus() } }

    // Stop a leftover live preview when you leave the Live section (but never while fullscreen/mini plays).
    LaunchedEffect(selectedSection, playerMode) {
        if (selectedSection != MainSection.LIVE_TV && playerMode == PlayerMode.NONE) player.stop()
    }

    BackHandler {
        when {
            playerMode == PlayerMode.FULLSCREEN -> exitPlayer()
            showAvatarPicker -> showAvatarPicker = false
            showExit -> showExit = false
            focusedLayer == ShellLayer.SIDEBAR -> showExit = true
            else -> runCatching { sidebarFocus.requestFocus() }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
      // Browse UI — hidden while the player is fullscreen (stays visible behind the docked mini-player).
      if (playerMode != PlayerMode.FULLSCREEN) {
        Column(modifier = Modifier.fillMaxSize()) {
          if (isOffline) OfflineBanner()
          Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Sidebar(
                selected = selectedSection,
                onSelect = onSelectSection,
                avatarId = avatarId,
                onPickAvatar = { showAvatarPicker = true },
                profileName = profileName,
                sourceSummary = sourceSummary,
                selectedItemFocusRequester = sidebarFocus,
                onFocused = { focusedLayer = ShellLayer.SIDEBAR },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(colors.surface),
            ) {
                when {
                    selectedSection == MainSection.SETTINGS -> SettingsScreen(
                        themeMode = themeMode,
                        uiZoomPercent = uiZoomPercent,
                        onSetZoom = onSetZoom,
                        onOpenPlaylist = { /* Phase 6: open setup/playlist */ },
                        openEpgAdd = openEpgAdd,
                        onEpgAddConsumed = { openEpgAdd = false },
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                            .focusGroup(),
                    )

                    selectedSection == MainSection.SEARCH -> SearchScreen(
                        onFullscreen = openFullscreen,
                        onOpenSeries = { onSelectSection(MainSection.SERIES) },
                        onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                        modifier = Modifier.fillMaxSize(),
                    )

                    selectedSection == MainSection.LIVE_TV -> LiveScreen(
                        onFullscreen = openFullscreen,
                        onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                        previewEnabled = playerMode == PlayerMode.NONE,
                        restoreFocus = restoreFocus,
                        onRestored = { restoreFocus = false },
                        modifier = Modifier.fillMaxSize(),
                    )

                    selectedSection == MainSection.MOVIES -> MoviesScreen(
                        onFullscreen = openFullscreen,
                        onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                        restoreFocus = restoreFocus,
                        onRestored = { restoreFocus = false },
                        modifier = Modifier.fillMaxSize(),
                    )

                    selectedSection == MainSection.SERIES -> SeriesScreen(
                        onFullscreen = openFullscreen,
                        onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                        restoreFocus = restoreFocus,
                        onRestored = { restoreFocus = false },
                        modifier = Modifier.fillMaxSize(),
                    )

                    selectedSection == MainSection.DOWNLOADS -> DownloadsScreen(
                        onFullscreen = openFullscreen,
                        onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                        restoreFocus = restoreFocus,
                        onRestored = { restoreFocus = false },
                        modifier = Modifier.fillMaxSize(),
                    )

                    selectedSection == MainSection.EPG -> EpgScreen(
                        onBack = { runCatching { sidebarFocus.requestFocus() } },
                        onFullscreen = openFullscreen,
                        onAddEpg = { openEpgAdd = true; onSelectSection(MainSection.SETTINGS) },
                        restoreFocus = restoreFocus,
                        onRestored = { restoreFocus = false },
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                            .focusGroup(),
                    )

                    else -> Row(modifier = Modifier.fillMaxSize()) {
                        CategoryRail(
                            categories = categories,
                            selectedIndex = selectedRail,
                            onSelect = { railSelection[selectedSection] = it },
                            onFocused = { focusedLayer = ShellLayer.RAIL },
                        )

                        ContentPane(
                            sectionTitle = selectedSection.label,
                            categoryName = categories.getOrNull(selectedRail)?.fullName ?: "All",
                            categoryAbbr = categories.getOrNull(selectedRail)?.abbr ?: "ALL",
                            countLabel = placeholderCount(selectedSection),
                            emptyIcon = selectedSection.emptyIcon,
                            emptyMessage = "Content for ${selectedSection.label} arrives in a later phase. Add an M3U or Xtream source to populate this list.",
                            onAddSource = { onSelectSection(MainSection.SETTINGS) },
                            modifier = Modifier
                                .weight(1.4f)
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(Dimens.GapLarge),
                        ) {
                            PreviewPane(hint = "Select a channel to preview it here.")
                        }
                    }
                }
            }
          }
        }
      }

      // Player surface — hoisted so it persists across fullscreen <-> mini (same call site = the
      // SurfaceView isn't recreated when docking/expanding, so playback never blips).
      if (playerMode != PlayerMode.NONE) {
        val isFull = playerMode == PlayerMode.FULLSCREEN
        Box(
            modifier = if (isFull) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                Modifier.align(Alignment.BottomEnd).padding(24.dp).size(width = 340.dp, height = 191.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color.Black)
            },
        ) {
            MpvVideoSurface(player = player, modifier = Modifier.fillMaxSize())
            // Direct render mode: mpv can't draw subtitles on the decoder-owned surface — the app does.
            if (isFull) tv.own.owntv.player.SubtitleOverlay(player = player, modifier = Modifier.fillMaxSize())
            if (isFull) {
                val liveZap = player.isLiveContent && canZap
                PlayerHud(
                    player = player,
                    onBack = exitPlayer,
                    onPip = dockPlayer, // PiP/dock now available for live too (#6)
                    onChannelUp = if (liveZap) ({ liveVm.zap(-1) }) else null,
                    onChannelDown = if (liveZap) ({ liveVm.zap(1) }) else null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MiniPlayer(player = player, onExpand = expandPlayer, onClose = exitPlayer, modifier = Modifier.fillMaxSize())
            }
        }
      }

        if (showExit) {
            ExitDialog(onConfirm = onExitApp, onDismiss = { showExit = false })
        }
        if (showAvatarPicker) {
            AvatarPickerDialog(
                selectedId = avatarId,
                onSelect = onSetAvatar,
                onDismiss = { showAvatarPicker = false },
            )
        }

        // Automatic update check (GitHub Releases) shortly after launch, once per session: a small
        // top-right status card shows "Checking… / up to date" (auto-hides) or stays with
        // Update now / Later when a release is newer. Hidden while in Settings (its manual
        // "Check for updates" dialog drives the same state machine) and during playback.
        val updateManager = koinInject<UpdateManager>()
        var showStartupToast by remember { mutableStateOf(false) }
        var showChangelog by remember { mutableStateOf(false) }
        val settingsRepo = koinInject<tv.own.owntv.features.settings.data.SettingsRepository>()
        val updateCheckOnStart by settingsRepo.updateCheckOnStart.collectAsStateWithLifecycle(initialValue = false)
        LaunchedEffect(updateCheckOnStart) {
            if (updateCheckOnStart && !showStartupToast) {
                kotlinx.coroutines.delay(5_000)
                showStartupToast = true
                updateManager.check()
            }
        }
        if (showChangelog) {
            // Full "What's New" changelog (same dialog the manual Settings check uses), shown when
            // the startup card's "What's New" is pressed. No re-check — the release is already loaded.
            UpdateDialog(onDismiss = { showChangelog = false; showStartupToast = false; updateManager.reset() }, checkOnOpen = false)
        } else if (showStartupToast && selectedSection != MainSection.SETTINGS && playerMode == PlayerMode.NONE) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                UpdateStatusToast(
                    onDone = { showStartupToast = false; updateManager.reset() },
                    onViewChangelog = { showChangelog = true },
                )
            }
        }
    }
}

/** A thin bar shown above the browse UI when the device loses internet. */
@Composable
private fun OfflineBanner() {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.tertiaryContainer)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "You're offline — playback and updates won't work until you reconnect.",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onTertiaryContainer,
        )
    }
}

private val MainSection.emptyIcon: OwnTVIcon
    get() = when (this) {
        MainSection.SEARCH -> OwnTVIcon.SEARCH
        MainSection.LIVE_TV -> OwnTVIcon.LIVE_TV
        MainSection.MOVIES -> OwnTVIcon.MOVIES
        MainSection.SERIES -> OwnTVIcon.SERIES
        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS
        MainSection.EPG -> OwnTVIcon.EPG
        MainSection.SETTINGS -> OwnTVIcon.SETTINGS
    }

private fun railCategoriesFor(section: MainSection): List<RailCategory> = when (section) {
    MainSection.SEARCH -> emptyList()
    MainSection.EPG -> emptyList()
    MainSection.LIVE_TV -> listOf(
        RailCategory("FAV", "Favorites"),
        RailCategory("HIS", "History"),
        RailCategory("ALL", "All Channels"),
        RailCategory("UK", "United Kingdom"),
        RailCategory("US", "United States"),
        RailCategory("DE", "Germany"),
        RailCategory("SPO", "Sports"),
    )
    MainSection.MOVIES -> listOf(
        RailCategory("FAV", "Favorites"),
        RailCategory("HIS", "History"),
        RailCategory("ALL", "All Movies"),
        RailCategory("ACT", "Action"),
        RailCategory("DRA", "Drama"),
        RailCategory("COM", "Comedy"),
        RailCategory("HOR", "Horror"),
    )
    MainSection.SERIES -> listOf(
        RailCategory("FAV", "Favorites"),
        RailCategory("HIS", "History"),
        RailCategory("ALL", "All Series"),
        RailCategory("DRA", "Drama"),
        RailCategory("ACT", "Action"),
        RailCategory("ANI", "Animation"),
        RailCategory("DOC", "Documentary"),
    )
    MainSection.DOWNLOADS -> listOf(
        RailCategory("ALL", "All Downloads"),
        RailCategory("MOV", "Movies"),
        RailCategory("SER", "Series"),
    )
    MainSection.SETTINGS -> emptyList()
}

private fun placeholderCount(section: MainSection): String = when (section) {
    MainSection.SEARCH -> ""
    MainSection.LIVE_TV -> "0 channels"
    MainSection.MOVIES -> "0 movies"
    MainSection.SERIES -> "0 series"
    MainSection.DOWNLOADS -> "0 downloads"
    MainSection.EPG -> ""
    MainSection.SETTINGS -> ""
}
