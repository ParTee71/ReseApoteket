package se.partee71.reseapoteket.ui.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import se.partee71.reseapoteket.data.common.DataError

/** Tillståndet för varje listskärm – samma fyra lägen överallt (NFR-1, `EntityListScreen`). */
sealed interface ListUiState<out T> {
    data object Loading : ListUiState<Nothing>

    data object Empty : ListUiState<Nothing>

    data class Content<T>(val items: List<T>) : ListUiState<T>

    data class Error(val error: DataError) : ListUiState<Nothing>
}

/** Den enda översättningen från en datalista till [ListUiState]. */
fun <T> Flow<List<T>>.asListUiState(): Flow<ListUiState<T>> =
    map<List<T>, ListUiState<T>> { items -> if (items.isEmpty()) ListUiState.Empty else ListUiState.Content(items) }
        .onStart { emit(ListUiState.Loading) }
        .catch { emit(ListUiState.Error(it as? DataError ?: DataError.Unknown)) }
