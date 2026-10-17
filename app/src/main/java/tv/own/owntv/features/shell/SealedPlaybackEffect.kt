package tv.own.owntv.features.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import org.koin.compose.koinInject
import tv.own.owntv.features.categorylock.PlayingCategories
import tv.own.owntv.features.categorylock.PlayingFileCategory
import tv.own.owntv.features.categorylock.SealedPlaybackGuard
import tv.own.owntv.features.categorylock.sealedTileIndices
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.movies.MovieViewModel
import tv.own.owntv.features.multiview.MultiviewState
import tv.own.owntv.features.series.SeriesViewModel
import tv.own.owntv.player.OwnTVPlayer

/**
 * Gives [shellVm] the shell's [SealedPlaybackGuard] while the shell is up. A sealed Live channel leaves
 * the player ([exitPlayer]) and the preview pane, and its engine forgets it; when the player was on that
 * channel's archive, mpv forgets the paused copy too. A sealed film or episode, or a download or
 * recording of one, leaves the player, and mpv forgets the paused copy it would bring back on return. A sealed Multiview tile is emptied, which
 * frees its engine, so the grid does not tune it again on return.
 */
@Composable
internal fun SealedPlaybackEffect(
    shellVm: ShellViewModel,
    liveVm: LiveViewModel,
    movieVm: MovieViewModel,
    seriesVm: SeriesViewModel,
    player: OwnTVPlayer,
    multiview: () -> MultiviewState?,
    exitPlayer: () -> Unit,
) {
    val exit by rememberUpdatedState(exitPlayer)
    val grid by rememberUpdatedState(multiview)
    val files = koinInject<PlayingFileCategory>()
    DisposableEffect(shellVm, liveVm, movieVm, seriesVm, player, files) {
        val inPlayer = { shellVm.playerMode.value != PlayerMode.NONE }
        shellVm.sealedPlayback = SealedPlaybackGuard(
            playing = {
                val vod = movieVm.playingCategoryId() ?: seriesVm.playingCategoryId() ?: files.categoryOf(player.currentMediaUrl)
                PlayingCategories(
                    live = liveVm.previewChannel.value?.categoryId,
                    liveInPlayer = inPlayer() && (liveVm.liveOnExo.value || (player.isLiveContent && player.currentMediaUrl != null)),
                    vod = vod,
                    liveArchive = vod == null && inPlayer() && !player.isLiveContent &&
                        (liveVm.catchupActive.value || liveVm.timeshiftOffsetSec.value != null),
                    tiles = grid()?.tiles?.map { it.channel?.categoryId }.orEmpty(),
                )
            },
            stopLive = { shown, archive ->
                if ((shown || archive) && inPlayer()) exit()
                if (archive) player.discardBackgroundRestore()
                liveVm.stopPreview()
                liveVm.previewEngine.discardBackgroundRestore()
            },
            stopVod = {
                if (inPlayer()) exit()
                player.discardBackgroundRestore()
            },
            stopTiles = { sealed ->
                grid()?.let { state -> sealedTileIndices(state.tiles.map { it.channel?.categoryId }, sealed).forEach(state::clear) }
            },
        )
        onDispose { shellVm.sealedPlayback = null }
    }
}
