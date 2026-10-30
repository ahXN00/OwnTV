package tv.own.owntv.features.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlMaskTest {
    @Test
    fun `an address field shows only where it points`() {
        assertEquals("http://x.tv:8080/…", UrlMask.origin("http://x.tv:8080/playlist/me/s3cret/m3u_plus"))
        assertEquals("http://x.tv/…", UrlMask.origin("http://x.tv/get.php?u=me&p=s3cret"))
        assertEquals("http://x.tv/…", UrlMask.origin("http://x.tv/get.php?username=me&passwd=s3cret&type=m3u"))
        assertEquals("https://[2001:db8::1]:8443/…", UrlMask.origin("https://[2001:db8::1]:8443/c/"))
        listOf("http://x.tv", "http://x.tv:8080", "").forEach { assertEquals(it, UrlMask.origin(it)) }
    }

    @Test
    fun `an address field without a scheme shows only a host, and other text not at all`() {
        assertEquals("x.tv:8080/…", UrlMask.origin("x.tv:8080/playlist/me/s3cret/m3u_plus"))
        assertEquals("[2001:db8::1]/…", UrlMask.origin("[2001:db8::1]/a.m3u"))
        assertEquals("x.tv", UrlMask.origin("x.tv"))
        listOf("me/s3cret://x.tv/a", "my s3cret referer", "s3cret", "localhost:8080/a", "me:pw@x.tv/a", "x.tv s3cret", "…")
            .forEach { assertEquals(it, "…", UrlMask.origin(it)) }
    }

    @Test
    fun `the host ends where OkHttp ends it, a backslash included`() {
        assertEquals("http://x.tv/…", UrlMask.origin("http://x.tv\\me\\s3cret"))
        assertEquals("http://x.tv:8080/…", UrlMask.origin("http://x.tv:8080\\a.m3u"))
    }

    @Test
    fun `no text before an at sign is shown, even one past the host`() {
        listOf(
            "http://me:12/34@x.tv/a.m3u",
            "http://x.tv\\@s3cret:123/x",
            "http://x.tv/get.php?username=me&password=p@ss",
            "http://x.tv/live/me/p@ss/1.ts",
            "http://@",
            "http://;s3cret",
        ).forEach { assertEquals(it, "http://…", UrlMask.origin(it)) }
        assertEquals("see http://… then", UrlMask.maskTvText("see http://me:12/34@x.tv/a then"))
    }

    @Test
    fun `long text is cut down in a time that grows with its length, not its square`() {
        listOf(
            "a".repeat(100_000),
            "a".repeat(100_000) + "://",
            "http://" + "a".repeat(100_000),
            "a://".repeat(25_000),
            "a:".repeat(50_000),
            "password=".repeat(11_000),
        ).forEach { text ->
            assertFast("cutting an address field ${text.take(12)}...") { UrlMask.origin(text) }
            assertFast("cutting TV text ${text.take(12)}...") { UrlMask.maskTvText(text) }
            assertFast("checking ${text.take(12)}...") { UrlMask.hasSecret(text) }
        }
    }

    /** Runs [block] once to warm the regex engine up, then fails if a second run takes 200 ms or more. */
    private fun assertFast(what: String, block: () -> Unit) {
        block()
        val started = System.nanoTime()
        block()
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("$what took $ms ms", ms < 200)
    }

    @Test
    fun `TV text shows each address only as far as its host and port`() {
        assertEquals("HTTP 403 for http://x.tv:8080/… after 3 tries", UrlMask.maskTvText("HTTP 403 for http://x.tv:8080/playlist/me/s3cret/m3u_plus after 3 tries"))
        assertEquals("http://x.tv/…", UrlMask.maskTvText("http://x.tv/get.php?username=me&password=s3cret"))
        assertEquals("https://x.tv/…", UrlMask.maskTvText("https://x.tv#me/s3cret"))
        assertEquals("http://x.tv/…", UrlMask.maskTvText("http://x.tv?me"))
        assertEquals("open \"rtmp://[2001:db8::1]:1935/…\" failed", UrlMask.maskTvText("open \"rtmp://[2001:db8::1]:1935/live/me/s3cret\" failed"))
        assertEquals("1http://x.tv/…", UrlMask.maskTvText("1http://x.tv/me/s3cret/1.ts"))
        assertEquals("${"w".repeat(40)}http://x.tv/…", UrlMask.maskTvText("${"w".repeat(40)}http://me:pw@x.tv/a"))
        listOf("http://x.tv", "http://x.tv:8080", "udp://239.0.0.1:1234", "no address here", "")
            .forEach { assertEquals(it, UrlMask.maskTvText(it)) }
    }

    @Test
    fun `user info and anything after the host that is not a port are dropped and marked as cut`() {
        assertEquals("http://x.tv:8080/…", UrlMask.maskTvText("http://me:s3cret@x.tv:8080/a.m3u"))
        assertEquals("http://x.tv/…", UrlMask.maskTvText("http://me:p@ss@x.tv/a.m3u"))
        assertEquals("http://x.tv/…", UrlMask.maskTvText("http://me@x.tv"))
        assertEquals("http://x.tv:8080/…", UrlMask.origin("http://me:s3cret@x.tv:8080"))
        assertEquals("http://x.tv:80/…", UrlMask.maskTvText("http://x.tv:80s3cret/a"))
        assertEquals("http://x.tv/…", UrlMask.maskTvText("http://x.tv;s3cret"))
    }

    @Test
    fun `TV text still has login values outside addresses masked by pattern`() {
        assertEquals("retry with password=*** later", UrlMask.maskTvText("retry with password=s3cret later"))
        assertEquals("form token=*** sent to http://x.tv/…", UrlMask.maskTvText("form token=abc sent to http://x.tv/login?pass=s3cret"))
    }

    @Test
    fun `an error quoting a caller's long address reads back as its host alone`() {
        val text = "HTTP 404 for http://evil.example.com:8080/${"0123456789".repeat(40)}.ts?k=345678#4567890123"
        assertEquals("HTTP 404 for http://evil.example.com:8080/…", UrlMask.maskTvText(text))
    }

    @Test
    fun `a cut or masked copy is recognised, and cutting it again changes nothing`() {
        val cut = UrlMask.origin("http://x.tv:8080/playlist/me/s3cret/m3u_plus")
        listOf(cut, "http://…", "…", "x.tv/…").forEach { assertEquals(it, UrlMask.origin(it)) }
        val text = UrlMask.maskTvText("http://me:pw@x.tv/live/me/s3cret/1.ts?token=1 then password=s")
        assertEquals(text, UrlMask.maskTvText(text))
        listOf(cut, UrlMask.origin("http://me@x.tv"), "http://x.tv/a?key=***", "http://x.tv/live/•••/•••/1.ts")
            .forEach { assertTrue(it, UrlMask.isMasked(it)) }
        listOf("http://x.tv:8080", "http://me:pw@x.tv/a?password=s").forEach { assertFalse(it, UrlMask.isMasked(it)) }
    }

    @Test
    fun `login query values are found whatever their case`() {
        listOf(
            "http://x.tv/get.php?username=me&password=s3cret&type=m3u",
            "http://x.tv/a.m3u?PASS=a", "http://x.tv/a.m3u?Token=b", "http://x.tv/a.m3u?key=c",
            "http://x.tv/a.m3u?MAC=00:1A:79:12:34:56", "http://x.tv/a.m3u?pwd=d#top",
            "https://cdn.x.tv/a.m3u8?auth=abc", "https://cdn.x.tv/a.m3u8?sig=def", "https://cdn.x.tv/a.m3u8?signature=ghi&page=2",
        ).forEach { assertTrue(it, UrlMask.hasSecret(it)) }
    }

    @Test
    fun `xtream path logins are found the way core finds them`() {
        listOf("live", "movie", "series", "timeshift").forEach { kind ->
            assertTrue(kind, UrlMask.hasSecret("http://x.tv:8080/$kind/me/s3cret/123.ts"))
        }
    }

    @Test
    fun `secrets are detected`() {
        assertTrue(UrlMask.hasSecret("http://me:pw@x.tv/a"))
        assertTrue(UrlMask.hasSecret("http://x.tv/a?token=1"))
        assertTrue(UrlMask.hasSecret("http://x.tv/series/me/pw/1.mkv"))
        assertFalse(UrlMask.hasSecret("http://x.tv/a?page=1"))
    }

    @Test
    fun `a user name alone or a name that ends like a login word is no secret`() {
        assertTrue(UrlMask.hasSecret("http://x.tv/get.php?username=me&password=pw&type=m3u"))
        assertTrue(UrlMask.hasSecret("http://me:p@ss@x.tv/a.m3u"))
        listOf(
            "http://x.tv/get.php?username=me&type=m3u",
            "http://x.tv/a.m3u?user=me&metadata=full&monkey=1",
            "https://me@x.tv/a.m3u",
            "http://x.tv:8080/a.m3u",
            "http://x.tv/a.m3u?password=",
        ).forEach { assertFalse(it, UrlMask.hasSecret(it)) }
    }
}
