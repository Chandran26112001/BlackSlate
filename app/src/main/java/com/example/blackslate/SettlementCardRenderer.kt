package com.example.blackslate

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.io.FileOutputStream

/** Creates a private, local image attachment for a respectful real-life settlement reminder. */
object SettlementCardRenderer {
    fun render(context: Context, person: Person, balances: Map<Currency, Long>, transactions: List<Transaction>,
        appearance: Appearance): File? = runCatching {
        val width = 1080
        val top = 640
        val rowHeight = 242
        val relevant = transactions.filter { tx -> tx.shares.any { it.person.id == person.id } }
        val height = top + (relevant.size * rowHeight) + 172
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val headline = ResourcesCompat.getFont(context, R.font.ntype82_headline) ?: Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val body = ResourcesCompat.getFont(context, R.font.helvetica) ?: Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val bold = Typeface.create(body, Typeface.BOLD)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(value: String, x: Float, y: Float, size: Float, color: Int, face: Typeface = body) {
            paint.typeface = face; paint.textSize = size; paint.color = color; paint.style = Paint.Style.FILL
            canvas.drawText(value, x, y, paint)
        }
        fun fittedText(value: String, x: Float, y: Float, maxWidth: Float, size: Float, color: Int,
            face: Typeface = body, alignRight: Boolean = false) {
            paint.typeface = face; paint.textSize = size; paint.color = color; paint.style = Paint.Style.FILL
            val measured = paint.measureText(value)
            if (measured > maxWidth) paint.textSize *= maxWidth / measured
            canvas.drawText(value, if (alignRight) x - paint.measureText(value) else x, y, paint)
        }
        fun rounded(rect: RectF, color: Int, radius: Float, stroke: Int? = null) {
            paint.style = Paint.Style.FILL; paint.color = color; canvas.drawRoundRect(rect, radius, radius, paint)
            if (stroke != null) { paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = stroke; canvas.drawRoundRect(rect, radius, radius, paint) }
        }
        fun ellipsize(value: String, widthLimit: Float): String {
            paint.typeface = body; paint.textSize = 28f
            return if (paint.measureText(value) <= widthLimit) value else {
                var short = value
                while (short.isNotEmpty() && paint.measureText(short + "…") > widthLimit) short = short.dropLast(1)
                short + "…"
            }
        }

        val dark = appearance == Appearance.MATTE || (appearance == Appearance.SYSTEM &&
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        val paper = if (dark) Color.rgb(10, 11, 14) else Color.rgb(228, 230, 235)
        val card = if (dark) Color.rgb(25, 27, 33) else Color.rgb(248, 249, 252)
        val ink = if (dark) Color.rgb(245, 244, 249) else Color.rgb(25, 27, 33)
        val muted = if (dark) Color.rgb(164, 166, 178) else Color.rgb(86, 92, 105)
        val line = if (dark) Color.rgb(67, 70, 82) else Color.rgb(185, 190, 201)
        val teal = if (dark) Color.rgb(43, 216, 176) else Color.rgb(0, 133, 106)
        val red = if (dark) Color.rgb(246, 88, 92) else Color.rgb(181, 42, 53)
        val totalCard = if (dark) Color.rgb(20, 49, 45) else Color.rgb(188, 235, 221)
        val totalBorder = if (dark) Color.rgb(50, 120, 108) else Color.rgb(0, 133, 106)
        canvas.drawColor(paper)

        text("BLACK SLATE", 72f, 94f, 25f, muted, bold)
        text("Settle in real life", 72f, 174f, 66f, ink, headline)
        fittedText(person.name, 72f, 236f, 936f, 34f, muted, body)
        val total = balances.filterValues { it > 0L }.entries.joinToString(" · ") { (currency, cents) -> money(cents, currency) }
        rounded(RectF(72f, 290f, 1008f, 448f), totalCard, 30f, totalBorder)
        text("TOTAL TO SETTLE", 108f, 344f, 22f, muted, bold)
        fittedText(total, 108f, 412f, 864f, 60f, teal, headline)
        text("Details", 72f, 530f, 24f, muted, bold)
        text("LENDING & GROUP EXPENSE", 72f, 566f, 22f, muted, bold)

        var y = 606f
        relevant.sortedByDescending { it.createdAt }.forEach { tx ->
            val share = tx.shares.firstOrNull { it.person.id == person.id } ?: return@forEach
            val accent = if (tx.kind == CategoryType.LENDING) teal else red
            rounded(RectF(72f, y, 1008f, y + 208f), card, 28f, line)
            rounded(RectF(72f, y, 82f, y + 208f), accent, 6f)
            val title = if (tx.kind == CategoryType.LENDING) "Lent" else "Group expense"
            text(title, 112f, y + 51f, 34f, ink, headline)
            val amountLabel = if (tx.kind == CategoryType.LENDING) money(share.cents, tx.currency)
                else "Total " + money(tx.cents, tx.currency) + " · their share " + money(share.cents, tx.currency)
            text(ellipsize(amountLabel, 820f), 112f, y + 91f, 28f, muted)
            text(dateLabel(tx.createdAt), 112f, y + 128f, 26f, muted)
            if (tx.note.isNotBlank()) text("Note · " + ellipsize(tx.note, 700f), 112f, y + 169f, 27f, ink)
            val state = if (share.outstanding == 0L) "Settled" else money(share.outstanding, tx.currency) + " pending"
            fittedText(state, 968f, y + 51f, 410f, 24f,
                if (share.outstanding == 0L) muted else teal, bold, alignRight = true)
            y += rowHeight
        }
        text("Black Slate · respectful reminders, no payments", 72f, (height - 68).toFloat(), 23f, muted)
        val destination = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(destination, "settlement-${person.id}-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        file
    }.getOrNull()
}
