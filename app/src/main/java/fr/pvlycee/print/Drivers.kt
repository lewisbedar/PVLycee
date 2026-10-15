package fr.pvlycee.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/** Socle commun : état observable, exclusion mutuelle et traduction des erreurs. */
abstract class BasePrinter : TicketPrinter {
    protected val _state = MutableStateFlow<PrinterState>(PrinterState.Disconnected)
    override val state: StateFlow<PrinterState> = _state
    protected val lock = Mutex()

    protected abstract suspend fun doConnect()
    protected abstract suspend fun doPrint(image: MonoImage, bitmap: Bitmap, intensity: Int)
    protected abstract suspend fun doFeed(lines: Int)
    protected abstract fun isConnected(): Boolean
    protected abstract fun doDisconnect()

    override suspend fun connect() = lock.withLock { connectLocked() }

    private suspend fun connectLocked() {
        if (isConnected()) { _state.value = PrinterState.Ready; return }
        _state.value = PrinterState.Connecting
        try {
            doConnect()
            _state.value = PrinterState.Ready
        } catch (e: Exception) {
            doDisconnect()
            val err = e.asPrinterException()
            _state.value = PrinterState.Error(err.message ?: "Erreur de connexion")
            throw err
        }
    }

    override suspend fun print(bitmap: Bitmap, intensity: Int) = lock.withLock {
        connectLocked()
        _state.value = PrinterState.Printing
        try {
            val mono = withContext(Dispatchers.Default) { Bitmaps.toMono(bitmap) }
            doPrint(mono, bitmap, intensity)
            _state.value = PrinterState.Ready
        } catch (e: Exception) {
            val err = e.asPrinterException()
            doDisconnect()
            _state.value = PrinterState.Error(err.message ?: "Erreur d'impression")
            throw err
        }
    }

    override suspend fun feed(lines: Int) = lock.withLock {
        connectLocked()
        try { doFeed(lines) } catch (e: Exception) {
            val err = e.asPrinterException()
            doDisconnect()
            _state.value = PrinterState.Error(err.message ?: "Erreur")
            throw err
        }
    }

    override suspend fun disconnect() = lock.withLock {
        doDisconnect()
        _state.value = PrinterState.Disconnected
    }
}

fun Throwable.asPrinterException(): PrinterException = when (this) {
    is PrinterException -> this
    is SecurityException -> PrinterException("Autorisation Bluetooth manquante. Accordez-la dans Réglages, Imprimante.", this)
    is IOException -> PrinterException("Liaison avec l'imprimante interrompue. Vérifiez qu'elle est allumée et chargée.", this)
    else -> PrinterException("Erreur d'impression : ${message ?: javaClass.simpleName}", this)
}

/** Aucun matériel : le ticket n'est visible qu'à l'écran. */
class PreviewOnlyPrinter : BasePrinter() {
    override val kind = PrinterKind.PREVIEW
    override val displayName = "Aperçu seulement"
    override val fixedWidthDots: Int? = null
    override val supportsIntensity = false
    override val isPhysical = false
    init { _state.value = PrinterState.Ready }
    override suspend fun doConnect() {}
    override suspend fun doPrint(image: MonoImage, bitmap: Bitmap, intensity: Int) {}
    override suspend fun doFeed(lines: Int) {}
    override fun isConnected() = true
    override fun doDisconnect() {}
}

