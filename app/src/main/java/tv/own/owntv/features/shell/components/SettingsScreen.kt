package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.customize.CustomizeScreen
import tv.own.owntv.core.i18n.SupportedLocales
import tv.own.owntv.core.player.SurroundMode
import tv.own.owntv.features.settings.HomeSettingsScreen
import tv.own.owntv.features.settings.LanguageSettingsScreen
import tv.own.owntv.features.settings.LanguageSettingsViewModel
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.update.UpdateDialog
import tv.own.owntv.features.settings.BackupScreen
import tv.own.owntv.features.settings.ManageProfilesScreen
import tv.own.owntv.features.settings.ManageSourcesScreen
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.settings.VideoPlayerSettingsScreen
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.ui.components.BrandLockup
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.components.BackgroundImageChooserDialog
import tv.own.owntv.ui.components.ingestBackgroundImage
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.longPressMenuGuard
import tv.own.owntv.ui.format.formatBestDateTime
import tv.own.owntv.ui.theme.ALL_GLASS_SURFACES
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.core.theme.GlassConfig
import tv.own.owntv.ui.theme.GlassInteraction
import tv.own.owntv.core.theme.GlassPreset
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.glass
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.PopupFontScale
import tv.own.owntv.core.theme.PopupSizeScale
import tv.own.owntv.ui.theme.LocalGlass
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.player.displayText
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.ui.theme.asComposeFamily
import kotlin.math.roundToInt
import java.io.File
import java.util.Locale
import tv.own.owntv.ui.theme.labelRes
import tv.own.owntv.ui.theme.primary

internal enum class TileTone { PRIMARY, SECONDARY, TERTIARY }

/**
 * The icon-tile tone for the rows *inside* a settings sub-screen. A sub-screen is opened by exactly
 * one root row, so its rows take that row's tone — otherwise e.g. Weather is a grey tile on the root
 * list and an accent tile the moment you open it. Provided per sub-screen at the dispatch below;
 * anything not listed there stays PRIMARY.
 */
internal val LocalSettingsRowTone = staticCompositionLocalOf { TileTone.PRIMARY }

/** Wraps a sub-screen so its rows match the tone of the root row that opens it (see `rootItems`). */
@Composable
private fun Toned(tone: TileTone, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalSettingsRowTone provides tone, content = content)

