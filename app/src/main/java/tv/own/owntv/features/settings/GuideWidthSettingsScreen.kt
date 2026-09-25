package tv.own.owntv.features.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.settings.GuideWidthLimits
import tv.own.owntv.core.settings.GuideWidthShares
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.dialogPanel
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.roundedPanel
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme

/** Layout setting for the Guide's pinned channel column and scrollable programme timeline. */
@Composable
fun GuideWidthSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val enabled by vm.guideWidthEnabled.collectAsStateWithLifecycle()
    val shares by vm.guideWidthShares.collectAsStateWithLifecycle()
    val current = shares ?: GuideWidthLimits.defaults
    val rowFocus = remember { FocusRequester() }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { rowFocus.requestFocus() } }
    LaunchedEffect(showDialog) {
        if (!showDialog) runCatching { rowFocus.requestFocus() }
    }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Header(title = stringResource(R.string.settings_guide_width), onBack = onBack)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_guide_width_description),
            style = MaterialTheme.typography.bodyMedium,
            color = OwnTVTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row2(
            icon = OwnTVIcon.EPG,
            title = stringResource(R.string.content_epg_title),
            desc = stringResource(
                R.string.settings_guide_width_summary,
                current.channels,
                current.epg,
            ),
            chip = stringResource(
                if (enabled) R.string.settings_live_latency_custom else R.string.settings_subtitle_default,
            ),
            primaryChip = enabled,
            chevron = true,
            modifier = Modifier.focusRequester(rowFocus),
            onClick = { showDialog = true },
        )
        Spacer(Modifier.height(12.dp))
        GroupLabel(stringResource(R.string.settings_how_it_works))
        Text(
            stringResource(R.string.settings_guide_width_help, *NO_ARGS),
            style = MaterialTheme.typography.bodyMedium,
            color = OwnTVTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    if (showDialog) {
        GuideWidthDialog(
            savedEnabled = enabled,
            savedShares = current,
            onSave = vm::setGuideWidths,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun GuideWidthDialog(
    savedEnabled: Boolean,
    savedShares: GuideWidthShares,
    onSave: (Boolean, GuideWidthShares) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var enabled by remember { mutableStateOf(savedEnabled) }
    var draft by remember { mutableStateOf(savedShares) }
    var showError by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    tv.own.owntv.ui.components.OwnTVPopup(onDismissRequest = onDismiss) {
        tv.own.owntv.ui.theme.PopupFontTheme(fontScale = 0.75f) {
            Box(
                modifier = Modifier.fillMaxSize().modalScrim().trapAllFocusExit().focusGroup(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.dialogPanel(width = 440.dp, corner = 16.dp, padding = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_guide_width),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    Row2(
                        icon = OwnTVIcon.EPG,
                        title = stringResource(R.string.settings_panel_width_customize),
                        desc = stringResource(R.string.settings_guide_width_description),
                        chip = stringResource(if (enabled) R.string.common_on else R.string.common_off),
                        primaryChip = enabled,
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = { enabled = !enabled },
                    )

                    GuideWidthDiagram(draft)
                    StepRow(
                        label = stringResource(R.string.settings_guide_width_channels),
                        value = draft.channels,
                        minimum = GuideWidthLimits.MIN,
                        maximum = GuideWidthLimits.MAX,
                        step = GuideWidthLimits.STEP,
                        onSet = { draft = draft.copy(channels = it); showError = false },
                    )
                    StepRow(
                        label = stringResource(R.string.settings_guide_width_epg),
                        value = draft.epg,
                        minimum = GuideWidthLimits.MIN,
                        maximum = GuideWidthLimits.MAX,
                        step = GuideWidthLimits.STEP,
                        onSet = { draft = draft.copy(epg = it); showError = false },
                    )

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.settings_panel_width_total),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.common_percent, draft.total),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (draft.isValid) colors.primary else colors.favorite,
                        )
                    }
                    if (showError && !draft.isValid) {
                        Text(
                            stringResource(R.string.settings_panel_width_invalid_total, draft.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.favorite,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OwnTVButton(
                            stringResource(R.string.common_reset),
                            onClick = { draft = GuideWidthLimits.defaults; showError = false },
                            style = OwnTVButtonStyle.SECONDARY,
                        )
                        Spacer(Modifier.weight(1f))
                        OwnTVButton(
                            stringResource(R.string.common_cancel),
                            onClick = onDismiss,
                            style = OwnTVButtonStyle.SECONDARY,
                        )
                        OwnTVButton(
                            stringResource(R.string.common_ok),
                            onClick = {
                                if (!draft.isValid) showError = true
                                else {
                                    onSave(enabled, draft)
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

@Composable
private fun GuideWidthDiagram(shares: GuideWidthShares) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(shares.channels.toFloat())
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.common_percent, shares.channels),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .weight(shares.epg.toFloat())
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.common_percent, shares.epg),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
