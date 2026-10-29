package bd.mathpaper

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import java.io.File
import kotlin.math.hypot

/** Fonts: bundled Noto Bengali (res/font) when present, otherwise the system fonts. */
object Fx {
    var sans: Typeface = Typeface.SANS_SERIF
    var serif: Typeface = Typeface.SERIF
    val mathItalic: Typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    var dir: File = File("/")

    fun init(ctx: Context) {
        fun font(name: String, fallback: Typeface): Typeface {
            val id = ctx.resources.getIdentifier(name, "font", ctx.packageName)
            return (if (id != 0) ResourcesCompat.getFont(ctx, id) else null) ?: fallback
        }
        sans = font("bengali_sans", sans); serif = font("bengali_serif", serif)
        dir = File(ctx.filesDir, "assets").apply { mkdirs() }
    }
}

// ---- text ----

fun layout(s: String, w: Float, p: TextPaint, align: Int, mult: Float = 1f): StaticLayout =
    StaticLayout.Builder.obtain(s, 0, s.length, p, w.toInt().coerceAtLeast(1))
        .setAlignment(when (align) { 1 -> Layout.Alignment.ALIGN_CENTER; 2 -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL })
        .setLineSpacing(0f, mult).build()

fun layoutOf(t: TextEl): StaticLayout {
    val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = t.size; color = t.color; isUnderlineText = t.underline
        typeface = Typeface.create(if (t.serif) Fx.serif else Fx.sans, (if (t.bold) 1 else 0) or (if (t.italic) 2 else 0))
    }
    return layout(t.text, t.w, p, t.align, t.spacing)
}

// ---- tables ----

private fun cellPaint(t: TableEl) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = t.size; color = Color.BLACK; typeface = Fx.sans }

fun rowHeights(t: TableEl): List<Float> {
    val p = cellPaint(t)
    return t.cells.map { r -> maxOf(t.rowH, r.indices.maxOf { j -> layout(r[j], t.colW[j] - 2 * t.pad, p, t.align).height + 2 * t.pad }) }
}

fun TableEl.cellAt(px: Float, py: Float): Pair<Int, Int>? {
    var x = this.x; var c = -1
    colW.forEachIndexed { j, w -> if (c < 0 && px >= x && px < x + w) c = j; x += w }
    var y = this.y; var r = -1
    rowHeights(this).forEachIndexed { i, h -> if (r < 0 && py >= y && py < y + h) r = i; y += h }
    return if (r >= 0 && c >= 0) r to c else null
}

private fun Canvas.drawTable(t: TableEl) {
    val p = cellPaint(t); val rh = rowHeights(t)
    val edge = Paint().apply { style = Paint.Style.STROKE; strokeWidth = t.border; color = Color.BLACK }
    var y = t.y
    t.cells.forEachIndexed { r, row ->
        var x = t.x
        row.forEachIndexed { j, s ->
            val w = t.colW[j]
            if (t.border > 0) drawRect(x, y, x + w, y + rh[r], edge)
            save(); translate(x + t.pad, y + t.pad); layout(s, w - 2 * t.pad, p, t.align).draw(this); restore()
            x += w
        }
        y += rh[r]
    }
}

// ---- images (stored downsampled inside the project's asset folder) ----

object Img {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, v: Bitmap) = v.byteCount
    }

    fun get(id: String): Bitmap? = cache[id] ?: BitmapFactory.decodeFile(File(Fx.dir, id).path)?.also { cache.put(id, it) }

    /** Copies a picked image into app storage (max 1600 px side). Returns null when unreadable or absurdly large. */
    fun import(ctx: Context, uri: Uri): ImageEl? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth.toLong() * o.outHeight > 60_000_000L) return null
        var k = 1
        while (maxOf(o.outWidth, o.outHeight) / k > 1600) k *= 2
        val bm = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = k }) } ?: return null
        val name = uid() + if (bm.hasAlpha()) ".png" else ".jpg"
        File(Fx.dir, name).outputStream().use { bm.compress(if (bm.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it) }
        val w = minOf(300f, bm.width.toFloat())
        val ar = bm.height.toFloat() / bm.width
        cache.put(name, bm)
        ImageEl(asset = name, w = w, h = w * ar, ar = ar)
    }.getOrNull()
}

// ---- the single renderer used by the editor canvas AND the PDF export (document-point units) ----

fun Canvas.drawEl(e: El) {
    when (e) {
        is ShapeEl -> drawShape(e)
        is TextEl -> { save(); translate(e.x, e.y); rotate(e.rot); layoutOf(e).draw(this); restore() }
        is EqEl -> eqBox(e.src, e.size).let { it.draw(this, e.x, e.y + it.a) }
        is TableEl -> drawTable(e)
        is ImageEl -> Img.get(e.asset)?.let { bm ->
            val src = Rect((e.crop[0] * bm.width).toInt(), (e.crop[1] * bm.height).toInt(), ((1 - e.crop[2]) * bm.width).toInt(), ((1 - e.crop[3]) * bm.height).toInt())
            save(); translate(e.x + e.w / 2, e.y + e.h / 2); rotate(e.rot)
            drawBitmap(bm, src, RectF(-e.w / 2, -e.h / 2, e.w / 2, e.h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            restore()
        }
    }
}

fun Canvas.drawChrome(d: Doc, i: Int) {
    if (!d.pageNums) return
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; textAlign = Paint.Align.CENTER; typeface = Fx.sans; color = 0xFF444444.toInt() }
    drawText("${i + 1}", d.pw / 2, d.ph - d.margin / 2 + 4, p)
}

/** Editor only: dashed margin guide and selection outline. */
fun Canvas.drawMargin(d: Doc) {
    val p = Paint().apply { style = Paint.Style.STROKE; strokeWidth = .6f; color = 0x552196F3; pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f) }
    drawRect(d.margin, d.margin, d.pw - d.margin, d.ph - d.margin, p)
}

fun Canvas.drawOutline(b: RectF, w: Float) {
    val p = Paint().apply { style = Paint.Style.STROKE; strokeWidth = w; color = 0xFF0B6E69.toInt(); pathEffect = DashPathEffect(floatArrayOf(w * 4, w * 3), 0f) }
    drawRect(b, p)
}

// ---- geometry of any element ----

fun El.bounds(): RectF = when (this) {
    is TextEl -> RectF(x, y, x + w, y + layoutOf(this).height)
    is EqEl -> eqBox(src, size).let { RectF(x, y, x + it.w, y + it.a + it.d) }
    is ImageEl -> RectF(x, y, x + w, y + h)
    is TableEl -> RectF(x, y, x + colW.sum(), y + rowHeights(this).sum())
    is ShapeEl -> {
        val a = pts[0]; val b = pts.getOrElse(1) { a }
        if (kind == K.CIRCLE || kind == K.ARC || kind == K.ANGLE) hypot(b.x - a.x, b.y - a.y).let { r -> RectF(a.x - r - w, a.y - r - w, a.x + r + w, a.y + r + w) }
        else RectF(x - w, y - w, pts.maxOf { it.x } + w, pts.maxOf { it.y } + w)
    }
}

/** Shapes hit on their strokes; everything else on its bounding box. */
fun El.hitAny(px: Float, py: Float, tol: Float): Boolean =
    if (this is ShapeEl) hit(px, py, tol) else bounds().let { px >= it.left - 4 && px <= it.right + 4 && py >= it.top - 4 && py <= it.bottom + 4 }
