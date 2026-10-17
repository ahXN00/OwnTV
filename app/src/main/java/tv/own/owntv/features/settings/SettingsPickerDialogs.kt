package tv.own.owntv.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.mpx

/**
 * A single-select list dialog (value → label), as a Stage popup: the page's eyebrow, the title, an
 * optional line, then one row per choice with a radio (the current one focused first). OK picks;
 * Back closes. [searchable] adds a search field that filters the labels live.
 */
@Composable
internal fun PickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    searchable: Boolean = false,
    trailingLabels: Map<String, String> = emptyMap(),
    leadingIcons: Map<String, OwnTVIcon> = emptyMap(),
    subtitle: String? = null,
    descriptions: Map<String, String> = emptyMap(),
    /** Drawn under an option — the layout chooser's little bar preview. */
    optionPreview: (@Composable (String) -> Unit)? = null,
) {
    // On a Stage settings page the choices open in the page's panel, not in a popup.
    val picker = PanelPicker(options, selected, onSelect, descriptions, searchable, optionPreview)
    if (panelEditor(onDismiss) { help -> PanelChoices(picker, help, onDismiss) }) return
    val fr = remember { FocusRequester() }
    val searchFr = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    // When searchable, filter the option labels live (e.g. finding a category among hundreds).
    val shown = if (searchable && query.isNotBlank()) {
        options.filter { it.second.contains(query.trim(), ignoreCase = true) }
    } else {
        options
    }
    val selIndex = shown.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val list = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = selIndex)
    LaunchedEffect(shown, selected, searchable) {
        // Nested pickers attach in the same frame their opener loses focus. Wait until this popup's
        // focus window exists, otherwise focus remains on the Add/Remove or Prefix/Suffix button.
        kotlinx.coroutines.delay(80)
        runCatching { (if (searchable) searchFr else fr).requestFocus() }
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = title,
        body = subtitle,
        width = if (descriptions.isEmpty() && optionPreview == null) 760.mpx else 880.mpx,
        scroll = false,
    ) {
        if (searchable) {
            tv.own.owntv.ui.stage.StageSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.common_search_hint),
                modifier = Modifier.fillMaxWidth().focusRequester(searchFr),
                height = 56.mpx,
                radius = 18.mpx,
            )
            Spacer(Modifier.height(14.mpx))
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), state = list, verticalArrangement = Arrangement.spacedBy(4.mpx)) {
            itemsIndexed(shown, key = { _, o -> o.first }) { index, (value, label) ->
                val isSel = value == selected
                Column {
                    tv.own.owntv.ui.stage.StagePopupOption(
                        title = label,
                        subtitle = descriptions[value],
                        onClick = { onSelect(value) },
                        modifier = if (index == selIndex) Modifier.focusRequester(fr) else Modifier,
                        leading = { focused ->
                            tv.own.owntv.ui.stage.StagePopupRadio(on = isSel, focused = focused)
                            leadingIcons[value]?.let { tv.own.owntv.ui.stage.StagePopupIcon(it) }
                        },
                        trailing = trailingLabels[value]?.let { name ->
                            { _ -> tv.own.owntv.ui.components.ProviderChip(name = name, maxWidth = 160.mpx, compact = true) }
                        },
                    )
                    optionPreview?.let { preview ->
                        Box(Modifier.padding(start = 64.mpx, end = 22.mpx, top = 4.mpx, bottom = 10.mpx)) { preview(value) }
                    }
                }
            }
        }
    }
}

/** A +/- stepper dialog for an integer value. */
@Composable
internal fun StepperDialog(
    title: String,
    value: Int,
    step: Int,
    min: Int,
    max: Int,
    format: @Composable (Int) -> String,
    onSet: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val stepper: @Composable () -> Unit = {
        PanelStepper(format(value), onStep = { d -> onSet((value + d * step).coerceIn(min, max)) }, onReset = onReset, onDone = onDismiss)
    }
    // On a Stage settings page the value is changed in the page's panel; elsewhere a Stage popup.
    if (panelEditor(onDismiss) { stepper() }) return
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = title) { stepper() }
}
