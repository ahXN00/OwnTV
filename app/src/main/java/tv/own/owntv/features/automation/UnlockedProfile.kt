package tv.own.owntv.features.automation

import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.profile.profileGateRequired
import tv.own.owntv.core.profile.shellMayCompose
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The profile the TV has let in, for the API. While OwnTV asks who is watching, or for a profile's PIN,
 * the saved active profile is not open, so the API must not list or play its catalog. The shell
 * publishes the profile this activity session unlocked ([publish]) while it is on screen, and null once
 * it leaves; [admits] decides by the rule that lets the shell itself show ([shellMayCompose]). That rule
 * lets the only profile, with no PIN, in without asking, yet switch profile on the rail still shows
 * "Who's watching?" for it, so the gate also marks itself up ([gateUp]) and closes every profile while it is.
 */
class UnlockedProfile {
    @Volatile
    private var authenticated: Long? = null
    private val gates = AtomicInteger(0)

    /** The profile the shell on screen was unlocked for, or null when no shell is up. */
    fun publish(profileId: Long?) {
        authenticated = profileId
    }

    /** Marks "Who's watching?" up until the returned handle is closed; closing it twice counts once. */
    fun gateUp(): AutoCloseable {
        gates.incrementAndGet()
        val closed = AtomicBoolean(false)
        return AutoCloseable { if (closed.compareAndSet(false, true)) gates.decrementAndGet() }
    }

    /**
     * True when [savedId] is open on the TV: "Who's watching?" is not up, and it is one of [profiles] and
     * unlocked, or the only profile, with no PIN, which OwnTV opens without asking.
     */
    fun admits(savedId: Long?, profiles: List<ProfileEntity>): Boolean =
        gates.get() == 0 && shellMayCompose(profiles, savedId, authenticated, profileGateRequired(profiles))
}
