package se.partee71.reseapoteket.ui.components

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.QuantityRule
import se.partee71.reseapoteket.data.common.AppErrors
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.testing.captureScreenLightAndDark
import se.partee71.reseapoteket.testing.clickWithoutRipple
import se.partee71.reseapoteket.ui.contracts
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.Green
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme
import se.partee71.ui.common.DetailUiState
import se.partee71.ui.common.EditorEvents
import se.partee71.ui.common.EditorState
import se.partee71.ui.common.EditorUiState
import se.partee71.ui.common.ListUiState
import se.partee71.ui.common.Validator
import se.partee71.ui.components.AddAction
import se.partee71.ui.components.AppCard
import se.partee71.ui.components.AppMenuItem
import se.partee71.ui.components.AppTextField
import se.partee71.ui.components.ArchiveEvent
import se.partee71.ui.components.ConfirmDialog
import se.partee71.ui.components.DeleteAction
import se.partee71.ui.components.DetailHeader
import se.partee71.ui.components.EmptyContent
import se.partee71.ui.components.EntityDetailScreen
import se.partee71.ui.components.EntityEditScreen
import se.partee71.ui.components.EntityListScreen
import se.partee71.ui.components.ItemRow
import se.partee71.ui.components.ListArchive
import se.partee71.ui.components.ListGroup
import se.partee71.ui.components.ListSubgroup
import se.partee71.ui.components.LoadErrorState
import se.partee71.ui.components.SectionHeader
import se.partee71.ui.testing.runEditScreenContract
import se.partee71.ui.testing.runListScreenContract
import se.partee71.ui.theme.Tone

/** Ramarna testas grundligt en gång här; skärmarna kör bara kontrakten (skill testing-strategy). */
@RunWith(RobolectricTestRunner::class)
class FramesTest {

    @get:Rule
    val rule = createComposeRule()

    private val empty = EmptyContent(R.drawable.ic_family_items, "Inga familjeartiklar än", "Solkräm, plåster, febertermometer – saker hela familjen delar.")
    private val add = AddAction("Lägg till", {})
    private val items = listOf("Solkräm" to "Krämer & salvor", "Hydrokortison 1 %" to "Krämer & salvor", "Plåster" to "Plåster & förband")

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun group(item: Pair<String, String>) = ListGroup(item.second, tone = if (item.second.startsWith("Plåster")) Tone.Green else Tone.Primary)

    @Test
    fun `EntityListScreen uppfyller listkontraktet`() = rule.runListScreenContract(contracts, "Solkräm", "Solkräm", empty.title, add.label) { state, onAdd, onRetry ->
        EntityListScreen("Familjeartiklar", state, empty, add.copy(onClick = onAdd), key = { it }, onRetry = onRetry) { ItemRow(it) }
    }

