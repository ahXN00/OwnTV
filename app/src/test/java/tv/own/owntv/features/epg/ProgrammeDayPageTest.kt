package tv.own.owntv.features.epg

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.parser.EpgDetails

class ProgrammeDayPageTest {

    private fun prog(start: Long, stop: Long, id: Long = 0) =
        EpgProgrammeEntity(id = id, sourceId = 1, epgChannelId = "c", startMs = start, stopMs = stop, title = "P$start")

    private val day = listOf(prog(0, 100), prog(100, 200), prog(200, 300), prog(300, 400))

    @Test
    fun `facts in order, categories are tags not facts`() {
        val d = EpgDetails(categories = "Drama", year = 2023, rating = "FSK 12", lengthMin = 95, episode = "S2 E5")
        assertEquals(listOf("S2 E5", "2023", "FSK 12", "95 min"), programmeFacts(d, "95 min"))
        assertEquals(emptyList<String>(), programmeFacts(EpgDetails.NONE, null))
    }

    @Test
    fun `past, on now and later against the clock`() {
        assertEquals(ProgrammeTime.PAST, programmeTime(prog(0, 100), now = 100))
        assertEquals(ProgrammeTime.NOW, programmeTime(prog(100, 200), now = 100))
        assertEquals(ProgrammeTime.NOW, programmeTime(prog(100, 200), now = 199))
        assertEquals(ProgrammeTime.LATER, programmeTime(prog(200, 300), now = 199))
    }

    @Test
    fun `opens on the programme asked for, by its start`() {
        assertEquals(3, openingIndex(day, wantedStartMs = 300, now = 150))
    }

    @Test
    fun `opens on the one on now when nothing is asked, or the asked one is gone`() {
        assertEquals(1, openingIndex(day, wantedStartMs = null, now = 150))
        assertEquals(1, openingIndex(day, wantedStartMs = 999, now = 150))
    }

    @Test
    fun `another day opens on the next to start, else the first`() {
        val gap = listOf(prog(0, 100), prog(300, 400))
        assertEquals(1, openingIndex(gap, wantedStartMs = null, now = 150))
        assertEquals(0, openingIndex(day, wantedStartMs = null, now = 10_000))
        assertEquals(0, openingIndex(emptyList(), wantedStartMs = null, now = 0))
    }

    @Test
    fun `buttons by where the programme sits`() {
        assertEquals(listOf(PageAction.WATCH_FROM_START, PageAction.RECORD_FROM_CATCHUP), pageActions(ProgrammeTime.PAST))
        assertEquals(listOf(PageAction.WATCH, PageAction.WATCH_FROM_START, PageAction.RECORD, PageAction.RECORD_SERIES), pageActions(ProgrammeTime.NOW))
        assertEquals(listOf(PageAction.REMIND, PageAction.RECORD, PageAction.RECORD_SERIES), pageActions(ProgrammeTime.LATER))
    }
}
