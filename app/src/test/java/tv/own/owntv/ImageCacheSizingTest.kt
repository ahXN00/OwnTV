package tv.own.owntv

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCacheSizingTest {

    private val mb = 1024L * 1024

    @Test
    fun `256 MB heap is 15 percent`() {
        assertEquals((256L * mb * 0.15).toLong(), imageMemoryCacheBytes(256L * mb, lowRam = false))
    }

    @Test
    fun `1 GB heap hits the 64 MB cap`() {
        assertEquals(64L * mb, imageMemoryCacheBytes(1024L * mb, lowRam = false))
    }

    @Test
    fun `low-RAM keeps 10 percent with no floor`() {
        assertEquals((128L * mb * 0.10).toLong(), imageMemoryCacheBytes(128L * mb, lowRam = true))
    }
}
