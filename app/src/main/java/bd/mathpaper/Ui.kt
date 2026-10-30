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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun App(vm: VM) { if (vm.doc == null) Home(vm) else Editor(vm) }

// ================= Home =================

private fun thumb(d: Doc): Bitmap {
    val w = 360; val sc = w / d.pw
    val bm = Bitmap.createBitmap(w, (d.ph * sc).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bm).apply {
        drawColor(android.graphics.Color.WHITE); scale(sc, sc)
        d.pages.firstOrNull()?.els?.forEach { drawEl(it) }
        drawChrome(d, 0)
    }
    return bm
}

@Composable
fun Home(vm: VM) {
    var ask by remember { mutableStateOf<Doc?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = vm::create, icon = { Icon(Icons.Outlined.NoteAdd, null) }, text = { Text("নতুন ডকুমেন্ট") },
                containerColor = cs.primary, contentColor = cs.onPrimary, shape = RoundedCornerShape(20.dp),
            )
        },
    ) { p ->
        LazyVerticalGrid(
            GridCells.Adaptive(168.dp), Modifier.fillMaxSize().padding(p),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 104.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp).clip(RoundedCornerShape(30.dp))
                        .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))).padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .16f)), Alignment.Center) {
                            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(98.dp))
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("MathPaper BD", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = cs.onPrimary)
                            Text("অফলাইন গণিত প্রশ্নপত্র নির্মাতা", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimary.copy(alpha = .86f))
                        }
                    }
                }
            }
            if (vm.docs.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(cs.primaryContainer), Alignment.Center) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(48.dp), tint = cs.onPrimaryContainer)
                    }
                    Text("এখনো কোনো ডকুমেন্ট নেই", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                    Text("নিচের বাটনে চেপে প্রথম প্রশ্নপত্র বানান", color = cs.onSurfaceVariant)
                }
            } else item(span = { GridItemSpan(maxLineSpan) }) {
                Text("সাম্প্রতিক", Modifier.padding(start = 4.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall, color = cs.primary)
            }
            items(vm.docs, key = { it.id }) { d ->
                ElevatedCard(
                    onClick = { vm.open(d.id) }, shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = cs.surfaceContainerLowest),
                ) {
                    val bm = remember(d.id, d.updated) { thumb(d) }
                    Image(
                        bm.asImageBitmap(), null,
                        Modifier.fillMaxWidth().padding(10.dp).aspectRatio(d.pw / d.ph).clip(RoundedCornerShape(12.dp))
                            .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Row(Modifier.padding(start = 16.dp, end = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${d.pages.size} পৃষ্ঠা · ${d.colCount} কলাম", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                        Box {
                            IconButton({ menuFor = d.id }) { Icon(Icons.Outlined.MoreVert, "আরও", tint = cs.onSurfaceVariant) }
                            DropdownMenu(menuFor == d.id, { menuFor = null }) {
                                DropdownMenuItem({ Text("ডুপ্লিকেট") }, { menuFor = null; vm.duplicateDoc(d.id) }, leadingIcon = { Icon(Icons.Outlined.LibraryAdd, null) })
                                DropdownMenuItem({ Text("মুছুন") }, { menuFor = null; ask = d }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                            }
                        }
                    }
                }
            }
        }
    }
    ask?.let { d ->
        AlertDialog(
            { ask = null }, title = { Text("মুছে ফেলবেন?") }, text = { Text("\"${d.title}\" স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = { TextButton({ vm.remove(d.id); ask = null }) { Text("মুছুন", color = cs.error) } },
            dismissButton = { TextButton({ ask = null }) { Text("বাতিল") } },
            shape = RoundedCornerShape(28.dp),
        )
    }
}

// ================= Editor =================

private val shapeTools = listOf<Pair<Tool, ImageVector?>>(
    Tool.LINE to Icons.Outlined.HorizontalRule, Tool.ARROW to Icons.AutoMirrored.Outlined.ArrowRightAlt, Tool.TRI to Icons.Outlined.ChangeHistory,
    Tool.QUAD to Icons.Outlined.CropSquare, Tool.CIRCLE to Icons.Outlined.RadioButtonUnchecked, Tool.ANGLE to null, Tool.ARC to null, Tool.FREE to Icons.Outlined.Gesture,
)

private fun hint(t: Tool) = when (t) {
    Tool.SELECT -> ""; Tool.TEXT -> "পেজে ট্যাপ করে লেখা বসান"; Tool.FREE -> "আঙুল দিয়ে আঁকুন"; else -> "পেজে আঙুল টেনে আঁকুন"
}

@Composable
fun DockItem(icon: ImageVector, label: String, on: Boolean, click: () -> Unit) = Column(
    Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = click).padding(horizontal = 6.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(54.dp, 30.dp).clip(CircleShape).background(if (on) c.primaryContainer else Color.Transparent), Alignment.Center) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (on) c.onPrimaryContainer else c.onSurfaceVariant)
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) c.primary else c.onSurfaceVariant, maxLines = 1)
}

