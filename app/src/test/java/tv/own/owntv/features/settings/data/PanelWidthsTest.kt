package tv.own.owntv.features.settings.data

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.PanelShares
import tv.own.owntv.core.settings.PanelWidthLimits
import tv.own.owntv.core.settings.CINEMATIC_DETAILS_MAX
import tv.own.owntv.core.settings.balanceToTotal

class PanelWidthsTest {

    @Test
    fun `zero is valid only for the third panel when total is 100`() {
        assertTrue(PanelShares(category = 40, list = 60, preview = 0).isValid)
        assertFalse(PanelShares(category = 0, list = 80, preview = 20).isValid)
        assertFalse(PanelShares(category = 20, list = 70, preview = 0).isValid)
        assertFalse(PanelShares(category = 15, list = 80, preview = 5).isValid)
    }

    @Test
    fun `balancing preserves a hidden third panel`() {
        val balanced = balanceToTotal(PanelShares(category = 35, list = 55, preview = 0))

        assertEquals(0, balanced.preview)
        assertEquals(100, balanced.total)
        assertTrue(balanced.category >= PanelWidthLimits.MIN)
        assertTrue(balanced.list >= PanelWidthLimits.MIN)
    }

    @Test
    fun `balancing never lets category or list become zero`() {
        val balanced = balanceToTotal(PanelShares(category = 0, list = 90, preview = 10))

        assertEquals(100, balanced.total)
        assertTrue(balanced.category >= PanelWidthLimits.MIN)
        assertTrue(balanced.list >= PanelWidthLimits.MIN)
    }

    @Test
    fun `hidden preview reserves divider and its two surrounding gaps`() {
        val widths = computePanelWidths(
            shares = PanelShares(category = 40, list = 60, preview = 0),
            total = 1_000.dp,
        )

        assertEquals(0f, widths.preview.value, 0f)
        assertEquals(975f, widths.category.value + widths.list.value, 0.01f)
    }

    @Test
    fun `visible preview reserves divider and all three column gaps`() {
        val widths = computePanelWidths(
            shares = PanelShares(category = 20, list = 50, preview = 30),
            total = 1_000.dp,
        )

        assertEquals(963f, widths.category.value + widths.list.value + widths.preview.value, 0.01f)
        assertTrue(widths.preview.value > 0f)
    }

    // --- Cinematic layout: two columns, and a details height that is NOT one of the shares ---

    @Test
    fun `cinematic splits the row between two columns only`() {
        val spec = computeCinematicLayout(
            shares = PanelShares(category = 20, list = 80, preview = 0),
            detailsPercent = 35,
            totalWidth = 1_000.dp,
            totalHeight = 600.dp,
        )

        // Two columns, so only the divider and two gaps are reserved - never the preview's.
        val content = 1_000f - browsePanelGapTotal(previewVisible = false).value
        assertEquals(content, spec.category.value + spec.content.value, 0.01f)
        assertEquals(content * 20f / 100f, spec.category.value, 0.01f)
    }

    @Test
    fun `cinematic details height is independent of the width shares`() {
        val narrow = computeCinematicLayout(
            shares = PanelShares(category = 20, list = 80, preview = 0),
            detailsPercent = 40,
            totalWidth = 1_000.dp,
            totalHeight = 600.dp,
        )
        val wide = computeCinematicLayout(
            shares = PanelShares(category = 50, list = 50, preview = 0),
            detailsPercent = 40,
            totalWidth = 1_000.dp,
            totalHeight = 600.dp,
        )

        // Changing how the row is split must not move the detail block's height at all.
        assertEquals(240f, narrow.detailsHeight.value, 0.01f)
        assertEquals(narrow.detailsHeight.value, wide.detailsHeight.value, 0.01f)
    }

    @Test
    fun `cinematic zero details height really means zero`() {
        val spec = computeCinematicLayout(
            shares = PanelShares(category = 20, list = 80, preview = 0),
            detailsPercent = 0,
            totalWidth = 1_000.dp,
            totalHeight = 600.dp,
        )

        assertEquals(0f, spec.detailsHeight.value, 0.01f)
    }

    @Test
    fun `cinematic never lets the details block eat the whole screen`() {
        val spec = computeCinematicLayout(
            shares = PanelShares(category = 20, list = 80, preview = 0),
            detailsPercent = 95,
            totalWidth = 1_000.dp,
            totalHeight = 600.dp,
        )

        assertEquals(600f * CINEMATIC_DETAILS_MAX / 100f, spec.detailsHeight.value, 0.01f)
    }

    @Test
    fun `cinematic tolerates width shares that do not add up to a hundred`() {
        val spec = computeCinematicLayout(
            shares = PanelShares(category = 10, list = 10, preview = 0),
            detailsPercent = 35,
            totalWidth = 1_000.dp,
            totalHeight = 600.dp,
        )

        val content = 1_000f - browsePanelGapTotal(previewVisible = false).value
        assertEquals(content, spec.category.value + spec.content.value, 0.01f)
        assertEquals(content / 2f, spec.category.value, 0.01f)
    }

    @Test
    fun `cinematic widths are always savable`() {
        // Every one of these used to be reachable with the steppers, and each produced a draft that
        // Okay silently refused to write - a total of 100 with one column over the 80% cap.
        listOf(
            PanelShares(category = 20, list = 50, preview = 30),
            PanelShares(category = 10, list = 60, preview = 30),
            PanelShares(category = 90, list = 10, preview = 0),
            PanelShares(category = 5, list = 5, preview = 5),
        ).forEach { stored ->
            val widths = cinematicWidths(stored)

            assertTrue("not savable: $stored -> $widths", widths.isValid)
            assertEquals(0, widths.preview)
            assertEquals(PanelWidthLimits.TOTAL, widths.total)
        }
    }

    @Test
    fun `cinematic lets the category go down to the minimum`() {
        assertEquals(PanelShares(10, 90, 0), cinematicWidths(PanelShares(10, 90, 0)))
        assertEquals(PanelShares(15, 85, 0), cinematicWidths(PanelShares(15, 85, 0)))
    }
}
