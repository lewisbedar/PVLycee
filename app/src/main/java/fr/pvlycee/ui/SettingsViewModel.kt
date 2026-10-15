package fr.pvlycee.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.pvlycee.PvApp
import fr.pvlycee.data.AppSettings
import fr.pvlycee.domain.Category
import fr.pvlycee.domain.Gage
import fr.pvlycee.domain.Infraction
import fr.pvlycee.print.PrinterProtocol
import fr.pvlycee.print.ProtocolDetector
import fr.pvlycee.print.asPrinterException
import fr.pvlycee.print.safeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BtItem(val device: BluetoothDevice, val name: String, val address: String, val fromScan: Boolean, val rssi: Int? = null) {
    val looksLikeCat: Boolean get() = ProtocolDetector.looksLikeCatPrinter(name)
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val pv = app as PvApp
    private val repo = pv.repository
    val printers = pv.printers

    val settings: StateFlow<AppSettings> = pv.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val catalog: StateFlow<CatalogSnapshot> =
        combine(repo.categories, repo.infractions, repo.gages) { c, i, g -> CatalogSnapshot(c, i, g) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, CatalogSnapshot())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun dismissMessage() { _message.value = null }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    // --- Établissement et impression ---

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { pv.settings.update(transform) }

    fun resetAll() = viewModelScope.launch {
        repo.resetAll()
        // Réglages par défaut : Sunmi si présent, sinon aperçu.
        printers.start()
        _message.value = "Remise à zéro effectuée : élèves, PV, catalogue et réglages."
    }

    // --- Bluetooth ---

    /** Permissions nécessaires selon la version d'Android. */
    fun requiredPermissions(scan: Boolean): Array<String> = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (scan) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        // Jusqu'à Android 11, BLUETOOTH et BLUETOOTH_ADMIN sont accordées à l'installation ;
        // seule la localisation est demandée, et uniquement pour la recherche BLE.
        scan -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> emptyArray()
    }

    fun missingPermissions(scan: Boolean): List<String> =
        requiredPermissions(scan).filter { ContextCompat.checkSelfPermission(pv, it) != PackageManager.PERMISSION_GRANTED }

    fun bluetoothAvailable(): Boolean = printers.adapter != null

    fun bluetoothEnabled(): Boolean = try { printers.adapter?.isEnabled == true } catch (_: SecurityException) { false }

    fun locationServicesOff(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) return false
        val lm = pv.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            !lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }
    }

    private val _bonded = MutableStateFlow<List<BtItem>>(emptyList())
    val bonded: StateFlow<List<BtItem>> = _bonded

    private val _scan = MutableStateFlow<List<BtItem>>(emptyList())
    val scanResults: StateFlow<List<BtItem>> = _scan

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private var scanJob: Job? = null

    @SuppressLint("MissingPermission")
    fun refreshBonded() {
        if (missingPermissions(scan = false).isNotEmpty()) { _bonded.value = emptyList(); return }
        _bonded.value = try {
            printers.adapter?.bondedDevices.orEmpty()
                .map { BtItem(it, safeName(it), it.address, fromScan = false) }
                .sortedWith(compareByDescending<BtItem> { looksLikePrinter(it.name) }.thenBy { it.name.lowercase() })
        } catch (_: SecurityException) { emptyList() }
    }

    private fun looksLikePrinter(name: String): Boolean =
        Regex("print|pos|pt-|mtp|rpp|58|80|gb0|mx0|gt0|bt", RegexOption.IGNORE_CASE).containsMatchIn(name)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device ?: return
            val name = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() } ?: safeName(d)
            _scan.update { list ->
                val item = BtItem(d, name, d.address, fromScan = true, rssi = result.rssi)
                (list.filterNot { it.address == d.address } + item)
                    .sortedWith(compareByDescending<BtItem> { it.looksLikeCat || looksLikePrinter(it.name) }.thenByDescending { it.rssi ?: -200 })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _message.value = "Recherche BLE impossible (code $errorCode). Désactivez puis réactivez le Bluetooth."
            _scanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = try { printers.adapter?.bluetoothLeScanner } catch (_: SecurityException) { null }
        if (scanner == null) { _message.value = "Activez le Bluetooth pour rechercher une imprimante."; return }
        if (missingPermissions(scan = true).isNotEmpty()) return
        _scan.value = emptyList()
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
                _scanning.value = true
                delay(12_000)
            } catch (e: Exception) {
                _message.value = "Recherche BLE impossible : ${e.message}"
            } finally {
                try { scanner.stopScan(scanCallback) } catch (_: Exception) {}
                _scanning.value = false
            }
        }
    }

    fun stopScan() { scanJob?.cancel() }

    fun select(item: BtItem) {
        stopScan()
        viewModelScope.launch {
            _busy.value = true
            _message.value = "Connexion à ${item.name}…"
            try {
                val p = printers.selectBluetooth(item.device, item.fromScan)
                _message.value = "${item.name} connectée (${p.label}). Imprimez un ticket de test pour vérifier."
            } catch (e: Exception) {
                _message.value = (e.asPrinterException().message ?: "Connexion impossible.") +
                    " L'imprimante reste mémorisée : réessayez ou forcez un autre protocole."
            } finally {
                _busy.value = false
            }
        }
    }

    fun forceProtocol(p: PrinterProtocol) = viewModelScope.launch { printers.forceProtocol(p) }

    fun useSunmi() = viewModelScope.launch { printers.useSunmi() }

    fun usePreview() = viewModelScope.launch { printers.usePreview() }

    fun reconnect() = printers.reconnect()

    fun printTest() = viewModelScope.launch {
        _busy.value = true
        try {
            val s = settings.value
            val width = printers.widthDots(s)
            val p = printers.active.value
            val label = p.displayName + if (p.supportsIntensity) ", intensité ${intensityLabel(s.intensity)}" else ""
            val bmp = withContext(Dispatchers.Default) { pv.renderer.renderTestPattern(width, label) }
            printers.print(bmp, s.intensity)
            _message.value = if (p.isPhysical) "Ticket de test envoyé." else "Mode aperçu : aucun ticket imprimé."
        } catch (e: Exception) {
            _message.value = e.asPrinterException().message
        } finally {
            _busy.value = false
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }

    // --- Catalogue ---

    fun saveCategory(c: Category) = viewModelScope.launch { repo.saveCategory(c) }
    fun deleteCategory(c: Category) = viewModelScope.launch { repo.deleteCategory(c.id) }
    fun saveInfraction(i: Infraction) = viewModelScope.launch { repo.saveInfraction(i) }
    fun deleteInfraction(i: Infraction) = viewModelScope.launch { repo.deleteInfraction(i.id) }
    fun saveGage(g: Gage) = viewModelScope.launch { repo.saveGage(g) }
    fun deleteGage(g: Gage) = viewModelScope.launch { repo.deleteGage(g.id) }
    fun restoreCatalog() = viewModelScope.launch {
        repo.restoreDefaultCatalog()
        _message.value = "Catalogue et gages par défaut restaurés."
    }

    /** Échange le rang de deux gages (le premier, « Simple avertissement », reste en tête). */
    fun moveGage(g: Gage, up: Boolean) = viewModelScope.launch {
        val list = catalog.value.gages.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val i = list.indexOfFirst { it.id == g.id }
        val j = if (up) i - 1 else i + 1
        if (i <= 0 || j <= 0 || j > list.lastIndex) return@launch
        val renumbered = list.toMutableList().apply { add(j, removeAt(i)) }
        renumbered.forEachIndexed { idx, item -> if (item.sortOrder != idx) repo.saveGage(item.copy(sortOrder = idx)) }
    }

    companion object {
        fun intensityLabel(i: Int) = when (i) { 0 -> "claire"; 2 -> "foncée"; else -> "normale" }
    }
}
