package fr.pvlycee.domain

/** Catalogue livré par défaut (restauré lors d'une remise à zéro). */
object DefaultCatalog {
    const val MOBILIER = 1L
    const val PAROLE = 2L
    const val MASTICATION = 3L
    const val NUMERIQUE = 4L
    const val CIRCULATION = 5L
    const val DIVERS = 6L

    val categories = listOf(
        Category(MOBILIER, "Mobilier", "Code de la Chaise", 0),
        Category(PAROLE, "Parole", "Code de la Parole", 1),
        Category(MASTICATION, "Mastication", "Code de la Mastication", 2),
        Category(NUMERIQUE, "Numérique", "Code du Numérique", 3),
        Category(CIRCULATION, "Circulation", "Code de la Route Scolaire", 4),
        Category(DIVERS, "Divers", "Code du Prof", 5),
    )

    val infractions: List<Infraction> = run {
        var id = 0L
        fun inf(cat: Long, label: String, art: String, note: String, pts: Int, free: Boolean = false) =
            Infraction(++id, cat, label, art, note, pts, freeText = free, sortOrder = id.toInt())
        listOf(
            inf(MOBILIER, "Stationnement de pieds sur une chaise", "C-1", "", 1),
            inf(MOBILIER, "Pieds sur la chaise d'un camarade", "C-2", "atteinte au mobilier d'autrui", 2),
            inf(MOBILIER, "Pieds sur la table", "C-3", "", 2),
            inf(MOBILIER, "Balancement sur deux pieds de chaise", "C-4", "excès de vitesse angulaire", 1),
            inf(MOBILIER, "Avachissement en position allongée", "C-5", "posture non réglementaire", 1),
            inf(MOBILIER, "Chaise non remontée en fin de cours", "C-9", "stationnement gênant", 1),
            inf(PAROLE, "Bavardage pendant le cours", "P-1", "nuisance sonore", 1),
            inf(PAROLE, "Bavardage pendant une consigne", "P-2", "brouillage des ondes", 2),
            inf(PAROLE, "Prise de parole sans lever la main", "P-3", "refus de priorité", 1),
            inf(PAROLE, "Fou rire contagieux", "P-4", "propagation non autorisée", 1),
            inf(MASTICATION, "Chewing-gum en cours", "M-1", "rumination illicite", 1),
            inf(MASTICATION, "Chewing-gum collé sous la table", "M-2", "dépôt sauvage", 3),
            inf(MASTICATION, "Grignotage clandestin", "M-3", "ravitaillement non déclaré", 1),
            inf(NUMERIQUE, "Téléphone visible en cours", "N-1", "communication non autorisée", 2),
            inf(NUMERIQUE, "Écouteurs en cours", "N-2", "écoute clandestine", 1),
            inf(CIRCULATION, "Retard", "R-1", "défaut de ponctualité", 1),
            inf(CIRCULATION, "Sac au milieu de l'allée", "R-2", "obstruction de la voie publique", 1),
            inf(CIRCULATION, "Course dans le couloir", "R-3", "excès de vitesse", 1),
            inf(CIRCULATION, "Oubli de matériel", "R-4", "défaut d'équipement obligatoire", 1),
            inf(DIVERS, "Papier par terre", "D-1", "dépôt d'ordures", 1),
            inf(DIVERS, "Autre infraction", "D-0", "le prof a toujours raison", 1, free = true),
        )
    }

    val gages = listOf(
        Gage(1, "Simple avertissement", allCategories = true, sortOrder = 0),
        Gage(2, "Remonter toutes les chaises", setOf(MOBILIER), sortOrder = 1),
        Gage(3, "Effacer le tableau", setOf(PAROLE, NUMERIQUE, CIRCULATION, DIVERS), sortOrder = 2),
        Gage(4, "Distribuer les copies", setOf(PAROLE, NUMERIQUE, CIRCULATION), sortOrder = 3),
        Gage(5, "Lire la consigne à voix haute", setOf(PAROLE, NUMERIQUE), sortOrder = 4),
        Gage(6, "Ranger la salle", setOf(MOBILIER, CIRCULATION, DIVERS), sortOrder = 5),
        Gage(7, "Ramasser 3 papiers dans la salle", setOf(MASTICATION, DIVERS), sortOrder = 6),
        Gage(8, "Gage personnalisé", allCategories = true, custom = true, sortOrder = 7),
    )
}
