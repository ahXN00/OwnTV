package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.database.entity.ProfileEntity

class SetupProfileTest {
    private val locked = ProfileEntity(id = 1, name = "Parents", avatarColor = 0, pinHash = "hash")
    private val family = ProfileEntity(id = 2, name = "Family", avatarColor = 0)
    private val kids = ProfileEntity(id = 3, name = "Kids", avatarColor = 0, isKids = true)

    @Test
    fun `with nothing chosen setup opens the oldest profile without a PIN`() {
        assertEquals(2L, SetupProfile.pick(listOf(locked, family, kids), chosen = null))
    }

    @Test
    fun `the chosen profile wins`() {
        assertEquals(3L, SetupProfile.pick(listOf(locked, family, kids), chosen = 3))
    }

    @Test
    fun `a chosen profile that is gone or has a PIN falls back to the oldest without one`() {
        assertEquals(2L, SetupProfile.pick(listOf(locked, family, kids), chosen = 9))
        assertEquals(2L, SetupProfile.pick(listOf(locked, family, kids), chosen = 1))
    }

    @Test
    fun `no profile, or only ones with a PIN, leaves setup nothing to open`() {
        assertNull(SetupProfile.pick(emptyList(), chosen = null))
        assertNull(SetupProfile.pick(listOf(locked), chosen = 1))
    }
}
