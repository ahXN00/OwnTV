package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

class HeroPreviewUrlTest {

    private val source = SourceEntity(
        id = 1,
        name = "X",
        type = SourceType.XTREAM,
        url = "http://a.example:8080",
    )
    private val stored = "http://a.example:8080/live/1.ts"

    @Test
    fun withSourceReturnsForRequestResult() {
        assertEquals("http://b.example/live/1.ts", heroPreviewUrl(stored, source) { _, _ -> "http://b.example/live/1.ts" })
    }

    @Test
    fun nullSourceReturnsStoredUrl() {
        assertEquals(stored, heroPreviewUrl(stored, null) { _, _ -> "http://b.example/live/1.ts" })
    }
}
