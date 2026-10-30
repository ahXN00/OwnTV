package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.nav.MainSection

class UiJsonTest {
    @Test
    fun `in the background only that and the screen are told, even over a private screen`() {
        val json = UiJson.reduced(inFront = false, screenOn = false, privateOpen = true)!!
        assertEquals(setOf("inFront", "screenOn"), keys(json))
        assertFalse(json.getBoolean("inFront"))
        assertFalse(json.getBoolean("screenOn"))
    }

    @Test
    fun `a private screen hides everything else`() {
        val json = UiJson.reduced(inFront = true, screenOn = true, privateOpen = true)!!
        assertEquals(setOf("inFront", "screenOn", "screen", "sensitive"), keys(json))
        assertEquals("private", json.getString("screen"))
        assertTrue(json.getBoolean("sensitive"))
    }

    @Test
    fun `in front with nothing private the whole state shows`() {
        assertNull(UiJson.reduced(inFront = true, screenOn = true, privateOpen = false))
        val snapshot = UiSnapshot(
            section = MainSection.MOVIES,
            player = UiPlayer.MINI,
            focused = UiItem(UiItemType.MOVIE, 12, "X"),
            playing = UiItem(UiItemType.CHANNEL, 5, "Y"),
        )
        val playlist = SourceEntity(id = 3, name = "My list", type = SourceType.M3U, url = "http://lists.example.com/a.m3u?key=1")
        val json = UiJson.full(screenOn = true, snapshot = snapshot, activePlaylist = playlist)
        assertEquals(
            setOf("inFront", "screenOn", "screen", "player", "sensitive", "activePlaylist", "focused", "playing"),
            keys(json),
        )
        assertTrue(json.getBoolean("inFront"))
        assertEquals("movies", json.getString("screen"))
        assertEquals("mini", json.getString("player"))
        assertFalse(json.getBoolean("sensitive"))
        val active = json.getJSONObject("activePlaylist")
        assertEquals(setOf("id", "name"), keys(active))
        assertEquals(3L, active.getLong("id"))
        assertEquals("My list", active.getString("name"))
        assertEquals(setOf("type", "id", "title"), keys(json.getJSONObject("focused")))
        assertEquals("movie", json.getJSONObject("focused").getString("type"))
        assertEquals(12L, json.getJSONObject("focused").getLong("id"))
        assertEquals("channel", json.getJSONObject("playing").getString("type"))
        assertEquals("Y", json.getJSONObject("playing").getString("title"))
    }

    @Test
    fun `unknown items and All playlists are null`() {
        val json = UiJson.full(screenOn = false, snapshot = UiSnapshot(), activePlaylist = null)
        assertEquals("other", json.getString("screen"))
        assertEquals("none", json.getString("player"))
        assertTrue(json.isNull("activePlaylist"))
        assertTrue(json.isNull("focused"))
        assertTrue(json.isNull("playing"))
    }

    @Test
    fun `screens are named as navigate takes them`() {
        assertEquals("live", UiJson.screen(MainSection.LIVE_TV))
        assertEquals("guide", UiJson.screen(MainSection.EPG))
        assertEquals("downloads", UiJson.screen(MainSection.DOWNLOADS))
        assertEquals("settings", UiJson.screen(MainSection.SETTINGS))
        assertEquals("other", UiJson.screen(MainSection.MORE))
        assertEquals("other", UiJson.screen(null))
    }

    private fun keys(json: JSONObject): Set<String> = json.keys().asSequence().toSet()
}
