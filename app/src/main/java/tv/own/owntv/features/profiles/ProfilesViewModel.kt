package tv.own.owntv.features.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.profile.ProfileManager
import tv.own.owntv.core.util.Pin
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Phase 6.5 — profile creation/switching and the launch gate's data. Shared by the "Who's watching?"
 * gate and the Settings → Profiles management screen.
 */
class ProfilesViewModel(
    private val profileDao: ProfileDao,
    private val settings: SettingsRepository,
    private val manager: ProfileManager,
) : ViewModel() {

    // Eagerly on purpose: MainActivity's splash gate blocks the first frame on this list, so the
    // query has to start when the ViewModel is built, not on first collection inside composition.
    // Measured: WhileSubscribed here cost ~1.3s of cold start.
    //
    // Loading is deliberately distinct from Loaded(emptyList()). An empty list can mean either a
    // fresh install or a restore/recovery window; treating both as the same value let MainActivity
    // enter the shell while Room was still deciding whether the active profile was PIN-locked.
    val profileState: StateFlow<ProfileLoadState> = profileDao.observeAll()
        .map<List<ProfileEntity>, ProfileLoadState> { ProfileLoadState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileLoadState.Loading)

    /** The persisted profile currently shown by the app. Settings screens use this to distinguish
     * deleting the active profile (which must invalidate the gate session) from deleting an
     * unrelated profile (which must leave the current session alone). */
    val activeProfileId: StateFlow<Long> = settings.activeProfileId
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    /** Compatibility projection for feature screens that only render an already-loaded list. */
    val profiles: StateFlow<List<ProfileEntity>> = profileState
        .map { (it as? ProfileLoadState.Loaded)?.profiles.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Make [profile] active (routes the app into the shell) once the preference write commits. */
    fun switchTo(profile: ProfileEntity, onSwitched: () -> Unit = {}) {
        viewModelScope.launch {
            manager.switchTo(profile.id)
            onSwitched()
        }
    }

    fun verifyPin(profile: ProfileEntity, pin: String): Boolean = manager.verifyPin(profile, pin)

    /**
     * Create a new profile. New profiles inherit the existing sources (single-account, multi-viewer)
     * so they immediately have content; favorites/history stay per-profile.
     */
    fun create(name: String, avatarId: Int, isKids: Boolean, pin: String?, defaultName: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            onCreated(manager.create(name, avatarId, isKids, pin, defaultName))
        }
    }

    /** Apply edits from the editor dialog. [pin]: null = keep the existing PIN, "" = remove it. */
    fun edit(profile: ProfileEntity, name: String, avatarId: Int, isKids: Boolean, pin: String?) {
        viewModelScope.launch { manager.edit(profile, name, avatarId, isKids, pin) }
    }

    fun rename(profile: ProfileEntity, name: String) {
        viewModelScope.launch { profileDao.update(profile.copy(name = name.ifBlank { profile.name })) }
    }

    fun setKids(profile: ProfileEntity, isKids: Boolean) {
        viewModelScope.launch { profileDao.update(profile.copy(isKids = isKids)) }
    }

    fun setPin(profile: ProfileEntity, pin: String?) {
        viewModelScope.launch { profileDao.setPin(profile.id, pin?.takeIf { it.isNotBlank() }?.let { Pin.hash(it) }) }
    }

    fun delete(profile: ProfileEntity) {
        viewModelScope.launch { manager.delete(profile) }
    }
}

/** Room has not emitted yet versus a completed query, including a legitimately empty result. */
sealed interface ProfileLoadState {
    data object Loading : ProfileLoadState
    data class Loaded(val profiles: List<ProfileEntity>) : ProfileLoadState
}

/**
 * The shell may only be composed after both sources of the launch decision are known. This small
 * policy is kept pure so the cold-start PIN invariant can be tested without a Compose test harness:
 * a persisted active id arriving before Room's locked profile must never be enough to enter the
 * shell, and a loaded empty result must never be treated as permission to enter it. Authentication
 * is bound to the active profile id, so a session that authenticated profile A cannot authorize B.
 */
internal fun shellMayCompose(
    profileState: ProfileLoadState,
    activeProfileId: Long?,
    authenticatedProfileId: Long?,
    gateRequired: Boolean,
): Boolean = tv.own.owntv.core.profile.shellMayCompose(
    profiles = (profileState as? ProfileLoadState.Loaded)?.profiles,
    activeProfileId = activeProfileId,
    authenticatedProfileId = authenticatedProfileId,
    gateRequired = gateRequired,
)

/** One unlocked profile enters the shell immediately; all chooser/PIN cases stay gated. Core's rule
 *  — the phone applies the same one. */
internal fun profileGateRequired(profiles: List<ProfileEntity>): Boolean =
    tv.own.owntv.core.profile.profileGateRequired(profiles)
