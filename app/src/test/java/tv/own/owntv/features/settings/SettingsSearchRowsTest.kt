package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The hand-written settings screens keep a list of their rows for Settings search beside the rows
 * themselves. This holds each list to its screen: a row title drawn there and missing from the list
 * would be unfindable by its own name again.
 *
 * Source-scanned, like the Video player catalogue test: the thing worth checking is the text.
 */
class SettingsSearchRowsTest {

    private fun read(name: String): String {
        val file = File("src/main/java/tv/own/owntv/features/settings/$name")
        assertTrue("expected to run from the app module, cwd=${File(".").absolutePath}", file.isFile)
        return file.readText()
    }

    /** The string names inside `val <list> ... = listOf(...)`. */
    private fun listed(source: String, list: String): Set<String> {
        val body = Regex("""val $list\b[^=]*=\s*listOf\(([^)]*)\)""").find(source)?.groupValues?.get(1)
        assertTrue("$list not found", body != null)
        return Regex("""R\.string\.([a-z_0-9]+)""").findAll(body!!).map { it.groupValues[1] }.toSet()
    }

    private fun titles(source: String, notRows: Set<String>): Set<String> =
        Regex("""title = stringResource\(R\.string\.([a-z_0-9]+)""").findAll(source)
            .map { it.groupValues[1] }.filterNot { it in notRows }.toSet()

    private fun check(file: String, list: String, notRows: Set<String> = emptySet()) {
        val source = read(file)
        val drawn = titles(source, notRows)
        assertTrue("no rows found in $file — has it changed shape?", drawn.isNotEmpty())
        assertEquals("$file rows missing from $list", emptySet<String>(), drawn - listed(source, list))
    }

    @Test
    fun `recording rows are searchable`() =
        check("RecordingSettingsScreen.kt", "RECORDING_SEARCH_ROWS", notRows = setOf("recording_settings_group"))

    @Test
    fun `proxy rows are searchable`() = check("NetworkSettingsScreen.kt", "PROXY_SEARCH_ROWS")

    @Test
    fun `dns rows are searchable`() = check("DnsSettingsScreen.kt", "DNS_SEARCH_ROWS")

    @Test
    fun `subtitle appearance rows are searchable`() {
        val source = read("VideoPlayerSettingsScreen.kt")
        // The popup's five style rows, as drawn by `Row2(... title = ...)` after the Size row.
        val popup = source.substringAfter("title = stringResource(R.string.settings_subtitle_size),")
            .substringBefore("SubDialog.TRANSPARENCY)")
        val drawn = titles(popup, emptySet()) + "settings_subtitle_size"
        assertEquals(5, drawn.size)
        assertEquals(emptySet<String>(), drawn - listed(source, "SUBTITLE_APPEARANCE_SEARCH_ROWS"))
    }
}
