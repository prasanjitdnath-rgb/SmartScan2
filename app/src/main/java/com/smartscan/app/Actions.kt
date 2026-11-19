package com.smartscan.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

object Deliver {
    const val PDF = "application/pdf"
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val TXT = "text/plain"
    const val JPG = "image/jpeg"

    fun exportsDir(ctx: Context): File = File(ctx.cacheDir, "exports").apply { mkdirs() }

    fun saveToDownloads(ctx: Context, file: File, mime: String): Boolean {
        return try {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/SmartScan")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            if (uri == null) {
                false
            } else {
                ctx.contentResolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
                true
            }
        } catch (e: Exception) { false }
    }

    private fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

    fun share(ctx: Context, files: List<File>, mime: String) {
        val uris = ArrayList<Uri>(files.map { uriFor(ctx, it) })
        val i = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        i.type = mime
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share"))
    }
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(i, "Share text"))
}

fun printPdf(ctx: Context, item: ScanItem) {
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(item.name, PdfAdapter(item.pdf), PrintAttributes.Builder().build())
}

private class PdfAdapter(val file: File) : PrintDocumentAdapter() {
    override fun onLayout(o: PrintAttributes?, n: PrintAttributes?, c: CancellationSignal?,
                          cb: LayoutResultCallback, e: Bundle?) {
        if (c?.isCanceled == true) { cb.onLayoutCancelled(); return }
        cb.onLayoutFinished(PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true)
    }
    override fun onWrite(p: Array<out PageRange>?, d: ParcelFileDescriptor, c: CancellationSignal?, cb: WriteResultCallback) {
        FileInputStream(file).use { i -> FileOutputStream(d.fileDescriptor).use { o -> i.copyTo(o) } }
        cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }
}
