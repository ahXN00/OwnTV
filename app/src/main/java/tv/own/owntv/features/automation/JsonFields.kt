package tv.own.owntv.features.automation

import org.json.JSONObject

/**
 * Typed reads from a request body. An absent key reads as null; a key holding the wrong JSON type
 * also reads as null and records the first such problem in [error], so a caller can refuse the body.
 */
class JsonFields(private val json: JSONObject) {
    var error: String? = null
        private set

    fun has(key: String): Boolean = json.has(key)

    fun string(key: String): String? = read(key, "a string") { it as? String }

    fun boolean(key: String): Boolean? = read(key, "true or false") { it as? Boolean }

    fun int(key: String): Int? = read(key, "a whole number") { value ->
        (value as? Number)?.takeIf { it.toDouble() == it.toLong().toDouble() }?.toLong()
            ?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    }

    fun strings(key: String): List<String>? = read(key, "a list of strings") { value ->
        val array = value as? org.json.JSONArray ?: return@read null
        (0 until array.length()).map { array.get(it) as? String ?: return@read null }
    }

    /** Records [message] as the problem, unless an earlier one is already recorded. */
    fun fail(message: String) {
        if (error == null) error = message
    }

    private fun <T> read(key: String, expected: String, convert: (Any) -> T?): T? {
        if (!json.has(key)) return null
        return convert(json.get(key)) ?: null.also { fail("$key must be $expected") }
    }
}
