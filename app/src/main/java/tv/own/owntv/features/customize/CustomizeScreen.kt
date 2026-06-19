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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.TextInputDialog
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Settings → Customize: hide / rename / reorder categories per section, and unhide hidden Live
 * channels. Everything is per-profile and survives source re-syncs.
 */
@Composable
fun CustomizeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: CustomizeViewModel = koinViewModel()
    val section by vm.section.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val hiddenChannels by vm.hiddenChannels.collectAsStateWithLifecycle()
    val rangeAnchorKey by vm.rangeAnchorKey.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    var renaming by remember { mutableStateOf<CustomizeCatRow?>(null) }
    // The category whose Hide button was clicked to close a range — opens the Show/Hide/Cancel prompt.
    var rangeEnd by remember { mutableStateOf<CustomizeCatRow?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }

    // While a span selection is in progress, Back cancels the selection instead of leaving the screen.
    BackHandler { if (rangeAnchorKey != null) vm.cancelRange() else onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            // Spatial D-pad entry from the sidebar would land mid-list — route it to the first chip.
            // onEnter fires only for directional entry from outside (internal moves don't re-trigger it).
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Text("Customize", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "Hide, rename and reorder categories for this profile. Changes survive re-syncs.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Section picker
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionChip("Live TV", section == MediaType.LIVE, Modifier.focusRequester(firstFocus)) { vm.selectSection(MediaType.LIVE) }
            SectionChip("Movies", section == MediaType.MOVIE) { vm.selectSection(MediaType.MOVIE) }
            SectionChip("Series", section == MediaType.SERIES) { vm.selectSection(MediaType.SERIES) }
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
                    "Span selection started. Press Show/Hide on the end item to select the span.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                OwnTVButton("Cancel", onClick = { vm.cancelRange() }, style = OwnTVButtonStyle.SECONDARY)
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            // Hidden Live channels first (channels are hidden from the Live preview pane) — kept on
            // top so they're findable even when a provider has hundreds of categories below.
            if (section == MediaType.LIVE && hiddenChannels.isNotEmpty()) {
                item {
                    Text("Hidden channels", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Unhide to bring a channel back to the lists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(hiddenChannels.entries.sortedBy { it.value.lowercase() }, key = { "hid:${it.key}" }) { (key, label) ->
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
                        OwnTVButton("Unhide", onClick = { vm.unhideChannel(key) }, style = OwnTVButtonStyle.SECONDARY)
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    Text("Categories", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (rows.isEmpty()) {
                item {
                    Text(
                        "No categories in this section yet — add a source first.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(rows, key = { it.key }) { row ->
                CategoryRow(
                    row = row,
                    inRangeMode = rangeAnchorKey != null,
                    isAnchor = row.key == rangeAnchorKey,
                    onMoveUp = { vm.move(row, up = true) },
                    onMoveDown = { vm.move(row, up = false) },
                    onRename = { renaming = row },
                    onToggleHidden = { vm.setCategoryHidden(row, !row.hidden) },
                    onHideLongPress = { vm.beginRange(row) },
                    onPickRangeEnd = { if (row.key == rangeAnchorKey) vm.cancelRange() else rangeEnd = row },
                )
            }
        }
    }

    renaming?.let { row ->
        TextInputDialog(
            title = "Rename category",
            initial = row.displayName,
            hint = "Only for this profile. Leave blank to restore “${row.originalName}”.",
            onConfirm = { vm.renameCategory(row, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
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
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(480.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp),
        ) {
            Text("Hide or show categories", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "$count ${if (count == 1) "category" else "categories"} selected.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Show", onClick = onShow, style = OwnTVButtonStyle.SECONDARY)
                OwnTVButton("Hide", onClick = onHide, modifier = Modifier.focusRequester(hideFocus))
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
    isAnchor: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onHideLongPress: () -> Unit,
    onPickRangeEnd: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // Tint the anchor while a range is in progress so its starting point is obvious.
            .background(if (isAnchor) colors.primaryContainer else colors.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (row.hidden) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.hidden || row.renamed) {
                Text(
                    buildString {
                        if (row.hidden) append("Hidden")
                        if (row.renamed) {
                            if (row.hidden) append("  ·  ")
                            append("was “${row.originalName}”")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        OwnTVButton("↑", onClick = onMoveUp, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("↓", onClick = onMoveDown, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton("Rename", onClick = onRename, style = OwnTVButtonStyle.SECONDARY)
        Spacer(Modifier.width(6.dp))
        OwnTVButton(
            label = if (row.hidden) "Show" else "Hide",
            // Long-press anchors a range; a normal press picks the span end while a range is active,
            // otherwise it toggles just this category.
            onClick = { if (inRangeMode) onPickRangeEnd() else onToggleHidden() },
            onLongClick = onHideLongPress,
            style = OwnTVButtonStyle.SECONDARY,
        )
    }
}
