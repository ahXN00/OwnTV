package tv.own.owntv.features.setup

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HostPool

/** The form's backup list: what it starts from, and which server it marks IN USE (ui-design Q3/Q4). */
class BackupServersFormTest {

    private val source = SourceEntity(
        id = 1, name = "X", type = SourceType.XTREAM, url = "http://a.example.top", username = "u", password = "p",
        backupUrls = HostPool.encode(listOf("http://b.example.top", "c.example.top:8080", "not an address")),
        failoverEnabled = false,
    )

    @Test
    fun `starts from the stored list, normalised, and the stored switch`() {
        val form = BackupServersForm(source, inUseNow = null)
        assertEquals(listOf("http://b.example.top", "http://c.example.top:8080"), form.backups)
        assertEquals(false, form.failoverEnabled)
    }

    @Test
    fun `marks the primary, the in-use backup, or a pending Use first`() {
        assertEquals("http://a.example.top", BackupServersForm(source, null).marked(source.url))
        val onBackup = BackupServersForm(source, "http://c.example.top:8080")
        assertEquals("http://c.example.top:8080", onBackup.marked(source.url))
        onBackup.useFirst = "http://b.example.top"
        assertEquals("http://b.example.top", onBackup.marked(source.url))
    }

    @Test
    fun `an in-use server deleted from the list falls back to the primary`() {
        val form = BackupServersForm(source, "http://c.example.top:8080")
        form.backups = listOf("http://b.example.top")
        assertEquals("http://a.example.top", form.marked(source.url))
    }

    @Test
    fun `a new playlist starts empty with switching on`() {
        val form = BackupServersForm(null, null)
        assertEquals(emptyList<String>(), form.backups)
        assertEquals(true, form.failoverEnabled)
    }

    @Test
    fun `paste splits on space comma and newline`() {
        val form = BackupServersForm(null, null)
        val r = HostPool.add("b.example.top, c.example.top:8080\nd.example.top", source.url, form.backups)
        assertEquals(3, r.added)
        assertEquals(listOf("http://b.example.top", "http://c.example.top:8080", "http://d.example.top"), r.backups)
    }

    @Test
    fun `paste dedupes against primary and the list and rejects invalid`() {
        val form = BackupServersForm(source, null)
        val r = HostPool.add("b.example.top a.example.top nope d.example.top", source.url, form.backups)
        assertEquals(1, r.added)
        assertEquals(2, r.duplicates)
        assertEquals(listOf("nope"), r.invalid)
        assertEquals(listOf("http://b.example.top", "http://c.example.top:8080", "http://d.example.top"), r.backups)
    }

    @Test
    fun `use first moves that server to the front and marks it`() {
        val form = BackupServersForm(source, null)
        val host = "http://c.example.top:8080"
        form.backups = listOf(host) + form.backups.filter { it != host }
        form.useFirst = host
        assertEquals(listOf(host, "http://b.example.top"), form.backups)
        assertEquals(host, form.marked(source.url))
    }

    @Test
    fun `move swaps order`() {
        val form = BackupServersForm(source, null)
        form.backups = form.backups.toMutableList().also { java.util.Collections.swap(it, 0, 1) }
        assertEquals(listOf("http://c.example.top:8080", "http://b.example.top"), form.backups)
    }

    @Test
    fun `delete drops the server and a pending use first`() {
        val form = BackupServersForm(source, "http://c.example.top:8080")
        form.useFirst = "http://b.example.top"
        val host = form.useFirst
        form.backups = form.backups.filter { it != host }
        if (form.useFirst == host) form.useFirst = null
        assertEquals(listOf("http://c.example.top:8080"), form.backups)
        assertEquals(null, form.useFirst)
        assertEquals("http://c.example.top:8080", form.marked(source.url))
    }

    @Test
    fun `edit rejects an invalid address and a duplicate`() {
        val form = BackupServersForm(source, null)
        assertEquals(null, HostPool.normalize("nope", source.url))
        val edited = HostPool.normalize("b.example.top", source.url)
        assertEquals(true, edited == HostPool.origin(source.url) || edited in form.backups)
    }
}
