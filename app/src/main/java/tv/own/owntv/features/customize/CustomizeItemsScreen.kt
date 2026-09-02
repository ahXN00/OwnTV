package tv.own.owntv.features.customize

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.jumpLazyListTo
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.LocalActionSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.PopupFontTheme

/**
 * Category items screen — shows every item (channel/movie/series) in a category, including hidden
 * ones (marked "Hidden"), with hide/show, rename (Live only), reorder controls and span selection.
 *
 * Reached from [CustomizeScreen] by pressing OK on a category name.
 */
@Composable
fun CustomizeItemsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parentVm: CustomizeViewModel = koinViewModel()
    val vm: CustomizeItemsViewModel = koinViewModel()
    val selectedCategory by parentVm.selectedCategory.collectAsStateWithLifecycle()
    val section by parentVm.section.collectAsStateWithLifecycle()
    val isLive = section == MediaType.LIVE
    val rangeAnchorKey by vm.rangeAnchorKey.collectAsStateWithLifecycle()
    val rangeMode by vm.rangeMode.collectAsStateWithLifecycle()
    val rangeEndKey by vm.rangeEndKey.collectAsStateWithLifecycle()
    val rangeSelectedKeys by vm.rangeSelectedKeys.collectAsStateWithLifecycle()
    val visibilityFilter by vm.visibilityFilter.collectAsStateWithLifecycle()

    // Propagate the category info from the parent ViewModel into the items ViewModel.
    val ctx = parentVm.ctxForItems()
    LaunchedEffect(selectedCategory) {
        val row = selectedCategory
        if (row != null && ctx != null) {
            vm.open(
                CustomizeItemsViewModel.CatInfo(
                    categoryId = ctx.categoryId,
                    contextKey = row.key,
                    mediaType = ctx.mediaType,
                    sourceIds = ctx.sourceIds,
                )
            )
        } else {
            vm.close()
        }
    }

    if (selectedCategory == null) return

    val items = vm.items.collectAsLazyPagingItems()
    val colors = OwnTVTheme.colors
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listPaneFocused by remember { mutableStateOf(false) }
    var focusedItemIndex by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<CustomizeItemRow?>(null) }
    var showFilterPicker by remember { mutableStateOf(false) }
    // The item whose Hide button was clicked to close a range — opens the Show/Hide/Cancel prompt.
    var rangeEnd by remember { mutableStateOf<CustomizeItemRow?>(null) }
    // The item the "Move to…" dialog is moving (issue #87); creatingCategory swaps the dialog for the
    // new-category name prompt.
    var movingItem by remember { mutableStateOf<CustomizeItemRow?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    val backFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    val renameItemsFocus = remember { FocusRequester() }
    val autoCleanupFocus = remember { FocusRequester() }
    val rowFocusers = remember { mutableMapOf<String, FocusRequester>() }
    // Focus the row that opened a dialog (rename / move) when it closes (a dialog close can land
    // focus on the screen's first focusable otherwise).
    var dialogReturn by tv.own.owntv.ui.components.rememberDialogFocusRestore(
        anyDialogOpen = showFilterPicker || renaming != null || rangeEnd != null || movingItem != null || creatingCategory,
    )
    // Focus the first row once the screen opens (rows arrive via paging, so wait for them).
    var firstLanding by remember { mutableStateOf(true) }

    BackHandler { if (rangeAnchorKey != null) vm.cancelRange() else onBack() }

    LaunchedEffect(items.itemCount) {
        if (firstLanding && items.itemCount > 0) {
            firstLanding = false
            kotlinx.coroutines.delay(60)
            items[0]?.let { rowFocusers[it.key] }?.let { runCatching { it.requestFocus() } }
        } else if (firstLanding && items.itemCount == 0) {
            // Empty custom categories are valid. Their screen still needs a deterministic focus
            // owner while Paging is empty (and the first row takes over later if data arrives).
            kotlinx.coroutines.delay(60)
            runCatching { backFocus.requestFocus() }
        }
    }

    CompositionLocalProvider(LocalActionSurface provides GlassSurface.CARDS) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        // Header: category name + back
        Row(verticalAlignment = Alignment.CenterVertically) {
            OwnTVButton(
                stringResource(R.string.settings_customize_back),
                onClick = onBack,
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(backFocus),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                selectedCategory!!.displayName,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(16.dp))
            OwnTVButton(
                label = stringResource(
                    R.string.settings_customize_filter_button,
                    stringResource(
                        when (visibilityFilter) {
                            CustomizeVisibilityFilter.ALL -> R.string.settings_customize_filter_all
                            CustomizeVisibilityFilter.VISIBLE -> R.string.settings_customize_filter_visible
                            CustomizeVisibilityFilter.HIDDEN -> R.string.settings_customize_filter_hidden
                        },
                    ),
                ),
                onClick = { dialogReturn = filterFocus; showFilterPicker = true },
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(filterFocus),
            )
            }
        Spacer(Modifier.height(4.dp))
        Text(
            when (section) {
                MediaType.LIVE -> stringResource(R.string.settings_customize_channels_description)
                MediaType.MOVIE -> stringResource(R.string.settings_customize_movies_description)
                else -> stringResource(R.string.settings_customize_series_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        // Bulk rename pills — Movies/Series only: rename the WHOLE category, optionally with the
        // ✨ Auto cleanup preset applied immediately (issue #86). Live renames go per-row or via span.
        if (!isLive) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OwnTVButton(
                    stringResource(R.string.settings_customize_rename_items),
                    onClick = { dialogReturn = renameItemsFocus; vm.bulkRenameAll(autocleanup = false) },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(renameItemsFocus),
                )
                OwnTVButton(
                    stringResource(R.string.settings_bulk_rename_auto_cleanup),
                    onClick = { dialogReturn = autoCleanupFocus; vm.bulkRenameAll(autocleanup = true) },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(autoCleanupFocus),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(4.dp))

        if (rangeAnchorKey != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        rangeMode == SpanSelector.Mode.HIDE ->
                            stringResource(R.string.settings_customize_range_hide_start)
                        rangeMode == SpanSelector.Mode.RENAME ->
                            stringResource(R.string.settings_customize_range_rename_start)
                        rangeEndKey == null ->
                            stringResource(R.string.settings_customize_range_move_start)
                        else ->
                            pluralStringResource(
                                R.plurals.settings_customize_move_items_selected,
                                rangeSelectedKeys.size,
                                rangeSelectedKeys.size,
                            )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                OwnTVButton(stringResource(R.string.common_cancel), onClick = { vm.cancelRange() }, style = OwnTVButtonStyle.SECONDARY)
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .trapVerticalFocusExit()
                .onFocusChanged { listPaneFocused = it.hasFocus }
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { listPaneFocused },
                    lastIndex = { items.itemCount - 1 },
                    currentTargetIndex = { focusedItemIndex },
                    onJumpToIndex = { idx ->
                        scope.jumpLazyListTo(listState, idx) {
                            // Land focus on the target row's name (the first focusable in the row).
                            items[idx]?.let { rowFocusers[it.key] }?.let { runCatching { it.requestFocus() } }
                        }
                    },
                ),
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { it.key },
                contentType = items.itemContentType(),
            ) { index ->
                val row = items[index] ?: return@items
                val inMoveRange = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.MOVE
                val inRenameRange = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.RENAME
                val isInSpan = row.key in rangeSelectedKeys || renaming?.key == row.key
                ItemRow(
                    row = row,
                    isLive = isLive,
                    inRangeMode = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.HIDE,
                    inRenameRange = inRenameRange,
                    isInSpan = isInSpan,
                    focusRequester = remember(row.key) { rowFocusers.getOrPut(row.key) { FocusRequester() } },
                    upFocusRequester = backFocus.takeIf { index == 0 },
                    onRowFocused = { focusedItemIndex = index },
                    // While a move span is active every arrow acts on the whole block, not this row.
                    onMoveUp = { if (inMoveRange) vm.moveRange(row, MoveKind.UP) else vm.move(row, up = true) },
                    onMoveDown = { if (inMoveRange) vm.moveRange(row, MoveKind.DOWN) else vm.move(row, up = false) },
                    onMoveTop = { if (inMoveRange) vm.moveRange(row, MoveKind.TOP) else vm.moveToEdge(row, top = true) },
                    onMoveBottom = { if (inMoveRange) vm.moveRange(row, MoveKind.BOTTOM) else vm.moveToEdge(row, top = false) },
                    onMoveLongPress = { dialogReturn = rowFocusers[row.key]; vm.beginMoveRange(row) },
                    // Long-press Rename anchors a rename span; while one is active, pressing Rename on
                    // a second row opens the bulk rename flow over the whole span. On the anchor row
                    // itself it cancels, mirroring the Show/Hide span behavior.
                    onRename = { dialogReturn = rowFocusers[row.key]; renaming = row },
                    onRenameLongPress = { dialogReturn = rowFocusers[row.key]; vm.beginRenameRange(row) },
                    onPickRenameEnd = {
                        if (row.key == rangeAnchorKey) {
                            vm.cancelRange()
                        } else {
                            dialogReturn = rowFocusers[row.key]
                            // No active span (anchor vanished?) — fall back to the single rename.
                            if (vm.finishRenameRange(row) == null) renaming = row
                        }
                    },
                    onMove = { dialogReturn = rowFocusers[row.key]; movingItem = row },
                    onToggleHidden = { vm.setItemHidden(row, !row.hidden) },
                    onHideLongPress = { dialogReturn = rowFocusers[row.key]; vm.beginRange(row) },
                    onPickRangeEnd = {
                        if (row.key == rangeAnchorKey) vm.cancelRange()
                        else {
                            dialogReturn = rowFocusers[row.key]
                            rangeEnd = row
                        }
                    },
                )
            }
        }
    }

    if (showFilterPicker) {
        PickerDialog(
            title = stringResource(R.string.settings_customize_filter_title),
            options = listOf(
                CustomizeVisibilityFilter.ALL.name to stringResource(R.string.settings_customize_filter_all),
                CustomizeVisibilityFilter.VISIBLE.name to stringResource(R.string.settings_customize_filter_visible),
                CustomizeVisibilityFilter.HIDDEN.name to stringResource(R.string.settings_customize_filter_hidden),
            ),
            selected = visibilityFilter.name,
            onSelect = { value ->
                CustomizeVisibilityFilter.entries.firstOrNull { it.name == value }
                    ?.let(vm::setVisibilityFilter)
                scope.launch { listState.scrollToItem(0) }
                showFilterPicker = false
            },
            onDismiss = { showFilterPicker = false },
        )
    }

    renaming?.let { row ->
        TextInputDialog(
            title = stringResource(R.string.content_rename_channel),
            initial = row.displayName,
            hint = stringResource(R.string.settings_customize_rename_item_hint, row.originalName),
            onConfirm = { vm.renameItem(row, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    rangeEnd?.let { row ->
        val count = vm.keysInRange(row)?.size ?: 0
        ItemsRangeHideDialog(
            count = count,
            onHide = { vm.applyRange(row, hidden = true); rangeEnd = null },
            onShow = { vm.applyRange(row, hidden = false); rangeEnd = null },
            onDismiss = { vm.cancelRange(); rangeEnd = null },
        )
    }

    // Bulk rename (issue #86): choice popup, rule builder, review, restore-confirm, refusal.
    // Its popups restore D-pad focus to the row that opened the flow when the whole flow closes.
    BulkRenameFlow(vm.bulk, returnFocus = dialogReturn)

    // Move to… (issue #87): pick a combined category; "＋ New category…" swaps this dialog for the
    // name prompt, then the move dialog re-opens with the fresh category listed.
    val moveTargets by vm.moveTargets.collectAsStateWithLifecycle()
    if (creatingCategory) {
        TextInputDialog(
            title = stringResource(R.string.settings_customize_new_category_title),
            hint = stringResource(R.string.settings_customize_new_category_description),
            confirmLabel = stringResource(R.string.common_create),
            allowBlank = false,
            onConfirm = { vm.createCustomCategory(it); creatingCategory = false },
            onDismiss = { creatingCategory = false },
        )
    } else {
        movingItem?.let { row ->
            MoveToCategoryDialog(
                moveTargets = moveTargets,
                originName = selectedCategory?.displayName ?: stringResource(R.string.settings_customize_this_category),
                onNewCategory = { creatingCategory = true },
                onMove = { targetId, keepInOrigin ->
                    vm.moveTo(row, targetId, keepInOrigin)
                    movingItem = null
                },
                onDismiss = { movingItem = null },
            )
        }
    }
    } // CompositionLocalProvider
}

/** Confirms a range select over ITEMS: hide or show every item in the chosen span (or cancel). */
@Composable
private fun ItemsRangeHideDialog(count: Int, onHide: () -> Unit, onShow: () -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val hideFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { hideFocus.requestFocus() } }
    BackHandler { onDismiss() }
    PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
        ) {
            Text(stringResource(R.string.settings_customize_hide_show_items), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                pluralStringResource(R.plurals.settings_customize_selected_items, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.common_show), onClick = onShow, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton(stringResource(R.string.common_hide), onClick = onHide, modifier = Modifier.focusRequester(hideFocus))
            }
        }
    }
    }
}

