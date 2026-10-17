package tv.own.owntv.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * ◀ runs [action] and is consumed while [enabled] says so, before the focused node sees it. For an
 * element whose ◀ goes somewhere a focus search would not find, such as a section's categories.
 */
fun Modifier.onDpadLeft(enabled: () -> Boolean, action: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && enabled() && run { action(); true }
}
