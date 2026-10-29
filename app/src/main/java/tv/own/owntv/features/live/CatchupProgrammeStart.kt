package tv.own.owntv.features.live

/**
 * The start of the programme on air, from the guide: the first row of the player's "Go back to…" list.
 *
 * Kept as a wall-clock instant, not an offset: the offset is taken when the row is pressed, so the
 * seconds spent reading the list do not push the picture past the programme's first frame.
 */
data class CatchupProgrammeStart(val title: String, val startMs: Long) {

    /** Seconds behind live at [nowMs]. */
    fun offsetSec(nowMs: Long): Int = ((nowMs - startMs) / 1000L).toInt()

    companion object {
        /** Below this, the programme has only just begun: going back to its start is not worth a row. */
        const val MIN_OFFSET_SEC = 30

        /**
         * The row for a programme that runs from [startMs] to [stopMs], or null when it cannot be
         * offered: not started yet, started a few seconds ago, already over (a guide answer that
         * outlived its programme), or older than the channel's archive ([windowSec]).
         */
        fun of(title: String?, startMs: Long, stopMs: Long, nowMs: Long, windowSec: Int): CatchupProgrammeStart? {
            if (startMs <= 0L || windowSec <= 0) return null
            if (stopMs in 1..nowMs) return null
            val offset = (nowMs - startMs) / 1000L
            if (offset < MIN_OFFSET_SEC || offset > windowSec) return null
            return CatchupProgrammeStart(title?.trim().orEmpty(), startMs)
        }
    }
}
