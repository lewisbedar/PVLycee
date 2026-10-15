package fr.pvlycee.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import fr.pvlycee.data.AppSettings
import fr.pvlycee.data.PrinterTransport
import fr.pvlycee.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Résumé affiché dans l'indicateur d'imprimante. */
data class PrinterStatus(val printer: TicketPrinter, val state: PrinterState)

/**
 * Choisit et pilote l'imprimante active : Sunmi si présente, sinon l'imprimante Bluetooth
 * mémorisée, sinon l'aperçu. Ne lève jamais d'exception non gérée vers l'interface.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrinterManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val preview = PreviewOnlyPrinter()
    private var sunmi: SunmiTicketPrinter? = null
    private val _sunmiAvailable = MutableStateFlow(false)
    val sunmiAvailable: StateFlow<Boolean> = _sunmiAvailable

    private val _active = MutableStateFlow<TicketPrinter>(preview)
    val active: StateFlow<TicketPrinter> = _active

    val status: StateFlow<PrinterStatus> = _active
        .flatMapLatest { p -> p.state.map { PrinterStatus(p, it) } }
        .stateIn(scope, SharingStarted.Eagerly, PrinterStatus(preview, preview.state.value))

    private var connectJob: Job? = null

    val adapter: BluetoothAdapter? by lazy {
        try { (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter } catch (_: Exception) { null }
    }

    /** Au lancement : détecte le Sunmi puis reconnecte l'imprimante mémorisée. */
    fun start() {
        scope.launch {
            sunmi = try { SunmiTicketPrinter.probe(context) } catch (_: Throwable) { null }
            _sunmiAvailable.value = sunmi != null
            applySettings(settings.current(), connect = true)
        }
    }

    private suspend fun applySettings(s: AppSettings, connect: Boolean) {
        val old = _active.value
        val next: TicketPrinter = when (s.printerTransport) {
            PrinterTransport.PREVIEW -> preview
            PrinterTransport.SUNMI -> sunmi ?: preview
            PrinterTransport.NONE -> sunmi ?: preview
            PrinterTransport.CLASSIC, PrinterTransport.BLE -> bluetoothDriver(s) ?: preview
        }
        if (old !== next) {
            connectJob?.cancel()
            if (old !== sunmi && old !== preview) scope.launch { try { old.disconnect() } catch (_: Exception) {} }
        }
        _active.value = next
        if (connect && next.isPhysical) reconnect()
    }

    private fun bluetoothDriver(s: AppSettings): TicketPrinter? {
        val a = adapter ?: return null
        if (s.printerAddress.isBlank()) return null
        val device = try { a.getRemoteDevice(s.printerAddress) } catch (_: Exception) { return null }
        val protocol = when {
            s.forcedProtocol != PrinterProtocol.AUTO -> s.forcedProtocol
            s.detectedProtocol != PrinterProtocol.AUTO -> s.detectedProtocol
            s.printerTransport == PrinterTransport.CLASSIC -> PrinterProtocol.ESCPOS_CLASSIC
            else -> ProtocolDetector.detect(PrinterProtocol.AUTO, BtTransport.LE, s.printerName, null)
        }
        return driverFor(a, device, protocol)
    }

    private fun driverFor(a: BluetoothAdapter, device: BluetoothDevice, protocol: PrinterProtocol): TicketPrinter =
        when (protocol) {
            PrinterProtocol.CAT -> CatPrinter(context, device)
            PrinterProtocol.ESCPOS_BLE -> EscPosBlePrinter(context, device)
            PrinterProtocol.ESCPOS_CLASSIC, PrinterProtocol.AUTO -> EscPosClassicPrinter(a, device)
        }

    /** Relance la connexion en arrière-plan ; l'erreur éventuelle reste visible dans l'état. */
    fun reconnect() {
        val p = _active.value
        connectJob?.cancel()
        connectJob = scope.launch {
            if (p.kind != PrinterKind.SUNMI && p.isPhysical && !hasConnectPermission()) {
                return@launch
            }
            try { p.connect() } catch (_: Exception) { }
        }
    }

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Largeur du ticket en points : imposée par le matériel ou choisie dans les réglages. */
    fun widthDots(s: AppSettings): Int = _active.value.fixedWidthDots ?: if (s.paperWidthMm >= 80) 576 else 384

    suspend fun print(bitmap: Bitmap, intensity: Int) {
        if (_active.value.isPhysical && _active.value.kind != PrinterKind.SUNMI && !hasConnectPermission()) {
            throw PrinterException("Autorisation Bluetooth manquante. Accordez-la dans Réglages, Imprimante.")
        }
        _active.value.print(bitmap, intensity)
    }

    suspend fun refreshStatus() {
        try { _active.value.refreshStatus() } catch (_: Exception) {}
    }

    suspend fun useSunmi() {
        settings.update { it.copy(printerTransport = PrinterTransport.SUNMI, printerAddress = "", printerName = "", detectedProtocol = PrinterProtocol.AUTO) }
        applySettings(settings.current(), connect = true)
    }

    suspend fun usePreview() {
        settings.update { it.copy(printerTransport = PrinterTransport.PREVIEW, printerAddress = "", printerName = "", detectedProtocol = PrinterProtocol.AUTO) }
        applySettings(settings.current(), connect = false)
    }

    /** Change le protocole forcé de l'imprimante mémorisée. */
    suspend fun forceProtocol(protocol: PrinterProtocol) {
        settings.update { it.copy(forcedProtocol = protocol) }
        val s = settings.current()
        if (s.printerTransport == PrinterTransport.CLASSIC || s.printerTransport == PrinterTransport.BLE) {
            val transport = when (protocol) {
                PrinterProtocol.ESCPOS_CLASSIC -> PrinterTransport.CLASSIC
                PrinterProtocol.AUTO -> s.printerTransport
                else -> PrinterTransport.BLE
            }
            settings.update { it.copy(printerTransport = transport) }
            applySettings(settings.current(), connect = true)
        }
    }

    /**
     * Mémorise une imprimante Bluetooth, détecte son protocole (sauf s'il est forcé) et s'y connecte.
     * Lève [PrinterException] si la connexion échoue ; le choix reste mémorisé.
     */
    @SuppressLint("MissingPermission")
    suspend fun selectBluetooth(device: BluetoothDevice, fromBleScan: Boolean): PrinterProtocol {
        val a = adapter ?: throw PrinterException("Bluetooth indisponible sur cet appareil.")
        val forced = settings.current().forcedProtocol
        val name = safeName(device)
        val transport = when (try { device.type } catch (_: SecurityException) { BluetoothDevice.DEVICE_TYPE_UNKNOWN }) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> BtTransport.CLASSIC
            BluetoothDevice.DEVICE_TYPE_LE -> BtTransport.LE
            BluetoothDevice.DEVICE_TYPE_DUAL -> if (fromBleScan) BtTransport.LE else BtTransport.DUAL
            else -> if (fromBleScan) BtTransport.LE else BtTransport.UNKNOWN
        }
        var services: List<String>? = null
        if (forced == PrinterProtocol.AUTO && transport == BtTransport.LE) {
            // Interrogation GATT : on regarde quels services l'imprimante publie.
            val probe = BleConnection(context, device)
            try {
                probe.connect()
                services = probe.serviceUuids()
            } catch (_: PrinterException) {
            } finally {
                probe.close()
            }
        }
        val protocol = ProtocolDetector.detect(forced, transport, name, services)
        val newTransport = if (protocol == PrinterProtocol.ESCPOS_CLASSIC) PrinterTransport.CLASSIC else PrinterTransport.BLE
        settings.update {
            it.copy(
                printerTransport = newTransport,
                printerAddress = device.address,
                printerName = name,
                detectedProtocol = protocol,
                paperWidthMm = if (protocol == PrinterProtocol.CAT) 58 else it.paperWidthMm,
            )
        }
        applySettings(settings.current(), connect = false)
        connectJob?.cancel()
        _active.value.connect()
        return protocol
    }
}
