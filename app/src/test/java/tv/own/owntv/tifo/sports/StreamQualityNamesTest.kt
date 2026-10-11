package tv.own.owntv.tifo.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamQualityNamesTest {

    private fun resolution(name: String) = StreamQualityNames.fromName(name)?.resolution

    @Test
    fun readsTheQualityMarkInTheName() {
        assertEquals("HD", resolution("US| CBS HD"))
        assertEquals("FHD", resolution("FR | beIN Sports 1 FHD"))
        assertEquals("SD", resolution("DE| NFL NETWORK SD"))
        assertEquals("4K", resolution("UK: Sky Sports Main Event 4K"))
        assertEquals("4K", resolution("Canal+ Sport 2160p"))
        assertEquals("1080p", resolution("Ligue 1+ 1080P"))
        assertEquals("HD", resolution("DE| Sky Cinema Premieren ᴴᴰ"))
    }

    @Test
    fun readsTheFrameRate() {
        val q = StreamQualityNames.fromName("UK: Sky Sports F1 FHD 50fps")
        assertEquals("FHD", q?.resolution)
        assertEquals(50, q?.fps)
    }

    @Test
    fun ignoresLettersInsideWords() {
        assertNull(StreamQualityNames.fromName("CA| CBS EAST"))
        assertNull(StreamQualityNames.fromName("US| HDNET MOVIES"))
        assertNull(StreamQualityNames.fromName("NCAAF 05: CBS"))
    }
}
