package fr.pvlycee.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PvRulesTest {

    @Test fun `le solde de points diminue sans descendre sous zero`() {
        assertEquals(10, PvRules.pointsAfter(12, 2))
        assertEquals(0, PvRules.pointsAfter(1, 3))
        assertEquals(0, PvRules.pointsAfter(0, 1))
        assertEquals(12, PvRules.pointsAfter(12, 0))
        assertEquals(12, PvRules.pointsAfter(12, -4))
    }

    @Test fun `permis suspendu a zero point`() {
        assertTrue(PvRules.isSuspended(0))
        assertFalse(PvRules.isSuspended(1))
    }

    @Test fun `classe de contravention egale aux PV precedents plus un plafonnee a 5`() {
        assertEquals(1, PvRules.contraventionClass(0))
        assertEquals(2, PvRules.contraventionClass(1))
        assertEquals(5, PvRules.contraventionClass(4))
        assertEquals(5, PvRules.contraventionClass(12))
        assertEquals(1, PvRules.contraventionClass(-3))
    }

    @Test fun `libelles de classe`() {
        assertEquals("1re classe", PvRules.contraventionClassLabel(1))
        assertEquals("2e classe", PvRules.contraventionClassLabel(2))
        assertEquals("5e classe", PvRules.contraventionClassLabel(5))
    }

    private val gages = DefaultCatalog.gages

    @Test fun `premier PV, simple avertissement pour toutes les categories`() {
        DefaultCatalog.categories.forEach { c ->
            assertEquals("Simple avertissement", PvRules.defaultGage(gages, c.id, 0)?.label)
        }
    }

    @Test fun `gage selon le rang de recidive puis plafonne au dernier gage standard`() {
        val m = DefaultCatalog.MOBILIER
        assertEquals("Remonter toutes les chaises", PvRules.defaultGage(gages, m, 1)?.label)
        assertEquals("Ranger la salle", PvRules.defaultGage(gages, m, 2)?.label)
        assertEquals("Ranger la salle", PvRules.defaultGage(gages, m, 9)?.label)

        val mast = DefaultCatalog.MASTICATION
        assertEquals("Ramasser 3 papiers dans la salle", PvRules.defaultGage(gages, mast, 1)?.label)
        assertEquals("Ramasser 3 papiers dans la salle", PvRules.defaultGage(gages, mast, 4)?.label)

        val parole = DefaultCatalog.PAROLE
        assertEquals("Effacer le tableau", PvRules.defaultGage(gages, parole, 1)?.label)
        assertEquals("Distribuer les copies", PvRules.defaultGage(gages, parole, 2)?.label)
        assertEquals("Lire la consigne à voix haute", PvRules.defaultGage(gages, parole, 3)?.label)
        assertEquals("Lire la consigne à voix haute", PvRules.defaultGage(gages, parole, 30)?.label)
    }

    @Test fun `le gage en texte libre n'est jamais propose automatiquement`() {
        for (c in DefaultCatalog.categories) for (r in 0..10) {
            assertFalse(PvRules.defaultGage(gages, c.id, r)!!.custom)
        }
    }

    @Test fun `les gages desactives sont ignores et l'ordre est respecte`() {
        val list = listOf(
            Gage(1, "A", allCategories = true, sortOrder = 0),
            Gage(2, "B", setOf(7L), sortOrder = 2),
            Gage(3, "C", setOf(7L), sortOrder = 1, enabled = false),
            Gage(4, "D", setOf(7L), sortOrder = 3),
        )
        assertEquals("B", PvRules.defaultGage(list, 7L, 1)?.label)
        assertEquals("D", PvRules.defaultGage(list, 7L, 2)?.label)
        assertEquals("A", PvRules.defaultGage(list, 99L, 3)?.label)
        assertNull(PvRules.defaultGage(emptyList(), 7L, 0))
    }

    @Test fun `numero de PV au format AAAA-0001`() {
        assertEquals("2026-0001", PvRules.formatPvNumber(2026, 1))
        assertEquals("2026-0042", PvRules.formatPvNumber(2026, 42))
        assertEquals("2027-12345", PvRules.formatPvNumber(2027, 12345))
    }

    @Test fun `numerotation globale incrementale qui repart a 1 chaque annee`() {
        assertEquals(1, PvRules.nextSequence(0, 0, 2026))
        assertEquals(42, PvRules.nextSequence(2026, 41, 2026))
        assertEquals(1, PvRules.nextSequence(2025, 318, 2026))
    }

    @Test fun `article complet avec le code de la categorie`() {
        assertEquals("Art. M-2 du Code de la Mastication", PvRules.fullArticle("M-2", "Code de la Mastication"))
        assertEquals("Art. D-0 du Code du Prof", PvRules.fullArticle("D-0", "Code du Prof"))
        assertEquals("Art. X-1", PvRules.fullArticle("X-1", " "))
    }

    @Test fun `aucun tiret cadratin`() {
        assertEquals("Retard, grave", PvRules.clean("Retard — grave"))
        assertFalse(PvRules.clean("a—b").contains('—'))
    }

    @Test fun `catalogue par defaut complet et sans tiret cadratin`() {
        assertEquals(6, DefaultCatalog.categories.size)
        assertEquals(21, DefaultCatalog.infractions.size)
        assertEquals(8, DefaultCatalog.gages.size)
        assertEquals(DefaultCatalog.infractions.size, DefaultCatalog.infractions.map { it.id }.toSet().size)
        val texts = DefaultCatalog.categories.flatMap { listOf(it.name, it.codeName) } +
            DefaultCatalog.infractions.flatMap { listOf(it.label, it.article, it.articleNote) } +
            DefaultCatalog.gages.map { it.label }
        texts.forEach { assertFalse(it, it.contains('—')) }
        val m2 = DefaultCatalog.infractions.first { it.article == "M-2" }
        assertEquals(3, m2.points)
        assertTrue(DefaultCatalog.infractions.single { it.freeText }.article == "D-0")
        assertEquals("Simple avertissement", DefaultCatalog.gages.minBy { it.sortOrder }.label)
        assertTrue(DefaultCatalog.gages.first { it.label.startsWith("Ramasser") }.categoryIds
            .containsAll(listOf(DefaultCatalog.MASTICATION, DefaultCatalog.DIVERS)))
    }
}
