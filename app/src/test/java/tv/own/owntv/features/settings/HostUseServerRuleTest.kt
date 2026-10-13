package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.repository.HostTestRow
import tv.own.owntv.core.repository.HostTestStatus

class HostUseServerRuleTest {

    private fun row(host: String, position: Int, status: HostTestStatus, ms: Long? = null) =
        HostTestRow(host, position, status, ms)

    @Test
    fun `fastest skips non-OK rows even when their ms is lower`() {
        val rows = listOf(
            row("slow-ok", 1, HostTestStatus.OK, 400),
            row("dead", 2, HostTestStatus.UNREACHABLE, 10),
            row("html", 3, HostTestStatus.INVALID_RESPONSE, 5),
            row("skipped", 4, HostTestStatus.NOT_TESTED, 1),
            row("fast-ok", 5, HostTestStatus.OK, 80),
        )
        val order = rows.map { it.host }
        assertEquals("fast-ok", SourceTestUi.AllResults(sourceName = "s", sourceId = 1L, rows = rows, inUse = null).fastest?.host)
        assertEquals(order, rows.map { it.host })
    }

    @Test
    fun `fastest is null when no row is OK`() {
        val rows = listOf(
            row("dead", 1, HostTestStatus.UNREACHABLE, 10),
            row("html", 2, HostTestStatus.INVALID_RESPONSE, 5),
            row("skipped", 3, HostTestStatus.NOT_TESTED),
        )
        assertNull(SourceTestUi.AllResults(sourceName = "s", sourceId = 1L, rows = rows, inUse = null).fastest)
    }

    @Test
    fun `use fastest hidden when that host is already in use`() {
        val fastest = row("a", 1, HostTestStatus.OK, 40)
        assertNull(useFastestTarget(fastest, "a"))
        assertEquals(fastest, useFastestTarget(fastest, "b"))
        assertEquals(fastest, useFastestTarget(fastest, null))
    }

    @Test
    fun `non-OK and in-use rows are not usable`() {
        val inUse = "primary"
        assertFalse(canUseResultRow(row("dead", 1, HostTestStatus.UNREACHABLE, 1), inUse))
        assertFalse(canUseResultRow(row("html", 2, HostTestStatus.INVALID_RESPONSE, 1), inUse))
        assertFalse(canUseResultRow(row("skipped", 3, HostTestStatus.NOT_TESTED), inUse))
        assertFalse(canUseResultRow(row(inUse, 4, HostTestStatus.OK, 20), inUse))
        assertTrue(canUseResultRow(row("backup", 5, HostTestStatus.OK, 30), inUse))
    }
}
