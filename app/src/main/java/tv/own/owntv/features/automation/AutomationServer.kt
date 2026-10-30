package tv.own.owntv.features.automation

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Semaphore

/**
 * The automation API's listener: plain HTTP on [PORT], every interface (home network and Tailscale),
 * one request per connection, [MAX_CONNECTIONS] connections at a time and at most [MAX_PER_CALLER]
 * from one caller ([ConnectionLimiter]: an IPv4 address or an IPv6 /64). Callers from outside the
 * home network (or Tailscale, when [allowTailscale]), home-range callers a VPN tunnel carried in among
 * them, are closed before anything is read ([PeerFilter]). A request's head must arrive within
 * [HEAD_DEADLINE_MS] and the whole request within [REQUEST_DEADLINE_MS], and the response is written
 * within [WRITE_DEADLINE_MS] ([writeWithin]), so nobody can hold the slots by trickling bytes or by not
 * reading. Whatever a request throws becomes a 500 ([RequestFailures]), and a failed accept is logged
 * and skipped ([AcceptLoop]), never a crash. [stop] cancels the requests still running ([ServerRequests]).
 * Keys are the routes' job ([AutomationGate]); they check them before reading a body ([AutomationRoutes.answer]).
 */
class AutomationServer(
    private val routes: AutomationRoutes,
    private val scope: CoroutineScope,
    private val allowTailscale: () -> Boolean,
) {
    sealed interface State {
        data object Stopped : State
        data class Listening(val urls: List<String>) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state.asStateFlow()
    private var socket: ServerSocket? = null
    private var requests: ServerRequests? = null
    private val slots = Semaphore(MAX_CONNECTIONS)
    private val perCaller = ConnectionLimiter(MAX_PER_CALLER)

    @Synchronized
    fun start() {
        if (socket != null) return
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }
        } catch (e: IOException) {
            Log.w(TAG, "bind $PORT failed", e)
            _state.value = State.Failed(e.message ?: "Port $PORT is in use")
            return
        }
        socket = server
        val running = ServerRequests(scope).also { requests = it }
        _state.value = State.Listening(lanUrls())
        scope.launch(Dispatchers.IO) { AcceptLoop.run(server) { handOff(it, running) } }
    }

    /** Closes the listener and cancels every request still running ([ServerRequests]). */
    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
        requests?.cancel()
        requests = null
        _state.value = State.Stopped
    }

    /** Serves [client] as one of [running] once [admit] gives it a slot; otherwise closes it. */
    private fun handOff(client: Socket, running: ServerRequests) {
        val tailscale = allowTailscale()
        val peer = peerOf(client, tailscale)
        if (!admit(peer, tailscale)) {
            runCatching { client.close() }
            return
        }
        running.launch(serve = { serve(client, peer) }) {
            // Also when the request was cancelled before it started, and so never closed its client.
            runCatching { client.close() }
            slots.release()
            perCaller.release(peer.address)
        }
    }

    /** Takes a slot for [peer], or returns false when it may not connect or has no slot left. */
    private fun admit(peer: Peer, tailscale: Boolean): Boolean {
        if (!PeerFilter.admits(peer, tailscale)) return false
        if (!perCaller.tryAcquire(peer.address)) return false
        if (slots.tryAcquire()) return true
        perCaller.release(peer.address)
        return false
    }

    /** Answers one request. Throws nothing but its own cancellation; closing can fail too, so it is not `use`. */
    private suspend fun serve(client: Socket, peer: Peer) {
        try {
            val response = RequestFailures.answer { respond(client, peer) } ?: return
            runCatching { writeWithin(client, WRITE_DEADLINE_MS) { AutomationHttp.writeResponse(client.getOutputStream(), response) } }
        } finally {
            runCatching { client.close() }
        }
    }

    /** Reads [socket]'s request and routes it; null when the caller sent no request. */
    private suspend fun respond(socket: Socket, peer: Peer): HttpResponse? {
        val input = DeadlineInputStream(
            socket.getInputStream(), REQUEST_DEADLINE_MS, { socket.soTimeout = it }, headBudgetMs = HEAD_DEADLINE_MS,
        )
        return routes.answer(BufferedInputStream(input), peer, onHead = input::headDone)
    }

    /**
     * The caller, and whether it came in through a tunnel or Tailscale's. The interface is looked up only
     * when that can matter: for a home-range caller, or a tailnet one while Tailscale is allowed.
     */
    private fun peerOf(socket: Socket, tailscale: Boolean): Peer {
        val remote = socket.inetAddress
        if (!PeerFilter.isLocal(remote) && !(tailscale && AutomationAccess.isTailscale(remote))) return Peer(remote)
        val arrival = TailscaleInterface.arrival(socket.localAddress)
        return Peer(remote, onTailscaleInterface = arrival.onTailscale, onTunnel = arrival.onTunnel)
    }

    private fun lanUrls(): List<String> {
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrNull().orEmpty()
        return interfaces.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .map { "http://${it.hostAddress}:$PORT" }
    }

    companion object {
        const val PORT = 8090
        private const val MAX_CONNECTIONS = 16
        private const val MAX_PER_CALLER = 2
        private const val REQUEST_DEADLINE_MS = 15_000L
        private const val HEAD_DEADLINE_MS = 5_000L
        private const val WRITE_DEADLINE_MS = 15_000L
        private const val TAG = "OwnTVAutomation"
    }
}
