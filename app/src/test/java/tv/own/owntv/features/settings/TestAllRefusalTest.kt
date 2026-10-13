package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.repository.SourceTestResult
import tv.own.owntv.player.ReleaseGate

class TestAllRefusalTest {

    @Test
    fun `each refusal refuses`() {
        assertTrue(testAllRefused(false, ReleaseGate.Result.Refused(ReleaseGate.Refusal.RECORDING)))
        assertTrue(testAllRefused(false, ReleaseGate.Result.Refused(ReleaseGate.Refusal.DOWNLOAD)))
        assertTrue(testAllRefused(false, ReleaseGate.Result.Refused(ReleaseGate.Refusal.MEASURING)))
        assertTrue(testAllRefused(false, ReleaseGate.Result.Refused(ReleaseGate.Refusal.TIMESHIFT)))
    }

    @Test
    fun `released does not refuse`() {
        assertFalse(testAllRefused(false, ReleaseGate.Result.Released(mpvDestroyed = false, quiet = true)))
    }

    @Test
    fun `probe lock refuses`() {
        assertTrue(testAllRefused(true, ReleaseGate.Result.Released(mpvDestroyed = false, quiet = true)))
    }

    @Test
    fun `busy cause maps each refusal`() {
        assertEquals(TestAllBusyCause.RECORDING, testAllBusyCause(false, ReleaseGate.Result.Refused(ReleaseGate.Refusal.RECORDING)))
        assertEquals(TestAllBusyCause.DOWNLOAD, testAllBusyCause(false, ReleaseGate.Result.Refused(ReleaseGate.Refusal.DOWNLOAD)))
        assertEquals(TestAllBusyCause.MEASURING, testAllBusyCause(false, ReleaseGate.Result.Refused(ReleaseGate.Refusal.MEASURING)))
        assertEquals(TestAllBusyCause.TIMESHIFT, testAllBusyCause(false, ReleaseGate.Result.Refused(ReleaseGate.Refusal.TIMESHIFT)))
    }

    @Test
    fun `busy cause probe lock only is measuring`() {
        assertEquals(TestAllBusyCause.MEASURING, testAllBusyCause(true, ReleaseGate.Result.Released(mpvDestroyed = false, quiet = true)))
    }

    @Test
    fun `busy cause released is null`() {
        assertNull(testAllBusyCause(false, ReleaseGate.Result.Released(mpvDestroyed = false, quiet = true)))
    }

    @Test
    fun `busy with previous returns that done`() {
        val done = SourceTestUi.Done("s", SourceTestResult.Ok())
        val busy = SourceTestUi.TestAllBusy("s", previous = done)
        assertEquals(done, busyDismissTarget(busy))
    }

    @Test
    fun `busy without previous returns null`() {
        assertNull(busyDismissTarget(SourceTestUi.TestAllBusy("s")))
    }

    @Test
    fun `non-busy state returns null`() {
        assertNull(busyDismissTarget(SourceTestUi.Done("s", SourceTestResult.Ok())))
    }
}
