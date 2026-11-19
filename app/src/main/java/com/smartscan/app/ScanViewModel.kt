package com.smartscan.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScanViewModel(app: Application) : AndroidViewModel(app) {
    val repo = ScanRepository(app)
    val items = MutableStateFlow(repo.list())
    val busy = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val editTick = MutableStateFlow(0)
    private var dirty = false

    private fun refresh() { items.value = repo.list() }

    private fun run(msg: String, work: suspend () -> Unit, done: () -> Unit = {}) {
        viewModelScope.launch {
            busy.value = msg
            try {
                withContext(Dispatchers.IO) { work() }
            } catch (e: Exception) {
                message.value = "Error: ${e.message}"
            }
            refresh()
            busy.value = null
            done()
        }
    }

    fun saveScan(r: GmsDocumentScanningResult, target: String?, done: () -> Unit) {
        run("Processing and reading text...", { repo.saveScan(r, target); dirty = false }, done)
    }

    fun importPdf(uri: Uri, done: () -> Unit) {
        run("Importing PDF...", { repo.importPdf(uri) }, done)
    }

    fun merge(list: List<ScanItem>, done: () -> Unit) {
        run("Merging PDFs...", { repo.merge(list) }, done)
    }

    fun finishEdit(name: String, done: () -> Unit) {
        if (dirty) {
            dirty = false
            run("Saving changes...", { repo.finalize(name) }, done)
        } else done()
    }

    fun rotate(name: String, idx: Int, deg: Float) {
        viewModelScope.launch(Dispatchers.IO) { repo.rotate(name, idx, deg); dirty = true; editTick.value++ }
    }

    fun filter(name: String, idx: Int, mode: Int) {
        viewModelScope.launch(Dispatchers.IO) { repo.filter(name, idx, mode); dirty = true; editTick.value++ }
    }

    fun deletePage(name: String, idx: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repo.deletePage(name, idx)) { dirty = true; editTick.value++ }
        }
    }

    fun pageFiles(name: String): List<File> = repo.pages(name)

    fun rename(i: ScanItem, n: String): Boolean {
        val ok = repo.rename(i, n)
        refresh()
        return ok
    }

    fun delete(list: List<ScanItem>) {
        list.forEach { repo.delete(it) }
        refresh()
    }

    fun saveToGallery(i: ScanItem): Int = repo.saveToGallery(i)
}
