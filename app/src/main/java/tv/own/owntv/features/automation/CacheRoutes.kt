package tv.own.owntv.features.automation

import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.features.automation.ApiReply.ok

/** The caches the API may empty. Neither holds anything the app cannot download again. */
enum class CacheTarget(val apiName: String) {
    IMAGES("images"),
    METADATA("metadata");

    companion object {
        fun parse(name: String): CacheTarget? = entries.firstOrNull { it.apiName == name.trim().lowercase() }
    }
}

fun interface CacheBackend {
    suspend fun clear(targets: Set<CacheTarget>)
}

fun cacheRoutes(backend: CacheBackend): List<ApiRoute> = listOf(
    ApiRoute(
        "POST", "cache/clear", "Empty caches: images (logos, posters) and metadata (movie and series details); both by default",
        """{"targets"?:[${CacheTarget.entries.joinToString { "\"${it.apiName}\"" }}]}""",
    ) { call ->
        val json = call.json() ?: return@ApiRoute ApiReply.badBody()
        val names = if (json.has("targets")) JsonFields(json).strings("targets") else CacheTarget.entries.map { it.apiName }
        if (names.isNullOrEmpty()) return@ApiRoute ApiReply.invalid("targets must be a non-empty list")
        val targets = names.map { CacheTarget.parse(it) ?: return@ApiRoute ApiReply.invalid("Unknown cache \"$it\"") }.toSet()
        backend.clear(targets)
        ok(JSONObject().put("cleared", JSONArray(targets.map { it.apiName })))
    },
)
