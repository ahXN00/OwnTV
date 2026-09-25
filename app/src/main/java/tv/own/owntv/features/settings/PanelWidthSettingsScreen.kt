package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.settings.PanelSection
import tv.own.owntv.core.settings.PanelShares
import tv.own.owntv.core.settings.PanelWidthLimits
import tv.own.owntv.core.settings.CINEMATIC_DETAILS_DEFAULT
import tv.own.owntv.core.settings.CINEMATIC_DETAILS_MAX
import tv.own.owntv.features.settings.data.BrowseColumnGap
import tv.own.owntv.features.settings.data.BrowseColumnDividerSpace
import tv.own.owntv.features.settings.data.BrowseContainerPadding
import tv.own.owntv.features.settings.data.cinematicWidths
import tv.own.owntv.features.settings.data.defaultPanelShares
import tv.own.owntv.ui.components.ContentPanelFill
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.PreviewPanelFill
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.restoreAfterDialogClose
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Panel Width Adjustment — lets the user re-balance the three browse panels (category rail · item
 * list/grid · preview/poster) independently for Live TV, Movies and Series.
 *
 * Each panel holds its share of the screen in percent, and the three must add up to exactly 100%.
 * The dialog shows a running total and refuses to save while it doesn't read 100, so the numbers
 * always mean what they look like they mean.
 *
 * This screen measures itself before its own padding, so `maxWidth` here is exactly the width the
 * browse row gets — that's what the stock (seed) percentages are derived from.
 */
