package se.partee71.reseapoteket.ui.persons

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.core.model.listed
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.RegimenRepository
import se.partee71.reseapoteket.ui.common.ArchiveActions
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.common.ListEvent
import se.partee71.reseapoteket.ui.common.ListLoader
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.components.ArchivedToggle
import se.partee71.reseapoteket.ui.components.EmptyContent
import se.partee71.reseapoteket.ui.components.EntityListScreen
import se.partee71.reseapoteket.ui.components.ItemRow
import se.partee71.reseapoteket.ui.components.PersonAvatar
import se.partee71.reseapoteket.ui.components.SwipeToHide
import se.partee71.reseapoteket.ui.components.UndoRequest
import se.partee71.reseapoteket.ui.theme.AppColors

/** En rad i personlistan: personen och hur många recept och kosttillskott den har (PER-1). */
data class PersonListItem(val person: Person, val medicines: Int = 0, val supplements: Int = 0)

sealed interface PersonsEvent {
    data class List(val event: ListEvent) : PersonsEvent

    /** Svep-arkivera, Ångra och "Visa arkiverade" (PER-3) – samma som i alla listor. */
    data class Archiving(val event: ArchiveEvent) : PersonsEvent
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PersonsViewModel @Inject constructor(
    persons: PersonRepository,
    regimens: RegimenRepository,
) : ViewModel() {
    private val archive = ArchiveActions(viewModelScope, persons::setArchived)
    val showingArchived: StateFlow<Boolean> = archive.showingArchived
    val undo: StateFlow<UndoRequest?> = archive.undo
    val error: StateFlow<DataError?> = archive.error

    // Aktiva först; arkiverade sist och bara när de visas (PER-1, PER-3).
    private val items: Flow<List<PersonListItem>> =
        archive.visible(persons.observe()).flatMapLatest { list ->
            regimens.regimensFor(list.map { it.id }).map { byPerson ->
                list.map { person ->
                    val own = byPerson[person.id].orEmpty()
                    PersonListItem(person, own.listed(RegimenKind.MEDICINE).size, own.listed(RegimenKind.SUPPLEMENT).size)
                }
            }
        }

    private val loader = ListLoader(items, viewModelScope)
    val state = loader.state

    fun onEvent(event: PersonsEvent) {
        when (event) {
            is PersonsEvent.List -> loader.onEvent(event.event)
            is PersonsEvent.Archiving -> archive.onEvent(event.event)
        }
    }
}

/** Fliken Personer (PER-1, PER-3). */
@Composable
fun PersonsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: PersonsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showingArchived by viewModel.showingArchived.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    PersonsContent(state, showingArchived, undo, viewModel::onEvent, onAdd, onOpen, error = error)
}

@Composable
fun PersonsContent(
    state: ListUiState<PersonListItem>,
    showingArchived: Boolean,
    undo: UndoRequest?,
    onEvent: (PersonsEvent) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: DataError? = null,
) {
    EntityListScreen(
        title = stringResource(R.string.tab_persons),
        state = state,
        empty = EmptyContent(
            icon = R.drawable.ic_person,
            title = stringResource(R.string.persons_empty_title),
            message = stringResource(R.string.persons_empty_message),
            actionLabel = stringResource(R.string.persons_add),
        ),
        onAdd = onAdd,
        key = { it.person.id },
        modifier = modifier,
        onRetry = { onEvent(PersonsEvent.List(ListEvent.Retry)) },
        archived = ArchivedToggle(showingArchived) { onEvent(PersonsEvent.Archiving(ArchiveEvent.ToggleArchived)) },
        undo = undo,
        onUndo = { onEvent(PersonsEvent.Archiving(ArchiveEvent.Undo)) },
        onUndoDismissed = { onEvent(PersonsEvent.Archiving(ArchiveEvent.UndoDismissed)) },
        error = error,
        onErrorShown = { onEvent(PersonsEvent.Archiving(ArchiveEvent.ErrorShown)) },
    ) { item ->
        val person = item.person
        if (person.archived) {
            PersonRow(item, onOpen = { onOpen(person.id) })
        } else {
            SwipeToHide(onHide = { onEvent(PersonsEvent.Archiving(ArchiveEvent.Archive(person.id, person.name))) }) { swipe ->
                PersonRow(item, swipe, onOpen = { onOpen(person.id) })
            }
        }
    }
}

@Composable
private fun PersonRow(item: PersonListItem, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val person = item.person
    ItemRow(
        title = person.name,
        modifier = modifier,
        subtitle = if (person.archived) stringResource(R.string.archived_state) else personSummary(item.medicines, item.supplements),
        leading = { PersonAvatar(person.emoji, AppColors.person(person.colorHex)) },
        onClick = onOpen,
        navigates = true,
    )
}

/** "3 recept · 1 kosttillskott", eller att personen inte har något än – samma text i lista och detalj. */
@Composable
internal fun personSummary(medicines: Int, supplements: Int): String {
    val parts = listOfNotNull(
        medicines.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.person_medicines, it, it) },
        supplements.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.person_supplements, it, it) },
    )
    return if (parts.isEmpty()) stringResource(R.string.person_nothing_yet) else parts.joinToString(" · ")
}
