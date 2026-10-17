package tv.own.owntv.features.shell.components

import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.labelRes
import tv.own.owntv.features.settings.AccentSwatches
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.StageSettingsPage
import tv.own.owntv.features.settings.StageSettingsHeading
import tv.own.owntv.features.settings.StageSettingsNote
import tv.own.owntv.features.settings.StageSearchResultRow
import tv.own.owntv.features.settings.VideoGroup
import tv.own.owntv.features.settings.VideoPlayerGroupRows
import tv.own.owntv.features.settings.settingHelp
import tv.own.owntv.features.settings.videoGroupCount
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.customize.CustomizeScreen
import tv.own.owntv.core.i18n.SupportedLocales
import tv.own.owntv.core.player.SurroundMode
import tv.own.owntv.features.settings.LocalSyncScreen
import tv.own.owntv.features.settings.HomeSettingsScreen
import tv.own.owntv.features.settings.NO_ARGS
import tv.own.owntv.features.settings.LanguageSettingsScreen
import tv.own.owntv.features.settings.LanguageSettingsViewModel
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.update.UpdateDialog
import tv.own.owntv.features.settings.BackupScreen
import tv.own.owntv.features.settings.ManageSourcesScreen
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.BackgroundImageChooserDialog
import tv.own.owntv.ui.components.ingestBackgroundImage
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.core.theme.GlassConfig
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.PopupSizeScale
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.UiZoom
import kotlin.math.roundToInt
import java.io.File
import java.util.Locale
import tv.own.owntv.ui.theme.labelRes
import tv.own.owntv.ui.theme.primary

internal enum class TileTone { PRIMARY, SECONDARY, TERTIARY }

private enum class SettingsTab { ROOT, LANGUAGE, SOURCES, EPG, BACKUP, LOCAL_SYNC, CUSTOMIZE, HOME, NETWORK, DNS, METADATA, OPEN_SUBTITLES, WEATHER, CH_NAV, PANEL_WIDTH, GUIDE_WIDTH, GLASS_EFFECT, CONTENT_MENUS, FONTS, BROWSING, SUBTITLE_STYLE, SPORTS }

@Composable
internal fun surroundModeLabel(mode: SurroundMode): String = stringResource(
    when (mode) {
        SurroundMode.AUTO -> R.string.settings_auto
        SurroundMode.STEREO -> R.string.settings_surround_stereo
        SurroundMode.SURROUND -> R.string.settings_surround_sound
    },
)

@Composable
private fun epgShiftLabel(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.common_off)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.US
    val number = java.text.NumberFormat.getIntegerInstance(locale)
    val sign = if (minutes < 0) "−" else "+"
    val absolute = kotlin.math.abs(minutes)
    val hours = absolute / 60
    val remainder = absolute % 60
    return when {
        hours == 0 -> stringResource(R.string.content_epg_shift_minutes, sign, number.format(remainder))
        remainder == 0 -> stringResource(R.string.content_epg_shift_hours, sign, number.format(hours))
        else -> stringResource(
            R.string.content_epg_shift_hours_minutes,
            sign,
            number.format(hours),
            number.format(remainder),
        )
    }
}

