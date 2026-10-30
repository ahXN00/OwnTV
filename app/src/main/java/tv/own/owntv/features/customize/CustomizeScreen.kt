package tv.own.owntv.features.customize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.StageSettingsHeading
import tv.own.owntv.features.settings.StageSettingsNote
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.features.settings.settingHints
import tv.own.owntv.features.settings.StageAction
import tv.own.owntv.features.settings.StageActionColumn
import tv.own.owntv.features.settings.SpanHelpBlock
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.features.profiles.PinLockedScreen
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.jumpLazyListTo
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.trapVerticalFocusExit
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
    val settingsPinVm: tv.own.owntv.features.settings.lock.SettingsPinViewModel = koinViewModel()
    val askForSettingsPin = settingsPinVm.lock.collectAsStateWithLifecycle().value.askOnOpen
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<CustomizeCatRow?>(null) }
    var showNewCatPicker by remember { mutableStateOf(false) }
    var showSortPicker by remember { mutableStateOf(false) }
    var showFilterPicker by remember { mutableStateOf(false) }
    // "…" in the tool row: New categories and the PIN lock.
    var showMoreMenu by remember { mutableStateOf(false) }
    var restoringDefaults by remember { mutableStateOf(false) }
    val restorePlaylist by vm.restorePlaylistName.collectAsStateWithLifecycle()
    val moreFocus = remember { FocusRequester() }
    val actionsFocus = remember { FocusRequester() }
    // The category whose Hide button was clicked to close a range — opens the Show/Hide/Cancel prompt.
    var rangeEnd by remember { mutableStateOf<CustomizeCatRow?>(null) }
    // ＋ New category: name prompt, then the empty combined category appears in the list.
    var creatingCategory by remember { mutableStateOf(false) }
    // Custom category pending deletion — confirmed in a scrim before anything is removed.
    var deletingCategory by remember { mutableStateOf<CustomizeCatRow?>(null) }
    // PIN gate: asked on every entry (state is per-composition, so leaving the screen re-locks it).
    var unlocked by remember { mutableStateOf(false) }
    // Set/Change/Remove PIN flow (only reachable once unlocked).
    var editingPin by remember { mutableStateOf<PinEdit?>(null) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPinStage by remember { mutableStateOf(false) }
    var pinMismatch by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    val newCatPillFocus = remember { FocusRequester() } // "＋ New category" pill — restore target after its name prompt
    var itemsReturnKey by remember { mutableStateOf<String?>(null) }
    // Opener row for whichever dialog (new-category picker, rename) is open — restored on close so
    // focus doesn't always jump back to the Live TV section chip.
    var dialogReturn by tv.own.owntv.ui.components.rememberDialogFocusRestore(
        anyDialogOpen = showNewCatPicker || showSortPicker || showFilterPicker || showMoreMenu || restoringDefaults || renaming != null || creatingCategory ||
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
    val rowFocusers = remember { mutableMapOf<String, FocusRequester>() }
    // LazyColumn items before the category rows (hidden-items header + hidden rows + "Categories"
    // header) — the offset that maps a category index to its LazyColumn item index.
    val headerOffset = if (hiddenChannels.isNotEmpty()) hiddenChannels.size + 2 else 0

    // Wait for the stored lock state before showing anything (no unlocked flash).
    if (!pinLock.loaded) return
    val lockPin = pinLock.pin
    if (lockPin != null && !unlocked) {
        PinLockedScreen(
            title = stringResource(R.string.settings_customize_title),
            pin = lockPin,
            onUnlock = { unlocked = true },
            onDismiss = onBack,
            modifier = modifier,
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
                runCatching { rowFocusers[key]?.requestFocus() }
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
        CustomizeItemsScreen(onBack = { vm.closeItems() }, modifier = modifier)
    } else {
    // The band, the tool row above the list, categories as rows and the panel with
    // the focused category's actions and the span help.
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_content_metadata)),
        title = stringResource(R.string.settings_customize_title),
        count = "",
        onBack = onBack,
        modifier = modifier,
        handleBack = false,
        toolbar = {
            tv.own.owntv.ui.stage.StageSegmented(
                options = listOf(stringResource(R.string.settings_live_tv), stringResource(R.string.settings_movies), stringResource(R.string.settings_series)),
                selected = when (section) { MediaType.LIVE -> 0; MediaType.MOVIE -> 1; else -> 2 },
                onSelect = { vm.selectSection(listOf(MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES)[it]) },
                modifier = Modifier.focusRequester(firstFocus),
            )
            Spacer(Modifier.weight(1f))
            tv.own.owntv.ui.stage.StageTool(
                stringResource(R.string.settings_customize_sort_button, stringResource(if (currentSort == SettingsRepository.SortMode.PLAYLIST) R.string.content_provider else R.string.settings_sort_alpha)),
                onClick = { dialogReturn = sortFocus; showSortPicker = true },
                icon = OwnTVIcon.SORT, boxed = true, modifier = Modifier.focusRequester(sortFocus),
            )
            tv.own.owntv.ui.stage.StageTool(
                stringResource(
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
                icon = OwnTVIcon.EYE_OFF, boxed = true, modifier = Modifier.focusRequester(filterFocus),
            )
            // ＋ New category: an empty combined category; items are moved into it from the
            // browse context menus or a category's items.
            tv.own.owntv.ui.stage.StageTool(
                stringResource(R.string.settings_customize_new_category),
                onClick = { dialogReturn = newCatPillFocus; creatingCategory = true },
                boxed = true, modifier = Modifier.focusRequester(newCatPillFocus),
            )
            tv.own.owntv.ui.stage.StageTool(
                null, onClick = { dialogReturn = moreFocus; showMoreMenu = true },
                icon = OwnTVIcon.MORE, boxed = true, modifier = Modifier.focusRequester(moreFocus),
            )
        },
        list = { pos ->
            Column(pos) {
                if (rangeAnchorKey != null) {
                    StageSettingRow(
                        icon = OwnTVIcon.LAYERS,
                        title = when {
                            rangeMode == SpanSelector.Mode.HIDE -> stringResource(R.string.settings_customize_range_hide_start)
                            rangeMode == SpanSelector.Mode.RENAME -> stringResource(R.string.settings_customize_range_rename_start)
                            rangeEndKey == null -> stringResource(R.string.settings_customize_range_move_start)
                            else -> pluralStringResource(R.plurals.settings_customize_range_selected, rangeSelectedKeys.size, rangeSelectedKeys.size)
                        },
                        desc = null,
                        value = SettingValue.Action(stringResource(R.string.common_cancel)),
                        onClick = { vm.cancelRange() },
                        marked = true,
                    )
                    Spacer(Modifier.height(6.mpx))
                }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.mpx),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.mpx),
                    modifier = Modifier
                        .fillMaxSize()
                        // Pin vertical focus inside the category list: a held Up/Down that outruns the lazy
                        // composition would otherwise escape to the tool row / rail. Every browse list uses this trap.
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
                                    rows.getOrNull(idx)?.let { rowFocusers[it.key] }?.let { runCatching { it.requestFocus() } }
                                }
                            },
                        ),
                ) {
                    // Hidden items of this section first (hidden via each section's long-press menu) — kept on
                    // top so they're findable even when a provider has hundreds of categories below.
                    if (hiddenChannels.isNotEmpty()) {
                        item {
                            StageSettingsHeading(
                                stringResource(
                                    when (section) {
                                        MediaType.LIVE -> R.string.settings_customize_hidden_channels
                                        MediaType.MOVIE -> R.string.settings_customize_hidden_movies
                                        else -> R.string.settings_customize_hidden_series
                                    },
                                ),
                                hiddenChannels.size, first = true,
                            )
                        }
                        itemsIndexed(
                            hiddenChannels.entries.sortedBy { it.value.lowercase() },
                            key = { _, entry -> "hid:${entry.key}" },
                        ) { hiddenIndex, (key, label) ->
                            val unhide = stringResource(R.string.settings_customize_unhide)
                            StageSettingRow(
                                icon = OwnTVIcon.EYE_OFF,
                                title = label.ifBlank { key },
                                desc = null,
                                value = SettingValue.Action(unhide),
                                onClick = { vm.unhideChannel(key) },
                                help = SettingHelp(label.ifBlank { key }, stringResource(R.string.settings_customize_unhide_description), hints = settingHints(SettingValue.Action(unhide), pinnable = false)),
                                modifier = if (hiddenIndex == 0) Modifier.upTo(firstFocus) else Modifier,
                            )
                        }
                    }
                    item { StageSettingsHeading(stringResource(R.string.settings_customize_categories), rows.size, first = hiddenChannels.isEmpty()) }

                    if (rows.isEmpty()) {
                        item { StageSettingsNote(stringResource(R.string.settings_customize_empty), null) }
                    }
                    itemsIndexed(rows, key = { _, r -> r.key }) { index, row ->
                        val inMoveRange = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.MOVE
                        val inRenameRange = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.RENAME
                        val isInSpan = row.key in rangeSelectedKeys || renaming?.key == row.key || deletingCategory?.key == row.key
                        CategoryRow(
                            row = row,
                            inRangeMode = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.HIDE,
                            inRenameRange = inRenameRange,
                            isInSpan = isInSpan,
                            focusRequester = remember(row.key) { rowFocusers.getOrPut(row.key) { FocusRequester() } },
                            upFocusRequester = firstFocus.takeIf { index == 0 && hiddenChannels.isEmpty() },
                            actionsFocus = actionsFocus,
                            keepPanel = index == focusedCatIndex,
                            onRowFocused = { focusedCatIndex = index },
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
                            onToggleHidden = { vm.setCategoryHidden(row, !row.hidden) },
                            onHideLongPress = { dialogReturn = rowFocusers[row.key]; vm.beginRange(row) },
                            onPickRangeEnd = {
                                if (row.key == rangeAnchorKey) vm.cancelRange()
                                else {
                                    dialogReturn = rowFocusers[row.key]
                                    rangeEnd = row
                                }
                            },
                            onOpenItems = { itemsReturnKey = row.key; vm.openItems(row) },
                        )
                    }
                }
            }
        },
    )

    if (showMoreMenu) {
        PickerDialog(
            title = stringResource(R.string.common_nav_more),
            options = customizeMoreOptions(hideNewCategories, hasPin = pinLock.pin != null, askForSettingsPin = askForSettingsPin),
            selected = "",
            onSelect = { value ->
                showMoreMenu = false
                when (value) {
                    "BULK" -> { dialogReturn = moreFocus; vm.bulkRenameAll(autocleanup = false) }
                    "BULK_CLEANUP" -> { dialogReturn = moreFocus; vm.bulkRenameAll(autocleanup = true) }
                    "NEW" -> showNewCatPicker = true
                    "PIN_SET", "PIN_CHANGE" -> {
                        firstPin = ""; confirmPinStage = false; pinMismatch = false
                        editingPin = if (value == "PIN_SET") PinEdit.SET else PinEdit.CHANGE
                    }
                    "PIN_REMOVE" -> editingPin = PinEdit.REMOVE
                    "SETTINGS_PIN" -> settingsPinVm.setAskOnOpen(!askForSettingsPin)
                    CustomizeOption.RESTORE.name -> restoringDefaults = true
                }
            },
            onDismiss = { showMoreMenu = false },
        )
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

    // Custom category pending deletion (opened from the rename dialog's Delete) — confirmed first.
    // It must never touch content.
    if (restoringDefaults) {
        PinConfirmDialog(
            title = stringResource(
                R.string.settings_customize_restore_defaults_title,
                stringResource(
                    when (section) {
                        MediaType.LIVE -> R.string.settings_live_tv
                        MediaType.MOVIE -> R.string.settings_movies
                        else -> R.string.settings_series
                    },
                ),
                restorePlaylist ?: stringResource(R.string.content_all_playlists),
            ),
            message = stringResource(R.string.settings_customize_restore_defaults_description),
            confirmLabel = stringResource(R.string.common_reset),
            onConfirm = { vm.restoreSectionDefaults(); restoringDefaults = false },
            onDismiss = { restoringDefaults = false },
        )
    }

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
            // Custom combined categories can be deleted from their own rename dialog;
            // the confirm scrim above runs before anything is removed.
            onDelete = if (isCustom) { { deletingCategory = row; renaming = null } } else null,
        )
    }

    // ＋ New category: name the empty combined category, then it appears in the list.
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

    // Bulk rename: choice popup, rule builder, review, restore-confirm, refusal.
    // Its popups restore D-pad focus to the row that opened the flow when the whole flow closes.
    BulkRenameFlow(vm.bulk, returnFocus = dialogReturn)

    editingPin?.let { mode ->
        CustomizePinEditor(
            mode = mode,
            firstPin = firstPin,
            confirmStage = confirmPinStage,
            mismatch = pinMismatch,
            onSetPin = { vm.setPin(it); editingPin = null },
            onFirstPin = { entered ->
                firstPin = entered
                confirmPinStage = true
                pinMismatch = false
            },
            onMismatch = { pinMismatch = true },
            onDismiss = { editingPin = null },
        )
    }
    } // else (selectedCategory == null)
}

