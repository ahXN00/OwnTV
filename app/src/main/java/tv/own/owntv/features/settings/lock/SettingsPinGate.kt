package tv.own.owntv.features.settings.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.settings.requiresPin
import tv.own.owntv.features.profiles.PinLockedScreen

/**
 * Shows [content] (Settings) only once the profile's Customize PIN has been entered, when the
 * profile asks for it. Back from the PIN prompt calls [onDismiss] without opening Settings.
 */
@Composable
fun SettingsPinGate(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val vm = koinViewModel<SettingsPinViewModel>()
    val lock by vm.lock.collectAsState()
    if (!lock.loaded) return
    // Decided when the visit starts, per profile: leaving Settings or switching profile locks it
    // again, and turning the lock on from inside Settings does not throw the user out.
    var unlocked by remember(lock.profileId) { mutableStateOf(!lock.requiresPin(unlocked = false)) }
    val pin = lock.pin
    if (pin != null && lock.requiresPin(unlocked)) {
        PinLockedScreen(
            title = stringResource(MainSection.SETTINGS.labelRes),
            pin = pin,
            onUnlock = { unlocked = true },
            onDismiss = onDismiss,
        )
        return
    }
    content()
}
