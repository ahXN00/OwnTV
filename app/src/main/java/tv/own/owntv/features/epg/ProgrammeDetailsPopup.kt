package tv.own.owntv.features.epg

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.parser.EpgDetails
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageText

/** The facts line of Program details: "S2 E5 · 2023 · FSK 12 · 95 min" — only what the feed carries. */
internal fun programmeFacts(d: EpgDetails, lengthText: String?): List<String> =
    listOfNotNull(d.episode, d.year?.toString(), d.rating, lengthText)

/**
 * Program details: everything the guide holds about one programme, for reading — when it is on, on
 * which channel, the whole title, the facts line, its categories as tags and the full description.
 * Opened from the programme menu (TV Guide and Live TV's schedule) and by the remote's Info key on a
 * guide cell. [load] fetches the stored programme with its synopsis and details, which the guide grid
 * leaves out; a row only the provider gave has none and shows [programme] as it is.
 *
 * Reading only, as on the film details page: ▲▼ scroll, OK or Back closes.
 */
@Composable
internal fun ProgrammeDetailsPopup(
    programme: EpgProgrammeEntity,
    channelName: String,
    load: (suspend () -> EpgProgrammeEntity?)?,
    onDismiss: () -> Unit,
) {
    val full by produceState<EpgProgrammeEntity?>(null, programme.id) { value = load?.invoke() }
    // The times stay the row's own: those carry the channel's EPG offset, the stored copy does not.
    val p = full?.copy(startMs = programme.startMs, stopMs = programme.stopMs) ?: programme
    val now = remember { System.currentTimeMillis() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
    val facts = programmeFacts(p.details, p.details.lengthMin?.let { stringResource(R.string.player_duration_minutes, it) })
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = null,
        eyebrow = programmeEyebrow(p, channelName, now),
        eyebrowAccent = true,
        eyebrowIcon = OwnTVIcon.CLOCK,
        width = 1100.mpx,
        scroll = false,
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    // Taken here, on release, as on the film details page: the popup's own focus node
                    // would otherwise spend Back as a focus exit, which the trap cancels.
                    when (e.key) {
                        Key.Back, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (e.type == KeyEventType.KeyUp) onDismiss()
                            true
                        }
                        Key.DirectionDown -> { if (e.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(220f) }; true }
                        Key.DirectionUp -> { if (e.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(-220f) }; true }
                        else -> false
                    }
                }
                .focusable()
                .verticalScroll(scroll),
        ) {
            Text(
                p.title, style = stageText(38, 800, (-0.5).mpxSp).copy(textDirection = TextDirection.Content), color = StageColors.Text,
                modifier = Modifier.padding(top = 6.mpx),
            )
            if (facts.isNotEmpty()) {
                Text(facts.joinToString(" · "), style = stageText(20, 600), color = StageColors.ItemText, modifier = Modifier.padding(top = 10.mpx))
            }
            val tags = p.details.categoryList
            if (tags.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 14.mpx), horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                    tags.forEach { tv.own.owntv.ui.stage.StageTag(it) }
                }
            }
            p.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, style = stageText(21, 400).copy(lineHeight = (21 * 1.55f).mpxSp, textDirection = TextDirection.Content),
                    color = StageColors.ItemText, modifier = Modifier.padding(top = 20.mpx),
                )
            }
            Text(stringResource(R.string.content_media_press_back), style = stageText(16, 600), color = StageColors.Dim, modifier = Modifier.padding(top = 24.mpx))
        }
    }
}
