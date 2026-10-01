package se.partee71.reseapoteket.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import se.partee71.reseapoteket.core.model.Archivable
import se.partee71.reseapoteket.data.common.DataError

/** Tillståndet för varje listskärm – samma fyra lägen överallt (NFR-1, `EntityListScreen`). */
sealed interface ListUiState<out T> {
    data object Loading : ListUiState<Nothing>

    data object Empty : ListUiState<Nothing>

    data class Content<T>(val items: List<T>) : ListUiState<T>

    data class Error(val error: DataError) : ListUiState<Nothing>
}

/**
 * Som [asListUiState], men börjar om från [Loading][ListUiState.Loading] varje gång [retry]
 * emitterar – "Försök igen" i `EntityListScreen`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<List<T>>.asListUiState(retry: Flow<Unit>): Flow<ListUiState<T>> =
    retry.onStart { emit(Unit) }.flatMapLatest { asListUiState() }

/** Den enda översättningen från en datalista till [ListUiState]. */
fun <T> Flow<List<T>>.asListUiState(): Flow<ListUiState<T>> =
    map<List<T>, ListUiState<T>> { items -> if (items.isEmpty()) ListUiState.Empty else ListUiState.Content(items) }
        .onStart { emit(ListUiState.Loading) }
        .catch { emit(ListUiState.Error(it as? DataError ?: DataError.Unknown)) }

/** Händelser som alla listskärmar har gemensamt; skärmens egna händelser läggs i en egen typ. */
sealed interface ListEvent {
    data object Retry : ListEvent
}

/**
 * Listtillståndet för en ViewModel: [state] från [source] och [onEvent] för [ListEvent].
 * Delas av alla listskärmars ViewModels, så att laddning, tomt och fel beter sig likadant.
 */
class ListLoader<T>(source: Flow<List<T>>, scope: CoroutineScope) {
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state: StateFlow<ListUiState<T>> =
        source.asListUiState(retries).stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    fun onEvent(event: ListEvent) {
        when (event) {
            ListEvent.Retry -> retries.tryEmit(Unit)
        }
    }
}

/** Arkiverade dokument visas inte i listorna förrän "Visa arkiverade" finns för samlingen. */
fun <T : Archivable> Flow<List<T>>.activeOnly(): Flow<List<T>> = map { items -> items.filterNot { it.archived } }

/** Hur länge ett flöde lever vidare utan prenumerant – överlever en rotation. */
const val STOP_TIMEOUT_MILLIS = 5_000L
