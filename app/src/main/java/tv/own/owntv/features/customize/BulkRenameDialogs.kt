package tv.own.owntv.features.customize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.customize.BULK_RENAME_MAX_ROWS
import tv.own.owntv.core.customize.BulkPreviewRow
import tv.own.owntv.core.customize.BulkRenameSession
import tv.own.owntv.core.customize.RenameRules
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.PopupFontTheme

/** Which field of a rule-builder row a nested picker is editing (focus returns to that row after). */
private enum class RuleField { TYPE, PLACEMENT, VALUE }


/**
 * Hosts whichever bulk-rename dialog the session's screen calls for, and restores D-pad focus to
 * [returnFocus] (the row/pill that opened the flow) once the whole flow closes.
 */
@Composable
fun BulkRenameFlow(session: BulkRenameSession, returnFocus: FocusRequester? = null) {
    val screen by session.screen.collectAsStateWithLifecycle()
    var wasOpen by remember { mutableStateOf(false) }
    // The opener row can change while the flow is idle (a second span started) — always restore to
    // the LATEST row, not the one from the composition that saw the flow open.
    val currentReturnFocus by rememberUpdatedState(returnFocus)
    LaunchedEffect(screen) {
        if (screen != BulkRenameSession.Screen.NONE) {
            wasOpen = true
        } else if (wasOpen) {
            wasOpen = false
            currentReturnFocus?.let { opener ->
                kotlinx.coroutines.delay(60)
                runCatching { opener.requestFocus() }
            }
        }
    }
    when (screen) {
        BulkRenameSession.Screen.CHOICE -> BulkRenameChoicePopup(session)
        BulkRenameSession.Screen.BUILDER -> BulkRuleBuilderDialog(session)
        BulkRenameSession.Screen.REVIEW -> BulkReviewDialog(session)
        BulkRenameSession.Screen.RESTORE_CONFIRM -> BulkRestoreConfirmDialog(session)
        BulkRenameSession.Screen.REFUSED -> BulkRefusedDialog(session)
        BulkRenameSession.Screen.NONE -> Unit
    }
}

/** [mockup frame 2] The four-way choice that opens a bulk rename. */
@Composable
private fun BulkRenameChoicePopup(session: BulkRenameSession) {
    val colors = OwnTVTheme.colors
    val count = session.entries.collectAsStateWithLifecycle().value.size
    val addFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { addFocus.requestFocus() } }
    BackHandler { session.close() }
    PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.settings_bulk_rename_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Text(
                pluralStringResource(R.plurals.settings_bulk_rename_selected, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OwnTVButton(stringResource(R.string.settings_bulk_rename_add_rule), onClick = { session.openBuilder() }, modifier = Modifier.fillMaxWidth().focusRequester(addFocus))
            OwnTVButton(stringResource(R.string.settings_bulk_rename_auto_cleanup), onClick = { session.autoCleanup() }, modifier = Modifier.fillMaxWidth())
            OwnTVButton(stringResource(R.string.settings_bulk_rename_restore_original), onClick = { session.requestRestore() }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            OwnTVButton(stringResource(R.string.common_cancel), onClick = { session.close() }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
        }
    }
    }
}

