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
 * Profile creation/switching and the launch gate's data. Shared by the "Who's watching?"
 * gate and the Settings › Profile page.
 */
class ProfilesViewModel(
    private val profileDao: ProfileDao,
    private val settings: SettingsRepository,
    private val manager: ProfileManager,
    private val protection: tv.own.owntv.core.profile.ProfileProtection,
) : ViewModel() {

    /**
     * Whether changing [profile] from the profile in use ([activeId]) needs one of its PINs first: it
     * has a profile lock or a Settings lock, and it is not the one in use.
     */
    suspend fun changeNeedsPin(profile: ProfileEntity, activeId: Long): Boolean =
        profile.id != activeId && protection.isProtected(profile)

    /** A protected profile's profile PIN or Settings PIN. */
    suspend fun verifyProtected(profile: ProfileEntity, pin: String) = protection.verify(profile, pin)

    /** Whether [profileId] may download (Settings › Profile › the profile › Allow downloads). */
    fun downloadsAllowed(profileId: Long): kotlinx.coroutines.flow.Flow<Boolean> = settings.downloadsAllowed(profileId)

    fun setDownloadsAllowed(profileId: Long, allowed: Boolean) {
        viewModelScope.launch { settings.setDownloadsAllowed(profileId, allowed) }
    }

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
     * so they immediately have content; favorites/history stay per-profile. [picture], when the user
     * chose one, is stored once the profile exists — before that there is no id to store it under.
     */
    fun create(name: String, avatarId: Int, isKids: Boolean, pin: String?, defaultName: String, picture: java.io.File? = null, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = manager.create(name, avatarId, isKids, pin, defaultName)
            if (picture != null) setPictureNow(id, picture)
            onCreated(id)
        }
    }

    /** Give the profile [profileId] the picture in [source]; core copies and scales it. */
    fun setPicture(profileId: Long, source: java.io.File) {
        viewModelScope.launch { setPictureNow(profileId, source) }
    }

    /** Drop [profileId]'s own picture; the drawn tile it already had comes back. */
    fun clearPicture(profileId: Long) {
        viewModelScope.launch { profileDao.getById(profileId)?.let { manager.clearCustomAvatar(it) } }
    }

    // Read fresh by id rather than taking a caller's entity: the picture is written as a copy of the
    // whole row, so a stale one would put back whatever the editor has just changed.
    private suspend fun setPictureNow(profileId: Long, source: java.io.File) {
        profileDao.getById(profileId)?.let { manager.setCustomAvatar(it, source) }
    }

    /**
     * Apply edits from the editor dialog. [pin]: null = keep the existing PIN, "" = remove it.
     * Edits the row as it is now, not [profile]: a picture chosen while the dialog was open has
     * already been written, and the dialog's copy would put the old one back.
     */
    fun edit(profile: ProfileEntity, name: String, avatarId: Int, isKids: Boolean, pin: String?) {
        viewModelScope.launch { manager.edit(profileDao.getById(profile.id) ?: profile, name, avatarId, isKids, pin) }
    }

    fun rename(profile: ProfileEntity, name: String) {
        viewModelScope.launch { profileDao.update(profile.copy(name = name.ifBlank { profile.name })) }
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
