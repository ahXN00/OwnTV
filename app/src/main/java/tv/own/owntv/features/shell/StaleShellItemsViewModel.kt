package tv.own.owntv.features.shell

import androidx.lifecycle.ViewModel

/**
 * Leaves out of GET ui what the shell's view models still hold from the profile before. Like them it is
 * activity-scoped, so it outlives a profile switch the way they do: until the new profile picks
 * something, the channel, movie or show the last profile focused or played would be reported as its
 * own. When the profile changes, each item held at that moment is stale, and stays left out until its
 * view model holds another.
 */
internal class StaleShellItemsViewModel : ViewModel() {
    private var profileId: Long? = null
    private var stale = ShellItems()

    /** [items] without those held over from before [profileId] became the shell's profile. */
    fun current(profileId: Long?, items: ShellItems): ShellItems {
        if (profileId != this.profileId) {
            if (this.profileId != null) stale = items
            this.profileId = profileId
        }
        stale = ShellItems(
            channel = still(stale.channel, items.channel),
            selectedMovie = still(stale.selectedMovie, items.selectedMovie),
            playingMovie = still(stale.playingMovie, items.playingMovie),
            selectedSeries = still(stale.selectedSeries, items.selectedSeries),
            openedSeries = still(stale.openedSeries, items.openedSeries),
            selectedEpisode = still(stale.selectedEpisode, items.selectedEpisode),
            playingSeries = still(stale.playingSeries, items.playingSeries),
        )
        return ShellItems(
            channel = fresh(items.channel, stale.channel),
            selectedMovie = fresh(items.selectedMovie, stale.selectedMovie),
            playingMovie = fresh(items.playingMovie, stale.playingMovie),
            selectedSeries = fresh(items.selectedSeries, stale.selectedSeries),
            openedSeries = fresh(items.openedSeries, stale.openedSeries),
            selectedEpisode = fresh(items.selectedEpisode, stale.selectedEpisode),
            playingSeries = fresh(items.playingSeries, stale.playingSeries),
        )
    }

    /** A stale item stays stale while the view model still holds it. */
    private fun <T> still(stale: T?, now: T?): T? = stale?.takeIf { it == now }

    private fun <T> fresh(now: T?, stale: T?): T? = now?.takeIf { it != stale }
}
