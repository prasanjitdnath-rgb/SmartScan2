@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartscan.app

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ViewerScreen(
    scan: ScanItem,
    loadOcr: (String) -> List<OcrPage>,
    tts: SpeechHelper,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit
) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val ocr by produceState(initialValue = emptyList<OcrPage>(), scan.name, scan.stamp) {
        value = withContext(Dispatchers.IO) { loadOcr(scan.name) }
    }
    var selected by remember(scan.name) { mutableStateOf(setOf<String>()) }
    val selText = remember(selected, ocr) {
        buildString {
            ocr.forEachIndexed { pi, p ->
                p.lines.forEachIndexed { li, l -> if ("$pi:$li" in selected) appendLine(l.text) }
            }
        }.trim()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(scan.name, maxLines = 1) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = {
                IconButton({
                    selected = ocr.flatMapIndexed { pi, p -> p.lines.indices.map { "$pi:$it" } }.toSet()
                }) { Icon(Icons.Default.SelectAll, "Select all text") }
                IconButton(onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onShare) { Icon(Icons.Default.Share, "Share PDF") }
                IconButton(onExport) { Icon(Icons.Default.Download, "Export") }
            }
        )
        Text(
            if (ocr.all { it.lines.isEmpty() }) "No text detected on this scan."
            else "Tap a line to select it, or long-press and drag to select several lines.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(scan.pages) { pi, f ->
                PageView(
                    file = f, page = ocr.getOrNull(pi), pageIdx = pi, selected = selected,
                    onToggle = { li ->
                        val k = "$pi:$li"
                        selected = if (k in selected) selected - k else selected + k
                    },
                    onAdd = { lis -> selected = selected + lis.map { "$pi:$it" } }
                )
            }
        }
        if (selected.isNotEmpty()) {
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selected.size} line(s)", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton({
                        clip.setText(AnnotatedString(selText))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                    TextButton({ shareText(ctx, selText) }) { Text("Share") }
                    TextButton({
                        if (!tts.toggle("sel", selText)) Toast.makeText(ctx, "Cannot read aloud", Toast.LENGTH_SHORT).show()
                    }) { Text(if (tts.currentKey == "sel") "Stop" else "Speak") }
                    TextButton({ selected = emptySet() }) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun PageView(
    file: File,
    page: OcrPage?,
    pageIdx: Int,
    selected: Set<String>,
    onToggle: (Int) -> Unit,
    onAdd: (List<Int>) -> Unit
) {
    val ratio = if (page != null && page.height > 0 && page.width > 0) page.width.toFloat() / page.height else 0.707f
    val toggle by rememberUpdatedState(onToggle)
    val add by rememberUpdatedState(onAdd)
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragRect by remember { mutableStateOf<Rect?>(null) }
    val selFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
        AsyncImage(
            model = fileModel(file), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds
        )
        if (page != null && page.width > 0) {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(page) {
                        detectTapGestures { o ->
                            val s = size.width.toFloat() / page.width
                            val hit = page.lines.indexOfFirst { l ->
                                Rect(l.left * s, l.top * s, l.right * s, l.bottom * s).inflate(8f).contains(o)
                            }
                            if (hit >= 0) toggle(hit)
                        }
                    }
                    .pointerInput(page) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { o ->
                                dragStart = o
                                dragRect = Rect(o, o)
                                val s = size.width.toFloat() / page.width
                                val hits = page.lines.indices.filter { i ->
                                    val l = page.lines[i]
                                    Rect(l.left * s, l.top * s, l.right * s, l.bottom * s).inflate(8f).contains(o)
                                }
                                if (hits.isNotEmpty()) add(hits)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val p = change.position
                                val r = Rect(
                                    Offset(minOf(dragStart.x, p.x), minOf(dragStart.y, p.y)),
                                    Offset(maxOf(dragStart.x, p.x), maxOf(dragStart.y, p.y))
                                )
                                dragRect = r
                                val s = size.width.toFloat() / page.width
                                val hits = page.lines.indices.filter { i ->
                                    val l = page.lines[i]
                                    r.overlaps(Rect(l.left * s, l.top * s, l.right * s, l.bottom * s))
                                }
                                if (hits.isNotEmpty()) add(hits)
                            },
                            onDragEnd = { dragRect = null },
                            onDragCancel = { dragRect = null }
                        )
                    }
            ) {
                val s = size.width / page.width
                page.lines.forEachIndexed { i, l ->
                    val tl = Offset(l.left * s, l.top * s)
                    val sz = Size((l.right - l.left) * s, (l.bottom - l.top) * s)
                    if ("$pageIdx:$i" in selected) drawRect(selFill, tl, sz)
                    else drawRect(outline, tl, sz, style = Stroke(width = 1.dp.toPx()))
                }
                dragRect?.let { drawRect(Color(0x551A56DB), it.topLeft, it.size, style = Stroke(width = 2.dp.toPx())) }
            }
        }
    }
}
