package fr.pvlycee.domain

import kotlinx.serialization.Serializable

/** Catégorie d'infractions (ex. « Mobilier », rattachée au « Code de la Chaise »). */
@Serializable
data class Category(
    val id: Long,
    val name: String,
    val codeName: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)

/**
 * Infraction du catalogue.
 * [article] est le numéro seul (« C-2 »), [articleNote] la précision humoristique
 * (« atteinte au mobilier d'autrui »), éventuellement vide.
 */
@Serializable
data class Infraction(
    val id: Long,
    val categoryId: Long,
    val label: String,
    val article: String,
    val articleNote: String,
    val points: Int,
    val enabled: Boolean = true,
    val freeText: Boolean = false,
    val sortOrder: Int = 0,
)

/**
 * Gage (peine encourue). [allCategories] vaut pour toutes les catégories,
 * sinon [categoryIds] liste celles auxquelles il s'applique.
 * [custom] désigne le gage en texte libre, jamais proposé automatiquement.
 */
@Serializable
data class Gage(
    val id: Long,
    val label: String,
    val categoryIds: Set<Long> = emptySet(),
    val allCategories: Boolean = false,
    val custom: Boolean = false,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
) {
    fun appliesTo(categoryId: Long): Boolean = allCategories || categoryId in categoryIds
}

@Serializable
data class Student(
    val id: Long,
    val firstName: String,
    val className: String,
    val points: Int = PvRules.MAX_POINTS,
)

/** Procès-verbal enregistré. Les libellés sont figés au moment de la verbalisation. */
@Serializable
data class Pv(
    val id: Long,
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
    val pointsAfter: Int?,
    val contraventionClass: Int,
    val gage: String,
    val agent: String,
)
