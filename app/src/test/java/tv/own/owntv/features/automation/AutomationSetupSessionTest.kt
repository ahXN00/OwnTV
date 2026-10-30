package tv.own.owntv.features.automation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSetupSessionTest {
    private var enabled = false
    private var offBefore = false
    private var pairingOpen = false
    private var opened = 0
    private val devices = mutableSetOf("before")
    private val writes = mutableListOf<String>()

    private val session = AutomationSetupSession(
        enabled = { enabled },
        setEnabled = { enabled = it; writes += "enabled=$it"; if (!it) pairingOpen = false },
        openPairing = { pairingOpen = true; opened++ },
        closePairing = { pairingOpen = false },
        deviceIds = { devices.toSet() },
        unpair = { devices -= it; writes += "unpair=$it" },
        offBefore = { offBefore },
        setOffBefore = { offBefore = it; writes += "offBefore=$it" },
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun `starting turns the API on, opens pairing and remembers who was paired already`() {
        session.start()
        assertTrue(enabled)
        assertTrue(pairingOpen)
        assertEquals(setOf("before"), session.known.value)
    }

    @Test
    fun `cancel closes pairing and turns the API back off when it was off`() {
        session.start()
        session.close()
        assertFalse(enabled)
        assertFalse(pairingOpen)
        assertNull(session.known.value)
    }

    @Test
    fun `cancel leaves the API on when it was on before the page opened`() {
        enabled = true
        session.start()
        session.close()
        assertTrue(enabled)
        assertFalse(pairingOpen)
    }

    @Test
    fun `setting up here instead after a device paired forgets it and turns the API back off when it was off`() {
        session.start()
        devices += "laptop"
        session.close()
        assertFalse(enabled)
        assertFalse(pairingOpen)
        assertFalse("the paired device may no longer set up the TV", session.paired())
        assertEquals(setOf("before"), devices)
    }

    @Test
    fun `cancel forgets only the devices paired during the visit`() {
        enabled = true
        devices += "earlier"
        session.start()
        devices += "laptop"
        devices += "phone"
        session.close()
        assertEquals(setOf("before", "earlier"), devices)
        assertTrue("the API was on before the visit", enabled)
    }

    @Test
    fun `leaving the app after a device paired keeps the visit and closes the PIN`() {
        session.start()
        devices += "laptop"
        session.away()
        assertFalse(pairingOpen)
        assertTrue(session.paired())
        session.start()
        assertTrue(enabled)
        assertFalse("nothing to pair any more", pairingOpen)
        assertEquals(1, opened)
        session.close()
        assertFalse("the API was off when the visit began", enabled)
    }

    @Test
    fun `leaving the app before anything paired ends the visit, and coming back starts a fresh one`() {
        session.start()
        session.away()
        assertFalse(enabled)
        assertFalse(pairingOpen)
        assertNull(session.known.value)
        session.start()
        assertTrue(enabled)
        assertTrue(pairingOpen)
        assertEquals(2, opened)
    }

    @Test
    fun `leaving the app outside a visit changes nothing`() {
        enabled = true
        session.away()
        assertTrue(enabled)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `a new PIN is refused once a device paired`() {
        session.start()
        devices += "laptop"
        pairingOpen = false
        session.newPin()
        assertFalse(pairingOpen)
    }

    @Test
    fun `a new PIN reopens an expired window`() {
        session.start()
        pairingOpen = false
        session.newPin()
        assertTrue(pairingOpen)
        assertEquals(2, opened)
    }

    @Test
    fun `a new PIN outside a visit opens nothing`() {
        session.newPin()
        assertFalse(pairingOpen)
        assertEquals(0, opened)
    }

    @Test
    fun `cancel outside a visit changes nothing`() {
        enabled = true
        session.close()
        assertTrue(enabled)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `the setup routes open only once a device paired from the page, and only while the visit lasts`() {
        assertFalse("no visit", session.paired())
        devices += "earlier"
        session.start()
        assertFalse("devices paired before the visit do not count", session.paired())
        devices += "laptop"
        assertTrue(session.paired())
        session.away()
        assertTrue("the page is off screen, but the visit goes on", session.paired())
        session.close()
        assertFalse(session.paired())
    }

    @Test
    fun `finishing ends the visit and leaves the API on for the paired device`() {
        session.start()
        devices += "laptop"
        session.finished()
        assertTrue(enabled)
        assertFalse(offBefore)
        assertFalse(pairingOpen)
        assertNull(session.known.value)
        assertFalse(session.paired())
        session.close()
        assertTrue("a close after finishing has nothing to undo", enabled)
        assertTrue("laptop" in devices)
    }

    @Test
    fun `a cancel during a setup step waits for it`() = runBlocking {
        session.start()
        devices += "laptop"
        val release = CompletableDeferred<Unit>()
        val step = launch(Dispatchers.Unconfined) { session.holding { release.await() } }
        session.close()
        assertTrue("the visit is held", session.paired())
        assertTrue(enabled)
        release.complete(Unit)
        step.join()
        assertFalse(session.paired())
        assertFalse(enabled)
        assertEquals(setOf("before"), devices)
    }

    @Test
    fun `the disk says the API was off before it comes on, and stops saying so only once it is off again`() {
        session.start()
        devices += "laptop"
        session.away()
        session.start()
        assertTrue(offBefore)
        session.close()
        val order = listOf("offBefore=true", "enabled=true", "unpair=laptop", "enabled=false", "offBefore=false")
        assertEquals(order, writes)
    }

    @Test
    fun `a visit that finds the API on leaves nothing on disk to undo`() {
        enabled = true
        session.start()
        assertFalse(offBefore)
        session.close()
        assertTrue(enabled)
        assertEquals(listOf("offBefore=false", "offBefore=false"), writes)
    }
}
