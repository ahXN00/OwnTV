package tv.own.owntv.features.shell.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import tv.own.owntv.R
import tv.own.owntv.features.autostart.AutoStart
import tv.own.owntv.features.autostart.AutoStartOptions
import tv.own.owntv.features.autostart.AutoStartPolicy
import tv.own.owntv.features.autostart.AutoStartStore
import tv.own.owntv.ui.components.OwnTVIcon

/** Settings › App › Auto start on boot and on wake-up: both switches, and whether they can work yet. */
@Stable
internal class AutoStartSettingsState(private val context: Context) {

    var options by mutableStateOf(AutoStartStore(context).read())
        private set

    var canStartFromBackground by mutableStateOf(AutoStart.canStartFromBackground(context))
        private set

    val wakeOffered: Boolean = AutoStart.isTelevision(context)

    fun toggleBoot() = change(options.copy(onBoot = !options.onBoot))

    fun toggleWake() = change(options.copy(onWake = !options.onWake))

    /** Back from Android's permission screen: a new grant starts the wake listener, a revoked one stops it. */
    fun refresh() {
        canStartFromBackground = AutoStart.canStartFromBackground(context)
        AutoStart.syncWakeListener(context, options, canStartFromBackground)
    }

    private fun change(after: AutoStartOptions) {
        val before = options
        options = after
        AutoStart.update(context, before, after)
    }
}

/** One state for the root rows and their search entries, so a switch flipped in search shows on the row. */
@Composable
internal fun rememberAutoStartSettingsState(): AutoStartSettingsState {
    val context = LocalContext.current
    val state = remember(context) { AutoStartSettingsState(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.refresh() }
    return state
}

/** The rows that follow App startup on the Settings root: on boot, and on wake-up on a television. */
@Composable
internal fun autoStartRootRows(state: AutoStartSettingsState): List<RootRow> = autoStartSwitches(state).map { s ->
    RootRow(
        s.key, TileTone.SECONDARY, OwnTVIcon.POWER,
        title = stringResource(s.title), desc = stringResource(s.desc),
        chip = stringResource(if (s.on) R.string.common_on else R.string.common_off),
        chipTone = if (s.on) TileTone.PRIMARY else TileTone.SECONDARY,
        onClick = s.toggle,
    )
}

/** The same switches in Settings search, flipped in place like the row. */
@Composable
internal fun autoStartSearchEntries(state: AutoStartSettingsState): List<SettingsSearchEntry> {
    val group = stringResource(R.string.settings_group_app)
    val keywords = stringResource(R.string.settings_search_keywords_auto_start)
    return autoStartSwitches(state).map { s ->
        SettingsSearchEntry(
            group, stringResource(s.title), keywords, OwnTVIcon.POWER, TileTone.SECONDARY,
            chip = stringResource(if (s.on) R.string.common_on else R.string.common_off),
            chipTone = if (s.on) TileTone.PRIMARY else TileTone.SECONDARY,
            showChevron = false,
            onClick = s.toggle,
        )
    }
}

private class AutoStartSwitch(
    val key: String,
    @StringRes val title: Int,
    @StringRes val desc: Int,
    val on: Boolean,
    val toggle: () -> Unit,
)

/** A switch that is on but cannot work yet says what it needs in place of its description. */
private fun autoStartSwitches(state: AutoStartSettingsState): List<AutoStartSwitch> {
    val canStart = state.canStartFromBackground
    fun desc(on: Boolean, @StringRes normal: Int) =
        if (AutoStartPolicy.needsOverlayNotice(on, canStart)) R.string.settings_auto_start_needs_overlay else normal
    val boot = state.options.onBoot
    val wake = state.options.onWake
    return listOfNotNull(
        AutoStartSwitch("auto_start_boot", R.string.settings_auto_start_boot, desc(boot, R.string.settings_auto_start_boot_description), boot, state::toggleBoot),
        AutoStartSwitch("auto_start_wake", R.string.settings_auto_start_wake, desc(wake, R.string.settings_auto_start_wake_description), wake, state::toggleWake)
            .takeIf { state.wakeOffered },
    )
}
