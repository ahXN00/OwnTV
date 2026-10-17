package tv.own.owntv.features.categorylock

import androidx.compose.runtime.Immutable
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.MediaType

/** The PIN popup a category lock has up, for the rail entry [key] in section [type]. */
sealed interface CategoryLockPrompt {
    val type: MediaType
    val key: LiveKey

    /** OK on a locked category: the PIN opens it for the rest of the session, then runs [then]. */
    data class Open(
        override val type: MediaType,
        override val key: LiveKey,
        val then: SealedCategoryAction? = null,
    ) : CategoryLockPrompt

    /** Remove lock: the PIN takes the lock off for good. */
    data class RemoveLock(override val type: MediaType, override val key: LiveKey) : CategoryLockPrompt

    /** Lock with PIN on a profile with no Customize PIN yet: enter one, then the same again. */
    data class CreatePin(
        override val type: MediaType,
        override val key: LiveKey,
        val firstPin: String? = null,
        val mismatch: Boolean = false,
    ) : CategoryLockPrompt
}

/**
 * The lock popups' state. [wrong] follows a wrong entry, [waitSeconds] counts down while entries are
 * refused, and [attempt] changes with every entry so the popup starts again with an empty field.
 */
@Immutable
data class CategoryLockUi(
    val prompt: CategoryLockPrompt? = null,
    val wrong: Boolean = false,
    val waitSeconds: Int = 0,
    val attempt: Int = 0,
)

/** A lock popup went away; [opened] when its PIN opened the category, which then gets [then]. */
data class CategoryLockClosed(
    val type: MediaType,
    val key: LiveKey,
    val opened: Boolean,
    val then: SealedCategoryAction? = null,
)