/**
 * The MD3 Settings screen (shown when [MainSection.SETTINGS] is active): grouped sections, each row
 * a tonal icon tile + title/description + a trailing chip or chevron. Theme / UI Zoom are live;
 * unfinished features show a "Soon" chip.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    uiZoomPercent: Int,
    onSetZoom: (Int) -> Unit,
    fontCustomization: FontCustomization,
    onSetFontCustomization: (FontCustomization) -> Unit,
    onOpenPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Where Back from the **root** of this screen goes. Settings sits behind More, so leaving
     * it should land on More rather than throw focus all the way out to the rail. Null keeps the old
     * behaviour, which is the shell's own handler.
     */
    onBack: (() -> Unit)? = null,
    openEpgAdd: Boolean = false,
    onEpgAddConsumed: () -> Unit = {},
    /** More › Settings: open at a root group, or with search open. */
    start: SettingsStart? = null,
    onStartConsumed: () -> Unit = {},
) {
    // A cross-script language change recreates the Activity so Android can apply the new script's
    // shaping and font fallback. Keep the open settings sub-screen across that configuration change
    // instead of dropping back to the Settings root/sidebar.
    var tab by rememberSaveable { mutableStateOf(SettingsTab.ROOT) }
    // Deep-link from the Guide's "Add EPG" button: jump straight to EPG Sources in add mode.
    var consumeEpgAdd by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var showAppIcon by remember { mutableStateOf(false) }
    var showPopupSize by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    var showFocusHighlight by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showCatchupTime by remember { mutableStateOf(false) }
    // Per-playlist catch-up time zone: the playlist list, then the value picker for [catchupSource].
    var showCatchupSources by remember { mutableStateOf(false) }
    var showCatchupSourceValue by remember { mutableStateOf(false) }
    var catchupSource by remember { mutableStateOf<tv.own.owntv.core.database.entity.SourceEntity?>(null) }
    var showEpgOffset by remember { mutableStateOf(false) }
    var showGuideDays by remember { mutableStateOf(false) }
    var showAnimations by remember { mutableStateOf(false) }
    var showVodLayout by remember { mutableStateOf(false) }
    var showNavigation by remember { mutableStateOf(false) }
    var showLiveLayout by remember { mutableStateOf(false) }
    // The three Stage pickers (Live TV opens in, Programme reminders, Reminder time): their state, rows,
    // search entries and dialogs live outside this function, which sits at the JVM's 64 KB method limit.
    val stageSettings = remember { StageSettingsState() }
    var showStartup by remember { mutableStateOf(false) }
    var showStartupChannelPicker by remember { mutableStateOf(false) }
    var showAfrWarning by remember { mutableStateOf(false) }
    var showLivePreviewPanelWarning by remember { mutableStateOf(false) }
    var showBgImageChooser by remember { mutableStateOf(false) }
    var showBgPicker by remember { mutableStateOf(false) }
    var showBgRemote by remember { mutableStateOf(false) }
    var showAmbientGlow by remember { mutableStateOf(false) }
    // Background-image ingest copies a multi-megabyte file; it runs here, off the main thread.
    val ingestScope = rememberCoroutineScope()

    // Batch 4 · Settings search + quick toggles. Empty query = normal grouped list; a non-blank
    // query swaps the list for flat results that carry their group context ("Playback › HDR").
    var searchQuery by remember { mutableStateOf("") }
    // Search opened (from More, or a full page's search): the plain search page, even while the query
    // is still empty — not the group that happens to be selected behind it.
    var searchMode by remember { mutableStateOf(false) }
    var searchFromMore by remember { mutableStateOf(false) }
    val searchFieldFocus = remember { FocusRequester() }
    // While searching, Back clears the query (and returns focus to the field) instead of leaving Settings.
    BackHandler(enabled = tab == SettingsTab.ROOT && searchQuery.isNotBlank()) {
        searchQuery = ""
        runCatching { searchFieldFocus.requestFocus() }
    }

    // Dialog-close focus return: closing a dialog/picker refocuses the row that opened it (focus
    // would otherwise fall spatially back to the sidebar).
    val themeRowFocus = remember { FocusRequester() }
    val accentRowFocus = remember { FocusRequester() }
    val focusHighlightRowFocus = remember { FocusRequester() }
    val zoomRowFocus = remember { FocusRequester() }
    val appIconRowFocus = remember { FocusRequester() }
    val popupSizeRowFocus = remember { FocusRequester() }
    val updateRowFocus = remember { FocusRequester() }
    val catchupRowFocus = remember { FocusRequester() }
    val catchupSourcesRowFocus = remember { FocusRequester() }
    val epgOffsetRowFocus = remember { FocusRequester() }
    val guideDaysRowFocus = remember { FocusRequester() }
    val animationsRowFocus = remember { FocusRequester() }
    val vodLayoutRowFocus = remember { FocusRequester() }
    val liveLayoutRowFocus = remember { FocusRequester() }
    val navigationRowFocus = remember { FocusRequester() }
    val startupRowFocus = remember { FocusRequester() }
    val livePreviewQuickFocus = remember { FocusRequester() }
    val ambientGlowRowFocus = remember { FocusRequester() }
    // Hoisted list state for the root settings list. We snapshot its position the instant a row is
    // clicked (in onClick, before any recomposition) and restore it on dialog close, so the list
    // doesn't visibly jump/scroll when the dialog opens or when we refocus the opener row afterward.
    // A lazy list carries its position as item index + offset into that item, so both are saved.
    val pageScroll = rememberScrollState()
    // These belong to the two-pane Settings root, but stay remembered while a detail screen replaces
    // it. Otherwise Back briefly rebuilds Quick at row zero before restoring the real group/row.
    // Start on the card More opened, not Quick: waiting for the start effect drew Quick for a few frames first.
    var selectedGroup by rememberSaveable { mutableIntStateOf(start?.group ?: 0) }
    var displayedGroup by rememberSaveable { mutableIntStateOf(start?.group ?: 0) }
    var savedScrollPx by remember { mutableIntStateOf(0) }
    val saveScroll = { savedScrollPx = pageScroll.value }
    // The rows column: fresh entry and a lost focus land here, on the page's first row.
    val rowsFocus = remember { FocusRequester() }
    // Opens the search field's keyboard as it appears, so one OK reaches typing (More's search box too).
    var searchAutoEdit by remember { mutableStateOf(false) }
    LaunchedEffect(searchAutoEdit) { if (searchAutoEdit) { kotlinx.coroutines.delay(1000); searchAutoEdit = false } }
    LaunchedEffect(start) {
        if (start == null) return@LaunchedEffect
        tab = SettingsTab.ROOT
        start.group?.let { selectedGroup = it; displayedGroup = it }
        if (start.search) {
            searchMode = true
            searchFromMore = true
            searchAutoEdit = true
            for (attempt in 0 until 10) { kotlinx.coroutines.delay(50); if (runCatching { searchFieldFocus.requestFocus() }.getOrDefault(false)) break }
        }
        onStartConsumed()
    }
    val anyDialogOpen = showZoom || showPopupSize || showTheme || showAccent || showUpdate || showCatchupTime || showCatchupSources || showCatchupSourceValue || showEpgOffset || showGuideDays || showAnimations || showStartup || showStartupChannelPicker || showAfrWarning || showLivePreviewPanelWarning || showBgImageChooser || showBgPicker || showAmbientGlow || showFocusHighlight || showBgRemote || showVodLayout || showNavigation || showLiveLayout || stageSettings.open != null
    // When a dialog closes, restore focus to the row that opened it. NOTE: this restore crosses
    // INTO the root focus group from outside (the dialog), but onEnter does NOT fire for programmatic
    // requestsFocus (only for directional entry) — so dialogReturn must be cleared HERE, not in onEnter.
    // If it's left set, the next directional entry (e.g. sidebar→here) would re-route to a stale row.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(showZoom, showPopupSize, showTheme, showAccent, showUpdate, showCatchupTime, showCatchupSources, showCatchupSourceValue, showEpgOffset, showGuideDays, showAnimations, showStartup, showStartupChannelPicker, showAfrWarning, showLivePreviewPanelWarning, showBgImageChooser, showBgPicker, showAmbientGlow, showFocusHighlight, showBgRemote, showVodLayout, showNavigation, showLiveLayout, stageSettings.open) {
        // Only after a popup really closed: on first composition there is no opener, and holding a stale
        // offset for the settle frames bounced the page.
        if (!anyDialogOpen && dialogReturn != null) {
            // Focus back on the opener row, with the scroll offset held still the whole way — see
            // [restoreAfterDialogClose] for why doing those two in sequence made the highlight travel.
            tv.own.owntv.ui.components.restoreAfterDialogClose(dialogReturn, pageScroll, savedScrollPx)
            dialogReturn = null
        }
    }
    val settingsVm: SettingsViewModel = koinViewModel()
    val appIcon by settingsVm.appIcon.collectAsStateWithLifecycle()
    val brandAccent by settingsVm.brandAccentTriangle.collectAsStateWithLifecycle()
    val languageVm: LanguageSettingsViewModel = koinViewModel()
    val currentLocaleTag by languageVm.currentTag.collectAsStateWithLifecycle()
    val languageChip = languageChipText(currentLocaleTag)
    val livePreview by settingsVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val livePreviewPanelActive by settingsVm.livePreviewPanelActive.collectAsStateWithLifecycle()
    val previewAudio by settingsVm.livePreviewAudio.collectAsStateWithLifecycle()
    val hdr by settingsVm.hdrEnabled.collectAsStateWithLifecycle()
    val autoFrameRate by settingsVm.autoFrameRate.collectAsStateWithLifecycle()
    val surroundMode by settingsVm.surroundMode.collectAsStateWithLifecycle()
    val autoPlayNext by settingsVm.autoPlayNext.collectAsStateWithLifecycle()
    val updateCheckOnStart by settingsVm.updateCheckOnStart.collectAsStateWithLifecycle()
    val channelNumbers by settingsVm.directTune.collectAsStateWithLifecycle()
    val quickPinned by settingsVm.quickPinnedKeys.collectAsStateWithLifecycle()
    val catchupTz by settingsVm.catchupTimezone.collectAsStateWithLifecycle()
    val catchupOffset by settingsVm.catchupOffsetMinutes.collectAsStateWithLifecycle()
    val epgOffset by settingsVm.epgOffsetMinutes.collectAsStateWithLifecycle()
    val guideDays by settingsVm.guideDaysToKeep.collectAsStateWithLifecycle()
    val catchupChannels by settingsVm.catchupChannelCount.collectAsStateWithLifecycle()
    val catchupPlayer by settingsVm.catchupPlayer.collectAsStateWithLifecycle()
    val playlistSources by settingsVm.sources.collectAsStateWithLifecycle()
    val catchupOverrides = playlistSources.count { it.catchupTimezone != null }
    val accent by settingsVm.accent.collectAsStateWithLifecycle()
    val customAccent by settingsVm.customAccent.collectAsStateWithLifecycle()
    val focusHighlight by settingsVm.focusHighlight.collectAsStateWithLifecycle()
    val focusHighlightWidth by settingsVm.focusHighlightWidth.collectAsStateWithLifecycle()
    val bgImagePath by settingsVm.bgImagePath.collectAsStateWithLifecycle()
    val glassConfig by settingsVm.glassConfig.collectAsStateWithLifecycle()
    val glassOn = glassConfig.enabled
    val animationLevel by settingsVm.animationLevel.collectAsStateWithLifecycle()
    val vodLayout by settingsVm.vodLayout.collectAsStateWithLifecycle()
    val ambientGlowEnabled by settingsVm.ambientGlowEnabled.collectAsStateWithLifecycle()
    val ambientGlowPulse by settingsVm.ambientGlowPulse.collectAsStateWithLifecycle()
    LaunchedEffect(glassOn, themeMode) {
        if (glassOn || themeMode != ThemeMode.DARK) showAmbientGlow = false
    }
    val weatherEnabled by settingsVm.weatherEnabled.collectAsStateWithLifecycle()
    val startupMode by settingsVm.startupMode.collectAsStateWithLifecycle()
    val autoStart = rememberAutoStartSettingsState()
    val startupChannel by settingsVm.startupChannel.collectAsStateWithLifecycle()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val rememberLastLive by settingsVm.rememberLastLive.collectAsStateWithLifecycle()
    val rememberLastMovies by settingsVm.rememberLastMovies.collectAsStateWithLifecycle()
    val rememberLastSeries by settingsVm.rememberLastSeries.collectAsStateWithLifecycle()
    // "Custom" on the Panel Width row as soon as any one of the three sections is switched on.
    val panelWidthLive by settingsVm.panelWidthEnabled.getValue(tv.own.owntv.core.settings.PanelSection.LIVE).collectAsStateWithLifecycle()
    val panelWidthMovies by settingsVm.panelWidthEnabled.getValue(tv.own.owntv.core.settings.PanelSection.MOVIES).collectAsStateWithLifecycle()
    val panelWidthSeries by settingsVm.panelWidthEnabled.getValue(tv.own.owntv.core.settings.PanelSection.SERIES).collectAsStateWithLifecycle()
    val panelWidthCustom = panelWidthLive || panelWidthMovies || panelWidthSeries
    val guideWidthCustom by settingsVm.guideWidthEnabled.collectAsStateWithLifecycle()

    // Auto frame rate is the one toggle that can make the picture visibly worse on the wrong hardware:
    // below Android 12 there is no way to ask the display which refresh rates it can reach without
    // blanking. Turning it on there therefore asks first; turning it off remains immediate.
    val afrNeedsWarning = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
    val toggleAutoFrameRate: (FocusRequester) -> Unit = { returnFocus ->
        if (!autoFrameRate && afrNeedsWarning) {
            saveScroll()
            dialogReturn = returnFocus
            showAfrWarning = true
        } else {
            settingsVm.setAutoFrameRate(!autoFrameRate)
        }
    }
    val toggleLivePreview: (FocusRequester) -> Unit = { returnFocus ->
        if (livePreview) {
            settingsVm.setLivePreviewEnabled(false)
        } else if (!livePreviewPanelActive) {
            saveScroll()
            dialogReturn = returnFocus
            showLivePreviewPanelWarning = true
        } else {
            settingsVm.setLivePreviewEnabled(true)
        }
    }

    // Restore focus to the row a sub-screen was opened from when the user navigates back.
    var lastTab by rememberSaveable { mutableStateOf<SettingsTab?>(null) }
    // The Sports hub's Settings button: straight to Settings → Sports; Back lands on its row. After the
    // start effect above (which resets to the root), so this one wins.
    // Built from the enum, not hand-listed. It used to be a literal map of twenty entries, and
    // adding SettingsTab.RECORDING without adding a line to it crashed the whole Settings screen
    // with "Key RECORDING is missing in the map" — a `getValue` on a map that had quietly gone
    // stale. From the entries there is nothing to keep in step. ROOT gets one it never uses, which
    // is cheaper than a list that can be wrong.
    val rowFocus = remember { SettingsTab.entries.associateWith { FocusRequester() } }
    // Mini player is a popup on the Watching & recording page, not a screen of its own. The settings
    // search still lists it by name, so it needs a way to say "open that popup on arrival".
    var openMiniPlayer by rememberSaveable { mutableStateOf(false) }
    // A Quick pin or a search result that jumps to a Video player row: the row to focus on its page,
    // and — for the way back — the Quick row it came from.
    var videoRowKey by rememberSaveable { mutableStateOf<String?>(null) }
    var deepReturnKey by rememberSaveable { mutableStateOf<String?>(null) }
    val deepRowFocus = remember { FocusRequester() }
    val jumpVideo: (String, Boolean) -> Unit = { key, fromQuick ->
        val ref = tv.own.owntv.features.settings.VIDEO_QUICK_ROWS.first { it.key == key }
        deepReturnKey = if (fromQuick) key else null
        videoRowKey = key
        searchQuery = ""
        selectedGroup = SettingsGroup.of(tv.own.owntv.features.settings.videoGroupOf(ref.section)).ordinal
    }
    // Back from a page reached through a Quick pin returns to that pin on Quick.
    BackHandler(enabled = tab == SettingsTab.ROOT && deepReturnKey != null && selectedGroup != 0 && searchQuery.isBlank()) {
        videoRowKey = null
        openMiniPlayer = false
        selectedGroup = 0
    }
    // Opening a sub-screen the ordinary way cancels any pending Quick-shortcut return, or the Back
    // from it would aim at the shortcut instead of the row just used.
    val open: (SettingsTab) -> Unit = { lastTab = it; deepReturnKey = null; videoRowKey = null; tab = it }
    LaunchedEffect(openEpgAdd) {
        if (openEpgAdd) { consumeEpgAdd = true; open(SettingsTab.EPG); onEpgAddConsumed() }
    }

    // Sub-screens sit below the status cluster (StageContentTop), 6 dp in from the edges.
    val sub = modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp, top = StageContentTop)
    // A full page's search hands over to Settings search, keyboard up.
    val openSearch: () -> Unit = { lastTab = null; searchQuery = ""; searchMode = true; searchFromMore = false; searchAutoEdit = true; tab = SettingsTab.ROOT }
    if (tab != SettingsTab.ROOT) {
    CompositionLocalProvider(tv.own.owntv.features.settings.LocalSettingsSearch provides openSearch) {
    when (tab) {
        SettingsTab.LANGUAGE -> { LanguageSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
        SettingsTab.SOURCES -> { ManageSourcesScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
        SettingsTab.EPG -> { tv.own.owntv.features.settings.EpgSourcesScreen(onBack = { tab = SettingsTab.ROOT; consumeEpgAdd = false }, modifier = modifier, startOnAdd = consumeEpgAdd) }
        SettingsTab.BACKUP -> { BackupScreen(onBack = { tab = SettingsTab.ROOT }, modifier = sub) }
        SettingsTab.LOCAL_SYNC -> { LocalSyncScreen(onBack = { tab = SettingsTab.ROOT }, modifier = sub) }
        SettingsTab.CUSTOMIZE -> { CustomizeScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
        SettingsTab.HOME -> { HomeSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
        SettingsTab.NETWORK -> { tv.own.owntv.features.settings.NetworkSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
        SettingsTab.DNS -> { tv.own.owntv.features.settings.DnsSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
        SettingsTab.METADATA -> { tv.own.owntv.features.settings.MetadataSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
        SettingsTab.OPEN_SUBTITLES -> { tv.own.owntv.features.settings.OpenSubtitlesAccountScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
        SettingsTab.WEATHER -> { tv.own.owntv.features.settings.WeatherSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
        SettingsTab.CH_NAV -> { tv.own.owntv.features.settings.ChNavSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
        SettingsTab.CONTENT_MENUS -> { tv.own.owntv.features.settings.ContentMenuSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
            SettingsTab.PANEL_WIDTH -> { tv.own.owntv.features.settings.PanelWidthSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
            SettingsTab.GUIDE_WIDTH -> { tv.own.owntv.features.settings.GuideWidthSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
            SettingsTab.GLASS_EFFECT -> { GlassEffectSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }
            SettingsTab.SUBTITLE_STYLE -> tv.own.owntv.features.settings.SubtitleStylePage(onBack = { tab = SettingsTab.ROOT }, modifier = modifier)
            SettingsTab.BROWSING -> tv.own.owntv.features.settings.BrowsingListsSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier)
            SettingsTab.SPORTS -> tv.own.owntv.features.sports.SportsSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier)
            SettingsTab.FONTS -> tv.own.owntv.features.settings.FontSettingsScreen(
                current = fontCustomization, onSet = onSetFontCustomization, familyLabel = { fontFamilyLabel(it) },
                onBack = { tab = SettingsTab.ROOT }, modifier = modifier,
            )
            SettingsTab.ROOT -> Unit
    }
    }
    return
    }

    // Only at the root: every sub-screen registers its own handler, which is nested deeper and
    // therefore wins while one is open.
    if (onBack != null) BackHandler { onBack() }


    // The root rows, as data (see [RootItem]). Building 40 small objects is a fraction of the cost of
    // composing 40 rows, and it is what lets the list below stay lazy while both focus restores can
    // still find a row that is scrolled out of view.
    val rootItems: List<RootItem> = listOfNotNull(
        // The most-used toggles, pinned above the nine real groups and separated from them by a
        // hairline. They are ordinary rows that flip in place — the header strip they replace could
        // only ever show whatever six the app chose, and it cost the root a fifth of its height.
        RootGroup("group_quick", stringResource(R.string.settings_group_quick), OwnTVIcon.SPARKLE, stringResource(R.string.settings_group_summary_quick)),
        RootRow(
            "quick_live_preview", TileTone.PRIMARY, OwnTVIcon.LIVE_TV,
            title = stringResource(R.string.settings_quick_live_preview),
            chip = stringResource(if (livePreview) R.string.common_on else R.string.common_off),
            chipTone = if (livePreview) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = livePreviewQuickFocus,
            onClick = { toggleLivePreview(livePreviewQuickFocus) },
        ),
        RootRow(
            "quick_preview_sound", TileTone.SECONDARY, OwnTVIcon.AUDIO,
            title = stringResource(R.string.settings_quick_preview_sound),
            chip = stringResource(if (previewAudio) R.string.common_on else R.string.common_off),
            chipTone = if (previewAudio) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setLivePreviewAudio(!previewAudio) },
        ),
        RootRow(
            "quick_channel_numbers", TileTone.SECONDARY, OwnTVIcon.LIVE_TV,
            title = stringResource(R.string.settings_quick_channel_numbers),
            chip = stringResource(if (channelNumbers) R.string.common_on else R.string.common_off),
            chipTone = if (channelNumbers) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setDirectTune(!channelNumbers) },
        ),
        RootRow(
            "quick_hdr", TileTone.SECONDARY, OwnTVIcon.VIDEO,
            title = stringResource(R.string.settings_quick_hdr),
            chip = stringResource(if (hdr) R.string.common_on else R.string.common_off),
            chipTone = if (hdr) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setHdrEnabled(!hdr) },
        ),
        RootRow(
            "quick_autoplay", TileTone.SECONDARY, OwnTVIcon.AUTOPLAY_NEXT,
            title = stringResource(R.string.settings_quick_autoplay),
            chip = stringResource(if (autoPlayNext) R.string.common_on else R.string.common_off),
            chipTone = if (autoPlayNext) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setAutoPlayNext(!autoPlayNext) },
        ),
        RootRow(
            "quick_check_update", TileTone.SECONDARY, OwnTVIcon.DOWNLOADS,
            title = stringResource(R.string.settings_quick_check_update),
            chip = stringResource(if (updateCheckOnStart) R.string.common_on else R.string.common_off),
            chipTone = if (updateCheckOnStart) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) },
        ),
        RootGroup("group_profile", stringResource(R.string.settings_profile_group), OwnTVIcon.PERSON, stringResource(R.string.settings_group_summary_profile)),
        RootGroup("group_sources", stringResource(R.string.settings_group_sources), OwnTVIcon.PLAYLIST, stringResource(R.string.settings_group_summary_sources)),
        RootRow(
            tabRowKey(SettingsTab.SOURCES), TileTone.PRIMARY, OwnTVIcon.PLAYLIST,
            heading = stringResource(R.string.settings_sources_title),
            title = stringResource(R.string.settings_playlists), desc = stringResource(R.string.settings_playlists_description),
            focus = rowFocus.getValue(SettingsTab.SOURCES),
            onClick = { open(SettingsTab.SOURCES) },
        ),
        RootRow(
            tabRowKey(SettingsTab.EPG), TileTone.PRIMARY, OwnTVIcon.EPG,
            title = stringResource(R.string.settings_epg_sources), desc = stringResource(R.string.settings_epg_sources_nav_description),
            focus = rowFocus.getValue(SettingsTab.EPG),
            onClick = { open(SettingsTab.EPG) },
        ),
        // The guide's own settings, under a divider of their own: they tune the guide, they are not
        // sources, and sitting straight under the two source lists they read as if they were.
        RootRow(
            "epg_offset", TileTone.SECONDARY, OwnTVIcon.EPG,
            heading = stringResource(R.string.settings_part_guide_catchup),
            title = stringResource(R.string.content_epg_time_offset),
            desc = stringResource(R.string.settings_epg_offset_root_description),
            chip = epgShiftLabel(epgOffset),
            chipTone = if (epgOffset == 0) TileTone.SECONDARY else TileTone.PRIMARY,
            focus = epgOffsetRowFocus,
            onClick = { saveScroll(); dialogReturn = epgOffsetRowFocus; showEpgOffset = true },
        ),
        *stageGuideRows(settingsVm, stageSettings) { saveScroll(); dialogReturn = it }.toTypedArray(),
        // How far ahead the guide is stored. Global rather than per-source: it is one horizon that
        // every feed is trimmed to, so it sits beside the other guide-wide setting, not inside a feed.
        RootRow(
            "guide_days", TileTone.SECONDARY, OwnTVIcon.EPG,
            title = stringResource(R.string.settings_epg_guide_days),
            chip = pluralStringResource(R.plurals.settings_epg_guide_days_value, guideDays, guideDays),
            chipTone = TileTone.PRIMARY,
            focus = guideDaysRowFocus,
            onClick = { saveScroll(); dialogReturn = guideDaysRowFocus; showGuideDays = true },
        ),
        // Sits with the EPG offset, not with Playback: both answer "the guide/archive clock is wrong",
        // and a user fixing one almost always looks at the other next.
        RootRow(
            "catchup", TileTone.SECONDARY, OwnTVIcon.CATCHUP,
            title = stringResource(R.string.settings_catchup),
            desc = if (catchupChannels > 0) pluralStringResource(R.plurals.settings_catchup_supported, catchupChannels, catchupChannels)
                else stringResource(R.string.settings_catchup_unavailable),
            chip = when (catchupTz) {
                SettingsRepository.CatchupTimezone.AUTOMATIC -> stringResource(R.string.settings_catchup_timezone_automatic)
                SettingsRepository.CatchupTimezone.DEVICE -> stringResource(R.string.settings_device)
                SettingsRepository.CatchupTimezone.MANUAL -> utcOffsetLabel(catchupOffset)
            },
            chipTone = TileTone.PRIMARY,
            focus = catchupRowFocus,
            onClick = { saveScroll(); dialogReturn = catchupRowFocus; showCatchupTime = true },
        ),
        if (playlistSources.isNotEmpty()) RootRow(
            "catchup_sources", TileTone.SECONDARY, OwnTVIcon.CATCHUP,
            title = stringResource(R.string.settings_catchup_timezone_per_playlist),
            desc = stringResource(R.string.settings_catchup_timezone_per_playlist_description),
            chip = if (catchupOverrides == 0) stringResource(R.string.common_off)
                else pluralStringResource(R.plurals.settings_live_preroll_overrides, catchupOverrides, catchupOverrides),
            chipTone = if (catchupOverrides > 0) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = catchupSourcesRowFocus,
            onClick = { saveScroll(); dialogReturn = catchupSourcesRowFocus; showCatchupSources = true },
        ) else null,
        RootGroup("group_appearance", stringResource(R.string.settings_appearance_group), OwnTVIcon.PALETTE, stringResource(R.string.settings_group_summary_appearance)),
        RootRow(
            "theme", TileTone.PRIMARY, OwnTVIcon.THEME,
            title = stringResource(R.string.settings_theme), desc = stringResource(R.string.settings_theme_description),
            chip = themeLabel(themeMode), chipTone = TileTone.PRIMARY,
            focus = themeRowFocus,
            onClick = { saveScroll(); dialogReturn = themeRowFocus; showTheme = true },
        ),
        RootRow(
            "accent", TileTone.SECONDARY, OwnTVIcon.PALETTE,
            title = stringResource(R.string.settings_accent), desc = stringResource(R.string.settings_accent_description),
            chip = if (customAccent.isNotBlank()) customAccent.uppercase() else stringResource(accent.labelRes),
            chipTone = TileTone.SECONDARY,
            focus = accentRowFocus,
            onClick = { saveScroll(); dialogReturn = accentRowFocus; showAccent = true },
        ),
        RootRow(
            "focus_highlight", TileTone.SECONDARY, OwnTVIcon.FOCUS_HIGHLIGHT,
            title = stringResource(R.string.settings_focus_highlight),
            desc = stringResource(R.string.settings_focus_highlight_description),
            chip = focusHighlightChip(focusHighlight, focusHighlightWidth),
            chipTone = TileTone.SECONDARY,
            focus = focusHighlightRowFocus,
            onClick = { saveScroll(); dialogReturn = focusHighlightRowFocus; showFocusHighlight = true },
        ),
        // Glass & background has enough controls to be a page of its own; the root row only summarizes it.
        RootRow(
            tabRowKey(SettingsTab.GLASS_EFFECT), TileTone.PRIMARY, OwnTVIcon.SPARKLE,
            title = stringResource(R.string.settings_glass_bg_title), desc = stringResource(R.string.settings_line_glass_bg),
            chip = glassBackgroundSummary(settingsVm.backgroundConfig.collectAsStateWithLifecycle().value, glassConfig),
            chipTone = if (glassOn) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = rowFocus.getValue(SettingsTab.GLASS_EFFECT),
            onClick = { open(SettingsTab.GLASS_EFFECT) },
        ),
        if (themeMode == ThemeMode.DARK && !glassOn) RootRow(
            "ambient_glow", TileTone.PRIMARY, OwnTVIcon.GLOW,
            title = stringResource(R.string.settings_ambient_glow),
            desc = stringResource(R.string.settings_ambient_glow_description),
            chip = stringResource(if (ambientGlowEnabled) R.string.common_on else R.string.common_off),
            chipTone = if (ambientGlowEnabled) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = ambientGlowRowFocus,
            onClick = { saveScroll(); dialogReturn = ambientGlowRowFocus; showAmbientGlow = true },
        ) else null,
        RootRow(
            "fonts", TileTone.SECONDARY, OwnTVIcon.TEXT_SIZE,
            title = stringResource(R.string.settings_font_customization),
            desc = stringResource(R.string.settings_font_customization_description),
            chip = stringResource(R.string.common_percent, fontCustomization.sizePercent),
            chipTone = TileTone.SECONDARY,
            focus = rowFocus.getValue(SettingsTab.FONTS),
            onClick = { open(SettingsTab.FONTS) },
        ),
        RootRow(
            "popup_size", TileTone.SECONDARY, OwnTVIcon.ZOOM,
            title = stringResource(R.string.settings_popup_size),
            desc = stringResource(R.string.settings_popup_size_description),
            chip = stringResource(R.string.common_percent, fontCustomization.popupSizePercent),
            chipTone = TileTone.SECONDARY,
            focus = popupSizeRowFocus,
            onClick = { saveScroll(); dialogReturn = popupSizeRowFocus; showPopupSize = true },
        ),
        RootRow(
            "ui_zoom", TileTone.SECONDARY, OwnTVIcon.ZOOM,
            title = stringResource(R.string.settings_ui_zoom), desc = stringResource(R.string.settings_ui_zoom_description),
            chip = stringResource(R.string.common_percent, uiZoomPercent), chipTone = TileTone.SECONDARY,
            focus = zoomRowFocus,
            onClick = { saveScroll(); dialogReturn = zoomRowFocus; showZoom = true },
        ),
        RootRow(
            "animations", TileTone.SECONDARY, OwnTVIcon.MOTION,
            title = stringResource(R.string.settings_animations), desc = stringResource(R.string.settings_animations_description),
            chip = stringResource(animationLevel.labelRes), chipTone = TileTone.SECONDARY,
            focus = animationsRowFocus,
            onClick = { saveScroll(); dialogReturn = animationsRowFocus; showAnimations = true },
        ),
        RootRow(
            tabRowKey(SettingsTab.WEATHER), TileTone.SECONDARY, OwnTVIcon.WEATHER,
            title = stringResource(R.string.settings_weather),
            desc = stringResource(R.string.settings_weather_description_root),
            chip = if (weatherEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            chipTone = if (weatherEnabled) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = rowFocus.getValue(SettingsTab.WEATHER),
            onClick = { open(SettingsTab.WEATHER) },
        ),
        RootGroup("group_layout", stringResource(R.string.settings_group_layout), OwnTVIcon.LIST_GRID, stringResource(R.string.settings_group_summary_layout)),
        navigationRootRow(settingsVm, navigationRowFocus) { saveScroll(); dialogReturn = navigationRowFocus; showNavigation = true },
        liveLayoutRootRow(settingsVm, liveLayoutRowFocus) { saveScroll(); dialogReturn = liveLayoutRowFocus; showLiveLayout = true },
        *stageLayoutRows(settingsVm, stageSettings) { saveScroll(); dialogReturn = it }.toTypedArray(),
        RootRow(
            "vod_layout", TileTone.PRIMARY, OwnTVIcon.LIST_GRID,
            title = stringResource(R.string.settings_vod_layout),
            desc = stringResource(R.string.settings_vod_layout_description),
            chip = stringResource(vodLayoutLabelRes(vodLayout)),
            chipTone = if (vodLayout == tv.own.owntv.core.settings.SettingsRepository.VodLayout.CINEMATIC) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = vodLayoutRowFocus,
            onClick = { saveScroll(); dialogReturn = vodLayoutRowFocus; showVodLayout = true },
        ),
        RootRow(
            tabRowKey(SettingsTab.PANEL_WIDTH), TileTone.PRIMARY, OwnTVIcon.PANEL_WIDTH,
            title = stringResource(R.string.settings_panel_width),
            desc = stringResource(R.string.settings_panel_width_description),
            chip = if (panelWidthCustom) stringResource(R.string.settings_live_latency_custom) else stringResource(R.string.settings_subtitle_default),
            chipTone = if (panelWidthCustom) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = rowFocus.getValue(SettingsTab.PANEL_WIDTH),
            onClick = { open(SettingsTab.PANEL_WIDTH) },
        ),
        RootRow(
            tabRowKey(SettingsTab.GUIDE_WIDTH), TileTone.PRIMARY, OwnTVIcon.EPG,
            title = stringResource(R.string.settings_guide_width),
            desc = stringResource(R.string.settings_guide_width_description),
            chip = if (guideWidthCustom) stringResource(R.string.settings_live_latency_custom) else stringResource(R.string.settings_subtitle_default),
            chipTone = if (guideWidthCustom) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = rowFocus.getValue(SettingsTab.GUIDE_WIDTH),
            onClick = { open(SettingsTab.GUIDE_WIDTH) },
        ),
        RootRow(
            "browsing_lists", TileTone.PRIMARY, OwnTVIcon.LIST_GRID,
            title = stringResource(R.string.settings_browsing_lists), desc = stringResource(R.string.settings_browsing_description),
            chevron = true,
            focus = rowFocus.getValue(SettingsTab.BROWSING),
            onClick = { open(SettingsTab.BROWSING) },
        ),
        RootRow(
            tabRowKey(SettingsTab.HOME), TileTone.SECONDARY, OwnTVIcon.HOME,
            title = stringResource(R.string.settings_home_root), desc = stringResource(R.string.settings_home_root_description),
            focus = rowFocus.getValue(SettingsTab.HOME),
            onClick = { open(SettingsTab.HOME) },
        ),
        RootRow(
            tabRowKey(SettingsTab.CONTENT_MENUS), TileTone.PRIMARY, OwnTVIcon.MENU,
            title = stringResource(R.string.settings_content_menus_title),
            desc = stringResource(R.string.settings_content_menus_description),
            focus = rowFocus.getValue(SettingsTab.CONTENT_MENUS),
            onClick = { open(SettingsTab.CONTENT_MENUS) },
        ),
        RootRow(
            tabRowKey(SettingsTab.CH_NAV), TileTone.PRIMARY, OwnTVIcon.CH_NAV,
            title = stringResource(R.string.settings_ch_paging), desc = stringResource(R.string.settings_ch_paging_description),
            chip = if (chNavEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            chipTone = if (chNavEnabled) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = rowFocus.getValue(SettingsTab.CH_NAV),
            onClick = { open(SettingsTab.CH_NAV) },
        ),
        RootGroup("group_content_metadata", stringResource(R.string.settings_group_content_metadata), OwnTVIcon.IMAGE, stringResource(R.string.settings_group_summary_content_metadata)),
        RootRow(
            tabRowKey(SettingsTab.CUSTOMIZE), TileTone.PRIMARY, OwnTVIcon.SORT,
            title = stringResource(R.string.settings_customize), desc = stringResource(R.string.settings_customize_nav_description),
            focus = rowFocus.getValue(SettingsTab.CUSTOMIZE),
            onClick = { open(SettingsTab.CUSTOMIZE) },
        ),
        RootRow(
            tabRowKey(SettingsTab.METADATA), TileTone.PRIMARY, OwnTVIcon.IMAGE,
            title = stringResource(R.string.settings_metadata), desc = stringResource(R.string.settings_metadata_root_description),
            focus = rowFocus.getValue(SettingsTab.METADATA),
            onClick = { open(SettingsTab.METADATA) },
        ),
        RootRow(
            tabRowKey(SettingsTab.OPEN_SUBTITLES), TileTone.PRIMARY, OwnTVIcon.SUBTITLE,
            title = stringResource(R.string.settings_open_subtitles), desc = stringResource(R.string.settings_open_subtitles_description),
            focus = rowFocus.getValue(SettingsTab.OPEN_SUBTITLES),
            onClick = { open(SettingsTab.OPEN_SUBTITLES) },
        ),
        RootGroup("group_player", stringResource(R.string.settings_vp_cat_player), OwnTVIcon.PLAY, ""),
        RootGroup("group_picture", stringResource(R.string.settings_vp_cat_picture), OwnTVIcon.VIDEO, ""),
        RootGroup("group_sound", stringResource(R.string.settings_group_sound_subtitles), OwnTVIcon.HEADPHONES, ""),
        RootGroup("group_live", stringResource(R.string.settings_live_tv), OwnTVIcon.LIVE_TV, ""),
        RootGroup("group_watching", stringResource(R.string.settings_group_watching_recording), OwnTVIcon.REC, ""),
        // No "Data" group: Backup and Local sync are places, not preferences,
        // and are More rows now; Clear history moved onto the History screen it acts on; the download
        // folder moved to the Downloads screen. With all four gone the group had nothing left in it.
        RootGroup("group_app", stringResource(R.string.settings_app_group), OwnTVIcon.INFO, stringResource(R.string.settings_group_summary_app)),
        // Sports Hub: one row in App, opening the page with every Sports setting. Not a group of its
        // own: More › Settings opens groups by position (SettingsGroup), and its 12 cards fill the page.
        RootRow(
            tabRowKey(SettingsTab.SPORTS), TileTone.PRIMARY, OwnTVIcon.SPORTS,
            title = stringResource(R.string.sports_title), desc = stringResource(R.string.sports_leagues),
            focus = rowFocus.getValue(SettingsTab.SPORTS),
            onClick = { open(SettingsTab.SPORTS) },
        ),
        RootRow(
            tabRowKey(SettingsTab.LANGUAGE), TileTone.PRIMARY, OwnTVIcon.LANGUAGE,
            heading = stringResource(R.string.settings_group_app),
            title = stringResource(R.string.settings_language),
            desc = stringResource(R.string.settings_language_description),
            chip = languageChip,
            chipTone = TileTone.PRIMARY,
            focus = rowFocus.getValue(SettingsTab.LANGUAGE),
            onClick = { open(SettingsTab.LANGUAGE) },
        ),
        RootRow(
            "app_icon", TileTone.SECONDARY, OwnTVIcon.PALETTE,
            title = stringResource(R.string.settings_app_icon), desc = stringResource(R.string.settings_app_icon_summary),
            chip = stringResource(appIcon.label), chipTone = TileTone.SECONDARY,
            focus = appIconRowFocus,
            onClick = { saveScroll(); dialogReturn = appIconRowFocus; showAppIcon = true },
        ),
        RootRow(
            "brand_accent", TileTone.SECONDARY, OwnTVIcon.PALETTE,
            title = stringResource(R.string.settings_brand_accent), desc = stringResource(R.string.settings_line_brand_accent),
            chip = stringResource(if (brandAccent) R.string.common_on else R.string.common_off), chipTone = TileTone.SECONDARY,
            onClick = { settingsVm.setBrandAccentTriangle(!brandAccent) },
        ),
        RootRow(
            "app_startup", TileTone.SECONDARY, OwnTVIcon.POWER,
            title = stringResource(R.string.settings_app_startup), desc = stringResource(R.string.settings_app_startup_description),
            chip = if (startupMode == tv.own.owntv.core.settings.StartupMode.SPECIFIC_CHANNEL) {
                startupChannel?.name ?: startupLabel(startupMode)
            } else startupLabel(startupMode),
            chipTone = TileTone.PRIMARY,
            focus = startupRowFocus,
            onClick = { saveScroll(); dialogReturn = startupRowFocus; showStartup = true },
        ),
        *autoStartRootRows(autoStart).toTypedArray(),
        RootRow(
            "check_updates", TileTone.PRIMARY, OwnTVIcon.REFRESH,
            title = stringResource(R.string.settings_check_updates), desc = stringResource(R.string.settings_check_updates_description),
            chip = "v${tv.own.owntv.BuildConfig.VERSION_NAME}",
            focus = updateRowFocus,
            onClick = { saveScroll(); dialogReturn = updateRowFocus; showUpdate = true },
        ),
        RootRow(
            "update_startup", TileTone.SECONDARY, OwnTVIcon.REFRESH,
            title = stringResource(R.string.settings_update_startup), desc = stringResource(R.string.settings_update_startup_description),
            chip = if (updateCheckOnStart) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            chipTone = if (updateCheckOnStart) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) },
        ),
        RootRow(
            tabRowKey(SettingsTab.NETWORK), TileTone.SECONDARY, OwnTVIcon.NETWORK,
            heading = stringResource(R.string.settings_group_network),
            title = stringResource(R.string.common_proxy), desc = stringResource(R.string.settings_proxy_description),
            focus = rowFocus.getValue(SettingsTab.NETWORK),
            onClick = { open(SettingsTab.NETWORK) },
        ),
        RootRow(
            tabRowKey(SettingsTab.DNS), TileTone.SECONDARY, OwnTVIcon.DNS,
            title = stringResource(R.string.settings_dns),
            desc = stringResource(R.string.settings_dns_description),
            focus = rowFocus.getValue(SettingsTab.DNS),
            onClick = { open(SettingsTab.DNS) },
        ),
        // About and the error log live in More. A page of facts and a log are
        // not preferences; both are More rows now, opening the very same dialogs.
    )

    // --- Two-pane root: the flat list above is still the single source of truth for order, tone,
    // icon, chip and click of every row. Here it is only *sliced* into (heading, its rows) so the
    // left column can list the headings and the right column only the selected group's rows. Adding
    // a row anywhere above needs no change here.
    // Quick is the one group whose contents are not positional: it is whatever the user pinned, in the
    // order they pinned it. Its rows are still defined above like every other row, so a pinned row and
    // its home-group twin are the same object and stay in step automatically.
    // Rows that live inside Video player settings can be pinned too, and they have no twin on the root
    // to borrow from — Playback must not list them a second time. Quick materialises those from the
    // catalogue next to the real rows, and each one reopens that screen on the row it came from.
    val videoDeepRows: Map<String, RootRow> = tv.own.owntv.features.settings.VIDEO_QUICK_ROWS
        .filter { it.key in quickPinned }
        .associate { ref ->
        // The pin is a copy of the row, not just a link to it: it shows the same value, and a row that
        // is a plain toggle flips here without leaving Quick. The rest still open the screen on the row.
        val binding = androidx.compose.runtime.key(ref.key) {
            tv.own.owntv.features.settings.videoQuickBinding(ref.key, settingsVm)
        }
        val jump = { lastTab = null; jumpVideo(ref.key, true) }
        ref.key to RootRow(
            key = ref.key,
            tone = TileTone.TERTIARY,
            icon = ref.icon,
            title = stringResource(ref.titleRes),
            // Where it lives, so a pin never loses its home ("Pinned from Player").
            desc = stringResource(R.string.settings_pinned_from, stringResource(SettingsGroup.of(tv.own.owntv.features.settings.videoGroupOf(ref.section)).titleRes)),
            chip = binding?.chip,
            chipTone = if (binding?.primaryChip == true) TileTone.PRIMARY else TileTone.SECONDARY,
            chevron = binding?.onToggle == null,
            focus = if (ref.key == deepReturnKey) deepRowFocus else null,
            onClick = binding?.onToggle ?: jump,
        )
    }
    val categories: List<Pair<RootGroup, List<RootRow>>> = remember(rootItems, quickPinned, deepReturnKey, videoDeepRows) {
        val sliced: List<Pair<RootGroup, List<RootRow>>> = buildList {
            rootItems.forEach { item ->
                when (item) {
                    is RootGroup -> add(item to mutableListOf<RootRow>())
                    is RootRow -> (lastOrNull()?.second as? MutableList<RootRow>)?.add(item)
                }
            }
        }
        val byKey = sliced.flatMap { it.second }.associateBy { it.key }
        sliced.map { (group, rows) ->
            if (group.key == "group_quick") {
                group to quickPinned.mapNotNull { byKey[it] ?: videoDeepRows[it] }
            } else {
                group to rows
            }
        }
    }
    // A pin that matches no Settings row at all (a row removed in an earlier release) is dropped, so
    // Quick's count says what it lists. Rows that are only hidden for now keep their pins.
    LaunchedEffect(quickPinned, rootItems.size) {
        val known = rootItems.map { it.key }.toSet() + tv.own.owntv.features.settings.VIDEO_QUICK_ROWS.map { it.key } +
            setOf("ambient_glow", "catchup_sources", "vp_tunneled", "vp_preview_audio", "vp_timeshift_window", "vp_timeshift_resume", "vp_multiview_tiles")
        val kept = quickPinned.filter { it in known }
        if (kept.size < quickPinned.size) settingsVm.setQuickPinnedKeys(kept)
    }
    /** Which category each row key lives in, so a Back from a sub-screen can reselect its group. */
    val groupOfKey: Map<String, Int> = remember(categories) {
        buildMap { categories.forEachIndexed { g, (_, rows) -> rows.forEach { if (g != 0) put(it.key, g) } } }
    }
    val selectedRows = categories.getOrNull(selectedGroup)?.second.orEmpty()
    // The row whose hold-OK menu is open, or null.
    var menuRow by remember { mutableStateOf<RootRow?>(null) }
    // Closing the menu leaves focus nowhere, so the row it belonged to asks for it back.
    var menuReturnKey by remember { mutableStateOf<String?>(null) }
    /** Where focus goes instead when the menu just removed its own row from Quick. */
    var unpinReturnKey by remember { mutableStateOf<String?>(null) }
    val menuReturnFocus = remember { FocusRequester() }
    LaunchedEffect(menuReturnKey) {
        if (menuReturnKey == null) return@LaunchedEffect
        kotlinx.coroutines.delay(60)
        // Unpinning removes the row from Quick, so its requester may no longer be attached to
        // anything — then the page's first row takes focus rather than nothing at all.
        if (runCatching { menuReturnFocus.requestFocus() }.isFailure) {
            runCatching { rowsFocus.requestFocus() }
        }
    }
    // A genuinely new group starts at its top. Recreating the page after a sub-screen does not:
    // its group and scroll were kept above the sub-screen dispatch, so leave them untouched.
    LaunchedEffect(selectedGroup) {
        if (displayedGroup != selectedGroup) {
            runCatching { pageScroll.scrollTo(0) }
            displayedGroup = selectedGroup
            if (videoRowKey == null) for (attempt in 0 until 10) { withFrameNanos { }; if (runCatching { rowsFocus.requestFocus() }.getOrDefault(false)) break }
        }
    }

    // Restore focus to the row a sub-screen was opened from when the user navigates back. This block
    // only exists while the page is showing, so coming back from a sub-screen is exactly when it runs.
    val returningRowFocus = when {
        searchQuery.isNotBlank() && (deepReturnKey != null || lastTab != null) -> searchFieldFocus
        deepReturnKey != null && selectedGroup == 0 -> deepRowFocus
        else -> lastTab?.let { rowFocus[it] }
    }
    LaunchedEffect(Unit) {
        val key = lastTab?.let(::tabRowKey)
        val target = returningRowFocus ?: return@LaunchedEffect
        val group = key?.let(groupOfKey::get)
        if (searchQuery.isBlank() && group != null) selectedGroup = group
        // Subtitle appearance's row sits in the Sound & subtitles rows, below the fold: the page comes
        // back at the top, so put the offset it had back while focus lands.
        if (lastTab == SettingsTab.SUBTITLE_STYLE && searchQuery.isBlank()) {
            tv.own.owntv.ui.components.restoreAfterDialogClose(target, pageScroll, savedScrollPx)
            lastTab = null
            return@LaunchedEffect
        }
        // By frames: the page may need a layout pass once its group is selected; once focus lands,
        // hold it a few frames so a late entry cannot move it.
        var settledFrames = 0
        repeat(10) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.getOrDefault(false)) {
                settledFrames++
                if (settledFrames >= 3) {
                    lastTab = null
                    if (selectedGroup == 0) deepReturnKey = null
                    return@LaunchedEffect
                }
            } else {
                settledFrames = 0
            }
        }
    }

    // A fresh entry from More: the page's first row (a search start focuses the field instead).
    if (onBack != null) {
        LaunchedEffect(Unit) {
            if (returningRowFocus != null || start?.search == true) return@LaunchedEffect
            repeat(10) {
                withFrameNanos { }
                if (runCatching { rowsFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
    }

        // Batch 4 · search results — flat, group-context-prefixed rows ("Playback › HDR").
        // Dialog-opening entries return focus to the search field on close (their normal row
        // isn't composed while searching). Toggle entries keep the results visible so the chip
        // updates live.
        //
        // Settings that live one level deeper name that screen too ("Playback › Video player ›
        // HDR"), because the group alone would send the user to a list the setting is no longer
        // on. Built from the same format string the row itself uses, so the separator and its
        // spacing stay translated rather than hard-coded here. It also lands in the search
        // haystack, so typing "video player" finds everything on that screen.
    val searchResults: List<SettingsSearchEntry> = if (searchQuery.isBlank()) emptyList() else {
        val entries = listOfNotNull(
            SettingsSearchEntry(stringResource(R.string.settings_app_group), stringResource(R.string.settings_language), stringResource(R.string.settings_search_keywords_language), OwnTVIcon.LANGUAGE, TileTone.PRIMARY,
                chip = languageChip, chipTone = TileTone.PRIMARY) { open(SettingsTab.LANGUAGE) },
            SettingsSearchEntry(stringResource(R.string.settings_group_profile), stringResource(R.string.profiles_title), stringResource(R.string.settings_search_keywords_profiles), OwnTVIcon.PERSON, TileTone.SECONDARY) { searchQuery = ""; selectedGroup = SettingsGroup.PROFILE.ordinal },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_playlists), stringResource(R.string.settings_search_keywords_playlists), OwnTVIcon.PLAYLIST, TileTone.PRIMARY) { open(SettingsTab.SOURCES) },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_epg_sources), stringResource(R.string.settings_search_keywords_epg), OwnTVIcon.EPG, TileTone.PRIMARY) { open(SettingsTab.EPG) },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.content_epg_time_offset), stringResource(R.string.settings_search_keywords_epg_offset), OwnTVIcon.EPG, TileTone.SECONDARY,
                chip = epgShiftLabel(epgOffset), chipTone = if (epgOffset == 0) TileTone.SECONDARY else TileTone.PRIMARY) { saveScroll(); dialogReturn = searchFieldFocus; showEpgOffset = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_epg_guide_days), stringResource(R.string.settings_search_keywords_epg), OwnTVIcon.EPG, TileTone.SECONDARY,
                chip = pluralStringResource(R.plurals.settings_epg_guide_days_value, guideDays, guideDays), chipTone = TileTone.PRIMARY) { saveScroll(); dialogReturn = searchFieldFocus; showGuideDays = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_search_guide_logos), stringResource(R.string.settings_search_keywords_logos), OwnTVIcon.EPG, TileTone.SECONDARY) { open(SettingsTab.EPG) },
            SettingsSearchEntry(stringResource(R.string.settings_group_content_metadata), stringResource(R.string.settings_customize), stringResource(R.string.settings_search_keywords_customize), OwnTVIcon.SORT, TileTone.PRIMARY) { open(SettingsTab.CUSTOMIZE) },
            navigationSearchEntry(settingsVm) { saveScroll(); dialogReturn = searchFieldFocus; showNavigation = true },
            liveLayoutSearchEntry(settingsVm) { saveScroll(); dialogReturn = searchFieldFocus; showLiveLayout = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_live_opens_in), stringResource(R.string.settings_search_keywords_live_opens_in), OwnTVIcon.LIVE_TV, TileTone.PRIMARY) { saveScroll(); dialogReturn = searchFieldFocus; stageSettings.open = StageSettingsDialog.LIVE_OPENS_IN },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_reminders), stringResource(R.string.settings_search_keywords_reminders), OwnTVIcon.BELL, TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; stageSettings.open = StageSettingsDialog.REMINDERS },
            SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_ch_paging), stringResource(R.string.settings_search_keywords_ch), OwnTVIcon.CH_NAV, TileTone.PRIMARY,
                chip = if (chNavEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (chNavEnabled) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.CH_NAV) },
            SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_vod_layout), stringResource(R.string.settings_search_keywords_vod_layout), OwnTVIcon.LIST_GRID, TileTone.PRIMARY,
                chip = stringResource(vodLayoutLabelRes(vodLayout)), chipTone = if (vodLayout == tv.own.owntv.core.settings.SettingsRepository.VodLayout.CINEMATIC) TileTone.PRIMARY else TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showVodLayout = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_panel_width), stringResource(R.string.settings_search_keywords_panel_width), OwnTVIcon.PANEL_WIDTH, TileTone.PRIMARY,
                chip = if (panelWidthCustom) stringResource(R.string.settings_live_latency_custom) else stringResource(R.string.settings_subtitle_default), chipTone = if (panelWidthCustom) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.PANEL_WIDTH) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_guide_width), stringResource(R.string.settings_search_keywords_guide_width), OwnTVIcon.EPG, TileTone.PRIMARY,
            chip = if (guideWidthCustom) stringResource(R.string.settings_live_latency_custom) else stringResource(R.string.settings_subtitle_default), chipTone = if (guideWidthCustom) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.GUIDE_WIDTH) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_browsing_lists), stringResource(R.string.settings_search_keywords_browsing), OwnTVIcon.LIST_GRID, TileTone.PRIMARY) { open(SettingsTab.BROWSING) },
            SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_home_root), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
            SettingsSearchEntry(stringResource(R.string.settings_group_content_metadata), stringResource(R.string.settings_metadata), stringResource(R.string.settings_search_keywords_metadata), OwnTVIcon.IMAGE, TileTone.PRIMARY) { open(SettingsTab.METADATA) },
            // No entries for the download folder, Backup, Local sync or Clear history. They
            // are not in Settings any more, and a result for something that is not here is a lie
            // about where it lives. The no-results state deliberately says nothing else either.
            SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_theme), stringResource(R.string.settings_search_keywords_theme), OwnTVIcon.THEME, TileTone.PRIMARY,
                chip = themeLabel(themeMode)) { saveScroll(); dialogReturn = searchFieldFocus; showTheme = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_accent), stringResource(R.string.settings_search_keywords_accent), OwnTVIcon.PALETTE, TileTone.SECONDARY,
                chip = if (customAccent.isNotBlank()) customAccent.uppercase() else stringResource(accent.labelRes), chipTone = TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showAccent = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_focus_highlight), stringResource(R.string.settings_search_keywords_focus), OwnTVIcon.FOCUS_HIGHLIGHT, TileTone.SECONDARY,
                chip = focusHighlightChip(focusHighlight, focusHighlightWidth), chipTone = TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showFocusHighlight = true },
            if (themeMode == ThemeMode.DARK && !glassOn) SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_ambient_glow), stringResource(R.string.settings_ambient_glow_description), OwnTVIcon.GLOW, TileTone.PRIMARY,
                chip = stringResource(if (ambientGlowEnabled) R.string.common_on else R.string.common_off), chipTone = if (ambientGlowEnabled) TileTone.PRIMARY else TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showAmbientGlow = true } else null,
        SettingsSearchEntry(
            stringResource(R.string.settings_group_appearance),
            stringResource(R.string.settings_font_customization),
                stringResource(R.string.settings_search_keywords_fonts),
                OwnTVIcon.TEXT_SIZE,
                TileTone.SECONDARY,
                chip = stringResource(R.string.common_percent, fontCustomization.sizePercent),
            chipTone = TileTone.SECONDARY,
        ) { open(SettingsTab.FONTS) },
        SettingsSearchEntry(
            stringResource(R.string.settings_group_appearance),
            stringResource(R.string.settings_popup_size),
            stringResource(R.string.settings_popup_size_description),
            OwnTVIcon.ZOOM,
            TileTone.SECONDARY,
            chip = stringResource(R.string.common_percent, fontCustomization.popupSizePercent),
            chipTone = TileTone.SECONDARY,
        ) { saveScroll(); dialogReturn = searchFieldFocus; showPopupSize = true },
        SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_ui_zoom), stringResource(R.string.settings_search_keywords_zoom), OwnTVIcon.ZOOM, TileTone.SECONDARY,
                chip = stringResource(R.string.common_percent, uiZoomPercent), chipTone = TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showZoom = true },
        SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_app_icon), stringResource(R.string.settings_app_icon_summary), OwnTVIcon.PALETTE, TileTone.SECONDARY,
                chip = stringResource(appIcon.label), chipTone = TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showAppIcon = true },
        SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_brand_accent), stringResource(R.string.settings_line_brand_accent), OwnTVIcon.PALETTE, TileTone.SECONDARY,
                chip = stringResource(if (brandAccent) R.string.common_on else R.string.common_off), chipTone = TileTone.SECONDARY, showChevron = false) { settingsVm.setBrandAccentTriangle(!brandAccent) },
            SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_animations), stringResource(R.string.settings_search_keywords_animation), OwnTVIcon.MOTION, TileTone.SECONDARY,
                chip = stringResource(animationLevel.labelRes), chipTone = TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showAnimations = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_weather), stringResource(R.string.settings_search_keywords_weather), OwnTVIcon.WEATHER, TileTone.SECONDARY,
                chip = if (weatherEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (weatherEnabled) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.WEATHER) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_live_preview"), stringResource(R.string.settings_quick_live_preview), stringResource(R.string.settings_search_keywords_live_preview), OwnTVIcon.LIVE_TV, TileTone.TERTIARY,
                chip = if (livePreview) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (livePreview) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { toggleLivePreview(searchFieldFocus) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_preview_audio"), stringResource(R.string.settings_preview_audio), stringResource(R.string.settings_search_keywords_sound), OwnTVIcon.AUDIO, TileTone.SECONDARY,
                chip = if (previewAudio) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (previewAudio) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setLivePreviewAudio(!previewAudio) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_channel_numbers"), stringResource(R.string.settings_quick_channel_numbers), stringResource(R.string.settings_search_keywords_channel_numbers), OwnTVIcon.LIVE_TV, TileTone.PRIMARY,
                chip = if (channelNumbers) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (channelNumbers) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setDirectTune(!channelNumbers) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_mini"), stringResource(R.string.settings_mini_player_root), stringResource(R.string.settings_search_keywords_mini), OwnTVIcon.PIP, TileTone.TERTIARY) { openMiniPlayer = true; jumpVideo("vp_mini", false) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_hdr"), stringResource(R.string.settings_quick_hdr), stringResource(R.string.settings_search_keywords_hdr), OwnTVIcon.VIDEO, TileTone.PRIMARY,
                chip = if (hdr) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (hdr) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setHdrEnabled(!hdr) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_afr"), stringResource(R.string.settings_auto_frame_rate), stringResource(R.string.settings_search_keywords_afr), OwnTVIcon.VIDEO, TileTone.PRIMARY,
                chip = if (autoFrameRate) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (autoFrameRate) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { toggleAutoFrameRate(searchFieldFocus) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_surround"), stringResource(R.string.settings_surround_sound), stringResource(R.string.settings_search_keywords_surround), OwnTVIcon.AUDIO, TileTone.SECONDARY,
                chip = surroundModeLabel(surroundMode), chipTone = if (surroundMode == SurroundMode.STEREO) TileTone.SECONDARY else TileTone.PRIMARY, showChevron = false) { settingsVm.cycleSurroundMode() },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_autoplay"), stringResource(R.string.settings_autoplay_next), stringResource(R.string.settings_search_keywords_autoplay), OwnTVIcon.AUTOPLAY_NEXT, TileTone.SECONDARY,
                chip = if (autoPlayNext) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (autoPlayNext) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setAutoPlayNext(!autoPlayNext) },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_catchup), stringResource(R.string.settings_search_keywords_catchup), OwnTVIcon.CATCHUP, TileTone.SECONDARY,
                chip = when (catchupTz) {
                    SettingsRepository.CatchupTimezone.AUTOMATIC -> stringResource(R.string.settings_catchup_timezone_automatic)
                    SettingsRepository.CatchupTimezone.DEVICE -> stringResource(R.string.settings_device)
                    SettingsRepository.CatchupTimezone.MANUAL -> utcOffsetLabel(catchupOffset)
                }) { saveScroll(); dialogReturn = searchFieldFocus; showCatchupTime = true },
            if (playlistSources.isNotEmpty()) SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_catchup_timezone_per_playlist), stringResource(R.string.settings_search_keywords_catchup), OwnTVIcon.CATCHUP, TileTone.SECONDARY,
                chip = if (catchupOverrides == 0) stringResource(R.string.common_off)
                    else pluralStringResource(R.plurals.settings_live_preroll_overrides, catchupOverrides, catchupOverrides),
                chipTone = if (catchupOverrides > 0) TileTone.PRIMARY else TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showCatchupSources = true } else null,
            // Four screens that had no entry at all, so nothing on them could be found by name.
            SettingsSearchEntry(stringResource(R.string.settings_group_watching_recording), stringResource(R.string.recording_settings_group), stringResource(R.string.settings_search_keywords_recording), OwnTVIcon.LIVE_TV, TileTone.TERTIARY) { searchQuery = ""; selectedGroup = SettingsGroup.WATCHING.ordinal },
            SettingsSearchEntry(stringResource(R.string.settings_group_content_metadata), stringResource(R.string.settings_open_subtitles), stringResource(R.string.settings_search_keywords_subtitle_appearance), OwnTVIcon.SUBTITLE, TileTone.PRIMARY) { open(SettingsTab.OPEN_SUBTITLES) },
            SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_glass_bg_title), stringResource(R.string.settings_search_keywords_glass), OwnTVIcon.SPARKLE, TileTone.PRIMARY,
                chip = glassBackgroundSummary(settingsVm.backgroundConfig.collectAsStateWithLifecycle().value, glassConfig), chipTone = if (glassOn) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.GLASS_EFFECT) },
            SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_content_menus_title), stringResource(R.string.settings_search_keywords_customize), OwnTVIcon.MENU, TileTone.PRIMARY) { open(SettingsTab.CONTENT_MENUS) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_sub_style"), stringResource(R.string.settings_subtitle_appearance), stringResource(R.string.settings_search_keywords_subtitle_appearance), OwnTVIcon.SUBTITLE, TileTone.TERTIARY) { open(SettingsTab.SUBTITLE_STYLE) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_live_latency"), stringResource(R.string.settings_live_latency), stringResource(R.string.settings_search_keywords_latency), OwnTVIcon.LIVE_TV, TileTone.TERTIARY) { jumpVideo("vp_live_latency", false) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_preroll"), stringResource(R.string.settings_live_preroll), stringResource(R.string.settings_search_keywords_live_preroll), OwnTVIcon.LIVE_TV, TileTone.TERTIARY) { jumpVideo("vp_preroll", false) },
            SettingsSearchEntry(tv.own.owntv.features.settings.videoRowPath("vp_logging"), stringResource(R.string.settings_detailed_playback_logging), stringResource(R.string.settings_search_keywords_detailed_logging), OwnTVIcon.INFO, TileTone.SECONDARY) { jumpVideo("vp_logging", false) },
            SettingsSearchEntry(stringResource(R.string.settings_breadcrumb, stringResource(R.string.settings_group_app), stringResource(R.string.settings_group_network)), stringResource(R.string.common_proxy), stringResource(R.string.settings_search_keywords_proxy), OwnTVIcon.NETWORK, TileTone.SECONDARY) { open(SettingsTab.NETWORK) },
            SettingsSearchEntry(stringResource(R.string.settings_breadcrumb, stringResource(R.string.settings_group_app), stringResource(R.string.settings_group_network)), stringResource(R.string.settings_dns), stringResource(R.string.settings_search_keywords_dns), OwnTVIcon.DNS, TileTone.SECONDARY) { open(SettingsTab.DNS) },
            SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.sports_title), stringResource(R.string.sports_leagues), OwnTVIcon.SPORTS, TileTone.PRIMARY) { open(SettingsTab.SPORTS) },
            // Settings inside a sub-screen, by their own names (kept out of line: this function is at the JVM size limit).
            *subScreenSearchEntries { t -> open(t) }.toTypedArray(),
            SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_app_startup), stringResource(R.string.settings_search_keywords_startup), OwnTVIcon.POWER, TileTone.SECONDARY,
                chip = startupLabel(startupMode)) { saveScroll(); dialogReturn = searchFieldFocus; showStartup = true },
            *autoStartSearchEntries(autoStart).toTypedArray(),
            SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_check_updates), stringResource(R.string.settings_search_keywords_updates), OwnTVIcon.REFRESH, TileTone.PRIMARY,
                chip = "v${tv.own.owntv.BuildConfig.VERSION_NAME}") { saveScroll(); dialogReturn = searchFieldFocus; showUpdate = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_update_startup), stringResource(R.string.settings_search_keywords_update_auto), OwnTVIcon.REFRESH, TileTone.SECONDARY,
                chip = if (updateCheckOnStart) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (updateCheckOnStart) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) },
        )
        // Rows that already have a bespoke entry above. Those are richer than a generic one — they flip
        // in place, or carry a confirmation the search field has to own — so the catalogue skips them
        // rather than listing them a second time.
        val bespokeVideoKeys = setOf(
            "vp_live_preview", "vp_preview_audio", "vp_channel_numbers", "vp_mini", "vp_hdr", "vp_afr",
            "vp_surround", "vp_autoplay", "vp_sub_style", "vp_live_latency", "vp_preroll", "vp_logging",
        )
        // …and every OTHER Video player row, taken straight from the catalogue that screen already draws
        // from. Twenty-four settings had no entry of any kind — Multiview, the engine pickers, the seek
        // and volume steps, the language preferences, the per-playlist overrides — so searching for them
        // by name found nothing, on a screen whose own header offers to search "every setting". Deriving
        // them means a row added there is searchable the day it exists, instead of the day someone
        // remembers to type it out here a second time.
        val videoKeywords = stringResource(R.string.settings_search_keywords_video)
        // Volume, audio sync and audio language: "lip sync" or "loud" is what someone types for these.
        val audioKeywords = stringResource(R.string.settings_search_keywords_audio)
        fun jumpTo(ref: tv.own.owntv.features.settings.VideoQuickRef): () -> Unit = { lastTab = null; jumpVideo(ref.key, false) }
        val videoEntries = tv.own.owntv.features.settings.VIDEO_QUICK_ROWS
            .filterNot { it.key in bespokeVideoKeys }
            .map { ref ->
                // The same binding Quick uses, so a result shows the row's live value and a plain toggle
                // flips right here in the results instead of sending the user to the screen to do it.
                val binding = androidx.compose.runtime.key(ref.key) {
                    tv.own.owntv.features.settings.videoQuickBinding(ref.key, settingsVm)
                }
                val jump = jumpTo(ref)
                SettingsSearchEntry(
                    tv.own.owntv.features.settings.videoRowPath(ref.key),
                    stringResource(ref.titleRes),
                    if (ref.section == tv.own.owntv.features.settings.SECTION_SOUND || ref.key == "vp_audio_lang") audioKeywords else videoKeywords,
                    ref.icon,
                    TileTone.TERTIARY,
                    chip = binding?.chip,
                    chipTone = if (binding?.primaryChip == true) TileTone.PRIMARY else TileTone.SECONDARY,
                    showChevron = binding?.onToggle == null,
                    onClick = binding?.onToggle ?: jump,
                )
            }
        // The rows inside hand-written screens and popups, by their own titles — before, only the
        // screen's name found them ("font" found nothing, although the subtitle popup has a Font row).
        // Each list sits beside its screen's rows. The result opens the screen that holds the row.
        @Composable fun rowsOf(group: String, titles: List<Int>, keywords: Int, icon: OwnTVIcon, onClick: () -> Unit) =
            titles.map { SettingsSearchEntry(group, stringResource(it), stringResource(keywords), icon, TileTone.TERTIARY, onClick = onClick) }
        val subStyleRef = tv.own.owntv.features.settings.VIDEO_QUICK_ROWS.first { it.key == "vp_sub_style" }
        val screenRowEntries =
            rowsOf(stringResource(R.string.settings_breadcrumb, stringResource(R.string.settings_group_watching_recording), stringResource(R.string.recording_settings_group)),
                tv.own.owntv.features.settings.RECORDING_SEARCH_ROWS, R.string.settings_search_keywords_recording, OwnTVIcon.LIVE_TV) { searchQuery = ""; selectedGroup = SettingsGroup.WATCHING.ordinal } +
            rowsOf(stringResource(R.string.settings_breadcrumb, tv.own.owntv.features.settings.videoRowPath("vp_sub_style"), stringResource(R.string.settings_subtitle_appearance)),
                tv.own.owntv.features.settings.SUBTITLE_APPEARANCE_SEARCH_ROWS, R.string.settings_search_keywords_subtitle_appearance, OwnTVIcon.SUBTITLE, jumpTo(subStyleRef)) +
            rowsOf(stringResource(R.string.settings_breadcrumb, stringResource(R.string.settings_breadcrumb, stringResource(R.string.settings_group_app), stringResource(R.string.settings_group_network)), stringResource(R.string.common_proxy)),
                tv.own.owntv.features.settings.PROXY_SEARCH_ROWS, R.string.settings_search_keywords_proxy, OwnTVIcon.NETWORK) { open(SettingsTab.NETWORK) } +
            rowsOf(stringResource(R.string.settings_breadcrumb, stringResource(R.string.settings_breadcrumb, stringResource(R.string.settings_group_app), stringResource(R.string.settings_group_network)), stringResource(R.string.settings_dns)),
                tv.own.owntv.features.settings.DNS_SEARCH_ROWS, R.string.settings_search_keywords_dns, OwnTVIcon.DNS) { open(SettingsTab.DNS) } +
            // The Catch-up popup's second row; its first (the time zone) is the Catch-up entry above.
            rowsOf(stringResource(R.string.settings_breadcrumb, stringResource(R.string.settings_group_sources), stringResource(R.string.settings_catchup)),
                listOf(R.string.settings_catchup_player), R.string.settings_search_keywords_catchup, OwnTVIcon.CATCHUP) { saveScroll(); dialogReturn = searchFieldFocus; showCatchupTime = true }
        // A setting is found by its own name only: "play" lists every setting with "play" in its name.
        val needle = searchQuery.trim().lowercase()
        (entries + videoEntries + screenRowEntries)
            .filter { e -> e.title.lowercase().contains(needle) }
            .distinctBy { it.group + it.title }
    }
    val searching = searchQuery.isNotBlank() || searchMode
    // Back from an empty search: to More when it was opened there, else to the group page.
    BackHandler(enabled = tab == SettingsTab.ROOT && searchMode && searchQuery.isBlank()) {
        searchMode = false
        if (searchFromMore) { searchFromMore = false; onBack?.invoke() }
    }
    val group = SettingsGroup.entries[selectedGroup.coerceIn(0, SettingsGroup.entries.size - 1)]
    val inQuick = group == SettingsGroup.QUICK
    val pageCount = when {
        searching && searchQuery.isBlank() -> ""
        searching -> pluralStringResource(R.plurals.settings_match_count, searchResults.size, searchResults.size)
        group.video != null -> videoGroupCount(group.video, settingsVm).let { n ->
            val all = n + if (group == SettingsGroup.WATCHING) 4 else 0
            pluralStringResource(R.plurals.settings_setting_count, all, all)
        }
        group == SettingsGroup.PROFILE -> tv.own.owntv.features.settings.profileCount().let { n ->
            pluralStringResource(R.plurals.settings_profile_count, n, n)
        }
        else -> (selectedRows.size + if (group == SettingsGroup.APP) videoGroupCount(VideoGroup.DIAGNOSTICS, settingsVm) else 0).let { n ->
            pluralStringResource(R.plurals.settings_setting_count, n, n)
        }
    }
    val extras = stageRowExtras(
        settingsVm, themeMode, uiZoomPercent, onSetZoom,
        onOpenZoom = { saveScroll(); dialogReturn = zoomRowFocus; showZoom = true },
        fontCustomization, onSetFontCustomization, playlistSources,
    )
    // Shared with the dialogs below, so the simple ones open in the page's panel.
    val panel = remember { tv.own.owntv.features.settings.SettingsPanelState() }
    CompositionLocalProvider(
        // Subtitle appearance is a page of its own, opened from the Sound & subtitles rows.
        tv.own.owntv.features.settings.LocalOpenSubtitleStyle provides { saveScroll(); open(SettingsTab.SUBTITLE_STYLE) },
        tv.own.owntv.features.settings.LocalSubtitleStyleRowFocus provides rowFocus.getValue(SettingsTab.SUBTITLE_STYLE),
    ) {
        StageSettingsPage(
            panel = panel,
            group = if (searching) stringResource(R.string.common_nav_settings) else stringResource(group.titleRes),
            count = pageCount,
            searchQuery = searchQuery,
            onSearchQuery = { searchQuery = it },
            scroll = pageScroll,
            crumb = !searching,
            searchFocus = searchFieldFocus,
            rowsFocus = rowsFocus,
            modifier = modifier,
            searchAutoEdit = searchAutoEdit,
            panelTop = if (group == SettingsGroup.APPEARANCE && !searching) ({ tv.own.owntv.features.settings.SettingsLivePreview() }) else null,
        ) {
            when {
                searching && searchQuery.isBlank() -> Unit
                searching && searchResults.isEmpty() -> StageSettingsNote(stringResource(R.string.settings_no_settings_match, searchQuery.trim()), null)
                searching -> searchResults.forEach { e ->
                    StageSearchResultRow(e.icon, e.group, e.title, e.chip, onClick = e.onClick)
                }
                group.video != null -> {
                    VideoPlayerGroupRows(
                        group.video, pageScroll,
                        openMiniPlayer = openMiniPlayer,
                        focusRowKey = videoRowKey,
                    )
                    if (group == SettingsGroup.WATCHING) tv.own.owntv.features.settings.RecordingSettingsRows(pageScroll)
                }
                group == SettingsGroup.PROFILE -> tv.own.owntv.features.settings.ProfileSettingsRows()
                // Only Quick can be empty, and only because the user emptied it.
                selectedRows.isEmpty() -> StageSettingsNote(stringResource(R.string.settings_quick_empty_title), stringResource(R.string.settings_quick_empty_hint))
                else -> {
                    selectedRows.forEachIndexed { i, row ->
                        row.heading?.let { heading ->
                            val under = selectedRows.drop(i).let { rest -> 1 + rest.drop(1).takeWhile { it.heading == null }.size }
                            StageSettingsHeading(heading, under, first = i == 0)
                        }
                        RootRowStage(
                            row,
                            // Inside Quick every row is pinned by definition — the dot would say nothing.
                            pinned = !inQuick && row.key in quickPinned,
                            inQuick = inQuick,
                            extra = extras[row.key],
                            onLongClick = { menuRow = row },
                            focus = if (row.key == menuReturnKey) menuReturnFocus else null,
                        )
                    }
                    if (group == SettingsGroup.APP) VideoPlayerGroupRows(VideoGroup.DIAGNOSTICS, pageScroll, firstHeading = false)
                }
            }
        }
    }

    // Every dialog below shares the page's panel: the simple ones open there instead of as a popup.
    CompositionLocalProvider(tv.own.owntv.features.settings.LocalSettingsPanel provides panel) {
    menuRow?.let { row ->
        val at = quickPinned.indexOf(row.key)
        // Order is a property of the Quick list, so it is only offered where that list is on screen.
        // In a row's home group the only thing the menu can usefully say is whether it is pinned.
        SettingsRowMenu(
            title = row.title,
            pinned = at >= 0,
            canMoveUp = inQuick && at > 0,
            canMoveDown = inQuick && at >= 0 && at < quickPinned.size - 1,
            onPinToggle = {
                // Unpinning from inside Quick takes the row out from under the cursor, so hand focus
                // to the row that slides into its place — the one below, or the one above when it was
                // last. Without this the sheet has nothing focused and the highlight drops to the spine.
                if (at >= 0 && inQuick) {
                    unpinReturnKey = quickPinned.getOrNull(at + 1) ?: quickPinned.getOrNull(at - 1)
                }
                settingsVm.setQuickPinnedKeys(
                    if (at >= 0) quickPinned - row.key else quickPinned + row.key,
                )
            },
            onMoveUp = {
                settingsVm.setQuickPinnedKeys(
                    quickPinned.toMutableList().apply { add(at - 1, removeAt(at)) },
                )
            },
            onMoveDown = {
                settingsVm.setQuickPinnedKeys(
                    quickPinned.toMutableList().apply { add(at + 1, removeAt(at)) },
                )
            },
            onDismiss = {
                menuReturnKey = unpinReturnKey ?: row.key
                unpinReturnKey = null
                menuRow = null
            },
        )
    }

    if (showUpdate) {
        UpdateDialog(onDismiss = { showUpdate = false }, checkOnOpen = true)
    }
    if (showCatchupTime) CatchupTimeHost(settingsVm, catchupTz, catchupOffset, catchupPlayer) { showCatchupTime = false }
    if (showCatchupSources) {
        CatchupSourcesHost(playlistSources, catchupSource, onPick = { picked ->
            catchupSource = picked
            showCatchupSourceValue = picked != null
            showCatchupSources = false
        }, onDismiss = { catchupSource = null; showCatchupSources = false })
    }
    if (showCatchupSourceValue) {
        CatchupSourceValueHost(settingsVm, playlistSources.firstOrNull { it.id == catchupSource?.id } ?: catchupSource) {
            showCatchupSources = true
            showCatchupSourceValue = false
        }
    }
    if (showEpgOffset) EpgOffsetRootDialog(settingsVm, epgOffset, onClose = { showEpgOffset = false })
    if (showGuideDays) GuideDaysDialog(settingsVm, guideDays, onClose = { showGuideDays = false })
    if (showTheme) ThemeDialogHost(settingsVm, themeMode, onClose = { showTheme = false })
    if (showStartup) StartupDialogHost(settingsVm, startupMode, onClose = { showStartup = false }, onPickChannel = { showStartupChannelPicker = true })
    if (showStartupChannelPicker) StartupChannelPickerHost(settingsVm, startupChannel, onClose = { showStartupChannelPicker = false })
    if (showAnimations) AnimationsDialogHost(settingsVm, animationLevel, onClose = { showAnimations = false })
    if (showNavigation) {
        NavigationPopupHost(settingsVm, onDismiss = { showNavigation = false })
    }
    if (showVodLayout) VodLayoutDialog(settingsVm, vodLayout, onClose = { showVodLayout = false })
    if (showLiveLayout) LiveLayoutDialog(settingsVm, onClose = { showLiveLayout = false })
    StageSettingsDialogHost(settingsVm, stageSettings)
    if (showFocusHighlight) {
        FocusHighlightDialog(
            highlight = focusHighlight,
            widthDp = focusHighlightWidth,
            onPickColor = { settingsVm.setFocusHighlight(it) },
            onPickWidth = { settingsVm.setFocusHighlightWidth(it) },
            onDismiss = { showFocusHighlight = false },
        )
    }
    if (showAccent) {
        tv.own.owntv.features.settings.AccentPopup(
            accent = accent,
            customAccent = customAccent,
            onPickPreset = { settingsVm.setAccent(it) },
            onPickCustom = { settingsVm.setCustomAccent(it) },
            onDismiss = { showAccent = false },
        )
    }
    if (showZoom) {
        ZoomDialog(current = uiZoomPercent, onSet = onSetZoom, onDismiss = { showZoom = false })
    }
    if (showAppIcon) {
        tv.own.owntv.ui.components.AppIconSettingsDialog(
            chosen = appIcon,
            onPick = settingsVm::setAppIcon,
            onDismiss = { showAppIcon = false },
        )
    }
    if (showPopupSize) {
        PopupSizeDialog(
            current = fontCustomization.popupSizePercent,
            onSet = { onSetFontCustomization(fontCustomization.copy(popupSizePercent = it)) },
            onDismiss = { showPopupSize = false },
        )
    }
    if (showAmbientGlow) AmbientGlowHost(settingsVm, ambientGlowEnabled, ambientGlowPulse, onClose = { showAmbientGlow = false })
    if (showAfrWarning) {
        AutoFrameRateWarningDialog(
            onEnable = { settingsVm.setAutoFrameRate(true); showAfrWarning = false },
            onDismiss = { showAfrWarning = false },
        )
    }
    if (showLivePreviewPanelWarning) {
        LivePreviewPanelHiddenDialog(onDismiss = { showLivePreviewPanelWarning = false })
    }
    if (showBgImageChooser) {
        BackgroundImageChooserDialog(
            hasImage = bgImagePath.isNotBlank(),
            onPickLocal = { showBgImageChooser = false; showBgPicker = true },
            onPickRemote = { showBgImageChooser = false; showBgRemote = true },
            onClear = { settingsVm.setBgImagePath(""); showBgImageChooser = false },
            onDismiss = { showBgImageChooser = false },
        )
    }
    if (showBgRemote) {
        val context = LocalContext.current
        val remoteState by settingsVm.remoteState.collectAsStateWithLifecycle()
        tv.own.owntv.ui.components.RemoteBackgroundDialog(
            state = remoteState,
            images = settingsVm.remoteImages,
            onStart = settingsVm::startRemoteImageListener,
            onStop = settingsVm::stopRemoteListener,
            onImageReceived = { file ->
                // Same ingest as the local pick: copy into app-private storage, then drop the cache temp.
                val destDir = File(context.filesDir, "backgrounds")
                ingestScope.launch {
                    val path = withContext(Dispatchers.IO) {
                        runCatching { ingestBackgroundImage(file, destDir) }.getOrNull()
                            .also { runCatching { file.delete() } }
                    }
                    if (path != null) settingsVm.setNewBackgroundPicture(path)
                }
                showBgRemote = false
            },
            onDismiss = { showBgRemote = false; showBgImageChooser = true }, // back one level
        )
    }
    if (showBgPicker) {
        val context = LocalContext.current
        StorageBrowser(
            title = stringResource(R.string.settings_pick_background_title),
            mode = BrowseMode.FILE,
            fileExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp"),
            onPick = { file ->
                // Copy into app-private storage so USB unplug / source-folder delete can't blank it.
                val destDir = File(context.filesDir, "backgrounds")
                ingestScope.launch {
                    val path = withContext(Dispatchers.IO) {
                        runCatching { ingestBackgroundImage(file, destDir) }.getOrNull()
                    }
                    if (path != null) settingsVm.setNewBackgroundPicture(path)
                }
                showBgPicker = false
            },
            // Back goes back one level, to the local/remote/clear chooser this was opened from —
            // closing both would leave the user on the Glass & background row two steps up.
            onDismiss = { showBgPicker = false; showBgImageChooser = true },
        )
    }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.SYSTEM -> R.string.settings_theme_system
    },
)

