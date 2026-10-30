package tv.own.owntv.features.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.ui.components.BrandMark
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.format.localizedInteger
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * First-run interface size: UI zoom and text size as steppers (◀ ▶), the app icon, and a
 * sample row to judge them by. Both steppers write straight through, so the whole step — rows and
 * sample alike — resizes as the user presses. No draft state and no Apply.
 */
@Composable
internal fun DisplaySizeStep(onNext: () -> Unit, onBack: () -> Unit) {
    val vm: DisplaySizeViewModel = koinViewModel()
    val zoom by vm.uiZoomPercent.collectAsStateWithLifecycle()
    val fontSize by vm.fontSizePercent.collectAsStateWithLifecycle()
    val appIcon by vm.appIcon.collectAsStateWithLifecycle()
    val zoomFocus = rememberFirstFocus()
    // Zoom below LOW_RAM_WARN can OOM a 2 GB device, so the first step under the line is gated
    // exactly as the Settings dialog gates it. Accepting once arms the rest of this visit; arriving
    // already below the line (a restored backup) must not nag.
    var lowZoomAccepted by remember { mutableStateOf(zoom < UiZoom.LOW_RAM_WARN) }
    var pendingLowZoom by remember { mutableStateOf<Int?>(null) }
    var pickIcon by remember { mutableStateOf(false) }
    val stepZoom: (Int) -> Unit = { dir ->
        if (dir > 0) vm.setZoom(zoom + UiZoom.STEP)
        else {
            val next = UiZoom.clamp(zoom - UiZoom.STEP)
            if (next < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) pendingLowZoom = next else vm.setZoom(next)
        }
    }
    WizardFrame(
        step = WizardStep.DISPLAY,
        title = stringResource(R.string.setup_display_size_title),
        sub = stringResource(R.string.setup_display_sub),
        back = WizardAction(stringResource(R.string.common_back), onBack),
        next = WizardAction(stringResource(R.string.setup_continue), onNext),
        extraLeft = listOf(WizardAction(stringResource(R.string.settings_reset), { vm.reset() })),
    ) {
        StageSettingRow(
            icon = OwnTVIcon.EXPAND,
            title = stringResource(R.string.settings_ui_zoom),
            desc = stringResource(R.string.setup_line_zoom),
            value = SettingValue.Stepper(stringResource(R.string.common_percent, zoom)),
            onClick = { stepZoom(1) },
            onStep = stepZoom,
            modifier = Modifier.focusRequester(zoomFocus),
        )
        StageSettingRow(
            icon = OwnTVIcon.PENCIL,
            title = stringResource(R.string.settings_font_size),
            desc = stringResource(R.string.setup_line_font),
            value = SettingValue.Stepper(stringResource(R.string.common_percent, fontSize)),
            onClick = { vm.setFontSize(fontSize + UiFontScale.STEP) },
            onStep = { dir -> vm.setFontSize(fontSize + dir * UiFontScale.STEP) },
        )
        // No restart prompt here: nothing is on the home screen yet, and the pick applies as soon as
        // the app is next in the background.
        val icons = AppIcon.entries
        val at = icons.indexOf(appIcon).coerceAtLeast(0)
        StageSettingRow(
            icon = OwnTVIcon.SPARKLE,
            title = stringResource(R.string.settings_app_icon),
            desc = stringResource(R.string.setup_line_app_icon),
            value = SettingValue.Custom { AppIconSwatches(icons, appIcon) },
            onClick = { pickIcon = true },
            onStep = { dir -> vm.setAppIcon(icons[(at + dir + icons.size) % icons.size]) },
        )
        // The sample: a row's own type at the size it will really be.
        Row(
            Modifier
                .padding(top = 14.mpx)
                .fillMaxWidth()
                .height(84.mpx)
                .background(StageColors.Ink.copy(alpha = 0.05f), RoundedCornerShape(20.mpx))
                .padding(horizontal = 22.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_panel_width_preview).uppercase(), style = stageText(13, 800, androidx.compose.ui.unit.TextUnit(0.13f, androidx.compose.ui.unit.TextUnitType.Em)), color = StageColors.Dim)
            Text(stringResource(R.string.home_row_now_trending), style = stageText(21, 700), color = StageColors.Text, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.common_on), style = stageText(18, 700), color = stageAccent.accent)
        }
    }

    // OK on App icon: the icon tiles in a popup (no restart question — nothing is on the home screen yet).
    if (pickIcon) {
        val tileFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { tileFocus.requestFocus() } }
        tv.own.owntv.ui.stage.StagePopup(onDismiss = { pickIcon = false }, title = stringResource(R.string.settings_app_icon), eyebrow = null, width = 880.mpx) {
            tv.own.owntv.ui.components.AppIconPicker(
                selected = appIcon,
                onPick = { vm.setAppIcon(it); pickIcon = false },
                firstFocus = tileFocus,
            )
        }
    }

    // Accept-the-risk gate for zoom below LOW_RAM_WARN, the same prompt Settings shows. One
    // button, focus locked in every D-pad direction — OK accepts and applies the pending step, Back
    // cancels and leaves the zoom where it was.
    pendingLowZoom?.let { target ->
        // The same low-memory question Settings asks, as a Stage question with focus on Cancel.
        tv.own.owntv.ui.stage.StageConfirm(
            title = stringResource(R.string.settings_low_zoom_warning_title),
            body = stringResource(R.string.settings_low_zoom_warning, UiZoom.LOW_RAM_WARN, UiZoom.LOW_RAM_WARN),
            confirm = stringResource(R.string.settings_low_zoom_accept),
            onConfirm = {
                lowZoomAccepted = true
                pendingLowZoom = null
                vm.setZoom(target)
                runCatching { zoomFocus.requestFocus() }
            },
            onCancel = {
                pendingLowZoom = null
                runCatching { zoomFocus.requestFocus() }
            },
            focusCancel = true,
            eyebrow = null,
        )
    }
}

/** The app icon row's value: the first five icon colours, the chosen one ringed, then "+N". */
@Composable
private fun AppIconSwatches(icons: List<AppIcon>, chosen: AppIcon) {
    val a = stageAccent
    val shown = (icons.take(5) + chosen).distinct().take(5)
    Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { icon ->
            Box(
                Modifier
                    .size(34.mpx)
                    .then(if (icon == chosen) Modifier.border(2.mpx, a.accent, RoundedCornerShape(9.mpx)) else Modifier),
                contentAlignment = Alignment.Center,
            ) { BrandMark(icon, 30.mpx, followAccent = false) }
        }
        if (icons.size > shown.size) {
            Text("+" + localizedInteger(icons.size - shown.size, grouping = false), style = stageText(17, 700), color = StageColors.Muted)
        }
    }
}
