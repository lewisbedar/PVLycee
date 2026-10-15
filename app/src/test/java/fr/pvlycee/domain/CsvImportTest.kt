package fr.pvlycee.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportTest {

    @Test fun `point-virgule avec en-tete (export Excel francais)`() {
        val r = CsvImport.parse("﻿Prénom;Classe\r\nLéa;2nde 3\r\nYanis;2nde 3\r\n\r\nInès;1re 2\r\n")
        assertEquals(listOf(CsvStudent("Léa", "2nde 3"), CsvStudent("Yanis", "2nde 3"), CsvStudent("Inès", "1re 2")), r.students)
        assertTrue(r.skipped.isEmpty())
    }

    @Test fun `virgule sans en-tete`() {
        val r = CsvImport.parse("Léa,2nde 3\nYanis,2nde 4")
        assertEquals(listOf(CsvStudent("Léa", "2nde 3"), CsvStudent("Yanis", "2nde 4")), r.students)
    }

    @Test fun `colonnes inversees reperees par l'en-tete`() {
        val r = CsvImport.parse("classe,prenom\nTerm 1,Noah\n")
        assertEquals(listOf(CsvStudent("Noah", "Term 1")), r.students)
    }

    @Test fun `guillemets, separateur dans un champ et espaces`() {
        val r = CsvImport.parse("prénom;classe\n\"  Marie-Anne \";\"2nde 3\"\n\"Jean \"\"JJ\"\"\";2nde 3\n")
        assertEquals(CsvStudent("Marie-Anne", "2nde 3"), r.students[0])
        assertEquals(CsvStudent("Jean \"JJ\"", "2nde 3"), r.students[1])
        val c = CsvImport.parse("\"Dupont, Léa\",2nde 1")
        assertEquals(CsvStudent("Dupont, Léa", "2nde 1"), c.students.single())
    }

    @Test fun `tabulation`() {
        val r = CsvImport.parse("Léa\t2nde 3\nTom\t2nde 3")
        assertEquals(2, r.students.size)
    }

    @Test fun `lignes incompletes et doublons ignores avec explication`() {
        val r = CsvImport.parse("Prénom;Classe\nLéa;2nde 3\n;2nde 3\nTom;\nlÉa;2nde 3\nTom;2nde 4")
        assertEquals(listOf(CsvStudent("Léa", "2nde 3"), CsvStudent("Tom", "2nde 4")), r.students)
        assertEquals(3, r.skipped.size)
        assertTrue(r.skipped[0].contains("prénom manquant"))
        assertTrue(r.skipped[1].contains("classe manquante"))
        assertTrue(r.skipped[2].contains("doublon"))
    }

    @Test fun `fichier vide`() {
        assertTrue(CsvImport.parse("").students.isEmpty())
        assertTrue(CsvImport.parse("\n\n").students.isEmpty())
    }

    @Test fun `colonnes supplementaires ignorees`() {
        val r = CsvImport.parse("Nom;Classe;Option\nLéa;2nde 3;Latin")
        assertEquals(CsvStudent("Léa", "2nde 3"), r.students.single())
    }
}