@Composable
fun Dock(vm: VM, onImage: () -> Unit) {
    var shapes by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val sel = vm.selEl
    val cs = MaterialTheme.colorScheme
    Column(Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(sel != null) { sel?.let { SelectionCard(vm, it, expanded) { expanded = !expanded } } }
        AnimatedVisibility(shapes) { ShapeBar(vm) }
        if (vm.tool != Tool.SELECT) {
            Surface(Modifier.align(Alignment.CenterHorizontally), shape = CircleShape, color = cs.primaryContainer) {
                Text(hint(vm.tool), Modifier.padding(horizontal = 14.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
            }
        }
        Surface(shape = RoundedCornerShape(30.dp), color = cs.surfaceContainerLowest, tonalElevation = 3.dp, shadowElevation = 12.dp) {
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
private fun ShapeBar(vm: VM) = Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp, shadowElevation = 8.dp) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        shapeTools.forEach { (t, ic) ->
            FilledTonalIconToggleButton(vm.tool == t, { vm.tool = if (it) t else Tool.SELECT }) {
                if (ic != null) Icon(ic, t.name) else Text(t.label)
            }
        }
        VerticalDivider(Modifier.height(24.dp))
        FilledTonalIconToggleButton(vm.snap, { vm.snap = it }) { Icon(Icons.Outlined.GridOn, "গ্রিড") }
    }
}

@Composable
private fun ZoomChip(z: Float, onIn: () -> Unit, onOut: () -> Unit, onFit: () -> Unit, modifier: Modifier) =
    Surface(modifier, shape = RoundedCornerShape(22.dp), tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onOut, Modifier.size(38.dp)) { Icon(Icons.Outlined.ZoomOut, "ছোট", Modifier.size(20.dp)) }
            Text("${(z * 100).roundToInt()}%", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFit).padding(horizontal = 4.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            IconButton(onIn, Modifier.size(38.dp)) { Icon(Icons.Outlined.ZoomIn, "বড়", Modifier.size(20.dp)) }
            IconButton(onFit, Modifier.size(38.dp)) { Icon(Icons.Outlined.FitScreen, "ফিট", Modifier.size(20.dp)) }
        }
    }

@Composable
private fun PageBtn(icon: ImageVector, desc: String, onClick: () -> Unit) =
    IconButton(onClick, Modifier.size(34.dp)) { Icon(icon, desc, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
fun Editor(vm: VM) {
    val d = vm.doc ?: return
    val cs = MaterialTheme.colorScheme
    var dialog by remember { mutableStateOf("") } // "", "rename", "setup"
    var menu by remember { mutableStateOf(false) }
    var delPage by remember { mutableIntStateOf(-1) }
    val safe = d.title.replace(Regex("[\\\\/:*?\"<>|]"), "-")
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::addImage) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let(vm::savePdf) }
    val docx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { it?.let(vm::saveDocx) }
    vm.msg?.let { m -> AlertDialog({ vm.msg = null }, { TextButton({ vm.msg = null }) { Text("ঠিক আছে") } }, text = { Text(m, Modifier.verticalScroll(rememberScrollState())) }, shape = RoundedCornerShape(28.dp)) }

    val ls = rememberLazyListState()
    var vw by remember { mutableFloatStateOf(0f) }
    var vh by remember { mutableFloatStateOf(0f) }
    fun clampPan(x: Float, z: Float): Float = if (z <= 1f) (vw - vw * z) / 2f else x.coerceIn(vw - vw * z, 0f)
    /** Zoom about the screen point (cx, cy): the content under the fingers stays under the fingers. */
    fun applyZoom(newZ: Float, cx: Float, cy: Float) {
        val z0 = vm.zoom; val z1 = newZ.coerceIn(0.5f, 4f)
        if (z1 == z0) return
        val local = (cx - vm.panX) / z0
        vm.zoom = z1
        vm.panX = clampPan(cx - local * z1, z1)
        ls.dispatchRawDelta(cy / z0 - cy / z1)
    }
    val hscroll = rememberScrollableState { delta ->
        val o = vm.panX; val n = clampPan(o + delta, vm.zoom); vm.panX = n; n - o
    }

    Scaffold(
        containerColor = cs.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { dialog = "rename" }.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${d.pages.size} পৃষ্ঠা · ${if (d.colCount == 2) "২ কলাম" else "১ কলাম"} · A4", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton({ vm.close() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "পেছনে") } },
                actions = {
                    IconButton(vm::undo, enabled = vm.ustack.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Undo, "আনডু") }
                    IconButton(vm::redo, enabled = vm.rstack.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Redo, "রিডু") }
                    if (vm.sel != null) IconButton(vm::del) { Icon(Icons.Outlined.Delete, "মুছুন", tint = cs.error) }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "আরও") }
                        DropdownMenu(menu, { menu = false }) {
                            listOf<Triple<String, ImageVector, () -> Unit>>(
                                Triple("পৃষ্ঠা ও কলাম সেটিংস", Icons.Outlined.ViewColumn) { dialog = "setup" },
                                Triple("প্রশ্নপত্রের হেডার যোগ করুন", Icons.Outlined.Title) { vm.addHeader() },
                                Triple("নাম বদলান", Icons.Outlined.Edit) { dialog = "rename" },
                                Triple("PDF এক্সপোর্ট", Icons.Outlined.PictureAsPdf) { save.launch("$safe.pdf") },
                                Triple("DOCX এক্সপোর্ট", Icons.Outlined.Description) { docx.launch("$safe.docx") },
                            ).forEach { (t, ic, f) -> DropdownMenuItem({ Text(t) }, { menu = false; f() }, leadingIcon = { Icon(ic, null) }) }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surfaceContainer),
            )
        },
        bottomBar = { Dock(vm) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
    ) { p ->
        // the page nearest the viewport centre is the "current" page: new objects land there
        LaunchedEffect(ls, d.pages.size) {
            snapshotFlow {
                val li = ls.layoutInfo; val mid = (li.viewportStartOffset + li.viewportEndOffset) / 2
                li.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: 0
            }.collect { vm.page = it.coerceIn(0, maxOf(0, d.pages.lastIndex)) }
        }
        LaunchedEffect(vm.jump) {
            val j = vm.jump
            if (j >= 0) { ls.animateScrollToItem(j.coerceIn(0, maxOf(0, d.pages.lastIndex))); vm.jump = -1 }
        }
        Box(
            Modifier.padding(p).fillMaxSize().clipToBounds()
                .onSizeChanged { vw = it.width.toFloat(); vh = it.height.toFloat() }
                // two-finger pinch = zoom + pan. Runs in the Initial pass so it wins over the page's own gestures.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            if (ev.changes.count { it.pressed } >= 2) {
                                val zc = ev.calculateZoom(); val pan = ev.calculatePan(); val c = ev.calculateCentroid(useCurrent = true)
                                if (zc != 1f || pan != Offset.Zero) {
                                    applyZoom(vm.zoom * zc, c.x, c.y)
                                    vm.panX = clampPan(vm.panX + pan.x, vm.zoom)
                                    ls.dispatchRawDelta(-pan.y / vm.zoom)
                                }
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
                // one finger on empty paper while zoomed in: pan sideways (vertical scroll is the list's own)
                .scrollable(hscroll, Orientation.Horizontal),
        ) {
            LazyColumn(
                Modifier
                    .layout { m, c ->
                        val z = vm.zoom
                        val w = c.maxWidth; val h = c.maxHeight
                        val pl = m.measure(Constraints.fixed(w, (h / z).roundToInt().coerceAtLeast(1)))
                        layout(w, h) { pl.place(0, 0) }
                    }
                    .graphicsLayer { scaleX = vm.zoom; scaleY = vm.zoom; translationX = vm.panX; transformOrigin = TransformOrigin(0f, 0f) },
                ls,
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(d.pages) { i, pg ->
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("পৃষ্ঠা ${i + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            if (d.pages.size > 1) {
                                if (i > 0) PageBtn(Icons.Outlined.ArrowUpward, "উপরে") { vm.movePage(i, -1) }
                                if (i < d.pages.lastIndex) PageBtn(Icons.Outlined.ArrowDownward, "নিচে") { vm.movePage(i, 1) }
                                PageBtn(Icons.Outlined.Delete, "পৃষ্ঠা মুছুন") { delPage = i }
                            }
                        }
                        Surface(shadowElevation = 8.dp, shape = RoundedCornerShape(6.dp), color = Color.White) {
                            PageView(vm, d, pg, i) { e, at -> vm.beginEdit(e, at) }
                        }
                    }
                }
                item {
                    FilledTonalButton({ vm.addPage() }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("পৃষ্ঠা যোগ করুন")
                    }
                }
            }
            ZoomChip(
                vm.zoom,
                onIn = { applyZoom(vm.zoom * 1.25f, vw / 2, vh / 2) },
                onOut = { applyZoom(vm.zoom / 1.25f, vw / 2, vh / 2) },
                onFit = { applyZoom(1f, vw / 2, vh / 2); vm.panX = 0f },
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            )
        }
    }

    // ---- dialogs (their state lives in the ViewModel, so they survive rotation) ----
    val el = vm.editId?.let { id -> d.pages.firstNotNullOfOrNull { pg -> pg.els.find { it.id == id } } }
    when (el) {
        is TextEl -> TextDialog(el, { vm.endEdit() }) { vm.update(it); vm.endEdit() }
        is EqEl -> EqDialog(el, { vm.endEdit() }) { vm.update(it); vm.endEdit() }
        is TableEl -> vm.cellRC?.let { rc ->
            if (el.cells.getOrNull(rc.first)?.getOrNull(rc.second) != null && rc.second in el.colW.indices)
                CellDialog(el, rc, { vm.endEdit() }) { vm.update(it); vm.endEdit() }
        }
        else -> {}
    }
    if (delPage >= 0) AlertDialog(
        { delPage = -1 }, title = { Text("পৃষ্ঠা ${delPage + 1} মুছবেন?") }, text = { Text("এই পৃষ্ঠার সব অবজেক্ট মুছে যাবে। আনডু করা যাবে।") },
        confirmButton = { TextButton({ vm.deletePage(delPage); delPage = -1 }) { Text("মুছুন", color = cs.error) } },
        dismissButton = { TextButton({ delPage = -1 }) { Text("বাতিল") } },
        shape = RoundedCornerShape(28.dp),
    )
    if (dialog == "rename") {
        var t by remember { mutableStateOf(d.title) }
        AlertDialog(
            { dialog = "" }, title = { Text("ডকুমেন্টের নাম") },
            confirmButton = { TextButton({ vm.rename(t); dialog = "" }) { Text("ঠিক আছে") } },
            dismissButton = { TextButton({ dialog = "" }) { Text("বাতিল") } },
            text = { OutlinedTextField(t, { t = it }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
            shape = RoundedCornerShape(28.dp),
        )
    }
    if (dialog == "setup") SetupDialog(d, { dialog = "" }) { l, m, n, c, g, r, f -> vm.setup(l, m, n, c, g, r, f); dialog = "" }
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
                // taps only (no double-tap listener, so a tap reacts immediately)
                detectTapGestures(onTap = { o ->
                    val doc = vm.doc
                    val r = raw(o)
                    vm.page = index
                    if (doc != null) {
                        if (vm.tool == Tool.TEXT) { // place a new text box where tapped (snapped to the column), then edit it
                            val y = r.y.coerceIn(0f, doc.ph - 30f)
                            val e = if (doc.colCount == 2) TextEl(x = doc.colX(doc.colAt(r.x)), y = y, w = doc.colW)
                            else TextEl(x = r.x.coerceIn(0f, doc.pw - 80f), y = y, w = (doc.pw - doc.margin - r.x).coerceIn(80f, 300f))
                            vm.addEl(e); vm.tool = Tool.SELECT; vm.beginEdit(e)
                        } else {
                            val hit = els().lastOrNull { it.hitAny(r.x, r.y, 24f / (s * vm.zoom)) }
                            // tap an already-selected text / formula / table to edit it, otherwise just select
                            if (hit != null && hit.id == vm.sel && hit !is ShapeEl && hit !is ImageEl) onEdit(hit, r) else vm.sel = hit?.id
                        }
                    }
                })
            }
            .pointerInput(index, s, vm.tool, vm.snap) {
                // Only takes over the touch when it grabs an object or draws; on empty space the page list scrolls.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val doc = vm.doc ?: return@awaitEachGesture
                    val t = vm.tool
                    if (t == Tool.TEXT) return@awaitEachGesture
                    val r0 = raw(down.position)
                    val z = vm.zoom
                    val tol = 24f / (s * z); val hr = 18f / (s * z)
                    var id: String? = null; var vi = -1; var resize = false
                    if (t == Tool.SELECT) {
                        val cur0 = els().find { it.id == vm.sel }
                        if (cur0 != null && cur0 !is ShapeEl) {
                            val b = cur0.bounds(); val q = cur0.unrot(r0.x, r0.y)
                            resize = hypot(q.x - b.right, q.y - b.bottom) < hr * 1.5f
                            if (resize) id = cur0.id
                        }
                        if (!resize) {
                            if (cur0 is ShapeEl && cur0.kind != K.FREE) vi = cur0.pts.indexOfFirst { hypot(it.x - r0.x, it.y - r0.y) < hr }
                            id = if (vi >= 0) cur0?.id else els().lastOrNull { it.hitAny(r0.x, r0.y, tol) }?.id
                        }
                        if (id == null) return@awaitEachGesture
                    }
                    val slop = awaitTouchSlopOrCancellation(down.id) { ch, _ -> ch.consume() } ?: return@awaitEachGesture
                    vm.page = index
                    val e0 = id?.let { i -> els().find { it.id == i } }
                    if (id != null) { vm.sel = id; vm.checkpoint() }
                    val a = vm.sn(r0); var cur: ShapeEl? = null
                    drag(slop.id) { ch ->
                        // NB: positions are absolute, so nothing depends on positionChange() (which is 0 once consumed)
                        ch.consume()
                        val r = raw(ch.position)
                        when {
                            t != Tool.SELECT -> {
                                val n = cur ?: newShape(t, a).also { vm.addEl(it) }
                                val u = reshape(n, t, a, vm.sn(r)); cur = u; vm.put(u)
                            }
                            resize -> if (e0 != null) vm.put(e0.resizeTo(r.x, r.y))
                            vi >= 0 -> (els().find { it.id == id } as? ShapeEl)?.let { vm.put(it.withPt(vi, vm.sn(r))) }
                            e0 != null -> {
                                var nx = e0.x + (r.x - r0.x); var ny = e0.y + (r.y - r0.y)
                                if (vm.snap) { nx = round(nx / 10f) * 10f; ny = round(ny / 10f) * 10f }
                                val wd = if (e0 is ShapeEl) e0.pts.maxOf { it.x } - e0.x else e0.bounds().width()
                                nx = doc.snapX(nx, wd, 6f / (s * z)) // magnet to the column edges
                                vm.moveTo(e0.id, nx, ny)
                            }
                            else -> {}
                        }
                    }
                    if (t != Tool.SELECT && t != Tool.FREE) vm.tool = Tool.SELECT
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                val z = vm.zoom
                n.save(); n.scale(density * s, density * s)
                if (vm.snap) n.drawGrid(d.pw, d.ph)
                n.drawGuides(d)
                pg.els.forEach { n.drawEl(it) }
                n.drawChrome(d, index)
                pg.els.find { it.id == vm.sel }?.let { e -> if (e is ShapeEl) n.drawHandles(e, 7f / (s * z)) else n.drawSelection(e, 1.5f / (s * z), 7f / (s * z)) }
                n.restore()
            }
        }
    }
}

