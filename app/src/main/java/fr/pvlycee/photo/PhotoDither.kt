package fr.pvlycee.photo

/**
 * Conversion d'une image en niveaux de gris vers des points noirs et blancs, sans Android
 * (testable sur JVM). Les luminances vont de 0 (noir) à 255 (blanc) ; le résultat vaut
 * `true` pour un point noir.
 */
object PhotoDither {

    /** Applique luminosité (-100..100) et contraste (0,5..2) autour du gris moyen. */
    fun adjust(lum: Int, brightness: Int, contrast: Float): Int =
        (((lum - 128) * contrast) + 128 + brightness).toInt().coerceIn(0, 255)

    /** Tramage Floyd-Steinberg : rend les dégradés d'une photo avec des points noirs. */
    fun floydSteinberg(lum: IntArray, width: Int, height: Int, brightness: Int = 0, contrast: Float = 1f): BooleanArray {
        require(lum.size == width * height) { "Taille incohérente" }
        val buf = FloatArray(lum.size) { adjust(lum[it], brightness, contrast).toFloat() }
        val out = BooleanArray(lum.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val old = buf[i]
                val black = old < 128f
                out[i] = black
                val err = old - if (black) 0f else 255f
                if (x + 1 < width) buf[i + 1] += err * 7 / 16
                if (y + 1 < height) {
                    if (x > 0) buf[i + width - 1] += err * 3 / 16
                    buf[i + width] += err * 5 / 16
                    if (x + 1 < width) buf[i + width + 1] += err * 1 / 16
                }
            }
        }
        return out
    }

    /** Seuil simple : adapté au texte, aux dessins et aux documents. */
    fun threshold(lum: IntArray, brightness: Int = 0, contrast: Float = 1f, level: Int = 128): BooleanArray =
        BooleanArray(lum.size) { adjust(lum[it], brightness, contrast) < level }

    /** Luminance perçue d'une couleur ARGB (transparent = blanc). */
    fun luminance(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        if (a < 128) return 255
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Dimensions pour ramener une image à [targetWidth] en gardant ses proportions. */
    fun scaledSize(srcW: Int, srcH: Int, targetWidth: Int, maxHeight: Int): Pair<Int, Int> {
        val h = (srcH.toLong() * targetWidth / srcW.coerceAtLeast(1)).toInt().coerceIn(1, maxHeight)
        return targetWidth to h
    }
}
