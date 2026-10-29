package bd.mathpaper

import android.app.Application
import androidx.compose.runtime.getValue
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
import android.net.Uri
import kotlin.math.round

/** Single ViewModel: state + commands. Undo/redo = document snapshots (documents are immutable). */
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
    private var job: Job? = null
    private var lastKey: String? = null
    private var lastT = 0L

    val selEl get() = doc?.pages?.flatMap { it.els }?.find { it.id == sel }
    fun sn(p: Pt) = if (snap) Pt(round(p.x / 10) * 10, round(p.y / 10) * 10) else p

    fun create() = show(Doc().also(store::save))
    fun open(id: String) { docs.find { it.id == id }?.let(::show) }
    fun remove(id: String) { store.delete(id); docs = store.list() }

    private fun show(d: Doc) { doc = d; sel = null; page = 0; ustack.clear(); rstack.clear() }

    fun close() { flush(); doc = null; docs = store.list() }
    override fun onCleared() = flush()
    fun flush() { job?.cancel(); doc?.let { runCatching { store.save(it) } } }

    /** Debounced autosave. */
    private fun set(d: Doc) {
        val n = d.copy(updated = System.currentTimeMillis())
        doc = n
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) { delay(400); runCatching { store.save(n) }.onFailure { msg = "সেভ ব্যর্থ — স্টোরেজ পূর্ণ?" } }
    }

    fun checkpoint() { ustack.add(doc!!); rstack.clear() }
    private fun edit(snap: Boolean = true, f: (Doc) -> Doc) { if (snap) checkpoint(); set(f(doc!!)) }

    fun undo() { if (ustack.isEmpty()) return; rstack.add(doc!!); set(ustack.removeAt(ustack.lastIndex)) }
    fun redo() { if (rstack.isEmpty()) return; ustack.add(doc!!); set(rstack.removeAt(rstack.lastIndex)) }

    fun move(id: String, dx: Float, dy: Float) = edit(false) { d -> d.map { if (it.id == id) it.at(it.x + dx, it.y + dy) else it } }
    /** Edits with the same [key] within 0.8 s share one undo step (sliders, typing). */
    fun update(e: El, key: String? = null) {
        val now = System.currentTimeMillis()
        val snapIt = key == null || key != lastKey || now - lastT > 800
        lastKey = key; lastT = now
        edit(snapIt) { d -> d.map { if (it.id == e.id) e else it } }
    }
    fun put(e: El) = edit(false) { d -> d.map { if (it.id == e.id) e else it } }
    fun rename(t: String) = edit { it.copy(title = t) }
    fun addPage() = edit { it.copy(pages = it.pages + Page()) }

    fun addEl(e: El) {
        edit { d -> d.copy(pages = d.pages.mapIndexed { i, p -> if (i == page) p.copy(els = p.els + e) else p }) }
        sel = e.id
    }
    /** New non-shape element: staggered so stacked additions stay visible. */
    fun addNew(e: El) {
        val n = (doc?.pages?.getOrNull(page)?.els?.size ?: 0) % 6 * 15
        addEl(e.at(e.x + n, e.y + n))
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
    fun setup(landscape: Boolean, margin: Float, nums: Boolean) = edit { it.copy(landscape = landscape, margin = margin, pageNums = nums) }
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
    fun dup() { selEl?.let { addEl(it.dup()) } }
    fun front() {
        val id = sel ?: return
        edit { d -> d.copy(pages = d.pages.map { p -> p.els.find { it.id == id }?.let { e -> p.copy(els = p.els - e + e) } ?: p }) }
    }

    fun del() {
        val id = sel ?: return
        edit { d -> d.map { if (it.id == id) null else it } }
        sel = null
    }
}
