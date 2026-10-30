package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.database.entity.ProfileEntity

class ProfileChoiceTest {
    private var now = 1_000L
    private val choice = ProfileChoice { now }
    private val family = ProfileEntity(id = 2, name = "Family", avatarColor = 0)
    private val locked = ProfileEntity(id = 3, name = "Parents", avatarColor = 0, pinHash = "hash")

    @Test
    fun `the gate takes the chosen profile once`() {
        choice.choose(2)
        val request = checkNotNull(choice.pending.value)
        assertEquals(family, choice.take(request, listOf(locked, family)))
        assertNull(choice.pending.value)
        assertNull(choice.take(request, listOf(locked, family)))
    }

    @Test
    fun `a profile the list does not hold yet stays pending`() {
        choice.choose(2)
        val request = checkNotNull(choice.pending.value)
        assertNull(choice.take(request, listOf(locked)))
        assertEquals(request, choice.pending.value)
    }

    @Test
    fun `a profile with a PIN is never taken`() {
        choice.choose(3)
        assertNull(choice.take(checkNotNull(choice.pending.value), listOf(locked)))
    }

    @Test
    fun `a stale choice is dropped instead of taken`() {
        choice.choose(2)
        val request = checkNotNull(choice.pending.value)
        now += ProfileChoice.WAIT_MS
        assertNull(choice.take(request, listOf(family)))
        assertNull(choice.pending.value)
    }

    @Test
    fun `taking an older choice leaves a newer one pending`() {
        choice.choose(2)
        val older = checkNotNull(choice.pending.value)
        now += 10
        choice.choose(2)
        assertNull(choice.take(older, listOf(family)))
        assertEquals(2L, choice.pending.value?.profileId)
    }
}
