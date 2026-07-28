package com.inventory.mobile.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.brother.sdk.lmprinter.setting.QLPrintSettings
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

/** A DK roll the store keeps loaded, paired with the size the printer must be told to expect. */
enum class LabelStock(
    val id: String,
    val displayName: String,
    val widthMm: Double,
    val heightMm: Double,
    val brotherSize: QLPrintSettings.LabelSize,
) {
    Dk1221("dk1221", "DK-1221 · 23 × 23 mm square", 23.0, 23.0, QLPrintSettings.LabelSize.DieCutW23H23),
    Dk1201("dk1201", "DK-1201 · 29 × 90 mm address", 29.0, 90.0, QLPrintSettings.LabelSize.DieCutW29H90),
    Dk1204("dk1204", "DK-1204 · 17 × 54 mm multipurpose", 17.0, 54.0, QLPrintSettings.LabelSize.DieCutW17H54),
    Dk1219("dk1219", "DK-1219 · 12 mm round", 12.0, 12.0, QLPrintSettings.LabelSize.RoundW12DIA),
    Dk2205("dk2205", "DK-2205 · 62 mm continuous", 62.0, 40.0, QLPrintSettings.LabelSize.RollW62),
    ;

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Dk1221
    }
}

data class LabelSpec(val name: String, val priceCents: Long, val code: String, val copies: Int)

object LabelRenderer {
    private const val PRINTER_DPI = 300.0
    private const val DOTS_PER_MM = PRINTER_DPI / 25.4

    fun render(label: LabelSpec, stock: LabelStock): Bitmap {
        val width = (stock.widthMm * DOTS_PER_MM).toInt()
        val height = (stock.heightMm * DOTS_PER_MM).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val padding = width * 0.04f
        val namePaint = textPaint(width * 0.10f, bold = true)
        val pricePaint = textPaint(width * 0.20f, bold = true)
        val codePaint = textPaint(width * 0.075f).apply { textAlign = Paint.Align.CENTER }
        val barPaint = Paint().apply { color = Color.BLACK }

        val usableWidth = width - padding * 2
        var cursorY = padding - namePaint.fontMetrics.ascent

        for (line in wrap(label.name, namePaint, usableWidth, maxLines = 2)) {
            canvas.drawText(line, padding, cursorY, namePaint)
            cursorY += namePaint.fontSpacing
        }

        cursorY += pricePaint.textSize * 0.15f
        canvas.drawText(formatPrice(label.priceCents), padding, cursorY - pricePaint.fontMetrics.ascent, pricePaint)
        cursorY += pricePaint.fontSpacing

        val codeBaseline = height - padding
        val codeTop = codeBaseline + codePaint.fontMetrics.ascent
        val barcodeTop = cursorY + padding * 0.5f
        val barcodeHeight = codeTop - barcodeTop - padding * 0.3f
        if (barcodeHeight > 0) {
            drawUpcA(canvas, label.code, padding, barcodeTop, usableWidth, barcodeHeight, barPaint)
        }
        canvas.drawText(label.code, width / 2f, codeBaseline, codePaint)
        return bitmap
    }

    private fun textPaint(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        isFakeBoldText = bold
    }

    private fun formatPrice(cents: Long) = "$" + "%.2f".format(cents / 100.0)

    private fun wrap(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val lines = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty() && lines.size < maxLines) {
            val counted = paint.breakText(remaining, true, maxWidth, null)
            if (counted <= 0) break
            if (counted == remaining.length) {
                lines.add(remaining)
                remaining = ""
                break
            }
            val lastSpace = remaining.lastIndexOf(' ', counted)
            // Break on a word boundary unless a single word already fills the line.
            val cut = if (lines.size == maxLines - 1 || lastSpace <= 0) counted else lastSpace
            lines.add(remaining.substring(0, cut).trim())
            remaining = remaining.substring(cut).trim()
        }
        if (remaining.isNotEmpty() && lines.isNotEmpty()) {
            val last = lines.removeAt(lines.size - 1)
            val room = paint.breakText(last, true, maxWidth - paint.measureText("…"), null)
            lines.add(last.substring(0, room.coerceAtLeast(0)).trimEnd() + "…")
        }
        return lines
    }

    // zxing renders a 1D symbology as a single-row matrix, so column presence in row 0
    // is the bar pattern and it is stretched to the requested height.
    private fun drawUpcA(canvas: Canvas, code: String, left: Float, top: Float, width: Float, height: Float, paint: Paint) {
        val matrix = runCatching { MultiFormatWriter().encode(code, BarcodeFormat.UPC_A, 1, 1) }.getOrNull() ?: return
        for (x in 0 until matrix.width) {
            if (!matrix[x, 0]) continue
            val x1 = left + x * width / matrix.width
            val x2 = left + (x + 1) * width / matrix.width
            canvas.drawRect(x1, top, x2, top + height, paint)
        }
    }
}
