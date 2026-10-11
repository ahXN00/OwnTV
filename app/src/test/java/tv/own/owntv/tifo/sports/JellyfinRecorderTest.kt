package tv.own.owntv.tifo.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinRecorderTest {

    @Test
    fun anAddressWithoutSchemeIsHttp() {
        assertEquals("http://192.168.1.20:8096/", JellyfinRecorder.baseUrl("192.168.1.20:8096")?.toString())
        assertEquals("https://jf.example.org/jellyfin/", JellyfinRecorder.baseUrl(" https://jf.example.org/jellyfin/ ")?.toString())
        assertNull(JellyfinRecorder.baseUrl(""))
    }

    @Test
    fun theChannelIsFoundByItsPlaylistName() {
        val channels = JellyfinRecorder.parseChannels(
            """{"Items":[{"Name":"FR: beIN Sports 1 HD","Id":"a1"},{"Name":"FR: beIN Sports 2 HD","Id":"a2"},{"Name":"US: ESPN 2","Id":"b"}]}""",
        )
        assertEquals("a2", JellyfinRecorder.findChannel(channels, "FR: beIN Sports 2 HD"))
        assertEquals("b", JellyfinRecorder.findChannel(channels, "US | ESPN 2"))
        assertNull(JellyfinRecorder.findChannel(channels, "Canal+ Foot"))
    }

    @Test
    fun aTimerCoversTheProgrammeOnItsChannel() {
        val timers = JellyfinRecorder.parseTimers(
            """{"Items":[
                {"Id":"t1","ChannelName":"US: ESPN 2","StartDate":"2026-10-12T00:00:00.0000000Z","EndDate":"2026-10-12T02:30:00.0000000Z","Status":"New"},
                {"Id":"t2","ChannelName":"US: CBS","StartDate":"2026-10-12T00:00:00.0000000Z","EndDate":"2026-10-12T02:30:00.0000000Z","Status":"Cancelled"}]}""",
        )
        assertEquals(listOf("t1"), timers.map { it.id })
        val start = parseEspnDate("2026-10-12T00:00Z")!!
        assertTrue(JellyfinRecorder.covers(timers.single(), "US: ESPN 2", start))
        assertTrue(!JellyfinRecorder.covers(timers.single(), "US: CBS", start))
        assertTrue(!JellyfinRecorder.covers(timers.single(), "US: ESPN 2", start + 3 * 3_600_000L))
    }

    @Test
    fun theApiKeyGoesInTheAuthorizationHeader() {
        assertTrue(JellyfinRecorder.authorization(" abc123 ").endsWith("Token=\"abc123\""))
    }
}
