package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.BrandLockup
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Layer 4 — preview / detail / player pane. Idle state shows OwnTV branding and a hint, per the plan.
 * Live preview playback, movie/series detail, and the fullscreen handoff land in phases 7–10.
 */
@Composable
fun PreviewPane(
    hint: String,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Dimens.CardCorner))
            .background(colors.panel)
            .padding(Dimens.GapLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandLockup(markSize = 56, textSize = 34, stacked = true)
        Spacer(Modifier.height(Dimens.GapMedium))
        Text(
            text = stringResource(R.string.content_preview_player),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Dimens.GapLarge))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
