package tv.own.owntv.features.automation

import android.view.KeyEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerRoutesTest {
    private val backend = FakePlayer()
    private val client = ApiTestClient(playerRoutes(backend))

    @Test
    fun `key names read in any common spelling`() {
        assertEquals(RemoteKey.OK, RemoteKey.parse("ok"))
        assertEquals(RemoteKey.CHANNEL_UP, RemoteKey.parse("channel-up"))
        assertEquals(RemoteKey.DIGIT_7, RemoteKey.parse("7"))
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, RemoteKey.OK.keyCode)
        assertNull(RemoteKey.parse("POWER"))
        assertNull(RemoteKey.parse("12"))
    }

    @Test
    fun `keys are pressed in order, and only known ones`() {
        assertEquals(202, client.call("POST", "/api/v1/player/keys", """{"keys":["down","DOWN","ok"]}""").status)
        assertEquals(listOf(RemoteKey.DOWN, RemoteKey.DOWN, RemoteKey.OK), backend.pressed)
        assertEquals(400, client.call("POST", "/api/v1/player/keys", """{"keys":["DOWN","POWER"]}""").status)
        assertEquals(400, client.call("POST", "/api/v1/player/keys", """{"keys":[]}""").status)
        val tooMany = (0..RemoteKey.MAX_PER_CALL).joinToString(",") { "\"UP\"" }
        assertEquals(400, client.call("POST", "/api/v1/player/keys", """{"keys":[$tooMany]}""").status)
        assertEquals(3, backend.pressed.size)
        backend.inFront = false
        assertEquals(409, client.call("POST", "/api/v1/player/keys", """{"keys":["UP"]}""").status)
    }

    @Test
    fun `keys are refused while a private screen is open, saying so`() {
        backend.result = KeyPress.PAUSED
        val refused = client.call("POST", "/api/v1/player/keys", """{"keys":["OK"]}""")
        assertEquals(409, refused.status)
        assertEquals(
            "Remote buttons are paused while a private screen (logins, PINs, profiles, network, backups or pairing) is open on the TV",
            JSONObject(refused.body).getString("error"),
        )
        assertEquals(emptyList<RemoteKey>(), backend.pressed)
        backend.result = KeyPress.PRESSED
        assertEquals(202, client.call("POST", "/api/v1/player/keys", """{"keys":["OK"]}""").status)
    }

    @Test
    fun `keys are refused while another key request still runs, saying so`() {
        backend.result = KeyPress.BUSY
        val refused = client.call("POST", "/api/v1/player/keys", """{"keys":["OK"]}""")
        assertEquals(409, refused.status)
        assertEquals("Another key request is still running; try again", JSONObject(refused.body).getString("error"))
        assertEquals(emptyList<RemoteKey>(), backend.pressed)
    }

    @Test
    fun `volume and tracks are checked before they reach the player`() {
        assertEquals(202, client.call("POST", "/api/v1/player/volume", """{"percent":40}""").status)
        assertEquals(400, client.call("POST", "/api/v1/player/volume", """{"percent":140}""").status)
        assertEquals(listOf(40), backend.volumes)
        assertEquals(202, client.call("POST", "/api/v1/player/audio", """{"id":2}""").status)
        assertEquals(404, client.call("POST", "/api/v1/player/audio", """{"id":9}""").status)
        assertEquals(202, client.call("POST", "/api/v1/player/subtitles", """{"id":null}""").status)
        assertEquals(400, client.call("POST", "/api/v1/player/subtitles", "{}").status)
        assertEquals(listOf<Int?>(null), backend.subtitles)
    }

    private class FakePlayer : PlayerBackend {
        val pressed = mutableListOf<RemoteKey>()
        val volumes = mutableListOf<Int>()
        val subtitles = mutableListOf<Int?>()
        var inFront = true
        var result = KeyPress.PRESSED

        override suspend fun nowPlaying() = JSONObject()
        override suspend fun press(keys: List<RemoteKey>): KeyPress = when {
            !inFront -> KeyPress.NOT_IN_FRONT
            result != KeyPress.PRESSED -> result
            else -> KeyPress.PRESSED.also { pressed += keys }
        }
        override suspend fun playPause() = Unit
        override suspend fun stop() = Unit
        override suspend fun setVolume(percent: Int) {
            volumes += percent
        }
        override suspend fun selectAudio(id: Int) = id == 2
        override suspend fun selectSubtitle(id: Int?) = (id == null || id == 3).also { if (it) subtitles += id }
    }
}
