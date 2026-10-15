package fr.pvlycee.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.pvlycee.print.PrinterProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** Type de liaison de l'imprimante mémorisée. */
enum class PrinterTransport { NONE, SUNMI, CLASSIC, BLE, PREVIEW }

@Serializable
data class AppSettings(
    val schoolName: String = "Lycée",
    val defaultRoom: String = "",
    val agentName: String = "",
    val paperWidthMm: Int = 58,
    /** 0 = clair, 1 = normal, 2 = foncé. */
    val intensity: Int = 1,
    val printerTransport: PrinterTransport = PrinterTransport.NONE,
    val printerAddress: String = "",
    val printerName: String = "",
    /** Protocole forcé par l'utilisateur, ou AUTO. */
    val forcedProtocol: PrinterProtocol = PrinterProtocol.AUTO,
    /** Dernier protocole détecté pour l'imprimante mémorisée (évite de refaire la détection). */
    val detectedProtocol: PrinterProtocol = PrinterProtocol.AUTO,
    val lastPvYear: Int = 0,
    val lastPvSeq: Int = 0,
    val lastClass: String = "",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reglages")

class SettingsRepository(private val context: Context) {
    private object K {
        val school = stringPreferencesKey("school")
        val room = stringPreferencesKey("room")
        val agent = stringPreferencesKey("agent")
        val paper = intPreferencesKey("paper")
        val intensity = intPreferencesKey("intensity")
        val transport = stringPreferencesKey("transport")
        val address = stringPreferencesKey("address")
        val printerName = stringPreferencesKey("printer_name")
        val forced = stringPreferencesKey("forced_protocol")
        val detected = stringPreferencesKey("detected_protocol")
        val lastYear = intPreferencesKey("last_pv_year")
        val lastSeq = intPreferencesKey("last_pv_seq")
        val lastClass = stringPreferencesKey("last_class")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { fromPrefs(it) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p -> write(p, transform(fromPrefs(p))) }
    }

    suspend fun replace(s: AppSettings) {
        context.dataStore.edit { p -> write(p, s) }
    }

    private fun fromPrefs(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            schoolName = p[K.school] ?: d.schoolName,
            defaultRoom = p[K.room] ?: d.defaultRoom,
            agentName = p[K.agent] ?: d.agentName,
            paperWidthMm = p[K.paper] ?: d.paperWidthMm,
            intensity = p[K.intensity] ?: d.intensity,
            printerTransport = enumOr(p[K.transport], d.printerTransport),
            printerAddress = p[K.address] ?: d.printerAddress,
            printerName = p[K.printerName] ?: d.printerName,
            forcedProtocol = enumOr(p[K.forced], d.forcedProtocol),
            detectedProtocol = enumOr(p[K.detected], d.detectedProtocol),
            lastPvYear = p[K.lastYear] ?: d.lastPvYear,
            lastPvSeq = p[K.lastSeq] ?: d.lastPvSeq,
            lastClass = p[K.lastClass] ?: d.lastClass,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[K.school] = s.schoolName
        p[K.room] = s.defaultRoom
        p[K.agent] = s.agentName
        p[K.paper] = s.paperWidthMm
        p[K.intensity] = s.intensity
        p[K.transport] = s.printerTransport.name
        p[K.address] = s.printerAddress
        p[K.printerName] = s.printerName
        p[K.forced] = s.forcedProtocol.name
        p[K.detected] = s.detectedProtocol.name
        p[K.lastYear] = s.lastPvYear
        p[K.lastSeq] = s.lastPvSeq
        p[K.lastClass] = s.lastClass
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
