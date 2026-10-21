package tv.own.owntv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlurCacheTest {

    @Test
    fun keepsAtMostEightEntries() {
        val map = LruMap<String>(8)
        repeat(10) { map.put("u$it", "v$it") }
        assertEquals(8, map.size())
        assertNull(map.get("u0"))
        assertNull(map.get("u1"))
        assertEquals("v9", map.get("u9"))
        assertEquals("v2", map.get("u2"))
    }

    @Test
    fun evictsLeastRecentlyUsed() {
        val map = LruMap<String>(8)
        repeat(8) { map.put("u$it", "v$it") }
        map.put("u8", "v8")
        assertEquals(8, map.size())
        assertNull(map.get("u0"))
        assertEquals("v1", map.get("u1"))
        assertEquals("v8", map.get("u8"))
    }

    @Test
    fun getRefreshesRecency() {
        val map = LruMap<String>(8)
        repeat(8) { map.put("u$it", "v$it") }
        assertEquals("v0", map.get("u0"))
        map.put("u8", "v8")
        assertEquals(8, map.size())
        assertEquals("v0", map.get("u0"))
        assertNull(map.get("u1"))
    }

    @Test
    fun keyedByUrl() {
        val map = LruMap<String>(8)
        map.put("a", "1")
        map.put("b", "2")
        map.put("a", "3")
        assertEquals("3", map.get("a"))
        assertEquals("2", map.get("b"))
        assertEquals(2, map.size())
    }
}
