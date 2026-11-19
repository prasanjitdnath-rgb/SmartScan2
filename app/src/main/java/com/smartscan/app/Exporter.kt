package com.smartscan.app

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes minimal but valid Word (.docx) and Excel (.xlsx) files without any external library. */
object Exporter {
    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private fun esc(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            if (c == '&') sb.append("&amp;")
            else if (c == '<') sb.append("&lt;")
            else if (c == '>') sb.append("&gt;")
            else if (c == '"') sb.append("&quot;")
            else if (c == '\n' || c == '\t' || c >= ' ') sb.append(c)
        }
        return sb.toString()
    }

    private fun zip(out: File, entries: List<Pair<String, String>>) {
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            entries.forEach { e ->
                z.putNextEntry(ZipEntry(e.first))
                z.write(e.second.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
    }

    // ---------------- Word ----------------
    private fun para(text: String, bold: Boolean = false): String {
        val rpr = if (bold) "<w:rPr><w:b/><w:sz w:val=\"30\"/></w:rPr>" else ""
        return "<w:p><w:r>$rpr<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"
    }

    fun docx(docs: List<Pair<String, List<OcrPage>>>, out: File) {
        val body = StringBuilder()
        docs.forEachIndexed { di, doc ->
            if (docs.size > 1) body.append(para(doc.first, true))
            doc.second.forEachIndexed { pi, page ->
                if (doc.second.size > 1) body.append(para("Page ${pi + 1}", true))
                page.lines.forEach { body.append(para(it.text)) }
                if (pi < doc.second.size - 1 || di < docs.size - 1) {
                    body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                }
            }
        }
        val document = XML + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" +
            body + "<w:sectPr/></w:body></w:document>"
        val types = XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"
        val rels = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
        zip(out, listOf("[Content_Types].xml" to types, "_rels/.rels" to rels, "word/document.xml" to document))
    }

    // ---------------- Excel ----------------
    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun strCell(ref: String, v: String, style: Int): String =
        "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>"

    fun xlsx(headers: List<String>, rows: List<List<String>>, numericCols: Set<Int>, out: File) {
        val sb = StringBuilder()
        sb.append(XML).append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        sb.append("<cols><col min=\"1\" max=\"${maxOf(headers.size, 1)}\" width=\"26\" customWidth=\"1\"/></cols><sheetData>")
        sb.append("<row r=\"1\">")
        headers.forEachIndexed { c, h -> sb.append(strCell("${colName(c)}1", h, 1)) }
        sb.append("</row>")
        rows.forEachIndexed { r, row ->
            val rn = r + 2
            sb.append("<row r=\"$rn\">")
            row.forEachIndexed { c, v ->
                val ref = "${colName(c)}$rn"
                val num = if (c in numericCols) v.replace(",", "").toDoubleOrNull() else null
                if (num != null) sb.append("<c r=\"$ref\" s=\"2\"><v>$num</v></c>")
                else sb.append(strCell(ref, v, 0))
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")

        val types = XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>"
        val rels = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
        val workbook = XML + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
        val wbRels = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>"
        val styles = XML + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1A56DB\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
            "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs></styleSheet>"
        zip(out, listOf(
            "[Content_Types].xml" to types, "_rels/.rels" to rels,
            "xl/workbook.xml" to workbook, "xl/_rels/workbook.xml.rels" to wbRels,
            "xl/styles.xml" to styles, "xl/worksheets/sheet1.xml" to sb.toString()
        ))
    }

    /** One row per recognised text line. */
    fun xlsxAllText(docs: List<Pair<String, List<OcrPage>>>, out: File) {
        val rows = ArrayList<List<String>>()
        docs.forEach { d ->
            d.second.forEachIndexed { pi, p ->
                p.lines.forEachIndexed { li, l -> rows.add(listOf(d.first, (pi + 1).toString(), (li + 1).toString(), l.text)) }
            }
        }
        xlsx(listOf("File", "Page", "Line", "Text"), rows, emptySet(), out)
    }

    fun txt(docs: List<Pair<String, List<OcrPage>>>, out: File) {
        val sb = StringBuilder()
        docs.forEach { d ->
            if (docs.size > 1) sb.append("=== ").append(d.first).append(" ===\n")
            d.second.forEach { p -> p.lines.forEach { sb.append(it.text).append('\n') }; sb.append('\n') }
        }
        out.writeText(sb.toString().trim())
    }
}
