package bd.mathpaper

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "t" }

    private fun sample() = Doc(pages = listOf(Page(listOf(
        TextEl(text = "ত্রিভুজ ABC-এর ∠A = ৩০° এবং AB = ৫ সেমি।"),
        EqEl(src = "\\frac{a}{b}+x^2"),
        TableEl(), ImageEl(asset = "a.jpg", w = 100f, h = 50f, ar = .5f),
        ShapeEl(kind = K.ANGLE, pts = listOf(Pt(10f, 10f), Pt(60f, 10f)), deg = 360f, label = "∠A"),
    ))))

    @Test fun saveReopenKeepsEverything() {
        val d = sample()
        assertEquals(d, json.decodeFromString<Doc>(json.encodeToString(d)))
    }

    @Test fun unknownFutureFieldsAreIgnored() {
        val j = json.encodeToString(sample()).replaceFirst("\"schema\":1", "\"schema\":1,\"future\":true")
        assertEquals(1, json.decodeFromString<Doc>(j).schema)
    }

    @Test fun movingShapeKeepsSizeAndLabelsStayIndependent() {
        val s = ShapeEl(kind = K.POLY, pts = listOf(Pt(0f, 0f), Pt(40f, 0f), Pt(20f, 30f)))
        val m = s.at(100f, 200f) as ShapeEl
        assertEquals(100f, m.x, 0f); assertEquals(200f, m.y, 0f)
        assertEquals(40f, m.pts[1].x - m.pts[0].x, 0.001f)
    }

    @Test fun vertexDragChangesOnlyThatPoint() {
        val s = ShapeEl(kind = K.POLY, pts = listOf(Pt(0f, 0f), Pt(40f, 0f), Pt(20f, 30f)))
        val t = s.withPt(2, Pt(5f, 5f))
        assertEquals(s.pts[0], t.pts[0]); assertEquals(s.pts[1], t.pts[1]); assertEquals(Pt(5f, 5f), t.pts[2])
    }

    @Test fun shapeHitTest() {
        val c = ShapeEl(kind = K.CIRCLE, pts = listOf(Pt(50f, 50f), Pt(80f, 50f)))
        assertTrue(c.hit(50f, 80f, 5f)); assertFalse(c.hit(50f, 50f, 5f))
    }

    @Test fun formulaParsesToTree() {
        val n = parseEq("\\frac{a}{b}+x^2_1\\sqrt{y}\\mat{1&2;3&4}") as ERow
        assertTrue(n.l[0] is EFrac); assertTrue(n.l.any { it is EScript }); assertTrue(n.l.any { it is ESqrt })
        assertEquals(2, (n.l.last() as EMat).rows.size)
    }

    @Test fun ommlHasStructure() {
        val x = omml(parseEq("\\frac{a}{b}x^2\\sqrt{y}\\mat{1&2;3&4}"), 36)
        listOf("<m:f>", "<m:sSup>", "<m:rad>", "<m:m>", "<m:mr>").forEach { assertTrue(it, x.contains(it)) }
    }

    @Test fun xmlEscaping() = assertEquals("a&lt;b&amp;c&gt;", esc("a<b&c>"))

    @Test fun bengaliRunsAreSplitAndLossless() {
        val s = "ত্রিভুজ ABC-এর ∠A = ৩০° এবং AB = ৫ সেমি।"
        val r = segs(s)
        assertEquals(s, r.joinToString("") { it.first })
        assertTrue(r.first().second)            // starts with Bengali
        assertTrue(r.any { !it.second && it.first.contains("ABC") })
    }

    @Test fun regressionStringsSurviveJson() {
        val t = "ক্ষ জ্ঞ ক্ত ক্র প্র ত্র শ্র র্ক ি ী ু ূ ে ৈ ো ৌ বাংলাদেশ গণিত ত্রিভুজ চতুর্ভুজ বৃত্ত সমীকরণ প্রশ্নপত্র"
        assertEquals(t, json.decodeFromString<TextEl>(json.encodeToString(TextEl(text = t))).text)
    }
}
