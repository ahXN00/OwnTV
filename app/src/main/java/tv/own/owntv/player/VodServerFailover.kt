package tv.own.owntv.player

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.live.OpenStreamRegistry
import tv.own.owntv.core.live.StreamPurpose
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.network.HostPool
import tv.own.owntv.core.network.HostPreflight
import tv.own.owntv.core.network.HostSelector
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.settings.SettingsRepository

interface VodPlaybackPort {
    val error: StateFlow<Any?>
    val videoRes: StateFlow<String?>
    val buffering: StateFlow<Boolean>
    val position: StateFlow<Long>
    val currentMediaContentKey: String?
    val lastErrorLine: String?
    fun stop()
    fun fail()
}

class OwnTVPlayerPort(private val player: OwnTVPlayer) : VodPlaybackPort {
    @Suppress("UNCHECKED_CAST")
    override val error: StateFlow<Any?> = player.error as StateFlow<Any?>
    override val videoRes: StateFlow<String?> get() = player.videoRes
    override val buffering: StateFlow<Boolean> get() = player.buffering
    override val position: StateFlow<Long> get() = player.position
    override val currentMediaContentKey: String? get() = player.currentMediaContentKey
    override val lastErrorLine: String? get() = player.lastErrorLine
    override fun stop() = player.stop()
    override fun fail() = player.failVod()
}

/**
 * Films and episodes on the in-use server, and core's [VodFailover] wired to the TV's player (AC5):
 * when the player gives up on an item (its error is on screen, its own retries spent) and the server
 * is what failed, the item reopens on the next server at the position it reached. One instance per
 * ViewModel, so a film and an episode never share a round.
 */