/** Settings › Layout › Navigation, hosted outside [SettingsScreen] (whose body is at the JVM method-size limit). */
@Composable
private fun NavigationPopupHost(settingsVm: SettingsViewModel, onDismiss: () -> Unit) {
    val navStyle by settingsVm.navStyle.collectAsStateWithLifecycle()
    val navSize by settingsVm.navSize.collectAsStateWithLifecycle()
    val navLength by settingsVm.navLength.collectAsStateWithLifecycle()
    val navWiden by settingsVm.navWiden.collectAsStateWithLifecycle()
    val navHideAfterMs by settingsVm.navHideAfterMs.collectAsStateWithLifecycle()
    val navMenuMode by settingsVm.navMenuMode.collectAsStateWithLifecycle()
    val navMenuHidden by settingsVm.navMenuHidden.collectAsStateWithLifecycle()
    tv.own.owntv.features.settings.NavigationSettingsPopup(
        style = navStyle,
        onStyle = settingsVm::setNavStyle,
        size = navSize,
        onSize = settingsVm::setNavSize,
        length = navLength,
        onLength = settingsVm::setNavLength,
        widen = navWiden,
        onWiden = settingsVm::setNavWiden,
        hideAfterMs = navHideAfterMs,
        onHideAfterMs = settingsVm::setNavHideAfterMs,
        menuMode = navMenuMode,
        onMenuMode = settingsVm::setNavMenuMode,
        hiddenSections = navMenuHidden,
        onSectionHidden = settingsVm::setNavSectionHidden,
        onDismiss = onDismiss,
    )
}

