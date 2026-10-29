@file:OptIn(ExperimentalMaterial3Api::class)

package bd.mathpaper

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowRightAlt
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun App(vm: VM) = if (vm.doc == null) Home(vm) else Editor(vm)

// ================= Home =================

private fun thumb(d: Doc): Bitmap {
    val w = 360; val sc = w / d.pw
    val bm = Bitmap.createBitmap(w, (d.ph * sc).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bm).apply { drawColor(android.graphics.Color.WHITE); scale(sc, sc); d.pages.firstOrNull()?.els?.forEach { drawEl(it) } }
    return bm
}

@Composable
fun Home(vm: VM) {
    var ask by remember { mutableStateOf<Doc?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = vm::create, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("নতুন ডকুমেন্ট") })
        },
    ) { p ->
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp), Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(92.dp))
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("MathPaper BD", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("অফলাইন গণিত প্রশ্নপত্র নির্মাতা", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (vm.docs.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Description, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .5f))
                    Text("এখনো কোনো ডকুমেন্ট নেই", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Text("নিচের বাটনে চেপে প্রথম প্রশ্নপত্র বানান", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else item(span = { GridItemSpan(maxLineSpan) }) {
                Text("সাম্প্রতিক", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            items(vm.docs, key = { it.id }) { d ->
                Card(
                    { vm.open(d.id) }, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
                ) {
                    val bm = remember(d.id, d.updated) { thumb(d) }
                    Image(bm.asImageBitmap(), null, Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).aspectRatio(d.pw / d.ph), contentScale = ContentScale.Crop)
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text("${d.pages.size} পৃষ্ঠা", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ ask = d }) { Icon(Icons.Outlined.Delete, "মুছুন", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
    ask?.let { d ->
        AlertDialog(
            { ask = null }, title = { Text("মুছে ফেলবেন?") }, text = { Text("\"${d.title}\" স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = { TextButton({ vm.remove(d.id); ask = null }) { Text("মুছুন", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ ask = null }) { Text("বাতিল") } },
        )
    }
}

// ================= Editor =================

private val shapeTools = listOf<Pair<Tool, ImageVector?>>(
    Tool.LINE to Icons.Outlined.HorizontalRule, Tool.ARROW to Icons.AutoMirrored.Outlined.ArrowRightAlt, Tool.TRI to Icons.Outlined.ChangeHistory,
    Tool.QUAD to Icons.Outlined.CropSquare, Tool.CIRCLE to Icons.Outlined.RadioButtonUnchecked, Tool.ANGLE to null, Tool.ARC to null, Tool.FREE to Icons.Outlined.Gesture,
)

private fun hint(t: Tool) = when (t) {
    Tool.SELECT -> ""; Tool.TEXT -> "পেজে ট্যাপ করে লিখুন"; Tool.FREE -> "আঙুল দিয়ে আঁকুন"; else -> "পেজে আঙুল টেনে আঁকুন"
}

@Composable
fun DockItem(icon: ImageVector, label: String, on: Boolean, click: () -> Unit) = Column(
    Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = click).padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(56.dp, 30.dp).clip(CircleShape).background(if (on) c.primaryContainer else Color.Transparent), Alignment.Center) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (on) c.onPrimaryContainer else c.onSurfaceVariant)
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) c.primary else c.onSurfaceVariant, maxLines = 1)
}

@Composable
fun Dock(vm: VM, onImage: () -> Unit, onEdit: (El) -> Unit) {
    var shapes by remember { mutableStateOf(false) }
    val sel = vm.selEl
    Surface(tonalElevation = 3.dp, shadowElevation = 12.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.navigationBarsPadding()) {
            AnimatedVisibility(sel != null) { sel?.let { Panel(vm, it) { onEdit(it) } } }
            AnimatedVisibility(shapes) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    shapeTools.forEach { (t, ic) ->
                        FilledTonalIconToggleButton(vm.tool == t, { vm.tool = if (it) t else Tool.SELECT }) {
                            if (ic != null) Icon(ic, t.name) else Text(t.label)
                        }
                    }
                    VerticalDivider(Modifier.height(24.dp))
                    FilledTonalIconToggleButton(vm.snap, { vm.snap = it }) { Icon(Icons.Outlined.GridOn, "গ্রিড") }
                }
            }
            if (vm.tool != Tool.SELECT) Text(hint(vm.tool), Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.SpaceEvenly) {
                DockItem(Icons.Outlined.TouchApp, "নির্বাচন", vm.tool == Tool.SELECT) { vm.tool = Tool.SELECT }
                DockItem(Icons.Outlined.TextFields, "লেখা", vm.tool == Tool.TEXT) { vm.tool = Tool.TEXT; shapes = false }
                DockItem(Icons.Outlined.Category, "আকৃতি", shapes || vm.tool in shapeTools.map { it.first }) { shapes = !shapes }
                DockItem(Icons.Outlined.Functions, "সূত্র", false) { vm.addEq() }
                DockItem(Icons.Outlined.TableChart, "টেবিল", false) { vm.addTable() }
                DockItem(Icons.Outlined.Image, "ছবি", false, onImage)
            }
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
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f),
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { dialog = "rename" }) {
                        Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text("${d.pages.size} পৃষ্ঠা · A4", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(vm::close) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "পেছনে") } },
                actions = {
                    IconButton(vm::undo, enabled = vm.ustack.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Undo, "আনডু") }
                    IconButton(vm::redo, enabled = vm.rstack.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Redo, "রিডু") }
                    if (vm.sel != null) IconButton(vm::del) { Icon(Icons.Outlined.Delete, "মুছুন", tint = MaterialTheme.colorScheme.error) }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "আরও") }
                        DropdownMenu(menu, { menu = false }) {
                            listOf<Triple<String, ImageVector, () -> Unit>>(
                                Triple("পৃষ্ঠা সেটিংস", Icons.Outlined.Settings) { dialog = "setup" },
                                Triple("নাম বদলান", Icons.Outlined.Edit) { dialog = "rename" },
                                Triple("PDF এক্সপোর্ট", Icons.Outlined.PictureAsPdf) { save.launch("${d.title}.pdf") },
                                Triple("DOCX এক্সপোর্ট", Icons.Outlined.Description) { docx.launch("${d.title}.docx") },
                            ).forEach { (t, ic, f) -> DropdownMenuItem({ Text(t) }, { menu = false; f() }, leadingIcon = { Icon(ic, null) }) }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { Dock(vm, { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { editing = it; cell = null } },
    ) { p ->
        val ls = rememberLazyListState()
        // the page nearest the viewport centre is the "current" page: new objects land there
        LaunchedEffect(ls, d.pages.size) {
            snapshotFlow {
                val li = ls.layoutInfo; val mid = (li.viewportStartOffset + li.viewportEndOffset) / 2
                li.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: 0
            }.collect { vm.page = it.coerceIn(0, d.pages.lastIndex) }
        }
        LazyColumn(
            Modifier.padding(p).fillMaxSize(), ls,
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(d.pages) { i, pg ->
                Column {
                    Text("পৃষ্ঠা ${i + 1}", Modifier.padding(start = 4.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shadowElevation = 6.dp, shape = RoundedCornerShape(4.dp), color = Color.White) {
                        PageView(vm, d, pg, i) { e, at ->
                            vm.sel = e.id
                            if (e is TableEl) cell = e.cellAt(at.x, at.y).also { if (it != null) editing = e } else { editing = e; cell = null }
                        }
                    }
                }
            }
            item { OutlinedButton(vm::addPage, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("পৃষ্ঠা যোগ করুন") } }
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
        AlertDialog(
            { dialog = "" }, title = { Text("ডকুমেন্টের নাম") },
            confirmButton = { TextButton({ vm.rename(t); dialog = "" }) { Text("ঠিক আছে") } },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
        )
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
                    onTap = { o ->
                        val r = raw(o); vm.page = index
                        if (vm.tool == Tool.TEXT) { // place a new text box exactly where tapped, then edit it
                            val e = TextEl(x = r.x.coerceIn(0f, d.pw - 80f), y = r.y.coerceIn(0f, d.ph - 30f)); vm.addEl(e); vm.tool = Tool.SELECT; onEdit(e, r)
                        } else vm.sel = els().lastOrNull { it.hitAny(r.x, r.y, 24f / s) }?.id
                    },
                    onDoubleTap = { o -> val r = raw(o); vm.page = index; els().lastOrNull { it.hitAny(r.x, r.y, 24f / s) }?.let { onEdit(it, r) } },
                )
            }
            .pointerInput(index, s, vm.tool, vm.snap) {
                // Only takes over the touch when it grabs an object or draws; on empty space the page list scrolls.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val r0 = raw(down.position); val t = vm.tool
                    if (t == Tool.TEXT) return@awaitEachGesture
                    var id: String? = null; var vi = -1
                    if (t == Tool.SELECT) {
                        val sel = els().find { it.id == vm.sel } as? ShapeEl
                        if (sel != null && sel.kind != K.FREE) vi = sel.pts.indexOfFirst { hypot(it.x - r0.x, it.y - r0.y) < 16f / s }
                        id = if (vi >= 0) sel!!.id else els().lastOrNull { it.hitAny(r0.x, r0.y, 24f / s) }?.id
                        if (id == null) return@awaitEachGesture
                    }
                    val slop = awaitTouchSlopOrCancellation(down.id) { ch, _ -> ch.consume() } ?: return@awaitEachGesture
                    vm.page = index
                    if (id != null) { vm.sel = id; vm.checkpoint() }
                    val a = vm.sn(r0); var cur: ShapeEl? = null
                    drag(slop.id) { ch ->
                        ch.consume()
                        val r = raw(ch.position)
                        if (t != Tool.SELECT) {
                            val n = cur ?: newShape(t, a).also { vm.addEl(it) }
                            cur = reshape(n, t, a, vm.sn(r)); vm.put(cur!!)
                        } else if (vi >= 0) (els().find { it.id == id } as? ShapeEl)?.let { vm.put(it.withPt(vi, vm.sn(r))) }
                        else ch.positionChange().let { dd -> vm.move(id!!, dd.x / dens.density / s, dd.y / dens.density / s) }
                    }
                    if (t != Tool.SELECT && t != Tool.FREE) vm.tool = Tool.SELECT
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
                IconButton(vm::dup) { Icon(Icons.Outlined.ContentCopy, "ডুপ্লিকেট") }
                IconButton(vm::front) { Icon(Icons.Outlined.FlipToFront, "সামনে আনুন") }
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
                IconButton(vm::dup) { Icon(Icons.Outlined.ContentCopy, "ডুপ্লিকেট") }
                IconButton(vm::front) { Icon(Icons.Outlined.FlipToFront, "সামনে আনুন") }
            }
            Sl("প্রস্থ", e.w, 40f..560f) { vm.update(e.copy(w = it), "w") }
            Sl("ঘূর্ণন (°)", e.rot, -180f..180f) { vm.update(e.copy(rot = it), "r") }
        }
        is EqEl -> {
            Row { TextButton(onEdit) { Text("✎ সূত্র সম্পাদনা") }; IconButton(vm::dup) { Icon(Icons.Outlined.ContentCopy, "ডুপ্লিকেট") }; IconButton(vm::front) { Icon(Icons.Outlined.FlipToFront, "সামনে আনুন") } }
            Sl("আকার", e.size, 10f..48f) { vm.update(e.copy(size = it), "s") }
        }
        is ImageEl -> {
            Row { IconButton(vm::dup) { Icon(Icons.Outlined.ContentCopy, "ডুপ্লিকেট") }; IconButton(vm::front) { Icon(Icons.Outlined.FlipToFront, "সামনে আনুন") } }
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
                IconButton(vm::dup) { Icon(Icons.Outlined.ContentCopy, "ডুপ্লিকেট") }
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
    var tf by remember { mutableStateOf(TextFieldValue(e.text, TextRange(0, e.text.length))) }
    val t = tf.text
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
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
                OutlinedTextField(tf, { tf = it }, Modifier.fillMaxWidth().focusRequester(focus))
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
