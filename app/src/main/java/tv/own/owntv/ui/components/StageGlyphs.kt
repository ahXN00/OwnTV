package tv.own.owntv.ui.components

import tv.own.owntv.core.ui.stage.StageGlyph

/**
 * The Stage line icons, as SVG path data on the same 24×24 grid [OwnTVIcon] draws on. The
 * paths live in core ([StageGlyph], shared with the phone); its names are the [OwnTVIcon] entries. The
 * phone-only marks (PLUS, MOON, EYE) have no entry here, so the TV keeps drawing ADD and BEDTIME its own
 * way, and has no use for EYE.
 */
internal val StageGlyphPaths: Map<OwnTVIcon, String> = StageGlyph.entries.mapNotNull { glyph ->
    OwnTVIcon.entries.firstOrNull { it.name == glyph.name }?.let { it to glyph.path }
}.toMap()
