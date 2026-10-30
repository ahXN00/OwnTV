package tv.own.owntv.features.customize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.features.automation.SensitiveScreenMarker
import tv.own.owntv.features.profiles.PinDialog

/** PIN lock editing flow opened from the Customize header. */
internal enum class PinEdit { SET, CHANGE, REMOVE }

/**
 * The Customize PIN lock's edit flow ([PinEdit]): remove it once confirmed, or set / change it by entering
 * the new PIN twice. [confirmStage] is true once the first entry ([firstPin]) is in, [mismatch] when the
 * second did not match it. [onSetPin] gets the new PIN, or null to remove the lock. Private for the
 * automation API throughout ([SensitiveScreenMarker]), the remove confirmation included.
 */
@Composable
internal fun CustomizePinEditor(
    mode: PinEdit,
    firstPin: String,
    confirmStage: Boolean,
    mismatch: Boolean,
    onSetPin: (String?) -> Unit,
    onFirstPin: (String) -> Unit,
    onMismatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    SensitiveScreenMarker()
    when (mode) {
        PinEdit.REMOVE -> PinConfirmDialog(
            title = stringResource(R.string.settings_customize_remove_pin_title),
            message = stringResource(R.string.settings_customize_remove_pin_message) + " " + stringResource(R.string.settings_customize_remove_pin_locks),
            confirmLabel = stringResource(R.string.settings_customize_remove),
            onConfirm = { onSetPin(null) },
            onDismiss = onDismiss,
        )
        // SET and CHANGE are the same flow (enter a new PIN, then confirm). To reach here with a
        // PIN already set the user unlocked the screen, so neither verifies the old PIN.
        PinEdit.SET, PinEdit.CHANGE -> {
            if (confirmStage) {
                key("confirm", mismatch) {
                    PinDialog(
                        title = stringResource(if (mismatch) R.string.settings_customize_pin_mismatch else R.string.settings_customize_confirm_pin),
                        onSubmit = { entered ->
                            if (entered == firstPin) {
                                onSetPin(entered)
                            } else {
                                onMismatch()
                            }
                        },
                        onDismiss = onDismiss,
                        compact = true,
                    )
                }
            } else {
                key("first") {
                    PinDialog(
                        title = stringResource(R.string.settings_customize_new_pin),
                        onSubmit = onFirstPin,
                        onDismiss = onDismiss,
                        compact = true,
                    )
                }
            }
        }
    }
}