@Composable
private fun ItemRow(
    row: CustomizeItemRow,
    isLive: Boolean,
    inRangeMode: Boolean,
    inRenameRange: Boolean,
    isInSpan: Boolean,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester?,
    onRowFocused: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onMoveLongPress: () -> Unit,
    onRename: () -> Unit,
    onRenameLongPress: () -> Unit,
    onPickRenameEnd: () -> Unit,
    // "Move to…" (issue #87): send this item into a user's combined category.
    onMove: () -> Unit,
    onToggleHidden: () -> Unit,
    onHideLongPress: () -> Unit,
    onPickRangeEnd: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // Highlight active span with a translucent tint so button focus remains clear.
            .background(if (isInSpan) colors.primaryContainer.copy(alpha = 0.35f) else colors.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // CH+- paging: a focusGroup with a FocusRequester so a jump lands focus on this row's
            // first focusable (the name); report up whenever any of the row's buttons gains focus.
            .focusGroup()
            .onFocusChanged { if (it.hasFocus) onRowFocused() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Name — focusable, no click action in Phase 1 (reserved).
        FocusableSurface(
            onClick = { },
            selected = isInSpan,
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester }
                    else Modifier,
                ),
            shape = RoundedCornerShape(12.dp),
            surface = GlassSurface.CARDS,
            contentAlignment = Alignment.CenterStart,
        ) { focused ->
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    row.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        row.hidden -> colors.onSurfaceVariant
                        focused -> colors.primary
                        else -> colors.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.hidden || row.renamed) {
                    Text(
                        listOfNotNull(
                            row.hidden.takeIf { it }?.let { stringResource(R.string.settings_customize_hidden) },
                            row.renamed.takeIf { it }?.let {
                                stringResource(R.string.settings_customize_item_was, row.originalName)
                            },
                        ).joinToString(stringResource(R.string.settings_customize_metadata_separator)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        // Long-pressing any arrow anchors a move span; pressing an arrow on a second row picks the
        // span end and moves the whole block, and keeps it selected for further steps.
        OwnTVButton("⤒", onClick = onMoveTop, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY, selected = isInSpan)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("↑", onClick = onMoveUp, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY, selected = isInSpan)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("↓", onClick = onMoveDown, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY, selected = isInSpan)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("⤓", onClick = onMoveBottom, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY, selected = isInSpan)
        // Live TV channels get a per-row Rename button; Movies/Series get bulk rename only (Phase 2).
        if (isLive) {
            Spacer(Modifier.width(6.dp))
            // Long-press anchors a rename span; a normal press picks the span end while one is
            // active, otherwise it opens the single-row rename dialog.
            OwnTVButton(
                stringResource(R.string.settings_customize_rename),
                onClick = { if (inRenameRange) onPickRenameEnd() else onRename() },
                onLongClick = onRenameLongPress,
                style = OwnTVButtonStyle.SECONDARY,
                selected = isInSpan,
            )
        }
        Spacer(Modifier.width(6.dp))
        // Move to… a user's combined category (issue #87). Always available — Live and non-Live rows
        // alike can join a custom category.
        OwnTVButton(stringResource(R.string.settings_customize_move_to), onClick = onMove, style = OwnTVButtonStyle.SECONDARY, selected = isInSpan)
        Spacer(Modifier.width(6.dp))
        OwnTVButton(
            label = stringResource(if (row.hidden) R.string.common_show else R.string.common_hide),
            // Long-press anchors a range; a normal press picks the span end while a range is active,
            // otherwise it toggles just this item.
            onClick = { if (inRangeMode) onPickRangeEnd() else onToggleHidden() },
            onLongClick = onHideLongPress,
            style = OwnTVButtonStyle.SECONDARY,
            selected = isInSpan,
        )
    }
}
