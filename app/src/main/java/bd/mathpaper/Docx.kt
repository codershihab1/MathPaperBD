package bd.mathpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/**
 * Editable DOCX written directly as OOXML (zip + XML, no extra library).
 *  text -> paragraphs (Bengali runs flagged as complex script) | tables -> real tables | images -> inline pictures with crop
 *  equations -> Office Math (OMML) | geometry -> one PNG per diagram cluster (warned; the project keeps it editable)
 * Free-form positions are approximated with paragraph indents and spacing, in top-to-bottom reading order.
 * Returns user-facing warnings for anything that could not be represented faithfully.
 */
fun exportDocx(ctx: Context, d: Doc, uri: Uri): List<String> {
    val warn = linkedSetOf<String>()
    val media = mutableListOf<Pair<String, ByteArray>>()
    val rels = StringBuilder()
    var n = 0

    fun addMedia(ext: String, bytes: ByteArray): String {
        n++; media += "word/media/m$n.$ext" to bytes
        rels.append("""<Relationship Id="rId${9 + n}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/m$n.$ext"/>""")
        return "rId${9 + n}"
    }

    fun inlinePic(rid: String, w: Float, h: Float, crop: List<Float>, rot: Float) = """<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">
<wp:extent cx="${emu(w)}" cy="${emu(h)}"/><wp:docPr id="$n" name="Picture $n"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>
<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$n" name="m$n"/><pic:cNvPicPr/></pic:nvPicPr>
<pic:blipFill><a:blip r:embed="$rid"/><a:srcRect l="${pm(crop[0])}" t="${pm(crop[1])}" r="${pm(crop[2])}" b="${pm(crop[3])}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
<pic:spPr><a:xfrm rot="${(rot * 60000).roundToInt()}"><a:off x="0" y="0"/><a:ext cx="${emu(w)}" cy="${emu(h)}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"""

    fun ppr(pb: Boolean, before: Float, left: Float, right: Float, jc: String = "left", line: Float = 1f, small: Boolean = false) =
        "<w:pPr>${if (pb) "<w:pageBreakBefore/>" else ""}<w:spacing w:before=\"${tw(before)}\" w:after=\"0\" w:line=\"${(240 * line).roundToInt()}\" w:lineRule=\"auto\"/>" +
            "<w:ind w:left=\"${tw(left)}\" w:right=\"${tw(right)}\"/><w:jc w:val=\"$jc\"/>${if (small) "<w:rPr><w:sz w:val=\"2\"/></w:rPr>" else ""}</w:pPr>"

    /** [col] = column the block sits in; xml(pageBreakBefore, spaceBefore, columnLeft, columnRight). */
    class Blk(val r: RectF, val col: Int, val xml: (Boolean, Float, Float, Float) -> String)

    val pages = d.pages.mapIndexed { pi, page ->
        val blocks = mutableListOf<Blk>()

        // geometry: cluster shapes whose (inflated) boxes touch; labels inside a cluster are baked into its image
        val groups = mutableListOf<Pair<RectF, MutableSet<String>>>()
        page.els.filterIsInstance<ShapeEl>().forEach { s ->
            val r = RectF(s.bounds()).also { it.inset(-20f, -20f) }
            val ids = mutableSetOf(s.id)
            val iter = groups.iterator()
            while (iter.hasNext()) { val (gr, gi) = iter.next(); if (RectF.intersects(gr, r)) { r.union(gr); ids += gi; iter.remove() } }
            groups += r to ids
        }
        for ((r0, ids) in groups) {
            page.els.filterIsInstance<TextEl>().filter { r0.contains(it.bounds().centerX(), it.bounds().centerY()) }.forEach { ids += it.id }
            val r = RectF(r0).also { it.intersect(0f, 0f, d.pw, d.ph) }
            val sc = 3f
            val bm = Bitmap.createBitmap((r.width() * sc).toInt().coerceAtLeast(1), (r.height() * sc).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            Canvas(bm).apply { scale(sc, sc); translate(-r.left, -r.top); page.els.filter { it.id in ids }.forEach { drawEl(it) } }
            val bytes = ByteArrayOutputStream().also { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bm.recycle()
            val rid = addMedia("png", bytes)
            val pic = inlinePic(rid, r.width(), r.height(), listOf(0f, 0f, 0f, 0f), 0f)
            if (d.colCount == 2 && r.width() > d.colW + 2f) warn += "কলামের চেয়ে চওড়া জ্যামিতি চিত্র DOCX-এ একটি কলামের ভেতরে বসেছে"
            blocks += Blk(r, d.colAt(r.centerX())) { pb, bef, left, _ -> "<w:p>${ppr(pb, bef, r.left - left, 0f)}$pic</w:p>" }
            warn += "জ্যামিতি চিত্র DOCX-এ ছবি হিসেবে গেছে (সম্পাদনাযোগ্য জ্যামিতি শুধু MathPaper প্রজেক্টে থাকে)"
        }
        val baked = groups.flatMap { it.second }.toSet()

        for (e in page.els) {
            if (e is ShapeEl || e.id in baked) continue
            val b = e.bounds()
            if (d.colCount == 2 && b.width() > d.colW + 2f) warn += "কলামের চেয়ে চওড়া অবজেক্ট DOCX-এ একটি কলামের ভেতরে বসেছে"
            val col = d.colAt(b.centerX())
            when (e) {
                is TextEl -> {
                    if (e.rot != 0f) warn += "ঘোরানো লেখার ঘূর্ণন DOCX-এ বাদ গেছে"
                    val jc = listOf("left", "center", "right")[e.align]
                    blocks += Blk(b, col) { pb, bef, left, right -> "<w:p>${ppr(pb, bef, e.x - left, right - (e.x + e.w), jc, e.spacing)}${runs(e)}</w:p>" }
                }
                is EqEl -> blocks += Blk(b, col) { pb, bef, left, _ ->
                    "<w:p>${ppr(pb, bef, e.x - left, 0f)}<m:oMath>${omml(parseEq(e.src), (e.size * 2).roundToInt())}</m:oMath></w:p>"
                }
                is ImageEl -> {
                    val f = File(Fx.dir, e.asset)
                    if (!f.exists()) { warn += "একটি ছবির ফাইল পাওয়া যায়নি, বাদ গেছে"; continue }
                    val pic = inlinePic(addMedia(f.extension, f.readBytes()), e.w, e.h, e.crop, e.rot)
                    blocks += Blk(b, col) { pb, bef, left, _ -> "<w:p>${ppr(pb, bef, e.x - left, 0f)}$pic</w:p>" }
                }
                is TableEl -> blocks += Blk(b, col) { pb, bef, left, _ ->
                    (if (pb || bef > 0f) "<w:p>${ppr(pb, bef, 0f, 0f, small = true)}</w:p>" else "") + table(e, left) + "<w:p/>"
                }
                is ShapeEl -> {}
            }
        }

        // reading order; spacing-before reproduces the vertical gaps
        val out = StringBuilder()
        // each column is written top-to-bottom; for two columns a column break moves on to the right-hand one
        for (c in 0 until d.colCount) {
            val left = d.colX(c); val right = left + d.colW
            var bottom = d.margin
            val list = blocks.filter { it.col == c }.sortedWith(compareBy({ it.r.top }, { it.r.left }))
            if (c == 1 && list.isNotEmpty()) {
                if (pi > 0 && blocks.none { it.col == 0 }) out.append("<w:p>${ppr(true, 0f, 0f, 0f)}</w:p>")
                out.append(COLBREAK)
            }
            list.forEachIndexed { i, b ->
                if (i > 0 && b.r.top < bottom - 1f) warn += "মিলে থাকা/পাশাপাশি অবজেক্ট DOCX-এ একটির পর একটি বসেছে"
                out.append(b.xml(pi > 0 && c == 0 && i == 0, maxOf(0f, b.r.top - bottom), left, right))
                bottom = b.r.bottom
            }
        }
        if (blocks.isEmpty() && pi > 0) out.append("<w:p>${ppr(true, 0f, 0f, 0f)}</w:p>")
        out.toString()
    }

    val m = tw(d.margin)
    val sect = "<w:sectPr>${if (d.pageNums) "<w:footerReference w:type=\"default\" r:id=\"rId2\"/>" else ""}" +
        "<w:pgSz w:w=\"${tw(d.pw)}\" w:h=\"${tw(d.ph)}\"${if (d.landscape) " w:orient=\"landscape\"" else ""}/>" +
        "<w:pgMar w:top=\"$m\" w:right=\"$m\" w:bottom=\"$m\" w:left=\"$m\" w:header=\"0\" w:footer=\"${tw(d.margin / 2)}\" w:gutter=\"0\"/>" +
        (if (d.colCount == 2) "<w:cols w:num=\"2\" w:space=\"${tw(d.gutter)}\"${if (d.colRule) " w:sep=\"1\"" else ""}/>" else "") + "</w:sectPr>"
    val document = "$HDR<w:document $NS><w:body>${pages.joinToString("")}$sect</w:body></w:document>"

    ZipOutputStream(ctx.contentResolver.openOutputStream(uri, "w")!!.buffered()).use { z ->
        fun put(name: String, text: String) = putZip(z, name, text.toByteArray())
        put("[Content_Types].xml", "$HDR<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Default Extension=\"png\" ContentType=\"image/png\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            (if (d.pageNums) "<Override PartName=\"/word/footer1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>" else "") + "</Types>")
        put("_rels/.rels", "$HDR<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
        put("word/_rels/document.xml.rels", "$HDR<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            (if (d.pageNums) "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"footer1.xml\"/>" else "") + "$rels</Relationships>")
        put("word/styles.xml", "$HDR<w:styles xmlns:w=\"$W\"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Calibri\" w:cs=\"Nirmala UI\"/>" +
            "<w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/><w:lang w:val=\"en-US\" w:bidi=\"bn-BD\"/></w:rPr></w:rPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style></w:styles>")
        if (d.pageNums) put("word/footer1.xml", "$HDR<w:ftr xmlns:w=\"$W\"><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r>" +
            "<w:r><w:instrText xml:space=\"preserve\"> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>1</w:t></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p></w:ftr>")
        put("word/document.xml", document)
        media.forEach { (name, bytes) -> putZip(z, name, bytes) }
    }
    return warn.toList()
}

