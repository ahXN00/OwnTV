package tv.own.owntv.features.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.player.ZoomMode
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

/** Common languages offered for the audio/subtitle preference (code → display name; "" = no preference). */
private val LANGUAGES = listOf(
    "" to "None / Auto",
    "eng" to "English",
    "spa" to "Spanish",
    "fra" to "French",
    "deu" to "German",
    "ita" to "Italian",
    "por" to "Portuguese",
    "nld" to "Dutch",
    "rus" to "Russian",
    "ara" to "Arabic",
    "hin" to "Hindi",
    "zho" to "Chinese",
    "jpn" to "Japanese",
    "kor" to "Korean",
    "tur" to "Turkish",
)

private val SUB_SIZES = listOf(0.8f to "Small", 1.0f to "Normal", 1.3f to "Large", 1.6f to "Extra Large")

private fun langName(code: String) = LANGUAGES.firstOrNull { it.first == code }?.second ?: code.ifBlank { "None / Auto" }
private fun subSizeName(scale: Float) = SUB_SIZES.minByOrNull { kotlin.math.abs(it.first - scale) }?.second ?: "Normal"

/**
 * Video Player settings — decoder, default aspect/zoom, subtitle size & language, audio sync. Each
 * value is persisted and applied to the shared mpv player (live where possible, otherwise next load).
 */
