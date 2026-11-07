package tv.own.owntv.player

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.PlayerHoldOkAction
import tv.own.owntv.core.settings.PlayerRemoteOptions
import tv.own.owntv.core.settings.PlayerShortOkAction

class PlayerOkPressTest {
    private val live = PlayerOkTargets(channelList = true, guide = true, programmeInfo = true)
    private val vod = PlayerOkTargets(channelList = false, guide = false, programmeInfo = false)

    @Test
    fun defaultsLeaveOkAloneSoItWaitsForNothing() {
        assertNull(playerOkPlan(PlayerRemoteOptions(), live))
        assertNull(playerOkPlan(PlayerRemoteOptions(), vod))
    }

    @Test
    fun choicesTheContentCannotOfferFallBackToTodaysOk() {
        val options = PlayerRemoteOptions(holdOk = PlayerHoldOkAction.GUIDE, shortOk = PlayerShortOkAction.CHANNEL_LIST)
        assertNull(playerOkPlan(options, vod))
        assertEquals(PlayerOkPlan(PlayerOkOutcome.GUIDE, PlayerOkOutcome.CHANNEL_LIST), playerOkPlan(options, live))
        val noList = live.copy(channelList = false)
        assertEquals(PlayerOkPlan(PlayerOkOutcome.GUIDE, PlayerOkOutcome.SHOW_CONTROLS), playerOkPlan(options, noList))
    }

    @Test
    fun everyHoldChoiceMapsToItsOutcome() {
        val expected = mapOf(
            PlayerHoldOkAction.CHANNEL_LIST to PlayerOkOutcome.CHANNEL_LIST,
            PlayerHoldOkAction.GUIDE to PlayerOkOutcome.GUIDE,
            PlayerHoldOkAction.PROGRAMME_INFO to PlayerOkOutcome.PROGRAMME_INFO,
        )
        expected.forEach { (action, outcome) ->
            assertEquals(PlayerOkPlan(outcome, PlayerOkOutcome.SHOW_CONTROLS), playerOkPlan(PlayerRemoteOptions(holdOk = action), live))
        }
    }

    @Test
    fun aPressIsShortUntilTheHoldThreshold() {
        val plan = PlayerOkPlan(PlayerOkOutcome.GUIDE, PlayerOkOutcome.SHOW_CONTROLS)
        assertEquals(PlayerOkOutcome.SHOW_CONTROLS, plan.outcome(PLAYER_OK_HOLD_MS - 1))
        assertEquals(PlayerOkOutcome.GUIDE, plan.outcome(PLAYER_OK_HOLD_MS))
        assertEquals(PlayerOkOutcome.CHANNEL_LIST, PlayerOkPlan(null, PlayerOkOutcome.CHANNEL_LIST).outcome(5_000))
    }

    @Test
    fun withNoPlanEveryEventPassesThrough() {
        val tracker = PlayerOkPressTracker()
        assertEquals(PlayerOkResult.Pass, tracker.onKey(down = true, repeatCount = 0, eventTimeMs = 0, currentPlan = null))
        assertEquals(PlayerOkResult.Pass, tracker.onKey(down = true, repeatCount = 1, eventTimeMs = 50, currentPlan = null))
        assertEquals(PlayerOkResult.Pass, tracker.onKey(down = false, repeatCount = 0, eventTimeMs = 100, currentPlan = null))
    }

    @Test
    fun aHeldOkFiresOnlyTheHoldActionOnRelease() {
        val tracker = PlayerOkPressTracker()
        val plan = PlayerOkPlan(PlayerOkOutcome.GUIDE, PlayerOkOutcome.CHANNEL_LIST)
        assertEquals(PlayerOkResult.Swallow, tracker.onKey(true, 0, 1_000, plan))
        assertEquals(PlayerOkResult.Swallow, tracker.onKey(true, 1, 1_400, plan))
        assertEquals(PlayerOkResult.Swallow, tracker.onKey(true, 2, 1_700, plan))
        assertEquals(PlayerOkResult.Fire(PlayerOkOutcome.GUIDE), tracker.onKey(false, 0, 1_800, plan))
        // The press is over: a stray release afterwards is not ours.
        assertEquals(PlayerOkResult.Pass, tracker.onKey(false, 0, 1_900, plan))
    }

    @Test
    fun aQuickPressFiresTheShortAction() {
        val tracker = PlayerOkPressTracker()
        val plan = PlayerOkPlan(PlayerOkOutcome.GUIDE, PlayerOkOutcome.SHOW_CONTROLS)
        assertEquals(PlayerOkResult.Swallow, tracker.onKey(true, 0, 0, plan))
        assertEquals(PlayerOkResult.Fire(PlayerOkOutcome.SHOW_CONTROLS), tracker.onKey(false, 0, 120, plan))
    }

    @Test
    fun thePlanIsFixedWhenThePressStarts() {
        val tracker = PlayerOkPressTracker()
        val plan = PlayerOkPlan(PlayerOkOutcome.CHANNEL_LIST, PlayerOkOutcome.SHOW_CONTROLS)
        tracker.onKey(true, 0, 0, plan)
        // Controls came up mid-press (no plan now): the release still belongs to the press that began.
        assertEquals(PlayerOkResult.Fire(PlayerOkOutcome.CHANNEL_LIST), tracker.onKey(false, 0, 700, null))
    }

    @Test
    fun aReleaseFromAPressThatStartedElsewhereIsNotOurs() {
        val tracker = PlayerOkPressTracker()
        val plan = PlayerOkPlan(PlayerOkOutcome.GUIDE, PlayerOkOutcome.SHOW_CONTROLS)
        assertEquals(PlayerOkResult.Pass, tracker.onKey(true, 3, 0, plan))
        assertEquals(PlayerOkResult.Pass, tracker.onKey(false, 0, 100, plan))
    }

    @Test
    fun okKeys() {
        assertTrue(isPlayerOkKey(Key.DirectionCenter))
        assertTrue(isPlayerOkKey(Key.Enter))
        assertTrue(isPlayerOkKey(Key.NumPadEnter))
        assertFalse(isPlayerOkKey(Key.DirectionUp))
    }
}
