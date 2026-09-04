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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.util.Pin
import tv.own.owntv.features.profiles.PinDialog
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.jumpLazyListTo
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.LocalActionSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.SpanSelector

/**
 * Settings → Customize Categories & Items: hide / rename / reorder categories per section, and unhide
 * hidden channels, movies and series. Everything is per-profile and survives source re-syncs.
 * Optionally locked behind a PIN (set from this screen's top-right) so hidden items can't be
 * unhidden by someone else — the PIN is asked on every entry.
 */
@Composable
fun CustomizeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: CustomizeViewModel = koinViewModel()
    val section by vm.section.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val hiddenChannels by vm.hiddenChannels.collectAsStateWithLifecycle()
    val hideNewCategories by vm.hideNewCategories.collectAsStateWithLifecycle()
    val currentSort by vm.currentSort.collectAsStateWithLifecycle()
    val visibilityFilter by vm.visibilityFilter.collectAsStateWithLifecycle()
    val rangeAnchorKey by vm.rangeAnchorKey.collectAsStateWithLifecycle()
    val rangeMode by vm.rangeMode.collectAsStateWithLifecycle()
    val rangeEndKey by vm.rangeEndKey.collectAsStateWithLifecycle()
    val rangeSelectedKeys by vm.rangeSelectedKeys.collectAsStateWithLifecycle()
    val pinLock by vm.pinLock.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    var renaming by remember { mutableStateOf<CustomizeCatRow?>(null) }
    var showNewCatPicker by remember { mutableStateOf(false) }
    var showSortPicker by remember { mutableStateOf(false) }
    var showFilterPicker by remember { mutableStateOf(false) }
    // The category whose Hide button was clicked to close a range — opens the Show/Hide/Cancel prompt.
    var rangeEnd by remember { mutableStateOf<CustomizeCatRow?>(null) }
    // ＋ New category (issue #87): name prompt, then the empty combined category appears in the list.
    var creatingCategory by remember { mutableStateOf(false) }
    // Custom category pending deletion — confirmed in a scrim before anything is removed (plan §3.5).
    var deletingCategory by remember { mutableStateOf<CustomizeCatRow?>(null) }
    // PIN gate: asked on every entry (state is per-composition, so leaving the screen re-locks it).
    var unlocked by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    // Set/Change/Remove PIN flow (only reachable once unlocked).
    var editingPin by remember { mutableStateOf<PinEdit?>(null) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPinStage by remember { mutableStateOf(false) }
    var pinMismatch by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    val newCategoriesFocus = remember { FocusRequester() }
    val newCatPillFocus = remember { FocusRequester() } // "＋ New category" pill — restore target after its name prompt
    val pinFocus = remember { FocusRequester() }
    val removePinFocus = remember { FocusRequester() }
    var itemsReturnKey by remember { mutableStateOf<String?>(null) }
    // Opener row for whichever dialog (new-category picker, rename) is open — restored on close so
    // focus doesn't always jump back to the Live TV section chip.
    var dialogReturn by tv.own.owntv.ui.components.rememberDialogFocusRestore(
        anyDialogOpen = showNewCatPicker || showSortPicker || showFilterPicker || renaming != null || creatingCategory ||
            deletingCategory != null || rangeEnd != null || editingPin != null,
    )

    // CH+- key paging for the category list (same as Live/Movies/Series browse). The modifier consumes
    // the CH keys and moves focus itself, so it can never leak focus out of the list.
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listPaneFocused by remember { mutableStateOf(false) }

    LaunchedEffect(section) {
        listState.scrollToItem(0)
    }
    // Index (within `rows`) of the category row that currently holds focus — the paging anchor.
    var focusedCatIndex by remember { mutableIntStateOf(0) }
    // One FocusRequester per category row, so a jump can land focus on the target row.
    val rowFocusers = remember(rows) { rows.map { FocusRequester() } }
    // LazyColumn items before the category rows (hidden-items header + hidden rows + "Categories"
    // header) — the offset that maps a category index to its LazyColumn item index.
    val headerOffset = if (hiddenChannels.isNotEmpty()) hiddenChannels.size + 2 else 0

    // Wait for the stored lock state before showing anything (no unlocked flash).
    if (!pinLock.loaded) {
        Column(modifier.fillMaxSize().roundedPanel()) {}
        return
    }
    if (pinLock.pin != null && !unlocked) {
        Column(
            modifier = modifier.fillMaxSize().roundedPanel().padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            Text(stringResource(R.string.settings_customize_title), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_customize_pin_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        PinDialog(
            title = stringResource(if (pinError) R.string.settings_customize_wrong_pin else R.string.settings_customize_enter_pin),
            onSubmit = { entered ->
                if (entered == pinLock.pin || Pin.verify(entered, pinLock.pin)) {
                    unlocked = true
                    pinError = false
                } else {
                    pinError = true
                }
            },
            onDismiss = onBack,
            compact = true,
        )
        return
    }

    LaunchedEffect(Unit) {
        vm.setVisibilityFilter(CustomizeVisibilityFilter.ALL)
        kotlinx.coroutines.delay(60)
        runCatching { firstFocus.requestFocus() }
    }
    // Restore focus to the row that opened a dialog (new-category picker / rename / new-category
    // prompt / delete confirm) when it closes — previously closing either always landed on the Live
    // TV section chip (firstFocus).
    // The category list is disposed while its item screen is open. Back recreates the row, so
    // explicitly return to the same category name instead of letting focus escape to Settings.
    LaunchedEffect(selectedCategory, rows) {
        if (selectedCategory == null) {
            val key = itemsReturnKey ?: return@LaunchedEffect
            val index = rows.indexOfFirst { it.key == key }
            if (index >= 0) {
                listState.scrollToItem(headerOffset + index)
                kotlinx.coroutines.delay(80)
                runCatching { rowFocusers.getOrNull(index)?.requestFocus() }
            } else {
                runCatching { firstFocus.requestFocus() }
            }
            itemsReturnKey = null
        }
    }

    // While a span selection is in progress, Back cancels the selection instead of leaving the screen.
    BackHandler { if (rangeAnchorKey != null) vm.cancelRange() else if (selectedCategory != null) vm.closeItems() else onBack() }

    // Items screen — shown when the user presses OK on a category name. The items screen covers the
    // full panel including the dialogs, so when it's up, render nothing else.
    if (selectedCategory != null) {
        CustomizeItemsScreen(onBack = { vm.closeItems() })
    } else {
    // Action pills on this panel frost with CARDS (the surface the panel rows use), not the DIALOGS
    // default. Covers the chip/move/unhide buttons; trailing Popups don't inherit this anyway.
    CompositionLocalProvider(LocalActionSurface provides GlassSurface.CARDS) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            // Spatial D-pad entry from the sidebar would land mid-list — route it to the first chip.
            // onEnter fires only for directional entry from outside (internal moves don't re-trigger it).
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text(
            stringResource(R.string.settings_customize_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_customize_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // One compact strip, matching the agreed mockup: section tabs left, actions right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionChip(stringResource(R.string.settings_live_tv), section == MediaType.LIVE, Modifier.focusRequester(firstFocus)) { vm.selectSection(MediaType.LIVE) }
            Spacer(Modifier.width(10.dp))
            SectionChip(stringResource(R.string.settings_movies), section == MediaType.MOVIE) { vm.selectSection(MediaType.MOVIE) }
            Spacer(Modifier.width(10.dp))
            SectionChip(stringResource(R.string.settings_series), section == MediaType.SERIES) { vm.selectSection(MediaType.SERIES) }
            Spacer(Modifier.weight(1f))
            // Sort pill — reuses the same per-section sort mode that Browse uses.
            OwnTVButton(
                label = stringResource(
                    R.string.settings_customize_sort_button,
                    stringResource(if (currentSort == SettingsRepository.SortMode.PLAYLIST) R.string.content_provider else R.string.settings_sort_alpha),
                ),
                onClick = { dialogReturn = sortFocus; showSortPicker = true },
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(sortFocus),
            )
            Spacer(Modifier.width(10.dp))
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
            Spacer(Modifier.width(10.dp))
            // New categories pill — same setting as the old Row2, now compact.
            OwnTVButton(
                label = stringResource(
                    R.string.settings_customize_new_categories_button,
                    stringResource(if (hideNewCategories) R.string.settings_customize_behavior_hide else R.string.settings_customize_behavior_show),
                ),
                onClick = { dialogReturn = newCategoriesFocus; showNewCatPicker = true },
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(newCategoriesFocus),
            )
            Spacer(Modifier.width(10.dp))
            // ＋ New category (issue #87) — creates an empty combined category; items are moved into
            // it from the browse context menus or this screen's items view.
            OwnTVButton(
                label = stringResource(R.string.settings_customize_new_category),
                onClick = { dialogReturn = newCatPillFocus; creatingCategory = true },
                style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(newCatPillFocus),
            )
            Spacer(Modifier.width(10.dp))
            // Optional PIN lock, restyled as compact pills instead of the old full-width block.
            if (pinLock.pin == null) {
                OwnTVButton(
                    stringResource(R.string.settings_customize_set_pin),
                    onClick = {
                        dialogReturn = pinFocus
                        firstPin = ""; confirmPinStage = false; pinMismatch = false; editingPin = PinEdit.SET
                    },
                    modifier = Modifier.focusRequester(pinFocus),
                )
            } else {
                OwnTVButton(
                    stringResource(R.string.settings_customize_change_pin),
                    onClick = {
                        dialogReturn = pinFocus
                        firstPin = ""; confirmPinStage = false; pinMismatch = false; editingPin = PinEdit.CHANGE
                    },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(pinFocus),
                )
                Spacer(Modifier.width(10.dp))
                OwnTVButton(
                    stringResource(R.string.settings_customize_remove_lock),
                    onClick = { dialogReturn = removePinFocus; editingPin = PinEdit.REMOVE },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(removePinFocus),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

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
                            pluralStringResource(R.plurals.settings_customize_range_selected, rangeSelectedKeys.size, rangeSelectedKeys.size)
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
                // Pin vertical focus inside the category list: a held Up/Down that outruns the lazy
                // composition would otherwise escape to the section chips / sidebar. Every other browse
                // list in the app (Movies/Series/Live/Epg/Downloads) uses this same trap.
                .trapVerticalFocusExit()
                .onFocusChanged { listPaneFocused = it.hasFocus }
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { listPaneFocused },
                    lastIndex = { rows.lastIndex },
                    currentTargetIndex = { focusedCatIndex },
                    onJumpToIndex = { idx ->
                        scope.jumpLazyListTo(listState, headerOffset + idx) {
                            rowFocusers.getOrNull(idx)?.let { runCatching { it.requestFocus() } }
                        }
                    },
                ),
        ) {
            // Hidden items of this section first (hidden via each section's long-press menu) — kept on
            // top so they're findable even when a provider has hundreds of categories below.
            if (hiddenChannels.isNotEmpty()) {
                item {
                    Text(
                        stringResource(
                            when (section) {
                                MediaType.LIVE -> R.string.settings_customize_hidden_channels
                                MediaType.MOVIE -> R.string.settings_customize_hidden_movies
                                else -> R.string.settings_customize_hidden_series
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_customize_unhide_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                itemsIndexed(
                    hiddenChannels.entries.sortedBy { it.value.lowercase() },
                    key = { _, entry -> "hid:${entry.key}" },
                ) { hiddenIndex, (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label.ifBlank { key },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        OwnTVButton(
                            stringResource(R.string.settings_customize_unhide),
                            onClick = { vm.unhideChannel(key) },
                            style = OwnTVButtonStyle.SECONDARY,
                            modifier = if (hiddenIndex == 0) {
                                Modifier.focusProperties { up = firstFocus }
                            } else Modifier,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.settings_customize_categories), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.settings_customize_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            itemsIndexed(rows, key = { _, r -> r.key }) { index, row ->
                val inMoveRange = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.MOVE
                val inRenameRange = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.RENAME
                CategoryRow(
                    row = row,
                    inRangeMode = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.HIDE,
                    inRenameRange = inRenameRange,
                    isInSpan = row.key in rangeSelectedKeys,
                    focusRequester = rowFocusers.getOrNull(index),
                    upFocusRequester = firstFocus.takeIf { index == 0 && hiddenChannels.isEmpty() },
                    onRowFocused = { focusedCatIndex = index },
                    // While a move span is active every arrow acts on the whole block, not this row.
                    onMoveUp = { if (inMoveRange) vm.moveRange(row, MoveKind.UP) else vm.move(row, up = true) },
                    onMoveDown = { if (inMoveRange) vm.moveRange(row, MoveKind.DOWN) else vm.move(row, up = false) },
                    onMoveTop = { if (inMoveRange) vm.moveRange(row, MoveKind.TOP) else vm.moveToEdge(row, top = true) },
                    onMoveBottom = { if (inMoveRange) vm.moveRange(row, MoveKind.BOTTOM) else vm.moveToEdge(row, top = false) },
                    onMoveLongPress = { vm.beginMoveRange(row) },
                    // Long-press Rename anchors a rename span; while one is active, pressing Rename on
                    // a second row opens the bulk rename flow over the whole span. On the anchor row
                    // itself it cancels, mirroring the Show/Hide span behavior.
                    // Restore focus to this row's first button when the rename dialog (or its delete
                    // confirm) closes — same restore target as the span-rename path below.
                    onRename = { dialogReturn = rowFocusers.getOrNull(index); renaming = row },
                    onRenameLongPress = { vm.beginRenameRange(row) },
                    onPickRenameEnd = {
                        if (row.key == rangeAnchorKey) {
                            vm.cancelRange()
                        } else {
                            dialogReturn = rowFocusers.getOrNull(index)
                            // No active span (anchor vanished?) — fall back to the single rename.
                            if (vm.finishRenameRange(row) == null) renaming = row
                        }
                    },
                    onToggleHidden = { vm.setCategoryHidden(row, !row.hidden) },
                    onHideLongPress = { vm.beginRange(row) },
                    onPickRangeEnd = {
                        if (row.key == rangeAnchorKey) vm.cancelRange()
                        else {
                            dialogReturn = rowFocusers.getOrNull(index)
                            rangeEnd = row
                        }
                    },
                    onOpenItems = { itemsReturnKey = row.key; vm.openItems(row) },
                )
            }
        }
    }

    if (showNewCatPicker) {
        PickerDialog(
            title = stringResource(R.string.settings_customize_new_category_behavior),
            options = listOf(
                "SHOW" to stringResource(R.string.settings_customize_behavior_show),
                "HIDE" to stringResource(R.string.settings_customize_behavior_hide),
            ),
            selected = if (hideNewCategories) "HIDE" else "SHOW",
            onSelect = { value -> vm.setHideNewCategories(value == "HIDE"); showNewCatPicker = false },
            onDismiss = { showNewCatPicker = false },
        )
    }

    if (showSortPicker) {
        PickerDialog(
            title = stringResource(R.string.settings_customize_sort_categories),
            options = listOf(
                "PLAYLIST" to stringResource(R.string.content_provider),
                "ALPHA" to stringResource(R.string.settings_sort_alpha),
            ),
            selected = currentSort.name,
            onSelect = { value ->
                val mode = runCatching { SettingsRepository.SortMode.valueOf(value) }.getOrNull()
                if (mode != null) vm.setSort(mode)
                showSortPicker = false
            },
            onDismiss = { showSortPicker = false },
        )
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

    // Custom category pending deletion (opened from the rename dialog's Delete) — confirmed first,
    // plan §3.5: "It must never touch content."
    deletingCategory?.let { row ->
        PinConfirmDialog(
            title = stringResource(R.string.settings_customize_delete_category, row.displayName),
            message = stringResource(R.string.settings_customize_delete_category_description),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                vm.deleteCustomCategory(row)
                deletingCategory = null
                renaming = null
            },
            onDismiss = { deletingCategory = null },
        )
    }

    renaming?.let { row ->
        val isCustom = row.categoryId == null
        TextInputDialog(
            title = stringResource(if (isCustom) R.string.settings_customize_rename_or_delete_category else R.string.settings_customize_rename_category),
            initial = row.displayName,
            hint = stringResource(R.string.settings_customize_rename_hint, row.originalName),
            onConfirm = { vm.renameCategory(row, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
            // Custom combined categories can be deleted from their own rename dialog (plan §3.5);
            // the confirm scrim above runs before anything is removed.
            onDelete = if (isCustom) { { deletingCategory = row; renaming = null } } else null,
        )
    }

    // ＋ New category (issue #87): name the empty combined category, then it appears in the list.
    if (creatingCategory) {
        TextInputDialog(
            title = stringResource(R.string.settings_customize_new_category_title),
            hint = stringResource(R.string.settings_customize_new_category_description),
            confirmLabel = stringResource(R.string.common_create),
            allowBlank = false,
            onConfirm = { vm.createCustomCategory(it); creatingCategory = false },
            onDismiss = { creatingCategory = false },
        )
    }

    rangeEnd?.let { row ->
        val count = vm.keysInRange(row)?.size ?: 0
        RangeHideDialog(
            count = count,
            onHide = { vm.applyRange(row, hidden = true); rangeEnd = null },
            onShow = { vm.applyRange(row, hidden = false); rangeEnd = null },
            onDismiss = { vm.cancelRange(); rangeEnd = null },
        )
    }

    // Bulk rename (issue #86): choice popup, rule builder, review, restore-confirm, refusal.
    // Its popups restore D-pad focus to the row that opened the flow when the whole flow closes.
    BulkRenameFlow(vm.bulk, returnFocus = dialogReturn)

    editingPin?.let { mode ->
        when (mode) {
            PinEdit.REMOVE -> PinConfirmDialog(
                title = stringResource(R.string.settings_customize_remove_pin_title),
                message = stringResource(R.string.settings_customize_remove_pin_message),
                confirmLabel = stringResource(R.string.settings_customize_remove),
                onConfirm = { vm.setPin(null); editingPin = null },
                onDismiss = { editingPin = null },
            )
            // SET and CHANGE are the same flow (enter a new PIN, then confirm). To reach here with a
            // PIN already set the user unlocked the screen, so neither verifies the old PIN.
            PinEdit.SET, PinEdit.CHANGE -> {
                if (confirmPinStage) {
                    key("confirm", pinMismatch) {
                        PinDialog(
                            title = stringResource(if (pinMismatch) R.string.settings_customize_pin_mismatch else R.string.settings_customize_confirm_pin),
                            onSubmit = { entered ->
                                if (entered == firstPin) {
                                    vm.setPin(entered); editingPin = null
                                } else {
                                    pinMismatch = true
                                }
                            },
                            onDismiss = { editingPin = null },
                            compact = true,
                        )
                    }
                } else {
                    key("first") {
                        PinDialog(
                            title = stringResource(R.string.settings_customize_new_pin),
                            onSubmit = { entered ->
                                firstPin = entered
                                confirmPinStage = true
                                pinMismatch = false
                            },
                            onDismiss = { editingPin = null },
                            compact = true,
                        )
                    }
                }
            }
        }
    }
    } // CompositionLocalProvider
    } // else (selectedCategory == null)
}

/**
 * Confirms a range select: hide or show every category in the chosen span (or cancel). [count] is
 * the number of categories the span covers, inclusive.
 */
@Composable
private fun RangeHideDialog(count: Int, onHide: () -> Unit, onShow: () -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val hideFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { hideFocus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
        ) {
            Text(stringResource(R.string.settings_customize_hide_show_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                pluralStringResource(R.plurals.settings_customize_selected_categories, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_customize_show), onClick = onShow, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton(stringResource(R.string.settings_customize_hide), onClick = onHide, modifier = Modifier.focusRequester(hideFocus))
            }
        }
    }
}

@Composable
private fun SectionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        selected = selected,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        selectedContainerColor = colors.primaryContainer,
        contentAlignment = Alignment.Center,
        surface = GlassSurface.CARDS,
    ) { focused ->
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = when {
                selected -> colors.onPrimaryContainer
                focused -> colors.primary
                else -> colors.onSurface
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CategoryRow(
    row: CustomizeCatRow,
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
    onToggleHidden: () -> Unit,
    onHideLongPress: () -> Unit,
    onPickRangeEnd: () -> Unit,
    onOpenItems: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // Tint every row in the span while a range is in progress, so the selected block is obvious.
            .background(if (isInSpan) colors.primaryContainer else colors.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // CH+- paging: a focusGroup with a FocusRequester so a jump lands focus on this row's first
            // button; report up whenever any of the row's buttons gains focus (the paging anchor).
            .focusGroup()
            .onFocusChanged { if (it.hasFocus) onRowFocused() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Name as a focusable button — OK opens the category's items; D-pad walks names one press per
        // row. Right steps into move arrows, Rename and Hide/Show.
        FocusableSurface(
            onClick = onOpenItems,
            modifier = Modifier
                .weight(1f)
                // Match the action pills' normal 12.dp + label height so the name focus target is
                // not visibly shorter than the move/Rename/Hide controls beside it.
                .heightIn(min = 42.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester }
                    else Modifier,
                ),
            // Use the same compact pill treatment as the action buttons to the right.
            shape = RoundedCornerShape(50),
            focusedScale = 1f,
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = colors.primaryContainer,
            surface = GlassSurface.CARDS,
            contentAlignment = Alignment.CenterStart,
        ) { focused ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    row.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        row.hidden -> colors.onSurfaceVariant
                        focused -> colors.onPrimaryContainer
                        else -> colors.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.hidden || row.renamed || row.providerName != null) {
                    Text(
                        listOfNotNull(
                            row.hidden.takeIf { it }?.let { stringResource(R.string.settings_customize_hidden) },
                            row.renamed.takeIf { it }?.let { stringResource(R.string.settings_customize_was, row.originalName) },
                            row.providerName,
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
        OwnTVButton("⤒", onClick = onMoveTop, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("↑", onClick = onMoveUp, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("↓", onClick = onMoveDown, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("⤓", onClick = onMoveBottom, onLongClick = onMoveLongPress, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        // Long-press anchors a rename span; a normal press picks the span end while one is active,
        // otherwise it opens the single-row rename dialog.
        OwnTVButton(
            stringResource(R.string.settings_customize_rename),
            onClick = { if (inRenameRange) onPickRenameEnd() else onRename() },
            onLongClick = onRenameLongPress,
            style = OwnTVButtonStyle.SECONDARY,
        )
        Spacer(Modifier.width(6.dp))
        OwnTVButton(
            label = stringResource(if (row.hidden) R.string.settings_customize_show else R.string.settings_customize_hide),
            // Long-press anchors a range; a normal press picks the span end while a range is active,
            // otherwise it toggles just this category.
            onClick = { if (inRangeMode) onPickRangeEnd() else onToggleHidden() },
            onLongClick = onHideLongPress,
            style = OwnTVButtonStyle.SECONDARY,
        )
    }
}

/** PIN lock editing flow opened from the Customize header. */
private enum class PinEdit { SET, CHANGE, REMOVE }

/**
 * Generic Yes/No confirmation scrim used to remove the Customize PIN lock. Mirrors [RangeHideDialog]'s
 * structure so D-pad focus and the back button behave the same way as the other scrim dialogs here.
 */
@Composable
private fun PinConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { confirmFocus.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.theme.PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.dialogPanel(width = 290.dp, corner = 16.dp, padding = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(confirmLabel, onClick = onConfirm, modifier = Modifier.focusRequester(confirmFocus))
            }
        }
    }
    }
}
