package tv.own.owntv.features.automation

import org.json.JSONObject

/**
 * A search endpoint's parameters: `q` (trimmed; empty finds everything), `playlist` (one playlist id,
 * or every playlist when absent) and `limit` (1 to [maxLimit], [defaultLimit] when absent).
 */
class SearchParams(private val defaultLimit: Int, private val maxLimit: Int) {
    /** How `GET /api/v1` lists the parameters. */
    val hint = "?q=&playlist=<id>&limit=1..$maxLimit (default $defaultLimit)"

    /** Hands the parameters of [call] to [find] and answers with what it finds, or with the 400 for the first wrong one. */
    suspend fun answer(call: ApiCall, find: suspend (query: String, playlistId: Long?, limit: Int) -> JSONObject): HttpResponse {
        val limit = call.query("limit")?.let { raw ->
            raw.toIntOrNull()?.takeIf { it in 1..maxLimit } ?: return ApiReply.invalid("limit must be 1 to $maxLimit")
        } ?: defaultLimit
        val playlist = call.query("playlist")?.let { raw ->
            raw.toLongOrNull() ?: return ApiReply.invalid("playlist must be a playlist id")
        }
        return ApiReply.ok(find(call.query("q").orEmpty().trim(), playlist, limit))
    }
}
