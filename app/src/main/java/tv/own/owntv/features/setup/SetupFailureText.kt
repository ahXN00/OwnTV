package tv.own.owntv.features.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.setup.displayText

/** Words a semantic onboarding failure at the Compose boundary; the sentences are core's. */
@Composable
fun SourceImporter.SetupFailure.displayText(): String = displayText(LocalResources.current)
