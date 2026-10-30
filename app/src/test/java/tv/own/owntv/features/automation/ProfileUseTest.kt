package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.ProfileEntity

class ProfileUseTest {
    private val family = ProfileEntity(id = 2, name = "Family", avatarColor = 0)
    private val locked = ProfileEntity(id = 3, name = "Parents", avatarColor = 0, pinHash = "hash")

    @Test
    fun `an unknown profile is not found`() {
        assertEquals(ProfileUse.NOT_FOUND, ProfileUse.of(null, setup = SetupAccess.DONE, openId = 1, privateOpen = false))
    }

    @Test
    fun `a profile with a PIN is refused, during setup too`() {
        assertEquals(ProfileUse.LOCKED, ProfileUse.of(locked, setup = SetupAccess.DONE, openId = 1, privateOpen = false))
        assertEquals(ProfileUse.LOCKED, ProfileUse.of(locked, setup = SetupAccess.OPEN, openId = null, privateOpen = true))
    }

    @Test
    fun `during setup it only chooses the profile setup opens, though the setup page is private`() {
        assertEquals(ProfileUse.CHOSEN, ProfileUse.of(family, setup = SetupAccess.OPEN, openId = null, privateOpen = true))
    }

    @Test
    fun `during setup without a device paired from the setup page it is refused`() {
        for (privateOpen in listOf(false, true)) {
            val use = ProfileUse.of(family, setup = SetupAccess.CLOSED, openId = null, privateOpen = privateOpen)
            assertEquals(ProfileUse.SETUP_CLOSED, use)
        }
    }

    @Test
    fun `after setup a private screen refuses the switch`() {
        assertEquals(ProfileUse.PRIVATE, ProfileUse.of(family, setup = SetupAccess.DONE, openId = 1, privateOpen = true))
        assertEquals(ProfileUse.PRIVATE, ProfileUse.of(family, setup = SetupAccess.DONE, openId = 2, privateOpen = true))
    }

    @Test
    fun `the open profile is already active, and another one switches`() {
        assertEquals(ProfileUse.ACTIVE, ProfileUse.of(family, setup = SetupAccess.DONE, openId = 2, privateOpen = false))
        assertEquals(ProfileUse.SWITCHING, ProfileUse.of(family, setup = SetupAccess.DONE, openId = 1, privateOpen = false))
        assertEquals(ProfileUse.SWITCHING, ProfileUse.of(family, setup = SetupAccess.DONE, openId = null, privateOpen = false))
    }
}
