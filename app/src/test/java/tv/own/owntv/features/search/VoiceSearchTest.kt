package tv.own.owntv.features.search

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSearchTest {

    @Test
    fun `the microphone shows only when a recogniser answers`() {
        assertTrue(VoiceSearch.isAvailable { true })
        assertFalse(VoiceSearch.isAvailable { false })
    }

    @Test
    fun `a package manager that throws hides the microphone instead of crashing`() {
        assertFalse(VoiceSearch.isAvailable { throw SecurityException("blocked by the OEM build") })
        assertFalse(VoiceSearch.isAvailable { throw IllegalStateException("package manager has died") })
    }

    @Test
    fun `the first guess becomes the query, tidied`() {
        assertEquals("world news tonight", VoiceSearch.spokenQuery(Activity.RESULT_OK, listOf("  world   news\ttonight ", "world news to night")))
    }

    @Test
    fun `blank and missing guesses are skipped`() {
        assertEquals("cartoons", VoiceSearch.spokenQuery(Activity.RESULT_OK, listOf(null, "   ", "cartoons")))
        assertNull(VoiceSearch.spokenQuery(Activity.RESULT_OK, listOf(" ", null)))
        assertNull(VoiceSearch.spokenQuery(Activity.RESULT_OK, emptyList()))
        assertNull(VoiceSearch.spokenQuery(Activity.RESULT_OK, null))
    }

    @Test
    fun `backing out of the recogniser leaves the query alone`() {
        assertNull(VoiceSearch.spokenQuery(Activity.RESULT_CANCELED, listOf("news")))
        assertNull(VoiceSearch.spokenQuery(RecognizerErrorCode, listOf("news")))
    }

    @Test
    fun `several recognisers go to the system default, one or an unknown default asks nobody`() {
        val both = listOf("com.google.android.tts", "com.vendor.voice")
        assertEquals("com.google.android.tts", VoiceSearch.pick(both, "com.google.android.tts"))
        assertNull(VoiceSearch.pick(listOf("com.google.android.tts"), "com.google.android.tts"))
        assertNull(VoiceSearch.pick(both, "com.other"))
        assertNull(VoiceSearch.pick(both, null))
    }

    private companion object {
        /** RecognizerIntent.RESULT_NO_MATCH: a recogniser's own "heard nothing" code. */
        const val RecognizerErrorCode = 4
    }
}
