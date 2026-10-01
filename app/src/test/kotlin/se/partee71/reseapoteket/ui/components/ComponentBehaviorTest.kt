package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

/** Beteende och tillgänglighet i katalogens komponenter – en gång här, inte per skärm. */
@RunWith(RobolectricTestRunner::class)
class ComponentBehaviorTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { ReseApoteketTheme(content = content) }

    private companion object {
        const val SETTLE_MILLIS = 2_000L
    }

    private val tablets: (Int) -> String = { if (it == 1) "tablett" else "tabletter" }

    @Test
    fun `kryssraden växlar med tryck på hela raden och läses som kryssruta`() {
        var checked by mutableStateOf(false)
        show { CheckRow("Enalapril 10 mg", checked, { checked = it }, subtitle = "18 tabletter") }
        rule.onNodeWithText("Enalapril 10 mg").performClick()
        assertTrue(checked)
        rule.onNodeWithText("Enalapril 10 mg")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun `växelraden växlar med tryck på raden`() {
        var on by mutableStateOf(false)
        show { SwitchRow("Flytta avprickade sist", on, { on = it }) }
        rule.onNodeWithText("Flytta avprickade sist").performClick()
        assertTrue(on)
    }

    @Test
    fun `stegaren ökar och minskar inom intervallet och beskriver värdet`() {
        var value by mutableStateOf(1)
        show { QuantityStepper(value, { value = it }, "dagar", range = 1..2) }
        rule.onNodeWithContentDescription("Minska dagar").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Öka dagar").performClick()
        assertEquals(2, value)
        rule.onNodeWithContentDescription("Öka dagar").assertIsNotEnabled()
        rule.onNodeWithContentDescription("2 dagar").assertIsDisplayed()
    }

    @Test
    fun `menyn öppnas från Fler val och kör valet`() {
        var archived = 0
        show { AppMenu(listOf(AppMenuItem("Arkivera", { archived++ }), AppMenuItem("Radera", {}, destructive = true))) }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        assertEquals(1, archived)
        rule.onNodeWithText("Radera").assertDoesNotExist()
    }

    @Test
    fun `split-knappen lägger till och pilen öppnar fler val`() {
        var added = 0
        var recalculated = 0
        show { AddSplitButton("Lägg till", { added++ }, menuItems = listOf(AppMenuItem("Räkna om", { recalculated++ }))) }
        rule.onNodeWithText("Lägg till").performClick()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Räkna om").performClick()
        assertEquals(1 to 1, added to recalculated)
    }

    @Test
    fun `bekräftelsen anropar bekräfta eller avbryt`() {
        var result = ""
        show { ConfirmDialog("Radera Alvedon?", "Det går inte att ångra.", "Radera", { result = "radera" }, { result = "avbryt" }, destructive = true) }
        rule.onNodeWithText("Avbryt").performClick()
        assertEquals("avbryt", result)
        rule.onNodeWithText("Radera").performClick()
        assertEquals("radera", result)
    }

    @Test
    fun `datumfältet öppnar väljaren och OK behåller valt datum`() {
        var date by mutableStateOf<LocalDate?>(LocalDate(2026, 12, 19))
        var changes = 0
        show { DateField("Startdatum", date, { date = it; changes++ }) }
        rule.onNodeWithContentDescription("Startdatum, lör 19 dec 2026").assertIsDisplayed().performClick()
        rule.onNodeWithText("OK").performClick()
        assertEquals(1, changes)
        assertEquals(LocalDate(2026, 12, 19), date)
    }

    @Test
    fun `valchips och segmentval rapporterar valet`() {
        var chip = 0
        var segment by mutableStateOf(2)
        show {
            Column {
                AppFilterChip("Tablett", false, { chip++ })
                AppSegmentedChoice(listOf("Ljust", "Mörkt", "System"), segment, { segment = it })
            }
        }
        rule.onNodeWithText("Tablett").performClick()
        rule.onNodeWithText("Mörkt").performClick()
        assertEquals(1, chip)
        assertEquals(1, segment)
        rule.onNodeWithText("Mörkt").assertIsSelected()
    }

    @Test
    fun `personchipet visar namnet och kan väljas`() {
        var selected by mutableStateOf(false)
        show { PersonChip("Anna", "🦊", AppColors.person(0), selected, { selected = !selected }) }
        rule.onNodeWithText("Anna").performClick()
        assertTrue(selected)
    }

    @Test
    fun `emojivalet väljer en emoji och läses som ett av flera alternativ`() {
        var emoji by mutableStateOf("🦊")
        show { EmojiPicker(emoji, { emoji = it }) }
        rule.onNodeWithContentDescription("🦉").performClick()
        assertEquals("🦉", emoji)
        rule.onNodeWithContentDescription("🦉").assertIsSelected()
        rule.onNodeWithContentDescription("🦉").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `färgvalet väljer en personfärg och läser färgens namn`() {
        var color by mutableStateOf(AppColors.PERSON_HEX[0])
        show { ColorSwatchPicker(color, { color = it }) }
        rule.onNodeWithContentDescription("Färg: lila").performClick()
        assertEquals(AppColors.PERSON_HEX[3], color)
        rule.onNodeWithContentDescription("Färg: lila").assertIsSelected()
    }

    @Test
    fun `färgvalet känner igen en lagrad färg med små bokstäver och har 48 dp tryckytor`() {
        show { ColorSwatchPicker(AppColors.PERSON_HEX[1].lowercase(), {}) }
        rule.onNodeWithContentDescription("Färg: blå").assertIsSelected()
        rule.onNodeWithContentDescription("Färg: blå").assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp)
    }

    @Test
    fun `doseringen byter typ och behåller antal per dos`() {
        var schedule by mutableStateOf<DoseSchedule>(DoseSchedule.Daily(dosesPerDay = 3, unitsPerDose = 2))
        show { DoseScheduleRow(schedule, { schedule = it }, tablets) }
        rule.onNodeWithText("Veckodagar").performClick()
        assertEquals(DoseSchedule.Weekdays(unitsPerDose = 2), schedule)
        rule.onNodeWithText("tor").performClick()
        assertEquals(setOf(DayOfWeek.THURSDAY), (schedule as DoseSchedule.Weekdays).days)
        rule.onNodeWithText("Vid behov").performClick()
        assertEquals(DoseSchedule.AsNeeded(quantity = 1), schedule, "vid behov börjar giltigt, med 1 att ta med")
    }

    @Test
    fun `doseringens stegare stannar där doseringen är giltig (REC-2)`() {
        var schedule by mutableStateOf<DoseSchedule>(DoseSchedule.IntervalDays(2, 1))
        show { DoseScheduleRow(schedule, { schedule = it }, tablets) }
        rule.onNodeWithContentDescription("Minska dagar mellan doserna").assertIsNotEnabled()
        rule.runOnIdle { schedule = DoseSchedule.AsNeeded(1) }
        rule.onNodeWithContentDescription("Minska tablett att ta med").assertIsNotEnabled()
    }

    @Test
    fun `okänd dosering eller regel visas som meddelande och går inte att ändra`() {
        show {
            Column {
                DoseScheduleRow(DoseSchedule.Unknown("framtida"), {}, tablets)
                QuantityRuleRow(QuantityRule.Unknown(), {}, tablets)
            }
        }
        rule.onAllNodes(hasText("Uppdatera appen", substring = true)).assertCountEquals(2)
        rule.onNodeWithText("Dagligen").assertDoesNotExist()
        rule.onNodeWithText("Fast antal").assertDoesNotExist()
    }

    @Test
    fun `mängdregeln byter typ och behåller antalet`() {
        var rule0 by mutableStateOf<QuantityRule>(QuantityRule.Fixed(3))
        show { QuantityRuleRow(rule0, { rule0 = it }, tablets) }
        rule.onNodeWithText("Per N dagar").performClick()
        assertEquals(QuantityRule.PerDays(quantity = 3), rule0)
    }

    @Test
    fun `packprogressen läses som en mening`() {
        show { SuitcaseProgress(9, 14) }
        rule.onNodeWithContentDescription("9 av 14 packat").assertIsDisplayed()
    }

    @Test
    fun `exempel med samma text väljer var sitt objekt`() {
        val picked = mutableListOf<Int>()
        show { ExampleChips(listOf(1, 2), { picked += it }, label = { "Solkräm" }) }
        rule.onAllNodesWithText("Solkräm")[1].performClick()
        assertEquals(listOf(2), picked)
    }

    @Test
    fun `en rad utan svep har ingen arkivera-åtgärd`() {
        show { SwipeToHide(onHide = {}, enabled = false) { ItemRow("Solkräm", it, onClick = {}) } }
        rule.onNodeWithText("Solkräm").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
    }

    @Test
    fun `svep åt vänster arkiverar`() {
        var hidden = 0
        show { SwipeToHide(onHide = { hidden++ }) { ItemRow("Solkräm", it, onClick = {}) } }
        rule.onNodeWithText("Solkräm").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(1, hidden)
    }

    @Test
    fun `en svept rad som står kvar glider tillbaka`() {
        show { SwipeToHide(onHide = {}) { ItemRow("Solkräm", it, onClick = {}) } }
        val x = { rule.onNodeWithText("Solkräm").fetchSemanticsNode().positionInRoot.x }
        val start = x()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Solkräm").performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(RESET_DELAY_MILLIS / 2) // undan, men inte tillbaka än
        assertTrue(x() < start, "raden ska ha svepts åt vänster innan den glider tillbaka")
        rule.mainClock.advanceTimeBy(RESET_DELAY_MILLIS + SETTLE_MILLIS)
        rule.waitForIdle()
        assertEquals(start, x())
    }

    @Test
    fun `arkivera-åtgärden för TalkBack ligger på raden som TalkBack läser`() {
        var hidden = 0
        show { SwipeToHide(onHide = { hidden++ }) { ItemRow("Solkräm", it, subtitle = "1 tub", onClick = {}) } }
        // Den sammanslagna noden är den TalkBack fokuserar; åtgärden ska finnas just där.
        val actions = rule.onNodeWithText("Solkräm").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.single { it.label == "Arkivera" }.action()
        assertEquals(1, hidden)
    }

    @Test
    fun `ikonknappen har sin beskrivning och anropar onClick`() {
        var clicks = 0
        show { AppIconButton(R.drawable.ic_arrow_back, "Tillbaka", { clicks++ }) }
        rule.onNodeWithContentDescription("Tillbaka").performClick()
        assertEquals(1, clicks)
    }
}