private enum class SettingsTab { ROOT, LANGUAGE, SOURCES, EPG, PROFILES, BACKUP, VIDEO, CUSTOMIZE, HOME, NETWORK, DNS, METADATA, OPEN_SUBTITLES, WEATHER, NAV_MENU, CH_NAV, PANEL_WIDTH, GUIDE_WIDTH, GLASS_EFFECT, CONTENT_MENUS }

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
    openEpgAdd: Boolean = false,
    onEpgAddConsumed: () -> Unit = {},
) {
    // A cross-script language change recreates the Activity so Android can apply the new script's
    // shaping and font fallback. Keep the open settings sub-screen across that configuration change
    // instead of dropping back to the Settings root/sidebar.
    var tab by rememberSaveable { mutableStateOf(SettingsTab.ROOT) }
    // Deep-link from the Guide's "Add EPG" button: jump straight to EPG Sources in add mode.
    var consumeEpgAdd by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var showPopupSize by remember { mutableStateOf(false) }
    var showFontCustomization by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    var showFocusHighlight by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showCatchupTime by remember { mutableStateOf(false) }
    var showEpgOffset by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    var showAnimations by remember { mutableStateOf(false) }
    var showStartup by remember { mutableStateOf(false) }
    var showStartupChannelPicker by remember { mutableStateOf(false) }
    var showErrorLog by remember { mutableStateOf(false) }
    var showAfrWarning by remember { mutableStateOf(false) }
    var showLivePreviewPanelWarning by remember { mutableStateOf(false) }
    var showBgImageChooser by remember { mutableStateOf(false) }
    var showBgPicker by remember { mutableStateOf(false) }
    var showBgRemote by remember { mutableStateOf(false) }
    var showAmbientGlow by remember { mutableStateOf(false) }
    var showBrowsing by remember { mutableStateOf(false) }
    val browsingRowFocus = remember { FocusRequester() }
    // U2 — background-image ingest copies a multi-megabyte file; it runs here, off the main thread.
    val ingestScope = rememberCoroutineScope()

    // Batch 4 · Settings search + quick toggles. Empty query = normal grouped list; a non-blank
    // query swaps the list for flat results that carry their group context ("Playback › HDR").
    var searchQuery by remember { mutableStateOf("") }
    // Search is a chip in the one-line header until it is opened.
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFieldFocus = remember { FocusRequester() }
    // While searching, Back clears the query (and returns focus to the field) instead of leaving Settings.
    BackHandler(enabled = tab == SettingsTab.ROOT && searchQuery.isNotBlank()) {
        searchQuery = ""
        runCatching { searchFieldFocus.requestFocus() }
    }

    // Dialog-close focus return: closing a dialog/picker refocuses the row that opened it (focus
    // would otherwise fall spatially back to the sidebar).
    val folderRowFocus = remember { FocusRequester() }
    val themeRowFocus = remember { FocusRequester() }
    val accentRowFocus = remember { FocusRequester() }
    val focusHighlightRowFocus = remember { FocusRequester() }
    val zoomRowFocus = remember { FocusRequester() }
    val popupSizeRowFocus = remember { FocusRequester() }
    val fontCustomizationRowFocus = remember { FocusRequester() }
    val updateRowFocus = remember { FocusRequester() }
    val aboutRowFocus = remember { FocusRequester() }
    val catchupRowFocus = remember { FocusRequester() }
    val epgOffsetRowFocus = remember { FocusRequester() }
    val clearHistoryRowFocus = remember { FocusRequester() }
    val animationsRowFocus = remember { FocusRequester() }
    val startupRowFocus = remember { FocusRequester() }
    val errorLogRowFocus = remember { FocusRequester() }
    val livePreviewQuickFocus = remember { FocusRequester() }
    val ambientGlowRowFocus = remember { FocusRequester() }
    // Hoisted list state for the root settings list. We snapshot its position the instant a row is
    // clicked (in onClick, before any recomposition) and restore it on dialog close, so the list
    // doesn't visibly jump/scroll when the dialog opens or when we refocus the opener row afterward.
    // A lazy list carries its position as item index + offset into that item, so both are saved.
    val listState = rememberLazyListState()
    // These belong to the two-pane Settings root, but stay remembered while a detail screen replaces
    // it. Otherwise Back briefly rebuilds Quick at row zero before restoring the real group/row.
    var selectedGroup by rememberSaveable { mutableIntStateOf(0) }
    var displayedGroup by rememberSaveable { mutableIntStateOf(0) }
    val spineState = rememberLazyListState()
    var savedIndex by remember { mutableIntStateOf(0) }
    var savedOffset by remember { mutableIntStateOf(0) }
    val saveScroll = {
        savedIndex = listState.firstVisibleItemIndex
        savedOffset = listState.firstVisibleItemScrollOffset
    }
    val anyDialogOpen = showZoom || showPopupSize || showFontCustomization || showTheme || showAccent || showFolderPicker || showUpdate || showAbout || showCatchupTime || showEpgOffset || showClearHistory || showAnimations || showStartup || showStartupChannelPicker || showErrorLog || showAfrWarning || showLivePreviewPanelWarning || showBgImageChooser || showBgPicker || showAmbientGlow || showBrowsing || showFocusHighlight || showBgRemote
    // When a dialog closes, restore focus to the row that opened it. NOTE: this restore crosses
    // INTO the root focus group from outside (the dialog), but onEnter does NOT fire for programmatic
    // requestsFocus (only for directional entry) — so dialogReturn must be cleared HERE, not in onEnter.
    // If it's left set, the next directional entry (e.g. sidebar→here) would re-route to a stale row.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(showZoom, showPopupSize, showFontCustomization, showTheme, showAccent, showFolderPicker, showUpdate, showAbout, showCatchupTime, showEpgOffset, showClearHistory, showAnimations, showStartup, showStartupChannelPicker, showErrorLog, showAfrWarning, showLivePreviewPanelWarning, showBgImageChooser, showBgPicker, showAmbientGlow, showBrowsing, showFocusHighlight, showBgRemote) {
        if (!anyDialogOpen) {
            // Focus back on the opener row, with the scroll offset held still the whole way — see
            // [restoreAfterDialogClose] for why doing those two in sequence made the highlight travel.
            tv.own.owntv.ui.components.restoreAfterDialogClose(dialogReturn, listState, savedIndex, savedOffset)
            dialogReturn = null
        }
    }
    val settingsVm: SettingsViewModel = koinViewModel()
    val languageVm: LanguageSettingsViewModel = koinViewModel()
    val currentLocaleTag by languageVm.currentTag.collectAsStateWithLifecycle()
    val languageChip = languageChipText(currentLocaleTag)
    val downloadRoot by settingsVm.downloadRoot.collectAsStateWithLifecycle()
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
    val catchupChannels by settingsVm.catchupChannelCount.collectAsStateWithLifecycle()
    val catchupPlayer by settingsVm.catchupPlayer.collectAsStateWithLifecycle()
    val accent by settingsVm.accent.collectAsStateWithLifecycle()
    val customAccent by settingsVm.customAccent.collectAsStateWithLifecycle()
    val focusHighlight by settingsVm.focusHighlight.collectAsStateWithLifecycle()
    val focusHighlightWidth by settingsVm.focusHighlightWidth.collectAsStateWithLifecycle()
    val bgImagePath by settingsVm.bgImagePath.collectAsStateWithLifecycle()
    val glassConfig by settingsVm.glassConfig.collectAsStateWithLifecycle()
    val glassOn = glassConfig.enabled
    val animationLevel by settingsVm.animationLevel.collectAsStateWithLifecycle()
    val ambientGlowEnabled by settingsVm.ambientGlowEnabled.collectAsStateWithLifecycle()
    val ambientGlowPulse by settingsVm.ambientGlowPulse.collectAsStateWithLifecycle()
    LaunchedEffect(glassOn, themeMode) {
        if (glassOn || themeMode != ThemeMode.DARK) showAmbientGlow = false
    }
    val weatherEnabled by settingsVm.weatherEnabled.collectAsStateWithLifecycle()
    val startupMode by settingsVm.startupMode.collectAsStateWithLifecycle()
    val startupChannel by settingsVm.startupChannel.collectAsStateWithLifecycle()
    val startupChannelQuery by settingsVm.startupChannelQuery.collectAsStateWithLifecycle()
    val startupChannelResults by settingsVm.startupChannelResults.collectAsStateWithLifecycle()
    val navMenuMode by settingsVm.navMenuMode.collectAsStateWithLifecycle()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val rememberLastLive by settingsVm.rememberLastLive.collectAsStateWithLifecycle()
    val rememberLastMovies by settingsVm.rememberLastMovies.collectAsStateWithLifecycle()
    val rememberLastSeries by settingsVm.rememberLastSeries.collectAsStateWithLifecycle()
    val rememberCatLive by settingsVm.rememberCategoryLive.collectAsStateWithLifecycle()
    val rememberCatMovies by settingsVm.rememberCategoryMovies.collectAsStateWithLifecycle()
    val rememberCatSeries by settingsVm.rememberCategorySeries.collectAsStateWithLifecycle()
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
    val rowFocus = remember { mapOf(
        SettingsTab.LANGUAGE to FocusRequester(),
        SettingsTab.SOURCES to FocusRequester(),
        SettingsTab.EPG to FocusRequester(),
        SettingsTab.PROFILES to FocusRequester(),
        SettingsTab.BACKUP to FocusRequester(),
        SettingsTab.VIDEO to FocusRequester(),
        SettingsTab.CUSTOMIZE to FocusRequester(),
        SettingsTab.HOME to FocusRequester(),
        SettingsTab.NETWORK to FocusRequester(),
        SettingsTab.DNS to FocusRequester(),
        SettingsTab.METADATA to FocusRequester(),
        SettingsTab.OPEN_SUBTITLES to FocusRequester(),
        SettingsTab.WEATHER to FocusRequester(),
        SettingsTab.NAV_MENU to FocusRequester(),
        SettingsTab.CH_NAV to FocusRequester(),
        SettingsTab.CONTENT_MENUS to FocusRequester(),
            SettingsTab.PANEL_WIDTH to FocusRequester(),
            SettingsTab.GUIDE_WIDTH to FocusRequester(),
            SettingsTab.GLASS_EFFECT to FocusRequester(),
    ) }
    // Mini player is a popup on the Video player screen, not a screen of its own. The settings search
    // still lists it by name, so it needs a way to say "open that popup on arrival".
    var openMiniPlayer by rememberSaveable { mutableStateOf(false) }
    // A Quick shortcut into Video player settings: which section to show, which row to focus there,
    // and — for the way back — which Quick row it was.
    var videoSection by rememberSaveable { mutableStateOf<Int?>(null) }
    var videoRowKey by rememberSaveable { mutableStateOf<String?>(null) }
    var deepReturnKey by rememberSaveable { mutableStateOf<String?>(null) }
    val deepRowFocus = remember { FocusRequester() }
    // Opening a sub-screen the ordinary way cancels any pending Quick-shortcut return, or the Back
    // from it would aim at the shortcut instead of the row just used.
    val open: (SettingsTab) -> Unit = { lastTab = it; deepReturnKey = null; videoSection = null; videoRowKey = null; tab = it }
    LaunchedEffect(openEpgAdd) {
        if (openEpgAdd) { consumeEpgAdd = true; open(SettingsTab.EPG); onEpgAddConsumed() }
    }

    when (tab) {
        SettingsTab.LANGUAGE -> { LanguageSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.SOURCES -> { ManageSourcesScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.EPG -> { tv.own.owntv.features.settings.EpgSourcesScreen(onBack = { tab = SettingsTab.ROOT; consumeEpgAdd = false }, modifier = modifier, startOnAdd = consumeEpgAdd); return }
        SettingsTab.PROFILES -> { ManageProfilesScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.BACKUP -> { Toned(TileTone.TERTIARY) { BackupScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }; return }
        SettingsTab.VIDEO -> {
            Toned(TileTone.TERTIARY) {
                VideoPlayerSettingsScreen(
                    onBack = {
                        tab = SettingsTab.ROOT
                        openMiniPlayer = false
                        videoSection = null
                        videoRowKey = null
                    },
                    openMiniPlayer = openMiniPlayer,
                    openSection = videoSection,
                    focusRowKey = videoRowKey,
                    modifier = modifier,
                )
            }
            return
        }
        SettingsTab.CUSTOMIZE -> { CustomizeScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.HOME -> { Toned(TileTone.SECONDARY) { HomeSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }; return }
        SettingsTab.NETWORK -> { Toned(TileTone.SECONDARY) { tv.own.owntv.features.settings.NetworkSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }; return }
        SettingsTab.DNS -> { Toned(TileTone.SECONDARY) { tv.own.owntv.features.settings.DnsSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }; return }
        SettingsTab.METADATA -> { tv.own.owntv.features.settings.MetadataSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.OPEN_SUBTITLES -> { tv.own.owntv.features.settings.OpenSubtitlesAccountScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.WEATHER -> { Toned(TileTone.SECONDARY) { tv.own.owntv.features.settings.WeatherSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier) }; return }
        SettingsTab.NAV_MENU -> { tv.own.owntv.features.settings.NavMenuSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.CH_NAV -> { tv.own.owntv.features.settings.ChNavSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.CONTENT_MENUS -> { tv.own.owntv.features.settings.ContentMenuSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
            SettingsTab.PANEL_WIDTH -> { tv.own.owntv.features.settings.PanelWidthSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
            SettingsTab.GUIDE_WIDTH -> { tv.own.owntv.features.settings.GuideWidthSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
            SettingsTab.GLASS_EFFECT -> { GlassEffectSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
            SettingsTab.ROOT -> Unit
    }

    val colors = OwnTVTheme.colors

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
        RootRow(
            tabRowKey(SettingsTab.PROFILES), TileTone.SECONDARY, OwnTVIcon.PERSON,
            title = stringResource(R.string.profiles_title), desc = stringResource(R.string.settings_profiles_description),
            focus = rowFocus.getValue(SettingsTab.PROFILES),
            onClick = { open(SettingsTab.PROFILES) },
        ),
        RootGroup("group_sources", stringResource(R.string.settings_group_sources), OwnTVIcon.PLAYLIST, stringResource(R.string.settings_group_summary_sources)),
        RootRow(
            tabRowKey(SettingsTab.SOURCES), TileTone.PRIMARY, OwnTVIcon.PLAYLIST,
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
        RootRow(
            "epg_offset", TileTone.SECONDARY, OwnTVIcon.EPG,
            title = stringResource(R.string.content_epg_time_offset),
            desc = stringResource(R.string.settings_epg_offset_root_description),
            chip = epgShiftLabel(epgOffset),
            chipTone = if (epgOffset == 0) TileTone.SECONDARY else TileTone.PRIMARY,
            focus = epgOffsetRowFocus,
            onClick = { saveScroll(); dialogReturn = epgOffsetRowFocus; showEpgOffset = true },
        ),
        // Sits with the EPG offset, not with Playback: both answer "the guide/archive clock is wrong",
        // and a user fixing one almost always looks at the other next.
        RootRow(
            "catchup", TileTone.SECONDARY, OwnTVIcon.CATCHUP,
            title = stringResource(R.string.settings_catchup),
            desc = if (catchupChannels > 0) pluralStringResource(R.plurals.settings_catchup_supported, catchupChannels, catchupChannels)
                else stringResource(R.string.settings_catchup_unavailable),
            chip = when (catchupTz) {
                SettingsRepository.CatchupTimezone.DEVICE -> stringResource(R.string.settings_device)
                SettingsRepository.CatchupTimezone.MANUAL -> utcOffsetLabel(catchupOffset)
            },
            chipTone = TileTone.PRIMARY,
            focus = catchupRowFocus,
            onClick = { saveScroll(); dialogReturn = catchupRowFocus; showCatchupTime = true },
        ),
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
        // Glass Effect has enough controls to be a full settings screen; the root row only summarizes it.
        RootRow(
            tabRowKey(SettingsTab.GLASS_EFFECT), TileTone.PRIMARY, OwnTVIcon.SPARKLE,
            title = stringResource(R.string.settings_glass_effect), desc = stringResource(R.string.settings_glass_description),
            chip = if (glassOn) glassPresetLabel(glassConfig.preset) else stringResource(R.string.common_off),
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
            focus = fontCustomizationRowFocus,
            onClick = { saveScroll(); dialogReturn = fontCustomizationRowFocus; showFontCustomization = true },
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
        RootRow(
            tabRowKey(SettingsTab.NAV_MENU), TileTone.PRIMARY, OwnTVIcon.MENU,
            title = stringResource(R.string.settings_sidebar_customization), desc = stringResource(R.string.settings_sidebar_description_root),
            chip = navModeLabel(navMenuMode),
            chipTone = if (navMenuMode == tv.own.owntv.core.settings.SettingsRepository.NavMenuMode.DYNAMIC) TileTone.PRIMARY else TileTone.SECONDARY,
            focus = rowFocus.getValue(SettingsTab.NAV_MENU),
            onClick = { open(SettingsTab.NAV_MENU) },
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
            focus = browsingRowFocus,
            onClick = { saveScroll(); dialogReturn = browsingRowFocus; showBrowsing = true },
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
        RootGroup("group_playback", stringResource(R.string.settings_playback_group), OwnTVIcon.VIDEO, stringResource(R.string.settings_group_summary_playback)),
        RootRow(
            tabRowKey(SettingsTab.VIDEO), TileTone.TERTIARY, OwnTVIcon.VIDEO,
            title = stringResource(R.string.settings_video_player), desc = stringResource(R.string.settings_video_player_description),
            focus = rowFocus.getValue(SettingsTab.VIDEO),
            onClick = { open(SettingsTab.VIDEO) },
        ),
        RootGroup("group_network", stringResource(R.string.settings_network_group), OwnTVIcon.NETWORK, stringResource(R.string.settings_group_summary_network)),
        RootRow(
            tabRowKey(SettingsTab.NETWORK), TileTone.SECONDARY, OwnTVIcon.NETWORK,
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
        RootGroup("group_data", stringResource(R.string.settings_group_data), OwnTVIcon.BACKUP, stringResource(R.string.settings_group_summary_data)),
        RootRow(
            tabRowKey(SettingsTab.BACKUP), TileTone.TERTIARY, OwnTVIcon.BACKUP,
            title = stringResource(R.string.settings_backup_restore), desc = stringResource(R.string.settings_backup_restore_description),
            focus = rowFocus.getValue(SettingsTab.BACKUP),
            onClick = { open(SettingsTab.BACKUP) },
        ),
        RootRow(
            "download_folder", TileTone.TERTIARY, OwnTVIcon.DOWNLOADS,
            title = stringResource(R.string.settings_download_folder),
            chip = downloadRoot.ifBlank { stringResource(R.string.settings_app_storage) }.let { java.io.File(it).name.ifBlank { it } },
            chipTone = TileTone.TERTIARY,
            focus = folderRowFocus,
            onClick = { saveScroll(); dialogReturn = folderRowFocus; showFolderPicker = true },
        ),
        RootRow(
            "clear_history", TileTone.SECONDARY, OwnTVIcon.HISTORY,
            title = stringResource(R.string.settings_clear_history), desc = stringResource(R.string.settings_clear_history_description),
            focus = clearHistoryRowFocus,
            onClick = { saveScroll(); dialogReturn = clearHistoryRowFocus; showClearHistory = true },
        ),
        RootGroup("group_app", stringResource(R.string.settings_app_group), OwnTVIcon.INFO, stringResource(R.string.settings_group_summary_app)),
        RootRow(
            tabRowKey(SettingsTab.LANGUAGE), TileTone.PRIMARY, OwnTVIcon.LANGUAGE,
            title = stringResource(R.string.settings_language),
            desc = stringResource(R.string.settings_language_description),
            chip = languageChip,
            chipTone = TileTone.PRIMARY,
            focus = rowFocus.getValue(SettingsTab.LANGUAGE),
            onClick = { open(SettingsTab.LANGUAGE) },
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
            "about", TileTone.SECONDARY, OwnTVIcon.INFO,
            title = stringResource(R.string.settings_about), desc = stringResource(R.string.settings_about_description),
            focus = aboutRowFocus,
            onClick = { saveScroll(); dialogReturn = aboutRowFocus; showAbout = true },
        ),
        // Last row in the app, deliberately: the log now carries the last crash as well as playback
        // failures, so it belongs with About rather than under Playback.
        RootRow(
            "error_log", TileTone.SECONDARY, OwnTVIcon.WARNING,
            title = stringResource(R.string.settings_playback_error_log), desc = stringResource(R.string.settings_playback_error_description),
            focus = errorLogRowFocus,
            onClick = { saveScroll(); dialogReturn = errorLogRowFocus; showErrorLog = true },
        ),
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
        val jump = {
            lastTab = null
            deepReturnKey = ref.key
            videoSection = ref.section
            videoRowKey = ref.key
            tab = SettingsTab.VIDEO
        }
        ref.key to RootRow(
            key = ref.key,
            tone = TileTone.TERTIARY,
            icon = ref.icon,
            title = stringResource(ref.titleRes),
            desc = ref.descRes?.let { stringResource(it) },
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
    /** Which category each row key lives in, so a Back from a sub-screen can reselect its group. */
    val groupOfKey: Map<String, Int> = remember(categories) {
        buildMap { categories.forEachIndexed { g, (_, rows) -> rows.forEach { put(it.key, g) } } }
    }
    // Opening the search chip has to carry focus into the field it was replaced by — one frame later,
    // once that field exists.
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            withFrameNanos { }
            runCatching { searchFieldFocus.requestFocus() }
        }
    }
    val selectedRows = categories.getOrNull(selectedGroup)?.second.orEmpty()
    // Which column has the cursor, so the sheet's count tag can go accent and say so.
    var sheetFocused by remember { mutableStateOf(false) }
    // Requester on the *selected* category, so a directional entry from the sidebar lands on the
    // group the user last used rather than on whatever row happens to be nearest.
    val selectedCategoryFocus = remember { FocusRequester() }
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
        // anything — then the group column takes focus rather than nothing at all.
        if (runCatching { menuReturnFocus.requestFocus() }.isFailure) {
            runCatching { selectedCategoryFocus.requestFocus() }
        }
    }
    // Back on the empty search field collapses it back to a chip and hands focus to the spine.
    // Deliberately NOT tied to the field losing focus: the field hands focus to its own inner editor
    // when OK opens the keyboard, so a focus-loss rule would slam it shut on the very keypress that
    // starts typing.
    BackHandler(enabled = tab == SettingsTab.ROOT && searchExpanded && searchQuery.isBlank()) {
        searchExpanded = false
        runCatching { selectedCategoryFocus.requestFocus() }
    }
    // Back inside the rows goes up a level to the groups rather than out of Settings — the same step
    // Left makes, so whichever the user reaches for does the same thing.
    BackHandler(enabled = tab == SettingsTab.ROOT && sheetFocused && searchQuery.isBlank()) {
        runCatching { selectedCategoryFocus.requestFocus() }
    }
    // A genuinely new group starts at its first row. Recreating the root after a sub-screen does not:
    // its group and list position were kept above the sub-screen dispatch, so leave them untouched.
    LaunchedEffect(selectedGroup) {
        if (displayedGroup != selectedGroup) {
            runCatching { listState.scrollToItem(0) }
            displayedGroup = selectedGroup
        }
    }

    // Restore focus to the row a sub-screen was opened from when the user navigates back. Fresh entry
    // intentionally does NOT grab focus here — every other main-menu section lets the shell/sidebar
    // own initial focus, and Settings stays consistent with them. This block only exists while the
    // root list is showing, so coming back from a sub-screen is exactly when it runs.
    val returningRowFocus = when {
        searchQuery.isNotBlank() && (deepReturnKey != null || lastTab != null) -> searchFieldFocus
        deepReturnKey != null -> deepRowFocus
        else -> lastTab?.let { rowFocus[it] }
    }
    LaunchedEffect(Unit) {
        // Either the row that opened a sub-screen, or the Quick shortcut that jumped inside one.
        val deep = deepReturnKey
        val key = deep ?: lastTab?.let(::tabRowKey)
        val target = returningRowFocus ?: return@LaunchedEffect
        val group = key?.let(groupOfKey::get)
        if (searchQuery.isBlank() && group != null) {
            selectedGroup = group
        }
        // Wait by frames rather than showing another focus target for fixed delays. A lazy row may need
        // a layout pass after its group is selected; once it lands, hold it for a few frames so a late
        // focus-group entry cannot move the cursor to the category column.
        var settledFrames = 0
        repeat(10) {
            withFrameNanos { }
            if (searchQuery.isBlank() && group != null) {
                val index = categories[group].second.indexOfFirst { it.key == key }
                if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                    runCatching { listState.scrollToItem(index) }
                }
            }
            if (runCatching { target.requestFocus() }.getOrDefault(false)) {
                settledFrames++
                if (settledFrames >= 3) {
                    lastTab = null
                    deepReturnKey = null
                    return@LaunchedEffect
                }
            } else {
                settledFrames = 0
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
        val videoPlayerGroup = stringResource(
            R.string.settings_breadcrumb,
            stringResource(R.string.settings_group_playback),
            stringResource(R.string.settings_video_player),
        )
        val entries = listOfNotNull(
            SettingsSearchEntry(stringResource(R.string.settings_app_group), stringResource(R.string.settings_language), stringResource(R.string.settings_search_keywords_language), OwnTVIcon.LANGUAGE, TileTone.PRIMARY,
                chip = languageChip, chipTone = TileTone.PRIMARY) { open(SettingsTab.LANGUAGE) },
            SettingsSearchEntry(stringResource(R.string.settings_group_profile), stringResource(R.string.profiles_title), stringResource(R.string.settings_search_keywords_profiles), OwnTVIcon.PERSON, TileTone.SECONDARY) { open(SettingsTab.PROFILES) },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_playlists), stringResource(R.string.settings_search_keywords_playlists), OwnTVIcon.PLAYLIST, TileTone.PRIMARY) { open(SettingsTab.SOURCES) },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_epg_sources), stringResource(R.string.settings_search_keywords_epg), OwnTVIcon.EPG, TileTone.PRIMARY) { open(SettingsTab.EPG) },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.content_epg_time_offset), stringResource(R.string.settings_search_keywords_epg_offset), OwnTVIcon.EPG, TileTone.SECONDARY,
                chip = epgShiftLabel(epgOffset), chipTone = if (epgOffset == 0) TileTone.SECONDARY else TileTone.PRIMARY) { saveScroll(); dialogReturn = searchFieldFocus; showEpgOffset = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_search_guide_logos), stringResource(R.string.settings_search_keywords_logos), OwnTVIcon.EPG, TileTone.SECONDARY) { open(SettingsTab.EPG) },
            SettingsSearchEntry(stringResource(R.string.settings_group_content_metadata), stringResource(R.string.settings_customize), stringResource(R.string.settings_search_keywords_customize), OwnTVIcon.SORT, TileTone.PRIMARY) { open(SettingsTab.CUSTOMIZE) },
            SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_sidebar_customization), stringResource(R.string.settings_search_keywords_sidebar), OwnTVIcon.MENU, TileTone.PRIMARY,
                chip = navModeLabel(navMenuMode), chipTone = if (navMenuMode == tv.own.owntv.core.settings.SettingsRepository.NavMenuMode.DYNAMIC) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.NAV_MENU) },
            SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_ch_paging), stringResource(R.string.settings_search_keywords_ch), OwnTVIcon.CH_NAV, TileTone.PRIMARY,
                chip = if (chNavEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (chNavEnabled) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.CH_NAV) },
            SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_panel_width), stringResource(R.string.settings_search_keywords_panel_width), OwnTVIcon.PANEL_WIDTH, TileTone.PRIMARY,
                chip = if (panelWidthCustom) stringResource(R.string.settings_live_latency_custom) else stringResource(R.string.settings_subtitle_default), chipTone = if (panelWidthCustom) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.PANEL_WIDTH) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_guide_width), stringResource(R.string.settings_search_keywords_guide_width), OwnTVIcon.EPG, TileTone.PRIMARY,
            chip = if (guideWidthCustom) stringResource(R.string.settings_live_latency_custom) else stringResource(R.string.settings_subtitle_default), chipTone = if (guideWidthCustom) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.GUIDE_WIDTH) },
        SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_browsing_lists), stringResource(R.string.settings_search_keywords_browsing), OwnTVIcon.LIST_GRID, TileTone.PRIMARY) { saveScroll(); dialogReturn = browsingRowFocus; showBrowsing = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_layout), stringResource(R.string.settings_home_root), stringResource(R.string.settings_search_keywords_home), OwnTVIcon.HOME, TileTone.SECONDARY) { open(SettingsTab.HOME) },
            SettingsSearchEntry(stringResource(R.string.settings_group_content_metadata), stringResource(R.string.settings_metadata), stringResource(R.string.settings_search_keywords_metadata), OwnTVIcon.IMAGE, TileTone.PRIMARY) { open(SettingsTab.METADATA) },
            SettingsSearchEntry(stringResource(R.string.settings_group_data), stringResource(R.string.settings_download_folder), stringResource(R.string.settings_search_keywords_download), OwnTVIcon.DOWNLOADS, TileTone.TERTIARY,
                chip = downloadRoot.ifBlank { stringResource(R.string.settings_app_storage) }.let { java.io.File(it).name.ifBlank { it } }, chipTone = TileTone.TERTIARY) { saveScroll(); dialogReturn = searchFieldFocus; showFolderPicker = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_data), stringResource(R.string.settings_backup_restore), stringResource(R.string.settings_search_keywords_backup), OwnTVIcon.BACKUP, TileTone.TERTIARY) { open(SettingsTab.BACKUP) },
            SettingsSearchEntry(stringResource(R.string.settings_group_data), stringResource(R.string.settings_clear_history), stringResource(R.string.settings_search_keywords_history), OwnTVIcon.HISTORY, TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showClearHistory = true },
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
        ) { saveScroll(); dialogReturn = searchFieldFocus; showFontCustomization = true },
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
            SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_animations), stringResource(R.string.settings_search_keywords_animation), OwnTVIcon.MOTION, TileTone.SECONDARY,
                chip = stringResource(animationLevel.labelRes), chipTone = TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showAnimations = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_appearance), stringResource(R.string.settings_weather), stringResource(R.string.settings_search_keywords_weather), OwnTVIcon.WEATHER, TileTone.SECONDARY,
                chip = if (weatherEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (weatherEnabled) TileTone.PRIMARY else TileTone.SECONDARY) { open(SettingsTab.WEATHER) },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_quick_live_preview), stringResource(R.string.settings_search_keywords_live_preview), OwnTVIcon.LIVE_TV, TileTone.TERTIARY,
                chip = if (livePreview) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (livePreview) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { toggleLivePreview(searchFieldFocus) },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_preview_audio), stringResource(R.string.settings_search_keywords_sound), OwnTVIcon.AUDIO, TileTone.SECONDARY,
                chip = if (previewAudio) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (previewAudio) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setLivePreviewAudio(!previewAudio) },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_quick_channel_numbers), stringResource(R.string.settings_search_keywords_channel_numbers), OwnTVIcon.LIVE_TV, TileTone.PRIMARY,
                chip = if (channelNumbers) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (channelNumbers) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setDirectTune(!channelNumbers) },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_mini_player_root), stringResource(R.string.settings_search_keywords_mini), OwnTVIcon.PIP, TileTone.TERTIARY) { openMiniPlayer = true; open(SettingsTab.VIDEO) },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_quick_hdr), stringResource(R.string.settings_search_keywords_hdr), OwnTVIcon.VIDEO, TileTone.PRIMARY,
                chip = if (hdr) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (hdr) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setHdrEnabled(!hdr) },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_auto_frame_rate), stringResource(R.string.settings_search_keywords_afr), OwnTVIcon.VIDEO, TileTone.PRIMARY,
                chip = if (autoFrameRate) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (autoFrameRate) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { toggleAutoFrameRate(searchFieldFocus) },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_surround_sound), stringResource(R.string.settings_search_keywords_surround), OwnTVIcon.AUDIO, TileTone.SECONDARY,
                chip = surroundModeLabel(surroundMode), chipTone = if (surroundMode == SurroundMode.STEREO) TileTone.SECONDARY else TileTone.PRIMARY, showChevron = false) { settingsVm.cycleSurroundMode() },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_autoplay_next), stringResource(R.string.settings_search_keywords_autoplay), OwnTVIcon.AUTOPLAY_NEXT, TileTone.SECONDARY,
                chip = if (autoPlayNext) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (autoPlayNext) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setAutoPlayNext(!autoPlayNext) },
            SettingsSearchEntry(stringResource(R.string.settings_group_sources), stringResource(R.string.settings_catchup), stringResource(R.string.settings_search_keywords_catchup), OwnTVIcon.CATCHUP, TileTone.SECONDARY,
                chip = when (catchupTz) {
                    SettingsRepository.CatchupTimezone.DEVICE -> stringResource(R.string.settings_device)
                    SettingsRepository.CatchupTimezone.MANUAL -> utcOffsetLabel(catchupOffset)
                }) { saveScroll(); dialogReturn = searchFieldFocus; showCatchupTime = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_playback), stringResource(R.string.settings_video_player), stringResource(R.string.settings_search_keywords_video), OwnTVIcon.VIDEO, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_subtitle_appearance), stringResource(R.string.settings_search_keywords_subtitle_appearance), OwnTVIcon.SUBTITLE, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_live_latency), stringResource(R.string.settings_search_keywords_latency), OwnTVIcon.LIVE_TV, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_live_preroll), stringResource(R.string.settings_search_keywords_live_preroll), OwnTVIcon.LIVE_TV, TileTone.TERTIARY) { open(SettingsTab.VIDEO) },
            SettingsSearchEntry(stringResource(R.string.settings_app_group), stringResource(R.string.settings_playback_error_log), stringResource(R.string.settings_search_keywords_errors), OwnTVIcon.WARNING, TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showErrorLog = true },
            SettingsSearchEntry(videoPlayerGroup, stringResource(R.string.settings_detailed_playback_logging), stringResource(R.string.settings_search_keywords_detailed_logging), OwnTVIcon.INFO, TileTone.SECONDARY) { open(SettingsTab.VIDEO) },
            SettingsSearchEntry(stringResource(R.string.settings_group_network), stringResource(R.string.common_proxy), stringResource(R.string.settings_search_keywords_proxy), OwnTVIcon.NETWORK, TileTone.SECONDARY) { open(SettingsTab.NETWORK) },
            SettingsSearchEntry(stringResource(R.string.settings_group_network), stringResource(R.string.settings_dns), stringResource(R.string.settings_search_keywords_dns), OwnTVIcon.DNS, TileTone.SECONDARY) { open(SettingsTab.DNS) },
            SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_app_startup), stringResource(R.string.settings_search_keywords_startup), OwnTVIcon.POWER, TileTone.SECONDARY,
                chip = startupLabel(startupMode)) { saveScroll(); dialogReturn = searchFieldFocus; showStartup = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_check_updates), stringResource(R.string.settings_search_keywords_updates), OwnTVIcon.REFRESH, TileTone.PRIMARY,
                chip = "v${tv.own.owntv.BuildConfig.VERSION_NAME}") { saveScroll(); dialogReturn = searchFieldFocus; showUpdate = true },
            SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_update_startup), stringResource(R.string.settings_search_keywords_update_auto), OwnTVIcon.REFRESH, TileTone.SECONDARY,
                chip = if (updateCheckOnStart) stringResource(R.string.common_on) else stringResource(R.string.common_off), chipTone = if (updateCheckOnStart) TileTone.PRIMARY else TileTone.SECONDARY, showChevron = false) { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) },
            SettingsSearchEntry(stringResource(R.string.settings_group_app), stringResource(R.string.settings_about), stringResource(R.string.settings_search_keywords_about), OwnTVIcon.INFO, TileTone.SECONDARY) { saveScroll(); dialogReturn = searchFieldFocus; showAbout = true },
        )
        val tokens = searchQuery.trim().lowercase().split(" ").filter { it.isNotBlank() }
        entries.filter { e -> tokens.all { t -> e.haystack.contains(t) } }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel(fillColor = ContentPanelFill)
            // onEnter fires ONLY for directional entry into this group (sidebar D-pad, etc.), NOT for
            // programmatic restores — those are handled by the dialog-return LaunchedEffect above (and
            // dialogReturn is cleared there). So this only picks the entry fallback: while searching,
            // the always-bound search field; otherwise the category column, at the group last used.
            // Landing on the categories (not on a row) is the whole point of the two panes — one Right
            // press then reaches the rows.
            .focusProperties {
                onEnter = {
                    val target = returningRowFocus
                        ?: if (searchQuery.isBlank()) selectedCategoryFocus else searchFieldFocus
                    val landed = runCatching { target.requestFocus() }.getOrDefault(false)
                    if (!landed && returningRowFocus != null) cancelFocusChange()
                }
            }
            .focusGroup()
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
    ) {
        // The panel head, spanning both columns: the title and what the two columns do, then search —
        // a chip that opens into the real field on OK.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.settings_header_hint),
                    fontSize = 12.sp,
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (searchExpanded || searchQuery.isNotBlank()) {
                // Sized, not stretched: a field spanning the whole header reads as the subject of the
                // screen, which it is not. Capped, and it gives way at 150% UI Zoom.
                OwnTVTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    // Captioned by the header it sits in; a label line would make the header two rows.
                    label = "",
                    placeholder = stringResource(R.string.settings_search_hint),
                    focusRequester = searchFieldFocus,
                    corner = 13.dp,
                    // The mockup's `flex:0 1 440px`: a fixed 440 dp measured before the title column,
                    // so the field always ends flush with the right edge of the header instead of
                    // splitting the width with the title and opening from the middle.
                    modifier = Modifier.width(440.dp),
                )
            } else {
                SearchChip(onClick = { searchExpanded = true })
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // 294 dp is the design width, but at 150% UI Zoom the whole panel is not much wider than
            // that — so it gives way rather than squeezing the sheet into a strip. The value column
            // gives way with it, for the same reason.
            val spineWidth = minOf(SettingsSkin.SpineWidth, maxWidth * 0.34f)
            val valueColumn = minOf(SettingsSkin.ValueColumn, maxWidth * 0.22f)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // --- The spine: Quick, then the nine groups, each with its icon, its one-line summary
                // and how many rows it holds. It carries its OWN plate, the same one the sheet has —
                // the two columns are a pair, and a bare list beside a panelled one reads as
                // unfinished. Focus selects, so one Right press lands in the rows already showing.
                // While searching it steps back rather than disappearing, so the shape of the screen
                // does not change under the user mid-keystroke.
                val paneShape = SettingsSkin.PaneShape
                val searching = searchQuery.isNotBlank()
                LazyColumn(
                    state = spineState,
                    modifier = Modifier
                        .width(spineWidth)
                        .fillMaxHeight()
                        .alpha(if (searching) 0.4f else 1f)
                        .clip(paneShape)
                        .glass(surface = GlassSurface.CARDS, baseFill = colors.surfaceContainerLow, shape = paneShape)
                        .border(1.dp, colors.outlineVariant, paneShape)
                        .settingsScrollbar(spineState)
                        .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)
                        // Coming back from the sheet must land on the group whose rows you were just
                        // in — spatial focus search would otherwise pick whichever item happens to sit
                        // level with the row you left, silently changing the selected group.
                        .focusProperties {
                            canFocus = !searching
                            onEnter = { runCatching { selectedCategoryFocus.requestFocus() } }
                        }
                        .focusGroup(),
                ) {
                    item(key = "spine_head") { SpineHeader() }
                    itemsIndexed(categories, key = { _, (g, _) -> g.key }) { i, (group, rows) ->
                        // Quick is group zero and belongs to the user, not to the nine; a hairline
                        // keeps it recognisably apart from them.
                        if (i == 1) SpineSeparator()
                        SpineItem(
                            label = group.label,
                            summary = group.summary,
                            icon = group.icon,
                            count = rows.size,
                            selected = i == selectedGroup,
                            active = rows.any { it.chip != null && it.chipTone != TileTone.SECONDARY },
                            onFocused = { selectedGroup = i },
                            modifier = if (i == selectedGroup) {
                                Modifier.focusRequester(selectedCategoryFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                    item(key = "spine_foot") { SpineFooter() }
                }
                // --- The sheet: ONE container holding the selected group's rows — or, while
                // searching, every match wherever it lives, with the path it came from.
                val group = categories.getOrNull(selectedGroup)?.first
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(paneShape)
                        .glass(surface = GlassSurface.CARDS, baseFill = colors.surfaceContainerLow, shape = paneShape)
                        .border(1.dp, colors.outlineVariant, paneShape),
                ) {
                    SheetHeader(
                        title = if (searching) stringResource(R.string.settings_results_title) else group?.label.orEmpty(),
                        summary = if (searching) stringResource(R.string.settings_results_summary) else group?.summary.orEmpty(),
                        tag = when {
                            searching -> pluralStringResource(R.plurals.settings_match_count, searchResults.size, searchResults.size)
                            group?.key == "group_quick" -> pluralStringResource(R.plurals.settings_pinned_count, selectedRows.size, selectedRows.size)
                            else -> pluralStringResource(R.plurals.settings_setting_count, selectedRows.size, selectedRows.size)
                        },
                        tagHot = sheetFocused,
                    )
                    if (searching && searchResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_no_settings_match, searchQuery.trim()),
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    } else if (!searching && selectedRows.isEmpty()) {
                        // Only Quick can be empty, and only because the user emptied it.
                        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_quick_empty_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_quick_empty_hint),
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .settingsScrollbar(listState)
                                .onFocusChanged { sheetFocused = it.hasFocus }
                                // Left is the way back to the groups. Spatial search would normally find
                                // them, but a full-width row has no neighbour to its left once the ring
                                // is inside the sheet's own plate — so the sheet says it explicitly.
                                .onPreviewKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && !searching) {
                                        runCatching { selectedCategoryFocus.requestFocus() }.isSuccess
                                    } else {
                                        false
                                    }
                                }
                                .focusGroup(),
                            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (searching) {
                                items(searchResults, key = { it.group + it.title }) { e ->
                                    SettingsRow(
                                        dense = true,
                                        valueColumn = valueColumn,
                                        tone = e.tone, icon = e.icon,
                                        title = stringResource(R.string.settings_breadcrumb, e.group, e.title),
                                        chip = e.chip, chipTone = e.chipTone,
                                        showChevron = e.showChevron,
                                        onClick = e.onClick,
                                    )
                                }
                            } else {
                                items(selectedRows, key = { it.key }) { row ->
                                    RootItemContent(
                                        row,
                                        valueColumn,
                                        // Inside Quick every row is pinned by definition — the dot there
                                        // would say nothing. It only marks the copy in its home group.
                                        pinned = group?.key != "group_quick" && row.key in quickPinned,
                                        onLongClick = { menuRow = row },
                                        focus = if (row.key == menuReturnKey) menuReturnFocus else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    menuRow?.let { row ->
        val at = quickPinned.indexOf(row.key)
        // Order is a property of the Quick list, so it is only offered where that list is on screen.
        // In a row's home group the only thing the menu can usefully say is whether it is pinned.
        val inQuick = categories.getOrNull(selectedGroup)?.first?.key == "group_quick"
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
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showUpdate = false }) {
            UpdateDialog(onDismiss = { showUpdate = false }, checkOnOpen = true)
        }
    }
    if (showCatchupTime) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showCatchupTime = false }) { CatchupTimeDialog(
            mode = catchupTz,
            offsetMinutes = catchupOffset,
            offsetRange = settingsVm.catchupOffsetRangeMinutes,
            onSetMode = settingsVm::setCatchupTimezone,
            onAdjustOffset = settingsVm::adjustCatchupOffset,
            player = catchupPlayer,
            onSetPlayer = settingsVm::setCatchupPlayer,
            onDismiss = { showCatchupTime = false },
        ) }
    }
    if (showEpgOffset) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showEpgOffset = false }) { EpgOffsetSettingDialog(
            offsetMinutes = epgOffset,
            offsetRange = settingsVm.epgOffsetRangeMinutes,
            onAdjust = settingsVm::adjustEpgOffset,
            onReset = { settingsVm.setEpgOffsetMinutes(0) },
            onDismiss = { showEpgOffset = false },
        ) }
    }
    if (showAbout) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showAbout = false }) {
            AboutDialog(onDismiss = { showAbout = false })
        }
    }
    if (showClearHistory) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showClearHistory = false }) { ClearHistoryDialog(
            onClear = { type -> settingsVm.clearWatchHistory(type); showClearHistory = false },
            onDismiss = { showClearHistory = false },
        ) }
    }
    if (showTheme) {
        tv.own.owntv.features.settings.PickerDialog(
            title = stringResource(R.string.settings_theme_dialog),
            options = ThemeMode.entries.map { it.name to themeLabel(it) },
            selected = themeMode.name,
            onSelect = { settingsVm.setThemeMode(ThemeMode.valueOf(it)); showTheme = false },
            onDismiss = { showTheme = false },
        )
    }
    if (showStartup) {
        tv.own.owntv.features.settings.PickerDialog(
            title = stringResource(R.string.settings_app_startup_dialog),
            options = tv.own.owntv.core.settings.StartupMode.entries.map { it.name to startupLabel(it) },
            selected = startupMode.name,
            onSelect = {
                val mode = tv.own.owntv.core.settings.StartupMode.valueOf(it)
                showStartup = false
                if (mode == tv.own.owntv.core.settings.StartupMode.SPECIFIC_CHANNEL) {
                    settingsVm.setStartupChannelQuery("")
                    settingsVm.refreshStartupChannelPicker()
                    showStartupChannelPicker = true
                } else {
                    settingsVm.setStartupMode(mode)
                }
            },
            onDismiss = { showStartup = false },
        )
    }
    if (showStartupChannelPicker) {
        StartupChannelPickerDialog(
            query = startupChannelQuery,
            channels = startupChannelResults,
            selected = startupChannel,
            onQueryChange = settingsVm::setStartupChannelQuery,
            onSelect = {
                settingsVm.setStartupChannel(it)
                showStartupChannelPicker = false
            },
            onDismiss = { showStartupChannelPicker = false },
        )
    }
    if (showAnimations) {
        tv.own.owntv.features.settings.PickerDialog(
            title = stringResource(R.string.settings_animations_dialog),
            options = tv.own.owntv.core.theme.AnimationLevel.entries.map { it.name to stringResource(it.labelRes) },
            selected = animationLevel.name,
            onSelect = { settingsVm.setAnimationLevel(tv.own.owntv.core.theme.AnimationLevel.valueOf(it)); showAnimations = false },
            onDismiss = { showAnimations = false },
        )
    }
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
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showAccent = false }) { AccentPaletteDialog(
            accent = accent,
            customAccent = customAccent,
            onPickPreset = { settingsVm.setAccent(it) },
            onPickCustom = { settingsVm.setCustomAccent(it) },
            onDismiss = { showAccent = false },
        ) }
    }
    if (showZoom) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showZoom = false }) {
            ZoomDialog(current = uiZoomPercent, onSet = onSetZoom, onDismiss = { showZoom = false })
        }
    }
    if (showPopupSize) {
        PopupSizeDialog(
            current = fontCustomization.popupSizePercent,
            onSet = { onSetFontCustomization(fontCustomization.copy(popupSizePercent = it)) },
            onDismiss = { showPopupSize = false },
        )
    }
    if (showFontCustomization) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showFontCustomization = false }) { FontCustomizationDialog(
            current = fontCustomization,
            onApply = {
                onSetFontCustomization(it)
                showFontCustomization = false
            },
            onDismiss = { showFontCustomization = false },
        ) }
    }
    if (showBrowsing) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showBrowsing = false }) { BrowsingListsDialog(
            catLive = rememberCatLive, catMovies = rememberCatMovies, catSeries = rememberCatSeries,
            itemLive = rememberLastLive, itemMovies = rememberLastMovies, itemSeries = rememberLastSeries,
            onToggleCatLive = { settingsVm.setRememberCategoryLive(!rememberCatLive) },
            onToggleCatMovies = { settingsVm.setRememberCategoryMovies(!rememberCatMovies) },
            onToggleCatSeries = { settingsVm.setRememberCategorySeries(!rememberCatSeries) },
            onToggleItemLive = { settingsVm.setRememberLastLive(!rememberLastLive) },
            onToggleItemMovies = { settingsVm.setRememberLastMovies(!rememberLastMovies) },
            onToggleItemSeries = { settingsVm.setRememberLastSeries(!rememberLastSeries) },
            onDismiss = { showBrowsing = false },
        ) }
    }
    if (showAmbientGlow) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showAmbientGlow = false }) { AmbientGlowDialog(
            glowEnabled = ambientGlowEnabled,
            pulseEnabled = ambientGlowPulse,
            onToggleGlow = { settingsVm.setAmbientGlowEnabled(!ambientGlowEnabled) },
            onTogglePulse = { settingsVm.setAmbientGlowPulse(!ambientGlowPulse) },
            onDismiss = { showAmbientGlow = false },
        ) }
    }
    if (showErrorLog) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showErrorLog = false }) {
            PlaybackErrorLogDialog(onDismiss = { showErrorLog = false })
        }
    }
    if (showAfrWarning) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showAfrWarning = false }) { AutoFrameRateWarningDialog(
            onEnable = { settingsVm.setAutoFrameRate(true); showAfrWarning = false },
            onDismiss = { showAfrWarning = false },
        ) }
    }
    if (showLivePreviewPanelWarning) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showLivePreviewPanelWarning = false }) {
            LivePreviewPanelHiddenDialog(onDismiss = { showLivePreviewPanelWarning = false })
        }
    }
    if (showFolderPicker) {
        StorageBrowser(
            title = stringResource(R.string.settings_download_folder_title),
            mode = BrowseMode.FOLDER,
            onPick = { settingsVm.setDownloadRoot(it.absolutePath); showFolderPicker = false },
            onDismiss = { showFolderPicker = false },
        )
    }
    if (showBgImageChooser) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showBgImageChooser = false }) { BackgroundImageChooserDialog(
            hasImage = bgImagePath.isNotBlank(),
            onPickLocal = { showBgImageChooser = false; showBgPicker = true },
            onPickRemote = { showBgImageChooser = false; showBgRemote = true },
            onClear = { settingsVm.setBgImagePath(""); showBgImageChooser = false },
            onDismiss = { showBgImageChooser = false },
        ) }
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
                    if (path != null) settingsVm.setBgImagePath(path)
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
                    if (path != null) settingsVm.setBgImagePath(path)
                }
                showBgPicker = false
            },
            // Back goes back one level, to the local/remote/clear chooser this was opened from —
            // closing both left the user on the Glass Effect row two steps up.
            onDismiss = { showBgPicker = false; showBgImageChooser = true },
        )
    }
}

