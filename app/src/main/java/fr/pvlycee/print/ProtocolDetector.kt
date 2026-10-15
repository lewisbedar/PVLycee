package fr.pvlycee.print

import java.util.Locale
import java.util.UUID

enum class BtTransport { CLASSIC, LE, DUAL, UNKNOWN }

/** Détection du protocole d'une imprimante Bluetooth (logique pure, testable). */
object ProtocolDetector {
    const val CAT_SERVICE = "ae30"
    const val CAT_WRITE = "ae01"
    const val CAT_NOTIFY = "ae02"

    /** Services BLE courants des imprimantes ESC/POS premier prix, par ordre de préférence. */
    val ESCPOS_BLE_SERVICES = listOf(
        "18f0", "ff00", "ffe0", "fff0", "fee7",
        "e7810a71-73ae-499d-8c15-faa9aef0c3f2",
        "49535343-fe7d-4ae5-8fa9-9fafd205e455",
    )

    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val catNames = Regex(
        "^(GB0\\d|GT0\\d|GT1\\d|MX0\\d|MX1\\d|MXW\\w*|YT0\\d|BQ\\d\\w*|_ZZ\\w*|CAT\\w*|MINIPRINTER)",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeCatPrinter(name: String?): Boolean = name != null && catNames.containsMatchIn(name.trim())

    /** UUID complet ou court (« 18f0 ») vers la forme courte en minuscules si c'est un UUID Bluetooth de base. */
    fun shortForm(uuid: String): String {
        val u = uuid.lowercase(Locale.ROOT)
        val base = "-0000-1000-8000-00805f9b34fb"
        return if (u.length == 36 && u.startsWith("0000") && u.endsWith(base)) u.substring(4, 8) else u
    }

    fun matches(uuid: String, wanted: String): Boolean = shortForm(uuid) == shortForm(wanted)

    /**
     * Choisit le protocole. [services] est la liste des services GATT découverts
     * (null si l'appareil n'a pas été interrogé).
     */
    fun detect(
        forced: PrinterProtocol,
        transport: BtTransport,
        name: String?,
        services: Collection<String>?,
    ): PrinterProtocol {
        if (forced != PrinterProtocol.AUTO) return forced
        if (services != null) {
            if (services.any { matches(it, CAT_SERVICE) }) return PrinterProtocol.CAT
            if (services.any { s -> ESCPOS_BLE_SERVICES.any { matches(s, it) } }) {
                return if (transport == BtTransport.DUAL) PrinterProtocol.ESCPOS_CLASSIC else PrinterProtocol.ESCPOS_BLE
            }
        }
        if (looksLikeCatPrinter(name)) return PrinterProtocol.CAT
        return when (transport) {
            BtTransport.LE -> PrinterProtocol.ESCPOS_BLE
            else -> PrinterProtocol.ESCPOS_CLASSIC
        }
    }

    /** Ordre de recherche d'une caractéristique d'écriture : services connus d'abord. */
    fun rankService(uuid: String): Int {
        val i = ESCPOS_BLE_SERVICES.indexOfFirst { matches(uuid, it) }
        return if (i >= 0) i else Int.MAX_VALUE
    }

    /** Services standards à ignorer (accès générique, attributs, informations appareil, batterie). */
    fun isStandardService(uuid: String): Boolean = shortForm(uuid) in setOf("1800", "1801", "180a", "180f")
}
