package fr.pvlycee.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import fr.pvlycee.domain.Category
import fr.pvlycee.domain.Gage
import fr.pvlycee.domain.Infraction
import fr.pvlycee.domain.Pv
import fr.pvlycee.domain.Student
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val codeName: String,
    val sortOrder: Int,
    val enabled: Boolean,
) {
    fun toDomain() = Category(id, name, codeName, sortOrder, enabled)
    companion object {
        fun from(c: Category) = CategoryEntity(c.id, c.name, c.codeName, c.sortOrder, c.enabled)
    }
}

@Entity(tableName = "infractions", indices = [Index("categoryId")])
data class InfractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val label: String,
    val article: String,
    val articleNote: String,
    val points: Int,
    val enabled: Boolean,
    val freeText: Boolean,
    val sortOrder: Int,
) {
    fun toDomain() = Infraction(id, categoryId, label, article, articleNote, points, enabled, freeText, sortOrder)
    companion object {
        fun from(i: Infraction) = InfractionEntity(
            i.id, i.categoryId, i.label, i.article, i.articleNote, i.points, i.enabled, i.freeText, i.sortOrder,
        )
    }
}

@Entity(tableName = "gages")
data class GageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    /** Identifiants de catégories séparés par des virgules. */
    val categoryIds: String,
    val allCategories: Boolean,
    val custom: Boolean,
    val enabled: Boolean,
    val sortOrder: Int,
) {
    fun toDomain() = Gage(
        id, label,
        categoryIds.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet(),
        allCategories, custom, enabled, sortOrder,
    )
    companion object {
        fun from(g: Gage) = GageEntity(
            g.id, g.label, g.categoryIds.sorted().joinToString(","), g.allCategories, g.custom, g.enabled, g.sortOrder,
        )
    }
}

@Entity(tableName = "students", indices = [Index("className")])
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val className: String,
    val points: Int,
) {
    fun toDomain() = Student(id, firstName, className, points)
    companion object {
        fun from(s: Student) = StudentEntity(s.id, s.firstName, s.className, s.points)
    }
}

@Entity(tableName = "pvs", indices = [Index("studentId"), Index("className")])
data class PvEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val timestamp: Long,
    val studentId: Long?,
    val studentName: String,
    val className: String,
    val room: String,
    val categoryId: Long,
    val categoryName: String,
    val codeName: String,
    val infractionId: Long,
    val infractionLabel: String,
    val article: String,
    val pointsRemoved: Int,
    @ColumnInfo(name = "pointsAfter") val pointsAfter: Int?,
    val contraventionClass: Int,
    val gage: String,
    val agent: String,
) {
    fun toDomain() = Pv(
        id, number, timestamp, studentId, studentName, className, room, categoryId, categoryName, codeName,
        infractionId, infractionLabel, article, pointsRemoved, pointsAfter, contraventionClass, gage, agent,
    )
    companion object {
        fun from(p: Pv) = PvEntity(
            p.id, p.number, p.timestamp, p.studentId, p.studentName, p.className, p.room, p.categoryId,
            p.categoryName, p.codeName, p.infractionId, p.infractionLabel, p.article, p.pointsRemoved,
            p.pointsAfter, p.contraventionClass, p.gage, p.agent,
        )
    }
}

data class UsageCount(val infractionId: Long, val n: Int)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM infractions ORDER BY sortOrder, id") fun infractions(): Flow<List<InfractionEntity>>
    @Query("SELECT * FROM gages ORDER BY sortOrder, id") fun gages(): Flow<List<GageEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id") suspend fun allCategories(): List<CategoryEntity>
    @Query("SELECT * FROM infractions ORDER BY sortOrder, id") suspend fun allInfractions(): List<InfractionEntity>
    @Query("SELECT * FROM gages ORDER BY sortOrder, id") suspend fun allGages(): List<GageEntity>
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int

    @Upsert suspend fun upsertCategory(c: CategoryEntity): Long
    @Upsert suspend fun upsertInfraction(i: InfractionEntity): Long
    @Upsert suspend fun upsertGage(g: GageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(c: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertInfractions(i: List<InfractionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGages(g: List<GageEntity>)

    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategoryRow(id: Long)
    @Query("DELETE FROM infractions WHERE categoryId = :categoryId") suspend fun deleteInfractionsOf(categoryId: Long)
    @Query("DELETE FROM infractions WHERE id = :id") suspend fun deleteInfraction(id: Long)
    @Query("DELETE FROM gages WHERE id = :id") suspend fun deleteGage(id: Long)

    @Transaction
    suspend fun deleteCategory(id: Long) {
        deleteInfractionsOf(id)
        deleteCategoryRow(id)
    }

    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM infractions") suspend fun clearInfractions()
    @Query("DELETE FROM gages") suspend fun clearGages()
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY className, firstName COLLATE NOCASE") fun students(): Flow<List<StudentEntity>>
    @Query("SELECT * FROM students ORDER BY className, firstName COLLATE NOCASE") suspend fun allStudents(): List<StudentEntity>
    @Query("SELECT * FROM students WHERE id = :id") suspend fun student(id: Long): StudentEntity?
    @Insert suspend fun insert(s: StudentEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(s: List<StudentEntity>)
    @Upsert suspend fun upsert(s: StudentEntity): Long
    @Query("UPDATE students SET points = :points WHERE id = :id") suspend fun setPoints(id: Long, points: Int)
    @Query("UPDATE students SET points = :points WHERE className = :className") suspend fun setClassPoints(className: String, points: Int)
    @Query("DELETE FROM students WHERE id = :id") suspend fun deleteRow(id: Long)
    @Query("DELETE FROM students WHERE className = :className") suspend fun deleteClassRows(className: String)
    @Query("UPDATE students SET className = :newName WHERE className = :oldName") suspend fun renameClassRows(oldName: String, newName: String)
    @Query("DELETE FROM students") suspend fun clear()
}

@Dao
interface PvDao {
    @Query("SELECT * FROM pvs ORDER BY timestamp DESC, id DESC") fun pvs(): Flow<List<PvEntity>>
    @Query("SELECT * FROM pvs ORDER BY timestamp DESC, id DESC") suspend fun allPvs(): List<PvEntity>
    @Query("SELECT COUNT(*) FROM pvs WHERE studentId = :studentId") suspend fun countFor(studentId: Long): Int
    @Query("SELECT infractionId, COUNT(*) AS n FROM pvs GROUP BY infractionId") fun usage(): Flow<List<UsageCount>>
    @Insert suspend fun insert(p: PvEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(p: List<PvEntity>)
    @Query("DELETE FROM pvs WHERE studentId = :studentId") suspend fun deleteFor(studentId: Long)
    @Query("DELETE FROM pvs WHERE studentId IN (SELECT id FROM students WHERE className = :className)") suspend fun deleteForClass(className: String)
    @Query("UPDATE pvs SET className = :newName WHERE className = :oldName") suspend fun renameClass(oldName: String, newName: String)
    @Query("DELETE FROM pvs") suspend fun clear()
}

@Database(
    entities = [CategoryEntity::class, InfractionEntity::class, GageEntity::class, StudentEntity::class, PvEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PvDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun studentDao(): StudentDao
    abstract fun pvDao(): PvDao

    companion object {
        fun create(context: Context): PvDatabase =
            Room.databaseBuilder(context.applicationContext, PvDatabase::class.java, "pvlycee.db").build()
    }
}
