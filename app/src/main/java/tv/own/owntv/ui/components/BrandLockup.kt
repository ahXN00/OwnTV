package tv.own.owntv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    return remember(context) { AppIconSwitcher.applied(context) }
}

/** The flat flip-card mark (no shadow, no next card). At 32 dp and below the simpler small drawing. */
@Composable
fun BrandMark(icon: AppIcon, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(if (size <= 32.dp) icon.markSmall else icon.mark),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/**
 * The "OwnTV" lockup of the pass-6 mockup: the mark, then the wordmark — "Own" in the text colour,
 * "TV" in the icon's own accent, extra bold with -0.025 em tracking. Side by side the gap is 0.26 × the
 * mark; [stacked] (Setup welcome, the preview pane) puts the mark above with a 0.2 × gap. The wordmark
 * is live text in the user's main font, so it follows a font change.
 */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    markSize: Int = 36,
    textSize: Int = 26,
    stacked: Boolean = false,
) {
    val colors = OwnTVTheme.colors
    val icon = rememberAppliedIcon()
    val tvColor = Color(if (colors.isDark) icon.accent else icon.accentOnLight)
    val parts = @Composable {
        BrandMark(icon, markSize.dp)
        Text(
            text = buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(color = colors.textPrimary)) {
                    append(stringResource(R.string.brand_own))
                }
                withStyle(androidx.compose.ui.text.SpanStyle(color = tvColor)) {
                    append(stringResource(R.string.brand_tv))
                }
            },
            fontSize = textSize.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.025).em,
        )
    }
    if (stacked) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((markSize * 0.2f).dp),
        ) { parts() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((markSize * 0.26f).dp),
        ) { parts() }
    }
}
