@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartscan.app

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun EditorScreen(name: String, vm: ScanViewModel, onAddPages: () -> Unit, onDone: () -> Unit) {
    val tick by vm.editTick.collectAsState()
    val pages = remember(tick, name) { vm.pageFiles(name) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Edit scan", maxLines = 1) },
            navigationIcon = { IconButton(onDone) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = {
                IconButton(onAddPages) { Icon(Icons.Default.Add, "Add pages") }
                IconButton(onDone) { Icon(Icons.Default.Check, "Done") }
            }
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(pages) { i, f ->
                EditPageCard(
                    index = i + 1, total = pages.size, file = f,
                    onRotateLeft = { vm.rotate(name, i + 1, -90f) },
                    onRotateRight = { vm.rotate(name, i + 1, 90f) },
                    onFilter = { mode -> vm.filter(name, i + 1, mode) },
                    onDelete = { vm.deletePage(name, i + 1) }
                )
            }
        }
    }
}

@Composable
private fun EditPageCard(
    index: Int, total: Int, file: File,
    onRotateLeft: () -> Unit, onRotateRight: () -> Unit,
    onFilter: (Int) -> Unit, onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            AsyncImage(
                model = fileModel(file), contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(280.dp),
                contentScale = ContentScale.Fit
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Page $index of $total", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                IconButton(onRotateLeft) { Icon(Icons.Default.RotateLeft, "Rotate left", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onRotateRight) { Icon(Icons.Default.RotateRight, "Rotate right", tint = MaterialTheme.colorScheme.primary) }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Default.Tune, "Filters", tint = MaterialTheme.colorScheme.primary) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Enhance") }, onClick = { menu = false; onFilter(3) })
                        DropdownMenuItem(text = { Text("Grayscale") }, onClick = { menu = false; onFilter(1) })
                        DropdownMenuItem(text = { Text("Black and white") }, onClick = { menu = false; onFilter(2) })
                    }
                }
                IconButton({
                    if (total <= 1) Toast.makeText(ctx, "A scan needs at least one page", Toast.LENGTH_SHORT).show()
                    else onDelete()
                }) { Icon(Icons.Default.Delete, "Delete page", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
