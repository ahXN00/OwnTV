package tv.own.owntv.features.automation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.koin.compose.koinInject

/**
 * Pauses the API's remote buttons for as long as the screen that calls this is shown ([SensitiveScreens]).
 * A [DisposableEffect], so the screen counts as closed however it leaves: Back, a new screen, or the
 * activity going away.
 */
@Composable
fun SensitiveScreenMarker(screens: SensitiveScreens = koinInject()) {
    DisposableEffect(screens) {
        val marker = screens.enter()
        onDispose { marker.close() }
    }
}
