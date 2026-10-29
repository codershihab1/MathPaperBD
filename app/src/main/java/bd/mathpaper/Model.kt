package bd.mathpaper

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

const val A4W = 595f // points, document space
const val A4H = 842f

fun uid() = UUID.randomUUID().toString()

/** Every page object extends El. Phases 2-3 only add subclasses here. */
@Serializable
sealed class El {
    abstract val id: String
    abstract val x: Float
    abstract val y: Float
    abstract fun at(x: Float, y: Float): El
}

@Serializable @SerialName("text")
data class TextEl(
    override val id: String = uid(),
    override val x: Float = 60f,
    override val y: Float = 60f,
    val text: String = "এখানে লিখুন",
    val size: Float = 14f,
    val w: Float = 300f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val serif: Boolean = false,
    val align: Int = 0, // 0 left, 1 centre, 2 right
    val spacing: Float = 1f,
    val color: Int = 0xFF000000.toInt(),
    val rot: Float = 0f,
) : El() {
    override fun at(x: Float, y: Float) = copy(x = x, y = y)
}

/** Formula source in a small TeX-like grammar (\frac{}{} ^ _ \sqrt{} \mat{a&b;c&d} \pi ...). See Eq.kt. */
@Serializable @SerialName("eq")
data class EqEl(
    override val id: String = uid(), override val x: Float = 60f, override val y: Float = 100f,
    val src: String = "\\frac{a}{b}", val size: Float = 18f,
) : El() { override fun at(x: Float, y: Float) = copy(x = x, y = y) }

/** crop = fractions removed from left, top, right, bottom; ar = source height/width. */
@Serializable @SerialName("img")
data class ImageEl(
    override val id: String = uid(), override val x: Float = 60f, override val y: Float = 100f,
    val asset: String, val w: Float, val h: Float, val ar: Float,
    val crop: List<Float> = listOf(0f, 0f, 0f, 0f), val rot: Float = 0f,
) : El() {
    override fun at(x: Float, y: Float) = copy(x = x, y = y)
    fun fit() = copy(h = w * ar * (1 - crop[1] - crop[3]) / (1 - crop[0] - crop[2]))
}

@Serializable @SerialName("table")
data class TableEl(
    override val id: String = uid(), override val x: Float = 60f, override val y: Float = 100f,
    val cells: List<List<String>> = List(3) { List(3) { "" } },
    val colW: List<Float> = List(3) { 100f },
    val rowH: Float = 28f, val size: Float = 12f, val border: Float = 1f, val align: Int = 0, val pad: Float = 4f,
) : El() { override fun at(x: Float, y: Float) = copy(x = x, y = y) }

@Serializable data class Pt(val x: Float, val y: Float)

enum class K { LINE, POLY, CIRCLE, ARC, ANGLE, FREE }

/**
 * One class for all geometry (stored as vertices, never pixels).
 * LINE: a,b (+arrow) | POLY: triangle/quad/polygon | CIRCLE: centre,edge | ARC: centre,start (+deg)
 * ANGLE: vertex,start-ray point (+deg 0..360, counter-clockwise, +label) | FREE: stroke points
 */
@Serializable @SerialName("shape")
data class ShapeEl(
    override val id: String = uid(),
    val kind: K,
    val pts: List<Pt>,
    val color: Int = 0xFF000000.toInt(),
    val w: Float = 1.5f,
    val dash: Boolean = false,
    val fill: Int = 0,
    val arrow: Boolean = false,
    val deg: Float = 60f,
    val label: String = "",
) : El() {
    override val x get() = pts.minOf { it.x }
    override val y get() = pts.minOf { it.y }
    override fun at(x: Float, y: Float) = copy(pts = pts.map { Pt(it.x + x - this.x, it.y + y - this.y) })
}

fun El.dup(): El = when (this) {
    is TextEl -> copy(id = uid(), x = x + 20, y = y + 20)
    is ShapeEl -> copy(id = uid(), pts = pts.map { Pt(it.x + 20, it.y + 20) })
    is EqEl -> copy(id = uid(), x = x + 20, y = y + 20)
    is ImageEl -> copy(id = uid(), x = x + 20, y = y + 20)
    is TableEl -> copy(id = uid(), x = x + 20, y = y + 20)
}

@Serializable data class Page(val els: List<El> = emptyList())

@Serializable
data class Doc(
    val id: String = uid(),
    val title: String = "নতুন ডকুমেন্ট",
    val schema: Int = 1,
    val pages: List<Page> = listOf(Page()),
    val updated: Long = System.currentTimeMillis(),
    val landscape: Boolean = false,
    val margin: Float = 40f,
    val pageNums: Boolean = false,
) {
    val pw get() = if (landscape) A4H else A4W
    val ph get() = if (landscape) A4W else A4H
    /** Map every element; returning null deletes it. */
    fun map(f: (El) -> El?) = copy(pages = pages.map { p -> p.copy(els = p.els.mapNotNull(f)) })
}

/** One JSON file per project, saved atomically (temp file + move). */
class Store(ctx: Context) {
    private val dir = File(ctx.filesDir, "projects").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "t" }

    fun list() = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { json.decodeFromString<Doc>(it.readText()) }.getOrNull() }
        .sortedByDescending { it.updated }

    fun save(d: Doc) {
        val tmp = File(dir, "${d.id}.tmp")
        tmp.writeText(json.encodeToString(d))
        Files.move(tmp.toPath(), File(dir, "${d.id}.json").toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    }

    fun delete(id: String) { File(dir, "$id.json").delete() }
}
