package tv.own.owntv.features.automation

/**
 * Counts failed attempts per address in a sliding window. An address that reaches [maxFailures]
 * within [windowMs] is refused outright until its oldest failure ages out, so a script on the LAN
 * cannot grind through keys or PINs.
 *
 * The table must not grow with every address that ever failed: entries whose failures have all aged
 * out are dropped on each use, and past [maxAddresses] the address whose last failure is oldest makes
 * room. Checking an address never adds it.
 */
class FailureLimiter(
    private val maxFailures: Int = 10,
    private val windowMs: Long = 10 * 60_000L,
    private val maxAddresses: Int = 1024,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Ordered by last failure, oldest first, so the head is the entry to drop when full. */
    private val failures = LinkedHashMap<String, ArrayDeque<Long>>()

    /** How many addresses have failures on record, for tests. */
    internal val tracked: Int
        @Synchronized get() = failures.size

    @Synchronized
    fun isBlocked(key: String): Boolean {
        dropExpired()
        return (failures[key]?.size ?: 0) >= maxFailures
    }

    @Synchronized
    fun recordFailure(key: String) {
        dropExpired()
        val recent = failures.remove(key) ?: ArrayDeque()
        recent.addLast(clock())
        while (recent.size > maxFailures) recent.removeFirst()
        if (failures.size >= maxAddresses) failures.remove(failures.keys.first())
        failures[key] = recent
    }

    @Synchronized
    fun clear(key: String) {
        failures.remove(key)
    }

    private fun dropExpired() {
        val cutoff = clock() - windowMs
        val entries = failures.values.iterator()
        while (entries.hasNext()) {
            val recent = entries.next()
            while (recent.isNotEmpty() && recent.first() <= cutoff) recent.removeFirst()
            if (recent.isEmpty()) entries.remove()
        }
    }
}
