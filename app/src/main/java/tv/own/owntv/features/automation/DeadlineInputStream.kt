package tv.own.owntv.features.automation

import java.io.FilterInputStream
import java.io.InputStream
import java.net.SocketTimeoutException

/**
 * Reads [input] until a time budget, counted from creation, runs out. A socket read timeout alone
 * is per read: a client that sends one byte just before each timeout can hold a connection for
 * hours. Here every read may wait only for what is left of the budget, handed to [setTimeout] (the
 * socket's read timeout) first.
 *
 * The request head gets a shorter budget of its own, [headBudgetMs]: a script sends its few header
 * lines at once, so a caller still sending them after a few seconds is only holding a slot. Once
 * [headDone] is called, the body may use what is left of the whole [budgetMs].
 */
class DeadlineInputStream(
    input: InputStream,
    budgetMs: Long,
    private val setTimeout: (Int) -> Unit,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    headBudgetMs: Long = budgetMs,
) : FilterInputStream(input) {
    private val start = clock()
    private val end = start + budgetMs
    private var deadline = minOf(end, start + headBudgetMs)

    /** The head is in: from now on reads may run to the end of the whole budget. */
    fun headDone() {
        deadline = end
    }

    override fun read(): Int {
        arm()
        return super.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        arm()
        return super.read(b, off, len)
    }

    override fun skip(n: Long): Long {
        arm()
        return super.skip(n)
    }

    private fun arm() {
        val left = deadline - clock()
        if (left <= 0) throw SocketTimeoutException()
        setTimeout(left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
