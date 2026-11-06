package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewOnOkTest {

    @Test
    fun `switch off always watches`() {
        assertEquals(OkAction.WATCH, okAction(twoStep = false, paneCanPlay = true, paneChannelId = null, okChannelId = 1))
        assertEquals(OkAction.WATCH, okAction(twoStep = false, paneCanPlay = true, paneChannelId = 2, okChannelId = 1))
        assertEquals(OkAction.WATCH, okAction(twoStep = false, paneCanPlay = true, paneChannelId = 1, okChannelId = 1))
    }

    @Test
    fun `no pane to play in always watches`() {
        assertEquals(OkAction.WATCH, okAction(twoStep = true, paneCanPlay = false, paneChannelId = null, okChannelId = 1))
        assertEquals(OkAction.WATCH, okAction(twoStep = true, paneCanPlay = false, paneChannelId = 2, okChannelId = 1))
    }

    @Test
    fun `first OK previews, OK on the channel in the pane watches`() {
        assertEquals(OkAction.PREVIEW, okAction(twoStep = true, paneCanPlay = true, paneChannelId = null, okChannelId = 1))
        assertEquals(OkAction.PREVIEW, okAction(twoStep = true, paneCanPlay = true, paneChannelId = 2, okChannelId = 1))
        assertEquals(OkAction.WATCH, okAction(twoStep = true, paneCanPlay = true, paneChannelId = 1, okChannelId = 1))
    }
}
