package tv.own.owntv.features.settings

/**
 * Bulk import of IPTV servers from a plain-text file (one server per line).
 *
 * Why this exists alongside backup restore: the `.own` backup file *does* already carry many
 * sources at once (its SOURCES section), but it is a backup container, not a server list — a JSON
 * document plus wallpaper/subtitle assets, optionally sealed whole-file with AES-256-GCM/PBKDF2,
 * with secrets omitted when unencrypted, sources deduped on type+URL+username (+MAC for Stalker)
 * and every id remapped on merge-restore. There is no way to hand the app a plain list of Xtream
 * logins / Stalker portals and have them added. This parser fills exactly that gap; each parsed
 * entry is then added through core's regular [tv.own.owntv.core.setup.SourceImporter] calls, so
 * validation, sync and error handling stay core's.
 *
 * Format (blank lines and `#` comments ignored, fields split on `;` or `|`):
 * ```
 * xtream;My Server;http://example.com:8080;user1;pass1
 * stalker;My Portal;http://portal.example.com:8080/c/;00:1A:79:AA:BB:CC
 * stalker;My Portal;http://portal.example.com;00:1A:79:AA:BB:CC;serial;deviceId;deviceId2;signature
 * ```
 * Shorthands (type inferred from shape):
 * ```
 * My Server;http://example.com:8080;user1;pass1   # 4 fields -> Xtream
 * http://example.com:8080;user1;pass1              # 3 fields, no MAC -> Xtream (auto name)
 * My Portal;http://portal.example.com;00:1A:79:AA:BB:CC  # MAC detected -> Stalker
 * http://portal.example.com;00:1A:79:AA:BB:CC      # 2 fields with MAC -> Stalker (auto name)
 * ```
 */
sealed interface ServerListEntry {
    val name: String

    data class Xtream(
        override val name: String,
        val server: String,
        val username: String,
        val password: String,
    ) : ServerListEntry

    data class Stalker(
        override val name: String,
        val portalUrl: String,
        val mac: String,
        val serialNumber: String = "",
        val deviceId: String = "",
        val deviceId2: String = "",
        val signature: String = "",
    ) : ServerListEntry
}

data class ServerListParseResult(
    val entries: List<ServerListEntry>,
    /** `line number to reason`, for lines that were skipped. */
    val skipped: List<Pair<Int, String>>,
)

private val MAC_PATTERN = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")

fun parseServerList(
    text: String,
    /** Display name for typeless Xtream lines that carry no name (`Server 1`, …). */
    defaultServerName: (Int) -> String,
    /** Display name for typeless Stalker lines that carry no name (`Portal 1`, …). */
    defaultPortalName: (Int) -> String,
): ServerListParseResult {
    val entries = mutableListOf<ServerListEntry>()
    val skipped = mutableListOf<Pair<Int, String>>()
    text.lineSequence().forEachIndexed { index, raw ->
        val lineNo = index + 1
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
        val parts = line.split(';', '|').map { it.trim() }
        val entry = parseServerLine(parts, entries.size, defaultServerName, defaultPortalName)
        if (entry != null) entries.add(entry)
        else skipped.add(lineNo to raw.trim())
    }
    return ServerListParseResult(entries, skipped)
}

private fun parseServerLine(
    parts: List<String>,
    position: Int,
    defaultServerName: (Int) -> String,
    defaultPortalName: (Int) -> String,
): ServerListEntry? {
    if (parts.any { it.isEmpty() }) return null
    val first = parts[0].lowercase()
    return when (first) {
        "xtream" -> {
            if (parts.size < 5) return null
            val (_, name, server, user, pass) = parts.take(5)
            if (name.isBlank() || server.isBlank() || user.isBlank() || pass.isBlank()) return null
            ServerListEntry.Xtream(name, server, user, pass)
        }
        "stalker" -> {
            if (parts.size < 4) return null
            val name = parts[1]
            val portal = parts[2]
            val mac = parts[3]
            if (name.isBlank() || portal.isBlank() || !MAC_PATTERN.matches(mac)) return null
            ServerListEntry.Stalker(
                name = name,
                portalUrl = portal,
                mac = mac,
                serialNumber = parts.getOrElse(4) { "" },
                deviceId = parts.getOrElse(5) { "" },
                deviceId2 = parts.getOrElse(6) { "" },
                signature = parts.getOrElse(7) { "" },
            )
        }
        else -> parseShorthand(parts, position, defaultServerName, defaultPortalName)
    }
}

/** typeless lines: a MAC-shaped field means Stalker, otherwise Xtream. */
private fun parseShorthand(
    parts: List<String>,
    position: Int,
    defaultServerName: (Int) -> String,
    defaultPortalName: (Int) -> String,
): ServerListEntry? {
    val macIndex = parts.indexOfFirst { MAC_PATTERN.matches(it) }
    if (macIndex >= 0) {
        // Stalker: [name,] portal, mac
        val portal = parts.getOrNull(macIndex - 1) ?: return null
        if (!portal.startsWith("http://", ignoreCase = true) && !portal.startsWith("https://", ignoreCase = true)) return null
        val name = parts.getOrNull(0)?.takeIf { macIndex > 1 } ?: defaultPortalName(position + 1)
        if (name.isBlank()) return null
        return ServerListEntry.Stalker(name, portal, parts[macIndex])
    }
    // Without a type keyword the server must be an absolute URL with a host — otherwise an
    // unrelated 3-field line (an M3U entry, `a;b;c`) would import as a bogus Xtream login.
    return when (parts.size) {
        4 -> {
            val (name, server, user, pass) = parts
            if (name.isBlank() || !hasUrlHost(server) || user.isBlank() || pass.isBlank()) null
            else ServerListEntry.Xtream(name, server, user, pass)
        }
        3 -> {
            val (server, user, pass) = parts
            if (!hasUrlHost(server) || user.isBlank() || pass.isBlank()) null
            else ServerListEntry.Xtream(defaultServerName(position + 1), server, user, pass)
        }
        else -> null
    }
}

private fun hasUrlHost(value: String): Boolean =
    runCatching { java.net.URI(value).host }.getOrNull() != null
