package fr.pvlycee.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.pvlycee.PvApp
import fr.pvlycee.data.AppSettings
import fr.pvlycee.domain.Category
import fr.pvlycee.domain.Gage
import fr.pvlycee.domain.Infraction
import fr.pvlycee.domain.Pv
import fr.pvlycee.domain.PvDraft
import fr.pvlycee.domain.PvFactory
import fr.pvlycee.domain.PvRequest
import fr.pvlycee.domain.PvRules
import fr.pvlycee.domain.Stats
import fr.pvlycee.domain.Student
import fr.pvlycee.print.PrinterException
import fr.pvlycee.print.asPrinterException
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

/** Sélection en cours sur l'écran Verbaliser. */
data class Selection(
    val className: String = "",
    /** null = personne choisie ; [UNKNOWN] = contrevenant non identifié. */
    val studentId: Long? = null,
    val categoryId: Long? = null,
    val infractionId: Long? = null,
    val freeText: String = "",
    val gage: String = "",
    /** Vrai si l'utilisateur a modifié le gage (on ne le remplace plus automatiquement). */
    val gageEdited: Boolean = false,
) {
    companion object { const val UNKNOWN = -1L }
}

sealed interface PrintPhase {
    data object Idle : PrintPhase
    data object Printing : PrintPhase
    data class Failed(val message: String) : PrintPhase
}

data class CatalogSnapshot(
    val categories: List<Category> = emptyList(),
    val infractions: List<Infraction> = emptyList(),
    val gages: List<Gage> = emptyList(),
    val usage: Map<Long, Int> = emptyMap(),
)

class VerbaliserViewModel(app: Application) : AndroidViewModel(app) {
    private val pv = app as PvApp
    private val repo = pv.repository
    val printers = pv.printers

    val settings: StateFlow<AppSettings> = pv.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val students: StateFlow<List<Student>> = repo.students.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pvs: StateFlow<List<Pv>> = repo.pvs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val catalog: StateFlow<CatalogSnapshot> =
        combine(repo.categories, repo.infractions, repo.gages, repo.usage) { c, i, g, u -> CatalogSnapshot(c, i, g, u) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, CatalogSnapshot())

    private val _sel = MutableStateFlow(Selection())
    val selection: StateFlow<Selection> = _sel

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview

    private val _phase = MutableStateFlow<PrintPhase>(PrintPhase.Idle)
    val phase: StateFlow<PrintPhase> = _phase

    /** Message de confirmation après enregistrement. */
    private val _done = MutableStateFlow<String?>(null)
    val done: StateFlow<String?> = _done

    private var previewJob: Job? = null
    private var pending: Pair<PvDraft, Bitmap>? = null

    init {
        viewModelScope.launch {
            // Restaure la dernière classe utilisée.
            val last = pv.settings.current().lastClass
            if (last.isNotBlank()) _sel.update { if (it.className.isBlank()) it.copy(className = last) else it }
        }
        viewModelScope.launch {
            combine(_sel, settings, students, catalog, printers.active) { _, _, _, _, _ -> Unit }.collect { schedulePreview() }
        }
    }

    // --- Données dérivées ---

    fun classes(list: List<Student>): List<String> =
        list.map { it.className }.distinct().sortedWith(naturalOrder)

    /** Classe affichée : celle choisie, sinon la première de la liste. */
    fun effectiveClass(): String {
        val all = classes(students.value)
        return _sel.value.className.takeIf { it in all } ?: all.firstOrNull().orEmpty()
    }

    fun previousCount(studentId: Long?): Int = if (studentId == null || studentId < 0) 0 else pvs.value.count { it.studentId == studentId }

    fun frequent(): List<Infraction> {
        val c = catalog.value
        val enabledCats = c.categories.filter { it.enabled }.map { it.id }.toSet()
        return Stats.frequentInfractions(c.usage, c.infractions.filter { it.categoryId in enabledCats })
    }

    fun gageChoices(categoryId: Long?): List<Gage> {
        val gs = catalog.value.gages.filter { it.enabled }
        if (categoryId == null) return gs.filter { it.allCategories }
        return gs.filter { it.appliesTo(categoryId) }.sortedWith(compareBy({ it.custom }, { it.sortOrder }))
    }

    // --- Actions ---

    fun chooseClass(name: String) {
        _sel.update { it.copy(className = name, studentId = null) }
        viewModelScope.launch { pv.settings.update { it.copy(lastClass = name) } }
        clearFeedback()
    }

    fun chooseStudent(id: Long) {
        _sel.update { it.copy(studentId = if (it.studentId == id) null else id) }
        refreshDefaultGage()
        clearFeedback()
    }

    fun chooseCategory(id: Long) { _sel.update { it.copy(categoryId = id) } }

    fun chooseInfraction(inf: Infraction) {
        _sel.update {
            if (it.infractionId == inf.id) it.copy(infractionId = null)
            else it.copy(infractionId = inf.id, categoryId = inf.categoryId)
        }
        refreshDefaultGage()
        clearFeedback()
    }

