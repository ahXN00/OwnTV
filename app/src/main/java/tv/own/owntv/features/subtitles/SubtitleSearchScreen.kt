package tv.own.owntv.features.subtitles

import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.mpx
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.OwnTVSpinner

/**
 * OpenSubtitles search overlay, opened from the player HUD's ADD SUBTITLES entry.
 * Shows results for the playing movie/episode; selecting one downloads, attaches, and remembers it.
 */
@Composable
fun SubtitleSearchScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SubtitleSearchViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val applying by vm.applying.collectAsStateWithLifecycle()
    val quotaNote by vm.quotaNote.collectAsStateWithLifecycle()

    // Fresh search each time the overlay opens (the ViewModel is reused across opens), and close on
    // the one-shot "applied" event.
    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(Unit) { vm.applied.collect { onDismiss() } }

    var editing by remember { mutableStateOf(false) }
    BackHandler { if (editing) editing = false else onDismiss() }


        val quotaText = quotaNote?.let { quota ->
            if (quota.reset != null) {
                pluralStringResource(tv.own.owntv.R.plurals.player_subtitles_remaining_reset, quota.remaining, quota.remaining, quota.reset)
            } else {
                pluralStringResource(tv.own.owntv.R.plurals.player_subtitles_remaining_count, quota.remaining, quota.remaining)
            }
        }
        tv.own.owntv.ui.stage.StagePopup(
            onDismiss = { if (editing) editing = false else onDismiss() },
            title = stringResource(tv.own.owntv.R.string.player_subtitles_search_title),
            body = quotaText,
            eyebrow = null,
            width = 1000.mpx,
        ) {
                if (editing) {
                    EditSearchField(
                        initial = vm.initialQuery,
                        onSubmit = { q -> editing = false; vm.editSearch(q) },
                        onCancel = { editing = false },
                    )
                } else {
                    when (val s = state) {
                        // Not signed in (or session expired) — sign-in lives in Settings only.
                        is SubtitleSearchViewModel.UiState.SignedOut -> Message(
                            if (s.sessionExpired) {
                                stringResource(tv.own.owntv.R.string.player_subtitles_session_expired)
                            } else {
                                stringResource(tv.own.owntv.R.string.player_subtitles_account_needed)
                            },
                            primary = stringResource(tv.own.owntv.R.string.settings_close), onPrimary = onDismiss,
                        )
                        SubtitleSearchViewModel.UiState.Loading ->
                            Centered { OwnTVSpinner(); Spacer(Modifier.height(12.mpx)); Text(stringResource(tv.own.owntv.R.string.player_subtitles_working), style = stageText(18, 400), color = StageColors.Muted) }
                        is SubtitleSearchViewModel.UiState.Empty -> Message(
                            if (s.showingAllLanguages) {
                                stringResource(tv.own.owntv.R.string.player_subtitles_no_matches_all_languages)
                            } else {
                                stringResource(tv.own.owntv.R.string.player_subtitles_no_matches_chosen_language)
                            },
                            primary = stringResource(tv.own.owntv.R.string.player_subtitles_edit_search), onPrimary = { editing = true },
                            secondary = stringResource(tv.own.owntv.R.string.player_subtitles_show_all_languages).takeIf { !s.showingAllLanguages },
                            onSecondary = vm::showAllLanguages,
                            tertiary = stringResource(tv.own.owntv.R.string.settings_close), onTertiary = onDismiss,
                        )
                        is SubtitleSearchViewModel.UiState.Error -> Message(
                            if (s.kind == SubtitleSearchViewModel.UiState.ErrorKind.LIMIT_REACHED) {
                                stringResource(tv.own.owntv.R.string.player_subtitles_limit_reached)
                            } else {
                                stringResource(tv.own.owntv.R.string.player_subtitles_network_error)
                            },
                            primary = stringResource(tv.own.owntv.R.string.player_subtitles_try_again), onPrimary = vm::retry,
                            tertiary = stringResource(tv.own.owntv.R.string.settings_close), onTertiary = onDismiss,
                        )
                        is SubtitleSearchViewModel.UiState.Results -> ResultsList(
                            results = s.results,
                            applyingFileId = applying,
                            onSelect = vm::select,
                            onEdit = { editing = true },
                            onShowAll = if (!s.showingAllLanguages) vm::showAllLanguages else null,
                            onClose = onDismiss,
                        )
                    }
                    Spacer(Modifier.height(22.mpx))
                    OpenSubtitlesAttribution()
                }
            }
    

    // Sign-in is handled in Settings › Content & metadata › OpenSubtitles only.
}

