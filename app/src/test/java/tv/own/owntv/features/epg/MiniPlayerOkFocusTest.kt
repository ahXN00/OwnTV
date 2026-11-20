package tv.own.owntv.features.epg

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerOkFocusTest {

    @Test
    fun `focuses mini player when mini player active and playing channel matches focused channel`() {
        var focused = false
        val onFocus = { focused = true }

        val result = shouldFocusMiniPlayerOnOk(
            isMiniPlayer = true,
            onFocusMiniPlayer = onFocus,
            channelId = 101L,
            playingChannelId = 101L,
        )

        assertTrue(result)
        onFocus()
        assertTrue(focused)
    }

    @Test
    fun `does not focus mini player when mini player is not active`() {
        val result = shouldFocusMiniPlayerOnOk(
            isMiniPlayer = false,
            onFocusMiniPlayer = {},
            channelId = 101L,
            playingChannelId = 101L,
        )

        assertFalse(result)
    }

    @Test
    fun `does not focus mini player when playing channel is different`() {
        val result = shouldFocusMiniPlayerOnOk(
            isMiniPlayer = true,
            onFocusMiniPlayer = {},
            channelId = 102L,
            playingChannelId = 101L,
        )

        assertFalse(result)
    }

    @Test
    fun `does not focus mini player when handler is null`() {
        val result = shouldFocusMiniPlayerOnOk(
            isMiniPlayer = true,
            onFocusMiniPlayer = null,
            channelId = 101L,
            playingChannelId = 101L,
        )

        assertFalse(result)
    }

    @Test
    fun `does not focus mini player when playing channel id is null`() {
        val result = shouldFocusMiniPlayerOnOk(
            isMiniPlayer = true,
            onFocusMiniPlayer = {},
            channelId = 101L,
            playingChannelId = null,
        )

        assertFalse(result)
    }
}
