package se.partee71.reseapoteket.ui.familyitems

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.core.engine.QuantityRuleFormatter
import se.partee71.reseapoteket.core.model.FamilyItem
import se.partee71.reseapoteket.core.model.FamilyItemCategory
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.repository.FamilyItemRepository
import se.partee71.reseapoteket.ui.common.ArchiveActions
import se.partee71.reseapoteket.ui.common.ArchiveEvent
import se.partee71.reseapoteket.ui.common.ListEvent
import se.partee71.reseapoteket.ui.common.ListLoader
import se.partee71.reseapoteket.ui.common.ListUiState
import se.partee71.reseapoteket.ui.components.ArchivedToggle
import se.partee71.reseapoteket.ui.components.EmptyContent
import se.partee71.reseapoteket.ui.components.EmptyExample
import se.partee71.reseapoteket.ui.components.EntityListScreen
import se.partee71.reseapoteket.ui.components.ListGroup
import se.partee71.reseapoteket.ui.components.PausableRow
import se.partee71.reseapoteket.ui.components.SwipeToHide
import se.partee71.reseapoteket.ui.components.UndoRequest
import se.partee71.reseapoteket.ui.components.kindLabel
import se.partee71.reseapoteket.ui.theme.Tone

sealed interface FamilyItemsEvent {
    data class List(val event: ListEvent) : FamilyItemsEvent

    /** Svep-arkivera, Ångra och "Visa arkiverade" (FAM-2) – samma som i alla listor. */
    data class Archiving(val event: ArchiveEvent) : FamilyItemsEvent
}

@HiltViewModel
class FamilyItemsViewModel @Inject constructor(items: FamilyItemRepository) : ViewModel() {
    private val archive = ArchiveActions(viewModelScope, items::setArchived)
    val showingArchived: StateFlow<Boolean> = archive.showingArchived
    val undo: StateFlow<UndoRequest?> = archive.undo
    val error: StateFlow<DataError?> = archive.error

    // Per kategori i fast ordning (FAM-1); inom kategorin aktiva först, sedan i sparad ordning.
    private val loader = ListLoader(archive.visible(items.observe()).map { list -> list.sortedBy { it.category.ordinal } }, viewModelScope)
    val state = loader.state

    fun onEvent(event: FamilyItemsEvent) {
        when (event) {
            is FamilyItemsEvent.List -> loader.onEvent(event.event)
            is FamilyItemsEvent.Archiving -> archive.onEvent(event.event)
        }
    }
}

/** Fliken Familjeartiklar (FAM-1–4). */
@Composable
fun FamilyItemsScreen(
    onAdd: () -> Unit,
    onAddExample: (FamilyItemExample) -> Unit,
    onOpen: (String) -> Unit,
    viewModel: FamilyItemsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showingArchived by viewModel.showingArchived.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    FamilyItemsContent(state, viewModel::onEvent, onAdd, onOpen, showingArchived = showingArchived, undo = undo, error = error, onAddExample = onAddExample)
}

@Composable
fun FamilyItemsContent(
    state: ListUiState<FamilyItem>,
    onEvent: (FamilyItemsEvent) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    showingArchived: Boolean = false,
    undo: UndoRequest? = null,
    error: DataError? = null,
    onAddExample: (FamilyItemExample) -> Unit = {},
) {
    val examples = FamilyItemExample.entries.map { example -> EmptyExample(stringResource(example.label)) { onAddExample(example) } }
    val groups = categoryGroups()
    val group = remember(groups) { { item: FamilyItem -> groups.getValue(item.category) } }
    EntityListScreen(
        title = stringResource(R.string.tab_family_items),
        state = state,
        empty = EmptyContent(
            icon = R.drawable.ic_family_items,
            title = stringResource(R.string.family_items_empty_title),
            message = stringResource(R.string.family_items_empty_message),
            actionLabel = stringResource(R.string.add),
            examples = examples,
        ),
        onAdd = onAdd,
        key = FamilyItem::id,
        modifier = modifier,
        onRetry = { onEvent(FamilyItemsEvent.List(ListEvent.Retry)) },
        group = group,
        archived = ArchivedToggle(showingArchived) { onEvent(FamilyItemsEvent.Archiving(ArchiveEvent.ToggleArchived)) },
        undo = undo,
        onUndo = { onEvent(FamilyItemsEvent.Archiving(ArchiveEvent.Undo)) },
        onUndoDismissed = { onEvent(FamilyItemsEvent.Archiving(ArchiveEvent.UndoDismissed)) },
        error = error,
        onErrorShown = { onEvent(FamilyItemsEvent.Archiving(ArchiveEvent.ErrorShown)) },
    ) { item ->
        if (item.archived) {
            FamilyItemRow(item, onOpen = { onOpen(item.id) })
        } else {
            SwipeToHide(onHide = { onEvent(FamilyItemsEvent.Archiving(ArchiveEvent.Archive(item.id, item.name))) }) { swipe ->
                FamilyItemRow(item, swipe, onOpen = { onOpen(item.id) })
            }
        }
    }
}

/** En rubrik per kategori, med ikon, i Övrigt-tonen (FAM-1). */
@Composable
private fun categoryGroups(): Map<FamilyItemCategory, ListGroup> =
    FamilyItemCategory.entries.associateWith { ListGroup(stringResource(it.label()), it.icon(), Tone.Blue) }

/** Namn, mängdregeln i klartext och regeltypen (FAM-3); pausad nedtonad som recepten. */
@Composable
private fun FamilyItemRow(item: FamilyItem, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    PausableRow(
        title = item.name,
        detail = if (item.archived) stringResource(R.string.archived_state) else QuantityRuleFormatter.format(item.rule, item.unit),
        kind = item.rule.kindLabel()?.let { stringResource(it) },
        // En arkiverad visas som arkiverad även om den är pausad – den återställs från formuläret.
        active = item.active || item.archived,
        onClick = onOpen,
        modifier = modifier,
    )
}
