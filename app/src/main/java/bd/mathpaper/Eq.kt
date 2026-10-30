package bd.mathpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

// ---- 1. Parse: TeX-like source -> tree (shared by the editor layout and the DOCX/OMML export) ----

sealed interface EN
class ETxt(val s: String) : EN
class ERow(val l: List<EN>) : EN
class EFrac(val n: EN, val d: EN) : EN
class ESqrt(val x: EN) : EN
class EScript(val b: EN, val up: EN?, val dn: EN?) : EN
class EMat(val rows: List<List<EN>>) : EN

private val SYM = mapOf(
    "pi" to "π", "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "theta" to "θ", "lambda" to "λ",
    "mu" to "μ", "sigma" to "σ", "omega" to "ω", "phi" to "φ", "le" to "≤", "ge" to "≥", "ne" to "≠",
    "times" to "×", "div" to "÷", "pm" to "±", "deg" to "°", "angle" to "∠", "triangle" to "△", "infty" to "∞",
    "int" to "∫", "sum" to "Σ", "perp" to "⊥", "parallel" to "∥", "cdot" to "·", "to" to "→", "approx" to "≈",
    "cong" to "≅", "sim" to "∼", "therefore" to "∴", "in" to "∈",
)

private class P(val s: String) {
    var i = 0
    var depth = 0   // open { } groups
    var matAt = -1  // group depth at which & ; separate matrix cells

    fun row(stop: String = ""): EN {
        val l = mutableListOf<EN>()
        while (i < s.length && s[i] !in stop) {
            val before = i
            l.add(item())
            if (i == before) break // no progress (stray delimiter): let the caller deal with it, never loop forever
        }
        return ERow(l)
    }

    /** primary with optional ^ and _ (either order; both may be present) */
    fun item(): EN {
        val b = prim()
        var up: EN? = null; var dn: EN? = null
        while (i < s.length && (s[i] == '^' || s[i] == '_')) { val isUp = s[i++] == '^'; val e = prim(); if (isUp) up = e else dn = e }
        return if (up == null && dn == null) b else EScript(b, up, dn)
    }

    fun prim(): EN {
        if (i >= s.length) return ERow(emptyList())
        val c = s[i]
        // a closing brace / cell separator belongs to the enclosing group: an empty argument such as x^{} or x^ must not swallow it
        if (c == '}' && depth > 0) return ERow(emptyList())
        if ((c == '&' || c == ';') && matAt == depth) return ERow(emptyList())
        return when {
            c == '{' -> {
                i++; depth++
                val r = row("}")
                depth--
                if (i < s.length && s[i] == '}') i++
                r
            }
            c == '\\' -> {
                i++; val st = i
                while (i < s.length && s[i].isLetter() && s[i].code < 128) i++
                if (i == st && i < s.length) i++
                when (val n = s.substring(st, i)) {
                    "frac" -> EFrac(prim(), prim())
                    "sqrt" -> ESqrt(prim())
                    "mat" -> mat()
                    else -> ETxt(SYM[n] ?: n) // \sin, \cos, \log ... fall through as upright names
                }
            }
            else -> { i++; ETxt(c.toString()) }
        }
    }

    /** \mat{a&b;c&d} */
    fun mat(): EN {
        if (i >= s.length || s[i] != '{') return ERow(emptyList())
        i++; depth++
        val saved = matAt; matAt = depth
        val rows = mutableListOf(mutableListOf<EN>())
        while (i < s.length) {
            rows.last().add(row("&;}"))
            if (i >= s.length) break
            val ch = s[i++]
            if (ch == '&') continue
            if (ch == ';') rows.add(mutableListOf()) else break
        }
        matAt = saved; depth--
        return EMat(rows)
    }
}

fun parseEq(src: String): EN = P(src).row()

// ---- 2. Layout: tree -> drawable box (width, ascent, descent, draw(canvas, left, baseline)) ----

class B(val w: Float, val a: Float, val d: Float, val draw: (Canvas, Float, Float) -> Unit = { _, _, _ -> })

private val line by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.BLACK } }

private fun txt(s: String, z: Float): B {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = z; color = Color.BLACK
        typeface = if (s.length == 1 && s[0].isLetter() && s[0].code < 128) Fx.mathItalic else Fx.sans
    }
    return B(if (s == " ") z * .3f else p.measureText(s), z * .8f, z * .25f) { c, x, y -> c.drawText(s, x, y, p) }
}

