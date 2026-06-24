package tv.own.owntv.features.multiview

import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.player.CornerEngine
import tv.own.owntv.player.CornerState
import tv.own.owntv.player.MediaMeta

/**
 * Verifies the picture-in-picture corner orchestration using a fake engine — no ExoPlayer, no Android, no
 * second decoder (which is exactly the resource we don't want a test to spend). Confirms the corner activates
 * with the right channel, starts muted (so the main keeps the audio), reuses the decoder instead of reloading
 * the same channel, switches on a new channel, and frees the decoder on close.
 */
class PipControllerTest {

    /** Records control calls and tracks [currentUrl] the way the real engine does, without any playback. */
    private class FakeCornerEngine : CornerEngine {
        override val state: StateFlow<CornerState> = MutableStateFlow(CornerState.IDLE)
        override val meta: StateFlow<MediaMeta> = MutableStateFlow(MediaMeta())
        override var currentUrl: String? = null

        val playCalls = mutableListOf<Triple<String, MediaMeta, Boolean>>()
        val muteCalls = mutableListOf<Boolean>()
        var stopCalls = 0

        override fun play(url: String, meta: MediaMeta, muted: Boolean) {
            playCalls += Triple(url, meta, muted)
            currentUrl = url
        }
        override fun setMuted(muted: Boolean) { muteCalls += muted }
        override fun stop() { stopCalls++; currentUrl = null }
        override fun release() { currentUrl = null }
        override fun setSurface(surface: Surface?) {}
    }

    private fun channel(id: Long, url: String) =
        ChannelEntity(id = id, sourceId = 1, name = "Ch$id", streamUrl = url)

    @Test
    fun initiallyInactive() {
        val pip = PipController(FakeCornerEngine())
        assertFalse(pip.active.value)
        assertNull(pip.channel.value)
    }

    @Test
    fun openCorner_activatesAndPlaysMutedByDefault() {
        val engine = FakeCornerEngine()
        val pip = PipController(engine)
        val ch = channel(1, "http://a")

        pip.openCorner(ch)

        assertTrue(pip.active.value)
        assertEquals(ch, pip.channel.value)
        assertEquals(1, engine.playCalls.size)
        assertEquals("http://a", engine.playCalls[0].first)
        assertTrue("corner must start muted so the main stream keeps the audio", engine.playCalls[0].third)
    }

    @Test
    fun openCorner_sameChannel_doesNotReload() {
        val engine = FakeCornerEngine()
        val pip = PipController(engine)
        val ch = channel(1, "http://a")

        pip.openCorner(ch)
        pip.openCorner(ch) // same URL → must NOT spin up the decoder again (just re-apply mute)

        assertEquals(1, engine.playCalls.size)
        assertEquals(1, engine.muteCalls.size)
        assertTrue(pip.active.value)
    }

    @Test
    fun openCorner_differentChannel_switchesStream() {
        val engine = FakeCornerEngine()
        val pip = PipController(engine)

        pip.openCorner(channel(1, "http://a"))
        pip.openCorner(channel(2, "http://b"))

        assertEquals(2, engine.playCalls.size)
        assertEquals("http://b", engine.playCalls[1].first)
        assertEquals(2L, pip.channel.value?.id)
    }

    @Test
    fun closeCorner_deactivatesAndStops() {
        val engine = FakeCornerEngine()
        val pip = PipController(engine)
        pip.openCorner(channel(1, "http://a"))

        pip.closeCorner()

        assertFalse(pip.active.value)
        assertNull(pip.channel.value)
        assertEquals(1, engine.stopCalls)
    }
}
