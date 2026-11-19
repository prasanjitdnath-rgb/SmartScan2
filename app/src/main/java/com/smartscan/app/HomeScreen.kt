@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.smartscan.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    scans: List<ScanItem>,
    tts: SpeechHelper,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onOpen: (ScanItem) -> Unit,
    onEdit: (ScanItem) -> Unit,
    onShare: (ScanItem) -> Unit,
    onExport: (List<ScanItem>) -> Unit,
    onMerge: (List<ScanItem>) -> Unit,
    onRename: (ScanItem, String) -> Boolean,
    onDelete: (List<ScanItem>) -> Unit,
    onPrint: (ScanItem) -> Unit,
    onSaveJpeg: (ScanItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf<List<ScanItem>?>(null) }
    val texts = remember(scans) { scans.associate { it.name to it.text } }
    val filtered = remember(scans, query, texts) {
        if (query.isBlank()) scans
        else scans.filter { it.name.contains(query, true) || (texts[it.name] ?: "").contains(query, true) }
    }
    val chosen = scans.filter { it.name in selected }
    val selecting = selected.isNotEmpty()
    BackHandler(selecting) { selected = emptySet() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                Surface(color = MaterialTheme.colorScheme.primary) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton({ selected = emptySet() }) { Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.onPrimary) }
                        Text("${chosen.size} selected", Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium)
                        if (chosen.size >= 2) {
                            IconButton({ onMerge(chosen); selected = emptySet() }) {
                                Icon(Icons.Default.MergeType, "Merge PDFs", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        IconButton({ onExport(chosen) }) { Icon(Icons.Default.Download, "Export", tint = MaterialTheme.colorScheme.onPrimary) }
                        IconButton({ confirmDelete = chosen }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            } else {
                Header(query) { query = it }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(Modifier.size(12.dp))
                        Text(if (scans.isEmpty()) "No scans yet" else "No matching scans", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (scans.isEmpty()) "Tap Scan to capture a document. Text is read automatically." else "Try a different word.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 160.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filtered, key = { it.name }) { s ->
                        ScanCard(
                            item = s, snippet = texts[s.name] ?: "", tts = tts,
                            selecting = selecting, isSelected = s.name in selected,
                            onClick = {
                                if (selecting) selected = if (s.name in selected) selected - s.name else selected + s.name
                                else onOpen(s)
                            },
                            onLongClick = { selected = selected + s.name },
                            onEdit = { onEdit(s) }, onShare = { onShare(s) },
                            onExport = { onExport(listOf(s)) }, onRename = onRename,
                            onDelete = { confirmDelete = listOf(s) },
                            onPrint = { onPrint(s) }, onSaveJpeg = { onSaveJpeg(s) }
                        )
                    }
                }
            }
        }

        if (!selecting) {
            Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = onImport, containerColor = MaterialTheme.colorScheme.surface) {
                    Icon(Icons.Default.PictureAsPdf, "Import PDF", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.size(12.dp))
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Default.CameraAlt, null) },
                    text = { Text("Scan") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    val del = confirmDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (del.size > 1) "Delete ${del.size} scans?" else "Delete scan?") },
            text = { Text(if (del.size == 1) del[0].name else "This cannot be undone.") },
            confirmButton = { TextButton({ onDelete(del); selected = emptySet(); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Header(query: String, onQuery: (String) -> Unit) {
    val fg = Color(0xFF111827)
    val hint = Color(0xFF6B7280)
    Column(
        Modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Brand, BrandDark)),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .statusBarsPadding()
            .padding(20.dp, 16.dp, 20.dp, 22.dp)
    ) {
        Text("Smart Scan", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Scan, read text, export", color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(16.dp))
        TextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search scans and text") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ onQuery("") }) { Icon(Icons.Default.Close, "Clear") } },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = fg, unfocusedTextColor = fg,
                focusedPlaceholderColor = hint, unfocusedPlaceholderColor = hint,
                focusedLeadingIconColor = hint, unfocusedLeadingIconColor = hint,
                focusedTrailingIconColor = hint, unfocusedTrailingIconColor = hint
            )
        )
    }
}

@Composable
private fun ScanCard(
    item: ScanItem, snippet: String, tts: SpeechHelper,
    selecting: Boolean, isSelected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit,
    onEdit: () -> Unit, onShare: () -> Unit, onExport: () -> Unit,
    onRename: (ScanItem, String) -> Boolean, onDelete: () -> Unit,
    onPrint: () -> Unit, onSaveJpeg: () -> Unit
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val speaking = tts.currentKey == item.name
    val container = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(12.dp)) {
            Box {
                AsyncImage(
                    model = fileModel(item.pages.firstOrNull()),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp, 106.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                if (selecting) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.align(Alignment.TopStart))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.date)) +
                        "  \u2022  ${item.pages.size} page" + if (item.pages.size == 1) "" else "s",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    snippet.ifBlank { "No text detected" }.replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onEdit, Modifier.size(40.dp)) { Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary) }
                    IconButton({
                        if (!tts.toggle(item.name, snippet)) Toast.makeText(ctx, "No text to read", Toast.LENGTH_SHORT).show()
                    }, Modifier.size(40.dp)) {
                        Icon(if (speaking) Icons.Default.Stop else Icons.Default.VolumeUp, "Read aloud",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onShare, Modifier.size(40.dp)) { Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onExport, Modifier.size(40.dp)) { Icon(Icons.Default.Download, "Export", tint = MaterialTheme.colorScheme.primary) }
                    Box {
                        IconButton({ menu = true }, Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = true })
                            DropdownMenuItem(text = { Text("Print") }, onClick = { menu = false; onPrint() })
                            DropdownMenuItem(text = { Text("Save pages as JPEG") }, onClick = { menu = false; onSaveJpeg() })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
        }
    }

    if (rename) {
        var newName by remember { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton({
                    if (!onRename(item, newName)) Toast.makeText(ctx, "Invalid or duplicate name", Toast.LENGTH_SHORT).show()
                    rename = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ rename = false }) { Text("Cancel") } }
        )
    }
}
