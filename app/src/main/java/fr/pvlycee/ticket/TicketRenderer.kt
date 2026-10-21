package fr.pvlycee.ticket

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import fr.pvlycee.R
import fr.pvlycee.domain.PvRules
import fr.pvlycee.domain.TicketData
import kotlin.math.roundToInt

/**
 * Dessine le ticket sur un Canvas à la largeur du papier (384 ou 576 points),
 * puis le convertit en noir et blanc pur (aucun niveau de gris).
 */
class TicketRenderer(context: Context) {
    private val titleFont: Typeface = loadFont(context, R.font.oswald_bold, Typeface.DEFAULT_BOLD)
    // Corps du ticket en demi-gras : en graisse normale, les traits fins se perdent au passage
    // en noir et blanc et le texte sort pâle et haché (constaté sur Sunmi V2).
    private val monoFont: Typeface = loadFont(context, R.font.jetbrains_mono_semibold, Typeface.MONOSPACE)
    private val monoBold: Typeface = loadFont(context, R.font.jetbrains_mono_bold, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD))

    /** Rendu du PV. */
    fun render(t: TicketData, widthDots: Int): Bitmap = draw(widthDots) { pen ->
        val s = pen.scale
        // Bandeau noir.
        pen.blackBand("AVIS DE CONTRAVENTION", 40f * s)
        pen.space(6f)
        pen.text("POLICE DE LA CLASSE · BRIGADE DE LA VIE SCOLAIRE", monoBold, 15f, Layout.Alignment.ALIGN_CENTER)
        pen.space(6f)
        pen.dashed()

        pen.field("PV N°", t.number, bold = true)
        pen.field("Le", "${t.date} à ${t.time}")
        val lieu = listOf(t.school, t.room.takeIf { it.isNotBlank() }?.let { "salle $it" })
            .filterNotNull().filter { it.isNotBlank() }.joinToString(", ")
        if (lieu.isNotBlank()) pen.field("Lieu", lieu)
        if (t.className.isNotBlank()) pen.field("Classe", t.className)
        pen.field("Contrevenant", t.offender, bold = true)
        pen.dashed()

        pen.text("NATURE DE L'INFRACTION", monoBold, 15f)
        pen.space(2f)
        pen.text(t.infraction.uppercase(), titleFont, 30f)
        pen.space(4f)
        pen.text(t.article, monoFont, 17f)
        if (t.articleNote.isNotBlank()) pen.text("(${t.articleNote})", monoFont, 17f)
        pen.space(10f)

        // Encadré de la classe de contravention.
        pen.boxed {
            pen.text("CONTRAVENTION DE ${PvRules.contraventionClassLabel(t.contraventionClass).replace("classe", "CLASSE")}", titleFont, 24f, Layout.Alignment.ALIGN_CENTER)
            if (t.isRecidive) {
                val n = t.previousPvCount
                pen.text("RÉCIDIVE : $n PV antérieur${if (n > 1) "s" else ""}", monoBold, 16f, Layout.Alignment.ALIGN_CENTER)
            }
        }
        pen.space(10f)

        if (t.identified) {
            val pts = t.pointsRemoved
            pen.text("RETRAIT DE POINTS : $pts point${if (pts > 1) "s" else ""}", monoBold, 18f)
            pen.space(4f)
            pen.text("Permis de bonne conduite", monoFont, 16f)
            pen.space(4f)
            pen.licence(t.pointsBefore, t.pointsAfter)
            pen.space(4f)
            pen.text("Solde : ${t.pointsAfter} / ${PvRules.MAX_POINTS} points", monoBold, 18f)
            if (t.suspended) {
                pen.space(6f)
                pen.blackBand("PERMIS SUSPENDU", 28f * s)
            }
            pen.space(6f)
            pen.dashed()
        }

        pen.text("PEINE ENCOURUE", monoBold, 15f)
        pen.space(2f)
        pen.text(t.gage.ifBlank { "Simple avertissement" }, titleFont, 24f)
        pen.space(6f)
        pen.text("Amende : 0,00 €. Payable en bonne conduite.", monoFont, 16f)
        pen.space(10f)

        pen.barcode(t.number)
        pen.text(t.number.replace("-", " "), monoFont, 15f, Layout.Alignment.ALIGN_CENTER)
        pen.space(8f)

        pen.text("Agent verbalisateur : ${t.agent.ifBlank { "le professeur" }}", monoFont, 16f)
        pen.space(6f)
        pen.text(
            "Contestation possible auprès du bureau des réclamations : la corbeille, au fond de la salle.",
            monoFont, 15f,
        )
        pen.space(8f)
        pen.dashed()
        pen.text("Document humoristique sans valeur légale.", monoBold, 14f, Layout.Alignment.ALIGN_CENTER)
        // Marge finale pour déchirer proprement.
        pen.space(90f)
    }

    /** Mire de réglage : largeur utile, bandes noires, texte en plusieurs tailles. */
    fun renderTestPattern(widthDots: Int, printerLabel: String): Bitmap = draw(widthDots) { pen ->
        val w = pen.width
        pen.blackBand("TICKET DE TEST", 34f * pen.scale)
        pen.space(6f)
        pen.text("PVLycée : réglage de l'imprimante", monoBold, 16f, Layout.Alignment.ALIGN_CENTER)
        pen.text(printerLabel, monoFont, 15f, Layout.Alignment.ALIGN_CENTER)
        pen.space(8f)
        pen.text("Largeur utile : $w points (${if (w >= 576) 80 else 58} mm)", monoBold, 16f)
        pen.space(4f)
        pen.ruler()
        pen.space(4f)
        pen.text("Les deux traits verticaux marquent les bords de la zone imprimable : ils doivent être visibles en entier.", monoFont, 15f)
        pen.space(10f)
        for (h in listOf(4, 8, 16, 32)) {
            pen.solid(h.toFloat())
            pen.space(6f)
        }
        pen.text("Bandes noires : 4, 8, 16 et 32 points. Elles doivent être bien noires et régulières.", monoFont, 15f)
        pen.space(10f)
        for (size in listOf(14f, 16f, 20f, 26f)) pen.text("Corps ${size.toInt()} : éèàçù 0123456789", monoFont, size)
        for (size in listOf(22f, 32f, 44f)) pen.text("OSWALD ${size.toInt()}", titleFont, size)
        pen.space(6f)
        pen.checker()
        pen.space(6f)
        pen.text("Si le texte est pâle, augmentez l'intensité. S'il est coupé, changez la largeur.", monoFont, 15f)
        pen.space(90f)
    }

    /**
     * Ticket photo : [photo] est déjà en noir et blanc pur, à la largeur du papier.
     * Avec [evidence], il est encadré façon « pièce à conviction ».
     */
    fun renderPhoto(photo: Bitmap, widthDots: Int, caption: String, evidence: Boolean, date: String, school: String): Bitmap =
        draw(widthDots) { pen ->
            if (evidence) {
                pen.blackBand("PIÈCE À CONVICTION", 34f * pen.scale)
                pen.space(4f)
                pen.text("POLICE DE LA CLASSE · SERVICE DE L'IDENTITÉ SCOLAIRE", monoBold, 14f, Layout.Alignment.ALIGN_CENTER)
                pen.space(6f)
            }
            pen.image(photo)
            if (caption.isNotBlank()) {
                pen.space(6f)
                pen.text(caption, titleFont, 24f, Layout.Alignment.ALIGN_CENTER)
            }
            if (evidence) {
                pen.space(4f)
                pen.text(listOf(school, date).filter { it.isNotBlank() }.joinToString(", "), monoFont, 15f, Layout.Alignment.ALIGN_CENTER)
                pen.dashed()
                pen.text("Document humoristique sans valeur légale.", monoBold, 14f, Layout.Alignment.ALIGN_CENTER)
            }
            pen.space(90f)
        }

    // --- Moteur de dessin ---

    private fun draw(widthDots: Int, block: (Pen) -> Unit): Bitmap {
        val maxH = 6000
        val big = Bitmap.createBitmap(widthDots, maxH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(big)
        canvas.drawColor(Color.WHITE)
        val pen = Pen(canvas, widthDots)
        block(pen)
        val h = pen.y.roundToInt().coerceIn(1, maxH)
        val cropped = Bitmap.createBitmap(big, 0, 0, widthDots, h)
        if (cropped !== big) big.recycle()
        return toPureBlackAndWhite(cropped)
    }

    private inner class Pen(val canvas: Canvas, val width: Int) {
        val scale = width / 384f
        val margin = 10f * scale
        var y = 8f * scale
        private val inner get() = width - 2 * margin
        var inset = 0f
        private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

        fun space(px: Float) { y += px * scale }

        fun text(
            s: String,
            font: Typeface,
            size: Float,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
            color: Int = Color.BLACK,
            left: Float = margin + inset,
            avail: Float = inner - 2 * inset,
        ) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = font; textSize = size * scale; this.color = color
            }
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, avail.toInt().coerceAtLeast(1))
                .setAlignment(align)
                // Oswald a de grandes marges verticales : on resserre ses lignes.
                .setLineSpacing(0f, if (font == titleFont) 0.86f else 1.0f)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(left, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height
        }

        /** Ligne « Étiquette : valeur », la valeur passant à la ligne si besoin. */
        fun field(label: String, value: String, bold: Boolean = false) {
            val size = 17f
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = monoFont; textSize = size * scale }
            val labelText = "$label : "
            val lw = p.measureText(labelText)
            if (lw > inner * 0.5f) {
                text(labelText, monoFont, size)
                text(value, if (bold) monoBold else monoFont, size)
                return
            }
            val top = y
            text(labelText, monoFont, size)
            val afterLabel = y
            y = top
            text(value, if (bold) monoBold else monoFont, size, left = margin + lw, avail = inner - lw)
            y = maxOf(y, afterLabel)
            y += 2f * scale
        }

        fun blackBand(title: String, size: Float) {
            val h = size * 1.45f
            canvas.drawRect(0f, y, width.toFloat(), y + h, black)
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = titleFont; textSize = size; color = Color.WHITE }
            // Réduit la taille si le titre dépasse la largeur.
            while (p.measureText(title) > inner && p.textSize > 10f) p.textSize -= 1f
            val fm = p.fontMetrics
            val baseline = y + h / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText(title, (width - p.measureText(title)) / 2f, baseline, p)
            y += h
        }

        fun dashed() {
            space(4f)
            val p = Paint().apply {
                color = Color.BLACK; strokeWidth = 2f * scale; style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(8f * scale, 6f * scale), 0f)
            }
            canvas.drawLine(margin, y, width - margin, y, p)
            space(8f)
        }

        /** Image déjà à la largeur du papier, copiée point par point (aucun lissage). */
        fun image(b: Bitmap) {
            val left = ((width - b.width) / 2).toFloat()
            canvas.drawBitmap(b, left, y, null)
            y += b.height
        }

        fun solid(h: Float) {
            canvas.drawRect(0f, y, width.toFloat(), y + h * scale, black)
            y += h * scale
        }

        fun boxed(content: () -> Unit) {
            val top = y
            space(8f)
            inset = 8f * scale
            content()
            inset = 0f
            space(8f)
            val p = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f * scale }
            canvas.drawRect(margin, top, width - margin, y, p)
            space(2f)
        }

        /** Les 12 cases du permis : pleines = points restants, barrées = points perdus. */
        fun licence(before: Int, after: Int) {
            val n = PvRules.MAX_POINTS
            val gap = 4f * scale
            val cell = ((inner - gap * (n - 1)) / n).coerceAtMost(30f * scale)
            val total = cell * n + gap * (n - 1)
            var x = margin + (inner - total) / 2f
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2.5f * scale }
            val fill = Paint().apply { color = Color.BLACK }
            for (i in 0 until n) {
                val r = android.graphics.RectF(x, y, x + cell, y + cell)
                canvas.drawRect(r, stroke)
                when {
                    i < after -> canvas.drawRect(r.left + 3 * scale, r.top + 3 * scale, r.right - 3 * scale, r.bottom - 3 * scale, fill)
                    i < before -> {
                        // Points retirés par ce PV : croix épaisse.
                        val c = Paint(stroke).apply { strokeWidth = 3.5f * scale }
                        canvas.drawLine(r.left + 3 * scale, r.top + 3 * scale, r.right - 3 * scale, r.bottom - 3 * scale, c)
                        canvas.drawLine(r.right - 3 * scale, r.top + 3 * scale, r.left + 3 * scale, r.bottom - 3 * scale, c)
                    }
                    else -> canvas.drawLine(r.left + 3 * scale, r.bottom - 3 * scale, r.right - 3 * scale, r.top + 3 * scale, stroke)
                }
                x += cell + gap
            }
            y += cell
        }

        /** Faux code-barres déterministe dérivé du numéro de PV. */
        fun barcode(seed: String) {
            val h = 46f * scale
            val unit = 2f * scale
            var rnd = seed.fold(7L) { acc, c -> acc * 31 + c.code }
            val bars = ArrayList<Pair<Float, Boolean>>()
            var totalW = 0f
            var black = true
            // Garde de début et de fin, motif pseudo-aléatoire au milieu.
            repeat(60) {
                rnd = (rnd * 6364136223846793005L + 1442695040888963407L)
                val w = (1 + ((rnd ushr 33) % 3).toInt()) * unit
                bars += w to black
                totalW += w
                black = !black
            }
            val maxW = inner * 0.9f
            val k = if (totalW > maxW) maxW / totalW else 1f
            var x = (width - totalW * k) / 2f
            for ((w, b) in bars) {
                if (b) canvas.drawRect(x, y, x + w * k, y + h, this.black)
                x += w * k
            }
            y += h + 4f * scale
        }

        /** Règle graduée sur toute la largeur, avec un trait aux deux bords. */
        fun ruler() {
            val h = 30f * scale
            for (x in 0 until width step 8) {
                val big = x % 64 == 0
                val tick = if (big) h else h / 2
                canvas.drawRect(x.toFloat(), y + h - tick, x + 2f, y + h, black)
            }
            canvas.drawRect(0f, y - 6f, 3f, y + h + 6f, black)
            canvas.drawRect(width - 3f, y - 6f, width.toFloat(), y + h + 6f, black)
            y += h + 2f
        }

        fun checker() {
            val cell = 8
            val h = 32
            for (yy in 0 until h step cell) for (xx in 0 until width step cell) {
                if (((xx / cell) + (yy / cell)) % 2 == 0) canvas.drawRect(xx.toFloat(), y + yy, (xx + cell).toFloat(), y + yy + cell, black)
            }
            y += h
        }
    }

    companion object {
        /** Seuillage : chaque point devient noir ou blanc. */
        fun toPureBlackAndWhite(src: Bitmap): Bitmap {
            val w = src.width
            val h = src.height
            val out = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
            val row = IntArray(w)
            for (yy in 0 until h) {
                out.getPixels(row, 0, w, 0, yy, w, 1)
                for (x in 0 until w) {
                    val c = row[x]
                    val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                    row[x] = if (Color.alpha(c) >= 128 && lum < 128) Color.BLACK else Color.WHITE
                }
                out.setPixels(row, 0, w, 0, yy, w, 1)
            }
            return out
        }

        private fun loadFont(context: Context, id: Int, fallback: Typeface): Typeface =
            try { ResourcesCompat.getFont(context, id) ?: fallback } catch (_: Exception) { fallback }
    }
}
