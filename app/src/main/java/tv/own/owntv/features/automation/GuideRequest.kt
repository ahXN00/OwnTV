package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.core.settings.EpgRefresh

/** A guide (XMLTV) source to add, or changes to one. In a change, null keeps the current value. */
data class GuideFields(
    val name: String? = null,
    val url: String? = null,
    val userAgent: String? = null,
    val refresh: EpgRefresh? = null,
)

sealed interface ParsedGuide {
    data class Ok(val fields: GuideFields) : ParsedGuide
    data class Invalid(val reason: String) : ParsedGuide
}

/** Reads `POST /api/v1/guide` (url required) and `PATCH /api/v1/guide/{id}` (at least one field) bodies. */
object GuideRequest {
    val FIELDS = setOf("name", "url", "userAgent", "refresh")

    fun parse(body: JSONObject, adding: Boolean): ParsedGuide {
        val unknown = ApiValues.unknownKeys(body, FIELDS)
        if (unknown.isNotEmpty()) return ParsedGuide.Invalid("Unknown fields: ${unknown.joinToString()}; allowed: ${FIELDS.joinToString()}")
        if (body.length() == 0) return ParsedGuide.Invalid(if (adding) "url is required" else "Nothing to change")
        val fields = JsonFields(body)
        val name = fields.string("name")?.trim()
        val url = fields.string("url")?.trim()
        val userAgent = fields.string("userAgent")?.trim()
        val refreshRaw = fields.string("refresh")
        fields.error?.let { return ParsedGuide.Invalid(it) }
        val refresh = refreshRaw?.let {
            ApiValues.epgRefresh(it) ?: return ParsedGuide.Invalid("refresh must be one of ${ApiValues.epgRefreshModes()} (MANUAL:<days> for a day count)")
        }
        val urlProblem = ApiValues.overlongUrl("url" to url) ?: ApiValues.shortenedUrl("url" to url)
        return when {
            adding && url == null -> ParsedGuide.Invalid("url is required")
            urlProblem != null -> ParsedGuide.Invalid(urlProblem)
            url != null && !ApiValues.isHttpUrl(url) -> ParsedGuide.Invalid("url must be an http(s) address")
            name != null && name.length > ApiValues.MAX_NAME -> ParsedGuide.Invalid("name must be up to ${ApiValues.MAX_NAME} characters")
            else -> HeaderValue.problem("userAgent", userAgent)?.let { ParsedGuide.Invalid(it) }
                ?: ParsedGuide.Ok(GuideFields(name, url, userAgent, refresh))
        }
    }
}
