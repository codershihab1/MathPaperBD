package bd.mathpaper

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri

/**
 * Vector PDF via android.graphics.pdf.PdfDocument: pages are drawn with the same renderer as the editor
 * (text stays text, shapes stay paths, fonts are embedded by the platform).
 */
fun exportPdf(ctx: Context, d: Doc, uri: Uri) {
    val pdf = PdfDocument()
    try {
        d.pages.forEachIndexed { i, p ->
            val pg = pdf.startPage(PdfDocument.PageInfo.Builder(d.pw.toInt(), d.ph.toInt(), i + 1).create())
            p.els.forEach { pg.canvas.drawEl(it) }
            pg.canvas.drawChrome(d, i)
            pdf.finishPage(pg)
        }
        ctx.contentResolver.openOutputStream(uri, "w")!!.use { pdf.writeTo(it) }
    } finally {
        pdf.close()
    }
}
