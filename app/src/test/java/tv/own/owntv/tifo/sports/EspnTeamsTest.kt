package tv.own.owntv.tifo.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnTeamsTest {

    private val nflTeams = """
        {"sports":[{"leagues":[{"teams":[
          {"team":{"id":"24","displayName":"Los Angeles Chargers","shortDisplayName":"Chargers","logos":[{"href":"https://a.espncdn.com/lac.png"}]}},
          {"team":{"id":"7","displayName":"Denver Broncos","shortDisplayName":"Broncos","logos":[]}},
          {"team":{"id":"","displayName":"No id"}}
        ]}]}]}
    """.trimIndent()

    @Test
    fun readsTheLeaguesTeamsByName() {
        val teams = parseTeams(nflTeams)
        assertEquals(listOf("Denver Broncos", "Los Angeles Chargers"), teams.map { it.name })
        assertEquals("7", teams[0].id)
        assertEquals(null, teams[0].logoUrl)
        assertEquals("https://a.espncdn.com/lac.png", teams[1].logoUrl)
    }

    @Test
    fun aFavoriteIsALeagueAndATeamId() {
        val broncos = SportsTeam("Denver Broncos", "Broncos", emptyList(), null, null, id = "7")
        val keys = setOf(SportsFavorites.keyOf(Competition.NFL, "7"))
        assertTrue(SportsFavorites.isFavorite(keys, Competition.NFL, broncos))
        assertFalse(SportsFavorites.isFavorite(keys, Competition.NBA, broncos))
        assertFalse(SportsFavorites.isFavorite(keys, Competition.NFL, broncos.copy(id = "")))
    }

    @Test
    fun myTeamsListsOnlyTheirGames() {
        val broncos = SportsTeam("Denver Broncos", "Broncos", emptyList(), null, null, id = "7")
        val chargers = SportsTeam("Los Angeles Chargers", "Chargers", emptyList(), null, null, id = "24")
        val bills = SportsTeam("Buffalo Bills", "Bills", emptyList(), null, null, id = "2")
        val rams = SportsTeam("Los Angeles Rams", "Rams", emptyList(), null, null, id = "14")
        fun game(id: String, home: SportsTeam, away: SportsTeam) = SportsEvent(id, Competition.NFL, "", 0L, EventState.UPCOMING, "", home, away, emptyList())
        val state = SportsUiState(events = listOf(game("1", chargers, broncos), game("2", rams, bills)), myTeams = true)
        val keys = setOf(SportsFavorites.keyOf(Competition.NFL, "7"))
        assertEquals(listOf("1"), state.visibleEvents(keys).map { it.id })
    }
}
