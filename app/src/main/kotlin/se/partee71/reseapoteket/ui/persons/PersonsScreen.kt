package se.partee71.reseapoteket.ui.persons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.data.repository.PersonRepository
import se.partee71.reseapoteket.ui.common.ListEvent
import se.partee71.reseapoteket.ui.common.ListLoader
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.common.activeOnly
import se.partee71.reseapoteket.ui.components.EmptyContent
import se.partee71.reseapoteket.ui.components.EntityListScreen
import se.partee71.reseapoteket.ui.components.ItemRow
import se.partee71.reseapoteket.ui.components.PersonAvatar
import se.partee71.reseapoteket.ui.theme.AppColors

@HiltViewModel
class PersonsViewModel @Inject constructor(persons: PersonRepository) : ViewModel() {
    private val loader = ListLoader(persons.observe().activeOnly(), viewModelScope)
    val state = loader.state

    fun onEvent(event: ListEvent) = loader.onEvent(event)
}

/** Fliken Personer. */
@Composable
fun PersonsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: PersonsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PersonsContent(state, viewModel::onEvent, onAdd, onOpen)
}

@Composable
fun PersonsContent(
    state: ListUiState<Person>,
    onEvent: (ListEvent) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
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
        key = Person::id,
        modifier = modifier,
        onRetry = { onEvent(ListEvent.Retry) },
    ) { person ->
        ItemRow(
            title = person.name,
            leading = { PersonAvatar(person.emoji, AppColors.person(person.colorHex)) },
            onClick = { onOpen(person.id) },
            navigates = true,
        )
    }
}