@Composable
fun PanelWidthSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val colors = OwnTVTheme.colors

    var open by remember { mutableStateOf<PanelSection?>(null) }
    val rowFocus = remember { PanelSection.entries.associateWith { FocusRequester() } }
    val scrollState = rememberScrollState()
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }

    LaunchedEffect(Unit) { runCatching { rowFocus.getValue(PanelSection.LIVE).requestFocus() } }
    // Snapshot the offset while the dialog is up and hold it across the restore, so the row is already
    // in view when focus lands — see [restoreAfterDialogClose].
    var savedScroll by remember { mutableIntStateOf(0) }
    LaunchedEffect(open) {
        if (open != null) { savedScroll = scrollState.value; return@LaunchedEffect }
        restoreAfterDialogClose(dialogReturn, scrollState, savedScroll)
        dialogReturn = null
    }
    BackHandler { onBack() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val rowWidth = maxWidth - BrowseContainerPadding * 2
        Column(
            modifier = Modifier
                .fillMaxSize()
                .roundedPanel()
                .focusProperties { onEnter = { runCatching { rowFocus.getValue(PanelSection.LIVE).requestFocus() } } }
                .focusGroup()
                .verticalScroll(scrollState)
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Header(title = stringResource(R.string.settings_panel_width), onBack = onBack)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_panel_width_screen_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            val vodLayout by vm.vodLayout.collectAsStateWithLifecycle()
            val cinematic = vodLayout == tv.own.owntv.core.settings.SettingsRepository.VodLayout.CINEMATIC
            val detailsHeights = PanelSection.entries.associateWith {
                vm.cinematicDetailsHeight(it).collectAsStateWithLifecycle().value
            }
            PanelSection.entries.forEach { section ->
                val enabled by vm.panelWidthEnabled.getValue(section).collectAsStateWithLifecycle()
                val shares by vm.panelShares.getValue(section).collectAsStateWithLifecycle()
                val current = shares ?: defaultPanelShares(section, rowWidth)
                Row2(
                    icon = when (section) {
                        PanelSection.LIVE -> OwnTVIcon.LIVE_TV
                        PanelSection.MOVIES -> OwnTVIcon.MOVIES
                        PanelSection.SERIES -> OwnTVIcon.SERIES
                    },
                    title = sectionTitle(section),
                    desc = stringResource(
                        R.string.settings_panel_width_summary,
                        current.category,
                        current.list,
                        // In Cinematic the third number is a height held separately, so the summary
                        // must show THAT value — not the preview share, which is always 0 there.
                        if (cinematic && section != PanelSection.LIVE) {
                            stringResource(R.string.settings_panel_width_details_height)
                        } else {
                            previewLabel(section)
                        },
                        if (cinematic && section != PanelSection.LIVE) detailsHeights.getValue(section) else current.preview,
                    ),
                    chip = stringResource(if (enabled) R.string.settings_live_latency_custom else R.string.settings_subtitle_default),
                    primaryChip = enabled,
                    chevron = true,
                    onClick = { dialogReturn = rowFocus.getValue(section); open = section },
                    modifier = Modifier.focusRequester(rowFocus.getValue(section)),
                )
            }

            Spacer(Modifier.height(12.dp))
            GroupLabel(stringResource(R.string.settings_how_it_works))
            Text(
                stringResource(R.string.settings_panel_width_help, *NO_ARGS),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        open?.let { section ->
            PanelWidthDialog(section = section, rowWidth = rowWidth, vm = vm, onDismiss = { open = null })
        }
    }
}

@Composable
private fun sectionTitle(section: PanelSection): String = when (section) {
    PanelSection.LIVE -> stringResource(R.string.settings_live_tv)
    PanelSection.MOVIES -> stringResource(R.string.settings_movies)
    PanelSection.SERIES -> stringResource(R.string.settings_series)
}

@Composable
private fun previewLabel(section: PanelSection): String =
    stringResource(if (section == PanelSection.LIVE) R.string.settings_panel_width_preview else R.string.settings_panel_width_poster)

/**
 * The second and third slider labels, which change with the layout.
 *
 * In Cinematic there is no preview panel to size, so the third slider sets the height of the detail
 * block instead and the second one is sizing the whole content column, not a list next to a preview.
 * Live TV is never Cinematic, so its labels never move.
 */
@Composable
private fun listLabel(section: PanelSection, cinematic: Boolean): String =
    if (cinematic && section != PanelSection.LIVE) stringResource(R.string.settings_panel_width_content_area)
    else stringResource(R.string.settings_panel_width_list)

@Composable
private fun thirdSliderLabel(section: PanelSection, cinematic: Boolean): String =
    if (cinematic && section != PanelSection.LIVE) stringResource(R.string.settings_panel_width_details_height)
    else stringResource(R.string.settings_panel_width_preview_panel, previewLabel(section))

/**
 * The per-section popup: master toggle, then one −/+ stepper per panel, a running total, and
 * Reset / Okay.
 *
 * Edits are held as a draft and only written on Okay — and Okay refuses while the total isn't 100%,
 * showing the reason in red. That way nothing half-adjusted can ever reach the browse screens, and
 * backing out discards cleanly.
 */
@Composable
private fun PanelWidthDialog(
    section: PanelSection,
    rowWidth: Dp,
    vm: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val savedEnabled by vm.panelWidthEnabled.getValue(section).collectAsStateWithLifecycle()
    val savedShares by vm.panelShares.getValue(section).collectAsStateWithLifecycle()
    val livePreviewEnabled by vm.livePreviewEnabled.collectAsStateWithLifecycle()
    val vodLayout by vm.vodLayout.collectAsStateWithLifecycle()
    val cinematic = vodLayout == tv.own.owntv.core.settings.SettingsRepository.VodLayout.CINEMATIC
    val stock = remember(section, rowWidth) { defaultPanelShares(section, rowWidth) }
    // Cinematic's detail block is a HEIGHT, so it is its own stored value and takes no part in the
    // 100% row budget below. Live TV is never Cinematic and never shows this row.
    val showDetailsHeight = cinematic && section != PanelSection.LIVE
    val savedDetailsHeight by vm.cinematicDetailsHeight(section).collectAsStateWithLifecycle()

    var enabled by remember { mutableStateOf(savedEnabled) }
    var draft by remember { mutableStateOf(savedShares ?: stock) }
    // Seeded ONCE, exactly like `draft` above — never resynced from the flow while the dialog is up.
    // The saved value is a `stateIn(WhileSubscribed)` StateFlow, so it starts at the default and the
    // stored number lands a frame later; a resync would let that late emission overwrite whatever the
    // user had already stepped to, and the edit would save as the default instead.
    var detailsHeight by remember(section) { mutableStateOf(savedDetailsHeight) }
    // Cinematic is two columns, so the third share must read 0 or the total would never reach 100
    // with only two steppers on screen. Whatever a preview panel used to hold goes to the content
    // area — that is where the space physically goes once the preview column is gone.
    LaunchedEffect(showDetailsHeight) {
        if (showDetailsHeight) draft = cinematicWidths(draft)
    }
    // The red note only appears once the user has actually tried to save an unbalanced total.
    var showError by remember { mutableStateOf(false) }
    var showPreviewDisableConfirmation by remember { mutableStateOf(false) }
    val valid = draft.isValid

    val toggleFocus = remember { FocusRequester() }
    val confirmationFocus = remember { FocusRequester() }
    LaunchedEffect(showPreviewDisableConfirmation) {
        kotlinx.coroutines.delay(80)
        runCatching {
            if (showPreviewDisableConfirmation) confirmationFocus.requestFocus() else toggleFocus.requestFocus()
        }
    }
    LaunchedEffect(valid) { if (valid) showError = false }
    BackHandler {
        if (showPreviewDisableConfirmation) showPreviewDisableConfirmation = false else onDismiss()
    }

    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            if (showPreviewDisableConfirmation) {
                Column(modifier = Modifier.dialogPanel(width = 500.dp, corner = 16.dp, padding = 24.dp)) {
                    Text(
                        stringResource(R.string.settings_panel_width_disable_preview_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.settings_panel_width_disable_preview_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OwnTVButton(
                            stringResource(R.string.common_cancel),
                            onClick = { showPreviewDisableConfirmation = false },
                            modifier = Modifier.focusRequester(confirmationFocus),
                        )
                        Spacer(Modifier.weight(1f))
                        OwnTVButton(
                            stringResource(R.string.common_ok),
                            onClick = {
                                // This branch is Live TV only, which is never Cinematic — the
                                // details height is saved by the main Okay button below.
                                vm.setPanelWidths(section, enabled, draft)
                                onDismiss()
                            },
                            style = OwnTVButtonStyle.SECONDARY,
                        )
                    }
                }
            } else {
            Column(modifier = Modifier.dialogPanel(width = 440.dp, corner = 16.dp, padding = 16.dp)) {
                Text(
                    stringResource(R.string.settings_panel_width_dialog_title, sectionTitle(section)),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(12.dp))

                FocusableSurface(
                    onClick = { enabled = !enabled },
                    modifier = Modifier.fillMaxWidth().focusRequester(toggleFocus),
                    shape = RoundedCornerShape(12.dp),
                    surface = GlassSurface.DIALOGS,
                    contentAlignment = Alignment.CenterStart,
                ) { _ ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_panel_width_customize),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(if (enabled) R.string.common_on else R.string.common_off),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (enabled) colors.onPrimaryContainer else colors.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (enabled) colors.primaryContainer else colors.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                if (enabled) {
                    Spacer(Modifier.height(10.dp))
                    if (showDetailsHeight) {
                        // Two columns that must total 100 are really one slider, so they move
                        // together: whatever one gives up, the other takes. Without this the total
                        // could sit at 100 while a column was over the per-panel 80% cap, which
                        // `isValid` rejects — Okay then refused to save anything at all, silently.
                        // The 20..80 bounds are what keep BOTH sides inside that cap.
                        StepRow(
                            stringResource(R.string.settings_panel_width_category),
                            draft.category,
                            minimum = PanelWidthLimits.TOTAL - PanelWidthLimits.MAX,
                            maximum = PanelWidthLimits.MAX,
                        ) { draft = PanelShares(it, PanelWidthLimits.TOTAL - it, 0) }
                        Spacer(Modifier.height(6.dp))
                        StepRow(
                            listLabel(section, cinematic),
                            draft.list,
                            minimum = PanelWidthLimits.TOTAL - PanelWidthLimits.MAX,
                            maximum = PanelWidthLimits.MAX,
                        ) { draft = PanelShares(PanelWidthLimits.TOTAL - it, it, 0) }
                    } else {
                    StepRow(stringResource(R.string.settings_panel_width_category), draft.category) { draft = draft.copy(category = it) }
                    Spacer(Modifier.height(6.dp))
                    StepRow(listLabel(section, cinematic), draft.list) { draft = draft.copy(list = it) }
                    }
                    if (!showDetailsHeight) {
                        Spacer(Modifier.height(6.dp))
                        StepRow(
                            thirdSliderLabel(section, cinematic),
                            draft.preview,
                            minimum = 0,
                        ) { draft = draft.copy(preview = it) }
                    }

                    Spacer(Modifier.height(10.dp))
                    PanelWidthDiagram(
                        draft,
                        cinematic = showDetailsHeight,
                        detailsHeight = detailsHeight,
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_panel_width_total),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.common_percent, draft.total),
                            style = MaterialTheme.typography.titleMedium,
                            // `favorite` is the theme's red — the same one MaterialTheme maps to `error`.
                            color = if (valid) colors.primary else colors.favorite,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            // Same width as a stepper's value + one button, so it lines up under them.
                            modifier = Modifier.padding(end = 48.dp).width(64.dp),
                        )
                    }

                    if (showDetailsHeight) {
                        // Below the total on purpose: everything above adds up to 100% across the
                        // row, and this one does not take part in that at all.
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.outlineVariant.copy(alpha = 0.5f)),
                        )
                        Spacer(Modifier.height(12.dp))
                        StepRow(
                            stringResource(R.string.settings_panel_width_details_height),
                            detailsHeight,
                            minimum = 0,
                            maximum = CINEMATIC_DETAILS_MAX,
                        ) { detailsHeight = it }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_panel_width_details_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }

                    if (showError) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.favorite.copy(alpha = 0.18f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                stringResource(R.string.settings_panel_width_invalid_total, draft.total),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.favorite,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVButton(
                        stringResource(R.string.common_reset),
                        onClick = {
                            draft = stock
                            detailsHeight = CINEMATIC_DETAILS_DEFAULT
                            showError = false
                        },
                        style = OwnTVButtonStyle.SECONDARY,
                    )
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(
                        stringResource(R.string.common_ok),
                        onClick = {
                            // An unbalanced total is only a problem for a section that's actually on.
                            if (enabled && !valid) {
                                showError = true
                            } else if (
                                section == PanelSection.LIVE && enabled && draft.preview == 0 && livePreviewEnabled
                            ) {
                                showPreviewDisableConfirmation = true
                            } else {
                                vm.setPanelWidths(section, enabled, draft)
                                if (showDetailsHeight) vm.setCinematicDetailsHeight(section, detailsHeight)
                                onDismiss()
                            }
                        },
                    )
                }
            }
            }
        }
    }
}

