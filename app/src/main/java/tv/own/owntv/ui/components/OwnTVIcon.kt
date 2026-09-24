package tv.own.owntv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Brand icon set drawn directly with Canvas so we don't depend on which glyphs ship in
 * `material-icons-core` (and to keep the APK lean). All icons are designed on a normalized 24×24
 * grid and scaled to fit. Line style by default; some support [filled].
 */
enum class OwnTVIcon {
    LIVE_TV, MOVIES, SERIES, DOWNLOADS, MENU, STAR, PLAY, SEARCH, HOME, HISTORY,
    PERSON, ADD, SETTINGS, PALETTE, THEME, ZOOM, PLAYLIST, EPG, VIDEO, SHARE, CHEVRON, CHEVRON_UP, CHEVRON_DOWN, FAVORITE,
    PAUSE, REWIND, FORWARD, AUDIO, SUBTITLE, SKIP_NEXT, SKIP_PREVIOUS,
    BACK, VOLUME_HIGH, VOLUME_LOW, VOLUME_MUTE, ASPECT, PIP, CLOSE,
    SORT, SWAP, HEADPHONES, EXPAND,
    IMAGE, INFO, LANGUAGE, GEAR, SPARKLE,
    CATCHUP,
    WEATHER, NETWORK, TEXT_SIZE, BACKUP, REFRESH, POWER, MOTION, GLOW, WARNING, LIST_GRID,
    // Marks that exist so one glyph stops doing two jobs: NETWORK was both proxy and DNS, EXPAND was
    // both panel width and focus highlight, SKIP_NEXT was both channel paging and autoplay-next.
    DNS, PANEL_WIDTH, FOCUS_HIGHLIGHT, CH_NAV, AUTOPLAY_NEXT, LIVE_DOT, SEEK_BACK, SEEK_FORWARD, MORE,
    REMOTE_CHANNEL_UP, REMOTE_CHANNEL_DOWN, PAGE_TOWARD_FIRST, PAGE_TOWARD_LAST,
    // Where the files go. A gear said "settings for this screen"; the question this button actually
    // asks is "which folder?", and a folder is the one mark that says so without a word.
    FOLDER,
    // Local sync: the other device in the house, and the direction data leaves in. Before these, an
    // archive box stood for a paired phone, for "let this one be found", and for "send to it" — three
    // meanings on one screen, and all three already meant "backup" everywhere else in the app.
    PHONE, SEND,
    // The sleep timer (N17): a crescent moon, the phone's mark for the same button.
    BEDTIME,
}

