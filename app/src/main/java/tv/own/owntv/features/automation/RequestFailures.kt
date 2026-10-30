package tv.own.owntv.features.automation

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.IOException

/**
 * What the server answers when handling a request throws. Requests run under the server's job, a child
 * of the app-wide scope's ([ServerRequests]), where anything escaping a request would still end the
 * whole app, not one call. So every [Throwable] becomes a 500, [Error]s included: a [StackOverflowError]
 * from a deeply nested body or an [OutOfMemoryError] from a large reply must cost the caller its answer,
 * not cost the user the app. A connection that drops ([IOException]) gets no answer, as there is nobody
 * left to read it. Cancellation passes through only when the request itself is being cancelled, as
 * [AutomationServer.stop] does to every request still running; a stray [CancellationException] from
 * deeper down, such as a timeout inside a route, is a failure like any other.
 */
object RequestFailures {
    /** Built before anything fails, so answering an [OutOfMemoryError] needs no new JSON. */
    private val INTERNAL_ERROR = HttpResponse(500, JSONObject().put("error", "Internal error").toString())

    /** [handle]'s response; [INTERNAL_ERROR] when it throws, or null when the caller hung up. */
    suspend fun answer(handle: suspend () -> HttpResponse?): HttpResponse? = try {
        handle()
    } catch (e: IOException) {
        null
    } catch (c: CancellationException) {
        currentCoroutineContext().ensureActive()
        failed(c)
    } catch (t: Throwable) {
        failed(t)
    }

    private fun failed(t: Throwable): HttpResponse {
        runCatching { Log.w(TAG, "request failed", t) }
        return INTERNAL_ERROR
    }

    private const val TAG = "OwnTVAutomation"
}
