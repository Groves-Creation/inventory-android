package com.inventory.mobile.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.inventory.mobile.data.AuditDto
import com.inventory.mobile.data.ItemDto
import com.inventory.mobile.data.ReportRowDto
import java.io.File
import java.io.FileOutputStream

fun shareLabelsPdf(context: Context, labels: List<Pair<ItemDto, Int>>) {
    if (labels.isEmpty()) return
    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(directory, "inventory-labels.pdf")
    val document = PdfDocument()
    var pageNumber = 1
    val writer = MultiFormatWriter()
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 6.5f; isFakeBoldText = true }
    val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 8f; isFakeBoldText = true }
    val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 5f; textAlign = Paint.Align.CENTER }
    labels.forEach { (item, copies) ->
        repeat(copies) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(65, 65, pageNumber++).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            canvas.drawText(item.name.take(24), 3f, 8f, titlePaint)
            canvas.drawText("$" + "%.2f".format(item.displayPriceCents / 100.0), 3f, 18f, pricePaint)
            val code = item.code.orEmpty()
            val matrix = writer.encode(code, BarcodeFormat.UPC_A, 118, 42)
            val paint = Paint().apply { color = Color.BLACK }
            val left = 3f
            val top = 22f
            val width = 59f
            val height = 30f
            for (x in 0 until matrix.width) if (matrix[x, 0]) {
                val x1 = left + x * width / matrix.width
                val x2 = left + (x + 1) * width / matrix.width
                canvas.drawRect(x1, top, x2, top + height, paint)
            }
            canvas.drawText(code, 32.5f, 61f, codePaint)
            document.finishPage(page)
        }
    }
    FileOutputStream(file).use(document::writeTo)
    document.close()
    shareFile(context, file, "application/pdf")
}

fun shareCsv(context: Context, fileName: String, content: String) {
    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(directory, fileName)
    file.writeText(content, Charsets.UTF_8)
    shareFile(context, file, "text/csv")
}

private fun shareFile(context: Context, file: File, type: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share ${file.name}",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

fun reportCsv(rows: List<ReportRowDto>): String = buildString {
    appendLine("item_name,sku,price_cents,stock_quantity,expected_qty,counted_qty,variance,status")
    rows.forEach { row ->
        appendLine(listOf(row.itemName, row.sku, row.priceCents, row.stockQuantity, row.expectedQty, row.countedQty, row.variance, row.status).joinToString(",") { csv(it) })
    }
}

fun auditCsv(rows: List<AuditDto>): String = buildString {
    appendLine("timestamp,action,description")
    rows.forEach { appendLine(listOf(it.ts, it.action, it.description).joinToString(",") { value -> csv(value) }) }
}

private fun csv(value: Any?): String {
    val text = value?.toString().orEmpty()
    return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${text.replace("\"", "\"\"")}\"" else text
}