@Composable
private fun StartupChannelPickerDialog(
    query: String,
    channels: List<tv.own.owntv.core.database.entity.ChannelEntity>,
    selected: tv.own.owntv.core.settings.StartupChannelRef?,
    onQueryChange: (String) -> Unit,
    onSelect: (tv.own.owntv.core.database.entity.ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val searchFocus = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        runCatching { searchFocus.requestFocus() }
    }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        tv.own.owntv.ui.theme.PopupFontTheme {
            Box(
                Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
                contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.dialogPanel(width = 600.dp, padding = 24.dp)) {
                    Text(
                        stringResource(R.string.settings_startup_specific_channel),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    tv.own.owntv.ui.components.SearchBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        placeholder = stringResource(R.string.common_search_hint),
                        surface = GlassSurface.DIALOGS,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (channels.isEmpty()) {
                        Text(
                            if (query.isBlank()) stringResource(R.string.content_no_channels_here)
                            else stringResource(R.string.content_no_channels_found, query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 330.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            items(channels, key = { it.id }) { channel ->
                                val isSelected = selected?.let { ref ->
                                    ref.sourceId == channel.sourceId &&
                                        if (!ref.remoteId.isNullOrBlank() && !channel.remoteId.isNullOrBlank()) {
                                            ref.remoteId == channel.remoteId
                                        } else {
                                            ref.name == channel.name
                                        }
                                } == true
                                FocusableSurface(
                                    onClick = { onSelect(channel) },
                                    modifier = Modifier.fillMaxWidth(),
                                    selected = isSelected,
                                    shape = RoundedCornerShape(12.dp),
                                    selectedContainerColor = colors.primaryContainer,
                                    contentAlignment = Alignment.CenterStart,
                                    surface = GlassSurface.DIALOGS,
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        channel.number?.let {
                                            Text(
                                                it.toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isSelected) colors.onPrimaryContainer else colors.primary,
                                                modifier = Modifier.width(54.dp),
                                            )
                                        }
                                        Text(
                                            channel.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OwnTVButton(
                            stringResource(R.string.content_close),
                            onDismiss,
                            style = OwnTVButtonStyle.SECONDARY,
                        )
                    }
                }
            }
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

@Composable
private fun startupLabel(mode: tv.own.owntv.core.settings.StartupMode): String = stringResource(
    when (mode) {
        tv.own.owntv.core.settings.StartupMode.HOME -> R.string.settings_startup_home
        tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL -> R.string.settings_startup_last_channel
        tv.own.owntv.core.settings.StartupMode.FAVORITES -> R.string.settings_startup_favorites
        tv.own.owntv.core.settings.StartupMode.SPECIFIC_CHANNEL -> R.string.settings_startup_specific_channel
    },
)

@Composable
private fun navModeLabel(mode: tv.own.owntv.core.settings.SettingsRepository.NavMenuMode): String = stringResource(
    if (mode == tv.own.owntv.core.settings.SettingsRepository.NavMenuMode.DYNAMIC) R.string.settings_dynamic else R.string.settings_static,
)

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
 * Accent picker: a handful of quick presets plus a full HSV color picker — a hue bar and a
 * saturation/brightness square (each an enter-to-edit D-pad control) with a live preview — and a
 * hex-code field for an exact color. The dialog scrolls so the on-screen keyboard never hides the
 * hex field. Presets clear the custom color; the picker/hex set it exactly (custom overrides the
 * preset in the theme).
 */
@Composable
private fun AccentPaletteDialog(
    accent: tv.own.owntv.core.theme.AccentColor,
    customAccent: String,
    onPickPreset: (tv.own.owntv.core.theme.AccentColor) -> Unit,
    onPickCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val isDark = colors.isDark
    val firstFocus = remember { FocusRequester() }

    // Live HSV state seeded from the current custom color (or a pleasant default).
    val hsv = remember {
        FloatArray(3).also { out ->
            val seed = tv.own.owntv.ui.theme.parseAccentHex(customAccent)?.toArgb() ?: 0xFF52DBC8.toInt()
            android.graphics.Color.colorToHSV(seed, out)
        }
    }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    val pickedHex = tv.own.owntv.ui.components.hsvToHex(hue, sat, value)
    var hexInput by remember { mutableStateOf(customAccent.removePrefix("#")) }
    var hexError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    // Keep the sliders and hex field in step whenever the HSV picker moves.
    fun syncHexFromPicker() { hexInput = pickedHex.removePrefix("#") }

    // PopupFontTheme swaps in the selected popup family and applies the shared popup type scale.
    tv.own.owntv.ui.theme.PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().imePadding().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // dialogPanel already applies verticalScroll; imePadding on the parent Box lifts the
            // whole panel above the on-screen keyboard so the hex field stays visible.
            modifier = Modifier.dialogPanel(width = 640.dp, padding = 28.dp),
        ) {
            Text(stringResource(R.string.settings_accent_dialog), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.settings_presets), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentPresetChoices.forEachIndexed { i, ac ->
                    val isSel = customAccent.isBlank() && ac == accent
                    tv.own.owntv.ui.components.ColorSwatch(
                        color = ac.primary(isDark),
                        selected = isSel,
                        onClick = { onPickPreset(ac); onDismiss() },
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.settings_hex_code), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            // Kept above the picker on purpose: the on-screen keyboard covers the lower half of the
            // screen, so the hex field must sit high enough to stay visible while the user types.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("#", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                tv.own.owntv.ui.components.OwnTVTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it.take(6); hexError = false },
                    label = stringResource(R.string.settings_hex),
                    placeholder = "52DBC8",
                    modifier = Modifier.width(200.dp),
                )
                OwnTVButton(stringResource(R.string.settings_apply), onClick = {
                    val parsed = tv.own.owntv.ui.theme.parseAccentHex(hexInput)
                    if (parsed != null) {
                        onPickCustom("#" + hexInput.trim().removePrefix("#").uppercase())
                        onDismiss()
                    } else {
                        hexError = true
                    }
                })
            }
            if (hexError) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_hex_error), style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_color_picker), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    // Hue bar: OK to enter, ◀ ▶ to shift the hue, OK/Back to exit.
                    tv.own.owntv.ui.components.HueBar(hue = hue) { h -> hue = h; syncHexFromPicker(); hexError = false }
                }
                // Live preview of the currently picked color.
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))))
                        .border(2.dp, colors.outline, androidx.compose.foundation.shape.CircleShape),
                )
            }
            Spacer(Modifier.height(14.dp))
            // Saturation / Brightness square: OK to enter, D-pad to move the dot, OK/Back to exit.
            tv.own.owntv.ui.components.SatValSquare(hue = hue, sat = sat, value = value) { s, v ->
                sat = s; value = v; syncHexFromPicker(); hexError = false
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_use_color), onClick = { onPickCustom(pickedHex); onDismiss() })
            }
        }
    }
    }
}


