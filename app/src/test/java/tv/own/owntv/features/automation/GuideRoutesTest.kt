package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.settings.EpgAutoRefresh

class GuideRoutesTest {
    private val backend = FakeGuide()
    private val client = ApiTestClient(guideRoutes(backend))

    @Test
    fun `adding needs an http url and takes an optional schedule`() {
        assertEquals(400, client.call("POST", "/api/v1/guide", """{"name":"G"}""").status)
        assertEquals(400, client.call("POST", "/api/v1/guide", """{"url":"ftp://x.tv/epg.xml"}""").status)
        assertEquals(400, client.call("POST", "/api/v1/guide", """{"url":"http://x.tv/epg.xml","refresh":"WEEKLY"}""").status)
        assertEquals(400, client.call("POST", "/api/v1/guide", """{"url":"http://x.tv/epg.xml?key=***"}""").status)
        assertEquals(0, backend.added.size)
        client.json("POST", "/api/v1/guide", """{"url":"http://x.tv/epg.xml","refresh":"HOURS_24"}""")
        val added = backend.added.single()
        assertEquals("http://x.tv/epg.xml", added.url)
        assertNull(added.name)
        assertEquals(EpgAutoRefresh.HOURS_24, added.refresh?.mode)
    }

    @Test
    fun `negative ids address guide sources`() {
        assertEquals(200, client.call("GET", "/api/v1/guide/-3").status)
        assertEquals(404, client.call("GET", "/api/v1/guide/3").status)
        assertEquals(202, client.call("POST", "/api/v1/guide/-3/sync").status)
        assertEquals(202, client.call("DELETE", "/api/v1/guide/-3/sync").status)
        assertEquals(202, client.call("DELETE", "/api/v1/guide/-3").status)
        assertEquals(listOf("sync", "cancel", "delete"), backend.calls)
    }

    @Test
    fun `edits need at least one known field`() {
        assertEquals(400, client.call("PATCH", "/api/v1/guide/-3", "{}").status)
        assertEquals(400, client.call("PATCH", "/api/v1/guide/-3", """{"colour":"red"}""").status)
        assertEquals(400, client.call("PATCH", "/api/v1/guide/-3", """{"url":"http://me:***@x.tv/epg.xml"}""").status)
        assertEquals(200, client.call("PATCH", "/api/v1/guide/-3", """{"userAgent":""}""").status)
        assertEquals("", backend.updated.single().userAgent)
        assertEquals(404, client.call("PATCH", "/api/v1/guide/-9", """{"name":"X"}""").status)
    }

    private class FakeGuide : GuideBackend {
        val added = mutableListOf<GuideFields>()
        val updated = mutableListOf<GuideFields>()
        val calls = mutableListOf<String>()

        override suspend fun guides() = JSONObject()
        override suspend fun guide(id: Long) = if (id == -3L) JSONObject().put("id", id) else null
        override suspend fun add(fields: GuideFields): JSONObject {
            added += fields
            return JSONObject().put("id", -4)
        }
        override suspend fun update(id: Long, fields: GuideFields) = (id == -3L).also { if (it) updated += fields }
        override suspend fun resync(id: Long) = (id == -3L).also { if (it) calls += "sync" }
        override suspend fun cancelSync(id: Long) = (id == -3L).also { if (it) calls += "cancel" }
        override suspend fun delete(id: Long) = (id == -3L).also { if (it) calls += "delete" }
    }
}
