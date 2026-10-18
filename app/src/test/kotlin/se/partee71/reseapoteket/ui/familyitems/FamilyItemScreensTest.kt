package se.partee71.reseapoteket.ui.familyitems

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingEngine
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.FamilyItemCategory
import se.partee71.reseapoteket.core.model.HouseholdSettings
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.common.EditorEvents
import se.partee71.reseapoteket.ui.common.EditorState
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.runEditScreenContract
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

/** Familjeartiklarnas skärmar (FAM-1–4): formulärkontraktet och det som är unikt. */
@RunWith(RobolectricTestRunner::class)
class FamilyItemScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val sunscreen = FamilyItem("f1", "Solkräm SPF 50", FamilyItemCategory.CREAM, ItemUnit.TUB, QuantityRule.PerDays(1, 7), "Barnen behöver faktor 50")
    private val list = listOf(
        sunscreen,
        FamilyItem("f2", "Hydrokortison 1 %", FamilyItemCategory.CREAM, ItemUnit.TUB, QuantityRule.Fixed(1)),
        FamilyItem("f3", "Myggmedel", FamilyItemCategory.CREAM, ItemUnit.ST, QuantityRule.PerTraveller(1)),
        FamilyItem("f4", "Plåster", FamilyItemCategory.PLASTER, ItemUnit.ST, QuantityRule.PerTraveller(10)),
        FamilyItem("f5", "Febertermometer", FamilyItemCategory.TOOL, ItemUnit.ST, QuantityRule.Fixed(1)),
        FamilyItem("f6", "Alvedon barn 250 mg", FamilyItemCategory.OTHER, ItemUnit.ST, QuantityRule.Fixed(10), active = false),
    )

    private fun preview(item: FamilyItem) = PackingEngine.familyPreview(item, 4, HouseholdSettings())

    @Test
    fun `redigeringen uppfyller formulärkontraktet`() {
        val editor = EditorState(sunscreen.copy(name = ""), FamilyItemValidator)
        rule.runEditScreenContract(
            editor = editor,
            makeInvalid = { update(FamilyItemValidator.NAME) { it.copy(name = "x") }; update(FamilyItemValidator.NAME) { it.copy(name = "") } },
            makeValid = { update(FamilyItemValidator.NAME) { it.copy(name = "Solkräm") } },
            invalidMessage = "Ange ett namn",
        ) { state, events, onSave, onClose ->
            FamilyItemEditContent(isNew = true, state, preview(state.value), events, { if (it == FamilyItemEditEvent.Save) onSave() }, onClose)
        }
    }

    @Test
    fun `listan visar kategorier, regeltext och typ, och pausade nedtonade (FAM-1, FAM-3)`() {
        rule.setContent { ReseApoteketTheme { FamilyItemsContent(ListUiState.Content(list.toImmutableList()), {}, {}, {}) } }
        rule.onNodeWithText("Krämer & salvor").assertIsDisplayed()
        rule.onNodeWithText("1 tub per 7 dagar").assertIsDisplayed()
        rule.onNodeWithText("1 per resenär").assertIsDisplayed()
        rule.onNodeWithText("Per N dagar").assertIsDisplayed()
        rule.onNodeWithText("Pausat – räknas inte med").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `exemplen i den tomma listan öppnar ett ifyllt formulär (FAM-4)`() {
        val opened = mutableListOf<FamilyItemExample>()
        rule.setContent { ReseApoteketTheme { FamilyItemsContent(ListUiState.Empty, {}, {}, {}, onAddExample = { opened += it }) } }
        rule.onNodeWithText("Solkräm").performClick()
        rule.onNodeWithText("Febertermometer").performClick()
        assertEquals(listOf(FamilyItemExample.SUNSCREEN, FamilyItemExample.THERMOMETER), opened)
    }

    @Test
    fun `en arkiverad artikel kan återställas från formuläret, en aktiv arkiveras (FAM-2)`() {
        val events = mutableListOf<FamilyItemEditEvent>()
        rule.setContent {
            ReseApoteketTheme { FamilyItemEditContent(false, EditorUiState(sunscreen), preview(sunscreen), EditorEvents.None, { events += it }, {}, stored = sunscreen.copy(archived = true)) }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Återställ").performClick()
        assertEquals(listOf<FamilyItemEditEvent>(FamilyItemEditEvent.Restore), events)
    }

    @Test
    fun `utan känt antal personer visas ingen förhandsvisning, och utan lagrad artikel ingen meny`() {
        rule.setContent { ReseApoteketTheme { FamilyItemEditContent(false, EditorUiState(sunscreen), null, EditorEvents.None, {}, {}) } }
        rule.onNodeWithText("För 4 resenärer i 7 dagar").assertDoesNotExist()
        rule.onNodeWithContentDescription("Fler val").assertDoesNotExist()
    }

    @Test
    fun `mängdfelet syns under regeln`() {
        val zero = sunscreen.copy(rule = QuantityRule.PerDays(1, 0))
        val state = EditorUiState(zero, errors = persistentMapOf(FamilyItemValidator.RULE to R.string.rule_min_one))
        rule.setContent { ReseApoteketTheme { FamilyItemEditContent(false, state, preview(zero), EditorEvents.None, {}, {}) } }
        rule.onNodeWithText("Minst 1").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h1400dp-xxhdpi")
    fun `skärmdumpar - formuläret i tre regler`() {
        fun capture(name: String, item: FamilyItem) = captureLightAndDark("FamilyItemEditScreen_$name") {
            FamilyItemEditContent(false, EditorUiState(item, isDirty = true), preview(item), EditorEvents.None, {}, {}, stored = item)
        }
        capture("per_n_dagar", sunscreen)
        capture("per_resenar", list[3])
        capture("fast_antal", list[4])
    }

    @Test
    fun `skärmdumpar - listan`() {
        captureLightAndDark("FamilyItemsScreen_innehall") { FamilyItemsContent(ListUiState.Content(list.toImmutableList()), {}, {}, {}) }
    }
}
