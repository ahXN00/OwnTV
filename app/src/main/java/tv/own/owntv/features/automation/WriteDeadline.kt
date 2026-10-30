package tv.own.owntv.features.automation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * Runs [write], closing [connection] if it has not finished within [budgetMs]. A socket's read
 * timeout does not cover writes: a caller that stops reading fills the send buffer, and the next
 * write then blocks for as long as the caller likes, holding a slot. Closing the socket from a
 * second coroutine makes that blocked write fail at once. The guard runs on [Dispatchers.IO], so a
 * caller on a single thread cannot starve it while [write] blocks.
 */
suspend fun <T> writeWithin(connection: Closeable, budgetMs: Long, write: () -> T): T = coroutineScope {
    val guard = launch(Dispatchers.IO) {
        delay(budgetMs)
        runCatching { connection.close() }
    }
    try {
        write()
    } finally {
        guard.cancel()
    }
}