/** [mockup frame 3] Rule builder: rows of type/placement/value/✕, options checkboxes, Apply. */
@Composable
private fun BulkRuleBuilderDialog(session: BulkRenameSession) {
    val colors = OwnTVTheme.colors
    val rules = session.rules.collectAsStateWithLifecycle().value
    val options = session.options.collectAsStateWithLifecycle().value
    // Draft rows edited locally until Apply commits them to the session (and starts the preview).
    var draft by remember { mutableStateOf(rules) }
    var trim by remember { mutableStateOf(options.trimLeftovers) }
    var ignoreCase by remember { mutableStateOf(options.ignoreCase) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    // Nested picker target: (row index, field). The row's focus is restored when it closes.
    var editing by remember { mutableStateOf<Pair<Int, RuleField>?>(null) }
    var pickerRow by remember { mutableIntStateOf(-1) }
    var pendingRowFocus by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val addRuleFocus = remember { FocusRequester() }
    val rowFocusers = remember(draft.size) { List(draft.size) { FocusRequester() } }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { (rowFocusers.firstOrNull() ?: addRuleFocus).requestFocus() }
    }
    // Adding/deleting a rule disposes and rebuilds row focus nodes. Hand focus to the new row or the
    // Add button explicitly instead of relying on spatial fallback through a full-screen scrim.
    LaunchedEffect(draft.size, pendingRowFocus) {
        if (pendingRowFocus != Int.MIN_VALUE) {
            kotlinx.coroutines.delay(60)
            val target = rowFocusers.getOrNull(pendingRowFocus) ?: addRuleFocus
            runCatching { target.requestFocus() }
            pendingRowFocus = Int.MIN_VALUE
        }
    }
    // Focus the row that opened a nested picker once that picker closes (a scrim teardown can leave
    // focus in limbo otherwise).
    LaunchedEffect(editing) {
        if (editing == null && pickerRow >= 0) {
            val row = pickerRow
            pickerRow = -1
            kotlinx.coroutines.delay(60)
            runCatching { rowFocusers.getOrNull(row)?.requestFocus() }
        } else if (editing != null) {
            pickerRow = editing!!.first
        }
    }
    BackHandler { session.backToChoice() }

    fun submit() {
        val out = mutableListOf<RenameRules.Rule>()
        for (rule in draft) {
            if (rule.pattern != null) {
                // Auto-cleanup rules are internal regexes, but remain visible/removable in this
                // editor so "Edit rules" genuinely tunes the generated preset (plan §2.6).
                out += rule
            } else if (rule.action == RenameRules.Action.ADD) {
                if (RenameRules.tokensOf(rule).size != 1) {
                    errorRes = R.string.settings_bulk_rename_add_single_value_error
                    return
                }
                out += rule
            } else if (RenameRules.tokensOf(rule).isNotEmpty()) {
                out += rule
            }
        }
        if (out.isEmpty()) {
            errorRes = R.string.settings_bulk_rename_rule_required
            return
        }
        errorRes = null
        session.submitRules(out, RenameRules.Options(trimLeftovers = trim, ignoreCase = ignoreCase))
    }

    PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 760.dp, padding = 24.dp)) {
            Text(stringResource(R.string.settings_bulk_rename_rules_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_bulk_rename_rules_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                draft.forEachIndexed { i, rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup()
                            .then(if (i < rowFocusers.size) Modifier.focusRequester(rowFocusers[i]) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OwnTVButton(
                            label = stringResource(
                                if (rule.action == RenameRules.Action.ADD) R.string.settings_bulk_rename_action_add
                                else R.string.settings_bulk_rename_action_remove,
                            ) + " ▾",
                            onClick = { editing = i to RuleField.TYPE },
                            style = OwnTVButtonStyle.SECONDARY,
                            modifier = Modifier.width(120.dp),
                        )
                        OwnTVButton(
                            label = stringResource(
                                if (rule.placement == RenameRules.Placement.PREFIX) R.string.settings_bulk_rename_before
                                else R.string.settings_bulk_rename_after,
                            ) + " ▾",
                            onClick = { editing = i to RuleField.PLACEMENT },
                            style = OwnTVButtonStyle.SECONDARY,
                            modifier = Modifier.width(120.dp),
                        )
                        OwnTVButton(
                            label = rule.autoLabel?.let { label ->
                                stringResource(
                                    when (label) {
                                        RenameRules.AutoLabel.COUNTRY_PROVIDER -> R.string.settings_bulk_rename_auto_country_provider
                                        RenameRules.AutoLabel.QUALITY_CODEC -> R.string.settings_bulk_rename_auto_quality_codec
                                        RenameRules.AutoLabel.EMOJI_SYMBOLS -> R.string.settings_bulk_rename_auto_emoji_symbols
                                    },
                                )
                            } ?: rule.value.ifBlank { stringResource(R.string.settings_bulk_rename_value_example) },
                            onClick = { editing = i to RuleField.VALUE },
                            style = OwnTVButtonStyle.SECONDARY,
                            modifier = Modifier.weight(1f),
                        )
                        OwnTVButton(
                            "✕",
                            onClick = {
                                val next = draft.toMutableList().apply { removeAt(i) }
                                draft = next
                                pendingRowFocus = if (next.isEmpty()) -1 else i.coerceAtMost(next.lastIndex)
                            },
                            style = OwnTVButtonStyle.SECONDARY,
                        )
                    }
                }
                OwnTVButton(
                    stringResource(R.string.settings_bulk_rename_add_another_rule),
                    onClick = {
                        pendingRowFocus = draft.size
                        draft = draft + RenameRules.Rule(RenameRules.Action.ADD, RenameRules.Placement.PREFIX, "")
                    },
                    style = OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth().focusRequester(addRuleFocus),
                )
            }
            Spacer(Modifier.height(12.dp))

            // The two options as toggle chips (Trim leftover spaces / Ignore case, both default ON).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OwnTVButton(
                    label = stringResource(
                        if (trim) R.string.settings_bulk_rename_trim_spaces_selected
                        else R.string.settings_bulk_rename_trim_spaces,
                    ),
                    onClick = { trim = !trim },
                    style = if (trim) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                )
                OwnTVButton(
                    label = stringResource(
                        if (ignoreCase) R.string.settings_bulk_rename_ignore_case_selected
                        else R.string.settings_bulk_rename_ignore_case,
                    ),
                    onClick = { ignoreCase = !ignoreCase },
                    style = if (ignoreCase) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                )
            }

            errorRes?.let {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.favorite)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = { session.backToChoice() }, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_bulk_rename_apply), onClick = { submit() })
            }
        }
    }

    when (editing?.second) {
        RuleField.TYPE -> PickerDialog(
            title = stringResource(R.string.settings_bulk_rename_rule_type),
            options = listOf(
                "ADD" to stringResource(R.string.settings_bulk_rename_action_add),
                "REMOVE" to stringResource(R.string.settings_bulk_rename_action_remove),
            ),
            selected = draft[editing!!.first].action.name,
            onSelect = { value ->
                runCatching { RenameRules.Action.valueOf(value) }.getOrNull()?.let { action ->
                    val i = editing!!.first
                    draft = draft.toMutableList().apply {
                        set(i, get(i).copy(action = action, pattern = null, autoLabel = null))
                    }
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        RuleField.PLACEMENT -> PickerDialog(
            title = stringResource(R.string.settings_bulk_rename_where),
            options = listOf(
                "PREFIX" to stringResource(R.string.settings_bulk_rename_before),
                "SUFFIX" to stringResource(R.string.settings_bulk_rename_after),
            ),
            selected = draft[editing!!.first].placement.name,
            onSelect = { value ->
                runCatching { RenameRules.Placement.valueOf(value) }.getOrNull()?.let { placement ->
                    val i = editing!!.first
                    draft = draft.toMutableList().apply {
                        set(i, get(i).copy(placement = placement, pattern = null, autoLabel = null))
                    }
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        RuleField.VALUE -> TextInputDialog(
            title = stringResource(R.string.settings_bulk_rename_rule_value),
            initial = draft[editing!!.first].value,
            hint = stringResource(R.string.settings_bulk_rename_value_hint),
            onConfirm = { value ->
                val i = editing!!.first
                draft = draft.toMutableList().apply {
                    set(i, get(i).copy(value = value, pattern = null, autoLabel = null))
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
    }
}

/** [mockup frame 4] Review modeled on EpgMatchReviewDialog: left rows, right centred bulk column. */
@Composable
private fun BulkReviewDialog(session: BulkRenameSession) {
    val colors = OwnTVTheme.colors
    val rows = session.preview.collectAsStateWithLifecycle().value
    BackHandler { session.done() }
    // Focus lands on the first APPLICABLE row (an unchanged first row has no buttons); if nothing
    // can be applied, it lands on "Apply all" instead. Mirrors EpgMatchReviewDialog's first-row focus.
    val firstChanged = rows.indexOfFirst { !it.unchanged }
    val firstApplyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstApplyFocus.requestFocus() } }

    // Popup(focusable=true) creates a hard focus boundary — applying/declining removes rows from the
    // LazyColumn, but focus stays inside instead of escaping to the screen behind (same pattern and
    // reason as EpgMatchReviewDialog).
    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = { session.done() }) {
        // Dense TV review: one-third smaller than the previous 0.75 scale.
        PopupFontTheme(fontScale = 0.50f) {
    Box(
        Modifier.fillMaxSize().modalScrim(),
        contentAlignment = Alignment.Center,
    ) {
        // scroll = false: this column holds a LazyColumn, which manages its own scrolling.
                Column(Modifier.dialogPanel(width = 680.dp, corner = 16.dp, padding = 12.dp, scroll = false)) {
            Text(stringResource(R.string.settings_bulk_rename_review), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.settings_bulk_rename_review_description),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(7.dp))
            val listHeight = (LocalConfiguration.current.screenHeightDp.dp - 220.dp).coerceIn(160.dp, 320.dp)
            Row(Modifier.fillMaxWidth()) {
                // Left: the pending rows. Rows are removed on Apply/Decline; keys keep the list stable.
                LazyColumn(
                    Modifier.weight(1f).height(listHeight),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    itemsIndexed(rows, key = { _, r -> r.key }) { index, r ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(colors.surface).padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.oldName, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (r.unchanged) {
                                    Text(
                                        if (r.blankRejected) stringResource(R.string.settings_bulk_rename_blank_rejected)
                                        else stringResource(R.string.settings_bulk_rename_unchanged),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.settings_bulk_rename_result, r.newName),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (r.duplicate) colors.favorite else colors.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (r.duplicate) {
                                        Text(
                                            stringResource(R.string.settings_bulk_rename_duplicate),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.favorite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (!r.unchanged) {
                                FocusableSurface(
                                    onClick = { session.applyRows(setOf(r.key)) },
                                    modifier = if (index == firstChanged) Modifier.focusRequester(firstApplyFocus) else Modifier,
                                    shape = RoundedCornerShape(8.dp),
                                    unfocusedContainerColor = colors.primaryContainer,
                                    contentAlignment = Alignment.Center,
                                    surface = GlassSurface.DIALOGS,
                                ) { _ -> Text(stringResource(R.string.settings_bulk_rename_apply), style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
                                FocusableSurface(
                                    onClick = { session.declineRows(setOf(r.key)) },
                                    shape = RoundedCornerShape(8.dp),
                                    unfocusedContainerColor = colors.surfaceContainerHigh,
                                    contentAlignment = Alignment.Center,
                                    surface = GlassSurface.DIALOGS,
                                ) { _ -> Text(stringResource(R.string.settings_bulk_rename_decline), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Right: bulk actions, VERTICALLY CENTRED so D-pad right from any middle row lands
                // here without scrolling (owner requirement for this dialog).
                Column(
                    Modifier.width(132.dp).height(listHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            pluralStringResource(
                                R.plurals.settings_bulk_rename_will_change,
                                rows.count { !it.unchanged },
                                rows.count { !it.unchanged },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pluralStringResource(
                                R.plurals.settings_bulk_rename_unchanged_count,
                                rows.count { it.unchanged },
                                rows.count { it.unchanged },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pluralStringResource(
                                R.plurals.settings_bulk_rename_duplicates_count,
                                rows.count { it.duplicate },
                                rows.count { it.duplicate },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.favorite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OwnTVButton(
                        stringResource(R.string.settings_bulk_rename_apply_all), onClick = { session.applyAll() }, icon = OwnTVIcon.PLAY,
                        modifier = Modifier.fillMaxWidth().then(if (firstChanged == -1) Modifier.focusRequester(firstApplyFocus) else Modifier),
                        compact = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    OwnTVButton(stringResource(R.string.settings_bulk_rename_decline_all), onClick = { session.declineAll() }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth(), compact = true)
                    Spacer(Modifier.height(6.dp))
                    OwnTVButton(stringResource(R.string.settings_bulk_rename_edit_rules), onClick = { session.editRules() }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth(), compact = true)
                    Spacer(Modifier.height(6.dp))
                    OwnTVButton(stringResource(R.string.common_done), onClick = { session.done() }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth(), compact = true)
                }
            }
        }
    }
    } // PopupFontTheme
    } // Popup
}

/** Confirm before ↺ Restore original names — the only undo for a bulk apply, so it's not optional. */
@Composable
private fun BulkRestoreConfirmDialog(session: BulkRenameSession) {
    val colors = OwnTVTheme.colors
    val restoreFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { restoreFocus.requestFocus() } }
    BackHandler { session.backToChoice() }
    PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 420.dp, padding = 24.dp)) {
            Text(stringResource(R.string.settings_bulk_rename_restore_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_bulk_rename_restore_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.common_cancel), onClick = { session.backToChoice() }, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(stringResource(R.string.settings_bulk_rename_restore), onClick = { session.confirmRestore() }, modifier = Modifier.focusRequester(restoreFocus))
            }
        }
    }
    }
}

/** The > [BULK_RENAME_MAX_ROWS] refusal (plan §2.7): a clear message, nothing else. */
@Composable
private fun BulkRefusedDialog(session: BulkRenameSession) {
    val colors = OwnTVTheme.colors
    val okFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { okFocus.requestFocus() } }
    BackHandler { session.dismissRefused() }
    PopupFontTheme {
    Box(
        modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.dialogPanel(width = 420.dp, padding = 24.dp)) {
            Text(stringResource(R.string.settings_bulk_rename_too_many_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.settings_bulk_rename_too_many_description), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OwnTVButton(stringResource(R.string.common_ok), onClick = { session.dismissRefused() }, modifier = Modifier.focusRequester(okFocus))
            }
        }
    }
    }
}