// ---- selection card: quick actions + properties of the selected object ----

private val swatches = listOf(0xFF000000, 0xFF424242, 0xFFD32F2F, 0xFFEF6C00, 0xFFF9A825, 0xFF2E7D32, 0xFF00838F, 0xFF1565C0, 0xFF6A1B9A, 0xFFC2185B)

@Composable
fun Swatches(cur: Int, on: (Int) -> Unit) = Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
    val cs = MaterialTheme.colorScheme
    swatches.forEach { c ->
        val ci = c.toInt()
        Box(
            Modifier.padding(4.dp).size(30.dp).clip(CircleShape).background(Color(c))
                .border(if (ci == cur) 3.dp else 1.dp, if (ci == cur) cs.primary else cs.outlineVariant, CircleShape)
                .clickable { on(ci) },
        )
    }
}

@Composable
fun Sl(label: String, v: Float, r: ClosedFloatingPointRange<Float>, on: (Float) -> Unit) = Column {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text("${(v * 10).roundToInt() / 10f}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
    Slider(v.coerceIn(r.start, r.endInclusive), on, valueRange = r)
}

@Composable
private fun Act(icon: ImageVector, desc: String, onClick: () -> Unit) =
    IconButton(onClick) { Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
fun SelectionCard(vm: VM, e: El, expanded: Boolean, toggle: () -> Unit) {
    val d = vm.doc ?: return
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 3.dp, shadowElevation = 12.dp) {
        Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
            // actions
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                if (e is TextEl || e is EqEl || e is TableEl) Act(Icons.Outlined.Edit, "সম্পাদনা") { vm.beginEdit(e) }
                Act(Icons.Outlined.ContentCopy, "ডুপ্লিকেট") { vm.dup() }
                Act(Icons.Outlined.FlipToFront, "সামনে আনুন") { vm.front() }
                Act(Icons.Outlined.FlipToBack, "পেছনে পাঠান") { vm.back() }
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
                if (d.colCount == 2) {
                    AssistChip({ vm.toColumn(0) }, { Text("কলাম ১") }, Modifier.padding(horizontal = 2.dp), leadingIcon = { Icon(Icons.Outlined.ViewColumn, null, Modifier.size(18.dp)) })
                    AssistChip({ vm.toColumn(1) }, { Text("কলাম ২") }, Modifier.padding(horizontal = 2.dp), leadingIcon = { Icon(Icons.Outlined.ViewColumn, null, Modifier.size(18.dp)) })
                } else {
                    AssistChip({ vm.toColumn(0) }, { Text("মার্জিনে ফিট") }, Modifier.padding(horizontal = 2.dp), leadingIcon = { Icon(Icons.Outlined.ViewColumn, null, Modifier.size(18.dp)) })
                }
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
                Act(Icons.Outlined.ArrowUpward, "আগের পৃষ্ঠায় পাঠান") { vm.sendToPage(-1) }
                Act(Icons.Outlined.ArrowDownward, "পরের পৃষ্ঠায় পাঠান") { vm.sendToPage(1) }
                Act(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, "বিস্তারিত", toggle)
            }
            when (e) {
                is ShapeEl -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        Swatches(e.color) { c -> vm.update(e.copy(color = c, fill = if (e.fill != 0) (c and 0xFFFFFF) or 0x33000000 else 0)) }
                        FilterChip(e.dash, { vm.update(e.copy(dash = !e.dash)) }, { Text("┅ ড্যাশ") }, Modifier.padding(2.dp))
                        if (e.kind == K.POLY || e.kind == K.CIRCLE)
                            FilterChip(e.fill != 0, { vm.update(e.copy(fill = if (e.fill != 0) 0 else (e.color and 0xFFFFFF) or 0x33000000)) }, { Text("▨ ফিল") }, Modifier.padding(2.dp))
                    }
                    if (expanded) {
                        Sl("রেখার বেধ", e.w, 0.5f..8f) { vm.update(e.copy(w = it), "w") }
                        if (e.kind == K.ANGLE || e.kind == K.ARC) {
                            Sl("কোণ (°)", e.deg, 0f..360f) { vm.update(e.copy(deg = it.roundToInt().toFloat()), "d") }
                            OutlinedTextField(e.label, { vm.update(e.copy(label = it), "l") }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("লেবেল (যেমন ∠A, ৩০°)") }, shape = RoundedCornerShape(16.dp))
                        }
                    }
                }
                is TextEl -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        Swatches(e.color) { vm.update(e.copy(color = it)) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(e.bold, { vm.update(e.copy(bold = !e.bold)) }, { Icon(Icons.Outlined.FormatBold, "বোল্ড", Modifier.size(18.dp)) }, Modifier.padding(2.dp))
                        FilterChip(e.italic, { vm.update(e.copy(italic = !e.italic)) }, { Icon(Icons.Outlined.FormatItalic, "ইটালিক", Modifier.size(18.dp)) }, Modifier.padding(2.dp))
                        FilterChip(e.underline, { vm.update(e.copy(underline = !e.underline)) }, { Icon(Icons.Outlined.FormatUnderlined, "আন্ডারলাইন", Modifier.size(18.dp)) }, Modifier.padding(2.dp))
                        FilterChip(e.serif, { vm.update(e.copy(serif = !e.serif)) }, { Text("Serif") }, Modifier.padding(2.dp))
                        listOf("◧", "▥", "◨").forEachIndexed { k, n -> FilterChip(e.align == k, { vm.update(e.copy(align = k)) }, { Text(n) }, Modifier.padding(2.dp)) }
                    }
                    if (expanded) {
                        Sl("আকার", e.size, 8f..48f) { vm.update(e.copy(size = it.roundToInt().toFloat()), "sz") }
                        Sl("প্রস্থ", e.w, 40f..560f) { vm.update(e.copy(w = it), "w") }
                        Sl("লাইন স্পেসিং", e.spacing, 1f..2f) { vm.update(e.copy(spacing = it), "sp") }
                        Sl("ঘূর্ণন (°)", e.rot, -180f..180f) { vm.update(e.copy(rot = it), "r") }
                    }
                }
                is EqEl -> {
                    TextButton({ vm.beginEdit(e) }) { Text("✎ সূত্র সম্পাদনা") }
                    if (expanded) Sl("আকার", e.size, 10f..60f) { vm.update(e.copy(size = it), "s") }
                }
                is ImageEl -> {
                    Text("কোণার বিন্দু টেনে ছবির মাপ বদলান", Modifier.padding(start = 6.dp, bottom = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (expanded) {
                        Sl("প্রস্থ", e.w, 20f..560f) { vm.update(e.copy(w = it).fit(), "w") }
                        Sl("ঘূর্ণন (°)", e.rot, -180f..180f) { vm.update(e.copy(rot = it), "r") }
                        listOf("বাম", "উপর", "ডান", "নিচ").forEachIndexed { i, n ->
                            Sl("ক্রপ $n", e.crop[i], 0f..0.45f) { v -> vm.update(e.copy(crop = e.crop.toMutableList().also { it[i] = v }).fit(), "c$i") }
                        }
                    }
                }
                is TableEl -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ vm.update(e.copy(cells = e.cells + listOf(List(e.colW.size) { "" }))) }) { Text("+ সারি") }
                        TextButton({ if (e.cells.size > 1) vm.update(e.copy(cells = e.cells.dropLast(1))) }) { Text("− সারি") }
                        TextButton({ vm.update(e.copy(cells = e.cells.map { it + "" }, colW = e.colW + 80f)) }) { Text("+ কলাম") }
                        TextButton({ if (e.colW.size > 1) vm.update(e.copy(cells = e.cells.map { it.dropLast(1) }, colW = e.colW.dropLast(1))) }) { Text("− কলাম") }
                        listOf("◧", "▥", "◨").forEachIndexed { k, n -> FilterChip(e.align == k, { vm.update(e.copy(align = k)) }, { Text(n) }, Modifier.padding(2.dp)) }
                    }
                    Text("সেল লিখতে: টেবিল সিলেক্ট করে সেলে আবার ট্যাপ করুন", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (expanded) {
                        Sl("সারির উচ্চতা", e.rowH, 16f..80f) { vm.update(e.copy(rowH = it), "h") }
                        Sl("বর্ডার", e.border, 0f..3f) { vm.update(e.copy(border = it), "b") }
                        Sl("প্যাডিং", e.pad, 0f..12f) { vm.update(e.copy(pad = it), "p") }
                    }
                }
            }
        }
    }
}

