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
import kotlinx.coroutines.flow.Flow
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.RegimenKind
import se.partee71.reseapoteket.core.model.listed
import se.partee71.reseapoteket.data.common.combineByKey
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.data.repository.RegimenRepository
import se.partee71.reseapoteket.ui.common.ArchiveActions
import se.partee71.reseapoteket.ui.common.ListEvent
import se.partee71.reseapoteket.ui.common.ListLoader
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.common.collectAsListArchive
import se.partee71.reseapoteket.ui.components.EmptyContent
import se.partee71.reseapoteket.ui.components.EntityListScreen
import se.partee71.reseapoteket.ui.components.ItemRow
import se.partee71.reseapoteket.ui.components.ListArchive
import se.partee71.reseapoteket.ui.components.PersonAvatar
import se.partee71.reseapoteket.ui.components.SwipeToHide
import se.partee71.reseapoteket.ui.theme.AppColors

/** En rad i personlistan: personen och hur många recept och kosttillskott den har (PER-1). */
data class PersonListItem(val person: Person, val medicines: Int = 0, val supplements: Int = 0)

sealed interface PersonsEvent {
    data class List(val event: ListEvent) : PersonsEvent
}

@HiltViewModel
class PersonsViewModel @Inject constructor(
    persons: PersonRepository,
    regimens: RegimenRepository,
) : ViewModel() {
    /** Svep-arkivera, Ångra och "Visa arkiverade" (PER-3) – samma som i alla listor. */
    val archive = ArchiveActions(viewModelScope, persons::setArchived)

    // Aktiva först; arkiverade sist och bara när de visas (PER-1, PER-3).
    private val items: Flow<List<PersonListItem>> =
        archive.visible(persons.observe()).combineByKey({ list -> list.map { it.id }.toSet() }, { ids -> regimens.regimensFor(ids.toList()) }) { list, byPerson ->
            list.map { person ->
                val own = byPerson[person.id].orEmpty()
                PersonListItem(person, own.listed(RegimenKind.MEDICINE).size, own.listed(RegimenKind.SUPPLEMENT).size)
            }
        }

    private val loader = ListLoader(items, viewModelScope)
    val state = loader.state

    fun onEvent(event: PersonsEvent) {
        when (event) {
            is PersonsEvent.List -> loader.onEvent(event.event)
        }
    }
}

/** Fliken Personer (PER-1, PER-3). */
@Composable
fun PersonsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: PersonsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PersonsContent(state, viewModel::onEvent, onAdd, onOpen, archive = viewModel.archive.collectAsListArchive())
}

@Composable
fun PersonsContent(
    state: ListUiState<PersonListItem>,
    onEvent: (PersonsEvent) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    archive: ListArchive = ListArchive(),
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
        archive = archive,
    ) { item ->
        val person = item.person
        SwipeToHide(onHide = { archive.archive(person.id, person.name) }, enabled = !person.archived) { swipe ->
            PersonRow(item, swipe, onOpen = { onOpen(person.id) })
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
