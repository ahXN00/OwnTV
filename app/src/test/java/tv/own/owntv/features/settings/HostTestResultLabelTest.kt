package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import tv.own.owntv.R
import tv.own.owntv.core.repository.HostTestRow
import tv.own.owntv.core.repository.HostTestStatus

class HostTestResultLabelTest {

    @Test
    fun `each status maps to its backup result string`() {
        assertEquals(R.string.settings_sources_backup_result_invalid, hostTestResultLabelRes(HostTestStatus.INVALID_RESPONSE))
        assertEquals(R.string.settings_sources_backup_result_blocked, hostTestResultLabelRes(HostTestStatus.BLOCKED))
        assertEquals(R.string.settings_sources_backup_result_unreachable, hostTestResultLabelRes(HostTestStatus.UNREACHABLE))
        assertEquals(R.string.settings_sources_backup_result_auth, hostTestResultLabelRes(HostTestStatus.AUTH_FAILED))
        assertEquals(R.string.settings_sources_backup_result_auth, hostTestResultLabelRes(HostTestStatus.EXPIRED))
        assertEquals(R.string.settings_sources_backup_result_skipped, hostTestResultLabelRes(HostTestStatus.NOT_TESTED))
        assertEquals(R.string.settings_sources_backup_result_ms, hostTestResultLabelRes(HostTestStatus.OK))
        assertEquals(R.string.settings_sources_backup_result_network, hostTestResultLabelRes(HostTestStatus.NETWORK_PROBLEM))
    }

    @Test
    fun `network problem is not a usable row`() {
        val row = HostTestRow("cdn.example", 2, HostTestStatus.NETWORK_PROBLEM, 40)
        assertFalse(canUseResultRow(row, inUse = null))
        assertFalse(canUseResultRow(row, inUse = "other.example"))
    }

    @Test
    fun `invalid and blocked differ from unreachable and auth`() {
        val invalid = hostTestResultLabelRes(HostTestStatus.INVALID_RESPONSE)
        val blocked = hostTestResultLabelRes(HostTestStatus.BLOCKED)
        val unreachable = hostTestResultLabelRes(HostTestStatus.UNREACHABLE)
        val auth = hostTestResultLabelRes(HostTestStatus.AUTH_FAILED)
        assertNotEquals(unreachable, invalid)
        assertNotEquals(auth, invalid)
        assertNotEquals(unreachable, blocked)
        assertNotEquals(auth, blocked)
    }
}
