package se.partee71.reseapoteket.ui.components

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.core.schema.TextLimits
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme
import se.partee71.ui.components.AddSplitButton
import se.partee71.ui.components.AppIconButton
import se.partee71.ui.components.AppTopBar
import se.partee71.ui.components.LocalTopBarStatus
import se.partee71.ui.components.SwipeToHide

/** Beteende och tillgänglighet i katalogens komponenter – en gång här, inte per skärm. */
@RunWith(RobolectricTestRunner::class)
class ComponentBehaviorTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { ReseApoteketTheme(content = content) }

    private val tablets: (Int) -> String = { if (it == 1) "tablett" else "tabletter" }

    @Test
    fun `textfältets tangentbord börjar med stor bokstav (NFR-18)`() {
        lateinit var view: View
        show {
            view = LocalView.current
            AppTextField("", {}, "Namn")
        }
        rule.onNodeWithText("Namn").performClick()
        rule.waitForIdle()
        val info = EditorInfo()
        rule.runOnIdle { view.onCreateInputConnection(info) }
        assertTrue(info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0, "inputType=${info.inputType}")
    }

    @Test
    fun `textfältet växer inte förbi taket men en lagrad längre text går att korta (NFR-16)`() {
        var text by mutableStateOf("x".repeat(TextLimits.SHORT))
        var notes by mutableStateOf("y".repeat(TextLimits.LONG + 5))
        show {
            Column {
                AppTextField(text, { text = it }, "Namn")
                AppTextField(notes, { notes = it }, "Anteckning", singleLine = false)
            }
        }
        rule.onNodeWithText("Namn").performTextInput("z")
        assertEquals(TextLimits.SHORT, text.length)
        rule.onNodeWithText("Anteckning").performTextReplacement("y".repeat(TextLimits.LONG + 4))
        assertEquals(TextLimits.LONG + 4, notes.length, "kortare är tillåtet, inget kapas")
        rule.onNodeWithText("Anteckning").performTextReplacement("kort")
        assertEquals("kort", notes)
    }

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
    fun `med egen tryckåtgärd är krysset en egen knapp med namn och mängd`() {
        var checked by mutableStateOf(false)
        var opened = 0
        show { CheckRow("Enalapril 10 mg", checked, { checked = it }, subtitle = "18 tabletter", onClick = { opened++ }, onClickLabel = "Ändra mängd") }
        rule.onNodeWithText("Enalapril 10 mg").performClick()
        assertEquals(1, opened)
        assertFalse(checked)
        rule.onNodeWithContentDescription("Enalapril 10 mg, 18 tabletter")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .performClick()
        assertTrue(checked)
    }

    @Test
    fun `allt packat visar Allt packat i stället för progressen (PAK-5)`() {
        show { SuitcaseProgress(14, 14) }
        rule.onNodeWithContentDescription("Allt packat! 🧳 Trevlig resa · 14 av 14").assertIsDisplayed()
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
        var value by mutableIntStateOf(1)
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
        show { AddSplitButton("Lägg till", { added++ }, menu = listOf(AppMenuItem("Räkna om", { recalculated++ })).asSplitMenu()) }
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
        show {
            DateField("Startdatum", date, {
                date = it
                changes++
            })
        }
        rule.onNodeWithContentDescription("Startdatum, lör 19 dec 2026").assertIsDisplayed().performClick()
        rule.onNodeWithText("OK").performClick()
        assertEquals(1, changes)
        assertEquals(LocalDate(2026, 12, 19), date)
    }

    @Test
    fun `tidsfältet öppnar väljaren och OK behåller vald tid`() {
        var time by mutableStateOf(LocalTime(18, 0))
        var changes = 0
        show {
            TimeField("Tid", time, {
                time = it
                changes++
            })
        }
        rule.onNodeWithContentDescription("Tid, 18:00").assertIsDisplayed().performClick()
        rule.onNodeWithText("OK").performClick()
        assertEquals(1, changes)
        assertEquals(LocalTime(18, 0), time)
        rule.onNodeWithContentDescription("Tid, 18:00").performClick()
        rule.onNodeWithText("Avbryt").performClick()
        assertEquals(1, changes)
    }

    @Test
    fun `förslagsfältet visar förslagen med antal, och ett tryck väljer rätt förslag (REC-6)`() {
        var text by mutableStateOf("alv")
        var picked = -1
        val suggestions = listOf(Suggestion("Alvedon 500 mg", "Filmdragerad tablett", 0..2), Suggestion("Alvedon forte 1 g", "Filmdragerad tablett", 0..2))
        show { SuggestionField(text, { text = it }, "Namn", suggestions.takeIf { text == "alv" }.orEmpty(), { picked = it }, footer = "Från listan") }
        rule.onNodeWithContentDescription("2 förslag").assertExists()
        rule.onNodeWithText("Från listan").assertIsDisplayed()
        rule.onNodeWithText("Alvedon forte 1 g").performClick()
        assertEquals(1, picked)
        text = "Alvedon"
        rule.onNodeWithText("Alvedon forte 1 g").assertDoesNotExist()
        rule.onNodeWithText("Från listan").assertDoesNotExist()
    }

    @Test
    fun `en markering utanför titeln klipps eller ignoreras i stället för att krascha`() {
        show {
            Column {
                ItemRow("Ipren", titleHighlight = 2..40)
                ItemRow("Alvedon", titleHighlight = -3..1)
                ItemRow("Levaxin", titleHighlight = 4..2)
                ItemRow("Zyrtec", titleHighlight = 10..12)
            }
        }
        listOf("Ipren", "Alvedon", "Levaxin", "Zyrtec").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `långtryck på en rad med kapad titel visar hela titeln och undertexten (NFR-20)`() {
        val long = "Febernedsättande för barn – Alvedon oral suspension 24 mg/ml med doseringsspruta"
        var opened = 0
        show {
            Column(Modifier.width(200.dp)) {
                CheckRow(long, false, {}, subtitle = "1 flaska", onClick = { opened++ })
                CheckRow("Plåster", false, {}, subtitle = "1 ask", onClick = { opened++ })
            }
        }
        // Bara raden vars titel kapas har långtrycket – den korta raden är som förut.
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)).assertCountEquals(1)
        rule.onAllNodesWithText(long).assertCountEquals(1)
        rule.onNodeWithText(long).performTouchInput { longClick() }
        rule.onAllNodesWithText(long).assertCountEquals(2)
        rule.onAllNodesWithText("1 flaska").assertCountEquals(2)
        assertEquals(0, opened, "långtrycket öppnar inte raden")
    }

    @Test
    fun `växelrad med egen tryckåtgärd öppnar raden och växlar med växeln`() {
        var on by mutableStateOf(true)
        var opened = 0
        show { SwitchRow("Dags att packa", on, { on = it }, subtitle = "3 dagar före · 18:00", onClick = { opened++ }) }
        rule.onNodeWithText("Dags att packa").performClick()
        assertEquals(1, opened)
        assertTrue(on)
        rule.onNodeWithContentDescription("Dags att packa").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)).performClick()
        assertFalse(on)
        assertEquals(1, opened)
    }

    @Test
    fun `packas på resedagen - växeln, resedagens morgon som förval och egen tid som kan tas bort (PAK-6)`() {
        var travelDay by mutableStateOf(false)
        var time by mutableStateOf<LocalTime?>(null)
        show {
            CompositionLocalProvider(LocalTravelDayMorning provides LocalTime(6, 45)) {
                TravelDayField(travelDay, time, {
                    travelDay = it.on
                    time = it.ownTime
                })
            }
        }
        rule.onNodeWithContentDescription("Påminn kl., 06:45", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Packas på resedagen").performClick()
        assertTrue(travelDay)
        rule.onNodeWithContentDescription("Påminn kl., 06:45", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Använd resedagens morgon").assertDoesNotExist()
        rule.runOnIdle { time = LocalTime(7, 30) }
        rule.onNodeWithContentDescription("Påminn kl., 07:30", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Använd resedagens morgon").performClick()
        assertEquals(null, time)
        assertTrue(travelDay)
    }

    @Test
    fun `valchipset rapporterar valet`() {
        var chip = 0
        show { AppFilterChip("Tablett", false, { chip++ }) }
        rule.onNodeWithText("Tablett").performClick()
        assertEquals(1, chip)
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
    fun `arkivera-åtgärden för TalkBack ligger på raden som TalkBack läser`() {
        var hidden = 0
        show { SwipeToHide(onHide = { hidden++ }) { ItemRow("Solkräm", it, subtitle = "1 tub", onClick = {}) } }
        // Den sammanslagna noden är den TalkBack fokuserar; åtgärden ska finnas just där.
        val actions = rule.onNodeWithText("Solkräm").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.single { it.label == "Arkivera" }.action()
        assertEquals(1, hidden)
    }

    @Test
    fun `en klickbar pill är en knapp med 48 dp tryckyta`() {
        var clicks = 0
        show { InfoPill("Väntar på synk", icon = R.drawable.ic_cloud_upload, onClick = { clicks++ }) }
        rule.onNodeWithText("Väntar på synk")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertTouchHeightIsEqualTo(48.dp)
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `toppraden visar synkindikatorn bara när ändringar väntar (NFR-6)`() {
        var indicator by mutableStateOf(SyncIndicator())
        var clicks = 0
        show {
            CompositionLocalProvider(LocalTopBarStatus provides indicator.topBarStatus()) {
                AppTopBar("Resor") { AppIconButton(R.drawable.ic_more_vert, "Fler val", {}) }
            }
        }
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas").assertDoesNotExist()
        indicator = SyncIndicator(pending = true) { clicks++ }
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .performClick()
        assertEquals(1, clicks)
        rule.onNodeWithContentDescription("Fler val").assertIsDisplayed()
        indicator = SyncIndicator()
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas").assertDoesNotExist()
    }
}