    @Test
    fun `grupperad lista visar en rubrik med antal per grupp`() {
        rule.setContent {
            ReseApoteketTheme {
                EntityListScreen("Familjeartiklar", ListUiState.Content(items.toImmutableList()), empty, add, key = { it.first }, group = ::group) { ItemRow(it.first) }
            }
        }
        rule.onNodeWithText("Krämer & salvor").assertIsDisplayed()
        rule.onNodeWithText("2").assertIsDisplayed()
        rule.onNodeWithText("Plåster & förband").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xxhdpi")
    fun `undergrupper, två kolumner, en hopfälld grupp och rubrik överst`() {
        val rows = listOf("A1" to "a", "A2" to "a", "B1" to "b", "Ö1" to "ö", "Ö2" to "ö", "Ö3" to "ö", "D1" to "d")
        val groups = mapOf(
            "a" to ListGroup("Mediciner", count = "1 / 2"),
            "b" to ListGroup("Mediciner"),
            "ö" to ListGroup("Övrigt", columns = 2),
            "d" to ListGroup("Dolda", collapsible = true)
        )
        rule.setContent {
            ReseApoteketTheme {
                EntityListScreen(
                    "Gran Canaria",
                    ListUiState.Content(rows.toImmutableList()),
                    empty,
                    add,
                    key = { it.first },
                    group = { groups.getValue(if (it.second == "b") "a" else it.second) },
                    subtitle = "7 dagar",
                    header = { ItemRow("Rubrikplats") },
                    subgroup = { row -> ListSubgroup(if (row.second == "b") "Erik" else "Anna", AppColors.person(0)).takeIf { row.second in setOf("a", "b") } },
                    archive = ListArchive(showToggle = false)
                ) { ItemRow(it.first) }
            }
        }
        rule.onNodeWithText("Rubrikplats").assertIsDisplayed()
        rule.onNodeWithText("7 dagar").assertIsDisplayed()
        rule.onNodeWithText("1 / 2").assertIsDisplayed()
        rule.onNodeWithText("Anna").assertIsDisplayed()
        rule.onNodeWithText("Erik").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fler val").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Ö2"))
        val left = rule.onNodeWithText("Ö1").fetchSemanticsNode().positionInRoot
        val right = rule.onNodeWithText("Ö2").fetchSemanticsNode().positionInRoot
        assertEquals(left.y, right.y, "två kolumner: samma rad")
        rule.onNodeWithText("D1").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Dolda"))
        rule.onNodeWithText("Dolda").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("D1"))
        rule.onNodeWithText("D1").assertExists()
    }

    @Test
    fun `på en telefon blir en grupp med två kolumner en kolumn (NFR-20)`() {
        val rows = listOf("Solkräm SPF 50", "Resetabletter mot åksjuka")
        rule.setContent {
            ReseApoteketTheme {
                EntityListScreen("Gran Canaria", ListUiState.Content(rows.toImmutableList()), empty, add, key = { it }, group = { ListGroup("Övrigt", columns = 2) }) { ItemRow(it) }
            }
        }
        val first = rule.onNodeWithText(rows[0]).fetchSemanticsNode().positionInRoot
        val second = rule.onNodeWithText(rows[1]).fetchSemanticsNode().positionInRoot
        assertEquals(first.x, second.x, "en kolumn: samma vänsterkant")
        assertTrue(second.y > first.y, "en kolumn: raderna under varandra")
    }

    @Test
    fun `en utfälld grupp står kvar efter att skärmen återskapats`() {
        val restore = StateRestorationTester(rule)
        restore.setContent {
            ReseApoteketTheme {
                EntityListScreen("Gran Canaria", ListUiState.Content(persistentListOf("A1", "D1")), empty, add, key = { it }, group = { ListGroup(if (it == "D1") "Dolda" else "Övrigt", collapsible = it == "D1") }) { ItemRow(it) }
            }
        }
        rule.onNodeWithText("D1").assertDoesNotExist()
        rule.onNodeWithText("Dolda").performClick()
        rule.onNodeWithText("D1").assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("D1").assertIsDisplayed()
    }

    @Test
    fun `Visa arkiverade finns i menyn där arkivering finns`() {
        var toggles = 0
        rule.setContent {
            ReseApoteketTheme {
                EntityListScreen("Familjeartiklar", ListUiState.Content(persistentListOf("Solkräm")), empty, add, key = { it }, archive = ListArchive { if (it == ArchiveEvent.ToggleArchived) toggles++ }) { ItemRow(it) }
            }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Visa arkiverade").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `EntityEditScreen uppfyller redigeringskontraktet`() {
        val editor = EditorState("", Validator { if (it.isBlank()) mapOf("name" to R.string.error_no_household) else emptyMap() }, AppErrors)
        rule.runEditScreenContract(
            contracts,
            editor = editor,
            makeInvalid = {
                update("name") { "x" }
                update("name") { "" }
            },
            makeValid = { update("name") { "Solkräm" } },
            invalidMessage = string(R.string.error_no_household)
        ) { state, events, onSave, onClose ->
            EntityEditScreen("Ny familjeartikel", state, events, onSave, onClose) {
                AppTextField(state.value, { v -> editor.update("name") { v } }, "Namn", error = state.errorFor("name")?.let { string(it) })
            }
        }
    }

    @Test
    fun `radering i redigeringens meny kräver bekräftelse`() {
        var deleted = 0
        var archived = 0
        rule.setContent {
            ReseApoteketTheme {
                EntityEditScreen(
                    "Solkräm",
                    EditorUiState("Solkräm"),
                    EditorEvents.None,
                    {},
                    {},
                    onArchive = { archived++ },
                    delete = DeleteAction("Radera Solkräm?", "Det går inte att ångra.") { deleted++ }
                ) {}
            }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(0, deleted)
        rule.onNodeWithText("Radera Solkräm?").assertIsDisplayed()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(1, deleted)
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        assertEquals(1, archived)
    }

    @Test
    fun `EntityEditScreen - lyckad sparning går till onSaved, bakåt till onClose`() {
        var saved = 0
        var closed = 0
        val editor = EditorState("Kreta", Validator { emptyMap() }, AppErrors)
        rule.setContent {
            ReseApoteketTheme {
                val state by editor.state.collectAsState()
                EntityEditScreen("Ny resa", state, editor, {}, { closed++ }, onSaved = { saved++ }) {}
            }
        }
        rule.runOnIdle { runBlocking { editor.run { Result.success(Unit) } } }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Tillbaka").performClick()
        assertEquals(1 to 1, saved to closed)
    }

    @Test
    fun `EntityEditScreen kvitterar klar och sparfel – de väntar tills skärmen visas och kommer inte igen efter rotation`() {
        val editor = EditorState("Kreta", Validator { emptyMap() }, AppErrors)
        var saved = 0
        var shown by mutableStateOf(false)
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            ReseApoteketTheme {
                val state by editor.state.collectAsState()
                if (shown) EntityEditScreen("Ny resa", state, editor, {}, {}, onSaved = { saved++ }) {}
            }
        }
        // Ett sparfel medan skärmen inte visas tappas inte: snackbaren kommer när den visas.
        rule.runOnIdle { runBlocking { editor.run { Result.failure(DataError.Offline) } } }
        assertEquals(DataError.Offline, editor.state.value.failure?.cause)
        rule.runOnIdle { shown = true }
        rule.onNodeWithText(string(R.string.error_offline)).assertIsDisplayed()
        assertNull(editor.state.value.failure, "kvitterat när snackbaren startats")

        rule.runOnIdle { runBlocking { editor.run { Result.success(Unit) } } }
        rule.waitForIdle()
        assertEquals(1, saved)
        assertFalse(editor.state.value.done, "kvitterat när skärmen lämnats")

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals(1, saved, "klar upprepas inte efter rotation")
        rule.onNodeWithText(string(R.string.error_offline)).assertDoesNotExist()
    }

    @Test
    fun `ConfirmDialog - nej-knappen och tryck utanför kan betyda olika saker`() {
        var declined = 0
        var dismissed = 0
        rule.setContent {
            ReseApoteketTheme {
                ConfirmDialog("Räkna om packlistan?", "…", "Räkna om", {}, { dismissed++ }, dismissLabel = "Behåll listan", onDecline = { declined++ })
            }
        }
        rule.onNodeWithText("Behåll listan").performClick()
        assertEquals(1 to 0, declined to dismissed)
        // Bakåt (som tryck utanför) stänger bara – "Räkna om packlistan?" sparar då ingenting (RES-4).
        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(1 to 1, declined to dismissed)
    }

    @Test
    fun `EntityEditScreen - arkivera med osparade ändringar frågar först (NFR-2)`() {
        var archived = 0
        rule.setContent {
            ReseApoteketTheme { EntityEditScreen("Solkräm", EditorUiState("Solkräm 50", isValid = true, isDirty = true), EditorEvents.None, {}, {}, onArchive = { archived++ }) {} }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        rule.onNodeWithText("Fortsätt redigera").performClick()
        assertEquals(0, archived)
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        rule.onNodeWithText("Släng").performClick()
        assertEquals(1, archived)
    }

    @Test
    fun `LoadErrorState - något som inte finns längre har inget Försök igen (NFR-5)`() {
        rule.setContent { ReseApoteketTheme { LoadErrorState("Kunde inte hämta uppgifterna", AppErrors.notFound(), {}) } }
        rule.onNodeWithText("Det finns inte längre", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Försök igen").assertDoesNotExist()
    }

    @Test
    fun `EntityEditScreen - Återställ i menyn för något arkiverat`() {
        var restored = 0
        rule.setContent {
            ReseApoteketTheme { EntityEditScreen("Solkräm", EditorUiState("Solkräm"), EditorEvents.None, {}, {}, onRestore = { restored++ }) {} }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").assertDoesNotExist()
        rule.onNodeWithText("Återställ").performClick()
        assertEquals(1, restored)
    }

    @Test
    fun `EntityDetailScreen - lägen, försök igen och meny`() {
        var state by mutableStateOf<DetailUiState<String>>(DetailUiState.Loading)
        var retries = 0
        var edits = 0
        var archived = 0
        rule.setContent {
            ReseApoteketTheme {
                EntityDetailScreen(
                    state,
                    header = { DetailHeader(it, "3 recept") },
                    onBack = {},
                    onEdit = { edits++ },
                    menu = { listOf(AppMenuItem("Arkivera", { archived++ })) },
                    onRetry = { retries++ }
                ) { Text("Recept") }
            }
        }
        rule.onNodeWithContentDescription("Laddar").assertExists()
        rule.onNodeWithContentDescription("Redigera").assertDoesNotExist()
        state = DetailUiState.Error(AppErrors.map(DataError.Offline))
        rule.onNodeWithText("Kunde inte hämta uppgifterna").assertIsDisplayed()
        rule.onNodeWithText("Försök igen").performClick()
        assertEquals(1, retries)
        state = DetailUiState.Content("Anna")
        rule.onNodeWithText("Anna").assertIsDisplayed()
        rule.onNodeWithText("Recept").assertIsDisplayed()
        rule.onNodeWithContentDescription("Redigera").performClick()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        assertEquals(1, edits)
        assertEquals(1, archived)
    }

    @Test
    fun `EntityDetailScreen - innehåll och fel`() {
        captureLightAndDark("EntityDetailScreen_innehall") {
            EntityDetailScreen(
                DetailUiState.Content("Anna"),
                header = { DetailHeader(it, "3 recept · 1 kosttillskott") },
                onBack = {},
                onEdit = {},
                menu = { listOf(AppMenuItem("Arkivera", {})) },
                leading = { PersonAvatar("🦊", AppColors.person(0), size = AvatarSize.Large) }
            ) {
                AppCard {
                    SectionHeader("Recept", icon = R.drawable.ic_medication, count = "1")
                    ItemRow("Levaxin 50 µg", subtitle = "1 tablett dagligen", navigates = true, onClick = {})
                }
            }
        }
        captureLightAndDark("EntityDetailScreen_fel") {
            EntityDetailScreen<String>(DetailUiState.Error(AppErrors.map(DataError.Offline)), header = { DetailHeader(it) }, onBack = {}) {}
        }
        captureLightAndDark("EntityDetailScreen_finns_inte") {
            EntityDetailScreen<String>(DetailUiState.Error(AppErrors.notFound()), header = { DetailHeader(it) }, onBack = {}) {}
        }
    }

    @Test
    fun `EntityListScreen - lägen`() {
        captureLightAndDark("EntityListScreen_laddar") { EntityListScreen<String>("Familjeartiklar", ListUiState.Loading, empty, add, key = { it }) {} }
        captureLightAndDark("EntityListScreen_tom") { EntityListScreen<String>("Familjeartiklar", ListUiState.Empty, empty, add, key = { it }) {} }
        captureLightAndDark("EntityListScreen_fel") { EntityListScreen<String>("Familjeartiklar", ListUiState.Error(AppErrors.map(DataError.Offline)), empty, add, key = { it }) {} }
        captureLightAndDark("EntityListScreen_innehall") {
            EntityListScreen(
                "Familjeartiklar",
                ListUiState.Content(items.toImmutableList()),
                empty,
                add.copy(menu = listOf(AppMenuItem("Visa arkiverade", {}))),
                key = { it.first },
                group = ::group
            ) { ItemRow(it.first, subtitle = "1 tub per 7 dagar", navigates = true, onClick = {}) }
        }
    }

    @Test
    fun `EntityEditScreen - ogiltig och giltig`() {
        captureLightAndDark("EntityEditScreen_ogiltig") {
            EntityEditScreen("Ny familjeartikel", EditorUiState("", persistentMapOf("name" to R.string.error_unknown), isValid = false, isDirty = true), EditorEvents.None, {}, {}) {
                AppTextField("", {}, "Namn", error = "Ange ett namn")
                QuantityRuleRow(QuantityRule.PerDays(1, 7), {}, { if (it == 1) "tub" else "tuber" })
            }
        }
        captureLightAndDark("EntityEditScreen_giltig") {
            EntityEditScreen("Ny familjeartikel", EditorUiState("Solkräm", isValid = true, isDirty = true), EditorEvents.None, {}, {}, onArchive = {}) {
                AppTextField("Solkräm", {}, "Namn")
                PackingPreview("För 4 personer i 10 dagar", "2 tuber", reason = "ceil((10 + 2) / 7) × 1 tub")
            }
        }
    }

    @Test
    fun `EntityEditScreen - släng ändringar`() = rule.captureScreenLightAndDark("EntityEditScreen_slang", open = { onNodeWithContentDescription("Tillbaka").clickWithoutRipple() }) {
        EntityEditScreen("Solkräm", EditorUiState("Solkräm 50", isValid = true, isDirty = true), EditorEvents.None, {}, {}) {
            AppTextField("Solkräm 50", {}, "Namn")
        }
    }
}
