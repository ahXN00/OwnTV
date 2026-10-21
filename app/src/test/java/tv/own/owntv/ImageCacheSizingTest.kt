package tv.own.owntv

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCacheSizingTest {

    private val mb = 1024L * 1024

    @Test
    fun `256 MB heap is 20 percent`() {
        assertEquals((256L * mb * 0.2).toLong(), imageMemoryCacheBytes(256L * mb, lowRam = false))
    }

    @Test
    fun `192 MB low-RAM hits the 24 MB floor`() {
        assertEquals(24L * mb, imageMemoryCacheBytes(192L * mb, lowRam = true))
    }

    @Test
    fun `1 GB heap hits the 96 MB ceiling`() {
        assertEquals(96L * mb, imageMemoryCacheBytes(1024L * mb, lowRam = false))
    }
}
