package tv.own.owntv.features.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SensitiveScreensTest {
    private val screens = SensitiveScreens()

    @Test
    fun `open until the last marker closes, and a second close counts once`() {
        assertFalse(screens.anyOpen)
        val leaving = screens.enter()
        val arriving = screens.enter()
        leaving.close()
        leaving.close()
        assertTrue(screens.anyOpen)
        arriving.close()
        assertFalse(screens.anyOpen)
    }

    @Test
    fun `markers from many threads balance out`() {
        val pool = Executors.newFixedThreadPool(8)
        val done = CountDownLatch(800)
        repeat(800) {
            pool.execute {
                screens.enter().close()
                done.countDown()
            }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdown()
        assertFalse(screens.anyOpen)
    }
}