    fun setFreeText(text: String) { _sel.update { it.copy(freeText = text) } }

    fun setGage(text: String) { _sel.update { it.copy(gage = text, gageEdited = true) } }

    fun chooseGage(g: Gage) {
        _sel.update { it.copy(gage = if (g.custom) "" else g.label, gageEdited = true) }
    }

    fun resetGage() {
        _sel.update { it.copy(gageEdited = false) }
        refreshDefaultGage()
    }

    private fun refreshDefaultGage() {
        val s = _sel.value
        if (s.gageEdited) return
        val inf = catalog.value.infractions.firstOrNull { it.id == s.infractionId } ?: return
        val recid = previousCount(s.studentId)
        val g = PvRules.defaultGage(catalog.value.gages, inf.categoryId, recid)
        _sel.update { it.copy(gage = g?.label ?: "Simple avertissement") }
    }

    fun clearFeedback() {
        _done.value = null
        if (_phase.value is PrintPhase.Failed) _phase.value = PrintPhase.Idle
    }

    fun canSubmit(): Boolean {
        val s = _sel.value
        val inf = catalog.value.infractions.firstOrNull { it.id == s.infractionId } ?: return false
        if (s.studentId == null) return false
        if (inf.freeText && s.freeText.isBlank()) return false
        return _phase.value !is PrintPhase.Printing
    }

    private fun buildDraft(now: Long): PvDraft? {
        val s = _sel.value
        val c = catalog.value
        val inf = c.infractions.firstOrNull { it.id == s.infractionId } ?: return null
        val cat = c.categories.firstOrNull { it.id == inf.categoryId } ?: return null
        val st = settings.value
        val student = students.value.firstOrNull { it.id == s.studentId }
        return PvFactory.build(
            PvRequest(
                student = student,
                className = effectiveClass(),
                category = cat,
                infraction = inf,
                freeTextLabel = s.freeText,
                gage = s.gage,
                previousPvCount = previousCount(student?.id),
                school = st.schoolName,
                room = st.defaultRoom,
                agent = st.agentName,
                lastPvYear = st.lastPvYear,
                lastPvSeq = st.lastPvSeq,
                now = now,
            ),
        )
    }

    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(120)
            val draft = buildDraft(System.currentTimeMillis())
            if (draft == null) { _preview.value = null; return@launch }
            val width = printers.widthDots(settings.value)
            _preview.value = withContext(Dispatchers.Default) { pv.renderer.render(draft.ticket, width) }
        }
    }

    /**
     * Imprime puis enregistre. Le PV n'est enregistré qu'après une impression réussie.
     * [retry] réimprime exactement le même ticket après un échec.
     */
    fun verbaliser(retry: Boolean = false) {
        if (_phase.value is PrintPhase.Printing) return
        viewModelScope.launch {
            val job = if (retry) pending else null
            val (draft, bitmap) = job ?: run {
                val d = buildDraft(System.currentTimeMillis()) ?: return@launch
                val width = printers.widthDots(settings.value)
                d to withContext(Dispatchers.Default) { pv.renderer.render(d.ticket, width) }
            }
            pending = draft to bitmap
            _preview.value = bitmap
            _phase.value = PrintPhase.Printing
            try {
                printers.print(bitmap, settings.value.intensity)
            } catch (e: Exception) {
                val err: PrinterException = e.asPrinterException()
                _phase.value = PrintPhase.Failed(err.message ?: "Impression impossible.")
                return@launch
            }
            save(draft, printed = true)
        }
    }

    /** Mode aperçu : enregistre sans passer par une imprimante. */
    fun saveWithoutPrinting() {
        viewModelScope.launch {
            val d = buildDraft(System.currentTimeMillis()) ?: return@launch
            save(d, printed = false)
        }
    }

    fun cancelFailed() {
        pending = null
        _phase.value = PrintPhase.Idle
    }

    private suspend fun save(draft: PvDraft, printed: Boolean) {
        repo.record(draft)
        pending = null
        _phase.value = PrintPhase.Idle
        _done.value = if (printed) "PV ${draft.pv.number} imprimé et enregistré." else "PV ${draft.pv.number} enregistré (non imprimé)."
        _sel.update { it.copy(studentId = null, infractionId = null, freeText = "", gage = "", gageEdited = false) }
    }

    companion object {
        /** Tri « naturel » : 2nde 2 avant 2nde 10. */
        val naturalOrder = Comparator<String> { a, b ->
            val ra = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
            val rb = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(ra.size, rb.size)) {
                val x = ra[i]; val y = rb[i]
                val c = if (x[0].isDigit() && y[0].isDigit()) {
                    x.toBigInteger().compareTo(y.toBigInteger())
                } else x.compareTo(y)
                if (c != 0) return@Comparator c
            }
            ra.size - rb.size
        }
    }
}
