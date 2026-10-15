package fr.pvlycee.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Données affichées sur le ticket, déjà mises en forme. */
data class TicketData(
    val number: String,
    val date: String,
    val time: String,
    val school: String,
    val room: String,
    val className: String,
    val offender: String,
    val identified: Boolean,
    val infraction: String,
    val article: String,
    val articleNote: String,
    val contraventionClass: Int,
    val previousPvCount: Int,
    val pointsRemoved: Int,
    val pointsBefore: Int,
    val pointsAfter: Int,
    val gage: String,
    val agent: String,
) {
    val suspended: Boolean get() = identified && PvRules.isSuspended(pointsAfter)
    val isRecidive: Boolean get() = identified && previousPvCount > 0
}

/** Ce qu'il faut pour verbaliser. */
data class PvRequest(
    val student: Student?,
    val className: String,
    val category: Category,
    val infraction: Infraction,
    /** Intitulé saisi pour une infraction en texte libre. */
    val freeTextLabel: String = "",
    val gage: String,
    val previousPvCount: Int,
    val school: String,
    val room: String,
    val agent: String,
    val lastPvYear: Int,
    val lastPvSeq: Int,
    val now: Long,
)

data class PvDraft(val pv: Pv, val ticket: TicketData, val year: Int, val seq: Int)

object PvFactory {
    const val UNKNOWN_OFFENDER = "Contrevenant non identifié"

    fun build(r: PvRequest): PvDraft {
        val year = PvRules.yearOf(r.now)
        val seq = PvRules.nextSequence(r.lastPvYear, r.lastPvSeq, year)
        val number = PvRules.formatPvNumber(year, seq)
        val identified = r.student != null
        val previous = if (identified) r.previousPvCount else 0
        val cls = PvRules.contraventionClass(previous)
        val before = r.student?.points ?: PvRules.MAX_POINTS
        val after = PvRules.pointsAfter(before, r.infraction.points)
        val label = PvRules.clean(
            if (r.infraction.freeText && r.freeTextLabel.isNotBlank()) r.freeTextLabel else r.infraction.label,
        )
        val article = PvRules.fullArticle(r.infraction.article, r.category.codeName)
        val gage = PvRules.clean(r.gage)
        val agent = PvRules.clean(r.agent)
        val className = PvRules.clean(r.student?.className ?: r.className)
        val offender = PvRules.clean(r.student?.firstName ?: UNKNOWN_OFFENDER)

        val pv = Pv(
            id = 0,
            number = number,
            timestamp = r.now,
            studentId = r.student?.id,
            studentName = offender,
            className = className,
            room = PvRules.clean(r.room),
            categoryId = r.category.id,
            categoryName = r.category.name,
            codeName = r.category.codeName,
            infractionId = r.infraction.id,
            infractionLabel = label,
            article = article,
            pointsRemoved = if (identified) r.infraction.points else 0,
            pointsAfter = if (identified) after else null,
            contraventionClass = cls,
            gage = gage,
            agent = agent,
        )
        val d = Date(r.now)
        val ticket = TicketData(
            number = number,
            date = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(d),
            time = SimpleDateFormat("HH 'h' mm", Locale.FRANCE).format(d),
            school = PvRules.clean(r.school),
            room = PvRules.clean(r.room),
            className = className,
            offender = offender,
            identified = identified,
            infraction = label,
            article = article,
            articleNote = PvRules.clean(r.infraction.articleNote),
            contraventionClass = cls,
            previousPvCount = previous,
            pointsRemoved = r.infraction.points,
            pointsBefore = before,
            pointsAfter = after,
            gage = gage,
            agent = agent,
        )
        return PvDraft(pv, ticket, year, seq)
    }
}
