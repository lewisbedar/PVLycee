package fr.pvlycee.print

/**
 * Image 1 bit indépendante d'Android (testable sur JVM).
 * `true` = point noir (chauffé).
 */
class MonoImage(val width: Int, val height: Int, private val pixels: BooleanArray = BooleanArray(width * height)) {
    init {
        require(width > 0 && height >= 0) { "Dimensions invalides" }
        require(pixels.size == width * height) { "Taille des données incohérente" }
    }

    operator fun get(x: Int, y: Int): Boolean = pixels[y * width + x]
    operator fun set(x: Int, y: Int, black: Boolean) { pixels[y * width + x] = black }

    /** Octets d'une ligne, bit de poids fort = point le plus à gauche (ESC/POS). */
    fun rowMsbFirst(y: Int, bytesPerRow: Int = (width + 7) / 8): ByteArray {
        val out = ByteArray(bytesPerRow)
        for (x in 0 until minOf(width, bytesPerRow * 8)) {
            if (this[x, y]) out[x shr 3] = (out[x shr 3].toInt() or (0x80 ushr (x and 7))).toByte()
        }
        return out
    }

    /** Octets d'une ligne, bit de poids faible = point le plus à gauche (cat printers). */
    fun rowLsbFirst(y: Int, bytesPerRow: Int = (width + 7) / 8): ByteArray {
        val out = ByteArray(bytesPerRow)
        for (x in 0 until minOf(width, bytesPerRow * 8)) {
            if (this[x, y]) out[x shr 3] = (out[x shr 3].toInt() or (1 shl (x and 7))).toByte()
        }
        return out
    }

    fun isRowBlank(y: Int): Boolean {
        val start = y * width
        for (i in start until start + width) if (pixels[i]) return false
        return true
    }

    /** Recadre ou complète en blanc à la largeur demandée (contenu centré). */
    fun fitWidth(target: Int): MonoImage {
        if (target == width) return this
        val out = MonoImage(target, height)
        val offset = (target - width) / 2
        for (y in 0 until height) for (x in 0 until width) {
            val tx = x + offset
            if (tx in 0 until target && this[x, y]) out[tx, y] = true
        }
        return out
    }
}
