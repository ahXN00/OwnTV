package tv.own.owntv.features.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideRowFocusArgsTest {

    @Test
    fun `focused row gets the cursor and cell mode`() {
        val args = guideRowFocusArgs(
            index = 3,
            focusedRow = 3,
            cursorTime = 1_700_000_000_000L,
            inCellMode = true,
            gridHasFocus = true,
        )
        assertEquals(1_700_000_000_000L, args.cursorTime)
        assertTrue(args.cellMode)
        assertTrue(args.selectedRow)
    }

    @Test
    fun `unfocused row gets no cursor and no cell mode`() {
        val args = guideRowFocusArgs(
            index = 2,
            focusedRow = 3,
            cursorTime = 1_700_000_000_000L,
            inCellMode = true,
            gridHasFocus = true,
        )
        assertEquals(-1L, args.cursorTime)
        assertEquals(NO_CURSOR, args.cursorTime)
        assertFalse(args.cellMode)
        assertFalse(args.selectedRow)
    }

    @Test
    fun `selectedRow needs the focused index and grid focus`() {
        assertFalse(
            guideRowFocusArgs(1, focusedRow = 1, cursorTime = 5L, inCellMode = true, gridHasFocus = false).selectedRow,
        )
        assertFalse(
            guideRowFocusArgs(0, focusedRow = 1, cursorTime = 5L, inCellMode = true, gridHasFocus = true).selectedRow,
        )
        assertTrue(
            guideRowFocusArgs(1, focusedRow = 1, cursorTime = 5L, inCellMode = false, gridHasFocus = true).selectedRow,
        )
    }

    @Test
    fun `cell mode stays off when not in cell mode`() {
        val args = guideRowFocusArgs(
            index = 4,
            focusedRow = 4,
            cursorTime = 9L,
            inCellMode = false,
            gridHasFocus = true,
        )
        assertFalse(args.cellMode)
        assertEquals(9L, args.cursorTime)
        assertTrue(args.selectedRow)
    }
}
