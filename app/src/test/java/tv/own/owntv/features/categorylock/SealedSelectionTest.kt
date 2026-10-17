package tv.own.owntv.features.categorylock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the VOD hero and details card show once the focused title's category is locked or relocked. */
class SealedSelectionTest {

    private data class Title(val id: Long, val categoryId: Long?)

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val selection = MutableStateFlow<Title?>(null)
    private val sealed = MutableStateFlow<Set<Long>>(emptySet())

    init {
        scope.clearWhenSealed(selection, sealed) { it.categoryId }
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun lockingTheShownTitlesCategoryClearsIt() {
        selection.value = Title(1, categoryId = 10)
        sealed.value = setOf(10)
        assertNull(selection.value)
    }

    @Test
    fun aTitleOfAnotherCategoryStays() {
        selection.value = Title(1, categoryId = 20)
        sealed.value = setOf(10)
        assertEquals(Title(1, 20), selection.value)
    }

    @Test
    fun aTitleWithNoCategoryIsNeverSealed() {
        selection.value = Title(1, categoryId = null)
        sealed.value = setOf(10)
        assertEquals(Title(1, null), selection.value)
    }

    @Test
    fun aTitleOfAnAlreadySealedCategoryIsClearedWhenSelected() {
        sealed.value = setOf(10)
        selection.value = Title(2, categoryId = 10)
        assertNull(selection.value)
    }

    @Test
    fun openingTheLockAgainKeepsTheNextTitle() {
        selection.value = Title(1, categoryId = 10)
        sealed.value = setOf(10)
        sealed.value = emptySet()
        selection.value = Title(3, categoryId = 10)
        assertEquals(Title(3, 10), selection.value)
    }
}
