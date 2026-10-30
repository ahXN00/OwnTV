package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.nav.MainSection

/**
 * How `GET ui` shows the TV. While OwnTV is in the background it says only that, and while a private
 * screen ([SensitiveScreens]) is open only that one is, so a script never learns more than where OwnTV is.
 */
object UiJson {
    /** The whole answer when OwnTV is not in front or a private screen is open; null otherwise. */
    fun reduced(inFront: Boolean, screenOn: Boolean, privateOpen: Boolean): JSONObject? = when {
        !inFront -> JSONObject().put("inFront", false).put("screenOn", screenOn)
        privateOpen -> JSONObject()
            .put("inFront", true)
            .put("screenOn", screenOn)
            .put("screen", PRIVATE)
            .put("sensitive", true)
        else -> null
    }

    /** The answer while OwnTV is in front with no private screen open. */
    fun full(screenOn: Boolean, snapshot: UiSnapshot, activePlaylist: SourceEntity?): JSONObject = JSONObject()
        .put("inFront", true)
        .put("screenOn", screenOn)
        .put("screen", screen(snapshot.section))
        .put("player", snapshot.player.name.lowercase())
        .put("sensitive", false)
        .put("activePlaylist", activePlaylist?.let { JSONObject().put("id", it.id).put("name", it.name) } ?: JSONObject.NULL)
        .put("focused", item(snapshot.focused))
        .put("playing", item(snapshot.playing))

    /** A section's name: the one `ui/navigate` takes, `settings`, or `other` (also when the shell is not up). */
    fun screen(section: MainSection?): String = when {
        section == null -> OTHER
        section == MainSection.SETTINGS -> SETTINGS
        else -> NavTarget.of(section)?.apiName ?: OTHER
    }

    private fun item(item: UiItem?): Any = item?.let {
        JSONObject().put("type", it.type.name.lowercase()).put("id", it.id).put("title", it.title)
    } ?: JSONObject.NULL

    private const val PRIVATE = "private"
    private const val SETTINGS = "settings"
    private const val OTHER = "other"
}
