package tv.own.owntv.features.epg

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.parser.EpgDetails

class ProgrammeFactsTest {

    @Test
    fun `every field in order`() {
        val d = EpgDetails(categories = "Drama", year = 2023, rating = "FSK 12", lengthMin = 95, episode = "S2 E5")
        assertEquals(listOf("S2 E5", "2023", "FSK 12", "95 min"), programmeFacts(d, "95 min"))
    }

    @Test
    fun `nothing from the feed gives no line`() {
        assertEquals(emptyList<String>(), programmeFacts(EpgDetails.NONE, null))
    }

    @Test
    fun `categories only become tags, not facts`() {
        val d = EpgDetails(categories = listOf("News", "Sport").joinToString(EpgDetails.CATEGORY_SEPARATOR.toString()))
        assertEquals(emptyList<String>(), programmeFacts(d, null))
        assertEquals(listOf("News", "Sport"), d.categoryList)
    }
}
