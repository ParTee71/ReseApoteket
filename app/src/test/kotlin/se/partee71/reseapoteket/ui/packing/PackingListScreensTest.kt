package se.partee71.reseapoteket.ui.packing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.robolectric.annotation.Config
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.core.engine.PackingEngine
import se.partee71.reseapoteket.core.engine.PackingGroup
import se.partee71.reseapoteket.core.engine.PackingList
import se.partee71.reseapoteket.core.engine.PackingProgress
import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.PackingItem
import se.partee71.reseapoteket.core.model.PackingSection
import se.partee71.reseapoteket.core.model.PackingSource
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.model.TripStatus
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.testing.captureScreenLightAndDark
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.common.EditorUiState
import se.partee71.reseapoteket.ui.common.ListEvent
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.components.ListArchive
import se.partee71.reseapoteket.ui.runListScreenContract
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

/** Packlistan (PAK-1–5): listkontraktet, det som är unikt för packlistan och skärmdumpar. */
@RunWith(RobolectricTestRunner::class)
class PackingListScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val anna = Person("anna", "Anna", "🦊", AppColors.PERSON_HEX[0])
    private val erik = Person("erik", "Erik", "🐻", AppColors.PERSON_HEX[1])
    private val lova = Person("lova", "Lova", "🐰", AppColors.PERSON_HEX[2])
    private val gunnar = Person("gunnar", "Morfar Gunnar", "🦉", AppColors.PERSON_HEX[3])
    private val trip = Trip("t1", "Gran Canaria", LocalDate(2026, 10, 2), 7, listOf("anna", "erik", "lova", "gunnar"))

    private fun med(id: String, person: Person, name: String, quantity: Int, unit: ItemUnit = ItemUnit.TABLETT, packages: Int? = null, checked: Boolean = false, suggested: Int = quantity, section: PackingSection = PackingSection.MEDICINE) =
        PackingItem(id, section, person.id, PackingSource.REGIMEN, id, name, unit, suggested, quantity, packages, "1 × 1 tablett/dag × (7 + 2) dagar", checked)

    private fun other(id: String, name: String, quantity: Int, unit: ItemUnit, checked: Boolean = false, hidden: Boolean = false, manual: Boolean = false) =
        PackingItem(id, PackingSection.FAMILY, null, if (manual) PackingSource.MANUAL else PackingSource.FAMILY_ITEM, id, name, unit, quantity, quantity, checked = checked, hidden = hidden)

    private val rows = listOf(
        PackingRow(med("levaxin", anna, "Levaxin 50 µg", 9, packages = 1, checked = true), anna, "1/dag"),
        PackingRow(med("zyrtec", anna, "Zyrtec 10 mg", 9, checked = true), anna, "1/dag"),
        PackingRow(med("enalapril", erik, "Enalapril 10 mg", 18, packages = 2), erik, "2/dag"),
        PackingRow(med("ventoline", erik, "Ventoline", 1, ItemUnit.ST, checked = true), erik, "vid behov"),
        PackingRow(med("alvedon", lova, "Alvedon 250 mg", 14, suggested = 10), lova, "vid behov"),
        PackingRow(med("metformin", gunnar, "Metformin 500 mg", 18), gunnar, "2/dag"),
        PackingRow(med("d", anna, "D-vitamin", 9, ItemUnit.KAPSEL, checked = true, section = PackingSection.SUPPLEMENT), anna, "1/dag"),
        PackingRow(med("omega", gunnar, "Omega-3", 9, ItemUnit.KAPSEL, section = PackingSection.SUPPLEMENT), gunnar, "1/dag"),
        PackingRow(other("solkram", "Solkräm", 2, ItemUnit.TUB, checked = true)),
        PackingRow(other("termometer", "Febertermometer", 1, ItemUnit.ST)),
        PackingRow(other("hydro", "Hydrokortison", 1, ItemUnit.TUB, checked = true)),
        PackingRow(other("laddare", "Laddare", 1, ItemUnit.ST, manual = true)),
        PackingRow(other("plaster", "Plåster", 1, ItemUnit.ST, hidden = true)),
    )

    private fun header(of: List<PackingRow>, status: TripStatus = TripStatus.PLANNING): PackingHeader {
        val items = of.map { it.item }
        val shown = items.filterNot { it.hidden }
        return PackingHeader(
            trip.copy(status = status),
            listOf(anna, erik, lova, gunnar),
            PackingEngine.progress(items),
            PackingGroup.entries.associateWith { group -> PackingEngine.progress(shown.filter { PackingList.group(it) == group }) },
        )
    }

    private val allChecked = rows.map { it.copy(item = it.item.copy(checked = true)) }

    @Test
    fun `packlistan uppfyller listkontraktet`() =
        rule.runListScreenContract(rows.first(), "Levaxin 50 µg", "Inget att packa än", "Lägg till") { state, onAdd, onRetry ->
            PackingListContent(state, header(rows), { if (it == PackingListEvent.Add) onAdd() else if (it == PackingListEvent.List(ListEvent.Retry)) onRetry() }, {}, {})
        }

    @Test
    fun `krysset prickar av, raden öppnar mängden och svep döljer (PAK-2–4)`() {
        val events = mutableListOf<PackingListEvent>()
        val hidden = mutableListOf<ArchiveEvent>()
        rule.setContent {
            ReseApoteketTheme {
                PackingListContent(ListUiState.Content(rows), header(rows), { events += it }, {}, {}, archive = ListArchive(showToggle = false) { hidden += it })
            }
        }
        rule.onNodeWithContentDescription("Enalapril 10 mg, 18 tabletter · 2 kartor").performClick()
        rule.onNodeWithText("Enalapril 10 mg").performClick()
        rule.onNodeWithText("Zyrtec 10 mg").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf(PackingListEvent.Check("enalapril", true), PackingListEvent.Open("enalapril")), events)
        assertEquals(listOf<ArchiveEvent>(ArchiveEvent.Archive("zyrtec", "Zyrtec 10 mg")), hidden)
    }

    @Test
    fun `Dolda är hopfälld tills rubriken trycks, och en dold rad visas igen (PAK-4)`() {
        val events = mutableListOf<PackingListEvent>()
        rule.setContent { ReseApoteketTheme { PackingListContent(ListUiState.Content(rows), header(rows), { events += it }, {}, {}) } }
        rule.onNodeWithText("Plåster").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Dolda"))
        rule.onNodeWithText("Dolda").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Visa igen"))
        // Semantisk klick: raden kan ligga under "Lägg till"-knappen längst ned.
        rule.onNodeWithText("Visa igen").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf<PackingListEvent>(PackingListEvent.Show("plaster")), events)
    }

    @Test
    fun `mängden har förslaget, Återställ och Klar (PAK-3)`() {
        val events = mutableListOf<PackingListEvent>()
        rule.setContent { ReseApoteketTheme { PackingListContent(ListUiState.Content(rows), header(rows), { events += it }, {}, {}, editing = rows[4]) } }
        rule.onNodeWithText("Förslag").assertIsDisplayed()
        rule.onNodeWithText("10 tabletter").assertIsDisplayed()
        rule.onNodeWithText("Återställ till förslag (10)").performClick()
        rule.onNodeWithText("Klar").performClick()
        assertEquals(listOf<PackingListEvent>(PackingListEvent.ResetQuantity, PackingListEvent.CloseQuantity), events)
    }

    @Test
    fun `ett skrivfel visas i panelen`() {
        rule.setContent { ReseApoteketTheme { PackingListContent(ListUiState.Content(rows), header(rows), {}, {}, {}, editing = rows[4], sheetError = DataError.Offline) } }
        rule.onNodeWithText("Ingen anslutning", substring = true).assertIsDisplayed()
    }

    @Test
    fun `lägg till frågar vem för en medicin (PAK-4)`() {
        val events = mutableListOf<PackingListEvent>()
        val adding = EditorUiState(PackingItem("ny", PackingSection.MEDICINE, name = "Nässpray", quantity = 1, sourceType = PackingSource.MANUAL), isValid = false, isDirty = true)
        rule.setContent { ReseApoteketTheme { PackingListContent(ListUiState.Content(rows), header(rows), { events += it }, {}, {}, adding = adding) } }
        rule.onNodeWithText("Vem?", ignoreCase = true).assertIsDisplayed()
        rule.onAllNodesWithText("Erik").filterToOne(hasClickAction()).performClick()
        assertEquals(listOf<PackingListEvent>(PackingListEvent.AddPerson("erik")), events)
    }

    @Test
    fun `pennan öppnar resan och Räkna om frågar först`() {
        var edits = 0
        val events = mutableListOf<PackingListEvent>()
        rule.setContent { ReseApoteketTheme { PackingListContent(ListUiState.Content(rows), header(rows), { events += it }, {}, { edits++ }, askRecount = true) } }
        rule.onNodeWithContentDescription("Redigera resa").performClick()
        rule.onNodeWithText("Räkna om packlistan?").assertIsDisplayed()
        rule.onNodeWithText("Räkna om").performClick()
        assertEquals(1, edits)
        assertEquals(listOf<PackingListEvent>(PackingListEvent.Recount), events)
    }

    @Test
    @Config(qualifiers = "w390dp-h1300dp-xxhdpi")
    fun `skärmdump - På resedagen sist i listan med tiderna (PAK-6)`() {
        val travelDay = listOf(
            rows[0],
            rows[2],
            PackingRow(med("insulin", anna, "Insulin NovoRapid", 1, ItemUnit.ST).copy(travelDay = true), anna),
            PackingRow(other("tandborste", "Tandborste", 4, ItemUnit.ST).copy(travelDay = true)),
            PackingRow(other("laddare", "Laddare", 2, ItemUnit.ST, manual = true).copy(travelDay = true, travelDayTime = LocalTime(8, 30))),
        )
        captureLightAndDark("PackingListScreen_resedag") { PackingListContent(ListUiState.Content(travelDay), header(travelDay), {}, {}, {}) }
    }

    @Test
    fun `skärmdumpar - delvis, allt packat och nyskapad`() {
        captureLightAndDark("PackingListScreen_delvis") { PackingListContent(ListUiState.Content(rows), header(rows), {}, {}, {}) }
        captureLightAndDark("PackingListScreen_klar") {
            PackingListContent(ListUiState.Content(allChecked), header(allChecked, TripStatus.PACKED), {}, {}, {})
        }
        val other = rows.filter { it.item.section == PackingSection.FAMILY }
        captureLightAndDark("PackingListScreen_ovrigt") { PackingListContent(ListUiState.Content(other), header(other), {}, {}, {}) }
        val fresh = rows.map { it.copy(item = it.item.copy(checked = false, hidden = false, quantity = it.item.suggestedQuantity)) }
        captureLightAndDark("PackingListScreen_ny") { PackingListContent(ListUiState.Content(fresh), header(fresh), {}, {}, {}) }
    }

    @Test
    fun `skärmdump - ändra mängd`() = rule.captureScreenLightAndDark("PackingListScreen_mangd") {
        PackingListContent(ListUiState.Content(rows), header(rows), {}, {}, {}, editing = rows[4])
    }

    @Test
    fun `skärmdump - lägg till`() = rule.captureScreenLightAndDark("PackingListScreen_lagg_till") {
        PackingListContent(
            ListUiState.Content(rows),
            header(rows),
            {},
            {},
            {},
            adding = EditorUiState(PackingItem("ny", PackingSection.MEDICINE, "erik", PackingSource.MANUAL, name = "Nässpray", quantity = 1), isValid = true, isDirty = true),
        )
    }

    @Test
    fun `packat visas i rubriken`() {
        rule.setContent { ReseApoteketTheme { PackingListContent(ListUiState.Content(rows), header(rows), {}, {}, {}) } }
        assertEquals(PackingProgress(6, 12), header(rows).progress)
        rule.onNodeWithContentDescription("6 av 12 packat").assertIsDisplayed()
    }
}
