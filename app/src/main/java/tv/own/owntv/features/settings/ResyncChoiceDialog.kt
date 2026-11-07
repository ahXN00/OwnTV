package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.mpx

/**
 * Resync picker: an ordinary refresh, or one that is also allowed to drop titles the provider has
 * stopped listing.
 *
 * A normal resync will not remove more than half a source's rows, because a truncated provider
 * response is indistinguishable from a genuinely shrunken catalog and guessing wrong wipes a working
 * playlist. The consequence is that a provider that really did drop a lot of titles leaves them
 * behind with no way out — this dialog is that way out, kept behind a deliberate choice.
 *
 * Focus starts on the ordinary refresh, so the common case is still Select-then-Select and a
 * mis-press can't trigger removals. Back cancels.
 */
@Composable
internal fun ResyncChoiceDialog(
    sourceName: String,
    onNormal: () -> Unit,
    onClean: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_sources_resync_title_full, sourceName),
        body = stringResource(R.string.settings_sources_resync_description),
        buttons = { tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19) },
    ) {
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_sources_resync_now_full), onClick = onNormal, modifier = Modifier.focusRequester(focus),
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.REFRESH) },
        )
        tv.own.owntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_sources_resync_remove_full), onClick = onClean, danger = true,
            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.TRASH, tv.own.owntv.ui.theme.StageColors.Danger) },
        )
    }
}
