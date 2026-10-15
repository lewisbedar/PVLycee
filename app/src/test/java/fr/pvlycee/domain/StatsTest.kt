package fr.pvlycee.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {
    private fun pv(cls: String, cat: String, inf: String, infId: Long = 1) = Pv(
        0, "2026-0001", 0, 1, "x", cls, "", 1, cat, "", infId, inf, "", 1, 11, 1, "", "",
    )

    @Test fun `recapitulatif par classe et specialite`() {
        val pvs = listOf(
            pv("2nde 3", "Parole", "Bavardage pendant le cours"),
            pv("2nde 3", "Parole", "Bavardage pendant le cours"),
            pv("2nde 3", "Mobilier", "Pieds sur la table"),
            pv("1re 2", "Numérique", "Téléphone visible en cours"),
        )
        val r = Stats.classRecap("2nde 3", pvs)
        assertEquals(3, r.totalPv)
        assertEquals(listOf("Parole" to 2, "Mobilier" to 1), r.byCategory)
        assertEquals("Spécialité de la 2nde 3 : bavardage pendant le cours", r.specialty)
        assertNull(Stats.classRecap("Term 1", pvs).specialty)
    }

    @Test fun `quatre infractions frequentes, completees par l'ordre du catalogue`() {
        val cat = DefaultCatalog.infractions
        val usage = mapOf(14L to 5, 7L to 9, 12L to 1)
        val f = Stats.frequentInfractions(usage, cat)
        assertEquals(listOf(7L, 14L, 12L, 1L), f.map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), Stats.frequentInfractions(emptyMap(), cat).map { it.id })
    }
}
