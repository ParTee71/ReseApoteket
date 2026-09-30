package se.partee71.reseapoteket.core.engine

import java.text.Collator
import java.util.Locale
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.core.model.Sortable
import se.partee71.reseapoteket.core.model.Trip

/** En uträknad mängd: antal enheter, förpackningar och förklaringen (BER-10). */
data class PackingLine(val quantity: Int, val packages: Int?, val reason: String, val warning: Boolean = false)

/** Förslaget för en resa; [warnings] är namnen på rader som inte kunde räknas ut. */
data class PackingSuggestion(val items: List<PackingItem>, val warnings: List<String>)

/**
 * Packförslaget (skill packing-engine, BER-1–10). Rena funktioner: allt skickas in, inget
 * datum eller någon klocka läses här. [regimenQuantity] och [familyQuantity] är enda stället
 * där formlerna finns – förhandsvisningarna anropar [regimenLine] och [familyLine].
 */
object PackingEngine {

    /** Längsta resa och marginal som räknas – skydd mot orimliga värden från databasen (BCK-5). */
    const val MAX_DAYS = 366

    /** Största mängd på en rad; allt över det är ett datafel, inte en packning. */
    const val MAX_QUANTITY = 99_999

    /** BER-1. Aldrig negativt; dagar och marginal begränsas till [MAX_DAYS] var. */
    fun effectiveDays(trip: Trip, settings: HouseholdSettings): Int =
        trip.days.coerceIn(0, MAX_DAYS) + settings.marginDays.coerceIn(0, MAX_DAYS)

    /** BER-2–4. `null` = kan inte räknas ut (okänd dosering, veckodagar utan startdatum). */
    fun regimenQuantity(schedule: DoseSchedule, trip: Trip, settings: HouseholdSettings): Int? {
        val days = effectiveDays(trip, settings)
        return when (schedule) {
            is DoseSchedule.Daily -> product(schedule.dosesPerDay, schedule.unitsPerDose, days)
            is DoseSchedule.Weekdays -> weekdayCount(schedule, trip, settings)?.let { product(it, schedule.unitsPerDose) }
            is DoseSchedule.IntervalDays -> product(ceilDiv(days, schedule.intervalDays), schedule.unitsPerDose)
            is DoseSchedule.AsNeeded -> product(schedule.quantity)
            is DoseSchedule.Unknown -> null
        }
    }

    /** BER-5–7. `null` = okänd regel. */
    fun familyQuantity(rule: QuantityRule, travellers: Int, trip: Trip, settings: HouseholdSettings): Int? = when (rule) {
        is QuantityRule.Fixed -> product(rule.quantity)
        is QuantityRule.PerTraveller -> product(rule.quantity, travellers)
        is QuantityRule.PerDays -> product(ceilDiv(effectiveDays(trip, settings), rule.perDays), rule.quantity)
        is QuantityRule.Unknown -> null
    }

    /**
     * BER-3: antal dagar i resan (med marginal) vars veckodag ingår; `null` utan startdatum.
     * Den enda räkningen av veckodagar – förklaringen (`ReasonFormatter`) använder den också.
     */
    fun weekdayCount(schedule: DoseSchedule.Weekdays, trip: Trip, settings: HouseholdSettings): Int? {
        val start = trip.startDate ?: return null
        return (0 until effectiveDays(trip, settings)).count { start.plus(DatePeriod(days = it)).dayOfWeek in schedule.days }
    }

    /** Mängd, förpackningar och förklaring för ett recept eller kosttillskott – också förhandsvisningen. */
    fun regimenLine(regimen: Regimen, trip: Trip, settings: HouseholdSettings): PackingLine {
        val quantity = regimenQuantity(regimen.schedule, trip, settings)
        val missing = quantity == null || (regimen.schedule is DoseSchedule.AsNeeded && quantity == 0)
        val total = quantity ?: 0
        val packages = regimen.packageSize?.takeIf { it > 0 && total > 0 }?.let { ceilDiv(total, it) }
        return PackingLine(total, packages, ReasonFormatter.regimen(regimen, trip, settings, quantity, packages), missing)
    }

    /** Mängd och förklaring för en familjeartikel – också förhandsvisningen. */
    fun familyLine(item: FamilyItem, travellers: Int, trip: Trip, settings: HouseholdSettings): PackingLine {
        val quantity = familyQuantity(item.rule, travellers, trip, settings)
        return PackingLine(quantity ?: 0, null, ReasonFormatter.family(item, travellers, trip, settings, quantity), quantity == null)
    }

