package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom

/** What `POST setup/finish` applies before it opens the profile. Null leaves a value as it is. */
data class SetupFinish(val language: String? = null, val uiZoom: Int? = null, val fontSize: Int? = null) {
    /** The values under their settings keys ([ApiSettingKeys]), for the import `PATCH settings` uses. */
    fun settings(): JSONObject = JSONObject().apply {
        language?.let { put(LANGUAGE, it) }
        uiZoom?.let { put(UI_ZOOM, it) }
        fontSize?.let { put(FONT_SIZE, it) }
    }

    companion object {
        private const val LANGUAGE = "ui_language"
        private const val UI_ZOOM = "ui_zoom_percent"
        private const val FONT_SIZE = "font_size_percent"
        private val FIELDS = mapOf(LANGUAGE to "language", UI_ZOOM to "uiZoom", FONT_SIZE to "fontSize")

        /** The request field a settings key from [settings] came from, so the answer names what was asked. */
        fun fieldOf(key: String): String = FIELDS.getValue(key)
    }
}

sealed interface ParsedFinish {
    data class Ok(val finish: SetupFinish) : ParsedFinish
    data class Invalid(val reason: String) : ParsedFinish
}

/**
 * Reads a `POST setup/finish` body. The language must be one the wizard offers ([languages], "" for the
 * system's), in any letter case; the sizes must be ones the wizard's Display size page can step to.
 */
object SetupFinishRequest {
    val FIELDS = setOf("language", "uiZoom", "fontSize")

    fun parse(body: JSONObject, languages: Set<String>): ParsedFinish {
        val unknown = ApiValues.unknownKeys(body, FIELDS)
        if (unknown.isNotEmpty()) return ParsedFinish.Invalid("Unknown fields: ${unknown.joinToString()}; allowed: ${FIELDS.joinToString()}")
        val fields = JsonFields(body)
        val language = fields.string("language")?.trim()
        val uiZoom = fields.int("uiZoom")
        val fontSize = fields.int("fontSize")
        fields.error?.let { return ParsedFinish.Invalid(it) }
        val tag = language?.let { raw ->
            languages.firstOrNull { it.equals(raw, ignoreCase = true) }
                ?: return ParsedFinish.Invalid("language must be \"\" (the system's) or one of ${languages.filter { it.isNotEmpty() }.sorted().joinToString()}")
        }
        val problem = stepProblem("uiZoom", uiZoom, UiZoom.MIN, UiZoom.MAX, UiZoom.STEP)
            ?: stepProblem("fontSize", fontSize, UiFontScale.MIN, UiFontScale.MAX, UiFontScale.STEP)
        return problem?.let { ParsedFinish.Invalid(it) } ?: ParsedFinish.Ok(SetupFinish(tag, uiZoom, fontSize))
    }

    private fun stepProblem(key: String, value: Int?, min: Int, max: Int, step: Int): String? =
        value?.takeIf { it !in min..max || (it - min) % step != 0 }?.let { "$key must be $min to $max in steps of $step" }
}
