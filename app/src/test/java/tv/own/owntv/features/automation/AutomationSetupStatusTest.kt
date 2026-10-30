package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationSetupStatusTest {
    private val listening = AutomationServer.State.Listening(listOf("http://192.0.2.10:8090"))
    private val open = PairingWindow.Open(pin = "123456", expiresAt = 1_000L)
    private val old = device("old", "Laptop")

    @Test
    fun `before the page starts it reads as starting`() {
        assertEquals(AutomationSetupStatus.Starting, AutomationSetupStatus.of(null, listOf(old), open, listening))
    }

    @Test
    fun `listening with the window open shows the PIN and the address`() {
        val status = AutomationSetupStatus.of(setOf("old"), listOf(old), open, listening)
        assertEquals(AutomationSetupStatus.Waiting(open, listening.urls), status)
    }

    @Test
    fun `the server still coming up reads as starting`() {
        assertEquals(AutomationSetupStatus.Starting, AutomationSetupStatus.of(setOf("old"), listOf(old), open, AutomationServer.State.Stopped))
    }

    @Test
    fun `a closed window with nothing new paired has expired`() {
        assertEquals(AutomationSetupStatus.Expired, AutomationSetupStatus.of(setOf("old"), listOf(old), null, listening))
    }

    @Test
    fun `a server that could not start says why`() {
        val failed = AutomationServer.State.Failed("port in use")
        assertEquals(AutomationSetupStatus.Failed("port in use"), AutomationSetupStatus.of(emptySet(), emptyList(), open, failed))
    }

    @Test
    fun `a device paired since the page opened wins, though its PIN has closed`() {
        val status = AutomationSetupStatus.of(setOf("old"), listOf(old, device("new", "NAS")), null, listening)
        assertEquals(AutomationSetupStatus.Paired("NAS"), status)
    }

    @Test
    fun `a device paired before the page opened is not this page's pairing`() {
        assertEquals(AutomationSetupStatus.Waiting(open, listening.urls), AutomationSetupStatus.of(setOf("old"), listOf(old), open, listening))
    }

    private fun device(id: String, name: String) = PairedDevice(id, name, "192.0.2.20", "hash", 0L)
}
