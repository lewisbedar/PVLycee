package fr.pvlycee.print

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.flow.StateFlow

enum class PrinterProtocol(val label: String) {
    AUTO("Détection automatique"),
    CAT("Cat printer (BLE, GB01, MX05…)"),
    ESCPOS_BLE("ESC/POS Bluetooth basse consommation"),
    ESCPOS_CLASSIC("ESC/POS Bluetooth classique"),
}

enum class PrinterKind(val label: String) {
    SUNMI("Imprimante intégrée Sunmi"),
    ESCPOS_CLASSIC("ESC/POS Bluetooth"),
    ESCPOS_BLE("ESC/POS BLE"),
    CAT("Cat printer"),
    PREVIEW("Aperçu seulement"),
}

sealed interface PrinterState {
    data object Disconnected : PrinterState
    data object Connecting : PrinterState
    data object Ready : PrinterState
    data object Printing : PrinterState
    data class Error(val message: String) : PrinterState
}

/** Erreur d'impression avec un message lisible par l'utilisateur. */
class PrinterException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Interface commune à tous les pilotes. Le ticket arrive toujours sous forme de Bitmap
 * en noir et blanc pur ; chaque pilote le convertit dans son propre format.
 */
interface TicketPrinter {
    val kind: PrinterKind
    val displayName: String
    val state: StateFlow<PrinterState>

    /** Largeur imposée par le matériel (384 points), ou null si le réglage utilisateur s'applique. */
    val fixedWidthDots: Int?
    val supportsIntensity: Boolean
    /** Faux pour le mode aperçu : rien ne sort physiquement. */
    val isPhysical: Boolean get() = true

    /** Établit la connexion. Lève [PrinterException] en cas d'échec. */
    suspend fun connect()
    suspend fun disconnect()

    /** Imprime le Bitmap (connexion automatique si besoin). Lève [PrinterException] en cas d'échec. */
    suspend fun print(bitmap: Bitmap, intensity: Int)

    /** Avance le papier de [lines] lignes de points. */
    suspend fun feed(lines: Int)

    /** Interroge l'imprimante (papier, capot, température) quand c'est possible. */
    suspend fun refreshStatus() {}
}

object Bitmaps {
    /** Convertit un Bitmap en image 1 bit (seuil sur la luminance, transparence = blanc). */
    fun toMono(bitmap: Bitmap): MonoImage {
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        val img = MonoImage(w, h)
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                if (Color.alpha(c) < 128) continue
                val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                if (lum < 128) img[x, y] = true
            }
        }
        return img
    }
}
