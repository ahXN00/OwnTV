package tv.own.owntv.features.categorylock

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CategoryRailEditor
import tv.own.owntv.core.customize.CategoryUnlockSession
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.util.Pin

/**
 * Locking a category with the profile's Customize PIN, and opening a locked one. Shared by the Live TV,
 * Movies and Series screens; each shows only the prompts for its own [MediaType].
 */
class CategoryLockViewModel(
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    categoryDao: CategoryDao,
    private val customize: CustomizationStore,
    private val session: CategoryUnlockSession,
    private val limiter: PinAttemptLimiter,
    private val launcher: LauncherIntegrationRepository,
) : ViewModel() {
    private val keys = CategoryRailEditor(customize, categoryDao, profileDao)

    private val _ui = MutableStateFlow(CategoryLockUi())
    val ui: StateFlow<CategoryLockUi> = _ui.asStateFlow()

    private val _closed = MutableSharedFlow<CategoryLockClosed>(extraBufferCapacity = 1)
    val closed: SharedFlow<CategoryLockClosed> = _closed.asSharedFlow()

    private var countdown: Job? = null

    /** A PIN popup is up: the category sheet behind it must not close when it loses focus. */
    val prompting: Boolean get() = _ui.value.prompt != null

    /** OK on a locked category, or a long-press action [then] on a sealed one. */
    fun open(type: MediaType, key: LiveKey, then: SealedCategoryAction? = null) =
        show(CategoryLockPrompt.Open(type, key, then))

    /** The long-press menu's Lock with PIN / Remove lock. Locking needs no entry once a PIN exists. */
    fun toggleLock(type: MediaType, key: LiveKey) {
        viewModelScope.launch {
            val pid = profileId() ?: return@launch
            val catKey = keys.customizationKey(key) ?: return@launch
            val stored = settings.customizePin(pid).first()
            when {
                stored == null -> show(CategoryLockPrompt.CreatePin(type, key))
                catKey in customize.observe(pid, type).first().lockedCategories ->
                    show(CategoryLockPrompt.RemoveLock(type, key))
                else -> lock(pid, type, catKey, stored)
            }
        }
    }

    fun submit(pin: String) {
        when (val prompt = _ui.value.prompt) {
            null -> Unit
            is CategoryLockPrompt.CreatePin -> createPin(prompt, pin)
            is CategoryLockPrompt.Open, is CategoryLockPrompt.RemoveLock -> checkPin(prompt, pin)
        }
    }

    /** Back or Cancel: a locked category stays locked. */
    fun dismiss() {
        val prompt = _ui.value.prompt ?: return
        close(prompt, opened = false)
    }

    private fun createPin(prompt: CategoryLockPrompt.CreatePin, pin: String) {
        val first = prompt.firstPin
        if (first == null || pin != first) {
            val next = if (first == null) prompt.copy(firstPin = pin) else prompt.copy(mismatch = true)
            _ui.update { it.copy(prompt = next, attempt = it.attempt + 1) }
            return
        }
        viewModelScope.launch {
            val pid = profileId() ?: return@launch
            val catKey = keys.customizationKey(prompt.key) ?: return@launch
            settings.setCustomizePin(pid, pin)
            lock(pid, prompt.type, catKey, settings.customizePin(pid).first() ?: return@launch)
            close(prompt, opened = false)
        }
    }

    private fun checkPin(prompt: CategoryLockPrompt, pin: String) {
        viewModelScope.launch {
            val pid = profileId() ?: return@launch
            // Read before the check: a relock while it runs (the app left the screen) voids this entry.
            val relocks = session.epoch()
            val stored = settings.customizePin(pid).first()
            // No stored PIN means nothing to check against: Pin.verifyCustomize would let any entry through.
            when (val result = limiter.attempt { stored != null && Pin.verifyCustomize(pin, stored) }) {
                PinAttempt.Accepted -> {
                    val catKey = keys.customizationKey(prompt.key) ?: return@launch
                    // The PIN was the profile's that was active when it was typed; another one may be now.
                    if (profileId() != pid) return@launch close(prompt, opened = false)
                    if (prompt is CategoryLockPrompt.Open) {
                        if (!session.unlock(pid, prompt.type, catKey, since = relocks)) return@launch close(prompt, opened = false)
                    } else {
                        customize.setCategoryLocked(pid, prompt.type, catKey, false)
                        refreshHomeRows(pid)
                    }
                    close(prompt, opened = prompt is CategoryLockPrompt.Open)
                }
                PinAttempt.Wrong -> _ui.update { it.copy(wrong = true, attempt = it.attempt + 1) }
                is PinAttempt.Wait -> {
                    _ui.update { it.copy(wrong = false, waitSeconds = result.seconds, attempt = it.attempt + 1) }
                    countDown()
                }
            }
        }
    }

    /** Locks [catKey]. A PIN saved before PINs were hashed is stored hashed first. */
    private suspend fun lock(pid: Long, type: MediaType, catKey: String, storedPin: String) {
        if (!Pin.isHashed(storedPin)) settings.setCustomizePin(pid, storedPin)
        customize.setCategoryLocked(pid, type, catKey, true)
        session.relock(pid, type, catKey)
        refreshHomeRows(pid)
    }

    /**
     * The TV home's Watch Next and Recent rows leave out locked categories, so a lock change republishes
     * them. A launcher that refuses the write must not undo the lock: the failure is logged and the lock stands.
     */
    private suspend fun refreshHomeRows(pid: Long) {
        runCatching { launcher.refreshProfile(pid) }
            .onFailure { t -> Log.w(TAG, "home rows refresh after a lock change failed profile=$pid", t) }
    }

    private fun show(prompt: CategoryLockPrompt) {
        _ui.update { CategoryLockUi(prompt = prompt, waitSeconds = limiter.waitSeconds(), attempt = it.attempt + 1) }
        if (_ui.value.waitSeconds > 0) countDown()
    }

    private fun close(prompt: CategoryLockPrompt, opened: Boolean) {
        countdown?.cancel()
        _ui.update { CategoryLockUi(attempt = it.attempt + 1) }
        _closed.tryEmit(CategoryLockClosed(prompt.type, prompt.key, opened, (prompt as? CategoryLockPrompt.Open)?.then))
    }

    private fun countDown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            do {
                val left = limiter.waitSeconds()
                _ui.update { it.copy(waitSeconds = left) }
                if (left > 0) delay(1_000)
            } while (left > 0)
        }
    }

    private suspend fun profileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }
}

private const val TAG = "CategoryLock"
