package se.partee71.reseapoteket.core.model

/**
 * `households/{hid}/persons/{pid}/regimens/{rid}` – ett recept eller ett kosttillskott.
 * Platt modell: medicinens uppgifter ligger på receptet, inget separat register.
 */
data class Regimen(
    override val id: String,
    val kind: RegimenKind = RegimenKind.MEDICINE,
    val name: String = "",
    val form: MedicineForm = MedicineForm.OTHER,
    val strength: String = "",
    val unit: ItemUnit = ItemUnit.TABLETT,
    val packageSize: Int? = null,
    // Saknad dosering betyder "okänd", aldrig en gissad mängd; formuläret väljer en typ för nya recept.
    val schedule: DoseSchedule = DoseSchedule.Unknown(),
    val notes: String = "",
    val active: Boolean = true,
    override val sortOrder: Int = 0,
    override val archived: Boolean = false,
) : Identified, Sortable, Archivable

/** "Levaxin 50 µg" – namn och styrka, så som receptet visas i listor och packlistan (PAK-2). */
val Regimen.displayName: String get() = listOf(name, strength).filter { it.isNotBlank() }.joinToString(" ")

/** Recepten (eller kosttillskotten) av sorten [kind] som inte är arkiverade – det som listor och antal visar. */
fun List<Regimen>.listed(kind: RegimenKind): List<Regimen> = filter { it.kind == kind && !it.archived }

enum class RegimenKind { MEDICINE, SUPPLEMENT }

enum class MedicineForm { TABLET, CAPSULE, LIQUID, INHALER, DROPS, PATCH, OTHER }
