package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.features.setup.AddSourceScreen
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.theme.OwnTVTheme

/** Phase 13 — list / add / re-sync / delete the active profile's IPTV sources. */
@Composable
fun ManageSourcesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val refreshIds by vm.refreshSourceIds.collectAsStateWithLifecycle()
    val defaultId by vm.defaultSourceId.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    var showAdd by remember { mutableStateOf(false) }
    var resyncing by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<SourceEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<SourceEntity?>(null) }
    val addFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    // Whenever we land back on the source list (add/edit/re-sync/delete closed), refocus "Add Source".
    LaunchedEffect(showAdd, editingSource, confirmDelete, resyncing) {
        if (!showAdd && editingSource == null && confirmDelete == null && !resyncing) {
            kotlinx.coroutines.delay(120); runCatching { addFocus.requestFocus() }
        }
    }
    // A failed import/re-sync swaps the form for an error screen — move focus onto its action button.
    LaunchedEffect(importState) {
        if (importState is SettingsViewModel.ImportState.Failed) {
            kotlinx.coroutines.delay(50); runCatching { errorFocus.requestFocus() }
        }
    }

    BackHandler {
        when {
            showAdd -> { showAdd = false; vm.resetImport() }
            resyncing -> { resyncing = false; vm.resetImport() }
            editingSource != null -> editingSource = null
            else -> onBack()
        }
    }

    // Re-sync overlay — shows the same import progress as adding a source, then auto-closes ~1s after
    // it reports the total.
    if (resyncing) {
        when (val s = importState) {
            SettingsViewModel.ImportState.Idle, SettingsViewModel.ImportState.Running -> CenterStatus {
                OwnTVSpinner(sizeDp = 56)
                Spacer(Modifier.height(16.dp))
                Text("Re-syncing…", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                progress?.let { Text("${it.processed} items", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
            is SettingsViewModel.ImportState.Success -> {
                CenterStatus {
                    Text("Re-sync complete", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(s.summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(1600); resyncing = false; vm.resetImport() }
            }
            is SettingsViewModel.ImportState.Failed -> CenterStatus {
                Text("Re-sync failed", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                OwnTVButton("Close", onClick = { resyncing = false; vm.resetImport() }, modifier = Modifier.focusRequester(errorFocus))
            }
        }
        return
    }

    editingSource?.let { src ->
        AddSourceScreen(
            initial = src,
            initialRefresh = src.id in refreshIds,
            onStartXtream = { n, server, u, p, ua, epg, refresh -> vm.updateSource(src.id, n, server, u, p, ua, epg, refresh); editingSource = null },
            onStartM3u = { n, url, ua, epg, refresh -> vm.updateSource(src.id, n, url, "", "", ua, epg, refresh); editingSource = null },
            onBack = { editingSource = null },
            modifier = modifier,
        )
        return
    }

    if (showAdd) {
        when (val s = importState) {
            SettingsViewModel.ImportState.Idle -> AddSourceScreen(
                onStartXtream = { n, server, u, p, ua, epg, refresh -> vm.addXtream(n, server, u, p, ua, epg, refresh) },
                onStartM3u = { n, url, ua, epg, refresh -> vm.addM3u(n, url, ua, epg, refresh) },
                onBack = { showAdd = false },
                modifier = modifier,
            )
            SettingsViewModel.ImportState.Running -> CenterStatus {
                OwnTVSpinner(sizeDp = 56)
                Spacer(Modifier.height(16.dp))
                Text("Importing…", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                progress?.let { Text("${it.processed} items", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
            is SettingsViewModel.ImportState.Success -> {
                LaunchedEffect(Unit) { showAdd = false; vm.resetImport() }
            }
            is SettingsViewModel.ImportState.Failed -> CenterStatus {
                Text("Import failed", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton("Back", onClick = { showAdd = false; vm.resetImport() }, style = OwnTVButtonStyle.SECONDARY)
                    OwnTVButton("Try again", onClick = { vm.resetImport() }, modifier = Modifier.focusRequester(errorFocus))
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            // Spatial D-pad entry from the sidebar would land mid-list — route it to "Add Source".
            // onEnter fires only for directional entry from outside; internal focus moves and
            // programmatic restores never re-trigger it (an onFocusChanged redirect did, freezing focus).
            .focusProperties { onEnter = { runCatching { addFocus.requestFocus() } } }
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sources", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.weight(1f))
            OwnTVButton("Add Source", onClick = { showAdd = true }, icon = tv.own.owntv.ui.components.OwnTVIcon.ADD, modifier = Modifier.focusRequester(addFocus))
        }
        Spacer(Modifier.height(8.dp))
        Text("Sources are shared across all profiles.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        if (sources.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No sources yet. Add an M3U or Xtream source.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sources, key = { it.id }) { source ->
                    // The default is the explicitly-chosen source, or the first one when none is set.
                    val isDefault = source.id == defaultId || (defaultId < 0 && source.id == sources.first().id)
                    val counts by remember(source.id) { vm.contentCounts(source.id) }.collectAsStateWithLifecycle(null)
                    SourceRow(
                        source = source,
                        refreshOnStart = source.id in refreshIds,
                        isDefault = isDefault,
                        countsLabel = counts?.breakdown,
                        showMakeDefault = sources.size > 1,
                        onMakeDefault = { vm.setDefaultSource(source.id) },
                        onEdit = { editingSource = source },
                        onResync = { resyncing = true; vm.resync(source) },
                        onDelete = { confirmDelete = source },
                    )
                }
            }
        }
    }

    confirmDelete?.let { src ->
        ConfirmDialog(
            title = "Delete “${src.name}”?",
            message = "This removes the source and all its channels, movies and series from every profile.",
            onConfirm = { vm.delete(src); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun SourceRow(
    source: SourceEntity,
    refreshOnStart: Boolean,
    isDefault: Boolean,
    countsLabel: String?,
    showMakeDefault: Boolean,
    onMakeDefault: () -> Unit,
    onEdit: () -> Unit,
    onResync: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                if (isDefault) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "DEFAULT",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                buildString {
                    append(when (source.type) { SourceType.XTREAM -> "Xtream • ${source.url}"; SourceType.M3U -> "M3U • ${source.url}"; SourceType.LOCAL_BACKUP -> "Backup" })
                    if (refreshOnStart) append("  •  ⟳ on startup")
                    if (!countsLabel.isNullOrBlank()) append("  •  $countsLabel")
                },
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (showMakeDefault && !isDefault) {
            OwnTVButton("Default", onClick = onMakeDefault, style = OwnTVButtonStyle.SECONDARY)
            Spacer(Modifier.width(10.dp))
        }
        OwnTVButton("Edit", onClick = onEdit, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(10.dp))
        OwnTVButton("Re-sync", onClick = onResync, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(10.dp))
        OwnTVButton("Delete", onClick = onDelete, style = OwnTVButtonStyle.SECONDARY)
    }
}

@Composable
private fun CenterStatus(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(OwnTVTheme.colors.surface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
internal fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(460.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(focus))
                Spacer(Modifier.weight(1f))
                OwnTVButton("Delete", onClick = onConfirm)
            }
        }
    }
}