// ---- dialogs ----

private val mathSymbols = listOf("°", "∠", "△", "²", "³", "√", "π", "×", "÷", "±", "≤", "≥", "≠", "∴", "θ", "⊥", "∥", "∞", "∈", "∪", "∩", "→")

private fun TextFieldValue.insert(s: String, back: Int = 0): TextFieldValue {
    val sel = selection
    return TextFieldValue(text.replaceRange(sel.min, sel.max, s), TextRange(sel.min + s.length - back))
}

@Composable
fun TextDialog(e: TextEl, onDismiss: () -> Unit, onDone: (TextEl) -> Unit) {
    var tf by remember { mutableStateOf(TextFieldValue(e.text, TextRange(0, e.text.length))) }
    val focus = remember { FocusRequester() }
    // the field may not be attached yet on the first frame: retry instead of throwing
    LaunchedEffect(Unit) { repeat(3) { delay(120L * (it + 1)); if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect } }
    var sz by remember { mutableFloatStateOf(e.size) }
    var sp by remember { mutableFloatStateOf(e.spacing) }
    var b by remember { mutableStateOf(e.bold) }
    var i by remember { mutableStateOf(e.italic) }
    var u by remember { mutableStateOf(e.underline) }
    var sf by remember { mutableStateOf(e.serif) }
    var al by remember { mutableIntStateOf(e.align) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Text("লেখা") },
        confirmButton = { TextButton({ onDone(e.copy(text = tf.text, size = sz, spacing = sp, bold = b, italic = i, underline = u, serif = sf, align = al)) }) { Text("ঠিক আছে") } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(tf, { tf = it }, Modifier.fillMaxWidth().focusRequester(focus), shape = RoundedCornerShape(16.dp), minLines = 2)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    mathSymbols.forEach { s -> TextButton({ tf = tf.insert(s) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(s) } }
                }
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
) + "π ∠ ° × ÷ ± ≤ ≥ ≠ ∞ △ θ α β ∫ Σ ∴".split(" ").map { Triple(it, it, 0) }

@Composable
fun EqDialog(e: EqEl, onDismiss: () -> Unit, onDone: (EqEl) -> Unit) {
    var tf by remember { mutableStateOf(TextFieldValue(e.src, TextRange(e.src.length))) }
    var sz by remember { mutableFloatStateOf(e.size) }
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Text("সূত্র") },
        confirmButton = { TextButton({ onDone(e.copy(src = tf.text, size = sz)) }) { Text("ঠিক আছে") } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Canvas(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))) {
                    drawIntoCanvas { c ->
                        val bx = eqBox(tf.text, sz)
                        c.nativeCanvas.save(); c.nativeCanvas.scale(density, density)
                        bx.draw(c.nativeCanvas, 12f, 48f + (bx.a - bx.d) / 2); c.nativeCanvas.restore()
                    }
                }
                OutlinedTextField(tf, { tf = it }, Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(16.dp), label = { Text("সূত্র (\\frac{a}{b}, x^2, \\sqrt{x}, \\mat{1&2;3&4})") })
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    quick.forEach { (label, code, back) -> TextButton({ tf = tf.insert(code, back) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(label) } }
                }
                Sl("আকার", sz, 10f..48f) { sz = it.roundToInt().toFloat() }
            }
        },
    )
}

