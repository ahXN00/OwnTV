package tv.own.owntv.features.categorylock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which PIN prompt closes move focus to the opened category's first item. */
class ListPinFocusTest {

    @Test
    fun theButtonsPromptOpeningTheCategoryWaitsForTheFirstItem() {
        val focus = ListPinFocus()
        focus.askedFromList()
        assertTrue(focus.takeClose(opened = true))
        assertTrue(focus.awaitingItems)
        assertFalse(focus.refocusPin)
    }

    @Test
    fun theButtonsPromptLeftShutReturnsToTheButton() {
        val focus = ListPinFocus()
        focus.askedFromList()
        assertTrue(focus.takeClose(opened = false))
        assertFalse(focus.awaitingItems)
        assertTrue(focus.refocusPin)
    }

    @Test
    fun theSheetsPromptCloseIsLeftToTheScreen() {
        val focus = ListPinFocus()
        assertFalse(focus.takeClose(opened = true))
        assertFalse(focus.awaitingItems)
    }

    @Test
    fun theSheetOpeningTheCategoryWaitsForTheFirstItemToo() {
        val focus = ListPinFocus()
        assertFalse(focus.takeClose(opened = true))
        focus.openedFromCategories()
        assertTrue(focus.awaitingItems)
        assertFalse(focus.refocusPin)
    }
}