/**
 * The Movies & Series layout picker. Moved out of [SettingsScreen] unchanged, because that function's
 * body had reached the JVM's 64 KB method-size limit and the Navigation row did not fit.
 */
@Composable
private fun VodLayoutDialog(
    settingsVm: SettingsViewModel,
    vodLayout: tv.own.owntv.core.settings.SettingsRepository.VodLayout,
    onClose: () -> Unit,
) {
    val layouts = tv.own.owntv.core.settings.SettingsRepository.VodLayout.entries
    tv.own.owntv.features.settings.PickerDialog(
        title = stringResource(R.string.settings_vod_layout),
        subtitle = stringResource(R.string.settings_vod_layout_screen_description),
        options = layouts.map { it.name to stringResource(vodLayoutLabelRes(it)) },
        descriptions = layouts.associate {
            it.name to stringResource(
                if (it == tv.own.owntv.core.settings.SettingsRepository.VodLayout.CINEMATIC) {
                    R.string.settings_vod_layout_cinematic_description
                } else {
                    R.string.settings_vod_layout_separate_description
                },
            )
        },
        optionPreview = { value ->
            VodLayoutPreviewBars(tv.own.owntv.core.settings.SettingsRepository.VodLayout.valueOf(value))
        },
        selected = vodLayout.name,
        onSelect = {
            settingsVm.setVodLayout(tv.own.owntv.core.settings.SettingsRepository.VodLayout.valueOf(it))
            onClose()
        },
        onDismiss = onClose,
    )
}

