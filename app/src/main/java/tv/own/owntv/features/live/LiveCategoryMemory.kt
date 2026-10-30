package tv.own.owntv.features.live

import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.parseLiveKey

/**
 * "Remember last category — Live TV".
 *
 * Live TV, its Guide view and the TV Guide share one selection, held by [LiveViewModel] and stored in
 * the `last_live_category` setting. These are the decisions behind restoring it, kept pure for the tests.
 */
object LiveCategoryMemory {

    /** What the restore does with one rail built for the current profile and playlists. */
    enum class Step {
        /** The wanted category is in the rail: show it, and stop. */
        APPLY,

        /** The category on screen is not in this rail (another profile's or playlist's folder): show
         *  All channels for now, without saving it, and keep waiting for the wanted one. */
        FALLBACK,

        /** Nothing to change yet: keep waiting (the rail may still be loading). */
        WAIT,
    }

    /**
     * The category to bring back: the user's last explicit pick in this run when there is one (it may
     * not be saved yet, the save is debounced), else the stored value. Null when the setting is off or
     * the stored value is empty or unreadable.
     */
    fun target(remember: Boolean, lastPicked: LiveKey?, stored: String): LiveKey? =
        if (!remember) null else lastPicked ?: parseLiveKey(stored)

    /** [shown] is what the screen shows now, [rail] the keys of a rail built for the current profile. */
    fun step(target: LiveKey, shown: LiveKey, rail: List<LiveKey>): Step = when {
        target in rail -> Step.APPLY
        // Only folders and custom categories depend on the profile and playlists; the fixed entries
        // (All, Favourites, History) are always there, Catch-up only while the provider has an archive.
        shown !in rail && shown != LiveKey.All -> Step.FALLBACK
        else -> Step.WAIT
    }
}
