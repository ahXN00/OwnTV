package tv.own.owntv.features.home

/** What a Keep watching card shows in its 16:9 frame (G12). */
sealed interface HomeStill {
    /** A picture filling the frame, cropped to 16:9 (a poster is cropped too). */
    data class Picture(val url: String) : HomeStill

    /** A channel logo, fitted in the middle of the frame. */
    data class Logo(val url: String) : HomeStill

    data object None : HomeStill
}

/**
 * The still for a Keep watching card, best first: the TMDB backdrop (for an episode, the show's backdrop
 * and then the episode's own still — what [HomeHeroMetadata.backdropUrl] already resolves to), then the
 * provider's backdrop, then its poster, cropped. A channel has none of those, so it shows its logo.
 */
fun homeStill(
    tmdbBackdrop: String?,
    providerBackdrop: String?,
    poster: String?,
    channelLogo: String?,
): HomeStill {
    fun String?.usable() = this?.takeIf { it.isNotBlank() }
    return tmdbBackdrop.usable()?.let(HomeStill::Picture)
        ?: providerBackdrop.usable()?.let(HomeStill::Picture)
        ?: poster.usable()?.let(HomeStill::Picture)
        ?: channelLogo.usable()?.let(HomeStill::Logo)
        ?: HomeStill.None
}
