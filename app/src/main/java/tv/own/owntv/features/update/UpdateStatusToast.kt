package tv.own.owntv.features.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Small semi-transparent status card (top-right corner) for the automatic startup update check:
 * "Checking…" → "You're up to date" (auto-hides after ~2s) — or, when an update exists, it stays
 * with Update now / Later. Update now downloads with progress and hands off to the system installer.
 * Only the Available state takes D-pad focus; the transient states never interrupt browsing.
 */
@Composable
fun UpdateStatusToast(onDone: () -> Unit, onViewChangelog: () -> Unit, modifier: Modifier = Modifier) {
    val manager: UpdateManager = koinInject()
    val state by manager.state.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }

    // Transient outcomes hide themselves; an available update keeps the card up.
    LaunchedEffect(state) {
        when (state) {
            UpdateManager.State.UpToDate -> { delay(2_000); manager.reset(); onDone() }
            is UpdateManager.State.Failed -> { delay(2_500); manager.reset(); onDone() }
            is UpdateManager.State.Available -> runCatching { focus.requestFocus() }
            UpdateManager.State.Idle -> onDone()
            else -> Unit
        }
    }

    Column(
        modifier = modifier
            .padding(top = 20.dp, end = 20.dp)
            .widthIn(min = 260.dp, max = 380.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHigh.copy(alpha = 0.90f))
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .focusGroup(),
    ) {
        when (val s = state) {
            UpdateManager.State.Idle, UpdateManager.State.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                OwnTVSpinner(sizeDp = 18)
                Spacer(Modifier.width(10.dp))
                Text("Checking for updates…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            UpdateManager.State.UpToDate -> Text(
                "You're on the latest version (v${manager.currentVersion}).",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            is UpdateManager.State.Failed -> Text(
                "Couldn't check for updates.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            is UpdateManager.State.Available -> {
                BackHandler { onDone() } // Back = Later
                Text("Update available", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "OwnTV v${s.info.version} is ready (you have v${manager.currentVersion}).",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // "What's New" opens the full changelog dialog (same view the manual check uses);
                    // both update paths show the changelog before downloading.
                    OwnTVButton("What's New", onClick = onViewChangelog, modifier = Modifier.focusRequester(focus))
                    OwnTVButton("Later", onClick = onDone, style = OwnTVButtonStyle.SECONDARY)
                }
            }
            is UpdateManager.State.Downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                OwnTVSpinner(sizeDp = 18)
                Spacer(Modifier.width(10.dp))
                Text("Downloading update… ${s.percent}%", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}
