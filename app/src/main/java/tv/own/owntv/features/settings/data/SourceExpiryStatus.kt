package tv.own.owntv.features.settings.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class SourceExpiryStatus(
    val label: String,
    val isExpired: Boolean,
)

internal fun parseStalkerExpiry(label: String): SourceExpiryStatus {
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(label)
    }.getOrNull()
        ?: return SourceExpiryStatus(label = label, isExpired = false)
    val expiryCal = Calendar.getInstance().apply {
        time = parsed
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return SourceExpiryStatus(label = label, isExpired = todayCal.after(expiryCal))
}
