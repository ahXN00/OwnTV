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

    // `title = …` on a row, or a Stage row's `val useTitle = …`; for
    // `if (testing) R.string.settings_testing else R.string.x` the row is the `else` string.
    private fun titles(source: String, notRows: Set<String>): Set<String> =
        Regex("""(?:title|Title) = stringResource\((?:if \(.*?\) R\.string\.[a-z_0-9]+ else )?R\.string\.([a-z_0-9]+)""").findAll(source)
            .map { it.groupValues[1] }.filterNot { it in notRows }.toSet()

    private fun check(file: String, list: String, notRows: Set<String> = emptySet()) {
        val source = read(file)
        val drawn = titles(source, notRows)
        assertTrue("no rows found in $file — has it changed shape?", drawn.isNotEmpty())
        assertEquals("$file rows missing from $list", emptySet<String>(), drawn - listed(source, list))
    }

    @Test
    fun `recording rows are searchable`() =
        check(
            "RecordingSettingsScreen.kt", "RECORDING_SEARCH_ROWS",
            // The group's heading and the "record while watching" warning dialog are not rows.
            notRows = setOf("recording_settings_group", "settings_record_watching_warning_title"),
        )

    @Test
    fun `player button rows are searchable`() = check("PlayerButtonsSettingsScreen.kt", "PLAYER_BUTTONS_SEARCH_ROWS")

    // A Stage full page's own title is the screen itself, which has a search entry of its own.
    @Test
    fun `proxy rows are searchable`() = check("NetworkSettingsScreen.kt", "PROXY_SEARCH_ROWS", notRows = setOf("common_proxy"))

    @Test
    fun `dns rows are searchable`() = check("DnsSettingsScreen.kt", "DNS_SEARCH_ROWS", notRows = setOf("settings_dns", "common_save"))

    @Test
    fun `subtitle appearance rows are searchable`() {
        val source = read("VideoPlayerSettingsScreen.kt")
        // The Stage page's five style rows, each drawn as `SubStyleRow(stringResource(R.string.…), …)`.
        val drawn = Regex("""SubStyleRow\(stringResource\(R\.string\.([a-z_0-9]+)\)""").findAll(source)
            .map { it.groupValues[1] }.toSet()
        assertEquals(5, drawn.size)
        assertEquals(emptySet<String>(), drawn - listed(source, "SUBTITLE_APPEARANCE_SEARCH_ROWS"))
    }
}
