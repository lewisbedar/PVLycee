package fr.pvlycee.print

import android.content.Context
import android.graphics.Bitmap
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Imprimante thermique intégrée des terminaux Sunmi (V2…), via le service SunmiPrinterService. */
class SunmiTicketPrinter private constructor(private val context: Context) : BasePrinter() {
    override val kind = PrinterKind.SUNMI
    override val displayName = "Imprimante intégrée Sunmi"
    override val fixedWidthDots: Int = 384
    override val supportsIntensity = false

    @Volatile private var service: SunmiPrinterService? = null
    private var bound = CompletableDeferred<SunmiPrinterService?>()

    private val callback = object : InnerPrinterCallback() {
        override fun onConnected(s: SunmiPrinterService) {
            service = s
            bound.complete(s)
        }

        override fun onDisconnected() {
            service = null
            _state.value = PrinterState.Disconnected
        }
    }

    /** Lie le service ; renvoie false si l'appareil n'est pas un Sunmi. */
    private suspend fun bind(): Boolean {
        bound = CompletableDeferred()
        val started = try {
            InnerPrinterManager.getInstance().bindService(context.applicationContext, callback)
        } catch (_: Exception) {
            false
        }
        if (!started) return false
        val s = withTimeoutOrNull(4_000) { bound.await() } ?: return false
        return try { InnerPrinterManager.getInstance().hasPrinter(s) } catch (_: Exception) { true }
    }

    override fun isConnected() = service != null

    override suspend fun doConnect() {
        if (service == null && !bind()) throw PrinterException("Service d'impression Sunmi indisponible.")
        checkStatus()
    }

    /** Traduit l'état matériel : papier, surchauffe, capot. */
    private fun checkStatus() {
        val s = service ?: throw PrinterException("Service d'impression Sunmi indisponible.")
        val code = try { s.updatePrinterState() } catch (e: Exception) { throw PrinterException("Imprimante Sunmi injoignable.", e) }
        statusMessage(code)?.let { throw PrinterException(it) }
    }

    override suspend fun refreshStatus() {
        if (service == null) return
        _state.value = try {
            checkStatus(); PrinterState.Ready
        } catch (e: PrinterException) {
            PrinterState.Error(e.message ?: "Erreur")
        }
    }

    override suspend fun doPrint(image: MonoImage, bitmap: Bitmap, intensity: Int) {
        val s = service ?: throw PrinterException("Service d'impression Sunmi indisponible.")
        checkStatus()
        val result = CompletableDeferred<Pair<Boolean, String>>()
        val cb = object : InnerResultCallback() {
            override fun onRunResult(isSuccess: Boolean) {}
            override fun onReturnString(result: String?) {}
            override fun onRaiseException(code: Int, msg: String?) {
                result.complete(false to (msg ?: "code $code"))
            }
            override fun onPrintResult(code: Int, msg: String?) {
                result.complete((code == 0) to (msg ?: ""))
            }
        }
        withContext(Dispatchers.IO) {
            s.enterPrinterBuffer(true)
            s.printerInit(null)
            s.printBitmap(bitmap, null)
            s.lineWrap(3, null)
            s.exitPrinterBufferWithCallback(true, cb)
        }
        val r = withTimeoutOrNull(30_000) { result.await() }
            ?: throw PrinterException("L'imprimante Sunmi n'a pas confirmé l'impression.")
        if (!r.first) {
            val code = try { s.updatePrinterState() } catch (_: Exception) { 0 }
            throw PrinterException(statusMessage(code) ?: "Impression Sunmi échouée. ${r.second}".trim())
        }
    }

    override suspend fun doFeed(lines: Int) {
        val s = service ?: return
        withContext(Dispatchers.IO) { s.lineWrap((lines / 24).coerceAtLeast(1), null) }
    }

    override fun doDisconnect() {
        // Le service reste lié : il est local et toujours disponible sur le terminal.
    }

    companion object {
        /** Renvoie le pilote si le service Sunmi est présent sur l'appareil, sinon null. */
        suspend fun probe(context: Context): SunmiTicketPrinter? {
            val p = SunmiTicketPrinter(context.applicationContext)
            return if (p.bind()) p.also { it._state.value = PrinterState.Ready } else null
        }

        fun statusMessage(code: Int): String? = when (code) {
            1 -> null
            2 -> "L'imprimante Sunmi démarre, réessayez dans un instant."
            3 -> "Communication avec l'imprimante Sunmi anormale."
            4 -> "Plus de papier. Remettez un rouleau puis réessayez."
            5 -> "L'imprimante surchauffe. Patientez une minute puis réessayez."
            6 -> "Le capot de l'imprimante est ouvert. Refermez-le puis réessayez."
            7 -> "Problème de massicot sur l'imprimante."
            505 -> "Aucune imprimante détectée sur ce terminal Sunmi."
            507 -> "Mise à jour du micrologiciel de l'imprimante échouée."
            else -> null
        }
    }
}
