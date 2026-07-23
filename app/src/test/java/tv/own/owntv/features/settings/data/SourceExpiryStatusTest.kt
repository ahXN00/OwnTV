package tv.own.owntv.features.settings.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SourceExpiryStatusTest {

    private fun dateLabel(daysOffset: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date(System.currentTimeMillis() + daysOffset * 86_400_000L))
    }

    @Test
    fun `past date marks expired`() {
        val label = dateLabel(-30)
        val result = parseStalkerExpiry(label)
        assertTrue(result.isExpired)
        assertEquals(label, result.label)
    }

    @Test
    fun `future date does not mark expired`() {
        val label = dateLabel(30)
        val result = parseStalkerExpiry(label)
        assertFalse(result.isExpired)
        assertEquals(label, result.label)
    }

    @Test
    fun `today is not expired`() {
        val label = dateLabel(0)
        val result = parseStalkerExpiry(label)
        assertFalse(result.isExpired)
    }

    @Test
    fun `yesterday is expired`() {
        val label = dateLabel(-1)
        val result = parseStalkerExpiry(label)
        assertTrue(result.isExpired)
    }

    @Test
    fun `tomorrow is not expired`() {
        val label = dateLabel(1)
        val result = parseStalkerExpiry(label)
        assertFalse(result.isExpired)
    }

    @Test
    fun `unparseable label displays without expired badge`() {
        val result = parseStalkerExpiry("unknown expiry")
        assertFalse(result.isExpired)
        assertEquals("unknown expiry", result.label)
    }

    @Test
    fun `empty label is not expired`() {
        val result = parseStalkerExpiry("")
        assertFalse(result.isExpired)
        assertEquals("", result.label)
    }

    @Test
    fun `dashed non-ISO date does not false expired`() {
        val result = parseStalkerExpiry("22-08-2026")
        assertFalse(result.isExpired)
    }
}
