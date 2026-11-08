package tv.own.owntv.features.series

import kotlin.random.Random

/**
 * Picks a uniformly random element from [episodes], or null when the list is empty.
 *
 * [random] yields a value in [0, 1) and is injectable so the choice is deterministic in tests.
 * The index is clamped so an out-of-spec random() of exactly 1.0 never runs off the end.
 */
fun <T> pickRandomEpisode(episodes: List<T>, random: () -> Double = { Random.nextDouble() }): T? {
    if (episodes.isEmpty()) return null
    val index = (random() * episodes.size).toInt().coerceIn(0, episodes.size - 1)
    return episodes[index]
}
