package fr.pvlycee.domain

data class ClassRecap(
    val className: String,
    val totalPv: Int,
    /** Nombre de PV par nom de catégorie, du plus fréquent au moins fréquent. */
    val byCategory: List<Pair<String, Int>>,
    val topInfraction: String?,
) {
    /** « Spécialité de la 2nde 3 : bavardage pendant le cours ». */
    val specialty: String?
        get() = topInfraction?.let { "Spécialité de la $className : ${it.replaceFirstChar { c -> c.lowercase() }}" }
}

object Stats {
    fun classRecap(className: String, pvs: List<Pv>): ClassRecap {
        val mine = pvs.filter { it.className == className }
        val byCat = mine.groupingBy { it.categoryName }.eachCount()
            .toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        val top = mine.groupingBy { it.infractionLabel }.eachCount()
            .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })?.key
        return ClassRecap(className, mine.size, byCat, top)
    }

    /** Les [n] infractions les plus utilisées, complétées par l'ordre du catalogue. */
    fun frequentInfractions(usage: Map<Long, Int>, catalog: List<Infraction>, n: Int = 4): List<Infraction> {
        val active = catalog.filter { it.enabled }
        val used = active.filter { (usage[it.id] ?: 0) > 0 }
            .sortedWith(compareByDescending<Infraction> { usage[it.id] ?: 0 }.thenBy { it.sortOrder })
        val rest = active.filter { it !in used && !it.freeText }.sortedBy { it.sortOrder }
        return (used + rest).take(n)
    }
}