@Composable
fun CellDialog(e: TableEl, rc: Pair<Int, Int>, onDismiss: () -> Unit, onDone: (TableEl) -> Unit) {
    var tf by remember { mutableStateOf(TextFieldValue(e.cells[rc.first][rc.second], TextRange(e.cells[rc.first][rc.second].length))) }
    var w by remember { mutableFloatStateOf(e.colW[rc.second]) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(3) { delay(120L * (it + 1)); if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect } }
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Text("সারি ${rc.first + 1}, কলাম ${rc.second + 1}") },
        confirmButton = {
            TextButton({
                onDone(e.copy(
                    cells = e.cells.mapIndexed { r, row -> if (r == rc.first) row.mapIndexed { c, s -> if (c == rc.second) tf.text else s } else row },
                    colW = e.colW.mapIndexed { c, x -> if (c == rc.second) w else x },
                ))
            }) { Text("ঠিক আছে") }
        },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
        text = {
            Column {
                OutlinedTextField(tf, { tf = it }, Modifier.fillMaxWidth().focusRequester(focus), shape = RoundedCornerShape(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    mathSymbols.forEach { s -> TextButton({ tf = tf.insert(s) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(s) } }
                }
                Sl("কলামের প্রস্থ", w, 30f..300f) { w = it.roundToInt().toFloat() }
            }
        },
    )
}

