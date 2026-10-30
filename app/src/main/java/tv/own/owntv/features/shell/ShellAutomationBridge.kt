package tv.own.owntv.features.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.features.automation.AutomationNav
import tv.own.owntv.features.automation.AutomationNoticeOverlay
import tv.own.owntv.features.automation.AutomationUiState
import tv.own.owntv.features.automation.NavCheck
import tv.own.owntv.features.automation.NavRequest
import tv.own.owntv.features.automation.UnlockedProfile
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.movies.MovieViewModel
import tv.own.owntv.features.profiles.ProfileGateSessionViewModel
import tv.own.owntv.features.series.SeriesViewModel

/**
 * The shell's side of the automation API: publishes the profile it was unlocked for ([UnlockedProfile])
 * and what is on screen ([AutomationUiState]), applies the navigation the API asks for ([AutomationNav])
 * and shows the API's notices where [modifier] puts them. [onNavigate] and [onOpenSeries] are the shell's own section switch and series opening, so the
 * API does what the remote does.
 */
@Composable
internal fun ShellAutomationBridge(
    section: MainSection,
    onNavigate: (MainSection) -> Unit,
    onOpenSeries: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    PublishUnlockedProfile()
    PublishUiState(section)
    ApplyNavRequests(onNavigate, onOpenSeries)
    AutomationNoticeOverlay(modifier)
}

/**
 * Tells the API which profile this session unlocked ([UnlockedProfile]) while the shell is on screen.
 * When the profile screen or a PIN takes over, the shell leaves and the API sees no open profile.
 */
@Composable
private fun PublishUnlockedProfile() {
    val gateSession = koinViewModel<ProfileGateSessionViewModel>()
    val unlocked = koinInject<UnlockedProfile>()
    val authenticated = gateSession.authenticatedProfileId
    DisposableEffect(unlocked, authenticated) {
        unlocked.publish(authenticated)
        onDispose { unlocked.publish(null) }
    }
}

@Composable
private fun PublishUiState(section: MainSection) {
    val shellVm = koinViewModel<ShellViewModel>()
    val liveVm = koinViewModel<LiveViewModel>()
    val movieVm = koinViewModel<MovieViewModel>()
    val seriesVm = koinViewModel<SeriesViewModel>()
    val items = ShellItems(
        channel = liveVm.previewChannel.collectAsStateWithLifecycle().value,
        selectedMovie = movieVm.selectedMovie.collectAsStateWithLifecycle().value,
        playingMovie = movieVm.playingMovie.collectAsStateWithLifecycle().value,
        selectedSeries = seriesVm.selectedSeries.collectAsStateWithLifecycle().value,
        openedSeries = seriesVm.openedSeries.collectAsStateWithLifecycle().value,
        selectedEpisode = seriesVm.selectedEpisode.collectAsStateWithLifecycle().value,
        playingSeries = seriesVm.playingSeries.collectAsStateWithLifecycle().value,
    )
    val profileId by shellVm.activeProfileId.collectAsStateWithLifecycle()
    val playerMode = shellVm.playerMode.value
    val zapSource = shellVm.zapSource.value
    val stale = koinViewModel<StaleShellItemsViewModel>()
    val uiState = koinInject<AutomationUiState>()
    SideEffect { uiState.publish(ShellUiItems.snapshot(section, playerMode, zapSource, stale.current(profileId, items))) }
    DisposableEffect(uiState) { onDispose { uiState.clear() } }
}

/**
 * Acts on the API's pending navigation once the shell is up, even when it was asked for while OwnTV
 * was still coming forward. One that waited too long, was asked for another profile, or opens a show
 * this profile may no longer see ([NavCheck]) is dropped unseen.
 */
@Composable
private fun ApplyNavRequests(onNavigate: (MainSection) -> Unit, onOpenSeries: (Long) -> Unit) {
    val nav = koinInject<AutomationNav>()
    val check = koinInject<NavCheck>()
    val shellVm = koinViewModel<ShellViewModel>()
    val pending by nav.pending.collectAsStateWithLifecycle()
    val navigate by rememberUpdatedState(onNavigate)
    val openSeries by rememberUpdatedState(onOpenSeries)
    LaunchedEffect(pending) {
        val request = pending ?: return@LaunchedEffect
        if (check.stillWanted(request, shellVm.activeProfileId.value)) {
            when (request) {
                is NavRequest.Section -> navigate(request.target.section)
                is NavRequest.OpenSeries -> openSeries(request.seriesId)
            }
        }
        nav.consumed(request)
    }
}
