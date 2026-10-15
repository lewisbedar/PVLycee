package fr.pvlycee.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Petite surcouche coroutines autour de BluetoothGatt : connexion, découverte des services,
 * négociation du MTU, écriture par paquets et notifications.
 */
@SuppressLint("MissingPermission")
class BleConnection(private val context: Context, val device: BluetoothDevice) {

    private var gatt: BluetoothGatt? = null
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var servicesReady = CompletableDeferred<Boolean>()
    private var mtuReady = CompletableDeferred<Int>()
    private val writeDone = Channel<Int>(Channel.CONFLATED)
    private val descriptorDone = Channel<Int>(Channel.CONFLATED)

    /** MTU négocié (23 par défaut en BLE). */
    var mtu: Int = 23
        private set

    var onNotification: ((UUID, ByteArray) -> Unit)? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            val ok = newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS
            _connected.value = ok
            if (!ok) {
                servicesReady.complete(false)
                mtuReady.complete(mtu)
                writeDone.trySend(-1)
                descriptorDone.trySend(-1)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            servicesReady.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = newMtu
            mtuReady.complete(mtu)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeDone.trySend(status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            descriptorDone.trySend(status)
        }

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            onNotification?.invoke(c.uuid, c.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onNotification?.invoke(c.uuid, value)
        }
    }

    /** Connexion + découverte des services + MTU. Lève [PrinterException]. */
    suspend fun connect(timeoutMs: Long = 15_000) {
        close()
        servicesReady = CompletableDeferred()
        mtuReady = CompletableDeferred()
        val g = try {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            throw PrinterException("Autorisation Bluetooth manquante.", e)
        } ?: throw PrinterException("Connexion Bluetooth impossible.")
        gatt = g
        val up = withTimeoutOrNull(timeoutMs) { _connected.first { it } }
        if (up == null) {
            close()
            throw PrinterException("L'imprimante ne répond pas. Vérifiez qu'elle est allumée et à proximité.")
        }
        delay(300) // certains modules ont besoin d'un court délai avant la découverte
        if (!g.discoverServices()) { close(); throw PrinterException("Découverte des services impossible.") }
        val found = withTimeoutOrNull(10_000) { servicesReady.await() } ?: false
        if (!found) { close(); throw PrinterException("L'imprimante n'a pas publié ses services.") }
        if (g.requestMtu(185)) {
            withTimeoutOrNull(3_000) { mtuReady.await() }
        }
    }

    fun serviceUuids(): List<String> = gatt?.services?.map { it.uuid.toString() }.orEmpty()

    fun findCharacteristic(service: String, characteristic: String): BluetoothGattCharacteristic? =
        gatt?.services?.firstOrNull { ProtocolDetector.matches(it.uuid.toString(), service) }
            ?.characteristics?.firstOrNull { ProtocolDetector.matches(it.uuid.toString(), characteristic) }

    /** Première caractéristique inscriptible, en privilégiant les services d'imprimantes connus. */
    fun findWritable(): BluetoothGattCharacteristic? {
        val services = gatt?.services.orEmpty()
            .filterNot { ProtocolDetector.isStandardService(it.uuid.toString()) }
            .sortedBy { ProtocolDetector.rankService(it.uuid.toString()) }
        for (s in services) {
            s.characteristics.firstOrNull { it.isWritable() }?.let { return it }
        }
        return null
    }

    @Suppress("DEPRECATION")
    suspend fun enableNotifications(c: BluetoothGattCharacteristic) {
        val g = gatt ?: return
        if (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0) return
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(CCCD) ?: return
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(d, value)
        } else {
            d.value = value
            g.writeDescriptor(d)
        }
        withTimeoutOrNull(2_000) { descriptorDone.receive() }
    }

    /**
     * Écrit [data] par paquets adaptés au MTU. [pauseMs] ralentit l'envoi pour les imprimantes
     * qui n'ont qu'un petit tampon ; [isPaused] permet le contrôle de flux.
     */
    suspend fun write(
        c: BluetoothGattCharacteristic,
        data: ByteArray,
        pauseMs: Long = 0,
        isPaused: () -> Boolean = { false },
    ) {
        val chunkSize = (mtu - 3).coerceIn(20, 180)
        val noResponse = c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val type = if (noResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        var offset = 0
        while (offset < data.size) {
            ensureConnected()
            var waited = 0
            while (isPaused() && waited < 10_000) { delay(20); waited += 20 }
            val end = minOf(offset + chunkSize, data.size)
            val chunk = data.copyOfRange(offset, end)
            writeChunk(c, chunk, type)
            offset = end
            if (pauseMs > 0) delay(pauseMs)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun writeChunk(c: BluetoothGattCharacteristic, chunk: ByteArray, type: Int) {
        val g = gatt ?: throw disconnected()
        while (writeDone.tryReceive().isSuccess) { /* purge */ }
        var attempts = 0
        while (true) {
            val accepted = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, chunk, type) == BluetoothStatusCodes.SUCCESS
            } else {
                c.writeType = type
                c.value = chunk
                g.writeCharacteristic(c)
            }
            if (accepted) break
            ensureConnected()
            if (++attempts > 50) throw PrinterException("L'imprimante n'accepte plus de données.")
            delay(20)
        }
        // Accusé de la pile Bluetooth (aussi émis en écriture sans réponse sur la plupart des appareils).
        val status = withTimeoutOrNull(if (type == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) 3_000 else 300) { writeDone.receive() }
        if (status == -1) throw disconnected()
    }

    private fun ensureConnected() {
        if (!_connected.value) throw disconnected()
    }

    private fun disconnected() =
        PrinterException("L'imprimante s'est déconnectée pendant l'impression.")

    fun close() {
        val g = gatt ?: return
        gatt = null
        try { g.disconnect() } catch (_: Exception) {}
        try { g.close() } catch (_: Exception) {}
        _connected.value = false
    }

    companion object {
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun BluetoothGattCharacteristic.isWritable(): Boolean =
            properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
    }
}
