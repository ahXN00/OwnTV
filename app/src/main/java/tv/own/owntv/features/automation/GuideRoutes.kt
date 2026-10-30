package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.features.automation.ApiReply.accepted
import tv.own.owntv.features.automation.ApiReply.notFound
import tv.own.owntv.features.automation.ApiReply.ok

/** What the guide routes ask of the app. The Android side is [AutomationGuide]. */
interface GuideBackend {
    suspend fun guides(): JSONObject
    /** Null when there is no guide source with that id; likewise for every id-taking call below. */
    suspend fun guide(id: Long): JSONObject?
    /** Adds the source and starts its first download; returns it. */
    suspend fun add(fields: GuideFields): JSONObject
    suspend fun update(id: Long, fields: GuideFields): Boolean
    suspend fun resync(id: Long): Boolean
    suspend fun cancelSync(id: Long): Boolean
    suspend fun delete(id: Long): Boolean
}

/**
 * The TV guide (EPG) sources: Settings › EPG, including the feeds a playlist registered there. Their
 * ids are negative, so they never collide with a playlist id.
 */
fun guideRoutes(backend: GuideBackend): List<ApiRoute> = listOf(
    ApiRoute("GET", "guide", "Guide sources with programme counts, schedule, last result and sync state") { ok(backend.guides()) },
    ApiRoute("POST", "guide", "Add a guide source and download it", """{"url","name"?,"userAgent"?,"refresh"?}""") { call ->
        val json = call.json() ?: return@ApiRoute ApiReply.badBody()
        when (val parsed = GuideRequest.parse(json, adding = true)) {
            is ParsedGuide.Invalid -> ApiReply.invalid(parsed.reason)
            is ParsedGuide.Ok -> ok(backend.add(parsed.fields))
        }
    },
    ApiRoute("GET", "guide/{id}", "One guide source") { call -> backend.guide(call.id())?.let(::ok) ?: notFound("guide source", call.id()) },
    ApiRoute(
        "PATCH", "guide/{id}", "Edit a guide source; a new name, url or user agent re-downloads it",
        """{"name"?,"url"?,"userAgent"?,"refresh"?}""",
    ) { call ->
        val json = call.json() ?: return@ApiRoute ApiReply.badBody()
        when (val parsed = GuideRequest.parse(json, adding = false)) {
            is ParsedGuide.Invalid -> ApiReply.invalid(parsed.reason)
            is ParsedGuide.Ok ->
                if (backend.update(call.id(), parsed.fields)) ok(JSONObject().put("status", "saved")) else notFound("guide source", call.id())
        }
    },
    ApiRoute("DELETE", "guide/{id}", "Delete a guide source and its programmes") { call ->
        if (backend.delete(call.id())) accepted("deleting") else notFound("guide source", call.id())
    },
    ApiRoute("POST", "guide/{id}/sync", "Download the guide now") { call ->
        if (backend.resync(call.id())) accepted("syncing") else notFound("guide source", call.id())
    },
    ApiRoute("DELETE", "guide/{id}/sync", "Cancel a running guide download") { call ->
        if (backend.cancelSync(call.id())) accepted("cancelled") else notFound("guide source", call.id())
    },
)