/**
 * Focus highlight presets (#121): the six accent presets plus gold and white, which are the two
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
 * Focus highlight picker (#121): presets, hex field and the shared HSV palette pick the ring color;
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
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }

    val hsv = remember {
        FloatArray(3).also { out ->
            val seed = tv.own.owntv.ui.theme.parseAccentHex(highlight)?.toArgb() ?: 0xFFF5B400.toInt()
            android.graphics.Color.colorToHSV(seed, out)
        }
    }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    val pickedHex = tv.own.owntv.ui.components.hsvToHex(hue, sat, value)
    var hexInput by remember { mutableStateOf(highlight.removePrefix("#")) }
    var hexError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    // The sample follows whatever is currently picked, falling back to the saved/accent color.
    val sampleColor = tv.own.owntv.ui.theme.parseAccentHex(pickedHex) ?: colors.focusBorder

    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, fontScale = .50f) {
        tv.own.owntv.ui.theme.PopupFontTheme {
            Box(
                Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
                contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.dialogPanel(width = 640.dp, padding = 28.dp)) {
                    Text(stringResource(R.string.settings_focus_highlight), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_focus_highlight_description), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))

                    Text(stringResource(R.string.settings_presets), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusHighlightPresets.forEachIndexed { i, hex ->
                            tv.own.owntv.ui.components.ColorSwatch(
                                color = tv.own.owntv.ui.theme.parseAccentHex(hex) ?: colors.primary,
                                selected = highlight.equals(hex, ignoreCase = true),
                                onClick = { onPickColor(hex) },
                                sizeDp = 36,
                                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.settings_hex_code), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    // Above the palette on purpose: the on-screen keyboard covers the lower half of
                    // the screen, so the hex field has to stay high enough to remain visible.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("#", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                        tv.own.owntv.ui.components.OwnTVTextField(
                            value = hexInput,
                            onValueChange = { hexInput = it.take(6); hexError = false },
                            label = stringResource(R.string.settings_hex),
                            placeholder = "F5B400",
                            modifier = Modifier.width(200.dp),
                        )
                        OwnTVButton(stringResource(R.string.settings_apply), onClick = {
                            if (tv.own.owntv.ui.theme.parseAccentHex(hexInput) != null) {
                                onPickColor("#" + hexInput.trim().removePrefix("#").uppercase())
                            } else {
                                hexError = true
                            }
                        })
                    }
                    if (hexError) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.settings_hex_error), style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.settings_color_picker), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    // Hue bar: OK to enter, ◀ ▶ to shift the hue, OK/Back to exit.
                    tv.own.owntv.ui.components.HueBar(hue = hue) { h ->
                        hue = h; hexInput = pickedHex.removePrefix("#"); hexError = false
                    }
                    Spacer(Modifier.height(14.dp))
                    // Saturation / Brightness square: OK to enter, D-pad to move the dot, OK/Back to exit.
                    tv.own.owntv.ui.components.SatValSquare(hue = hue, sat = sat, value = value) { s, v ->
                        sat = s; value = v; hexInput = pickedHex.removePrefix("#"); hexError = false
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.settings_focus_thickness), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        tv.own.owntv.ui.theme.FocusBorderWidthChoices.forEach { w ->
                            OwnTVButton(
                                focusWidthLabel(w),
                                onClick = { onPickWidth(w) },
                                style = if (w == widthDp) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(tv.own.owntv.ui.theme.Dimens.CardCorner))
                            .background(colors.surfaceContainerHigh)
                            .border(
                                widthDp.dp,
                                sampleColor,
                                androidx.compose.foundation.shape.RoundedCornerShape(tv.own.owntv.ui.theme.Dimens.CardCorner),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.settings_focus_highlight_sample), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OwnTVButton(stringResource(R.string.settings_reset), onClick = { onPickColor("") }, style = OwnTVButtonStyle.SECONDARY)
                        Spacer(Modifier.weight(1f))
                        OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                        OwnTVButton(stringResource(R.string.settings_use_color), onClick = { onPickColor(pickedHex); onDismiss() })
                    }
                }
            }
        }
    }
}


private const val GITHUB_REPO = "github.com/ahXN00/OwnTV"
private const val TELEGRAM_LINK = "t.me/owntvplayer"

/** About OwnTV: version, license, author and project link — all readable on screen (no TV browser). */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 520.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLockup(markSize = 48, textSize = 30)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.settings_about_version, tv.own.owntv.BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium, color = colors.primary)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.settings_about_description_full),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.settings_about_license), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(GITHUB_REPO, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            Spacer(Modifier.height(16.dp))
            // Community: Telegram link + a QR, side-by-side to keep the dialog compact, so TV users can
            // join from their phone — no TV browser needed.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_join_telegram), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(TELEGRAM_LINK, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_telegram_scan),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    )
                }
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp)) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(tv.own.owntv.R.drawable.telegram_qr),
                        contentDescription = stringResource(R.string.settings_telegram_qr),
                        modifier = Modifier.size(120.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.settings_contributions),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, modifier = Modifier.focusRequester(focus))
        }
    }
}

