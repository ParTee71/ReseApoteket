package se.partee71.reseapoteket.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.Archivable
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.ui.components.ListArchive
import se.partee71.reseapoteket.ui.components.UndoRequest

/** Arkivera med svep, Ångra och "Visa arkiverade" – samma händelser i alla listor (NFR-3). */
sealed interface ArchiveEvent {
    data class Archive(val id: String, val name: String) : ArchiveEvent

    data object Undo : ArchiveEvent

    data object UndoDismissed : ArchiveEvent

    data object ToggleArchived : ArchiveEvent

    data object ErrorShown : ArchiveEvent
}

/**
 * Den enda arkiveringslogiken för en listskärms ViewModel (skill shared-ui-components): vad
 * `SwipeToHide`, `UndoSnackbar` och "Visa arkiverade" i `EntityListScreen` behöver. [setArchived]
 * skriver; ett fel visas som meddelande ([error]) i stället för att tappas. Samma mekanik för
 * att dölja en rad i packlistan: [setArchived] sätter då `hidden` och [undoFormat] är "%s dold". ViewModeln exponerar
 * den som `archive`, och skärmen skickar `archive.collectAsListArchive()` till listan. Samma Ångra kan
 * också erbjudas för något annat än arkivering ([offerUndo]), t.ex. rader som just lagts till (PAK-7).
 */
class ArchiveActions(
    private val scope: CoroutineScope,
    private val setArchived: suspend (id: String, archived: Boolean) -> Result<Unit>,
    @param:StringRes private val undoFormat: Int = R.string.archived_format
) {
    private val _showingArchived = MutableStateFlow(false)
    val showingArchived: StateFlow<Boolean> = _showingArchived.asStateFlow()

    private val _undo = MutableStateFlow<UndoRequest?>(null)
    val undo: StateFlow<UndoRequest?> = _undo.asStateFlow()

    /** Vad Ångra gör när det inte är att arkivera tillbaka ([offerUndo]); `null` = arkivera tillbaka. */
    private var undoAction: (suspend () -> Result<Unit>)? = null

    /** Ett misslyckat arkivera/ångra, tills listan visat det. */
    private val _error = MutableStateFlow<DataError?>(null)
    val error: StateFlow<DataError?> = _error.asStateFlow()

    /** Aktiva först; arkiverade sist och bara när de visas. Ordningen i övrigt behålls. */
    fun <T : Archivable> visible(source: Flow<List<T>>): Flow<List<T>> = combine(source, _showingArchived) { all, showArchived -> all.filter { showArchived || !it.archived }.sortedBy { it.archived } }

    /** Ett fel från en annan skrivning i samma lista (t.ex. en avprickning) visas på samma sätt. */
    fun report(error: DataError) {
        _error.value = error
    }

    /** Erbjuder Ångra för något annat än arkivering: [undo] körs om användaren trycker Ångra (NFR-3). */
    fun offerUndo(request: UndoRequest, undo: suspend () -> Result<Unit>) {
        undoAction = undo
        _undo.value = request
    }

    fun onEvent(event: ArchiveEvent) {
        when (event) {
            is ArchiveEvent.Archive -> scope.launch {
                setArchived(event.id, true).dataError()?.let { _error.value = it } ?: run {
                    undoAction = null
                    _undo.value = UndoRequest(event.id, event.name, undoFormat)
                }
            }

            ArchiveEvent.Undo -> {
                val request = _undo.value ?: return
                val action = undoAction
                _undo.value = null
                undoAction = null
                scope.launch { (action?.invoke() ?: setArchived(request.id, false)).dataError()?.let { _error.value = it } }
            }

            ArchiveEvent.UndoDismissed -> {
                _undo.value = null
                undoAction = null
            }

            ArchiveEvent.ToggleArchived -> _showingArchived.value = !_showingArchived.value

            ArchiveEvent.ErrorShown -> _error.value = null
        }
    }
}

/** Tillståndet som listan visar, med händelserna tillbaka hit (NFR-3); [showToggle] = "Visa arkiverade" i menyn. */
@Composable
fun ArchiveActions.collectAsListArchive(showToggle: Boolean = true): ListArchive {
    val showing by showingArchived.collectAsStateWithLifecycle()
    val request by undo.collectAsStateWithLifecycle()
    val failure by error.collectAsStateWithLifecycle()
    return ListArchive(showing, request, failure, showToggle, ::onEvent)
}