    /**
     * Förslaget för [trip]. Urval (BER-9): resenärer som finns och inte är arkiverade; deras
     * aktiva, icke arkiverade recept och kosttillskott (i [regimensByPerson]); aktiva, icke
     * arkiverade familjeartiklar. Sortering: sektion → person (`sortOrder`) → namn; familje-
     * artiklar på `sortOrder` → namn. Rad-ID:n är stabila, så att [merge] kan matcha dem.
     */
    fun suggest(
        trip: Trip,
        persons: List<Person>,
        regimensByPerson: Map<String, List<Regimen>>,
        familyItems: List<FamilyItem>,
        settings: HouseholdSettings,
    ): PackingSuggestion {
        val travellers = persons.filter { it.id in trip.travellerIds && !it.archived }
            .sortedWith(bySortOrder<Person>().thenBy(swedish) { it.name }.thenBy { it.id })
        val regimenRows = travellers.flatMap { person ->
            regimensByPerson[person.id].orEmpty()
                .filter { it.active && !it.archived }
                .sortedWith(compareBy<Regimen, String>(swedish) { it.name }.thenBy(swedish) { it.strength }.thenBy { it.id })
                .map { regimen -> regimenItem(person, regimen, regimenLine(regimen, trip, settings)) }
        }
        val familyRows = familyItems
            .filter { it.active && !it.archived }
            .sortedWith(bySortOrder<FamilyItem>().thenBy(swedish) { it.name }.thenBy { it.id })
            .map { item -> familyItem(item, familyLine(item, travellers.size, trip, settings)) }
        // sortedBy är stabil: person- och namnordningen ovan behålls inom varje sektion.
        val rows = (regimenRows + familyRows).sortedBy { (item, _) -> item.section.ordinal }
        val items = rows.mapIndexed { index, (item, _) -> item.copy(sortOrder = index) }
        val warnings = rows.filter { (_, line) -> line.warning }.map { (item, _) -> item.name }
        return PackingSuggestion(items, warnings)
    }

    private fun regimenItem(person: Person, regimen: Regimen, line: PackingLine) = PackingItem(
        id = "regimen-${person.id}-${regimen.id}",
        section = if (regimen.kind == RegimenKind.SUPPLEMENT) PackingSection.SUPPLEMENT else PackingSection.MEDICINE,
        personId = person.id,
        sourceType = PackingSource.REGIMEN,
        sourceId = regimen.id,
        name = listOf(regimen.name, regimen.strength).filter { it.isNotBlank() }.joinToString(" "),
        unit = regimen.unit,
        suggestedQuantity = line.quantity,
        quantity = line.quantity,
        packages = line.packages,
        reason = line.reason,
    ) to line

    private fun familyItem(item: FamilyItem, line: PackingLine) = PackingItem(
        id = "family-${item.id}",
        section = PackingSection.FAMILY,
        sourceType = PackingSource.FAMILY_ITEM,
        sourceId = item.id,
        name = item.name,
        unit = item.unit,
        suggestedQuantity = line.quantity,
        quantity = line.quantity,
        reason = line.reason,
    ) to line

    /**
     * "Räkna om" (RES-4): matchar på (`sourceType`, `sourceId`, `personId`). En omatchad rad
     * får nytt förslag; en manuellt ändrad mängd (med sina förpackningar), `checked`, `checkedAt`
     * och `hidden` behålls. Rader som försvunnit ur förslaget – och dubbletter av samma nyckel –
     * tas bort om de inte är avprickade; avprickade behålls och får förklaringen
     * [Texts.NO_LONGER_SUGGESTED]. Egna (`MANUAL`) rader rörs aldrig. Resultatet har entydig
     * ordning: förslagets rader, sedan kvarvarande avprickade, sedan egna rader.
     */
    fun merge(existing: List<PackingItem>, suggestion: PackingSuggestion): List<PackingItem> {
        fun key(item: PackingItem) = Triple(item.sourceType, item.sourceId, item.personId)
        val (manual, generated) = existing.partition { it.sourceType == PackingSource.MANUAL }
        val byKey = generated.groupBy(::key)
        val used = mutableSetOf<String>()
        val updated = suggestion.items.map { suggested ->
            val old = byKey[key(suggested)]?.first() ?: return@map suggested
            used += old.id
            val manuallyChanged = old.quantity != old.suggestedQuantity
            suggested.copy(
                id = old.id,
                quantity = if (manuallyChanged) old.quantity else suggested.quantity,
                // Förpackningarna hör till mängden: en manuell mängd behåller radens egna.
                packages = if (manuallyChanged) old.packages else suggested.packages,
                checked = old.checked,
                checkedAt = old.checkedAt,
                hidden = old.hidden,
            )
        }
        val keptChecked = generated.filter { it.id !in used && it.checked }.map { it.copy(reason = Texts.NO_LONGER_SUGGESTED) }
        return (updated + keptChecked + manual.sortedWith(Sortable.ORDER))
            .mapIndexed { index, item -> item.copy(sortOrder = index) }
    }

    /** Namn sorteras i svensk ordning: … x, y, z, å, ä, ö – inte på teckenkod. */
    private val swedish: Comparator<String> = Collator.getInstance(Locale.forLanguageTag("sv-SE")).let { collator ->
        Comparator { a, b -> collator.compare(a, b) }
    }

    /** [Sortable.ORDER] för en konkret typ, så att den kan kedjas med namn och ID. */
    private fun <T : Sortable> bySortOrder(): Comparator<T> = Comparator { a, b -> Sortable.ORDER.compare(a, b) }

    /** Uppåt avrundad division utan överlopp; en nämnare under 1 räknas som 1. */
    internal fun ceilDiv(a: Int, b: Int): Int = if (a <= 0) 0 else (a - 1) / b.coerceAtLeast(1) + 1

    /** Produkt av antal, där negativa räknas som 0 och resultatet begränsas till [MAX_QUANTITY]. */
    private fun product(vararg factors: Int): Int =
        factors.fold(1L) { acc, factor -> (acc * factor.coerceAtLeast(0)).coerceAtMost(MAX_QUANTITY.toLong()) }.toInt()
}
