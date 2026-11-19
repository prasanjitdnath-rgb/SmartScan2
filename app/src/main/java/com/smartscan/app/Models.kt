package com.smartscan.app

import java.io.File

data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
data class OcrPage(val width: Int, val height: Int, val lines: List<OcrLine>)

data class ScanItem(
    val name: String,
    val pdf: File,
    val pages: List<File>,
    val ocrFile: File,
    val txtFile: File,
    val stamp: Long
) {
    val date: Long get() = stamp
    val text: String get() = if (txtFile.exists()) txtFile.readText() else ""
}

enum class ExportKind { PDF, WORD, EXCEL_TEXT, EXCEL_FIELDS, JPEG, TEXT }

sealed class Screen {
    object Home : Screen()
    data class Viewer(val name: String) : Screen()
    data class Editor(val name: String) : Screen()
    data class Extract(val names: List<String>) : Screen()
}
