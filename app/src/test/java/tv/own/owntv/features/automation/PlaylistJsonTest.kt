package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.work.CatalogSyncState

class PlaylistJsonTest {
    private fun json(source: SourceEntity): JSONObject = PlaylistJson.of(
        source = source, counts = SyncCounts(0, 0, 0, 0), isDefault = false,
        refresh = PlaylistRefresh.OFF, sync = CatalogSyncState.Idle,
    )

    @Test
    fun `each address shows only where it points, wherever the login sits in it`() {
        val source = SourceEntity(
            name = "P", type = SourceType.M3U, url = "http://lists.example.com:8080/playlist/me/s3cret/m3u_plus",
            username = "me", password = "s3cret", epgUrl = "http://me:s3cret@lists.example.com/xmltv.php?u=me&p=s3cret",
            httpReferer = "https://lists.example.com/embed?passwd=s3cret",
        )
        val json = json(source)
        assertEquals("http://lists.example.com:8080/…", json.getString("url"))
        assertEquals("http://lists.example.com/…", json.getString("epgUrl"))
        assertEquals("https://lists.example.com/…", json.getString("httpReferer"))
        assertTrue(json.getBoolean("hasPassword"))
        assertFalse(json.toString().contains("s3cret"))
    }

    @Test
    fun `a password in the address still counts as one`() {
        val source = SourceEntity(name = "P", type = SourceType.M3U, url = "http://lists.example.com/get.php?username=me&password=s3cret")
        assertTrue(json(source).getBoolean("hasPassword"))
        assertEquals("http://lists.example.com/…", json(source).getString("url"))
    }

    @Test
    fun `a user name in the address is not a password`() {
        val source = SourceEntity(name = "P", type = SourceType.M3U, url = "http://lists.example.com/get.php?username=me&metadata=1")
        assertFalse(json(source).getBoolean("hasPassword"))
        assertTrue(json(source.copy(password = "s3cret")).getBoolean("hasPassword"))
    }

    @Test
    fun `addresses read back the same whatever login is stored`() {
        val digits = "0123456789".repeat(40)
        fun shown(password: String?) = json(
            SourceEntity(
                name = "P", type = SourceType.XTREAM, url = "http://lists.example.com/$digits/list.m3u", username = "me",
                password = password, epgUrl = "http://lists.example.com/epg/$digits.xml",
            ),
        )
        val holding = shown("345678")
        assertEquals("http://lists.example.com/…", holding.getString("url"))
        assertEquals("http://lists.example.com/…", holding.getString("epgUrl"))
        listOf(shown("not-in-it"), shown(null)).forEach { other ->
            assertEquals(other.getString("url"), holding.getString("url"))
            assertEquals(other.getString("epgUrl"), holding.getString("epgUrl"))
        }
    }

    @Test
    fun `a server address with nothing after it reads back whole`() {
        val source = SourceEntity(name = "P", type = SourceType.XTREAM, url = "http://lists.example.com:8080", username = "me", password = "s3cret")
        assertEquals("http://lists.example.com:8080", json(source).getString("url"))
        assertTrue(json(source).isNull("epgUrl"))
    }
}
