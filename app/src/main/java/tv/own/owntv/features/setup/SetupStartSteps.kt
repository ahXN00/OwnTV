package tv.own.owntv.features.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.features.settings.FirstRunLanguageSelector
import tv.own.owntv.ui.components.OwnTVIcon

/**
 * The language as a row (OK opens the list), then Get started. Set up by Automation sits to its
 * left; Down from the language still lands on Get started.
 */
@Composable
internal fun WelcomeStep(onNext: () -> Unit, onAutomation: () -> Unit) {
    val start = remember { FocusRequester() }
    WizardFrame(
        step = WizardStep.LANGUAGE,
        title = stringResource(R.string.setup_welcome_title),
        sub = stringResource(R.string.setup_welcome_sub),
        back = null,
        next = WizardAction(stringResource(R.string.setup_get_started), onNext, start),
        extraLeft = listOf(WizardAction(stringResource(R.string.setup_automation_action), onAutomation)),
    ) {
        FirstRunLanguageSelector(Modifier.focusProperties { down = start }.focusRequester(rememberFirstFocus()))
    }
}

/** "Before you start": the player-only notice, accepted before anything is set up. */
@Composable
internal fun DisclaimerStep(onBack: () -> Unit, onAccept: () -> Unit) {
    WizardFrame(
        step = WizardStep.DISPLAY,
        title = stringResource(R.string.setup_before_you_start),
        sub = stringResource(R.string.setup_disclaimer),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = WizardAction(stringResource(R.string.setup_i_understand), onAccept, rememberFirstFocus()),
    )
}

/**
 * First decision: start fresh or bring everything back from a backup (profiles included — no point
 * creating a profile first that the restore would replace).
 */
@Composable
internal fun SetupChoiceStep(onBack: () -> Unit, onNewProfile: () -> Unit, onRestore: () -> Unit, onSync: () -> Unit) {
    val first = rememberFirstFocus()
    WizardFrame(
        step = WizardStep.PROFILE,
        title = stringResource(R.string.setup_set_up_owntv),
        sub = stringResource(R.string.setup_setup_choice_description),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = null,
    ) {
        WizardCards(
            listOf(
                WizardCard(OwnTVIcon.PERSON, stringResource(R.string.setup_new_profile), stringResource(R.string.setup_create_profile_add_sources), onNewProfile),
                WizardCard(OwnTVIcon.DOWNLOADS, stringResource(R.string.setup_restore_a_backup), stringResource(R.string.setup_restore_card_line), onRestore),
                WizardCard(OwnTVIcon.PHONE, stringResource(R.string.setup_sync_device), stringResource(R.string.setup_sync_device_description), onSync),
            ),
            first,
        )
    }
}
