package tv.own.owntv.features.settings.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SourceExpiryStatus(
    val label: String,
    val isExpired: Boolean,
)

/**
 * Stalker portals are inconsistent about the expiry date format (ISO, European dot/slash/dash,
 * or a written-out month). Patterns are tried in order; the first that parses non-leniently wins,
 * so a wrong-shaped input falls through instead of being silently misread.
 */
private val expiryDateFormats = listOf(
    "yyyy-MM-dd",
    "yyyy/MM/dd",
    "dd.MM.yyyy",
    "dd/MM/yyyy",
    "dd-MM-yyyy",
    "dd MMM yyyy",
    "dd MMMM yyyy",
)

internal fun parseStalkerExpiry(label: String): SourceExpiryStatus {
    val expiry = expiryDateFormats.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(label)
        }.getOrNull()
    } ?: return SourceExpiryStatus(label = label, isExpired = false)
    return SourceExpiryStatus(label = label, isExpired = startOfDay(expiry).before(startOfDay(Date())))
}

private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
    time = date
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time
