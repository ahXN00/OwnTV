package tv.own.owntv.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.SyncProgressDisplay
import tv.own.owntv.core.sync.SyncWarning
import tv.own.owntv.core.sync.breakdownText
import tv.own.owntv.core.sync.compactCount
import tv.own.owntv.core.sync.detailText
import tv.own.owntv.core.sync.displayText
import tv.own.owntv.core.sync.labelText
import tv.own.owntv.core.sync.primaryText
import tv.own.owntv.core.sync.remainderText
import tv.own.owntv.core.sync.summaryText
import tv.own.owntv.core.sync.warningText

/**
 * Import counts at the Compose presentation boundary. The sentences themselves are core's, shared
 * with the mobile app; these are only the composable wrappers that reach for the resources.
 */

@Composable
fun SyncCounts.breakdownText(includeEpg: Boolean = false): String =
    breakdownText(LocalResources.current, includeEpg)

@Composable
fun SyncCounts.summaryText(includeEpg: Boolean = false): String =
    summaryText(LocalResources.current, includeEpg)

@Composable
fun SyncProgressCounts.displayText(): String = displayText(LocalResources.current)

internal fun compactCount(context: Context, value: Int): String = compactCount(context.resources, value)

@Composable
fun SyncProgressDisplay.primaryText(): String = primaryText(LocalResources.current)

@Composable
fun SyncProgressDisplay.detailText(): String = detailText(LocalResources.current)

@Composable
fun SyncWarning.labelText(): String = labelText(LocalResources.current)

@Composable
fun List<SyncWarning>.warningText(): String? = warningText(LocalResources.current)

@Composable
fun SyncContentTypes.remainderText(): String? = remainderText(LocalResources.current)
