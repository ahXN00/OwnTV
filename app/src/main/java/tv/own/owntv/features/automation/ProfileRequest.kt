package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.ui.components.OwnTVAvatars

/** A profile to add through the API. [avatar] -1 is the letter tile new profiles start on. */
data class NewProfile(val name: String, val kids: Boolean = false, val avatar: Int = NO_AVATAR) {
    companion object {
        const val NO_AVATAR = -1
    }
}

sealed interface ParsedProfile {
    data class Ok(val profile: NewProfile) : ParsedProfile
    data class Invalid(val reason: String) : ParsedProfile
}

/** Reads a `POST profiles` body. A PIN is refused by name: one is only ever set at the TV. */
object ProfileRequest {
    val FIELDS = setOf("name", "kids", "avatar")

    fun parse(body: JSONObject): ParsedProfile {
        if (body.has("pin")) return ParsedProfile.Invalid("A PIN cannot be set through the API; add one on the TV")
        val unknown = ApiValues.unknownKeys(body, FIELDS)
        if (unknown.isNotEmpty()) return ParsedProfile.Invalid("Unknown fields: ${unknown.joinToString()}; allowed: ${FIELDS.joinToString()}")
        val fields = JsonFields(body)
        val name = fields.string("name")?.trim()
        val kids = fields.boolean("kids") ?: false
        val avatar = fields.int("avatar") ?: NewProfile.NO_AVATAR
        fields.error?.let { return ParsedProfile.Invalid(it) }
        return when {
            name.isNullOrEmpty() -> ParsedProfile.Invalid("name is required")
            name.length > ApiValues.MAX_NAME -> ParsedProfile.Invalid("name must be up to ${ApiValues.MAX_NAME} characters")
            name.codePoints().anyMatch(::unseen) ->
                ParsedProfile.Invalid("name cannot hold control or invisible format characters")
            avatar !in NewProfile.NO_AVATAR until OwnTVAvatars.COUNT ->
                ParsedProfile.Invalid("avatar must be ${NewProfile.NO_AVATAR} (a letter tile) to ${OwnTVAvatars.COUNT - 1}")
            else -> ParsedProfile.Ok(NewProfile(name, kids, avatar))
        }
    }

    /** A control character, or a format one that shows nothing itself: a zero-width space, a direction override. */
    private fun unseen(codePoint: Int): Boolean =
        Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT.toInt()
}
