package fr.pvlycee.domain

import java.util.Calendar

/** Règles métier pures (sans Android), couvertes par les tests unitaires. */
object PvRules {
    const val MAX_POINTS = 12
    const val MAX_CONTRAVENTION_CLASS = 5

    /** Solde après retrait, jamais sous 0 ni au-dessus de 12. */
    fun pointsAfter(current: Int, removed: Int): Int =
        (current - removed.coerceAtLeast(0)).coerceIn(0, MAX_POINTS)

    fun isSuspended(points: Int): Boolean = points <= 0

    /** Classe de contravention : PV précédents + 1, plafonnée à 5. */
    fun contraventionClass(previousPvCount: Int): Int =
        (previousPvCount.coerceAtLeast(0) + 1).coerceAtMost(MAX_CONTRAVENTION_CLASS)

    /** « 1re classe », « 2e classe »… */
    fun contraventionClassLabel(cls: Int): String =
        if (cls == 1) "1re classe" else "${cls}e classe"

    /**
     * Gage par défaut : parmi les gages actifs et standards (hors texte libre) associés à la
     * catégorie, triés par rang, celui dont le rang correspond au nombre de récidives,
     * plafonné au dernier. Renvoie null si aucun gage ne s'applique.
     */
    fun defaultGage(gages: List<Gage>, categoryId: Long, recidives: Int): Gage? {
        val candidates = standardGagesFor(gages, categoryId)
        if (candidates.isEmpty()) return null
        return candidates[recidives.coerceIn(0, candidates.lastIndex)]
    }

    fun standardGagesFor(gages: List<Gage>, categoryId: Long): List<Gage> =
        gages.filter { it.enabled && !it.custom && it.appliesTo(categoryId) }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

    /** Numéro au format AAAA-0001. */
    fun formatPvNumber(year: Int, seq: Int): String = "%04d-%04d".format(year, seq)

    /**
     * Prochain numéro de séquence : le compteur est global (tous élèves confondus)
     * et repart à 1 au changement d'année civile.
     */
    fun nextSequence(lastYear: Int, lastSeq: Int, currentYear: Int): Int =
        if (lastYear == currentYear) lastSeq.coerceAtLeast(0) + 1 else 1

    fun yearOf(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

    /** Article complet : « Art. M-2 du Code de la Mastication ». */
    fun fullArticle(article: String, codeName: String): String {
        val code = codeName.trim()
        val prefix = "Art. ${article.trim()}"
        if (code.isEmpty()) return prefix
        return "$prefix du $code"
    }

    /** Remplace le tiret cadratin (interdit sur le ticket et dans l'interface). */
    fun clean(text: String): String =
        text.replace(" — ", ", ").replace("—", "-").replace("–", "-").trim()
}
