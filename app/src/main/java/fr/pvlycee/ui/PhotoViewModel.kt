package fr.pvlycee.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.pvlycee.PvApp
import fr.pvlycee.data.AppSettings
import fr.pvlycee.photo.PhotoProcessor
import fr.pvlycee.photo.PhotoSettings
import fr.pvlycee.print.asPrinterException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Photo à imprimer : rien n'est enregistré sur l'appareil, tout reste en mémoire. */
class PhotoViewModel(app: Application) : AndroidViewModel(app) {
    private val pv = app as PvApp
    val printers = pv.printers
    val settings: StateFlow<AppSettings> = pv.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _source = MutableStateFlow<Bitmap?>(null)
    val source: StateFlow<Bitmap?> = _source

    private val _options = MutableStateFlow(PhotoSettings())
    val options: StateFlow<PhotoSettings> = _options

    private val _caption = MutableStateFlow("")
    val caption: StateFlow<String> = _caption

    private val _evidence = MutableStateFlow(true)
    val evidence: StateFlow<Boolean> = _evidence

    /** Ticket complet, tel qu'il sera imprimé. */
    private val _ticket = MutableStateFlow<Bitmap?>(null)
    val ticket: StateFlow<Bitmap?> = _ticket

    private val _phase = MutableStateFlow<PrintPhase>(PrintPhase.Idle)
    val phase: StateFlow<PrintPhase> = _phase

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var renderJob: Job? = null

    fun setSource(bitmap: Bitmap) {
        _source.value = PhotoProcessor.downscale(bitmap)
        _options.update { it.copy(rotation = 0) }
        _message.value = null
        _phase.value = PrintPhase.Idle
        render()
    }

    fun loadFrom(uri: Uri) = viewModelScope.launch {
        val bmp = withContext(Dispatchers.IO) {
            try { PhotoProcessor.decode(pv.contentResolver, uri) } catch (_: Exception) { null }
        }
        if (bmp == null) _message.value = "Image illisible. Choisissez une photo au format JPEG ou PNG."
        else setSource(bmp)
    }

    fun discard() {
        _source.value = null
        _ticket.value = null
        _phase.value = PrintPhase.Idle
        _message.value = null
    }

    fun setBrightness(v: Int) { _options.update { it.copy(brightness = v) }; render() }
    fun setContrast(v: Float) { _options.update { it.copy(contrast = v) }; render() }
    fun setDither(v: Boolean) { _options.update { it.copy(dither = v) }; render() }
    fun rotate() { _options.update { it.copy(rotation = (it.rotation + 90) % 360) }; render() }
    fun setCaption(v: String) { _caption.value = v; render() }
    fun setEvidence(v: Boolean) { _evidence.value = v; render() }

    fun reportError(message: String) { _message.value = message }

    private fun render() {
        val src = _source.value ?: return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            delay(80)
            val width = printers.widthDots(settings.value)
            val s = settings.value
            _ticket.value = withContext(Dispatchers.Default) {
                val photo = PhotoProcessor.toPrintable(src, width, _options.value)
                pv.renderer.renderPhoto(
                    photo, width,
                    caption = fr.pvlycee.domain.PvRules.clean(_caption.value),
                    evidence = _evidence.value,
                    date = SimpleDateFormat("dd/MM/yyyy 'à' HH 'h' mm", Locale.FRANCE).format(Date()),
                    school = s.schoolName,
                )
            }
        }
    }

    fun print() {
        if (_phase.value is PrintPhase.Printing) return
        viewModelScope.launch {
            renderJob?.join()
            val bmp = _ticket.value ?: return@launch
            _phase.value = PrintPhase.Printing
            _message.value = null
            try {
                printers.print(bmp, settings.value.intensity)
                _phase.value = PrintPhase.Idle
                _message.value = if (printers.active.value.isPhysical) "Photo imprimée." else "Mode aperçu : aucune imprimante, rien n'a été imprimé."
            } catch (e: Exception) {
                _phase.value = PrintPhase.Failed(e.asPrinterException().message ?: "Impression impossible.")
            }
        }
    }
}
