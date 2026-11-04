package tv.own.owntv.features.shell.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.player.SurroundMode

@Composable
internal fun surroundModeLabel(mode: SurroundMode): String = stringResource(
    when (mode) {
        SurroundMode.AUTO -> R.string.settings_auto
        SurroundMode.STEREO -> R.string.settings_surround_stereo
        SurroundMode.SURROUND -> R.string.settings_surround_sound
    },
)
