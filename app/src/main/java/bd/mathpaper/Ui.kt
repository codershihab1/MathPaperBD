@file:OptIn(ExperimentalMaterial3Api::class)

package bd.mathpaper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun App(vm: VM) = if (vm.doc == null) Home(vm) else Editor(vm)

@Composable
fun Home(vm: VM) = Scaffold(
    topBar = { TopAppBar(title = { Text("MathPaper BD") }) },
    floatingActionButton = { ExtendedFloatingActionButton(onClick = vm::create) { Text("+ নতুন ডকুমেন্ট") } },
) { p ->
    LazyColumn(Modifier.padding(p)) {
        items(vm.docs, key = { it.id }) { d ->
            ListItem(
                headlineContent = { Text(d.title) },
                supportingContent = { Text("${d.pages.size} পৃষ্ঠা") },
                trailingContent = { TextButton({ vm.remove(d.id) }) { Text("মুছুন") } },
                modifier = Modifier.clickable { vm.open(d.id) },
            )
        }
    }
}

@Composable
fun Editor(vm: VM) {
    val d = vm.doc!!
    var editing by remember { mutableStateOf<El?>(null) }
    var cell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dialog by remember { mutableStateOf("") } // "", "rename", "setup"
    var menu by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::addImage) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let(vm::savePdf) }
    val docx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { it?.let(vm::saveDocx) }
    vm.msg?.let { m -> AlertDialog({ vm.msg = null }, { TextButton({ vm.msg = null }) { Text("ঠিক আছে") } }, text = { Text(m, Modifier.verticalScroll(rememberScrollState())) }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(d.title, Modifier.clickable { dialog = "rename" }, maxLines = 1) },
                navigationIcon = { TextButton(vm::close) { Text("←") } },
                actions = {
                    TextButton(vm::undo, enabled = vm.ustack.isNotEmpty()) { Text("↶") }
                    TextButton(vm::redo, enabled = vm.rstack.isNotEmpty()) { Text("↷") }
                    TextButton(vm::del, enabled = vm.sel != null) { Text("মুছুন") }
                    Box {
                        TextButton({ menu = true }) { Text("⋮") }
                        DropdownMenu(menu, { menu = false }) {
                            listOf<Pair<String, () -> Unit>>(
                                "T  লেখা" to vm::addText,
                                "∑  সমীকরণ" to vm::addEq,
                                "▦  টেবিল" to vm::addTable,
                                "🖼  ছবি" to { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                "⚙  পৃষ্ঠা সেটিংস" to { dialog = "setup" },
                                "⤓  PDF এক্সপোর্ট" to { save.launch("${d.title}.pdf") },
                                "⤓  DOCX এক্সপোর্ট" to { docx.launch("${d.title}.docx") },
                            ).forEach { (t, f) -> DropdownMenuItem({ Text(t) }, { menu = false; f() }) }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    vm.selEl?.let { e -> Panel(vm, e) { editing = e; cell = null } }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                        Tool.entries.forEach { t -> FilterChip(vm.tool == t, { vm.tool = t }, { Text(t.label) }, Modifier.padding(2.dp)) }
                        FilterChip(vm.snap, { vm.snap = !vm.snap }, { Text("▦ গ্রিড") }, Modifier.padding(2.dp))
                    }
                }
            }
        },
    ) { p ->
        LazyColumn(
            Modifier.padding(p).fillMaxSize().background(Color(0xFFCFD8DC)),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(d.pages) { i, pg ->
                PageView(vm, d, pg, i) { e, at ->
                    vm.sel = e.id
                    if (e is TableEl) cell = e.cellAt(at.x, at.y).also { if (it != null) editing = e } else { editing = e; cell = null }
                }
            }
            item { Button(vm::addPage, Modifier.fillMaxWidth()) { Text("+ পৃষ্ঠা") } }
        }
    }

    val e = editing
    val done = { editing = null; cell = null }
    when {
        e is TextEl -> TextDialog(e, done) { vm.update(it); done() }
        e is EqEl -> EqDialog(e, done) { vm.update(it); done() }
        e is TableEl && cell != null -> CellDialog(e, cell!!, done) { vm.update(it); done() }
    }
    if (dialog == "rename") {
        var t by remember { mutableStateOf(d.title) }
        AlertDialog({ dialog = "" }, { TextButton({ vm.rename(t); dialog = "" }) { Text("ঠিক আছে") } }, text = { OutlinedTextField(t, { t = it }, singleLine = true) })
    }
    if (dialog == "setup") SetupDialog(d, { dialog = "" }) { l, m, n -> vm.setup(l, m, n); dialog = "" }
}

