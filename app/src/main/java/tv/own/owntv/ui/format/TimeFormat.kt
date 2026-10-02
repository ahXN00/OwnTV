package tv.own.owntv.ui.format

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class DateFormatterKey(
    val locale: Locale,
    val skeleton: String,
    val timeZoneId: String,
)

@Composable
fun rememberSystemTimeFormatter(): (Long) -> String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val formatter = remember(context, configuration) {
        DateFormat.getTimeFormat(context)
    }
    return remember(formatter) {
        val date = Date(0L)
        val format: (Long) -> String = { ms ->
            date.time = ms
            formatter.format(date)
        }
        format
    }
}

fun formatSystemTime(context: Context, ms: Long): String {
    return DateFormat.getTimeFormat(context).format(Date(ms))
}

@Composable
fun rememberBestDateFormatter(
    skeleton: String,
): (Long) -> String {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val timeZone = TimeZone.getDefault()
    val key = DateFormatterKey(locale, skeleton, timeZone.id)
    val formatter = remember(key) {
        bestDateFormatter(locale, skeleton, timeZone)
    }
    return remember(formatter) { { ms -> formatter.format(Date(ms)) } }
}

/**
 * A localized day for an episode's air date, formatted **in UTC** — see `AirDate` in core. An air
 * date is a calendar day rather than an instant, and formatting UTC midnight in the device's own
 * zone would show the day before to everyone west of it.
 */
@Composable
fun rememberAirDateFormatter(): (Long) -> String {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val formatter = remember(locale) {
        bestDateFormatter(locale, AIR_DATE_SKELETON, tv.own.owntv.core.content.AirDate.UTC)
    }
    return remember(formatter) { { ms -> formatter.format(Date(ms)) } }
}

/** Year, abbreviated month, day — the shortest form that still identifies a specific broadcast. */
private const val AIR_DATE_SKELETON = "yMMMd"

fun formatBestDate(
    context: Context,
    skeleton: String,
    ms: Long,
): String {
    val locale = context.resources.configuration.locales[0]
    // Memoized: resolving the pattern runs ICU work, and rows (recordings progress, diagnostics)
    // call this on every recomposition. The cache holds prototypes — SimpleDateFormat is not
    // thread-safe, so each format works on a cheap clone, never the shared instance.
    val formatter = cachedFormatter(locale, skeleton, TimeZone.getDefault())
    return formatter.format(Date(ms))
}

fun formatBestDateTime(
    context: Context,
    dateSkeleton: String,
    ms: Long,
): String {
    val skeleton = combinedDateTimeSkeleton(dateSkeleton, DateFormat.is24HourFormat(context))
    return formatBestDate(context, skeleton, ms)
}

internal fun combinedDateTimeSkeleton(dateSkeleton: String, is24Hour: Boolean): String =
    dateSkeleton + if (is24Hour) "Hm" else "hm"

private fun bestDateFormatter(
    locale: Locale,
    skeleton: String,
    timeZone: TimeZone,
): SimpleDateFormat {
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }
}

/** Prototype formatters by (locale, skeleton, zone): a handful exist app-wide, so 8 is plenty. */
private const val FORMATTER_CACHE_SIZE = 8
private val formatterCache =
    object : LinkedHashMap<DateFormatterKey, SimpleDateFormat>(FORMATTER_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<DateFormatterKey, SimpleDateFormat>?,
        ): Boolean = size > FORMATTER_CACHE_SIZE
    }

/** A cheap clone of the cached prototype — never the shared instance (see formatBestDate). */
private fun cachedFormatter(
    locale: Locale,
    skeleton: String,
    timeZone: TimeZone,
): SimpleDateFormat = synchronized(formatterCache) {
    val key = DateFormatterKey(locale, skeleton, timeZone.id)
    (formatterCache.getOrPut(key) { bestDateFormatter(locale, skeleton, timeZone) }).clone() as SimpleDateFormat
}
