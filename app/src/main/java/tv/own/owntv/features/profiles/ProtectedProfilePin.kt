package tv.own.owntv.features.profiles

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.categorylock.PinAttempt
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * The PIN prompt for a protected profile that is not the one in use: either its profile PIN or its
 * Settings PIN opens it ([verify], core's ProfileProtection, which counts wrong entries on the
 * profile door). [body] says what the PIN is for.
 */
@Composable
internal fun ProtectedProfilePinDialog(
    profileName: String,
    body: String,
    verify: suspend (String) -> PinAttempt,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var waitSeconds by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { fieldFocus.requestFocus() } }
    LaunchedEffect(waitSeconds > 0) {
        while (waitSeconds > 0) {
            delay(1_000)
            waitSeconds--
        }
    }
    val submit = {
        val entered = pin
        if (entered.isNotBlank()) scope.launch {
            when (val result = verify(entered)) {
                PinAttempt.Accepted -> onUnlocked()
                PinAttempt.Wrong -> { wrong = true; pin = "" }
                is PinAttempt.Wait -> { wrong = false; waitSeconds = result.seconds; pin = "" }
            }
        }
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_backup_profile_locked, profileName),
        body = body,
        width = 760.mpx,
        buttons = {
            tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.owntv.ui.stage.StageButton(
                stringResource(R.string.settings_backup_unlock),
                onClick = { submit() },
                height = 56.mpx, textSize = 19, tinted = true,
            )
        },
    ) {
        tv.own.owntv.features.settings.StageFieldRow(
            icon = tv.own.owntv.ui.components.OwnTVIcon.PENCIL,
            label = stringResource(R.string.profiles_pin),
            value = pin,
            onValueChange = { pin = it; wrong = false },
            modifier = Modifier.focusRequester(fieldFocus),
            password = true,
        )
        val note = when {
            waitSeconds > 0 -> pluralStringResource(R.plurals.content_category_pin_wait, waitSeconds, waitSeconds)
            wrong -> stringResource(R.string.settings_backup_pin_incorrect)
            else -> null
        }
        if (note != null) Text(note, style = stageText(17, 600), color = StageColors.Danger, modifier = Modifier.padding(top = 10.mpx))
    }
}
