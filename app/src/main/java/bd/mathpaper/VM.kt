package bd.mathpaper

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.round

/**
 * Single ViewModel: state + commands. Undo/redo = document snapshots (documents are immutable).
 * Everything the UI needs to survive rotation / recreation (open dialog, zoom, selection) lives here,
 * and no command assumes a document is open (no `!!`): a late callback after "back" is simply ignored.
 */
class VM(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    var docs by mutableStateOf(store.list()); private set
    var doc by mutableStateOf<Doc?>(null); private set
    var sel by mutableStateOf<String?>(null)
    var page by mutableIntStateOf(0)
    val ustack = mutableStateListOf<Doc>()
    val rstack = mutableStateListOf<Doc>()
    var tool by mutableStateOf(Tool.SELECT)
    var snap by mutableStateOf(false)
    var msg by mutableStateOf<String?>(null)

    /** Element whose text/formula/cell dialog is open (null = none). Kept here so rotation cannot lose it. */
    var editId by mutableStateOf<String?>(null)
    var cellRC by mutableStateOf<Pair<Int, Int>?>(null)

    /** Viewport zoom / horizontal pan (pixels) of the editor. */
    var zoom by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)

    /** Page index the editor should scroll to (-1 = none). */
    var jump by mutableIntStateOf(-1)

    private var job: Job? = null
    private var lastKey: String? = null
    private var lastT = 0L

    val selEl get() = doc?.pages?.flatMap { it.els }?.find { it.id == sel }
    fun sn(p: Pt) = if (snap) Pt(round(p.x / 10) * 10, round(p.y / 10) * 10) else p

    // ---------- documents ----------
    fun create() = show(Doc().also(store::save))
    fun open(id: String) { docs.find { it.id == id }?.let(::show) }
    fun remove(id: String) { store.delete(id); docs = store.list() }
    fun duplicateDoc(id: String) {
        docs.find { it.id == id }?.let { store.save(it.copy(id = uid(), title = it.title + " (কপি)", updated = System.currentTimeMillis())) }
        docs = store.list()
    }

    private fun show(d: Doc) {
        doc = d; sel = null; page = 0; ustack.clear(); rstack.clear()
        tool = Tool.SELECT; editId = null; cellRC = null; zoom = 1f; panX = 0f; jump = -1
        // decode pictures off the UI thread so the first frame does not stall
        viewModelScope.launch(Dispatchers.IO) {
            d.pages.flatMap { it.els }.filterIsInstance<ImageEl>().forEach { runCatching { Img.get(it.asset) } }
        }
    }

    fun close() { flush(); doc = null; sel = null; editId = null; cellRC = null; docs = store.list() }
    override fun onCleared() = flush()
    fun flush() { job?.cancel(); doc?.let { runCatching { store.save(it) } } }

    /** Debounced autosave. */
    private fun set(d: Doc) {
        val n = d.copy(updated = System.currentTimeMillis())
        doc = n
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) { delay(400); runCatching { store.save(n) }.onFailure { msg = "সেভ ব্যর্থ — স্টোরেজ পূর্ণ?" } }
    }

    fun checkpoint() {
        val d = doc ?: return
        ustack.add(d); rstack.clear()
        if (ustack.size > 100) ustack.removeAt(0) // bounded memory
    }
    private fun edit(snapshot: Boolean = true, f: (Doc) -> Doc) {
        val d = doc ?: return
        if (snapshot) checkpoint()
        set(f(d))
    }

    private fun fixSelection() {
        if (selEl == null) sel = null
        if (editId != null && doc?.pages?.any { p -> p.els.any { it.id == editId } } != true) { editId = null; cellRC = null }
        doc?.let { page = page.coerceIn(0, it.pages.lastIndex) }
    }
    fun undo() { val d = doc ?: return; if (ustack.isEmpty()) return; rstack.add(d); set(ustack.removeAt(ustack.lastIndex)); fixSelection() }
    fun redo() { val d = doc ?: return; if (rstack.isEmpty()) return; ustack.add(d); set(rstack.removeAt(rstack.lastIndex)); fixSelection() }

    // ---------- element edits ----------
    /** Absolute move (used while dragging; the undo checkpoint is taken once when the drag starts). */
    fun moveTo(id: String, x: Float, y: Float) = edit(false) { d -> d.map { if (it.id == id) it.at(x, y) else it } }

    /** Edits with the same [key] within 0.8 s share one undo step (sliders, typing). */
    fun update(e: El, key: String? = null) {
        val now = System.currentTimeMillis()
        val snapIt = key == null || key != lastKey || now - lastT > 800
        lastKey = key; lastT = now
        edit(snapIt) { d -> d.map { if (it.id == e.id) e else it } }
    }
    fun put(e: El) = edit(false) { d -> d.map { if (it.id == e.id) e else it } }
    fun rename(t: String) = edit { it.copy(title = t.ifBlank { it.title }) }

    fun addPage() { edit { it.copy(pages = it.pages + Page()) }; doc?.let { jump = it.pages.lastIndex } }
    fun deletePage(i: Int) {
        val d = doc ?: return
        if (d.pages.size < 2 || i !in d.pages.indices) return
        edit { it.copy(pages = it.pages.filterIndexed { k, _ -> k != i }) }
        fixSelection()
    }
    fun movePage(i: Int, delta: Int) {
        val d = doc ?: return
        val j = i + delta
        if (i !in d.pages.indices || j !in d.pages.indices) return
        edit { dd -> dd.copy(pages = dd.pages.toMutableList().also { l -> val p = l.removeAt(i); l.add(j, p) }) }
        jump = j
    }

    fun addEl(e: El) {
        edit { d -> d.copy(pages = d.pages.mapIndexed { i, p -> if (i == page) p.copy(els = p.els + e) else p }) }
        sel = e.id
    }
    /** New non-shape element: fitted to column 1 and staggered vertically so stacked additions stay visible. */
    fun addNew(e: El) {
        val d = doc ?: return
        val n = (d.pages.getOrNull(page)?.els?.size ?: 0) % 6 * 15
        val f = e.inColumn(d, 0)
        addEl(f.at(f.x, f.y + n))
    }
    fun addText() = addNew(TextEl())
    fun addEq() = addNew(EqEl())
    fun addTable() = addNew(TableEl())
    fun addImage(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val e = withContext(Dispatchers.IO) { Img.import(ctx, uri) }
            if (e != null) addNew(e) else msg = "ছবি পড়া যায়নি"
        }
    }

    /** Ready-made question-paper heading (school, exam, time / marks, rule) on the current page. */
    fun addHeader() {
        val d = doc ?: return
        val x = d.margin; val w = d.pw - 2 * d.margin; val y = d.margin
        val els = listOf(
            TextEl(x = x, y = y, w = w, text = "প্রতিষ্ঠানের নাম", size = 20f, bold = true, align = 1),
            TextEl(x = x, y = y + 28, w = w, text = "পরীক্ষার নাম · শ্রেণি · বিষয়: গণিত", size = 14f, align = 1),
            TextEl(x = x, y = y + 52, w = w, text = "সময়: ২ ঘণ্টা", size = 12f),
            TextEl(x = x, y = y + 52, w = w, text = "পূর্ণমান: ৫০", size = 12f, align = 2),
            ShapeEl(kind = K.LINE, pts = listOf(Pt(x, y + 74), Pt(x + w, y + 74)), w = 1f),
        )
        edit { dd -> dd.copy(pages = dd.pages.mapIndexed { i, p -> if (i == page) p.copy(els = p.els + els) else p }) }
        sel = els.first().id
    }

    fun setup(landscape: Boolean, margin: Float, nums: Boolean, columns: Int, gutter: Float, rule: Boolean, fitAll: Boolean) = edit { d ->
        val n = d.copy(landscape = landscape, margin = margin, pageNums = nums, columns = columns, gutter = gutter, colRule = rule)
        if (!fitAll) n else n.map { e -> if (e is ShapeEl) e else e.inColumn(n, n.colAt(e.bounds().centerX())) }
    }

    // ---------- exports ----------
    fun saveDocx(uri: Uri) {
        val d = doc ?: return; val ctx = getApplication<Application>()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { exportDocx(ctx, d, uri) } }
            msg = r.fold({ w -> "DOCX সেভ হয়েছে।" + if (w.isEmpty()) "" else "\n\nসতর্কতা:\n" + w.joinToString("\n") { "• $it" } }, { "এক্সপোর্ট ব্যর্থ: ${it.message}" })
        }
    }
    fun savePdf(uri: Uri) {
        val d = doc ?: return; val ctx = getApplication<Application>()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { exportPdf(ctx, d, uri) } }
            msg = r.fold({ "PDF সেভ হয়েছে" }, { "এক্সপোর্ট ব্যর্থ: ${it.message}" })
        }
    }

    // ---------- selection commands ----------
    fun dup() { selEl?.let { addEl(it.dup()) } }

    fun front() {
        val id = sel ?: return
        edit { d -> d.copy(pages = d.pages.map { p -> p.els.find { it.id == id }?.let { e -> p.copy(els = p.els.filter { it.id != id } + e) } ?: p }) }
    }
    fun back() {
        val id = sel ?: return
        edit { d -> d.copy(pages = d.pages.map { p -> p.els.find { it.id == id }?.let { e -> p.copy(els = listOf(e) + p.els.filter { it.id != id }) } ?: p }) }
    }

    /** Fit the selected object into column [col] (left edge + width). */
    fun toColumn(col: Int) {
        val d = doc ?: return; val e = selEl ?: return
        update(e.inColumn(d, col))
    }

    /** Send the selected object to the previous (-1) / next (+1) page. */
    fun sendToPage(delta: Int) {
        val id = sel ?: return; val d = doc ?: return
        val from = d.pages.indexOfFirst { p -> p.els.any { it.id == id } }
        val to = from + delta
        if (from < 0 || to !in d.pages.indices) { msg = "ওই দিকে আর পৃষ্ঠা নেই"; return }
        val e = d.pages[from].els.first { it.id == id }
        edit { dd -> dd.copy(pages = dd.pages.mapIndexed { i, p -> when (i) { from -> p.copy(els = p.els.filter { it.id != id }); to -> p.copy(els = p.els + e); else -> p } }) }
        page = to; jump = to
    }

    fun del() {
        val id = sel ?: return
        edit { d -> d.map { if (it.id == id) null else it } }
        sel = null; editId = null; cellRC = null
    }

    // ---------- editing dialogs ----------
    fun beginEdit(e: El, at: Pt? = null) {
        sel = e.id
        when (e) {
            is TextEl, is EqEl -> { cellRC = null; editId = e.id }
            is TableEl -> {
                val rc = if (at != null) e.cellAt(at.x, at.y) else (cellRC ?: (0 to 0))
                if (rc != null) { cellRC = rc; editId = e.id }
            }
            else -> {}
        }
    }
    fun endEdit() { editId = null; cellRC = null }
}
