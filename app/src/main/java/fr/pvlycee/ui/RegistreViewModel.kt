package fr.pvlycee.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.pvlycee.PvApp
import fr.pvlycee.domain.CsvImport
import fr.pvlycee.domain.Pv
import fr.pvlycee.domain.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

class RegistreViewModel(app: Application) : AndroidViewModel(app) {
    private val pv = app as PvApp
    private val repo = pv.repository

    val students: StateFlow<List<Student>> = repo.students.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pvs: StateFlow<List<Pv>> = repo.pvs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Dernière classe utilisée sur l'écran Verbaliser. */
    val lastClass: StateFlow<String> = pv.settings.settings.map { it.lastClass }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun dismissMessage() { _message.value = null }

    fun addStudent(firstName: String, className: String) {
        if (firstName.isBlank() || className.isBlank()) return
        viewModelScope.launch {
            repo.addStudent(firstName, className)
            _message.value = "${firstName.trim()} ajouté(e) en ${className.trim()}."
        }
    }

    fun renameStudent(s: Student, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.renameStudent(s, name) }
    }

    fun amnesty(s: Student) = viewModelScope.launch {
        repo.amnesty(s.id)
        _message.value = "Amnistie : ${s.firstName} retrouve ses 12 points."
    }

    fun amnestyClass(className: String) = viewModelScope.launch {
        repo.amnestyClass(className)
        _message.value = "Amnistie générale : toute la $className retrouve ses 12 points."
    }

    fun deleteStudent(s: Student) = viewModelScope.launch {
        repo.deleteStudent(s.id)
        _message.value = "${s.firstName} a été supprimé(e) du registre."
    }

    fun deleteClass(className: String) = viewModelScope.launch {
        repo.deleteClass(className)
        _message.value = "La classe $className a été supprimée."
    }

    fun renameClass(old: String, new: String) {
        if (new.isBlank() || new == old) return
        viewModelScope.launch { repo.renameClass(old, new) }
    }

    fun importCsv(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) { readText(uri) }
            val result = CsvImport.parse(text)
            val added = repo.importStudents(result.students)
            val ignored = result.students.size - added
            _message.value = buildString {
                append("Import CSV : $added élève${if (added > 1) "s" else ""} ajouté${if (added > 1) "s" else ""}.")
                if (ignored > 0) append(" $ignored déjà présent${if (ignored > 1) "s" else ""}.")
                if (result.skipped.isNotEmpty()) {
                    append(" ${result.skipped.size} ligne${if (result.skipped.size > 1) "s" else ""} ignorée${if (result.skipped.size > 1) "s" else ""}")
                    append(" (").append(result.skipped.take(3).joinToString("; ")).append(if (result.skipped.size > 3) "…)" else ")")
                }
            }
        } catch (e: Exception) {
            _message.value = "Import impossible : ${e.message ?: "fichier illisible"}"
        }
    }

    fun exportJson(uri: Uri) = viewModelScope.launch {
        try {
            val json = repo.exportJson()
            withContext(Dispatchers.IO) {
                pv.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: error("écriture refusée")
            }
            _message.value = "Sauvegarde exportée."
        } catch (e: Exception) {
            _message.value = "Export impossible : ${e.message ?: "erreur"}"
        }
    }

    fun importJson(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) { readText(uri) }
            val b = repo.importJson(text)
            _message.value = "Sauvegarde restaurée : ${b.students.size} élèves, ${b.pvs.size} PV."
        } catch (e: Exception) {
            _message.value = "Restauration impossible : ${e.message ?: "fichier invalide"}"
        }
    }

    /** Lit un fichier texte en UTF-8, ou en Windows-1252 si ce n'est pas de l'UTF-8 valide (export Excel). */
    private fun readText(uri: Uri): String {
        val bytes = pv.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("fichier introuvable")
        val decoder = Charsets.UTF_8.newDecoder()
        return try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}
