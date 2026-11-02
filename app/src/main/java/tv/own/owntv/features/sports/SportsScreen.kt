package tv.own.owntv.features.sports

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.theme.mpx
import java.time.LocalDate

/** A page over the tabs: a match, or a team. Back returns to the one below, then to the tab. */
private sealed interface SportsPage {
    data class MatchPage(val match: Match) : SportsPage
    data class TeamPage(val ref: TeamRef) : SportsPage
}

/**
 * The Sports section: the Scores | Calendar | Leagues tabs, and the match and team pages opened from
 * them (Back returns to where each was opened) — or the match page from the player's *Match* button,
 * when Back plays that channel again.
 */
@Composable
fun SportsScreen(
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onAddPlaylist: () -> Unit,
    onChildFocused: () -> Unit,
    onEntryHook: (((() -> Boolean)?) -> Unit)?,
    contentStart: Dp,
    modifier: Modifier = Modifier,
    /** False while the player is full screen over the section: its Back is the player's. */
    active: Boolean = true,
    vm: SportsViewModel = koinViewModel(),
    liveVm: LiveViewModel = koinViewModel(),
) {
    val playback = koinInject<SportsPlayback>()
    // The section always opens on Scores, today — unless it is left for the player from a match page,
    // which Back from the player opens again, and whose Back then lands on the tab it was opened from.
    DisposableEffect(Unit) {
        onDispose {
            if (playback.returnToMatch == null) {
                vm.tab.value = SportsTab.SCORES
                vm.calendarDay.value = LocalDate.now()
            }
        }
    }
    val stack = remember { mutableStateListOf<SportsPage>() }
    var filters by remember { mutableStateOf(false) }
    // Set when the page was opened from the player's Match button: Back plays that channel again.
    var returnTo by remember { mutableStateOf<ChannelEntity?>(null) }
    // The row or card a match was opened from, so Back lands on it.
    var lastKey by remember { mutableStateOf<String?>(null) }
    val open: (Match) -> Unit = { m -> lastKey = m.key; stack.add(SportsPage.MatchPage(m)); vm.open(m) }
    val openTeam: (TeamRef) -> Unit = { ref -> ref.from?.let { lastKey = it }; stack.add(SportsPage.TeamPage(ref)) }
    val back: () -> Unit = {
        stack.removeLastOrNull()
        vm.open((stack.lastOrNull() as? SportsPage.MatchPage)?.match)
    }
    // A league from a match page or the Calendar: that league in the Leagues tab, the pages closed.
    // Each tab draws its own header, so after a switch the new header's open tab takes focus.
    val tab by vm.tab.collectAsStateWithLifecycle()
    val tabFocus = remember { FocusRequester() }
    var focusTabs by remember { mutableIntStateOf(0) }
    val openLeague: (String) -> Unit = { key ->
        stack.clear()
        vm.open(null)
        lastKey = null
        focusTabs++
        vm.chooseLeague(key)
    }
    // A switch forgets the match last opened: the new tab's own focus is its open tab, not that row.
    val onTab: (SportsTab) -> Unit = { t -> if (t != tab) { lastKey = null; vm.tab.value = t; focusTabs++ } }
    LaunchedEffect(focusTabs) {
        if (focusTabs == 0) return@LaunchedEffect
        for (attempt in 0 until 10) {
            withFrameNanos { }
            if (runCatching { tabFocus.requestFocus() }.getOrDefault(false)) break
        }
    }
    LaunchedEffect(Unit) {
        playback.pendingTeam.value?.let { t ->
            playback.pendingTeam.value = null
            vm.teamRefFor(t)?.let(openTeam)
        }
        playback.pendingMatch.value?.let { (m, ch) ->
            playback.pendingMatch.value = null
            returnTo = ch
            open(m)
        } ?: playback.returnToMatch?.let { m ->
            playback.returnToMatch = null
            open(m)
        }
    }

    when (val page = stack.lastOrNull()) {
        is SportsPage.MatchPage -> {
            SportsMatchScreen(
                vm = vm,
                liveVm = liveVm,
                onPlayChannel = { ch, zap -> playback.returnToMatch = page.match; onPlayChannel(ch, zap) },
                onOpenFilters = { filters = true },
                onOpenLeague = { m -> openLeague(League.keyOf(m.sport, m.leagueSlug)) },
                onOpenTeam = openTeam,
                onAddPlaylist = onAddPlaylist,
                onBack = {
                    val ch = returnTo
                    returnTo = null
                    back()
                    if (ch != null) onPlayChannel(ch, playback.session.value?.options?.map { it.channel }.orEmpty())
                },
                active = active,
                contentStart = contentStart,
                modifier = modifier,
            )
            val state = vm.matchPage.collectAsStateWithLifecycle().value
            if (filters && state != null && state.hasPlaylist) {
                SportsFilterPanel(state, onFilter = vm::setFilter, onRemember = vm::setRememberFilters, onDismiss = { filters = false })
            }
        }
        is SportsPage.TeamPage -> SportsTeamScreen(
            vm = vm,
            ref = page.ref,
            onOpenMatch = open,
            onOpenTeam = openTeam,
            onBack = back,
            active = active,
            contentStart = contentStart,
            modifier = modifier,
        )
        null -> when (tab) {
            SportsTab.SCORES -> SportsHubScreen(
                vm = vm,
                onTab = onTab,
                tabFocus = tabFocus,
                onOpenMatch = open,
                onOpenTeam = openTeam,
                onPlayChannel = onPlayChannel,
                onChildFocused = onChildFocused,
                onEntryHook = onEntryHook,
                contentStart = contentStart,
                modifier = modifier,
                restoreKey = lastKey,
            )
            SportsTab.CALENDAR -> SportsCalendarScreen(
                vm = vm,
                frame = TabFrame(tab, onTab, tabFocus, onChildFocused, onEntryHook, contentStart, active),
                onOpenMatch = open,
                onOpenTeam = openTeam,
                onOpenLeague = openLeague,
                restoreKey = lastKey,
                modifier = modifier,
            )
            SportsTab.LEAGUES -> SportsLeaguesScreen(
                vm = vm,
                frame = TabFrame(tab, onTab, tabFocus, onChildFocused, onEntryHook, contentStart, active),
                onOpenMatch = open,
                onOpenTeam = openTeam,
                restoreKey = lastKey,
                modifier = modifier,
            )
        }
    }
}

