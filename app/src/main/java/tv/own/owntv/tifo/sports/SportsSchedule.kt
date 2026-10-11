package tv.own.owntv.tifo.sports

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which day a game belongs to in the Sports schedule, in the TV's own time zone. The league tabs
 * show one day at a time (today by default, plus whatever is live); My teams shows every day.
 */
internal object SportsSchedule {

    /** How far ahead the day picker goes, and how far back. */
    const val DAYS_AHEAD = 13L
    const val DAYS_BACK = 1L

    fun dayOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** The days a game or tournament is played on: one day, or a tournament's whole run. */
    private fun days(event: SportsEvent, zone: ZoneId): ClosedRange<LocalDate> {
        val first = dayOf(event.startMs, zone)
        val last = event.endMs?.let { dayOf(it, zone) }?.takeIf { !it.isBefore(first) } ?: first
        return first..last
    }

    /** [event] is on [day]'s schedule: played that day, or live while [day] is [today]. */
    fun isOn(event: SportsEvent, day: LocalDate, today: LocalDate, zone: ZoneId): Boolean =
        (day == today && event.state == EventState.LIVE) || day in days(event, zone)

    /** The first day after [after] with a game among [events], if the schedule knows one. */
    fun nextDay(events: List<SportsEvent>, after: LocalDate, zone: ZoneId): LocalDate? {
        val from = after.plusDays(1)
        return events.mapNotNull { e ->
            val run = days(e, zone)
            if (run.endInclusive.isBefore(from)) null else maxOf(run.start, from)
        }.minOrNull()
    }
}
