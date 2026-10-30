package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupAccessTest {
    @Test
    fun `setup is open only to a device paired from the setup page, before first run is over`() {
        assertEquals(SetupAccess.OPEN, SetupAccess.of(done = false, paired = true))
    }

    @Test
    fun `without a device paired from the open setup page it is closed`() {
        assertEquals(SetupAccess.CLOSED, SetupAccess.of(done = false, paired = false))
    }

    @Test
    fun `once first run is over it is done, paired or not`() {
        assertEquals(SetupAccess.DONE, SetupAccess.of(done = true, paired = true))
        assertEquals(SetupAccess.DONE, SetupAccess.of(done = true, paired = false))
    }
}
