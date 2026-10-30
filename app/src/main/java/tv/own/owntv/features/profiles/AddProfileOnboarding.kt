package tv.own.owntv.features.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.own.owntv.features.automation.ProfileGateMarker
import tv.own.owntv.features.setup.Onboarding

/**
 * Adding a profile from the gate → onboard the new profile. Still part of the gate for the automation
 * API ([ProfileGateMarker]): no profile is open until the new one is entered or Back returns to the gate.
 */
@Composable
internal fun AddProfileOnboarding(gateSession: ProfileGateSessionViewModel, modifier: Modifier = Modifier) {
    ProfileGateMarker()
    Onboarding(
        firstRun = false,
        onDone = { profileId -> gateSession.completeAddingProfile(profileId) },
        onCancel = { gateSession.cancelAddingProfile() },
        modifier = modifier,
    )
}
