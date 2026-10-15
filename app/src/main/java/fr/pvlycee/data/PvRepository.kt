package fr.pvlycee.data

import androidx.room.withTransaction
import fr.pvlycee.domain.Category
import fr.pvlycee.domain.CsvStudent
import fr.pvlycee.domain.DefaultCatalog
import fr.pvlycee.domain.Gage
import fr.pvlycee.domain.Infraction
import fr.pvlycee.domain.Pv
import fr.pvlycee.domain.PvDraft
import fr.pvlycee.domain.PvRules
import fr.pvlycee.domain.Student
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Backup(
    val format: String = "pvlycee",
    val version: Int = 1,
    val exportedAt: Long,
    val settings: AppSettings,
    val categories: List<Category>,
    val infractions: List<Infraction>,
    val gages: List<Gage>,
    val students: List<Student>,
    val pvs: List<Pv>,
)

class PvRepository(
    private val db: PvDatabase,
    val settingsRepo: SettingsRepository,
) {
    private val catalog = db.catalogDao()
    private val studentDao = db.studentDao()
    private val pvDao = db.pvDao()

    val categories: Flow<List<Category>> = catalog.categories().map { l -> l.map { it.toDomain() } }
    val infractions: Flow<List<Infraction>> = catalog.infractions().map { l -> l.map { it.toDomain() } }
    val gages: Flow<List<Gage>> = catalog.gages().map { l -> l.map { it.toDomain() } }
    val students: Flow<List<Student>> = studentDao.students().map { l -> l.map { it.toDomain() } }
    val pvs: Flow<List<Pv>> = pvDao.pvs().map { l -> l.map { it.toDomain() } }
    val usage: Flow<Map<Long, Int>> = pvDao.usage().map { l -> l.associate { it.infractionId to it.n } }

    suspend fun ensureSeeded() {
        if (catalog.categoryCount() == 0) seedCatalog()
    }

    private suspend fun seedCatalog() {
        catalog.insertCategories(DefaultCatalog.categories.map(CategoryEntity::from))
        catalog.insertInfractions(DefaultCatalog.infractions.map(InfractionEntity::from))
        catalog.insertGages(DefaultCatalog.gages.map(GageEntity::from))
    }

    suspend fun previousPvCount(studentId: Long): Int = pvDao.countFor(studentId)

    /** Enregistre un PV imprimé : PV, solde de points de l'élève et compteur de numérotation. */
    suspend fun record(draft: PvDraft) {
        db.withTransaction {
            pvDao.insert(PvEntity.from(draft.pv))
            val sid = draft.pv.studentId
            val after = draft.pv.pointsAfter
            if (sid != null && after != null) studentDao.setPoints(sid, after)
        }
        settingsRepo.update { it.copy(lastPvYear = draft.year, lastPvSeq = draft.seq) }
    }

    // --- Élèves et classes ---

    suspend fun addStudent(firstName: String, className: String): Long =
        studentDao.insert(StudentEntity(firstName = PvRules.clean(firstName), className = PvRules.clean(className), points = PvRules.MAX_POINTS))

    /** Ajoute les élèves absents (même prénom et même classe), renvoie le nombre ajouté. */
    suspend fun importStudents(list: List<CsvStudent>): Int {
        val existing = studentDao.allStudents().map { it.firstName.lowercase() to it.className.lowercase() }.toHashSet()
        val toAdd = list.filter { existing.add(it.firstName.lowercase() to it.className.lowercase()) }
        studentDao.insertAll(toAdd.map { StudentEntity(firstName = it.firstName, className = it.className, points = PvRules.MAX_POINTS) })
        return toAdd.size
    }

    suspend fun renameStudent(s: Student, firstName: String) =
        studentDao.upsert(StudentEntity.from(s.copy(firstName = PvRules.clean(firstName))))

    suspend fun amnesty(studentId: Long) = studentDao.setPoints(studentId, PvRules.MAX_POINTS)

    suspend fun amnestyClass(className: String) = studentDao.setClassPoints(className, PvRules.MAX_POINTS)

    suspend fun deleteStudent(studentId: Long) = db.withTransaction {
        pvDao.deleteFor(studentId)
        studentDao.deleteRow(studentId)
    }

    suspend fun deleteClass(className: String) = db.withTransaction {
        pvDao.deleteForClass(className)
        studentDao.deleteClassRows(className)
    }

    suspend fun renameClass(oldName: String, newName: String) = db.withTransaction {
        val n = PvRules.clean(newName)
        studentDao.renameClassRows(oldName, n)
        pvDao.renameClass(oldName, n)
    }

    // --- Catalogue ---

    suspend fun saveCategory(c: Category): Long = catalog.upsertCategory(CategoryEntity.from(c.copy(name = PvRules.clean(c.name), codeName = PvRules.clean(c.codeName))))
    suspend fun deleteCategory(id: Long) = catalog.deleteCategory(id)
    suspend fun saveInfraction(i: Infraction): Long = catalog.upsertInfraction(
        InfractionEntity.from(i.copy(label = PvRules.clean(i.label), article = PvRules.clean(i.article), articleNote = PvRules.clean(i.articleNote))),
    )
    suspend fun deleteInfraction(id: Long) = catalog.deleteInfraction(id)
    suspend fun saveGage(g: Gage): Long = catalog.upsertGage(GageEntity.from(g.copy(label = PvRules.clean(g.label))))
    suspend fun deleteGage(id: Long) = catalog.deleteGage(id)

    suspend fun restoreDefaultCatalog() = db.withTransaction {
        catalog.clearInfractions(); catalog.clearCategories(); catalog.clearGages()
        seedCatalog()
    }

    /** Remise à zéro complète : élèves, PV, catalogue et réglages. */
    suspend fun resetAll() {
        db.withTransaction {
            pvDao.clear(); studentDao.clear()
            catalog.clearInfractions(); catalog.clearCategories(); catalog.clearGages()
            seedCatalog()
        }
        settingsRepo.replace(AppSettings())
    }

    // --- Sauvegarde JSON ---

    suspend fun exportJson(): String {
        val b = Backup(
            exportedAt = System.currentTimeMillis(),
            settings = settingsRepo.current(),
            categories = catalog.allCategories().map { it.toDomain() },
            infractions = catalog.allInfractions().map { it.toDomain() },
            gages = catalog.allGages().map { it.toDomain() },
            students = studentDao.allStudents().map { it.toDomain() },
            pvs = pvDao.allPvs().map { it.toDomain() },
        )
        return json.encodeToString(Backup.serializer(), b)
    }

    /** Remplace toutes les données par celles de la sauvegarde. Lève une exception si le fichier est invalide. */
    suspend fun importJson(text: String): Backup {
        val b = json.decodeFromString(Backup.serializer(), text)
        require(b.format == "pvlycee") { "Ce fichier n'est pas une sauvegarde PVLycée." }
        db.withTransaction {
            pvDao.clear(); studentDao.clear()
            catalog.clearInfractions(); catalog.clearCategories(); catalog.clearGages()
            catalog.insertCategories(b.categories.map(CategoryEntity::from))
            catalog.insertInfractions(b.infractions.map(InfractionEntity::from))
            catalog.insertGages(b.gages.map(GageEntity::from))
            studentDao.insertAll(b.students.map(StudentEntity::from))
            pvDao.insertAll(b.pvs.map(PvEntity::from))
        }
        // L'imprimante reste celle de cet appareil : on ne reprend pas celle de la sauvegarde.
        settingsRepo.update { cur ->
            b.settings.copy(
                printerTransport = cur.printerTransport,
                printerAddress = cur.printerAddress,
                printerName = cur.printerName,
                forcedProtocol = cur.forcedProtocol,
                detectedProtocol = cur.detectedProtocol,
            )
        }
        return b
    }

    companion object {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