/** One page. Everything lives in document points; the same drawEl() renders it into the PDF. */
@Composable
fun PageView(vm: VM, d: Doc, pg: Page, index: Int, onEdit: (El, Pt) -> Unit) = BoxWithConstraints(Modifier.fillMaxWidth()) {
    val s = maxWidth.value / d.pw // dp per point
    val dens = LocalDensity.current
    fun raw(o: Offset) = Pt(o.x / dens.density / s, o.y / dens.density / s)
    fun els() = vm.doc?.pages?.getOrNull(index)?.els.orEmpty()
    Box(
        Modifier.fillMaxWidth().height((d.ph * s).dp).background(Color.White).clipToBounds()
            .pointerInput(index, s) {
                detectTapGestures(
                    onTap = { o -> val r = raw(o); vm.page = index; vm.sel = els().lastOrNull { it.hitAny(r.x, r.y, 24f / s) }?.id },
                    onDoubleTap = { o -> val r = raw(o); vm.page = index; els().lastOrNull { it.hitAny(r.x, r.y, 24f / s) }?.let { onEdit(it, r) } },
                )
            }
            .pointerInput(index, s, vm.tool, vm.snap) {
                var id: String? = null; var vi = -1; var a = Pt(0f, 0f); var cur: ShapeEl? = null
                detectDragGestures(
                    onDragStart = { o ->
                        val r = raw(o); a = vm.sn(r); id = null; vi = -1; cur = null; vm.page = index
                        if (vm.tool == Tool.SELECT) {
                            val sel = els().find { it.id == vm.sel } as? ShapeEl
                            if (sel != null && sel.kind != K.FREE) vi = sel.pts.indexOfFirst { hypot(it.x - r.x, it.y - r.y) < 16f / s }
                            if (vi >= 0) id = sel!!.id
                            else els().lastOrNull { it.hitAny(r.x, r.y, 24f / s) }?.let { id = it.id; vm.sel = it.id }
                            if (id != null) vm.checkpoint()
                        }
                    },
                    onDragEnd = { if (vm.tool != Tool.SELECT && vm.tool != Tool.FREE) vm.tool = Tool.SELECT },
                ) { c, drag ->
                    c.consume()
                    val r = raw(c.position)
                    val t = vm.tool
                    if (t != Tool.SELECT) {
                        val n = cur ?: newShape(t, a).also { vm.addEl(it) }
                        cur = reshape(n, t, a, vm.sn(r)); vm.put(cur!!)
                    } else id?.let { i ->
                        if (vi >= 0) (els().find { it.id == i } as? ShapeEl)?.let { vm.put(it.withPt(vi, vm.sn(r))) }
                        else vm.move(i, drag.x / dens.density / s, drag.y / dens.density / s)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                n.save(); n.scale(density * s, density * s)
                if (vm.snap) n.drawGrid(d.pw, d.ph)
                n.drawMargin(d)
                pg.els.forEach { n.drawEl(it) }
                n.drawChrome(d, index)
                pg.els.find { it.id == vm.sel }?.let { e -> if (e is ShapeEl) n.drawHandles(e, 6f / s) else n.drawOutline(e.bounds(), 1.5f / s) }
                n.restore()
            }
        }
    }
}

// ---- property panels ----

private val swatches = listOf(0xFF000000, 0xFFD32F2F, 0xFF1565C0, 0xFF2E7D32, 0xFFEF6C00)

@Composable
fun Swatches(on: (Int) -> Unit) = Row(Modifier.horizontalScroll(rememberScrollState())) {
    swatches.forEach { c -> Box(Modifier.size(40.dp).padding(6.dp).background(Color(c), CircleShape).clickable { on(c.toInt()) }) }
}

@Composable
fun Sl(label: String, v: Float, r: ClosedFloatingPointRange<Float>, on: (Float) -> Unit) = Column {
    Text("$label: ${"%.1f".format(v)}", style = MaterialTheme.typography.labelSmall)
    Slider(v, on, valueRange = r)
}

@Composable
fun Panel(vm: VM, e: El, onEdit: () -> Unit) = Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
    when (e) {
        is ShapeEl -> {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Swatches { vm.update(e.copy(color = it)) }
                FilterChip(e.dash, { vm.update(e.copy(dash = !e.dash)) }, { Text("┅") }, Modifier.padding(2.dp))
                if (e.kind == K.POLY || e.kind == K.CIRCLE)
                    FilterChip(e.fill != 0, { vm.update(e.copy(fill = if (e.fill != 0) 0 else (e.color and 0xFFFFFF) or 0x33000000)) }, { Text("▨") }, Modifier.padding(2.dp))
                TextButton(vm::dup) { Text("⧉") }
                TextButton(vm::front) { Text("↑ সামনে") }
            }
            Sl("রেখার বেধ", e.w, 0.5f..8f) { vm.update(e.copy(w = it), "w") }
            if (e.kind == K.ANGLE || e.kind == K.ARC) {
                Sl("কোণ (°)", e.deg, 0f..360f) { vm.update(e.copy(deg = it.roundToInt().toFloat()), "d") }
                OutlinedTextField(e.label, { vm.update(e.copy(label = it), "l") }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("লেবেল (যেমন ∠A, ৩০°)") })
            }
        }
        is TextEl -> {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Swatches { vm.update(e.copy(color = it)) }
                TextButton(onEdit) { Text("✎ সম্পাদনা") }
                TextButton(vm::dup) { Text("⧉") }
                TextButton(vm::front) { Text("↑ সামনে") }
            }
            Sl("প্রস্থ", e.w, 40f..560f) { vm.update(e.copy(w = it), "w") }
            Sl("ঘূর্ণন (°)", e.rot, -180f..180f) { vm.update(e.copy(rot = it), "r") }
        }
        is EqEl -> {
            Row { TextButton(onEdit) { Text("✎ সূত্র সম্পাদনা") }; TextButton(vm::dup) { Text("⧉") }; TextButton(vm::front) { Text("↑ সামনে") } }
            Sl("আকার", e.size, 10f..48f) { vm.update(e.copy(size = it), "s") }
        }
        is ImageEl -> {
            Row { TextButton(vm::dup) { Text("⧉") }; TextButton(vm::front) { Text("↑ সামনে") } }
            Sl("প্রস্থ", e.w, 20f..560f) { vm.update(e.copy(w = it).fit(), "w") }
            Sl("ঘূর্ণন (°)", e.rot, -180f..180f) { vm.update(e.copy(rot = it), "r") }
            listOf("বাম", "উপর", "ডান", "নিচ").forEachIndexed { i, n ->
                Sl("ক্রপ $n", e.crop[i], 0f..0.45f) { v -> vm.update(e.copy(crop = e.crop.toMutableList().also { it[i] = v }).fit(), "c$i") }
            }
        }
        is TableEl -> {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton({ vm.update(e.copy(cells = e.cells + listOf(List(e.colW.size) { "" }))) }) { Text("+ সারি") }
                TextButton({ if (e.cells.size > 1) vm.update(e.copy(cells = e.cells.dropLast(1))) }) { Text("− সারি") }
                TextButton({ vm.update(e.copy(cells = e.cells.map { it + "" }, colW = e.colW + 80f)) }) { Text("+ কলাম") }
                TextButton({ if (e.colW.size > 1) vm.update(e.copy(cells = e.cells.map { it.dropLast(1) }, colW = e.colW.dropLast(1))) }) { Text("− কলাম") }
                TextButton(vm::dup) { Text("⧉") }
            }
            Text("সেল সম্পাদনা: সেলে ডাবল-ট্যাপ করুন", style = MaterialTheme.typography.labelSmall)
            Sl("সারির উচ্চতা", e.rowH, 16f..80f) { vm.update(e.copy(rowH = it), "h") }
            Sl("বর্ডার", e.border, 0f..3f) { vm.update(e.copy(border = it), "b") }
            Sl("প্যাডিং", e.pad, 0f..12f) { vm.update(e.copy(pad = it), "p") }
            Row { listOf("বাম", "মাঝ", "ডান").forEachIndexed { i, n -> FilterChip(e.align == i, { vm.update(e.copy(align = i)) }, { Text(n) }, Modifier.padding(2.dp)) } }
        }
    }
}

