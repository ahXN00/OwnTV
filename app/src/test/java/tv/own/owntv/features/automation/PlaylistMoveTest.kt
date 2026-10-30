package tv.own.owntv.features.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

class PlaylistMoveTest {
    private val xtream = SourceEntity(name = "X", type = SourceType.XTREAM, url = "http://panel.example.com:8080", username = "u", password = "p")
    private val stalker = SourceEntity(name = "S", type = SourceType.STALKER, url = "http://portal.example.com/c/", mac = "00:1A:79:00:00:01")
    private val m3u = SourceEntity(name = "M", type = SourceType.M3U, url = "https://lists.example.com/a.m3u")

    @Test
    fun `same server keeps the stored login`() {
        assertNull(PlaylistMove.refusal(xtream, PlaylistPatch(url = "http://PANEL.example.com:8080/")))
        assertNull(PlaylistMove.refusal(stalker, PlaylistPatch(url = "http://portal.example.com:80/stalker_portal/c/")))
        assertNull(PlaylistMove.refusal(xtream, PlaylistPatch(name = "Renamed")))
    }

    @Test
    fun `new host, port or scheme needs the password again`() {
        listOf("http://other.example.com:8080", "http://panel.example.com:8081", "https://panel.example.com:8080").forEach { url ->
            assertTrue(url, PlaylistMove.refusal(xtream, PlaylistPatch(url = url))!!.contains("password"))
            assertNull(url, PlaylistMove.refusal(xtream, PlaylistPatch(url = url, password = "p2")))
        }
    }

    @Test
    fun `a blank password or mac never counts as sent`() {
        listOf(" ", "\t").forEach { blank ->
            val moveXtream = PlaylistMove.refusal(xtream, PlaylistPatch(url = "http://other.example.com:8080", password = blank))
            assertTrue(moveXtream.orEmpty().contains("password"))
            val moveStalker = PlaylistMove.refusal(stalker, PlaylistPatch(url = "http://other.example.com/c/", mac = blank))
            assertTrue(moveStalker.orEmpty().contains("mac"))
            assertTrue(PlaylistMove.refusal(xtream, PlaylistPatch(username = "v", password = blank)) != null)
        }
    }

    @Test
    fun `a new username or mac on the same server needs the password`() {
        val reason = PlaylistMove.refusal(xtream, PlaylistPatch(username = "v"))
        assertTrue(reason.orEmpty().contains("username") && reason.orEmpty().contains("password"))
        assertNull(PlaylistMove.refusal(xtream, PlaylistPatch(username = "v", password = "p2")))
        val withLogin = stalker.copy(username = "u", password = "p")
        assertTrue(PlaylistMove.refusal(withLogin, PlaylistPatch(mac = "00:1A:79:00:00:02")).orEmpty().contains("password"))
        assertNull(PlaylistMove.refusal(withLogin, PlaylistPatch(mac = "00:1A:79:00:00:02", password = "p")))
        assertNull(PlaylistMove.refusal(m3u, PlaylistPatch(username = "v")))
    }

    @Test
    fun `a stalker move needs the mac again`() {
        val reason = PlaylistMove.refusal(stalker, PlaylistPatch(url = "http://other.example.com/c/"))
        assertTrue(reason!!.contains("mac") && !reason.contains("password"))
        assertNull(PlaylistMove.refusal(stalker, PlaylistPatch(url = "http://other.example.com/c/", mac = "00:1A:79:00:00:02")))
    }

    @Test
    fun `a stalker move with a stored password needs both`() {
        val withLogin = stalker.copy(username = "u", password = "p")
        val reason = PlaylistMove.refusal(withLogin, PlaylistPatch(url = "http://other.example.com/c/"))
        assertTrue(reason!!.contains("password and mac"))
    }

    @Test
    fun `an m3u address without a stored password moves freely`() {
        assertNull(PlaylistMove.refusal(m3u, PlaylistPatch(url = "https://mirror.example.com/a.m3u")))
        assertTrue(PlaylistMove.refusal(m3u.copy(password = "p"), PlaylistPatch(url = "https://mirror.example.com/a.m3u")) != null)
    }

    @Test
    fun `mac is only for stalker playlists`() {
        assertTrue(PlaylistMove.refusal(xtream, PlaylistPatch(mac = "00:1A:79:00:00:02"))!!.contains("Stalker"))
        assertNull(PlaylistMove.refusal(stalker, PlaylistPatch(mac = "00:1A:79:00:00:02")))
    }

    @Test
    fun `default ports and unreadable stored addresses`() {
        assertFalse(PlaylistMove.changesServer("http://a.example.com", "http://a.example.com:80/x"))
        assertFalse(PlaylistMove.changesServer("https://a.example.com", "https://a.example.com:443"))
        assertTrue(PlaylistMove.changesServer("http://a.example.com", "https://a.example.com"))
        assertTrue(PlaylistMove.changesServer("not a url", "http://a.example.com"))
        assertTrue(PlaylistMove.changesServer("a.example.com:8080", "http://a.example.com:8080"))
    }
}
