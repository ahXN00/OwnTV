package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.PlayerRemoteOptions

class PlayerChannelKeysTest {
    @Test
    fun byDefaultUpIsTheNextChannel() {
        assertEquals(1, zapDirection(up = true, PlayerRemoteOptions()))
        assertEquals(-1, zapDirection(up = false, PlayerRemoteOptions()))
    }

    @Test
    fun swappingFlipsBothKeys() {
        val swapped = PlayerRemoteOptions(swapChannelKeys = true)
        assertEquals(-1, zapDirection(up = true, swapped))
        assertEquals(1, zapDirection(up = false, swapped))
    }

    @Test
    fun numberKeysTuneOnlyWhenBothSettingsAreOn() {
        assertTrue(numberKeysTune(channelNumbersOn = true, PlayerRemoteOptions()))
        assertFalse(numberKeysTune(channelNumbersOn = true, PlayerRemoteOptions(numberKeysTune = false)))
        assertFalse(numberKeysTune(channelNumbersOn = false, PlayerRemoteOptions()))
        assertFalse(numberKeysTune(channelNumbersOn = false, PlayerRemoteOptions(numberKeysTune = false)))
    }
}