// ---- dialogs ----

@Composable
fun TextDialog(e: TextEl, onDismiss: () -> Unit, onDone: (TextEl) -> Unit) {
    var t by remember { mutableStateOf(e.text) }
    var sz by remember { mutableFloatStateOf(e.size) }
    var sp by remember { mutableFloatStateOf(e.spacing) }
    var b by remember { mutableStateOf(e.bold) }
    var i by remember { mutableStateOf(e.italic) }
    var u by remember { mutableStateOf(e.underline) }
    var sf by remember { mutableStateOf(e.serif) }
    var al by remember { mutableIntStateOf(e.align) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onDone(e.copy(text = t, size = sz, spacing = sp, bold = b, italic = i, underline = u, serif = sf, align = al)) }) { Text("ঠিক আছে") } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(b, { b = !b }, { Text("B") }, Modifier.padding(2.dp))
                    FilterChip(i, { i = !i }, { Text("I") }, Modifier.padding(2.dp))
                    FilterChip(u, { u = !u }, { Text("U") }, Modifier.padding(2.dp))
                    FilterChip(sf, { sf = !sf }, { Text("Serif") }, Modifier.padding(2.dp))
                    listOf("◧", "▥", "◨").forEachIndexed { k, n -> FilterChip(al == k, { al = k }, { Text(n) }, Modifier.padding(2.dp)) }
                }
                Sl("আকার", sz, 8f..48f) { sz = it.roundToInt().toFloat() }
                Sl("লাইন স্পেসিং", sp, 1f..2f) { sp = it }
            }
        },
    )
}

