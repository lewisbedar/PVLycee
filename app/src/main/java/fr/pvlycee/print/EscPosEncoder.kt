package fr.pvlycee.print

import java.io.ByteArrayOutputStream

/** Commandes ESC/POS génériques, compatibles avec les imprimantes premier prix. */
object EscPosEncoder {
    const val DEFAULT_BAND_HEIGHT = 64

    /** ESC @ : réinitialisation. */
    val INIT = byteArrayOf(0x1B, 0x40)

    /** ESC d n : avance de n lignes de texte. */
    fun feedLines(n: Int): ByteArray = byteArrayOf(0x1B, 0x64, n.coerceIn(0, 255).toByte())

    /**
     * GS v 0 m xL xH yL yH d1…dk : image raster.
     * x = octets par ligne, y = nombre de lignes de la bande.
     */
    fun rasterBand(img: MonoImage, yStart: Int, lines: Int): ByteArray {
        val bytesPerRow = (img.width + 7) / 8
        val h = lines.coerceAtMost(img.height - yStart)
        val out = ByteArrayOutputStream(8 + bytesPerRow * h)
        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
        out.write(bytesPerRow and 0xFF); out.write((bytesPerRow shr 8) and 0xFF)
        out.write(h and 0xFF); out.write((h shr 8) and 0xFF)
        for (y in yStart until yStart + h) out.write(img.rowMsbFirst(y, bytesPerRow))
        return out.toByteArray()
    }

    /**
     * Découpe l'image en bandes de [bandHeight] lignes pour ne pas saturer le tampon des
     * petites imprimantes. Chaque élément est envoyé séparément par le pilote.
     */
    fun encode(img: MonoImage, bandHeight: Int = DEFAULT_BAND_HEIGHT, feed: Int = 4): List<ByteArray> {
        val parts = ArrayList<ByteArray>()
        parts += INIT
        var y = 0
        while (y < img.height) {
            parts += rasterBand(img, y, bandHeight)
            y += bandHeight
        }
        if (feed > 0) parts += feedLines(feed)
        return parts
    }
}
