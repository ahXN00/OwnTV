package tv.own.owntv.features.automation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RequestFailuresTest {
    private fun answer(handle: suspend () -> HttpResponse?) = runBlocking { RequestFailures.answer(handle) }

    @Test
    fun `errors that would end the app answer 500 instead`() {
        listOf(StackOverflowError(), OutOfMemoryError("Java heap space"), NoClassDefFoundError("x")).forEach { error ->
            val response = answer { throw error }
            assertEquals(500, response?.status)
            assertTrue(response?.body.orEmpty().contains("Internal error"))
        }
        assertEquals(500, answer { throw IllegalStateException("bug") }?.status)
    }

    @Test
    fun `a stray cancellation from inside a route is a failure like any other`() {
        assertEquals(500, answer { throw CancellationException("route timed out") }?.status)
    }

    @Test
    fun `a dropped connection gets no answer and a good one passes through`() {
        assertNull(answer { throw IOException("Connection reset") })
        assertEquals(HttpResponse(200, "{}"), answer { HttpResponse(200, "{}") })
    }

    @Test
    fun `a request being cancelled stays cancelled`() {
        var answered = false
        runBlocking {
            val request = launch {
                RequestFailures.answer {
                    currentCoroutineContext().job.cancel()
                    yield()
                    HttpResponse(200, "{}")
                }
                answered = true
            }
            request.join()
            assertTrue(request.isCancelled)
        }
        assertFalse(answered)
    }
}
