package tv.own.owntv.features.automation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ServerRequestsTest {
    private val app = CoroutineScope(SupervisorJob())
    private val requests = ServerRequests(app)

    /** Runs [check] and then cancels the stand-in app scope, also when [check] fails. */
    private fun withApp(check: suspend () -> Unit) = runBlocking {
        try {
            withTimeout(5_000) { check() }
        } finally {
            app.cancel()
        }
    }

    @Test
    fun `stopping cancels a request that is still running and gives its slot back`() = withApp {
        val started = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val finished = AtomicInteger()
        requests.launch(serve = {
            started.complete(Unit)
            repeat(50) {
                delay(1_000)
                finished.incrementAndGet()
            }
        }) { done.complete(Unit) }
        started.await()
        requests.cancel()
        done.await()
        assertEquals(0, finished.get())
        assertTrue(app.isActive)
    }

    @Test
    fun `a request handed over after stopping never runs but still gives its slot back`() = withApp {
        requests.cancel()
        val done = CompletableDeferred<Unit>()
        var ran = false
        requests.launch(serve = { ran = true }) { done.complete(Unit) }
        done.await()
        assertFalse(ran)
    }

    @Test
    fun `a request that ends on its own gives its slot back once`() = withApp {
        val done = AtomicInteger()
        val over = CompletableDeferred<Unit>()
        requests.launch(serve = {}) {
            done.incrementAndGet()
            over.complete(Unit)
        }
        over.await()
        requests.cancel()
        delay(50)
        assertEquals(1, done.get())
    }
}