/**
 * Read-only viewer for the persisted playback error history (B5): the last ~10 failures with their
 * plain-English reason, media spec, raw engine text, engine, stream type and device info — so users
 * who can't pull logcat can read/report what happened after dismissing the error screen.
 */
@Composable
private fun String.playbackDisplayName(): String = when (trim().lowercase(java.util.Locale.ROOT)) {
    "mpv" -> stringResource(R.string.settings_player_mpv)
    "exoplayer", "exo" -> stringResource(R.string.settings_player_exoplayer)
    else -> this
}

@Composable
private fun PlaybackErrorLogDialog(onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var exportPath by remember { mutableStateOf<String?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val entries by androidx.compose.runtime.produceState<List<tv.own.owntv.player.PlaybackErrorLog.Entry>?>(initialValue = null, refresh) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            tv.own.owntv.player.PlaybackErrorLog.read(context)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(entries) { if (entries != null) runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    val dateContext = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        // scroll = false: the entries live in a LazyColumn, which manages its own scrolling. A plain
        // verticalScroll column can't work here — with 25 entries and nothing focusable inside them the
        // panel grew past the screen and the D-pad had no way to move the scroll, so the oldest entries
        // were simply unreachable.
        Column(modifier = Modifier.dialogPanel(width = 640.dp, padding = 28.dp, scroll = false)) {
            Text(stringResource(R.string.settings_playback_error_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_playback_error_description_full),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            val list = entries
            when {
                list == null -> Text(stringResource(R.string.settings_loading), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                list.isEmpty() -> Text(stringResource(R.string.settings_no_playback_errors), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                // Each entry is focusable even though there is nothing to activate: on a TV that is the
                // only thing that makes a list scroll. Up from the buttons walks back through the history.
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list) { e ->
                        FocusableSurface(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            contentAlignment = Alignment.CenterStart,
                            surface = GlassSurface.DIALOGS,
                        ) { _ ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                // The kind matters at a glance now: a log full of "Event" lines next to one
                                // "Error" tells a very different story from ten failures in a row.
                                val kindLabel = when (e.kind) {
                                    tv.own.owntv.player.PlaybackErrorLog.Kind.ERROR -> stringResource(R.string.settings_playback_kind_error)
                                    tv.own.owntv.player.PlaybackErrorLog.Kind.EVENT -> stringResource(R.string.settings_playback_kind_event)
                                    tv.own.owntv.player.PlaybackErrorLog.Kind.REPORT -> stringResource(R.string.settings_playback_kind_report)
                                }
                                Text(
                                    stringResource(
                                        R.string.settings_playback_entry_with_kind,
                                        formatBestDateTime(dateContext, "dMMM", e.atMs),
                                        kindLabel,
                                        e.engine.playbackDisplayName(),
                                        stringResource(if (e.live) R.string.settings_live else R.string.settings_vod),
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (e.kind == tv.own.owntv.player.PlaybackErrorLog.Kind.ERROR) colors.primary else colors.onSurfaceVariant,
                                )
                                val reasonText = e.reason?.displayText() ?: e.legacyReason
                                reasonText?.let {
                                    Spacer(Modifier.height(2.dp))
                                    Text(it, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                                }
                                e.mediaSpec()?.let { spec ->
                                    Spacer(Modifier.height(2.dp))
                                    Text(spec.displayText(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                } ?: e.spec?.let { legacySpec ->
                                    Spacer(Modifier.height(2.dp))
                                    Text(legacySpec, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                                e.raw?.let {
                                    Spacer(Modifier.height(2.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(stringResource(R.string.settings_device_details, e.model, e.android), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            exportPath?.let {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_backup_saved_to, it), style = MaterialTheme.typography.bodySmall, color = colors.primary)
            }
            if (exportFailed) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_backup_export_error), style = MaterialTheme.typography.bodySmall, color = colors.favorite)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Export also includes the live diagnostics ring, so keep it available when the visible
                // error list is empty; an engine handoff can leave useful diagnostics without an entry.
                OwnTVButton(stringResource(R.string.settings_export), onClick = {
                    scope.launch {
                        val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            tv.own.owntv.player.PlaybackErrorLog.export(context)
                        }
                        exportPath = path
                        exportFailed = path == null
                    }
                }, style = OwnTVButtonStyle.SECONDARY)
                if (!entries.isNullOrEmpty()) {
                    OwnTVButton(stringResource(R.string.settings_clear_log), onClick = {
                        tv.own.owntv.player.PlaybackErrorLog.clear(context)
                        exportPath = null
                        exportFailed = false
                        refresh++
                    }, style = OwnTVButtonStyle.SECONDARY)
                }
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_close), onClick = onDismiss, modifier = Modifier.focusRequester(focus))
            }
        }
    }
}

/**
 * Warn before enabling Auto frame rate below Android 12, where smooth refresh-rate alternatives cannot
 * be queried and a mode switch can trigger a visible HDMI re-handshake.
 */
@Composable
internal fun AutoFrameRateWarningDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 500.dp, padding = 28.dp)) {
            Text(
                stringResource(R.string.settings_auto_frame_rate_warning_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    R.string.settings_auto_frame_rate_warning_description,
                    android.os.Build.VERSION.RELEASE,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(
                    stringResource(R.string.settings_auto_frame_rate_keep_off),
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(focus),
                )
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    stringResource(R.string.settings_auto_frame_rate_turn_on_anyway),
                    onClick = onEnable,
                    style = OwnTVButtonStyle.SECONDARY,
                )
            }
        }
    }
}

@Composable
internal fun LivePreviewPanelHiddenDialog(onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 500.dp, padding = 28.dp)) {
            Text(
                stringResource(R.string.settings_live_preview_panel_hidden_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.settings_live_preview_panel_hidden_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    stringResource(R.string.common_ok),
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        }
    }
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
private fun ClearHistoryDialog(
    onClear: (tv.own.owntv.core.model.MediaType?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var pending by remember { mutableStateOf<HistoryScope?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(pending) { runCatching { firstFocus.requestFocus() } }
    BackHandler { if (pending != null) pending = null else onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 460.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val p = pending
            if (p == null) {
                Text(stringResource(R.string.settings_clear_history), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_choose_history),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus))
                Spacer(Modifier.height(10.dp))
                OwnTVButton(stringResource(R.string.settings_history_live), onClick = { pending = HistoryScope.LIVE }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton(stringResource(R.string.settings_history_movies), onClick = { pending = HistoryScope.MOVIES }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton(stringResource(R.string.settings_history_series), onClick = { pending = HistoryScope.SERIES }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton(stringResource(R.string.settings_all_history), onClick = { pending = HistoryScope.ALL }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.settings_clear_history_confirm, stringResource(p.labelRes)), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_cannot_undo), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OwnTVButton(stringResource(R.string.settings_no), onClick = { pending = null }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(firstFocus))
                    OwnTVButton(stringResource(R.string.settings_yes_clear), onClick = { onClear(p.type) })
                }
            }
        }
    }
}

private enum class FontPickerTarget { MAIN, POPUP }

@Composable
private fun fontFamilyLabel(family: AppFontFamily): String = stringResource(
    when (family) {
        AppFontFamily.LORA -> R.string.settings_font_lora
        AppFontFamily.SYSTEM_SANS -> R.string.settings_font_system_sans
        AppFontFamily.MONOSPACE -> R.string.settings_font_monospace
        AppFontFamily.PLAYFAIR_DISPLAY -> R.string.settings_font_playfair_display
        AppFontFamily.DANCING_SCRIPT -> R.string.settings_font_dancing_script
        AppFontFamily.POPPINS -> R.string.settings_font_poppins
    },
)

/** Staged font editor: Back cancels; Apply commits size + both families atomically. */
@Composable
private fun FontCustomizationDialog(
    current: FontCustomization,
    onApply: (FontCustomization) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var draft by remember(current) { mutableStateOf(current) }
    var picker by remember { mutableStateOf<FontPickerTarget?>(null) }
    var pickerReturn by remember { mutableStateOf<FontPickerTarget?>(null) }
    val firstFocus = remember { FocusRequester() }
    val mainFocus = remember { FocusRequester() }
    val popupFocus = remember { FocusRequester() }

    LaunchedEffect(picker) {
        if (picker == null) {
            val target = when (pickerReturn) {
                FontPickerTarget.MAIN -> mainFocus
                FontPickerTarget.POPUP -> popupFocus
                null -> firstFocus
            }
            kotlinx.coroutines.delay(50)
            runCatching { target.requestFocus() }
        }
    }
    BackHandler {
        if (picker != null) picker = null else onDismiss()
    }

    if (picker == null) {
        Box(
            modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .dialogPanel(width = 600.dp, panelHeight = 620.dp, padding = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.settings_font_customization),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_font_size_range, UiFontScale.MIN, UiFontScale.MAX),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.settings_font_size),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(
                        stringResource(R.string.settings_decrease),
                        dimmed = draft.sizePercent <= UiFontScale.MIN,
                        modifier = Modifier.focusRequester(firstFocus),
                    ) {
                        draft = draft.copy(sizePercent = UiFontScale.clamp(draft.sizePercent - UiFontScale.STEP))
                    }
                    Text(
                        stringResource(R.string.common_percent, draft.sizePercent),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = TextAlign.Center,
                    )
                    StepButton(
                        stringResource(R.string.settings_increase),
                        dimmed = draft.sizePercent >= UiFontScale.MAX,
                    ) {
                        draft = draft.copy(sizePercent = UiFontScale.clamp(draft.sizePercent + UiFontScale.STEP))
                    }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.settings_popup_font_size),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_popup_font_size_description),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StepButton(
                stringResource(R.string.settings_decrease),
                dimmed = draft.popupFontSizePercent <= PopupFontScale.MIN,
            ) {
                draft = draft.copy(
                    popupFontSizePercent = PopupFontScale.clamp(
                        draft.popupFontSizePercent - PopupFontScale.STEP,
                    ),
                )
            }
            Text(
                stringResource(R.string.common_percent, draft.popupFontSizePercent),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.primary,
                modifier = Modifier.width(120.dp),
                textAlign = TextAlign.Center,
            )
            StepButton(
                stringResource(R.string.settings_increase),
                dimmed = draft.popupFontSizePercent >= PopupFontScale.MAX,
            ) {
                draft = draft.copy(
                    popupFontSizePercent = PopupFontScale.clamp(
                        draft.popupFontSizePercent + PopupFontScale.STEP,
                    ),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        FontChoiceRow(
                    title = stringResource(R.string.settings_main_interface_font),
                    family = draft.mainFamily,
                    modifier = Modifier.focusRequester(mainFocus),
                ) {
                    pickerReturn = FontPickerTarget.MAIN
                    picker = FontPickerTarget.MAIN
                }
                Spacer(Modifier.height(10.dp))
                FontChoiceRow(
                    title = stringResource(R.string.settings_popup_font),
                    family = draft.popupFamily,
                    modifier = Modifier.focusRequester(popupFocus),
                ) {
                    pickerReturn = FontPickerTarget.POPUP
                    picker = FontPickerTarget.POPUP
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton(
                stringResource(R.string.settings_reset),
                onClick = {
                    draft = FontCustomization(popupSizePercent = draft.popupSizePercent)
                },
                style = OwnTVButtonStyle.SECONDARY,
            )
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.settings_apply), onClick = { onApply(draft) })
                }
            }
        }
    } else {
        val target = picker ?: return
        FontFamilyPickerDialog(
            title = stringResource(
                if (target == FontPickerTarget.MAIN) R.string.settings_main_interface_font
                else R.string.settings_popup_font,
            ),
            selected = if (target == FontPickerTarget.MAIN) draft.mainFamily else draft.popupFamily,
            onSelect = { family ->
                draft = if (target == FontPickerTarget.MAIN) draft.copy(mainFamily = family)
                else draft.copy(popupFamily = family)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun FontChoiceRow(
    title: String,
    family: AppFontFamily,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 70.dp),
        shape = RoundedCornerShape(16.dp),
        surface = GlassSurface.DIALOGS,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(
                fontFamilyLabel(family),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = family.asComposeFamily()),
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Text("›", style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun FontFamilyPickerDialog(
    title: String,
    selected: AppFontFamily,
    onSelect: (AppFontFamily) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { AppFontFamily.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { focus.getValue(selected).requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
                modifier = Modifier
                    .dialogPanel(width = 640.dp, panelHeight = 640.dp, padding = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.settings_choose_font), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            AppFontFamily.entries.forEach { family ->
                FocusableSurface(
                    onClick = { onSelect(family) },
                    selected = family == selected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp)
                        .focusRequester(focus.getValue(family)),
                    shape = RoundedCornerShape(14.dp),
                    surface = GlassSurface.DIALOGS,
                    contentAlignment = Alignment.CenterStart,
                ) { _ ->
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text(
                            fontFamilyLabel(family),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = family.asComposeFamily()),
                            color = if (family == selected) colors.primary else colors.onSurface,
                        )
                        Text(
                            stringResource(R.string.settings_font_preview),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = family.asComposeFamily()),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** A stepper for shared popup geometry. Changes apply live to this dialog too. */
@Composable
private fun PopupSizeDialog(current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.dialogPanel(width = 460.dp, panelHeight = 270.dp, padding = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.settings_popup_size),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_popup_size_range, PopupSizeScale.MIN, PopupSizeScale.MAX),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(
                        stringResource(R.string.settings_decrease),
                        dimmed = current <= PopupSizeScale.MIN,
                        modifier = Modifier.focusRequester(firstFocus),
                    ) { onSet(PopupSizeScale.clamp(current - PopupSizeScale.STEP)) }
                    Text(
                        stringResource(R.string.common_percent, current),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = TextAlign.Center,
                    )
                    StepButton(
                        stringResource(R.string.settings_increase),
                        dimmed = current >= PopupSizeScale.MAX,
                    ) { onSet(PopupSizeScale.clamp(current + PopupSizeScale.STEP)) }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton(
                        stringResource(R.string.settings_reset),
                        onClick = { onSet(PopupSizeScale.DEFAULT) },
                        style = OwnTVButtonStyle.SECONDARY,
                    )
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss)
                }
            }
        }
    }
}

/** A stepper for the global UI scale. Changes apply live (the whole UI re-scales as you adjust). */
@Composable
private fun ZoomDialog(current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    // Zoom below LOW_RAM_WARN doubles the on-screen item count, which can OOM-crash 2 GB devices
    // (#51) — the first step under it is gated behind an accept-the-risk warning. Accepting once
    // arms the rest of this dialog session; if it was opened already below the line, don't nag.
    var lowZoomAccepted by remember { mutableStateOf(current < UiZoom.LOW_RAM_WARN) }
    var pendingLowZoom by remember { mutableStateOf<Int?>(null) }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 460.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_ui_zoom), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_ui_zoom_range, UiZoom.MIN, UiZoom.MAX),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // Initial focus lands on the DECREASE button: the dialog is most often opened to escape an
                // over-zoomed screen (where everything's too big to navigate), so "–" must be first under
                // the cursor. The buttons stay focusable at the limits (clamped + dimmed, never disabled)
                // so focus always lands inside the dialog — a disabled "+" at MAX zoom was leaving focus
                // stranded outside, trapping the user at high zoom.
                StepButton(stringResource(R.string.settings_decrease), dimmed = current <= UiZoom.MIN, modifier = Modifier.focusRequester(firstFocus)) {
                    val next = UiZoom.clamp(current - UiZoom.STEP)
                    if (next < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) pendingLowZoom = next else onSet(next)
                }
                Text(
                    stringResource(R.string.common_percent, current),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.primary,
                    modifier = Modifier.width(120.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton(stringResource(R.string.settings_increase), dimmed = current >= UiZoom.MAX) {
                    onSet(UiZoom.clamp(current + UiZoom.STEP))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.settings_reset), onClick = { onSet(UiZoom.DEFAULT) }, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss)
            }
        }

        // Accept-the-risk gate for zoom below LOW_RAM_WARN (#51). One button, focus locked (all
        // D-pad directions cancelled) — OK accepts and applies the pending step, Back cancels.
        pendingLowZoom?.let { target ->
            val acceptFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { acceptFocus.requestFocus() } }
            // Composed after the dialog's own BackHandler, so it wins while the warning is up.
            BackHandler {
                pendingLowZoom = null
                runCatching { firstFocus.requestFocus() }
            }
            Box(
                modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.dialogPanel(width = 460.dp, padding = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.settings_low_zoom_warning_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.settings_low_zoom_warning, UiZoom.LOW_RAM_WARN, UiZoom.LOW_RAM_WARN),
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    OwnTVButton(
                        stringResource(R.string.settings_low_zoom_accept),
                        onClick = {
                            lowZoomAccepted = true
                            pendingLowZoom = null
                            onSet(target)
                            runCatching { firstFocus.requestFocus() }
                        },
                        modifier = Modifier
                            .focusRequester(acceptFocus)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                start = FocusRequester.Cancel
                                end = FocusRequester.Cancel
                            },
                    )
                }
            }
        }
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
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        tv.own.owntv.ui.theme.PopupFontTheme {
            Column(
                    modifier = Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.settings_ambient_glow), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_ambient_glow_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                OwnTVButton(
                    stringResource(
                        R.string.settings_section_toggle,
                        stringResource(R.string.settings_ambient_glow_effect),
                        stringResource(if (glowEnabled) R.string.common_on else R.string.common_off),
                    ),
                    onClick = onToggleGlow,
                    style = if (glowEnabled) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                    icon = OwnTVIcon.PALETTE,
                    modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
                )
                if (glowEnabled) {
                    Spacer(Modifier.height(10.dp))
                    OwnTVButton(
                        stringResource(
                            R.string.settings_section_toggle,
                            stringResource(R.string.settings_ambient_glow_pulse),
                            stringResource(if (pulseEnabled) R.string.common_on else R.string.common_off),
                        ),
                        onClick = onTogglePulse,
                        style = if (pulseEnabled) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                        icon = OwnTVIcon.THEME,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(18.dp))
                OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * A stepper for the Glass effect fill strength — how opaque the translucent panels are over the
 * background photo. Higher = more solid (less see-through). Changes apply live. Range 20–95% in 5%
 * steps so panels can never go fully transparent (text would be unreadable) or fully solid (pointless).
 */
/** Dedicated Glass Effect settings destination. Background selection stays nested here, so Back returns here. */
@Composable
private fun GlassEffectSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settingsVm: SettingsViewModel = koinViewModel()
    val glassConfig by settingsVm.glassConfig.collectAsStateWithLifecycle()
    val bgImagePath by settingsVm.bgImagePath.collectAsStateWithLifecycle()
    var showBackgroundChooser by remember { mutableStateOf(false) }
    var showLocalPicker by remember { mutableStateOf(false) }
    var showRemotePicker by remember { mutableStateOf(false) }
    val ingestScope = rememberCoroutineScope()

    GlassEffectDesignedScreen(
        glassOn = glassConfig.enabled,
        preset = glassConfig.preset,
        alphaPercent = (glassConfig.alpha * 100).roundToInt(),
        blurPercent = (glassConfig.blurStrength * 100).roundToInt(),
        highlightPercent = (glassConfig.highlightStrength * 100).roundToInt(),
        allowFullTransparency = glassConfig.allowFullTransparency,
        depthEffects = glassConfig.depthEffects,
        bgOn = bgImagePath.isNotBlank(),
        scope = glassConfig.scope,
        onToggleGlass = {
            settingsVm.setGlassScopeBitmask(
                if (glassConfig.enabled) 0 else GlassConfig(ALL_GLASS_SURFACES).toBitmask(),
            )
        },
        onSetPreset = settingsVm::setGlassPreset,
        onSetAlpha = {
            settingsVm.setGlassAlphaPercent(it, (glassConfig.blurStrength * 100).roundToInt())
        },
        onSetBlur = {
            settingsVm.setGlassBlurPercent(it, (glassConfig.alpha * 100).roundToInt())
        },
        onSetHighlight = settingsVm::setGlassHighlightPercent,
        onSetAllowFullTransparency = settingsVm::setGlassAllowFullTransparency,
        onSetDepthEffects = settingsVm::setGlassDepthEffects,
        onSetScope = settingsVm::setGlassScopeBitmask,
        onOpenBackground = { showBackgroundChooser = true },
        onBack = onBack,
        modifier = modifier,
    )

    if (showBackgroundChooser) {
        tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { showBackgroundChooser = false }) {
            BackgroundImageChooserDialog(
                hasImage = bgImagePath.isNotBlank(),
                onPickLocal = { showBackgroundChooser = false; showLocalPicker = true },
                onPickRemote = { showBackgroundChooser = false; showRemotePicker = true },
                onClear = { settingsVm.setBgImagePath(""); showBackgroundChooser = false },
                onDismiss = { showBackgroundChooser = false },
            )
        }
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
                    if (path != null) settingsVm.setBgImagePath(path)
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
                    if (path != null) settingsVm.setBgImagePath(path)
                }
                showLocalPicker = false
            },
            onDismiss = { showLocalPicker = false; showBackgroundChooser = true },
        )
    }
}