private fun hcat(l: List<B>) = B(l.sumOf { it.w.toDouble() }.toFloat(), l.maxOfOrNull { it.a } ?: 0f, l.maxOfOrNull { it.d } ?: 0f) { c, x, y ->
    var cx = x; l.forEach { it.draw(c, cx, y); cx += it.w }
}

private fun frac(n: B, d: B, z: Float): B {
    val ax = .3f * z; val w = maxOf(n.w, d.w) + 4f
    return B(w, ax + 2 + n.d + n.a, 2 + d.a + d.d - ax) { c, x, y ->
        n.draw(c, x + (w - n.w) / 2, y - ax - 2 - n.d)
        d.draw(c, x + (w - d.w) / 2, y - ax + 2 + d.a)
        c.drawLine(x, y - ax, x + w, y - ax, line)
    }
}

private fun sqrt(b: B, z: Float): B {
    val k = .45f * z
    return B(b.w + k + 2, b.a + 4, b.d) { c, x, y ->
        val top = y - b.a - 3
        c.drawPath(Path().apply { moveTo(x, y - .35f * z); lineTo(x + k * .45f, y + b.d); lineTo(x + k, top); lineTo(x + k + b.w + 2, top) }, line)
        b.draw(c, x + k, y)
    }
}

private fun script(b: B, u: B?, d: B?, z: Float) =
    B(b.w + maxOf(u?.w ?: 0f, d?.w ?: 0f), maxOf(b.a, if (u != null) .4f * z + u.a else 0f), maxOf(b.d, if (d != null) .2f * z + d.d else 0f)) { c, x, y ->
        b.draw(c, x, y); u?.draw(c, x + b.w, y - .4f * z); d?.draw(c, x + b.w, y + .2f * z)
    }

private fun mat(rows: List<List<B>>, z: Float): B {
    val cols = rows.maxOf { it.size }
    val cw = List(cols) { j -> (rows.maxOfOrNull { it.getOrNull(j)?.w ?: 0f } ?: 0f) + 10f }
    val ra = rows.map { r -> r.maxOfOrNull { it.a } ?: 0f }
    val rd = rows.map { r -> r.maxOfOrNull { it.d } ?: 0f }
    val h = rows.indices.sumOf { (ra[it] + rd[it] + 4f).toDouble() }.toFloat()
    val ax = .3f * z
    return B(cw.sum() + 12f, ax + h / 2, h / 2 - ax) { c, x, y ->
        val top = y - ax - h / 2
        var ty = top
        rows.forEachIndexed { r, cells ->
            var cx = x + 6f
            cells.forEachIndexed { j, b -> b.draw(c, cx + (cw[j] - b.w) / 2, ty + ra[r]); cx += cw[j] }
            ty += ra[r] + rd[r] + 4f
        }
        for (side in 0..1) {
            val bx = if (side == 0) x + 2 else x + cw.sum() + 10; val t = if (side == 0) 4f else -4f
            c.drawPath(Path().apply { moveTo(bx + t, top); lineTo(bx, top); lineTo(bx, top + h); lineTo(bx + t, top + h) }, line)
        }
    }
}

private fun lay(n: EN, z: Float): B = when (n) {
    is ETxt -> txt(n.s, z)
    is ERow -> hcat(n.l.map { lay(it, z) })
    is EFrac -> frac(lay(n.n, z * .85f), lay(n.d, z * .85f), z)
    is ESqrt -> sqrt(lay(n.x, z), z)
    is EScript -> script(lay(n.b, z), n.up?.let { lay(it, z * .7f) }, n.dn?.let { lay(it, z * .7f) }, z)
    is EMat -> mat(n.rows.map { r -> r.map { lay(it, z * .9f) } }, z)
}

private val eqCache = android.util.LruCache<Pair<String, Float>, B>(64)

/** Layout is cached: the editor asks for it on every frame and every touch. */
fun eqBox(src: String, size: Float): B {
    val k = src to size
    eqCache.get(k)?.let { return it }
    return lay(parseEq(src), size).also { eqCache.put(k, it) }
}
