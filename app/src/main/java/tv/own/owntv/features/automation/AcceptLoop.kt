package tv.own.owntv.features.automation

import android.util.Log
import kotlinx.coroutines.delay
import java.net.ServerSocket
import java.net.Socket

/**
 * The listener's accept loop. It runs on the app-wide scope, where anything escaping would end the
 * whole app, so nothing that accepting or handing off one connection throws may leave it. A failure is
 * logged by its type only (an exception message can carry a caller's address) and the loop goes on.
 * Only a closed server socket ([AutomationServer.stop], seen through [ServerSocket.isClosed]) ends the
 * loop. Any other accept failure, an I/O one included, is waited out and retried: running out of file
 * descriptors or buffers, or a caller that gives up mid-handshake, arrives as a [java.net.SocketException]
 * while the socket is still open, and ending the loop then would leave the port open with nobody answering.
 */
internal object AcceptLoop {
    /** Accepts until [server] closes and gives each client to [handOff]; a client [handOff] throws on is closed. */
    suspend fun run(server: ServerSocket, handOff: (Socket) -> Unit) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (t: Throwable) {
                if (server.isClosed) break
                runCatching { Log.w(TAG, "accept failed: ${t.javaClass.name}") }
                // A failure that repeats on every call must not spin a core or flood the log.
                delay(RETRY_MS)
                continue
            }
            try {
                handOff(client)
            } catch (t: Throwable) {
                runCatching { Log.w(TAG, "connection setup failed: ${t.javaClass.name}") }
                runCatching { client.close() }
            }
        }
    }

    private const val RETRY_MS = 500L
    private const val TAG = "OwnTVAutomation"
}
