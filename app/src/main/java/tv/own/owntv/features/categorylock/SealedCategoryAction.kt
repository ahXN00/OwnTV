package tv.own.owntv.features.categorylock

import tv.own.owntv.core.live.LiveKey

/**
 * A long-press menu action on a sealed category. Hiding, moving or restoring it would change what the
 * lock guards, so the PIN is asked first and the action runs once the PIN opens the category.
 */
enum class SealedCategoryAction {
    HIDE,
    MOVE,
    RESTORE,
    ;

    fun run(key: LiveKey, hide: (LiveKey) -> Unit, move: (LiveKey) -> Unit, restore: (LiveKey) -> Unit) = when (this) {
        HIDE -> hide(key)
        MOVE -> move(key)
        RESTORE -> restore(key)
    }
}
