package tv.own.owntv.features.shell

import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.features.automation.UiItem
import tv.own.owntv.features.automation.UiItemType
import tv.own.owntv.features.automation.UiPlayer
import tv.own.owntv.features.automation.UiSnapshot

/** What the shell's view models hold about the items on screen. [channel] is Live TV's preview channel. */
internal data class ShellItems(
    val channel: ChannelEntity? = null,
    val selectedMovie: MovieEntity? = null,
    val playingMovie: MovieEntity? = null,
    val selectedSeries: SeriesEntity? = null,
    val openedSeries: SeriesEntity? = null,
    val selectedEpisode: EpisodeEntity? = null,
    val playingSeries: SeriesEntity? = null,
)

/**
 * The shell's state as the automation API reports it ([UiSnapshot]). Best effort: the focused item is
 * the one the section's detail pane shows, and the playing item the one the player's section holds.
 */
internal object ShellUiItems {
    fun snapshot(section: MainSection, playerMode: PlayerMode, zapSource: MainSection?, items: ShellItems) = UiSnapshot(
        section = section,
        player = player(playerMode),
        focused = if (playerMode == PlayerMode.FULLSCREEN) null else focused(section, items),
        playing = if (playerMode == PlayerMode.NONE) null else playing(zapSource, items),
    )

    fun player(mode: PlayerMode): UiPlayer = when (mode) {
        PlayerMode.NONE -> UiPlayer.NONE
        PlayerMode.FULLSCREEN -> UiPlayer.FULLSCREEN
        PlayerMode.MINI -> UiPlayer.MINI
        PlayerMode.AUDIO -> UiPlayer.AUDIO
    }

    private fun focused(section: MainSection, items: ShellItems): UiItem? = when (section) {
        MainSection.LIVE_TV -> items.channel?.let(::channel)
        MainSection.MOVIES -> items.selectedMovie?.let(::movie)
        MainSection.SERIES -> items.openedSeries?.let { show -> openedItem(show, items.selectedEpisode) }
            ?: items.selectedSeries?.let(::series)
        else -> null
    }

    /** Inside an opened show the focus is on one of its episodes, or still on the show itself. */
    private fun openedItem(show: SeriesEntity, episode: EpisodeEntity?): UiItem =
        episode?.takeIf { it.seriesId == show.id }?.let(::episode) ?: series(show)

    private fun playing(zapSource: MainSection?, items: ShellItems): UiItem? = when (zapSource) {
        MainSection.LIVE_TV, MainSection.EPG -> items.channel?.let(::channel)
        MainSection.MOVIES -> items.playingMovie?.let(::movie)
        MainSection.SERIES -> items.playingSeries?.let(::series)
        else -> null
    }

    private fun channel(it: ChannelEntity) = UiItem(UiItemType.CHANNEL, it.id, it.name)
    private fun movie(it: MovieEntity) = UiItem(UiItemType.MOVIE, it.id, it.name)
    private fun series(it: SeriesEntity) = UiItem(UiItemType.SERIES, it.id, it.name)
    private fun episode(it: EpisodeEntity) = UiItem(UiItemType.EPISODE, it.id, it.name)
}
