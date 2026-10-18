package se.partee71.reseapoteket.ui.regimens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingEngine
import se.partee71.reseapoteket.core.medicine.MedicineCatalog
import se.partee71.reseapoteket.core.medicine.MedicineLookup
import se.partee71.reseapoteket.core.medicine.fillFrom
import se.partee71.reseapoteket.core.medicine.lookup
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.MedicineForm
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.testing.FakeMedicines
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.runEditScreenContract
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

/**
 * Formuläret för recept och kosttillskott (REC-1–5, KOS-1–3): ramens kontrakt för båda sorterna
 * och det som är unikt – sorten styrs bara av `RegimenKindConfig`.
 */
@RunWith(RobolectricTestRunner::class)
class RegimenScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val config = RegimenKindConfig.of(RegimenKind.MEDICINE).editor
    private val supplements = RegimenKindConfig.of(RegimenKind.SUPPLEMENT).editor
    private val today = LocalDate(2026, 10, 1)
    private val levaxin = Regimen(
        "r1", RegimenKind.MEDICINE, "Levaxin", MedicineForm.TABLET, "50 µg", ItemUnit.TABLETT, 100,
        DoseSchedule.Daily(1, 1), "På fastande mage",
    )

    private fun preview(regimen: Regimen) = PackingEngine.regimenPreview(regimen, HouseholdSettings(), today)

    @Test
    fun `receptformuläret uppfyller formulärkontraktet`() = contract(RegimenKind.MEDICINE)

    @Test
    fun `kosttillskottsformuläret uppfyller formulärkontraktet`() = contract(RegimenKind.SUPPLEMENT)

    private fun contract(kind: RegimenKind) {
        val config = RegimenKindConfig.of(kind).editor
        val editor = EditorState(levaxin.copy(kind = kind, name = ""), RegimenValidator)
        rule.runEditScreenContract(
            editor = editor,
            makeInvalid = { update(RegimenValidator.NAME) { it.copy(name = "x") }; update(RegimenValidator.NAME) { it.copy(name = "") } },
            makeValid = { update(RegimenValidator.NAME) { it.copy(name = "Levaxin") } },
            invalidMessage = "Ange ett namn",
        ) { state, effects, onSave, onClose ->
            RegimenEditContent(config, isNew = true, state, preview(state.value), effects, { event ->
                if (event == RegimenEditEvent.Save) onSave()
            }, onClose)
        }
    }

    @Test
    fun `kosttillskott har Dos, sina former och snabbval som fyller i namnet (KOS-1–3)`() {
        val events = mutableListOf<RegimenEditEvent>()
        val new = Regimen("s1", RegimenKind.SUPPLEMENT, form = MedicineForm.TABLET, schedule = DoseSchedule.Daily())
        rule.setContent { ReseApoteketTheme { RegimenEditContent(supplements, true, EditorUiState(new), preview(new), emptyFlow(), { events += it }, {}) } }
        rule.onNodeWithText("Nytt kosttillskott").assertIsDisplayed()
        rule.onNodeWithText("Dos").assertIsDisplayed()
        rule.onNodeWithText("Pulver").assertIsDisplayed()
        rule.onNodeWithText("Inhalator").assertDoesNotExist()
        rule.onNodeWithText("Omega-3").performClick()
        assertEquals(listOf<RegimenEditEvent>(RegimenEditEvent.Name("Omega-3")), events)
    }

    @Test
    fun `snabbval visas bara för något nytt, och en lagrad form utanför listan visas ändå`() {
        val stored = Regimen("s1", RegimenKind.SUPPLEMENT, "D-vitamin", MedicineForm.LIQUID, unit = ItemUnit.ML, schedule = DoseSchedule.Daily())
        // Användaren har redan bytt till Tablett: Flytande, den sparade formen, går att välja igen.
        val edited = stored.copy(form = MedicineForm.TABLET)
        rule.setContent { ReseApoteketTheme { RegimenEditContent(supplements, false, EditorUiState(edited), preview(edited), emptyFlow(), {}, {}, stored = stored) } }
        rule.onNodeWithText("Snabbval").assertDoesNotExist()
        rule.onNodeWithText("Tablett").assertIsSelected()
        rule.onNodeWithText("Flytande").assertIsDisplayed()
    }

    @Test
    fun `recept kan också vara pulver (en form i hela appen)`() {
        rule.setContent { ReseApoteketTheme { RegimenEditContent(config, true, EditorUiState(levaxin), preview(levaxin), emptyFlow(), {}, {}) } }
        rule.onNodeWithText("Pulver").assertIsDisplayed()
        rule.onNodeWithText("Snabbval").assertDoesNotExist()
    }

    @Test
    fun `form och enhet väljs var för sig (variant B)`() {
        val events = mutableListOf<RegimenEditEvent>()
        rule.setContent { ReseApoteketTheme { RegimenEditContent(config, false, EditorUiState(levaxin), preview(levaxin), emptyFlow(), { events += it }, {}) } }
        rule.onNodeWithText("Flytande").performClick()
        rule.onNodeWithText("ml").performScrollTo().performClick()
        assertEquals(listOf(RegimenEditEvent.Form(MedicineForm.LIQUID), RegimenEditEvent.DoseUnit(ItemUnit.ML)), events)
    }

    @Test
    fun `förhandsvisningen och doseringsfelet syns i formuläret (REC-2, REC-4)`() {
        val weekdays = levaxin.copy(schedule = DoseSchedule.Weekdays(emptySet(), 1))
        val state = EditorUiState(weekdays, errors = persistentMapOf(RegimenValidator.SCHEDULE to R.string.regimen_days_required))
        rule.setContent { ReseApoteketTheme { RegimenEditContent(config, false, state, preview(levaxin), emptyFlow(), {}, {}) } }
        rule.onNodeWithText("Välj minst en veckodag").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("För en 7-dagarsresa").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("9 tabletter · 1 karta").assertIsDisplayed()
    }

    @Test
    fun `radering frågar först och nämner det lagrade receptet (REC-5)`() {
        val events = mutableListOf<RegimenEditEvent>()
        // Namnfältet är ändrat men inte sparat: dialogen nämner det lagrade namnet.
        rule.setContent {
            ReseApoteketTheme {
                RegimenEditContent(config, false, EditorUiState(levaxin.copy(name = "")), preview(levaxin), emptyFlow(), { events += it }, {}, stored = levaxin)
            }
        }
        rule.onNodeWithText("Redigera recept").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Radera Levaxin 50 µg?").assertIsDisplayed()
        rule.onNodeWithText("Receptet tas bort för gott. Det går inte att ångra.").assertIsDisplayed()
        assertEquals(emptyList(), events, "inget raderas innan det bekräftats")
    }

    @Test
    fun `ett nytt recept kan inte raderas`() {
        rule.setContent { ReseApoteketTheme { RegimenEditContent(config, true, EditorUiState(levaxin), preview(levaxin), emptyFlow(), {}, {}) } }
        rule.onNodeWithText("Nytt recept").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fler val").assertDoesNotExist()
    }

    // Hög skärm, så att hela formuläret med förhandsvisningen syns i bilden.
    @Test
    @Config(qualifiers = "w390dp-h1500dp-xxhdpi")
    fun `skärmdumpar i alla fyra doseringslägen`() {
        fun capture(name: String, regimen: Regimen) =
            captureLightAndDark("RegimenEditScreen_$name") {
                RegimenEditContent(config, false, EditorUiState(regimen, isDirty = true), preview(regimen), emptyFlow(), {}, {}, stored = levaxin)
            }
        capture("dagligen", levaxin)
        capture(
            "veckodagar",
            levaxin.copy(name = "Enalapril", strength = "10 mg", notes = "", schedule = DoseSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 1)),
        )
        capture("intervall", levaxin.copy(name = "Alendronat", strength = "70 mg", packageSize = 4, notes = "", schedule = DoseSchedule.IntervalDays(7, 1)))
        capture("vid_behov", levaxin.copy(name = "Alvedon", strength = "500 mg", packageSize = 20, notes = "Vid feber eller värk", schedule = DoseSchedule.AsNeeded(20)))
        val omega = Regimen("s1", RegimenKind.SUPPLEMENT, "D-vitamin", MedicineForm.TABLET, "10 µg", ItemUnit.TABLETT, null, DoseSchedule.Daily(1, 1))
        captureLightAndDark("RegimenEditScreen_kosttillskott_nytt") {
            RegimenEditContent(supplements, true, EditorUiState(omega, isDirty = true), preview(omega), emptyFlow(), {}, {})
        }
    }

    private val catalog = MedicineCatalog.parse(FakeMedicines.SAMPLE)
    private val searching = Regimen("n1", name = "alved", form = MedicineForm.TABLET, unit = ItemUnit.TABLETT, schedule = DoseSchedule.Daily())

    @Test
    fun `nytt recept – förslag under namnet, ett tryck väljer läkemedlet (REC-6, REC-7)`() {
        val events = mutableListOf<RegimenEditEvent>()
        val lookup = catalog.lookup(searching, null)
        rule.setContent {
            ReseApoteketTheme { RegimenEditContent(config, true, EditorUiState(searching), preview(searching), emptyFlow(), { events += it }, {}, lookup = lookup) }
        }
        rule.onNodeWithText("Från Läkemedelsverkets lista · fortsätt skriva om ditt inte finns").assertIsDisplayed()
        rule.onNodeWithContentDescription("4 förslag").assertExists()
        rule.onNodeWithText("Alvedon forte 1 g").performClick()
        assertEquals(RegimenEditEvent.PickMedicine(lookup.suggestions[3].entry), events.single())
    }

    @Test
    fun `ingen träff och ifyllt visas under namnet (REC-6, REC-7)`() {
        var lookup by mutableStateOf(MedicineLookup(noMatch = true))
        rule.setContent {
            ReseApoteketTheme { RegimenEditContent(config, true, EditorUiState(searching), preview(searching), emptyFlow(), {}, {}, lookup = lookup) }
        }
        rule.onNodeWithText("Inget i Läkemedelsverkets lista heter så. Namnet sparas som du skrev det.").assertIsDisplayed()
        lookup = MedicineLookup(filled = true)
        rule.onNodeWithText("Inget i Läkemedelsverkets lista heter så. Namnet sparas som du skrev det.").assertDoesNotExist()
        rule.onNodeWithText("Ifyllt från Läkemedelsverket – ändra fritt").assertIsDisplayed()
    }

    @Test
    fun `inga förslag för kosttillskott eller när ett recept redigeras`() {
        val lookup = catalog.lookup(searching, null)
        var kind by mutableStateOf(supplements to true)
        rule.setContent {
            ReseApoteketTheme { RegimenEditContent(kind.first, kind.second, EditorUiState(searching), preview(searching), emptyFlow(), {}, {}, lookup = lookup) }
        }
        rule.onNodeWithText("Alvedon forte 1 g").assertDoesNotExist()
        kind = config to false
        rule.onNodeWithText("Alvedon forte 1 g").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w390dp-h1100dp-xxhdpi")
    fun `skärmdumpar – förslag och ifyllt`() {
        captureLightAndDark("RegimenEditScreen_forslag") {
            RegimenEditContent(config, true, EditorUiState(searching), preview(searching), emptyFlow(), {}, {}, lookup = catalog.lookup(searching, null))
        }
        val alvedon = catalog.entries[1]
        val filled = searching.fillFrom(alvedon)
        captureLightAndDark("RegimenEditScreen_ifyllt") {
            RegimenEditContent(config, true, EditorUiState(filled, isDirty = true), preview(filled), emptyFlow(), {}, {}, lookup = catalog.lookup(filled, alvedon))
        }
    }
}
