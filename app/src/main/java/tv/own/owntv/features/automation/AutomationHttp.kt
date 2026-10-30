package tv.own.owntv.features.automation

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URLDecoder

/** One parsed request. Header names are lower-cased; the body is UTF-8 text. */
data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: String,
)

/** A request's head: everything but its body, which is [contentLength] bytes. Header names are lower-cased. */
data class HttpHead(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val contentLength: Int,
)

data class HttpResponse(val status: Int, val body: String)

/**
 * The little HTTP the automation server speaks: one request per connection, a bounded header block,
 * a bounded body, no chunked uploads. Anything malformed or oversized reads as null and the
 * connection is dropped — the clients are scripts we write ourselves, not browsers. The head and the
 * body are read in two steps, so a request can be refused before its body is read ([AutomationRoutes.answer]).
 */
object AutomationHttp {
    const val MAX_HEAD_BYTES = 16 * 1024
    const val MAX_BODY_BYTES = 64 * 1024

    /** Reads one request's head and none of its body; null when it is malformed or announces too long a body. */
    fun readHead(input: BufferedInputStream): HttpHead? {
        val lines = (readHeadText(input) ?: return null).split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) return null
        val headers = lines.drop(1).mapNotNull(::header).toMap()
        if ("transfer-encoding" in headers) return null
        val length = headers["content-length"]?.let { it.toIntOrNull() ?: return null } ?: 0
        if (length !in 0..MAX_BODY_BYTES) return null
        val target = parts[1]
        val q = target.indexOf('?')
        return HttpHead(
            method = parts[0].uppercase(),
            path = if (q < 0) target else target.substring(0, q),
            query = if (q < 0) emptyMap() else parseQuery(target.substring(q + 1)),
            headers = headers,
            contentLength = length,
        )
    }

    /** Reads the body [head] announces; null when the stream ends before all of it is in. */
    fun readBody(input: BufferedInputStream, head: HttpHead): HttpRequest? {
        val body = readExactly(input, head.contentLength) ?: return null
        return HttpRequest(head.method, head.path, head.query, head.headers, String(body, Charsets.UTF_8))
    }

    fun writeResponse(out: OutputStream, response: HttpResponse) {
        val body = response.body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ${response.status} ${reason(response.status)}\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    fun parseQuery(query: String): Map<String, String> = query.split('&').mapNotNull { pair ->
        if (pair.isEmpty()) return@mapNotNull null
        val split = pair.split('=', limit = 2)
        runCatching { decode(split[0]) to decode(split.getOrElse(1) { "" }) }.getOrNull()
    }.toMap()

    private fun decode(s: String) = URLDecoder.decode(s, "UTF-8")

    private fun header(line: String): Pair<String, String>? {
        val i = line.indexOf(':')
        if (i <= 0) return null
        return line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
    }

    /** Up to the blank line that ends the headers, without it; null past [MAX_HEAD_BYTES] or at EOF. */
    private fun readHeadText(input: BufferedInputStream): String? {
        val buf = ByteArrayOutputStream()
        var matched = 0
        while (buf.size() < MAX_HEAD_BYTES) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            matched = when {
                b == HEAD_END[matched].code -> matched + 1
                b == '\r'.code -> 1
                else -> 0
            }
            if (matched == HEAD_END.length) {
                val bytes = buf.toByteArray()
                return String(bytes, 0, bytes.size - HEAD_END.length, Charsets.ISO_8859_1)
            }
        }
        return null
    }

    private fun readExactly(input: BufferedInputStream, length: Int): ByteArray? {
        val out = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(out, read, length - read)
            if (n < 0) return null
            read += n
        }
        return out
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        202 -> "Accepted"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        409 -> "Conflict"
        415 -> "Unsupported Media Type"
        429 -> "Too Many Requests"
        else -> "Internal Server Error"
    }

    private const val HEAD_END = "\r\n\r\n"
}
