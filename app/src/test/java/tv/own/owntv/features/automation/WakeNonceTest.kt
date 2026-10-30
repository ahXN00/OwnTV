package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeNonceTest {
    private val nonce = WakeNonce()

    @Test
    fun `only this process's value wakes the TV`() {
        assertTrue(nonce.matches(nonce.value))
        assertFalse(nonce.matches(null))
        assertFalse(nonce.matches(""))
        assertFalse(nonce.matches("true"))
        assertFalse(nonce.matches(nonce.value.dropLast(1)))
        assertFalse(nonce.matches(WakeNonce().value))
    }

    @Test
    fun `the value is 16 random bytes in hex, new for every process`() {
        assertEquals(32, nonce.value.length)
        assertTrue(nonce.value.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(nonce.value, WakeNonce().value)
    }
}
