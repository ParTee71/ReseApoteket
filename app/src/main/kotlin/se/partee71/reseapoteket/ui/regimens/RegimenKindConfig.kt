package se.partee71.reseapoteket.ui.regimens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.MedicineForm
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.ui.theme.Tone

/**
 * Det enda som skiljer recept och kosttillskott åt i GUI:t: rubrik, ikon, färg, texter och
 * former. Skärmarna är generiska över [RegimenKind] och läser allt sort-specifikt härifrån –
 * inga `if (kind == …)` på andra ställen.
 */
data class RegimenKindConfig(
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    val tone: Tone,
    @param:StringRes val none: Int,
    @param:StringRes val add: Int,
    /** Formulärets texter och former; `null` = formuläret finns inte än och visar "Kommer snart". */
    val editor: RegimenEditorConfig?,
) {
    companion object {
        fun of(kind: RegimenKind): RegimenKindConfig = when (kind) {
            RegimenKind.MEDICINE -> MEDICINE
            RegimenKind.SUPPLEMENT -> SUPPLEMENT
        }

        private val MEDICINE = RegimenKindConfig(
            title = R.string.person_medicines_title,
            icon = R.drawable.ic_medication,
            tone = Tone.Primary,
            none = R.string.person_medicines_none,
            add = R.string.person_medicines_add,
            editor = RegimenEditorConfig(
                newTitle = R.string.regimen_medicine_new,
                editTitle = R.string.regimen_medicine_edit,
                deleteMessage = R.string.regimen_medicine_delete_message,
                forms = MedicineForm.entries,
            ),
        )

        // Formuläret för kosttillskott, med egna former och snabbval, kommer i #23.
        private val SUPPLEMENT = RegimenKindConfig(
            title = R.string.person_supplements_title,
            icon = R.drawable.ic_eco,
            tone = Tone.Green,
            none = R.string.person_supplements_none,
            add = R.string.person_supplements_add,
            editor = null,
        )
    }
}

/** Formulärets sort-specifika delar (REC-1). */
data class RegimenEditorConfig(
    @param:StringRes val newTitle: Int,
    @param:StringRes val editTitle: Int,
    @param:StringRes val deleteMessage: Int,
    val forms: List<MedicineForm>,
)

/** Formens namn i formuläret: "Tablett", "Flytande". */
@StringRes
fun MedicineForm.label(): Int = when (this) {
    MedicineForm.TABLET -> R.string.form_tablet
    MedicineForm.CAPSULE -> R.string.form_capsule
    MedicineForm.LIQUID -> R.string.form_liquid
    MedicineForm.INHALER -> R.string.form_inhaler
    MedicineForm.DROPS -> R.string.form_drops
    MedicineForm.PATCH -> R.string.form_patch
    MedicineForm.OTHER -> R.string.form_other
}
