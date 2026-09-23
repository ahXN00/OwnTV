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

/** Phase 12 — drives Backup & Restore (selective export/import to a JSON file). */
enum class DoneKind { EXPORTED, RESTORED }
enum class BackupError { EXPORT, READ, IMPORT }

class BackupViewModel(
    private val backup: BackupManager,
    private val profileDao: tv.own.owntv.core.database.dao.ProfileDao,
    private val settings: tv.own.owntv.core.settings.SettingsRepository,
    private val companion: tv.own.owntv.core.companion.CompanionController,
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
    data class ProfileChoices(val profiles: List<tv.own.owntv.core.database.entity.ProfileEntity>, val activeId: Long)

    private val _profileChoices = MutableStateFlow<ProfileChoices?>(null)
    val profileChoices: StateFlow<ProfileChoices?> = _profileChoices.asStateFlow()

    fun loadProfiles() {
        viewModelScope.launch {
            _profileChoices.value = ProfileChoices(
                profiles = profileDao.getAllOnce(),
                activeId = settings.activeProfileId.first(),
            )
        }
    }

    /** PIN check for ticking a locked, non-active profile in the export picker. */
    fun verifyPin(profile: tv.own.owntv.core.database.entity.ProfileEntity, pin: String): Boolean =
        tv.own.owntv.core.util.Pin.verify(pin, profile.pinHash)

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

    /** Step 2 of restore: apply the chosen sections. */
    fun import(file: File, sections: Set<BackupManager.Section>, backupPassword: String?, deviceSettings: Boolean = false) {
        viewModelScope.launch {
            _state.value = State.Working
            backup.import(file, sections, backupPassword, deviceSettings = deviceSettings).fold(
                onSuccess = { summary ->
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
