package fr.pvlycee

import android.app.Application
import fr.pvlycee.data.PvDatabase
import fr.pvlycee.data.PvRepository
import fr.pvlycee.data.SettingsRepository
import fr.pvlycee.print.PrinterManager
import fr.pvlycee.ticket.TicketRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Conteneur de dépendances de l'application (pas de framework d'injection). */
class PvApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings by lazy { SettingsRepository(this) }
    val repository by lazy { PvRepository(PvDatabase.create(this), settings) }
    val printers by lazy { PrinterManager(this, settings, appScope) }
    val renderer by lazy { TicketRenderer(this) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.ensureSeeded() }
        printers.start()
    }
}
