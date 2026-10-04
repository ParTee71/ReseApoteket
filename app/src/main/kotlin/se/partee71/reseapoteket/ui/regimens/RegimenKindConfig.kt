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
    /** Formulärets texter, former och snabbval. */
    val editor: RegimenEditorConfig,
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
                strength = R.string.regimen_strength,
                strengthHelper = R.string.regimen_strength_helper,
                forms = MedicineForm.entries,
                medicineLookup = true,
            ),
        )

        private val SUPPLEMENT = RegimenKindConfig(
            title = R.string.person_supplements_title,
            icon = R.drawable.ic_eco,
            tone = Tone.Green,
            none = R.string.person_supplements_none,
            add = R.string.person_supplements_add,
            editor = RegimenEditorConfig(
                newTitle = R.string.regimen_supplement_new,
                editTitle = R.string.regimen_supplement_edit,
                deleteMessage = R.string.regimen_supplement_delete_message,
                strength = R.string.regimen_dose,
                strengthHelper = R.string.regimen_dose_helper,
                forms = listOf(MedicineForm.TABLET, MedicineForm.CAPSULE, MedicineForm.POWDER, MedicineForm.DROPS),
                suggestions = listOf(R.string.suggestion_vitamin_d, R.string.suggestion_omega3, R.string.suggestion_magnesium, R.string.suggestion_probiotics),
            ),
        )
    }
}

/**
 * Formulärets sort-specifika delar (REC-1, KOS-1–3): rubriker, etiketten för styrkan ("Styrka"
 * eller "Dos"), formerna i visningsordning och snabbval som fyller i namnet för något nytt.
 * [medicineLookup]: namnfältet i något nytt föreslår ur Läkemedelsverkets lista (REC-6) –
 * bara recept, kosttillskott finns inte där.
 */
data class RegimenEditorConfig(
    @param:StringRes val newTitle: Int,
    @param:StringRes val editTitle: Int,
    @param:StringRes val deleteMessage: Int,
    @param:StringRes val strength: Int,
    @param:StringRes val strengthHelper: Int,
    val forms: List<MedicineForm>,
    val suggestions: List<Int> = emptyList(),
    val medicineLookup: Boolean = false,
)

/** Formens namn i formuläret: "Tablett", "Flytande". */
@StringRes
fun MedicineForm.label(): Int = when (this) {
    MedicineForm.TABLET -> R.string.form_tablet
    MedicineForm.CAPSULE -> R.string.form_capsule
    MedicineForm.LIQUID -> R.string.form_liquid
    MedicineForm.POWDER -> R.string.form_powder
    MedicineForm.INHALER -> R.string.form_inhaler
    MedicineForm.DROPS -> R.string.form_drops
    MedicineForm.PATCH -> R.string.form_patch
    MedicineForm.OTHER -> R.string.form_other
}