@Composable
private fun GlassEffectDesignedScreen(
    glassOn: Boolean,
    preset: GlassPreset,
    alphaPercent: Int,
    blurPercent: Int,
    highlightPercent: Int,
    allowFullTransparency: Boolean,
    depthEffects: Boolean,
    bgOn: Boolean,
    scope: Set<GlassSurface>,
    onToggleGlass: () -> Unit,
    onSetPreset: (GlassPreset) -> Unit,
    onSetAlpha: (Int) -> Unit,
    onSetBlur: (Int) -> Unit,
    onSetHighlight: (Int) -> Unit,
    onSetAllowFullTransparency: (Boolean) -> Unit,
    onSetDepthEffects: (Boolean) -> Unit,
    onSetScope: (Int) -> Unit,
    onOpenBackground: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        tv.own.owntv.features.settings.Header(
            title = stringResource(R.string.settings_glass_effect_title),
            onBack = onBack,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_glass_screen_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 56.dp),
        )
        Spacer(Modifier.height(14.dp))
        GlassEffectPreview()
        Spacer(Modifier.height(14.dp))
        tv.own.owntv.features.settings.Row2(
            icon = OwnTVIcon.SPARKLE,
            title = stringResource(R.string.settings_glass_effect),
            desc = stringResource(R.string.settings_glass_master_description),
            chip = stringResource(if (glassOn) R.string.common_on else R.string.common_off),
            primaryChip = glassOn,
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            onClick = onToggleGlass,
        )

        if (glassOn) {
            Spacer(Modifier.height(12.dp))
            GlassSettingsSection(stringResource(R.string.settings_glass_section_appearance)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 650.dp
                    val background: @Composable (Modifier) -> Unit = { itemModifier ->
                        GlassActionTile(
                            title = stringResource(R.string.settings_glass_background_image),
                            description = stringResource(R.string.settings_glass_background_action_description),
                            selected = bgOn,
                            modifier = itemModifier,
                            onClick = onOpenBackground,
                        )
                    }
                    val presets: @Composable (Modifier) -> Unit = { itemModifier ->
                        Column(itemModifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            GlassPreset.entries.chunked(3).forEach { choices ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    choices.forEach { choice ->
                                        OwnTVButton(
                                            label = glassPresetLabel(choice),
                                            onClick = { onSetPreset(choice) },
                                            style = OwnTVButtonStyle.SECONDARY,
                                            selected = preset == choice,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            background(Modifier.fillMaxWidth())
                            presets(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            background(Modifier.weight(0.75f).height(86.dp))
                            presets(Modifier.weight(1.25f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            GlassSettingsSection(stringResource(R.string.settings_glass_section_fine_tuning)) {
                GlassTuningRow(
                    title = stringResource(R.string.settings_glass_surface_transparency_title),
                    description = stringResource(R.string.settings_glass_transparency_short_description),
                    value = alphaPercent,
                    minimum = 20,
                    maximum = 100,
                    step = 5,
                    onSet = onSetAlpha,
                )
                GlassSectionDivider()
                GlassTuningRow(
                    title = stringResource(R.string.settings_glass_background_blur_title),
                    description = stringResource(R.string.settings_glass_blur_short_description),
                    value = blurPercent,
                    minimum = 0,
                    maximum = 100,
                    step = 10,
                    onSet = onSetBlur,
                )
                GlassSectionDivider()
                GlassTuningRow(
                    title = stringResource(R.string.settings_glass_highlight_title),
                    description = stringResource(R.string.settings_glass_highlight_short_description),
                    value = highlightPercent,
                    minimum = 0,
                    maximum = 100,
                    step = 5,
                    onSet = onSetHighlight,
                )
            }

            Spacer(Modifier.height(10.dp))
            GlassSettingsSection(stringResource(R.string.settings_glass_section_behavior)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 650.dp
                    val fullTransparencyTile: @Composable (Modifier) -> Unit = { itemModifier ->
                        GlassActionTile(
                            title = stringResource(R.string.settings_glass_full_transparency_short),
                            description = stringResource(R.string.settings_glass_full_transparency_short_description),
                            selected = allowFullTransparency,
                            modifier = itemModifier,
                            onClick = { onSetAllowFullTransparency(!allowFullTransparency) },
                        )
                    }
                    val depthTile: @Composable (Modifier) -> Unit = { itemModifier ->
                        GlassActionTile(
                            title = stringResource(R.string.settings_glass_depth_effects_short),
                            description = stringResource(R.string.settings_glass_depth_effects_short_description),
                            selected = depthEffects,
                            modifier = itemModifier,
                            onClick = { onSetDepthEffects(!depthEffects) },
                        )
                    }
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            fullTransparencyTile(Modifier.fillMaxWidth())
                            depthTile(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            fullTransparencyTile(Modifier.weight(1f))
                            depthTile(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            GlassSettingsSection(stringResource(R.string.settings_glass_apply_to)) {
                val allSelected = scope == ALL_GLASS_SURFACES
                val choices = listOf<Pair<String, GlassSurface?>>(stringResource(R.string.settings_glass_surface_all) to null) +
                    GlassSurface.entries.map { glassSurfaceLabel(it) to it }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth < 650.dp) 2 else 4
                    Column {
                        choices.chunked(columns).forEachIndexed { rowIndex, rowChoices ->
                            if (rowIndex > 0) Spacer(Modifier.height(7.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                rowChoices.forEach { (label, surface) ->
                                    val selected = if (surface == null) allSelected else surface in scope
                                    OwnTVButton(
                                        label = label,
                                        onClick = {
                                            if (surface == null) {
                                                onSetScope(GlassConfig(ALL_GLASS_SURFACES).toBitmask())
                                            } else {
                                                val next = if (surface in scope) scope - surface else scope + surface
                                                onSetScope(GlassConfig(next).toBitmask())
                                            }
                                        },
                                        style = OwnTVButtonStyle.SECONDARY,
                                        selected = selected,
                                        compact = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(columns - rowChoices.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    stringResource(R.string.settings_glass_reset_balanced),
                    onClick = {
                        onSetPreset(GlassPreset.BALANCED)
                        onSetHighlight((GlassConfig.DEFAULT_HIGHLIGHT_STRENGTH * 100).roundToInt())
                        onSetAllowFullTransparency(false)
                        onSetDepthEffects(true)
                        onSetScope(GlassConfig(ALL_GLASS_SURFACES).toBitmask())
                    },
                    style = OwnTVButtonStyle.SECONDARY,
                )
            }
        }
    }
}

@Composable
private fun GlassSettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun GlassActionTile(
    title: String,
    description: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        selected = selected,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        surface = GlassSurface.CARDS,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Text(
                stringResource(if (selected) R.string.common_on else R.string.common_off),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.primary else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GlassTuningRow(
    title: String,
    description: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    step: Int,
    onSet: (Int) -> Unit,
) {
    val colors = OwnTVTheme.colors
    val fraction = ((value - minimum).toFloat() / (maximum - minimum).coerceAtLeast(1)).coerceIn(0f, 1f)
    FocusableSurface(
        onClick = { onSet((value + step).let { if (it > maximum) minimum else it }) },
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSet((value - step).coerceAtLeast(minimum)); true }
                    Key.DirectionRight -> { onSet((value + step).coerceAtMost(maximum)); true }
                    else -> false
                }
            },
        shape = RoundedCornerShape(11.dp),
        surface = GlassSurface.CARDS,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Box(
                Modifier.width(210.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(colors.outlineVariant.copy(alpha = 0.55f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction).fillMaxHeight()
                        .background(colors.primary, RoundedCornerShape(3.dp)),
                )
            }
            Text(
                stringResource(R.string.settings_surface_transparency, value),
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(58.dp),
            )
        }
    }
}

@Composable
private fun GlassSectionDivider() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp)
            .background(OwnTVTheme.colors.outlineVariant.copy(alpha = 0.45f)),
    )
}

@Composable
private fun GlassEffectContentScreen(
    glassOn: Boolean,
    preset: GlassPreset,
    alphaPercent: Int,
    blurPercent: Int,
    highlightPercent: Int,
    allowFullTransparency: Boolean,
    depthEffects: Boolean,
    bgOn: Boolean,
    scope: Set<GlassSurface>,
    onToggleGlass: () -> Unit,
    onSetPreset: (GlassPreset) -> Unit,
    onSetAlpha: (Int) -> Unit,
    onSetBlur: (Int) -> Unit,
    onSetHighlight: (Int) -> Unit,
    onSetAllowFullTransparency: (Boolean) -> Unit,
    onSetDepthEffects: (Boolean) -> Unit,
    onSetScope: (Int) -> Unit,
    onOpenBackground: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    // Per-surface scope sub-dialog (advanced). While it is open the main panel is NOT composed at all:
    // the main panel's trapAllFocusExit would otherwise keep D-pad focus locked inside itself, making
    // the sub-dialog unreachable. Re-request focus here whenever the main panel comes (back) on screen.
    var showSurfaces by remember { mutableStateOf(false) }
    LaunchedEffect(showSurfaces) { if (!showSurfaces) runCatching { firstFocus.requestFocus() } }
    val min = 20
    val max = 100
    val step = 5
    fun clamp(v: Int) = v.coerceIn(min, max)
    // Backdrop blur ("frost") stepper — 0..100 in 10% steps. 0 keeps the Tier-1 translucency-only look;
    // only has an effect when a background image is set and the device supports it (API 31+).
    val blurMin = 0
    val blurMax = 100
    val blurStep = 10
    fun blurClamp(v: Int) = v.coerceIn(blurMin, blurMax)
    val highlightStep = 5
    fun highlightClamp(v: Int) = v.coerceIn(0, 100)
    BackHandler { onBack() }
    if (!showSurfaces) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .roundedPanel()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            tv.own.owntv.features.settings.Header(
                title = stringResource(R.string.settings_glass_effect_title),
                onBack = onBack,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_glass_effect_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.settings_glass_live_preview),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            GlassEffectPreview()
            Spacer(Modifier.height(14.dp))
            // Master on/off for the glass (works with or without a background image).
            tv.own.owntv.features.settings.Row2(
                icon = OwnTVIcon.THEME,
                title = stringResource(R.string.settings_glass_effect),
                desc = stringResource(R.string.settings_glass_description),
                chip = stringResource(if (glassOn) R.string.common_on else R.string.common_off),
                primaryChip = glassOn,
                onClick = onToggleGlass,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            )
            if (glassOn) {
                Spacer(Modifier.height(14.dp))
                // Wallpaper belongs to Glass mode and stays hidden until Glass is enabled.
                tv.own.owntv.features.settings.Row2(
                    icon = OwnTVIcon.IMAGE,
                    title = stringResource(if (bgOn) R.string.settings_background_on else R.string.settings_background_off),
                    chevron = true,
                    onClick = onOpenBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(22.dp))
                Text(stringResource(R.string.settings_glass_preset_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    glassPresetDescription(preset),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                GlassPreset.entries.chunked(2).forEach { rowPresets ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowPresets.forEach { choice ->
                            OwnTVButton(
                                label = glassPresetLabel(choice),
                                onClick = { onSetPreset(choice) },
                                style = OwnTVButtonStyle.SECONDARY,
                                selected = preset == choice,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.settings_transparency_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_transparency_description, min, max),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(stringResource(R.string.settings_decrease), dimmed = alphaPercent <= min) { onSetAlpha(clamp(alphaPercent - step)) }
                    Text(
                        stringResource(R.string.settings_surface_transparency, alphaPercent),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton(stringResource(R.string.settings_increase), dimmed = alphaPercent >= max) { onSetAlpha(clamp(alphaPercent + step)) }
                }
                // Backdrop blur — the real "frost" behind the panels (needs a background image; API 31+).
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.settings_blur_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (bgOn) R.string.settings_blur_description_enabled else R.string.settings_blur_description_disabled, blurMin, blurMax),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(stringResource(R.string.settings_decrease), dimmed = blurPercent <= blurMin) { onSetBlur(blurClamp(blurPercent - blurStep)) }
                    Text(
                        stringResource(R.string.settings_surface_transparency, blurPercent),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton(stringResource(R.string.settings_increase), dimmed = blurPercent >= blurMax) { onSetBlur(blurClamp(blurPercent + blurStep)) }
                }

                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.settings_glass_highlight_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_glass_highlight_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton(stringResource(R.string.settings_decrease), dimmed = highlightPercent <= 0) {
                        onSetHighlight(highlightClamp(highlightPercent - highlightStep))
                    }
                    Text(
                        stringResource(R.string.settings_surface_transparency, highlightPercent),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.primary,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton(stringResource(R.string.settings_increase), dimmed = highlightPercent >= 100) {
                        onSetHighlight(highlightClamp(highlightPercent + highlightStep))
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.settings_glass_full_transparency_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                OwnTVButton(
                    label = "${stringResource(R.string.settings_glass_full_transparency_title)}: ${stringResource(if (allowFullTransparency) R.string.common_on else R.string.common_off)}",
                    onClick = { onSetAllowFullTransparency(!allowFullTransparency) },
                    style = OwnTVButtonStyle.SECONDARY,
                    selected = allowFullTransparency,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.settings_glass_depth_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                OwnTVButton(
                    label = "${stringResource(R.string.settings_glass_depth_title)}: ${stringResource(if (depthEffects) R.string.common_on else R.string.common_off)}",
                    onClick = { onSetDepthEffects(!depthEffects) },
                    style = OwnTVButtonStyle.SECONDARY,
                    selected = depthEffects,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Advanced: choose exactly which surfaces render as glass.
                Spacer(Modifier.height(16.dp))
                OwnTVButton(
                    if (scope == ALL_GLASS_SURFACES) stringResource(R.string.settings_surface_count_all)
                    else pluralStringResource(R.plurals.settings_surface_count, scope.size, scope.size, ALL_GLASS_SURFACES.size),
                    onClick = { showSurfaces = true },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (glassOn) {
                Spacer(Modifier.height(24.dp))
                OwnTVButton(
                    stringResource(R.string.settings_reset),
                    onClick = {
                        onSetPreset(GlassPreset.BALANCED)
                        onSetHighlight((GlassConfig.DEFAULT_HIGHLIGHT_STRENGTH * 100).roundToInt())
                        onSetAllowFullTransparency(false)
                        onSetDepthEffects(true)
                        onSetScope(GlassConfig(ALL_GLASS_SURFACES).toBitmask())
                    },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (showSurfaces) {
        GlassSurfacesDialog(scope = scope, onSetScope = onSetScope, onDismiss = { showSurfaces = false })
    }
}

/** Compact always-visible sample of the same panel/card/top-bar surfaces controlled below. */
@Composable
private fun GlassEffectPreview() {
    val colors = OwnTVTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.secondaryContainer.copy(alpha = 0.42f))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1.5f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).glass(
                    surface = GlassSurface.PANELS,
                    baseFill = colors.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    interaction = GlassInteraction.SELECTED,
                ),
            )
            Box(
                Modifier.weight(0.9f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).glass(
                    surface = GlassSurface.CARDS,
                    baseFill = colors.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    interaction = GlassInteraction.FOCUSED,
                ),
            )
            Box(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(17.dp)).glass(
                    surface = GlassSurface.TOPBAR,
                    baseFill = colors.surfaceContainerHighest,
                    shape = RoundedCornerShape(17.dp),
                    interaction = GlassInteraction.FOCUSED,
                ),
            )
        }
    }
}

/**
 * Browsing & lists — six per-section toggles, two for each of Live TV / Movies / Series:
 *
 *  - "Remember last category" (on by default): reopening the section lands on the category you left
 *    rather than All. Live TV has always behaved this way; Movies/Series gained it alongside the toggle.
 *  - "Remember last item" (off by default): each category keeps its own scroll position instead of
 *    resetting to the top. The Live TV one additionally gates the last-focused-channel restore.
 *
 * The separate "App startup -> Last channel" setting is independent of all six.
 */
@Composable
private fun BrowsingListsDialog(
    catLive: Boolean,
    catMovies: Boolean,
    catSeries: Boolean,
    itemLive: Boolean,
    itemMovies: Boolean,
    itemSeries: Boolean,
    onToggleCatLive: () -> Unit,
    onToggleCatMovies: () -> Unit,
    onToggleCatSeries: () -> Unit,
    onToggleItemLive: () -> Unit,
    onToggleItemMovies: () -> Unit,
    onToggleItemSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        tv.own.owntv.ui.theme.PopupFontTheme {
        // Six toggles + two group headers overflow a 720p panel — dialogPanel already scrolls the body
        // (scroll = true by default), so do NOT add another verticalScroll here.
        Column(
            modifier = Modifier.dialogPanel(width = 520.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_browsing_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_browsing_description_full),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))

            // SECONDARY chrome on every row (matching GlassSurfacesDialog): with an accent fill on each
            // "On" row the focused row becomes hard to pick out on a TV. State reads from the ": On/Off"
            // text; focus is carried by the button's own highlight.
            BrowsingGroupLabel(stringResource(R.string.settings_browsing_last_category), stringResource(R.string.settings_browsing_last_category_description))
            OwnTVButton(
                stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_live), stringResource(if (catLive) R.string.common_on else R.string.common_off)), onClick = onToggleCatLive,
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            )
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_movies), stringResource(if (catMovies) R.string.common_on else R.string.common_off)), onClick = onToggleCatMovies,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_series), stringResource(if (catSeries) R.string.common_on else R.string.common_off)), onClick = onToggleCatSeries,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(18.dp))
            BrowsingGroupLabel(
                stringResource(R.string.settings_browsing_last_item),
                stringResource(R.string.settings_browsing_last_item_description),
            )
            OwnTVButton(stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_live), stringResource(if (itemLive) R.string.common_on else R.string.common_off)), onClick = onToggleItemLive,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_movies), stringResource(if (itemMovies) R.string.common_on else R.string.common_off)), onClick = onToggleItemMovies,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_section_toggle, stringResource(R.string.settings_history_series), stringResource(if (itemSeries) R.string.common_on else R.string.common_off)), onClick = onToggleItemSeries,
                style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(20.dp))
            OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
        }
    }
}

@Composable
private fun BrowsingGroupLabel(title: String, desc: String) {
    val colors = OwnTVTheme.colors
    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface,
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(2.dp))
    Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun glassPresetLabel(preset: GlassPreset): String = stringResource(
    when (preset) {
        GlassPreset.ULTRA_CLEAR -> R.string.settings_glass_preset_ultra_clear
        GlassPreset.CLEAR -> R.string.settings_glass_preset_clear
        GlassPreset.BALANCED -> R.string.settings_glass_preset_balanced
        GlassPreset.TINTED -> R.string.settings_glass_preset_tinted
        GlassPreset.OPAQUE -> R.string.settings_glass_preset_opaque
        GlassPreset.AURORA -> R.string.settings_glass_preset_aurora
        GlassPreset.CUSTOM -> R.string.settings_glass_preset_custom
    },
)

@Composable
private fun glassPresetDescription(preset: GlassPreset): String = stringResource(
    when (preset) {
        GlassPreset.ULTRA_CLEAR -> R.string.settings_glass_preset_ultra_clear_description
        GlassPreset.CLEAR -> R.string.settings_glass_preset_clear_description
        GlassPreset.BALANCED -> R.string.settings_glass_preset_balanced_description
        GlassPreset.TINTED -> R.string.settings_glass_preset_tinted_description
        GlassPreset.OPAQUE -> R.string.settings_glass_preset_opaque_description
        GlassPreset.AURORA -> R.string.settings_glass_preset_aurora_description
        GlassPreset.CUSTOM -> R.string.settings_glass_preset_custom_description
    },
)

/** User-facing label for a glassable surface. */
@Composable
private fun glassSurfaceLabel(s: GlassSurface): String = stringResource(
    when (s) {
        GlassSurface.PANELS -> R.string.settings_glass_surface_panels
        GlassSurface.SIDEBAR -> R.string.settings_glass_surface_sidebar
        GlassSurface.PREVIEW -> R.string.settings_glass_surface_preview
        GlassSurface.DIALOGS -> R.string.settings_glass_surface_dialogs
        GlassSurface.TOPBAR -> R.string.settings_glass_surface_topbar
        GlassSurface.CARDS -> R.string.settings_glass_surface_cards
        GlassSurface.MINI_PLAYER -> R.string.settings_glass_surface_miniplayer
    },
)

/**
 * Advanced per-surface glass scope: one On/Off row per [GlassSurface] plus an "All" master.
 * Changes apply live (persisted via the scope bitmask). Unticking every surface is the same as
 * turning glass off — the helper text says so instead of blocking it.
 */
@Composable
private fun GlassSurfacesDialog(
    scope: Set<GlassSurface>,
    onSetScope: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    fun toggled(s: GlassSurface): Int = GlassConfig(if (s in scope) scope - s else scope + s).toBitmask()
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        tv.own.owntv.ui.theme.PopupFontTheme {
        Column(
            modifier = Modifier.dialogPanel(width = 440.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_glass_surfaces), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_glass_surfaces_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // All rows use the SECONDARY chrome: with the accent (PRIMARY) fill on every "On" row the
            // focused row was indistinguishable on TV. State lives in the ": On/Off" text; focus in the
            // button's own focus highlight.
            OwnTVButton(
                if (scope == ALL_GLASS_SURFACES) stringResource(R.string.settings_all_surfaces_on) else stringResource(R.string.settings_all_surfaces_off),
                onClick = {
                    onSetScope(if (scope == ALL_GLASS_SURFACES) 0 else GlassConfig(ALL_GLASS_SURFACES).toBitmask())
                },
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            )
            Spacer(Modifier.height(12.dp))
            GlassSurface.entries.forEach { s ->
                val on = s in scope
                OwnTVButton(
                    stringResource(R.string.settings_surface_toggle, glassSurfaceLabel(s), stringResource(if (on) R.string.common_on else R.string.common_off)),
                    onClick = { onSetScope(toggled(s)) },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
            OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
        }
    }
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
    player: SettingsRepository.CatchupPlayer,
    onSetPlayer: (SettingsRepository.CatchupPlayer) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    val manual = mode == SettingsRepository.CatchupTimezone.MANUAL
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.settings_catchup), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_catchup_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            // Mode toggle: Device / Manual.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(
                    stringResource(R.string.settings_catchup_timezone_device),
                    onClick = { onSetMode(SettingsRepository.CatchupTimezone.DEVICE) },
                    style = if (!manual) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                OwnTVButton(
                    stringResource(R.string.settings_manual),
                    onClick = { onSetMode(SettingsRepository.CatchupTimezone.MANUAL) },
                    style = if (manual) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                )
            }
            if (manual) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Dimmed, never disabled — a disabled button leaves the focus graph and the D-pad
                    // then walks straight out of the dialog.
                    StepButton(stringResource(R.string.settings_decrease), dimmed = offsetMinutes <= offsetRange.first) { onAdjustOffset(-60) }
                    Text(
                        utcOffsetLabel(offsetMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.primary,
                        modifier = Modifier.width(150.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton(stringResource(R.string.settings_increase), dimmed = offsetMinutes >= offsetRange.last) { onAdjustOffset(60) }
                }
            }
            // Which player takes an archive programme. Archives are the streams the in-app engines
            // struggle with most, so an external app is a useful fallback — "Ask" puts the choice on
            // the "Watch from start" action itself instead of forcing one answer forever.
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.settings_catchup_player), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsRepository.CatchupPlayer.entries.forEach { p ->
                    OwnTVButton(
                        when (p) {
                            SettingsRepository.CatchupPlayer.ASK -> stringResource(R.string.settings_catchup_player_ask)
                            SettingsRepository.CatchupPlayer.INTERNAL -> stringResource(R.string.settings_catchup_player_internal)
                            SettingsRepository.CatchupPlayer.EXTERNAL -> stringResource(R.string.settings_catchup_player_external)
                        },
                        onClick = { onSetPlayer(p) },
                        style = if (player == p) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                        compact = true,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            OwnTVButton(stringResource(R.string.settings_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
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
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    val doneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.content_epg_time_offset), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_epg_offset_dialog_description),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // Dimmed, never disabled: a disabled button leaves the focus graph, so reaching a limit
                // used to drop focus out of the dialog entirely. The adjust is clamped anyway.
                StepButton("–", dimmed = offsetMinutes <= offsetRange.first, modifier = Modifier.focusRequester(firstFocus)) { onAdjust(-30) }
                Text(
                    epgShiftLabel(offsetMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.primary,
                    modifier = Modifier.width(150.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton("+", dimmed = offsetMinutes >= offsetRange.last) { onAdjust(30) }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (offsetMinutes != 0) {
                    // Reset removes itself from the row (the offset becomes 0), taking the focused
                    // element with it — so hand focus to Done in the same click.
                    OwnTVButton(
                        stringResource(R.string.common_reset),
                        onClick = { onReset(); runCatching { doneFocus.requestFocus() } },
                        style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.weight(1f),
                    )
                }
                OwnTVButton(stringResource(R.string.common_done), onClick = onDismiss, modifier = Modifier.weight(1f).focusRequester(doneFocus))
            }
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(64.dp),
        shape = RoundedCornerShape(18.dp),
        contentAlignment = Alignment.Center,
        surface = GlassSurface.DIALOGS,
    ) { _ ->
        Text(label, style = MaterialTheme.typography.headlineMedium, color = if (enabled && !dimmed) colors.onSurface else colors.outline)
    }
}

/**
 * One entry in the Settings root list, as data rather than as an inline call.
 *
 * The root is a lazy list, which needs a stable key per entry — and both focus restores (closing a
 * dialog, and Back from a sub-screen) need the *index* of a row that may be scrolled off-screen and
 * therefore not composed at all. Neither is knowable from a plain Column of 36 inline rows.
 */
private sealed interface RootItem {
    val key: String
}

/** A group heading — one entry in the left spine, with the icon and summary the sheet is headed by. */
private data class RootGroup(
    override val key: String,
    val label: String,
    val icon: OwnTVIcon,
    val summary: String,
) : RootItem

private data class RootRow(
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

/** The list key of the row that opens [tab], so a Back from that sub-screen can find its index. */
private fun tabRowKey(tab: SettingsTab) = "tab_${tab.name}"

@Composable
private fun RootItemContent(
    item: RootRow,
    valueColumn: Dp,
    pinned: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    /** Takes precedence over the row's own requester: used to give focus back after its menu closes. */
    focus: FocusRequester? = null,
) {
    SettingsRow(
        dense = true,
        valueColumn = valueColumn,
        pinned = pinned,
        onLongClick = onLongClick,
        tone = item.tone,
        icon = item.icon,
        title = item.title,
        desc = item.desc,
        chip = item.chip,
        chipTone = item.chipTone,
        showChevron = item.showChevron,
        onClick = item.onClick,
        modifier = (focus ?: item.focus)?.let { Modifier.focusRequester(it) } ?: Modifier,
    )
}

// ---------------------------------------------------------------------------------------------
// The Settings surface, built to audit/OwnTV_Settings_Final_Mockup.html. Every number below is the
// mockup's own CSS value read as dp. The pieces are deliberately hand-drawn rather than inherited
// from FocusableSurface's tonal ladder: the mockup designs Glass OFF as its own solid pass, not as
// the frosted pass with the blur removed, so both material modes must land on the *same* silhouette.
// ---------------------------------------------------------------------------------------------

/** The value column, the count badges and the eyebrows. Fixed on purpose — see [SettingsMono]. */
private val SettingsMono = FontFamily(Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold))

/**
 * Design tokens, straight from the mockup's `:root`. `veil`/`veil2` are the neutral washes an idle
 * icon tile and a hovered row sit on; everything accent-tinted is derived from [primary] so a custom
 * accent reaches all of it.
 *
 * This is also the fix for "some icons take the accent and some don't": the old rows tinted their
 * tile from [TileTone], and only `PRIMARY` maps to a container that follows the accent —
 * `secondaryContainer`/`tertiaryContainer` are fixed palette constants. Tiles now have exactly two
 * states, neutral and accent, and the accent one is always the real accent.
 */
internal object SettingsSkin {
    val RowShape = RoundedCornerShape(14.dp)
    val TileShape = RoundedCornerShape(11.dp)
    val PaneShape = RoundedCornerShape(18.dp)
    val TileSize = 34.dp
    val GlyphSize = 17.dp
    val RowMinHeight = 63.dp
    val NavMinHeight = 57.dp
    val ValueColumn = 170.dp
    val SpineWidth = 294.dp

    val veil: Color @Composable get() = OwnTVTheme.colors.onSurface.copy(alpha = 0.055f)
    val veil2: Color @Composable get() = OwnTVTheme.colors.onSurface.copy(alpha = 0.030f)
    /** The focus wash — `rgb(var(--rgb)/.09)` in the mockup. */
    val focusWash: Color @Composable get() = OwnTVTheme.colors.primary.copy(alpha = 0.09f)
    /** An active or focused icon tile — `rgb(var(--rgb)/.16)`. */
    val tileHot: Color @Composable get() = OwnTVTheme.colors.primary.copy(alpha = 0.16f)
}

/**
 * The focus ring: `box-shadow:0 0 0 2px var(--ring)` plus the accent wash under it. Drawn here rather
 * than by [FocusableSurface] so the ring is identical with Glass on and off, and so it still honours
 * the user's Focus highlight colour and width.
 */
@Composable
internal fun Modifier.settingsFocusRing(focused: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier {
    if (!focused) return this
    val colors = OwnTVTheme.colors
    return this
        .background(SettingsSkin.focusWash, shape)
        .border(tv.own.owntv.ui.theme.LocalFocusBorderWidth.current, colors.focusBorder, shape)
}

/**
 * A thin thumb down the inner edge of a scrolling column. Both columns scroll, and both have to be
 * *seen* to scroll: a hidden scrollbar on a ten-group spine reads as "that is all there is" from the
 * sofa, which is precisely the complaint this screen was rebuilt to answer.
 */
@Composable
private fun Modifier.settingsScrollbar(state: androidx.compose.foundation.lazy.LazyListState): Modifier {
    val idle = OwnTVTheme.colors.onSurfaceVariant.copy(alpha = 0.20f)
    return this.drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo.size
        if (total == 0 || visible == 0 || visible >= total) return@drawWithContent
        val inset = 4.dp.toPx()
        val trackHeight = size.height - inset * 2
        val thumbHeight = (trackHeight * visible / total).coerceAtLeast(28.dp.toPx())
        val first = info.visibleItemsInfo.first().index.toFloat()
        val progress = (first / (total - visible).coerceAtLeast(1)).coerceIn(0f, 1f)
        drawRoundRect(
            color = idle,
            topLeft = androidx.compose.ui.geometry.Offset(size.width - 6.dp.toPx(), inset + (trackHeight - thumbHeight) * progress),
            size = androidx.compose.ui.geometry.Size(4.dp.toPx(), thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
        )
    }
}

/** The 34 dp rounded square every row and nav item leads with. Neutral, or accent when it matters. */
@Composable
internal fun SettingsIconTile(icon: OwnTVIcon, hot: Boolean, size: Dp = SettingsSkin.TileSize) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(SettingsSkin.TileShape)
            .background(if (hot) SettingsSkin.tileHot else SettingsSkin.veil),
        contentAlignment = Alignment.Center,
    ) {
        OwnTVIcon(
            icon = icon,
            tint = if (hot) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(SettingsSkin.GlyphSize),
        )
    }
}

/** A mono label: the count badges, the sheet tag, the spine eyebrow. */
@Composable
internal fun MonoText(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color, letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = SettingsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = size,
        letterSpacing = letterSpacing,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * One group on the spine. Focus *is* the selection here — the TV pattern used by every other rail in
 * the app — so [onFocused] swaps the sheet while the user is still on the left. The selected item
 * keeps its highlight after focus moves right, which is what tells the user which group the rows on
 * the right belong to.
 */
@Composable
internal fun SpineItem(
    label: String,
    summary: String,
    icon: OwnTVIcon,
    count: Int,
    selected: Boolean,
    active: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val shape = SettingsSkin.RowShape
    var focused by remember { mutableStateOf(false) }
    val hot = focused || selected || active
    FocusableSurface(
        onClick = onFocused,
        selected = selected,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = shape,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        focusedScale = 1f,
        showFocusBorder = false,
        renderSelectionContainer = false,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        // The selected group's plate: `rgb(var(--rgb)/.12)` with a `.28` hairline and the 4 dp accent
        // bar on the inner edge, pointing at the rows it owns.
        if (selected) {
            Box(
                Modifier.matchParentSize()
                    .background(colors.primary.copy(alpha = 0.12f), shape)
                    .border(1.dp, colors.primary.copy(alpha = 0.28f), shape),
            )
        }
        Box(Modifier.matchParentSize().settingsFocusRing(focused, shape))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsSkin.NavMinHeight)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsIconTile(icon, hot = hot)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    color = colors.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .heightIn(min = 24.dp)
                    .widthIn(min = 26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hot) colors.primary.copy(alpha = 0.15f) else SettingsSkin.veil2)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                MonoText(count.toString(), 11.5.sp, if (hot) colors.primary else colors.onSurfaceVariant)
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = 9.dp)
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(colors.primary),
            )
        }
    }
}

