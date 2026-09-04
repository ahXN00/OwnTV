package tv.own.owntv.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.theme.GlassConfig
import tv.own.owntv.core.theme.GlassPreset
import tv.own.owntv.core.theme.GlassSurface

class GlassConfigTest {

    @Test
    fun `surface bit assignments remain stable for stored settings`() {
        assertEquals(
            listOf(
                "PANELS", "SIDEBAR", "PREVIEW", "DIALOGS", "TOPBAR", "CARDS", "MINI_PLAYER",
                // Appended for the mobile app, and only ever appended: the stored scope is a
                // bitmask over these ordinals.
                "PLAYER_CONTROLS", "TOASTS",
            ),
            GlassSurface.entries.map { it.name },
        )
        // The two appended surfaces have no ten-foot caller, so the television neither offers nor
        // stores them; its own set is still the original seven bits.
        assertEquals(0b111_1111, GlassConfig(scope = ALL_GLASS_SURFACES).toBitmask())
        assertEquals(
            setOf(GlassSurface.PLAYER_CONTROLS, GlassSurface.TOASTS),
            GlassSurface.entries.toSet() - ALL_GLASS_SURFACES,
        )
    }

    @Test
    fun `existing preset ladder keeps exact material values`() {
        assertEquals(0.24f, GlassPreset.ULTRA_CLEAR.resolveAlpha(0f), 0f)
        assertEquals(0.35f, GlassPreset.ULTRA_CLEAR.resolveBlur(0f), 0f)
        assertEquals(0.38f, GlassPreset.CLEAR.resolveAlpha(0f), 0f)
        assertEquals(0.62f, GlassPreset.CLEAR.resolveBlur(0f), 0f)
        assertEquals(0.56f, GlassPreset.BALANCED.resolveAlpha(0f), 0f)
        assertEquals(0.78f, GlassPreset.BALANCED.resolveBlur(0f), 0f)
        assertEquals(0.74f, GlassPreset.TINTED.resolveAlpha(0f), 0f)
        assertEquals(0.88f, GlassPreset.TINTED.resolveBlur(0f), 0f)
        assertEquals(0.92f, GlassPreset.OPAQUE.resolveAlpha(0f), 0f)
        assertEquals(1.00f, GlassPreset.OPAQUE.resolveBlur(0f), 0f)
        assertEquals(0.27f, GlassPreset.CUSTOM.resolveAlpha(0.27f), 0f)
        assertEquals(0.43f, GlassPreset.CUSTOM.resolveBlur(0.43f), 0f)
    }

    @Test
    fun `highlight default preserves the original focused material`() {
        assertEquals(0.55f, GlassConfig.DEFAULT_HIGHLIGHT_STRENGTH, 0f)
        assertEquals(GlassConfig.DEFAULT_HIGHLIGHT_STRENGTH, GlassConfig().highlightStrength, 0f)
        assertFalse(GlassConfig().allowFullTransparency)
        assertTrue(GlassConfig().depthEffects)
    }

    @Test
    fun `bitmask restore preserves adaptive contrast and depth escape hatches`() {
        val restored = GlassConfig.fromBitmask(
            bits = 0,
            allowFullTransparency = true,
            depthEffects = false,
        )

        assertTrue(restored.allowFullTransparency)
        assertFalse(restored.depthEffects)
    }

    @Test
    fun `legibility floor raises dark fill over bright wallpaper`() {
        val required = requiredLegibilityAlpha(
            backdropLuma = 1f,
            fillLuma = 0.01f,
            textLuma = 1f,
        )

        assertTrue(required > 0.7f)
        assertEquals(
            0f,
            requiredLegibilityAlpha(backdropLuma = 0.01f, fillLuma = 0.01f, textLuma = 1f),
            0.001f,
        )
    }

    @Test
    fun `legibility floor raises light fill over dark wallpaper for dark text`() {
        val required = requiredLegibilityAlpha(
            backdropLuma = 0f,
            fillLuma = 1f,
            textLuma = 0f,
        )

        assertTrue(required > 0.1f)
        assertEquals(
            0f,
            requiredLegibilityAlpha(backdropLuma = 1f, fillLuma = 1f, textLuma = 0f),
            0.001f,
        )
    }

    @Test
    fun `ten-level frost pyramid stays inside former backdrop memory ceiling`() {
        val levels = frostMipDimensions(baseWidth = 448, baseHeight = 252)
        val pyramidPixels = levels.sumOf { (width, height) -> width.toLong() * height }
        val formerBackdropPixels = 768L * 432L

        assertEquals(10, levels.size)
        assertTrue(pyramidPixels <= formerBackdropPixels)
        assertTrue(levels.zipWithNext().all { (a, b) -> b.first <= a.first && b.second <= a.second })
    }

