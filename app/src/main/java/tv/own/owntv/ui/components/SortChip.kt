package tv.own.owntv.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.features.settings.data.SettingsRepository.SortMode
import tv.own.owntv.ui.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Compact sort toggle shown next to a section's search bar. Click flips between the playlist's own
 * order and A–Z; the label names the *current* mode.
 */
@Composable
fun SortChip(
    mode: SortMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    playlistLabel: String = "Playlist",
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onToggle,
        // Same height + pill shape as SearchBar so the two read as one row of controls.
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(50),
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.Center,
        surface = GlassSurface.CARDS,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(
                OwnTVIcon.SORT,
                tint = if (focused) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when (mode) {
                    SortMode.PLAYLIST -> playlistLabel
                    SortMode.ALPHA -> "A–Z"
                    SortMode.RATING -> "Rating"
                    SortMode.DATE_ADDED -> "Fecha de añadido"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (focused) colors.primary else colors.onSurface,
            )
        }
    }
}
