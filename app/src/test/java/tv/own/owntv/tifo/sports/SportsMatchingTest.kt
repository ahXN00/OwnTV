package tv.own.owntv.tifo.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsMatchingTest {

    private val psgLyon = """
        {"events":[{"id":"7001","date":"2026-10-11T19:00Z","name":"Lyon at Paris Saint-Germain",
          "status":{"type":{"state":"in","shortDetail":"62'"}},
          "competitions":[{"date":"2026-10-11T19:00Z",
            "competitors":[
              {"homeAway":"home","score":"2","team":{"displayName":"Paris Saint-Germain","shortDisplayName":"PSG","name":"Paris Saint-Germain","abbreviation":"PSG","logo":"https://a.espncdn.com/psg.png"}},
              {"homeAway":"away","score":"1","team":{"displayName":"Lyon","shortDisplayName":"Lyon","name":"Lyon","abbreviation":"LYON"}}],
            "broadcasts":[{"names":["beIN SPORTS USA"]}]}]}]}
    """.trimIndent()

    private val singapore = """
        {"events":[{"id":"600","date":"2026-10-02T09:30Z","name":"Singapore Grand Prix",
          "status":{"type":{"state":"pre","shortDetail":"Sun"}},
          "competitions":[
            {"date":"2026-10-02T09:30Z","type":{"abbreviation":"FP1"},"status":{"type":{"state":"post"}}},
            {"date":"2026-10-04T12:00Z","type":{"abbreviation":"Race"},"status":{"type":{"state":"pre","shortDetail":"Sun 2:00 PM"}}}]}]}
    """.trimIndent()

    @Test
    fun parsesATeamGame() {
        val event = parseScoreboard(Competition.LIGUE_1, psgLyon).single()
        assertEquals(EventState.LIVE, event.state)
        assertEquals("62'", event.statusDetail)
        assertEquals("PSG", event.home?.shortName)
        assertEquals("2", event.home?.score)
        assertEquals("Lyon", event.away?.name)
        assertEquals(listOf("beIN SPORTS USA"), event.broadcasts)
        assertEquals(parseEspnDate("2026-10-11T19:00:00Z"), event.startMs)
    }

    @Test
    fun aRaceIsTimedByItsRaceSession() {
        val event = parseScoreboard(Competition.FORMULA_1, singapore).single()
        assertEquals(parseEspnDate("2026-10-04T12:00Z"), event.startMs)
        assertEquals(EventState.UPCOMING, event.state)
        assertNull(event.home)
    }

    @Test
    fun espnDatesWithoutSecondsParse() {
        assertNotNull(parseEspnDate("2026-10-11T19:00Z"))
        assertNull(parseEspnDate("not a date"))
    }

    @Test
    fun guideTitlesScoreByHowMuchOfTheGameTheyName() {
        val event = parseScoreboard(Competition.LIGUE_1, psgLyon).single()
        assertEquals(SportsMatching.BOTH_TEAMS, SportsMatching.score(event, "Football : PSG / Olympique Lyonnais"))
        assertEquals(SportsMatching.BOTH_TEAMS, SportsMatching.score(event, "Paris SG - Lyon"))
        // One team is never enough.
        assertEquals(SportsMatching.COMPETITION_ONLY, SportsMatching.score(event, "Ligue 1 : avant-match Paris Saint-Germain"))
        assertEquals(0, SportsMatching.score(event, "Le mag de l'OL : Lyon"))
        assertEquals(SportsMatching.COMPETITION_ONLY, SportsMatching.score(event, "Multiplex Ligue 1"))
        assertEquals(0, SportsMatching.score(event, "Journal de 20h"))
    }

    @Test
    fun shortNamesOnlyMatchAsWholeWords() {
        val event = parseScoreboard(Competition.LIGUE_1, psgLyon).single()
        // "Lyon" inside "Lyonnaise" is not the club.
        assertEquals(0, SportsMatching.score(event, "Cuisine lyonnaise"))
        assertTrue(SportsMatching.containsTerm(SportsMatching.normalize("Saint-Étienne - PSG"), "Saint-Etienne"))
    }

    @Test
    fun aRaceMatchesOnItsName() {
        val event = parseScoreboard(Competition.FORMULA_1, singapore).single()
        assertEquals(SportsMatching.EVENT_NAME + 10, SportsMatching.score(event, "Formule 1 : Grand Prix de Singapour - Singapore"))
        assertEquals(SportsMatching.EVENT_NAME, SportsMatching.score(event, "Singapore: the race"))
        assertEquals(SportsMatching.COMPETITION_ONLY + 10, SportsMatching.score(event, "F1 : le mag"))
    }

    @Test
    fun teamScoreboardsAskForOneDayAtATime() {
        // ESPN rejects "dates=A-B" ranges, so a team league is fetched day by day.
        assertEquals(
            "https://site.api.espn.com/apis/site/v2/sports/soccer/fra.1/scoreboard?dates=20261011&limit=200",
            EspnScoreboard.urlFor(Competition.LIGUE_1, java.time.LocalDate.of(2026, 10, 11)),
        )
        assertEquals(
            "https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard",
            EspnScoreboard.urlFor(Competition.FORMULA_1, null),
        )
    }

    private fun nflTeam(city: String, nickname: String, abbreviation: String) =
        SportsTeam("$city $nickname", nickname, listOf(nickname, abbreviation), null, null)

    private fun nflGame(home: SportsTeam, away: SportsTeam) = SportsEvent(
        id = "401", competition = Competition.NFL, name = "${away.name} at ${home.name}",
        startMs = 0L, state = EventState.UPCOMING, statusDetail = "", home = home, away = away,
        broadcasts = emptyList(),
    )

    private val bearsAtPackers = nflGame(nflTeam("Green Bay", "Packers", "GB"), nflTeam("Chicago", "Bears", "CHI"))

    @Test
    fun eventChannelsNamingBothTeamsRankFirst() {
        assertEquals(SportsMatching.EVENT_CHANNEL, SportsMatching.channelScore(bearsAtPackers, "NFL 03: Bears vs Packers 1:00 PM ET"))
        assertEquals(SportsMatching.EVENT_CHANNEL, SportsMatching.channelScore(bearsAtPackers, "USA | NFL: Chicago @ Green Bay"))
        assertEquals(SportsMatching.EVENT_CHANNEL, SportsMatching.channelScore(bearsAtPackers, "NFL | Bears at Green Bay"))
        val psgLyonEvent = parseScoreboard(Competition.LIGUE_1, psgLyon).single()
        assertEquals(SportsMatching.EVENT_CHANNEL, SportsMatching.channelScore(psgLyonEvent, "LIGUE 1 | PSG - Lyon"))
    }

    @Test
    fun aCityAloneIsNotATeam() {
        assertEquals(0, SportsMatching.channelScore(bearsAtPackers, "Packers TV"))
        assertEquals(0, SportsMatching.channelScore(bearsAtPackers, "NBC Sports Chicago"))
        assertEquals(0, SportsMatching.channelScore(bearsAtPackers, "NFL RedZone"))
        assertEquals("Green Bay", SportsMatching.city(bearsAtPackers.home!!))
        // Two teams from one city: the city names neither.
        val laDerby = nflGame(nflTeam("Los Angeles", "Rams", "LAR"), nflTeam("Los Angeles", "Chargers", "LAC"))
        assertEquals(0, SportsMatching.channelScore(laDerby, "Los Angeles Sports 4K"))
        assertEquals(SportsMatching.EVENT_CHANNEL, SportsMatching.channelScore(laDerby, "Chargers @ Rams"))
    }

    @Test
    fun guideTitlesWithCitiesNameTheGame() {
        assertEquals(SportsMatching.BOTH_TEAMS, SportsMatching.score(bearsAtPackers, "NFL Football: Chicago at Green Bay"))
    }

    @Test
    fun aRaceChannelNeedsTheRaceAndTheSport() {
        val event = parseScoreboard(Competition.FORMULA_1, singapore).single()
        assertEquals(SportsMatching.EVENT_NAME, SportsMatching.channelScore(event, "F1 | Singapore GP Live"))
        assertEquals(0, SportsMatching.channelScore(event, "Singapore News"))
        assertTrue("abbreviations stay out of the channel search", "CHI" !in SportsMatching.channelSearchTerms(bearsAtPackers))
    }

    @Test
    fun sectionHeadersAreNotChannels() {
        assertTrue(SportsMatching.isSeparator("# # # NFL GAMES # # #"))
        assertTrue(SportsMatching.isSeparator("  ##### Bears vs Packers #####"))
        assertTrue(!SportsMatching.isSeparator("NFL 03: Bears vs Packers"))
    }

    private fun nbaTeam(city: String, nickname: String, abbreviation: String) =
        SportsTeam("$city $nickname", nickname, listOf(nickname, abbreviation), null, null)

    private val spursAtSuns = SportsEvent(
        id = "402", competition = Competition.NBA, name = "San Antonio Spurs at Phoenix Suns",
        startMs = 0L, state = EventState.LIVE, statusDetail = "", home = nbaTeam("Phoenix", "Suns", "PHX"),
        away = nbaTeam("San Antonio", "Spurs", "SA"), broadcasts = listOf("ESPN2"),
    )

    @Test
    fun bothTeamsOrNothing() {
        assertEquals(0, SportsMatching.channelScore(spursAtSuns, "EPL 04: Man United vs Spurs"))
        assertEquals(SportsMatching.BOTH_TEAMS, SportsMatching.score(spursAtSuns, "Phoenix vs Spurs"))
        assertEquals(0, SportsMatching.score(spursAtSuns, "Phoenix Mercury Highlights"))
        assertEquals(SportsMatching.EVENT_CHANNEL, SportsMatching.channelScore(spursAtSuns, "NBA 02: Spurs @ Suns"))
        assertEquals(SportsMatching.EVENT_CHANNEL, SportsMatching.channelScore(spursAtSuns, "NBA | San Antonio @ Phoenix"))
        // Two cities alone could be any sport: the competition has to be named too.
        assertEquals(0, SportsMatching.channelScore(spursAtSuns, "San Antonio vs Phoenix"))
    }

    @Test
    fun anotherSportIsNeverTheGame() {
        assertEquals(0, SportsMatching.channelScore(spursAtSuns, "NHL 03: Spurs vs Suns"))
        assertEquals(0, SportsMatching.score(spursAtSuns, "Spurs - Suns", "Sports; Hockey"))
        assertEquals(0, SportsMatching.channelScore(spursAtSuns, "Premier League | Spurs v Suns"))
        assertEquals(SportsMatching.BOTH_TEAMS, SportsMatching.score(spursAtSuns, "Spurs - Suns", "Sports; Basketball"))
    }

    @Test
    fun espnsBroadcasterIsFoundByItsChannelName() {
        assertTrue(SportsMatching.broadcasterMatches("US | ESPN2 HD", "ESPN2"))
        assertTrue(SportsMatching.broadcasterMatches("USA: ESPN 2", "ESPN2"))
        assertTrue(!SportsMatching.broadcasterMatches("ESPN2", "ESPN"))
        assertTrue(!SportsMatching.broadcasterMatches("ESPN+ 03", "ESPN"))
        assertTrue(SportsMatching.broadcasterMatches("FR | beIN Sports 1 FHD", "beIn Sports"))
        assertTrue(SportsMatching.broadcasterMatches("CBS New York", "CBS"))
        assertTrue(!SportsMatching.broadcasterMatches("CBSN News", "CBS"))
        assertTrue(SportsMatching.broadcasterMatches("US: NFL Network HD", "NFL Net"))
        assertTrue(SportsMatching.LISTED_BROADCASTER > SportsMatching.EVENT_CHANNEL)
    }

    @Test
    fun onlyTheUsLeaguesGoByEspnsListing() {
        val espn = Competition.entries.filter { it.usesEspnBroadcasts }.toSet()
        assertEquals(setOf(Competition.NBA, Competition.NFL), espn)
        assertEquals(listOf("Ligue 1+"), Competition.LIGUE_1.homeBroadcasters)
        assertTrue(Competition.LIGUE_1.broadcasters.none { it.contains("beIN", ignoreCase = true) })
        assertTrue(SportsMatching.broadcasterMatches("FR: Ligue 1+ 2 FHD", "Ligue 1+"))
        assertTrue(SportsMatching.broadcasterMatches("FR| LIGUE1+ 3", "Ligue 1+"))
        assertTrue(!SportsMatching.broadcasterMatches("FR: Ligue 1 Replay", "Ligue 1+"))
        assertTrue(SportsMatching.broadcasterMatches("UK: Sky Sports Premier League", "Sky Sports"))
        assertTrue(SportsMatching.LISTED_AND_SHOWING > SportsMatching.LISTED_BROADCASTER)
    }

    private val shanghai = """
        {"events":[{"id":"315-2026","date":"2026-10-05T04:00Z","endDate":"2026-10-19T03:59Z","name":"Rolex Shanghai Masters",
          "groupings":[{"grouping":{"displayName":"Men's Singles"},"competitions":[
            {"date":"2026-10-10T06:00Z","status":{"type":{"state":"post"}}},
            {"date":"2026-10-11T06:00Z","status":{"type":{"state":"pre"}},"broadcasts":[{"names":["Tennis Channel"]}]}]}]}]}
    """.trimIndent()

    @Test
    fun aTournamentIsLiveBetweenItsDatesWhileMatchesRemain() {
        val now = parseEspnDate("2026-10-11T03:00Z")!!
        val event = parseScoreboard(Competition.ATP, shanghai, now).single()
        assertEquals(EventState.LIVE, event.state)
        assertEquals(listOf("Tennis Channel"), event.broadcasts)
        assertEquals(EventState.UPCOMING, parseScoreboard(Competition.ATP, shanghai, parseEspnDate("2026-10-01T00:00Z")!!).single().state)
        assertEquals(SportsMatching.EVENT_NAME + 10, SportsMatching.score(event, "Tennis : Masters 1000 de Shanghai"))
        assertEquals(SportsMatching.EVENT_NAME, SportsMatching.channelScore(event, "ATP Shanghai Court 1"))
    }
}
