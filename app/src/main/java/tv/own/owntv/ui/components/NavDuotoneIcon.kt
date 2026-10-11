package tv.own.owntv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import tv.own.owntv.core.nav.MainSection

/**
 * Rank 1 — Neo Signal Duotone (Phase 7). SVG shapes from
 * extras/owntv_future_nav_icons_ranked.html, rendered on a crisp 100-unit Canvas grid
 * with clean integer coordinates so icons stay sharp at any UI zoom.
 */
@Composable
fun NavDuotoneIcon(
    section: MainSection,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val s = size.minDimension / 100f
        val soft = color.copy(alpha = 0.43f)
        val fill = color
        val stroke = Stroke(width = 7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val thin = Stroke(width = 5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        fun sz(w: Float, h: Float) = Size(w * s, h * s)

        fun poly(vararg pts: Float): Path = Path().apply {
            var i = 0; while (i < pts.size) { if (i == 0) moveTo(pts[0] * s, pts[1] * s) else lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }
        }
        fun polyC(vararg pts: Float): Path = poly(*pts).apply { close() }

        fun DrawScope.rect(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color, st: Stroke?) =
            drawRoundRect(c, o(x, y), sz(w, h), CornerRadius(r * s, r * s), style = st ?: Fill)

        fun DrawScope.dot(cx: Float, cy: Float, r: Float, c: Color) =
            drawCircle(c, radius = r * s, center = o(cx, cy))

        fun DrawScope.arc(c: Color, cx: Float, cy: Float, r: Float, startD: Float, sweepD: Float, st: Stroke) {
            val tl = o(cx - r, cy - r); drawArc(c, startD, sweepD, false, tl, sz(r * 2, r * 2), style = st)
        }

        when (section) {
            // ---- Home — house + roof + signal dash --------------------------------
            MainSection.HOME -> {
                drawPath(polyC(17f,47f, 50f,17f, 83f,47f, 83f,83f, 17f,83f), soft)
                drawPath(poly(17f,47f, 50f,17f, 83f,47f), fill, style = stroke)
                drawPath(poly(27f,83f, 27f,44f), fill, style = stroke)
                drawPath(poly(73f,83f, 73f,44f), fill, style = stroke)
                drawPath(poly(42f,42f, 58f,42f), fill, style = stroke) // signal dash
                drawPath(polyC(40f,83f, 40f,61f, 60f,61f, 60f,83f), soft)
            }

            // ---- Live TV — screen + stand + broadcast arcs -----------------------
            MainSection.LIVE_TV -> {
                rect(17f, 29f, 67f, 44f, 10f, fill, stroke)
                drawPath(polyC(46f,42f, 46f,60f, 62f,51f), fill, style = Fill)
                drawPath(poly(33f,83f, 67f,83f), soft, style = stroke)
                drawPath(poly(50f,73f, 50f,83f), soft, style = stroke)
                arc(soft, 50f, 15f, 20f, 210f, 100f, thin)
                arc(soft, 50f, 8f, 12f, 210f, 100f, thin)
                dot(50f, 22f, 2.5f, fill)
            }

            // ---- Movies — film strip sprocket + play ------------------------------
            MainSection.MOVIES -> {
                drawPath(polyC(21f,33f, 76f,21f, 79f,36f, 24f,48f), soft)
                drawPath(poly(33f,30f, 44f,43f), soft, style = thin)
                drawPath(poly(51f,26f, 62f,39f), soft, style = thin)
                drawPath(poly(68f,23f, 77f,33f), soft, style = thin)
                rect(21f, 43f, 58f, 38f, 9f, fill, stroke)
                drawPath(polyC(46f,55f, 46f,70f, 60f,62f), fill, style = Fill)
            }

            // ---- Series — stacked screens + episode dots --------------------------
            MainSection.SERIES -> {
                rect(21f, 24f, 53f, 37f, 8f, soft, stroke)
                rect(30f, 36f, 49f, 40f, 9f, fill, stroke)
                drawPath(poly(42f,50f, 66f,50f), fill, style = stroke)
                drawPath(poly(42f,63f, 57f,63f), fill, style = stroke)
                dot(37f, 83f, 3f, fill)
                dot(50f, 83f, 3f, fill)
                dot(63f, 83f, 3f, soft)
            }

            // ---- Downloads — down-arrow tray + accent arcs -----------------------
            MainSection.DOWNLOADS -> {
                drawPath(poly(50f,19f, 50f,58f), fill, style = stroke)
                drawPath(poly(35f,43f, 50f,58f, 65f,43f), fill, style = stroke)
                drawPath(poly(23f,73f, 23f,80f, 77f,80f, 77f,73f), soft, style = stroke)
                drawPath(poly(31f,85f, 69f,85f), soft, style = stroke)
                arc(soft, 21f, 16f, 12f, 220f, 90f, thin) // left accent
                arc(soft, 79f, 16f, 12f, 50f, 90f, thin)  // right accent
                dot(50f, 67f, 3f, fill)
            }

            // ---- Guide — grid with channel blocks + dot --------------------------
            MainSection.EPG -> {
                rect(17f, 21f, 67f, 63f, 10f, fill, stroke)
                drawPath(poly(33f,21f, 33f,83f), fill, style = stroke)
                drawPath(poly(17f,42f, 83f,42f), fill, style = stroke)
                drawPath(poly(17f,63f, 83f,63f), fill, style = stroke)
                drawPath(poly(44f,33f, 57f,33f), soft, style = stroke)
                drawPath(poly(44f,54f, 69f,54f), soft, style = stroke)
                drawPath(poly(44f,75f, 63f,75f), soft, style = stroke)
                dot(25f, 32f, 3f, fill)
            }

            // ---- Search — magnifier + corner brackets + dash ---------------------
            MainSection.SEARCH -> {
                drawCircle(fill, radius = 24f * s, center = o(45f, 45f), style = stroke)
                drawPath(poly(63f,63f, 82f,82f), fill, style = stroke)
                drawPath(poly(21f,16f, 16f,16f, 16f,21f), soft, style = thin)
                drawPath(poly(79f,16f, 84f,16f, 84f,21f), soft, style = thin)
                drawPath(poly(16f,79f, 16f,84f, 21f,84f), soft, style = thin)
                drawPath(poly(84f,79f, 84f,84f, 79f,84f), soft, style = thin)
                drawPath(poly(35f,45f, 55f,45f), soft, style = stroke)
            }

            // ---- Settings — sliders + orbit accent --------------------------------
            MainSection.SETTINGS -> {
                drawPath(poly(20f,33f, 59f,33f), fill, style = stroke)
                drawPath(poly(72f,33f, 83f,33f), fill, style = stroke)
                drawCircle(fill, radius = 7f * s, center = o(65f, 33f), style = stroke)
                drawPath(poly(20f,67f, 33f,67f), fill, style = stroke)
                drawPath(poly(45f,67f, 83f,67f), fill, style = stroke)
                drawCircle(fill, radius = 7f * s, center = o(39f, 67f), style = stroke)
                drawPath(poly(50f,15f, 59f,21f, 69f,22f, 74f,30f), soft, style = thin)
            }

            // ---- Sport — trophy cup + handles + ball dot -------------------------
            MainSection.SPORT -> {
                drawPath(polyC(30f,20f, 70f,20f, 66f,50f, 50f,58f, 34f,50f), fill, style = stroke)
                drawPath(poly(30f,20f, 70f,20f), fill, style = stroke)
                arc(soft, 24f, 32f, 10f, 90f, 180f, thin) // left handle
                arc(soft, 76f, 32f, 10f, 270f, 180f, thin) // right handle
                drawPath(poly(50f,58f, 50f,72f), fill, style = stroke)
                drawPath(poly(36f,80f, 64f,80f), fill, style = stroke)
                dot(50f, 34f, 4f, soft)
            }

            // ---- More — three stacked bars, the shortest last, with a dot accent -----
            MainSection.MORE -> {
                drawPath(poly(20f,33f, 80f,33f), fill, style = stroke)
                drawPath(poly(20f,50f, 80f,50f), fill, style = stroke)
                drawPath(poly(20f,67f, 58f,67f), fill, style = stroke)
                dot(72f, 67f, 4f, soft)
            }
        }
    }
}

/**
 * Rank 1 — Profile icon (Phase 7). Person silhouette + star accent on 100-grid.
 */
@Composable
fun ProfileIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 100f
        val soft = color.copy(alpha = 0.43f)
        val fill = color
        val stroke = Stroke(width = 7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun o(x: Float, y: Float) = Offset(x * s, y * s)

        // Head — circle
        drawCircle(fill, radius = 13f * s, center = o(50f, 38f), style = stroke)
        // Body arc — cubic bezier
        val body = Path().apply {
            moveTo(24f * s, 81f * s)
            cubicTo(28f * s, 67f * s, 37f * s, 60f * s, 50f * s, 60f * s)
            cubicTo(63f * s, 60f * s, 72f * s, 67f * s, 76f * s, 81f * s)
        }
        drawPath(body, fill, style = stroke)
        // Star accent
        val star = Path().apply {
            moveTo(76f * s, 19f * s); lineTo(78f * s, 24f * s); lineTo(83f * s, 26f * s)
            lineTo(78f * s, 28f * s); lineTo(76f * s, 33f * s); lineTo(74f * s, 28f * s)
            lineTo(69f * s, 26f * s); lineTo(74f * s, 24f * s); close()
        }
        drawPath(star, soft, style = stroke)
    }
}
