package tv.own.owntv.tifo.sports

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsScheduleTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 10, 11)

    private fun at(day: LocalDate, hour: Int) = ZonedDateTime.of(day.atTime(hour, 0), paris).toInstant().toEpochMilli()

    private fun event(id: String, start: Long, state: EventState = EventState.UPCOMING, end: Long? = null, c: Competition = Competition.NFL) =
        SportsEvent(id, c, "", start, state, "", null, null, emptyList(), end)

    @Test
    fun todayListsTodaysGamesAndWhateverIsLive() {
        val lateLastNight = event("late", at(today.minusDays(1), 23), EventState.LIVE)
        val tonight = event("tonight", at(today, 21))
        val tomorrow = event("tomorrow", at(today.plusDays(1), 2))
        val state = SportsUiState(loading = false, filter = Competition.NFL, events = listOf(lateLastNight, tonight, tomorrow))
        assertEquals(listOf("late", "tonight"), state.visibleEvents(emptySet(), today, paris).map { it.id })
        val picked = state.copy(day = today.plusDays(1))
        assertEquals(listOf("tomorrow"), picked.visibleEvents(emptySet(), today, paris).map { it.id })
    }

    @Test
    fun allOpensOnLiveWhenAGameIsLiveAndSplitsByState() {
        val live = event("live", at(today, 20), EventState.LIVE)
        val ended = event("ended", at(today, 13), EventState.FINISHED)
        val later = event("later", at(today, 22))
        val all = SportsUiState(loading = false, events = listOf(live, ended, later))
        assertEquals(listOf("live"), all.visibleEvents(emptySet(), today, paris).map { it.id })
        assertEquals(listOf("ended"), all.copy(stage = EventState.FINISHED).visibleEvents(emptySet(), today, paris).map { it.id })
        val quiet = all.copy(events = listOf(ended, later))
        assertEquals(listOf("later"), quiet.visibleEvents(emptySet(), today, paris).map { it.id })
    }

    @Test
    fun myTeamsListsEveryDay() {
        val team = SportsTeam("Jets", "Jets", emptyList(), null, null, id = "20")
        val other = SportsTeam("Browns", "Browns", emptyList(), null, null, id = "5")
        val later = SportsEvent("g", Competition.NFL, "", at(today.plusDays(7), 19), EventState.UPCOMING, "", team, other, emptyList())
        val state = SportsUiState(loading = false, myTeams = true, events = listOf(later))
        assertEquals(listOf("g"), state.visibleEvents(setOf(SportsFavorites.keyOf(Competition.NFL, "20")), today, paris).map { it.id })
    }

    @Test
    fun aTournamentIsOnEveryDayOfItsRun() {
        val masters = event("m", at(today.minusDays(3), 10), end = at(today.plusDays(4), 18), c = Competition.ATP)
        assertTrue(SportsSchedule.isOn(masters, today.plusDays(2), today, paris))
        assertFalse(SportsSchedule.isOn(masters, today.plusDays(5), today, paris))
    }

    @Test
    fun nextDayIsTheFirstDayWithAGame() {
        val events = listOf(event("a", at(today.plusDays(4), 20)), event("b", at(today.plusDays(2), 13)), event("c", at(today, 9)))
        assertEquals(today.plusDays(2), SportsSchedule.nextDay(events, today, paris))
        assertNull(SportsSchedule.nextDay(events, today.plusDays(4), paris))
    }
}