    @Test
    fun `bitmask restore preserves and clamps highlight strength`() {
        val restored = GlassConfig.fromBitmask(
            bits = 1 shl GlassSurface.CARDS.ordinal,
            highlightStrength = 0.85f,
        )
        val clamped = GlassConfig.fromBitmask(bits = 0, highlightStrength = 1.4f)

        assertEquals(0.85f, restored.highlightStrength, 0f)
        assertEquals(1f, clamped.highlightStrength, 0f)
    }

    @Test
    fun `stored preset name wins and resolves its material values`() {
        val preset = GlassPreset.fromStored("TINTED", customAlpha = 0.21f, customBlur = 0.32f)

        assertEquals(GlassPreset.TINTED, preset)
        assertEquals(0.74f, preset.resolveAlpha(0.21f), 0f)
        assertEquals(0.88f, preset.resolveBlur(0.32f), 0f)
    }

    @Test
    fun `missing preset recognizes balanced values`() {
        assertEquals(
            GlassPreset.BALANCED,
            GlassPreset.fromStored(null, customAlpha = 0.56f, customBlur = 0.78f),
        )
    }

    @Test
    fun `legacy user values migrate to custom without being changed`() {
        val alpha = 0.63f
        val blur = 0.41f
        val preset = GlassPreset.fromStored(null, customAlpha = alpha, customBlur = blur)
        val config = GlassConfig.fromBitmask(
            bits = 1 shl GlassSurface.PANELS.ordinal,
            alpha = alpha,
            blurStrength = blur,
            preset = preset,
        )

        assertEquals(GlassPreset.CUSTOM, preset)
        assertEquals(alpha, config.alpha, 0f)
        assertEquals(blur, config.blurStrength, 0f)
    }

    @Test
    fun `surface bitmask round trip preserves every selected role`() {
        val selected = setOf(GlassSurface.SIDEBAR, GlassSurface.DIALOGS, GlassSurface.MINI_PLAYER)
        val encoded = GlassConfig(scope = selected, preset = GlassPreset.CUSTOM).toBitmask()
        val decoded = GlassConfig.fromBitmask(encoded, preset = GlassPreset.CUSTOM)

        assertEquals(selected, decoded.scope)
        assertTrue(decoded.enabled)
        assertTrue(decoded.isGlassy(GlassSurface.DIALOGS))
        assertFalse(decoded.isGlassy(GlassSurface.PREVIEW))
    }

    @Test
    fun `preset values resolve when restoring a bitmask`() {
        val config = GlassConfig.fromBitmask(
            bits = 1 shl GlassSurface.CARDS.ordinal,
            alpha = 0.12f,
            blurStrength = 0.23f,
            preset = GlassPreset.CLEAR,
        )

        assertEquals(GlassPreset.CLEAR, config.preset)
        assertEquals(0.38f, config.alpha, 0f)
        assertEquals(0.62f, config.blurStrength, 0f)
    }

    @Test
    fun `material role table remains coherent and pinned`() {
        assertEquals(
            GlassMaterial(0.12f, 0.30f, 0.18f, 0.34f, 0.78f, 0.30f, GlassDepth.FLOATING, true),
            GlassSurface.DIALOGS.material,
        )
        assertEquals(
            GlassMaterial(0.04f, 0.24f, 0.15f, 0.34f, 0.72f, 0.22f, GlassDepth.CHROME, true),
            GlassSurface.TOPBAR.material,
        )
        assertEquals(GlassSurface.TOPBAR.material, GlassSurface.SIDEBAR.material)
        assertEquals(GlassSurface.TOPBAR.material, GlassSurface.MINI_PLAYER.material)
        assertEquals(
            GlassMaterial(0f, 0.20f, 0.11f, 0.32f, 0.68f, 0.18f, GlassDepth.CONTAINER, true),
            GlassSurface.PANELS.material,
        )
        assertEquals(GlassSurface.PANELS.material, GlassSurface.PREVIEW.material)
        assertEquals(
            GlassMaterial(-0.08f, 0.18f, 0.06f, 0.30f, 0.64f, 0.14f, GlassDepth.INLINE, false),
            GlassSurface.CARDS.material,
        )
    }

    @Test
    fun `layer budget allows one frost pass then progressively simplifies`() {
        assertEquals(
            GlassLayerTreatment(frost = true, glint = true, brightRim = true, darkEdge = true),
            glassLayerTreatment(1),
        )
        assertEquals(
            GlassLayerTreatment(frost = false, glint = true, brightRim = true, darkEdge = true),
            glassLayerTreatment(2),
        )
        assertEquals(
            GlassLayerTreatment(frost = false, glint = false, brightRim = false, darkEdge = true),
            glassLayerTreatment(3),
        )
        assertEquals(glassLayerTreatment(3), glassLayerTreatment(9))
    }
}
