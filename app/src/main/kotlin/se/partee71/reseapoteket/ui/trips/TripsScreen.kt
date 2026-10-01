package se.partee71.reseapoteket.ui.trips

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.data.repository.TripRepository
import se.partee71.reseapoteket.ui.common.ListEvent
import se.partee71.reseapoteket.ui.common.ListLoader
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.common.activeOnly
import se.partee71.reseapoteket.ui.components.EmptyContent
import se.partee71.reseapoteket.ui.components.EntityListScreen
import se.partee71.reseapoteket.ui.components.ItemRow

@HiltViewModel
class TripsViewModel @Inject constructor(trips: TripRepository) : ViewModel() {
    private val loader = ListLoader(trips.observe().activeOnly(), viewModelScope)
    val state = loader.state

    fun onEvent(event: ListEvent) = loader.onEvent(event)
}

/** Fliken Resor – appens startskärm (NAV-2). */
@Composable
fun TripsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TripsContent(state, viewModel::onEvent, onAdd, onOpen)
}

@Composable
fun TripsContent(
    state: ListUiState<Trip>,
    onEvent: (ListEvent) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    EntityListScreen(
        title = stringResource(R.string.tab_trips),
        state = state,
        empty = EmptyContent(
            icon = R.drawable.ic_luggage,
            title = stringResource(R.string.trips_empty_title),
            message = stringResource(R.string.trips_empty_message),
            actionLabel = stringResource(R.string.trips_add),
        ),
        onAdd = onAdd,
        key = Trip::id,
        modifier = modifier,
        onRetry = { onEvent(ListEvent.Retry) },
    ) { trip ->
        ItemRow(
            title = trip.name,
            subtitle = pluralStringResource(R.plurals.trip_days, trip.days, trip.days),
            onClick = { onOpen(trip.id) },
            navigates = true,
        )
    }
}
