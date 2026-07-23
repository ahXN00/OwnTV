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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.core.sync.resyncBadgeText
import tv.own.owntv.core.sync.syncProgressCountsLabel
import tv.own.owntv.core.sync.work.CatalogSyncState
import tv.own.owntv.features.settings.data.PlaylistAutoRefresh
import tv.own.owntv.features.settings.data.SourceExpiryStatus
import tv.own.owntv.features.setup.AddSourceChooserScreen
import tv.own.owntv.features.setup.AddSourceScreen
import tv.own.owntv.features.setup.RemoteSetupScreen
import tv.own.owntv.features.setup.StalkerTestUi
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
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
    val colors = OwnTVTheme.colors

    var showAdd by remember { mutableStateOf(false) }
    // Within "Add source": null = the Remote|Manual chooser, else the chosen path.
    var addMode by remember { mutableStateOf<AddMode?>(null) }
    var editingSource by remember { mutableStateOf<SourceEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<SourceEntity?>(null) }
    val addFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }

    // Per-row focus restore (mirrors MoviesScreen): track the row the user is acting on so, when
    // edit/re-sync/delete closes, focus lands back inside the list — on the same row if it survived,
    // else the nearest neighbour that slid into its slot, else the first row, else "Add Source".
    var contextId by remember { mutableStateOf<Long?>(null) }
    var contextIndex by remember { mutableStateOf(-1) }
    val contextFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }

    // Whenever the list view is showing (no add form / edit form / delete dialog on top), restore
    // focus inside the list — not on "Add Source" as before, which is what pushed focus out of the
    // menu. contextId/contextFocus decide the specific row; firstRowFocus is the empty-list fallback.
    LaunchedEffect(showAdd, editingSource, confirmDelete) {
        if (showAdd || editingSource != null || confirmDelete != null) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        val targetId = contextId
        if (targetId != null && sources.any { it.id == targetId }) {
            runCatching { contextFocus.requestFocus() }
        } else if (sources.isNotEmpty()) {
            runCatching { firstRowFocus.requestFocus() }
        } else {
            runCatching { addFocus.requestFocus() }
        }
    }

    // When the row a delete landed on disappears from `sources`, move focus to the nearest surviving
    // neighbour (same index slot, else new last row, else first row) instead of letting focus escape
    // outside the menu.
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
    // Leaving "Add source" always returns to the Remote|Manual chooser next time (and drops any
    // running Remote listener), so a prior choice never skips the chooser.
    LaunchedEffect(showAdd) { if (!showAdd) { addMode = null; vm.stopRemoteListener() } }
    // A failed import/re-sync swaps the form for an error screen — move focus onto its action button.
    LaunchedEffect(importState) {
        if (importState is SettingsViewModel.ImportState.Failed) {
            kotlinx.coroutines.delay(50); runCatching { errorFocus.requestFocus() }
        }
    }

    BackHandler {
        when {
            showAdd -> { showAdd = false; addMode = null; vm.stopRemoteListener(); vm.cancelImport() }
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

    Box(modifier = modifier.fillMaxSize()) {
        if (editingSource != null) {
            val src = editingSource!!
            AddSourceScreen(
                initial = src,
                initialAutoRefresh = playlistAutoRefresh[src.id] ?: PlaylistAutoRefresh.OFF,
                initialIsDefault = src.id == defaultId,
                onStartXtream = { n, server, u, p, ua, epg, autoRefresh, live, movies, series, isDefault ->
                    vm.updateSource(
                        src.id, n, server, u, p, ua, epg, autoRefresh, isDefault,
                        syncLive = live != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncMovies = movies != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncSeries = series != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                    )
                    editingSource = null
                },
                onStartM3u = { n, url, ua, epg, autoRefresh, isDefault -> vm.updateSource(src.id, n, url, "", "", ua, epg, autoRefresh, isDefault); editingSource = null },
                onStartStalker = { n, url, mac, ua, autoRefresh, isDefault, live, movies, series ->
                    vm.updateSource(
                        src.id, n, url, "", "", ua, "", autoRefresh, isDefault, mac = mac,
                        syncLive = live != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncMovies = movies != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncSeries = series != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                    )
                    vm.resetStalkerTest()
                    editingSource = null
                },
                onTestStalker = { url, mac, ua -> vm.testStalker(url, mac, ua) },
                stalkerTest = stalkerTestUi,
                onBack = { vm.resetStalkerTest(); editingSource = null },
                modifier = Modifier,
            )
        } else if (showAdd) {
            when (val s = importState) {
                SettingsViewModel.ImportState.Idle -> when (addMode) {
                    null -> AddSourceChooserScreen(
                        onRemote = { addMode = AddMode.REMOTE },
                        onManual = { addMode = AddMode.MANUAL },
                        onBack = { showAdd = false },
                        modifier = Modifier,
                    )
                    AddMode.REMOTE -> RemoteSetupScreen(
                        state = vm.remoteState.collectAsStateWithLifecycle().value,
                        payloads = vm.remotePayloads,
                        onStartListener = { port -> vm.startRemoteListener(port) },
                        onStopListener = { vm.stopRemoteListener() },
                        // A phone submission hands off to the pre-filled Manual form.
                        onPayloadReceived = { addMode = AddMode.MANUAL },
                        onBack = { vm.stopRemoteListener(); addMode = null },
                        modifier = Modifier,
                    )
                    AddMode.MANUAL -> AddSourceScreen(
                        onStartXtream = { n, server, u, p, ua, epg, autoRefresh, live, movies, series, isDefault ->
                            vm.addXtream(n, server, u, p, ua, epg, autoRefresh, live, movies, series, isDefault)
                        },
                        onStartM3u = { n, url, ua, epg, autoRefresh, isDefault -> vm.addM3u(n, url, ua, epg, autoRefresh, isDefault) },
                        onStartStalker = { n, url, mac, ua, autoRefresh, isDefault, live, movies, series ->
                            vm.resetStalkerTest()
                            vm.addStalker(n, url, mac, ua, autoRefresh, isDefault, live, movies, series)
                        },
                        onTestStalker = { url, mac, ua -> vm.testStalker(url, mac, ua) },
                        stalkerTest = stalkerTestUi,
                        // Submissions from the Remote screen land here pre-filled (type + fields).
                        remotePayload = vm.remotePayload,
                        onRemotePayloadConsumed = { vm.consumeRemotePayload() },
                        // A newly-added playlist can be made default only when others already exist.
                        showDefaultToggle = sources.isNotEmpty(),
                        onBack = { vm.resetStalkerTest(); addMode = null },
                        modifier = Modifier,
                        initial = vm.lastFailedSource, // pre-fill on retry — no re-typing after a typo
                    )
                }
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
                    OwnTVButton("Cancel", onClick = { showAdd = false; vm.cancelImport() }, style = OwnTVButtonStyle.SECONDARY)
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
                            OwnTVButton("Done", onClick = { showAdd = false; vm.resetImport() })
                        }
                    } else {
                        LaunchedEffect(Unit) { showAdd = false; vm.resetImport() }
                    }
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .roundedPanel()
                    // D-pad entry from outside (sidebar / back from a sub-screen) should fall INSIDE the
                    // menu — on the last-acted row if there is one, else the first row, else "Add Source"
                    // (only when the list is empty). Previously this always went to "Add Source", which is
                    // why focus never landed in the list.
                    .focusProperties {
                        onEnter = {
                            val tid = contextId
                            when {
                                tid != null && sources.any { it.id == tid } -> runCatching { contextFocus.requestFocus() }
                                sources.isNotEmpty() -> runCatching { firstRowFocus.requestFocus() }
                                else -> runCatching { addFocus.requestFocus() }
                            }
                        }
                    }
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
                        itemsIndexed(sources, key = { _, it -> it.id }) { index, source ->
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
                                // Bind contextFocus to the row the user is acting on (so we can restore it),
                                // and firstRowFocus to row 0 (entry / empty-context fallback).
                                rowModifier = when {
                                    source.id == contextId -> Modifier.focusRequester(contextFocus)
                                    index == 0 -> Modifier.focusRequester(firstRowFocus)
                                    else -> Modifier
                                },
                                onEdit = { contextId = source.id; contextIndex = index; editingSource = source },
                                onResync = { contextId = source.id; contextIndex = index; vm.resync(source) },
                                onCancelSync = { contextId = source.id; contextIndex = index; vm.cancelResync(source) },
                                onDelete = { contextId = source.id; contextIndex = index; confirmDelete = source },
                            )
                        }
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
}

@Composable
private fun SourceRow(
    source: SourceEntity,
    autoRefresh: PlaylistAutoRefresh,
    isDefault: Boolean,
    expiry: SourceExpiryStatus?,
    counts: SyncCounts?,
    syncState: CatalogSyncState,
    isDeleting: Boolean,
    rowModifier: Modifier,
    onEdit: () -> Unit,
    onResync: () -> Unit,
    onCancelSync: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val activeSync = syncState as? CatalogSyncState.Syncing
    val activeCountsLabel = activeSync?.countsLabel(source.type, counts)
    Row(
        modifier = rowModifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh).padding(16.dp),
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
                if (expiry?.isExpired == true) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "EXPIRED",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.surface,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.favorite).padding(horizontal = 8.dp, vertical = 2.dp),
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
                    val expiryLabel = expiry?.label
                    if (!expiryLabel.isNullOrBlank()) append("  •  Expires $expiryLabel")
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
            // One stable button whose label/action flips with syncState. Keeping the SAME composable
            // in the tree (instead of an if/else that disposes "Re-sync" and composes "Cancel") means
            // the focusable node is never removed, so D-pad focus survives the swap instead of escaping
            // the row — that swap was the re-sync focus loss.
            OwnTVButton(
                label = if (syncState.isActive) "Cancel" else "Re-sync",
                onClick = if (syncState.isActive) onCancelSync else onResync,
                style = OwnTVButtonStyle.SECONDARY,
            )
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
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
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

/** How the user chose to add a source: fill it from a phone (Remote) or type it here (Manual). */
private enum class AddMode { REMOTE, MANUAL }
