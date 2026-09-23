package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.core.sync.resyncProgressPercent
import tv.own.owntv.core.sync.work.CatalogSyncState
import tv.own.owntv.ui.components.breakdownText
import tv.own.owntv.ui.components.summaryText
import tv.own.owntv.ui.components.displayText
import tv.own.owntv.ui.components.detailText
import tv.own.owntv.ui.components.primaryText
import tv.own.owntv.ui.components.remainderText
import tv.own.owntv.ui.components.warningText
import tv.own.owntv.core.repository.SourceTestResult
import tv.own.owntv.core.setup.detailLines
import tv.own.owntv.core.setup.headline
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.features.setup.playlistAutoRefreshLabel
import tv.own.owntv.features.setup.AddSourceChooserScreen
import tv.own.owntv.features.setup.AddSourceScreen
import tv.own.owntv.features.setup.RemoteSetupScreen
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
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
    val sourceTest by vm.sourceTest.collectAsStateWithLifecycle()
    val deletingIds by vm.deletingSourceIds.collectAsStateWithLifecycle()
    val epgSync by vm.epgSync.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    val defaultIptvName = stringResource(R.string.setup_default_iptv)
    val defaultPlaylistName = stringResource(R.string.setup_name_default_playlist)
    val defaultPortalName = stringResource(R.string.setup_default_portal)

    var showAdd by remember { mutableStateOf(false) }
    // Within "Add source": null = the Remote|Manual chooser, else the chosen path.
    var addMode by remember { mutableStateOf<AddMode?>(null) }
    var editingSource by remember { mutableStateOf<SourceEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<SourceEntity?>(null) }
    var resyncChoice by remember { mutableStateOf<SourceEntity?>(null) }
    // Set while the "this will stop playback and take a while" confirmation is on screen.
    var confirmRetest by remember { mutableStateOf<SourceEntity?>(null) }
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

    Box(modifier = modifier.fillMaxSize()) {
        if (editingSource != null) {
            val src = editingSource!!
            AddSourceScreen(
                initial = src,
                initialAutoRefresh = playlistAutoRefresh[src.id] ?: PlaylistRefresh.OFF,
                initialIsDefault = src.id == defaultId,
                onStartXtream = { n, server, u, p, ua, ref, epg, autoRefresh, live, movies, series, isDefault, preferHls ->
                    vm.updateSource(
                        src.id, n, server, u, p, ua, epg, autoRefresh, isDefault, httpReferer = ref,
                        syncLive = live != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncMovies = movies != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncSeries = series != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        preferHls = preferHls,
                    )
                    editingSource = null
                },
                onStartM3u = { n, url, ua, ref, epg, autoRefresh, isDefault -> vm.updateSource(src.id, n, url, "", "", ua, epg, autoRefresh, isDefault, httpReferer = ref); editingSource = null },
                onStartStalker = { n, url, mac, serialNumber, deviceId, deviceId2, signature, ua, ref, autoRefresh, isDefault, live, movies, series ->
                    vm.updateSource(
                        src.id, n, url, "", "", ua, "", autoRefresh, isDefault, mac = mac, httpReferer = ref,
                        stalkerSerialNumber = serialNumber, stalkerDeviceId = deviceId,
                        stalkerDeviceId2 = deviceId2, stalkerSignature = signature,
                        syncLive = live != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncMovies = movies != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                        syncSeries = series != tv.own.owntv.core.sync.SyncScopeChoice.Off,
                    )
                    editingSource = null
                },
                onBack = { editingSource = null },
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
                        // A remote submission hands off to the pre-filled Manual form.
                        onPayloadReceived = { addMode = AddMode.MANUAL },
                        onBack = { vm.stopRemoteListener(); addMode = null },
                        modifier = Modifier,
                    )
                    AddMode.MANUAL -> AddSourceScreen(
                        onStartXtream = { n, server, u, p, ua, ref, epg, autoRefresh, live, movies, series, isDefault, preferHls ->
                            vm.addXtream(n.ifBlank { defaultIptvName }, server, u, p, ua, epg, autoRefresh, live, movies, series, isDefault, preferHls, httpReferer = ref)
                        },
                        onStartM3u = { n, url, ua, ref, epg, autoRefresh, isDefault -> vm.addM3u(n.ifBlank { defaultPlaylistName }, url, ua, epg, autoRefresh, isDefault, httpReferer = ref) },
                        onStartStalker = { n, url, mac, serialNumber, deviceId, deviceId2, signature, ua, ref, autoRefresh, isDefault, live, movies, series ->
                            vm.addStalker(
                                n.ifBlank { defaultPortalName }, url, mac, serialNumber, deviceId,
                                deviceId2, signature, ua, autoRefresh, isDefault, live, movies, series, httpReferer = ref,
                            )
                        },
                        // Submissions from the Remote screen land here pre-filled (type + fields).
                        remotePayload = vm.remotePayload,
                        onRemotePayloadConsumed = { vm.consumeRemotePayload() },
                        // A newly-added playlist can be made default only when others already exist.
                        showDefaultToggle = sources.isNotEmpty(),
                        onBack = { addMode = null },
                        modifier = Modifier,
                        initial = vm.lastFailedSource, // pre-fill on retry — no re-typing after a typo
                    )
                }
                SettingsViewModel.ImportState.Running -> CenterStatus {
                    val display = progress?.importProgressDisplay()
                    OwnTVSpinner(sizeDp = 56)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.settings_sources_importing),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(display?.primaryText() ?: stringResource(R.string.settings_sources_preparing), style = MaterialTheme.typography.headlineSmall, color = colors.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(display?.detailText() ?: stringResource(R.string.settings_sources_preparing), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    OwnTVButton(stringResource(R.string.common_cancel), onClick = { showAdd = false; vm.cancelImport() }, style = OwnTVButtonStyle.SECONDARY)
                }
                is SettingsViewModel.ImportState.Success -> {
                    // Semi-auto EPG: ask → sync (with a live count, like the import) → done, before returning.
                    if (epgSync !is EpgSyncUi.Hidden) {
                        EpgSyncDialog(state = epgSync, onSync = vm::syncPendingEpg, onDismiss = vm::dismissPendingEpg)
                    } else if (s.warnings.isNotEmpty() || s.remainder.hasAny) {
                        CenterStatus {
                            Text(stringResource(R.string.settings_sources_import_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                            Spacer(Modifier.height(8.dp))
                            Text(s.counts.summaryText(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            s.warnings.warningText()?.let { warning ->
                                Spacer(Modifier.height(4.dp))
                                Text(warning, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                            s.remainder.remainderText()?.let { remainder ->
                                Spacer(Modifier.height(4.dp))
                                Text(remainder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(20.dp))
                            OwnTVButton(stringResource(R.string.common_done), onClick = { showAdd = false; vm.resetImport() })
                        }
                    } else {
                        LaunchedEffect(Unit) { showAdd = false; vm.resetImport() }
                    }
                }
                is SettingsViewModel.ImportState.Failed -> CenterStatus {
                    Text(stringResource(R.string.settings_sources_import_failed), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(s.failure.displayText(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OwnTVButton(stringResource(R.string.common_back), onClick = { showAdd = false; vm.resetImport() }, style = OwnTVButtonStyle.SECONDARY)
                        OwnTVButton(stringResource(R.string.settings_sources_try_again), onClick = { vm.resetImport() }, modifier = Modifier.focusRequester(errorFocus))
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
                    Text(stringResource(R.string.settings_sources_title), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(stringResource(R.string.settings_sources_add), onClick = { showAdd = true }, icon = tv.own.owntv.ui.components.OwnTVIcon.ADD, modifier = Modifier.focusRequester(addFocus))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_sources_description), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))

                if (sources.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.settings_sources_empty), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
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
                                autoRefresh = playlistAutoRefresh[source.id] ?: PlaylistRefresh.OFF,
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
                                onTest = { contextId = source.id; contextIndex = index; vm.testSource(source) },
                                onResync = { contextId = source.id; contextIndex = index; resyncChoice = source },
                                onCancelSync = { contextId = source.id; contextIndex = index; vm.cancelResync(source) },
                                onDelete = { contextId = source.id; contextIndex = index; confirmDelete = source },
                            )
                        }
                    }
                }
            }
        }

        resyncChoice?.let { src ->
            ResyncChoiceDialog(
                sourceName = src.name,
                onNormal = { vm.resync(src); resyncChoice = null },
                onClean = { vm.resync(src, clean = true); resyncChoice = null },
                onDismiss = { resyncChoice = null },
            )
        }

        sourceTest?.let { state ->
            SourceTestDialog(
                state = state,
                onDismiss = { vm.dismissSourceTest() },
                // Only for a playlist that exists; the measurement is stored against its row.
                onRetest = sources.firstOrNull { it.name == state.sourceName }?.let { src ->
                    { confirmRetest = src }
                },
                onSkip = { vm.skipConnectionMeasurement() },
            )
        }

        // Measuring opens real streams, so the user is told plainly that playback stops and that it
        // is slow, and gets to say no. Same shape as the delete confirmation beneath it.
        confirmRetest?.let { src ->
            ConfirmDialog(
                title = stringResource(R.string.settings_sources_probe_title),
                message = stringResource(R.string.settings_sources_probe_warning),
                onConfirm = { confirmRetest = null; vm.retestSource(src) },
                onDismiss = { confirmRetest = null },
                confirmLabel = R.string.settings_sources_retest,
            )
        }

        confirmDelete?.let { src ->
            ConfirmDialog(
                title = stringResource(R.string.settings_sources_delete_title, src.name),
                message = stringResource(R.string.settings_sources_delete_message),
                onConfirm = { vm.delete(src); confirmDelete = null },
                onDismiss = { confirmDelete = null },
            )
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceEntity,
    autoRefresh: PlaylistRefresh,
    isDefault: Boolean,
    expiry: String?,
    counts: SyncCounts?,
    syncState: CatalogSyncState,
    isDeleting: Boolean,
    rowModifier: Modifier,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onResync: () -> Unit,
    onCancelSync: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val activeSync = syncState as? CatalogSyncState.Syncing
    val activeCounts = activeSync?.countsLabel(source.type, counts)
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
                        stringResource(R.string.settings_sources_default),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (isDeleting) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.settings_sources_deleting),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                activeSync?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        resyncProgressPercent(it.baseItemCount, it.totalProcessed)?.let { percent -> stringResource(R.string.sync_progress_percent, percent) }
                            ?: stringResource(R.string.sync_progress_syncing),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            val sourceTypeText = stringResource(
                when (source.type) {
                    SourceType.XTREAM -> R.string.settings_sources_type_xtream
                    SourceType.M3U -> R.string.settings_sources_type_m3u
                    SourceType.STALKER -> R.string.settings_sources_type_stalker
                    SourceType.LOCAL_BACKUP -> R.string.settings_sources_backup
                },
                source.url,
            )
            val visibleCounts = if (activeSync == null) counts?.breakdownText() else activeCounts?.displayText()
            val details = buildList {
                add(sourceTypeText)
                if (autoRefresh.mode != PlaylistAutoRefresh.OFF) add(stringResource(R.string.settings_sources_auto_refresh, playlistAutoRefreshLabel(autoRefresh)))
                if (!expiry.isNullOrBlank()) add(stringResource(R.string.settings_sources_expiry, expiry))
                if (!visibleCounts.isNullOrBlank()) add(visibleCounts)
                else if (activeSync != null) add(stringResource(R.string.settings_sources_preparing_detail))
            }
            Text(
                details.joinToString(stringResource(R.string.settings_sources_details_separator)),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (isDeleting) {
            // Removing a huge source cascades through hundreds of thousands of rows — show that the
            // removal is running and take the row's actions away so it can't be edited/re-synced/
            // deleted again mid-delete.
            OwnTVSpinner(sizeDp = 22)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.settings_sources_removing_detail), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            OwnTVButton(stringResource(R.string.settings_sources_edit), onClick = onEdit, style = OwnTVButtonStyle.SECONDARY)
            Spacer(Modifier.width(10.dp))
            // "Info", not "Test": the expensive measurement now lives behind Re-test inside the
            // popup, and this button answers the question it always really answered — is it alive?
            OwnTVButton(stringResource(R.string.settings_sources_info), onClick = onTest, style = OwnTVButtonStyle.SECONDARY)
            Spacer(Modifier.width(10.dp))
            // One stable button whose label/action flips with syncState. Keeping the SAME composable
            // in the tree (instead of an if/else that disposes "Re-sync" and composes "Cancel") means
            // the focusable node is never removed, so D-pad focus survives the swap instead of escaping
            // the row — that swap was the re-sync focus loss.
            OwnTVButton(
                label = stringResource(if (syncState.isActive) R.string.settings_sources_cancel else R.string.settings_sources_resync),
                onClick = if (syncState.isActive) onCancelSync else onResync,
                style = OwnTVButtonStyle.SECONDARY,
            )
            Spacer(Modifier.width(10.dp))
            OwnTVButton(stringResource(R.string.settings_sources_delete), onClick = onDelete, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

private fun CatalogSyncState.Syncing.countsLabel(sourceType: SourceType, stored: SyncCounts?): tv.own.owntv.core.sync.SyncProgressCounts? {
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
    return counts.takeIf { it.hasItems }
}

@Composable
private fun CenterStatus(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().roundedPanel(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * What the confirming button says. Defaults to Delete because every caller was a deletion when
     * this was written — which is exactly how the connection-measurement confirmation ended up
     * offering "Delete", a word with nothing to do with what it would have done.
     */
    @StringRes confirmLabel: Int = R.string.common_delete,
) {
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 460.dp, padding = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(focus))
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(confirmLabel), onClick = onConfirm)
            }
        }
    }
    }
}

/**
 * Resync picker: an ordinary refresh, or one that is also allowed to drop titles the provider has
 * stopped listing.
 *
 * A normal resync will not remove more than half a source's rows, because a truncated provider
 * response is indistinguishable from a genuinely shrunken catalog and guessing wrong wipes a working
 * playlist. The consequence is that a provider that really did drop a lot of titles leaves them
 * behind with no way out — this dialog is that way out, kept behind a deliberate choice.
 *
 * Focus starts on the ordinary refresh, so the common case is still Select-then-Select and a
 * mis-press can't trigger removals. Back cancels.
 */
@Composable
private fun ResyncChoiceDialog(
    sourceName: String,
    onNormal: () -> Unit,
    onClean: () -> Unit,
    onDismiss: () -> Unit,
) {
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 520.dp, padding = 28.dp)) {
            Text(stringResource(R.string.settings_sources_resync_title_full, sourceName), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.settings_sources_resync_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OwnTVButton(stringResource(R.string.settings_sources_resync_now_full), onClick = onNormal, modifier = Modifier.fillMaxWidth().focusRequester(focus))
                OwnTVButton(
                    stringResource(R.string.settings_sources_resync_remove_full),
                    onClick = onClean,
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    }
}

/** How the user chose to add a source: fill it from another device (Remote) or type it here (Manual). */
private enum class AddMode { REMOTE, MANUAL }

/**
 * Result of the row's "Test" button: is the server reachable, is the subscription still good, and how
 * many of the account's connections are in use right now.
 *
 * The provider's own status word is shown verbatim rather than translated — panels invent their own
 * vocabulary there, and a wrong translation of "Banned" would be worse than the English original.
 */
@Composable
internal fun SourceTestDialog(
    state: SourceTestUi,
    onDismiss: () -> Unit,
    /** Null hides Re-test — the add form has no saved playlist to measure yet. */
    onRetest: (() -> Unit)? = null,
    /** Abandon a measurement in progress. Falls back to simply closing when not supplied. */
    onSkip: (() -> Unit)? = null,
) {
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    // Keyed on *which* button carries the requester, not on first composition.
    //
    // While measuring that button is Skip; when the measurement finishes it becomes OK, and the node
    // holding the requester is therefore removed and replaced. A one-shot request left focus on a
    // node that no longer existed, so the finished dialog could not be dismissed with the remote at
    // all — the press went nowhere. A frame is waited for because the replacement must exist before
    // it can be asked to take focus.
    val measuring = state is SourceTestUi.Measuring
    LaunchedEffect(measuring) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.dialogPanel(width = 520.dp, padding = 28.dp)) {
            Text(stringResource(R.string.settings_sources_test_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(state.sourceName, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            when (state) {
                is SourceTestUi.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OwnTVSpinner(sizeDp = 22)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.setup_testing), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                // The measurement is slow by nature, so it says which stream it is on rather than
                // spinning silently for two minutes and looking like a hang.
                is SourceTestUi.Measuring -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OwnTVSpinner(sizeDp = 22)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(
                            R.string.settings_sources_probe_running,
                            state.progress.stream,
                            state.progress.maxStreams,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
                is SourceTestUi.Done -> SourceTestReport(state.result, state.limit)
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // While measuring, the only honest button is one that abandons it: OK would suggest
                // the answer is already in. Nobody should be trapped for two minutes by a provider
                // that is simply slow.
                if (state is SourceTestUi.Measuring) {
                    OwnTVButton(
                        stringResource(R.string.settings_sources_probe_skip),
                        onClick = onSkip ?: onDismiss,
                        modifier = Modifier.focusRequester(focus),
                    )
                } else {
                    OwnTVButton(stringResource(R.string.common_ok), onClick = onDismiss, modifier = Modifier.focusRequester(focus))
                }
                // Only once the quick check has finished: starting a two-minute measurement on top of
                // a request that is still running would race it for the same connection.
                if (onRetest != null && state is SourceTestUi.Done) {
                    OwnTVButton(
                        stringResource(R.string.settings_sources_retest),
                        onClick = onRetest,
                        style = OwnTVButtonStyle.SECONDARY,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun SourceTestReport(result: SourceTestResult, limit: tv.own.owntv.core.live.ConnectionLimit?) {
    val colors = OwnTVTheme.colors
    val res = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            result.headline(res),
            style = MaterialTheme.typography.bodyLarge,
            color = if (result is SourceTestResult.Ok) colors.onSurface else colors.favorite,
        )
        result.detailLines(res, limit).forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
