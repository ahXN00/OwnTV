package tv.own.owntv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import tv.own.owntv.core.setup.displayText
import tv.own.owntv.core.util.FriendlySyncFailure

/** Resolve a classified sync failure only at the Compose presentation boundary; the sentences are core's. */
@Composable
fun FriendlySyncFailure.displayText(): String = displayText(LocalResources.current)
