package se.partee71.reseapoteket.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.core.model.Archivable
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError
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
 * skriver; ett fel visas som meddelande ([error]) i stället för att tappas.
 */
class ArchiveActions(
    private val scope: CoroutineScope,
    private val setArchived: suspend (id: String, archived: Boolean) -> Result<Unit>,
) {
    private val _showingArchived = MutableStateFlow(false)
    val showingArchived: StateFlow<Boolean> = _showingArchived.asStateFlow()

    private val _undo = MutableStateFlow<UndoRequest?>(null)
    val undo: StateFlow<UndoRequest?> = _undo.asStateFlow()

    /** Ett misslyckat arkivera/ångra, tills listan visat det. */
    private val _error = MutableStateFlow<DataError?>(null)
    val error: StateFlow<DataError?> = _error.asStateFlow()

    /** Aktiva först; arkiverade sist och bara när de visas. Ordningen i övrigt behålls. */
    fun <T : Archivable> visible(source: Flow<List<T>>): Flow<List<T>> =
        combine(source, _showingArchived) { all, showArchived -> all.filter { showArchived || !it.archived }.sortedBy { it.archived } }

    fun onEvent(event: ArchiveEvent) {
        when (event) {
            is ArchiveEvent.Archive -> scope.launch {
                setArchived(event.id, true).dataError()?.let { _error.value = it } ?: run { _undo.value = UndoRequest(event.id, event.name) }
            }
            ArchiveEvent.Undo -> {
                val request = _undo.value ?: return
                _undo.value = null
                scope.launch { setArchived(request.id, false).dataError()?.let { _error.value = it } }
            }
            ArchiveEvent.UndoDismissed -> _undo.value = null
            ArchiveEvent.ToggleArchived -> _showingArchived.value = !_showingArchived.value
            ArchiveEvent.ErrorShown -> _error.value = null
        }
    }
}
