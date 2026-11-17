package io.github.sebasgl1.queprefieres

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File

/** Dibuja la pregunta (y el resultado, si ya se votó) como imagen y abre el menú de compartir. */
fun shareQuestion(context: Context, game: Game) {
    val q = game.current ?: return
    val w = 1080; val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Ink.toArgb())

    val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F1FA.toInt(); textSize = 76f; typeface = bold }
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9690A8.toInt(); textSize = 36f }
    c.drawText("¿Qué prefieres?", 80f, 160f, title)
    c.drawText("${CATEGORY_EMOJI[q.categoria] ?: ""} ${q.categoria}", 80f, 225f, small)

    val total = game.countA() + game.countB()
    val showPct = game.revealed && total > 0
    fun card(top: Float, text: String, color: Int, letter: String, votes: Int) {
        val r = RectF(80f, top, w - 80f, top + 440f)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawRoundRect(r, 56f, 56f, bg)
        val ink = Ink.toArgb()
        val lp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ink; textSize = 40f; typeface = bold; alpha = 150 }
        c.drawText(letter, r.left + 44f, r.top + 70f, lp)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ink; textSize = if (text.length > 60) 54f else 64f; typeface = bold }
        val width = (r.width() - 120f).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, width).setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(if (showPct) 4 else 5).setEllipsize(TextUtils.TruncateAt.END).build()
        val extra = if (showPct) 90f else 0f
        c.save()
        c.translate(r.left + 60f, r.centerY() - (layout.height + extra) / 2f)
        layout.draw(c)
        c.restore()
        if (showPct) {
            val pp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ink; textSize = 72f; typeface = bold; textAlign = Paint.Align.CENTER }
            c.drawText("${Math.round(votes * 100f / total)}%", r.centerX(), r.centerY() + (layout.height + extra) / 2f, pp)
        }
    }
    card(290f, game.optionA(), ColorA.toArgb(), "A", game.countA())
    card(770f, game.optionB(), ColorB.toArgb(), "B", game.countB())
    val o = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.toArgb() }
    c.drawCircle(w / 2f, 750f, 62f, o)
    val oc = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F1FA.toInt(); textSize = 52f; typeface = bold; textAlign = Paint.Align.CENTER }
    c.drawText("o", w / 2f, 768f, oc)
    if (showPct) c.drawText("$total ${if (total == 1) "voto" else "votos"} del parche", 80f, 1290f, small)

    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "pregunta.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val text = "¿Qué prefieres? ${game.optionA()} o ${game.optionB()}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Compartir pregunta").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