class VodServerFailover(
    private val player: VodPlaybackPort,
    private val hosts: HostSelector,
    gate: suspend (Long) -> ReleaseGate.Result,
    preflight: suspend (SourceEntity, String) -> HostPreflight.Result,
    private val notices: ServerNotices,
    isOnline: () -> Boolean,
    awaitOnline: suspend () -> Unit,
    private val reconnects: suspend () -> Int,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
    private val filmCapMs: Long = FILM_CAP_MS,
    reserve: (Long) -> (() -> Unit) = { {} },
) {
    constructor(
        player: OwnTVPlayer,
        hosts: HostSelector,
        gate: ReleaseGate,
        xtream: XtreamClient,
        notices: ServerNotices,
        connectivity: ConnectivityObserver,
        settings: SettingsRepository,
        openStreams: OpenStreamRegistry,
    ) : this(
        OwnTVPlayerPort(player),
        hosts,
        { gate.stopAllStreamsAndAwait(it) },
        { s, h -> HostPreflight.run(xtream, s, h) },
        notices,
        { connectivity.isOnlineNow() },
        { connectivity.isOnline.first { it } },
        { settings.vodReconnects.first() },
        reserve = reserveVia(openStreams),
    )

    private val failover = VodFailover(
        hosts = hosts,
        gate = gate,
        preflight = preflight,
        log = log,
        isOnline = isOnline,
        awaitOnline = awaitOnline,
        onOffline = { notices.noNetwork() },
        reserve = reserve,
    )
    private var job: Job? = null
    private var activeKey: String? = null
    private var sameServerReplays = 0

    /** [storedUrl] (as sync stored it, on the primary) moved onto [source]'s in-use server for playing now. */
    fun urlFor(storedUrl: String, source: SourceEntity?): String =
        source?.let { hosts.forRequest(storedUrl, it) } ?: storedUrl

    /** [url] mapped back to the stored form, for comparisons with stored URLs (AC11). */
    fun canonical(url: String?, source: SourceEntity?): String? =
        if (url == null || source == null) url else HostPool.canonical(url, source)

    /**
     * Watch the item [key] just started on the player. On a server failure it is replayed with
     * [replay] (the URL on the next server, the position reached, and the content key that was on the
     * player — a queue may have moved on to another episode). Calling it again for another item, or
     * [stop], ends the watch.
     */
    fun watch(scope: CoroutineScope, source: SourceEntity?, key: String, replay: suspend (url: String, positionMs: Long, contentKey: String?) -> Unit, storedUrl: String, isOurs: () -> Boolean) {
        job?.cancel()
        val changed = activeKey != key
        activeKey = key
        sameServerReplays = 0
        notices.clear()
        if (source == null || !HostPool.failoverActive(source)) {
            if (changed) job = scope.launch {
                failover.abandonPending()
                failover.reset(key)
            } else failover.reset(key)
            return
        }
        job = scope.launch {
            if (changed) failover.abandonPending()
            failover.reset(key)
            val stillCurrent = { activeKey == key }
            while (stillCurrent()) {
                // The last item's error clears when this load starts; never act on it.
                player.error.first { it == null }
                var leave = false
                val capWhy = coroutineScope {
                    val cap = async { awaitFilmCap() }
                    val watched = async {
                        if (!awaitPlayerGiveUp(source, isOurs)) leave = true
                    }
                    select<String?> {
                        cap.onAwait {
                            watched.cancel()
                            it
                        }
                        watched.onAwait {
                            cap.cancel()
                            null
                        }
                    }
                }
                // Another screen's item (Live, the other VOD screen) failing is not ours to handle.
                if (leave || !stillCurrent() || !isOurs()) return@launch
                val position = player.position.value
                val contentKey = player.currentMediaContentKey
                if (capWhy != null) {
                    if (!onFilmCap(capWhy, source, storedUrl, position, contentKey, stillCurrent, replay)) return@launch
                    continue
                }
                when (val outcome = failover.onFailure(source, player.lastErrorLine, stillCurrent)) {
                    is VodFailover.Outcome.Reopen -> {
                        if (!stillCurrent()) return@launch
                        replay(replayUrl(storedUrl, source, outcome.server), position, contentKey)
                    }
                    is VodFailover.Outcome.Exhausted -> {
                        notices.exhausted(outcome.total)
                        return@launch
                    }
                    VodFailover.Outcome.NotServer, is VodFailover.Outcome.Refused -> return@launch
                }
            }
        }
    }

    /** True when the player gave up on our item. False when the screen moved on before that. */
    private suspend fun awaitPlayerGiveUp(source: SourceEntity, isOurs: () -> Boolean): Boolean {
        // Opened or failed, whichever comes first, judged as Live's mpv watch does (a picture, or the
        // spinner clearing) once this load has started buffering. Not isPlaying: mpv seeds it true.
        kotlinx.coroutines.withTimeoutOrNull(LOAD_START_MS) { player.buffering.first { it } }
        val failed = combine(player.error, player.videoRes, player.buffering) { e, res, buffering ->
            when {
                e != null -> true
                res != null || !buffering -> false
                else -> null
            }
        }.first { it != null } == true
        if (!isOurs()) return false
        if (!failed) {
            failover.played(source)?.let { server ->
                val pool = HostPool.pool(source)
                notices.switched(HostPool.position(pool, server), pool.size, HostPool.display(server))
            }
            // Playing: wait for a later failure (a mid-film outage starts its own round).
            player.error.first { it != null }
        }
        return isOurs()
    }

    /**
     * No picture for [filmCapMs] from this call (the load start), then — once a picture is up — buffering
     * with a frozen position for [filmCapMs]. delay() steps so virtual time advances the cap.
     */
    private suspend fun awaitFilmCap(): String {
        var waited = 0L
        while (player.videoRes.value == null) {
            if (waited >= filmCapMs) return "no picture"
            val step = minOf(CAP_POLL_MS, filmCapMs - waited)
            delay(step)
            waited += step
        }
        var lastPos = player.position.value
        var stalled = 0L
        while (stalled < filmCapMs) {
            val step = minOf(CAP_POLL_MS, filmCapMs - stalled)
            delay(step)
            val pos = player.position.value
            if (player.buffering.value && pos == lastPos) stalled += step
            else {
                stalled = 0L
                lastPos = pos
            }
        }
        return "stalled"
    }

    /** @return false to end the watch. */
    private suspend fun onFilmCap(
        why: String,
        source: SourceEntity,
        storedUrl: String,
        position: Long,
        contentKey: String?,
        stillCurrent: () -> Boolean,
        replay: suspend (url: String, positionMs: Long, contentKey: String?) -> Unit,
    ): Boolean {
        player.stop()
        log("film cap: $why (${filmCapMs}ms)")
        when (val outcome = failover.onFailure(source, null, stillCurrent)) {
            is VodFailover.Outcome.Reopen -> {
                if (!stillCurrent()) return false
                replay(replayUrl(storedUrl, source, outcome.server), position, contentKey)
                return true
            }
            is VodFailover.Outcome.Exhausted -> {
                notices.exhausted(outcome.total)
                player.fail()
                return false
            }
            VodFailover.Outcome.NotServer -> {
                if (sameServerReplays < reconnects()) {
                    sameServerReplays++
                    replay(urlFor(storedUrl, source), position, contentKey)
                    return true
                }
                log("film cap: giving up, the server stopped sending the film")
                player.fail()
                return false
            }
            is VodFailover.Outcome.Refused -> {
                log("film cap: refused ${outcome.detail}")
                player.fail()
                return false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        activeKey = null
        failover.releaseReservation()
    }

    internal companion object {
        private const val TAG = "OwnTVFailover"
        /** How long a load may take to start buffering before its state is read anyway. */
        private const val LOAD_START_MS = 5_000L
        private const val FILM_CAP_MS = 20_000L
        private const val CAP_POLL_MS = 1_000L

        internal fun replayUrl(storedUrl: String, source: SourceEntity, server: String): String =
            HostPool.forRequest(storedUrl, source, server)

        private fun reserveVia(openStreams: OpenStreamRegistry): (Long) -> (() -> Unit) = { id ->
            val c = openStreams.claim(id, StreamPurpose.WATCHING)
            val release: () -> Unit = { openStreams.release(c) }
            release
        }
    }
}