/** The Layout group's Navigation row, built here to keep [SettingsScreen] under the JVM method-size limit. */
@Composable
private fun navigationRootRow(settingsVm: SettingsViewModel, focus: FocusRequester, onClick: () -> Unit): RootRow {
    val style by settingsVm.navStyle.collectAsStateWithLifecycle()
    return RootRow(
        "navigation", TileTone.PRIMARY, OwnTVIcon.MENU,
        title = stringResource(R.string.settings_navigation),
        desc = stringResource(R.string.settings_navigation_description),
        chip = navStyleLabel(style),
        chipTone = TileTone.PRIMARY,
        focus = focus,
        onClick = onClick,
    )
}

/** The Navigation row's settings-search entry (see [navigationRootRow]). */
@Composable
private fun navigationSearchEntry(settingsVm: SettingsViewModel, onClick: () -> Unit): SettingsSearchEntry {
    val style by settingsVm.navStyle.collectAsStateWithLifecycle()
    return SettingsSearchEntry(
        stringResource(R.string.settings_group_layout), stringResource(R.string.settings_navigation),
        stringResource(R.string.settings_search_keywords_navigation), OwnTVIcon.MENU, TileTone.PRIMARY,
        chip = navStyleLabel(style), chipTone = TileTone.PRIMARY, onClick = onClick,
    )
}

/** The Layout group's Live TV layout row: Stage, or Separate panels. */
@Composable
private fun liveLayoutRootRow(settingsVm: SettingsViewModel, focus: FocusRequester, onClick: () -> Unit): RootRow {
    val layout by settingsVm.liveLayout.collectAsStateWithLifecycle()
    return RootRow(
        "live_layout", TileTone.PRIMARY, OwnTVIcon.LIVE_TV,
        title = stringResource(R.string.settings_live_layout),
        desc = stringResource(R.string.settings_live_layout_description),
        chip = stringResource(liveLayoutLabelRes(layout)),
        chipTone = TileTone.PRIMARY,
        focus = focus,
        onClick = onClick,
    )
}

@Composable
private fun liveLayoutSearchEntry(settingsVm: SettingsViewModel, onClick: () -> Unit): SettingsSearchEntry {
    val layout by settingsVm.liveLayout.collectAsStateWithLifecycle()
    return SettingsSearchEntry(
        stringResource(R.string.settings_group_layout), stringResource(R.string.settings_live_layout),
        stringResource(R.string.settings_search_keywords_live_layout), OwnTVIcon.LIVE_TV, TileTone.PRIMARY,
        chip = stringResource(liveLayoutLabelRes(layout)), chipTone = TileTone.PRIMARY, onClick = onClick,
    )
}

private fun liveLayoutLabelRes(layout: tv.own.owntv.core.settings.SettingsRepository.LiveLayout): Int =
    if (layout == tv.own.owntv.core.settings.SettingsRepository.LiveLayout.SEPARATE) R.string.settings_vod_layout_separate
    else R.string.settings_live_layout_stage

@Composable
private fun LiveLayoutDialog(settingsVm: SettingsViewModel, onClose: () -> Unit) {
    val current by settingsVm.liveLayout.collectAsStateWithLifecycle()
    val layouts = tv.own.owntv.core.settings.SettingsRepository.LiveLayout.entries
    tv.own.owntv.features.settings.PickerDialog(
        title = stringResource(R.string.settings_live_layout),
        subtitle = stringResource(R.string.settings_live_layout_description),
        options = layouts.map { it.name to stringResource(liveLayoutLabelRes(it)) },
        descriptions = layouts.associate {
            it.name to stringResource(
                if (it == tv.own.owntv.core.settings.SettingsRepository.LiveLayout.SEPARATE) R.string.settings_live_layout_separate_description
                else R.string.settings_live_layout_stage_description,
            )
        },
        selected = current.name,
        onSelect = {
            settingsVm.setLiveLayout(tv.own.owntv.core.settings.SettingsRepository.LiveLayout.valueOf(it))
            onClose()
        },
        onDismiss = onClose,
    )
}

// Moved out of SettingsScreen unchanged: that function sits at the JVM's 64 KB method limit.
@Composable
private fun ThemeDialogHost(settingsVm: SettingsViewModel, themeMode: ThemeMode, onClose: () -> Unit) {
    tv.own.owntv.features.settings.PickerDialog(
        title = stringResource(R.string.settings_theme_dialog),
        options = ThemeMode.entries.map { it.name to themeLabel(it) },
        selected = themeMode.name,
        onSelect = { settingsVm.setThemeMode(ThemeMode.valueOf(it)); onClose() },
        onDismiss = { onClose() },
    )
}

@Composable
private fun AnimationsDialogHost(settingsVm: SettingsViewModel, animationLevel: tv.own.owntv.core.theme.AnimationLevel, onClose: () -> Unit) {
    tv.own.owntv.features.settings.PickerDialog(
        title = stringResource(R.string.settings_animations_dialog),
        options = tv.own.owntv.core.theme.AnimationLevel.entries.map { it.name to stringResource(it.labelRes) },
        selected = animationLevel.name,
        onSelect = { settingsVm.setAnimationLevel(tv.own.owntv.core.theme.AnimationLevel.valueOf(it)); onClose() },
        onDismiss = { onClose() },
    )
}

@Composable
private fun AmbientGlowHost(settingsVm: SettingsViewModel, ambientGlowEnabled: Boolean, ambientGlowPulse: Boolean, onClose: () -> Unit) {
    AmbientGlowDialog(
        glowEnabled = ambientGlowEnabled,
        pulseEnabled = ambientGlowPulse,
        onToggleGlow = { settingsVm.setAmbientGlowEnabled(!ambientGlowEnabled) },
        onTogglePulse = { settingsVm.setAmbientGlowPulse(!ambientGlowPulse) },
        onDismiss = onClose,
    )
}

@Composable
private fun EpgOffsetRootDialog(settingsVm: SettingsViewModel, epgOffset: Int, onClose: () -> Unit) {
    EpgOffsetSettingDialog(
        offsetMinutes = epgOffset,
        offsetRange = settingsVm.epgOffsetRangeMinutes,
        onAdjust = settingsVm::adjustEpgOffset,
        onReset = { settingsVm.setEpgOffsetMinutes(0) },
        onDismiss = onClose,
    )
}

@Composable
private fun GuideDaysDialog(settingsVm: SettingsViewModel, guideDays: Int, onClose: () -> Unit) {
    tv.own.owntv.ui.components.DayStepperDialog(
        title = stringResource(R.string.settings_epg_guide_days),
        hint = stringResource(
            R.string.settings_epg_guide_days_hint,
            tv.own.owntv.core.settings.GuideRetention.MIN_DAYS,
            tv.own.owntv.core.settings.GuideRetention.MAX_DAYS,
        ),
        initialDays = guideDays,
        minDays = tv.own.owntv.core.settings.GuideRetention.MIN_DAYS,
        maxDays = tv.own.owntv.core.settings.GuideRetention.MAX_DAYS,
        label = { days -> pluralStringResource(R.plurals.settings_epg_guide_days_value, days, days) },
        onConfirm = { settingsVm.setGuideDaysToKeep(it); onClose() },
        onDismiss = onClose,
    )
}

private enum class StageSettingsDialog { LIVE_OPENS_IN, REMINDERS, REMINDER_LEAD }

/** Which Stage picker is open, and each row's focus to come back to. */
@androidx.compose.runtime.Stable
private class StageSettingsState {
    var open by mutableStateOf<StageSettingsDialog?>(null)
    val focus = StageSettingsDialog.entries.associateWith { FocusRequester() }
}

/** Layout: Live TV opens in. [onOpen] saves the scroll and names the focus to return to. */
@Composable
private fun stageLayoutRows(settingsVm: SettingsViewModel, state: StageSettingsState, onOpen: (FocusRequester) -> Unit): List<RootRow> {
    val f = state.focus.getValue(StageSettingsDialog.LIVE_OPENS_IN)
    return listOf(liveOpensInRootRow(settingsVm, f) { onOpen(f); state.open = StageSettingsDialog.LIVE_OPENS_IN })
}

/** The guide block: Programme reminders, Reminder time. */
@Composable
private fun stageGuideRows(settingsVm: SettingsViewModel, state: StageSettingsState, onOpen: (FocusRequester) -> Unit): List<RootRow> {
    val r = state.focus.getValue(StageSettingsDialog.REMINDERS)
    val l = state.focus.getValue(StageSettingsDialog.REMINDER_LEAD)
    return listOf(
        remindersRootRow(settingsVm, r) { onOpen(r); state.open = StageSettingsDialog.REMINDERS },
        reminderLeadRootRow(settingsVm, l) { onOpen(l); state.open = StageSettingsDialog.REMINDER_LEAD },
    )
}

@Composable
private fun StageSettingsDialogHost(settingsVm: SettingsViewModel, state: StageSettingsState) {
    val close = { state.open = null }
    when (state.open) {
        StageSettingsDialog.LIVE_OPENS_IN -> LiveOpensInDialog(settingsVm, close)
        StageSettingsDialog.REMINDERS -> RemindersDialog(settingsVm, close)
        StageSettingsDialog.REMINDER_LEAD -> ReminderLeadDialog(settingsVm, close)
        null -> Unit
    }
}

