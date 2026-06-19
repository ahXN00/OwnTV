package tv.own.owntv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.ui.theme.OwnTVTheme

/** Visual emphasis for [OwnTVButton]. */
enum class OwnTVButtonStyle { PRIMARY, SECONDARY }

/**
 * Remote-friendly TV button built on [FocusableSurface]. PRIMARY fills with the brand accent;
 * SECONDARY is an outline that fills on focus.
 */
@Composable
fun OwnTVButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OwnTVButtonStyle = OwnTVButtonStyle.PRIMARY,
    icon: OwnTVIcon? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(50) // M3 full/pill button

    val primary = style == OwnTVButtonStyle.PRIMARY

    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        focusedScale = 1.04f,
        // M3 tonal: PRIMARY keeps the primary fill; SECONDARY is a tonal surface that lifts to the
        // primary container on focus.
        unfocusedContainerColor = if (primary) colors.primary else colors.card,
        focusedContainerColor = if (primary) colors.primary else colors.primaryContainer,
        selectedContainerColor = if (primary) colors.primary else colors.card,
    ) { focused ->
        val contentColor = when {
            primary -> colors.onPrimary
            focused -> colors.onPrimaryContainer
            else -> colors.textPrimary
        }

        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                OwnTVIcon(icon = icon, tint = contentColor, filled = true, modifier = Modifier.size(20.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
