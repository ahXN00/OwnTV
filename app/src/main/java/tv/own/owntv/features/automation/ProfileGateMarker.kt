package tv.own.owntv.features.automation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.koin.compose.koinInject

/**
 * Closes every profile to the API for as long as "Who's watching?", or the Add profile flow opened from
 * it, is shown ([UnlockedProfile.gateUp]), whether OwnTV opened the gate at launch or switch profile on
 * the rail did. A [DisposableEffect], so the gate counts as gone however it leaves: a profile chosen,
 * Back, or the activity going away.
 */
@Composable
fun ProfileGateMarker(unlocked: UnlockedProfile = koinInject()) {
    DisposableEffect(unlocked) {
        val gate = unlocked.gateUp()
        onDispose { gate.close() }
    }
}
