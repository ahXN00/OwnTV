package tv.own.owntv.features.categorylock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.customize.ItemCategory
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.database.entity.ReminderEntity

/** The lock-aware flows behind the rail and More counts, Recordings and programme reminders. */
class LockedFlowsTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val sealed = MutableStateFlow<Set<Long>>(emptySet())

    /** Channel id to its category: 1 and 2 are in 10, 3 is in 20, 4 has none, any other is gone. */
    private val categories = mapOf(1L to 10L, 2L to 10L, 3L to 20L)
    private val categoryOf: suspend (Long) -> Long? = { categories[it] }
    private val channelCategory: suspend (Long) -> ItemCategory = { id ->
        if (id in 1L..4L) ItemCategory.Found(categories[id]) else ItemCategory.Unresolved
    }

    @After
    fun tearDown() = scope.cancel()

    private fun <T> Flow<T>.values(): List<T> = mutableListOf<T>().also { seen -> onEach { seen += it }.launchIn(scope) }

    @Test
    fun aCountRunsThePlainQueryUntilACategoryIsSealedThenLeavesItOut() {
        val asked = mutableListOf<String>()
        val counts = lockAwareCount(sealed, { asked += "plain"; flowOf(5) }) { ids -> asked += "excluding $ids"; flowOf(3) }.values()
        sealed.value = setOf(10)
        sealed.value = emptySet()
        assertEquals(listOf(5, 3, 5), counts)
        assertEquals(listOf("plain", "excluding [10]", "plain"), asked)
    }

    @Test
    fun theLastWatchedIsLookedAtAgainWhenALockClosesOrOpens() {
        val last = MutableStateFlow("Ch 1")
        val seen = last.recheckedOn(sealed).values()
        sealed.value = setOf(10)
        sealed.value = emptySet()
        assertEquals(listOf("Ch 1", "Ch 1", "Ch 1"), seen)
        last.value = "Ch 2"
        assertEquals("Ch 2", seen.last())
    }

    private fun recording(id: Long, channelId: Long) = RecordingEntity(
        id = id, profileId = 1, sourceId = 1, channelId = channelId, channelName = "Ch $channelId",
        streamUrl = "http://x/$channelId.ts", title = "Show $id",
        programmeStartMs = 0, programmeStopMs = 1, startMs = 0, stopMs = 1,
    )

    @Test
    fun aRecordingOfASealedChannelLeavesTheListUntilThePinOpensIt() {
        val rows = listOf(recording(1, channelId = 1), recording(2, channelId = 3), recording(3, channelId = 4), recording(4, channelId = 99))
        val anySealed = MutableStateFlow(false)
        val lists = flowOf(rows).withoutSealed(sealed, anySealed, channelCategory).values()
        sealed.value = setOf(10)
        anySealed.value = true
        // Channel 99 is gone, so its recording cannot be tied to a category and stays out with the lock.
        assertEquals(listOf(2L, 3L), lists.last().map { it.id })
        sealed.value = emptySet()
        anySealed.value = false
        assertEquals(listOf(1L, 2L, 3L, 4L), lists.last().map { it.id })
    }

    @Test
    fun aRecordingOfAGoneChannelStaysOutWhileAnyLockIsClosed() {
        val rows = listOf(recording(1, channelId = 1), recording(2, channelId = 99))
        val anySealed = MutableStateFlow(true)
        // A closed lock elsewhere (a custom category, another section) seals no folder of this list.
        val lists = flowOf(rows).withoutSealed(sealed, anySealed, channelCategory).values()
        assertEquals(listOf(1L), lists.last().map { it.id })
        anySealed.value = false
        assertEquals(listOf(1L, 2L), lists.last().map { it.id })
    }

    private fun reminder(id: Long, profileId: Long, channelId: Long) = ReminderEntity(
        id = id, profileId = profileId, channelId = channelId, channelName = "Ch $channelId",
        title = "Show $id", startMs = 0, stopMs = 1, leadMinutes = 0, createdAt = 0,
    )

    @Test
    fun aDueReminderOnASealedChannelWaitsAndTheNextOneComesUp() {
        val due = MutableStateFlow(listOf(reminder(1, profileId = 2, channelId = 3), reminder(2, profileId = 1, channelId = 1), reminder(3, profileId = 1, channelId = 3)))
        val shown = due.firstUnsealed(profileId = 1, sealed = sealed, categoryOf = categoryOf).values()
        assertEquals(2L, shown.last()?.id)
        sealed.value = setOf(10)
        assertEquals(3L, shown.last()?.id)
        sealed.value = setOf(10, 20)
        assertEquals(null, shown.last())
        sealed.value = emptySet()
        assertEquals(2L, shown.last()?.id)
    }

    @Test
    fun theFilePlayedFromDownloadsOrRecordingsGivesItsCategoryWhileThePlayerIsOnIt() {
        val files = PlayingFileCategory()
        files.started("/data/film.mp4", categoryId = 10)
        assertEquals(10L, files.categoryOf("/data/film.mp4"))
        assertEquals(null, files.categoryOf("http://x/1.ts"))
        assertEquals(null, files.categoryOf(null))
        files.started("/data/other.mp4", categoryId = null)
        assertEquals(null, files.categoryOf("/data/film.mp4"))
        assertEquals(null, files.categoryOf("/data/other.mp4"))
    }
}
