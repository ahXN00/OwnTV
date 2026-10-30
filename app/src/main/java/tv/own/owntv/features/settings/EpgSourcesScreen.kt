package tv.own.owntv.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.core.sync.work.EpgSyncState

/**
 * Settings → EPG Sources: standalone XMLTV feeds that fill the guide, independent of playlists.
 * Add (auto-syncs) / Edit / Re-sync / Delete. [startOnAdd] opens the add form immediately (deep-link
 * from the Guide's "Add EPG" button).
 */
@Composable
fun EpgSourcesScreen(onBack: () -> Unit, modifier: Modifier = Modifier, startOnAdd: Boolean = false) {
    tv.own.owntv.features.automation.SensitiveScreenMarker()
    val vm: EpgSourcesViewModel = koinViewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val autoRefreshMap by vm.autoRefresh.collectAsStateWithLifecycle()
    val useLogosIds by vm.useLogos.collectAsStateWithLifecycle()
    val deletingIds by vm.deletingIds.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<EpgSource?>(null) }
    var adding by remember { mutableStateOf(startOnAdd) }
    var confirmDelete by remember { mutableStateOf<EpgSource?>(null) }
    val addFocus = remember { FocusRequester() }

    // Per-row focus restore (mirrors ManageSourcesScreen / MoviesScreen): track the row the user is
    // acting on so edit/re-sync/delete returns focus INSIDE the list — same row if it survived, else
    // the nearest neighbour, else the first row, else "Add EPG". Without this the old code always
    // refocused the "Add EPG" button, which is why focus escaped the menu.
    var contextId by remember { mutableStateOf<Long?>(null) }
    var contextIndex by remember { mutableStateOf(-1) }
    val contextFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }

    // Grab focus inside the list (not on "Add EPG") whenever the list view is showing.
    LaunchedEffect(adding, editing, confirmDelete) {
        if (adding || editing != null || confirmDelete != null) return@LaunchedEffect
        kotlinx.coroutines.delay(80)
        val targetId = contextId
        if (targetId != null && sources.any { it.id == targetId }) {
            runCatching { contextFocus.requestFocus() }
        } else if (sources.isNotEmpty()) {
            runCatching { firstRowFocus.requestFocus() }
        } else {
            runCatching { addFocus.requestFocus() }
        }
    }

    // When the deleted row vanishes from `sources`, move focus to the nearest surviving neighbour
    // (same index slot, else new last row) instead of letting it escape the menu.
    LaunchedEffect(sources) {
        val targetId = contextId ?: return@LaunchedEffect
        if (sources.any { it.id == targetId }) return@LaunchedEffect
        withFrameNanos { }
        if (sources.isEmpty()) {
            contextId = null; contextIndex = -1
            runCatching { addFocus.requestFocus() }
            return@LaunchedEffect
        }
        val neighbor = sources.getOrNull(contextIndex.coerceAtLeast(0)) ?: sources.last()
        contextId = neighbor.id
        contextIndex = sources.indexOfFirst { it.id == neighbor.id }
        withFrameNanos { }
        runCatching { contextFocus.requestFocus() }
    }

    // Add / edit form.
    if (adding || editing != null) {
        EpgSourceForm(
            initial = editing,
            initialAutoRefresh = editing?.let { autoRefreshMap[it.id] } ?: EpgRefresh.OFF,
            initialUseLogos = editing?.let { it.id in useLogosIds } ?: false,
            loadPlaylistOptions = { vm.playlistEpgOptions() },
            onSave = { name, url, ua, autoRefresh, useLogos ->
                val e = editing
                if (e == null) vm.add(name, url, ua, autoRefresh, useLogos)
                else { vm.update(e, name, url, ua); vm.setAutoRefresh(e, autoRefresh); vm.setUseLogos(e, useLogos) }
                adding = false; editing = null
            },
            onCancel = { adding = false; editing = null },
            modifier = modifier,
        )
        return
    }

    // The guides as rows; the focused one's Re-sync / Edit / Delete in the panel.
    val actionsFocus = remember { FocusRequester() }
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_sources)),
        title = stringResource(R.string.settings_epg_sources_title),
        count = "",
        onBack = onBack,
        modifier = modifier,
        toolbar = {
            tv.own.owntv.ui.stage.StageTool(
                stringResource(R.string.content_epg_add), onClick = { adding = true },
                icon = OwnTVIcon.ADD, boxed = true, modifier = Modifier.focusRequester(addFocus),
            )
        },
    ) {
        if (sources.isEmpty()) StageSettingsNote(stringResource(R.string.settings_epg_sources_empty), null)
        sources.forEachIndexed { index, source ->
            // Keyed: Move up / Move down reorder the rows, and the moved guide keeps its panel.
            key(source.id) {
                val syncState by remember(source.id) { vm.observeSync(source.id) }
                    .collectAsStateWithLifecycle(EpgSyncState.Idle)
                EpgRow(
                    source = source,
                    autoRefresh = autoRefreshMap[source.id] ?: EpgRefresh.OFF,
                    counts = { vm.counts(source.id) },
                    syncState = syncState,
                    deleting = source.id in deletingIds,
                    // Bind contextFocus to the acted-on row (restore target), firstRowFocus to row 0.
                    rowFocus = when {
                        source.id == contextId -> contextFocus
                        index == 0 -> firstRowFocus
                        else -> null
                    },
                    actionsFocus = actionsFocus,
                    keepPanel = source.id == contextId,
                    priority = epgSourcePriorityLine(index, sources.size),
                    orderActions = epgSourceOrderActions(sources.size) { up ->
                        contextId = source.id; contextIndex = index; vm.move(source, up)
                    },
                    onFocused = { contextId = source.id; contextIndex = index },
                    onResync = { contextId = source.id; contextIndex = index; vm.resync(source) },
                    onCancelSync = { contextId = source.id; contextIndex = index; vm.cancelSync(source) },
                    onEdit = { contextId = source.id; contextIndex = index; editing = source },
                    onDelete = { contextId = source.id; contextIndex = index; confirmDelete = source },
                )
            }
        }
    }

    confirmDelete?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.settings_epg_sources_delete_title, s.name),
            message = stringResource(R.string.settings_epg_sources_delete_message),
            onConfirm = { vm.delete(s); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }
}
