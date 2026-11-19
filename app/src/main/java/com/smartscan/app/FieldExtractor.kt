package com.smartscan.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class FieldType { TEXT, DATE, AMOUNT, ID }

data class ExtractField(
    val id: String,
    val label: String,
    val keywords: List<String>,
    val type: FieldType,
    val custom: Boolean = false
)

object FieldExtractor {
    val presets = listOf(
        ExtractField("invoice_no", "Invoice Number",
            listOf("invoice number", "invoice no", "invoice #", "inv no", "inv.", "bill number", "bill no", "receipt no", "voucher no"), FieldType.ID),
        ExtractField("invoice_date", "Invoice Date",
            listOf("invoice date", "bill date", "receipt date", "dated", "date"), FieldType.DATE),
        ExtractField("due_date", "Due Date", listOf("due date", "payment due", "pay by"), FieldType.DATE),
        ExtractField("bill_amount", "Bill Amount",
            listOf("grand total", "total amount", "net amount", "amount payable", "total payable", "amount due",
                "net payable", "invoice total", "total"), FieldType.AMOUNT),
        ExtractField("tax_amount", "Tax / GST Amount",
            listOf("total gst", "total tax", "igst", "cgst", "sgst", "gst", "vat", "tax"), FieldType.AMOUNT),
        ExtractField("vendor", "Vendor / Party Name", emptyList(), FieldType.TEXT),
        ExtractField("gstin", "GSTIN", emptyList(), FieldType.ID),
        ExtractField("pan", "PAN", emptyList(), FieldType.ID),
        ExtractField("po_no", "PO Number", listOf("po number", "po no", "p.o. no", "purchase order"), FieldType.ID),
        ExtractField("phone", "Phone", emptyList(), FieldType.TEXT),
        ExtractField("email", "Email", emptyList(), FieldType.TEXT)
    )

    private const val MON = "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*"
    private val DATE_RX = Regex(
        "\\b\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}\\b|\\b\\d{1,2}(?:st|nd|rd|th)?[ \\-]${MON}[ \\-,.]*\\d{2,4}\\b|\\b${MON}\\.? \\d{1,2},? \\d{4}\\b",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_RX = Regex("\\d[\\d,]*(?:\\.\\d+)?")
    private val ID_RX = Regex("[A-Za-z0-9][A-Za-z0-9/_.\\-]*")
    private val GSTIN = Regex("\\b\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]\\b")
    private val PAN = Regex("\\b[A-Z]{5}\\d{4}[A-Z]\\b")
    private val PHONE = Regex("(?<!\\d)(?:\\+\\d{1,3}[\\s-]?)?\\d{10}(?!\\d)")
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val SKIP_VENDOR = listOf("invoice", "tax", "bill", "receipt", "gst", "date", "original", "copy", "money", "cash memo")

    fun extract(text: String, f: ExtractField): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        when (f.id) {
            "gstin" -> return GSTIN.find(text)?.value ?: ""
            "pan" -> return PAN.find(text)?.value ?: ""
            "phone" -> return PHONE.find(text)?.value ?: ""
            "email" -> return EMAIL.find(text)?.value ?: ""
            "vendor" -> return vendor(lines)
        }
        for (kw in f.keywords) {
            val rx = Regex("(?<![A-Za-z])" + Regex.escape(kw.trim()) + "(?![A-Za-z])", RegexOption.IGNORE_CASE)
            val found = ArrayList<String>()
            for ((i, line) in lines.withIndex()) {
                val m = rx.find(line) ?: continue
                if (f.type == FieldType.AMOUNT && line.contains("sub", true) && (kw == "total" || kw == "amount")) continue
                val after = line.substring(m.range.last + 1).trimStart(' ', ':', '-', '.', '#', '=', '\t')
                var v = parse(after, f.type)
                if (v.isEmpty() && after.isBlank() && i + 1 < lines.size) v = parse(lines[i + 1], f.type)
                if (v.isNotEmpty()) found.add(v)
            }
            if (found.isNotEmpty()) return if (f.type == FieldType.AMOUNT) found.last() else found.first()
        }
        return ""
    }

    private fun parse(s: String, type: FieldType): String = when (type) {
        FieldType.DATE -> DATE_RX.find(s)?.value?.trim() ?: ""
        FieldType.AMOUNT -> AMOUNT_RX.findAll(s).map { it.value }.lastOrNull { it.any(Char::isDigit) }?.replace(",", "") ?: ""
        FieldType.ID -> ID_RX.findAll(s).map { it.value }.firstOrNull { it.any(Char::isDigit) } ?: ""
        FieldType.TEXT -> s.trim()
    }

    private fun vendor(lines: List<String>): String {
        for (l in lines.take(8)) {
            if (l.length !in 3..60) continue
            val letters = l.count { it.isLetter() }
            if (letters * 10 < l.length * 6) continue
            if (SKIP_VENDOR.any { l.contains(it, true) }) continue
            return l
        }
        return ""
    }
}

class FieldStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("fields", Context.MODE_PRIVATE)

    fun custom(): List<ExtractField> {
        val raw = sp.getString("custom", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ExtractField(o.getString("id"), o.getString("label"),
                    o.getString("kw").split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    FieldType.valueOf(o.getString("type")), true)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveCustom(list: List<ExtractField>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("label", it.label)
                .put("kw", it.keywords.joinToString(",")).put("type", it.type.name))
        }
        sp.edit().putString("custom", arr.toString()).apply()
    }

    fun selected(): List<String> =
        (sp.getString("selected", null) ?: "invoice_no,invoice_date,bill_amount").split(",").filter { it.isNotEmpty() }

    fun saveSelected(ids: List<String>) {
        sp.edit().putString("selected", ids.joinToString(",")).apply()
    }
}
