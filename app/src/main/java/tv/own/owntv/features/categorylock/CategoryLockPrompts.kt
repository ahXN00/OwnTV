package tv.own.owntv.features.categorylock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.features.profiles.PinDialog

/**
 * The category-lock PIN popups of section [type], in the Customize screen's compact PIN look. [onClosed]
 * hears every popup that goes away, with whether its PIN opened the category, so the screen can move
 * focus on to the items or back to the category. A popup asked for by a long-press action instead runs
 * that action through [onAction] once the PIN opens the category, and focus stays on the rail.
 */
@Composable
fun CategoryLockPrompts(
    type: MediaType,
    onAction: (key: LiveKey, action: SealedCategoryAction) -> Unit,
    onClosed: (key: LiveKey, opened: Boolean) -> Unit,
) {
    val vm: CategoryLockViewModel = koinViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val latestOnAction by rememberUpdatedState(onAction)
    val latestOnClosed by rememberUpdatedState(onClosed)
    LaunchedEffect(vm, type) {
        vm.closed.collect {
            if (it.type != type) return@collect
            val then = it.then
            if (it.opened && then != null) latestOnAction(it.key, then)
            latestOnClosed(it.key, it.opened && then == null)
        }
    }
    val prompt = ui.prompt?.takeIf { it.type == type } ?: return
    key(ui.attempt) {
        PinDialog(title = promptTitle(prompt, ui), onSubmit = vm::submit, onDismiss = vm::dismiss, compact = true)
    }
}

@Composable
private fun promptTitle(prompt: CategoryLockPrompt, ui: CategoryLockUi): String = when {
    prompt is CategoryLockPrompt.CreatePin -> stringResource(
        when {
            prompt.firstPin == null -> R.string.settings_customize_new_pin
            prompt.mismatch -> R.string.settings_customize_pin_mismatch
            else -> R.string.settings_customize_confirm_pin
        },
    )
    else -> pinEntryTitle(wrong = ui.wrong, waitSeconds = ui.waitSeconds)
}

/** The title of a Customize PIN entry: the wait while entries are refused, else whether the last one was wrong. */
@Composable
internal fun pinEntryTitle(wrong: Boolean, waitSeconds: Int): String = when {
    waitSeconds > 0 -> pluralStringResource(R.plurals.content_category_pin_wait, waitSeconds, waitSeconds)
    wrong -> stringResource(R.string.settings_customize_wrong_pin)
    else -> stringResource(R.string.settings_customize_enter_pin)
}
