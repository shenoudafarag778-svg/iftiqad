package com.iftiqad.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class PdfBuilder private constructor(private val ctx: Context) {

    private val W = 842
    private val H = 595
    private val M = 28f
    private val usable = W - 2 * M
    private val limit = H - M - 16f

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNo = 0

    private class Cell(val x: Float, val w: Float, val l: StaticLayout, val bg: String)

    companion object {
        fun build(ctx: Context, json: String, fileName: String): File {
            return PdfBuilder(ctx).run(JSONObject(json), fileName)
        }
    }

    private fun layout(
        text: String, width: Int, size: Float, bold: Boolean,
        align: Layout.Alignment, forceRtl: Boolean, color: Int = Color.BLACK
    ): StaticLayout {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = size
        p.color = color
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val heur = if (forceRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.ANYRTL_LTR
        return StaticLayout.Builder.obtain(text, 0, text.length, p, if (width < 1) 1 else width)
            .setAlignment(align)
            .setTextDirection(heur)
            .build()
    }

    private fun put(l: StaticLayout, x: Float, yy: Float) {
        val c = canvas!!
        c.save()
        c.translate(x, yy)
        l.draw(c)
        c.restore()
    }

    private fun finishPage() {
        page?.let { doc.finishPage(it) }
        page = null
    }

    private fun newPage() {
        finishPage()
        pageNo++
        val info = PdfDocument.PageInfo.Builder(W, H, pageNo).create()
        page = doc.startPage(info)
        canvas = page!!.canvas
        y = M
        val f = layout(
            "تطبيق افتقاد — صفحة $pageNo", 300, 8f, false,
            Layout.Alignment.ALIGN_CENTER, true, Color.GRAY
        )
        put(f, W / 2f - 150f, H - 22f)
    }

    private fun bgColor(code: String): Int = when (code) {
        "g" -> Color.parseColor("#C6F6D5")
        "r" -> Color.parseColor("#FED7D7")
        "y" -> Color.parseColor("#FEF3C7")
        "h" -> Color.parseColor("#E5E7EB")
        else -> 0
    }

    private fun prep(row: JSONArray, widths: FloatArray, fs: Float, head: Boolean): Pair<List<Cell>, Float> {
        val cells = ArrayList<Cell>()
        var col = 0
        var right = M + usable
        var maxH = 0f
        for (i in 0 until row.length()) {
            val raw = row.get(i)
            val o = if (raw is JSONObject) raw else JSONObject().put("t", raw.toString())
            val span = o.optInt("s", 1)
            var w = 0f
            for (k in col until minOf(col + span, widths.size)) w += widths[k]
            val x = right - w
            val isR = o.optString("a") == "r"
            val l = layout(
                o.optString("t"), (w - 8f).toInt(), fs, head || o.optBoolean("b"),
                if (isR) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER,
                isR
            )
            cells.add(Cell(x, w, l, if (head) "h" else o.optString("bg")))
            if (l.height > maxH) maxH = l.height.toFloat()
            right -= w
            col += span
        }
        return Pair(cells, maxOf(maxH + 8f, 18f))
    }

    private fun drawRow(p: Pair<List<Cell>, Float>) {
        val c = canvas!!
        val h = p.second
        val stroke = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = Color.parseColor("#9CA3AF")
        }
        for (cell in p.first) {
            val bg = bgColor(cell.bg)
            if (bg != 0) {
                val fill = Paint().apply { style = Paint.Style.FILL; color = bg }
                c.drawRect(cell.x, y, cell.x + cell.w, y + h, fill)
            }
            c.drawRect(cell.x, y, cell.x + cell.w, y + h, stroke)
            put(cell.l, cell.x + 4f, y + (h - cell.l.height) / 2f)
        }
        y += h
    }

    private fun table(t: JSONObject) {
        val fs = t.optDouble("fs", 9.0).toFloat()
        val colsA = t.getJSONArray("cols")
        val n = colsA.length()
        var sum = 0.0
        for (i in 0 until n) sum += colsA.getDouble(i)
        val widths = FloatArray(n) { (colsA.getDouble(it) / sum * usable).toFloat() }
        val heads = t.getJSONArray("head")
        val rows = t.getJSONArray("rows")

        val headingText = t.optString("heading")
        if (y + 90f > limit) newPage()
        if (headingText.isNotEmpty()) {
            val hl = layout(headingText, usable.toInt(), 13f, true, Layout.Alignment.ALIGN_NORMAL, true)
            put(hl, M, y)
            y += hl.height + 4f
        }
        val headPreps = ArrayList<Pair<List<Cell>, Float>>()
        for (k in 0 until heads.length()) headPreps.add(prep(heads.getJSONArray(k), widths, fs, true))
        for (hp in headPreps) drawRow(hp)

        for (r in 0 until rows.length()) {
            val pr = prep(rows.getJSONArray(r), widths, fs, false)
            if (y + pr.second > limit) {
                newPage()
                for (hp in headPreps) drawRow(hp)
            }
            drawRow(pr)
        }

        val foot = t.optString("foot")
        if (foot.isNotEmpty()) {
            val fl = layout(foot, usable.toInt(), 8f, false, Layout.Alignment.ALIGN_NORMAL, true, Color.DKGRAY)
            if (y + fl.height + 4f > limit) newPage()
            y += 3f
            put(fl, M, y)
            y += fl.height
        }
        y += 14f
    }

    private fun run(o: JSONObject, fileName: String): File {
        newPage()
        val w = usable.toInt()
        val title = layout(o.optString("title"), w, 20f, true, Layout.Alignment.ALIGN_CENTER, true)
        put(title, M, y)
        y += title.height + 4f
        val sub = layout(o.optString("sub"), w, 10f, false, Layout.Alignment.ALIGN_CENTER, true, Color.DKGRAY)
        put(sub, M, y)
        y += sub.height + 8f
        val sm = o.optJSONArray("summary")
        if (sm != null) {
            for (i in 0 until sm.length()) {
                val l = layout(sm.getString(i), w, 10f, false, Layout.Alignment.ALIGN_NORMAL, true)
                put(l, M, y)
                y += l.height + 2f
            }
        }
        y += 8f
        val tables = o.getJSONArray("tables")
        for (i in 0 until tables.length()) table(tables.getJSONObject(i))
        finishPage()

        val dir = File(ctx.cacheDir, "reports")
        dir.mkdirs()
        dir.listFiles()?.forEach {
            if (System.currentTimeMillis() - it.lastModified() > 86400000L) it.delete()
        }
        val safe = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val f = File(dir, safe)
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