// ---- helpers ----

private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val COLBREAK = "<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\" w:line=\"20\" w:lineRule=\"exact\"/></w:pPr><w:r><w:br w:type=\"column\"/></w:r></w:p>"
private const val HDR = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
private const val NS = "xmlns:w=\"$W\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
    "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
    "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\" xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\""

private fun putZip(z: ZipOutputStream, name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
private fun tw(pt: Float) = (pt * 20).roundToInt().coerceAtLeast(0) // points -> twips
private fun emu(pt: Float) = (pt * 12700).roundToInt()
private fun pm(f: Float) = (f * 100000).roundToInt() // fraction -> 1/1000 percent

/** Splits into Bengali / other runs so Word applies complex-script fonts and sizes (neutral characters join the previous run). */
fun segs(s: String): List<Pair<String, Boolean>> {
    val out = mutableListOf<Pair<String, Boolean>>()
    for (ch in s) {
        val flag = if (!ch.isLetterOrDigit() && out.isNotEmpty()) out.last().second else ch.code in 0x980..0x9FF
        if (out.isNotEmpty() && out.last().second == flag) out[out.lastIndex] = (out.last().first + ch) to flag else out += ch.toString() to flag
    }
    return out
}

private fun runs(e: TextEl): String {
    val latin = if (e.serif) "Times New Roman" else "Calibri"
    val sz = (e.size * 2).roundToInt()
    return e.text.split("\n").joinToString("<w:r><w:br/></w:r>") { line ->
        segs(line).joinToString("") { (s, bn) ->
            "<w:r><w:rPr><w:rFonts w:ascii=\"$latin\" w:hAnsi=\"$latin\" w:cs=\"Nirmala UI\"/>" +
                (if (e.bold) "<w:b/><w:bCs/>" else "") + (if (e.italic) "<w:i/><w:iCs/>" else "") +
                "<w:color w:val=\"${"%06X".format(e.color and 0xFFFFFF)}\"/><w:sz w:val=\"$sz\"/><w:szCs w:val=\"$sz\"/>" +
                (if (e.underline) "<w:u w:val=\"single\"/>" else "") + (if (bn) "<w:cs/>" else "") +
                "</w:rPr><w:t xml:space=\"preserve\">${esc(s)}</w:t></w:r>"
        }
    }
}

private fun table(t: TableEl, left: Float): String {
    val b = if (t.border > 0) "w:val=\"single\" w:sz=\"${(t.border * 8).roundToInt()}\" w:space=\"0\" w:color=\"000000\"" else "w:val=\"none\" w:sz=\"0\" w:space=\"0\""
    val pad = tw(t.pad)
    val rh = rowHeights(t)
    val jc = listOf("left", "center", "right")[t.align]
    return "<w:tbl><w:tblPr><w:tblW w:w=\"${tw(t.colW.sum())}\" w:type=\"dxa\"/><w:tblInd w:w=\"${tw(t.x - left)}\" w:type=\"dxa\"/><w:tblBorders>" +
        listOf("top", "left", "bottom", "right", "insideH", "insideV").joinToString("") { "<w:$it $b/>" } +
        "</w:tblBorders><w:tblLayout w:type=\"fixed\"/><w:tblCellMar><w:top w:w=\"$pad\" w:type=\"dxa\"/><w:left w:w=\"$pad\" w:type=\"dxa\"/><w:bottom w:w=\"$pad\" w:type=\"dxa\"/><w:right w:w=\"$pad\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr>" +
        "<w:tblGrid>${t.colW.joinToString("") { "<w:gridCol w:w=\"${tw(it)}\"/>" }}</w:tblGrid>" +
        t.cells.mapIndexed { r, row ->
            "<w:tr><w:trPr><w:trHeight w:val=\"${tw(rh[r])}\" w:hRule=\"atLeast\"/></w:trPr>" +
                row.mapIndexed { j, s ->
                    "<w:tc><w:tcPr><w:tcW w:w=\"${tw(t.colW.getOrElse(j) { 80f })}\" w:type=\"dxa\"/></w:tcPr><w:p><w:pPr><w:jc w:val=\"$jc\"/></w:pPr>${runs(TextEl(text = s, size = t.size))}</w:p></w:tc>"
                }.joinToString("") + "</w:tr>"
        }.joinToString("") + "</w:tbl>"
}

