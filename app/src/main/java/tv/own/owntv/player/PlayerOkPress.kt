package tv.own.owntv.player

import androidx.compose.ui.input.key.Key
import tv.own.owntv.core.settings.PlayerHoldOkAction
import tv.own.owntv.core.settings.PlayerRemoteOptions
import tv.own.owntv.core.settings.PlayerShortOkAction

/** How long OK must be held, from its first KeyDown, to count as held — the player shortcuts' threshold. */
const val PLAYER_OK_HOLD_MS = 600L

/** What an OK press in the player ends up doing. */
enum class PlayerOkOutcome { SHOW_CONTROLS, CHANNEL_LIST, GUIDE, PROGRAMME_INFO }

/** What the player can open right now: the channel list where it is offered, the guide and banner on live only. */
data class PlayerOkTargets(val channelList: Boolean, val guide: Boolean, val programmeInfo: Boolean)

/** The resolved meaning of a press: [hold] is null when holding OK does nothing different. */
data class PlayerOkPlan(val hold: PlayerOkOutcome?, val short: PlayerOkOutcome) {
    fun outcome(heldMs: Long): PlayerOkOutcome = if (hold != null && heldMs >= PLAYER_OK_HOLD_MS) hold else short
}

/**
 * The plan for OK under [options], or null when OK keeps its ordinary path — Hold OK off and a short
 * press showing the controls, or a choice that is not available here (a film has no channel list).
 * Null means the press is not intercepted at all, so it waits for nothing.
 */
fun playerOkPlan(options: PlayerRemoteOptions, targets: PlayerOkTargets): PlayerOkPlan? {
    val hold = when (options.holdOk) {
        PlayerHoldOkAction.OFF -> null
        PlayerHoldOkAction.CHANNEL_LIST -> PlayerOkOutcome.CHANNEL_LIST.takeIf { targets.channelList }
        PlayerHoldOkAction.GUIDE -> PlayerOkOutcome.GUIDE.takeIf { targets.guide }
        PlayerHoldOkAction.PROGRAMME_INFO -> PlayerOkOutcome.PROGRAMME_INFO.takeIf { targets.programmeInfo }
    }
    val short = when (options.shortOk) {
        PlayerShortOkAction.SHOW_CONTROLS -> null
        PlayerShortOkAction.CHANNEL_LIST -> PlayerOkOutcome.CHANNEL_LIST.takeIf { targets.channelList }
        PlayerShortOkAction.GUIDE -> PlayerOkOutcome.GUIDE.takeIf { targets.guide }
    } ?: PlayerOkOutcome.SHOW_CONTROLS
    if (hold == null && short == PlayerOkOutcome.SHOW_CONTROLS) return null
    return PlayerOkPlan(hold, short)
}

/** What the player does with one OK key event. */
sealed interface PlayerOkResult {
    /** Not ours: the event goes on exactly as before. */
    data object Pass : PlayerOkResult
    /** Part of a press being timed: swallowed, nothing happens yet. */
    data object Swallow : PlayerOkResult
    /** The press ended: do [outcome] and swallow the event. */
    data class Fire(val outcome: PlayerOkOutcome) : PlayerOkResult
}

fun isPlayerOkKey(key: Key): Boolean = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/**
 * Times one OK press and decides it on release, like the player's configured shortcuts: the whole
 * press stays inside the player, so a held OK never also fires the short action and the release
 * never lands on a list or guide opened by it. The plan is fixed at the first KeyDown.
 */
class PlayerOkPressTracker {
    private var downAt = NOT_PRESSED
    private var plan: PlayerOkPlan? = null

    fun onKey(down: Boolean, repeatCount: Int, eventTimeMs: Long, currentPlan: PlayerOkPlan?): PlayerOkResult {
        if (down && repeatCount == 0) {
            plan = currentPlan
            downAt = if (currentPlan != null) eventTimeMs else NOT_PRESSED
            return if (currentPlan != null) PlayerOkResult.Swallow else PlayerOkResult.Pass
        }
        val pressPlan = plan
        if (downAt == NOT_PRESSED || pressPlan == null) return PlayerOkResult.Pass
        if (down) return PlayerOkResult.Swallow
        val heldMs = eventTimeMs - downAt
        downAt = NOT_PRESSED
        plan = null
        return PlayerOkResult.Fire(pressPlan.outcome(heldMs))
    }

    private companion object {
        const val NOT_PRESSED = -1L
    }
}
