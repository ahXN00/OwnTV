package tv.own.owntv.features.categorylock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.onDpadLeft
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * What a section's item list shows when it has nothing to list: [text]. A [sealed] category adds an
 * Enter PIN button that asks the PIN through [onEnterPin]. The button holds [pinFocus], the requester
 * the first item would hold, so the ways into the list (the rail's right, the search field's down)
 * land on it as they would on a title. [onNavigateLeft] is the button's ◀, the way out a title's ◀
 * would take (the categories); null leaves ◀ to the focus search.
 */
@Composable
internal fun CategoryEmptyState(
    text: String,
    sealed: Boolean,
    onEnterPin: () -> Unit,
    pinFocus: FocusRequester,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    onNavigateLeft: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.mpx), horizontalAlignment = horizontalAlignment) {
        Text(text, style = stageText(20, 500), color = StageColors.Muted)
        if (sealed) {
            StageButton(
                text = stringResource(R.string.content_category_enter_pin),
                onClick = onEnterPin,
                icon = OwnTVIcon.LOCK,
                modifier = Modifier.focusRequester(pinFocus).onDpadLeft({ onNavigateLeft != null }) { onNavigateLeft?.invoke() },
            )
        }
    }
}
