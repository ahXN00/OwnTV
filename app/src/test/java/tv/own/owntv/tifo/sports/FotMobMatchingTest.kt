package tv.own.owntv.tifo.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FotMobMatchingTest {

    @Test
    fun aGuidesSpellingAndThePlaylistsMeet() {
        assertEquals("sky sport main event", SportsMatching.tvKey("UK-NOWTV| SKY SPORTS MAIN EVENTS HD(BK)"))
        assertEquals("sky sport main event", SportsMatching.tvKey("UK| SKY SPORT MAIN EVENT"))
        assertEquals("sky sport main event", SportsMatching.tvKey("Sky Sports Main Event"))
        assertEquals("canal plus live 3", SportsMatching.tvKey("FR| CANAL+LIVE 3 FHD"))
        assertEquals("canal plus live 3", SportsMatching.tvKey("Canal+ Live 3"))
        assertEquals("canal plus", SportsMatching.tvKey("FR| CANAL+ FHD ᴿᴬᵂ"))
        assertEquals("dazn laliga 1", SportsMatching.tvKey("ES| DAZN LA LIGA 1 HD ᴿᴬᵂ"))
        assertEquals("dazn 1", SportsMatching.tvKey("DAZN 1 (FR)"))
        assertEquals("ligue 1 plus 3", SportsMatching.tvKey("FR| LIGUE1 + 3"))
        assertEquals("sky sport plus", SportsMatching.tvKey("UK| SKY SPORTS + HD"))
        assertEquals("sky sport plus", SportsMatching.tvKey("Sky Sports+ HD"))
    }

    @Test
    fun aChannelsCountryIsItsPrefix() {
        assertEquals("FR", SportsMatching.countryOf("FR| CANAL+ FOOT FHD"))
        assertEquals("UK", SportsMatching.countryOf("UK-NOWTV| SKY SPORTS PREMIER LEAGUE FHD"))
        assertEquals("ES", SportsMatching.countryOf("ES-C| DAZN LALIGA ᴿᴬᵂ"))
        assertEquals("UK", SportsMatching.countryOf("GB: Sky Sports 1"))
        assertNull(SportsMatching.countryOf("beIN Sports 1"))
        assertEquals("UK", SportsMatching.playlistCountry("GB"))
    }

    @Test
    fun aListedStationIsOneChannelNotItsFamily() {
        assertTrue(SportsMatching.isListedChannel("FR| CANAL+LIVE 3 FHD", "Canal+ Live 3", "FR"))
        assertFalse(SportsMatching.isListedChannel("FR| CANAL+LIVE 13 FHD", "Canal+ Live 3", "FR"))
        assertTrue(SportsMatching.isListedChannel("FR| CANAL+ FOOT HD", "Canal+ Foot", "FR"))
        assertFalse(SportsMatching.isListedChannel("FR| CANAL+ SPORT 360 HD", "Canal+ Sport", "FR"))
        // "<name> TV" is the same channel; "<name> 1" only in Spain.
        assertTrue(SportsMatching.isListedChannel("ES| M+ LALIGA TV FHD ᴿᴬᵂ", "M+ LALIGA", "ES"))
        assertTrue(SportsMatching.isListedChannel("ES| DAZN LA LIGA 1 HD ᴿᴬᵂ", "DAZN LaLiga", "ES"))
        assertFalse(SportsMatching.isListedChannel("FR| CANAL+ SPORT 1", "Canal+ Sport", "FR"))
        assertTrue(SportsMatching.isListedChannel("UK-NOWTV| SKY SPORTS MAIN EVENT FHD", "Sky Sports Main Event", "UK"))
        // A "+" service is not the network of the same name.
        assertFalse(SportsMatching.isListedChannel("US| PARAMOUNT NETWORK HD", "Paramount+", "US"))
    }

    @Test
    fun aListedServiceBringsItsFeedsOnly() {
        assertTrue(SportsMatching.isServiceFeed("FR| LIGUE 1+ 3 HD", "Ligue 1+"))
        assertTrue(SportsMatching.isServiceFeed("FR| LIGUE1 + 7", "Ligue 1+"))
        assertTrue(SportsMatching.isServiceFeed("FR| LIGUE1+ FHD ( Samedi Match 1)", "Ligue 1+"))
        assertFalse(SportsMatching.isServiceFeed("FR| DISNEY+ FILMS 1 FHD", "Disney+"))
        assertFalse(SportsMatching.isServiceFeed("FR| DAZN 1 FHD", "DAZN"))
        assertFalse(SportsMatching.isServiceFeed("FR| MYCANAL", "myCANAL"))
    }

    @Test
    fun eventSlotsAreNotABroadcastersChannels() {
        assertTrue(SportsMatching.isEventSlot("US| ESPN+ PPV 23"))
        assertTrue(SportsMatching.isEventSlot("US (ESPN+ 002) | Football: Godoy Cruz - Huracan"))
        assertTrue(SportsMatching.isEventSlot("NFL 03: Ravens vs. Falcons"))
        assertFalse(SportsMatching.isEventSlot("US| ESPN 2 HD"))
        assertFalse(SportsMatching.isEventSlot("FR| CANAL+ FOOT FHD"))
    }

    private val listingsJson = """
        {"5100001":[
            {"startTime":"/Date(1792236600000)/","station":{"name":"Canal+","type":"TV"},
             "program":{"teams":[{"name":"Everton","isHome":true},{"name":"Chelsea","isHome":false}]}},
            {"startTime":"/Date(1792236600000)/","station":{"name":"myCANAL","type":"Stream"},
             "program":{"teams":[{"name":"Everton","isHome":true},{"name":"Chelsea","isHome":false}]}},
            {"startTime":"/Date(1792236600000)/","station":{"name":"Canal+ Foot","type":null},
             "program":{"teams":[{"name":"Everton","isHome":true},{"name":"Chelsea","isHome":false}]}}],
         "5100002":[
            {"startTime":"/Date(1792332000000)/","station":{"name":"Ligue 1+","type":"Stream"},
             "program":{"teams":[{"name":"Lille","isHome":true},{"name":"Brest","isHome":false}]}},
            {"startTime":"/Date(1792332000000)/","station":{"name":"DAZN 1 (FR)","type":"TV"},
             "program":{"teams":[{"name":"Lille","isHome":true},{"name":"Brest","isHome":false}]}}]}
    """.trimIndent()

    private fun team(name: String, short: String = name) = SportsTeam(name, short, emptyList(), null, null)

    private fun game(competition: Competition, home: SportsTeam, away: SportsTeam, startMs: Long) = SportsEvent(
        id = "1", competition = competition, name = "${away.name} at ${home.name}", startMs = startMs,
        state = EventState.UPCOMING, statusDetail = "", home = home, away = away, broadcasts = emptyList(),
    )

    @Test
    fun fotMobsGuideParses() {
        val listings = parseFotMobListings(listingsJson)
        assertEquals(2, listings.size)
        val everton = listings.first { "Everton" in it.teams }
        assertEquals(1792236600000L, everton.startMs)
        // TV channels first; a station with no type is a TV channel, an app is not.
        assertEquals(listOf("Canal+", "Canal+ Foot", "myCANAL"), everton.stations.map { it.name })
        assertEquals(listOf(true, true, false), everton.stations.map { it.isTv })
        val lille = listings.first { "Lille" in it.teams }
        assertEquals(listOf("DAZN 1", "Ligue 1+"), lille.stations.map { it.name })
    }

    @Test
    fun aGameIsFoundInTheGuideByKickOffAndTeams() {
        val listings = parseFotMobListings(listingsJson)
        val everton = game(Competition.PREMIER_LEAGUE, team("Everton"), team("Chelsea"), 1792236600000L)
        assertEquals("Canal+ Foot", FotMobTv.listingFor(everton, listings)?.stations?.get(1)?.name)
        // Same teams, another day: not this listing.
        val later = game(Competition.PREMIER_LEAGUE, team("Everton"), team("Chelsea"), 1792236600000L + 4 * 3_600_000L)
        assertNull(FotMobTv.listingFor(later, listings))
        // ESPN's "Lille" / "Brest" against FotMob's, five minutes apart.
        val lille = game(Competition.LIGUE_1, team("Lille"), team("Brest"), 1792332000000L + 5 * 60_000L)
        assertNotNull(FotMobTv.listingFor(lille, listings))
    }

    @Test
    fun footballReadsFranceAndItsOwnCountry() {
        assertEquals(listOf("FR", "GB"), Competition.PREMIER_LEAGUE.fotMobCountries)
        assertEquals(listOf("FR", "ES"), Competition.LALIGA.fotMobCountries)
        assertEquals(listOf("FR"), Competition.LIGUE_1.fotMobCountries)
        assertEquals(listOf("FR"), Competition.CHAMPIONS_LEAGUE.fotMobCountries)
        assertTrue(Competition.NBA.fotMobCountries.isEmpty())
        assertTrue(Competition.FORMULA_1.fotMobCountries.isEmpty())
        assertTrue(SportsMatching.LISTED_CHANNEL > SportsMatching.LISTED_AND_SHOWING)
        assertTrue(SportsMatching.LISTED_SERVICE in (SportsMatching.BROADCASTER + 1) until SportsMatching.BEST_MATCH)
    }
}
