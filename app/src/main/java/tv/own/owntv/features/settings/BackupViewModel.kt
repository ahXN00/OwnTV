package tv.own.owntv.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.BackupManager
import java.io.File

/** Drives Backup & Restore (selective export/import to a JSON file). */
enum class DoneKind { EXPORTED, RESTORED }
enum class BackupError { EXPORT, READ, IMPORT }

class BackupViewModel(
    private val backup: BackupManager,
    private val profileDao: tv.own.owntv.core.database.dao.ProfileDao,
    private val settings: tv.own.owntv.core.settings.SettingsRepository,
    private val companion: tv.own.owntv.core.companion.CompanionController,
    private val protection: tv.own.owntv.core.profile.ProfileProtection,
) : ViewModel() {

    // ---- Remote restore: another device uploads a backup JSON to the TV over the LAN companion server. ----
    /** Server lifecycle (Idle / Starting / Listening with PIN+QR / Failed) for the remote-restore panel. */
    val remoteState get() = companion.state

    /** Uploaded backup files — the screen collects this and feeds each into [inspect]. */
    val remoteBackups get() = companion.backups

    fun startRemoteRestore(port: Int = tv.own.owntv.core.companion.CompanionLink.DEFAULT_PORT) =
        companion.startForBackupRestore(port)

    fun stopRemoteRestore() = companion.stop()

    /** Exports to an app-internal cache file, then serves it over the companion server for another device
     *  to download. On failure the base screen shows the error; on success the remote panel shows PIN+QR. */
    fun exportRemote(sections: Set<BackupManager.Section>, backupPassword: String?, profileIds: Set<Long>) {
        viewModelScope.launch {
            _state.value = State.Working
            backup.export(companion.backupExportDir, sections, backupPassword, profileIds).fold(
                onSuccess = { path ->
                    companion.startForBackupDownload(tv.own.owntv.core.companion.CompanionLink.DEFAULT_PORT, File(path))
                    _state.value = State.Idle
                },
                onFailure = { _state.value = State.Error(BackupError.EXPORT) },
            )
        }
    }

    fun stopRemoteExport() = companion.stop()

    /** All profiles + the active one, for the export flow's profile-picker step. */
    /** [protectedIds]: profiles with a profile lock or a Settings lock — another profile needs one of their PINs. */
    data class ProfileChoices(
        val profiles: List<tv.own.owntv.core.database.entity.ProfileEntity>,
        val activeId: Long,
        val protectedIds: Set<Long> = emptySet(),
    )

    private val _profileChoices = MutableStateFlow<ProfileChoices?>(null)
    val profileChoices: StateFlow<ProfileChoices?> = _profileChoices.asStateFlow()

    fun loadProfiles() {
        viewModelScope.launch {
            _profileChoices.value = ProfileChoices(
                profiles = profileDao.getAllOnce(),
                activeId = settings.activeProfileId.first(),
                protectedIds = protection.protectedIds(),
            )
        }
    }

    /** A protected profile's profile PIN or Settings PIN, for including or changing it. */
    suspend fun verifyPin(profile: tv.own.owntv.core.database.entity.ProfileEntity, pin: String): tv.own.owntv.core.categorylock.PinAttempt =
        protection.verify(profile, pin)

    sealed interface State {
        data object Idle : State
        data object Working : State

        /** A restore file was picked & inspected: let the user choose which sections to apply.
         *  [password] is non-null only for a sealed `.own` already unlocked in the step before —
         *  it must ride through to the import, or the file would have to be decrypted twice. */
        data class ChooseRestore(
            val file: File,
            val available: Set<BackupManager.Section>,
            val encrypted: Boolean,
            val password: String? = null,
            /** Written on another device: the picker offers that device's hardware settings (unticked). */
            val fromOtherDevice: Boolean = false,
        ) : State
        data class Done(
            val kind: DoneKind,
            val path: String? = null,
            val items: Int = 0,
            val passwordsOmitted: Boolean = false,
            val skippedSources: Int = 0,
            val invalidLocale: Boolean = false,
        ) : State
        data class Error(val kind: BackupError) : State

        /**
         * The file has profiles named like ones here: ask what to do with each ([clashes]). Merging or
         * replacing a protected one other than the profile in use ([protectedIds], [activeId]) asks one
         * of its PINs.
         */
        data class ProfileClashes(
            val file: File,
            val sections: Set<BackupManager.Section>,
            val password: String?,
            val deviceSettings: Boolean,
            val clashes: List<BackupManager.ProfileClash>,
            val protectedIds: Set<Long>,
            val activeId: Long,
            val profiles: List<tv.own.owntv.core.database.entity.ProfileEntity>,
        ) : State

        /**
         * Encrypted restore needs the backup password — [retry] is true after a wrong attempt.
         *
         * [sections] is null for a **sealed** `.own` container, which is asked for the password
         * BEFORE anything else: none of its contents — not even the section list — is readable until
         * it is decrypted, so there is nothing to pick yet and nothing to gain by skipping.
         * A non-null [sections] is the older field-encrypted flow: sections picked, password
         * optional, skipping restores everything but the saved passwords.
         */
        data class NeedPassword(
            val file: File,
            val sections: Set<BackupManager.Section>?,
            val retry: Boolean = false,
            /** The picker's "hardware settings from the other device" answer, carried to the import. */
            val deviceSettings: Boolean = false,
        ) : State {
            /** Whole-file encryption: the password is mandatory and there is nothing to skip to. */
            val sealed: Boolean get() = sections == null
        }
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** The icon colour a finished restore left chosen; the screen offers a restart if the launcher differs. */
    private val _restoredIcon = MutableStateFlow<tv.own.owntv.core.brand.AppIcon?>(null)
    val restoredIcon: StateFlow<tv.own.owntv.core.brand.AppIcon?> = _restoredIcon.asStateFlow()
    fun clearRestoredIcon() { _restoredIcon.value = null }

    /** Export with an optional backup passphrase (blank/null = omit secret password fields).
     *  [profileIds] are the PIN-authorized profiles from the picker step. */
    fun export(folder: File, sections: Set<BackupManager.Section>, backupPassword: String?, profileIds: Set<Long>) {
        viewModelScope.launch {
            _state.value = State.Working
            backup.export(folder, sections, backupPassword, profileIds).fold(
                onSuccess = {
                    _state.value = State.Done(DoneKind.EXPORTED, path = it, passwordsOmitted = backupPassword.isNullOrBlank())
                },
                onFailure = { _state.value = State.Error(BackupError.EXPORT) },
            )
        }
    }

    /**
     * Step 1 of restore: inspect the picked file so the section picker can show what's inside.
     *
     * A sealed `.own` can't be inspected at all yet — it asks for the password first and comes back
     * through [unlock]. Everything else (plain `.own`, legacy `.json`) inspects immediately.
     */
    fun inspect(file: File) {
        viewModelScope.launch {
            _state.value = State.Working
            if (backup.isSealed(file)) {
                _state.value = State.NeedPassword(file, sections = null)
                return@launch
            }
            backup.sectionsIn(file).fold(
                onSuccess = { _state.value = State.ChooseRestore(file, it.sections, it.encrypted, fromOtherDevice = it.fromOtherDevice) },
                onFailure = { _state.value = State.Error(BackupError.READ) }
            )
        }
    }

    /** Sealed-container step 1b: decrypt with [password], then show the section picker. */
    fun unlock(file: File, password: String) {
        viewModelScope.launch {
            _state.value = State.Working
            backup.sectionsIn(file, password).fold(
                onSuccess = { _state.value = State.ChooseRestore(file, it.sections, it.encrypted, password, it.fromOtherDevice) },
                onFailure = {
                    _state.value = if (it is BackupManager.WrongPasswordException) {
                        State.NeedPassword(file, sections = null, retry = true)
                    } else {
                        State.Error(BackupError.READ)
                    }
                },
            )
        }
    }

    /**
     * Step 2 of restore: apply the chosen sections. Profiles named like ones here are asked about first
     * ([State.ProfileClashes]); [choices] is the answer, by the profile's id in the file.
     */
    fun import(
        file: File,
        sections: Set<BackupManager.Section>,
        backupPassword: String?,
        deviceSettings: Boolean = false,
        choices: Map<Long, BackupManager.ProfileRestoreChoice>? = null,
    ) {
        viewModelScope.launch {
            _state.value = State.Working
            if (choices == null) {
                val clashes = backup.sectionsIn(file, backupPassword).getOrNull()?.clashes.orEmpty()
                if (clashes.isNotEmpty()) {
                    _state.value = State.ProfileClashes(
                        file, sections, backupPassword, deviceSettings, clashes,
                        protection.protectedIds(), settings.activeProfileId.first(), profileDao.getAllOnce(),
                    )
                    return@launch
                }
            }
            backup.import(file, sections, backupPassword, deviceSettings = deviceSettings, profileChoices = choices.orEmpty()).fold(
                onSuccess = { summary ->
                    _restoredIcon.value = settings.appIcon.first()
                    _state.value = State.Done(
                        DoneKind.RESTORED,
                        items = summary.items,
                        passwordsOmitted = backupPassword.isNullOrBlank(),
                        skippedSources = summary.skippedSources,
                        invalidLocale = summary.invalidLocale,
                    )
                },
                onFailure = {
                    if (it is BackupManager.WrongPasswordException) {
                        _state.value = State.NeedPassword(file, sections, retry = true, deviceSettings = deviceSettings)
                    } else {
                        _state.value = State.Error(BackupError.IMPORT)
                    }
                },
            )
        }
    }

    /** Restore proceeds: import straight away when we already hold the password (sealed container,
     *  unlocked in [unlock]) or nothing is encrypted; otherwise prompt for the optional passphrase. */
    fun beginImport(
        file: File,
        sections: Set<BackupManager.Section>,
        encrypted: Boolean,
        password: String? = null,
        deviceSettings: Boolean = false,
    ) {
        when {
            password != null -> import(file, sections, password, deviceSettings)
            encrypted -> _state.value = State.NeedPassword(file, sections, deviceSettings = deviceSettings)
            else -> import(file, sections, null, deviceSettings)
        }
    }

    fun reset() { _state.value = State.Idle }
}
