package com.smartscan.app

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: ScanViewModel by viewModels()
    private lateinit var tts: SpeechHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdManager.init(this)
        AdManager.showOnLaunch(this)
        tts = SpeechHelper(this)
        setContent { SmartScanTheme { Root() } }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Root() {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val scans by vm.items.collectAsState()
        val busy by vm.busy.collectAsState()
        val msg by vm.message.collectAsState()
        val store = remember { FieldStore(ctx) }
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        var scanTarget by remember { mutableStateOf<String?>(null) }
        var exportFor by remember { mutableStateOf<List<ScanItem>?>(null) }

        LaunchedEffect(msg) {
            msg?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.message.value = null }
        }

        // ----- scanner -----
        val scanner = remember {
            GmsDocumentScanning.getClient(
                GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(30)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
            )
        }
        val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK) {
                val res = GmsDocumentScanningResult.fromActivityResultIntent(r.data)
                if (res != null) {
                    vm.saveScan(res, scanTarget) { AdManager.afterAction(this@MainActivity) }
                }
            }
        }
        val startScan: (String?) -> Unit = { target ->
            scanTarget = target
            scanner.getStartScanIntent(this@MainActivity)
                .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { e ->
                    Toast.makeText(ctx, "Scanner unavailable: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
        val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importPdf(uri) { }
        }

        // ----- export -----
        fun deliver(file: File, mime: String) {
            val saved = Deliver.saveToDownloads(ctx, file, mime)
            if (saved) Toast.makeText(ctx, "Saved to Downloads/SmartScan", Toast.LENGTH_SHORT).show()
            Deliver.share(ctx, listOf(file), mime)
            AdManager.afterAction(this@MainActivity)
        }

        fun runExport(kind: ExportKind, list: List<ScanItem>) {
            exportFor = null
            when (kind) {
                ExportKind.EXCEL_FIELDS -> screen = Screen.Extract(list.map { it.name })
                ExportKind.PDF -> {
                    list.forEach { Deliver.saveToDownloads(ctx, it.pdf, Deliver.PDF) }
                    Toast.makeText(ctx, "Saved to Downloads/SmartScan", Toast.LENGTH_SHORT).show()
                    Deliver.share(ctx, list.map { it.pdf }, Deliver.PDF)
                    AdManager.afterAction(this@MainActivity)
                }
                ExportKind.JPEG -> {
                    list.forEach { vm.saveToGallery(it) }
                    Toast.makeText(ctx, "Saved to Pictures/SmartScan", Toast.LENGTH_SHORT).show()
                    Deliver.share(ctx, list.flatMap { it.pages }, Deliver.JPG)
                    AdManager.afterAction(this@MainActivity)
                }
                else -> scope.launch {
                    val base = if (list.size == 1) list[0].name else "SmartScan_${stamp()}"
                    val (file, mime) = withContext(Dispatchers.IO) {
                        val docs = list.map { it.name to vm.repo.readOcr(it.name) }
                        val dir = Deliver.exportsDir(ctx)
                        when (kind) {
                            ExportKind.WORD -> File(dir, "$base.docx").also { Exporter.docx(docs, it) } to Deliver.DOCX
                            ExportKind.EXCEL_TEXT -> File(dir, "$base.xlsx").also { Exporter.xlsxAllText(docs, it) } to Deliver.XLSX
                            else -> File(dir, "$base.txt").also { Exporter.txt(docs, it) } to Deliver.TXT
                        }
                    }
                    deliver(file, mime)
                }
            }
        }

        // ----- navigation -----
        BackHandler(screen !is Screen.Home) {
            val s = screen
            if (s is Screen.Editor) vm.finishEdit(s.name) { screen = Screen.Home } else screen = Screen.Home
        }

        Scaffold(
            bottomBar = { Banner() },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (val s = screen) {
                    is Screen.Home -> HomeScreen(
                        scans = scans, tts = tts,
                        onScan = { startScan(null) },
                        onImport = { pdfPicker.launch(arrayOf("application/pdf")) },
                        onOpen = { screen = Screen.Viewer(it.name) },
                        onEdit = { screen = Screen.Editor(it.name) },
                        onShare = { Deliver.share(ctx, listOf(it.pdf), Deliver.PDF) },
                        onExport = { exportFor = it },
                        onMerge = { list -> vm.merge(list) { AdManager.afterAction(this@MainActivity) } },
                        onRename = { item, n -> vm.rename(item, n) },
                        onDelete = { vm.delete(it) },
                        onPrint = { printPdf(ctx, it) },
                        onSaveJpeg = {
                            val n = vm.saveToGallery(it)
                            Toast.makeText(ctx, "Saved $n image(s) to Pictures/SmartScan", Toast.LENGTH_SHORT).show()
                        }
                    )
                    is Screen.Viewer -> {
                        val scan = scans.firstOrNull { it.name == s.name }
                        if (scan == null) LaunchedEffect(Unit) { screen = Screen.Home }
                        else ViewerScreen(
                            scan = scan, loadOcr = { vm.repo.readOcr(it) }, tts = tts,
                            onBack = { screen = Screen.Home },
                            onEdit = { screen = Screen.Editor(scan.name) },
                            onShare = { Deliver.share(ctx, listOf(scan.pdf), Deliver.PDF) },
                            onExport = { exportFor = listOf(scan) }
                        )
                    }
                    is Screen.Editor -> EditorScreen(
                        name = s.name, vm = vm,
                        onAddPages = { startScan(s.name) },
                        onDone = { vm.finishEdit(s.name) { screen = Screen.Home } }
                    )
                    is Screen.Extract -> {
                        val list = scans.filter { it.name in s.names }
                        ExtractScreen(
                            scans = list, store = store,
                            onBack = { screen = Screen.Home },
                            onExport = { headers, rows, numeric ->
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        File(Deliver.exportsDir(ctx), "SmartScan_Fields_${stamp()}.xlsx")
                                            .also { Exporter.xlsx(headers, rows, numeric, it) }
                                    }
                                    deliver(file, Deliver.XLSX)
                                }
                            }
                        )
                    }
                }
                busy?.let { BusyOverlay(it) }
            }
        }

        val ex = exportFor
        if (ex != null) {
            ExportSheet(count = ex.size, onPick = { runExport(it, ex) }, onDismiss = { exportFor = null })
        }
    }
}