@Composable
private fun PagePreview(landscape: Boolean, cols: Int, margin: Float, gutter: Float) {
    val pw = if (landscape) A4H else A4W; val ph = if (landscape) A4W else A4H
    val h = 96.dp
    val cs = MaterialTheme.colorScheme
    val fill = cs.primary.copy(alpha = .28f)
    Canvas(Modifier.size(h * (pw / ph), h).border(1.dp, cs.outlineVariant).background(Color.White)) {
        val k = size.width / pw
        val cw = (pw - 2 * margin - (cols - 1) * gutter) / cols
        for (i in 0 until cols) drawRect(fill, Offset((margin + i * (cw + gutter)) * k, margin * k), Size(cw * k, (ph - 2 * margin) * k))
    }
}

@Composable
fun SetupDialog(d: Doc, onDismiss: () -> Unit, onDone: (Boolean, Float, Boolean, Int, Float, Boolean, Boolean) -> Unit) {
    var l by remember { mutableStateOf(d.landscape) }
    var m by remember { mutableFloatStateOf(d.margin) }
    var n by remember { mutableStateOf(d.pageNums) }
    var cols by remember { mutableIntStateOf(d.colCount) }
    var g by remember { mutableFloatStateOf(d.gutter) }
    var rule by remember { mutableStateOf(d.colRule) }
    var fit by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        confirmButton = { TextButton({ onDone(l, m, n, cols, g, rule, fit) }) { Text("ঠিক আছে") } },
        dismissButton = { TextButton(onDismiss) { Text("বাতিল") } },
        title = { Text("A4 পৃষ্ঠা ও কলাম") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { PagePreview(l, cols, m, g) }
                Text("দিক", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelMedium)
                Row {
                    FilterChip(!l, { l = false }, { Text("পোর্ট্রেট") }, Modifier.padding(2.dp))
                    FilterChip(l, { l = true }, { Text("ল্যান্ডস্কেপ") }, Modifier.padding(2.dp))
                }
                Text("কলাম", style = MaterialTheme.typography.labelMedium)
                Row {
                    FilterChip(cols == 1, { cols = 1 }, { Text("১ কলাম") }, Modifier.padding(2.dp))
                    FilterChip(cols == 2, { cols = 2 }, { Text("২ কলাম") }, Modifier.padding(2.dp))
                }
                if (cols == 2) {
                    Sl("কলামের ফাঁক (pt)", g, 10f..50f) { g = it.roundToInt().toFloat() }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rule, { rule = it }); Text("কলামের মাঝে দাগ") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(fit, { fit = it }); Text("বিদ্যমান লেখা/টেবিল/ছবি কলামে বসান", Modifier.weight(1f)) }
                Sl("মার্জিন (pt)", m, 10f..80f) { m = it.roundToInt().toFloat() }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(n, { n = it }); Text("পৃষ্ঠা নম্বর") }
            }
        },
    )
}