/**
 * The head of a stacked spine: where you came from, above the screen you are in, with the arrow that
 * takes you back there. Sits where the root spine's heading sits, so the column keeps its shape.
 */
@Composable
internal fun SpineBackRow(from: String, title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val shape = SettingsSkin.RowShape
    var focused by remember { mutableStateOf(false) }
    FocusableSurface(
        onClick = onBack,
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp).onFocusChanged { focused = it.isFocused },
        shape = shape,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedScale = 1f,
        showFocusBorder = false,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Box(Modifier.matchParentSize().background(SettingsSkin.veil2, shape).border(1.dp, colors.outlineVariant, shape))
        Box(Modifier.matchParentSize().settingsFocusRing(focused, shape))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsIconTile(OwnTVIcon.BACK, hot = focused)
            Column(modifier = Modifier.weight(1f)) {
                MonoText(from.uppercase(), 11.sp, colors.outline, letterSpacing = 1.1.sp)
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** The spine's own heading, above the groups. */
@Composable
private fun SpineHeader() {
    val colors = OwnTVTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 12.dp)) {
        MonoText(
            stringResource(R.string.settings_spine_eyebrow).uppercase(),
            10.5.sp,
            colors.outline,
            letterSpacing = 1.8.sp,
        )
        Text(
            stringResource(R.string.settings_spine_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        Text(
            stringResource(R.string.settings_spine_hint),
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/**
 * The sheet's own heading: the group the rows below belong to, one line saying what is in it, and the
 * count tag on the right — which goes accent while focus is in the rows, so the sheet says out loud
 * which of the two columns has the cursor.
 */
@Composable
internal fun SheetHeader(title: String, summary: String, tag: String, tagHot: Boolean) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                summary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .heightIn(min = 26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (tagHot) colors.primary.copy(alpha = 0.15f) else SettingsSkin.veil)
                .border(1.dp, if (tagHot) colors.primary.copy(alpha = 0.30f) else colors.outlineVariant, RoundedCornerShape(9.dp))
                .padding(horizontal = 11.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            MonoText(tag, 11.sp, if (tagHot) colors.primary else colors.onSurfaceVariant)
        }
    }
}

/** Collapsed search: the `.sbtn` chip beside the title that opens into the real field on OK. */
@Composable
private fun SearchChip(onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(13.dp)
    var focused by remember { mutableStateOf(false) }
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = shape,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedScale = 1f,
        showFocusBorder = false,
        contentAlignment = Alignment.Center,
    ) { _ ->
        Box(
            Modifier.matchParentSize()
                .background(SettingsSkin.veil, shape)
                .border(1.dp, if (focused) colors.primary else colors.outlineVariant, shape),
        )
        Box(Modifier.matchParentSize().settingsFocusRing(focused, shape))
        Row(
            modifier = Modifier.heightIn(min = 41.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OwnTVIcon(
                icon = OwnTVIcon.SEARCH,
                tint = if (focused) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.settings_search_label),
                fontSize = 13.sp,
                color = if (focused) colors.onSurface else colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The one hairline on the spine: Quick belongs to the user, the nine groups below it do not. */
@Composable
private fun SpineSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .height(1.dp)
            .background(OwnTVTheme.colors.outlineVariant),
    )
}

/** The spine's foot: one quiet line saying the accent you are seeing is following your cursor. */
@Composable
private fun SpineFooter() {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(colors.primary))
        Text(
            stringResource(R.string.settings_spine_foot),
            fontSize = 11.5.sp,
            color = colors.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsRow(
    tone: TileTone,
    icon: OwnTVIcon,
    title: String,
    desc: String? = null,
    chip: String? = null,
    chipTone: TileTone = TileTone.PRIMARY,
    soon: Boolean = false,
    showChevron: Boolean = false,
    /** Square inside the sheet (one container, hairline separators); rounded as a standalone card. */
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    /** A row on the sheet, drawn to the mockup; false is the plain card used elsewhere. */
    dense: Boolean = false,
    /** The mockup's fixed value column, narrowed by the caller when the panel is tight. */
    valueColumn: Dp = SettingsSkin.ValueColumn,
    /** Marks the row with the accent dot that says "this one is also in Quick". */
    pinned: Boolean = false,
    /** Hold OK: opens the row menu. Null on rows that have nothing to offer there. */
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    if (dense) {
        SheetRow(icon, title, desc, chip, chipTone, soon, showChevron, valueColumn, pinned, onLongClick, modifier, onClick)
        return
    }
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        surface = GlassSurface.CARDS,
        // Diagnostic + production-safe scrolling path: a full-width row must not move an aligned
        // backdrop texture inside the scroll container. Focus still gets luminous tint and rim;
        // the static parent panel retains real frost. This also avoids stale HWUI damage trails on
        // affected Android TV GPUs.
        glassFrostScale = 0f,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Tonal icon tile
            val (tileBg, tileOn) = tone.colors()
            Box(
                modifier = Modifier
                    .size(Dimens.IconTileSize)
                    .clip(RoundedCornerShape(Dimens.IconTileCorner))
                    .background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                OwnTVIcon(icon = icon, tint = tileOn, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (desc != null) {
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.widthIn(min = 78.dp), contentAlignment = Alignment.CenterEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (soon) SoonChip()
                        if (chip != null) ValueChip(chip, chipTone)
                    }
                }
                if (showChevron) {
                    OwnTVIcon(icon = OwnTVIcon.CHEVRON, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * One row on the sheet, to the mockup: 34 dp tile · title and description · the value column · the
 * chevron. Three things go accent together — the tile, the value and the chevron — and they do it
 * both when the setting is *doing* something and when the row has focus. Nothing that is off or on
 * its default is ever accent, so scanning the column tells you what you have changed.
 */
@Composable
private fun SheetRow(
    icon: OwnTVIcon,
    title: String,
    desc: String?,
    chip: String?,
    chipTone: TileTone,
    soon: Boolean,
    showChevron: Boolean,
    valueColumn: Dp,
    pinned: Boolean,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val shape = SettingsSkin.RowShape
    var focused by remember { mutableStateOf(false) }
    // A held OK on the remote raises the long press while the key is still down, and a plain click when
    // it is finally released — so without this the menu would open and the row would fire underneath it.
    var longAt by remember { mutableLongStateOf(0L) }
    // "Doing something" is already encoded: every row that is off or on its default sends SECONDARY.
    val active = chip != null && chipTone != TileTone.SECONDARY
    FocusableSurface(
        onClick = { if (android.os.SystemClock.uptimeMillis() - longAt > 800) onClick() },
        onLongClick = onLongClick?.let { handler -> { longAt = android.os.SystemClock.uptimeMillis(); handler() } },
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = shape,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedScale = 1f,
        showFocusBorder = false,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Box(Modifier.matchParentSize().settingsFocusRing(focused, shape))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsSkin.RowMinHeight)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconTile(icon, hot = focused || active)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // The dot the mockup puts beside a title that is also sitting in Quick.
                    if (pinned) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(colors.primary),
                        )
                    }
                }
                if (desc != null) {
                    Text(
                        desc,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.width(valueColumn),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (soon) SoonChip()
                if (chip != null) ValueText(chip, active)
            }
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                if (showChevron) {
                    OwnTVIcon(
                        icon = OwnTVIcon.CHEVRON,
                        tint = when {
                            focused -> colors.primary
                            active -> colors.primary.copy(alpha = 0.75f)
                            else -> colors.outline
                        },
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
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
    val colors = OwnTVTheme.colors
    BackHandler { onDismiss() }
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss, fontScale = .50f) {
        Box(
            Modifier.fillMaxSize().longPressMenuGuard().modalScrim().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.dialogPanel(width = 420.dp, padding = 16.dp)) {
                MonoText(
                    text = title.uppercase(),
                    size = 10.sp,
                    color = colors.onSurfaceVariant,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(10.dp))
                tv.own.owntv.features.settings.Row2(
                    icon = OwnTVIcon.SPARKLE,
                    title = stringResource(
                        if (pinned) R.string.settings_row_menu_unpin else R.string.settings_row_menu_pin,
                    ),
                    onClick = { onPinToggle(); onDismiss() },
                )
                if (canMoveUp) {
                    Spacer(Modifier.height(6.dp))
                    tv.own.owntv.features.settings.Row2(
                        icon = OwnTVIcon.CHEVRON_UP,
                        title = stringResource(R.string.settings_row_menu_move_up),
                        onClick = { onMoveUp(); onDismiss() },
                    )
                }
                if (canMoveDown) {
                    Spacer(Modifier.height(6.dp))
                    tv.own.owntv.features.settings.Row2(
                        icon = OwnTVIcon.CHEVRON_DOWN,
                        title = stringResource(R.string.settings_row_menu_move_down),
                        onClick = { onMoveDown(); onDismiss() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SoonChip() {
    val colors = OwnTVTheme.colors
    Text(
        text = stringResource(R.string.settings_soon),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerHighest)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Batch 4 · one searchable settings row: its group breadcrumb, title, extra keywords, and action. */
private class SettingsSearchEntry(
    val group: String,
    val title: String,
    keywords: String,
    val icon: OwnTVIcon,
    val tone: TileTone,
    val chip: String? = null,
    val chipTone: TileTone = TileTone.PRIMARY,
    val showChevron: Boolean = true,
    val onClick: () -> Unit,
) {
    /** Lower-cased match target: group + title + keywords. */
    val haystack: String = "$group $title $keywords".lowercase()
}

/**
 * A sheet value: monospaced so the whole column lines up under itself, accent when the row is set to
 * something and muted on its default/off state.
 */
@Composable
private fun ValueText(text: String, active: Boolean) {
    val colors = OwnTVTheme.colors
    Text(
        text = text,
        fontFamily = SettingsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.13.sp,
        color = if (active) colors.primary else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ValueChip(text: String, tone: TileTone) {
    val (bg, on) = tone.colors()
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = on,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
internal fun TileTone.colors(): Pair<Color, Color> {
    val c = OwnTVTheme.colors
    return when (this) {
        TileTone.PRIMARY -> c.primaryContainer to c.onPrimaryContainer
        TileTone.SECONDARY -> c.secondaryContainer to c.onSecondaryContainer
        TileTone.TERTIARY -> c.tertiaryContainer to c.onTertiaryContainer
    }
}