private val quick = listOf(
    Triple("a/b", "\\frac{}{}", 3), Triple("xⁿ", "^{}", 1), Triple("xₙ", "_{}", 1), Triple("√", "\\sqrt{}", 1), Triple("[ ]", "\\mat{1&0;0&1}", 0),
) + "π ∠ ° × ÷ ± ≤ ≥ ≠ ∞ △ θ α".split(" ").map { Triple(it, it, 0) }

@Composable
fun EqDialog(e: EqEl, onDismiss: () -> Unit, onDone: (EqEl) -> Unit) {
    var tf by remember { mutableStateOf(TextFieldValue(e.src, TextRange(e.src.length))) }
    var sz by remember { mutableFloatStateOf(e.size) }
    fun ins(s: String, back: Int) {
        val sel = tf.selection
        tf = TextFieldValue(tf.text.replaceRange(sel.min, sel.max, s), TextRange(sel.min + s.length - back))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onDone(e.copy(src = tf.text, size = sz)) }) { Text("ঠিক আছে") } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Canvas(Modifier.fillMaxWidth().height(80.dp).background(Color.White)) {
                    drawIntoCanvas { c ->
                        val b = eqBox(tf.text, sz)
                        c.nativeCanvas.save(); c.nativeCanvas.scale(density, density)
                        b.draw(c.nativeCanvas, 8f, 40f + (b.a - b.d) / 2); c.nativeCanvas.restore()
                    }
                }
                OutlinedTextField(tf, { tf = it }, Modifier.fillMaxWidth(), label = { Text("সূত্র (\\frac{a}{b}, x^2, \\sqrt{x}, \\mat{1&2;3&4})") })
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    quick.forEach { (label, code, back) -> TextButton({ ins(code, back) }) { Text(label) } }
                }
                Sl("আকার", sz, 10f..48f) { sz = it.roundToInt().toFloat() }
            }
        },
    )
}

@Composable
fun CellDialog(e: TableEl, rc: Pair<Int, Int>, onDismiss: () -> Unit, onDone: (TableEl) -> Unit) {
    var t by remember { mutableStateOf(e.cells[rc.first][rc.second]) }
    var w by remember { mutableFloatStateOf(e.colW[rc.second]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                onDone(e.copy(
                    cells = e.cells.mapIndexed { r, row -> if (r == rc.first) row.mapIndexed { c, s -> if (c == rc.second) t else s } else row },
                    colW = e.colW.mapIndexed { c, x -> if (c == rc.second) w else x },
                ))
            }) { Text("ঠিক আছে") }
        },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
        text = {
            Column {
                OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), label = { Text("সারি ${rc.first + 1}, কলাম ${rc.second + 1}") })
                Sl("কলামের প্রস্থ", w, 30f..300f) { w = it.roundToInt().toFloat() }
            }
        },
    )
}

@Composable
fun SetupDialog(d: Doc, onDismiss: () -> Unit, onDone: (Boolean, Float, Boolean) -> Unit) {
    var l by remember { mutableStateOf(d.landscape) }
    var m by remember { mutableFloatStateOf(d.margin) }
    var n by remember { mutableStateOf(d.pageNums) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onDone(l, m, n) }) { Text("ঠিক আছে") } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
        title = { Text("A4 পৃষ্ঠা সেটিংস") },
        text = {
            Column {
                Row {
                    FilterChip(!l, { l = false }, { Text("পোর্ট্রেট") }, Modifier.padding(2.dp))
                    FilterChip(l, { l = true }, { Text("ল্যান্ডস্কেপ") }, Modifier.padding(2.dp))
                }
                Sl("মার্জিন (pt)", m, 10f..80f) { m = it.roundToInt().toFloat() }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(n, { n = it }); Text("পৃষ্ঠা নম্বর") }
            }
        },
    )
}
