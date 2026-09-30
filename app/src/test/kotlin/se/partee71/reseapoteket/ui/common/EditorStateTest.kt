package se.partee71.reseapoteket.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.data.common.DataError

/** Formulärlogiken bakom `EntityEditScreen` (NFR-2) – testas en gång här, inte per skärm. */
class EditorStateTest {

    private data class Form(val name: String = "", val note: String = "")

    private val validator = Validator<Form> { form -> if (form.name.isBlank()) mapOf(NAME to R.string.error_unknown) else emptyMap() }

    private fun editor(initial: Form = Form()) = EditorState(initial, validator)

    @Test
    fun `ett nytt formulär är oändrat och kan inte sparas, och felet syns inte än`() {
        val state = editor().state.value
        assertFalse(state.isDirty)
        assertFalse(state.isValid)
        assertFalse(state.canSave)
        assertNull(state.errorFor(NAME))
    }

    @Test
    fun `ett fältfel syns först när fältet har ändrats`() {
        val editor = editor()
        editor.update(NOTE) { it.copy(note = "Faktor 50") }
        assertNull(editor.state.value.errorFor(NAME))
        editor.update(NAME) { it.copy(name = "") }
        assertEquals(R.string.error_unknown, editor.state.value.errorFor(NAME))
    }

    @Test
    fun `spara aktiveras när formuläret är giltigt och ändrat`() {
        val editor = editor()
        editor.update(NAME) { it.copy(name = "Solkräm") }
        assertTrue(editor.state.value.canSave)
        editor.update(NAME) { it.copy(name = "") }
        assertFalse(editor.state.value.canSave)
    }

    @Test
    fun `att ändra tillbaka till det sparade gör formuläret oändrat`() {
        val editor = editor(Form("Solkräm"))
        editor.update(NAME) { it.copy(name = "Solkräm 50") }
        editor.update(NAME) { it.copy(name = "Solkräm") }
        assertFalse(editor.state.value.isDirty)
    }

    @Test
    fun `ogiltigt sparförsök visar alla fel och skriver inget`() = runTest {
        val editor = editor()
        var writes = 0
        editor.save { writes++; Result.success(Unit) }
        assertEquals(0, writes)
        assertEquals(R.string.error_unknown, editor.state.value.errorFor(NAME))
    }

    @Test
    fun `lyckad sparning ger Done och ett oändrat formulär`() = runTest {
        val editor = editor()
        editor.update(NAME) { it.copy(name = "Solkräm") }
        editor.effects.test {
            var saved: Form? = null
            editor.save { saved = it; Result.success(Unit) }
            assertEquals(EditorEffect.Done, awaitItem())
            assertEquals(Form("Solkräm"), saved)
        }
        assertFalse(editor.state.value.isDirty)
        assertFalse(editor.state.value.saving)
    }

    @Test
    fun `sparfel ger Failed och formuläret står kvar ändrat`() = runTest {
        val editor = editor()
        editor.update(NAME) { it.copy(name = "Solkräm") }
        editor.effects.test {
            editor.save { Result.failure(DataError.Offline) }
            assertEquals(EditorEffect.Failed(DataError.Offline), awaitItem())
        }
        assertTrue(editor.state.value.isDirty)
        assertTrue(editor.state.value.canSave)
    }

    @Test
    fun `load sätter det lagrade värdet och avslutar laddningen`() {
        val editor = EditorState(Form(), validator, loading = true)
        assertTrue(editor.state.value.loading)
        assertFalse(editor.state.value.canSave)
        editor.update(NAME) { it.copy(name = "") }
        editor.load(Form("Plåster"))
        val state = editor.state.value
        assertFalse(state.loading)
        assertFalse(state.isDirty)
        assertEquals(Form("Plåster"), state.value)
        assertNull(state.errorFor(NAME))
    }

    @Test
    fun `run för arkivera och radera ger Done eller Failed`() = runTest {
        val editor = editor(Form("Solkräm"))
        editor.effects.test {
            editor.run { Result.success(Unit) }
            assertEquals(EditorEffect.Done, awaitItem())
            editor.run { Result.failure(DataError.PermissionDenied) }
            assertEquals(EditorEffect.Failed(DataError.PermissionDenied), awaitItem())
        }
    }

    private companion object {
        const val NAME = "name"
        const val NOTE = "note"
    }
}
