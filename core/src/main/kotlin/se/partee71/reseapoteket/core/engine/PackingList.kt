package se.partee71.reseapoteket.core.engine

import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Sortable
import se.partee71.reseapoteket.core.model.TripStatus

/** Packlistans ordning och resans status (PAK-1, PAK-2, PAK-5) – rena funktioner. */
object PackingList {

    /** Raderna som visas, i ordning, och de dolda (PAK-4). */
    data class Arranged(val visible: List<PackingItem>, val hidden: List<PackingItem>)

    /**
     * Ordningen i listan: sektion (Mediciner, Kosttillskott, Övrigt) → person (som i
     * packförslaget: `sortOrder`, sedan namn i svensk ordning; okända personer och rader utan
     * person sist) → avprickade sist om [checkedLast] (SET-3) → radens `sortOrder`. Dolda rader
     * står för sig, i `sortOrder`.
     */
    fun arrange(items: List<PackingItem>, persons: List<Person>, checkedLast: Boolean): Arranged {
        val rank = persons
            .sortedWith(Comparator<Person> { a, b -> Sortable.ORDER.compare(a, b) }.thenBy(SwedishOrder.names) { it.name }.thenBy { it.id })
            .mapIndexed { index, person -> person.id to index }
            .toMap()
        fun personRank(item: PackingItem): Int = item.personId?.let { rank[it] ?: rank.size } ?: (rank.size + 1)
        val order = compareBy<PackingItem> { it.section.ordinal }
            .thenBy(::personRank)
            .thenBy { if (checkedLast) it.checked else false }
            .thenBy { it.sortOrder }
            .thenBy { it.id }
        val (hidden, visible) = items.partition { it.hidden }
        return Arranged(visible.sortedWith(order), hidden.sortedWith(compareBy<PackingItem> { it.sortOrder }.thenBy { it.id }))
    }

    /** En ändrad mängd: inte samma som förslaget (egna rader har inget förslag att ändra). */
    fun isChanged(item: PackingItem): Boolean = item.sourceType != PackingSource.MANUAL && item.quantity != item.suggestedQuantity

    /**
     * Mängden på en rad (PAK-2): "18 tabletter · 2 kartor". Förpackningarna hör till förslaget och
     * visas bara när mängden inte är ändrad (PAK-3).
     */
    fun amount(item: PackingItem): String {
        val packages = item.packages?.takeIf { it > 0 && !isChanged(item) }
        return listOfNotNull(UnitNames.quantity(item.quantity, item.unit), packages?.let { UnitNames.packages(it, item.unit) }).joinToString(" · ")
    }

    /**
     * Status efter en ändring i listan (PAK-5): allt packat → [TripStatus.PACKED]; en packad resa
     * där något inte längre är packat → [TripStatus.PLANNING]. En klar resa ändras inte, och en
     * lista utan synliga rader ändrar ingenting: den räknas inte som packad, och att dölja allt
     * packat packar inte upp resan.
     */
    fun statusAfter(current: TripStatus, progress: PackingProgress): TripStatus = when {
        current == TripStatus.DONE || progress.total == 0 -> current
        progress.isComplete -> TripStatus.PACKED
        current == TripStatus.PACKED -> TripStatus.PLANNING
        else -> current
    }
}
