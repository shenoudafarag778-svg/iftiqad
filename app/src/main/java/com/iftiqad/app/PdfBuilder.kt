package com.iftiqad.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    private val limit = H - M - 30f

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNo = 0

    private var accent = Color.parseColor("#3B82F6")
    private var accentDark = Color.parseColor("#1E3A8A")
    private var accentTint = Color.parseColor("#E8EEF9")
    private var runTitle = ""

    private class Cell(
        val x: Float, val w: Float, val l: StaticLayout,
        val fill: Int, val k: String, val col: Int
    )

    companion object {
        fun build(ctx: Context, json: String, fileName: String): File {
            return PdfBuilder(ctx).run(JSONObject(json), fileName)
        }
    }

    private fun mix(c: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(c) * (1 - t) + Color.red(to) * t).toInt(),
        (Color.green(c) * (1 - t) + Color.green(to) * t).toInt(),
        (Color.blue(c) * (1 - t) + Color.blue(to) * t).toInt()
    )

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

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
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
        val c = canvas!!
        // footer
        val line = Paint().apply {
            color = Color.parseColor("#E5E7EB"); strokeWidth = 0.8f
        }
        c.drawLine(M, H - 30f, W - M, H - 30f, line)
        val f = layout(
            "تطبيق افتقاد  •  صفحة $pageNo", 400, 8f, false,
            Layout.Alignment.ALIGN_CENTER, true, Color.GRAY
        )
        put(f, W / 2f - 200f, H - 24f)
        if (pageNo > 1 && runTitle.isNotEmpty()) {
            val t = layout(runTitle + " (تابع)", usable.toInt(), 9f, true, Layout.Alignment.ALIGN_NORMAL, true, accentDark)
            put(t, M, y)
            y += t.height + 8f
        }
    }

    private fun drawLogo(x: Float, yy: Float, size: Float) {
        val c = canvas!!
        c.drawRoundRect(RectF(x, yy, x + size, yy + size), size * 0.22f, size * 0.22f, fillPaint(Color.parseColor("#070B14")))
        val gold = fillPaint(Color.parseColor("#F5C542"))
        val s = size / 108f
        val path = Path().apply {
            moveTo(x + 50 * s, yy + 20 * s)
            rLineTo(8 * s, 0f); rLineTo(0f, 10 * s); rLineTo(10 * s, 0f)
            rLineTo(0f, 8 * s); rLineTo(-10 * s, 0f); rLineTo(0f, 16 * s)
            rLineTo(-8 * s, 0f); rLineTo(0f, -16 * s); rLineTo(-10 * s, 0f)
            rLineTo(0f, -8 * s); rLineTo(10 * s, 0f); close()
        }
        c.drawPath(path, gold)
        val heart = fillPaint(Color.parseColor("#6366F1"))
        c.drawCircle(x + 47 * s, yy + 61 * s, 8 * s, heart)
        c.drawCircle(x + 61 * s, yy + 61 * s, 8 * s, heart)
        val tri = Path().apply {
            moveTo(x + 39 * s, yy + 64 * s)
            lineTo(x + 69 * s, yy + 64 * s)
            lineTo(x + 54 * s, yy + 80 * s)
            close()
        }
        c.drawPath(tri, heart)
    }

    private fun banner(o: JSONObject) {
        val c = canvas!!
        val h = 72f
        c.drawRoundRect(RectF(M, y, M + usable, y + h), 14f, 14f, fillPaint(accent))
        drawLogo(M + 14f, y + 14f, 44f)
        val app = layout("تطبيق افتقاد", 110, 11f, true, Layout.Alignment.ALIGN_NORMAL, true, Color.WHITE)
        put(app, M + 64f, y + (h - app.height) / 2f)
        val tw = (usable - 200f).toInt()
        val title = layout(o.optString("title"), tw, 22f, true, Layout.Alignment.ALIGN_NORMAL, true, Color.WHITE)
        val sub = layout(o.optString("sub"), tw, 10.5f, false, Layout.Alignment.ALIGN_NORMAL, true, mix(Color.WHITE, accent, 0.15f))
        val total = title.height + 4f + sub.height
        val ty = y + (h - total) / 2f
        put(title, M + usable - 16f - tw, ty)
        put(sub, M + usable - 16f - tw, ty + title.height + 4f)
        y += h + 12f
    }

    private fun stats(arr: JSONArray?) {
        if (arr == null || arr.length() == 0) return
        val c = canvas!!
        val n = arr.length()
        val gap = 8f
        val bw = (usable - gap * (n - 1)) / n
        val bh = 46f
        val bg = fillPaint(Color.parseColor("#F3F4F6"))
        val bd = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 0.8f; color = Color.parseColor("#E5E7EB")
        }
        var right = M + usable
        for (i in 0 until n) {
            val o = arr.getJSONObject(i)
            val left = right - bw
            c.drawRoundRect(RectF(left, y, right, y + bh), 10f, 10f, bg)
            c.drawRoundRect(RectF(left, y, right, y + bh), 10f, 10f, bd)
            val v = layout(o.optString("v"), (bw - 8f).toInt(), 15f, true, Layout.Alignment.ALIGN_CENTER, false, accentDark)
            val l = layout(o.optString("l"), (bw - 8f).toInt(), 8.5f, false, Layout.Alignment.ALIGN_CENTER, false, Color.DKGRAY)
            val tot = v.height + l.height
            val ty = y + (bh - tot) / 2f
            put(v, left + 4f, ty)
            put(l, left + 4f, ty + v.height)
            right = left - gap
        }
        y += bh + 14f
    }

    private fun prep(
        row: JSONArray, widths: FloatArray, fs: Float, style: Int, zebra: Boolean
    ): Pair<List<Cell>, Float> {
        val cells = ArrayList<Cell>()
        var col = 0
        var right = M + usable
        var maxH = 0f
        val bgBase = when (style) {
            1 -> accent
            2 -> accentTint
            else -> if (zebra) Color.parseColor("#F9FAFB") else Color.WHITE
        }
        val txtBase = when (style) {
            1 -> Color.WHITE
            2 -> accentDark
            else -> Color.parseColor("#111827")
        }
        for (i in 0 until row.length()) {
            val raw = row.get(i)
            val o = if (raw is JSONObject) raw else JSONObject().put("t", raw.toString())
            val span = o.optInt("s", 1)
            var w = 0f
            for (k in col until minOf(col + span, widths.size)) w += widths[k]
            val x = right - w
            val isR = o.optString("a") == "r"
            val code = o.optString("bg")
            val kind = o.optString("k")
            var fill = bgBase
            var tc = txtBase
            if (style == 0) {
                when (code) {
                    "g" -> { fill = Color.parseColor("#D1FAE5"); tc = Color.parseColor("#065F46") }
                    "r" -> { fill = Color.parseColor("#FEE2E2"); tc = Color.parseColor("#991B1B") }
                    "y" -> { fill = Color.parseColor("#FEF3C7"); tc = Color.parseColor("#92400E") }
                }
                if (kind == "ok") fill = Color.parseColor("#D1FAE5")
                if (kind == "no") fill = Color.parseColor("#FEE2E2")
            }
            val bold = style != 0 || o.optBoolean("b") || code.isNotEmpty() || isR
            val l = layout(
                o.optString("t"), (w - 10f).toInt(), fs, bold,
                if (isR) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER,
                isR, tc
            )
            cells.add(Cell(x, w, l, fill, kind, col))
            if (l.height > maxH) maxH = l.height.toFloat()
            right -= w
            col += span
        }
        return Pair(cells, maxOf(maxH + 10f, 24f))
    }

    private fun drawRow(p: Pair<List<Cell>, Float>, grp: Int) {
        val c = canvas!!
        val h = p.second
        val border = Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 0.6f; color = Color.parseColor("#D1D5DB")
        }
        val thick = Paint().apply { strokeWidth = 1.4f; color = accent }
        val ok = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.2f; color = Color.parseColor("#059669")
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val no = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.2f; color = Color.parseColor("#DC2626")
            strokeCap = Paint.Cap.ROUND
        }
        for (cell in p.first) {
            c.drawRect(cell.x, y, cell.x + cell.w, y + h, fillPaint(cell.fill))
            c.drawRect(cell.x, y, cell.x + cell.w, y + h, border)
            val cx = cell.x + cell.w / 2f
            val cy = y + h / 2f
            if (cell.k == "ok") {
                val path = Path().apply {
                    moveTo(cx - 5f, cy); lineTo(cx - 1.5f, cy + 4f); lineTo(cx + 5.5f, cy - 4.5f)
                }
                c.drawPath(path, ok)
            } else if (cell.k == "no") {
                c.drawLine(cx - 4f, cy - 4f, cx + 4f, cy + 4f, no)
                c.drawLine(cx + 4f, cy - 4f, cx - 4f, cy + 4f, no)
            } else {
                put(cell.l, cell.x + 5f, y + (h - cell.l.height) / 2f)
            }
            if (grp > 0 && cell.col >= 1 && (cell.col - 1) % grp == 0) {
                c.drawLine(cell.x + cell.w, y, cell.x + cell.w, y + h, thick)
            }
        }
        y += h
    }

    private fun table(t: JSONObject) {
        val fs = t.optDouble("fs", 9.0).toFloat()
        val grp = t.optInt("grp", 0)
        val colsA = t.getJSONArray("cols")
        val n = colsA.length()
        var sum = 0.0
        for (i in 0 until n) sum += colsA.getDouble(i)
        val widths = FloatArray(n) { (colsA.getDouble(it) / sum * usable).toFloat() }
        val heads = t.getJSONArray("head")
        val rows = t.getJSONArray("rows")

        if (y + 100f > limit) newPage()
        val headingText = t.optString("heading")
        if (headingText.isNotEmpty()) {
            val hl = layout(headingText, (usable - 14f).toInt(), 12.5f, true, Layout.Alignment.ALIGN_NORMAL, true, accentDark)
            canvas!!.drawRoundRect(
                RectF(M + usable - 4f, y, M + usable, y + hl.height), 2f, 2f, fillPaint(accent)
            )
            put(hl, M, y)
            y += hl.height + 6f
        }
        val headPreps = ArrayList<Pair<List<Cell>, Float>>()
        val hn = heads.length()
        for (k in 0 until hn) {
            val style = if (k == 0) 1 else 2
            headPreps.add(prep(heads.getJSONArray(k), widths, fs, style, false))
        }
        for (hp in headPreps) drawRow(hp, grp)

        for (r in 0 until rows.length()) {
            val pr = prep(rows.getJSONArray(r), widths, fs, 0, r % 2 == 1)
            if (y + pr.second > limit) {
                newPage()
                for (hp in headPreps) drawRow(hp, grp)
            }
            drawRow(pr, grp)
        }

        val foot = t.optString("foot")
        if (foot.isNotEmpty()) {
            val fl = layout(foot, usable.toInt(), 8f, false, Layout.Alignment.ALIGN_NORMAL, true, Color.DKGRAY)
            if (y + fl.height + 6f > limit) newPage()
            y += 4f
            put(fl, M, y)
            y += fl.height
        }
        y += 16f
    }

    private fun run(o: JSONObject, fileName: String): File {
        try {
            accent = Color.parseColor(o.optString("accent", "#3B82F6"))
        } catch (e: Exception) {
        }
        accentDark = mix(accent, Color.BLACK, 0.45f)
        accentTint = mix(accent, Color.WHITE, 0.86f)
        runTitle = o.optString("title")

        newPage()
        banner(o)
        stats(o.optJSONArray("stats"))
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