@Composable
fun OwnTVIcon(
    icon: OwnTVIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f // scale: 24-unit grid -> px
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val stroke = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (icon) {
            OwnTVIcon.LIVE_TV -> {
                drawRoundRectStroke(p(3f, 8f), p(21f, 21f), 2.5f * s, tint, stroke)
                drawLineStroke(p(8f, 8f), p(12f, 3f), tint, stroke)
                drawLineStroke(p(16f, 8f), p(12f, 3f), tint, stroke)
            }
            OwnTVIcon.MOVIES -> {
                drawRoundRectStroke(p(3f, 9f), p(21f, 20f), 2f * s, tint, stroke)
                drawLineStroke(p(3f, 9f), p(21f, 6.5f), tint, stroke)
                drawLineStroke(p(7.5f, 9f), p(9f, 6.2f), tint, stroke)
                drawLineStroke(p(12f, 9f), p(13.5f, 5.8f), tint, stroke)
                drawLineStroke(p(16.5f, 9f), p(18f, 5.5f), tint, stroke)
            }
            OwnTVIcon.SERIES -> {
                drawRoundRectStroke(p(6f, 4f), p(21f, 14f), 2f * s, tint, stroke)
                drawLineStroke(p(3f, 8f), p(3f, 20f), tint, stroke)
                drawLineStroke(p(3f, 20f), p(18f, 20f), tint, stroke)
            }
            OwnTVIcon.DOWNLOADS -> {
                drawLineStroke(p(12f, 3f), p(12f, 15f), tint, stroke)
                drawLineStroke(p(7f, 10f), p(12f, 15f), tint, stroke)
                drawLineStroke(p(17f, 10f), p(12f, 15f), tint, stroke)
                drawLineStroke(p(5f, 20f), p(19f, 20f), tint, stroke)
            }
            OwnTVIcon.MENU -> {
                drawLineStroke(p(4f, 7f), p(20f, 7f), tint, stroke)
                drawLineStroke(p(4f, 12f), p(20f, 12f), tint, stroke)
                drawLineStroke(p(4f, 17f), p(20f, 17f), tint, stroke)
            }
            OwnTVIcon.SORT -> { // descending bars — classic sort glyph
                drawLineStroke(p(4f, 7f), p(20f, 7f), tint, stroke)
                drawLineStroke(p(4f, 12f), p(14f, 12f), tint, stroke)
                drawLineStroke(p(4f, 17f), p(9f, 17f), tint, stroke)
            }
            OwnTVIcon.HISTORY -> {
                drawCircleStroke(p(12f, 12f), 9f * s, tint, stroke)
                drawLineStroke(p(12f, 7f), p(12f, 12f), tint, stroke)
                drawLineStroke(p(12f, 12f), p(16f, 14f), tint, stroke)
            }
            OwnTVIcon.SEARCH -> {
                drawCircleStroke(p(10.5f, 10.5f), 6.5f * s, tint, stroke)
                drawLineStroke(p(15.5f, 15.5f), p(20f, 20f), tint, stroke)
            }
            OwnTVIcon.HOME -> {
                val roof = Path().apply {
                    moveTo(p(12f, 3f).x, p(12f, 3f).y)
                    lineTo(p(3f, 12f).x, p(3f, 12f).y)
                    lineTo(p(21f, 12f).x, p(21f, 12f).y)
                    close()
                }
                drawPath(roof, tint, style = stroke)
                drawRoundRectStroke(p(5f, 12f), p(19f, 21f), 2f * s, tint, stroke)
                drawLineStroke(p(10f, 21f), p(10f, 15f), tint, stroke)
                drawLineStroke(p(14f, 21f), p(14f, 15f), tint, stroke)
                drawLineStroke(p(10f, 15f), p(14f, 15f), tint, stroke)
            }
            OwnTVIcon.STAR -> {
                val star = starPath(center = p(12f, 12f), outer = 9f * s, inner = 3.7f * s)
                if (filled) drawPath(star, tint, style = Fill) else drawPath(star, tint, style = stroke)
            }
            OwnTVIcon.FAVORITE -> {
                // Heart — the app-wide favourite mark (a star reads as a *rating* on posters, which is
                // exactly what PosterCard's rating badge uses STAR for).
                val heart = Path().apply {
                    moveTo(p(12f, 20.5f).x, p(12f, 20.5f).y)
                    cubicTo(
                        p(6.5f, 16.2f).x, p(6.5f, 16.2f).y,
                        p(3f, 12.8f).x, p(3f, 12.8f).y,
                        p(3f, 9f).x, p(3f, 9f).y,
                    )
                    cubicTo(
                        p(3f, 6f).x, p(3f, 6f).y,
                        p(5.3f, 3.8f).x, p(5.3f, 3.8f).y,
                        p(8f, 3.8f).x, p(8f, 3.8f).y,
                    )
                    cubicTo(
                        p(10f, 3.8f).x, p(10f, 3.8f).y,
                        p(11.4f, 5f).x, p(11.4f, 5f).y,
                        p(12f, 6.3f).x, p(12f, 6.3f).y,
                    )
                    cubicTo(
                        p(12.6f, 5f).x, p(12.6f, 5f).y,
                        p(14f, 3.8f).x, p(14f, 3.8f).y,
                        p(16f, 3.8f).x, p(16f, 3.8f).y,
                    )
                    cubicTo(
                        p(18.7f, 3.8f).x, p(18.7f, 3.8f).y,
                        p(21f, 6f).x, p(21f, 6f).y,
                        p(21f, 9f).x, p(21f, 9f).y,
                    )
                    cubicTo(
                        p(21f, 12.8f).x, p(21f, 12.8f).y,
                        p(17.5f, 16.2f).x, p(17.5f, 16.2f).y,
                        p(12f, 20.5f).x, p(12f, 20.5f).y,
                    )
                    close()
                }
                if (filled) drawPath(heart, tint, style = Fill) else drawPath(heart, tint, style = stroke)
            }
            OwnTVIcon.INFO -> {
                drawCircleStroke(p(12f, 12f), 9f * s, tint, stroke)
                drawCircle(tint, 1.2f * s, p(12f, 7.6f), style = Fill)
                drawLineStroke(p(12f, 11f), p(12f, 16.5f), tint, stroke)
            }
            OwnTVIcon.PLAY -> {
                val tri = Path().apply {
                    moveTo(p(8f, 5f).x, p(8f, 5f).y)
                    lineTo(p(19f, 12f).x, p(19f, 12f).y)
                    lineTo(p(8f, 19f).x, p(8f, 19f).y)
                    close()
                }
                if (filled) drawPath(tri, tint, style = Fill) else drawPath(tri, tint, style = stroke)
            }
            OwnTVIcon.PERSON -> {
                drawCircleStroke(p(12f, 8f), 3.6f * s, tint, if (filled) stroke else stroke)
                if (filled) drawCircle(tint, 3.6f * s, p(12f, 8f), style = Fill)
                // shoulders
                drawArc(
                    color = tint,
                    startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = p(5f, 13f), size = Size(14f * s, 14f * s), style = stroke,
                )
            }
            OwnTVIcon.ADD -> {
                drawLineStroke(p(12f, 5f), p(12f, 19f), tint, stroke)
                drawLineStroke(p(5f, 12f), p(19f, 12f), tint, stroke)
            }
        OwnTVIcon.SETTINGS -> {
                // "tune" sliders — clearer than a gear at small sizes
                drawLineStroke(p(4f, 8f), p(20f, 8f), tint, stroke)
                drawLineStroke(p(4f, 16f), p(20f, 16f), tint, stroke)
                drawCircle(tint, 2.6f * s, p(9f, 8f), style = Fill)
                drawCircle(tint, 2.6f * s, p(15f, 16f), style = Fill)
            }
            OwnTVIcon.PALETTE -> {
                drawArc(
                    color = tint, startAngle = 110f, sweepAngle = 320f, useCenter = false,
                    topLeft = p(3f, 3f), size = Size(18f * s, 18f * s), style = stroke,
                )
                drawCircle(tint, 1.3f * s, p(8.5f, 8f), style = Fill)
                drawCircle(tint, 1.3f * s, p(13f, 6.5f), style = Fill)
                drawCircle(tint, 1.3f * s, p(16.5f, 9.5f), style = Fill)
            }
            OwnTVIcon.THEME -> {
                // half-filled circle — classic dark-mode glyph
                drawCircleStroke(p(12f, 12f), 8f * s, tint, stroke)
                drawArc(
                    color = tint,
                    startAngle = -90f, sweepAngle = 180f, useCenter = true,
                    topLeft = p(4f, 4f), size = Size(16f * s, 16f * s), style = Fill,
                )
            }
            OwnTVIcon.ZOOM -> {
                // A frame with ⤢ inside — scale the whole picture. ASPECT keeps the corner marks, so
                // the two stopped being the same drawing.
                drawRoundRectStroke(p(4f, 5f), p(20f, 19f), 2f * s, tint, stroke)
                drawLineStroke(p(9f, 15f), p(15f, 9f), tint, stroke)
                drawLineStroke(p(15f, 9f), p(11.4f, 9f), tint, stroke)
                drawLineStroke(p(15f, 9f), p(15f, 12.6f), tint, stroke)
                drawLineStroke(p(9f, 15f), p(12.6f, 15f), tint, stroke)
                drawLineStroke(p(9f, 15f), p(9f, 11.4f), tint, stroke)
            }
            OwnTVIcon.PLAYLIST -> {
                drawLineStroke(p(4f, 7f), p(16f, 7f), tint, stroke)
                drawLineStroke(p(4f, 12f), p(16f, 12f), tint, stroke)
                drawLineStroke(p(4f, 17f), p(11f, 17f), tint, stroke)
                val tri = Path().apply {
                    moveTo(p(15f, 14f).x, p(15f, 14f).y)
                    lineTo(p(21f, 17f).x, p(21f, 17f).y)
                    lineTo(p(15f, 20f).x, p(15f, 20f).y)
                    close()
                }
                drawPath(tri, tint, style = Fill)
            }
            OwnTVIcon.EPG -> {
                // A time axis across the top with programme blocks of differing lengths staggered
                // under it — a schedule, where the even grid it used to draw was just LIST_GRID again.
                drawRoundRectStroke(p(3f, 4f), p(21f, 20f), 2f * s, tint, stroke)
                drawLineStroke(p(3f, 9f), p(21f, 9f), tint, stroke)
                drawLineStroke(p(7f, 4f), p(7f, 9f), tint, stroke)
                drawLineStroke(p(13f, 4f), p(13f, 9f), tint, stroke)
                drawLineStroke(p(19f, 4f), p(19f, 9f), tint, stroke)
                drawRect(tint, topLeft = p(4.6f, 11f), size = Size(6.4f * s, 2.4f * s))
                drawRect(tint, topLeft = p(12.4f, 11f), size = Size(7f * s, 2.4f * s))
                drawRect(tint, topLeft = p(4.6f, 15.6f), size = Size(10.2f * s, 2.4f * s))
                drawRect(tint, topLeft = p(17.2f, 15.6f), size = Size(3.4f * s, 2.4f * s))
            }
            OwnTVIcon.VIDEO -> {
                drawRoundRectStroke(p(3f, 6f), p(21f, 18f), 2.5f * s, tint, stroke)
                val tri = Path().apply {
                    moveTo(p(10f, 9f).x, p(10f, 9f).y)
                    lineTo(p(15f, 12f).x, p(15f, 12f).y)
                    lineTo(p(10f, 15f).x, p(10f, 15f).y)
                    close()
                }
                drawPath(tri, tint, style = Fill)
            }
            OwnTVIcon.SHARE -> {
                drawCircleStroke(p(6f, 12f), 2.4f * s, tint, stroke)
                drawCircleStroke(p(18f, 6f), 2.4f * s, tint, stroke)
                drawCircleStroke(p(18f, 18f), 2.4f * s, tint, stroke)
                drawLineStroke(p(8f, 11f), p(16f, 7f), tint, stroke)
                drawLineStroke(p(8f, 13f), p(16f, 17f), tint, stroke)
            }
            OwnTVIcon.CHEVRON -> {
                drawLineStroke(p(9f, 5f), p(16f, 12f), tint, stroke)
                drawLineStroke(p(16f, 12f), p(9f, 19f), tint, stroke)
            }
            OwnTVIcon.CHEVRON_UP -> {
                drawLineStroke(p(5f, 15f), p(12f, 8f), tint, stroke)
                drawLineStroke(p(12f, 8f), p(19f, 15f), tint, stroke)
            }
            OwnTVIcon.CHEVRON_DOWN -> {
                drawLineStroke(p(5f, 9f), p(12f, 16f), tint, stroke)
                drawLineStroke(p(12f, 16f), p(19f, 9f), tint, stroke)
            }
            OwnTVIcon.REMOTE_CHANNEL_UP -> {
                drawLineStroke(p(5f, 10f), p(12f, 4f), tint, stroke)
                drawLineStroke(p(12f, 4f), p(19f, 10f), tint, stroke)
                drawLineStroke(p(12f, 4f), p(12f, 18f), tint, stroke)
                drawLineStroke(p(4f, 21f), p(20f, 21f), tint, stroke)
            }
            OwnTVIcon.REMOTE_CHANNEL_DOWN -> {
                drawLineStroke(p(5f, 14f), p(12f, 20f), tint, stroke)
                drawLineStroke(p(12f, 20f), p(19f, 14f), tint, stroke)
                drawLineStroke(p(12f, 20f), p(12f, 6f), tint, stroke)
                drawLineStroke(p(4f, 3f), p(20f, 3f), tint, stroke)
            }
            OwnTVIcon.PAGE_TOWARD_FIRST -> {
                val thin = Stroke(width = 1.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawRoundRectStroke(p(9f, 3f), p(15f, 12f), 1.2f * s, tint.copy(alpha = .38f), thin)
                drawRoundRectStroke(p(12f, 5f), p(18f, 14f), 1.2f * s, tint.copy(alpha = .66f), thin)
                drawRoundRectStroke(p(15f, 7f), p(21f, 16f), 1.2f * s, tint, thin)
                drawLineStroke(p(10f, 19f), p(3f, 19f), tint, stroke)
                drawLineStroke(p(3f, 19f), p(6.5f, 15.5f), tint, stroke)
                drawLineStroke(p(3f, 19f), p(6.5f, 22.5f), tint, stroke)
            }
            OwnTVIcon.PAGE_TOWARD_LAST -> {
                val thin = Stroke(width = 1.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawRoundRectStroke(p(9f, 3f), p(15f, 12f), 1.2f * s, tint.copy(alpha = .38f), thin)
                drawRoundRectStroke(p(6f, 5f), p(12f, 14f), 1.2f * s, tint.copy(alpha = .66f), thin)
                drawRoundRectStroke(p(3f, 7f), p(9f, 16f), 1.2f * s, tint, thin)
                drawLineStroke(p(14f, 19f), p(21f, 19f), tint, stroke)
                drawLineStroke(p(21f, 19f), p(17.5f, 15.5f), tint, stroke)
                drawLineStroke(p(21f, 19f), p(17.5f, 22.5f), tint, stroke)
            }
            OwnTVIcon.PAUSE -> {
                drawRect(tint, topLeft = p(8f, 5f), size = Size(2.6f * s, 14f * s))
                drawRect(tint, topLeft = p(13.4f, 5f), size = Size(2.6f * s, 14f * s))
            }
            OwnTVIcon.REWIND -> {
                drawPath(triangle(p(11f, 6f), p(4f, 12f), p(11f, 18f)), tint, style = Fill)
                drawPath(triangle(p(20f, 6f), p(13f, 12f), p(20f, 18f)), tint, style = Fill)
            }
            OwnTVIcon.FORWARD -> {
                drawPath(triangle(p(4f, 6f), p(11f, 12f), p(4f, 18f)), tint, style = Fill)
                drawPath(triangle(p(13f, 6f), p(20f, 12f), p(13f, 18f)), tint, style = Fill)
            }
            OwnTVIcon.SKIP_NEXT -> {
                // play-to-bar: ▶|
                drawPath(triangle(p(5f, 6f), p(14f, 12f), p(5f, 18f)), tint, style = Fill)
                drawRect(tint, topLeft = p(15.6f, 6f), size = Size(2.6f * s, 12f * s))
            }
            OwnTVIcon.SKIP_PREVIOUS -> {
                // bar-to-play: |◀
                drawRect(tint, topLeft = p(5.8f, 6f), size = Size(2.6f * s, 12f * s))
                drawPath(triangle(p(19f, 6f), p(10f, 12f), p(19f, 18f)), tint, style = Fill)
            }
            OwnTVIcon.AUDIO -> {
                // Music note (audio track) — clearly distinct from the speaker/volume icon.
                drawCircle(tint, radius = 3f * s, center = p(8.5f, 17.5f))    // filled note head
                drawLineStroke(p(11.5f, 17.5f), p(11.5f, 5f), tint, stroke)   // stem
                drawLineStroke(p(11.5f, 5f), p(16.5f, 7f), tint, stroke)      // upper flag
                drawLineStroke(p(11.5f, 8.5f), p(16.5f, 10.5f), tint, stroke) // lower flag
            }
            OwnTVIcon.HEADPHONES -> {
                // Over-ear headphones: headband arc + two filled earcups.
                drawArc(tint, 180f, 180f, false, topLeft = p(4f, 5f), size = Size(16f * s, 16f * s), style = stroke)
                drawRoundRectStroke(p(4f, 13f), p(8f, 20f), 2f * s, tint, stroke)
                drawRect(tint, topLeft = p(4.5f, 13.5f), size = Size(3f * s, 6f * s))
                drawRoundRectStroke(p(16f, 13f), p(20f, 20f), 2f * s, tint, stroke)
                drawRect(tint, topLeft = p(16.5f, 13.5f), size = Size(3f * s, 6f * s))
            }
            OwnTVIcon.SUBTITLE -> {
                drawRoundRectStroke(p(3f, 5f), p(21f, 19f), 2.5f * s, tint, stroke)
                drawLineStroke(p(6f, 14f), p(11f, 14f), tint, stroke)
                drawLineStroke(p(13f, 14f), p(18f, 14f), tint, stroke)
            }
            OwnTVIcon.BACK -> {
                drawLineStroke(p(20f, 12f), p(4f, 12f), tint, stroke)
                drawLineStroke(p(4f, 12f), p(10f, 6f), tint, stroke)
                drawLineStroke(p(4f, 12f), p(10f, 18f), tint, stroke)
            }
            // Catch-up: a TV set with a replay loop and a play triangle inside — television, replay and
            // play in one mark. Drawn with a lighter stroke than the 24-grid default and a wide gap in
            // the loop, because three shapes nested inside a screen turn to mush at the ~20 dp the
            // player HUD renders it at. Antenna rather than a stand: it reads as a TV in fewer pixels.
            OwnTVIcon.CATCHUP -> {
                // Lighter than the 24-grid default 2f: three shapes nested inside a screen turn to
                // mush at the ~20 dp the player HUD renders this at.
                val thin = Stroke(width = 1.7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawRoundRectStroke(p(2.2f, 6.4f), p(21.8f, 20.2f), 3f * s, tint, thin)
                // One antenna, not a V: it says "television" for the cost of a single line, and it sits
                // clear of the arrowhead. A second line only crowds the top edge.
                drawLineStroke(p(12.6f, 6.4f), p(16.2f, 2.8f), tint, thin)
                // Replay loop, centred on (12, 13.4) r=4.3, open at the top so the head has room.
                drawArc(
                    tint, -30f, 285f, false,
                    topLeft = p(7.7f, 9.1f), size = Size(8.6f * s, 8.6f * s), style = thin,
                )
                // Arrowhead at the loop's end, tangent to it. Filled, and pre-computed rather than
                // trigonometry at draw time — this runs on every frame of every icon.
                drawPath(
                    Path().apply {
                        moveTo(p(12.72f, 8.75f).x, p(12.72f, 8.75f).y)
                        lineTo(p(10.79f, 10.82f).x, p(10.79f, 10.82f).y)
                        lineTo(p(10.02f, 7.93f).x, p(10.02f, 7.93f).y)
                        close()
                    },
                    tint, style = Fill,
                )
                // Play triangle, filled so it survives scaling down.
                drawPath(
                    Path().apply {
                        moveTo(p(10.6f, 11.4f).x, p(10.6f, 11.4f).y)
                        lineTo(p(13.7f, 13.4f).x, p(13.7f, 13.4f).y)
                        lineTo(p(10.6f, 15.4f).x, p(10.6f, 15.4f).y)
                        close()
                    },
                    tint, style = Fill,
                )
            }
            OwnTVIcon.VOLUME_HIGH -> {
                drawPath(speaker(::p), tint, style = Fill)
                drawArc(tint, -52f, 104f, false, topLeft = p(11.5f, 8.5f), size = Size(5f * s, 7f * s), style = stroke)
                drawArc(tint, -52f, 104f, false, topLeft = p(12.5f, 6f), size = Size(8f * s, 12f * s), style = stroke)
            }
            OwnTVIcon.VOLUME_LOW -> {
                drawPath(speaker(::p), tint, style = Fill)
                drawArc(tint, -52f, 104f, false, topLeft = p(11.5f, 8.5f), size = Size(5f * s, 7f * s), style = stroke)
            }
            OwnTVIcon.VOLUME_MUTE -> {
                drawPath(speaker(::p), tint, style = Fill)
                drawLineStroke(p(14f, 9f), p(20f, 15f), tint, stroke)
                drawLineStroke(p(20f, 9f), p(14f, 15f), tint, stroke)
            }
            OwnTVIcon.ASPECT -> {
                drawRoundRectStroke(p(3f, 5f), p(21f, 19f), 2.5f * s, tint, stroke)
                drawLineStroke(p(7f, 11f), p(7f, 9f), tint, stroke)
                drawLineStroke(p(7f, 9f), p(9f, 9f), tint, stroke)
                drawLineStroke(p(17f, 13f), p(17f, 15f), tint, stroke)
                drawLineStroke(p(17f, 15f), p(15f, 15f), tint, stroke)
            }
            OwnTVIcon.EXPAND -> { // ⤢ open-in-full: diagonal with arrowheads at both ends
                drawLineStroke(p(6f, 18f), p(18f, 6f), tint, stroke)
                drawLineStroke(p(18f, 6f), p(12.5f, 6f), tint, stroke)
                drawLineStroke(p(18f, 6f), p(18f, 11.5f), tint, stroke)
                drawLineStroke(p(6f, 18f), p(11.5f, 18f), tint, stroke)
                drawLineStroke(p(6f, 18f), p(6f, 12.5f), tint, stroke)
            }
            OwnTVIcon.IMAGE -> { // photo/picture frame: rounded rect + sun + mountain
                drawRoundRectStroke(p(3f, 5f), p(21f, 19f), 2f * s, tint, stroke)
                drawCircle(tint, 1.2f * s, p(8f, 10f), style = Fill) // sun
                // two-peak mountain ridge filling the lower frame
                drawLineStroke(p(4.5f, 18f), p(9.5f, 12f), tint, stroke)
                drawLineStroke(p(9.5f, 12f), p(13f, 15f), tint, stroke)
                drawLineStroke(p(13f, 15f), p(16f, 11f), tint, stroke)
                drawLineStroke(p(16f, 11f), p(19.5f, 18f), tint, stroke)
            }
            OwnTVIcon.PIP -> {
                drawRoundRectStroke(p(3f, 5f), p(21f, 19f), 2.5f * s, tint, stroke)
                drawRect(tint, topLeft = p(12.5f, 12f), size = Size(6.5f * s, 5f * s))
            }
            OwnTVIcon.CLOSE -> {
                drawLineStroke(p(6f, 6f), p(18f, 18f), tint, stroke)
                drawLineStroke(p(18f, 6f), p(6f, 18f), tint, stroke)
            }
            OwnTVIcon.SWAP -> { // ⇄ switch/swap engine (top arrow →, bottom arrow ←)
                drawLineStroke(p(4f, 9f), p(18f, 9f), tint, stroke)
                drawLineStroke(p(18f, 9f), p(15f, 6.5f), tint, stroke)
                drawLineStroke(p(18f, 9f), p(15f, 11.5f), tint, stroke)
                drawLineStroke(p(6f, 15f), p(20f, 15f), tint, stroke)
                drawLineStroke(p(6f, 15f), p(9f, 12.5f), tint, stroke)
                drawLineStroke(p(6f, 15f), p(9f, 17.5f), tint, stroke)
            }
        OwnTVIcon.LANGUAGE -> {
                // Material Translate — Latin "A" + character bars (not a globe; globe reads as network).
                drawLineStroke(p(4f, 15f), p(8.5f, 4f), tint, stroke)
                drawLineStroke(p(8.5f, 4f), p(13f, 15f), tint, stroke)
                drawLineStroke(p(5.8f, 11f), p(11.2f, 11f), tint, stroke)
                drawLineStroke(p(15f, 6f), p(21f, 6f), tint, stroke)
                drawLineStroke(p(15f, 10.5f), p(19.5f, 10.5f), tint, stroke)
            drawLineStroke(p(15f, 15f), p(21f, 15f), tint, stroke)
        }
        OwnTVIcon.GEAR -> {
            drawCircleStroke(p(12f, 12f), 6.5f * s, tint, stroke)
            drawCircleStroke(p(12f, 12f), 2.7f * s, tint, stroke)
            drawLineStroke(p(12f, 2.5f), p(12f, 5.5f), tint, stroke)
            drawLineStroke(p(12f, 18.5f), p(12f, 21.5f), tint, stroke)
            drawLineStroke(p(2.5f, 12f), p(5.5f, 12f), tint, stroke)
            drawLineStroke(p(18.5f, 12f), p(21.5f, 12f), tint, stroke)
            drawLineStroke(p(5.3f, 5.3f), p(7.3f, 7.3f), tint, stroke)
            drawLineStroke(p(16.7f, 16.7f), p(18.7f, 18.7f), tint, stroke)
            drawLineStroke(p(18.7f, 5.3f), p(16.7f, 7.3f), tint, stroke)
            drawLineStroke(p(7.3f, 16.7f), p(5.3f, 18.7f), tint, stroke)
        }
        OwnTVIcon.SPARKLE -> {
            val sparkle = Path().apply {
                moveTo(p(12f, 2.5f).x, p(12f, 2.5f).y)
                lineTo(p(14.2f, 9.8f).x, p(14.2f, 9.8f).y)
                lineTo(p(21.5f, 12f).x, p(21.5f, 12f).y)
                lineTo(p(14.2f, 14.2f).x, p(14.2f, 14.2f).y)
                lineTo(p(12f, 21.5f).x, p(12f, 21.5f).y)
                lineTo(p(9.8f, 14.2f).x, p(9.8f, 14.2f).y)
                lineTo(p(2.5f, 12f).x, p(2.5f, 12f).y)
                lineTo(p(9.8f, 9.8f).x, p(9.8f, 9.8f).y)
                close()
            }
            drawPath(sparkle, tint, style = Fill)
        }
            OwnTVIcon.WEATHER -> {
                // Sun behind a cloud. Lighter than the 2f default: a sun, its rays and a three-lobe
                // cloud is the busiest glyph in the set, and the rays close up at settings-row size.
                val thin = Stroke(width = 1.7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawCircleStroke(p(8.2f, 7.2f), 2.8f * s, tint, thin)
                drawLineStroke(p(8.2f, 2f), p(8.2f, 3.3f), tint, thin)
                drawLineStroke(p(2.9f, 7.2f), p(4.2f, 7.2f), tint, thin)
                drawLineStroke(p(4.5f, 3.5f), p(5.4f, 4.4f), tint, thin)
                drawLineStroke(p(11.9f, 3.5f), p(11f, 4.4f), tint, thin)
                // The cloud is three arcs plus a flat bottom rather than one Path: the round caps make
                // the joins continuous, and it keeps the whole shape in the same drawArc vocabulary the
                // rest of this file uses.
                drawLineStroke(p(8.5f, 20f), p(17f, 20f), tint, thin)
                drawArc(tint, 90f, -180f, false, p(13.8f, 13.6f), Size(6.4f * s, 6.4f * s), style = thin)
                drawArc(tint, -28.4f, -138.6f, false, p(7.98f, 11.08f), Size(9.6f * s, 9.6f * s), style = thin)
                drawArc(tint, -79.4f, -150f, false, p(4.9f, 14.75f), Size(5.4f * s, 5.4f * s), style = thin)
            }
            OwnTVIcon.NETWORK -> {
                // Globe: equator + two latitudes + one meridian. A share graph reads as "send to".
                val thin = Stroke(width = 1.7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawCircleStroke(p(12f, 12f), 9f * s, tint, thin)
                drawLineStroke(p(3.3f, 9f), p(20.7f, 9f), tint, thin)
                drawLineStroke(p(3.3f, 15f), p(20.7f, 15f), tint, thin)
                val meridian = Path().apply {
                    moveTo(p(12f, 3f).x, p(12f, 3f).y)
                    cubicTo(p(9f, 7f).x, p(9f, 7f).y, p(9f, 17f).x, p(9f, 17f).y, p(12f, 21f).x, p(12f, 21f).y)
                    cubicTo(p(15f, 17f).x, p(15f, 17f).y, p(15f, 7f).x, p(15f, 7f).y, p(12f, 3f).x, p(12f, 3f).y)
                }
                drawPath(meridian, tint, style = thin)
            }
            OwnTVIcon.TEXT_SIZE -> { // Big A + small a — a typeface, not a colour.
                drawLineStroke(p(2f, 19f), p(7f, 5f), tint, stroke)
                drawLineStroke(p(7f, 5f), p(12f, 19f), tint, stroke)
                drawLineStroke(p(3.7f, 14.6f), p(10.3f, 14.6f), tint, stroke)
                drawLineStroke(p(14.5f, 19f), p(17.7f, 11f), tint, stroke)
                drawLineStroke(p(17.7f, 11f), p(21f, 19f), tint, stroke)
                drawLineStroke(p(15.6f, 16.2f), p(19.8f, 16.2f), tint, stroke)
            }
            OwnTVIcon.BACKUP -> { // Archive box: lid, body, handle. A backup is not a download.
                drawRoundRectStroke(p(3f, 4f), p(21f, 8.5f), 1.5f * s, tint, stroke)
                // The body's top edge lands exactly on the lid's bottom edge, so the two coincide
                // instead of drawing a second line.
                drawRoundRectStroke(p(5f, 8.5f), p(19f, 20.8f), 1.8f * s, tint, stroke)
                drawLineStroke(p(9.8f, 12.6f), p(14.2f, 12.6f), tint, stroke)
            }
            OwnTVIcon.BEDTIME -> {
                // A full disc with a smaller one bitten out of its upper right — the crescent.
                val disc = Path().apply { addOval(Rect(p(3.5f, 3.5f), p(20.5f, 20.5f))) }
                val bite = Path().apply { addOval(Rect(p(9.5f, 1f), p(23f, 14.5f))) }
                drawPath(Path.combine(PathOperation.Difference, disc, bite), tint, style = stroke)
            }
            OwnTVIcon.SEND -> {
                // DOWNLOADS reflected: same shaft, same baseline, arrowhead at the top. Send and
                // Receive sit one row apart in Local sync, so they have to read as one pair.
                drawLineStroke(p(12f, 3f), p(12f, 15f), tint, stroke)
                drawLineStroke(p(7f, 8f), p(12f, 3f), tint, stroke)
                drawLineStroke(p(17f, 8f), p(12f, 3f), tint, stroke)
                drawLineStroke(p(5f, 20f), p(19f, 20f), tint, stroke)
            }
            OwnTVIcon.PHONE -> { // Handset: the body, and the bar along its bottom edge.
                drawRoundRectStroke(p(7f, 2.5f), p(17f, 21.5f), 2.2f * s, tint, stroke)
                drawLineStroke(p(11f, 18.5f), p(13f, 18.5f), tint, stroke)
            }
            OwnTVIcon.FOLDER -> { // Folder: the tab, then the body it sits on.
                drawLineStroke(p(3.2f, 8.2f), p(3.2f, 5.8f), tint, stroke)
                drawLineStroke(p(3.2f, 5.8f), p(9.2f, 5.8f), tint, stroke)
                drawLineStroke(p(9.2f, 5.8f), p(11.1f, 8.2f), tint, stroke)
                drawRoundRectStroke(p(3.2f, 8.2f), p(20.8f, 19.4f), 1.8f * s, tint, stroke)
            }
            OwnTVIcon.REFRESH -> { // Circular arrow, head drawn as the usual corner marker.
                drawArc(tint, 0f, 314.1f, false, p(3.5f, 3.5f), Size(17f * s, 17f * s), style = stroke)
                drawLineStroke(p(20.8f, 3.6f), p(20.8f, 8.6f), tint, stroke)
                drawLineStroke(p(20.8f, 8.6f), p(15.8f, 8.6f), tint, stroke)
            }
            OwnTVIcon.POWER -> {
                drawLineStroke(p(12f, 3f), p(12f, 11.5f), tint, stroke)
                drawArc(tint, -127.3f, -285.4f, false, p(4.4f, 5.05f), Size(15.2f * s, 15.2f * s), style = stroke)
            }
            OwnTVIcon.MOTION -> { // A body sweeping round, with speed lines trailing behind it.
                drawArc(tint, -117.1f, 282.8f, false, p(5.15f, 4.52f), Size(16f * s, 16f * s), style = stroke)
                drawLineStroke(p(2.5f, 8.5f), p(7.5f, 8.5f), tint, stroke)
                drawLineStroke(p(1.6f, 12.4f), p(6.6f, 12.4f), tint, stroke)
                drawLineStroke(p(2.8f, 16.3f), p(6.8f, 16.3f), tint, stroke)
            }
            OwnTVIcon.GLOW -> { // Light spilling from a centre: a solid core inside two pairs of arcs.
                drawCircle(tint, 3.2f * s, p(12f, 12f))
                drawArc(tint, -134.2f, -91.6f, false, p(5.89f, 6f), Size(12f * s, 12f * s), style = stroke)
                drawArc(tint, -45.8f, 91.6f, false, p(6.12f, 6f), Size(12f * s, 12f * s), style = stroke)
                drawArc(tint, -134.6f, -90.8f, false, p(1.51f, 1.6f), Size(20.8f * s, 20.8f * s), style = stroke)
                drawArc(tint, -45.4f, 90.8f, false, p(1.69f, 1.6f), Size(20.8f * s, 20.8f * s), style = stroke)
            }
            OwnTVIcon.WARNING -> { // A clock read as "history" on the error log; a triangle cannot.
                drawPath(triangle(p(12f, 3.6f), p(21.8f, 20.4f), p(2.2f, 20.4f)), tint, style = stroke)
                drawLineStroke(p(12f, 10f), p(12f, 14.6f), tint, stroke)
                drawCircle(tint, 1.2f * s, p(12f, 17.8f))
            }
            OwnTVIcon.LIST_GRID -> { // Four tiles — the browsing lists themselves.
                drawRoundRectStroke(p(3f, 4f), p(10.5f, 10.5f), 1.5f * s, tint, stroke)
                drawRoundRectStroke(p(13.5f, 4f), p(21f, 10.5f), 1.5f * s, tint, stroke)
                drawRoundRectStroke(p(3f, 13.5f), p(10.5f, 20f), 1.5f * s, tint, stroke)
                drawRoundRectStroke(p(13.5f, 13.5f), p(21f, 20f), 1.5f * s, tint, stroke)
            }
            OwnTVIcon.DNS -> {
                // The same globe as NETWORK but wearing a name tag: DNS is the address book, the proxy
                // is the plain globe. Drawn small and offset so the tag has room in the lower corner.
                val thin = Stroke(width = 1.7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawCircleStroke(p(10.5f, 10.5f), 7.5f * s, tint, thin)
                drawLineStroke(p(3.2f, 8.2f), p(17.8f, 8.2f), tint, thin)
                drawLineStroke(p(3.2f, 12.8f), p(15.6f, 12.8f), tint, thin)
                val meridian = Path().apply {
                    moveTo(p(10.5f, 3f).x, p(10.5f, 3f).y)
                    cubicTo(p(8f, 6.5f).x, p(8f, 6.5f).y, p(8f, 14.5f).x, p(8f, 14.5f).y, p(10.5f, 18f).x, p(10.5f, 18f).y)
                    cubicTo(p(13f, 14.5f).x, p(13f, 14.5f).y, p(13f, 6.5f).x, p(13f, 6.5f).y, p(10.5f, 3f).x, p(10.5f, 3f).y)
                }
                drawPath(meridian, tint, style = thin)
                drawRoundRectStroke(p(13.5f, 15.5f), p(22f, 21.5f), 1.5f * s, tint, thin)
                drawLineStroke(p(15.5f, 18.5f), p(20f, 18.5f), tint, thin)
            }
            OwnTVIcon.PANEL_WIDTH -> { // Two walls with a two-headed arrow between them.
                drawLineStroke(p(4f, 5f), p(4f, 19f), tint, stroke)
                drawLineStroke(p(20f, 5f), p(20f, 19f), tint, stroke)
                drawLineStroke(p(8f, 12f), p(16f, 12f), tint, stroke)
                drawLineStroke(p(8f, 12f), p(10.6f, 9.6f), tint, stroke)
                drawLineStroke(p(8f, 12f), p(10.6f, 14.4f), tint, stroke)
                drawLineStroke(p(16f, 12f), p(13.4f, 9.6f), tint, stroke)
                drawLineStroke(p(16f, 12f), p(13.4f, 14.4f), tint, stroke)
            }
            OwnTVIcon.FOCUS_HIGHLIGHT -> { // A row with light spilling off its rim — the focus ring itself.
                drawRoundRectStroke(p(5.5f, 7f), p(18.5f, 17f), 3f * s, tint, stroke)
                drawLineStroke(p(3f, 10.5f), p(3f, 13.5f), tint, stroke)
                drawLineStroke(p(12f, 3.4f), p(12f, 5.2f), tint, stroke)
                drawLineStroke(p(5.6f, 5.2f), p(6.9f, 6.4f), tint, stroke)
                drawLineStroke(p(18.4f, 5.2f), p(17.1f, 6.4f), tint, stroke)
                drawLineStroke(p(2.4f, 17.6f), p(3.6f, 18.8f), tint, stroke)
            }
            OwnTVIcon.CH_NAV -> { // A TV screen with page arrows inside: paging channels, not skipping a track.
                drawRoundRectStroke(p(3f, 5f), p(21f, 19f), 2.5f * s, tint, stroke)
                drawLineStroke(p(8f, 10f), p(11f, 12f), tint, stroke)
                drawLineStroke(p(11f, 12f), p(8f, 14f), tint, stroke)
                drawLineStroke(p(13f, 10f), p(16f, 12f), tint, stroke)
                drawLineStroke(p(16f, 12f), p(13f, 14f), tint, stroke)
                drawLineStroke(p(18.6f, 9.5f), p(18.6f, 14.5f), tint, stroke)
            }
            OwnTVIcon.AUTOPLAY_NEXT -> { // Play-to-bar with a loop arcing over it: it goes on by itself.
                drawPath(triangle(p(4f, 6f), p(12f, 12f), p(4f, 18f)), tint, style = Fill)
                drawRect(tint, topLeft = p(13.4f, 6f), size = Size(2f * s, 12f * s))
                drawArc(tint, -60f, 150f, false, p(13f, 4f), Size(10f * s, 10f * s), style = stroke)
            }
            OwnTVIcon.LIVE_DOT -> { // A lit core with two pairs of arcs spilling off it — "on the air".
                drawCircle(tint, 3.4f * s, p(12f, 12f))
                drawArc(tint, -120f, 90f, false, p(5.5f, 5.5f), Size(13f * s, 13f * s), style = stroke)
                drawArc(tint, 60f, 90f, false, p(5.5f, 5.5f), Size(13f * s, 13f * s), style = stroke)
                drawArc(tint, -125f, 100f, false, p(1.8f, 1.8f), Size(20.4f * s, 20.4f * s), style = stroke)
                drawArc(tint, 55f, 100f, false, p(1.8f, 1.8f), Size(20.4f * s, 20.4f * s), style = stroke)
            }
            OwnTVIcon.MORE -> { // ⋯ — the rest of the controls, when the window is too small for them.
                drawCircle(tint, 1.7f * s, p(5.5f, 12f))
                drawCircle(tint, 1.7f * s, p(12f, 12f))
                drawCircle(tint, 1.7f * s, p(18.5f, 12f))
            }
            OwnTVIcon.SEEK_BACK -> { // REFRESH's circular arrow, mirrored: jump back through the stream.
                drawArc(tint, 180f, -314.1f, false, p(3.5f, 3.5f), Size(17f * s, 17f * s), style = stroke)
                drawLineStroke(p(3.2f, 3.6f), p(3.2f, 8.6f), tint, stroke)
                drawLineStroke(p(3.2f, 8.6f), p(8.2f, 8.6f), tint, stroke)
            }
            OwnTVIcon.SEEK_FORWARD -> {
                drawArc(tint, 0f, 314.1f, false, p(3.5f, 3.5f), Size(17f * s, 17f * s), style = stroke)
                drawLineStroke(p(20.8f, 3.6f), p(20.8f, 8.6f), tint, stroke)
                drawLineStroke(p(20.8f, 8.6f), p(15.8f, 8.6f), tint, stroke)
            }
    }
    }
}

private fun triangle(a: Offset, b: Offset, c: Offset): Path = Path().apply {
    moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
}

/** Speaker body (left part of the volume glyphs), drawn on the shared 24-grid via [p]. */
private fun speaker(p: (Float, Float) -> Offset): Path = Path().apply {
    moveTo(p(3f, 9f).x, p(3f, 9f).y)
    lineTo(p(7f, 9f).x, p(7f, 9f).y)
    lineTo(p(11f, 5f).x, p(11f, 5f).y)
    lineTo(p(11f, 19f).x, p(11f, 19f).y)
    lineTo(p(7f, 15f).x, p(7f, 15f).y)
    lineTo(p(3f, 15f).x, p(3f, 15f).y)
    close()
}

private fun DrawScope.drawLineStroke(a: Offset, b: Offset, color: Color, stroke: Stroke) {
    drawLine(color, a, b, strokeWidth = stroke.width, cap = stroke.cap)
}

private fun DrawScope.drawRoundRectStroke(topLeft: Offset, bottomRight: Offset, radius: Float, color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        style = stroke,
    )
}

private fun DrawScope.drawCircleStroke(center: Offset, radius: Float, color: Color, stroke: Stroke) {
    drawCircle(color = color, radius = radius, center = center, style = stroke)
}

private fun starPath(center: Offset, outer: Float, inner: Float): Path {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val angle = Math.PI / points * i - Math.PI / 2
        val x = center.x + (r * Math.cos(angle)).toFloat()
        val y = center.y + (r * Math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
