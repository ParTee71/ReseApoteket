package se.partee71.reseapoteket.ui.common

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.data.common.AppErrors
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.testing.MainDispatcherRule
import se.partee71.ui.components.UndoRequest

/** Den delade arkiveringslogiken för listor (NFR-3) – listornas egna tester visar den i sitt sammanhang. */
class ArchiveActionsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val written = mutableListOf<Pair<String, Boolean>>()
    private var result: Result<Unit> = Result.success(Unit)

    private fun actions(undoFormat: Int = R.string.archived_format) = ArchiveActions(
        CoroutineScope(main.dispatcher),
        setArchived = { id, archived ->
            written += id to archived
            result
        },
        undoFormat = undoFormat
    )

    @Test
    fun `arkivera ger Ångra, och Ångra återställer en gång`() = runTest {
        val archive = actions()
        archive.onEvent(ArchiveEvent.Archive("a", "Anna"))
        assertEquals(UndoRequest("a", "Anna"), archive.undo.value)
        archive.onEvent(ArchiveEvent.Undo)
        archive.onEvent(ArchiveEvent.Undo)
        assertNull(archive.undo.value)
        assertEquals(listOf("a" to true, "a" to false), written)
    }

    @Test
    fun `Ångra kan erbjudas för något annat än arkivering och körs en gång (PAK-7)`() = runTest {
        val archive = actions()
        val undone = mutableListOf<String>()
        val request = UndoRequest("tillagt", "6", R.string.packing_added_other)
        archive.offerUndo(request) {
            undone += "tillagt"
            result
        }
        assertEquals(request, archive.undo.value)
        archive.onEvent(ArchiveEvent.Undo)
        archive.onEvent(ArchiveEvent.Undo)
        assertEquals(listOf("tillagt"), undone)
        assertEquals(emptyList(), written, "inget arkiveras tillbaka")
        // Ett fel när det ångras visas som meddelande.
        result = Result.failure(DataError.PermissionDenied)
        archive.offerUndo(request) { result }
        archive.onEvent(ArchiveEvent.Undo)
        assertEquals(DataError.PermissionDenied, archive.error.value?.cause)
    }

    @Test
    fun `en arkivering efter ett eget Ångra ångrar arkiveringen, och avfärdat glömmer åtgärden`() = runTest {
        val archive = actions()
        val undone = mutableListOf<String>()
        archive.offerUndo(UndoRequest("tillagt", "6", R.string.packing_added_other)) { Result.success(Unit).also { undone += "tillagt" } }
        archive.onEvent(ArchiveEvent.Archive("a", "Anna"))
        archive.onEvent(ArchiveEvent.Undo)
        assertEquals(listOf("a" to true, "a" to false), written)
        archive.offerUndo(UndoRequest("tillagt", "6", R.string.packing_added_other)) { Result.success(Unit).also { undone += "tillagt" } }
        archive.onEvent(ArchiveEvent.UndoDismissed)
        archive.onEvent(ArchiveEvent.Undo)
        assertEquals(emptyList(), undone)
    }

    @Test
    fun `ett skrivfel visas som meddelande och ger inget Ångra`() = runTest {
        val archive = actions()
        result = Result.failure(DataError.PermissionDenied)
        archive.onEvent(ArchiveEvent.Archive("a", "Anna"))
        assertEquals(DataError.PermissionDenied, archive.error.value?.cause)
        assertNull(archive.undo.value)
        archive.onEvent(ArchiveEvent.ErrorShown)
        assertNull(archive.error.value)
    }

    @Test
    fun `arkiverade visas sist och bara när de visas`() = runTest {
        val archive = actions()
        val all = flowOf(listOf(Person("a", "Anna", archived = true), Person("b", "Bo")))
        assertEquals(listOf("Bo"), archive.visible(all).first().map { it.name })
        archive.onEvent(ArchiveEvent.ToggleArchived)
        assertTrue(archive.showingArchived.value)
        assertEquals(listOf("Bo", "Anna"), archive.visible(all).first().map { it.name })
        archive.onEvent(ArchiveEvent.ToggleArchived)
        assertFalse(archive.showingArchived.value)
    }

    @Test
    fun `samma mekanik döljer en rad med ett eget meddelande, och andra fel visas likadant`() {
        val hiding = actions(R.string.hidden_format)
        hiding.onEvent(ArchiveEvent.Archive("p", "Plåster"))
        assertEquals(UndoRequest("p", "Plåster", R.string.hidden_format), hiding.undo.value)
        hiding.report(AppErrors.map(DataError.Offline))
        assertEquals(DataError.Offline, hiding.error.value?.cause)
    }
}