/** The browse layout users are sizing: one container, two plain columns, and a raised preview. */
@Composable
private fun PanelWidthDiagram(shares: PanelShares, cinematic: Boolean = false, detailsHeight: Int = 0) {
    val colors = OwnTVTheme.colors
    if (cinematic) {
        // Two columns, and the third share drawn as a band across the top of the content one —
        // because that is what it now does. A preview column here would be a picture of a panel
        // the layout does not have.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ContentPanelFill)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(shares.category.toFloat().coerceAtLeast(1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.onSurface.copy(alpha = 0.035f)),
            )
            Spacer(Modifier.width(BrowseColumnGap))
            Column(
                Modifier
                    .weight(shares.list.toFloat().coerceAtLeast(1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                val detailsWeight = detailsHeight.toFloat().coerceIn(0f, CINEMATIC_DETAILS_MAX.toFloat())
                Box(
                    Modifier
                        .weight(detailsWeight)
                        .fillMaxWidth()
                        .background(colors.primary.copy(alpha = 0.32f)),
                )
                Box(
                    Modifier
                        .weight((100f - detailsWeight).coerceAtLeast(1f))
                        .fillMaxWidth()
                        .background(colors.onSurface.copy(alpha = 0.10f)),
                )
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
                .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
                .background(ContentPanelFill)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(shares.category.toFloat())
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                .background(colors.onSurface.copy(alpha = 0.035f)),
        )
        Spacer(Modifier.width(BrowseColumnGap))
        Box(
            Modifier
                .width(BrowseColumnDividerSpace)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .background(colors.outlineVariant.copy(alpha = 0.35f)),
        )
        Spacer(Modifier.width(BrowseColumnGap))
        Box(
            Modifier
                .weight(shares.list.toFloat())
                .fillMaxHeight(),
        )
        if (shares.preview != 0) {
            Spacer(Modifier.width(BrowseColumnGap))
            Box(
                Modifier
                    .weight(shares.preview.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PreviewPanelFill),
            )
        }
    }
}

/** One panel's row: label, then − value + in [PanelWidthLimits.STEP] increments. */
@Composable
internal fun StepRow(
    label: String,
    value: Int,
    minimum: Int = PanelWidthLimits.MIN,
    maximum: Int = PanelWidthLimits.MAX,
    step: Int = PanelWidthLimits.STEP,
    onSet: (Int) -> Unit,
) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
        // Both buttons stay focusable at the ends of the range: disabling the one holding focus would
        // drop it, and focus is trapped in this dialog — the D-pad would go dead (the bug fixed in
        // StepperDialog). They just stop moving the value and dim instead.
        StepBtn("–", atLimit = value <= minimum) {
            onSet((value - step).coerceAtLeast(minimum))
        }
        Text(
            stringResource(R.string.common_percent, value),
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        StepBtn("+", atLimit = value >= maximum) {
            onSet((value + step).coerceAtMost(maximum))
        }
    }
}

/** Square − / + button (matches the one in NumberInputDialog / StepperDialog). */
@Composable
private fun StepBtn(label: String, atLimit: Boolean, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.Center,
        surface = GlassSurface.DIALOGS,
    ) { _ ->
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (atLimit) colors.outline else colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
