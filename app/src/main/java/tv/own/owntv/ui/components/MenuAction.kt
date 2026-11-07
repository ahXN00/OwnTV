package tv.own.owntv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.menu.MenuLayout
import tv.own.owntv.core.menu.arrange
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.core.settings.SettingsRepository

/**
 * One action in a long-press content menu — Live channel, movie, series or episode.
 *
 * The four menus used to be hand-written columns of buttons wrapped in `if`s, which meant their order
 * was the source order and nothing could be rearranged. They now build a list of these instead, and
 * render it in list order, so a later release can sort that list without touching any menu.
 *
 * [key] is a stable identifier and is deliberately NOT derived from the label: labels are translated,
 * and several of them toggle between two texts ("Add to favourites" / "Remove from favourites").
 *
 * [group] only spaces the menu out: a divider (or a gap, depending on the menu) is drawn wherever the
 * group changes between two neighbouring actions. An empty group simply produces no divider.
 */
data class MenuAction(
    val key: String,
    val label: String,
    val icon: OwnTVIcon? = null,
    val destructive: Boolean = false,
    val group: Int = 0,
    val onClick: () -> Unit,
)

/** A menu's actions as the popup draws them, and whether to draw its group headings. */
data class ArrangedMenu(val actions: List<MenuAction>, val headings: Boolean)

/**
 * [actions] in the order the user arranged [menu] into, without the ones they hid. The menus read the
 * setting themselves rather than have it threaded down through four screens and their view models.
 *
 * The rule itself is core's, so both apps arrange the same saved keys the same way. Group headings
 * are drawn only in the shipped order: a heading names a run of related actions, and a rearranged
 * menu has none.
 */
@Composable
fun arranged(menu: ContentMenu, actions: List<MenuAction>): ArrangedMenu {
    val settings: SettingsRepository = koinInject()
    val layout by remember(menu) { settings.menuLayout(menu) }.collectAsStateWithLifecycle(MenuLayout())
    val gate: tv.own.owntv.features.settings.lock.MenuChangeGate = koinInject()
    // The organising actions ask for the Settings PIN first when its long-press switch is on.
    val guarded = actions.map { action ->
        if (action.key in tv.own.owntv.core.categorylock.GUARDED_MENU_ACTIONS) action.copy(onClick = { gate.run(action.onClick) }) else action
    }
    return ArrangedMenu(arrange(menu, guarded, layout) { it.key }, layout.isShippedOrder(menu))
}