/**
 * Confirms a range select: hide or show every category in the chosen span (or cancel). [count] is
 * the number of categories the span covers, inclusive.
 */
@Composable
private fun RangeHideDialog(count: Int, onHide: () -> Unit, onShow: () -> Unit, onDismiss: () -> Unit) {
    val hideFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { hideFocus.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_customize_hide_show_title),
        body = pluralStringResource(R.plurals.settings_customize_selected_categories, count, count),
        width = 864.mpx,
        buttons = {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.common_cancel), onClick = onDismiss, primary = false)
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.settings_customize_show), onClick = onShow, primary = false)
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.settings_customize_hide), onClick = onHide, modifier = Modifier.focusRequester(hideFocus))
        },
    )
}

@Composable
private fun CategoryRow(
    row: CustomizeCatRow,
    inRangeMode: Boolean,
    inRenameRange: Boolean,
    isInSpan: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    actionsFocus: FocusRequester,
    keepPanel: Boolean,
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
    val meta = listOfNotNull(
        row.hidden.takeIf { it }?.let { stringResource(R.string.settings_customize_hidden) },
        row.renamed.takeIf { it }?.let { stringResource(R.string.settings_customize_was, row.originalName) },
        row.providerName,
    ).joinToString(stringResource(R.string.settings_customize_metadata_separator))
    // A held OK on a Move, Rename or Hide action anchors a span; a normal press on a second row picks its end.
    val actions = listOf(
        StageAction(OwnTVIcon.PAGE_TOWARD_FIRST, stringResource(R.string.settings_customize_move_top), onMoveTop, onMoveLongPress),
        StageAction(OwnTVIcon.CHEVRON_UP, stringResource(R.string.settings_row_menu_move_up), onMoveUp, onMoveLongPress),
        StageAction(OwnTVIcon.CHEVRON_DOWN, stringResource(R.string.settings_row_menu_move_down), onMoveDown, onMoveLongPress),
        StageAction(OwnTVIcon.PAGE_TOWARD_LAST, stringResource(R.string.settings_customize_move_bottom), onMoveBottom, onMoveLongPress),
        StageAction(OwnTVIcon.PENCIL, stringResource(R.string.settings_customize_rename), { if (inRenameRange) onPickRenameEnd() else onRename() }, onRenameLongPress),
        StageAction(
            OwnTVIcon.EYE_OFF,
            stringResource(if (row.hidden) R.string.settings_customize_show else R.string.settings_customize_hide),
            { if (inRangeMode) onPickRangeEnd() else onToggleHidden() },
            onHideLongPress,
        ),
    )
    StageSettingRow(
        icon = OwnTVIcon.FOLDER,
        title = row.displayName,
        desc = meta.ifBlank { null },
        value = if (row.hidden) SettingValue.Custom {
            androidx.tv.material3.Text(stringResource(R.string.settings_customize_hidden), style = tv.own.owntv.ui.theme.stageText(18, 700), color = tv.own.owntv.ui.theme.StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else SettingValue.Opens(null),
        onClick = onOpenItems,
        marked = isInSpan,
        keepPanel = keepPanel,
        help = SettingHelp(
            title = row.displayName,
            text = stringResource(R.string.settings_customize_description),
            hints = listOf(
                stringResource(R.string.common_ok) to stringResource(R.string.settings_key_open),
                "▶" to stringResource(R.string.settings_key_actions),
                stringResource(R.string.common_back) to stringResource(R.string.common_nav_settings),
            ),
            extra = { StageActionColumn(actions, focusRequester, actionsFocus) },
            footer = { SpanHelpBlock() },
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .then(if (upFocusRequester != null) Modifier.upTo(upFocusRequester) else Modifier)
            .focusProperties { right = actionsFocus }
            .onFocusChanged { if (it.isFocused) onRowFocused() },
    )
}

/**
 * Generic Yes/No confirmation scrim used to remove the Customize PIN lock. Mirrors [RangeHideDialog]'s
 * structure so D-pad focus and the back button behave the same way as the other scrim dialogs here.
 */
@Composable
internal fun PinConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { confirmFocus.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = title,
        body = message,
        width = 522.mpx,
        buttons = {
            tv.own.owntv.ui.stage.StagePopupButton(stringResource(R.string.common_cancel), onClick = onDismiss, primary = false)
            tv.own.owntv.ui.stage.StagePopupButton(confirmLabel, onClick = onConfirm, modifier = Modifier.focusRequester(confirmFocus))
        },
    )
}

/**
 * ▲ from the list's first row goes to the tool row above. A plain `focusProperties { up = … }` is not
 * enough: the list's [trapVerticalFocusExit] cancels every move that leaves it, that one included.
 */
internal fun Modifier.upTo(target: FocusRequester): Modifier = onPreviewKeyEvent { e ->
    if (e.key != androidx.compose.ui.input.key.Key.DirectionUp) return@onPreviewKeyEvent false
    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) runCatching { target.requestFocus() }
    true
}
