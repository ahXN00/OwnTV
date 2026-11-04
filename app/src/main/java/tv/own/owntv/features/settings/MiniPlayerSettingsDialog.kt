package tv.own.owntv.features.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.size
import tv.own.owntv.ui.theme.mpx
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import tv.own.owntv.core.player.MiniPlayerPosition
import tv.own.owntv.core.player.MiniPlayerSize
import tv.own.owntv.player.labelRes
import tv.own.owntv.ui.components.trapAllFocusExit

/**
 * Settings › Watching & recording › Mini-player: how big the docked mini-player is, and which corner it sits
 * in. Both are also adjustable on the fly from the mini-player's own resize / move buttons.
 *
 * Two settings, so this is **one panel** rather than a screen holding two rows that each opened another
 * popup. That chain was three levels deep for a size and a corner, and every level was another Back
 * press and another focus restore to get wrong.
 *
 * The position choices are laid out as the screen itself — top row above bottom row, left/centre/right
 * across — so the grid *is* the preview: the option you focus sits where the mini-player will.
 */
@Composable
fun MiniPlayerSettingsDialog(onDismiss: () -> Unit) {
    val vm: SettingsViewModel = koinViewModel()
    val sizePct by vm.miniPlayerSizePct.collectAsStateWithLifecycle()
    val position by vm.miniPlayerPosition.collectAsStateWithLifecycle()
    val form: @Composable () -> Unit = {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); runCatching { first.requestFocus() } }
        val label = tv.own.owntv.ui.theme.stageText(14, 800, androidx.compose.ui.unit.TextUnit(0.12f, androidx.compose.ui.unit.TextUnitType.Em))
        Column(Modifier.trapAllFocusExit().focusGroup()) {
            Text(stringResource(R.string.settings_size).uppercase(), style = label, color = tv.own.owntv.ui.theme.StageColors.Dim, modifier = Modifier.padding(start = 8.mpx, bottom = 6.mpx))
            // ◀ ▶ step the size, clamped at the ends.
            tv.own.owntv.ui.stage.StageSurface(
                onClick = {},
                radius = 18.mpx,
                focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
                modifier = Modifier.fillMaxWidth().height(72.mpx).focusRequester(first).onPreviewKeyEvent { e ->
                    val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                    if (d != 0 && e.type == KeyEventType.KeyDown) vm.setMiniPlayerSize((sizePct + d * MiniPlayerSize.STEP).coerceIn(MiniPlayerSize.MIN, MiniPlayerSize.MAX))
                    d != 0
                },
                contentAlignment = Alignment.Center,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.mpx), verticalAlignment = Alignment.CenterVertically) {
                    tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(24.mpx).graphicsLayer { rotationZ = 180f })
                    Text(stringResource(R.string.common_percent, sizePct), style = tv.own.owntv.ui.theme.stageText(30, 800), color = tv.own.owntv.ui.theme.stageAccent.accent, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.CHEVRON, tv.own.owntv.ui.theme.StageColors.Muted, Modifier.size(24.mpx))
                }
            }
            // The position laid out as the screen it describes: the focused spot is where the player sits.
            Text(stringResource(R.string.settings_position).uppercase(), style = label, color = tv.own.owntv.ui.theme.StageColors.Dim, modifier = Modifier.padding(start = 8.mpx, top = 18.mpx, bottom = 2.mpx))
            listOf(MiniPlayerPosition.entries.take(3), MiniPlayerPosition.entries.drop(3)).forEach { row ->
                tv.own.owntv.ui.stage.StageMenuChoice(
                    options = row.map { stringResource(it.labelRes) },
                    selected = row.indexOf(position),
                    onSelect = { vm.setMiniPlayerPosition(row[it]) },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx, Alignment.End)) {
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_reset), onClick = {
                    vm.setMiniPlayerSize(MiniPlayerSize.DEFAULT)
                    vm.setMiniPlayerPosition(MiniPlayerPosition.DEFAULT)
                }, height = 52.mpx, textSize = 18)
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = onDismiss, height = 52.mpx, textSize = 18, tinted = true)
            }
        }
    }
    // Two settings: in the page's panel, a Stage popup elsewhere.
    if (panelEditor(onDismiss) { form() }) return
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = stringResource(R.string.settings_mini_player), body = stringResource(R.string.settings_mini_player_description)) { form() }
}
