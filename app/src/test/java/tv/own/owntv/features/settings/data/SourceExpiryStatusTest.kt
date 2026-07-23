package tv.own.owntv.features.settings.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SourceExpiryStatusTest {

    private fun label(pattern: String, daysOffset: Long): String {
        val fmt = SimpleDateFormat(pattern, Locale.US)
        return fmt.format(Date(System.currentTimeMillis() + daysOffset * 86_400_000L))
    }

    @Test
    fun `past ISO date marks expired`() {
        val text = label("yyyy-MM-dd", -30)
        val result = parseStalkerExpiry(text)
        assertTrue(result.isExpired)
        assertEquals(text, result.label)
    }

    @Test
    fun `future ISO date does not mark expired`() {
        val text = label("yyyy-MM-dd", 30)
        val result = parseStalkerExpiry(text)
        assertFalse(result.isExpired)
        assertEquals(text, result.label)
    }

    @Test
    fun `today is not expired`() {
        assertFalse(parseStalkerExpiry(label("yyyy-MM-dd", 0)).isExpired)
    }

    @Test
    fun `yesterday is expired`() {
        assertTrue(parseStalkerExpiry(label("yyyy-MM-dd", -1)).isExpired)
    }

    @Test
    fun `tomorrow is not expired`() {
        assertFalse(parseStalkerExpiry(label("yyyy-MM-dd", 1)).isExpired)
    }

    @Test
    fun `european dotted past date marks expired`() {
        assertTrue(parseStalkerExpiry(label("dd.MM.yyyy", -30)).isExpired)
    }

    @Test
    fun `european slashed future date does not mark expired`() {
        assertFalse(parseStalkerExpiry(label("dd/MM/yyyy", 30)).isExpired)
    }

    @Test
    fun `european dashed past date marks expired`() {
        assertTrue(parseStalkerExpiry(label("dd-MM-yyyy", -30)).isExpired)
    }

    @Test
    fun `written month past date marks expired`() {
        assertTrue(parseStalkerExpiry(label("dd MMM yyyy", -30)).isExpired)
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
}
