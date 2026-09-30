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

enum class RegimenKind { MEDICINE, SUPPLEMENT }

enum class MedicineForm { TABLET, CAPSULE, LIQUID, INHALER, DROPS, PATCH, OTHER }
