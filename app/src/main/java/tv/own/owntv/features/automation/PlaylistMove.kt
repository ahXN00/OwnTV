package tv.own.owntv.features.automation

import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import java.net.URI

/**
 * Whether a playlist edit may keep the stored login, and what it must carry when it may not.
 *
 * The app sends a playlist's stored login (the Xtream password, the Stalker MAC) to whatever server
 * the playlist names. If an edit could change the server and keep that login, any paired device could
 * point the playlist at a machine of its own and collect the login at the next refresh. So an edit
 * that changes the scheme, host or port must send the login again: a caller that knows it gains
 * nothing, and one that does not cannot move it. A new username or MAC makes a new login, so it needs
 * the password too: the stored password only ever goes out with the login it was saved with. Other
 * edits on the same server keep the stored login. A blank value is never taken for one sent, because
 * saving a blank keeps the stored value (SourceActions.update).
 */
object PlaylistMove {
    /** Why [patch] cannot be applied to [source], or null when it can. */
    fun refusal(source: SourceEntity, patch: PlaylistPatch): String? {
        if (patch.mac != null && source.type != SourceType.STALKER) return "mac applies only to Stalker playlists"
        val passwordMissing = needsPassword(source) && patch.password.isNullOrBlank()
        if (patch.url != null && changesServer(source.url, patch.url)) {
            val missing = listOfNotNull(
                "password".takeIf { passwordMissing },
                "mac".takeIf { source.type == SourceType.STALKER && patch.mac.isNullOrBlank() },
            )
            if (missing.isEmpty()) return null
            return "url points to another server, so send ${missing.joinToString(" and ")} again with it"
        }
        val newLogin = listOfNotNull("username".takeIf { patch.username != null }, "mac".takeIf { patch.mac != null })
        if (newLogin.isEmpty() || !passwordMissing) return null
        return "a new ${newLogin.joinToString(" or ")} is a new login, so send password with it"
    }

    /**
     * True when [new] differs from [stored] in scheme, host or port. A missing port counts as the
     * scheme's default, host case is ignored, and a stored address that cannot be read counts as
     * moved, since then nothing shows the new one is the same server.
     */
    fun changesServer(stored: String, new: String): Boolean {
        val before = origin(stored) ?: return true
        return before != origin(new)
    }

    private fun needsPassword(source: SourceEntity) = source.type == SourceType.XTREAM || !source.password.isNullOrEmpty()

    private fun origin(url: String): Triple<String, String, Int>? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val port = when {
            uri.port >= 0 -> uri.port
            scheme == "https" -> HTTPS_PORT
            else -> HTTP_PORT
        }
        return Triple(scheme, host, port)
    }

    private const val HTTP_PORT = 80
    private const val HTTPS_PORT = 443
}
