package tv.own.owntv.features.automation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.profile.ProfileManager

/**
 * Opens a profile for the API once setup is done. Playback stops first, as switch profile on the rail
 * stops it before the gate (the rail leaves that to its caller too); then the profile becomes active,
 * and "Who's watching?", which comes up for the change, picks it ([ProfileChoice]).
 */
class ProfileSwitch(private val stop: PlayerStop, private val manager: ProfileManager, private val choice: ProfileChoice) {
    suspend fun switchTo(profileId: Long) {
        withContext(Dispatchers.Main) { stop.stop() }
        choice.choose(profileId)
        manager.switchTo(profileId)
    }
}
