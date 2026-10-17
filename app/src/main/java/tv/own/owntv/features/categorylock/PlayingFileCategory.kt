package tv.own.owntv.features.categorylock

/**
 * The category of the downloaded or recorded file last played from the Downloads or Recordings screen,
 * so a relock can tell whether the player is on a file from a locked category. The player knows only
 * the file's path; those screens know the film, episode or channel it came from.
 */
class PlayingFileCategory {
    private data class Played(val path: String, val categoryId: Long)

    @Volatile
    private var played: Played? = null

    /** [path] is about to play. [categoryId] is its film's, show's or channel's category; null when none is known. */
    fun started(path: String, categoryId: Long?) {
        played = categoryId?.let { Played(path, it) }
    }

    /** The category of the file the player is on, when [playerUrl] is the file last started here. */
    fun categoryOf(playerUrl: String?): Long? = played?.takeIf { it.path == playerUrl }?.categoryId
}
