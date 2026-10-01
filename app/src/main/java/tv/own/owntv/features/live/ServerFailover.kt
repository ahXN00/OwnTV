package tv.own.owntv.features.live

import android.content.Context
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * Cross-server failover for interrupted live streams.
 *
 * The engine ladder inside core (ExoPlayer ⇄ mpv, reconnects, watchdogs) already retries the
 * *same* stream; this is the layer above it. Once the same channel has errored more times than
 * [FailoverPrefs.getRetries] (default [FailoverPrefs.DEFAULT_RETRIES], configurable in
 * Video player settings), the watcher in [LiveViewModel] looks for the same channel name on the
 * *other* configured live sources — Xtream, Stalker or M3U alike, anything whose catalog holds a
 * fuzzy match — and tunes the best match. Hopping continues across failures until every live
 * source has been tried for the current channel, then it stops and leaves the error on screen.
 *
 * App-local [android.content.SharedPreferences] backs the two settings on purpose: the shared
 * settings store lives in the external core artifact, which this fork does not change, so
 * failover tuning stays additive here.
 */
object FailoverPrefs {
    const val DEFAULT_RETRIES = 3
    const val MAX_RETRIES = 10
    val RETRY_CHOICES: IntRange = 0..MAX_RETRIES

    private const val PREFS = "owntv_server_failover"
    private const val KEY_RETRIES = "stream_retries"
    private const val KEY_ENABLED = "hop_enabled"

    fun getRetries(context: Context): Int =
        prefs(context).getInt(KEY_RETRIES, DEFAULT_RETRIES).coerceIn(RETRY_CHOICES)

    fun setRetries(context: Context, retries: Int) {
        prefs(context).edit().putInt(KEY_RETRIES, retries.coerceIn(RETRY_CHOICES)).apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Minimum [matchScore] for a cross-server candidate to be considered the same channel. */
const val FAILOVER_MIN_SCORE = 0.45

/** Compact identity key: lowercased, letters/digits only (`"NOVA Sports-Prime"` → `"novasportsprime"`). */
fun normalizeChannelKey(name: String): String =
    name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

/** Significant search tokens of a channel name (`"Nova Sports Prime HD"` → `{nova, sports, prime, hd}`). */
fun channelTokens(name: String): Set<String> =
    name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

/**
 * How likely [candidateName] is the same channel as [targetName]: 1.0 for an exact (modulo
 * punctuation/case) match, 0.9 for containment, else token Jaccard overlap. Deliberately
 * forgiving about provider suffixes (`" HD"`, `"GR"`, `" 1080p"`); the threshold in
 * [bestChannelMatch] keeps near-misses like `"Nova Sports News"` for `"Nova Sports Prime"`
 * (0.5) eligible — a same-name channel that plays beats a perfect-name one that does not.
 */
fun matchScore(targetName: String, candidateName: String): Double {
    val target = normalizeChannelKey(targetName)
    val candidate = normalizeChannelKey(candidateName)
    if (target.isEmpty() || candidate.isEmpty()) return 0.0
    if (target == candidate) return 1.0
    if (target.contains(candidate) || candidate.contains(target)) return 0.9
    val targetTokens = channelTokens(targetName)
    val candidateTokens = channelTokens(candidateName)
    if (targetTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
    val intersection = targetTokens.intersect(candidateTokens).size
    val union = targetTokens.union(candidateTokens).size
    return if (union == 0) 0.0 else intersection.toDouble() / union
}

/** Highest-scoring candidate at or above [FAILOVER_MIN_SCORE], or null when nothing is close. */
fun bestChannelMatch(targetName: String, candidates: List<ChannelEntity>): ChannelEntity? =
    candidates
        .map { it to matchScore(targetName, it.name) }
        .filter { it.second >= FAILOVER_MIN_SCORE }
        .maxByOrNull { it.second }
        ?.first
