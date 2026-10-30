package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.sync.work.EpgSyncState

class GuideJsonTest {
    private fun json(source: EpgSource): JSONObject =
        GuideJson.of(source, EpgRefresh.OFF, channels = 0, programmes = 0, sync = EpgSyncState.Idle)

    @Test
    fun `a guide address shows only where it points`() {
        listOf(
            "http://guide.example.com/${"0123456789".repeat(40)}.xml",
            "http://guide.example.com/xmltv.php?username=me&password=s3cret",
            "http://guide.example.com/epg/me/s3cret/guide.xml",
        ).forEach { assertEquals("http://guide.example.com/…", json(EpgSource(id = -1, name = "G", url = it)).getString("url")) }
        assertEquals("http://guide.example.com", json(EpgSource(id = -1, name = "G", url = "http://guide.example.com")).getString("url"))
    }

    @Test
    fun `the TV's own error text shows each address only as far as its host and port`() {
        val source = EpgSource(
            id = -1, name = "G", url = "http://guide.example.com/a.xml",
            lastError = "HTTP 403 for http://guide.example.com:8080/me/s3cret/a.xml",
        )
        assertEquals("HTTP 403 for http://guide.example.com:8080/…", json(source).getString("lastError"))
    }

    @Test
    fun `an error quoting a caller's long guide address reads back as its host alone`() {
        val error = "HTTP 404 for http://guide.example.com/${"0123456789".repeat(40)}.xml"
        val source = EpgSource(id = -1, name = "G", url = "http://guide.example.com/a.xml", lastError = error)
        assertEquals("HTTP 404 for http://guide.example.com/…", json(source).getString("lastError"))
    }
}
