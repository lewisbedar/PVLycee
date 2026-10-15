package fr.pvlycee.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PvFactoryTest {
    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 14, 10, 5, 0) }.timeInMillis
    private val mastication = DefaultCatalog.categories.first { it.id == DefaultCatalog.MASTICATION }
    private val gumUnderTable = DefaultCatalog.infractions.first { it.article == "M-2" }

    private fun request(student: Student?, previous: Int = 0, lastYear: Int = 2026, lastSeq: Int = 6) = PvRequest(
        student = student, className = "2nde 3", category = mastication, infraction = gumUnderTable,
        gage = "Ramasser 3 papiers dans la salle", previousPvCount = previous,
        school = "Lycée Victor Hugo", room = "B12", agent = "M. Martin",
        lastPvYear = lastYear, lastPvSeq = lastSeq, now = now,
    )

    @Test fun `PV d'un eleve identifie`() {
        val d = PvFactory.build(request(Student(5, "Léa", "2nde 3", 12), previous = 1))
        assertEquals("2026-0007", d.pv.number)
        assertEquals(7, d.seq)
        assertEquals(2, d.pv.contraventionClass)
        assertEquals(3, d.pv.pointsRemoved)
        assertEquals(9, d.pv.pointsAfter)
        assertEquals(5L, d.pv.studentId)
        assertEquals("Art. M-2 du Code de la Mastication", d.ticket.article)
        assertEquals("dépôt sauvage", d.ticket.articleNote)
        assertEquals("14/10/2026", d.ticket.date)
        assertEquals("10 h 05", d.ticket.time)
        assertTrue(d.ticket.isRecidive)
        assertFalse(d.ticket.suspended)
    }

    @Test fun `contrevenant non identifie, pas de points, 1re classe`() {
        val d = PvFactory.build(request(null, previous = 3))
        assertNull(d.pv.studentId)
        assertNull(d.pv.pointsAfter)
        assertEquals(0, d.pv.pointsRemoved)
        assertEquals(1, d.pv.contraventionClass)
        assertEquals(PvFactory.UNKNOWN_OFFENDER, d.ticket.offender)
        assertFalse(d.ticket.identified)
        assertFalse(d.ticket.isRecidive)
    }

    @Test fun `permis suspendu quand le solde atteint zero`() {
        val d = PvFactory.build(request(Student(1, "Tom", "1re 2", 2), previous = 7))
        assertEquals(0, d.pv.pointsAfter)
        assertEquals(5, d.pv.contraventionClass)
        assertTrue(d.ticket.suspended)
    }

    @Test fun `changement d'annee, la numerotation repart a 0001`() {
        val d = PvFactory.build(request(null, lastYear = 2025, lastSeq = 250))
        assertEquals("2026-0001", d.pv.number)
    }

    @Test fun `infraction en texte libre`() {
        val free = DefaultCatalog.infractions.first { it.freeText }
        val divers = DefaultCatalog.categories.first { it.id == free.categoryId }
        val d = PvFactory.build(request(null).copy(infraction = free, category = divers, freeTextLabel = "Avion en papier — vol non autorisé"))
        assertEquals("Avion en papier, vol non autorisé", d.ticket.infraction)
        assertEquals("Art. D-0 du Code du Prof", d.ticket.article)
    }
}
