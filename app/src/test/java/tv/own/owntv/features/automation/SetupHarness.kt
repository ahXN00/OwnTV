package tv.own.owntv.features.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import tv.own.owntv.core.database.entity.ProfileEntity

/**
 * First-run setup wired as the app wires it: a real [AutomationSetupSession] over an API switch, pairing
 * window and paired devices kept here, the [SetupProfile] the routes ask, and the [PlaylistTarget] of a
 * TV with no profile open yet, as on the wizard. Setup is done once [activeProfileId] holds a profile.
 */
class SetupHarness {
    var enabled = false
    var pairingOpen = false
    val devices = mutableSetOf<String>()
    val activeProfileId = MutableStateFlow(-1L)
    val profileDao = FakeProfileDao()
    private var offBefore = false

    val session = AutomationSetupSession(
        enabled = { enabled },
        setEnabled = { enabled = it; if (!it) pairingOpen = false },
        openPairing = { pairingOpen = true },
        closePairing = { pairingOpen = false },
        deviceIds = { devices.toSet() },
        unpair = { devices -= it },
        offBefore = { offBefore },
        setOffBefore = { offBefore = it },
        scope = CoroutineScope(Dispatchers.Unconfined),
    )
    val setup = SetupProfile(activeProfileId, profileDao, session)
    val target = PlaylistTarget(openId = { null }, openPlaylists = { emptyList() }, setup = setup)

    /** The page opens and a computer pairs from it. */
    fun pair() {
        session.start()
        devices += PAIRED
    }

    suspend fun profile(name: String, pinHash: String? = null): Long =
        profileDao.insert(ProfileEntity(name = name, avatarColor = 0, pinHash = pinHash))

    companion object {
        const val PAIRED = "laptop"
    }
}
