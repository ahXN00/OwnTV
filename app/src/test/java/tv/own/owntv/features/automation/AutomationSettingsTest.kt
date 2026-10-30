package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationSettingsTest {
    private val device = PairedDevice(id = "laptop", name = "Laptop", address = "", secretHash = "", pairedAt = 0L)

    @Test
    fun `a setup visit the last process left open is over, and the API it turned on starts off`() {
        val left = AutomationSettings(enabled = true, devices = listOf(device), offBeforeSetup = true)
        assertEquals(AutomationSettings(enabled = false, devices = listOf(device)), left.restarted())
    }

    @Test
    fun `without a visit left open the API starts as the user left it`() {
        val on = AutomationSettings(enabled = true, devices = listOf(device))
        assertEquals(on, on.restarted())
        assertEquals(AutomationSettings(), AutomationSettings().restarted())
    }
}
