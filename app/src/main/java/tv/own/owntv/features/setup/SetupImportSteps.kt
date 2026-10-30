package tv.own.owntv.features.setup

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.detailText
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.components.primaryText
import tv.own.owntv.ui.components.remainderText
import tv.own.owntv.ui.components.summaryText
import tv.own.owntv.ui.components.warningText

/** Existing playlists: one switch row per playlist another profile has, then Add N playlists. */
@Composable
internal fun ExistingSourcesStep(sources: List<SourceEntity>, onAdd: (Set<Long>) -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val first = rememberFirstFocus()
    WizardFrame(
        step = WizardStep.PLAYLIST,
        title = stringResource(R.string.setup_use_existing_playlists),
        sub = stringResource(R.string.setup_pick_playlists),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = WizardAction(
            pluralStringResource(R.plurals.setup_add_selected_playlists, selected.size, selected.size),
            { if (selected.isNotEmpty()) onAdd(selected) },
            enabled = selected.isNotEmpty(),
        ),
    ) {
        sources.forEachIndexed { i, src ->
            val checked = src.id in selected
            StageSettingRow(
                icon = OwnTVIcon.PLAYLIST,
                title = src.name,
                desc = src.url,
                value = SettingValue.Switch(checked),
                onClick = { selected = if (checked) selected - src.id else selected + src.id },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

/**
 * "Importing <name>" with the bar and where the import is, Cancel and Run in background; then
 * "All set!" with what arrived and an Add a TV guide row; or the failure with Back and Try again.
 */
@Composable
internal fun ImportProgressStep(
    state: SourceImporter.ImportState,
    progress: tv.own.owntv.core.sync.ImportStage?,
    name: String?,
    onContinue: () -> Unit,
    onAddGuide: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onBackground: () -> Unit,
) {
    when (state) {
        SourceImporter.ImportState.Running, SourceImporter.ImportState.Idle,
        is SourceImporter.ImportState.NeedPassword -> {
            val display = progress?.importProgressDisplay()
            WizardFrame(
                step = WizardStep.PLAYLIST,
                title = name?.let { stringResource(R.string.setup_importing_title, it) } ?: stringResource(R.string.setup_importing_catalog),
                sub = stringResource(R.string.setup_watching_during_import),
                back = WizardAction(stringResource(R.string.common_cancel), onCancel),
                // Enter the app right away; the import keeps running (VM is activity-scoped) and content
                // appears as it lands — no need to sit through a big movies/series sync.
                next = WizardAction(stringResource(R.string.setup_run_in_background), onBackground, rememberFirstFocus()),
            ) {
                StageSettingRow(
                    icon = OwnTVIcon.DOWNLOADS,
                    title = display?.primaryText() ?: stringResource(R.string.setup_preparing_catalog),
                    desc = display?.detailText(),
                    value = null,
                    onClick = {},
                )
            }
        }
        is SourceImporter.ImportState.Success -> {
            val lines = listOfNotNull(
                state.counts?.summaryText(includeEpg = true),
                state.restoredItems?.let { pluralStringResource(R.plurals.setup_restored_items, it, it) },
                if (state.passwordsOmitted) stringResource(R.string.setup_passwords_omitted) else null,
                state.needPassword.takeIf { it.isNotEmpty() }?.let {
                    stringResource(R.string.setup_need_password, ListFormatter.getInstance(LocalConfiguration.current.locales[0]).format(it))
                },
                state.skippedSources.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.setup_skipped_sources, it, it) },
                if (state.invalidLocale) stringResource(R.string.setup_invalid_locale) else null,
                state.warnings.warningText(),
                state.remainder.remainderText(),
            )
            WizardFrame(
                step = WizardStep.READY,
                title = stringResource(R.string.setup_all_set),
                sub = lines.joinToString("\n"),
                back = null,
                next = WizardAction(stringResource(R.string.setup_continue), onContinue, rememberFirstFocus()),
                onBack = onContinue,
            ) {
                StageSettingRow(
                    icon = OwnTVIcon.EPG,
                    title = stringResource(R.string.setup_add_guide),
                    desc = stringResource(R.string.setup_add_guide_line),
                    value = SettingValue.Opens(null),
                    onClick = onAddGuide,
                )
            }
        }
        is SourceImporter.ImportState.Failed -> WizardFrame(
            step = WizardStep.PLAYLIST,
            title = stringResource(R.string.setup_import_failed),
            sub = state.failure.displayText(),
            back = WizardAction(stringResource(R.string.common_back), onCancel),
            next = WizardAction(stringResource(R.string.setup_try_again_caps), onRetry, rememberFirstFocus()),
        )
    }
}
