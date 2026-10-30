package tv.own.owntv.features.automation

/**
 * Checks for text the app later sends as an HTTP header to the provider, such as a playlist's or
 * guide's user agent and referer. A line break would end the header early and start a new one, and
 * other control characters or a very long value make servers drop the request, so the sync would
 * fail on the TV with nothing to show why. Refusing them here gives the caller a clear reason.
 */
object HeaderValue {
    const val MAX_LENGTH = 512

    /** Why [value] cannot be sent as the header behind [field], or null when it can (or is absent). */
    fun problem(field: String, value: String?): String? = when {
        value == null -> null
        value.length > MAX_LENGTH -> "$field must be up to $MAX_LENGTH characters"
        value.any { it.isISOControl() } -> "$field cannot contain line breaks or other control characters"
        else -> null
    }
}
