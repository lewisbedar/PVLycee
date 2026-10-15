package fr.pvlycee.domain

data class CsvStudent(val firstName: String, val className: String)

data class CsvResult(
    val students: List<CsvStudent>,
    /** Lignes ignorées, numérotées à partir de 1, avec la raison. */
    val skipped: List<String>,
)

/**
 * Import d'une liste d'élèves : une colonne prénom, une colonne classe.
 * Accepte « ; », « , » ou tabulation, guillemets, BOM, en-tête facultatif
 * (l'en-tête permet aussi d'inverser l'ordre des colonnes).
 */
object CsvImport {

    fun parse(text: String): CsvResult {
        val content = text.removePrefix("﻿")
        val lines = content.split("\r\n", "\n", "\r")
        val firstNonBlank = lines.firstOrNull { it.isNotBlank() } ?: return CsvResult(emptyList(), emptyList())
        val sep = detectSeparator(firstNonBlank)

        var nameCol = 0
        var classCol = 1
        var startIndex = 0
        val headerCells = splitLine(firstNonBlank, sep).map { normalize(it) }
        val headerName = headerCells.indexOfFirst { it in NAME_HEADERS }
        val headerClass = headerCells.indexOfFirst { it in CLASS_HEADERS }
        if (headerName >= 0 || headerClass >= 0) {
            if (headerName >= 0) nameCol = headerName
            if (headerClass >= 0) classCol = headerClass
            if (headerName >= 0 && headerClass < 0) classCol = if (nameCol == 0) 1 else 0
            if (headerClass >= 0 && headerName < 0) nameCol = if (classCol == 0) 1 else 0
            startIndex = lines.indexOf(firstNonBlank) + 1
        }

        val seen = HashSet<Pair<String, String>>()
        val students = ArrayList<CsvStudent>()
        val skipped = ArrayList<String>()
        for (i in startIndex until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val cells = splitLine(line, sep)
            val name = cells.getOrNull(nameCol)?.let(::cleanCell).orEmpty()
            val cls = cells.getOrNull(classCol)?.let(::cleanCell).orEmpty()
            when {
                name.isEmpty() -> skipped += "Ligne ${i + 1} : prénom manquant"
                cls.isEmpty() -> skipped += "Ligne ${i + 1} : classe manquante"
                !seen.add(name.lowercase() to cls.lowercase()) -> skipped += "Ligne ${i + 1} : doublon ($name, $cls)"
                else -> students += CsvStudent(name, cls)
            }
        }
        return CsvResult(students, skipped)
    }

    internal fun detectSeparator(line: String): Char {
        val counts = listOf(';', '\t', ',').associateWith { c -> countOutsideQuotes(line, c) }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ';'
    }

    private fun countOutsideQuotes(line: String, c: Char): Int {
        var inQuotes = false
        var n = 0
        for (ch in line) {
            if (ch == '"') inQuotes = !inQuotes else if (ch == c && !inQuotes) n++
        }
        return n
    }

    internal fun splitLine(line: String, sep: Char): List<String> {
        val cells = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                ch == sep && !inQuotes -> { cells += sb.toString(); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        cells += sb.toString()
        return cells
    }

    private fun cleanCell(s: String): String = PvRules.clean(s.trim().replace(Regex("\\s+"), " "))

    private fun normalize(s: String): String =
        s.trim().lowercase()
            .replace('é', 'e').replace('è', 'e').replace('ê', 'e')
            .replace(" ", "")

    private val NAME_HEADERS = setOf("prenom", "prenoms", "nom", "eleve", "firstname", "name")
    private val CLASS_HEADERS = setOf("classe", "class", "division", "groupe")
}