/** ESC/POS en Bluetooth classique (profil série SPP). */
@SuppressLint("MissingPermission")
class EscPosClassicPrinter(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
) : BasePrinter() {
    override val kind = PrinterKind.ESCPOS_CLASSIC
    override val displayName: String = safeName(device)
    override val fixedWidthDots: Int? = null
    override val supportsIntensity = false

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    override fun isConnected() = socket?.isConnected == true && out != null

    override suspend fun doConnect() = withContext(Dispatchers.IO) {
        try { adapter.cancelDiscovery() } catch (_: SecurityException) {}
        val attempts = listOf<() -> BluetoothSocket>(
            { device.createRfcommSocketToServiceRecord(ProtocolDetector.SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(ProtocolDetector.SPP_UUID) },
            {
                // Repli courant pour les imprimantes sans enregistrement SDP correct.
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            },
        )
        var last: Exception? = null
        for (make in attempts) {
            val s = try { make() } catch (e: Exception) { last = e; continue }
            // connect() est bloquant : un chien de garde ferme la socket au bout de 12 s.
            val ok = coroutineScope {
                val watchdog = launch { delay(12_000); try { s.close() } catch (_: IOException) {} }
                try { s.connect(); true } catch (e: IOException) { last = e; false } finally { watchdog.cancel() }
            }
            if (ok) {
                socket = s
                out = s.outputStream
                return@withContext
            }
            try { s.close() } catch (_: IOException) {}
        }
        throw PrinterException(
            "Connexion impossible à « $displayName ». Allumez l'imprimante et vérifiez qu'elle est appairée.",
            last,
        )
    }

    override suspend fun doPrint(image: MonoImage, bitmap: Bitmap, intensity: Int) = withContext(Dispatchers.IO) {
        val o = out ?: throw PrinterException("Imprimante non connectée.")
        for (part in EscPosEncoder.encode(image, EscPosEncoder.DEFAULT_BAND_HEIGHT, feed = 0)) {
            o.write(part)
            o.flush()
            // Laisse le temps aux imprimantes à petit tampon de chauffer la bande.
            delay(if (part.size > 64) 60 else 5)
        }
    }

    override suspend fun doFeed(lines: Int) = withContext(Dispatchers.IO) {
        val o = out ?: throw PrinterException("Imprimante non connectée.")
        // ESC J n : avance de n points.
        var left = lines
        while (left > 0) {
            val n = minOf(left, 255)
            o.write(byteArrayOf(0x1B, 0x4A, n.toByte()))
            left -= n
        }
        o.flush()
    }

    override fun doDisconnect() {
        try { out?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        out = null
        socket = null
    }
}

/** ESC/POS en Bluetooth basse consommation. */
class EscPosBlePrinter(
    private val context: Context,
    private val device: BluetoothDevice,
) : BasePrinter() {
    override val kind = PrinterKind.ESCPOS_BLE
    override val displayName: String = safeName(device)
    override val fixedWidthDots: Int? = null
    override val supportsIntensity = false

    private var conn: BleConnection? = null
    private var target: BluetoothGattCharacteristic? = null

    override fun isConnected() = conn?.connected?.value == true && target != null

    override suspend fun doConnect() {
        val c = BleConnection(context, device)
        c.connect()
        val w = c.findWritable()
        if (w == null) {
            c.close()
            throw PrinterException("Aucune voie d'écriture trouvée sur « $displayName ». Essayez de forcer un autre protocole.")
        }
        conn = c
        target = w
    }

    override suspend fun doPrint(image: MonoImage, bitmap: Bitmap, intensity: Int) {
        val c = conn ?: throw PrinterException("Imprimante non connectée.")
        val t = target ?: throw PrinterException("Imprimante non connectée.")
        for (part in EscPosEncoder.encode(image, EscPosEncoder.DEFAULT_BAND_HEIGHT, feed = 0)) {
            c.write(t, part, pauseMs = 8)
            delay(30)
        }
    }

    override suspend fun doFeed(lines: Int) {
        val c = conn ?: return
        val t = target ?: return
        var left = lines
        while (left > 0) {
            val n = minOf(left, 255)
            c.write(t, byteArrayOf(0x1B, 0x4A, n.toByte()))
            left -= n
        }
    }

    override fun doDisconnect() {
        conn?.close()
        conn = null
        target = null
    }
}

/** « Cat printers » BLE : service ae30, écriture ae01, notifications ae02. */
class CatPrinter(
    private val context: Context,
    private val device: BluetoothDevice,
) : BasePrinter() {
    override val kind = PrinterKind.CAT
    override val displayName: String = safeName(device)
    override val fixedWidthDots: Int = CatPrinterEncoder.WIDTH
    override val supportsIntensity = true

    private var conn: BleConnection? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile private var paused = false

    override fun isConnected() = conn?.connected?.value == true && writeChar != null

    override suspend fun doConnect() {
        val c = BleConnection(context, device)
        c.connect()
        val w = c.findCharacteristic(ProtocolDetector.CAT_SERVICE, ProtocolDetector.CAT_WRITE)
        if (w == null) {
            c.close()
            throw PrinterException("« $displayName » n'est pas un cat printer. Choisissez le protocole ESC/POS.")
        }
        c.onNotification = { _, value -> CatPrinterEncoder.parseFlowControl(value)?.let { paused = it } }
        c.findCharacteristic(ProtocolDetector.CAT_SERVICE, ProtocolDetector.CAT_NOTIFY)?.let { c.enableNotifications(it) }
        conn = c
        writeChar = w
    }

    override suspend fun doPrint(image: MonoImage, bitmap: Bitmap, intensity: Int) {
        val c = conn ?: throw PrinterException("Imprimante non connectée.")
        val w = writeChar ?: throw PrinterException("Imprimante non connectée.")
        paused = false
        val data = CatPrinterEncoder.concat(CatPrinterEncoder.encode(image, intensity, feedLines = 0))
        // Le tampon de ces imprimantes est minuscule : envoi lent avec contrôle de flux.
        c.write(w, data, pauseMs = 12) { paused }
    }

    override suspend fun doFeed(lines: Int) {
        val c = conn ?: return
        val w = writeChar ?: return
        c.write(w, CatPrinterEncoder.feed(lines))
    }

    override fun doDisconnect() {
        conn?.close()
        conn = null
        writeChar = null
    }
}

@SuppressLint("MissingPermission")
fun safeName(device: BluetoothDevice): String =
    try { device.name?.takeIf { it.isNotBlank() } } catch (_: SecurityException) { null } ?: device.address