@Composable
fun VideoPlayerSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: SettingsViewModel = koinViewModel()
    val hw by vm.hwDecoding.collectAsStateWithLifecycle()
    val zoom by vm.defaultZoom.collectAsStateWithLifecycle()
    val subScale by vm.subtitleScale.collectAsStateWithLifecycle()
    val audioDelay by vm.audioDelayMs.collectAsStateWithLifecycle()
    val audioLang by vm.preferredAudioLang.collectAsStateWithLifecycle()
    val subLang by vm.preferredSubLang.collectAsStateWithLifecycle()
    val resumeMode by vm.resumeMode.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf(Dialog.NONE) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onBack() }

    // Dialog-close focus return: closing a picker refocuses the row that opened it. The restore
    // request crosses INTO this screen's focus group from the dialog, so the group's onEnter
    // intercepts it — it consults dialogReturn first (and clears it) instead of hijacking.
    val dialogRowFocus = remember { Dialog.entries.associateWith { FocusRequester() } }
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(dialog) {
        if (dialog != Dialog.NONE) {
            dialogReturn = dialogRowFocus.getValue(dialog)
        } else {
            dialogReturn?.let { row ->
                kotlinx.coroutines.delay(80)
                runCatching { row.requestFocus() }
            }
        }
    }

    val zoomMode = runCatching { ZoomMode.valueOf(zoom) }.getOrDefault(ZoomMode.FIT)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            // onEnter fires for any entry from outside the group — including our own dialog-close
            // restores (the dialogs live outside it) — so it must prefer the pending return row.
            .focusProperties {
                onEnter = {
                    val target = dialogReturn ?: firstFocus
                    dialogReturn = null
                    runCatching { target.requestFocus() }
                }
            }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Header("Video Player", onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel("Decoding")
        Row2(
            icon = OwnTVIcon.VIDEO, title = "Hardware decoding",
            desc = "Use the TV's hardware decoder. Turn off if some streams stutter or show artifacts.",
            chip = if (hw) "On" else "Off", primaryChip = hw,
            modifier = Modifier.focusRequester(firstFocus),
            onClick = { vm.setHwDecoding(!hw) },
        )
        Row2(
            icon = OwnTVIcon.ASPECT, title = "Default zoom",
            desc = "Aspect/zoom applied when playback starts.",
            chip = zoomMode.label, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.ZOOM)),
            onClick = { dialog = Dialog.ZOOM },
        )
        Row2(
            icon = OwnTVIcon.PLAY, title = "Resume playback",
            desc = "What to do when a movie or episode has a saved position.",
            chip = resumeMode.label, chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.RESUME)),
            onClick = { dialog = Dialog.RESUME },
        )

        Divider()
        GroupLabel("Subtitles")
        Row2(
            icon = OwnTVIcon.SUBTITLE, title = "Subtitle size",
            desc = "Scale subtitle text.",
            chip = subSizeName(subScale), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.SUB_SIZE)),
            onClick = { dialog = Dialog.SUB_SIZE },
        )
        Row2(
            icon = OwnTVIcon.SUBTITLE, title = "Preferred subtitle language",
            desc = "Auto-select this subtitle track when available.",
            chip = langName(subLang), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.SUB_LANG)),
            onClick = { dialog = Dialog.SUB_LANG },
        )

        Divider()
        GroupLabel("Audio")
        Row2(
            icon = OwnTVIcon.AUDIO, title = "Preferred audio language",
            desc = "Auto-select this audio track when available.",
            chip = langName(audioLang), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.AUDIO_LANG)),
            onClick = { dialog = Dialog.AUDIO_LANG },
        )
        Row2(
            icon = OwnTVIcon.AUDIO, title = "Audio sync",
            desc = "Shift audio earlier or later to match the video.",
            chip = "%+d ms".format(audioDelay), chevron = true,
            modifier = Modifier.focusRequester(dialogRowFocus.getValue(Dialog.AUDIO_SYNC)),
            onClick = { dialog = Dialog.AUDIO_SYNC },
        )
    }

    when (dialog) {
        Dialog.ZOOM -> PickerDialog(
            title = "Default zoom",
            options = ZoomMode.entries.map { it.name to it.label },
            selected = zoomMode.name,
            onSelect = { vm.setDefaultZoom(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.RESUME -> PickerDialog(
            title = "Resume playback",
            options = tv.own.owntv.features.settings.data.SettingsRepository.ResumeMode.entries.map { it.name to it.label },
            selected = resumeMode.name,
            onSelect = { vm.setResumeMode(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SUB_SIZE -> PickerDialog(
            title = "Subtitle size",
            options = SUB_SIZES.map { it.first.toString() to it.second },
            selected = (SUB_SIZES.minByOrNull { kotlin.math.abs(it.first - subScale) }?.first ?: 1.0f).toString(),
            onSelect = { vm.setSubtitleScale(it.toFloat()); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.SUB_LANG -> PickerDialog(
            title = "Subtitle language",
            options = LANGUAGES,
            selected = subLang,
            onSelect = { vm.setPreferredSubLang(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AUDIO_LANG -> PickerDialog(
            title = "Audio language",
            options = LANGUAGES,
            selected = audioLang,
            onSelect = { vm.setPreferredAudioLang(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.AUDIO_SYNC -> StepperDialog(
            title = "Audio sync",
            value = audioDelay, step = 50, min = -2000, max = 2000,
            format = { "%+d ms".format(it) },
            onSet = { vm.setAudioDelayMs(it) },
            onReset = { vm.setAudioDelayMs(0) },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.NONE -> Unit
    }
}

private enum class Dialog { NONE, ZOOM, SUB_SIZE, SUB_LANG, AUDIO_LANG, AUDIO_SYNC, RESUME }

// --- Shared building blocks (kept local to the settings sub-screens) ---

@Composable
internal fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusableSurface(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            contentAlignment = Alignment.Center,
        ) { _ -> OwnTVIcon(OwnTVIcon.BACK, tint = OwnTVTheme.colors.onSurface, modifier = Modifier.size(20.dp)) }
        Text(title, style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
    }
}

@Composable
internal fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OwnTVTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
internal fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(1.dp)
            .background(OwnTVTheme.colors.outlineVariant),
    )
}

/** A settings row with an icon tile, title/description and a trailing value chip (+ optional chevron). */
@Composable
internal fun Row2(
    icon: OwnTVIcon,
    title: String,
    desc: String? = null,
    chip: String? = null,
    primaryChip: Boolean = true,
    chevron: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(Dimens.IconTileSize).clip(RoundedCornerShape(Dimens.IconTileCorner)).background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { OwnTVIcon(icon = icon, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                if (desc != null) Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (chip != null) {
                val bg = if (primaryChip) colors.primaryContainer else colors.secondaryContainer
                val on = if (primaryChip) colors.onPrimaryContainer else colors.onSecondaryContainer
                Text(
                    chip, style = MaterialTheme.typography.labelMedium, color = on, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (chevron) OwnTVIcon(OwnTVIcon.CHEVRON, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** A single-select list dialog (value → label). */
@Composable
internal fun PickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    val selIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(460.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(options, key = { _, o -> o.first }) { index, (value, label) ->
                    val isSel = value == selected
                    FocusableSurface(
                        onClick = { onSelect(value) },
                        modifier = if (index == selIndex) Modifier.fillMaxWidth().focusRequester(fr) else Modifier.fillMaxWidth(),
                        selected = isSel,
                        shape = RoundedCornerShape(12.dp),
                        selectedContainerColor = colors.primaryContainer,
                        contentAlignment = Alignment.CenterStart,
                    ) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.titleMedium, color = if (isSel) colors.onPrimaryContainer else colors.onSurface, modifier = Modifier.weight(1f))
                            if (isSel) OwnTVIcon(OwnTVIcon.STAR, tint = colors.onPrimaryContainer, filled = true, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
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
    format: (Int) -> String,
    onSet: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(460.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StepBtn("–", enabled = value > min) { onSet((value - step).coerceAtLeast(min)) }
                Text(format(value), style = MaterialTheme.typography.headlineMedium, color = colors.primary, modifier = Modifier.width(140.dp), textAlign = TextAlign.Center)
                StepBtn("+", enabled = value < max, modifier = Modifier.focusRequester(fr)) { onSet((value + step).coerceAtMost(max)) }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Reset", onClick = onReset, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Done", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun StepBtn(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(64.dp),
        shape = RoundedCornerShape(18.dp),
        contentAlignment = Alignment.Center,
    ) { _ -> Text(label, style = MaterialTheme.typography.headlineMedium, color = if (enabled) colors.onSurface else colors.outline) }
}
