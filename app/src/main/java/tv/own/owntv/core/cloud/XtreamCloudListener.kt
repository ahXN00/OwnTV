package tv.own.owntv.core.cloud

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed interface CloudServerState {
    data object Idle : CloudServerState
    data object Starting : CloudServerState
    data class Listening(val port: Int, val urls: List<String>) : CloudServerState
    data class Failed(val message: String) : CloudServerState
}

data class XtreamCloudPayload(
  val sourceType: String = "xtream",
    val name: String,
    val server: String,
    val user: String,
    val pass: String,
  val portalUrl: String = "",
  val mac: String = "",
    val userAgent: String = "",
    val epgUrl: String = "",
    val autoRefresh: String = "OFF",
    val syncLive: Boolean = true,
    val syncMovies: Boolean = true,
    val syncSeries: Boolean = true,
    val isDefault: Boolean = false,
)

/**
 * Tiny embedded HTTP listener used by the Cloud tab. It serves a simple HTML form and accepts the
 * submitted Xtream fields on the same device or any device on the same LAN.
 */
class XtreamCloudListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    @Volatile
    private var serverSocket: ServerSocket? = null

    fun start(port: Int, onPayload: (XtreamCloudPayload) -> Unit): List<String> {
        stop()
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(port))
        serverSocket = socket
        running.set(true)
        scope.launch {
            acceptLoop(socket, onPayload)
        }
        return cloudUrls(port)
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private suspend fun acceptLoop(socket: ServerSocket, onPayload: (XtreamCloudPayload) -> Unit) {
        try {
            while (running.get() && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    if (running.get()) Log.w(TAG, "Cloud listener accept failed", e)
                    break
                }
                scope.launch { handleClient(client, onPayload) }
            }
        } finally {
            stop()
        }
    }

    private fun handleClient(client: Socket, onPayload: (XtreamCloudPayload) -> Unit) {
        client.use { socket ->
            socket.soTimeout = 10_000
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input) ?: return sendResponse(socket, 400, "text/plain; charset=utf-8", "Bad request")
            val parts = requestLine.split(' ')
            if (parts.size < 3) return sendResponse(socket, 400, "text/plain; charset=utf-8", "Bad request")

            val method = parts[0].uppercase()
            val path = parts[1].substringBefore('?')
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }

            if (method == "GET" && (path == "/" || path == "/index.html")) {
                val hostUrls = headers["host"]?.let { host -> listOf("http://$host/") } ?: emptyList()
                val body = renderFormPage(hostUrls.ifEmpty { cloudUrls(socket.localPort) })
                return sendResponse(socket, 200, "text/html; charset=utf-8", body)
            }

            if (method != "POST" || (path != "/xtream" && path != "/stalker" && path != "/source")) {
                return sendResponse(socket, 404, "text/plain; charset=utf-8", "Not found")
            }

            val length = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val bodyBytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val read = input.read(bodyBytes, offset, length - offset)
                if (read <= 0) break
                offset += read
            }
            val bodyText = String(bodyBytes, 0, offset, StandardCharsets.UTF_8)
            val fallbackType = if (path == "/stalker") "stalker" else "xtream"
            val payload = parsePayload(headers["content-type"], bodyText, fallbackType)
              ?: return sendResponse(socket, 400, "text/plain; charset=utf-8", "Missing required fields")

            onPayload(payload)
            val okHtml = renderSavedPage(payload)
            sendResponse(socket, 200, "text/html; charset=utf-8", okHtml)
        }
    }

        private fun parsePayload(contentType: String?, bodyText: String, fallbackType: String): XtreamCloudPayload? {
        val fields = when {
            contentType?.contains("application/json", ignoreCase = true) == true || bodyText.trimStart().startsWith("{") ->
                parseJsonFields(bodyText)
            else -> parseFormFields(bodyText)
        }

          val sourceType = stringField(fields, "type", "sourceType", "source_type")
            .ifBlank { fallbackType }
            .trim()
            .lowercase()

          val portalUrl = stringField(fields, "portalUrl", "portal_url", "url", "server").trim()
          val mac = stringField(fields, "mac", "macAddress", "mac_address").trim()

        val server = stringField(fields, "server", "url", "portalUrl", "portal_url").trim()
        val user = stringField(fields, "user", "username").trim()
        val pass = stringField(fields, "pass", "password").trim()

          if (sourceType == "stalker") {
            if (portalUrl.isBlank() || mac.isBlank()) return null
          } else {
            if (server.isBlank() || user.isBlank() || pass.isBlank()) return null
          }

        return XtreamCloudPayload(
            sourceType = sourceType,
            name = stringField(fields, "name").trim(),
            server = server,
            user = user,
            pass = pass,
            portalUrl = portalUrl,
            mac = mac,
            userAgent = stringField(fields, "userAgent", "user_agent").trim(),
            epgUrl = stringField(fields, "epgUrl", "epg_url").trim(),
            autoRefresh = stringField(fields, "autoRefresh", "auto_refresh").ifBlank { "OFF" },
            syncLive = booleanField(fields, "syncLive", "sync_live", defaultValue = true),
            syncMovies = booleanField(fields, "syncMovies", "sync_movies", defaultValue = true),
            syncSeries = booleanField(fields, "syncSeries", "sync_series", defaultValue = true),
            isDefault = booleanField(fields, "isDefault", "is_default", defaultValue = false),
        )
    }

    private fun parseJsonFields(bodyText: String): Map<String, String> {
        val json = org.json.JSONObject(bodyText)
        val out = LinkedHashMap<String, String>()
        json.keys().forEach { key ->
            val value = json.opt(key)
            when (value) {
                null, org.json.JSONObject.NULL -> Unit
                is Boolean -> out[key] = value.toString()
                is Number -> out[key] = value.toString()
                else -> out[key] = value.toString()
            }
        }
        return out
    }

    private fun parseFormFields(bodyText: String): Map<String, String> {
        if (bodyText.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        bodyText.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val split = pair.split('=', limit = 2)
            val key = urlDecode(split[0])
            val value = urlDecode(split.getOrNull(1).orEmpty())
            if (key.isNotBlank()) out[key] = value
        }
        return out
    }

    private fun stringField(fields: Map<String, String>, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> fields[key]?.takeIf { it.isNotBlank() } } ?: ""

    private fun booleanField(fields: Map<String, String>, primary: String, secondary: String, defaultValue: Boolean): Boolean {
        val raw = fields[primary] ?: fields[secondary] ?: return defaultValue
        return raw.isTruthy() || (!raw.isFalsy() && defaultValue)
    }

    private fun String.isTruthy(): Boolean =
        equals("true", ignoreCase = true) || equals("1") || equals("on", ignoreCase = true) || equals("yes", ignoreCase = true)

    private fun String.isFalsy(): Boolean =
        equals("false", ignoreCase = true) || equals("0") || equals("off", ignoreCase = true) || equals("no", ignoreCase = true)

    private fun renderFormPage(urls: List<String>): String = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>OwnTV Cloud Import</title>
          <style>
            :root {
              color-scheme: dark;
              --bg-0: #030711;
              --bg-1: #091224;
              --surface: rgba(11, 19, 36, 0.92);
              --surface-2: rgba(13, 26, 46, 0.9);
              --line: rgba(116, 154, 194, 0.22);
              --text: #f8fbff;
              --muted: #a6bdd8;
              --primary: #52dbc8;
              --primary-ink: #052b28;
            }
            body {
              margin: 0;
              font-family: "Segoe UI", "Trebuchet MS", sans-serif;
              background:
                radial-gradient(1200px 500px at 10% -10%, #123057 0%, transparent 60%),
                radial-gradient(800px 420px at 100% 0%, #0d3f5d 0%, transparent 62%),
                linear-gradient(160deg, var(--bg-0), var(--bg-1));
              color: var(--text);
            }
            main {
              max-width: 820px;
              margin: 0 auto;
              padding: 32px 20px 48px;
            }
            .card {
              background: var(--surface);
              border: 1px solid var(--line);
              border-radius: 18px;
              padding: 20px;
              margin-top: 18px;
              box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
            }
            .card-title { margin: 0 0 8px; }
            h1, h2 { margin: 0 0 12px; }
            p { line-height: 1.5; color: var(--muted); }
            .urls { display: grid; gap: 8px; margin-top: 12px; }
            .url {
              padding: 10px 12px;
              background: var(--surface-2);
              border-radius: 12px;
              overflow-wrap: anywhere;
              color: var(--text);
            }
            form { display: grid; gap: 14px; }
            label { display: grid; gap: 6px; font-size: 14px; color: var(--text); }
            input, select {
              border: 1px solid var(--line);
              border-radius: 12px;
              padding: 12px 14px;
              background: #0a1427;
              color: var(--text);
              font-size: 16px;
            }
            .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
            .checks { display: grid; gap: 8px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
            .check { display: flex; align-items: center; gap: 8px; }
            .check input { width: 18px; height: 18px; }
            .type-switch {
              display: grid;
              grid-template-columns: repeat(2, minmax(0, 1fr));
              gap: 10px;
              margin-bottom: 14px;
            }
            .type-btn {
              border: 1px solid var(--line);
              border-radius: 12px;
              padding: 10px 12px;
              background: #08152b;
              color: var(--muted);
              font-weight: 700;
              cursor: pointer;
            }
            .type-btn.active {
              border-color: var(--primary);
              background: #0c2738;
              color: #d4fff9;
            }
            .source-panel { display: none; }
            .source-panel.active { display: block; }
            button {
              border: 0;
              border-radius: 12px;
              padding: 14px 16px;
              background: var(--primary);
              color: var(--primary-ink);
              font-weight: 700;
              font-size: 16px;
              cursor: pointer;
            }
            .hint { font-size: 13px; color: #89a8c7; }
            @media (max-width: 720px) {
              .grid, .checks { grid-template-columns: 1fr; }
            }
          </style>
        </head>
        <body>
          <main>
            <h1>OwnTV Cloud Import</h1>
            <p>Open this page from any device on the same network, then submit either Xtream or Stalker details. OwnTV will add it using the same import path as the in-app forms.</p>

            <section class="card">
              <h2>Listener URLs</h2>
              <div class="urls">
                ${urls.joinToString(separator = "\n") { "<div class=\"url\">$it</div>" }}
              </div>
              <p class="hint">Use the root page for the form, or POST directly to <strong>/xtream</strong> (Xtream) / <strong>/stalker</strong> (Stalker).</p>
            </section>

            <section class="card">
              <h2 class="card-title">Add Source</h2>
              <div class="type-switch">
                <button type="button" class="type-btn active" id="xtreamBtn">Xtream</button>
                <button type="button" class="type-btn" id="stalkerBtn">Stalker</button>
              </div>

              <div id="xtreamPanel" class="source-panel active">
                <form method="post" action="/xtream">
                  <input type="hidden" name="type" value="xtream">
                  <div class="grid">
                    <label>Name
                      <input name="name" placeholder="My IPTV">
                    </label>
                    <label>Auto refresh
                      <select name="autoRefresh">
                        <option value="OFF" selected>Off</option>
                        <option value="STARTUP">Refresh at startup</option>
                        <option value="HOURS_6">6 hours</option>
                        <option value="HOURS_12">12 hours</option>
                        <option value="HOURS_24">24 hours</option>
                        <option value="HOURS_48">48 hours</option>
                      </select>
                    </label>
                  </div>
                  <label>Server URL
                    <input name="server" placeholder="http://host:port" required>
                  </label>
                  <div class="grid">
                    <label>Username
                      <input name="user" autocomplete="username" required>
                    </label>
                    <label>Password
                      <input name="pass" type="password" autocomplete="current-password" required>
                    </label>
                  </div>
                  <label>User-Agent
                    <input name="userAgent" placeholder="Optional">
                  </label>
                  <label>EPG URL
                    <input name="epgUrl" placeholder="Optional">
                  </label>
                  <div class="checks">
                    <label class="check"><input type="checkbox" name="syncLive" checked> Sync live TV</label>
                    <label class="check"><input type="checkbox" name="syncMovies" checked> Sync movies</label>
                    <label class="check"><input type="checkbox" name="syncSeries" checked> Sync series</label>
                    <label class="check"><input type="checkbox" name="isDefault"> Make default playlist</label>
                  </div>
                  <button type="submit">Add Xtream source</button>
                </form>
              </div>

              <div id="stalkerPanel" class="source-panel">
                <form method="post" action="/stalker">
                  <input type="hidden" name="type" value="stalker">
                  <div class="grid">
                    <label>Name
                      <input name="name" placeholder="My Portal">
                    </label>
                    <label>Auto refresh
                      <select name="autoRefresh">
                        <option value="OFF" selected>Off</option>
                        <option value="STARTUP">Refresh at startup</option>
                        <option value="HOURS_6">6 hours</option>
                        <option value="HOURS_12">12 hours</option>
                        <option value="HOURS_24">24 hours</option>
                        <option value="HOURS_48">48 hours</option>
                      </select>
                    </label>
                  </div>
                  <label>Portal URL
                    <input name="portalUrl" placeholder="http://host:port/c/" required>
                  </label>
                  <label>MAC address
                    <input name="mac" placeholder="00:1A:79:AA:BB:CC" required>
                  </label>
                  <label>User-Agent
                    <input name="userAgent" placeholder="Optional">
                  </label>
                  <label class="check"><input type="checkbox" name="isDefault"> Make default playlist</label>
                  <button type="submit">Add Stalker source</button>
                </form>
              </div>
            </section>
          </main>
          <script>
              const xtreamBtn = document.getElementById('xtreamBtn');
              const stalkerBtn = document.getElementById('stalkerBtn');
              const xtreamPanel = document.getElementById('xtreamPanel');
              const stalkerPanel = document.getElementById('stalkerPanel');

              function showSource(type) {
                const xtream = type === 'xtream';
                xtreamBtn.classList.toggle('active', xtream);
                stalkerBtn.classList.toggle('active', !xtream);
                xtreamPanel.classList.toggle('active', xtream);
                stalkerPanel.classList.toggle('active', !xtream);
              }

              xtreamBtn.addEventListener('click', () => showSource('xtream'));
              stalkerBtn.addEventListener('click', () => showSource('stalker'));
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun renderSavedPage(payload: XtreamCloudPayload): String = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>OwnTV Cloud Import Saved</title>
          <style>
            body {
              margin: 0;
              font-family: Arial, Helvetica, sans-serif;
              background: #020617;
              color: #e2e8f0;
              display: grid;
              place-items: center;
              min-height: 100vh;
              padding: 24px;
            }
            .card {
              max-width: 620px;
              width: 100%;
              background: rgba(15, 23, 42, 0.94);
              border: 1px solid rgba(148, 163, 184, 0.18);
              border-radius: 18px;
              padding: 24px;
              box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
            }
            a { color: #38bdf8; }
          </style>
        </head>
        <body>
          <div class="card">
            <h1>Saved</h1>
            <p>OwnTV received <strong>${payload.name.ifBlank { if (payload.sourceType == "stalker") "My Portal" else "My IPTV" }}</strong> and started the ${payload.sourceType.uppercase()} import flow.</p>
            <p>You can submit another source from the form, or go back to the listener page.</p>
            <p><a href="/">Back to form</a></p>
          </div>
        </body>
        </html>
    """.trimIndent()

    private fun sendResponse(socket: Socket, code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "OK"
        }
        socket.getOutputStream().use { output ->
            val header = buildString {
                append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(bytes.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(header.toByteArray(StandardCharsets.UTF_8))
            output.write(bytes)
            output.flush()
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next == -1) {
                if (buffer.size() == 0) return null
                break
            }
            if (next == '\n'.code) break
            if (next != '\r'.code) buffer.write(next)
        }
        return buffer.toString(StandardCharsets.UTF_8.name())
    }

    private fun urlDecode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    companion object {
        private const val TAG = "XtreamCloud"

      fun cloudUrls(port: Int): List<String> {
        val lanHosts = buildList {
          Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { iface ->
            if (!iface.isUp || iface.isLoopback || iface.isVirtual) return@forEach
            Collections.list(iface.inetAddresses)
              .filterIsInstance<Inet4Address>()
              .mapNotNull { address ->
                val host = address.hostAddress?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (address.isLoopbackAddress || address.isLinkLocalAddress) null else host
              }
              .forEach { host -> add(host) }
          }
        }.distinct()

        val preferredHost = lanHosts.firstOrNull { host ->
          host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.startsWith("172.")
        } ?: lanHosts.firstOrNull() ?: "127.0.0.1"

        return listOf("http://$preferredHost:$port/")
      }
    }
}