/** Formula tree -> Office Math. [sz] = half-points. */
fun omml(n: EN, sz: Int): String = when (n) {
    is ETxt -> "<m:r><w:rPr><w:sz w:val=\"$sz\"/></w:rPr><m:t xml:space=\"preserve\">${esc(n.s)}</m:t></m:r>"
    is ERow -> n.l.joinToString("") { omml(it, sz) }
    is EFrac -> "<m:f><m:num>${omml(n.n, sz)}</m:num><m:den>${omml(n.d, sz)}</m:den></m:f>"
    is ESqrt -> "<m:rad><m:radPr><m:degHide m:val=\"1\"/></m:radPr><m:deg/><m:e>${omml(n.x, sz)}</m:e></m:rad>"
    is EScript -> when {
        n.up != null && n.dn != null -> "<m:sSubSup><m:e>${omml(n.b, sz)}</m:e><m:sub>${omml(n.dn, sz)}</m:sub><m:sup>${omml(n.up, sz)}</m:sup></m:sSubSup>"
        n.up != null -> "<m:sSup><m:e>${omml(n.b, sz)}</m:e><m:sup>${omml(n.up, sz)}</m:sup></m:sSup>"
        else -> "<m:sSub><m:e>${omml(n.b, sz)}</m:e><m:sub>${omml(n.dn!!, sz)}</m:sub></m:sSub>"
    }
    is EMat -> "<m:d><m:dPr><m:begChr m:val=\"[\"/><m:endChr m:val=\"]\"/></m:dPr><m:e><m:m>" +
        n.rows.joinToString("") { r -> "<m:mr>" + r.joinToString("") { "<m:e>${omml(it, sz)}</m:e>" } + "</m:mr>" } + "</m:m></m:e></m:d>"
}
