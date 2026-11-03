package tv.own.owntv.features.categorylock

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import tv.own.owntv.core.categorylock.CategoryLockClosed
import tv.own.owntv.core.categorylock.CategoryLockFlow
import tv.own.owntv.core.categorylock.CategoryLockUi
import tv.own.owntv.core.categorylock.PinAttemptLimiter
import tv.own.owntv.core.categorylock.SealedCategoryAction
import tv.own.owntv.core.customize.CategoryUnlockSession
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.launcher.LauncherIntegrationRepository
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

/** Core's [CategoryLockFlow] for the Live TV, Movies and Series screens, which each show their own prompts. */
class CategoryLockViewModel(
    settings: SettingsRepository,
    profileDao: ProfileDao,
    categoryDao: CategoryDao,
    customize: CustomizationStore,
    session: CategoryUnlockSession,
    limiter: PinAttemptLimiter,
    private val launcher: LauncherIntegrationRepository,
) : ViewModel() {
    private val flow = CategoryLockFlow(
        viewModelScope, settings, profileDao, categoryDao, customize, session, limiter, onLocksChanged = ::refreshHomeRows,
    )

    val ui: StateFlow<CategoryLockUi> = flow.ui
    val closed: SharedFlow<CategoryLockClosed> = flow.closed

    /** A PIN popup is up: the category sheet behind it must not close when it loses focus. */
    val prompting: Boolean get() = flow.prompting

    fun open(type: MediaType, key: LiveKey, then: SealedCategoryAction? = null) = flow.open(type, key, then)
    fun toggleLock(type: MediaType, key: LiveKey) = flow.toggleLock(type, key)
    fun submit(pin: String) = flow.submit(pin)
    fun dismiss() = flow.dismiss()

    /**
     * The TV home's Watch Next and Recent rows leave out locked categories, so a lock change republishes
     * them. A launcher that refuses the write must not undo the lock: the failure is logged and the lock stands.
     */
    private suspend fun refreshHomeRows(pid: Long) {
        runCatching { launcher.refreshProfile(pid) }
            .onFailure { t -> Log.w(TAG, "home rows refresh after a lock change failed profile=$pid", t) }
    }
}

private const val TAG = "CategoryLock"
