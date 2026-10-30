package bd.mathpaper

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

enum class Tool(val label: String) {
    SELECT("☝"), TEXT("T"), LINE("╱"), ARROW("→"), TRI("△"), QUAD("▭"), CIRCLE("◯"), ANGLE("∠"), ARC("◠"), FREE("✎")
}

// ---- creation / editing (pure functions) ----

fun newShape(t: Tool, a: Pt) = ShapeEl(
    kind = when (t) {
        Tool.LINE, Tool.ARROW -> K.LINE
        Tool.TRI, Tool.QUAD -> K.POLY
        Tool.CIRCLE -> K.CIRCLE
        Tool.ANGLE -> K.ANGLE
        Tool.ARC -> K.ARC
        else -> K.FREE
    },
    pts = listOf(a), arrow = t == Tool.ARROW,
)

fun reshape(s: ShapeEl, t: Tool, a: Pt, b: Pt) = s.copy(
    pts = when (t) {
        Tool.TRI -> listOf(Pt(a.x, b.y), Pt(b.x, b.y), Pt((a.x + b.x) / 2, a.y))
        Tool.QUAD -> listOf(a, Pt(b.x, a.y), b, Pt(a.x, b.y))
        Tool.FREE -> s.pts + b
        else -> listOf(a, b)
    },
)

fun ShapeEl.withPt(i: Int, p: Pt) = copy(pts = pts.toMutableList().also { it[i] = p })

private fun segD(x: Float, y: Float, a: Pt, b: Pt): Float {
    val dx = b.x - a.x; val dy = b.y - a.y; val l2 = dx * dx + dy * dy
    val t = if (l2 == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / l2).coerceIn(0f, 1f)
    return hypot(x - (a.x + t * dx), y - (a.y + t * dy))
}

/** Touch hit-test; [t] is the tolerance in document points. */
fun ShapeEl.hit(x: Float, y: Float, t: Float): Boolean {
    val a = pts[0]; val b = pts.getOrElse(1) { a }
    val d = hypot(x - a.x, y - a.y); val r = hypot(b.x - a.x, b.y - a.y)
    return when (kind) {
        K.CIRCLE -> abs(d - r) < t || (fill != 0 && d < r)
        K.ARC -> abs(d - r) < t
        K.POLY -> (pts + pts[0]).zipWithNext().any { (p, q) -> segD(x, y, p, q) < t }
        else -> pts.zipWithNext().any { (p, q) -> segD(x, y, p, q) < t }
    }
}

// ---- rendering: one renderer on android.graphics.Canvas, document-space units.
// The editor scales the canvas to the screen; the PDF export (phase 3) reuses it as-is. ----

private const val R = PI.toFloat() / 180f

fun Canvas.drawShape(s: ShapeEl) {
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = s.w; color = s.color
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        if (s.dash) pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.fill }
    val a = s.pts[0]; val b = s.pts.getOrElse(1) { a }
    when (s.kind) {
        K.LINE -> {
            drawLine(a.x, a.y, b.x, b.y, line)
            if (s.arrow) {
                val t = atan2(b.y - a.y, b.x - a.x)
                for (k in listOf(2.6f, -2.6f)) drawLine(b.x, b.y, b.x + 10f * cos(t + k), b.y + 10f * sin(t + k), line)
            }
        }
        K.POLY, K.FREE -> {
            val path = Path().apply {
                moveTo(a.x, a.y); s.pts.drop(1).forEach { lineTo(it.x, it.y) }
                if (s.kind == K.POLY) close()
            }
            if (s.kind == K.POLY && s.fill != 0) drawPath(path, fill)
            drawPath(path, line)
        }
        K.CIRCLE -> {
            val r = hypot(b.x - a.x, b.y - a.y)
            if (s.fill != 0) drawCircle(a.x, a.y, r, fill)
            drawCircle(a.x, a.y, r, line)
        }
        K.ARC, K.ANGLE -> {
            val len = hypot(b.x - a.x, b.y - a.y)
            val a0 = atan2(b.y - a.y, b.x - a.x) / R // screen y points down, so CCW = minus
            fun at(deg: Float, r: Float) = Pt(a.x + r * cos(deg * R), a.y + r * sin(deg * R))
            val r = if (s.kind == K.ARC) len else min(28f, len * .6f)
            if (s.kind == K.ANGLE) {
                val e = at(a0 - s.deg, len)
                drawLine(a.x, a.y, b.x, b.y, line); drawLine(a.x, a.y, e.x, e.y, line)
            }
            if (s.kind == K.ANGLE && abs(s.deg - 90f) < .5f) { // right-angle marker
                val u = at(a0, 12f); val v = at(a0 - 90f, 12f)
                drawPath(Path().apply { moveTo(u.x, u.y); lineTo(u.x + v.x - a.x, u.y + v.y - a.y); lineTo(v.x, v.y) }, line)
            } else drawArc(RectF(a.x - r, a.y - r, a.x + r, a.y + r), a0, -s.deg, false, line)
            if (s.label.isNotEmpty()) {
                val m = at(a0 - s.deg / 2, r + 12f)
                drawText(s.label, m.x, m.y + 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 12f; textAlign = Paint.Align.CENTER; color = s.color
                })
            }
        }
    }
}

/** Editor only: draggable vertex handles ([r] = radius in document points). */
fun Canvas.drawHandles(s: ShapeEl, r: Float) {
    if (s.kind == K.FREE) return
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    s.pts.forEach {
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); drawCircle(it.x, it.y, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = r / 3; p.color = 0xFF0B6E69.toInt(); drawCircle(it.x, it.y, r, p)
    }
}

fun Canvas.drawGrid(w: Float, h: Float) {
    val p = Paint().apply { color = 0x22000000; strokeWidth = .5f }
    for (i in 0..(w / 10).toInt()) drawLine(i * 10f, 0f, i * 10f, h, p)
    for (j in 0..(h / 10).toInt()) drawLine(0f, j * 10f, w, j * 10f, p)
}
