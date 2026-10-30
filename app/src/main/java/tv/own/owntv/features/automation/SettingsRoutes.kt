package tv.own.owntv.features.automation

import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.features.automation.ApiReply.ok

/** What the settings routes ask of the app. The Android side is [AutomationAppSettings]. */
interface SettingsBackend {
    /** The settings a backup carries; a key that is absent still has the app's default. */
    suspend fun export(): JSONObject
    /** Applies the keys present, the same way restoring a backup does; bad values are skipped. */
    suspend fun import(changes: JSONObject)
}

/**
 * The app settings in [ApiSettingKeys] (player, buffer, guide, look, language...) under the names a
 * settings backup uses. A PATCH holding any other key is refused whole, naming the keys, so nothing
 * half-applies. Otherwise it answers which keys took: a key comes back under `notApplied` when its
 * value is the wrong type or out of range.
 */
fun settingsRoutes(backend: SettingsBackend): List<ApiRoute> = listOf(
    ApiRoute("GET", "settings", "Changed settings the API may edit, by key; keys not listed are at their default") {
        ok(ApiSettingKeys.visible(backend.export()))
    },
    ApiRoute("PATCH", "settings", "Change settings, using the keys from GET settings", """{"<key>":<value>,...}""") { call ->
        val changes = call.json() ?: return@ApiRoute ApiReply.badBody()
        if (changes.length() == 0) return@ApiRoute ApiReply.invalid("Nothing to change")
        val rejected = ApiSettingKeys.rejected(changes)
        if (rejected.isNotEmpty()) return@ApiRoute ApiReply.invalid("Not settable through the API: ${rejected.joinToString()}")
        backend.import(changes)
        ok(SettingsDiff.of(changes, backend.export()))
    },
)

/** Which requested values the app now holds. */
object SettingsDiff {
    /** Each key is reported as [name] gives it: the name the caller asked by, when that is not the key. */
    fun of(requested: JSONObject, now: JSONObject, name: (String) -> String = { it }): JSONObject {
        val applied = JSONArray()
        val notApplied = JSONArray()
        requested.keys().forEach { key ->
            val took = now.has(key) && same(requested.get(key), now.get(key))
            (if (took) applied else notApplied).put(name(key))
        }
        return JSONObject().put("applied", applied).put("notApplied", notApplied)
    }

    private fun same(a: Any, b: Any): Boolean = when {
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        a is JSONArray && b is JSONArray -> strings(a) == strings(b)
        else -> a == b
    }

    private fun strings(array: JSONArray): Set<String> = (0 until array.length()).mapTo(mutableSetOf()) { array.optString(it) }
}
