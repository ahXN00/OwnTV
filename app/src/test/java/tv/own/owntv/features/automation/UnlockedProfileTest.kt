package tv.own.owntv.features.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ProfileEntity

class UnlockedProfileTest {
    private val unlocked = UnlockedProfile()
    private val open = profile(1)
    private val kids = profile(2)
    private val locked = profile(3, pinHash = "hash")

    @Test
    fun `the only profile, with no PIN, is open without being unlocked`() {
        assertTrue(unlocked.admits(1, listOf(open)))
    }

    @Test
    fun `with several profiles the saved one is closed until the TV unlocks it`() {
        assertFalse(unlocked.admits(1, listOf(open, kids)))
        unlocked.publish(1)
        assertTrue(unlocked.admits(1, listOf(open, kids)))
        assertFalse(unlocked.admits(2, listOf(open, kids)))
    }

    @Test
    fun `a profile with a PIN is closed until it is unlocked`() {
        assertFalse(unlocked.admits(3, listOf(locked)))
        unlocked.publish(3)
        assertTrue(unlocked.admits(3, listOf(locked)))
    }

    @Test
    fun `when the shell leaves the profile closes again`() {
        unlocked.publish(1)
        unlocked.publish(null)
        assertFalse(unlocked.admits(1, listOf(open, kids)))
    }

    @Test
    fun `while who's watching is up no profile is open, not even the only one`() {
        val gate = unlocked.gateUp()
        assertFalse(unlocked.admits(1, listOf(open)))
        gate.close()
        assertTrue(unlocked.admits(1, listOf(open)))
    }

    @Test
    fun `switch profile closes the unlocked profile until the gate is gone`() {
        unlocked.publish(1)
        unlocked.publish(null)
        val gate = unlocked.gateUp()
        val replaced = unlocked.gateUp()
        gate.close()
        gate.close()
        assertFalse(unlocked.admits(1, listOf(open)))
        replaced.close()
        unlocked.publish(1)
        assertTrue(unlocked.admits(1, listOf(open, kids)))
    }

    @Test
    fun `no saved profile, or one that is gone, is never open`() {
        unlocked.publish(1)
        assertFalse(unlocked.admits(null, listOf(open)))
        assertFalse(unlocked.admits(-1, listOf(open)))
        assertFalse(unlocked.admits(1, listOf(kids, locked)))
    }

    private fun profile(id: Long, pinHash: String? = null) =
        ProfileEntity(id = id, name = "Viewer $id", avatarColor = 0, isKids = false, pinHash = pinHash)
}
