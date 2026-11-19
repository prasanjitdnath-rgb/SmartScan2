package com.smartscan.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanRepository(private val ctx: Context) {
    val dir = File(ctx.filesDir, "scans").apply { mkdirs() }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // ---------- listing ----------
    fun list(): List<ScanItem> = dir.listFiles { f -> f.extension == "pdf" }.orEmpty()
        .sortedByDescending { it.lastModified() }.map { get(it.nameWithoutExtension) }

    fun get(base: String): ScanItem {
        val pdf = File(dir, "$base.pdf")
        return ScanItem(base, pdf, pages(base), File(dir, "$base.json"), File(dir, "$base.txt"), pdf.lastModified())
    }

    fun pages(base: String): List<File> {
        val rx = Regex("${Regex.escape(base)}_p(\\d+)\\.jpg")
        return dir.listFiles().orEmpty().filter { rx.matches(it.name) }
            .sortedBy { it.name.substringAfterLast("_p").removeSuffix(".jpg").toInt() }
    }

    private fun pageFile(base: String, i: Int) = File(dir, "${base}_p$i.jpg")

    private fun uniqueName(prefix: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var n = "${prefix}_$ts"
        var k = 1
        while (File(dir, "$n.pdf").exists() || pageFile(n, 1).exists()) { n = "${prefix}_${ts}_$k"; k++ }
        return n
    }

    // ---------- create ----------
    suspend fun saveScan(result: GmsDocumentScanningResult, target: String?): String {
        val base = target ?: uniqueName("Scan")
        var n = if (target == null) 0 else pages(base).size
        result.pages.orEmpty().forEach { p -> n++; copy(p.imageUri, pageFile(base, n)) }
        finalize(base)
        return base
    }

    suspend fun importPdf(uri: Uri): String {
        val base = uniqueName("Import")
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Cannot open file")
        val renderer = PdfRenderer(pfd)
        try {
            val count = minOf(renderer.pageCount, 60)
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                try {
                    val bmp = Bitmap.createBitmap(page.width * 3, page.height * 3, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    saveJpeg(bmp, pageFile(base, i + 1))
                    bmp.recycle()
                } finally { page.close() }
            }
        } finally { renderer.close(); pfd.close() }
        finalize(base)
        return base
    }

    fun merge(list: List<ScanItem>): String {
        val base = uniqueName("Merged")
        var n = 0
        val allPages = ArrayList<OcrPage>()
        val sb = StringBuilder()
        list.forEach { s ->
            s.pages.forEach { f -> n++; f.copyTo(pageFile(base, n), overwrite = true) }
            allPages.addAll(readOcr(s.name))
            sb.append(s.text).append("\n\n")
        }
        writeOcr(base, allPages, sb.toString().trim())
        buildPdf(base)
        return base
    }

    suspend fun finalize(base: String) {
        buildPdf(base)
        runOcr(base)
    }

    private fun copy(uri: Uri, to: File) {
        ctx.contentResolver.openInputStream(uri)?.use { i -> to.outputStream().use { o -> i.copyTo(o) } }
    }

    private fun saveJpeg(b: Bitmap, f: File) {
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    // ---------- PDF ----------
    private fun decode(f: File, maxW: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= maxW) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    fun buildPdf(base: String) {
        val doc = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        pages(base).forEachIndexed { i, f ->
            val bmp = decode(f, 1700) ?: return@forEachIndexed
            val pw = 595
            val ph = (595f * bmp.height / bmp.width).toInt().coerceAtLeast(100)
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
            page.canvas.drawBitmap(bmp, null, Rect(0, 0, pw, ph), paint)
            doc.finishPage(page)
            bmp.recycle()
        }
        File(dir, "$base.pdf").outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    // ---------- OCR ----------
    suspend fun runOcr(base: String) {
        val result = ArrayList<OcrPage>()
        val sb = StringBuilder()
        pages(base).forEach { f ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            val lines = ArrayList<OcrLine>()
            try {
                val r = recognizer.process(InputImage.fromFilePath(ctx, Uri.fromFile(f))).await()
                for (blk in r.textBlocks) {
                    for (ln in blk.lines) {
                        val bb = ln.boundingBox ?: continue
                        lines.add(OcrLine(ln.text, bb.left, bb.top, bb.right, bb.bottom))
                        sb.append(ln.text).append('\n')
                    }
                    sb.append('\n')
                }
            } catch (_: Exception) { }
            result.add(OcrPage(bounds.outWidth, bounds.outHeight, lines))
            sb.append('\n')
        }
        writeOcr(base, result, sb.toString().trim())
    }

    private fun writeOcr(base: String, pages: List<OcrPage>, text: String) {
        val arr = JSONArray()
        pages.forEach { p ->
            val la = JSONArray()
            p.lines.forEach { l ->
                la.put(JSONObject().put("t", l.text).put("l", l.left).put("tp", l.top).put("r", l.right).put("b", l.bottom))
            }
            arr.put(JSONObject().put("w", p.width).put("h", p.height).put("lines", la))
        }
        File(dir, "$base.json").writeText(JSONObject().put("pages", arr).toString())
        File(dir, "$base.txt").writeText(text)
    }

    fun readOcr(base: String): List<OcrPage> {
        val f = File(dir, "$base.json")
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONObject(f.readText()).getJSONArray("pages")
            (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                val la = p.getJSONArray("lines")
                OcrPage(p.getInt("w"), p.getInt("h"), (0 until la.length()).map { j ->
                    val l = la.getJSONObject(j)
                    OcrLine(l.getString("t"), l.getInt("l"), l.getInt("tp"), l.getInt("r"), l.getInt("b"))
                })
            }
        } catch (_: Exception) { emptyList() }
    }

    // ---------- editing ----------
    fun rotate(base: String, idx: Int, deg: Float) {
        val f = pageFile(base, idx)
        val b = BitmapFactory.decodeFile(f.path) ?: return
        val m = Matrix().apply { postRotate(deg) }
        val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        saveJpeg(r, f)
    }

    /** mode: 1 = grayscale, 2 = black & white, 3 = enhance */
    fun filter(base: String, idx: Int, mode: Int) {
        val f = pageFile(base, idx)
        val src = BitmapFactory.decodeFile(f.path) ?: return
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix()
        when (mode) {
            1 -> cm.setSaturation(0f)
            2 -> {
                cm.setSaturation(0f)
                cm.postConcat(ColorMatrix(floatArrayOf(
                    2.2f, 0f, 0f, 0f, -150f,
                    0f, 2.2f, 0f, 0f, -150f,
                    0f, 0f, 2.2f, 0f, -150f,
                    0f, 0f, 0f, 1f, 0f)))
            }
            else -> cm.postConcat(ColorMatrix(floatArrayOf(
                1.25f, 0f, 0f, 0f, -22f,
                0f, 1.25f, 0f, 0f, -22f,
                0f, 0f, 1.25f, 0f, -22f,
                0f, 0f, 0f, 1f, 0f)))
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        saveJpeg(out, f)
    }

    fun deletePage(base: String, idx: Int): Boolean {
        val n = pages(base).size
        if (n <= 1) return false
        pageFile(base, idx).delete()
        for (i in idx + 1..n) pageFile(base, i).renameTo(pageFile(base, i - 1))
        return true
    }

    // ---------- file ops ----------
    fun rename(item: ScanItem, newName: String): Boolean {
        val n = newName.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "_")
        if (n.isEmpty() || n == item.name || File(dir, "$n.pdf").exists()) return false
        item.pdf.renameTo(File(dir, "$n.pdf"))
        item.txtFile.renameTo(File(dir, "$n.txt"))
        item.ocrFile.renameTo(File(dir, "$n.json"))
        item.pages.forEach { it.renameTo(File(dir, it.name.replaceFirst(item.name, n))) }
        return true
    }

    fun delete(item: ScanItem) {
        item.pdf.delete(); item.txtFile.delete(); item.ocrFile.delete(); item.pages.forEach { it.delete() }
    }

    fun saveToGallery(item: ScanItem): Int {
        var n = 0
        item.pages.forEach { f ->
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, f.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartScan")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
                n++
            }
        }
        return n
    }
}