/** The chips' filter, kept up to date with the profile's sports settings. */
@Composable
internal fun rememberChipFilter(vm: SportsViewModel): ChipFilter {
    val config by vm.sportsConfig.collectAsStateWithLifecycle()
    val chips by vm.chips.collectAsStateWithLifecycle()
    val leagues by vm.leagues.collectAsStateWithLifecycle()
    return remember(config, chips, leagues) { ChipFilter(config, chips, leagues) }
}

/** What the Calendar and Leagues tabs need from the section to draw [SportsTabFrame]. */
internal class TabFrame(
    val tab: SportsTab,
    val onTab: (SportsTab) -> Unit,
    val tabFocus: FocusRequester,
    val onChildFocused: () -> Unit,
    val onEntryHook: (((() -> Boolean)?) -> Unit)?,
    val contentStart: Dp,
    val active: Boolean,
)

/**
 * The Calendar and Leagues tabs around their page: the shared header (title, tabs, [subtitle]), the
 * chips, then [body]; the key [hints] at the bottom. Focus enters on the chips, Back returns to Scores.
 */
@Composable
internal fun SportsTabFrame(
    vm: SportsViewModel,
    frame: TabFrame,
    filter: ChipFilter,
    modifier: Modifier,
    hints: List<Pair<String, String>>,
    subtitle: @Composable () -> Unit,
    /** Back, when the tab has a step of its own to undo first; null returns to Scores. */
    onBack: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    androidx.activity.compose.BackHandler(enabled = frame.active) { onBack?.invoke() ?: frame.onTab(SportsTab.SCORES) }
    var tabFocused by remember { mutableStateOf<SportsTab?>(null) }
    val chipFocus = remember { FocusRequester() }
    DisposableEffect(frame.onEntryHook) {
        frame.onEntryHook?.invoke { runCatching { chipFocus.requestFocus() }.getOrDefault(false) }
        onDispose { frame.onEntryHook?.invoke(null) }
    }
    Box(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) frame.onChildFocused() }) {
        Column(Modifier.fillMaxSize().focusGroup().padding(top = 39.mpx)) {
            SportsHeader(frame.tab, frame.onTab, frame.tabFocus, { tabFocused = it }, Modifier.padding(start = frame.contentStart, end = 360.mpx)) {
                subtitle()
            }
            SportsChipRows(vm, filter, frame.contentStart, frame.tabFocus, chipFocus)
            body()
        }
        StageKeyHints(
            tabHints(frame.tab, tabFocused) ?: hints,
            Modifier.align(Alignment.BottomEnd).padding(end = 60.mpx, bottom = 27.mpx),
            textSize = 18,
        )
    }
}
