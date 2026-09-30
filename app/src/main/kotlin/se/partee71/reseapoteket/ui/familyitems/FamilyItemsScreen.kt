package se.partee71.reseapoteket.ui.familyitems

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
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.data.repository.FamilyItemRepository
import se.partee71.reseapoteket.ui.common.ListLoader
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.common.activeOnly
import se.partee71.reseapoteket.ui.components.EmptyContent
import se.partee71.reseapoteket.ui.components.EntityListScreen
import se.partee71.reseapoteket.ui.components.ItemRow

sealed interface FamilyItemsEvent {
    data object Retry : FamilyItemsEvent
}

@HiltViewModel
class FamilyItemsViewModel @Inject constructor(items: FamilyItemRepository) : ViewModel() {
    private val loader = ListLoader(items.observe().activeOnly(), viewModelScope)
    val state = loader.state

    fun onEvent(event: FamilyItemsEvent) {
        when (event) {
            FamilyItemsEvent.Retry -> loader.retry()
        }
    }
}

/** Fliken Familjeartiklar. */
@Composable
fun FamilyItemsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: FamilyItemsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FamilyItemsContent(state, viewModel::onEvent, onAdd, onOpen)
}

@Composable
fun FamilyItemsContent(
    state: ListUiState<FamilyItem>,
    onEvent: (FamilyItemsEvent) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    EntityListScreen(
        title = stringResource(R.string.tab_family_items),
        state = state,
        empty = EmptyContent(
            icon = R.drawable.ic_family_items,
            title = stringResource(R.string.family_items_empty_title),
            message = stringResource(R.string.family_items_empty_message),
            actionLabel = stringResource(R.string.add),
        ),
        onAdd = onAdd,
        key = FamilyItem::id,
        modifier = modifier,
        onRetry = { onEvent(FamilyItemsEvent.Retry) },
    ) { item ->
        ItemRow(title = item.name, onClick = { onOpen(item.id) }, navigates = true)
    }
}
