package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class ChannelJsonTest {
    private fun channel(logoUrl: String?) =
        ChannelEntity(sourceId = 1, name = "C", logoUrl = logoUrl, streamUrl = "http://tv.example.com/1.ts")

    private fun logo(url: String): String = ChannelJson.of(channel(url)).getString("logoUrl")

    @Test
    fun `a logo shows only where it points, wherever the login sits in it`() {
        assertEquals("http://logos.example.com/…", logo("http://logos.example.com/me/s3cret/a.png"))
        assertEquals("http://logos.example.com/…", logo("http://logos.example.com/a.png?v=s3cret"))
        assertEquals("http://logos.example.com:8080/…", logo("http://me:pw@logos.example.com:8080/s3cret.png"))
    }

    @Test
    fun `logos on one host read back the same whatever follows the host`() {
        listOf(
            "http://logos.example.com/${"0123456789".repeat(40)}.png",
            "http://logos.example.com/00:1A:79:12:34:56.png",
            "http://logos.example.com/a.png",
        ).forEach { assertEquals("http://logos.example.com/…", logo(it)) }
    }

    @Test
    fun `a channel without a logo shows none`() {
        assertTrue(ChannelJson.of(channel(null)).isNull("logoUrl"))
    }
}
