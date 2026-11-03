package tv.own.owntv.features.pip

import kotlin.math.roundToInt

/** Everything that decides whether leaving OwnTV right now moves the video into a picture-in-picture window. */
internal data class PipConditions(
    /** Settings › Video player › Picture-in-picture when leaving the app. */
    val optionOn: Boolean,
    /** The TV offers picture-in-picture at all. */
    val supported: Boolean,
    /** Video is on screen full screen or in the mini-player. */
    val playerShown: Boolean,
    /** Multiview is open: several streams, no single picture to keep. */
    val multiview: Boolean,
    /** Sound with no picture: the item has no video track, or Audio Mode switched the video off. */
    val audioOnly: Boolean,
    /** Playing or buffering; a paused video is left to stop as usual. */
    val playing: Boolean,
)

/** A window shape, width to height, inside the range Android accepts for picture-in-picture. */
internal data class PipAspect(val width: Int, val height: Int)

/** A rectangle in window pixels. */
internal data class PipBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** When to offer picture-in-picture, and in what shape. Pure, so the rules are tested without a device. */
internal object PipPolicy {

    /** Android refuses a window wider than 2.39:1 or taller than 1:2.39. */
    private val WIDEST = PipAspect(239, 100)
    private val TALLEST = PipAspect(100, 239)

    /** The shape before the stream reports its own, and for a stream that never does. */
    private val DEFAULT = PipAspect(16, 9)

    private const val SCALE = 1000

    fun eligible(c: PipConditions): Boolean =
        c.optionOn && c.supported && c.playerShown && !c.multiview && !c.audioOnly && c.playing

    /** [videoAspect] is width / height as the engines report it; null or nonsense falls back to 16:9. */
    fun aspect(videoAspect: Float?): PipAspect {
        if (videoAspect == null || !videoAspect.isFinite() || videoAspect <= 0f) return DEFAULT
        val ratio = PipAspect((videoAspect * SCALE).roundToInt().coerceAtLeast(1), SCALE)
        return when {
            wider(ratio, WIDEST) -> WIDEST
            wider(TALLEST, ratio) -> TALLEST
            else -> ratio
        }
    }

    /**
     * Where the picture sits inside the player's [bounds]: the [aspect] fitted and centred, as the video
     * surface draws it. Android animates the window from there. Null for a player not laid out yet.
     */
    fun sourceRect(bounds: PipBounds, aspect: PipAspect): PipBounds? {
        val w = (bounds.right - bounds.left).toLong()
        val h = (bounds.bottom - bounds.top).toLong()
        if (w <= 0 || h <= 0) return null
        return if (w * aspect.height > h * aspect.width) {
            val fitted = (h * aspect.width / aspect.height).toInt()
            val left = bounds.left + (w.toInt() - fitted) / 2
            PipBounds(left, bounds.top, left + fitted, bounds.bottom)
        } else {
            val fitted = (w * aspect.height / aspect.width).toInt()
            val top = bounds.top + (h.toInt() - fitted) / 2
            PipBounds(bounds.left, top, bounds.right, top + fitted)
        }
    }

    /** Compared by cross-multiplying, so the limits hold exactly rather than to within a float rounding. */
    private fun wider(a: PipAspect, b: PipAspect): Boolean =
        a.width.toLong() * b.height > b.width.toLong() * a.height
}