/** Layout › Live TV opens in: List view or Guide view — the same value Live TV's toggle sets. */
@Composable
private fun liveOpensInRootRow(settingsVm: SettingsViewModel, focus: FocusRequester, onClick: () -> Unit): RootRow {
    val view by settingsVm.liveView.collectAsStateWithLifecycle()
    return RootRow(
        "live_opens_in", TileTone.PRIMARY, OwnTVIcon.LIVE_TV,
        title = stringResource(R.string.settings_live_opens_in),
        desc = stringResource(R.string.settings_live_opens_in_description),
        chip = stringResource(liveViewLabelRes(view)),
        chipTone = TileTone.PRIMARY,
        focus = focus,
        onClick = onClick,
    )
}

private fun liveViewLabelRes(view: tv.own.owntv.core.settings.SettingsRepository.LiveView): Int =
    if (view == tv.own.owntv.core.settings.SettingsRepository.LiveView.GUIDE) R.string.content_live_guide_view else R.string.content_live_list_view

@Composable
private fun LiveOpensInDialog(settingsVm: SettingsViewModel, onClose: () -> Unit) {
    val current by settingsVm.liveView.collectAsStateWithLifecycle()
    tv.own.owntv.features.settings.PickerDialog(
        title = stringResource(R.string.settings_live_opens_in),
        subtitle = stringResource(R.string.settings_live_opens_in_description),
        options = tv.own.owntv.core.settings.SettingsRepository.LiveView.entries.map { it.name to stringResource(liveViewLabelRes(it)) },
        selected = current.name,
        onSelect = { settingsVm.setLiveView(tv.own.owntv.core.settings.SettingsRepository.LiveView.valueOf(it)); onClose() },
        onDismiss = onClose,
    )
}

/** The guide block's Programme reminders: Ask to switch · Switch · Notify only. */
@Composable
private fun remindersRootRow(settingsVm: SettingsViewModel, focus: FocusRequester, onClick: () -> Unit): RootRow {
    val mode by settingsVm.reminderMode.collectAsStateWithLifecycle()
    return RootRow(
        "reminders", TileTone.SECONDARY, OwnTVIcon.BELL,
        title = stringResource(R.string.settings_reminders),
        desc = stringResource(R.string.settings_reminders_description),
        chip = stringResource(reminderModeLabelRes(mode)),
        chipTone = TileTone.PRIMARY,
        focus = focus,
        onClick = onClick,
    )
}

private fun reminderModeLabelRes(mode: tv.own.owntv.core.settings.SettingsRepository.ReminderMode): Int = when (mode) {
    tv.own.owntv.core.settings.SettingsRepository.ReminderMode.ASK -> R.string.settings_reminder_ask
    tv.own.owntv.core.settings.SettingsRepository.ReminderMode.SWITCH -> R.string.settings_reminder_switch
    tv.own.owntv.core.settings.SettingsRepository.ReminderMode.NOTIFY -> R.string.settings_reminder_notify
}

@Composable
private fun RemindersDialog(settingsVm: SettingsViewModel, onClose: () -> Unit) {
    val current by settingsVm.reminderMode.collectAsStateWithLifecycle()
    tv.own.owntv.features.settings.PickerDialog(
        title = stringResource(R.string.settings_reminders),
        subtitle = stringResource(R.string.settings_reminders_description),
        options = tv.own.owntv.core.settings.SettingsRepository.ReminderMode.entries.map { it.name to stringResource(reminderModeLabelRes(it)) },
        selected = current.name,
        onSelect = { settingsVm.setReminderMode(tv.own.owntv.core.settings.SettingsRepository.ReminderMode.valueOf(it)); onClose() },
        onDismiss = onClose,
    )
}

/** Reminder time: at the start, or 1 or 5 minutes before. */
@Composable
private fun reminderLeadRootRow(settingsVm: SettingsViewModel, focus: FocusRequester, onClick: () -> Unit): RootRow {
    val lead by settingsVm.reminderLeadMinutes.collectAsStateWithLifecycle()
    return RootRow(
        "reminder_lead", TileTone.SECONDARY, OwnTVIcon.CLOCK,
        title = stringResource(R.string.settings_reminder_lead),
        desc = stringResource(R.string.settings_reminder_lead_description),
        chip = tv.own.owntv.features.live.reminderLeadText(lead),
        chipTone = TileTone.PRIMARY,
        focus = focus,
        onClick = onClick,
    )
}

@Composable
private fun ReminderLeadDialog(settingsVm: SettingsViewModel, onClose: () -> Unit) {
    val current by settingsVm.reminderLeadMinutes.collectAsStateWithLifecycle()
    tv.own.owntv.features.settings.PickerDialog(
        title = stringResource(R.string.settings_reminder_lead),
        subtitle = stringResource(R.string.settings_reminder_lead_description),
        options = tv.own.owntv.core.reminder.ReminderSchedule.LEAD_CHOICES.map { it.toString() to tv.own.owntv.features.live.reminderLeadText(it) },
        selected = current.toString(),
        onSelect = { settingsVm.setReminderLeadMinutes(it.toInt()); onClose() },
        onDismiss = onClose,
    )
}

/** Chip label for the Navigation row: the chosen rail style. */
@Composable
private fun navStyleLabel(style: tv.own.owntv.core.settings.SettingsRepository.NavStyle): String = stringResource(
    when (style) {
        tv.own.owntv.core.settings.SettingsRepository.NavStyle.FLOATING -> R.string.settings_nav_floating
        tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED -> R.string.settings_nav_docked
    },
)

/** Chip and option label for the Movies & Series layout choice. */
private fun vodLayoutLabelRes(layout: tv.own.owntv.core.settings.SettingsRepository.VodLayout): Int =
    if (layout == tv.own.owntv.core.settings.SettingsRepository.VodLayout.CINEMATIC) {
        R.string.settings_vod_layout_cinematic
    } else {
        R.string.settings_vod_layout_separate
    }

/**
 * The little bar diagram under each layout option, so the choice is legible at TV distance without
 * reading the description.
 *
 * Separate panels is three plain bars — categories, the list, the preview. Cinematic is two: the
 * category rail, dimmed because it floats as glass, and one wide block carrying the artwork's own
 * colour, because in that layout the picture IS the background.
 */
@Composable
private fun VodLayoutPreviewBars(layout: tv.own.owntv.core.settings.SettingsRepository.VodLayout) {
    val bar = tv.own.owntv.ui.theme.StageColors.Text.copy(alpha = 0.13f)
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (layout == tv.own.owntv.core.settings.SettingsRepository.VodLayout.CINEMATIC) {
            Box(Modifier.width(48.dp).fillMaxHeight().clip(shape).background(bar.copy(alpha = 0.07f)))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(tv.own.owntv.ui.theme.stageAccent.accent.copy(alpha = 0.45f), tv.own.owntv.ui.theme.stageAccent.accent.copy(alpha = 0.28f)),
                        ),
                    ),
            )
        } else {
            Box(Modifier.width(48.dp).fillMaxHeight().clip(shape).background(bar))
            Box(Modifier.weight(1f).fillMaxHeight().clip(shape).background(bar))
            Box(Modifier.width(64.dp).fillMaxHeight().clip(shape).background(bar))
        }
    }
}

/** Chip text for the Language settings row: system-default label, or the selected locale's endonym. */
@Composable
private fun languageChipText(tag: String): String {
    if (tag.isEmpty()) return stringResource(R.string.settings_language_system_default)
    return SupportedLocales.all.find { it.languageTag == tag }?.endonym
        ?: stringResource(R.string.settings_language_system_default)
}

/** The six quick presets shown at the top of the accent picker. */
private val AccentPresetChoices: List<tv.own.owntv.core.theme.AccentColor> =
    tv.own.owntv.core.theme.AccentColor.entries.take(6)

/**
 * Focus highlight presets: the six accent presets plus gold and white, which are the two
 * colors people actually ask for when they want the cursor to shout. Hex, so a preset and a
 * hand-typed color are the same stored value — there is no second "preset" concept to keep in sync.
 */
private val FocusHighlightPresets: List<String> = listOf("#F5B400", "#FFFFFF") +
    AccentPresetChoices.map { ac -> "#%06X".format(java.util.Locale.ROOT, ac.primary(true).toArgb() and 0xFFFFFF) }

/** Row chip for the focus highlight, e.g. "#F5B400 · Thick" or "Default · Normal". */
@Composable
private fun focusHighlightChip(highlight: String, widthDp: Int): String = stringResource(
    R.string.settings_focus_highlight_chip,
    // Only the hex is uppercased — a translated "Default" must keep its own casing.
    if (highlight.isBlank()) stringResource(R.string.settings_subtitle_default) else highlight.uppercase(),
    focusWidthLabel(widthDp),
)

/** Short label for a focus ring width, for the chip on the row and the thickness buttons. */
@Composable
private fun focusWidthLabel(dp: Int): String = stringResource(
    when (dp) {
        1 -> R.string.settings_focus_width_thin
        4 -> R.string.settings_focus_width_thick
        6 -> R.string.settings_focus_width_extra
        else -> R.string.settings_focus_width_normal
    },
)

/**
 * Focus highlight picker: presets, hex field and the shared HSV palette pick the ring color;
 * four buttons pick its width. A live sample sits under the controls because the dialog itself is
 * still drawn with the *saved* values — without it you could not judge a color before committing.
 * "Reset" clears the color back to the accent.
 */
@Composable
private fun FocusHighlightDialog(
    highlight: String,
    widthDp: Int,
    onPickColor: (String) -> Unit,
    onPickWidth: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // The Stage colour popup, as Accent colour and the clock colours; its preview is the ring
    // itself and the thickness under it.
    val start = remember { highlight }
    val ring = tv.own.owntv.ui.theme.parseAccentHex(highlight) ?: tv.own.owntv.ui.theme.stageAccent.focus
    val defaultRing = tv.own.owntv.ui.theme.stageAccent.focus
    tv.own.owntv.features.settings.StageColorPopup(
        eyebrow = stringResource(R.string.settings_group_appearance),
        title = stringResource(R.string.settings_focus_highlight),
        presets = listOf(tv.own.owntv.features.settings.ColorChoice(defaultRing, stringResource(R.string.settings_subtitle_default)) { onPickColor("") }) +
            // The same seven as before, named rather than written as codes.
            (listOf(R.string.settings_subtitle_color_yellow, R.string.settings_clock_color_white) + AccentPresetChoices.map { it.labelRes })
                .zip(FocusHighlightPresets) { label, hex ->
                    val c = tv.own.owntv.ui.theme.parseAccentHex(hex) ?: defaultRing
                    tv.own.owntv.features.settings.ColorChoice(c, stringResource(label)) { onPickColor(hex) }
                },
        start = ring,
        current = ring,
        onLive = onPickColor,
        onCancel = { onPickColor(start) },
        onDone = { hex -> if (hex != null) onPickColor(hex) },
        onDismiss = onDismiss,
    ) {
        Column(Modifier.padding(top = 20.mpx)) {
            Box(
                Modifier.fillMaxWidth().height(64.mpx)
                    .background(StageColors.Ink.copy(alpha = 0.04f), RoundedCornerShape(18.mpx))
                    .border(widthDp.dp, ring, RoundedCornerShape(18.mpx)),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.settings_focus_highlight_sample), style = tv.own.owntv.ui.theme.stageText(19, 700), color = tv.own.owntv.ui.theme.StageColors.Text)
            }
            Text(
                stringResource(R.string.settings_focus_thickness).uppercase(),
                style = tv.own.owntv.ui.theme.stageText(14, 800, androidx.compose.ui.unit.TextUnit(0.12f, androidx.compose.ui.unit.TextUnitType.Em)),
                color = tv.own.owntv.ui.theme.StageColors.Dim,
                modifier = Modifier.padding(top = 18.mpx, bottom = 4.mpx),
            )
            val widths = tv.own.owntv.ui.theme.FocusBorderWidthChoices
            tv.own.owntv.ui.stage.StageMenuChoice(
                options = widths.map { focusWidthLabel(it) },
                selected = widths.indexOf(widthDp),
                onSelect = { onPickWidth(widths[it]) },
            )
        }
    }
}


/** The global catch-up time and player dialog, moved out of [SettingsScreen] unchanged (its body is at the JVM method limit). */
@Composable
private fun CatchupTimeHost(
    settingsVm: SettingsViewModel,
    mode: SettingsRepository.CatchupTimezone,
    offsetMinutes: Int,
    player: SettingsRepository.CatchupPlayer,
    onDismiss: () -> Unit,
) {
    CatchupTimeDialog(
            mode = mode,
            offsetMinutes = offsetMinutes,
            offsetRange = settingsVm.catchupOffsetRangeMinutes,
            offsetStep = settingsVm.catchupOffsetStepMinutes,
            onSetMode = settingsVm::setCatchupTimezone,
            onAdjustOffset = settingsVm::adjustCatchupOffset,
            player = player,
            onSetPlayer = settingsVm::setCatchupPlayer,
            onDismiss = onDismiss,
        )
}

/** The playlist list behind "Catch-up time zone per playlist", moved out of [SettingsScreen] unchanged. */
@Composable
private fun CatchupSourcesHost(
    sources: List<tv.own.owntv.core.database.entity.SourceEntity>,
    current: tv.own.owntv.core.database.entity.SourceEntity?,
    onPick: (tv.own.owntv.core.database.entity.SourceEntity?) -> Unit,
    onDismiss: () -> Unit,
) {
    tv.own.owntv.features.settings.PickerDialog(
        title = stringResource(R.string.settings_live_preroll_playlist_picker),
        options = sources.map { src -> src.id.toString() to "${src.name}  ·  ${catchupOverrideLabel(src)}" },
        selected = current?.id?.toString() ?: "",
        onSelect = { id -> onPick(sources.firstOrNull { it.id.toString() == id }) },
        onDismiss = onDismiss,
    )
}

/** More's way into this screen: a root group to open at, or the search field. */
data class SettingsStart(val group: Int?, val search: Boolean)

/** One playlist's catch-up time zone, moved out of [SettingsScreen] unchanged (its body is at the JVM method limit). */
@Composable
private fun CatchupSourceValueHost(settingsVm: SettingsViewModel, src: tv.own.owntv.core.database.entity.SourceEntity?, onDone: () -> Unit) {
    tv.own.owntv.features.settings.PickerDialog(
        title = src?.name ?: stringResource(R.string.settings_catchup_timezone_per_playlist),
        options = listOf(
            CATCHUP_FOLLOW to stringResource(R.string.settings_live_preroll_follow),
            SettingsRepository.CatchupTimezone.DEVICE.name to stringResource(R.string.settings_catchup_timezone_device),
        ) + settingsVm.catchupOffsetChoicesMinutes.map { "$CATCHUP_MANUAL_PREFIX$it" to utcOffsetLabel(it) },
        selected = when (src?.catchupTimezone) {
            null -> CATCHUP_FOLLOW
            SettingsRepository.CatchupTimezone.MANUAL.name -> "$CATCHUP_MANUAL_PREFIX${src.catchupOffsetMin ?: 0}"
            else -> src.catchupTimezone ?: CATCHUP_FOLLOW
        },
        onSelect = { value ->
            src?.let {
                when {
                    value == CATCHUP_FOLLOW -> settingsVm.setSourceCatchupTimezone(it.id, null, null)
                    value.startsWith(CATCHUP_MANUAL_PREFIX) -> settingsVm.setSourceCatchupTimezone(
                        it.id, SettingsRepository.CatchupTimezone.MANUAL.name,
                        value.removePrefix(CATCHUP_MANUAL_PREFIX).toIntOrNull() ?: 0,
                    )
                    else -> settingsVm.setSourceCatchupTimezone(it.id, value, null)
                }
            }
            onDone()
        },
        // Back goes back one level, to the playlist list — same as the Video player overrides.
        onDismiss = onDone,
    )
}

/** The repository line More › About shows. */
internal const val GITHUB_REPO = "github.com/ahXN00/OwnTV"
internal const val TELEGRAM_LINK = "t.me/owntvplayer"

/**
 * Read-only viewer for the persisted playback error history: the last ~10 failures with their
 * plain-English reason, media spec, raw engine text, engine, stream type and device info — so users
 * who can't pull logcat can read/report what happened after dismissing the error screen.
 */
@Composable
internal fun String.playbackDisplayName(): String = when (trim().lowercase(java.util.Locale.ROOT)) {
    "mpv" -> stringResource(R.string.settings_player_mpv)
    "exoplayer", "exo" -> stringResource(R.string.settings_player_exoplayer)
    else -> this
}

/**
 * Warn before enabling Auto frame rate below Android 12, where smooth refresh-rate alternatives cannot
 * be queried and a mode switch can trigger a visible HDMI re-handshake.
 */
@Composable
internal fun AutoFrameRateWarningDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    tv.own.owntv.ui.stage.StageConfirm(
        title = stringResource(R.string.settings_auto_frame_rate_warning_title),
        body = stringResource(R.string.settings_auto_frame_rate_warning_description, android.os.Build.VERSION.RELEASE),
        cancel = stringResource(R.string.settings_auto_frame_rate_keep_off),
        confirm = stringResource(R.string.settings_auto_frame_rate_turn_on_anyway),
        onConfirm = onEnable,
        onCancel = onDismiss,
        focusCancel = true,
    )
}

@Composable
internal fun LivePreviewPanelHiddenDialog(onDismiss: () -> Unit) {
    tv.own.owntv.ui.stage.StageNotice(
        title = stringResource(R.string.settings_live_preview_panel_hidden_title),
        body = stringResource(R.string.settings_live_preview_panel_hidden_description, *NO_ARGS),
        onDismiss = onDismiss,
    )
}

/** Stable, non-display choices for the history picker. */
private enum class HistoryScope(val type: tv.own.owntv.core.model.MediaType?, val labelRes: Int) {
    LIVE(tv.own.owntv.core.model.MediaType.LIVE, R.string.settings_history_live),
    MOVIES(tv.own.owntv.core.model.MediaType.MOVIE, R.string.settings_history_movies),
    SERIES(tv.own.owntv.core.model.MediaType.SERIES, R.string.settings_history_series),
    ALL(null, R.string.settings_history_all),
}

/**
 * Pick what watch history to clear: everything, or just Live TV / Movies / Series. Over a dimmed scrim;
 * Cancel is focused first so a stray OK doesn't wipe anything. [onClear] gets null for "all".
 */
@Composable
internal fun ClearHistoryDialog(
    onClear: (tv.own.owntv.core.model.MediaType?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pending by remember { mutableStateOf<HistoryScope?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(pending) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    val p = pending
    // One popup, two steps: what to clear, then "can't be undone" (focus on No). Back steps back first.
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = { if (pending != null) pending = null else onDismiss() },
        title = if (p == null) stringResource(R.string.settings_clear_history) else stringResource(R.string.settings_clear_history_confirm, stringResource(p.labelRes)),
        body = if (p == null) stringResource(R.string.settings_choose_history) else stringResource(R.string.settings_cannot_undo),
        width = 760.mpx,
        buttons = {
            if (p == null) {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            } else {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_no), onClick = { pending = null }, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(firstFocus))
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_yes_clear), onClick = { onClear(p.type) }, height = 56.mpx, textSize = 19, tinted = true)
            }
        },
    ) {
        if (p == null) {
            listOf(HistoryScope.LIVE to OwnTVIcon.LIVE_TV, HistoryScope.MOVIES to OwnTVIcon.MOVIES, HistoryScope.SERIES to OwnTVIcon.SERIES, HistoryScope.ALL to OwnTVIcon.TRASH).forEachIndexed { i, (scope, icon) ->
                val all = scope == HistoryScope.ALL
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(if (all) R.string.settings_all_history else scope.labelRes),
                    onClick = { pending = scope }, danger = all,
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    leading = { tv.own.owntv.ui.stage.StagePopupIcon(icon, if (all) tv.own.owntv.ui.theme.StageColors.Danger else tv.own.owntv.ui.theme.StageColors.Text) },
                )
            }
        }
    }
}