/** Logo + credit line, mirroring the TMDB attribution in Metadata settings. */
@Composable
private fun OpenSubtitlesAttribution() {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(tv.own.owntv.R.drawable.ic_opensubtitles_logo),
        contentDescription = stringResource(tv.own.owntv.R.string.settings_open_subtitles),
        modifier = Modifier.height(28.mpx),
    )
    Spacer(Modifier.height(8.mpx))
    Text(
        stringResource(tv.own.owntv.R.string.player_subtitles_api_notice),
        style = stageText(15, 500),
        color = StageColors.Dim,
    )
}

@Composable
private fun ResultsList(
    results: List<SubtitleSearchViewModel.Result>,
    applyingFileId: Long?,
    onSelect: (SubtitleSearchViewModel.Result) -> Unit,
    onEdit: () -> Unit,
    onShowAll: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Column {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.mpx), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
            items(results, key = { it.fileId }) { r ->
                val tags = buildList {
                    if (r.fromTrusted) add(stringResource(tv.own.owntv.R.string.player_subtitles_trusted))
                    if (r.hearingImpaired) add(stringResource(tv.own.owntv.R.string.player_subtitles_sdh))
                    if (r.aiTranslated) add(stringResource(tv.own.owntv.R.string.player_subtitles_ai))
                    if (r.downloads > 0) add(pluralStringResource(tv.own.owntv.R.plurals.player_subtitles_download_count, r.downloads, r.downloads))
                }
                val separator = stringResource(tv.own.owntv.R.string.player_subtitles_tags_separator)
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = r.languageName ?: r.language ?: stringResource(tv.own.owntv.R.string.player_subtitles_subtitle),
                    subtitle = r.releaseName ?: stringResource(tv.own.owntv.R.string.player_subtitles_subtitle),
                    onClick = { onSelect(r) },
                    enabled = applyingFileId == null,
                    modifier = if (r == results.first()) Modifier.focusRequester(firstFocus) else Modifier,
                    trailing = { focused ->
                        if (tags.isNotEmpty()) {
                            Text(tags.joinToString(separator), style = stageText(15, 700), color = if (focused) StageColors.Text else stageAccent.accent, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        if (applyingFileId == r.fileId) OwnTVSpinner()
                    },
                )
            }
        }
        ButtonRow {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(tv.own.owntv.R.string.player_subtitles_edit_search), onClick = onEdit, primary = false)
            onShowAll?.let { tv.own.owntv.ui.stage.StagePopupButton(stringResource(tv.own.owntv.R.string.player_subtitles_all_languages), onClick = it, primary = false) }
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(tv.own.owntv.R.string.settings_close), onClick = onClose, primary = false)
        }
    }
}

/** The popup's buttons, bottom right as every Stage popup has them. */
@Composable
private fun ButtonRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 24.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx, Alignment.End), content = content)
}

@Composable
private fun EditSearchField(initial: String, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    Column {
        tv.own.owntv.ui.stage.StagePopupLabel(stringResource(tv.own.owntv.R.string.player_subtitles_edit_search).uppercase())
        tv.own.owntv.features.settings.StageFieldRow(
            icon = tv.own.owntv.ui.components.OwnTVIcon.SEARCH,
            label = stringResource(tv.own.owntv.R.string.player_subtitles_title_label),
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
        )
        ButtonRow {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(tv.own.owntv.R.string.common_cancel), onClick = onCancel, primary = false)
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(tv.own.owntv.R.string.player_subtitles_search), onClick = { onSubmit(value.trim()) })
        }
    }
}

@Composable
private fun Message(
    text: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiary: String? = null,
    onTertiary: (() -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column {
        Text(text, style = stageText(18, 400), color = StageColors.Muted)
        ButtonRow {
            secondary?.let { tv.own.owntv.ui.stage.StagePopupButton(it, onClick = { onSecondary?.invoke() }, primary = false) }
            tertiary?.let { tv.own.owntv.ui.stage.StagePopupButton(it, onClick = { onTertiary?.invoke() }, primary = false) }
            tv.own.owntv.ui.stage.StagePopupButton(primary, onClick = onPrimary, modifier = Modifier.focusRequester(focus))
        }
    }
}

@Composable
private fun Centered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.mpx),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
