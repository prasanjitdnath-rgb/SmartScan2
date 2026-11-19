@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.smartscan.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ExtractScreen(
    scans: List<ScanItem>,
    store: FieldStore,
    onBack: () -> Unit,
    onExport: (List<String>, List<List<String>>, Set<Int>) -> Unit
) {
    val custom = remember { mutableStateListOf<ExtractField>().apply { addAll(store.custom()) } }
    val selected = remember { mutableStateListOf<String>().apply { addAll(store.selected()) } }
    val values = remember { mutableStateMapOf<String, String>() }
    var showAdd by remember { mutableStateOf(false) }
    val texts = remember(scans) { scans.associate { it.name to it.text } }
    val allFields = FieldExtractor.presets + custom
    val active = allFields.filter { it.id in selected }

    LaunchedEffect(selected.toList(), custom.toList()) {
        active.forEach { f ->
            scans.forEach { s ->
                val k = "${s.name}|${f.id}"
                if (!values.containsKey(k)) values[k] = FieldExtractor.extract(texts[s.name] ?: "", f)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Excel - smart fields") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Choose the columns you need", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Values are detected automatically from the scanned text. You can correct any value before exporting.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            allFields.forEach { f ->
                                FilterChip(
                                    selected = f.id in selected,
                                    onClick = {
                                        if (f.id in selected) selected.remove(f.id) else selected.add(f.id)
                                        store.saveSelected(selected.toList())
                                    },
                                    label = { Text(f.label) }
                                )
                            }
                        }
                        OutlinedButton({ showAdd = true }, Modifier.padding(top = 10.dp)) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Add custom field")
                        }
                        custom.forEach { f ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.label, style = MaterialTheme.typography.bodyMedium)
                                    Text("Looks for: ${f.keywords.joinToString(", ")}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton({
                                    custom.remove(f); selected.remove(f.id)
                                    store.saveCustom(custom.toList()); store.saveSelected(selected.toList())
                                }) { Icon(Icons.Default.Delete, "Remove field") }
                            }
                        }
                    }
                }
            }
            items(scans, key = { it.name }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        active.forEach { f ->
                            val k = "${s.name}|${f.id}"
                            OutlinedTextField(
                                value = values[k] ?: "", onValueChange = { values[k] = it },
                                label = { Text(f.label) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (active.isEmpty()) Text("Select at least one field above.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(
            onClick = {
                val headers = listOf("File") + active.map { it.label }
                val rows = scans.map { s -> listOf(s.name) + active.map { f -> values["${s.name}|${f.id}"] ?: "" } }
                val numeric = active.mapIndexedNotNull { i, f -> if (f.type == FieldType.AMOUNT) i + 1 else null }.toSet()
                onExport(headers, rows, numeric)
            },
            enabled = active.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(Icons.Default.TableChart, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Export ${scans.size} row(s) to Excel")
        }
    }

    if (showAdd) {
        var label by remember { mutableStateOf("") }
        var kw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Custom field") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("Column name (e.g. Customer ID)") }, singleLine = true)
                    OutlinedTextField(kw, { kw = it }, label = { Text("Words to look for, comma separated") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton({
                    val words = kw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (label.isNotBlank() && words.isNotEmpty()) {
                        val f = ExtractField("custom_${System.currentTimeMillis()}", label.trim(), words, FieldType.TEXT, true)
                        custom.add(f); selected.add(f.id)
                        store.saveCustom(custom.toList()); store.saveSelected(selected.toList())
                        showAdd = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton({ showAdd = false }) { Text("Cancel") } }
        )
    }
}