@Composable
internal fun fontFamilyLabel(family: AppFontFamily): String = stringResource(
    when (family) {
        AppFontFamily.LORA -> R.string.settings_font_lora
        AppFontFamily.SYSTEM_SANS -> R.string.settings_font_system_sans
        AppFontFamily.MONOSPACE -> R.string.settings_font_monospace
        AppFontFamily.PLAYFAIR_DISPLAY -> R.string.settings_font_playfair_display
        AppFontFamily.DANCING_SCRIPT -> R.string.settings_font_dancing_script
        AppFontFamily.POPPINS -> R.string.settings_font_poppins
        AppFontFamily.PLUS_JAKARTA_SANS -> R.string.settings_font_plus_jakarta_sans
    },
)

/** A stepper for shared popup geometry. Changes apply live to this dialog too. */
@Composable
private fun PopupSizeDialog(current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    // The value with a real-size sample popup above it: in the page's panel on a settings page,
    // a Stage popup elsewhere.
    val stepper: @Composable () -> Unit = {
        tv.own.owntv.features.settings.PanelStepper(
            value = stringResource(R.string.common_percent, current),
            onStep = { d -> onSet(PopupSizeScale.clamp(current + d * PopupSizeScale.STEP)) },
            onReset = { onSet(PopupSizeScale.DEFAULT) },
            onDone = onDismiss,
            preview = { tv.own.owntv.features.settings.PopupSizeSample(current) },
        )
    }
    if (tv.own.owntv.features.settings.panelEditor(onDismiss) { stepper() }) return
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = stringResource(R.string.settings_popup_size), body = stringResource(R.string.settings_popup_size_range, PopupSizeScale.MIN, PopupSizeScale.MAX)) { stepper() }
}

/** A stepper for the global UI scale. Changes apply live (the whole UI re-scales as you adjust). */
@Composable
private fun ZoomDialog(current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    // Zoom below LOW_RAM_WARN doubles the on-screen item count, which can OOM-crash 2 GB devices
    // the first step under it asks first (a Stage question, focus on Cancel). Accepting once
    // arms the rest of this session; if it was opened already below the line, don't nag.
    var lowZoomAccepted by remember { mutableStateOf(current < UiZoom.LOW_RAM_WARN) }
    var pendingLowZoom by remember { mutableStateOf<Int?>(null) }
    val stepper: @Composable () -> Unit = {
        tv.own.owntv.features.settings.PanelStepper(
            value = stringResource(R.string.common_percent, current),
            onStep = { d ->
                val next = UiZoom.clamp(current + d * UiZoom.STEP)
                if (d < 0 && next < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) pendingLowZoom = next else onSet(next)
            },
            onReset = { onSet(UiZoom.DEFAULT) },
            onDone = onDismiss,
        )
    }
    // In the page's panel on a settings page, a Stage popup elsewhere.
    if (!tv.own.owntv.features.settings.panelEditor(onDismiss) { stepper() }) {
        tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = stringResource(R.string.settings_ui_zoom), body = stringResource(R.string.settings_ui_zoom_range, UiZoom.MIN, UiZoom.MAX)) { stepper() }
    }
    pendingLowZoom?.let { target ->
        tv.own.owntv.ui.stage.StageConfirm(
            title = stringResource(R.string.settings_low_zoom_warning_title),
            body = stringResource(R.string.settings_low_zoom_warning, UiZoom.LOW_RAM_WARN, UiZoom.LOW_RAM_WARN),
            confirm = stringResource(R.string.settings_low_zoom_accept),
            onConfirm = { lowZoomAccepted = true; pendingLowZoom = null; onSet(target) },
            onCancel = { pendingLowZoom = null },
            focusCancel = true,
        )
    }
}

/** Solid-interface radiance controls, kept together in one compact TV-safe popup. */
@Composable
private fun AmbientGlowDialog(
    glowEnabled: Boolean,
    pulseEnabled: Boolean,
    onToggleGlow: () -> Unit,
    onTogglePulse: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Two switches: in the page's panel, a Stage popup elsewhere.
    val rows: @Composable () -> Unit = {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
        Column(Modifier.trapAllFocusExit().focusGroup()) {
            tv.own.owntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.settings_ambient_glow_effect), onClick = onToggleGlow, modifier = Modifier.focusRequester(first),
                trailing = { tv.own.owntv.ui.stage.StageSwitch(glowEnabled) },
            )
            if (glowEnabled) tv.own.owntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.settings_ambient_glow_pulse), onClick = onTogglePulse,
                trailing = { tv.own.owntv.ui.stage.StageSwitch(pulseEnabled) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.mpx), horizontalArrangement = Arrangement.End) {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_done), onClick = onDismiss, height = 52.mpx, textSize = 18, tinted = true)
            }
        }
    }
    if (tv.own.owntv.features.settings.panelEditor(onDismiss) { rows() }) return
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = stringResource(R.string.settings_ambient_glow), body = stringResource(R.string.settings_ambient_glow_dialog_description)) { rows() }
}

/** The Glass & background page. Background selection stays nested here, so Back returns here. */
@Composable
private fun GlassEffectSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settingsVm: SettingsViewModel = koinViewModel()
    val glassConfig by settingsVm.glassConfig.collectAsStateWithLifecycle()
    val bgImagePath by settingsVm.bgImagePath.collectAsStateWithLifecycle()
    var showBackgroundChooser by remember { mutableStateOf(false) }
    var showLocalPicker by remember { mutableStateOf(false) }
    var showRemotePicker by remember { mutableStateOf(false) }
    val ingestScope = rememberCoroutineScope()

    val background by settingsVm.backgroundConfig.collectAsStateWithLifecycle()
    GlassBackgroundPage(
        background = background,
        glassOn = glassConfig.enabled,
        alphaPercent = (glassConfig.alpha * 100).roundToInt(),
        onSetStyle = { style ->
            // Picture with no picture yet goes straight to the picker.
            settingsVm.setBackgroundStyle(style)
            if (style == tv.own.owntv.core.theme.BackgroundStyle.PICTURE && bgImagePath.isBlank()) showBackgroundChooser = true
        },
        onOpenPicture = { showBackgroundChooser = true },
        onSetLook = settingsVm::setPictureLook,
        onSetDim = settingsVm::setBackgroundDim,
        onSetBlur = settingsVm::setBackgroundBlur,
        onSetAccentLight = settingsVm::setBackgroundAccentLight,
        onToggleGlass = {
            settingsVm.setGlassScopeBitmask(
                if (glassConfig.enabled) 0 else GlassConfig.ON_BITS,
            )
        },
        onSetAlpha = settingsVm::setGlassAlphaPercent,
        onReset = settingsVm::resetGlassAndBackground,
        onBack = onBack,
        modifier = modifier,
    )

    if (showBackgroundChooser) {
        BackgroundImageChooserDialog(
                hasImage = bgImagePath.isNotBlank(),
                onPickLocal = { showBackgroundChooser = false; showLocalPicker = true },
                onPickRemote = { showBackgroundChooser = false; showRemotePicker = true },
                onClear = { settingsVm.setBgImagePath(""); showBackgroundChooser = false },
                onDismiss = { showBackgroundChooser = false },
            )
    }
    if (showRemotePicker) {
        val context = LocalContext.current
        val remoteState by settingsVm.remoteState.collectAsStateWithLifecycle()
        tv.own.owntv.ui.components.RemoteBackgroundDialog(
            state = remoteState,
            images = settingsVm.remoteImages,
            onStart = settingsVm::startRemoteImageListener,
            onStop = settingsVm::stopRemoteListener,
            onImageReceived = { file ->
                val destDir = File(context.filesDir, "backgrounds")
                ingestScope.launch {
                    val path = withContext(Dispatchers.IO) {
                        runCatching { ingestBackgroundImage(file, destDir) }.getOrNull()
                            .also { runCatching { file.delete() } }
                    }
                    if (path != null) settingsVm.setNewBackgroundPicture(path)
                }
                showRemotePicker = false
            },
            onDismiss = { showRemotePicker = false; showBackgroundChooser = true },
        )
    }
    if (showLocalPicker) {
        val context = LocalContext.current
        StorageBrowser(
            title = stringResource(R.string.settings_pick_background_title),
            mode = BrowseMode.FILE,
            fileExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp"),
            onPick = { file ->
                val destDir = File(context.filesDir, "backgrounds")
                ingestScope.launch {
                    val path = withContext(Dispatchers.IO) {
                        runCatching { ingestBackgroundImage(file, destDir) }.getOrNull()
                    }
                    if (path != null) settingsVm.setNewBackgroundPicture(path)
                }
                showLocalPicker = false
            },
            onDismiss = { showLocalPicker = false; showBackgroundChooser = true },
        )
    }
}

/** Per-playlist catch-up picker keys: follow the global setting, or a UTC offset in minutes. */
private const val CATCHUP_FOLLOW = ""
private const val CATCHUP_MANUAL_PREFIX = "MANUAL:"

/** A playlist's own catch-up time zone as the playlist list shows it. */
@Composable
private fun catchupOverrideLabel(source: tv.own.owntv.core.database.entity.SourceEntity): String =
    when (source.catchupTimezone) {
        null -> stringResource(R.string.settings_live_preroll_follow)
        SettingsRepository.CatchupTimezone.MANUAL.name -> utcOffsetLabel(source.catchupOffsetMin ?: 0)
        else -> stringResource(R.string.settings_device)
    }

/** "UTC", "UTC+05:00", "UTC-03:30" — labels a UTC offset (in minutes) for catch-up. */
private fun utcOffsetLabel(minutes: Int): String {
    if (minutes == 0) return "UTC"
    val sign = if (minutes < 0) "-" else "+"
    val abs = kotlin.math.abs(minutes)
    return "UTC$sign%02d:%02d".format(Locale.ROOT, abs / 60, abs % 60)
}

@Composable
private fun CatchupTimeDialog(
    mode: SettingsRepository.CatchupTimezone,
    offsetMinutes: Int,
    offsetRange: IntRange,
    onSetMode: (SettingsRepository.CatchupTimezone) -> Unit,
    onAdjustOffset: (Int) -> Unit,
    /** One −/+ press, in minutes (a quarter hour). */
    offsetStep: Int,
    player: SettingsRepository.CatchupPlayer,
    onSetPlayer: (SettingsRepository.CatchupPlayer) -> Unit,
    onDismiss: () -> Unit,
) {
    // Three simple values: in the page's panel, a Stage popup elsewhere.
    val manual = mode == SettingsRepository.CatchupTimezone.MANUAL
    val form: @Composable () -> Unit = {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
        val modes = SettingsRepository.CatchupTimezone.entries
        Column(Modifier.trapAllFocusExit().focusGroup()) {
            tv.own.owntv.ui.stage.StageMenuChoice(
                options = listOf(
                    stringResource(R.string.settings_catchup_timezone_automatic),
                    stringResource(R.string.settings_catchup_timezone_device),
                    stringResource(R.string.settings_manual),
                ),
                selected = modes.indexOf(mode),
                onSelect = { onSetMode(modes[it]) },
                focusRequester = first,
            )
            if (manual) {
                // ◀ ▶ move the offset by a quarter hour; clamped at the ends.
                tv.own.owntv.ui.stage.StageSurface(
                    onClick = {},
                    radius = 18.mpx,
                    focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.mpx).height(72.mpx).onPreviewKeyEvent { e ->
                        val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                        if (d != 0 && e.type == KeyEventType.KeyDown) onAdjustOffset(d * offsetStep)
                        d != 0
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.mpx), verticalAlignment = Alignment.CenterVertically) {
                        OwnTVIcon(OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(24.mpx).graphicsLayer { rotationZ = 180f })
                        Text(
                            utcOffsetLabel(offsetMinutes), style = tv.own.owntv.ui.theme.stageText(30, 800), color = tv.own.owntv.ui.theme.stageAccent.accent,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f),
                        )
                        OwnTVIcon(OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(24.mpx))
                    }
                }
            }
            // Which player takes an archive programme — "Ask" puts the choice on "Watch from start" itself.
            Text(
                stringResource(R.string.settings_catchup_player).uppercase(),
                style = tv.own.owntv.ui.theme.stageText(14, 800, androidx.compose.ui.unit.TextUnit(0.12f, androidx.compose.ui.unit.TextUnitType.Em)),
                color = tv.own.owntv.ui.theme.StageColors.Dim,
                modifier = Modifier.padding(start = 8.mpx, top = 18.mpx, bottom = 4.mpx),
            )
            val players = SettingsRepository.CatchupPlayer.entries
            tv.own.owntv.ui.stage.StageMenuChoice(
                options = players.map {
                    when (it) {
                        SettingsRepository.CatchupPlayer.ASK -> stringResource(R.string.settings_catchup_player_ask)
                        SettingsRepository.CatchupPlayer.INTERNAL -> stringResource(R.string.settings_catchup_player_internal)
                        SettingsRepository.CatchupPlayer.EXTERNAL -> stringResource(R.string.settings_catchup_player_external)
                    }
                },
                selected = players.indexOf(player),
                onSelect = { onSetPlayer(players[it]) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.mpx), horizontalArrangement = Arrangement.End) {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.settings_done), onClick = onDismiss, height = 52.mpx, textSize = 18, tinted = true)
            }
        }
    }
    if (tv.own.owntv.features.settings.panelEditor(onDismiss) { form() }) return
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = stringResource(R.string.settings_catchup), body = stringResource(R.string.settings_catchup_timezone_description)) { form() }
}

/**
 * Global guide shift. Some XMLTV feeds publish in a timezone the channels don't actually air in;
 * this moves every programme by a fixed amount. A per-channel override (channel long-press → EPG
 * time offset) wins over it — that's what a lineup carrying both East and West feeds needs, since
 * one global shift can only ever fix one of the two.
 */
@Composable
private fun EpgOffsetSettingDialog(
    offsetMinutes: Int,
    offsetRange: IntRange,
    onAdjust: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A number: in the page's panel on a settings page, a Stage popup elsewhere.
    val stepper: @Composable () -> Unit = {
        tv.own.owntv.features.settings.PanelStepper(
            value = epgShiftLabel(offsetMinutes),
            onStep = { d -> onAdjust(d * 30) },
            onReset = onReset,
            onDone = onDismiss,
        )
    }
    if (tv.own.owntv.features.settings.panelEditor(onDismiss) { stepper() }) return
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_epg_time_offset),
        body = stringResource(R.string.settings_epg_offset_dialog_description),
    ) { stepper() }
}

/**
 * One entry in the Settings root list, as data rather than as an inline call.
 *
 * The root is a lazy list, which needs a stable key per entry — and both focus restores (closing a
 * dialog, and Back from a sub-screen) need the *index* of a row that may be scrolled off-screen and
 * therefore not composed at all. Neither is knowable from a plain Column of 36 inline rows.
 */
internal sealed interface RootItem {
    val key: String
}

/** A group heading — one entry in the left spine, with the icon and summary the sheet is headed by. */
private data class RootGroup(
    override val key: String,
    val label: String,
    val icon: OwnTVIcon,
    val summary: String,
) : RootItem

internal data class RootRow(
    override val key: String,
    val tone: TileTone,
    val icon: OwnTVIcon,
    val title: String,
    val desc: String? = null,
    val chip: String? = null,
    val chipTone: TileTone = TileTone.PRIMARY,
    /** Set only by pinned Video player rows, which decide row by row whether they open a screen. */
    val chevron: Boolean? = null,
    val focus: FocusRequester? = null,
    /** A divider label drawn above this row, starting a sub-section inside its group. Not a row itself. */
    val heading: String? = null,
    /** What the Stage row shows on its right when the chip alone does not say (switch, stepper, …). */
    val value: SettingValue? = null,
    /** The context panel's choices for a value picked from a list, and the recommended one. */
    val choices: List<String> = emptyList(),
    val recommended: Int = -1,
    /** ◀ ▶ on a stepper or segmented value. */
    val onStep: ((Int) -> Unit)? = null,
    val onClick: () -> Unit,
) : RootItem {
    /**
     * Honest chevrons: the arrow promises another screen, so only the rows that open one carry it.
     * Derived from the key rather than declared per row — [tabRowKey] is used by exactly the rows that
     * push a [SettingsTab], so a new sub-screen row gets its chevron with no extra flag to forget.
     * Rows pinned to Quick from inside Video player settings are the exception and say so themselves
     * in [chevron]: the ones that toggle in place must not promise a screen they never open.
     */
    val showChevron: Boolean get() = chevron ?: key.startsWith("tab_")
}

/** The settings that live inside a sub-screen, findable by their own names; each opens its screen. */
@Composable
private fun subScreenSearchEntries(open: (SettingsTab) -> Unit): List<SettingsSearchEntry> = listOf(
        SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_epg_sources_auto_refresh_title), stringResource(R.string.settings_search_keywords_epg), OwnTVIcon.EPG, TileTone.SECONDARY) { open(SettingsTab.EPG) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.home_row_now_trending), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.home_trending_style), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_hero_preview), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_live_keep_watching), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_movies_keep_watching), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_series_keep_watching), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_android_tv_home), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_ch_nav_up), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.CH_NAV, TileTone.SECONDARY) { open(SettingsTab.CH_NAV) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_ch_nav_down), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.CH_NAV, TileTone.SECONDARY) { open(SettingsTab.CH_NAV) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_remote_shortcuts), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.CH_NAV, TileTone.SECONDARY) { open(SettingsTab.CH_NAV) },
        SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_show_weather), stringResource(R.string.settings_search_keywords_weather), OwnTVIcon.WEATHER, TileTone.SECONDARY) { open(SettingsTab.WEATHER) },
        SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_temperature_unit), stringResource(R.string.settings_search_keywords_weather), OwnTVIcon.WEATHER, TileTone.SECONDARY) { open(SettingsTab.WEATHER) },
        SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_custom_location), stringResource(R.string.settings_search_keywords_weather), OwnTVIcon.WEATHER, TileTone.SECONDARY) { open(SettingsTab.WEATHER) },
        SettingsSearchEntry(stringResource(R.string.settings_group_content_metadata), stringResource(R.string.settings_metadata_source), stringResource(R.string.settings_search_keywords_metadata), OwnTVIcon.IMAGE, TileTone.SECONDARY) { open(SettingsTab.METADATA) },
        SettingsSearchEntry(stringResource(R.string.settings_group_content_metadata), stringResource(R.string.settings_metadata_language), stringResource(R.string.settings_search_keywords_metadata), OwnTVIcon.IMAGE, TileTone.SECONDARY) { open(SettingsTab.METADATA) },
        SettingsSearchEntry(stringResource(R.string.settings_group_content_metadata), stringResource(R.string.player_subtitles_search_language), stringResource(R.string.settings_search_keywords_subtitle_appearance), OwnTVIcon.SUBTITLE, TileTone.SECONDARY) { open(SettingsTab.OPEN_SUBTITLES) },
        SettingsSearchEntry(stringResource(R.string.settings_group_content_metadata), stringResource(R.string.player_subtitles_filter_title), stringResource(R.string.settings_search_keywords_subtitle_appearance), OwnTVIcon.SUBTITLE, TileTone.SECONDARY) { open(SettingsTab.OPEN_SUBTITLES) },
        SettingsSearchEntry(stringResource(R.string.settings_group_content_metadata), stringResource(R.string.player_subtitles_stay_signed_in), stringResource(R.string.settings_search_keywords_subtitle_appearance), OwnTVIcon.SUBTITLE, TileTone.SECONDARY) { open(SettingsTab.OPEN_SUBTITLES) },
)

/** The list key of the row that opens [tab], so a Back from that sub-screen can find its index. */
private fun tabRowKey(tab: SettingsTab) = "tab_${tab.name}"

