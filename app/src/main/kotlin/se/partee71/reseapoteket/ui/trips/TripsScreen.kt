package se.partee71.reseapoteket.ui.trips

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.PackingEngine
import se.partee71.reseapoteket.core.engine.PackingProgress
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.model.TripStatus
import se.partee71.reseapoteket.data.repository.PackingRepository
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.TripRepository
import se.partee71.reseapoteket.ui.common.ArchiveActions
import se.partee71.reseapoteket.ui.common.collectAsListArchive
import se.partee71.reseapoteket.ui.common.DateFormat
import se.partee71.reseapoteket.ui.common.ListEvent
import se.partee71.reseapoteket.ui.common.ListLoader
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.components.ListArchive
import se.partee71.reseapoteket.ui.components.AvatarStack
import se.partee71.reseapoteket.ui.components.EmptyContent
import se.partee71.reseapoteket.ui.components.EntityListScreen
import se.partee71.reseapoteket.ui.components.InfoPill
import se.partee71.reseapoteket.ui.components.ItemRow
import se.partee71.reseapoteket.ui.components.StackedAvatar
import se.partee71.reseapoteket.ui.components.SuitcaseProgress
import se.partee71.reseapoteket.ui.components.SwipeToHide
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.reseapoteket.ui.theme.Tone

/** En rad i reselistan: resan, resenärerna och hur mycket som är packat (RES-1). */
data class TripListItem(val trip: Trip, val travellers: List<Person> = emptyList(), val progress: PackingProgress = PackingProgress.NONE)

sealed interface TripsEvent {
    data class List(val event: ListEvent) : TripsEvent
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModel @Inject constructor(
    trips: TripRepository,
    persons: PersonRepository,
    packing: PackingRepository,
) : ViewModel() {
    /** Svep-arkivera, Ångra och "Visa arkiverade" (RES-1) – samma som i alla listor. */
    val archive = ArchiveActions(viewModelScope, trips::setArchived)

    // Senaste start först; resor utan startdatum sist (RES-1). Arkiverade sist och bara när de visas.
    // Packlistorna lyssnas på per uppsättning resor, inte om vid varje ändring av en resa.
    private val items: Flow<List<TripListItem>> = archive.visible(trips.observe()).map { it.sortedWith(NEWEST_FIRST) }.let { list ->
        val progress = list.map { all -> all.map { it.id } }.distinctUntilChanged().flatMapLatest { ids ->
            packing.packingFor(ids).map { byTrip -> byTrip.mapValues { PackingEngine.progress(it.value) } }
        }
        combine(list, persons.observe(), progress) { all, people, byTrip ->
            val byId = people.associateBy { it.id }
            all.map { trip -> TripListItem(trip, trip.travellerIds.mapNotNull(byId::get), byTrip[trip.id] ?: PackingProgress.NONE) }
        }
    }

    private val loader = ListLoader(items, viewModelScope)
    val state = loader.state

    fun onEvent(event: TripsEvent) {
        when (event) {
            is TripsEvent.List -> loader.onEvent(event.event)
        }
    }

    private companion object {
        val NEWEST_FIRST: Comparator<Trip> = compareBy<Trip> { it.archived }.thenByDescending { it.startDate }.thenBy { it.name }
    }
}

/** Fliken Resor – appens startskärm (NAV-2, RES-1). */
@Composable
fun TripsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TripsContent(state, viewModel::onEvent, onAdd, onOpen, archive = viewModel.archive.collectAsListArchive())
}

@Composable
fun TripsContent(
    state: ListUiState<TripListItem>,
    onEvent: (TripsEvent) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    archive: ListArchive = ListArchive(),
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
        key = { it.trip.id },
        modifier = modifier,
        onRetry = { onEvent(TripsEvent.List(ListEvent.Retry)) },
        archive = archive,
    ) { item ->
        val trip = item.trip
        SwipeToHide(onHide = { archive.archive(trip.id, trip.name) }, enabled = !trip.archived) { swipe ->
            TripRow(item, swipe, onOpen = { onOpen(trip.id) })
        }
    }
}

/** Namn, startdatum och dagar, status, resenärerna och packat (RES-1). En klar resa är nedtonad. */
@Composable
private fun TripRow(item: TripListItem, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val trip = item.trip
    val days = pluralStringResource(R.plurals.trip_days, trip.days, trip.days)
    val subtitle = when {
        trip.archived -> stringResource(R.string.archived_state)
        trip.startDate != null -> stringResource(R.string.trip_date_days_format, DateFormat.display(trip.startDate!!), days)
        else -> days
    }
    val (status, tone) = trip.status.label()
    ItemRow(
        title = trip.name,
        modifier = modifier,
        subtitle = subtitle,
        trailing = { InfoPill(stringResource(status), tone = tone) },
        onClick = onOpen,
        navigates = true,
        inactive = trip.status == TripStatus.DONE,
        below = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                AvatarStack(item.travellers.map { StackedAvatar(it.emoji, AppColors.person(it.colorHex), it.name) })
                if (item.progress.total > 0) SuitcaseProgress(item.progress.packed, item.progress.total, compact = true)
            }
        },
    )
}

/** Statusens namn och ton i listan (RES-1). */
private fun TripStatus.label(): Pair<Int, Tone> = when (this) {
    TripStatus.PLANNING -> R.string.trip_status_planning to Tone.Blue
    TripStatus.PACKED -> R.string.trip_status_packed to Tone.Green
    TripStatus.DONE -> R.string.trip_status_done to Tone.Primary
}
