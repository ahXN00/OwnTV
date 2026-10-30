package tv.own.owntv.features.automation

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A random value made once per process, carried only by OwnTV's own API starts ([AppLauncher.EXTRA_WAKE]).
 * MainActivity is exported, so any app could ask it to wake the TV; [AutomationWake] wakes it only for a
 * value that [matches], which no other app can know.
 */
class WakeNonce(random: SecureRandom = SecureRandom()) {
    val value: String = buildString {
        ByteArray(SIZE).also(random::nextBytes).forEach { append((it.toInt() and 0xff).toString(16).padStart(2, '0')) }
    }

    /** True when [candidate] is this process's value; compared in constant time. */
    fun matches(candidate: String?): Boolean =
        candidate != null && MessageDigest.isEqual(candidate.toByteArray(), value.toByteArray())

    private companion object {
        const val SIZE = 16
    }
}