/**
 * A Settings row on a Stage group page. The value is the row's own, or read from its chip: On / Off
 * in place is a switch, a row that opens a screen keeps its › (honest chevrons), anything else was
 * picked in a popup (▾).
 */
@Composable
private fun RootRowStage(
    item: RootRow,
    pinned: Boolean,
    onLongClick: () -> Unit,
    /** Takes precedence over the row's own requester: used to give focus back after its menu closes. */
    focus: FocusRequester? = null,
    /** On Quick: the panel still explains the focused row; its hold-OK hint says Unpin. */
    inQuick: Boolean = false,
    extra: RowExtra? = null,
) {
    val on = stringResource(R.string.common_on)
    val off = stringResource(R.string.common_off)
    val pin = stringResource(R.string.settings_row_menu_pin)
    val unpin = stringResource(R.string.settings_key_unpin)
    val value = extra?.value ?: item.value ?: when {
        item.showChevron || item.key == "catchup_sources" -> SettingValue.Opens(item.chip)
        item.key == "epg_offset" -> SettingValue.Choice(item.chip.orEmpty())
        item.chip == on || item.chip == off -> SettingValue.Switch(item.chip == on)
        item.chip != null -> SettingValue.Choice(item.chip)
        else -> null
    }
    val words = tv.own.owntv.features.settings.settingWords(item.key, item.title, item.desc).let {
        if (inQuick && item.key.startsWith("vp_")) tv.own.owntv.features.settings.SettingWords(it.title, item.desc) else it
    }
    StageSettingRow(
        icon = item.icon,
        title = words.title,
        desc = words.line,
        value = value,
        onClick = extra?.onClick ?: item.onClick,
        onLongClick = onLongClick,
        onStep = extra?.onStep ?: item.onStep,
        pinned = pinned,
        help = settingHelp(item.key, words.title, item.desc, value, extra?.choices ?: item.choices, chosen = extra?.chosen ?: -1, recommended = extra?.recommended ?: item.recommended).let { h ->
            val hints = (extra?.hints ?: h.hints).map { if (inQuick && it.second == pin) it.first to unpin else it }
            h.copy(text = extra?.help ?: h.text, hints = hints, extra = extra?.extra)
        },
        modifier = (focus ?: item.focus)?.let { Modifier.focusRequester(it) } ?: Modifier,
    )
}
/**
 * Hold OK on a sheet row: pin it to Quick, take it back out, or move it within Quick. Actions that
 * cannot apply are left out rather than greyed — a focusable row that refuses to do anything is worse
 * on a remote than one that is simply not there.
 */
@Composable
internal fun SettingsRowMenu(
    title: String,
    pinned: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPinToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler { onDismiss() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    // The Stage menu (playbook 11), as every other hold-OK menu.
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        Box(
            Modifier.fillMaxSize().background(tv.own.owntv.ui.theme.StageColors.Scrim).longPressMenuGuard(),
            contentAlignment = Alignment.Center,
        ) {
            tv.own.owntv.ui.stage.StageMenu(Modifier.width(520.mpx).trapAllFocusExit().focusGroup()) {
                tv.own.owntv.ui.stage.StageMenuHeader(title = title, subtitle = null)
                tv.own.owntv.ui.stage.StageMenuItem(
                    stringResource(if (pinned) R.string.settings_row_menu_unpin else R.string.settings_row_menu_pin),
                    onClick = { onPinToggle(); onDismiss() },
                    icon = OwnTVIcon.SPARKLE,
                    modifier = Modifier.focusRequester(first),
                )
                if (canMoveUp) {
                    tv.own.owntv.ui.stage.StageMenuItem(
                        stringResource(R.string.settings_row_menu_move_up),
                        onClick = { onMoveUp(); onDismiss() },
                        icon = OwnTVIcon.CHEVRON_UP,
                    )
                }
                if (canMoveDown) {
                    tv.own.owntv.ui.stage.StageMenuItem(
                        stringResource(R.string.settings_row_menu_move_down),
                        onClick = { onMoveDown(); onDismiss() },
                        icon = OwnTVIcon.CHEVRON_DOWN,
                    )
                }
            }
        }
    }
}

/** Batch 4 · one searchable settings row: its group breadcrumb, title, extra keywords, and action. */
internal class SettingsSearchEntry(
    val group: String,
    val title: String,
    keywords: String,
    val icon: OwnTVIcon,
    val tone: TileTone,
    val chip: String? = null,
    val chipTone: TileTone = TileTone.PRIMARY,
    val showChevron: Boolean = true,
    val onClick: () -> Unit,
)

/** What a root row shows and does on its Stage page beyond its chip. */
private class RowExtra(
    val value: SettingValue? = null,
    val onStep: ((Int) -> Unit)? = null,
    /** Replaces the row's click: a switch or segmented value changes in place instead of opening a popup. */
    val onClick: (() -> Unit)? = null,
    val choices: List<String> = emptyList(),
    val chosen: Int = -1,
    val recommended: Int = -1,
    /** Panel text that needs an argument (Language: how many languages). */
    val help: String? = null,
    val hints: List<Pair<String, String>>? = null,
    val extra: (@Composable () -> Unit)? = null,
)

/**
 * The Stage values of the root rows, by row key: switches, steppers and segmented choices that change
 * in place, the accent swatches, and the "opens a screen" values. Kept out of [SettingsScreen], which
 * sits at the JVM's method size limit.
 */
@Composable
private fun stageRowExtras(
    vm: SettingsViewModel,
    themeMode: ThemeMode,
    uiZoomPercent: Int,
    onSetZoom: (Int) -> Unit,
    onOpenZoom: () -> Unit,
    font: FontCustomization,
    onSetFont: (FontCustomization) -> Unit,
    playlists: List<tv.own.owntv.core.database.entity.SourceEntity>,
): Map<String, RowExtra> {
    val accent by vm.accent.collectAsStateWithLifecycle()
    val customAccent by vm.customAccent.collectAsStateWithLifecycle()
    val glass by vm.glassConfig.collectAsStateWithLifecycle()
    val animation by vm.animationLevel.collectAsStateWithLifecycle()
    val weatherOn by vm.weatherEnabled.collectAsStateWithLifecycle()
    val weatherPlace by vm.weatherLocation.collectAsStateWithLifecycle()
    val fahrenheit by vm.weatherFahrenheit.collectAsStateWithLifecycle()
    val navStyle by vm.navStyle.collectAsStateWithLifecycle()
    val navSize by vm.navSize.collectAsStateWithLifecycle()
    val liveLayout by vm.liveLayout.collectAsStateWithLifecycle()
    val liveView by vm.liveView.collectAsStateWithLifecycle()
    val vodLayout by vm.vodLayout.collectAsStateWithLifecycle()
    val appIcon by vm.appIcon.collectAsStateWithLifecycle()
    val brandAccent by vm.brandAccentTriangle.collectAsStateWithLifecycle()
    val focusHighlight by vm.focusHighlight.collectAsStateWithLifecycle()
    val focusWidth by vm.focusHighlightWidth.collectAsStateWithLifecycle()
    val sep = stringResource(R.string.content_epg_bits_separator)
    fun <T> cycle(all: List<T>, now: T, step: Int): T = all[(all.indexOf(now) + step).mod(all.size)]

    val themes = ThemeMode.entries
    val themeNames = themes.map { themeLabel(it) }
    val liveLayouts = tv.own.owntv.core.settings.SettingsRepository.LiveLayout.entries
    val liveViews = tv.own.owntv.core.settings.SettingsRepository.LiveView.entries
    // Cinematic first.
    val vodLayouts = listOf(tv.own.owntv.core.settings.SettingsRepository.VodLayout.CINEMATIC, tv.own.owntv.core.settings.SettingsRepository.VodLayout.SEPARATE)
    val presets = tv.own.owntv.core.theme.AccentColor.entries
    val presetColors = presets.map { it.primary(true) }
    val current = tv.own.owntv.ui.theme.stageAccent.accent
    val hex = if (customAccent.isNotBlank()) customAccent.uppercase() else String.format(java.util.Locale.ROOT, "#%06X", 0xFFFFFF and current.toArgb())
    val focusColor = tv.own.owntv.ui.theme.stageAccent.focus
    val accentStep: (Int) -> Unit = { step ->
        vm.setCustomAccent("")
        vm.setAccent(if (customAccent.isNotBlank()) (if (step > 0) presets.first() else presets.last()) else cycle(presets, accent, step))
    }
    val zoomStep: (Int) -> Unit = { step ->
        val next = tv.own.owntv.core.theme.UiZoom.clamp(uiZoomPercent + step * tv.own.owntv.core.theme.UiZoom.STEP)
        // Below the low-memory line the zoom popup asks first; the row never steps past it alone.
        if (next < tv.own.owntv.core.theme.UiZoom.LOW_RAM_WARN && uiZoomPercent >= tv.own.owntv.core.theme.UiZoom.LOW_RAM_WARN) onOpenZoom() else onSetZoom(next)
    }
    val popupStep: (Int) -> Unit = { step ->
        onSetFont(font.copy(popupSizePercent = tv.own.owntv.core.theme.PopupSizeScale.clamp(font.popupSizePercent + step * tv.own.owntv.core.theme.PopupSizeScale.STEP)))
    }
    val languages = tv.own.owntv.core.i18n.SupportedLocales.all.count { it.packaged }
    val valueStyle = tv.own.owntv.ui.theme.stageText(18, 700)
    return mapOf(
        "tab_SOURCES" to RowExtra(
            SettingValue.Opens(pluralStringResource(R.plurals.more_playlist_count, playlists.size, playlists.size)),
            extra = { tv.own.owntv.features.settings.PlaylistSyncList(playlists) },
        ),
        "theme" to RowExtra(
            SettingValue.Segmented(themeNames, themes.indexOf(themeMode)),
            onStep = { vm.setThemeMode(cycle(themes, themeMode, it)) },
            onClick = { vm.setThemeMode(cycle(themes, themeMode, 1)) },
        ),
        "accent" to RowExtra(
            SettingValue.Custom {
                AccentSwatches(presetColors, current)
                Text(hex, style = valueStyle, color = current, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.mpx))
            },
            onStep = accentStep,
            hints = listOf(
                stringResource(R.string.common_ok) to stringResource(R.string.settings_key_palette),
                "◀ ▶" to stringResource(R.string.settings_presets),
                stringResource(R.string.content_key_hold_ok) to stringResource(R.string.settings_row_menu_pin),
                stringResource(R.string.common_back) to stringResource(R.string.common_nav_settings),
            ),
        ),
        "focus_highlight" to RowExtra(SettingValue.Custom {
            Box(Modifier.size(22.mpx).background(focusColor, androidx.compose.foundation.shape.CircleShape))
            Text(focusWidthLabel(focusWidth), style = valueStyle, color = current, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OwnTVIcon(OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(20.mpx))
        }),
        "tab_GLASS_EFFECT" to RowExtra(SettingValue.Opens(glassBackgroundSummary(vm.backgroundConfig.collectAsStateWithLifecycle().value, glass))),
        "fonts" to RowExtra(SettingValue.Opens(fontFamilyLabel(font.mainFamily) + sep + stringResource(R.string.common_percent, font.sizePercent))),
        // The sample popup shows as soon as the row has focus, so ◀ ▶ on the row resize it live.
        "popup_size" to RowExtra(
            SettingValue.Stepper(stringResource(R.string.common_percent, font.popupSizePercent)), onStep = popupStep,
            extra = { Box(Modifier.fillMaxWidth().padding(top = 18.mpx), contentAlignment = Alignment.Center) { tv.own.owntv.features.settings.PopupSizeSample(font.popupSizePercent) } },
        ),
        "ui_zoom" to RowExtra(SettingValue.Stepper(stringResource(R.string.common_percent, uiZoomPercent)), onStep = zoomStep),
        "animations" to RowExtra(
            SettingValue.Switch(animation == tv.own.owntv.core.theme.AnimationLevel.FULL),
            onClick = {
                vm.setAnimationLevel(
                    if (animation == tv.own.owntv.core.theme.AnimationLevel.FULL) tv.own.owntv.core.theme.AnimationLevel.OFF
                    else tv.own.owntv.core.theme.AnimationLevel.FULL,
                )
            },
        ),
        "tab_WEATHER" to RowExtra(SettingValue.Opens(
            if (!weatherOn) stringResource(R.string.common_off)
            else listOfNotNull(weatherPlace.ifBlank { null }, stringResource(if (fahrenheit) R.string.settings_degree_fahrenheit else R.string.settings_degree_celsius)).joinToString(sep),
        )),
        "navigation" to RowExtra(
            SettingValue.Opens(navStyleLabel(navStyle) + sep + stringResource(navSize.labelRes)),
            choices = listOf(stringResource(R.string.settings_nav_floating), stringResource(R.string.settings_nav_docked)),
            chosen = navStyle.ordinal,
            recommended = 1,
        ),
        "live_layout" to RowExtra(
            SettingValue.Segmented(liveLayouts.map { stringResource(if (it == tv.own.owntv.core.settings.SettingsRepository.LiveLayout.STAGE) R.string.settings_live_layout_stage else R.string.settings_seg_separate) }, liveLayouts.indexOf(liveLayout)),
            onStep = { vm.setLiveLayout(cycle(liveLayouts, liveLayout, it)) },
            onClick = { vm.setLiveLayout(cycle(liveLayouts, liveLayout, 1)) },
        ),
        "live_opens_in" to RowExtra(
            SettingValue.Segmented(liveViews.map { stringResource(if (it == tv.own.owntv.core.settings.SettingsRepository.LiveView.LIST) R.string.settings_view_list else R.string.settings_seg_guide) }, liveViews.indexOf(liveView)),
            onStep = { vm.setLiveView(cycle(liveViews, liveView, it)) },
            onClick = { vm.setLiveView(cycle(liveViews, liveView, 1)) },
        ),
        "vod_layout" to RowExtra(
            SettingValue.Segmented(vodLayouts.map { stringResource(if (it == tv.own.owntv.core.settings.SettingsRepository.VodLayout.CINEMATIC) R.string.settings_vod_layout_cinematic else R.string.settings_seg_separate) }, vodLayouts.indexOf(vodLayout)),
            onStep = { vm.setVodLayout(cycle(vodLayouts, vodLayout, it)) },
            onClick = { vm.setVodLayout(cycle(vodLayouts, vodLayout, 1)) },
        ),
        "app_icon" to RowExtra(SettingValue.Opens(stringResource(appIcon.label))),
        "brand_accent" to RowExtra(SettingValue.Switch(brandAccent), onClick = { vm.setBrandAccentTriangle(!brandAccent) }),
        "check_updates" to RowExtra(SettingValue.Action(stringResource(R.string.settings_check_now))),
        "tab_LANGUAGE" to RowExtra(help = pluralStringResource(R.plurals.settings_help_language, languages, languages)),
    ) + serviceRowExtras(vm)
}

/** EPG Sources, Metadata, OpenSubtitles, Proxy and DNS: what each is set to, and the details in the panel. */
@Composable
private fun serviceRowExtras(vm: SettingsViewModel): Map<String, RowExtra> {
    val epgVm: tv.own.owntv.features.settings.EpgSourcesViewModel = org.koin.androidx.compose.koinViewModel()
    val feeds by epgVm.sources.collectAsStateWithLifecycle()
    val mode by vm.metadataMode.collectAsStateWithLifecycle()
    val tier by vm.metadataTier.collectAsStateWithLifecycle()
    val budget by vm.metadataBudgetStatus.collectAsStateWithLifecycle()
    LaunchedEffect(tier) { if (tier == tv.own.owntv.core.metadata.MetadataConfig.Tier.DEFAULT_WORKER) vm.refreshMetadataBudget() }
    val osVm: tv.own.owntv.features.settings.OpenSubtitlesViewModel = org.koin.androidx.compose.koinViewModel()
    val os by osVm.state.collectAsStateWithLifecycle()
    val proxy by vm.proxyConfig.collectAsStateWithLifecycle()
    val dns by vm.dnsConfig.collectAsStateWithLifecycle()
    val on = stringResource(R.string.common_on)
    val off = stringResource(R.string.common_off)
    val small = tv.own.owntv.ui.theme.stageText(17, 600)
    @Composable fun line(label: String, value: String) = Row(Modifier.padding(top = 10.mpx)) {
        Text(label, style = small, color = tv.own.owntv.ui.theme.StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, style = small, color = tv.own.owntv.ui.theme.StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val signedIn = os as? tv.own.owntv.features.settings.OpenSubtitlesViewModel.UiState.SignedIn
    val dnsHost = dns.dohUrl.ifBlank { dns.host }.removePrefix("https://").substringBefore('/')
    return mapOf(
        "tab_EPG" to RowExtra(
            SettingValue.Opens(pluralStringResource(R.plurals.settings_epg_feed_count, feeds.size, feeds.size)),
            extra = {
                // As Playlists draws its list: the name in bold, when it last synced in small muted type.
                Column(Modifier.padding(top = 18.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                    feeds.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.name, style = tv.own.owntv.ui.theme.stageText(18, 700), color = tv.own.owntv.ui.theme.StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            f.lastSyncAt?.let {
                                Text(
                                    stringResource(R.string.settings_synced_at, tv.own.owntv.features.downloads.recordingWhen(it)),
                                    style = tv.own.owntv.ui.theme.stageText(15, 500), color = tv.own.owntv.ui.theme.StageColors.Muted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
        ),
        "tab_METADATA" to RowExtra(
            SettingValue.Opens(if (!mode.enrich) off else when (tier) {
                tv.own.owntv.core.metadata.MetadataConfig.Tier.DEFAULT_WORKER -> stringResource(R.string.settings_shared)
                tv.own.owntv.core.metadata.MetadataConfig.Tier.OWN_KEY -> stringResource(R.string.settings_tier_key)
                tv.own.owntv.core.metadata.MetadataConfig.Tier.SELF_HOST -> stringResource(R.string.settings_tier_self_host)
            }),
            extra = {
                val b = budget
                if (mode.enrich) Column(Modifier.padding(top = 8.mpx)) {
                    line(stringResource(R.string.settings_metadata_connection), stringResource(when (tier) {
                        tv.own.owntv.core.metadata.MetadataConfig.Tier.DEFAULT_WORKER -> R.string.settings_tier_default
                        tv.own.owntv.core.metadata.MetadataConfig.Tier.OWN_KEY -> R.string.settings_tier_key
                        tv.own.owntv.core.metadata.MetadataConfig.Tier.SELF_HOST -> R.string.settings_tier_self_host
                    }))
                    // The shared service has a daily allowance; an own key or self-host has none to show.
                    if (tier == tv.own.owntv.core.metadata.MetadataConfig.Tier.DEFAULT_WORKER && b != null) {
                        line(stringResource(R.string.settings_allowance_day), pluralStringResource(R.plurals.settings_allowance_value, b.remainingDay, b.remainingDay, b.limitDay))
                    }
                }
            },
        ),
        "tab_OPEN_SUBTITLES" to RowExtra(
            SettingValue.Opens(stringResource(if (signedIn != null) R.string.settings_open_subtitles_connected else R.string.settings_signed_out)),
            extra = {
                signedIn?.session?.let { s ->
                    Column(Modifier.padding(top = 8.mpx)) {
                        line(stringResource(R.string.player_subtitles_connected_as), s.username)
                        s.remainingDownloads?.let { r ->
                            line(stringResource(R.string.player_subtitles_downloads), pluralStringResource(R.plurals.player_subtitles_remaining_short, r, r))
                        }
                    }
                }
            },
        ),
        "tab_NETWORK" to RowExtra(SettingValue.Opens(if (proxy.usable) "${proxy.host}:${proxy.port}" else off)),
        "tab_DNS" to RowExtra(SettingValue.Opens(if (dns.enabled && dnsHost.isNotBlank()) dnsHost else if (dns.enabled) on else off)),
    )
}
