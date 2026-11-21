package tv.own.owntv.features.subtitles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupButton
import tv.own.owntv.ui.stage.StagePopupCheck
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * "Find in folder", opened from the player's subtitle menu: the chosen folder's subtitle files, best
 * match for the film or episode first, a search box to narrow them, and the automatic-loading switch.
 * Choosing another folder is the host's job ([onChangeFolder]); it hosts the folder browser in its own
 * window, as every popup here is one.
 */
@Composable
fun FolderSubtitleScreen(
    onChangeFolder: () -> Unit,
    onFailed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val vm: FolderSubtitleViewModel = koinViewModel()
    val shown by vm.shown.collectAsStateWithLifecycle()
    val hasFiles by vm.hasFiles.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()
    val autoLoad by vm.autoLoad.collectAsStateWithLifecycle()
    val applying by vm.applying.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(Unit) { vm.picked.collect { ok -> if (ok) onDismiss() else onFailed() } }
    BackHandler { onDismiss() }

    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.player_subtitle_folder_title),
        body = folder,
        eyebrow = null,
        width = 1000.mpx,
    ) {
        val files = shown
        when {
            files == null -> Centered { OwnTVSpinner() }
            !hasFiles -> Message(
                stringResource(R.string.player_subtitle_folder_empty),
                primary = stringResource(R.string.player_subtitle_folder_change), onPrimary = onChangeFolder,
                tertiary = stringResource(R.string.settings_close), onTertiary = onDismiss,
            )
            else -> Column {
                val firstFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
                tv.own.owntv.features.settings.StageFieldRow(
                    icon = OwnTVIcon.SEARCH,
                    label = stringResource(R.string.common_search),
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = stringResource(R.string.common_search_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.mpx))
                StagePopupOption(
                    title = stringResource(R.string.player_subtitle_folder_auto),
                    subtitle = stringResource(R.string.player_subtitle_folder_auto_description),
                    onClick = { vm.setAutoLoad(!autoLoad) },
                    leading = { focused -> StagePopupCheck(autoLoad, focused) },
                )
                Spacer(Modifier.height(8.mpx))
                if (files.isEmpty()) {
                    Text(
                        stringResource(R.string.search_nothing_found, query.trim()),
                        style = stageText(18, 400), color = StageColors.Muted,
                        modifier = Modifier.padding(vertical = 16.mpx),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.mpx), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
                        items(files, key = { it.location }) { file ->
                            StagePopupOption(
                                title = file.name,
                                onClick = { vm.select(file) },
                                enabled = applying == null,
                                modifier = if (file == files.first()) Modifier.focusRequester(firstFocus) else Modifier,
                                trailing = { if (applying == file.location) OwnTVSpinner() },
                            )
                        }
                    }
                }
                ButtonRow {
                    StagePopupButton(stringResource(R.string.player_subtitle_folder_change), onClick = onChangeFolder, primary = false)
                    StagePopupButton(stringResource(R.string.settings_close), onClick = onDismiss, primary = false)
                }
            }
        }
    }
}
