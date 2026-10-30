package tv.own.owntv.features.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.ui.components.OwnTVIcon

/** What to sync, one row per section: ◀ ▶ (or OK) step Now / Later / Off — On / Off while editing. */
@Composable
internal fun SyncScopeStageRow(
    icon: OwnTVIcon,
    label: String,
    desc: String,
    value: SyncScopeChoice,
    editing: Boolean,
    help: tv.own.owntv.features.settings.SettingHelp,
    modifier: Modifier,
    onChange: (SyncScopeChoice) -> Unit,
) {
    val options = if (editing) listOf(SyncScopeChoice.Now, SyncScopeChoice.Off) else listOf(SyncScopeChoice.Now, SyncScopeChoice.Later, SyncScopeChoice.Off)
    val labels = options.map {
        when (it) {
            SyncScopeChoice.Now -> stringResource(if (editing) R.string.setup_on else R.string.setup_now)
            SyncScopeChoice.Later -> stringResource(R.string.setup_later)
            SyncScopeChoice.Off -> stringResource(R.string.setup_off)
        }
    }
    val at = options.indexOf(value).coerceAtLeast(0)
    StageSettingRow(
        icon = icon,
        title = label,
        desc = desc,
        value = SettingValue.Segmented(labels, at),
        onClick = { onChange(options[(at + 1) % options.size]) },
        onStep = { step -> onChange(options[(at + step).coerceIn(0, options.lastIndex)]) },
        modifier = modifier,
        help = help,
    )
}
