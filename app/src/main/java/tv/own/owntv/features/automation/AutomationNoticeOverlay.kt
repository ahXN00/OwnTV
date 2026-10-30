package tv.own.owntv.features.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * The API's latest notice ([AutomationNotices]), such as "Remote: playing Nick", while it is fresh: a
 * solid card, large enough to read from the sofa, in the corner [modifier] places it. It never takes
 * focus, so the remote keeps working while it shows.
 */
@Composable
fun AutomationNoticeOverlay(modifier: Modifier = Modifier) {
    val notices = koinInject<AutomationNotices>()
    val latest by notices.latest.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(latest) {
        val notice = latest
        shown = notice?.takeIf(notices::isFresh)?.text
        if (notice == null) return@LaunchedEffect
        while (notices.isFresh(notice)) delay(POLL_MS)
        shown = null
    }
    val text = shown ?: return
    Text(
        text = text,
        style = stageText(24, 700),
        color = StageColors.Text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(top = 40.mpx, end = 40.mpx)
            .widthIn(max = 720.mpx)
            .background(StageColors.GlassOff, RoundedCornerShape(StageRadii.Button))
            .padding(horizontal = 28.mpx, vertical = 18.mpx),
    )
}

/** How often a showing notice checks whether its time is up. */
private const val POLL_MS = 250L
