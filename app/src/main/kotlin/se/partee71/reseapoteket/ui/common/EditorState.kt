package se.partee71.reseapoteket.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError

/** Validering av ett formulärvärde: fältnamn → felmeddelande. Tom karta = giltigt. */
fun interface Validator<T> {
    fun validate(value: T): Map<String, Int>
}

/** Det som `EntityEditScreen` visar. [errors] innehåller bara de fel som ska synas. */
@Immutable
data class EditorUiState<T>(
    val value: T,
    val errors: ImmutableMap<String, Int> = persistentMapOf(),
    val isValid: Boolean = true,
    val isDirty: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    /** Det lagrade värdet gick inte att läsa; ramen visar felet och "Försök igen". */
    val loadError: DataError? = null,
    /** Sparat, arkiverat eller raderat: ramen lämnar formuläret och kvitterar med [EditorEvents.onDoneHandled]. */
    val done: Boolean = false,
    /** Sparfelet som ska visas som snackbar; ramen kvitterar med [EditorEvents.onFailureShown]. */
    val failure: DataError? = null,
) {
    /** "Spara" är aktiv först när formuläret är giltigt och ändrat (NFR-2). */
    val canSave: Boolean get() = isValid && isDirty && !saving && !loading && loadError == null

    /** Felet för [field], om det ska visas. */
    @StringRes
    fun errorFor(field: String): Int? = errors[field]
}

/**
 * Ramens kvittering av händelserna i [EditorUiState] ([EditorUiState.done], [EditorUiState.failure]):
 * en händelse står kvar i tillståndet tills den hanterats – den tappas inte när skärmen inte
 * visas, och den upprepas inte vid rotation (skill android-projekt, Flöden).
 */
@Stable
interface EditorEvents {
    fun onDoneHandled()

    fun onFailureShown()

    companion object {
        /** För ett tillstånd utan händelser (förhandsvisning, galleri). */
        val None: EditorEvents = object : EditorEvents {
            override fun onDoneHandled() = Unit

            override fun onFailureShown() = Unit
        }
    }
}

/**
 * Den enda formulärlogiken (skill shared-ui-components): värde, ändrat-läge, validering och
 * sparning. Varje `*EditViewModel` exponerar en [EditorState]; `EntityEditScreen` visar den.
 *
 * - Ett fältfel visas när fältet har ändrats eller efter ett sparförsök – aldrig innan
 *   användaren hunnit skriva.
 * - [loading] = true tills [load] anropats (redigering av något som finns).
 */
class EditorState<T>(initial: T, private val validator: Validator<T>, loading: Boolean = false) : EditorEvents {
    private var original: T = initial
    private val touched = mutableSetOf<String>()
    private var attempted = false
    private val _state = MutableStateFlow(compute(initial, loading = loading))

    val state: StateFlow<EditorUiState<T>> = _state.asStateFlow()

    val value: T get() = _state.value.value

    /** Sätter det lagrade värdet – formuläret blir oändrat och fel nollställs. */
    fun load(value: T) {
        original = value
        touched.clear()
        attempted = false
        show(compute(value, loading = false))
    }

    /**
     * Läser det lagrade värdet med [read] – en gång för alla formulär. Finns det inte, eller går
     * det inte att läsa, visas läsfelet; [prepare] fyller i förval för det som saknas.
     */
    suspend fun loadFrom(read: suspend () -> Result<T?>, prepare: (T) -> T = { it }) {
        read()
            .onSuccess { stored -> if (stored == null) loadFailed(DataError.NotFound) else load(prepare(stored)) }
            .onFailure { loadFailed(it as? DataError ?: DataError.Unknown) }
    }

    /** Det lagrade värdet gick inte att läsa – formuläret visar felet i stället för en evig laddning. */
    fun loadFailed(error: DataError) {
        _state.update { it.copy(loading = false, loadError = error) }
    }

    /** Inför ett nytt läsförsök ("Försök igen"). */
    fun reload() {
        _state.update { it.copy(loading = true, loadError = null) }
    }

    /** Ändrar värdet; [fields] markeras som rörda så att deras fel börjar synas. */
    fun update(vararg fields: String, transform: (T) -> T) {
        touched += fields
        _state.update { compute(transform(it.value), loading = it.loading, saving = it.saving).withEventsOf(it) }
    }

    /**
     * Validerar och sparar med [write]. Ogiltigt → alla fel visas och inget sparas.
     * Lyckat → [EditorUiState.done]; fel → [EditorUiState.failure] och formuläret står kvar.
     */
    suspend fun save(write: suspend (T) -> Result<Unit>) {
        // Ett dubbeltryck sparar inte två gånger, och medan det lagrade värdet laddas (eller inte
        // gick att läsa) finns inget att spara – platshållaren får aldrig skriva över det.
        val now = _state.value
        if (now.saving || now.loading || now.loadError != null) return
        attempted = true
        val current = compute(value, loading = false)
        show(current)
        if (!current.isValid) return
        // Ändrades formuläret medan det sparades är ändringen inte sparad: formuläret står kvar
        // som ändrat i stället för att stängas och tappa den (NFR-2).
        perform(written = current.value, closeIfChanged = false) { write(current.value) }
    }

    /**
     * Kör en åtgärd som stänger formuläret (arkivera, radera) med samma fel- och klarhantering.
     * Den stänger alltid: en ändring under tiden får inte kunna sparas tillbaka över det som
     * arkiverats eller raderats.
     */
    suspend fun run(action: suspend () -> Result<Unit>) = perform(written = value, closeIfChanged = true, action)

    private suspend fun perform(written: T, closeIfChanged: Boolean, action: suspend () -> Result<Unit>) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        val error = action().dataError()
        _state.update { it.copy(saving = false) }
        if (error == null) {
            original = written
            val after = compute(value, loading = false)
            val close = closeIfChanged || !after.isDirty
            _state.update { after.withEventsOf(it).copy(done = close || it.done) }
        } else {
            _state.update { it.copy(failure = error) }
        }
    }

    override fun onDoneHandled() = _state.update { it.copy(done = false) }

    override fun onFailureShown() = _state.update { it.copy(failure = null) }

    /** Ett nytt formulärläge; händelser som inte kvitterats än står kvar. */
    private fun show(next: EditorUiState<T>) = _state.update { next.withEventsOf(it) }

    private fun EditorUiState<T>.withEventsOf(old: EditorUiState<T>) = copy(done = old.done, failure = old.failure)

    private fun compute(value: T, loading: Boolean, saving: Boolean = false): EditorUiState<T> {
        val all = validator.validate(value)
        val visible = if (attempted) all else all.filterKeys { it in touched }
        return EditorUiState(value, visible.toImmutableMap(), all.isEmpty(), value != original, loading, saving)
    }
}
