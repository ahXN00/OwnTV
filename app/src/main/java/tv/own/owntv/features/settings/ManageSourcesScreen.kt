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
import androidx.compose.runtime.setValue
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
import tv.own.owntv.core.cloud.CloudServerState
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.core.sync.resyncBadgeText
import tv.own.owntv.core.sync.syncProgressCountsLabel
import tv.own.owntv.core.sync.work.CatalogSyncState
import tv.own.owntv.features.settings.data.PlaylistAutoRefresh
import tv.own.owntv.features.setup.AddSourceScreen
import tv.own.owntv.features.setup.StalkerTestUi
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.theme.OwnTVTheme

/** Phase 13 — list / add / re-sync / delete the active profile's IPTV sources. */
@Composable
fun ManageSourcesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val playlistAutoRefresh by vm.playlistAutoRefresh.collectAsStateWithLifecycle()
    val defaultId by vm.defaultSourceId.collectAsStateWithLifecycle()
    val sourceExpiry by vm.sourceExpiry.collectAsStateWithLifecycle()
    val deletingIds by vm.deletingSourceIds.collectAsStateWithLifecycle()
    val epgSync by vm.epgSync.collectAsStateWithLifecycle()
    val cloudState by vm.cloudState.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    var showAdd by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<SourceEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<SourceEntity?>(null) }
    val addFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    // Whenever we land back on the source list (add/edit/re-sync/delete closed), refocus "Add Source".
    LaunchedEffect(showAdd, editingSource, confirmDelete) {
        if (!showAdd && editingSource == null && confirmDelete == null) {
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
            showAdd -> { showAdd = false; vm.clearTransientAddSourceState() }
            editingSource != null -> editingSource = null
            else -> onBack()
        }
    }

    val stalkerTest by vm.stalkerTest.collectAsStateWithLifecycle()
    val stalkerTestUi = when (val t = stalkerTest) {
        SettingsViewModel.StalkerTestState.Idle -> StalkerTestUi.Idle
        SettingsViewModel.StalkerTestState.Testing -> StalkerTestUi.Testing
        is SettingsViewModel.StalkerTestState.Ok -> StalkerTestUi.Ok(t.summary)
        is SettingsViewModel.StalkerTestState.Failed -> StalkerTestUi.Failed(t.message)
    }

    editingSource?.let { src ->
        AddSourceScreen(
            initial = src,
            initialAutoRefresh = playlistAutoRefresh[src.id] ?: PlaylistAutoRefresh.OFF,
            initialIsDefault = src.id == defaultId,
            onStartXtream = { n, server, u, p, ua, epg, autoRefresh, _, _, _, isDefault ->
                vm.updateSource(src.id, n, server, u, p, ua, epg, autoRefresh, isDefault)
                editingSource = null
            },
            onStartM3u = { n, url, ua, epg, autoRefresh, isDefault -> vm.updateSource(src.id, n, url, "", "", ua, epg, autoRefresh, isDefault); editingSource = null },
            onStartCloud = null,
            onStopCloud = null,
            cloudState = CloudServerState.Idle,
            onStartStalker = { n, url, mac, ua, autoRefresh, isDefault ->
                vm.updateSource(src.id, n, url, "", "", ua, "", autoRefresh, isDefault, mac = mac)
                vm.resetStalkerTest()
                editingSource = null
            },
            onTestStalker = { url, mac, ua -> vm.testStalker(url, mac, ua) },
            stalkerTest = stalkerTestUi,
            onBack = { vm.resetStalkerTest(); editingSource = null },
            modifier = modifier,
        )
        return
    }

    if (showAdd) {
        when (val s = importState) {
            SettingsViewModel.ImportState.Idle -> AddSourceScreen(
                onStartXtream = { n, server, u, p, ua, epg, autoRefresh, live, movies, series, isDefault ->
                    vm.addXtream(n, server, u, p, ua, epg, autoRefresh, live, movies, series, isDefault)
                },
                onStartM3u = { n, url, ua, epg, autoRefresh, isDefault -> vm.addM3u(n, url, ua, epg, autoRefresh, isDefault) },
                onStartCloud = { port -> vm.startCloudListener(port) },
                onStopCloud = { vm.stopCloudListener() },
                cloudState = cloudState,
                onStartStalker = { n, url, mac, ua, autoRefresh, isDefault ->
                    vm.resetStalkerTest()
                    vm.addStalker(n, url, mac, ua, autoRefresh, isDefault)
                },
                onTestStalker = { url, mac, ua -> vm.testStalker(url, mac, ua) },
                stalkerTest = stalkerTestUi,
                // A newly-added playlist can be made default only when others already exist.
                showDefaultToggle = sources.isNotEmpty(),
                onBack = { vm.clearTransientAddSourceState(); showAdd = false },
                modifier = modifier,
                initial = vm.lastFailedSource, // pre-fill on retry — no re-typing after a typo
            )
            SettingsViewModel.ImportState.Running -> CenterStatus {
                val display = progress?.importProgressDisplay()
                OwnTVSpinner(sizeDp = 56)
                Spacer(Modifier.height(16.dp))
                Text(
                    display?.title ?: "Importing catalog…",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(display?.primaryText ?: "Preparing catalog", style = MaterialTheme.typography.headlineSmall, color = colors.primary)
                Spacer(Modifier.height(4.dp))
                Text(display?.detail ?: "Preparing catalog", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                OwnTVButton("Cancel", onClick = { showAdd = false; vm.clearTransientAddSourceState() }, style = OwnTVButtonStyle.SECONDARY)
            }
            is SettingsViewModel.ImportState.Success -> {
                // Semi-auto EPG: ask → sync (with a live count, like the import) → done, before returning.
                if (epgSync !is EpgSyncUi.Hidden) {
                    EpgSyncDialog(state = epgSync, onSync = vm::syncPendingEpg, onDismiss = vm::dismissPendingEpg)
                } else if (s.summary.contains("Imported with warnings:")) {
                    CenterStatus {
                        Text("Import complete", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(s.summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        OwnTVButton("Done", onClick = { showAdd = false; vm.clearTransientAddSourceState() })
                    }
                } else {
                    LaunchedEffect(Unit) { showAdd = false; vm.clearTransientAddSourceState() }
                }
            }
            is SettingsViewModel.ImportState.Failed -> CenterStatus {
                Text("Import failed", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton("Back", onClick = { showAdd = false; vm.clearTransientAddSourceState() }, style = OwnTVButtonStyle.SECONDARY)
                    OwnTVButton("Try again", onClick = { vm.resetImport() }, modifier = Modifier.focusRequester(errorFocus))
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
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
                    // Default is only the explicitly-chosen source; when none is set every playlist shows
                    // (no badge). Chosen via the add/edit form's "Default playlist" toggle, not a row action.
                    val isDefault = source.id == defaultId
                    val counts by remember(source.id) { vm.contentCounts(source.id) }.collectAsStateWithLifecycle(null)
                    val syncState by remember(source.id) { vm.syncState(source.id) }.collectAsStateWithLifecycle(CatalogSyncState.Idle)
                    SourceRow(
                        source = source,
                        autoRefresh = playlistAutoRefresh[source.id] ?: PlaylistAutoRefresh.OFF,
                        isDefault = isDefault,
                        expiry = sourceExpiry[source.id],
                        counts = counts,
                        syncState = syncState,
                        isDeleting = source.id in deletingIds,
                        onEdit = { editingSource = source },
                        onResync = { vm.resync(source) },
                        onCancelSync = { vm.cancelResync(source) },
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
    autoRefresh: PlaylistAutoRefresh,
    isDefault: Boolean,
    expiry: String?,
    counts: SyncCounts?,
    syncState: CatalogSyncState,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onResync: () -> Unit,
    onCancelSync: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val activeSync = syncState as? CatalogSyncState.Syncing
    val activeCountsLabel = activeSync?.countsLabel(source.type, counts)
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
                if (isDeleting) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "DELETING…",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                activeSync?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        resyncBadgeText(it.baseItemCount, it.totalProcessed),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                buildString {
                    append(when (source.type) { SourceType.XTREAM -> "Xtream • ${source.url}"; SourceType.M3U -> "M3U • ${source.url}"; SourceType.STALKER -> "Stalker • ${source.url}"; SourceType.LOCAL_BACKUP -> "Backup" })
                    if (autoRefresh != PlaylistAutoRefresh.OFF) append("  •  ⟳ ${autoRefresh.label}")
                    // Subscription expiry (Phase F): Xtream user_info.exp_date / Stalker account_info.
                    if (!expiry.isNullOrBlank()) append("  •  Expires $expiry")
                    val visibleCounts = if (activeSync == null) counts?.breakdown else activeCountsLabel
                    if (!visibleCounts.isNullOrBlank()) {
                        append("  •  $visibleCounts")
                    } else if (activeSync != null) {
                        append("  •  Preparing catalog")
                    }
                },
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (isDeleting) {
            // Removing a huge source cascades through hundreds of thousands of rows — show that the
            // removal is running and take the row's actions away so it can't be edited/re-synced/
            // deleted again mid-delete.
            OwnTVSpinner(sizeDp = 22)
            Spacer(Modifier.width(10.dp))
            Text("Removing…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            OwnTVButton("Edit", onClick = onEdit, style = OwnTVButtonStyle.SECONDARY)
            Spacer(Modifier.width(10.dp))
            if (syncState.isActive) {
                OwnTVButton("Cancel", onClick = onCancelSync, style = OwnTVButtonStyle.SECONDARY)
            } else {
                OwnTVButton("Re-sync", onClick = onResync, style = OwnTVButtonStyle.SECONDARY)
            }
            Spacer(Modifier.width(10.dp))
            OwnTVButton("Delete", onClick = onDelete, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

private fun CatalogSyncState.Syncing.countsLabel(sourceType: SourceType, stored: SyncCounts?): String? {
    fun visibleCount(active: Boolean, processed: Int, storedCount: Int): Int =
        if (active) processed else storedCount

    val live = visibleCount(liveActive, liveProcessed, stored?.channels ?: 0)
    val movies = visibleCount(moviesActive, moviesProcessed, stored?.movies ?: 0)
    val series = visibleCount(seriesActive, seriesProcessed, stored?.series ?: 0)
    val counts = when (sourceType) {
        SourceType.M3U -> SyncProgressCounts(
            live = live,
            movies = 0,
            series = 0,
            liveActive = true,
            moviesActive = false,
            seriesActive = false,
        )
        SourceType.XTREAM -> SyncProgressCounts(
            live = live,
            movies = movies,
            series = series,
            liveActive = liveActive || live > 0,
            moviesActive = moviesActive || movies > 0,
            seriesActive = seriesActive || series > 0,
        )
        SourceType.LOCAL_BACKUP -> SyncProgressCounts(
            live = 0,
            movies = 0,
            series = 0,
            liveActive = false,
            moviesActive = false,
            seriesActive = false,
        )
        // Stalker: LIVE (Phase C-1) + VOD/series (Phase D-1) all populate.
        SourceType.STALKER -> SyncProgressCounts(
            live = live,
            movies = movies,
            series = series,
            liveActive = liveActive || live > 0,
            moviesActive = moviesActive || movies > 0,
            seriesActive = seriesActive || series > 0,
        )
    }
    return syncProgressCountsLabel(counts)
}

@Composable
private fun CenterStatus(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().roundedPanel(), contentAlignment = Alignment.Center) {
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
        Column(Modifier.dialogPanel(width = 460.dp, padding = 28.dp)) {
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
