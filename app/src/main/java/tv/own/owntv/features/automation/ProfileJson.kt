package tv.own.owntv.features.automation

import org.json.JSONObject
import tv.own.owntv.core.database.entity.ProfileEntity

/** A profile as the API shows it: whether a PIN locks it, never the PIN's hash or the picture's path. */
object ProfileJson {
    fun of(profile: ProfileEntity, active: Boolean): JSONObject = JSONObject()
        .put("id", profile.id)
        .put("name", profile.name)
        .put("kids", profile.isKids)
        .put("hasPin", profile.pinHash != null)
        .put("active", active)
}
